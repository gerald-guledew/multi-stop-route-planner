package com.solvelify.routeplanner.poi;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads the suburbs, towns and other areas from the OpenStreetMap export, so that a search finds
 * an area by its name. Runs after the other places import, on the same
 * {@code --routeplanner.poi.import-enabled=true}.
 *
 * <p>The map often has one area twice: a point where it is known to be, and an outline of where it
 * reaches. Of two areas of one name within 5 km, the point is kept, and of two points the larger
 * kind of area, a suburb before a locality. An area goes only for one that stays, so three of a
 * name in a row, 3 km apart, keep the first and the third.
 *
 * <p>Each area takes the city of the nearest LINZ address, as a place takes its suburb, so the
 * addresses are imported first. The address has to be within 2 km, as a remote locality's nearest
 * address can be a town away. And the city has to be another place than the area: Warkworth is
 * not in Warkworth.
 *
 * <p>Built to run again. The areas an earlier run loaded are replaced, so what comes out is what
 * the file says.
 */
@Component
@Order(4)
@ConditionalOnProperty(name = "routeplanner.poi.import-enabled", havingValue = "true")
class AreaImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AreaImporter.class);

    /** How far apart two areas of one name can be and still be one area, drawn twice. */
    private static final int ONE_AREA_METRES = 5_000;

    /** How far away the nearest address may be and still lend the area its city. */
    private static final int CITY_METRES = 2_000;

    /** Shaped exactly like the file, all text: the CSV is parsed, not trusted. */
    private static final String CREATE_STAGING = """
            CREATE TEMPORARY TABLE area_import (
                id text, name text, place text, latitude text, longitude text
            ) ON COMMIT DROP
            """;

    /** MATCH makes PostgreSQL check the header against those column names, in that order. */
    private static final String COPY_IN = "COPY area_import FROM STDIN WITH (FORMAT csv, HEADER MATCH)";

    /**
     * Each area with where it stands, and what decides between two of one name: a point before an
     * outline, then the larger kind of area. A kind not listed comes last.
     */
    private static final String READ_POSITIONS = """
            CREATE TEMPORARY TABLE on_the_map ON COMMIT DROP AS
            SELECT id, name,
                   latitude::double precision AS latitude,
                   longitude::double precision AS longitude,
                   ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision),
                              4326)::geography AS location,
                   lower(searchable(name)) AS plain_name,
                   CASE WHEN id LIKE 'node/%' THEN 0 ELSE 1 END AS drawn,
                   coalesce(array_position(ARRAY['city', 'town', 'suburb', 'quarter', 'village',
                                                 'neighbourhood', 'hamlet', 'locality'], place), 99) AS size
            FROM area_import
            """;

    private static final String INDEX_POSITIONS = "CREATE INDEX ON on_the_map USING gist (location)";

    private static final String READ_THE_TABLE_FILLED = "ANALYZE on_the_map";

    /**
     * Of one area drawn twice, the one that comes first stays. An area that nothing near comes
     * before is sure to stay, so each round removes what comes after one of those. One removed can
     * leave another with nothing before it, which then stays, so the rounds go on until one removes
     * nothing.
     */
    private static final String KEEP_ONE_OF_EACH = """
            DELETE FROM on_the_map drawn_again
            USING on_the_map kept
            WHERE kept.plain_name = drawn_again.plain_name
              AND ST_DWithin(kept.location, drawn_again.location, ?, false)
              AND (kept.drawn, kept.size, kept.id) < (drawn_again.drawn, drawn_again.size, drawn_again.id)
              AND NOT EXISTS (
                  SELECT 1 FROM on_the_map before_it
                  WHERE before_it.plain_name = kept.plain_name
                    AND ST_DWithin(before_it.location, kept.location, ?, false)
                    AND (before_it.drawn, before_it.size, before_it.id) < (kept.drawn, kept.size, kept.id))
            """;

    private static final String REMOVE_THE_AREAS = "DELETE FROM area";

    /**
     * Each area with the city of its nearest address, read from the index on where the addresses
     * are. The nearest is found first and measured after. Asking for it within a distance would
     * cost the index its short cut.
     */
    private static final String ADD_THE_AREAS = """
            INSERT INTO area (id, name, city, latitude, longitude)
            SELECT on_the_map.id, on_the_map.name,
                   CASE WHEN nearest.metres <= ?
                             AND lower(searchable(nearest.town_city)) <> on_the_map.plain_name
                        THEN nearest.town_city
                   END,
                   on_the_map.latitude, on_the_map.longitude
            FROM on_the_map
            LEFT JOIN LATERAL (
                SELECT nullif(address.town_city, '') AS town_city,
                       ST_Distance(address.location, on_the_map.location, false) AS metres
                FROM address
                ORDER BY address.location <-> on_the_map.location
                LIMIT 1
            ) nearest ON true
            """;

    private static final String COUNT_AREAS_WITH_A_CITY = "SELECT count(*) FROM area WHERE city IS NOT NULL";

    private final DataSource dataSource;
    private final PoiProperties properties;

    AreaImporter(DataSource dataSource, PoiProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        importFrom(Path.of(properties.areasCsv()));
    }

    void importFrom(Path csv) throws Exception {
        if (!Files.exists(csv)) {
            throw new IllegalStateException("Areas CSV not found at " + csv.toAbsolutePath()
                    + ". Create it with: duckdb -f scripts/export-osm-areas.sql");
        }

        log.info("Importing the suburbs and towns from {}", csv.toAbsolutePath());
        long startedAt = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection()) {
            // One connection, one transaction: the temporary tables have to outlive the COPY,
            // and a failure part way through must leave the existing areas as they were.
            connection.setAutoCommit(false);
            try {
                execute(connection, CREATE_STAGING);

                long read = copyInto(connection, csv);
                if (read == 0) {
                    // Carrying on would take every area away.
                    throw new IllegalStateException("Areas CSV at " + csv.toAbsolutePath()
                            + " has no rows. Nothing was changed.");
                }

                execute(connection, READ_POSITIONS);
                execute(connection, INDEX_POSITIONS);
                execute(connection, READ_THE_TABLE_FILLED);
                long drawnTwice = keepOneOfEach(connection);
                execute(connection, REMOVE_THE_AREAS);
                long added = update(connection, ADD_THE_AREAS, CITY_METRES);
                long withACity = count(connection, COUNT_AREAS_WITH_A_CITY);
                connection.commit();

                log.info("{} areas, {} of them with a city from the nearest address, in {} seconds. {} more"
                                + " were the same area drawn twice.",
                        added, withACity, (System.currentTimeMillis() - startedAt) / 1000, drawnTwice);
                if (withACity == 0) {
                    log.warn("No area got a city. Import the addresses first, then run this again.");
                }
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private static long keepOneOfEach(Connection connection) throws SQLException {
        long removed = 0;
        try (PreparedStatement statement = connection.prepareStatement(KEEP_ONE_OF_EACH)) {
            statement.setInt(1, ONE_AREA_METRES);
            statement.setInt(2, ONE_AREA_METRES);
            long thisRound;
            do {
                thisRound = statement.executeLargeUpdate();
                removed += thisRound;
            } while (thisRound > 0);
        }
        return removed;
    }

    private long copyInto(Connection connection, Path csv) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            return connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY_IN, reader);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long update(Connection connection, String sql, int metres) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, metres);
            return statement.executeLargeUpdate();
        }
    }

    private static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             var result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }
}

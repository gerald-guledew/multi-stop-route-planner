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
 * Loads named places from the Overture Maps export into PostgreSQL. Runs only when asked,
 * with {@code --routeplanner.poi.import-enabled=true}.
 *
 * <p>Built to be run again every time Overture publishes, which is monthly. Their ids stay
 * the same from one release to the next, so a place that is still there is updated, a new
 * one is added, and one that has gone from the file is removed. Nothing is left behind
 * from an older release.
 *
 * <p>Ordered after the address import, because the last step borrows each place's suburb
 * from the nearest address.
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "routeplanner.poi.import-enabled", havingValue = "true")
class PoiImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PoiImporter.class);

    /**
     * How far away an address may be and still lend its suburb. Far enough to reach across
     * a car park or a large site, near enough that a place in the hills gets no suburb
     * rather than one from the next valley.
     */
    private static final int SUBURB_SEARCH_METRES = 500;

    /** Shaped exactly like the file, all text: the CSV is parsed, not trusted. */
    private static final String CREATE_STAGING = """
            CREATE TEMPORARY TABLE overture_import (
                id text, name text, address text, locality text, category text,
                confidence text, latitude text, longitude text
            ) ON COMMIT DROP
            """;

    /** MATCH makes PostgreSQL check the header against those column names, in that order. */
    private static final String COPY_IN =
            "COPY overture_import FROM STDIN WITH (FORMAT csv, HEADER MATCH)";

    /**
     * A place that has moved loses its suburb here, and gets the right one back in the last
     * step. One that has not moved keeps the suburb it already has.
     */
    private static final String UPSERT_PLACES = """
            INSERT INTO poi (id, source, name, address, locality, category, confidence,
                             latitude, longitude)
            SELECT id,
                   'overture',
                   name,
                   nullif(address, ''),
                   nullif(locality, ''),
                   nullif(category, ''),
                   confidence::real,
                   latitude::double precision,
                   longitude::double precision
            FROM overture_import
            WHERE name IS NOT NULL AND name <> ''
            ON CONFLICT (id) DO UPDATE SET
                name = excluded.name,
                address = excluded.address,
                locality = excluded.locality,
                category = excluded.category,
                confidence = excluded.confidence,
                latitude = excluded.latitude,
                longitude = excluded.longitude,
                suburb = CASE
                             WHEN poi.latitude = excluded.latitude
                                  AND poi.longitude = excluded.longitude
                             THEN poi.suburb
                         END
            """;

    private static final String REMOVE_PLACES_NO_LONGER_LISTED = """
            DELETE FROM poi
            WHERE source = 'overture'
              AND NOT EXISTS (SELECT 1 FROM overture_import WHERE overture_import.id = poi.id)
            """;

    /**
     * Overture says "Auckland" where people say "Remuera", so each place takes the suburb of
     * the nearest LINZ address. The spatial index on the address table answers that in a
     * fraction of a millisecond per place.
     */
    private static final String FILL_SUBURBS = """
            UPDATE poi
            SET suburb = (SELECT address.suburb
                          FROM address
                          WHERE ST_DWithin(address.location, poi.location, ?)
                          ORDER BY address.location <-> poi.location
                          LIMIT 1)
            WHERE source = 'overture' AND suburb IS NULL
            """;

    private static final String COUNT_PLACES_WITH_A_SUBURB =
            "SELECT count(*) FROM poi WHERE source = 'overture' AND suburb IS NOT NULL";

    private final DataSource dataSource;
    private final PoiProperties properties;

    PoiImporter(DataSource dataSource, PoiProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        importFrom(Path.of(properties.csv()));
    }

    void importFrom(Path csv) throws Exception {
        if (!Files.exists(csv)) {
            throw new IllegalStateException("Places CSV not found at " + csv.toAbsolutePath()
                    + ". Create it with: duckdb -f scripts/export-overture-places.sql");
        }

        log.info("Importing places from {}", csv.toAbsolutePath());
        long startedAt = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection()) {
            // One connection, one transaction: the temporary table has to outlive the COPY,
            // and a failure part way through must leave the existing places as they were.
            connection.setAutoCommit(false);
            try {
                execute(connection, CREATE_STAGING);

                long read = copyInto(connection, csv);
                if (read == 0) {
                    // Carrying on would remove every place as "no longer listed".
                    throw new IllegalStateException("Places CSV at " + csv.toAbsolutePath()
                            + " has no rows. Nothing was changed.");
                }

                long stored = update(connection, UPSERT_PLACES);
                long removed = update(connection, REMOVE_PLACES_NO_LONGER_LISTED);
                fillSuburbs(connection);
                long withSuburb = count(connection, COUNT_PLACES_WITH_A_SUBURB);
                connection.commit();

                log.info("Imported {} of {} places in {} seconds. Removed {} that are no longer"
                                + " listed. {} have a suburb from the nearest address.",
                        stored, read, (System.currentTimeMillis() - startedAt) / 1000,
                        removed, withSuburb);
                if (withSuburb == 0) {
                    log.warn("No place got a suburb. Import the addresses first, then run this"
                            + " again, or searches like \"the warehouse newmarket\" will not work.");
                }
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private long copyInto(Connection connection, Path csv) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            return connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY_IN, reader);
        }
    }

    private void fillSuburbs(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(FILL_SUBURBS)) {
            statement.setInt(1, SUBURB_SEARCH_METRES);
            statement.executeUpdate();
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private static long update(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeLargeUpdate(sql);
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

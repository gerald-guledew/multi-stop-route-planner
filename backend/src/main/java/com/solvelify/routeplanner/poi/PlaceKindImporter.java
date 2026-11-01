package com.solvelify.routeplanner.poi;

import com.solvelify.routeplanner.search.SearchProperties;
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
 * Says what kind of place each place is, from the OpenStreetMap export. Runs after the Overture
 * import, on the same {@code --routeplanner.poi.import-enabled=true}.
 *
 * <p>The two sources make one list, in which a place shows once. Overture has the fuller names,
 * "New World Remuera" where the map says "New World", and search already reads it. The map says
 * more reliably what a place is. So a place on the map lends its kind to Overture's place of a
 * like name beside it, and one Overture does not have is added under the source {@code osm}.
 * Overture often lists one shop two or three times, and only the one matched takes the kind.
 *
 * <p>Where the map lacks a place, Overture's own category gives the kind, for the categories in
 * {@link #CREATE_OVERTURE_KINDS}. No rule hides a place because only one source has it: the
 * map has 135 of Caltex's 203 fuel stations.
 *
 * <p>Built to run again. Whatever an earlier run did is undone first, so what comes out is what
 * the two files say.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "routeplanner.poi.import-enabled", havingValue = "true")
class PlaceKindImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlaceKindImporter.class);

    /** How far apart a shop on the map and in Overture can be and still be one place. */
    private static final int SHOP_METRES = 150;

    /**
     * The same for a place drawn as an outline. It stands at the outline's middle on the map, and
     * Overture's point can be anywhere in it.
     */
    private static final int LARGE_PLACE_METRES = 500;

    /**
     * How alike the names of a place drawn as an outline must read, from 0 to 1. "Sylvia Park" and
     * "Sylvia Park" do, and "Waihi Beach" and "Waihi Beach Butchery" do not.
     */
    private static final double LARGE_PLACE_LIKENESS = 0.7;

    /** The map gives no such figure. People put these places there, so they are taken as sure. */
    private static final double CONFIDENCE_OF_A_PLACE_ON_THE_MAP = 0.8;

    /** Shaped exactly like the file, all text: the CSV is parsed, not trusted. */
    private static final String CREATE_STAGING = """
            CREATE TEMPORARY TABLE osm_import (
                id text, name text, place_kind text, latitude text, longitude text
            ) ON COMMIT DROP
            """;

    /** MATCH makes PostgreSQL check the header against those column names, in that order. */
    private static final String COPY_IN =
            "COPY osm_import FROM STDIN WITH (FORMAT csv, HEADER MATCH)";

    private static final String READ_POSITIONS = """
            CREATE TEMPORARY TABLE on_the_map ON COMMIT DROP AS
            SELECT id, name, place_kind,
                   latitude::double precision AS latitude,
                   longitude::double precision AS longitude,
                   ST_SetSRID(ST_MakePoint(longitude::double precision, latitude::double precision),
                              4326)::geography AS location
            FROM osm_import
            """;

    private static final String INDEX_POSITIONS =
            "CREATE INDEX ON on_the_map USING gist (location)";

    /**
     * The places of Overture that can be matched, with an index on where they are, which only this
     * import has a use for. Places search hides are no match, or the place would go with them.
     */
    private static final String LIST_OVERTURE_PLACES = """
            CREATE TEMPORARY TABLE overture_place ON COMMIT DROP AS
            SELECT id, name, category, location
            FROM poi
            WHERE source = 'overture' AND confidence >= ?
            """;

    private static final String INDEX_OVERTURE_PLACES =
            "CREATE INDEX ON overture_place USING gist (location)";

    private static final String READ_THE_TABLES_FILLED = "ANALYZE on_the_map, overture_place";

    private static final String REMOVE_THE_PLACES_ADDED = "DELETE FROM poi WHERE source = 'osm'";

    private static final String REMOVE_THE_KINDS =
            "UPDATE poi SET place_kind = NULL WHERE place_kind IS NOT NULL";

    /**
     * Overture's categories that a look through them found right. Its {@code zoo} holds a handyman,
     * its {@code shopping_mall} a stream and a garden fete, and its {@code park} and {@code beach}
     * hold groups and towns, where the map is enough. Its supermarkets are in with the bakeries.
     */
    private static final String CREATE_OVERTURE_KINDS = """
            CREATE TEMPORARY TABLE overture_kind ON COMMIT DROP AS
            SELECT * FROM (VALUES
                ('gas_station', 'fuel'), ('fueling_station', 'fuel'),
                ('convenience_store', 'dairy'),
                ('cafe', 'cafe'), ('coffee_shop', 'cafe'),
                ('restaurant', 'food'), ('fast_food_restaurant', 'food'), ('casual_eatery', 'food'),
                ('museum', 'tourist')
            ) AS kinds (category, place_kind)
            """;

    /**
     * Whether two names are one place's. They start with the same word, leaving out "the", so
     * that Z Remuera is not BP Remuera across the road, and Sylvia Park is not a shop called
     * Charlotte Tilbury Sylvia Park. And they read near enough the same, as "BP 2go Parnell" and
     * "BP Connect Parnell", or one is held whole in the other, as "New World" in "New World
     * Remuera". Lives as long as the import's connection.
     */
    private static final String CREATE_NAMES_ALIKE = """
            CREATE OR REPLACE FUNCTION pg_temp.names_alike(a text, b text) RETURNS boolean
            LANGUAGE sql STABLE AS $$
                SELECT a_words[1] = b_words[1]
                       AND (similarity(one, other) >= 0.4
                            OR word_similarity(one, other) >= 0.8
                            OR word_similarity(other, one) >= 0.8)
                FROM (SELECT lower(searchable(a)) AS one, lower(searchable(b)) AS other) AS name,
                     LATERAL regexp_split_to_array(regexp_replace(one, '^the\\s+', ''), '\\W+') AS a_words,
                     LATERAL regexp_split_to_array(regexp_replace(other, '^the\\s+', ''), '\\W+') AS b_words
            $$
            """;

    /**
     * Each place on the map and the Overture place of the likest name beside it, the nearest of
     * those alike. Each Overture place goes to the place on the map nearest it, and the other is
     * left to be added on its own.
     *
     * <p>Toilets and lookouts are matched with nothing, as Overture does not list them: a toilet
     * block named for its beach would take the beach. And a place drawn as an outline reaches a
     * long way over what is named after it, so its match has to read almost the same, and what
     * Overture files as another kind is another place: Cornwall Park Cafe, in the park.
     */
    private static final String MATCH = """
            CREATE TEMPORARY TABLE matched ON COMMIT DROP AS
            SELECT DISTINCT ON (nearest.id) nearest.id, on_the_map.id AS map_id, on_the_map.place_kind
            FROM on_the_map
            CROSS JOIN LATERAL (
                SELECT overture_place.id,
                       ST_Distance(overture_place.location, on_the_map.location, false) AS metres,
                       similarity(lower(searchable(overture_place.name)),
                                  lower(searchable(on_the_map.name))) AS likeness
                FROM overture_place
                WHERE ST_DWithin(overture_place.location, on_the_map.location,
                                 CASE WHEN on_the_map.place_kind IN
                                           ('park', 'beach', 'mall', 'zoo', 'tourist')
                                      THEN ? ELSE ? END,
                                 false)
                  AND pg_temp.names_alike(overture_place.name, on_the_map.name)
                  AND on_the_map.place_kind NOT IN ('toilets', 'lookout')
                  AND (on_the_map.place_kind NOT IN ('park', 'beach', 'mall', 'zoo', 'tourist')
                       OR (similarity(lower(searchable(overture_place.name)),
                                      lower(searchable(on_the_map.name))) >= ?
                           AND NOT EXISTS (
                               SELECT 1 FROM overture_kind
                               WHERE overture_kind.category = overture_place.category
                                 AND overture_kind.place_kind <> on_the_map.place_kind)))
                ORDER BY likeness DESC, metres
                LIMIT 1
            ) nearest
            ORDER BY nearest.id, nearest.metres
            """;

    private static final String LEND_KINDS = """
            UPDATE poi SET place_kind = matched.place_kind
            FROM matched
            WHERE poi.id = matched.id
            """;

    private static final String ADD_THE_PLACES_ONLY_THE_MAP_HAS = """
            INSERT INTO poi (id, source, name, place_kind, confidence, latitude, longitude)
            SELECT id, 'osm', name, place_kind, ?, latitude, longitude
            FROM on_the_map
            WHERE NOT EXISTS (SELECT 1 FROM matched WHERE matched.map_id = on_the_map.id)
            """;

    private static final String LIST_WHAT_OVERTURE_SAYS = """
            CREATE TEMPORARY TABLE by_category ON COMMIT DROP AS
            SELECT poi.id, kinds.place_kind, poi.name, poi.confidence, poi.location
            FROM poi
            JOIN overture_kind kinds ON kinds.category = poi.category
            WHERE poi.source = 'overture' AND poi.place_kind IS NULL AND poi.confidence >= ?
            """;

    private static final String INDEX_WHAT_OVERTURE_SAYS =
            "CREATE INDEX ON by_category USING gist (location)";

    private static final String LIST_WHAT_HAS_A_KIND = """
            CREATE TEMPORARY TABLE has_a_kind ON COMMIT DROP AS
            SELECT name, place_kind, location FROM poi WHERE place_kind IS NOT NULL
            """;

    private static final String INDEX_WHAT_HAS_A_KIND =
            "CREATE INDEX ON has_a_kind USING gist (location)";

    private static final String READ_THE_LISTS_OF_KINDS = "ANALYZE by_category, has_a_kind";

    /**
     * Unless the place has a kind already under one of its other listings, or a listing Overture
     * is surer of would take it. Of one shop listed twice, the surer listing is the one shown.
     * Each look is a lateral join so that it goes place by place, through the index on where they
     * are. Written with NOT EXISTS it is planned as one join on the kind, of every place with every
     * other of its kind, and takes minutes.
     */
    private static final String TAKE_WHAT_OVERTURE_SAYS = """
            UPDATE poi SET place_kind = candidate.place_kind
            FROM by_category candidate
            LEFT JOIN LATERAL (
                SELECT 1 AS found FROM has_a_kind known
                WHERE ST_DWithin(known.location, candidate.location, ?, false)
                  AND known.place_kind = candidate.place_kind
                  AND pg_temp.names_alike(known.name, candidate.name)
                LIMIT 1
            ) listed ON true
            LEFT JOIN LATERAL (
                SELECT 1 AS found FROM by_category surer
                WHERE ST_DWithin(surer.location, candidate.location, ?, false)
                  AND surer.place_kind = candidate.place_kind
                  AND (surer.confidence, surer.id) > (candidate.confidence, candidate.id)
                  AND pg_temp.names_alike(surer.name, candidate.name)
                LIMIT 1
            ) better ON true
            WHERE poi.id = candidate.id AND listed.found IS NULL AND better.found IS NULL
            """;

    private static final String COUNT_PLACES_WITH_A_KIND =
            "SELECT count(*) FROM poi WHERE place_kind IS NOT NULL";

    private final DataSource dataSource;
    private final PoiProperties properties;
    private final double leastConfidence;

    PlaceKindImporter(DataSource dataSource, PoiProperties properties, SearchProperties search) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.leastConfidence = search.minConfidence();
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        importFrom(Path.of(properties.osmCsv()));
    }

    void importFrom(Path csv) throws Exception {
        if (!Files.exists(csv)) {
            throw new IllegalStateException("Places CSV not found at " + csv.toAbsolutePath()
                    + ". Create it with: duckdb -f scripts/export-osm-places.sql");
        }

        log.info("Importing the kinds of places from {}", csv.toAbsolutePath());
        long startedAt = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection()) {
            // One connection, one transaction: the temporary tables have to outlive the COPY,
            // and a failure part way through must leave the existing places as they were.
            connection.setAutoCommit(false);
            try {
                execute(connection, CREATE_STAGING);

                long read = copyInto(connection, csv);
                if (read == 0) {
                    // Carrying on would take every kind away.
                    throw new IllegalStateException("Places CSV at " + csv.toAbsolutePath()
                            + " has no rows. Nothing was changed.");
                }

                execute(connection, READ_POSITIONS);
                execute(connection, INDEX_POSITIONS);
                execute(connection, CREATE_OVERTURE_KINDS);
                execute(connection, CREATE_NAMES_ALIKE);
                execute(connection, REMOVE_THE_PLACES_ADDED);
                execute(connection, REMOVE_THE_KINDS);
                listOverturePlaces(connection);
                execute(connection, INDEX_OVERTURE_PLACES);
                execute(connection, READ_THE_TABLES_FILLED);
                match(connection);
                long lent = update(connection, LEND_KINDS);
                long added = addThePlacesOnlyTheMapHas(connection);
                PoiImporter.fillSuburbs(connection);
                long fromOverture = takeWhatOvertureSays(connection);
                long withAKind = count(connection, COUNT_PLACES_WITH_A_KIND);
                connection.commit();

                log.info("{} places have a kind, in {} seconds: {} from a like place on the map, {}"
                                + " added from the map, {} from Overture's category.",
                        withAKind, (System.currentTimeMillis() - startedAt) / 1000,
                        lent, added, fromOverture);
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

    private void listOverturePlaces(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LIST_OVERTURE_PLACES)) {
            statement.setDouble(1, leastConfidence);
            statement.executeUpdate();
        }
    }

    private static void match(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MATCH)) {
            statement.setInt(1, LARGE_PLACE_METRES);
            statement.setInt(2, SHOP_METRES);
            statement.setDouble(3, LARGE_PLACE_LIKENESS);
            statement.executeUpdate();
        }
    }

    private static long addThePlacesOnlyTheMapHas(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ADD_THE_PLACES_ONLY_THE_MAP_HAS)) {
            statement.setDouble(1, CONFIDENCE_OF_A_PLACE_ON_THE_MAP);
            return statement.executeLargeUpdate();
        }
    }

    private long takeWhatOvertureSays(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(LIST_WHAT_OVERTURE_SAYS)) {
            statement.setDouble(1, leastConfidence);
            statement.executeUpdate();
        }
        execute(connection, INDEX_WHAT_OVERTURE_SAYS);
        execute(connection, LIST_WHAT_HAS_A_KIND);
        execute(connection, INDEX_WHAT_HAS_A_KIND);
        execute(connection, READ_THE_LISTS_OF_KINDS);
        try (PreparedStatement statement = connection.prepareStatement(TAKE_WHAT_OVERTURE_SAYS)) {
            statement.setInt(1, SHOP_METRES);
            statement.setInt(2, SHOP_METRES);
            return statement.executeLargeUpdate();
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

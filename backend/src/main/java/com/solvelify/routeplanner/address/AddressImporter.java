package com.solvelify.routeplanner.address;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Loads the LINZ address export into PostgreSQL. Runs only when asked, with
 * {@code --routeplanner.addresses.import-enabled=true}, because it is a one-off.
 *
 * <p>2.4 million rows, so this uses PostgreSQL's COPY rather than inserting through JPA. COPY
 * streams the file in one pass and finishes in seconds; row-by-row inserts would take hours.
 *
 * <p>The file is read into a temporary table shaped exactly like the CSV, then filtered into the
 * real table. That keeps LINZ's 26 columns out of the application's schema, and means a bad file
 * fails before touching existing data.
 */
@Component
@ConditionalOnProperty(name = "routeplanner.addresses.import-enabled", havingValue = "true")
class AddressImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AddressImporter.class);

    /** Columns in the order LINZ writes them, all text: the CSV is parsed, not trusted. */
    private static final String CREATE_STAGING = """
            CREATE TEMPORARY TABLE linz_import (
                wkt text, address_id text, road_id text, full_address_number text,
                full_road_name text, full_address text, territorial_authority text, unit text,
                address_number text, address_number_suffix text, address_number_high text,
                road_name text, road_name_type text, road_name_suffix text, suburb_locality text,
                town_city text, is_land text, address_lifecycle text, full_road_name_ascii text,
                full_address_ascii text, territorial_authority_ascii text, road_name_ascii text,
                suburb_locality_ascii text, town_city_ascii text, shape_x text, shape_y text
            ) ON COMMIT DROP
            """;

    private static final String COPY_IN =
            "COPY linz_import FROM STDIN WITH (FORMAT csv, HEADER true)";

    /**
     * Retired addresses and offshore points would only clutter the suggestions, so they are
     * dropped here rather than filtered on every search.
     */
    private static final String INSERT_ADDRESSES = """
            INSERT INTO address (id, full_address, suburb, town_city, latitude, longitude)
            SELECT address_id::bigint,
                   full_address,
                   nullif(suburb_locality, ''),
                   nullif(town_city, ''),
                   shape_y::double precision,
                   shape_x::double precision
            FROM linz_import
            WHERE address_lifecycle = 'Current'
              AND is_land = 'T'
              AND shape_x <> '' AND shape_y <> ''
            ON CONFLICT (id) DO UPDATE SET
                full_address = excluded.full_address,
                suburb = excluded.suburb,
                town_city = excluded.town_city,
                latitude = excluded.latitude,
                longitude = excluded.longitude
            """;

    private final DataSource dataSource;
    private final AddressProperties properties;

    AddressImporter(DataSource dataSource, AddressProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path csv = Path.of(properties.csv());
        if (!Files.exists(csv)) {
            throw new IllegalStateException("Address CSV not found at " + csv.toAbsolutePath());
        }

        log.info("Importing addresses from {}", csv.toAbsolutePath());
        long startedAt = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection()) {
            // One connection, one transaction: the temporary table has to outlive the COPY and
            // still be visible to the INSERT that reads from it.
            connection.setAutoCommit(false);

            try (Statement statement = connection.createStatement()) {
                statement.execute(CREATE_STAGING);
            }

            long copied = copyInto(connection, csv);
            long inserted = insertAddresses(connection);
            connection.commit();

            log.info("Imported {} of {} rows in {} seconds. Skipped rows were retired addresses,"
                            + " offshore points or rows with no coordinates.",
                    inserted, copied, (System.currentTimeMillis() - startedAt) / 1000);
        }
    }

    private long copyInto(Connection connection, Path csv) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            return connection.unwrap(PGConnection.class).getCopyAPI().copyIn(COPY_IN, reader);
        }
    }

    private long insertAddresses(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            return statement.executeUpdate(INSERT_ADDRESSES);
        }
    }
}

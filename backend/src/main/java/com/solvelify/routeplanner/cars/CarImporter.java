package com.solvelify.routeplanner.cars;

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
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Loads the petrol cars of NZTA's Motor Vehicle Register into PostgreSQL. Runs only when asked,
 * with {@code --routeplanner.cars.import-enabled=true}.
 *
 * <p>NZTA updates the register every month. Each import replaces the whole table with what the
 * file holds, since a car is described by its make, model, year, engine and origin and has no
 * id of its own to update it by. A saved vehicle keeps its own copy of that description, so it
 * is not touched.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "routeplanner.cars.import-enabled", havingValue = "true")
class CarImporter implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CarImporter.class);

    /** Shaped exactly like the file, all text: the CSV is parsed, not trusted. */
    private static final String CREATE_STAGING = """
            CREATE TEMPORARY TABLE car_import (
                make text, model text, year text, engine_litres text, origin text, cars text,
                litres_per_100_km text, urban_litres_per_100_km text,
                extra_urban_litres_per_100_km text, gross_mass_kg text, seats text
            ) ON COMMIT DROP
            """;

    /** MATCH makes PostgreSQL check the header against those column names, in that order. */
    private static final String COPY_IN = "COPY car_import FROM STDIN WITH (FORMAT csv, HEADER MATCH)";

    private static final String REMOVE_THE_CARS_THERE_WERE = "DELETE FROM car_variant";

    /** The register says NEW for a car sold new here and USED for a used import. Anything else is refused. */
    private static final String ADD_THE_CARS = """
            INSERT INTO car_variant (make, model, year, engine_litres, imported_used, cars,
                                     litres_per_100_km, urban_litres_per_100_km,
                                     extra_urban_litres_per_100_km, gross_mass_kg, seats)
            SELECT make,
                   model,
                   year::smallint,
                   engine_litres::numeric(3, 1),
                   CASE origin WHEN 'NEW' THEN false WHEN 'USED' THEN true END,
                   cars::integer,
                   litres_per_100_km::double precision,
                   urban_litres_per_100_km::double precision,
                   extra_urban_litres_per_100_km::double precision,
                   gross_mass_kg::integer,
                   seats::smallint
            FROM car_import
            """;

    private final DataSource dataSource;
    private final CarProperties properties;

    CarImporter(DataSource dataSource, CarProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        importFrom(Path.of(properties.csv()));
    }

    void importFrom(Path csv) throws Exception {
        if (!Files.exists(csv)) {
            throw new IllegalStateException("Cars CSV not found at " + csv.toAbsolutePath()
                    + ". Create it with: scripts/export-nzta-cars.sh");
        }

        log.info("Importing cars from {}", csv.toAbsolutePath());
        long startedAt = System.currentTimeMillis();

        try (Connection connection = dataSource.getConnection()) {
            // One transaction, so a failure part way through leaves the cars there were, and
            // nobody finding a car meanwhile sees the table half full.
            connection.setAutoCommit(false);
            try {
                execute(connection, CREATE_STAGING);

                long read = copyInto(connection, csv);
                if (read == 0) {
                    // Carrying on would leave no car to find.
                    throw new IllegalStateException("Cars CSV at " + csv.toAbsolutePath()
                            + " has no rows. Nothing was changed.");
                }

                long removed = update(connection, REMOVE_THE_CARS_THERE_WERE);
                long stored = update(connection, ADD_THE_CARS);
                connection.commit();

                log.info("Imported {} makes, models, years and engines in {} seconds, in place of {}.",
                        stored, (System.currentTimeMillis() - startedAt) / 1000, removed);
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
}

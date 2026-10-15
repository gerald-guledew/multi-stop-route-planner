package com.solvelify.routeplanner.cars;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The import against a real PostgreSQL, with small files written by each test, so it runs
 * anywhere without the export.
 *
 * <p>Not transactional. The importer opens its own connection and commits, so a transaction
 * around the test could neither see nor undo its work. Each test empties the table instead.
 */
@SpringBootTest
class CarImporterTest {

    private static final String HEADER = "make,model,year,engine_litres,origin,cars,litres_per_100_km,"
            + "urban_litres_per_100_km,extra_urban_litres_per_100_km,gross_mass_kg,seats\n";

    // Rows as the export writes them, figures as the register gives them.
    private static final String SWIFT_NEW = "\"SUZUKI\",\"SWIFT\",2018,1.2,\"NEW\",1344,4.8,,,1365,5\n";
    private static final String SWIFT_USED = "\"SUZUKI\",\"SWIFT\",2018,1.2,\"USED\",552,4.2,5.8,4.2,1165,5\n";
    private static final String COROLLA = "\"TOYOTA\",\"COROLLA\",2020,2,\"NEW\",711,6,7.5,5.1,1845,5\n";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @TempDir
    private Path folder;

    private CarImporter importer;

    @BeforeEach
    void startClean() {
        emptyTheTable();
        importer = new CarImporter(dataSource, new CarProperties(false, null));
    }

    @AfterEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM car_variant").update();
    }

    @Test
    void loadsEveryCarInTheFile() throws Exception {
        importer.importFrom(fileWith(SWIFT_NEW, SWIFT_USED, COROLLA));

        assertThat(carCount()).isEqualTo(3);
        assertThat(jdbc.sql("""
                        SELECT make || '|' || model || '|' || year || '|' || engine_litres || '|' || imported_used
                               || '|' || cars || '|' || litres_per_100_km || '|' || urban_litres_per_100_km
                               || '|' || extra_urban_litres_per_100_km || '|' || gross_mass_kg || '|' || seats
                        FROM car_variant WHERE make = 'TOYOTA'
                        """).query(String.class).single())
                .isEqualTo("TOYOTA|COROLLA|2020|2.0|false|711|6|7.5|5.1|1845|5");
    }

    @Test
    void tellsACarSoldNewFromAUsedImport() throws Exception {
        importer.importFrom(fileWith(SWIFT_NEW, SWIFT_USED));

        assertThat(jdbc.sql("SELECT litres_per_100_km FROM car_variant WHERE imported_used")
                .query(Double.class).single()).isEqualTo(4.2);
        assertThat(jdbc.sql("SELECT litres_per_100_km FROM car_variant WHERE NOT imported_used")
                .query(Double.class).single()).isEqualTo(4.8);
    }

    @Test
    void storesAFigureTheRegisterDoesNotHaveAsMissing() throws Exception {
        importer.importFrom(fileWith(SWIFT_NEW));

        assertThat(jdbc.sql("SELECT urban_litres_per_100_km IS NULL AND extra_urban_litres_per_100_km IS NULL"
                + " FROM car_variant").query(Boolean.class).single()).isTrue();
    }

    @Test
    void replacesTheCarsThereWere() throws Exception {
        importer.importFrom(fileWith(SWIFT_NEW, SWIFT_USED));

        importer.importFrom(fileWith(COROLLA));

        assertThat(carCount()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT model FROM car_variant").query(String.class).single()).isEqualTo("COROLLA");
    }

    @Test
    void refusesAFileWithNoRowsAndChangesNothing() throws Exception {
        importer.importFrom(fileWith(SWIFT_NEW, SWIFT_USED));

        // A failed export looks like this. Carrying on would leave no car to find.
        assertThatThrownBy(() -> importer.importFrom(fileWith()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no rows");
        assertThat(carCount()).isEqualTo(2);
    }

    @Test
    void refusesAnOriginOtherThanNewOrUsedAndChangesNothing() throws Exception {
        importer.importFrom(fileWith(SWIFT_NEW));

        assertThatThrownBy(() -> importer.importFrom(fileWith(SWIFT_USED.replace("USED", "RE-REG"))))
                .hasMessageContaining("imported_used");
        assertThat(carCount()).isEqualTo(1);
    }

    @Test
    void refusesAFileWithColumnsInTheWrongOrder() throws Exception {
        // Every row would load, with the urban and the combined figures swapped.
        Path swapped = folder.resolve("swapped.csv");
        Files.writeString(swapped, HEADER.replace("cars,litres_per_100_km,urban_litres_per_100_km",
                "cars,urban_litres_per_100_km,litres_per_100_km") + SWIFT_USED);

        assertThatThrownBy(() -> importer.importFrom(swapped)).hasMessageContaining("header line");
        assertThat(carCount()).isZero();
    }

    @Test
    void saysHowToCreateTheFileWhenItIsMissing() {
        assertThatThrownBy(() -> importer.importFrom(folder.resolve("not-there.csv")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("export-nzta-cars.sh");
    }

    private Path fileWith(String... rows) throws Exception {
        Path csv = folder.resolve("cars.csv");
        Files.writeString(csv, HEADER + String.join("", rows));
        return csv;
    }

    private long carCount() {
        return jdbc.sql("SELECT count(*) FROM car_variant").query(Long.class).single();
    }
}

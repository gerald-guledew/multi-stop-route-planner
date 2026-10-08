package com.solvelify.routeplanner.poi;

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
 * anywhere without the 28 MB export.
 *
 * <p>Not transactional, unlike the search tests. The importer opens its own connection and
 * commits, so a transaction around the test could neither see nor undo its work. Each test
 * removes what it committed instead.
 */
@SpringBootTest
class PoiImporterTest {

    private static final String HEADER =
            "id,name,address,locality,category,confidence,latitude,longitude\n";

    private static final String NEW_WORLD = "a1ed9e57-ce67-4d7e-958d-4c2b49c73e44,"
            + "New World Remuera,10 Clonbern Rd,Auckland,food_and_beverage_store,0.99997,"
            + "-36.88169017,174.79746689\n";
    private static final String HOTEL = "0000d793-310d-4f24-984b-62860f97ae56,"
            + "Hotel DeBrett,2 High St,Auckland,hotel,0.9976722824573517,-36.84687597,174.76691111\n";
    private static final String SURFAB = "00002b56-ee68-4c08-ac15-461a8b0813d6,"
            + "Surfab,\"Silvester Complex, 144 Moorhouse Ave\",Christchurch,"
            + "building_or_construction_service,0.27,-43.54015981,172.62718399\n";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @TempDir
    private Path folder;

    private PoiImporter importer;

    @BeforeEach
    void startClean() {
        removeWhatTheImportCommitted();
        importer = new PoiImporter(dataSource, new PoiProperties(false, null));
    }

    @AfterEach
    void removeWhatTheImportCommitted() {
        jdbc.sql("DELETE FROM poi WHERE source IN ('overture', 'test')").update();
        jdbc.sql("DELETE FROM address WHERE id >= 990000000").update();
    }

    @Test
    void loadsEveryPlaceInTheFile() throws Exception {
        importer.importFrom(fileWith(NEW_WORLD, HOTEL, SURFAB));

        assertThat(placeCount()).isEqualTo(3);
        assertThat(jdbc.sql("""
                        SELECT name || '|' || address || '|' || locality || '|' || category
                               || '|' || source || '|' || latitude || '|' || longitude
                        FROM poi WHERE id = 'a1ed9e57-ce67-4d7e-958d-4c2b49c73e44'
                        """).query(String.class).single())
                .isEqualTo("New World Remuera|10 Clonbern Rd|Auckland|food_and_beverage_store"
                        + "|overture|-36.88169017|174.79746689");
    }

    @Test
    void keepsACommaInsideAQuotedFieldTogether() throws Exception {
        importer.importFrom(fileWith(SURFAB));

        assertThat(valueOf("address", "Surfab")).isEqualTo("Silvester Complex, 144 Moorhouse Ave");
    }

    @Test
    void storesAnEmptyFieldAsMissing() throws Exception {
        String noAddressNoCategory = "0f000000-0000-4000-8000-000000000001,"
                + "Baby Sensory Auckland,,Auckland,,0.26,-36.85,174.76\n";

        importer.importFrom(fileWith(noAddressNoCategory));

        assertThat(valueOf("address", "Baby Sensory Auckland")).isNull();
        assertThat(valueOf("category", "Baby Sensory Auckland")).isNull();
    }

    @Test
    void updatesAPlaceOnTheNextImportInsteadOfDuplicatingIt() throws Exception {
        importer.importFrom(fileWith(NEW_WORLD, HOTEL));

        // Same id, new name: what a rebrand looks like in the next release.
        importer.importFrom(fileWith(NEW_WORLD.replace("New World Remuera", "New World Metro Remuera"), HOTEL));

        assertThat(placeCount()).isEqualTo(2);
        assertThat(valueOf("name", "New World Metro Remuera")).isEqualTo("New World Metro Remuera");
    }

    @Test
    void removesAPlaceThatIsNoLongerInTheFile() throws Exception {
        importer.importFrom(fileWith(NEW_WORLD, HOTEL));

        importer.importFrom(fileWith(NEW_WORLD));

        assertThat(placeCount()).isEqualTo(1);
        assertThat(valueOf("name", "Hotel DeBrett")).isNull();
    }

    @Test
    void leavesPlacesFromAnotherSourceAlone() throws Exception {
        jdbc.sql("""
                INSERT INTO poi (id, source, name, confidence, latitude, longitude)
                VALUES ('test-1', 'test', 'Somewhere Else Entirely', 1, -36.9, 174.8)
                """).update();

        importer.importFrom(fileWith(NEW_WORLD));

        assertThat(valueOf("name", "Somewhere Else Entirely")).isEqualTo("Somewhere Else Entirely");
    }

    @Test
    void takesTheSuburbFromTheNearestAddress() throws Exception {
        insertAddress(990000001, "8 Clonbern Road, Remuera, Auckland", "Remuera", -36.8816, 174.7973);
        insertAddress(990000002, "1 Queen Street, Auckland Central, Auckland", "Auckland Central", -36.8440, 174.7670);

        importer.importFrom(fileWith(NEW_WORLD));

        assertThat(valueOf("suburb", "New World Remuera")).isEqualTo("Remuera");
    }

    @Test
    void leavesTheSuburbEmptyWhenNoAddressIsNear() throws Exception {
        // Four kilometres away: close on a map of the country, but not this place's suburb.
        insertAddress(990000002, "1 Queen Street, Auckland Central, Auckland", "Auckland Central", -36.8440, 174.7670);

        importer.importFrom(fileWith(NEW_WORLD));

        assertThat(valueOf("suburb", "New World Remuera")).isNull();
    }

    @Test
    void findsANewSuburbWhenAPlaceMoves() throws Exception {
        insertAddress(990000001, "8 Clonbern Road, Remuera, Auckland", "Remuera", -36.8816, 174.7973);
        insertAddress(990000002, "1 Queen Street, Auckland Central, Auckland", "Auckland Central", -36.8440, 174.7670);
        importer.importFrom(fileWith(NEW_WORLD));

        importer.importFrom(fileWith(NEW_WORLD.replace("-36.88169017,174.79746689", "-36.8441,174.7671")));

        assertThat(valueOf("suburb", "New World Remuera")).isEqualTo("Auckland Central");
    }

    @Test
    void buildsTheSearchTextFromNameAddressSuburbAndLocality() throws Exception {
        insertAddress(990000001, "8 Clonbern Road, Remuera, Auckland", "Remuera", -36.8816, 174.7973);

        importer.importFrom(fileWith(NEW_WORLD));

        assertThat(valueOf("search_text", "New World Remuera"))
                .isEqualTo("New World Remuera 10 Clonbern Rd Remuera Auckland");
    }

    @Test
    void dropsAccentsAndApostrophesFromTheSearchText() throws Exception {
        String accented = "0f000000-0000-4000-8000-000000000002,"
                + "Café Māngere & Tony's,1 Bader Dr,Auckland,cafe,0.9,-36.9690,174.7990\n";

        importer.importFrom(fileWith(accented));

        assertThat(valueOf("search_text", "Café Māngere & Tony's"))
                .startsWith("Cafe Mangere & Tonys 1 Bader Dr");
    }

    @Test
    void refusesAFileWithNoRowsAndChangesNothing() throws Exception {
        importer.importFrom(fileWith(NEW_WORLD, HOTEL));

        // A failed download looks like this. Treating it as "everything closed" would empty the table.
        assertThatThrownBy(() -> importer.importFrom(fileWith()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no rows");
        assertThat(placeCount()).isEqualTo(2);
    }

    @Test
    void refusesAFileWithColumnsMissing() throws Exception {
        Path tooFewColumns = folder.resolve("wrong.csv");
        Files.writeString(tooFewColumns, "id,name,latitude,longitude\nx,Somewhere,-36.9,174.8\n");

        assertThatThrownBy(() -> importer.importFrom(tooFewColumns)).hasMessageContaining("header line");
        assertThat(placeCount()).isZero();
    }

    @Test
    void refusesAFileWithColumnsInTheWrongOrder() throws Exception {
        // The dangerous one: every row would load, with each place somewhere near Antarctica.
        Path swapped = folder.resolve("swapped.csv");
        Files.writeString(swapped, "id,name,address,locality,category,confidence,longitude,latitude\n"
                + "x,Somewhere,1 Some St,Auckland,cafe,0.9,174.8,-36.9\n");

        assertThatThrownBy(() -> importer.importFrom(swapped)).hasMessageContaining("header line");
        assertThat(placeCount()).isZero();
    }

    @Test
    void saysHowToCreateTheFileWhenItIsMissing() {
        assertThatThrownBy(() -> importer.importFrom(folder.resolve("not-there.csv")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("export-overture-places.sql");
    }

    private Path fileWith(String... rows) throws Exception {
        Path csv = folder.resolve("places.csv");
        Files.writeString(csv, HEADER + String.join("", rows));
        return csv;
    }

    private long placeCount() {
        return jdbc.sql("SELECT count(*) FROM poi WHERE source = 'overture'").query(Long.class).single();
    }

    /** One column of the place with that name, or null when the place or the value is missing. */
    private String valueOf(String column, String placeName) {
        return jdbc.sql("SELECT " + column + " FROM poi WHERE name = ?")
                .param(placeName)
                .query((row, index) -> row.getString(1))
                .optional()
                .orElse(null);
    }

    private void insertAddress(long id, String fullAddress, String suburb, double latitude, double longitude) {
        jdbc.sql("""
                        INSERT INTO address (id, full_address, suburb, town_city, latitude, longitude)
                        VALUES (?, ?, ?, 'Auckland', ?, ?)
                        """)
                .params(id, fullAddress, suburb, latitude, longitude)
                .update();
    }
}

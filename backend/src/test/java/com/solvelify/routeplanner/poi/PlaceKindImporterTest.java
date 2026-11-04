package com.solvelify.routeplanner.poi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solvelify.routeplanner.search.SearchProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Making one list of two sources, against a real PostgreSQL, with places each test states.
 *
 * <p>Not transactional, as with the Overture import. The importer opens its own connection and
 * commits, so each test removes what it committed instead.
 */
@SpringBootTest
class PlaceKindImporterTest {

    private static final String HEADER = "id,name,place_kind,latitude,longitude\n";

    /** About 40 m apart, and nowhere near anything else in these tests. */
    private static final double LATITUDE = -36.88169;
    private static final double LONGITUDE = 174.79746;
    private static final double A_LITTLE_NORTH = LATITUDE + 0.00036;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private SearchProperties searchProperties;

    @TempDir
    private Path folder;

    private PlaceKindImporter importer;

    @BeforeEach
    void startClean() {
        removeWhatTheImportCommitted();
        importer = new PlaceKindImporter(dataSource, new PoiProperties(false, null, null, null), searchProperties);
    }

    @AfterEach
    void removeWhatTheImportCommitted() {
        jdbc.sql("DELETE FROM poi WHERE source IN ('overture', 'osm')").update();
    }

    @Test
    void givesAPlaceOnTheMapsKindToOverturesPlaceOfALikeNameBesideIt() throws Exception {
        overture("nw", "New World Remuera", "food_and_beverage_store", 0.99, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("way/1,New World,groceries," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly("nw|overture|New World Remuera|groceries");
    }

    @Test
    void addsAPlaceOnlyTheMapHas() throws Exception {
        importer.importFrom(fileWith("way/2,Mission Bay Beach,beach,-36.8465,174.8320"));

        assertThat(kinds()).containsExactly("way/2|osm|Mission Bay Beach|beach");
    }

    @Test
    void keepsAShopAcrossTheRoadWithAnotherNameApart() throws Exception {
        overture("bp", "BP Remuera", "gas_station", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("node/3,Z Remuera,fuel," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly("bp|overture|BP Remuera|fuel", "node/3|osm|Z Remuera|fuel");
    }

    @Test
    void readsTheWordsOfANameWhateverTheirCaseAndAccents() throws Exception {
        overture("one", "PAK'nSAVE Māngere", "food_and_beverage_store", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("way/4,Paknsave Mangere,groceries," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly("one|overture|PAK'nSAVE Māngere|groceries");
    }

    @Test
    void doesNotCountTheAsTheWordANameStartsWith() throws Exception {
        overture("club", "The Coffee Club", "food_and_beverage_store", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("node/5,The Corner Cafe,cafe," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly("club|overture|The Coffee Club|null", "node/5|osm|The Corner Cafe|cafe");
    }

    @Test
    void givesOneShopListedTwiceItsKindOnce() throws Exception {
        overture("first", "Z Parnell", "gas_station", 0.9, LATITUDE, LONGITUDE);
        overture("second", "Z Energy Parnell", "gas_station", 0.8, LATITUDE, LONGITUDE + 0.0002);

        importer.importFrom(fileWith("way/6,Z Parnell,fuel," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly(
                "first|overture|Z Parnell|fuel",
                "second|overture|Z Energy Parnell|null");
    }

    @Test
    void takesTheKindFromOverturesCategoryWhereTheMapHasNoSuchPlace() throws Exception {
        overture("caltex", "Caltex Remuera", "gas_station", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("way/7,Mission Bay Beach,beach,-36.8465,174.8320"));

        assertThat(kinds()).containsExactly(
                "caltex|overture|Caltex Remuera|fuel",
                "way/7|osm|Mission Bay Beach|beach");
    }

    @Test
    void keepsTheSurerOfTwoListingsOfOneShopOnlyOvertureHas() throws Exception {
        overture("sure", "Caltex Remuera", "gas_station", 0.9, LATITUDE, LONGITUDE);
        overture("unsure", "Caltex Remuera Service Station", "fueling_station", 0.5, A_LITTLE_NORTH, LONGITUDE);

        importer.importFrom(fileWith("way/8,Mission Bay Beach,beach,-36.8465,174.8320"));

        assertThat(kinds()).containsExactly(
                "sure|overture|Caltex Remuera|fuel",
                "unsure|overture|Caltex Remuera Service Station|null",
                "way/8|osm|Mission Bay Beach|beach");
    }

    @Test
    void keepsOutACategoryOvertureFilesWrongly() throws Exception {
        overture("handyman", "Handyman West Coast", "zoo", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("way/9,Mission Bay Beach,beach,-36.8465,174.8320"));

        assertThat(kinds()).containsExactly(
                "handyman|overture|Handyman West Coast|null",
                "way/9|osm|Mission Bay Beach|beach");
    }

    @Test
    void givesAMallsKindToTheMallAndNotToAShopInIt() throws Exception {
        overture("shop", "Charlotte Tilbury Sylvia Park", "fashion_and_apparel_store", 0.9, LATITUDE, LONGITUDE);
        overture("chemist", "Sylvia Park Pharmacy", "pharmacy", 0.9, A_LITTLE_NORTH, LONGITUDE);
        overture("mall", "Sylvia Park", "shopping_mall", 0.9, LATITUDE - 0.002, LONGITUDE);

        importer.importFrom(fileWith("way/20,Sylvia Park,mall," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly(
                "chemist|overture|Sylvia Park Pharmacy|null",
                "mall|overture|Sylvia Park|mall",
                "shop|overture|Charlotte Tilbury Sylvia Park|null");
    }

    @Test
    void givesAShopsKindToTheLikestNameBesideItAndNotTheNearest() throws Exception {
        overture("chemist", "Z Remuera Car Wash Kiosk Forecourt", "automotive_service", 0.9, A_LITTLE_NORTH, LONGITUDE);
        overture("fuel", "Z Remuera", "gas_station", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("node/21,Z Remuera,fuel," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly(
                "chemist|overture|Z Remuera Car Wash Kiosk Forecourt|null",
                "fuel|overture|Z Remuera|fuel");
    }

    @Test
    void keepsAToiletBlockNamedForItsBeachFromTakingTheBeach() throws Exception {
        overture("beach", "Kohimarama Beach", "beach", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith(
                "node/22,Kohimarama Beach,toilets," + A_LITTLE_NORTH + "," + LONGITUDE,
                "way/23,Kohimarama Beach,beach," + LATITUDE + "," + (LONGITUDE + 0.002)));

        assertThat(kinds()).containsExactly(
                "beach|overture|Kohimarama Beach|beach",
                "node/22|osm|Kohimarama Beach|toilets");
    }

    @Test
    void keepsACafeInAParkFromTakingThePark() throws Exception {
        overture("cafe", "Cornwall Park Cafe", "cafe", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("way/10,Cornwall Park,park," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly(
                "cafe|overture|Cornwall Park Cafe|cafe",
                "way/10|osm|Cornwall Park|park");
    }

    @Test
    void matchesNoLookoutToAPlaceOfTheSameName() throws Exception {
        overture("cafe", "Lookout Cafe", "cafe", 0.9, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("node/11,Lookout,lookout," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly(
                "cafe|overture|Lookout Cafe|cafe",
                "node/11|osm|Lookout|lookout");
    }

    @Test
    void matchesNoPlaceThatSearchHides() throws Exception {
        overture("doubtful", "Z Remuera", "gas_station", 0.1, LATITUDE, LONGITUDE);

        importer.importFrom(fileWith("node/12,Z Remuera,fuel," + A_LITTLE_NORTH + "," + LONGITUDE));

        assertThat(kinds()).containsExactly(
                "doubtful|overture|Z Remuera|null",
                "node/12|osm|Z Remuera|fuel");
    }

    @Test
    void givesAPlaceOnTheMapItsSuburbFromTheNearestAddress() throws Exception {
        jdbc.sql("""
                INSERT INTO address (id, full_address, suburb, town_city, latitude, longitude)
                VALUES (990000001, '1 Tamaki Drive', 'Mission Bay', 'Auckland', -36.8466, 174.8321)
                """).update();
        try {
            importer.importFrom(fileWith("way/13,Mission Bay Beach,beach,-36.8465,174.8320"));

            assertThat(jdbc.sql("SELECT suburb FROM poi WHERE id = 'way/13'").query(String.class).single())
                    .isEqualTo("Mission Bay");
        } finally {
            jdbc.sql("DELETE FROM address WHERE id >= 990000000").update();
        }
    }

    @Test
    void comesOutTheSameWhenRunAgain() throws Exception {
        overture("nw", "New World Remuera", "food_and_beverage_store", 0.99, LATITUDE, LONGITUDE);
        Path file = fileWith(
                "way/1,New World,groceries," + A_LITTLE_NORTH + "," + LONGITUDE,
                "way/2,Mission Bay Beach,beach,-36.8465,174.8320");

        importer.importFrom(file);
        List<String> first = kinds();
        importer.importFrom(file);

        assertThat(kinds()).isEqualTo(first);
    }

    @Test
    void forgetsWhatTheFileNoLongerSays() throws Exception {
        overture("nw", "New World Remuera", "food_and_beverage_store", 0.99, LATITUDE, LONGITUDE);
        importer.importFrom(fileWith(
                "way/1,New World,groceries," + A_LITTLE_NORTH + "," + LONGITUDE,
                "way/2,Mission Bay Beach,beach,-36.8465,174.8320"));

        importer.importFrom(fileWith("way/3,Piha Beach,beach,-36.9540,174.4670"));

        assertThat(kinds()).containsExactly(
                "nw|overture|New World Remuera|null",
                "way/3|osm|Piha Beach|beach");
    }

    @Test
    void refusesAFileWithNoPlacesAndChangesNothing() throws Exception {
        importer.importFrom(fileWith("way/2,Mission Bay Beach,beach,-36.8465,174.8320"));
        Path empty = Files.writeString(folder.resolve("empty.csv"), HEADER);

        assertThatThrownBy(() -> importer.importFrom(empty))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no rows");

        assertThat(kinds()).containsExactly("way/2|osm|Mission Bay Beach|beach");
    }

    @Test
    void saysHowToMakeTheFileWhenItIsMissing() {
        assertThatThrownBy(() -> importer.importFrom(folder.resolve("missing.csv")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("export-osm-places.sql");
    }

    private void overture(String id, String name, String category, double confidence,
                          double latitude, double longitude) {
        jdbc.sql("""
                        INSERT INTO poi (id, source, name, category, confidence, latitude, longitude)
                        VALUES (:id, 'overture', :name, :category, :confidence, :latitude, :longitude)
                        """)
                .param("id", id)
                .param("name", name)
                .param("category", category)
                .param("confidence", confidence)
                .param("latitude", latitude)
                .param("longitude", longitude)
                .update();
    }

    private List<String> kinds() {
        return jdbc.sql("""
                        SELECT id || '|' || source || '|' || name || '|' || coalesce(place_kind, 'null')
                        FROM poi
                        ORDER BY source DESC, id
                        """)
                .query(String.class)
                .list();
    }

    private Path fileWith(String... rows) throws Exception {
        return Files.writeString(
                folder.resolve("places-" + System.nanoTime() + ".csv"),
                HEADER + String.join("\n", rows) + "\n");
    }
}

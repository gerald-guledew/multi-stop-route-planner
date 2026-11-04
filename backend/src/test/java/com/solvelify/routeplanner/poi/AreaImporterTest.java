package com.solvelify.routeplanner.poi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Loading the suburbs and towns, against a real PostgreSQL, with areas and addresses each test
 * states.
 *
 * <p>Not transactional, as with the other imports. The importer opens its own connection and
 * commits, so each test removes what it committed instead.
 */
@SpringBootTest
class AreaImporterTest {

    private static final String HEADER = "id,name,place,latitude,longitude\n";

    /** Above the ids of anything else the tests put in the address table. */
    private static final long FIRST_ADDRESS = 9_000_001;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcClient jdbc;

    @TempDir
    private Path folder;

    private AreaImporter importer;

    private long nextAddress = FIRST_ADDRESS;

    @BeforeEach
    void startClean() {
        removeWhatTheTestsCommitted();
        importer = new AreaImporter(dataSource, new PoiProperties(false, null, null, null));
    }

    @AfterEach
    void removeWhatTheTestsCommitted() {
        jdbc.sql("DELETE FROM area").update();
        jdbc.sql("DELETE FROM address WHERE id >= ?").param(FIRST_ADDRESS).update();
    }

    @Test
    void givesAnAreaTheCityOfTheNearestAddress() throws Exception {
        address("1 Ponsonby Road, Ponsonby, Auckland", "Auckland", -36.8565, 174.7465);
        address("1 Bank Street, Whangārei Central, Whangārei", "Whangārei", -35.7251, 174.3237);

        importer.importFrom(fileWith("node/1,Ponsonby,suburb,-36.8560,174.7460"));

        assertThat(areas()).containsExactly("node/1|Ponsonby|Auckland");
    }

    @Test
    void givesNoCityWhenTheNearestAddressIsOverTwoKilometresAway() throws Exception {
        // About 2.6 km south of the area.
        address("1 Dominion Road, Mount Eden, Auckland", "Auckland", -36.8800, 174.7460);

        importer.importFrom(fileWith("node/1,Ponsonby,suburb,-36.8560,174.7460"));

        assertThat(areas()).containsExactly("node/1|Ponsonby|");
    }

    @Test
    void givesNoCityWhenTheCityIsTheAreaItself() throws Exception {
        address("1 Queen Street, Warkworth", "Warkworth", -36.3995, 174.6635);

        importer.importFrom(fileWith("node/2,Warkworth,town,-36.3990,174.6630"));

        assertThat(areas()).containsExactly("node/2|Warkworth|");
    }

    @Test
    void keepsThePointOfAnAreaDrawnAsAnOutlineToo() throws Exception {
        importer.importFrom(fileWith(
                "relation/3,Ponsonby,suburb,-36.8600,174.7420",
                "node/1,Ponsonby,suburb,-36.8560,174.7460"));

        assertThat(areas()).containsExactly("node/1|Ponsonby|");
    }

    @Test
    void keepsTheLargerKindOfTwoPointsOfOneName() throws Exception {
        importer.importFrom(fileWith(
                "node/4,Riverhead,locality,-36.7610,174.5990",
                "node/5,Riverhead,hamlet,-36.7570,174.5930"));

        assertThat(areas()).containsExactly("node/5|Riverhead|");
    }

    @Test
    void keepsTwoAreasOfOneNameFarApart() throws Exception {
        importer.importFrom(fileWith(
                "node/6,Hillcrest,suburb,-36.7930,174.7380",
                "node/7,Hillcrest,suburb,-37.7930,175.3190"));

        assertThat(areas()).containsExactly("node/6|Hillcrest|", "node/7|Hillcrest|");
    }

    @Test
    void replacesWhatAnEarlierRunLoaded() throws Exception {
        importer.importFrom(fileWith("node/1,Ponsonby,suburb,-36.8560,174.7460"));
        importer.importFrom(fileWith("node/6,Hillcrest,suburb,-36.7930,174.7380"));

        assertThat(areas()).containsExactly("node/6|Hillcrest|");
    }

    @Test
    void refusesAFileWithNoRowsAndChangesNothing() throws Exception {
        importer.importFrom(fileWith("node/1,Ponsonby,suburb,-36.8560,174.7460"));

        assertThatThrownBy(() -> importer.importFrom(fileWith()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has no rows");
        assertThat(areas()).containsExactly("node/1|Ponsonby|");
    }

    @Test
    void saysHowToMakeTheFileWhenThereIsNone() {
        assertThatThrownBy(() -> importer.importFrom(folder.resolve("missing.csv")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("export-osm-areas.sql");
    }

    @Test
    void refusesAFileWithOtherColumns() throws Exception {
        Path wrong = folder.resolve("wrong.csv");
        Files.writeString(wrong, "id,name,place_kind,latitude,longitude\nnode/1,Ponsonby,suburb,-36.8560,174.7460\n");

        assertThatThrownBy(() -> importer.importFrom(wrong)).hasMessageContaining("header");
        assertThat(areas()).isEmpty();
    }

    private void address(String fullAddress, String townCity, double latitude, double longitude) {
        jdbc.sql("INSERT INTO address (id, full_address, town_city, latitude, longitude) VALUES (?, ?, ?, ?, ?)")
                .params(nextAddress++, fullAddress, townCity, latitude, longitude)
                .update();
    }

    private Path fileWith(String... rows) throws Exception {
        Path csv = Files.createTempFile(folder, "areas", ".csv");
        Files.writeString(csv, HEADER + String.join("\n", rows) + (rows.length > 0 ? "\n" : ""));
        return csv;
    }

    /** Each area as id|name|city, the city empty when there is none, in the order of their ids. */
    private List<String> areas() {
        return jdbc.sql("SELECT id || '|' || name || '|' || coalesce(city, '') FROM area ORDER BY id")
                .query(String.class)
                .list();
    }
}

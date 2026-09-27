package com.solvelify.routeplanner.address;

import static org.assertj.core.api.Assertions.assertThat;

import com.solvelify.routeplanner.planning.Location;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Search against a real PostgreSQL, because the parts being tested are PostgreSQL's: the
 * trigram index and the similarity ranking. An in-memory database would prove nothing.
 *
 * <p>Rows are inserted with SQL rather than through the entity, which has no setters on
 * purpose: nothing in the application writes addresses, only the bulk importer does.
 */
@SpringBootTest
@Transactional
class LinzAddressSearchTest {

    @Autowired
    private LinzAddressSearch addressSearch;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void insertAddresses() {
        insert(1, "20A Bassett Road, Remuera", "Remuera", "Auckland", -36.8712, 174.7866);
        insert(2, "3/90A Bassett Road, Remuera", "Remuera", "Auckland", -36.8714, 174.7869);
        insert(3, "12 Bassett Street, Invercargill", "Georgetown", "Invercargill", -46.4300, 168.3800);
        insert(4, "100 Broadway, Newmarket", "Newmarket", "Auckland", -36.8700, 174.7770);
    }

    @Test
    void findsAnAddressFromPartOfIt() {
        List<Location> found = addressSearch.search("bassett road remuera", 8);

        assertThat(found).extracting(Location::name)
                .contains("20A Bassett Road, Remuera", "3/90A Bassett Road, Remuera");
    }

    @Test
    void putsTheClosestMatchFirst() {
        List<Location> found = addressSearch.search("bassett road remuera", 8);

        assertThat(found.getFirst().name()).isEqualTo("20A Bassett Road, Remuera");
    }

    @Test
    void doesNotConfuseTheInvercargillStreetWithTheAucklandRoad() {
        List<Location> found = addressSearch.search("bassett road remuera", 8);

        assertThat(found).extracting(Location::name)
                .doesNotContain("12 Bassett Street, Invercargill");
    }

    @Test
    void carriesCoordinatesBackWithTheName() {
        Location found = addressSearch.search("100 Broadway", 1).getFirst();

        assertThat(found.latitude()).isEqualTo(-36.8700);
        assertThat(found.longitude()).isEqualTo(174.7770);
    }

    @Test
    void ignoresSearchesTooShortToMeanAnything() {
        assertThat(addressSearch.search("ba", 8)).isEmpty();
    }

    @Test
    void respectsTheLimit() {
        assertThat(addressSearch.search("bassett", 1)).hasSize(1);
    }

    private void insert(long id, String fullAddress, String suburb, String townCity,
                        double latitude, double longitude) {
        jdbc.sql("""
                        INSERT INTO address (id, full_address, suburb, town_city, latitude, longitude)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """)
                .params(id, fullAddress, suburb, townCity, latitude, longitude)
                .update();
    }
}

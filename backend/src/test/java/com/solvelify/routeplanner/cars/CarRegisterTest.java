package com.solvelify.routeplanner.cars;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finding a car against a real PostgreSQL. Each test starts from an empty table and a few cars
 * inserted with SQL, all rolled back afterwards.
 */
@SpringBootTest
@Transactional
class CarRegisterTest {

    @Autowired
    private CarRegister register;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void someCars() {
        jdbc.sql("DELETE FROM car_variant").update();
        insert("SUZUKI", "SWIFT", 2018, "1.4", false, 6.1, 951);
        insert("SUZUKI", "SWIFT", 2018, "1.2", true, 4.2, 552);
        insert("SUZUKI", "SWIFT", 2018, "1.2", false, 4.8, 1344);
        insert("SUZUKI", "SWIFT", 2018, "1.0", false, 5.1, 545);
        insert("SUZUKI", "SWIFT", 2017, "1.2", false, 4.8, 1497);
        insert("SUZUKI", "ALTO", 2015, "1.0", false, 4.9, 380);
        insert("MAZDA", "DEMIO", 2016, "1.3", true, 4.1, 2210);
    }

    @Test
    void listsEveryMakeOnceInAlphabeticalOrder() {
        assertThat(register.makes()).containsExactly("MAZDA", "SUZUKI");
    }

    @Test
    void listsTheModelsOfOneMake() {
        assertThat(register.models("SUZUKI")).containsExactly("ALTO", "SWIFT");
    }

    @Test
    void listsNoModelForAMakeItDoesNotKnow() {
        assertThat(register.models("Suzuki")).isEmpty();
    }

    @Test
    void listsTheYearsNewestFirst() {
        assertThat(register.years("SUZUKI", "SWIFT")).containsExactly(2018, 2017);
    }

    @Test
    void listsTheEnginesSmallestFirstAndSoldNewBeforeImportedUsed() {
        assertThat(register.variants("SUZUKI", "SWIFT", 2018))
                .extracting(found -> found.car().engineLitres() + (found.car().importedUsed() ? " used" : " new")
                        + " " + found.litresPer100Km() + " x" + found.cars())
                .containsExactly("1.0 new 5.1 x545", "1.2 new 4.8 x1344", "1.2 used 4.2 x552", "1.4 new 6.1 x951");
    }

    @Test
    void findsACarByItsDescription() {
        Car usedSwift = new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.2"), true);

        assertThat(register.find(usedSwift)).hasValueSatisfying(found -> {
            assertThat(found.car()).isEqualTo(usedSwift);
            assertThat(found.litresPer100Km()).isEqualTo(4.2);
        });
    }

    @Test
    void findsTheSameEngineHoweverManyNoughtsItIsWrittenWith() {
        assertThat(register.find(new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.20"), false)))
                .hasValueSatisfying(found -> assertThat(found.litresPer100Km()).isEqualTo(4.8));
    }

    @Test
    void findsNothingForACarItDoesNotList() {
        assertThat(register.find(new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.3"), false))).isEmpty();
        assertThat(register.find(new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.4"), true))).isEmpty();
    }

    private void insert(String make, String model, int year, String engineLitres, boolean importedUsed,
                        double litresPer100Km, int cars) {
        jdbc.sql("""
                        INSERT INTO car_variant (make, model, year, engine_litres, imported_used, cars, litres_per_100_km)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """)
                .params(make, model, year, new BigDecimal(engineLitres), importedUsed, cars, litresPer100Km)
                .update();
    }
}

package com.solvelify.routeplanner.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The saved vehicle against a real PostgreSQL, because the rule of one row is the table's own.
 * Each test starts by emptying the table. Only these tests write to it.
 */
@SpringBootTest
class VehiclesTest {

    @Autowired
    private Vehicles vehicles;

    @Autowired
    private VehicleRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTable() {
        repository.deleteAllInBatch();
    }

    @Test
    void hasNoVehicleUntilOneIsSaved() {
        assertThat(vehicles.current()).isEmpty();
    }

    @Test
    void keepsWhatWasSaved() {
        vehicles.save(7.5, VehicleSize.MEDIUM);

        Vehicle saved = vehicles.current().orElseThrow();
        assertThat(saved.getLitresPer100Km()).isEqualTo(7.5);
        assertThat(saved.getSize()).isEqualTo(VehicleSize.MEDIUM);
    }

    @Test
    void replacesTheVehicleThereWasRatherThanAddingASecond() {
        vehicles.save(7.5, VehicleSize.MEDIUM);
        vehicles.save(11.2, VehicleSize.LARGE);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(vehicles.current().orElseThrow().getLitresPer100Km()).isEqualTo(11.2);
        assertThat(vehicles.current().orElseThrow().getSize()).isEqualTo(VehicleSize.LARGE);
    }

    @Test
    void removesTheVehicle() {
        vehicles.save(7.5, VehicleSize.MEDIUM);

        vehicles.remove();

        assertThat(vehicles.current()).isEmpty();
    }

    @Test
    void removingWhenThereIsNoneIsNoMistake() {
        vehicles.remove();

        assertThat(vehicles.current()).isEmpty();
    }

    @Test
    void theTableRefusesASecondRowAndAFigureOutOfRange() {
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO vehicle (id, litres_per_100_km, size) VALUES (2, 7.5, 'SMALL')").update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO vehicle (litres_per_100_km, size) VALUES (45, 'SMALL')").update())
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO vehicle (litres_per_100_km, size) VALUES (7.5, 'HUGE')").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

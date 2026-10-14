package com.solvelify.routeplanner.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.solvelify.routeplanner.cars.Car;
import com.solvelify.routeplanner.cars.CarNotInRegisterException;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The saved vehicle against a real PostgreSQL, because the rule of one row is the table's own.
 * Each test starts by emptying the table, and with one car in the register.
 */
@SpringBootTest
class VehiclesTest {

    private static final Car USED_SWIFT = new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.2"), true);

    @Autowired
    private Vehicles vehicles;

    @Autowired
    private VehicleRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTableAndListOneCar() {
        repository.deleteAllInBatch();
        jdbc.sql("DELETE FROM car_variant").update();
        jdbc.sql("""
                INSERT INTO car_variant (make, model, year, engine_litres, imported_used, cars, litres_per_100_km)
                VALUES ('SUZUKI', 'SWIFT', 2018, 1.2, true, 552, 4.2)
                """).update();
    }

    @AfterEach
    void removeTheCar() {
        jdbc.sql("DELETE FROM car_variant").update();
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
    void keepsACarFoundInTheRegisterWithTheRegistersFigure() {
        vehicles.saveFromRegister(USED_SWIFT, null);

        Vehicle saved = vehicles.current().orElseThrow();
        assertThat(saved.getCar()).contains(USED_SWIFT);
        assertThat(saved.getLitresPer100Km()).isEqualTo(4.2);
        assertThat(saved.getFigureFrom()).isEqualTo(FigureFrom.REGISTER);
        assertThat(saved.getSize()).isNull();
    }

    @Test
    void keepsTheDashboardsFigureForACarFoundInTheRegister() {
        vehicles.saveFromRegister(USED_SWIFT, 5.6);

        Vehicle saved = vehicles.current().orElseThrow();
        assertThat(saved.getLitresPer100Km()).isEqualTo(5.6);
        assertThat(saved.getFigureFrom()).isEqualTo(FigureFrom.DASHBOARD);
    }

    @Test
    void refusesACarTheRegisterDoesNotListAndKeepsTheVehicleThereWas() {
        vehicles.save(7.5, VehicleSize.MEDIUM);
        Car newSwift = new Car("SUZUKI", "SWIFT", 2018, new BigDecimal("1.2"), false);

        assertThatThrownBy(() -> vehicles.saveFromRegister(newSwift, null))
                .isInstanceOf(CarNotInRegisterException.class)
                .hasMessage("NZTA's register has no 2018 SUZUKI SWIFT 1.2 L sold new in New Zealand.");
        assertThat(vehicles.current().orElseThrow().getSize()).isEqualTo(VehicleSize.MEDIUM);
    }

    @Test
    void forgetsTheCarWhenAVehicleIsEnteredByHandInItsPlace() {
        vehicles.saveFromRegister(USED_SWIFT, null);

        vehicles.save(7.5, VehicleSize.MEDIUM);

        Vehicle saved = vehicles.current().orElseThrow();
        assertThat(saved.getCar()).isEmpty();
        assertThat(saved.getFigureFrom()).isEqualTo(FigureFrom.DASHBOARD);
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

    @Test
    void theTableRefusesAVehicleThatIsBothEnteredAndFoundOrNeither() {
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO vehicle (litres_per_100_km, size, make, model, year, engine_litres, imported_used)
                VALUES (7.5, 'SMALL', 'SUZUKI', 'SWIFT', 2018, 1.2, true)
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.sql("INSERT INTO vehicle (litres_per_100_km) VALUES (7.5)").update())
                .isInstanceOf(DataIntegrityViolationException.class);
        // A car with no year is not a car the register lists.
        assertThatThrownBy(() -> jdbc.sql("""
                INSERT INTO vehicle (litres_per_100_km, make, model, engine_litres, imported_used)
                VALUES (7.5, 'SUZUKI', 'SWIFT', 1.2, true)
                """).update()).isInstanceOf(DataIntegrityViolationException.class);
        // A size is entered by hand, and only the dashboard gives a figure then.
        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO vehicle (litres_per_100_km, size, figure_from) VALUES (7.5, 'SMALL', 'REGISTER')").update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

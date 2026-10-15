package com.solvelify.routeplanner.cars;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * The petrol cars of NZTA's Motor Vehicle Register, as {@link CarImporter} loaded them. A driver
 * finds theirs one step at a time: the make, the model, the year, then the engine. Each list
 * comes in the order it is shown in.
 *
 * <p>The register tells a car sold new here from the same car imported used, and their figures
 * differ by up to 13%. A driver is not asked which theirs is, so each engine is listed once,
 * with the figure of whichever of the two has more cars: the figure most of those cars carry.
 */
@Repository
public class CarRegister {

    // One row for each engine of a model and year, counting the cars of both origins.
    private static final String ONE_ROW_AN_ENGINE = """
            SELECT DISTINCT ON (engine_litres) make, model, year, engine_litres, litres_per_100_km,
                   sum(cars) OVER (PARTITION BY engine_litres) AS all_cars
            FROM car_variant
            WHERE make = ? AND model = ? AND year = ?
            """;

    // The row kept for each engine is the first: the origin with more cars, and in a tie the
    // car sold new here.
    private static final String LARGER_ORIGIN_FIRST = """
            ORDER BY engine_litres, cars DESC, imported_used
            """;

    private final JdbcClient jdbc;

    public CarRegister(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Every make, in alphabetical order. */
    public List<String> makes() {
        return jdbc.sql("SELECT DISTINCT make FROM car_variant ORDER BY make")
                .query(String.class)
                .list();
    }

    /** The models of one make, in alphabetical order. Empty for a make the register does not list. */
    public List<String> models(String make) {
        return jdbc.sql("SELECT DISTINCT model FROM car_variant WHERE make = ? ORDER BY model")
                .param(make)
                .query(String.class)
                .list();
    }

    /** The years one model was made in, newest first, as a driver looks for a recent one. */
    public List<Integer> years(String make, String model) {
        return jdbc.sql("SELECT DISTINCT year FROM car_variant WHERE make = ? AND model = ? ORDER BY year DESC")
                .params(make, model)
                .query(Integer.class)
                .list();
    }

    /** The engines of one model and year, smallest first. */
    public List<CarVariant> variants(String make, String model, int year) {
        return jdbc.sql(ONE_ROW_AN_ENGINE + LARGER_ORIGIN_FIRST)
                .params(make, model, year)
                .query(CarRegister::variantOf)
                .list();
    }

    /** The register's figure for this car. Empty when it does not list the car. */
    public Optional<CarVariant> find(Car car) {
        return jdbc.sql(ONE_ROW_AN_ENGINE + "AND engine_litres = ?\n" + LARGER_ORIGIN_FIRST)
                .params(car.make(), car.model(), car.year(), car.engineLitres())
                .query(CarRegister::variantOf)
                .optional();
    }

    private static CarVariant variantOf(ResultSet row, int index) throws SQLException {
        Car car = new Car(row.getString("make"), row.getString("model"), row.getInt("year"),
                row.getBigDecimal("engine_litres"));
        return new CarVariant(car, row.getDouble("litres_per_100_km"), row.getInt("all_cars"));
    }
}

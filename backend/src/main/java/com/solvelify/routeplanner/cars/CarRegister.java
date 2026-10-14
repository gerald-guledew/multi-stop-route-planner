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
 */
@Repository
public class CarRegister {

    private static final String SELECT_VARIANTS = """
            SELECT make, model, year, engine_litres, imported_used, litres_per_100_km, cars
            FROM car_variant
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

    /** The engines of one model and year, smallest first, and sold new before imported used. */
    public List<CarVariant> variants(String make, String model, int year) {
        return jdbc.sql(SELECT_VARIANTS + """
                        WHERE make = ? AND model = ? AND year = ?
                        ORDER BY engine_litres, imported_used
                        """)
                .params(make, model, year)
                .query(CarRegister::variantOf)
                .list();
    }

    /** The register's figure for this car. Empty when it does not list the car. */
    public Optional<CarVariant> find(Car car) {
        return jdbc.sql(SELECT_VARIANTS + """
                        WHERE make = ? AND model = ? AND year = ? AND engine_litres = ? AND imported_used = ?
                        """)
                .params(car.make(), car.model(), car.year(), car.engineLitres(), car.importedUsed())
                .query(CarRegister::variantOf)
                .optional();
    }

    private static CarVariant variantOf(ResultSet row, int index) throws SQLException {
        Car car = new Car(row.getString("make"), row.getString("model"), row.getInt("year"),
                row.getBigDecimal("engine_litres"), row.getBoolean("imported_used"));
        return new CarVariant(car, row.getDouble("litres_per_100_km"), row.getInt("cars"));
    }
}

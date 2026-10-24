package com.solvelify.routeplanner.cars;

import com.solvelify.routeplanner.cars.CarWeights.Rated;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.stream.Collectors;
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

    /**
     * How many times the open road's fuel this car takes in town, from the register's town and
     * open-road figures, of whichever origin has more cars among those it gives both. Empty when
     * it gives this car neither, as for most cars imported used.
     */
    public OptionalDouble townOverOpenRoadOf(Car car) {
        return jdbc.sql("""
                        SELECT urban_litres_per_100_km / extra_urban_litres_per_100_km FROM car_variant
                        WHERE make = ? AND model = ? AND year = ? AND engine_litres = ?
                          AND urban_litres_per_100_km > 0 AND extra_urban_litres_per_100_km > 0
                        ORDER BY cars DESC, imported_used
                        LIMIT 1
                        """)
                .params(car.make(), car.model(), car.year(), car.engineLitres())
                .query(Double.class)
                .optional()
                .map(OptionalDouble::of)
                .orElse(OptionalDouble.empty());
    }

    /**
     * What this car weighs empty, from the gross mass the register gives it: see
     * {@link CarWeights}. When the register has no weight for most of these cars, the middle
     * weight of the cars with the same engine size. Empty when the register does not list it.
     */
    public OptionalDouble weightOf(Car car) {
        List<Rated> rows = jdbc.sql("""
                        SELECT imported_used, gross_mass_kg, seats, cars FROM car_variant
                        WHERE make = ? AND model = ? AND year = ? AND engine_litres = ?
                        """)
                .params(car.make(), car.model(), car.year(), car.engineLitres())
                .query(CarRegister::ratedOf)
                .list();
        OptionalDouble weight = CarWeights.weightOf(rows);
        if (weight.isPresent() || rows.isEmpty()) {
            return weight;
        }

        // Each make, model and year of this engine size, with its rows sold new and imported used.
        Map<String, List<Rated>> sameSize = jdbc.sql("""
                        SELECT make, model, year, imported_used, gross_mass_kg, seats, cars FROM car_variant
                        WHERE engine_litres = ?
                        """)
                .param(car.engineLitres())
                .query((row, index) -> new RatedEngine(
                        row.getString("make") + " " + row.getString("model") + " " + row.getInt("year"),
                        ratedOf(row, index)))
                .list()
                .stream()
                .collect(Collectors.groupingBy(RatedEngine::engine, LinkedHashMap::new,
                        Collectors.mapping(RatedEngine::rated, Collectors.toList())));
        return CarWeights.middleOf(List.copyOf(sameSize.values()));
    }

    private record RatedEngine(String engine, Rated rated) {
    }

    private static Rated ratedOf(ResultSet row, int index) throws SQLException {
        return new Rated(row.getBoolean("imported_used"), row.getObject("gross_mass_kg", Integer.class),
                row.getObject("seats", Integer.class), row.getInt("cars"));
    }

    private static CarVariant variantOf(ResultSet row, int index) throws SQLException {
        Car car = new Car(row.getString("make"), row.getString("model"), row.getInt("year"),
                row.getBigDecimal("engine_litres"));
        return new CarVariant(car, row.getDouble("litres_per_100_km"), row.getInt("all_cars"));
    }
}

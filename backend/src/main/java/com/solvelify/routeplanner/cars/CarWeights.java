package com.solvelify.routeplanner.cars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

/**
 * What a car weighs empty, worked out from the gross mass NZTA's register gives it.
 *
 * <p>The register has no empty weight, and its gross mass is on two bases.
 *
 * <ul>
 *   <li>A used import from Japan carries Japan's gross weight: the car and 55 kg a seat. Japan's
 *       catalogue gives a 2013 Aqua S 1,080 kg, and this gives 1,065.
 *   <li>A car sold new here carries the most its maker allows it to weigh loaded. Over the 890
 *       engines the register weighs both ways, that is the car and 36% more, in the middle. Half
 *       of those come within 5% of the import's weight. A 2019 RAV4 2.5 comes out at 1,629 kg,
 *       where its maker gives 1,650 with two-wheel drive and 1,695 with four, and a 2012 Camry
 *       2.5 at 1,540, where its maker gives 1,465.
 * </ul>
 *
 * <p>Not every used import came from Japan. When the same engine sold new here is not rated at
 * least {@link #JAPAN_IS_LIGHTER_BY_KILOGRAMS} heavier, the import is taken to carry its maker's
 * rating too: a 2015 Hyundai i30 1.8 is 1,840 kg either way.
 */
final class CarWeights {

    static final double JAPAN_KILOGRAMS_A_SEAT = 55;

    /** Japan's weight is the car and 275 kg for five seats, the maker's 490 kg in the middle. */
    static final double JAPAN_IS_LIGHTER_BY_KILOGRAMS = 200;

    static final double MAKERS_GROSS_OVER_EMPTY = 1.36;

    private static final int SEATS_WHEN_NOT_GIVEN = 5;

    private CarWeights() {
    }

    /**
     * One row of the register for a make, model, year and engine.
     *
     * @param grossKilograms nought or null when the register has none for most of these cars
     * @param seats          null when not given
     * @param cars           how many are registered
     */
    record Rated(boolean importedUsed, Integer grossKilograms, Integer seats, int cars) {

        boolean weighed() {
            return grossKilograms != null && grossKilograms > 0;
        }
    }

    /** The weight of one engine, from its rows sold new and imported used. Empty when neither is weighed. */
    static OptionalDouble weightOf(List<Rated> rows) {
        Rated soldNew = weighed(rows, false);
        Rated imported = weighed(rows, true);
        if (imported != null
                && (soldNew == null || soldNew.grossKilograms() - imported.grossKilograms() >= JAPAN_IS_LIGHTER_BY_KILOGRAMS)) {
            int seats = imported.seats() == null || imported.seats() <= 0 ? SEATS_WHEN_NOT_GIVEN : imported.seats();
            return OptionalDouble.of(imported.grossKilograms() - JAPAN_KILOGRAMS_A_SEAT * seats);
        }
        Rated makers = soldNew != null ? soldNew : imported;
        return makers == null ? OptionalDouble.empty() : OptionalDouble.of(makers.grossKilograms() / MAKERS_GROSS_OVER_EMPTY);
    }

    /**
     * The weight in the middle of many engines, counted by how many cars each has. Used for an
     * engine the register does not weigh, from the others of the same size: half of the weighed
     * engines come within 6% of what that gives them.
     */
    static OptionalDouble middleOf(List<List<Rated>> engines) {
        List<double[]> weighed = new ArrayList<>();
        int cars = 0;
        for (List<Rated> engine : engines) {
            OptionalDouble weight = weightOf(engine);
            if (weight.isPresent()) {
                int ofThisEngine = engine.stream().mapToInt(Rated::cars).sum();
                weighed.add(new double[] {weight.getAsDouble(), ofThisEngine});
                cars += ofThisEngine;
            }
        }
        weighed.sort(Comparator.comparingDouble(weight -> weight[0]));
        double counted = 0;
        for (double[] weight : weighed) {
            counted += weight[1];
            if (counted >= cars / 2.0) {
                return OptionalDouble.of(weight[0]);
            }
        }
        return OptionalDouble.empty();
    }

    private static Rated weighed(List<Rated> rows, boolean importedUsed) {
        return rows.stream()
                .filter(row -> row.importedUsed() == importedUsed && row.weighed())
                .findFirst()
                .orElse(null);
    }
}

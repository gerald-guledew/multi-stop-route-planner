package com.solvelify.routeplanner.planning;

import com.solvelify.routeplanner.planning.ClimbAndDescent.Descent;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How much fuel a vehicle uses: its figures for town roads and for the open road, and, once its
 * weight is known, what each climb costs and each descent gives back.
 *
 * <p>Town roads take more fuel for every kilometre than the open road, with their stops, starts
 * and low gears. The register gives many cars a figure for each, from the two parts of the test
 * they were approved with. A car with one figure, the dashboard's or the register's combined, is
 * given the usual split: town {@value #TOWN_OVER_OPEN_ROAD} times the open road, the two weighed
 * as that test weighs them, with 37% of its distance in town. Over the 4,888 engines the register
 * gives all three figures, that weighing gives the combined figure to within 0.5% at the middle.
 *
 * <p>A climb lifts the car and the driver. The work that takes is their weight times the height,
 * and the engine turns {@link #ENGINE_EFFICIENCY} of the extra fuel it burns into that work.
 * Checked against a published measurement: a car on a 4% climb uses 1.5 to 2 times its fuel on
 * level road. This gives 1.8 for cars from 1,000 to 1,800 kg.
 *
 * <p>A descent gives the work back, but never more than that stretch of road takes on the level
 * at the one figure: once the slope does all the pulling, the engine burns next to nothing, and
 * anything steeper is braked away. So a hill of a few percent costs on the way up what it gives
 * back on the way down, and a steeper one costs more than it gives. Published measurements on the
 * road found the same.
 *
 * <p>A dashboard figure is an average over the driving the car has done, hills included. On a
 * round trip the hills give back nearly all they cost: measured over 142 drives across Auckland,
 * they add 0.2% to the fuel, and in Wellington 3%. So the dashboard figure is read as the
 * figure for level road, split between town and the open road, and this trip's hills are counted
 * on top.
 *
 * <p>A load, passengers or cargo besides the driver, takes fuel of its own, which the car's
 * figures do not hold. On the level it takes {@link #ROLLING_RESISTANCE} of its weight to roll
 * along, and in town it is got going again after every stop, as often as the town part of the
 * approval test stops, its speed braked away each time. The engine turns the same share of that
 * fuel into work as on a climb. For 100 kg that is 0.24 L/100 km in town and 0.10 on the open
 * road, 2% more over the test for a medium car at 7.5 L/100 km. Checked against what is published:
 * about 1% for every 100 lb, the US Department of Energy's figure, which is 2.2% for 100 kg, and
 * up to 0.3 L/100 km for 100 kg, as the German automobile club is quoted. On a hill the load is lifted with
 * the car.
 *
 * @param litresPer100Km         the one figure, the dashboard's or the register's. It is what a
 *                               drive takes on the level where its roads are not known, as on a
 *                               straight line, and what a descent gives back is held to it
 * @param townLitresPer100Km     what town roads take
 * @param openRoadLitresPer100Km what the open road takes
 * @param kilograms              what the car weighs empty. Without it, hills are not counted
 * @param loadKilograms          what it carries besides the driver, nought for nothing
 */
public record FuelUse(
        double litresPer100Km,
        double townLitresPer100Km,
        double openRoadLitresPer100Km,
        OptionalDouble kilograms,
        double loadKilograms) {

    /**
     * How many times the open road's fuel a kilometre of town roads takes, for a car whose own
     * figures are not known. Over the 4,888 engines the register gives both, the middle is 1.62,
     * and counted by their cars 1.58.
     */
    static final double TOWN_OVER_OPEN_ROAD = 1.6;

    /** The share of the approval test's distance driven in town: 4.052 km of 11.007. */
    static final double TOWN_SHARE_OF_THE_TEST = 4.052 / 11.007;

    /** The driver, as European type approval counts one. */
    static final double DRIVER_KILOGRAMS = 75;

    static final double GRAVITY = 9.81;

    /** What a litre of petrol gives when burnt, counted without the heat of its steam. */
    static final double JOULES_IN_A_LITRE_OF_PETROL = 33e6;

    /**
     * The share of the extra fuel a petrol engine turns into work at the wheels, after the
     * gearbox. Such engines burn 250 to 350 g of petrol for every kWh they give, 24 to 33%.
     */
    static final double ENGINE_EFFICIENCY = 0.30;

    /**
     * The share of its weight a car's tyres take to roll on a sealed road: from 0.007 for tyres
     * made to roll easily to 0.015.
     */
    static final double ROLLING_RESISTANCE = 0.010;

    /**
     * The work each kilogram takes to get going again, for every kilometre in town. The town part
     * of the approval test pulls away from a stop to 15, 32 and 50 km/h every 1.013 km, and brakes
     * each away again: 143 J.
     */
    static final double TOWN_JOULES_PER_KILOGRAM_PER_KM =
            (squared(15 / 3.6) + squared(32 / 3.6) + squared(50 / 3.6)) / 2 / 1.013;

    public FuelUse {
        requireAboveNought("litresPer100Km", litresPer100Km);
        requireAboveNought("townLitresPer100Km", townLitresPer100Km);
        requireAboveNought("openRoadLitresPer100Km", openRoadLitresPer100Km);
        if (kilograms.isPresent()) {
            requireAboveNought("kilograms", kilograms.getAsDouble());
        }
        // Written as "not nought or above" rather than "below nought", so NaN is refused too.
        if (!(loadKilograms >= 0) || Double.isInfinite(loadKilograms)) {
            throw new IllegalArgumentException("loadKilograms must be nought or above, was " + loadKilograms);
        }
    }

    /** A vehicle carrying nothing besides the driver. */
    public FuelUse(
            double litresPer100Km, double townLitresPer100Km, double openRoadLitresPer100Km, OptionalDouble kilograms) {
        this(litresPer100Km, townLitresPer100Km, openRoadLitresPer100Km, kilograms, 0);
    }

    /** A vehicle with one figure, split between town and the open road the usual way. */
    public FuelUse(double litresPer100Km, OptionalDouble kilograms) {
        this(litresPer100Km,
                TOWN_OVER_OPEN_ROAD * openRoadFrom(litresPer100Km, TOWN_OVER_OPEN_ROAD),
                openRoadFrom(litresPer100Km, TOWN_OVER_OPEN_ROAD),
                kilograms);
    }

    /** A vehicle with one figure and no known weight, so hills are not counted. */
    public FuelUse(double litresPer100Km) {
        this(litresPer100Km, OptionalDouble.empty());
    }

    /**
     * A vehicle with one figure, split between town and the open road as its own two figures
     * are: town this many times the open road.
     */
    public static FuelUse withTownOverOpenRoad(
            double litresPer100Km, double townOverOpenRoad, OptionalDouble kilograms) {
        requireAboveNought("townOverOpenRoad", townOverOpenRoad);
        double openRoad = openRoadFrom(litresPer100Km, townOverOpenRoad);
        return new FuelUse(litresPer100Km, townOverOpenRoad * openRoad, openRoad, kilograms);
    }

    /** The same vehicle carrying this besides the driver. */
    public FuelUse carrying(double loadKilograms) {
        return new FuelUse(litresPer100Km, townLitresPer100Km, openRoadLitresPer100Km, kilograms, loadKilograms);
    }

    /** The open road's figure that, with this split, comes to the one figure over the test. */
    private static double openRoadFrom(double litresPer100Km, double townOverOpenRoad) {
        return litresPer100Km / (TOWN_SHARE_OF_THE_TEST * townOverOpenRoad + 1 - TOWN_SHARE_OF_THE_TEST);
    }

    private static void requireAboveNought(String name, double value) {
        // Written as "not above nought" rather than "nought or below", so NaN is refused too.
        if (!(value > 0) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be a number above nought, was " + value);
        }
    }

    /** Whether this vehicle's weight is known, so a drive's hills can be counted. */
    public boolean countsHills() {
        return kilograms.isPresent();
    }

    /**
     * The fuel a drive of this length takes on level road, at the one figure. A load is counted
     * as if the drive were in town as much as the approval test is.
     */
    public double litresFor(double distanceKm) {
        return distanceKm * litresPer100Km / 100 + loadOnTheLevel(distanceKm, distanceKm * TOWN_SHARE_OF_THE_TEST);
    }

    /**
     * The fuel a drive takes on level road: the town figure for its kilometres in town, and the
     * open road's for the rest. The one figure where that is not known.
     */
    public double litresOnTheLevel(double distanceKm, OptionalDouble kmInTown) {
        if (kmInTown.isEmpty()) {
            return litresFor(distanceKm);
        }
        double inTown = Math.min(kmInTown.getAsDouble(), distanceKm);
        return (inTown * townLitresPer100Km + (distanceKm - inTown) * openRoadLitresPer100Km) / 100
                + loadOnTheLevel(distanceKm, inTown);
    }

    /**
     * The fuel the load takes on level road: rolling it along every kilometre, and in town getting
     * it going again after each stop. Nought without a load.
     */
    private double loadOnTheLevel(double distanceKm, double kmInTown) {
        double joules = loadKilograms
                * (GRAVITY * ROLLING_RESISTANCE * distanceKm * 1000 + TOWN_JOULES_PER_KILOGRAM_PER_KM * kmInTown);
        return joules / (ENGINE_EFFICIENCY * JOULES_IN_A_LITRE_OF_PETROL);
    }

    /** The fuel a drive takes on roads whose speeds are not known, counting its hills. */
    public double litresFor(double distanceKm, Optional<ClimbAndDescent> hills) {
        return litresFor(distanceKm, OptionalDouble.empty(), hills);
    }

    /**
     * The fuel a drive takes, counting how much of it is in town where that is known, and its
     * hills when they and the weight are known.
     *
     * <p>When the descents are known only as a total, as in a test, the whole drive is taken
     * as the road they fall over.
     */
    public double litresFor(double distanceKm, OptionalDouble kmInTown, Optional<ClimbAndDescent> hills) {
        double level = litresOnTheLevel(distanceKm, kmInTown);
        if (hills.isEmpty() || kilograms.isEmpty()) {
            return level;
        }
        ClimbAndDescent drive = hills.get();
        double perMetreClimbed = litresPerMetreClimbed();
        double givenBack = drive.descents().isEmpty()
                ? Math.min(perMetreClimbed * drive.descentMetres(), litresFor(distanceKm))
                : drive.descents().stream().mapToDouble(descent -> givenBack(descent, perMetreClimbed)).sum();
        return level + perMetreClimbed * drive.climbMetres() - givenBack;
    }

    /**
     * The fuel a drive uses up for good: what it takes on the level, and what its steep descents
     * brake away. What it climbs is left out, since the car keeps that as height and gets it back
     * coming down. Over a round trip, which ends as high as it began, this is the fuel it takes.
     *
     * <p>A one-way trip that ends lower takes less fuel than one that ends higher, but only because
     * the car is left lower, and the next drive up takes that fuel back. This leaves it out.
     */
    public double litresUsedUpFor(double distanceKm, OptionalDouble kmInTown, Optional<ClimbAndDescent> hills) {
        if (hills.isEmpty() || kilograms.isEmpty()) {
            return litresOnTheLevel(distanceKm, kmInTown);
        }
        ClimbAndDescent drive = hills.get();
        return litresFor(distanceKm, kmInTown, hills)
                - litresPerMetreClimbed() * (drive.climbMetres() - drive.descentMetres());
    }

    /** The fuel a drive on roads whose speeds are not known uses up for good. */
    public double litresUsedUpFor(double distanceKm, Optional<ClimbAndDescent> hills) {
        return litresUsedUpFor(distanceKm, OptionalDouble.empty(), hills);
    }

    /** The fuel each metre of climb takes, lifting the car, the driver and the load. */
    double litresPerMetreClimbed() {
        return (kilograms.getAsDouble() + DRIVER_KILOGRAMS + loadKilograms) * GRAVITY
                / (ENGINE_EFFICIENCY * JOULES_IN_A_LITRE_OF_PETROL);
    }

    private double givenBack(Descent descent, double perMetreClimbed) {
        return Math.min(perMetreClimbed * descent.dropMetres(), litresFor(descent.overMetres() / 1000.0));
    }

    private static double squared(double value) {
        return value * value;
    }
}

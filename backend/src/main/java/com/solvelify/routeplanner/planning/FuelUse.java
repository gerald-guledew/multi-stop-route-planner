package com.solvelify.routeplanner.planning;

import com.solvelify.routeplanner.planning.ClimbAndDescent.Descent;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * How much fuel a vehicle uses: its figure for every kilometre, and, once its weight is known,
 * what each climb costs and each descent gives back.
 *
 * <p>A climb lifts the car and the driver. The work that takes is their weight times the height,
 * and the engine turns {@link #ENGINE_EFFICIENCY} of the extra fuel it burns into that work.
 * Checked against a published measurement: a car on a 4% climb uses 1.5 to 2 times its fuel on
 * level road. This gives 1.8 for cars from 1,000 to 1,800 kg.
 *
 * <p>A descent gives the work back, but never more than that stretch of road takes on the level:
 * once the slope does all the pulling, the engine burns next to nothing, and anything steeper is
 * braked away. So a hill of a few percent costs on the way up what it gives back on the way down,
 * and a steeper one costs more than it gives. Published measurements on the road found the same.
 *
 * <p>A dashboard figure is an average over the driving the car has done, hills included. On a
 * round trip the hills give back nearly all they cost: measured over 142 drives across Auckland,
 * they add 0.2% to the fuel, and in Wellington 3%. So the dashboard figure is read as the
 * figure for level road, and this trip's hills are counted on top.
 *
 * @param litresPer100Km the figure for every kilometre, the dashboard's or the register's
 * @param kilograms      what the car weighs empty. Without it, hills are not counted
 */
public record FuelUse(double litresPer100Km, OptionalDouble kilograms) {

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

    public FuelUse {
        // Written as "not above nought" rather than "nought or below", so NaN is refused too.
        if (!(litresPer100Km > 0) || Double.isInfinite(litresPer100Km)) {
            throw new IllegalArgumentException("litresPer100Km must be a number above nought, was " + litresPer100Km);
        }
        if (kilograms.isPresent() && !(kilograms.getAsDouble() > 0 && Double.isFinite(kilograms.getAsDouble()))) {
            throw new IllegalArgumentException("kilograms must be a number above nought, was " + kilograms.getAsDouble());
        }
    }

    /** A vehicle whose weight is not known, so hills are not counted. */
    public FuelUse(double litresPer100Km) {
        this(litresPer100Km, OptionalDouble.empty());
    }

    /** Whether this vehicle's weight is known, so a drive's hills can be counted. */
    public boolean countsHills() {
        return kilograms.isPresent();
    }

    /** The fuel a drive of this length takes on level road. */
    public double litresFor(double distanceKm) {
        return distanceKm * litresPer100Km / 100;
    }

    /**
     * The fuel a drive takes, counting its hills when they and the weight are known.
     *
     * <p>When the descents are known only as a total, as in a test, the whole drive is taken
     * as the road they fall over.
     */
    public double litresFor(double distanceKm, Optional<ClimbAndDescent> hills) {
        double level = litresFor(distanceKm);
        if (hills.isEmpty() || kilograms.isEmpty()) {
            return level;
        }
        ClimbAndDescent drive = hills.get();
        double perMetreClimbed = litresPerMetreClimbed();
        double givenBack = drive.descents().isEmpty()
                ? Math.min(perMetreClimbed * drive.descentMetres(), level)
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
    public double litresUsedUpFor(double distanceKm, Optional<ClimbAndDescent> hills) {
        if (hills.isEmpty() || kilograms.isEmpty()) {
            return litresFor(distanceKm);
        }
        ClimbAndDescent drive = hills.get();
        return litresFor(distanceKm, hills) - litresPerMetreClimbed() * (drive.climbMetres() - drive.descentMetres());
    }

    /** The fuel each metre of climb takes. */
    double litresPerMetreClimbed() {
        return (kilograms.getAsDouble() + DRIVER_KILOGRAMS) * GRAVITY
                / (ENGINE_EFFICIENCY * JOULES_IN_A_LITRE_OF_PETROL);
    }

    private double givenBack(Descent descent, double perMetreClimbed) {
        return Math.min(perMetreClimbed * descent.dropMetres(), litresPer100Km * descent.overMetres() / 100_000);
    }
}

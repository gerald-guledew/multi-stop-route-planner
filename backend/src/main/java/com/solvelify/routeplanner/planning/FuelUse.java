package com.solvelify.routeplanner.planning;

/**
 * How much fuel a vehicle uses, as its dashboard shows it.
 *
 * <p>Read the same for every kilometre for now. A dashboard figure is an average over the
 * driving the car has done, hills and town included, so for a trip like that driving it is
 * close. Hills and speed are counted later.
 */
public record FuelUse(double litresPer100Km) {

    public FuelUse {
        // Written as "not above nought" rather than "nought or below", so NaN is refused too.
        if (!(litresPer100Km > 0) || Double.isInfinite(litresPer100Km)) {
            throw new IllegalArgumentException("litresPer100Km must be a number above nought, was " + litresPer100Km);
        }
    }

    /** The fuel a drive of this length takes. */
    public double litresFor(double distanceKm) {
        return distanceKm * litresPer100Km / 100;
    }
}

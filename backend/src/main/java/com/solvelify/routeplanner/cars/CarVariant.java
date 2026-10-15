package com.solvelify.routeplanner.cars;

/**
 * One car the register lists, with the figure most cars like it carry.
 *
 * @param litresPer100Km the combined figure of the laboratory test the car was approved with,
 *                       the one most of these cars carry. Real driving uses more
 * @param cars           how many of these cars are registered, sold new here and imported used
 */
public record CarVariant(Car car, double litresPer100Km, int cars) {
}

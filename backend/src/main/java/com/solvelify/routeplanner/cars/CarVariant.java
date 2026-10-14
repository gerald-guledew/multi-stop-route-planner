package com.solvelify.routeplanner.cars;

/**
 * One car the register lists, with the figure most cars like it carry.
 *
 * @param litresPer100Km the combined figure of the laboratory test the car was approved with,
 *                       the middle one of these cars. Real driving uses more
 * @param cars           how many of these cars are registered
 */
public record CarVariant(Car car, double litresPer100Km, int cars) {
}

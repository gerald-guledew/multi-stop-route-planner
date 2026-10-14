package com.solvelify.routeplanner.vehicle;

/**
 * How big a vehicle is. It stands in for the weight, which is what climbing a hill costs, and
 * which few drivers know in kilograms. Everybody knows which of these their car is.
 */
public enum VehicleSize {

    /** A city hatchback. */
    SMALL,

    /** A family hatchback or sedan. */
    MEDIUM,

    /** A large car, an SUV or a people mover. */
    LARGE
}

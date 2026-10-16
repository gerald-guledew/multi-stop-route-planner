package com.solvelify.routeplanner.vehicle;

/**
 * How big a vehicle is. It stands in for the weight, which is what climbing a hill costs, and
 * which few drivers know in kilograms. Everybody knows which of these their car is.
 *
 * <p>Each weighs what the cars it names weigh in NZTA's register, the middle of them, counted
 * by how many are on the road: the Swift, Demio, Fit, Vitz, Aqua and their like for a small car,
 * the Corolla, Axela, Civic, Impreza and Golf for a medium one, and the RAV4, CX-5, Outlander,
 * Commodore, Camry and Odyssey for a large one.
 */
public enum VehicleSize {

    /** A city hatchback. */
    SMALL(1020),

    /** A family hatchback or sedan. */
    MEDIUM(1270),

    /** A large car, an SUV or a people mover. */
    LARGE(1570);

    private final int kilograms;

    VehicleSize(int kilograms) {
        this.kilograms = kilograms;
    }

    /** What a car of this size weighs empty. */
    public int kilograms() {
        return kilograms;
    }
}

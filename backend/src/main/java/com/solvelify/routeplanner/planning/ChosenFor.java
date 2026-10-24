package com.solvelify.routeplanner.planning;

/** What the order of the stops is chosen for. */
public enum ChosenFor {

    /** The shortest trip. */
    DISTANCE,

    /**
     * Driving time. An order that is longer and keeps to faster roads comes out ahead when it
     * takes less time, by a minute or more. The times are for clear roads, so this is the quickest
     * order with nothing in the way.
     */
    TIME,

    /**
     * The least fuel, for one vehicle: its figure for every kilometre, with what each climb costs
     * and each descent gives back, as {@link FuelUse} counts them. That needs the heights of the
     * roads and the vehicle's weight. Without either the fuel goes with the distance, and the
     * shortest order takes the least.
     */
    FUEL
}

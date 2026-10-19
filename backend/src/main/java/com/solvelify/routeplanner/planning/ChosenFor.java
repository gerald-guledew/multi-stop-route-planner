package com.solvelify.routeplanner.planning;

/** What the order of the stops is chosen for. */
public enum ChosenFor {

    /** The shortest trip. */
    DISTANCE,

    /**
     * Distance and climb together. Every metre a drive climbs counts as so many metres of road
     * on top of its length, so an order that is a little longer and climbs a good deal less
     * comes out ahead. How many metres is a setting, in {@link PlanningProperties}.
     */
    DISTANCE_AND_CLIMB,

    /**
     * Driving time. An order that is longer and keeps to faster roads comes out ahead when it
     * takes less time, by a minute or more. The times are for clear roads, so this is the quickest
     * order with nothing in the way.
     */
    TIME
}

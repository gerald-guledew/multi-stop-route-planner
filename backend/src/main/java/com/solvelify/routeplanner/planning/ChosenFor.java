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
    DISTANCE_AND_CLIMB
}

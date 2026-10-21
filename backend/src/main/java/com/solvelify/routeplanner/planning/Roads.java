package com.solvelify.routeplanner.planning;

/**
 * Which roads a drive between two places takes.
 *
 * <p>An order counting climb is chosen over drives that follow either, pair by pair, and each leg
 * of the plan follows the roads its drive did. So the order is chosen for the roads that are
 * driven. See {@link Drives}.
 */
public enum Roads {

    /** The roads the engine picks on its own: quick ones, with some weight on distance. */
    USUAL,

    /**
     * The usual roads, with every metre a road climbs counted as so many metres more of it. A
     * road round a hill can then come out ahead of one over it. Coming down counts for nothing.
     * Climb is known only on roads read with their heights, and anywhere else these are the
     * usual roads.
     */
    CLIMBING_LESS
}

package com.solvelify.routeplanner.planning;

/**
 * How much a drive goes up and how much it comes down, in metres.
 *
 * <p>Both are added up along the road, so they are not the difference in height between the two
 * ends. A drive over a hill to a place at the same height has climbed the hill and come down it,
 * though it ends level. The difference between the ends is the climb less the descent.
 *
 * <p>The descent is a number of metres, like the climb, and not a negative one.
 */
public record ClimbAndDescent(double climbMetres, double descentMetres) {

    /** A drive that neither climbs nor descends. Staying where you are is one. */
    public static final ClimbAndDescent LEVEL = new ClimbAndDescent(0, 0);

    public ClimbAndDescent {
        if (climbMetres < 0 || descentMetres < 0) {
            throw new IllegalArgumentException(
                    "a climb of " + climbMetres + " m and a descent of " + descentMetres
                            + " m: both are given as metres, never below zero");
        }
    }

    /** One drive after another. */
    public ClimbAndDescent plus(ClimbAndDescent next) {
        return new ClimbAndDescent(climbMetres + next.climbMetres, descentMetres + next.descentMetres);
    }
}

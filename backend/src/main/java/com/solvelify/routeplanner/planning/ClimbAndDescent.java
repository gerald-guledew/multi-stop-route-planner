package com.solvelify.routeplanner.planning;

import java.util.List;
import java.util.stream.Stream;

/**
 * How much a drive goes up and how much it comes down, in metres.
 *
 * <p>Both are added up along the road, so they are not the difference in height between the two
 * ends. A drive over a hill to a place at the same height has climbed the hill and come down it,
 * though it ends level. The difference between the ends is the climb less the descent.
 *
 * <p>The descent is a number of metres, like the climb, and not a negative one.
 *
 * <p>Read off the roads, the descent also comes as the descents it is made of, each with the
 * length of road it falls over, because a steep descent gives back less fuel than a gentle one:
 * see {@link FuelUse}. Given as totals alone, as in a test, the descents are not known one by
 * one and the list is empty.
 *
 * @param descents the descents one by one, in the order they are driven. Their drops add up to
 *                 the descent. Empty when only the totals are known
 */
public record ClimbAndDescent(double climbMetres, double descentMetres, List<Descent> descents) {

    /** A drive that neither climbs nor descends. Staying where you are is one. */
    public static final ClimbAndDescent LEVEL = new ClimbAndDescent(0, 0);

    public ClimbAndDescent {
        if (climbMetres < 0 || descentMetres < 0) {
            throw new IllegalArgumentException(
                    "a climb of " + climbMetres + " m and a descent of " + descentMetres
                            + " m: both are given as metres, never below zero");
        }
        descents = List.copyOf(descents);
    }

    /** The two totals, with nothing said about the descents one by one. */
    public ClimbAndDescent(double climbMetres, double descentMetres) {
        this(climbMetres, descentMetres, List.of());
    }

    /** One drive after another. */
    public ClimbAndDescent plus(ClimbAndDescent next) {
        return new ClimbAndDescent(climbMetres + next.climbMetres, descentMetres + next.descentMetres,
                Stream.concat(descents.stream(), next.descents.stream()).toList());
    }

    /**
     * One stretch of road that goes down from one turn to the next.
     *
     * @param dropMetres how much lower it ends, as a number above zero
     * @param overMetres how much road it takes to get there, measured on the map. Nought only
     *                   where the map gives the same point twice at two heights
     */
    public record Descent(double dropMetres, double overMetres) {

        public Descent {
            // Written as "not above" rather than "at or below", so NaN is refused too.
            if (!(dropMetres > 0) || !(overMetres >= 0)) {
                throw new IllegalArgumentException(
                        "a descent of " + dropMetres + " m over " + overMetres
                                + " m: the drop must be above nought and the road no shorter than nought");
            }
        }
    }
}

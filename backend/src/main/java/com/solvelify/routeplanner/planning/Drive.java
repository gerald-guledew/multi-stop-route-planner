package com.solvelify.routeplanner.planning;

import java.util.OptionalDouble;

/**
 * One drive from one place to another.
 *
 * @param distanceKm      how far, along the road or in a straight line
 * @param durationSeconds how long, on clear roads. Empty on straight lines, which say how far and
 *                        not how long
 */
public record Drive(double distanceKm, OptionalDouble durationSeconds) {
}

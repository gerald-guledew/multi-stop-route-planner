package com.solvelify.routeplanner.along;

import com.solvelify.routeplanner.search.FoundPlace;
import java.util.OptionalDouble;

/**
 * A place to stop at on the way, and what stopping there adds to the trip.
 *
 * @param place         the place, as the search found it
 * @param leg           the leg it is a stop on, counting from 0: between the place the route
 *                      gives at that turn and the next one
 * @param kmAdded       how much further the leg is with the stop on it. Nought for a place on
 *                      the road the leg follows anyway
 * @param secondsAdded  how much longer the leg takes with the stop on it, on clear roads and
 *                      with no time spent there. Empty on straight lines
 */
public record PlaceAlong(FoundPlace place, int leg, double kmAdded, OptionalDouble secondsAdded) {
}

import type { Position } from './location'
import type { Place, RoutePlan } from './types'

/**
 * The rules about where the map goes by itself. Plain functions with no React in them, so each
 * rule can be tested on its own.
 */

/**
 * A place picked from the search or the saved places, for the map to go to. Counted, so that
 * picking the same place twice moves the map twice.
 */
export interface Picked {
  at: Position
  times: number
}

/** Close enough to make out the buildings, and the house numbers along a street. */
export const STREET_ZOOM = 17

/**
 * How close to go to a place picked from a list, to check it is the right one. Never further
 * out than the map already is: somebody who has zoomed in closer wants to stay there.
 */
export function closeEnoughToCheck(zoom: number): number {
  return Math.max(zoom, STREET_ZOOM)
}

/** Every point the trip covers: each place, and once it is planned, every road between them. */
export function everywhereTheTripGoes(
  places: Place[],
  plan: RoutePlan | null,
): [number, number][] {
  return [
    ...places.map((place): [number, number] => [place.latitude, place.longitude]),
    ...(plan?.legs.flatMap((leg) => leg.path) ?? []),
  ]
}

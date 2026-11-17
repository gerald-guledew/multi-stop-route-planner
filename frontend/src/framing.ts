import type { Position } from './location'
import type { Place, PlaceAlong, RoutePlan } from './types'

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

/**
 * Every point a search along the planned route has to show: the route, and each place it found.
 * A place found off the end of the route still shows, and so does the far end of the route.
 */
export function everywhereTheFindsAre(plan: RoutePlan, found: PlaceAlong[]): [number, number][] {
  return [
    ...everywhereTheTripGoes(plan.route, plan),
    ...found.map((place): [number, number] => [place.latitude, place.longitude]),
  ]
}

/**
 * Whether two lists of places found are the same places, in the same order. A search asked again
 * can bring back the very same places, and the map need not move for them.
 */
export function sameFinds(one: PlaceAlong[], other: PlaceAlong[]): boolean {
  return (
    one.length === other.length &&
    one.every(
      (place, index) =>
        place.name === other[index].name &&
        place.latitude === other[index].latitude &&
        place.longitude === other[index].longitude,
    )
  )
}

/** On a phone, the list of places found is folded to one line under the search box when they arrive. */
export const FOLDED_LIST_COVERS = 52

/**
 * How far down from the top of the map the places found and the route are framed: clear of the
 * search box, and on a phone, where it covers the map, of the folded list under it. New places
 * always fold the list, so it is never open when they are framed.
 */
export function topPaddingForFinds(coveredAtTheTop: number): number {
  return 40 + coveredAtTheTop + (coveredAtTheTop > 0 ? FOLDED_LIST_COVERS : 0)
}

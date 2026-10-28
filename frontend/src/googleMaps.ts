import type { Place } from './types'

/**
 * How many stops a Google Maps link can carry between its start and its end. A tenth is dropped
 * without a word: tried with ten in a desktop browser on 2026-10-19, and Google's page for these
 * links gives the same nine. A trip takes no more stops than fit, by `mostStops`.
 */
export const MOST_STOPS_BETWEEN = 9

/** Hands the finished order to Google Maps, which does the actual navigating. */
export function googleMapsLink(route: Place[]): string {
  const asCoordinates = (place: Place) => `${place.latitude},${place.longitude}`
  const origin = asCoordinates(route[0])
  const destination = asCoordinates(route[route.length - 1])
  const waypoints = route.slice(1, -1).map(asCoordinates).join('|')

  return `https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${destination}&waypoints=${encodeURIComponent(waypoints)}&travelmode=driving`
}

import type { Place } from './types'

/**
 * How many stops a Google Maps link can carry between its start and its end. A tenth is dropped
 * without a word: tried with ten in a desktop browser on 2026-10-19, and Google's page for these
 * links gives the same nine.
 */
export const MOST_STOPS_BETWEEN = 9

/**
 * Whether the link has to leave out the drive back to the start, so that every stop is in it.
 * Only a round trip of ten stops has one stop too many between its start and its end.
 */
export function leavesOutTheDriveBack(route: Place[]): boolean {
  return route.length - 2 > MOST_STOPS_BETWEEN
}

/**
 * Hands the finished order to Google Maps, which does the actual navigating. When the stops do
 * not all fit, the link ends at the last stop instead of back at the start.
 */
export function googleMapsLink(route: Place[]): string {
  const driven = leavesOutTheDriveBack(route) ? route.slice(0, -1) : route
  const asCoordinates = (place: Place) => `${place.latitude},${place.longitude}`
  const origin = asCoordinates(driven[0])
  const destination = asCoordinates(driven[driven.length - 1])
  const waypoints = driven.slice(1, -1).map(asCoordinates).join('|')

  return `https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${destination}&waypoints=${encodeURIComponent(waypoints)}&travelmode=driving`
}

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
 * What to tell before a trip that does not all fit in the link goes to Google Maps: how many stops
 * it takes, and where the link ends instead. Null when the whole trip fits, as it nearly always does.
 */
export function beforeGoogleMaps(route: Place[]): string | null {
  if (!leavesOutTheDriveBack(route)) {
    return null
  }
  return (
    `Google Maps takes ${MOST_STOPS_BETWEEN} stops at most between the start and the end. ` +
    `Your round trip has ${route.length - 2}, so the link ends at your last stop. Head back to the start from there.`
  )
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

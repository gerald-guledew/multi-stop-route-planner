import type { Position } from './location'
import type { Place } from './types'

/** What a search was ranked near, so the list can say so. */
export type LookedFrom = 'you' | 'start' | 'map'

/**
 * Where a search looks from, so the nearest of several matches can come first.
 *
 * Where you are, when the browser has shared it. Failing that the start of the trip, and
 * until there is a start, the middle of the map, which is where the person is looking.
 */
export function lookFrom(
  devicePosition: Position | null,
  start: Place | undefined,
  mapCentre: Position | null,
): { position: Position; what: LookedFrom } | null {
  if (devicePosition) {
    return { position: devicePosition, what: 'you' }
  }
  if (start) {
    return { position: start, what: 'start' }
  }
  if (mapCentre) {
    return { position: mapCentre, what: 'map' }
  }
  return null
}

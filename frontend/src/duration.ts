import type { Leg } from './types'

/**
 * A driving time said the way a person would: "21 min", "1 h 24 min", "2 h". To the minute,
 * because these are estimates for clear roads and seconds would claim more than is known.
 *
 * A drive of a few seconds is "1 min", not "0 min". Nothing is no time away unless it is the
 * same spot.
 */
export function howLong(seconds: number): string {
  if (seconds <= 0) {
    return '0 min'
  }
  const minutes = Math.max(1, Math.round(seconds / 60))
  const hours = Math.floor(minutes / 60)
  const left = minutes % 60

  if (hours === 0) {
    return `${minutes} min`
  }
  return left === 0 ? `${hours} h` : `${hours} h ${left} min`
}

/** How far a leg is, and how long it takes to drive when the roads say. */
export function legSaid(leg: Leg): string {
  const far = `${leg.distanceKm.toFixed(2)} km`
  return leg.durationSeconds === null ? far : `${far} · ${howLong(leg.durationSeconds)}`
}

import type { Place } from './types'

/**
 * The rules about the list of stops. Plain functions with no React in them, so each rule can
 * be tested on its own.
 */

/** "1st", "2nd", "3rd", "11th": a stop's turn, the way it is said. */
export function ordinal(turn: number): string {
  const lastTwo = turn % 100
  if (lastTwo >= 11 && lastTwo <= 13) {
    return `${turn}th`
  }
  switch (turn % 10) {
    case 1:
      return `${turn}st`
    case 2:
      return `${turn}nd`
    case 3:
      return `${turn}rd`
    default:
      return `${turn}th`
  }
}

/**
 * Marks a stop to keep its place in the order, or lets it go again. The start cannot be
 * marked. It is always first.
 */
export function toggleKeptInPlace(places: Place[], index: number): Place[] {
  if (index === 0) {
    return places
  }
  return places.map((place, at) =>
    at === index ? { ...place, keepInPlace: !place.keepInPlace } : place,
  )
}

/**
 * Takes a place out of the list. If the start goes, the first stop becomes the start, and a
 * start has no place to keep.
 */
export function withoutPlace(places: Place[], index: number): Place[] {
  const left = places.filter((_, at) => at !== index)
  if (left.length === 0 || !left[0].keepInPlace) {
    return left
  }
  const start = left[0]
  return [
    { name: start.name, latitude: start.latitude, longitude: start.longitude },
    ...left.slice(1),
  ]
}

/** Whether any stop is being kept in its place. */
export function anyKeptInPlace(places: Place[]): boolean {
  return places.some((place, at) => at > 0 && place.keepInPlace === true)
}

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

/** Up is one turn earlier, down is one turn later, as the list is drawn. */
export type Direction = 'up' | 'down'

/**
 * Whether a stop has a turn to move to that way. The start is no stop and is not moved like
 * one: it does not go down, and no stop goes up past it.
 */
export function canMove(places: Place[], index: number, direction: Direction): boolean {
  const takesTheTurnOf = direction === 'up' ? index - 1 : index + 1
  return index >= 1 && index < places.length && takesTheTurnOf >= 1 && takesTheTurnOf < places.length
}

/**
 * Moves a stop one turn, by changing places with the stop beside it. A stop that is kept in
 * its place stays kept, at the turn it now has. So does a kept stop it changed places with:
 * keeping holds a stop against the planner, not against the person arranging the list.
 */
export function moved(places: Place[], index: number, direction: Direction): Place[] {
  if (!canMove(places, index, direction)) {
    return places
  }
  const takesTheTurnOf = direction === 'up' ? index - 1 : index + 1
  const rearranged = [...places]
  rearranged[index] = places[takesTheTurnOf]
  rearranged[takesTheTurnOf] = places[index]
  return rearranged
}

/** Whether any stop is being kept in its place. */
export function anyKeptInPlace(places: Place[]): boolean {
  return places.some((place, at) => at > 0 && place.keepInPlace === true)
}

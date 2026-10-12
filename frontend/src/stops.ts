import type { Place } from './types'

/**
 * The rules about the list of stops. Plain functions with no React in them, so each rule can
 * be tested on its own.
 */

/** What a place is called until somebody names it: "Start", then "Stop 1", "Stop 2". */
export function defaultLabel(turn: number): string {
  return turn === 0 ? 'Start' : `Stop ${turn}`
}

/**
 * A label nobody has changed says the turn, so it has to change when the turn does. Otherwise
 * the second stop in the list can be the one called "Stop 3". A label somebody typed is a name
 * and goes with its place wherever that ends up.
 *
 * Takes the list as it was and as it is now, and finds each place's old turn by the place
 * being the very same object in both.
 */
export function withLabelsInStep(before: Place[], after: Place[]): Place[] {
  return after.map((place, turn) => {
    const turnBefore = before.indexOf(place)
    const untouched = turnBefore >= 0 && place.name === defaultLabel(turnBefore)
    return untouched && turnBefore !== turn ? { ...place, name: defaultLabel(turn) } : place
  })
}

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
  const left = withLabelsInStep(
    places,
    places.filter((_, at) => at !== index),
  )
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
  return withLabelsInStep(places, rearranged)
}

/** Whether any stop is being kept in its place. */
export function anyKeptInPlace(places: Place[]): boolean {
  return places.some((place, at) => at > 0 && place.keepInPlace === true)
}

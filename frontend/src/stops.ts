import { MOST_STOPS_BETWEEN } from './googleMaps'
import type { Place } from './types'

/**
 * The rules about the list of stops. Plain functions with no React in them, so each rule can
 * be tested on its own.
 */

/**
 * The most stops a trip takes, so that every one of them goes into the Google Maps link. A
 * one-way trip ends at its last stop, so it takes ten, which is also the most the planner checks
 * every order of. A round trip ends back at the start, so it takes nine.
 */
export function mostStops(returnToStart: boolean): number {
  return returnToStart ? MOST_STOPS_BETWEEN : MOST_STOPS_BETWEEN + 1
}

/** Whether the trip has all the stops it takes, so that no other can be added. */
export function isFull(places: Place[], returnToStart: boolean): boolean {
  return places.length - 1 >= mostStops(returnToStart)
}

/**
 * Whether a one-way trip has one stop too many to come back to its start. Going one way again
 * is never held up.
 */
export function tooManyToReturn(places: Place[], returnToStart: boolean): boolean {
  return !returnToStart && places.length - 1 > mostStops(true)
}

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

/**
 * The list with a stop at a turn, and a label nobody changed moved along with the turn it now has.
 */
export function withStopAt(places: Place[], turn: number, stop: Place): Place[] {
  return withLabelsInStep(places, [...places.slice(0, turn), stop, ...places.slice(turn)])
}

/**
 * Where a new stop goes: at the end of the list. On a round trip a stop-over at the end is on the
 * way back to the start, which it has to stay on. So the new stop goes in front of that stop-over
 * and the stop it comes after.
 */
export function turnForANewStop(places: Place[], returnToStart: boolean): number {
  if (!returnToStart) {
    return places.length
  }
  let turn = places.length
  while (turn > 1 && places[turn - 1].stopOver) {
    turn--
  }
  return turn === places.length ? turn : Math.max(turn - 1, 1)
}

/** "1st", "2nd", "3rd", "11th": a stop's turn, or a roundabout's exit, the way it is said. */
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

/** A stop-over that goes back to being a stop like any other, free to move. */
function letGo({ stopOver: _, ...stop }: Place): Place {
  return stop
}

/** The list with one stop-over let go, by the person who added it. */
export function withStopOverLetGo(places: Place[], index: number): Place[] {
  return places.map((place, at) => (at === index ? letGo(place) : place))
}

/**
 * The list with the stop-overs at the very end let go, once the trip no longer drives back to the
 * start: the way back was the leg they were on.
 */
export function withoutTheWayBack(places: Place[]): Place[] {
  let firstAtTheEnd = places.length
  while (firstAtTheEnd > 1 && places[firstAtTheEnd - 1].stopOver) {
    firstAtTheEnd--
  }
  return places.map((place, at) => (at >= firstAtTheEnd ? letGo(place) : place))
}

/**
 * The list with the stop-overs let go that were on a leg to or from the place at a turn, for when
 * that place is going. A stop-over itself takes no leg with it: the two stops are still there,
 * and so is any other stop-over between them. The start is at both ends of a round trip.
 */
export function withItsLegsLetGo(places: Place[], turn: number): Place[] {
  if (places[turn]?.stopOver) {
    return places
  }
  const legGone = new Set<number>()
  for (let at = turn + 1; places[at]?.stopOver; at++) {
    legGone.add(at)
  }
  for (let at = turn - 1; places[at]?.stopOver; at--) {
    legGone.add(at)
  }
  const letGoEnd = turn === 0 ? withoutTheWayBack(places) : places
  return letGoEnd.map((place, at) => (legGone.has(at) ? letGo(place) : place))
}

/** Whether any stop is a stop-over. */
export function anyStopOver(places: Place[]): boolean {
  return places.some((place) => place.stopOver === true)
}

/** Which two places a stop-over stays between: "between A and B", or "after A" if nothing follows. */
export function stopOverSaid(places: Place[], index: number, returnToStart: boolean): string {
  const next = places[index + 1] ?? (returnToStart ? places[0] : undefined)
  const before = places[index - 1].name
  return next ? `between ${before} and ${next.name}` : `after ${before}`
}

/**
 * Takes a place out of the list. If the start goes, the first stop becomes the start, and a
 * start has no place to keep. A stop-over on a leg of the place that goes is let go.
 */
export function withoutPlace(places: Place[], index: number): Place[] {
  const legsGone = withItsLegsLetGo(places, index)
  const left = withLabelsInStep(
    legsGone,
    legsGone.filter((_, at) => at !== index),
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

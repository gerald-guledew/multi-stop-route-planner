import { howLong } from './duration'
import { sitsOn } from './foundStart'
import { isFull, withLabelsInStep } from './stops'
import type { Place, PlaceAlong, RoutePlan } from './types'

/**
 * The rules about a stop on the way: a place added between two stops of a planned trip. The
 * stops keep the order the plan gave them, and the new one goes between the two it was added
 * between. Plain functions with no React in them, so each rule can be tested on its own.
 */

/** Under this, a stop adds nothing worth a figure. The screen gives times to the minute. */
const A_MINUTE = 60

/** On straight lines, the same for distance: under this, a figure to a tenth would say 0.0 km. */
const A_FEW_STEPS_KM = 0.05

/**
 * Whether a stop can be added on the way: once the trip is planned, while it has room for one
 * more stop, and not while a plan is on its way.
 */
export function canAddOnTheWay(
  places: Place[],
  returnToStart: boolean,
  plan: RoutePlan | null,
  planning: boolean,
): boolean {
  return plan !== null && !planning && !isFull(places, returnToStart)
}

/**
 * The trip with a stop on the way added straight after the place its leg sets off from. That is
 * where it goes in the order the stops were entered, which the plan is held against.
 */
export function withStopOn(places: Place[], plan: RoutePlan, leg: number, stop: Place): Place[] {
  const setsOffFrom = places.findIndex((place) => sitsOn(place, plan.route[leg]))
  const at = setsOffFrom < 0 ? places.length : setsOffFrom + 1
  return withLabelsInStep(places, [...places.slice(0, at), stop, ...places.slice(at)])
}

/**
 * The trip as its plan drives it, with the stop on its leg and every stop kept at its turn.
 * Planned like that it has one order: the stops keep the order the plan gave them, and the new one
 * sits between the two it was added between.
 *
 * Takes the trip with the stop already in it, so the names sent are the ones the trip shows.
 * Null when a place the plan drives to is not in the trip, as the plan is then for another trip.
 */
export function heldInPlannedOrder(
  places: Place[],
  plan: RoutePlan,
  returnToStart: boolean,
  leg: number,
  stop: Place,
): Place[] | null {
  // A round trip's plan ends back at the start, which is not a stop to send.
  const driven = returnToStart ? plan.route.slice(0, -1) : plan.route
  const inTheTrip = driven.map((planned) => places.find((place) => sitsOn(place, planned)))
  const added = places.find((place) => sitsOn(place, stop))
  if (inTheTrip.some((place) => place === undefined) || added === undefined) {
    return null
  }
  const [start, ...stops] = [
    ...inTheTrip.slice(0, leg + 1),
    added,
    ...inTheTrip.slice(leg + 1),
  ] as Place[]
  return [
    { name: start.name, latitude: start.latitude, longitude: start.longitude },
    ...stops.map((place) => ({ ...place, keepInPlace: true })),
  ]
}

/**
 * What stopping there adds to the trip: "+4 min", or "+1.2 km" on straight lines. A stop that
 * adds under a minute, or a few steps, is on the way.
 */
export function addedSaid(found: PlaceAlong): string {
  if (found.secondsAdded !== null) {
    return found.secondsAdded < A_MINUTE ? 'On the way' : `+${howLong(found.secondsAdded)}`
  }
  return found.kmAdded < A_FEW_STEPS_KM ? 'On the way' : `+${found.kmAdded.toFixed(1)} km`
}

/** Which two places a leg runs between, by the names the plan gives them: "between A and B". */
export function betweenSaid(plan: RoutePlan, leg: number): string {
  return `between ${plan.route[leg].name} and ${plan.route[leg + 1].name}`
}

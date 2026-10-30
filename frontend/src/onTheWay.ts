import { howLong } from './duration'
import { sitsOn } from './foundStart'
import { isFull, withLabelsInStep } from './stops'
import type { FoundPlace, Place, PlaceAlong, RoutePlan } from './types'

/**
 * The rules about a stop on the way: a place added between two stops of a planned trip. The
 * stops keep the order the plan gave them, and the new one goes between the two it was added
 * between. Plain functions with no React in them, so each rule can be tested on its own.
 */

/** What the search box looks along: one leg of a plan, by its number, or all of it, by null. */
export interface LookingAlong {
  plan: RoutePlan
  leg: number | null
}

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

/** The places a plan drives to. A round trip's plan ends back at the start, which is not one more. */
function placesDriven(plan: RoutePlan, returnToStart: boolean): Place[] {
  return returnToStart ? plan.route.slice(0, -1) : plan.route
}

/**
 * The list in the order its plan drives it, so that two stops side by side are a leg of the plan.
 * Each place stays as it was, kept at its turn or not, and a label nobody changed moves with its
 * turn. The very same list when the plan drives it as it stands, so that undo gains nothing.
 * Null when the plan is for other places than these.
 */
export function inPlannedOrder(
  places: Place[],
  plan: RoutePlan,
  returnToStart: boolean,
): Place[] | null {
  const ordered = placesDriven(plan, returnToStart).map((planned) =>
    places.find((place) => sitsOn(place, planned)),
  )
  if (
    ordered.length !== places.length ||
    ordered.includes(undefined) ||
    new Set(ordered).size !== places.length
  ) {
    return null
  }
  if (ordered.every((place, turn) => place === places[turn])) {
    return places
  }
  return withLabelsInStep(places, ordered as Place[])
}

/**
 * The plan under the names the trip shows. It comes back with the names it was asked with, and a
 * label nobody changed moves with its turn once the list takes the plan's order.
 */
export function namedAs(plan: RoutePlan, places: Place[]): RoutePlan {
  const route = plan.route.map((planned) => {
    const place = places.find((one) => sitsOn(one, planned))
    return place ? { ...planned, name: place.name } : planned
  })
  const legs = plan.legs.map((leg, index) => ({
    ...leg,
    from: route[index].name,
    to: route[index + 1].name,
  }))
  return { ...plan, route, legs }
}

/**
 * The leg between a place in the list and the one after it, by its number: on a round trip the
 * last place has the way back to the start after it. Null when there is no place after it, and
 * when the plan does not drive straight from the one to the other.
 */
export function legAfter(
  places: Place[],
  plan: RoutePlan,
  index: number,
  returnToStart: boolean,
): number | null {
  const next = places[index + 1] ?? (returnToStart ? places[0] : undefined)
  return sitsOn(places[index], plan.route[index] ?? null) &&
    sitsOn(next, plan.route[index + 1] ?? null)
    ? index
    : null
}

/**
 * The trip with a stop on the way added straight after the place its leg sets off from. The list
 * is in the plan's order, so that puts it between the two stops of its leg.
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
  const inTheTrip = placesDriven(plan, returnToStart).map((planned) =>
    places.find((place) => sitsOn(place, planned)),
  )
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

/** What the box under the search bar says it finds: along all of the plan, or one leg of it. */
export function alongSaid(plan: RoutePlan, leg: number | null): string {
  return leg === null ? 'Find along the current route' : `Find ${betweenSaid(plan, leg)}`
}

/** Whether a place was found along a route, and so says what stopping there adds. */
export function isAlong(found: FoundPlace): found is PlaceAlong {
  return 'leg' in found
}

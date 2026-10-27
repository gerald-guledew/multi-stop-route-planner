import { howLong } from './duration'
import { litresSaid } from './fuel'
import { NO_FURTHER_KM, NO_LESS_LITRES, NO_QUICKER_SECONDS } from './shortestOrder'
import type { OrderTotals, RoutePlan } from './types'

/**
 * Under a minute apart, the two take about the same time. The screen gives times to the minute,
 * and the fastest takes another order than the shortest only for a minute or more.
 */
const A_MINUTE = 60

/** The least fuel and the fastest, each as what it comes to, and which of the two the map shows. */
export interface BothChoices {
  leastFuel: OrderTotals
  fastest: OrderTotals
  shown: 'leastFuel' | 'fastest'
}

/**
 * The two choices together: what the plan came to, and what the other choice would have.
 * Null when the plan gives no other choice, which is so without a vehicle and on straight lines,
 * since there is then no fuel or no time to hold the two against each other by.
 */
export function bothChoices(plan: RoutePlan): BothChoices | null {
  const own: OrderTotals = {
    distanceKm: plan.totalDistanceKm,
    durationSeconds: plan.totalDurationSeconds,
    climbMetres: plan.totalClimbMetres,
    descentMetres: plan.totalDescentMetres,
    fuelLitres: plan.totalFuelLitres,
  }
  const both: BothChoices | null = plan.fastestOrder
    ? { leastFuel: own, fastest: plan.fastestOrder, shown: 'leastFuel' }
    : plan.leastFuelOrder
      ? { leastFuel: plan.leastFuelOrder, fastest: own, shown: 'fastest' }
      : null
  if (both === null) {
    return null
  }
  const comparable = [both.leastFuel, both.fastest].every(
    (order) => order.durationSeconds !== null && order.fuelLitres !== null,
  )
  return comparable ? both : null
}

/**
 * Whether the two come to the same, to the last digit the planner gives. Usually they are the
 * same order: the open road that takes less fuel is often the quicker way too.
 */
export function comeOutTheSame(both: BothChoices): boolean {
  return (
    Math.abs(both.leastFuel.distanceKm - both.fastest.distanceKm) < NO_FURTHER_KM &&
    Math.abs(seconds(both.leastFuel) - seconds(both.fastest)) < NO_QUICKER_SECONDS &&
    Math.abs(litres(both.leastFuel) - litres(both.fastest)) < NO_LESS_LITRES
  )
}

/**
 * The other choice in one line, against the one on the map, led by what it is chosen for:
 * "Fastest: 4 min quicker, 0.3 L more" beside the least fuel, and "Least fuel: 0.3 L less, 4 min
 * slower" beside the fastest. "Same as Fastest" when the two come out the same.
 *
 * Each usually wins on what it is chosen for, and either way it is said as it is. The fastest can
 * take a little less fuel as driven, by ending lower on a one-way trip. The least fuel can be
 * quicker by less than the minute the fastest goes further for, which is about the same time.
 */
export function otherChoiceSaid(both: BothChoices): string {
  const fastestShown = both.shown === 'fastest'
  const other = fastestShown ? 'Least fuel' : 'Fastest'
  if (comeOutTheSame(both)) {
    return `Same as ${other}.`
  }
  const [onTheMap, theOther] = fastestShown ? [both.fastest, both.leastFuel] : [both.leastFuel, both.fastest]
  const time = longerSaid(seconds(theOther) - seconds(onTheMap))
  const fuel = moreSaid(litres(theOther) - litres(onTheMap))

  if (time === null && fuel === null) {
    return `${other}: about the same time and fuel.`
  }
  const timeSaid = time ?? 'about the same time'
  const fuelSaid = fuel ?? 'the same fuel'
  return fastestShown ? `${other}: ${fuelSaid}, ${timeSaid}.` : `${other}: ${timeSaid}, ${fuelSaid}.`
}

/** "4 min slower" or "4 min quicker". Null under a minute either way. */
function longerSaid(longerSeconds: number): string | null {
  if (Math.abs(longerSeconds) < A_MINUTE) {
    return null
  }
  return `${howLong(Math.abs(longerSeconds))} ${longerSeconds > 0 ? 'slower' : 'quicker'}`
}

/** "0.3 L more" or "under 0.1 L less". Null when the two take the same, to the last digit given. */
function moreSaid(moreLitres: number): string | null {
  if (Math.abs(moreLitres) < NO_LESS_LITRES) {
    return null
  }
  return `${litresSaid(Math.abs(moreLitres))} ${moreLitres > 0 ? 'more' : 'less'}`
}

/** Known for both, by the time they are held against each other. */
function seconds(order: OrderTotals): number {
  return order.durationSeconds ?? 0
}

function litres(order: OrderTotals): number {
  return order.fuelLitres ?? 0
}

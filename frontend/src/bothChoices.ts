import { howLong } from './duration'
import { litresSaid } from './fuel'
import { NO_FURTHER_KM, NO_LESS_LITRES, NO_QUICKER_SECONDS } from './shortestOrder'
import type { OrderTotals, RoutePlan } from './types'

/** Below half a minute, "1 min quicker" would claim twice the time saved, or more. */
const UNDER_HALF_A_MINUTE = 30

/** The least fuel and the fastest, each as what it comes to, and which of the two the map shows. */
export interface BothChoices {
  leastFuel: OrderTotals
  fastest: OrderTotals
  shown: 'leastFuel' | 'fastest'
}

/**
 * The two choices side by side: what the plan came to, and what the other choice would have.
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

/** The rows of the side by side: each figure as the rest of the screen says it, for each choice. */
export function sideBySide(both: BothChoices): { says: string; leastFuel: string; fastest: string }[] {
  return [
    { says: 'Distance', leastFuel: kmSaid(both.leastFuel), fastest: kmSaid(both.fastest) },
    { says: 'Driving time', leastFuel: howLong(seconds(both.leastFuel)), fastest: howLong(seconds(both.fastest)) },
    { says: 'Fuel', leastFuel: litresSaid(litres(both.leastFuel)), fastest: litresSaid(litres(both.fastest)) },
  ]
}

/**
 * The difference between the two, in a sentence: the fuel the least fuel saves, and the time the
 * fastest does. Said even when it is small, since that is worth knowing too. Each usually saves
 * what it is chosen for. The fastest can take a little less fuel as driven, by ending lower on a
 * one-way trip, or by less than the hundredth of a litre the least fuel changes the order for.
 * The least fuel can be quicker, by less than the minute the fastest goes further for. Either is
 * said as it is.
 */
export function bothSaid(both: BothChoices): string {
  if (comeOutTheSame(both)) {
    return 'Least fuel and Fastest come out the same for this trip.'
  }
  // How much less fuel the least fuel takes, and how much quicker the fastest is.
  const lessLitres = litres(both.fastest) - litres(both.leastFuel)
  const quickerSeconds = seconds(both.leastFuel) - seconds(both.fastest)
  const savesFuel = Math.abs(lessLitres) < NO_LESS_LITRES ? null : lessLitres > 0 ? 'Least fuel' : 'Fastest'
  const savesTime =
    Math.abs(quickerSeconds) < UNDER_HALF_A_MINUTE ? null : quickerSeconds > 0 ? 'Fastest' : 'Least fuel'
  const fuel = litresSaid(Math.abs(lessLitres))
  const time = howLong(Math.abs(quickerSeconds))

  if (savesFuel === null && savesTime === null) {
    return 'They take the same fuel and about the same time.'
  }
  if (savesFuel === null) {
    return `They take the same fuel, and ${savesTime} is ${time} quicker.`
  }
  if (savesTime === null) {
    return `${savesFuel} saves ${fuel}, and they take about the same time.`
  }
  if (savesFuel === savesTime) {
    return `${savesFuel} saves ${fuel} and is ${time} quicker.`
  }
  return `${savesFuel} saves ${fuel}, and ${savesTime} is ${time} quicker.`
}

function kmSaid(order: OrderTotals): string {
  return `${order.distanceKm.toFixed(2)} km`
}

/** Known for both, by the time they are set side by side. */
function seconds(order: OrderTotals): number {
  return order.durationSeconds ?? 0
}

function litres(order: OrderTotals): number {
  return order.fuelLitres ?? 0
}

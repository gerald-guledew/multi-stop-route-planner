import type { ChosenFor } from './chosenFor'
import { howLong } from './duration'
import { litresSaid } from './fuel'
import type { RoutePlan, ShortestOrder } from './types'

/**
 * Half of the last digit shown. The figures arrive rounded, to a hundredth of a kilometre, to a
 * second, to a metre and to a hundredth of a litre, so anything smaller than this is no
 * difference at all.
 */
export const NO_FURTHER_KM = 0.005
const NO_QUICKER_SECONDS = 0.5
const NO_LESS_METRES = 0.5
const NO_LESS_LITRES = 0.005

/**
 * How much further the order chosen goes than the shortest, how much quicker it is, how much
 * less it climbs, and how much less fuel it takes. Nought for a figure that one of the two does
 * not give.
 */
export function against(
  shortest: ShortestOrder,
  plan: RoutePlan,
): { furtherKm: number; quickerSeconds: number; lessMetres: number; lessLitres: number } {
  return {
    furtherKm: plan.totalDistanceKm - shortest.distanceKm,
    quickerSeconds:
      shortest.durationSeconds === null || plan.totalDurationSeconds === null
        ? 0
        : shortest.durationSeconds - plan.totalDurationSeconds,
    lessMetres:
      shortest.climbMetres === null || plan.totalClimbMetres === null
        ? 0
        : shortest.climbMetres - plan.totalClimbMetres,
    lessLitres:
      shortest.fuelLitres === null || plan.totalFuelLitres === null
        ? 0
        : shortest.fuelLitres - plan.totalFuelLitres,
  }
}

/**
 * Whether the order chosen is another one than the shortest, going by the figures. False when
 * the plan gives no shortest order to hold it against, since it is then the shortest itself.
 */
export function differsFromTheShortest(plan: RoutePlan): boolean {
  if (plan.shortestOrder === null) {
    return false
  }
  const { furtherKm, quickerSeconds, lessMetres, lessLitres } = against(plan.shortestOrder, plan)
  return (
    furtherKm >= NO_FURTHER_KM ||
    quickerSeconds >= NO_QUICKER_SECONDS ||
    lessMetres >= NO_LESS_METRES ||
    lessLitres >= NO_LESS_LITRES
  )
}

/**
 * The shortest order in a few words, for the row beside the order chosen: how far it is, and
 * what the order was chosen for besides. "62.58 km, 1 h 12 min" beside the fastest, and
 * "62.58 km, 4.9 L" beside the least fuel.
 */
export function shortestSaid(shortest: ShortestOrder, chosenFor: ChosenFor): string {
  const parts = [`${shortest.distanceKm.toFixed(2)} km`]
  if (chosenFor === 'time' && shortest.durationSeconds !== null) {
    parts.push(howLong(shortest.durationSeconds))
  }
  if (chosenFor === 'fuel' && shortest.fuelLitres !== null) {
    parts.push(litresSaid(shortest.fuelLitres))
  }
  return parts.join(', ')
}

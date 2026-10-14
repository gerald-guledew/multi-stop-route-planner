import { metres } from './climb'
import type { RoutePlan, ShortestOrder } from './types'

/**
 * Half of the last digit shown. The figures arrive rounded, to a hundredth of a kilometre and
 * to a metre, so anything smaller than this is no difference at all.
 */
const NO_FURTHER_KM = 0.005
const NO_LESS_METRES = 0.5

/** How much further the order chosen goes than the shortest, and how much less it climbs. */
function against(shortest: ShortestOrder, plan: RoutePlan): { furtherKm: number; lessMetres: number } {
  return {
    furtherKm: plan.totalDistanceKm - shortest.distanceKm,
    lessMetres: shortest.climbMetres - (plan.totalClimbMetres ?? shortest.climbMetres),
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
  const { furtherKm, lessMetres } = against(plan.shortestOrder, plan)
  return furtherKm >= NO_FURTHER_KM || lessMetres >= NO_LESS_METRES
}

/** The shortest order in a few words, for the row beside the order chosen: "62.58 km, 666 m up". */
export function shortestSaid(shortest: ShortestOrder): string {
  return `${shortest.distanceKm.toFixed(2)} km, ${metres(shortest.climbMetres)} up`
}

/**
 * What factoring in road climbs came to for this plan, in a sentence. Null when it was not asked
 * for, and then nothing is shown.
 *
 * The planner counts every metre climbed as so many metres of road, so the order can come out
 * a little longer than the shortest, to climb less. Often the shortest order stands. That is
 * said too, or ticking the box would look as if it had done nothing.
 *
 * A planner started without heights cannot count any climb. It answers with the shortest order
 * and gives nothing to hold it against, which is how this knows.
 */
export function roadClimbsSaid(askedFor: boolean, plan: RoutePlan): string | null {
  if (!askedFor) {
    return null
  }
  if (plan.shortestOrder === null) {
    return 'Road climbs were not factored in. This planner has no heights to go on, so this is the shortest order.'
  }
  if (!differsFromTheShortest(plan)) {
    return 'With road climbs factored in, the shortest order still comes out best.'
  }

  const { furtherKm, lessMetres } = against(plan.shortestOrder, plan)
  const longer = furtherKm < NO_FURTHER_KM ? 'no longer than' : `${furtherKm.toFixed(2)} km longer than`
  // Two rounded figures can differ by a metre the wrong way. Less than nought is not said.
  return `This order is ${longer} the shortest and climbs ${metres(Math.max(lessMetres, 0))} less.`
}

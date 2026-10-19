import { metres } from './climb'
import { against, differsFromTheShortest, NO_FURTHER_KM } from './shortestOrder'
import type { RoutePlan } from './types'

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

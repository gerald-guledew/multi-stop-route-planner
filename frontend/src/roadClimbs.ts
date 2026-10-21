import { metres } from './climb'
import { against, differsFromTheShortest, NO_FURTHER_KM } from './shortestOrder'
import type { RoutePlan } from './types'

/**
 * What factoring in road climbs came to for this plan, in a sentence. Null when it was not asked
 * for, and then nothing is shown.
 *
 * The planner counts every metre climbed as so many metres of road, in the order and on the roads
 * between the stops, so the route can come out a little longer than the shortest order, to climb
 * less. Often the shortest order stands, on the usual roads. That is said too, or ticking the box
 * would look as if it had done nothing.
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
  return `This route is ${longer} the shortest order and ${climbsAgainst(lessMetres)}.`
}

/**
 * Two climbs closer than this are as good as the same. The heights are good to about 10 m, and
 * the planner counts only rises of 10 m or more.
 */
const ABOUT_AS_MUCH_METRES = 10

/**
 * How the route's climb compares with the shortest order's. A route on other roads can be
 * shorter and climb a little more, and still come out ahead with the climb counted as road.
 */
function climbsAgainst(lessMetres: number): string {
  if (lessMetres >= ABOUT_AS_MUCH_METRES) {
    return `climbs ${metres(lessMetres)} less`
  }
  if (lessMetres <= -ABOUT_AS_MUCH_METRES) {
    return `climbs ${metres(-lessMetres)} more`
  }
  return 'climbs about as much'
}

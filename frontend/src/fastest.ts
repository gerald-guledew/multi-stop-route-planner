import { howLong } from './duration'
import { against, differsFromTheShortest, NO_FURTHER_KM } from './shortestOrder'
import type { RoutePlan } from './types'

/** Below half a minute, "1 min quicker" would claim twice the time saved, or more. */
const UNDER_HALF_A_MINUTE = 30

/**
 * What asking for the fastest order came to for this plan, in a sentence. Null when it was not
 * asked for, and then nothing is shown.
 *
 * The fastest order can be longer than the shortest, by keeping to faster roads. Often the
 * shortest order is the fastest as well, or within a minute of it, and the planner then keeps it:
 * a few seconds on clear roads are not worth kilometres more. That is said too, or picking the
 * fastest would look as if it had done nothing.
 *
 * A planner that measures straight lines has no times to choose by. It answers with the shortest
 * order and gives nothing to hold it against, which is how this knows.
 */
export function fastestSaid(askedFor: boolean, plan: RoutePlan): string | null {
  if (!askedFor) {
    return null
  }
  if (plan.shortestOrder === null || plan.shortestOrder.durationSeconds === null) {
    return 'This planner has no driving times to go on, so this is the shortest order.'
  }
  if (!differsFromTheShortest(plan)) {
    return 'The shortest order is also the fastest, to within a minute.'
  }

  const { furtherKm, quickerSeconds } = against(plan.shortestOrder, plan)
  const longer = furtherKm < NO_FURTHER_KM ? 'no longer than' : `${furtherKm.toFixed(2)} km longer than`
  // The planner takes another order only when it saves a minute. Should one come back that saves
  // less, a few seconds are not called a minute.
  const quicker = quickerSeconds < UNDER_HALF_A_MINUTE ? 'less than a minute' : howLong(quickerSeconds)
  return `This order is ${longer} the shortest and ${quicker} quicker.`
}

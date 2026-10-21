import { howLong } from './duration'
import { litresSaid } from './fuel'
import { against, differsFromTheShortest, NO_FURTHER_KM } from './shortestOrder'
import type { RoutePlan } from './types'

/** A drive a minute or more longer is said to be. Less than that is lost in the estimate. */
const A_MINUTE = 60

/**
 * What asking for the least fuel came to for this plan, in a sentence. Null when it was not
 * asked for, and then nothing is shown.
 *
 * Most often the shortest order takes the least fuel, since on a trip that ends where it began
 * the hills give back nearly all they cost. The planner takes another order only when it takes a
 * tenth of a litre less, and says so, or picking the least fuel would look as if it had done
 * nothing.
 *
 * Without a vehicle the planner is asked for the shortest order. With one whose weight it does
 * not know, or on roads read without heights, it cannot count the hills and answers with the
 * shortest order, giving nothing to hold it against, which is how this knows.
 */
export function leastFuelSaid(askedFor: boolean, hasVehicle: boolean, plan: RoutePlan): string | null {
  if (!askedFor) {
    return null
  }
  if (!hasVehicle) {
    return 'With no vehicle saved, this is the shortest order.'
  }
  if (plan.shortestOrder === null) {
    return 'The hills were not counted, so the fuel goes with the distance and the shortest order takes the least.'
  }
  if (!differsFromTheShortest(plan)) {
    return 'The shortest order takes the least fuel too, to within a tenth of a litre.'
  }

  const { furtherKm, quickerSeconds, lessLitres } = against(plan.shortestOrder, plan)
  const longer = furtherKm < NO_FURTHER_KM ? 'no longer than' : `${furtherKm.toFixed(2)} km longer than`
  const slower = -quickerSeconds >= A_MINUTE ? ` It takes ${howLong(-quickerSeconds)} more to drive.` : ''
  return `This order is ${longer} the shortest and takes ${litresSaid(lessLitres)} less fuel.${slower}`
}

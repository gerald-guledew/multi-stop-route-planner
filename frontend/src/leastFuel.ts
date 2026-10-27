import { howLong } from './duration'
import { litresSaid } from './fuel'
import { against, differsFromTheShortest, NO_FURTHER_KM } from './shortestOrder'
import type { RoutePlan } from './types'

/** A drive a minute or more longer or shorter is said to be. Less than that is lost in the estimate. */
const A_MINUTE = 60

/**
 * What asking for the least fuel came to for this plan, in a sentence. Null when it was not
 * asked for, and then nothing is shown.
 *
 * Another order than the shortest often takes less fuel, by keeping to the open road, where a
 * kilometre takes less than in town. It is often quicker too, and that is said. When the shortest
 * order takes the least, that is said as well, or picking the least fuel would look as if it had
 * done nothing.
 *
 * The least fuel is asked for only with a vehicle. On straight lines the planner knows neither how
 * fast the roads are nor how high, and answers with the shortest order, giving nothing to hold it
 * against, which is how this knows.
 */
export function leastFuelSaid(askedFor: boolean, plan: RoutePlan): string | null {
  if (!askedFor) {
    return null
  }
  if (plan.shortestOrder === null) {
    return 'On straight lines the fuel goes with the distance, so the shortest order takes the least.'
  }
  if (!differsFromTheShortest(plan)) {
    return 'The shortest order takes the least fuel too.'
  }

  const { furtherKm, quickerSeconds, lessLitres } = against(plan.shortestOrder, plan)
  const longer = furtherKm < NO_FURTHER_KM ? 'no longer than' : `${furtherKm.toFixed(2)} km longer than`
  const time =
    quickerSeconds >= A_MINUTE
      ? ` It is ${howLong(quickerSeconds)} quicker.`
      : -quickerSeconds >= A_MINUTE
        ? ` It takes ${howLong(-quickerSeconds)} more to drive.`
        : ''
  return `This order is ${longer} the shortest and takes ${litresSaid(lessLitres)} less fuel.${time}`
}

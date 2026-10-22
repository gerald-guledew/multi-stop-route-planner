/**
 * What the order of the stops is chosen for: the shortest trip, the shortest with road climbs
 * counted as road, the least driving time, or the least fuel.
 */
export type ChosenFor = 'distance' | 'distanceAndClimb' | 'time' | 'fuel'

/** The order picked on the screen: the shortest, the fastest, or the one taking the least fuel. */
export type Order = 'shortest' | 'fastest' | 'leastFuel'

/**
 * What the trip's choices ask the order to be chosen for. Road climbs are counted as road, so
 * they count for the shortest order and not for the others. A tick left in the box while another
 * order is picked is kept for when the shortest is picked again.
 *
 * The least fuel is a vehicle's. Without one there is no fuel to go by, and the order is the
 * shortest.
 */
export function chosenFor(choices: { order: Order; factorInRoadClimbs: boolean; hasVehicle: boolean }): ChosenFor {
  switch (choices.order) {
    case 'fastest':
      return 'time'
    case 'leastFuel':
      return choices.hasVehicle ? 'fuel' : 'distance'
    case 'shortest':
      return choices.factorInRoadClimbs ? 'distanceAndClimb' : 'distance'
  }
}

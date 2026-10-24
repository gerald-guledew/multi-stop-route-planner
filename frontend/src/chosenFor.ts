/** What the order of the stops is chosen for: the shortest trip, the least driving time, or the least fuel. */
export type ChosenFor = 'distance' | 'time' | 'fuel'

/** The order picked on the screen: the one taking the least fuel, or the fastest. */
export type Order = 'leastFuel' | 'fastest'

/**
 * What the order picked asks the order to be chosen for.
 *
 * The least fuel is a vehicle's. Without one there is no fuel to go by, and the order is the
 * shortest.
 */
export function chosenFor(choices: { order: Order; hasVehicle: boolean }): ChosenFor {
  switch (choices.order) {
    case 'fastest':
      return 'time'
    case 'leastFuel':
      return choices.hasVehicle ? 'fuel' : 'distance'
  }
}

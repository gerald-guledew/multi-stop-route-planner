/** What the order of the stops is chosen for: the shortest trip, the least driving time, or the least fuel. */
export type ChosenFor = 'distance' | 'time' | 'fuel'

/** The order picked on the screen: the one taking the least fuel, or the fastest. */
export type Order = 'leastFuel' | 'fastest'

/**
 * The order the screen goes by. The least fuel is a vehicle's, and without one there is no fuel to
 * go by, so it cannot be picked and the fastest stands in. The pick itself is kept, so the least
 * fuel comes back once a vehicle is saved.
 */
export function orderGoneBy(choices: { order: Order; hasVehicle: boolean }): Order {
  return choices.order === 'leastFuel' && !choices.hasVehicle ? 'fastest' : choices.order
}

/** What the order the screen goes by asks the order to be chosen for. */
export function chosenFor(choices: { order: Order; hasVehicle: boolean }): ChosenFor {
  return orderGoneBy(choices) === 'leastFuel' ? 'fuel' : 'time'
}

/** What the order of the stops is chosen for: the shortest trip, the least driving time, or the least fuel. */
export type ChosenFor = 'distance' | 'time' | 'fuel'

/** The order picked on the screen: the one taking the least fuel, or the fastest. */
export type Order = 'leastFuel' | 'fastest'

/**
 * Whether an order cannot be picked yet. The least fuel is a vehicle's, and without one there is
 * no fuel to go by.
 */
export function needsAVehicle(order: Order, hasVehicle: boolean): boolean {
  return order === 'leastFuel' && !hasVehicle
}

/**
 * The order the screen goes by. While the least fuel cannot be picked, the fastest stands in. The
 * pick itself is kept, so the least fuel comes back once a vehicle is saved.
 */
export function orderGoneBy(choices: { order: Order; hasVehicle: boolean }): Order {
  return needsAVehicle(choices.order, choices.hasVehicle) ? 'fastest' : choices.order
}

/** What the order the screen goes by asks the order to be chosen for. */
export function chosenFor(choices: { order: Order; hasVehicle: boolean }): ChosenFor {
  return orderGoneBy(choices) === 'leastFuel' ? 'fuel' : 'time'
}

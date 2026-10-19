/**
 * What the order of the stops is chosen for: the shortest trip, the shortest with road climbs
 * counted as road, or the least driving time.
 */
export type ChosenFor = 'distance' | 'distanceAndClimb' | 'time'

/**
 * What the trip's choices ask the order to be chosen for. Road climbs are counted as road, so
 * they count for the shortest order and not for the fastest. A tick left in the box while the
 * fastest order is picked is kept for when the shortest is picked again.
 */
export function chosenFor(choices: { fastest: boolean; factorInRoadClimbs: boolean }): ChosenFor {
  if (choices.fastest) {
    return 'time'
  }
  return choices.factorInRoadClimbs ? 'distanceAndClimb' : 'distance'
}

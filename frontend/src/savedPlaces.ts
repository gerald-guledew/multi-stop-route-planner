import { sitsOn } from './foundStart'
import type { Position } from './location'
import { withItsLegsLetGo, withLabelsInStep } from './stops'
import type { Place, SavedPlace } from './types'

/**
 * The rules about saved places and the trip. Plain functions with no React in them, so each
 * rule can be tested on its own.
 *
 * A saved place and a place in the trip are told to be the same spot by their position. One is
 * always copied from the other, so the numbers match to the last digit or it is another spot.
 */

/** The saved place on this exact spot, if there is one. */
export function savedAt(saved: SavedPlace[], place: Place): SavedPlace | undefined {
  return saved.find((candidate) => sitsOn(place, candidate))
}

/** A label that is only spaces is no name to save a place under. */
export function canBeSaved(place: Place): boolean {
  return place.name.trim() !== ''
}

/**
 * Whether the trip already goes there, as its start or as a stop. Asked of a saved place, and of a
 * place found by searching, which is copied from the same tables to the last digit.
 */
export function isInTrip(places: Place[], spot: Position): boolean {
  return places.some((place) => sitsOn(place, spot))
}

/** Whether the trip already starts there. */
export function isTheStart(places: Place[], saved: SavedPlace): boolean {
  return sitsOn(places[0], saved)
}

/** A saved place as a place in a trip: its name and where it is, and nothing about saving. */
export function asPlace(saved: SavedPlace): Place {
  return { name: saved.name, latitude: saved.latitude, longitude: saved.longitude }
}

/**
 * The trip with a saved place as its start. It takes the place of the start there was, which
 * is how a start the browser guessed badly gets put right. If the trip already went there as a
 * stop, that stop goes, because nobody calls at their own start on the way round. A stop-over on
 * a leg of either is let go.
 */
export function startingFrom(places: Place[], saved: SavedPlace): Place[] {
  const stopThere = places.findIndex((stop, at) => at > 0 && sitsOn(stop, saved))
  const legsGone = withItsLegsLetGo(
    stopThere > 0 ? withItsLegsLetGo(places, stopThere) : places,
    0,
  )
  const stops = legsGone.slice(1).filter((stop) => !sitsOn(stop, saved))
  return withLabelsInStep(legsGone, [asPlace(saved), ...stops])
}

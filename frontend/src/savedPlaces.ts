import { sitsOn } from './foundStart'
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

/** Whether the trip already goes there, as its start or as a stop. */
export function isInTrip(places: Place[], saved: SavedPlace): boolean {
  return places.some((place) => sitsOn(place, saved))
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
 * stop, that stop goes, because nobody calls at their own start on the way round.
 */
export function startingFrom(places: Place[], saved: SavedPlace): Place[] {
  const stops = places.slice(1).filter((stop) => !sitsOn(stop, saved))
  return [asPlace(saved), ...stops]
}

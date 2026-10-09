import type { DevicePosition, Position } from './location'
import type { Place } from './types'

/**
 * The rules about a start the browser found for you. Plain functions with no React in them,
 * so each rule can be tested on its own.
 */

/** A phone with GPS does better than this. Beyond it the pin is a guess worth owning up to. */
const ROUGH_BEYOND_METRES = 20

/**
 * Whether a place sits exactly where a position is. One was copied from the other when the
 * start was found, so the numbers are identical or the pin has since been moved.
 */
export function sitsOn(place: Position | undefined, position: Position | null): boolean {
  return (
    place !== undefined &&
    position !== null &&
    place.latitude === position.latitude &&
    place.longitude === position.longitude
  )
}

/**
 * Whether the start is still the browser's guess, and a rough one. Once the pin is dragged or
 * replaced the start is no longer a guess, so there is nothing to own up to.
 */
export function isRoughGuess(start: Place | undefined, here: DevicePosition | null): boolean {
  return (
    here !== null &&
    sitsOn(start, here) &&
    !here.correctedByHand &&
    here.accuracyMetres > ROUGH_BEYOND_METRES
  )
}

/** Said the way a person would: "80 m", not "83.4 m". From a kilometre up, in kilometres. */
export function roughly(metres: number): string {
  const toTheNearestTen = Math.round(metres / 10) * 10
  return toTheNearestTen < 1000 ? `${toTheNearestTen} m` : `${Math.round(metres / 1000)} km`
}

/**
 * What a later, better answer from the browser leaves behind.
 *
 * It takes the place of the earlier answer. If the start is still sitting on that earlier
 * answer, the start goes with it, keeping its name: it was only ever the browser's guess.
 *
 * A position the person has put right by hand is never replaced. They know better than the
 * browser, however sure the browser has become.
 */
export function afterABetterAnswer(
  places: Place[],
  devicePosition: DevicePosition | null,
  better: DevicePosition,
): { places: Place[]; devicePosition: DevicePosition | null; movedTheStart: boolean } {
  if (devicePosition?.correctedByHand) {
    return { places, devicePosition, movedTheStart: false }
  }

  const movedTheStart = sitsOn(places[0], devicePosition)

  return {
    places: movedTheStart
      ? places.map((place, at) =>
          at === 0 ? { ...place, latitude: better.latitude, longitude: better.longitude } : place,
        )
      : places,
    devicePosition: better,
    movedTheStart,
  }
}

/**
 * What a pin dragged to a new spot leaves behind.
 *
 * The pin keeps its name and its place in the list. If it was the start, still sitting where
 * the browser put it, then dragging it is the person saying where they really are, and they
 * know better than the browser: the device position goes with it and is marked as theirs.
 */
export function afterDragging(
  places: Place[],
  devicePosition: DevicePosition | null,
  index: number,
  latitude: number,
  longitude: number,
): { places: Place[]; devicePosition: DevicePosition | null } {
  const draggedTheGuess = index === 0 && sitsOn(places[0], devicePosition)

  return {
    places: places.map((place, at) => (at === index ? { ...place, latitude, longitude } : place)),
    devicePosition:
      draggedTheGuess && devicePosition
        ? { ...devicePosition, latitude, longitude, correctedByHand: true }
        : devicePosition,
  }
}

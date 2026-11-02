import { sitsOn } from './foundStart'
import type { Position } from './location'
import { defaultLabel } from './stops'
import type { Place } from './types'

/**
 * The rules about naming a pin from the address nearest it. A pin put down on the map has no name
 * of its own, so the list could only call it "Stop 3". Plain functions with no React in them, so
 * each rule can be tested on its own.
 */

/** What the start is called while it is only where the browser says this device is. */
export const MY_LOCATION = 'My location'

/**
 * How far off an address can be and still be where a pin is. Measured on 5,836 places whose street
 * is known: within 50 m the nearest address was on that street at least four times in five, and
 * past it about two times in three. A pin further off is near the address, and says so.
 */
const AT_THE_ADDRESS_METRES = 50

export function addressSaid(address: string, metres: number): string {
  return metres <= AT_THE_ADDRESS_METRES ? address : `Near ${address}`
}

/** Whether a place still has a name nobody gave it. */
function hasNoNameOfItsOwn(place: Place, turn: number): boolean {
  return place.name === MY_LOCATION || place.name === defaultLabel(turn)
}

/**
 * The places to find an address for. A start that is a rough guess is not one of them. It may be a
 * street out, and an address would claim to know the house, so it stays "My location" until the
 * browser does better or the pin is put right by hand.
 */
export function spotsWantingAName(places: Place[], startIsARoughGuess: boolean): Position[] {
  return places.filter(
    (place, turn) => hasNoNameOfItsOwn(place, turn) && !(turn === 0 && startIsARoughGuess),
  )
}

/**
 * The list with the places on a spot given a name, and marked as named from the address, so that
 * moving the pin takes the name away again. A name somebody gave a place is left alone. The very
 * same list if no place takes the name.
 */
export function namedAt(places: Place[], spot: Position, name: string): Place[] {
  const takesIt = (place: Place, turn: number) => sitsOn(place, spot) && hasNoNameOfItsOwn(place, turn)
  return places.some(takesIt)
    ? places.map((place, turn) =>
        takesIt(place, turn) ? { ...place, name, namedByAddress: true } : place,
      )
    : places
}

/** The list with a name somebody typed. It is theirs from then on, wherever the pin goes. */
export function withNameTyped(places: Place[], index: number, name: string): Place[] {
  return places.map((place, at) => {
    if (at !== index) {
      return place
    }
    const { namedByAddress: _, ...typed } = place
    return { ...typed, name }
  })
}

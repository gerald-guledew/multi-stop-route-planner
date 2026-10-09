import { describe, expect, it } from 'vitest'
import { afterDragging, isRoughGuess, roughly, sitsOn } from './foundStart'
import type { DevicePosition } from './location'
import type { Place } from './types'

/** What a laptop's browser said: this spot, good to about 90 metres. */
const guess: DevicePosition = { latitude: -36.86877, longitude: 174.78173, accuracyMetres: 93 }
const startOnTheGuess: Place = { name: 'My location', latitude: -36.86877, longitude: 174.78173 }
const office: Place = { name: 'Office', latitude: -36.8485, longitude: 174.7621 }

describe('a place sitting on a position', () => {
  it('is on it when the coordinates are the same', () => {
    expect(sitsOn(startOnTheGuess, guess)).toBe(true)
  })

  it('is not on it once either coordinate differs', () => {
    expect(sitsOn({ ...startOnTheGuess, latitude: -36.8688 }, guess)).toBe(false)
    expect(sitsOn({ ...startOnTheGuess, longitude: 174.7818 }, guess)).toBe(false)
  })

  it('is not on it when either is missing', () => {
    expect(sitsOn(undefined, guess)).toBe(false)
    expect(sitsOn(startOnTheGuess, null)).toBe(false)
  })
})

describe('owning up to a rough guess', () => {
  it('owns up when the start is the browser’s guess and the browser was unsure', () => {
    expect(isRoughGuess(startOnTheGuess, guess)).toBe(true)
  })

  it('says nothing when the browser was sure, as a phone with GPS is', () => {
    expect(isRoughGuess(startOnTheGuess, { ...guess, accuracyMetres: 8 })).toBe(false)
  })

  it('says nothing at exactly 20 metres, which is where rough begins', () => {
    expect(isRoughGuess(startOnTheGuess, { ...guess, accuracyMetres: 20 })).toBe(false)
    expect(isRoughGuess(startOnTheGuess, { ...guess, accuracyMetres: 21 })).toBe(true)
  })

  it('says nothing once the person has put the pin right by hand', () => {
    expect(isRoughGuess(startOnTheGuess, { ...guess, correctedByHand: true })).toBe(false)
  })

  it('says nothing when the start is somewhere else', () => {
    expect(isRoughGuess(office, guess)).toBe(false)
  })

  it('says nothing with no start, or with no position from the browser', () => {
    expect(isRoughGuess(undefined, guess)).toBe(false)
    expect(isRoughGuess(startOnTheGuess, null)).toBe(false)
  })
})

describe('saying a distance the way a person would', () => {
  it.each([
    [83.4, '80 m'],
    [93, '90 m'],
    [65, '70 m'],
    [24, '20 m'],
    [994, '990 m'],
    // Rounds to a thousand metres, which nobody says.
    [996, '1 km'],
    [1400, '1 km'],
    [5200, '5 km'],
  ])('%d metres is "%s"', (metres, said) => {
    expect(roughly(metres)).toBe(said)
  })
})

describe('dragging a pin', () => {
  const home = { latitude: -36.86896, longitude: 174.78316 }

  it('moves the pin and keeps its name and its place in the list', () => {
    const moved = afterDragging([startOnTheGuess, office], null, 1, -36.85, 174.77)

    expect(moved.places).toEqual([startOnTheGuess, { name: 'Office', latitude: -36.85, longitude: 174.77 }])
  })

  it('takes the device position along when the pin dragged was the browser’s guess', () => {
    const moved = afterDragging([startOnTheGuess, office], guess, 0, home.latitude, home.longitude)

    expect(moved.places[0]).toEqual({ name: 'My location', ...home })
    expect(moved.devicePosition).toEqual({ ...home, accuracyMetres: 93, correctedByHand: true })
  })

  it('goes on taking it along if the same pin is dragged again', () => {
    const once = afterDragging([startOnTheGuess], guess, 0, home.latitude, home.longitude)
    const twice = afterDragging(once.places, once.devicePosition, 0, -36.87, 174.79)

    expect(twice.devicePosition).toMatchObject({ latitude: -36.87, longitude: 174.79, correctedByHand: true })
  })

  it('leaves the device position alone when a stop is dragged', () => {
    const moved = afterDragging([startOnTheGuess, office], guess, 1, -36.85, 174.77)

    expect(moved.devicePosition).toBe(guess)
  })

  it('leaves the device position alone when the start was picked, not found', () => {
    const moved = afterDragging([office], guess, 0, -36.85, 174.77)

    expect(moved.devicePosition).toBe(guess)
  })

  it('does not change the list it was given', () => {
    const places = [startOnTheGuess, office]
    afterDragging(places, guess, 0, home.latitude, home.longitude)

    expect(places).toEqual([startOnTheGuess, office])
    expect(guess.correctedByHand).toBeUndefined()
  })
})

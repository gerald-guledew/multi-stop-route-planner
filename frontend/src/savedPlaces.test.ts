import { describe, expect, it } from 'vitest'
import { asPlace, canBeSaved, isInTrip, isTheStart, savedAt, startingFrom } from './savedPlaces'
import type { Place, SavedPlace } from './types'

const home: SavedPlace = { id: 7, name: 'Home', latitude: -36.8689416667, longitude: 174.7832303333 }
const office: SavedPlace = { id: 9, name: 'The office', latitude: -36.8485, longitude: 174.7621 }

const guess: Place = { name: 'My location', latitude: -36.8701, longitude: 174.7845 }
const mall: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }
const shop: Place = { name: 'Corner dairy', latitude: -36.9, longitude: 174.8, keepInPlace: true }

describe('finding the saved place on a spot', () => {
  it('finds the one saved exactly there', () => {
    const pin: Place = { name: 'Anything', latitude: home.latitude, longitude: home.longitude }

    expect(savedAt([office, home], pin)).toBe(home)
  })

  it('goes by where the pin is, not by what it is called', () => {
    const namesake: Place = { name: 'Home', latitude: -36.9, longitude: 174.8 }

    expect(savedAt([home], namesake)).toBeUndefined()
  })

  it('does not count a pin dragged a little way off', () => {
    const dragged: Place = { name: 'Home', latitude: home.latitude + 0.00001, longitude: home.longitude }

    expect(savedAt([home], dragged)).toBeUndefined()
  })

  it('finds nothing when nothing is saved', () => {
    expect(savedAt([], mall)).toBeUndefined()
  })
})

describe('whether a place can be saved', () => {
  it('can, once it has a label', () => {
    expect(canBeSaved(mall)).toBe(true)
  })

  it.each(['', '   ', '\t'])('cannot, under the label "%s"', (label) => {
    expect(canBeSaved({ ...mall, name: label })).toBe(false)
  })
})

describe('whether the trip already goes to a saved place', () => {
  it('does when it starts there', () => {
    expect(isInTrip([asPlace(home), mall], home)).toBe(true)
    expect(isTheStart([asPlace(home), mall], home)).toBe(true)
  })

  it('does when it stops there, without that being its start', () => {
    expect(isInTrip([guess, asPlace(home)], home)).toBe(true)
    expect(isTheStart([guess, asPlace(home)], home)).toBe(false)
  })

  it('does not when it goes somewhere else', () => {
    expect(isInTrip([guess, mall], home)).toBe(false)
    expect(isTheStart([guess, mall], home)).toBe(false)
  })

  it('does not while there is no trip', () => {
    expect(isInTrip([], home)).toBe(false)
    expect(isTheStart([], home)).toBe(false)
  })
})

describe('a saved place as a place in the trip', () => {
  it('carries its name and its position', () => {
    expect(asPlace(home)).toEqual({ name: 'Home', latitude: -36.8689416667, longitude: 174.7832303333 })
  })

  it('leaves its id behind, which the planner knows nothing about', () => {
    expect(asPlace(home)).not.toHaveProperty('id')
  })
})

describe('starting the trip from a saved place', () => {
  it('begins a trip that had nothing in it', () => {
    expect(startingFrom([], home)).toEqual([asPlace(home)])
  })

  it('takes the place of the start there was, and keeps the stops', () => {
    expect(startingFrom([guess, mall, shop], home)).toEqual([asPlace(home), mall, shop])
  })

  it('leaves a stop that is kept in its place still kept', () => {
    expect(startingFrom([guess, mall, shop], home)[2].keepInPlace).toBe(true)
  })

  it('is not also called at on the way, when it was a stop before', () => {
    expect(startingFrom([guess, mall, asPlace(home), shop], home)).toEqual([asPlace(home), mall, shop])
  })

  it('changes nothing when the trip starts there already', () => {
    expect(startingFrom([asPlace(home), mall], home)).toEqual([asPlace(home), mall])
  })

  it('does not alter the list it was given', () => {
    const trip = [guess, mall]

    startingFrom(trip, home)

    expect(trip).toEqual([guess, mall])
  })
})

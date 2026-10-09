import { describe, expect, it } from 'vitest'
import { anyKeptInPlace, ordinal, toggleKeptInPlace, withoutPlace } from './stops'
import type { Place } from './types'

const home: Place = { name: 'Home', latitude: -36.8689, longitude: 174.7832 }
const office: Place = { name: 'Office', latitude: -36.8485, longitude: 174.7621 }
const mall: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }

describe('saying which turn a stop has', () => {
  it.each([
    [1, '1st'],
    [2, '2nd'],
    [3, '3rd'],
    [4, '4th'],
    [10, '10th'],
    // The teens do not follow their last digit.
    [11, '11th'],
    [12, '12th'],
    [13, '13th'],
    [21, '21st'],
    [22, '22nd'],
    [111, '111th'],
  ])('%d is "%s"', (turn, said) => {
    expect(ordinal(turn)).toBe(said)
  })
})

describe('keeping a stop in its place', () => {
  it('marks the stop and leaves the others as they were', () => {
    expect(toggleKeptInPlace([home, office, mall], 1)).toEqual([
      home,
      { ...office, keepInPlace: true },
      mall,
    ])
  })

  it('lets a kept stop go again', () => {
    const kept = toggleKeptInPlace([home, office, mall], 1)

    expect(toggleKeptInPlace(kept, 1)[1].keepInPlace).toBe(false)
  })

  it('will not mark the start, which is first anyway', () => {
    const places = [home, office]

    expect(toggleKeptInPlace(places, 0)).toBe(places)
  })

  it('does not change the list it was given', () => {
    const places = [home, office]
    toggleKeptInPlace(places, 1)

    expect(places).toEqual([home, office])
  })
})

describe('knowing whether anything is kept', () => {
  it('is false for a list nobody has marked', () => {
    expect(anyKeptInPlace([home, office, mall])).toBe(false)
  })

  it('is true once a stop is kept', () => {
    expect(anyKeptInPlace([home, { ...office, keepInPlace: true }, mall])).toBe(true)
  })

  it('is false again when the stop is let go', () => {
    expect(anyKeptInPlace([home, { ...office, keepInPlace: false }])).toBe(false)
  })
})

describe('taking a place out of the list', () => {
  it('removes it and keeps the rest in order', () => {
    expect(withoutPlace([home, office, mall], 1)).toEqual([home, mall])
  })

  it('leaves a kept stop kept when a different stop goes', () => {
    const keptMall = { ...mall, keepInPlace: true }

    expect(withoutPlace([home, office, keptMall], 1)).toEqual([home, keptMall])
  })

  it('stops keeping a stop that has just become the start', () => {
    const keptOffice = { ...office, keepInPlace: true }

    expect(withoutPlace([home, keptOffice, mall], 0)).toEqual([office, mall])
  })

  it('copes with the last place going', () => {
    expect(withoutPlace([home], 0)).toEqual([])
  })
})

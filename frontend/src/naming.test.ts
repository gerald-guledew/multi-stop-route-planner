import { describe, expect, it } from 'vitest'
import { spotOf } from './height'
import {
  addressSaid,
  MY_LOCATION,
  namedAt,
  namingSteps,
  spotsWantingAName,
  withNameTyped,
} from './naming'
import type { Place } from './types'

const start: Place = { name: 'Start', latitude: -36.917, longitude: 174.8414 }
const stop: Place = { name: 'Stop 1', latitude: -36.8604, longitude: 174.7778 }
const tower: Place = { name: 'Sky Tower', latitude: -36.8485, longitude: 174.7621 }

describe('saying what address a pin stands at', () => {
  it('gives the address when it is where the pin is', () => {
    expect(addressSaid('277 Broadway, Newmarket', 12)).toBe('277 Broadway, Newmarket')
  })

  it('still gives it at exactly 50 metres', () => {
    expect(addressSaid('277 Broadway, Newmarket', 50)).toBe('277 Broadway, Newmarket')
  })

  it('says near past that', () => {
    expect(addressSaid('277 Broadway, Newmarket', 51)).toBe('Near 277 Broadway, Newmarket')
  })
})

describe('which pins want a name', () => {
  it('takes a start with the label it was given', () => {
    expect(spotsWantingAName([start], false)).toEqual([start])
  })

  it('takes a stop with the label of its turn', () => {
    expect(spotsWantingAName([tower, stop], false)).toEqual([stop])
  })

  it('leaves a stop alone once somebody has named it', () => {
    expect(spotsWantingAName([tower], false)).toEqual([])
  })

  it('leaves a pin alone that has its address already', () => {
    const named: Place = { ...stop, name: '277 Broadway, Newmarket', namedByAddress: true }

    expect(spotsWantingAName([named], false)).toEqual([])
  })

  it('takes my location', () => {
    const here: Place = { ...start, name: MY_LOCATION }

    expect(spotsWantingAName([here], false)).toEqual([here])
  })

  it('leaves my location alone while it is a rough guess', () => {
    const here: Place = { ...start, name: MY_LOCATION }

    expect(spotsWantingAName([here, stop], true)).toEqual([stop])
  })
})

describe('what to do about the pins wanting a name', () => {
  const nothingKnown = new Map<string, string | null>()

  it('asks about a spot nothing is known of', () => {
    expect(namingSteps([stop], nothingKnown, false)).toEqual({ toAsk: [stop], toGive: [] })
  })

  it('does not ask again while the answer is on its way', () => {
    const onItsWay = new Map([[spotOf(stop), null]])

    expect(namingSteps([stop], onItsWay, false)).toEqual({ toAsk: [], toGive: [] })
  })

  it('asks once about a spot two pins stand on', () => {
    expect(namingSteps([stop, { ...stop }], nothingKnown, false).toAsk).toHaveLength(1)
  })

  it('gives a name that is known', () => {
    const known = new Map([[spotOf(stop), '12 Museum Circuit, Parnell']])

    expect(namingSteps([stop], known, false)).toEqual({
      toAsk: [],
      toGive: [{ spot: stop, name: '12 Museum Circuit, Parnell' }],
    })
  })

  it('holds a name back while a plan is on its way', () => {
    const known = new Map([[spotOf(stop), '12 Museum Circuit, Parnell']])

    expect(namingSteps([stop], known, true)).toEqual({ toAsk: [], toGive: [] })
  })

  it('still asks while a plan is on its way', () => {
    expect(namingSteps([stop], nothingKnown, true).toAsk).toEqual([stop])
  })

  it('gives every name that is known at once', () => {
    const known = new Map([
      [spotOf(start), '286 Mount Wellington Highway'],
      [spotOf(stop), '12 Museum Circuit, Parnell'],
    ])

    expect(namingSteps([start, stop], known, false).toGive).toHaveLength(2)
  })
})

describe('giving a pin its name', () => {
  it('names the place on that spot and marks it as named from the address', () => {
    const named = namedAt([tower, stop], stop, '12 Museum Circuit, Parnell')

    expect(named[1]).toEqual({ ...stop, name: '12 Museum Circuit, Parnell', namedByAddress: true })
  })

  it('leaves the other places as they are', () => {
    const named = namedAt([tower, stop], stop, '12 Museum Circuit, Parnell')

    expect(named[0]).toBe(tower)
  })

  it('replaces my location', () => {
    const named = namedAt([{ ...start, name: MY_LOCATION }], start, '286 Mount Wellington Highway')

    expect(named[0].name).toBe('286 Mount Wellington Highway')
  })

  it('leaves alone a name typed while the address was on its way', () => {
    const typed: Place = { ...stop, name: 'Mum' }

    expect(namedAt([tower, typed], stop, '12 Museum Circuit, Parnell')).toEqual([tower, typed])
  })

  it('gives back the same list when no place takes the name', () => {
    const places = [tower]

    expect(namedAt(places, stop, '12 Museum Circuit, Parnell')).toBe(places)
  })

  it('keeps the marks a stop has', () => {
    const kept: Place = { ...stop, keepInPlace: true, stopOver: true }

    expect(namedAt([tower, kept], stop, 'Near a place')[1]).toMatchObject({
      keepInPlace: true,
      stopOver: true,
    })
  })
})

describe('typing a name', () => {
  it('makes it the name of that place', () => {
    expect(withNameTyped([tower, stop], 1, 'Mum')[1].name).toBe('Mum')
  })

  it('makes it theirs, so that moving the pin keeps it', () => {
    const named: Place = { ...stop, name: '12 Museum Circuit, Parnell', namedByAddress: true }

    expect(withNameTyped([named], 0, 'Mum')[0]).toEqual({ ...stop, name: 'Mum' })
  })

  it('leaves the other places as they are', () => {
    expect(withNameTyped([tower, stop], 1, 'Mum')[0]).toBe(tower)
  })
})

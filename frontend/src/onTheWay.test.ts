import { describe, expect, it } from 'vitest'
import { addedSaid, alreadyInTrip, betweenSaid, canAddOnTheWay, heldInPlannedOrder, withStopOn } from './onTheWay'
import type { Place, PlaceAlong, RoutePlan } from './types'

const start: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }
const museum: Place = { name: 'Museum', latitude: -36.8604, longitude: 174.7778 }
const tower: Place = { name: 'Sky Tower', latitude: -36.8485, longitude: 174.7621 }
const airport: Place = { name: 'Airport', latitude: -37.007, longitude: 174.783 }
const fuel: Place = { name: 'Z Ellerslie', latitude: -36.899, longitude: 174.813 }

/** The plan of a trip, driving to its places in the order given. */
function planOf(...route: Place[]): RoutePlan {
  return {
    route,
    legs: [],
    totalDistanceKm: 40,
    totalDurationSeconds: 2700,
    totalClimbMetres: null,
    totalDescentMetres: null,
    totalFuelLitres: null,
    fuelCountsHills: null,
    fuelCountsSpeed: null,
    enteredOrderDistanceKm: 44,
    shortestOrder: null,
    fastestOrder: null,
    leastFuelOrder: null,
    ordersChecked: 6,
  }
}

function found(more: Partial<PlaceAlong> = {}): PlaceAlong {
  return { ...fuel, kind: 'poi', detail: 'Ellerslie', leg: 0, kmAdded: 1.2, secondsAdded: 240, ...more }
}

describe('adding a stop on the way', () => {
  // Entered as the tower, then the museum, then the airport. The plan drives to the museum first.
  const trip = [start, tower, museum, airport]
  const planned = planOf(start, museum, tower, airport)

  it('puts it straight after the place its leg sets off from', () => {
    expect(withStopOn(trip, planned, 1, fuel)).toEqual([start, tower, museum, fuel, airport])
  })

  it('puts it straight after the start on the first leg', () => {
    expect(withStopOn(trip, planned, 0, fuel)).toEqual([start, fuel, tower, museum, airport])
  })

  it('puts it after the last stop on the way back to the start', () => {
    const roundTrip = planOf(start, museum, tower, airport, start)

    expect(withStopOn(trip, roundTrip, 3, fuel)).toEqual([start, tower, museum, airport, fuel])
  })

  it('moves a label nobody changed along with its turn', () => {
    const stop1: Place = { name: 'Stop 1', latitude: -36.86, longitude: 174.77 }
    const stop2: Place = { name: 'Stop 2', latitude: -36.85, longitude: 174.76 }

    expect(withStopOn([start, stop1, stop2], planOf(start, stop1, stop2), 0, fuel).map((place) => place.name))
      .toEqual(['Sylvia Park', 'Z Ellerslie', 'Stop 2', 'Stop 3'])
  })
})

describe('planning with a stop on the way', () => {
  const planned = planOf(start, museum, tower, airport)

  it('keeps every stop at the turn the plan gave it, with the new one between the two', () => {
    const trip = withStopOn([start, tower, museum, airport], planned, 1, fuel)

    expect(heldInPlannedOrder(trip, planned, false, 1, fuel)).toEqual([
      start,
      { ...museum, keepInPlace: true },
      { ...fuel, keepInPlace: true },
      { ...tower, keepInPlace: true },
      { ...airport, keepInPlace: true },
    ])
  })

  it('sends the start as it is, since nothing moves it', () => {
    const kept = { ...start, keepInPlace: true }
    const trip = withStopOn([kept, tower, museum, airport], planned, 0, fuel)

    expect(heldInPlannedOrder(trip, planned, false, 0, fuel)?.[0]).toEqual(start)
  })

  it('leaves out the way back to the start, which the plan of a round trip ends with', () => {
    const roundTrip = planOf(start, museum, tower, airport, start)
    const trip = withStopOn([start, tower, museum, airport], roundTrip, 3, fuel)

    expect(heldInPlannedOrder(trip, roundTrip, true, 3, fuel)?.map((place) => place.name)).toEqual([
      'Sylvia Park',
      'Museum',
      'Sky Tower',
      'Airport',
      'Z Ellerslie',
    ])
  })

  it('sends the names the trip shows, after the labels have moved', () => {
    const stop1: Place = { name: 'Stop 1', latitude: -36.86, longitude: 174.77 }
    const stop2: Place = { name: 'Stop 2', latitude: -36.85, longitude: 174.76 }
    const plan = planOf(start, stop2, stop1)
    const trip = withStopOn([start, stop1, stop2], plan, 0, fuel)

    expect(heldInPlannedOrder(trip, plan, false, 0, fuel)?.map((place) => place.name)).toEqual([
      'Sylvia Park',
      'Z Ellerslie',
      'Stop 3',
      'Stop 2',
    ])
  })

  it('holds nothing when the plan drives to a place the trip no longer has', () => {
    expect(heldInPlannedOrder([start, tower, fuel], planned, false, 0, fuel)).toBeNull()
  })
})

describe('what a stop on the way adds', () => {
  it('says the minutes it adds', () => {
    expect(addedSaid(found({ secondsAdded: 240 }))).toBe('+4 min')
  })

  it('says the hours of a long way round', () => {
    expect(addedSaid(found({ secondsAdded: 3900 }))).toBe('+1 h 5 min')
  })

  it('says a stop that adds under a minute is on the way', () => {
    expect(addedSaid(found({ secondsAdded: 59 }))).toBe('On the way')
    expect(addedSaid(found({ secondsAdded: 0 }))).toBe('On the way')
  })

  it('says the distance it adds on straight lines', () => {
    expect(addedSaid(found({ secondsAdded: null, kmAdded: 1.24 }))).toBe('+1.2 km')
    expect(addedSaid(found({ secondsAdded: null, kmAdded: 0.04 }))).toBe('On the way')
  })

  it('says which two places its leg runs between', () => {
    expect(betweenSaid(planOf(start, museum, tower), 1)).toBe('between Museum and Sky Tower')
  })
})

describe('a place found along the way', () => {
  it('is in the trip when the trip goes to that very spot', () => {
    expect(alreadyInTrip([start, fuel], found())).toBe(true)
  })

  it('is not when the trip only goes near it', () => {
    expect(alreadyInTrip([start, { ...fuel, longitude: 174.8131 }], found())).toBe(false)
  })
})

describe('whether a stop can be added on the way', () => {
  const plan = planOf(start, museum, tower)
  /** The start and as many stops. */
  const trip = (stops: number): Place[] => [
    start,
    ...Array.from({ length: stops }, (_, n) => ({ name: `Stop ${n + 1}`, latitude: -36.8 - n / 100, longitude: 174.7 })),
  ]

  it('can once the trip is planned', () => {
    expect(canAddOnTheWay(trip(2), false, plan, false)).toBe(true)
  })

  it('cannot before there is a plan, which says where the way goes', () => {
    expect(canAddOnTheWay(trip(2), false, null, false)).toBe(false)
  })

  it('cannot while a plan is on its way', () => {
    expect(canAddOnTheWay(trip(2), false, plan, true)).toBe(false)
  })

  it('cannot once the trip has all the stops the Google Maps link takes', () => {
    expect(canAddOnTheWay(trip(9), false, plan, false)).toBe(true)
    expect(canAddOnTheWay(trip(10), false, plan, false)).toBe(false)
    expect(canAddOnTheWay(trip(8), true, plan, false)).toBe(true)
    expect(canAddOnTheWay(trip(9), true, plan, false)).toBe(false)
  })
})

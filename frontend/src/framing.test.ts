import { describe, expect, it } from 'vitest'
import {
  closeEnoughToCheck,
  everywhereTheFindsAre,
  everywhereTheTripGoes,
  FOLDED_LIST_COVERS,
  sameFinds,
  STREET_ZOOM,
  topPaddingForFinds,
} from './framing'
import type { Leg, Place, PlaceAlong, RoutePlan } from './types'

const mall: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }
const museum: Place = { name: 'Auckland Museum', latitude: -36.8604, longitude: 174.7778 }

function legAlong(path: [number, number][]): Leg {
  return {
    from: mall.name,
    to: museum.name,
    distanceKm: 9.6,
    durationSeconds: null,
    climbMetres: null,
    descentMetres: null,
    fuelLitres: null,
    path,
    turns: [],
  }
}

function planWith(legs: Leg[]): RoutePlan {
  return {
    route: [mall, museum, mall],
    legs,
    totalDistanceKm: 19.2,
    totalDurationSeconds: null,
    totalClimbMetres: null,
    totalDescentMetres: null,
    totalFuelLitres: null,
    fuelCountsHills: null,
    fuelCountsSpeed: null,
    enteredOrderDistanceKm: 19.2,
    shortestOrder: null,
    fastestOrder: null,
    leastFuelOrder: null,
    ordersChecked: 1,
  }
}

describe('how close the map goes to a place picked from a list', () => {
  it('goes from a view of the city down to the street', () => {
    expect(closeEnoughToCheck(12)).toBe(STREET_ZOOM)
  })

  it('stays closer if the map already is', () => {
    expect(closeEnoughToCheck(STREET_ZOOM + 2)).toBe(STREET_ZOOM + 2)
  })
})

describe('everywhere the trip goes', () => {
  it('is the places alone until there is a plan', () => {
    expect(everywhereTheTripGoes([mall, museum], null)).toEqual([
      [mall.latitude, mall.longitude],
      [museum.latitude, museum.longitude],
    ])
  })

  it('takes in a road that bends out beyond every place', () => {
    const outOfTheWay: [number, number] = [-36.95, 174.9]
    const points = everywhereTheTripGoes([mall, museum], planWith([legAlong([outOfTheWay])]))

    expect(points).toContainEqual(outOfTheWay)
  })

  it('adds nothing for a plan on straight lines, which has no roads to draw', () => {
    expect(everywhereTheTripGoes([mall, museum], planWith([legAlong([]), legAlong([])]))).toEqual([
      [mall.latitude, mall.longitude],
      [museum.latitude, museum.longitude],
    ])
  })

  it('is nothing for an empty trip', () => {
    expect(everywhereTheTripGoes([], null)).toEqual([])
  })
})

describe('everywhere the places found along the route are', () => {
  const road: [number, number] = [-36.89, 174.81]
  const offTheEnd: PlaceAlong = {
    name: 'Z Grafton',
    latitude: -36.862,
    longitude: 174.765,
    kind: 'poi',
    detail: 'Grafton',
    leg: 1,
    kmAdded: 2.1,
    secondsAdded: 300,
  }

  it('takes in the route and each place found, so that how far each is from the route shows', () => {
    const points = everywhereTheFindsAre(planWith([legAlong([road]), legAlong([])]), [offTheEnd])

    expect(points).toContainEqual([mall.latitude, mall.longitude])
    expect(points).toContainEqual([museum.latitude, museum.longitude])
    expect(points).toContainEqual(road)
    expect(points).toContainEqual([offTheEnd.latitude, offTheEnd.longitude])
  })

  it('is the route alone when nothing was found', () => {
    const plan = planWith([legAlong([road]), legAlong([])])

    expect(everywhereTheFindsAre(plan, [])).toEqual(everywhereTheTripGoes(plan.route, plan))
  })
})

describe('whether the map moves for the places found', () => {
  const cafe: PlaceAlong = {
    name: 'Corner Cafe',
    latitude: -36.88,
    longitude: 174.8,
    kind: 'poi',
    detail: 'Ellerslie',
    leg: 0,
    kmAdded: 0.4,
    secondsAdded: 60,
  }
  const fuel: PlaceAlong = { ...cafe, name: 'Z Ellerslie', latitude: -36.899, longitude: 174.813 }

  it('stays for the same places asked for again', () => {
    expect(sameFinds([cafe, fuel], [{ ...cafe }, { ...fuel, secondsAdded: 90 }])).toBe(true)
  })

  it('moves for other places, more or fewer, or the same in another order', () => {
    expect(sameFinds([cafe], [fuel])).toBe(false)
    expect(sameFinds([cafe], [cafe, fuel])).toBe(false)
    expect(sameFinds([cafe, fuel], [fuel, cafe])).toBe(false)
  })

  it('moves for a place of the same name somewhere else', () => {
    expect(sameFinds([cafe], [{ ...cafe, latitude: -36.9 }])).toBe(false)
  })
})

describe('how far down the places found are framed', () => {
  it('leaves room for the search box and the folded list on a phone', () => {
    expect(topPaddingForFinds(56)).toBe(40 + 56 + FOLDED_LIST_COVERS)
  })

  it('leaves the usual margin where nothing covers the map', () => {
    expect(topPaddingForFinds(0)).toBe(40)
  })
})

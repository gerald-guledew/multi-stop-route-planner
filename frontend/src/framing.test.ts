import { describe, expect, it } from 'vitest'
import { closeEnoughToCheck, everywhereTheTripGoes, STREET_ZOOM } from './framing'
import type { Leg, Place, RoutePlan } from './types'

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
    enteredOrderDistanceKm: 19.2,
    shortestOrder: null,
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

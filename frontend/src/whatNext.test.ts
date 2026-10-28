import { describe, expect, it } from 'vitest'
import type { Place, RoutePlan } from './types'
import { whatNext } from './whatNext'

const home: Place = { name: 'Home', latitude: -36.9146, longitude: 174.8392 }
const office: Place = { name: 'Office', latitude: -36.8485, longitude: 174.7621 }
const mall: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }

const plan: RoutePlan = {
  route: [home, office, mall, home],
  legs: [],
  // As the API sends it, already rounded to two decimals.
  totalDistanceKm: 19.31,
  totalDurationSeconds: null,
  totalClimbMetres: null,
  totalDescentMetres: null,
  totalFuelLitres: null,
  fuelCountsHills: null,
  fuelCountsSpeed: null,
  enteredOrderDistanceKm: 21,
  shortestOrder: null,
  fastestOrder: null,
  leastFuelOrder: null,
  ordersChecked: 2,
}

describe('what the bar along the bottom of a phone says', () => {
  it('asks for a start, and offers to find you, while the trip is empty', () => {
    expect(whatNext([], null)).toEqual({ says: 'Set your start', step: 'locate' })
  })

  it('asks for a stop once there is a start, with nothing to press yet', () => {
    expect(whatNext([home], null)).toEqual({ says: 'Now add a stop', step: null })
  })

  it('offers to plan as soon as there is one stop', () => {
    expect(whatNext([home, office], null)).toEqual({ says: '1 stop', step: 'plan' })
  })

  it('counts the stops, leaving out the start', () => {
    expect(whatNext([home, office, mall], null)).toEqual({ says: '2 stops', step: 'plan' })
  })

  it('gives the distance once planned, and offers to hand the order over', () => {
    expect(whatNext([home, office, mall], plan)).toEqual({
      says: '19.31 km',
      alsoSays: '2 stops',
      step: 'open',
    })
  })

  it('says how long the drive takes as well, when the roads say', () => {
    const byRoad = { ...plan, totalDurationSeconds: 5057 }

    expect(whatNext([home, office, mall], byRoad)).toEqual({
      says: '19.31 km',
      alsoSays: '1 h 24 min, 2 stops',
      step: 'open',
    })
  })

  it('has one line to say until there is a plan', () => {
    expect(whatNext([home, office], null).alsoSays).toBeUndefined()
  })
})

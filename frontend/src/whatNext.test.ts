import { describe, expect, it } from 'vitest'
import type { Place, RoutePlan } from './types'
import { whatNext } from './whatNext'

const home: Place = { name: 'Home', latitude: -36.8689, longitude: 174.7832 }
const office: Place = { name: 'Office', latitude: -36.8485, longitude: 174.7621 }
const mall: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }

const plan: RoutePlan = {
  route: [home, office, mall, home],
  legs: [],
  // As the API sends it, already rounded to two decimals.
  totalDistanceKm: 19.31,
  enteredOrderDistanceKm: 21,
  ordersChecked: 2,
}

describe('what the bar along the bottom of a phone says', () => {
  it('asks for a start, and offers to find you, while the trip is empty', () => {
    expect(whatNext([], null, 10)).toEqual({ says: 'Set your start', step: 'locate' })
  })

  it('asks for a stop once there is a start, with nothing to press yet', () => {
    expect(whatNext([home], null, 10)).toEqual({ says: 'Now add a stop', step: null })
  })

  it('offers to plan as soon as there is one stop', () => {
    expect(whatNext([home, office], null, 10)).toEqual({ says: '1 stop', step: 'plan' })
  })

  it('counts the stops, leaving out the start', () => {
    expect(whatNext([home, office, mall], null, 10)).toEqual({ says: '2 stops', step: 'plan' })
  })

  it('gives the distance once planned, and offers to hand the order over', () => {
    expect(whatNext([home, office, mall], plan, 10)).toEqual({ says: '19.31 km, 2 stops', step: 'open' })
  })

  it('offers nothing while there are more stops than can be planned', () => {
    const tooMany = [home, ...Array.from({ length: 11 }, () => office)]

    expect(whatNext(tooMany, null, 10)).toEqual({ says: '11 stops, 10 at most', step: null })
  })

  it('still plans at exactly the most stops allowed', () => {
    const full = [home, ...Array.from({ length: 10 }, () => office)]

    expect(whatNext(full, null, 10).step).toBe('plan')
  })
})

import { describe, expect, it } from 'vitest'
import { fastestSaid } from './fastest'
import type { RoutePlan, ShortestOrder } from './types'

/** A round trip of 62.88 km that takes 4,980 seconds, on roads read without their heights. */
function aPlan(shortestOrder: ShortestOrder | null): RoutePlan {
  return {
    route: [],
    legs: [],
    totalDistanceKm: 62.88,
    totalDurationSeconds: 4980,
    totalClimbMetres: null,
    totalDescentMetres: null,
    totalFuelLitres: null,
    fuelCountsHills: null,
    fuelCountsSpeed: null,
    enteredOrderDistanceKm: 70.1,
    shortestOrder,
    ordersChecked: 24,
  }
}

/** The shortest order, with as many seconds on top of the plan's as given. */
function shortest(distanceKm: number, slowerBySeconds: number): ShortestOrder {
  return { distanceKm, durationSeconds: 4980 + slowerBySeconds, climbMetres: null, descentMetres: null, fuelLitres: null }
}

describe('saying what asking for the fastest order came to', () => {
  it('says how much longer the order is than the shortest, and how much quicker', () => {
    expect(fastestSaid(true, aPlan(shortest(61.68, 360)))).toBe(
      'This order is 1.20 km longer than the shortest and 6 min quicker.',
    )
  })

  it('gives a saving of an hour or more in hours and minutes', () => {
    expect(fastestSaid(true, aPlan(shortest(50.11, 3720)))).toBe(
      'This order is 12.77 km longer than the shortest and 1 h 2 min quicker.',
    )
  })

  it('does not round a few seconds up to a minute', () => {
    // The planner takes another order only when it saves a minute. Should it ever take one
    // for less, that is not said to be a minute.
    expect(fastestSaid(true, aPlan(shortest(62.85, 20)))).toBe(
      'This order is 0.03 km longer than the shortest and less than a minute quicker.',
    )
  })

  it('calls an order as long as the shortest no longer', () => {
    expect(fastestSaid(true, aPlan(shortest(62.88, 120)))).toBe(
      'This order is no longer than the shortest and 2 min quicker.',
    )
  })

  it('says so when the shortest order is the fastest too, or picking it would seem to do nothing', () => {
    expect(fastestSaid(true, aPlan(shortest(62.88, 0)))).toBe(
      'The shortest order is also the fastest, to within a minute.',
    )
  })

  it('says nothing when the fastest order was not asked for', () => {
    expect(fastestSaid(false, aPlan(null))).toBeNull()
    expect(fastestSaid(false, aPlan(shortest(61.68, 360)))).toBeNull()
  })

  it('says so when the planner had no times to go on', () => {
    const onStraightLines = { ...aPlan(null), totalDurationSeconds: null }

    expect(fastestSaid(true, onStraightLines)).toBe(
      'This planner has no driving times to go on, so this is the shortest order.',
    )
  })
})

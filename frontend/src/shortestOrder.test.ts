import { describe, expect, it } from 'vitest'
import { differsFromTheShortest, shortestSaid } from './shortestOrder'
import type { RoutePlan } from './types'

/** A round trip of 62.88 km that takes 83 minutes and climbs 561 m, and whatever else is said of it. */
function aPlan(more: Partial<RoutePlan> = {}): RoutePlan {
  return {
    route: [],
    legs: [],
    totalDistanceKm: 62.88,
    totalDurationSeconds: 4980,
    totalClimbMetres: 561,
    totalDescentMetres: 561,
    totalFuelLitres: null,
    fuelCountsHills: null,
    enteredOrderDistanceKm: 70.1,
    shortestOrder: null,
    ordersChecked: 24,
    ...more,
  }
}

const SHORTER_AND_HILLIER = { distanceKm: 62.58, durationSeconds: 4930, climbMetres: 666, descentMetres: 666 }
const THE_SAME = { distanceKm: 62.88, durationSeconds: 4980, climbMetres: 561, descentMetres: 561 }

describe('telling the order chosen from the shortest', () => {
  it('says they differ when the order is longer', () => {
    expect(differsFromTheShortest(aPlan({ shortestOrder: SHORTER_AND_HILLIER }))).toBe(true)
  })

  it('says they differ when the order only climbs less', () => {
    const asLong = { ...THE_SAME, climbMetres: 562, descentMetres: 562 }

    expect(differsFromTheShortest(aPlan({ shortestOrder: asLong }))).toBe(true)
  })

  it('says they differ when the order is only quicker', () => {
    const asLongAndSlower = { ...THE_SAME, durationSeconds: 5100 }

    expect(differsFromTheShortest(aPlan({ shortestOrder: asLongAndSlower }))).toBe(true)
  })

  it('says they do not when the figures are the same', () => {
    expect(differsFromTheShortest(aPlan({ shortestOrder: THE_SAME }))).toBe(false)
  })

  it('says they do not when the same figures come without the climb', () => {
    const withoutHeights = aPlan({
      totalClimbMetres: null,
      totalDescentMetres: null,
      shortestOrder: { ...THE_SAME, climbMetres: null, descentMetres: null },
    })

    expect(differsFromTheShortest(withoutHeights)).toBe(false)
  })

  it('says they do not when there is no shortest order to hold it against', () => {
    expect(differsFromTheShortest(aPlan())).toBe(false)
  })
})

describe('saying what the shortest order comes to', () => {
  it('gives how far it is and how much it climbs, beside an order chosen to climb less', () => {
    expect(shortestSaid(SHORTER_AND_HILLIER, 'distanceAndClimb')).toBe('62.58 km, 666 m up')
  })

  it('gives the climb and not the descent', () => {
    const oneWay = { distanceKm: 66.16, durationSeconds: 5190, climbMetres: 678, descentMetres: 634 }

    expect(shortestSaid(oneWay, 'distanceAndClimb')).toBe('66.16 km, 678 m up')
  })

  it('puts a comma in the thousands, and keeps two decimals', () => {
    const farHillier = { distanceKm: 141.7, durationSeconds: 9000, climbMetres: 1403, descentMetres: 1403 }

    expect(shortestSaid(farHillier, 'distanceAndClimb')).toBe('141.70 km, 1,403 m up')
  })

  it('gives how far it is and how long it takes, beside the fastest order', () => {
    const slower = { distanceKm: 61.68, durationSeconds: 5340, climbMetres: null, descentMetres: null }

    expect(shortestSaid(slower, 'time')).toBe('61.68 km, 1 h 29 min')
  })

  it('gives the time and not the climb beside the fastest order, when it knows both', () => {
    expect(shortestSaid(SHORTER_AND_HILLIER, 'time')).toBe('62.58 km, 1 h 22 min')
  })
})

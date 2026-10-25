import { describe, expect, it } from 'vitest'
import { bothChoices, bothSaid, comeOutTheSame, sideBySide, type BothChoices } from './bothChoices'
import type { OrderTotals, RoutePlan } from './types'

/** A round trip of 41.44 km that takes 52 minutes and 3.04 L, chosen for the least fuel. */
function aPlan(more: Partial<RoutePlan> = {}): RoutePlan {
  return {
    route: [],
    legs: [],
    totalDistanceKm: 41.44,
    totalDurationSeconds: 3120,
    totalClimbMetres: 312,
    totalDescentMetres: 312,
    totalFuelLitres: 3.04,
    fuelCountsHills: true,
    fuelCountsSpeed: true,
    enteredOrderDistanceKm: 45.1,
    shortestOrder: null,
    fastestOrder: null,
    leastFuelOrder: null,
    ordersChecked: 24,
    ...more,
  }
}

/** Another order, its time and its fuel as given, 41.37 km long and as hilly as the plan. */
function anOrder(durationSeconds: number | null, fuelLitres: number | null, distanceKm = 41.37): OrderTotals {
  return { distanceKm, durationSeconds, climbMetres: 312, descentMetres: 312, fuelLitres }
}

/** The least fuel and the fastest, from their times and their fuel. */
function both(leastFuel: [number, number], fastest: [number, number]): BothChoices {
  return {
    leastFuel: anOrder(leastFuel[0], leastFuel[1], 41.44),
    fastest: anOrder(fastest[0], fastest[1]),
    shown: 'leastFuel',
  }
}

describe('setting the two choices side by side', () => {
  it('sets the plan beside the fastest order when it was made for the least fuel', () => {
    const fastest = anOrder(3000, 3.12)

    expect(bothChoices(aPlan({ fastestOrder: fastest }))).toEqual({
      leastFuel: { distanceKm: 41.44, durationSeconds: 3120, climbMetres: 312, descentMetres: 312, fuelLitres: 3.04 },
      fastest,
      shown: 'leastFuel',
    })
  })

  it('sets the plan beside the least fuel when it was made for the fastest', () => {
    const leastFuel = anOrder(3240, 2.98)

    const sideBySide = bothChoices(aPlan({ leastFuelOrder: leastFuel }))

    expect(sideBySide?.leastFuel).toEqual(leastFuel)
    expect(sideBySide?.fastest.durationSeconds).toBe(3120)
    expect(sideBySide?.shown).toBe('fastest')
  })

  it('has nothing to set beside a plan that gives no other choice', () => {
    expect(bothChoices(aPlan())).toBeNull()
  })

  it('has nothing to set side by side without the fuel of each', () => {
    // A plan made without a vehicle says nothing about fuel.
    expect(bothChoices(aPlan({ totalFuelLitres: null, fastestOrder: anOrder(3000, null) }))).toBeNull()
  })

  it('has nothing to set side by side without the time of each', () => {
    expect(bothChoices(aPlan({ totalDurationSeconds: null, fastestOrder: anOrder(null, 3.12) }))).toBeNull()
  })
})

describe('telling whether the two come out the same', () => {
  it('finds them the same when every figure is, to the last digit given', () => {
    const same = { leastFuel: anOrder(3000, 3.04), fastest: anOrder(3000, 3.04), shown: 'fastest' as const }

    expect(comeOutTheSame(same)).toBe(true)
  })

  it('tells them apart by a hundredth of a litre', () => {
    const apart = { leastFuel: anOrder(3000, 3.04), fastest: anOrder(3000, 3.05), shown: 'fastest' as const }

    expect(comeOutTheSame(apart)).toBe(false)
  })

  it('tells them apart by a second', () => {
    const apart = { leastFuel: anOrder(3001, 3.04), fastest: anOrder(3000, 3.04), shown: 'fastest' as const }

    expect(comeOutTheSame(apart)).toBe(false)
  })
})

describe('the figures side by side', () => {
  it('says each figure as the rest of the screen does', () => {
    expect(sideBySide(both([3120, 3.04], [3000, 3.12]))).toEqual([
      { says: 'Distance', leastFuel: '41.44 km', fastest: '41.37 km' },
      { says: 'Driving time', leastFuel: '52 min', fastest: '50 min' },
      { says: 'Fuel', leastFuel: '3.0 L', fastest: '3.1 L' },
    ])
  })
})

describe('saying what the two come to against each other', () => {
  it('says the fuel the least fuel saves and the time the fastest does', () => {
    expect(bothSaid(both([3360, 2.86], [3120, 3.12]))).toBe('Least fuel saves 0.3 L, and Fastest is 4 min quicker.')
  })

  it('says a small difference too', () => {
    expect(bothSaid(both([3120, 3.04], [3060, 3.08]))).toBe(
      'Least fuel saves under 0.1 L, and Fastest is 1 min quicker.',
    )
  })

  it('calls under half a minute about the same time', () => {
    expect(bothSaid(both([3120, 2.94], [3100, 3.12]))).toBe(
      'Least fuel saves 0.2 L, and they take about the same time.',
    )
  })

  it('says when the two take the same fuel', () => {
    expect(bothSaid(both([3120, 3.04], [3000, 3.04]))).toBe('They take the same fuel, and Fastest is 2 min quicker.')
  })

  it('says so when the fastest takes less fuel as well, as it can on a one-way trip', () => {
    expect(bothSaid(both([3180, 3.08], [3000, 3.05]))).toBe('Fastest saves under 0.1 L and is 3 min quicker.')
  })

  it('says so when the least fuel is quicker as well, by less than the minute the fastest needs', () => {
    expect(bothSaid(both([3080, 2.98], [3120, 3.04]))).toBe('Least fuel saves 0.1 L and is 1 min quicker.')
  })

  it('says when they differ by little more than the roads', () => {
    // 70 m apart, with the same fuel and ten seconds between them.
    expect(bothSaid(both([3120, 3.04], [3110, 3.04]))).toBe('They take the same fuel and about the same time.')
  })

  it('says when the two come out the same', () => {
    const same = { leastFuel: anOrder(3000, 3.04), fastest: anOrder(3000, 3.04), shown: 'leastFuel' as const }

    expect(bothSaid(same)).toBe('Least fuel and Fastest come out the same for this trip.')
  })
})

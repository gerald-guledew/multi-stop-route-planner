import { describe, expect, it } from 'vitest'
import { bothChoices, comeOutTheSame, otherChoiceSaid, type BothChoices } from './bothChoices'
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

/** The least fuel and the fastest, from their times and their fuel, the least fuel on the map. */
function both(
  leastFuel: [number, number],
  fastest: [number, number],
  shown: BothChoices['shown'] = 'leastFuel',
): BothChoices {
  return { leastFuel: anOrder(leastFuel[0], leastFuel[1], 41.44), fastest: anOrder(fastest[0], fastest[1]), shown }
}

describe('putting the two choices together', () => {
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

    const together = bothChoices(aPlan({ leastFuelOrder: leastFuel }))

    expect(together?.leastFuel).toEqual(leastFuel)
    expect(together?.fastest.durationSeconds).toBe(3120)
    expect(together?.shown).toBe('fastest')
  })

  it('has nothing to set beside a plan that gives no other choice', () => {
    expect(bothChoices(aPlan())).toBeNull()
  })

  it('has nothing to hold against each other without the fuel of each', () => {
    // A plan made without a vehicle says nothing about fuel.
    expect(bothChoices(aPlan({ totalFuelLitres: null, fastestOrder: anOrder(3000, null) }))).toBeNull()
  })

  it('has nothing to hold against each other without the time of each', () => {
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

describe('saying the other choice in one line', () => {
  it('says beside the least fuel how much quicker the fastest is, and the fuel it takes', () => {
    expect(otherChoiceSaid(both([3360, 2.86], [3120, 3.16]))).toBe('Fastest: 4 min quicker, 0.3 L more.')
  })

  it('says beside the fastest the fuel the least fuel saves, and the time it takes', () => {
    expect(otherChoiceSaid(both([3360, 2.86], [3120, 3.16], 'fastest'))).toBe('Least fuel: 0.3 L less, 4 min slower.')
  })

  it('says when the two come out the same', () => {
    const same = (shown: BothChoices['shown']) => ({ leastFuel: anOrder(3000, 3.04), fastest: anOrder(3000, 3.04), shown })

    expect(otherChoiceSaid(same('leastFuel'))).toBe('Same as Fastest.')
    expect(otherChoiceSaid(same('fastest'))).toBe('Same as Least fuel.')
  })

  it('calls under a minute about the same time', () => {
    expect(otherChoiceSaid(both([3120, 2.94], [3070, 3.14]))).toBe('Fastest: about the same time, 0.2 L more.')
  })

  it('says a minute', () => {
    expect(otherChoiceSaid(both([3120, 3.04], [3060, 3.08]))).toBe('Fastest: 1 min quicker, under 0.1 L more.')
  })

  it('calls the least fuel about the same time when it is quicker by less than a minute', () => {
    // The fastest keeps the shortest order unless another saves a minute.
    expect(otherChoiceSaid(both([3080, 2.98], [3120, 3.04], 'fastest'))).toBe(
      'Least fuel: 0.1 L less, about the same time.',
    )
  })

  it('says when the two take the same fuel', () => {
    expect(otherChoiceSaid(both([3120, 3.04], [3000, 3.04]))).toBe('Fastest: 2 min quicker, the same fuel.')
  })

  it('says so when the fastest takes less fuel as well, as it can on a one-way trip', () => {
    expect(otherChoiceSaid(both([3180, 3.08], [3000, 3.05]))).toBe('Fastest: 3 min quicker, under 0.1 L less.')
  })

  it('says when they differ by little more than the roads', () => {
    // 70 m apart, with the same fuel and ten seconds between them.
    expect(otherChoiceSaid(both([3120, 3.04], [3110, 3.04]))).toBe('Fastest: about the same time and fuel.')
  })
})

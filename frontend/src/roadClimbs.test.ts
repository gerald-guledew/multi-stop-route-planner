import { describe, expect, it } from 'vitest'
import { roadClimbsSaid } from './roadClimbs'
import type { RoutePlan } from './types'

/** A round trip of 62.88 km that climbs 561 m, and whatever else is said of it. */
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

describe('saying what factoring in road climbs came to', () => {
  it('says how much longer the order is than the shortest, and how much less it climbs', () => {
    expect(roadClimbsSaid(true, aPlan({ shortestOrder: SHORTER_AND_HILLIER }))).toBe(
      'This order is 0.30 km longer than the shortest and climbs 105 m less.',
    )
  })

  it('goes by what the shortest order climbs, and not by what it comes down', () => {
    // One way, a trip can end lower than it began, and the two are then different figures.
    const oneWay = aPlan({
      totalDistanceKm: 66.71,
      totalClimbMetres: 575,
      totalDescentMetres: 531,
      shortestOrder: { distanceKm: 66.16, durationSeconds: 5190, climbMetres: 678, descentMetres: 634 },
    })

    expect(roadClimbsSaid(true, oneWay)).toBe(
      'This order is 0.55 km longer than the shortest and climbs 103 m less.',
    )
  })

  it('says so when the shortest order stands, or the box would seem to have done nothing', () => {
    expect(roadClimbsSaid(true, aPlan({ shortestOrder: THE_SAME }))).toBe(
      'With road climbs factored in, the shortest order still comes out best.',
    )
  })

  it('says nothing when road climbs were not asked for', () => {
    expect(roadClimbsSaid(false, aPlan())).toBeNull()
    expect(roadClimbsSaid(false, aPlan({ shortestOrder: SHORTER_AND_HILLIER }))).toBeNull()
  })

  it('says so when the planner had no heights to go on', () => {
    const withoutHeights = aPlan({ totalClimbMetres: null, totalDescentMetres: null })

    expect(roadClimbsSaid(true, withoutHeights)).toBe(
      'Road climbs were not factored in. This planner has no heights to go on, so this is the shortest order.',
    )
  })

  it('calls an order as long as the shortest no longer', () => {
    const asLong = { distanceKm: 62.88, durationSeconds: 4980, climbMetres: 600, descentMetres: 600 }

    expect(roadClimbsSaid(true, aPlan({ shortestOrder: asLong }))).toBe(
      'This order is no longer than the shortest and climbs 39 m less.',
    )
  })

  it('puts a comma in the thousands', () => {
    const farHillier = { distanceKm: 60.08, durationSeconds: 4790, climbMetres: 1761, descentMetres: 1761 }

    expect(roadClimbsSaid(true, aPlan({ shortestOrder: farHillier }))).toBe(
      'This order is 2.80 km longer than the shortest and climbs 1,200 m less.',
    )
  })

  it('does not say less than nought for a metre lost in rounding', () => {
    const aMetreTheWrongWay = { distanceKm: 62.87, durationSeconds: 4975, climbMetres: 560, descentMetres: 560 }

    expect(roadClimbsSaid(true, aPlan({ shortestOrder: aMetreTheWrongWay }))).toBe(
      'This order is 0.01 km longer than the shortest and climbs 0 m less.',
    )
  })
})

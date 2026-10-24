import { describe, expect, it } from 'vitest'
import { leastFuelSaid } from './leastFuel'
import type { RoutePlan, ShortestOrder } from './types'

/** A round trip of 62.88 km that takes 83 minutes, climbs 561 m and takes 4.70 L. */
function aPlan(shortestOrder: ShortestOrder | null, more: Partial<RoutePlan> = {}): RoutePlan {
  return {
    route: [],
    legs: [],
    totalDistanceKm: 62.88,
    totalDurationSeconds: 4980,
    totalClimbMetres: 561,
    totalDescentMetres: 561,
    totalFuelLitres: 4.7,
    fuelCountsHills: true,
    fuelCountsSpeed: true,
    enteredOrderDistanceKm: 70.1,
    shortestOrder,
    ordersChecked: 24,
    ...more,
  }
}

const THE_SAME: ShortestOrder = {
  distanceKm: 62.88,
  durationSeconds: 4980,
  climbMetres: 561,
  descentMetres: 561,
  fuelLitres: 4.7,
}

describe('saying what asking for the least fuel came to', () => {
  it('says how much longer the order is than the shortest, and how much less fuel it takes', () => {
    const shorterAndThirstier = { ...THE_SAME, distanceKm: 62.46, durationSeconds: 4950, fuelLitres: 4.84 }

    expect(leastFuelSaid(true, true, aPlan(shorterAndThirstier))).toBe(
      'This order is 0.42 km longer than the shortest and takes 0.1 L less fuel.',
    )
  })

  it('says how much longer the drive takes, when it is a minute or more', () => {
    const shorterQuickerAndThirstier = { ...THE_SAME, distanceKm: 62.46, durationSeconds: 4560, fuelLitres: 4.9 }

    expect(leastFuelSaid(true, true, aPlan(shorterQuickerAndThirstier))).toBe(
      'This order is 0.42 km longer than the shortest and takes 0.2 L less fuel. It takes 7 min more to drive.',
    )
  })

  it('says how much quicker the drive is, when it is a minute or more', () => {
    const shorterSlowerAndThirstier = { ...THE_SAME, distanceKm: 62.46, durationSeconds: 5100, fuelLitres: 4.84 }

    expect(leastFuelSaid(true, true, aPlan(shorterSlowerAndThirstier))).toBe(
      'This order is 0.42 km longer than the shortest and takes 0.1 L less fuel. It is 2 min quicker.',
    )
  })

  it('says under a tenth of a litre for a saving that small, and not none', () => {
    const shorterAndALittleThirstier = { ...THE_SAME, distanceKm: 62.46, fuelLitres: 4.72 }

    expect(leastFuelSaid(true, true, aPlan(shorterAndALittleThirstier))).toBe(
      'This order is 0.42 km longer than the shortest and takes under 0.1 L less fuel.',
    )
  })

  it('says so when the shortest order takes the least fuel too, or picking it would seem to do nothing', () => {
    expect(leastFuelSaid(true, true, aPlan(THE_SAME))).toBe('The shortest order takes the least fuel too.')
  })

  it('says why it is the shortest order on straight lines', () => {
    expect(leastFuelSaid(true, true, aPlan(null, { fuelCountsHills: false, fuelCountsSpeed: false }))).toBe(
      'On straight lines the fuel goes with the distance, so the shortest order takes the least.',
    )
  })

  it('says it is the shortest order when there is no vehicle to work the fuel out for', () => {
    expect(leastFuelSaid(true, false, aPlan(null, { totalFuelLitres: null, fuelCountsHills: null }))).toBe(
      'With no vehicle saved, this is the shortest order.',
    )
  })

  it('says nothing when the least fuel was not asked for', () => {
    expect(leastFuelSaid(false, true, aPlan(null))).toBeNull()
    expect(leastFuelSaid(false, true, aPlan(THE_SAME))).toBeNull()
  })
})

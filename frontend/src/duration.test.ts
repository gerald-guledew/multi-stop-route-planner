import { describe, expect, it } from 'vitest'
import { howLong, legSaid } from './duration'

describe('saying how long a drive takes', () => {
  it.each([
    [1260, '21 min'],
    [569, '9 min'],
    // Halfway rounds up, as a person would.
    [90, '2 min'],
    [89, '1 min'],
  ])('says %d seconds as "%s"', (seconds, said) => {
    expect(howLong(seconds)).toBe(said)
  })

  it('calls a drive of a few seconds a minute, not no time at all', () => {
    expect(howLong(20)).toBe('1 min')
  })

  it('says no time only for no time', () => {
    expect(howLong(0)).toBe('0 min')
  })

  it.each([
    [5057, '1 h 24 min'],
    [3660, '1 h 1 min'],
    [36_000, '10 h'],
  ])('goes into hours from an hour up: %d seconds is "%s"', (seconds, said) => {
    expect(howLong(seconds)).toBe(said)
  })

  it('leaves the minutes off an exact number of hours', () => {
    expect(howLong(7200)).toBe('2 h')
  })

  it('carries into the hour when the minutes round up to sixty', () => {
    expect(howLong(3599)).toBe('1 h')
    expect(howLong(3570)).toBe('1 h')
    expect(howLong(3569)).toBe('59 min')
  })
})

describe('saying how far and how long a leg is', () => {
  const leg = {
    from: 'Sky Tower',
    to: 'Airport',
    distanceKm: 20.29,
    durationSeconds: 1260,
    climbMetres: null,
    descentMetres: null,
    fuelLitres: null,
    path: [],
    turns: [],
  }

  it('gives the distance and the time when the roads say how long', () => {
    expect(legSaid(leg)).toBe('20.29 km · 21 min')
  })

  it('gives the distance alone on straight lines, which say nothing of time', () => {
    expect(legSaid({ ...leg, durationSeconds: null })).toBe('20.29 km')
  })

  it('still gives a time for a leg that takes none, since none is not the same as unknown', () => {
    expect(legSaid({ ...leg, distanceKm: 0, durationSeconds: 0 })).toBe('0.00 km · 0 min')
  })

  it('keeps two decimals on the distance', () => {
    expect(legSaid({ ...leg, distanceKm: 6.9, durationSeconds: null })).toBe('6.90 km')
  })

  it('gives the fuel the leg takes when the plan was made for a vehicle', () => {
    expect(legSaid({ ...leg, fuelLitres: 1.52 })).toBe('20.29 km · 21 min · 1.5 L')
    expect(legSaid({ ...leg, durationSeconds: null, fuelLitres: 1.52 })).toBe('20.29 km · 1.5 L')
  })
})

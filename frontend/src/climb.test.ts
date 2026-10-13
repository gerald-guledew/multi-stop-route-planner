import { describe, expect, it } from 'vitest'
import { climbSaid } from './climb'

describe('saying how much a drive climbs and descends', () => {
  it('gives the way up first, then the way down', () => {
    expect(climbSaid(120, 95)).toBe('120 m up, 95 m down')
  })

  it('says both of a drive over a hill to a place at the same height', () => {
    expect(climbSaid(80, 80)).toBe('80 m up, 80 m down')
  })

  it('calls a level drive level, since that is known', () => {
    expect(climbSaid(0, 0)).toBe('0 m up, 0 m down')
  })

  it('still says a drive that only goes down', () => {
    expect(climbSaid(0, 340)).toBe('0 m up, 340 m down')
  })

  it('puts a comma in the thousands', () => {
    expect(climbSaid(1076, 12)).toBe('1,076 m up, 12 m down')
    expect(climbSaid(12, 1076)).toBe('12 m up, 1,076 m down')
  })

  it('says nothing when the roads were read without their heights', () => {
    expect(climbSaid(null, null)).toBeNull()
  })

  it('says nothing with only half the answer', () => {
    expect(climbSaid(120, null)).toBeNull()
    expect(climbSaid(null, 95)).toBeNull()
  })
})

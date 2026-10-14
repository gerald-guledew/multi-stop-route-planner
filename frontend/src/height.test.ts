import { describe, expect, it } from 'vitest'
import { heightSaid, spotOf } from './height'

describe('saying how high a pin stands', () => {
  it('says the height above sea level', () => {
    expect(heightSaid(195)).toBe('195 m above sea level')
    expect(heightSaid(1)).toBe('1 m above sea level')
  })

  it('says when the pin is at sea level', () => {
    expect(heightSaid(0)).toBe('At sea level')
    expect(heightSaid(0.4)).toBe('At sea level')
  })

  it('says how far below sea level a pin stands', () => {
    expect(heightSaid(-1)).toBe('1 m below sea level')
  })

  it('rounds to the metre, as the planner does', () => {
    expect(heightSaid(194.6)).toBe('195 m above sea level')
  })

  it('says nothing when the height is not known', () => {
    expect(heightSaid(null)).toBeNull()
    expect(heightSaid(undefined)).toBeNull()
  })
})

describe('telling one spot from another', () => {
  it('tells apart two pins a metre apart', () => {
    expect(spotOf({ latitude: -36.87781, longitude: 174.7639 })).not.toBe(
      spotOf({ latitude: -36.8778, longitude: 174.7639 }),
    )
  })

  it('takes the same spot for the same pin', () => {
    expect(spotOf({ latitude: -36.8778, longitude: 174.7639 })).toBe(
      spotOf({ latitude: -36.8778, longitude: 174.7639 }),
    )
  })
})

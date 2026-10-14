import { describe, expect, it } from 'vitest'
import { litresSaid } from './fuel'

describe('saying an amount of fuel', () => {
  it('says it to a tenth of a litre', () => {
    expect(litresSaid(3.88)).toBe('3.9 L')
    expect(litresSaid(0.52)).toBe('0.5 L')
    expect(litresSaid(12.34)).toBe('12.3 L')
  })

  it('says a little for a short drive, not none', () => {
    expect(litresSaid(0.04)).toBe('under 0.1 L')
    expect(litresSaid(0)).toBe('under 0.1 L')
  })

  it('rounds a twentieth of a litre up to a tenth', () => {
    expect(litresSaid(0.05)).toBe('0.1 L')
  })
})

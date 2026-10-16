import { describe, expect, it } from 'vitest'
import { fuelNote, litresSaid } from './fuel'

describe('saying what the fuel rests on', () => {
  it('says a dashboard figure is the figure for every kilometre', () => {
    expect(fuelNote('dashboard', false)).toBe(
      "Fuel is your vehicle's figure for every kilometre. Hills and speed do not change it yet.",
    )
  })

  it('says a figure from the register is the official one', () => {
    expect(fuelNote('register', false)).toBe(
      "Fuel is your car's official figure for every kilometre. Hills and speed do not change it yet.",
    )
  })

  it('says when the hills were counted', () => {
    expect(fuelNote('dashboard', true)).toBe(
      "Fuel is your vehicle's figure for every kilometre, with more uphill and less downhill. Speed does not change it yet.",
    )
    expect(fuelNote('register', true)).toBe(
      "Fuel is your car's official figure for every kilometre, with more uphill and less downhill. Speed does not change it yet.",
    )
  })
})

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

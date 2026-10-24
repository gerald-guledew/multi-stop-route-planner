import { describe, expect, it } from 'vitest'
import { fuelNote, litresSaid } from './fuel'

describe('saying what the fuel rests on', () => {
  it('says a dashboard figure is the figure for every kilometre, on straight lines', () => {
    expect(fuelNote('dashboard', false, false)).toBe(
      "Fuel is your vehicle's figure for every kilometre. Hills and speed do not change it.",
    )
  })

  it('says a figure from the register is the official one', () => {
    expect(fuelNote('register', false, false)).toBe(
      "Fuel is your car's official figure for every kilometre. Hills and speed do not change it.",
    )
  })

  it('says when the figure was taken higher in town than on the open road', () => {
    expect(fuelNote('dashboard', false, true)).toBe(
      "Fuel is your vehicle's figure, higher in town and lower on the open road. Hills do not change it.",
    )
  })

  it('says when the hills were counted too', () => {
    expect(fuelNote('dashboard', true, true)).toBe(
      "Fuel is your vehicle's figure, higher in town and lower on the open road, with more uphill and less downhill.",
    )
    expect(fuelNote('register', true, true)).toBe(
      "Fuel is your car's official figure, higher in town and lower on the open road, with more uphill and less downhill.",
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

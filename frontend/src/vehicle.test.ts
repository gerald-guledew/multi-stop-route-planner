import { describe, expect, it } from 'vitest'
import { litresFrom, SIZES, vehicleSaid } from './vehicle'

describe('saying what the vehicle is', () => {
  it('gives its size and its fuel use', () => {
    expect(vehicleSaid({ litresPer100Km: 7.5, size: 'medium' })).toBe('Medium car, 7.5 L/100 km')
    expect(vehicleSaid({ litresPer100Km: 11, size: 'large' })).toBe('Large car or SUV, 11 L/100 km')
    expect(vehicleSaid({ litresPer100Km: 5.2, size: 'small' })).toBe('Small car, 5.2 L/100 km')
  })

  it('offers every size, smallest first', () => {
    expect(SIZES.map((choice) => choice.size)).toEqual(['small', 'medium', 'large'])
  })
})

describe('reading the fuel use as it was typed', () => {
  it('reads a figure with a point or a comma', () => {
    expect(litresFrom('7.5')).toEqual({ litres: 7.5 })
    expect(litresFrom('7,5')).toEqual({ litres: 7.5 })
    expect(litresFrom(' 11 ')).toEqual({ litres: 11 })
  })

  it('takes the limits themselves', () => {
    expect(litresFrom('2')).toEqual({ litres: 2 })
    expect(litresFrom('30')).toEqual({ litres: 30 })
  })

  it('asks for a figure when nothing was typed', () => {
    expect(litresFrom('  ')).toEqual({ problem: 'Type the fuel use your dashboard shows, such as 7.5.' })
  })

  it('refuses what is not a figure', () => {
    for (const typed of ['seven', '7.5 L', '1e1', '-7', '7..5', '.5']) {
      expect(litresFrom(typed)).toEqual({ problem: 'Type the fuel use as a number, such as 7.5.' })
    }
  })

  it('refuses a figure no petrol car shows', () => {
    for (const typed of ['1.9', '30.1', '75']) {
      expect(litresFrom(typed)).toEqual({ problem: 'A petrol car uses between 2 and 30 L/100 km.' })
    }
  })
})

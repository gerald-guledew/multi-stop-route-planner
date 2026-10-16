import { describe, expect, it } from 'vitest'
import {
  carSaid,
  dashboardLitresFrom,
  engineSaid,
  litresFrom,
  SIZES,
  variantKey,
  variantToPick,
  vehicleSaid,
  vehicleToSave,
} from './vehicle'
import type { Car, CarVariant } from './types'

const SWIFT: Car = { make: 'SUZUKI', model: 'SWIFT', year: 2018, engineLitres: 1.2 }
const SWIFT_SPORT: Car = { ...SWIFT, engineLitres: 1.4 }

const found = (car: Car, litresPer100Km: number): CarVariant => ({ car, litresPer100Km, cars: 100 })

describe('saying what the vehicle is', () => {
  it('gives the size and the fuel use of a vehicle entered by hand', () => {
    const entered = { figureFrom: 'dashboard' as const, car: null, kilograms: 1270 }
    expect(vehicleSaid({ ...entered, litresPer100Km: 7.5, size: 'medium' })).toBe('Medium car, 7.5 L/100 km')
    expect(vehicleSaid({ ...entered, litresPer100Km: 11, size: 'large' })).toBe('Large car or SUV, 11 L/100 km')
    expect(vehicleSaid({ ...entered, litresPer100Km: 5.2, size: 'small' })).toBe('Small car, 5.2 L/100 km')
  })

  it('names a car found in the register, and calls its figure the official one', () => {
    expect(vehicleSaid({ litresPer100Km: 4.8, figureFrom: 'register', size: null, car: SWIFT, kilograms: 890 })).toBe(
      '2018 SUZUKI SWIFT 1.2 L, 4.8 L/100 km (official figure)',
    )
  })

  it('does not call the dashboard figure of a car found in the register the official one', () => {
    expect(vehicleSaid({ litresPer100Km: 5.6, figureFrom: 'dashboard', size: null, car: SWIFT, kilograms: 890 })).toBe(
      '2018 SUZUKI SWIFT 1.2 L, 5.6 L/100 km',
    )
  })

  it('offers every size, smallest first', () => {
    expect(SIZES.map((choice) => choice.size)).toEqual(['small', 'medium', 'large'])
  })
})

describe('saying a car from the register', () => {
  it('gives an engine size with its tenth, as it is said', () => {
    expect(engineSaid(1.2)).toBe('1.2 L')
    expect(engineSaid(2)).toBe('2.0 L')
  })

  it('names the car as the register does', () => {
    expect(carSaid(SWIFT)).toBe('2018 SUZUKI SWIFT 1.2 L')
  })

  it('tells the engines of one year apart', () => {
    expect(variantKey(found(SWIFT, 4.8))).not.toBe(variantKey(found(SWIFT_SPORT, 6.1)))
  })
})

describe('picking an engine when the list arrives', () => {
  const swifts = [found(SWIFT, 4.8), found(SWIFT_SPORT, 6.1)]

  it('picks the one saved, when the saved car is being changed', () => {
    expect(variantToPick(swifts, SWIFT_SPORT)).toEqual(found(SWIFT_SPORT, 6.1))
  })

  it('picks the only one there is', () => {
    expect(variantToPick([found(SWIFT_SPORT, 6.1)], null)).toEqual(found(SWIFT_SPORT, 6.1))
  })

  it('leaves the pick to the driver when there are several', () => {
    expect(variantToPick(swifts, null)).toBeNull()
  })

  it('leaves it to the driver when the saved car is another year', () => {
    expect(variantToPick(swifts, { ...SWIFT, year: 2017 })).toBeNull()
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

describe("reading the dashboard's figure for a car found in the register", () => {
  it('takes nothing typed as no figure, so the official figure is used', () => {
    expect(dashboardLitresFrom('')).toEqual({ litres: null })
    expect(dashboardLitresFrom('  ')).toEqual({ litres: null })
  })

  it('reads a figure, and refuses a wrong one, as for a vehicle entered by hand', () => {
    expect(dashboardLitresFrom('5,6')).toEqual({ litres: 5.6 })
    expect(dashboardLitresFrom('75')).toEqual({ problem: 'A petrol car uses between 2 and 30 L/100 km.' })
  })
})

describe('what the form saves', () => {
  const picked = found(SWIFT, 4.8)

  it("saves a vehicle entered by hand with its size and the dashboard's figure", () => {
    expect(vehicleToSave('enter', { size: 'small', found: picked, typed: '5,2' })).toEqual({
      size: 'small',
      litresPer100Km: 5.2,
    })
  })

  it("needs the dashboard's figure for a vehicle entered by hand", () => {
    expect(vehicleToSave('enter', { size: 'small', found: null, typed: '' })).toEqual({
      problem: 'Type the fuel use your dashboard shows, such as 7.5.',
    })
  })

  it("saves a car found in the register with no figure when none was typed, so the register's is used", () => {
    expect(vehicleToSave('find', { size: 'medium', found: picked, typed: ' ' })).toEqual({
      car: SWIFT,
      litresPer100Km: null,
    })
  })

  it("saves the dashboard's figure with a car found in the register when one was typed", () => {
    expect(vehicleToSave('find', { size: 'medium', found: picked, typed: '5.6' })).toEqual({
      car: SWIFT,
      litresPer100Km: 5.6,
    })
  })

  it('asks for the car down to its engine before saving it', () => {
    expect(vehicleToSave('find', { size: 'medium', found: null, typed: '5.6' })).toEqual({
      problem: 'Pick your car down to its engine, or enter it yourself.',
    })
  })

  it('refuses a figure no petrol car shows, whichever way', () => {
    const problem = { problem: 'A petrol car uses between 2 and 30 L/100 km.' }
    expect(vehicleToSave('find', { size: 'medium', found: picked, typed: '75' })).toEqual(problem)
    expect(vehicleToSave('enter', { size: 'medium', found: null, typed: '75' })).toEqual(problem)
  })
})

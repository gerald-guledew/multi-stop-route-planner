import { describe, expect, it } from 'vitest'
import { chosenFor, orderGoneBy } from './chosenFor'

describe('the order the screen goes by', () => {
  it('is the one picked when there is a vehicle', () => {
    expect(orderGoneBy({ order: 'leastFuel', hasVehicle: true })).toBe('leastFuel')
    expect(orderGoneBy({ order: 'fastest', hasVehicle: true })).toBe('fastest')
  })

  it('is the fastest when the least fuel is picked and there is no vehicle', () => {
    expect(orderGoneBy({ order: 'leastFuel', hasVehicle: false })).toBe('fastest')
  })
})

describe('what the order is chosen for', () => {
  it('is fuel when the least fuel is picked for a vehicle', () => {
    expect(chosenFor({ order: 'leastFuel', hasVehicle: true })).toBe('fuel')
  })

  it('is time when the least fuel is picked and there is no vehicle', () => {
    expect(chosenFor({ order: 'leastFuel', hasVehicle: false })).toBe('time')
  })

  it('is time when the fastest order is picked, with a vehicle or without', () => {
    expect(chosenFor({ order: 'fastest', hasVehicle: false })).toBe('time')
    expect(chosenFor({ order: 'fastest', hasVehicle: true })).toBe('time')
  })
})

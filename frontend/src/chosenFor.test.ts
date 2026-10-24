import { describe, expect, it } from 'vitest'
import { chosenFor } from './chosenFor'

describe('what the order is chosen for', () => {
  it('is fuel when the least fuel is picked for a vehicle', () => {
    expect(chosenFor({ order: 'leastFuel', hasVehicle: true })).toBe('fuel')
  })

  it('is distance when the least fuel is picked and there is no vehicle', () => {
    expect(chosenFor({ order: 'leastFuel', hasVehicle: false })).toBe('distance')
  })

  it('is time when the fastest order is picked, with a vehicle or without', () => {
    expect(chosenFor({ order: 'fastest', hasVehicle: false })).toBe('time')
    expect(chosenFor({ order: 'fastest', hasVehicle: true })).toBe('time')
  })
})

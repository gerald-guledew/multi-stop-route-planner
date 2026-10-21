import { describe, expect, it } from 'vitest'
import { chosenFor } from './chosenFor'

describe('what the order is chosen for', () => {
  it('is distance unless something else is picked', () => {
    expect(chosenFor({ order: 'shortest', factorInRoadClimbs: false, hasVehicle: false })).toBe('distance')
  })

  it('is distance and climb together when road climbs are factored in', () => {
    expect(chosenFor({ order: 'shortest', factorInRoadClimbs: true, hasVehicle: false })).toBe('distanceAndClimb')
  })

  it('is time when the fastest order is picked', () => {
    expect(chosenFor({ order: 'fastest', factorInRoadClimbs: false, hasVehicle: false })).toBe('time')
  })

  it('leaves road climbs out of the fastest order, when the box was ticked before', () => {
    expect(chosenFor({ order: 'fastest', factorInRoadClimbs: true, hasVehicle: false })).toBe('time')
  })

  it('is fuel when the least fuel is picked for a vehicle', () => {
    expect(chosenFor({ order: 'leastFuel', factorInRoadClimbs: false, hasVehicle: true })).toBe('fuel')
  })

  it('leaves road climbs out of the least fuel, which counts the climbs itself', () => {
    expect(chosenFor({ order: 'leastFuel', factorInRoadClimbs: true, hasVehicle: true })).toBe('fuel')
  })

  it('is distance when the least fuel is picked and there is no vehicle', () => {
    expect(chosenFor({ order: 'leastFuel', factorInRoadClimbs: true, hasVehicle: false })).toBe('distance')
  })

  it('leaves the vehicle out of the other orders', () => {
    expect(chosenFor({ order: 'shortest', factorInRoadClimbs: false, hasVehicle: true })).toBe('distance')
    expect(chosenFor({ order: 'fastest', factorInRoadClimbs: false, hasVehicle: true })).toBe('time')
  })
})

import { describe, expect, it } from 'vitest'
import { chosenFor } from './chosenFor'

describe('what the order is chosen for', () => {
  it('is distance unless something else is picked', () => {
    expect(chosenFor({ fastest: false, factorInRoadClimbs: false })).toBe('distance')
  })

  it('is distance and climb together when road climbs are factored in', () => {
    expect(chosenFor({ fastest: false, factorInRoadClimbs: true })).toBe('distanceAndClimb')
  })

  it('is time when the fastest order is picked', () => {
    expect(chosenFor({ fastest: true, factorInRoadClimbs: false })).toBe('time')
  })

  it('leaves road climbs out of the fastest order, when the box was ticked before', () => {
    expect(chosenFor({ fastest: true, factorInRoadClimbs: true })).toBe('time')
  })
})

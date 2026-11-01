import { describe, expect, it } from 'vitest'
import { KINDS_OF_PLACE } from './kindsOfPlace'

describe('the kinds of place on offer', () => {
  it('offers each kind once', () => {
    const names = KINDS_OF_PLACE.map((kind) => kind.name)

    expect(new Set(names).size).toBe(names.length)
  })

  it('fills the rows of three the dialog lays them out in', () => {
    expect(KINDS_OF_PLACE.length % 3).toBe(0)
  })

  it('calls fuel what a driver looking for it calls it', () => {
    expect(KINDS_OF_PLACE.find((kind) => kind.name === 'fuel')?.label).toBe('Petrol')
  })
})

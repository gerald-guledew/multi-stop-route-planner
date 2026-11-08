import { describe, expect, it } from 'vitest'
import { KINDS_OF_PLACE, offersKinds } from './kindsOfPlace'

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

describe('offering the kinds of place', () => {
  it('offers them while nothing is typed and none is picked', () => {
    expect(offersKinds('', null)).toBe(true)
    expect(offersKinds('  ', null)).toBe(true)
  })

  it('takes them away once something is typed', () => {
    expect(offersKinds('pe', null)).toBe(false)
  })

  it('takes them away once a kind is picked', () => {
    expect(offersKinds('', KINDS_OF_PLACE[0])).toBe(false)
  })
})

import { describe, expect, it } from 'vitest'
import { lookFrom } from './lookFrom'

const whereYouAre = { latitude: -36.8689, longitude: 174.7832 }
const start = { name: 'Depot', latitude: -36.9, longitude: 174.8 }
const mapCentre = { latitude: -36.8485, longitude: 174.7621 }

describe('where a search looks from', () => {
  it('looks from where you are when the browser has shared that', () => {
    expect(lookFrom(whereYouAre, start, mapCentre)).toEqual({ position: whereYouAre, what: 'you' })
  })

  it('looks from the start of the trip when the browser has not', () => {
    expect(lookFrom(null, start, mapCentre)).toEqual({ position: start, what: 'start' })
  })

  it('looks from the middle of the map until there is a start', () => {
    expect(lookFrom(null, undefined, mapCentre)).toEqual({ position: mapCentre, what: 'map' })
  })

  it('looks from where you are even before there is a start', () => {
    expect(lookFrom(whereYouAre, undefined, null)?.what).toBe('you')
  })

  it('has nowhere to look from before the map has said where it is', () => {
    expect(lookFrom(null, undefined, null)).toBeNull()
  })
})

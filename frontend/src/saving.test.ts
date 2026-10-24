import { describe, expect, it } from 'vitest'
import { savingSaid } from './saving'

describe('saying what the order chosen saves over the order entered', () => {
  it('says how much shorter it is', () => {
    expect(savingSaid(62.78, 62.22)).toEqual({ label: 'Saved', figure: '0.56 km', good: true })
  })

  it('says nought, and is not pleased about it, when the order entered was the best', () => {
    expect(savingSaid(51.76, 51.76)).toEqual({ label: 'Saved', figure: '0.00 km', good: false })
  })

  it('calls an order that is longer than the one entered longer, and not a saving below nought', () => {
    // Chosen for time or fuel, an order can be longer than the one clicked.
    expect(savingSaid(62.78, 63.21)).toEqual({
      label: 'Longer than you clicked by',
      figure: '0.43 km',
      good: false,
    })
  })

  it('counts a hundredth of a kilometre either way', () => {
    expect(savingSaid(10.01, 10)).toEqual({ label: 'Saved', figure: '0.01 km', good: true })
    expect(savingSaid(10, 10.01)).toEqual({
      label: 'Longer than you clicked by',
      figure: '0.01 km',
      good: false,
    })
  })
})

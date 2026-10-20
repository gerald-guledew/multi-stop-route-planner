import { describe, expect, it } from 'vitest'
import {
  afterHolding,
  afterMoving,
  droppedAt,
  HOLD_MS,
  holdFor,
  landing,
  LIFT_PX,
  pressed,
  STEADY_PX,
  type Grip,
} from './pinDrag'

const onThePin = { x: 200, y: 300 }

/** A finger rested on the pin long enough for it to come away. */
function lifted(): Grip {
  return afterHolding(pressed('touch', onThePin))
}

describe('a finger on a pin', () => {
  it('has to rest there before the pin comes away', () => {
    const grip = pressed('touch', onThePin)

    expect(holdFor(grip)).toBe(HOLD_MS)
    expect(grip.stage).toBe('resting')
    expect(lifted().stage).toBe('carrying')
  })

  it('lets go of the pin when it moves off at once, so the map moves instead', () => {
    const grip = pressed('touch', onThePin)

    expect(afterMoving(grip, { x: 200, y: 300 + STEADY_PX + 1 })).toBeNull()
  })

  it('keeps hold through a small wobble while resting', () => {
    const grip = afterMoving(pressed('touch', onThePin), { x: 204, y: 303 })

    expect(grip?.stage).toBe('resting')
  })

  it('holds the pin above itself once it comes away, so the thumb does not cover the spot', () => {
    const carried = afterMoving(lifted(), { x: 260, y: 420 })

    expect(carried && landing(carried)).toEqual({ x: 260, y: 420 - LIFT_PX })
  })

  it('lands the pin where its dot is once it has carried it', () => {
    const carried = afterMoving(lifted(), { x: 260, y: 420 })

    expect(carried && droppedAt(carried)).toEqual({ x: 260, y: 420 - LIFT_PX })
  })

  it('leaves the pin where it was when it is lifted and put straight back down', () => {
    expect(droppedAt(lifted())).toBeNull()
    expect(droppedAt(afterMoving(lifted(), { x: 203, y: 304 }) as Grip)).toBeNull()
  })

  it('still lands the pin after carrying it away and back', () => {
    const away = afterMoving(lifted(), { x: 200, y: 400 }) as Grip
    const back = afterMoving(away, { x: 201, y: 301 }) as Grip

    expect(droppedAt(back)).toEqual({ x: 201, y: 301 - LIFT_PX })
  })

  it('moves nothing when it lets go before the pin came away', () => {
    expect(droppedAt(pressed('touch', onThePin))).toBeNull()
  })
})

describe('a mouse on a pin', () => {
  it('carries the pin as soon as it drags, without resting first', () => {
    const grip = pressed('mouse', onThePin)

    expect(holdFor(grip)).toBeNull()
    expect(afterMoving(grip, { x: 205, y: 300 })?.stage).toBe('carrying')
  })

  it('is still a click while it has hardly moved', () => {
    expect(afterMoving(pressed('mouse', onThePin), { x: 202, y: 301 })?.stage).toBe('resting')
  })

  it('lands the pin right under the pointer', () => {
    const carried = afterMoving(pressed('mouse', onThePin), { x: 320, y: 240 }) as Grip

    expect(droppedAt(carried)).toEqual({ x: 320, y: 240 })
  })
})

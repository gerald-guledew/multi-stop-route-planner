/**
 * The rules about picking a pin up and moving it. Plain functions with no React or Leaflet in
 * them, so each rule can be tested on its own.
 *
 * On a phone a thumb meant for the map often lands on a pin. So a finger has to rest on a pin
 * before the pin comes away, and a finger that moves off straight away moves the map. Once the
 * pin comes away it is held above the finger, larger, with a dot where it will land: a thumb
 * covers whatever is under it. A mouse covers nothing and seldom lands on a pin by mistake, so
 * it carries a pin as soon as it drags one, with the dot right under the pointer.
 */

/** A point on the screen, in pixels. */
export interface Point {
  x: number
  y: number
}

/** How long a finger rests on a pin before the pin comes away. */
export const HOLD_MS = 500

/** A finger that wanders further than this while resting on a pin was moving the map. */
export const STEADY_PX = 10

/** How far a mouse moves with its button down before it is dragging rather than clicking. */
export const MOUSE_DRAG_PX = 3

/** How far above the finger a lifted pin lands, so the thumb does not cover the spot. */
export const LIFT_PX = 64

/** A pointer pressed on a pin: resting on it, or carrying it. */
export type Grip =
  | { stage: 'resting'; byMouse: boolean; from: Point; at: Point }
  | { stage: 'carrying'; byMouse: boolean; from: Point; at: Point; moved: boolean }

export function pressed(pointerType: string, at: Point): Grip {
  return { stage: 'resting', byMouse: pointerType === 'mouse', from: at, at }
}

/** How long a grip rests before the pin comes away. Null for a mouse, which drags it away. */
export function holdFor(grip: Grip): number | null {
  return grip.byMouse ? null : HOLD_MS
}

/**
 * The grip once the pointer moves, or null once it has let go of the pin. A finger that
 * wanders off a resting pin lets go, and the map moves instead. A mouse that drags picks the
 * pin up at once. A pin being carried follows.
 */
export function afterMoving(grip: Grip, to: Point): Grip | null {
  const distance = Math.hypot(to.x - grip.from.x, to.y - grip.from.y)
  if (grip.stage === 'carrying') {
    return { ...grip, at: to, moved: grip.moved || distance > STEADY_PX }
  }
  if (grip.byMouse) {
    return distance > MOUSE_DRAG_PX ? { ...grip, stage: 'carrying', at: to, moved: true } : grip
  }
  return distance > STEADY_PX ? null : { ...grip, at: to }
}

/** The grip once a finger has rested long enough: the pin comes away, not yet moved. */
export function afterHolding(grip: Grip): Grip {
  return grip.stage === 'resting' ? { ...grip, stage: 'carrying', moved: false } : grip
}

/** Where a pin being carried would land, with the pointer where it is now. */
export function landing(grip: Grip): Point {
  return { x: grip.at.x, y: grip.at.y - (grip.byMouse ? 0 : LIFT_PX) }
}

/**
 * Where the pin goes when the pointer lets go, or null to leave it where it was. A pin lifted
 * and put straight back down stays put: it lands above the finger, so landing it there would
 * move it by the lift.
 */
export function droppedAt(grip: Grip): Point | null {
  return grip.stage === 'carrying' && grip.moved ? landing(grip) : null
}

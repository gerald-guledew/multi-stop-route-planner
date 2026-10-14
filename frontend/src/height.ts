import type { Position } from './location'

/**
 * How high a pin stands, in words for its popup. Null when the height is not known, and the
 * popup then says nothing about it: a planner started without heights is not broken.
 */
export function heightSaid(metres: number | null | undefined): string | null {
  if (metres === null || metres === undefined) {
    return null
  }
  const whole = Math.round(metres)
  if (whole === 0) {
    return 'At sea level'
  }
  return whole > 0 ? `${whole} m above sea level` : `${-whole} m below sea level`
}

/** A pin's heights are kept by where it stands, so a pin dragged somewhere else is asked again. */
export function spotOf(at: Position): string {
  return `${at.latitude},${at.longitude}`
}

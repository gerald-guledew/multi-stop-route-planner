import type { Place } from './types'

/**
 * The rules about undoing and redoing changes to the trip. Plain functions with no React in
 * them, so each rule can be tested on its own.
 */

/** Everything about the trip that a change can be undone in: the places, and the two boxes. */
export interface Trip {
  places: Place[]
  returnToStart: boolean
  factorInRoadClimbs: boolean
}

/** The trip as it is, and as it was before each change. */
export interface History {
  /** The trip before each change, the oldest first. */
  past: Trip[]
  present: Trip
  /** The trip before each undo, the one undone last first. */
  future: Trip[]
  /**
   * The kind of change made last, when a run of them is undone as one. Typing a label changes
   * the trip at every letter, and is still one change to undo.
   */
  run?: string
}

/** How many changes can be undone. Further back than this, nobody remembers what they were. */
export const CHANGES_KEPT = 100

export function startingWith(trip: Trip): History {
  return { past: [], present: trip, future: [] }
}

function same(one: Trip, other: Trip): boolean {
  return (
    one.places === other.places &&
    one.returnToStart === other.returnToStart &&
    one.factorInRoadClimbs === other.factorInRoadClimbs
  )
}

/**
 * The history once the trip is changed. The change can be undone, and whatever was undone
 * before it can no longer be redone: it would be redone onto a different trip.
 *
 * A change in the same run as the one before it joins that one, and is undone with it.
 */
export function afterChange(history: History, next: Trip, run?: string): History {
  if (same(next, history.present)) {
    return history
  }
  if (run !== undefined && run === history.run) {
    return { ...history, present: next, future: [] }
  }
  return {
    past: [...history.past, history.present].slice(-CHANGES_KEPT),
    present: next,
    future: [],
    run,
  }
}

/** The history with the last change undone. The same history if there is nothing to undo. */
export function undone(history: History): History {
  if (history.past.length === 0) {
    return history
  }
  return {
    past: history.past.slice(0, -1),
    present: history.past[history.past.length - 1],
    future: [history.present, ...history.future],
  }
}

/** The history with the last undo redone. The same history if there is nothing to redo. */
export function redone(history: History): History {
  if (history.future.length === 0) {
    return history
  }
  return {
    past: [...history.past, history.present],
    present: history.future[0],
    future: history.future.slice(1),
  }
}

/**
 * The history with a correction made all through it, to the trip as it is and as it was. Not
 * a change to undo: the browser putting its own guess right, say, which every trip that
 * started from the guess should start from.
 */
export function correctedThroughout(history: History, correct: (trip: Trip) => Trip): History {
  return {
    ...history,
    past: history.past.map(correct),
    present: correct(history.present),
    future: history.future.map(correct),
  }
}

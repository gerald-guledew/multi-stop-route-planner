import { howLong } from './duration'
import type { Place, RoutePlan } from './types'

/**
 * What the bar along the bottom of a phone says, and the one thing it offers to press.
 *
 * While the map has the screen that bar is all that shows of the trip. So it says where things
 * stand in a few words, and holds whatever comes next: finding you, planning, or handing the
 * finished order to the navigation app.
 */
export type NextStep = 'locate' | 'plan' | 'open'

export interface WhatNext {
  says: string
  /** A second, quieter line under the first. Only a finished plan has enough to say for one. */
  alsoSays?: string
  /** Null when there is nothing to press yet. */
  step: NextStep | null
}

export function whatNext(places: Place[], plan: RoutePlan | null, mostStops: number): WhatNext {
  const stops = Math.max(places.length - 1, 0)

  if (places.length === 0) {
    return { says: 'Set your start', step: 'locate' }
  }
  if (stops === 0) {
    return { says: 'Now add a stop', step: null }
  }
  if (stops > mostStops) {
    return { says: `${stops} stops, ${mostStops} at most`, step: null }
  }
  if (plan) {
    // How far on the first line. Under it how long, when the roads say, and how many stops.
    const driving = plan.totalDurationSeconds
    return {
      says: `${plan.totalDistanceKm.toFixed(2)} km`,
      alsoSays: driving === null ? counted(stops) : `${howLong(driving)}, ${counted(stops)}`,
      step: 'open',
    }
  }
  return { says: counted(stops), step: 'plan' }
}

function counted(stops: number): string {
  return stops === 1 ? '1 stop' : `${stops} stops`
}

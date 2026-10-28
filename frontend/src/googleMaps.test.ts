import { describe, expect, it } from 'vitest'
import { googleMapsLink } from './googleMaps'
import type { Place } from './types'

const start: Place = { name: 'Sky Tower', latitude: -36.8485, longitude: 174.7621 }

/** Stops one hundredth of a degree apart, numbered from 1. */
function stops(howMany: number): Place[] {
  return Array.from({ length: howMany }, (_, at) => ({
    name: `Stop ${at + 1}`,
    latitude: -(3680 + at + 1) / 100,
    longitude: 174.7,
  }))
}

function linked(link: string): { origin: string; destination: string; waypoints: string[] } {
  const asked = new URL(link).searchParams
  return {
    origin: asked.get('origin') ?? '',
    destination: asked.get('destination') ?? '',
    waypoints: (asked.get('waypoints') ?? '').split('|').filter((waypoint) => waypoint !== ''),
  }
}

describe('the Google Maps link', () => {
  it('starts and ends a round trip at the start, with the stops in order between', () => {
    const link = linked(googleMapsLink([start, ...stops(3), start]))

    expect(link.origin).toBe('-36.8485,174.7621')
    expect(link.destination).toBe('-36.8485,174.7621')
    expect(link.waypoints).toEqual(['-36.81,174.7', '-36.82,174.7', '-36.83,174.7'])
  })

  it('ends a one-way trip at its last stop', () => {
    const link = linked(googleMapsLink([start, ...stops(3)]))

    expect(link.destination).toBe('-36.83,174.7')
    expect(link.waypoints).toHaveLength(2)
  })

  it('keeps all nine stops of a round trip, the most it takes', () => {
    const link = linked(googleMapsLink([start, ...stops(9), start]))

    expect(link.destination).toBe('-36.8485,174.7621')
    expect(link.waypoints).toHaveLength(9)
  })

  it('keeps all ten stops of a one-way trip, the most it takes', () => {
    const link = linked(googleMapsLink([start, ...stops(10)]))

    expect(link.destination).toBe('-36.9,174.7')
    expect(link.waypoints).toHaveLength(9)
  })

  it('asks for driving directions', () => {
    expect(new URL(googleMapsLink([start, ...stops(1)])).searchParams.get('travelmode')).toBe('driving')
  })
})

import { useEffect, useRef, useState } from 'react'
import { findAlong, NotAnswering } from '../api'
import type { KindOfPlace } from '../kindsOfPlace'
import type { PlaceAlong, RoutePlan } from '../types'

const MINIMUM_CHARACTERS = 3
const DEBOUNCE_MS = 250

/**
 * The places along a planned route that the words typed, or the kind of place picked, find: those
 * that add the least first. Along one leg, or along all of them when the leg is null. Used by the
 * dialog of a "+" and by the search along the planned route.
 */
export function useSearchAlong(plan: RoutePlan, leg: number | null, query: string, kind: KindOfPlace | null) {
  const [results, setResults] = useState<PlaceAlong[]>([])
  const [searching, setSearching] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)
  const [nothingFound, setNothingFound] = useState(false)
  const inFlight = useRef<AbortController | null>(null)

  useEffect(() => {
    const term = query.trim()
    // Whatever the last search said, it was about something else.
    setNothingFound(false)
    setResults([])
    setSearching(false)
    setFailed(null)
    if (kind === null && term.length < MINIMUM_CHARACTERS) {
      return
    }

    // Wait for a pause in typing, so one search runs instead of one per keystroke.
    const timer = setTimeout(async () => {
      const controller = new AbortController()
      inFlight.current = controller

      setSearching(true)
      try {
        const found = await findAlong(kind ? { placeKind: kind.name } : { q: term }, plan.route, leg, controller.signal)
        setResults(found)
        setNothingFound(found.length === 0)
      } catch (caught) {
        if (!controller.signal.aborted) {
          setFailed(caught instanceof NotAnswering ? caught.message : 'Search is unavailable. Try again.')
        }
      } finally {
        if (!controller.signal.aborted) {
          setSearching(false)
        }
      }
    }, kind ? 0 : DEBOUNCE_MS)

    // A search on its way has nobody to answer once what it was for has changed, or the search gone.
    return () => {
      clearTimeout(timer)
      inFlight.current?.abort()
    }
  }, [query, kind, plan, leg])

  return { results, searching, failed, nothingFound }
}

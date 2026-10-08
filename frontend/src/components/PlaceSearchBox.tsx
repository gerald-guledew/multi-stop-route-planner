import { useEffect, useRef, useState } from 'react'
import { searchPlaces } from '../api'
import type { Position } from '../location'
import type { FoundPlace, Place } from '../types'

const MINIMUM_CHARACTERS = 3
const DEBOUNCE_MS = 250

interface Props {
  onPick: (place: Place) => void
  /** The start of the trip, once there is one. */
  start: Place | undefined
  /** Where the map is looking. Null until the map has said. */
  mapCentre: Position | null
}

/** What a search was ranked near, so the list can say so. */
type LookedFrom = 'start' | 'map'

const NEAREST_TO: Record<LookedFrom, string> = {
  start: 'Nearest to your start first',
  map: 'Nearest to the middle of the map first',
}

/**
 * Type an address or the name of a business, pick it, and the pin lands there rather than
 * wherever a click happened to fall. Clicking the map still works for anywhere else.
 *
 * Of several matches the nearest comes first. Near means near the start of the trip, because
 * stops are usually around it. Until there is a start it means near the middle of the map,
 * which is where the person is looking. Search never asks where this device is. That can be
 * refused, a laptop only knows it roughly, and a trip is often planned for somewhere else.
 */
export default function PlaceSearchBox({ onPick, start, mapCentre }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<FoundPlace[]>([])
  const [rankedNear, setRankedNear] = useState<LookedFrom | null>(null)
  const [searching, setSearching] = useState(false)
  const [failed, setFailed] = useState(false)
  const [nothingFound, setNothingFound] = useState(false)
  const inFlight = useRef<AbortController | null>(null)

  // Read by the search when it runs, rather than listed as something the search depends on.
  // Moving the map then does not run the search again and reshuffle a list somebody is reading.
  const lookingFrom = useRef<{ position: Position; what: LookedFrom } | null>(null)
  useEffect(() => {
    lookingFrom.current = start
      ? { position: start, what: 'start' }
      : mapCentre
        ? { position: mapCentre, what: 'map' }
        : null
  })

  useEffect(() => {
    const term = query.trim()
    // Whatever the last search said, it was about different text.
    setNothingFound(false)
    if (term.length < MINIMUM_CHARACTERS) {
      setResults([])
      return
    }

    // Wait for a pause in typing, so one search runs instead of one per keystroke.
    const timer = setTimeout(async () => {
      inFlight.current?.abort()
      const controller = new AbortController()
      inFlight.current = controller
      const from = lookingFrom.current

      setSearching(true)
      setFailed(false)
      try {
        const found = await searchPlaces(term, from?.position ?? null, controller.signal)
        setResults(found)
        setRankedNear(from?.what ?? null)
        setNothingFound(found.length === 0)
      } catch (caught) {
        if (!controller.signal.aborted) {
          setResults([])
          setFailed(true)
        }
      } finally {
        if (!controller.signal.aborted) {
          setSearching(false)
        }
      }
    }, DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [query])

  function pick(found: FoundPlace) {
    // Only what a stop needs. The kind and the detail were for choosing between results.
    onPick({ name: found.name, latitude: found.latitude, longitude: found.longitude })
    setQuery('')
    setResults([])
  }

  return (
    <div className="search">
      <input
        type="search"
        value={query}
        placeholder="Search an address or a business"
        aria-label="Search for an address or a business"
        onChange={(event) => setQuery(event.target.value)}
      />

      {searching && <p className="searching">Searching…</p>}

      {failed && <p className="error">Search is unavailable. Click the map instead.</p>}

      {nothingFound && !searching && (
        <p className="searching">Nothing found. Try fewer words, or click the map.</p>
      )}

      {results.length > 0 && (
        <ul className="results">
          {rankedNear && <li className="results-note">{NEAREST_TO[rankedNear]}</li>}
          {results.map((found) => (
            <li key={`${found.kind}|${found.name}|${found.latitude},${found.longitude}`}>
              <button onClick={() => pick(found)}>
                <span className="result-name">{found.name}</span>
                {found.detail && <span className="result-detail">{found.detail}</span>}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { NotAnswering, searchPlaces } from '../api'
import type { Position } from '../location'
import { lookFrom, type LookedFrom } from '../lookFrom'
import { isInTrip } from '../savedPlaces'
import type { FoundPlace, Place } from '../types'

const MINIMUM_CHARACTERS = 3
const DEBOUNCE_MS = 250

interface Props {
  onPick: (place: Place) => void
  /** Where the browser says this device is. Null if it was not asked, or said no. */
  devicePosition: Position | null
  /** The trip as it stands: its start to look from, and the places it already goes to. */
  places: Place[]
  /** Where the map is looking. Null until the map has said. */
  mapCentre: Position | null
  /** Whether the trip has all the stops it takes. The box then says so and finds nothing. */
  full: boolean
}

const NEAREST_TO: Record<LookedFrom, string> = {
  you: 'Nearest to where you are first',
  start: 'Nearest to your start first',
  map: 'Nearest to the middle of the map first',
}

/**
 * Type an address or the name of a business, pick it, and the pin lands there rather than
 * wherever a click happened to fall. Clicking the map still works for anywhere else.
 *
 * Of several matches the nearest comes first, and the list says nearest to what.
 */
export default function PlaceSearchBox({ onPick, devicePosition, places, mapCentre, full }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<FoundPlace[]>([])
  const [rankedNear, setRankedNear] = useState<LookedFrom | null>(null)
  const [searching, setSearching] = useState(false)
  // Why the last search failed, in words, or null if it did not.
  const [failed, setFailed] = useState<string | null>(null)
  const [nothingFound, setNothingFound] = useState(false)
  const inFlight = useRef<AbortController | null>(null)

  // Read by the search when it runs, rather than listed as something the search depends on.
  // Moving the map then does not run the search again and reshuffle a list somebody is reading.
  const lookingFrom = useRef(lookFrom(devicePosition, places[0], mapCentre))
  useEffect(() => {
    lookingFrom.current = lookFrom(devicePosition, places[0], mapCentre)
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
      setFailed(null)
      try {
        const found = await searchPlaces(term, from?.position ?? null, controller.signal)
        setResults(found)
        setRankedNear(from?.what ?? null)
        setNothingFound(found.length === 0)
      } catch (caught) {
        if (!controller.signal.aborted) {
          setResults([])
          // No answer at all is worth saying as what it is. The map is no way round it, since
          // planning would get no answer either.
          setFailed(
            caught instanceof NotAnswering
              ? caught.message
              : 'Search is unavailable. Click the map instead.',
          )
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

  // Nothing found could be added. Whatever was being typed comes back once a stop goes.
  if (full) {
    return (
      <div className="search">
        <input
          type="search"
          value=""
          disabled
          placeholder="Max stops reached"
          aria-label="Search for an address or a business"
        />
      </div>
    )
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

      {failed && <p className="error">{failed}</p>}

      {nothingFound && !searching && (
        <p className="searching">Nothing found. Try fewer words, or click the map.</p>
      )}

      {results.length > 0 && (
        <ul className="results">
          {rankedNear && <li className="results-note">{NEAREST_TO[rankedNear]}</li>}
          {results.map((found) => {
            // Once ordered, a place in the trip twice is only driven to twice in a row.
            const inTrip = isInTrip(places, found)
            return (
              <li key={`${found.kind}|${found.name}|${found.latitude},${found.longitude}`}>
                <button disabled={inTrip} onClick={() => pick(found)}>
                  <span className="result-name">{found.name}</span>
                  {found.detail && <span className="result-detail">{found.detail}</span>}
                  {inTrip && <span className="result-detail">Already in this trip</span>}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

import { useEffect, useRef, useState } from 'react'
import { searchPlaces } from '../api'
import type { Place } from '../types'

const MINIMUM_CHARACTERS = 3
const DEBOUNCE_MS = 250

interface Props {
  onPick: (place: Place) => void
}

/**
 * Type an address, pick it, and the pin lands on the street rather than wherever a click
 * happened to fall. Clicking the map still works for places with no address.
 */
export default function AddressSearchBox({ onPick }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Place[]>([])
  const [searching, setSearching] = useState(false)
  const [failed, setFailed] = useState(false)
  const inFlight = useRef<AbortController | null>(null)

  useEffect(() => {
    const term = query.trim()
    if (term.length < MINIMUM_CHARACTERS) {
      setResults([])
      return
    }

    // Wait for a pause in typing, so one search runs instead of one per keystroke.
    const timer = setTimeout(async () => {
      inFlight.current?.abort()
      const controller = new AbortController()
      inFlight.current = controller

      setSearching(true)
      setFailed(false)
      try {
        setResults(await searchPlaces(term, controller.signal))
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

  function pick(place: Place) {
    onPick(place)
    setQuery('')
    setResults([])
  }

  return (
    <div className="search">
      <input
        type="search"
        value={query}
        placeholder="Search an address, or click the map"
        aria-label="Search for an address"
        onChange={(event) => setQuery(event.target.value)}
      />

      {searching && <p className="searching">Searching…</p>}

      {failed && <p className="error">Address search is unavailable. Click the map instead.</p>}

      {results.length > 0 && (
        <ul className="results">
          {results.map((place) => (
            <li key={`${place.latitude},${place.longitude}`}>
              <button onClick={() => pick(place)}>{place.name}</button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

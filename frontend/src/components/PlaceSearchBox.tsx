import { useEffect, useRef, useState } from 'react'
import { searchPlaces } from '../api'
import type { FoundPlace, Place } from '../types'

const MINIMUM_CHARACTERS = 3
const DEBOUNCE_MS = 250

interface Props {
  onPick: (place: Place) => void
}

/**
 * Type an address or the name of a business, pick it, and the pin lands there rather than
 * wherever a click happened to fall. Clicking the map still works for anywhere else.
 */
export default function PlaceSearchBox({ onPick }: Props) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<FoundPlace[]>([])
  const [searching, setSearching] = useState(false)
  const [failed, setFailed] = useState(false)
  const [nothingFound, setNothingFound] = useState(false)
  const inFlight = useRef<AbortController | null>(null)

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

      setSearching(true)
      setFailed(false)
      try {
        const found = await searchPlaces(term, controller.signal)
        setResults(found)
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

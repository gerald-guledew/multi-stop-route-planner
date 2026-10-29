import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { CrossIcon } from './icons'
import { findAlong, NotAnswering } from '../api'
import { addedSaid, alreadyInTrip, betweenSaid } from '../onTheWay'
import type { Place, PlaceAlong, RoutePlan } from '../types'

const MINIMUM_CHARACTERS = 3
const DEBOUNCE_MS = 250

interface Props {
  plan: RoutePlan
  /** The leg to find a stop on, counting from 0. Null for anywhere along the trip. */
  leg: number | null
  /** The trip as it stands, to tell the places it already goes to. */
  places: Place[]
  onPick: (found: PlaceAlong) => void
  onClose: () => void
}

/**
 * Finds a place to stop at on the way, by its name or its address: between two stops, from the
 * "+" between them, or anywhere along the trip. The places that add the least time come first,
 * each with what it adds.
 *
 * A dialog over everything, so on a phone the search has the whole screen and the keyboard.
 */
export default function FindOnTheWay({ plan, leg, places, onPick, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PlaceAlong[]>([])
  const [searching, setSearching] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)
  const [nothingFound, setNothingFound] = useState(false)
  const inFlight = useRef<AbortController | null>(null)

  const box = useRef<HTMLInputElement>(null)

  // Opened as soon as it is drawn, still within the tap that asked for it: a phone shows its
  // keyboard only for a box given the cursor by a tap. It is closed by being taken off the page,
  // which says nothing, so closing it never comes back here as an answer. Opening puts the cursor
  // on the first button, so it is moved to the box, which is what the dialog is for.
  useLayoutEffect(() => {
    const opened = dialog.current
    if (opened && !opened.open) {
      opened.showModal()
    }
    box.current?.focus()
  }, [])

  // A search on its way when the dialog goes has nobody to answer.
  useEffect(() => () => inFlight.current?.abort(), [])

  useEffect(() => {
    const term = query.trim()
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
      setFailed(null)
      try {
        const found = await findAlong(term, plan.route, leg, controller.signal)
        setResults(found)
        setNothingFound(found.length === 0)
      } catch (caught) {
        if (!controller.signal.aborted) {
          setResults([])
          setFailed(caught instanceof NotAnswering ? caught.message : 'Search is unavailable. Try again.')
        }
      } finally {
        if (!controller.signal.aborted) {
          setSearching(false)
        }
      }
    }, DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [query, plan, leg])

  const heading = leg === null ? 'Find along the current route' : `Add a stop ${betweenSaid(plan, leg)}`

  return (
    <dialog
      ref={dialog}
      className="on-the-way-search"
      aria-label={heading}
      // Escape. The dialog is left for whoever opened it to take away.
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
    >
      <div className="on-the-way-head">
        <h2>{heading}</h2>
        <button className="icon-button" title="Close" aria-label="Close" onClick={onClose}>
          <CrossIcon />
        </button>
      </div>

      <div className="search">
        <input
          ref={box}
          type="search"
          value={query}
          placeholder="Search an address or a business"
          aria-label="Search for an address or a business on the way"
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      {searching && <p className="searching">Searching…</p>}

      {failed && <p className="error">{failed}</p>}

      {nothingFound && !searching && <p className="searching">Nothing found. Try fewer words.</p>}

      {results.length > 0 && (
        <ul className="on-the-way-results">
          <li className="results-note">Least out of your way first</li>
          {results.map((found) => {
            const inTrip = alreadyInTrip(places, found)
            return (
              <li key={`${found.kind}|${found.name}|${found.latitude},${found.longitude}`}>
                <button disabled={inTrip} onClick={() => onPick(found)}>
                  <span className="result-name">{found.name}</span>
                  {found.detail && <span className="result-detail">{found.detail}</span>}
                  <span className="result-added">
                    {inTrip ? 'Already in this trip' : addedSaid(found)}
                    {!inTrip && leg === null && (
                      <span className="result-between"> · {betweenSaid(plan, found.leg)}</span>
                    )}
                  </span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </dialog>
  )
}

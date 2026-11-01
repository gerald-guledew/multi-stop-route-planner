import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { CrossIcon } from './icons'
import { findAlong, NotAnswering } from '../api'
import { addedSaid, betweenSaid, hasOtherLegs } from '../onTheWay'
import { isInTrip } from '../savedPlaces'
import type { Place, PlaceAlong, RoutePlan } from '../types'

const MINIMUM_CHARACTERS = 3
const DEBOUNCE_MS = 250

interface Props {
  plan: RoutePlan
  /** The leg its "+" sits on, counting from 0. A stop-over belongs to a leg, so that comes first. */
  leg: number
  /** The trip as it stands, to tell the places it already goes to. */
  places: Place[]
  onPick: (found: PlaceAlong) => void
  onClose: () => void
}

/**
 * Finds a place to stop at between two stops of a planned trip. The places that add the least
 * time come first, each with what it adds, and the search can be widened to the whole route.
 *
 * A dialog over everything, low on a phone and in the middle of a wide screen. It has a search of
 * its own, so the search box of the map screen only ever looks for stops.
 */
export default function StopOverDialog({ plan, leg, places, onPick, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null)
  const box = useRef<HTMLInputElement>(null)
  // The leg looked along, or null for all of them.
  const [lookingAlong, setLookingAlong] = useState<number | null>(leg)
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<PlaceAlong[]>([])
  const [searching, setSearching] = useState(false)
  const [failed, setFailed] = useState<string | null>(null)
  const [nothingFound, setNothingFound] = useState(false)
  const inFlight = useRef<AbortController | null>(null)

  // Opened as soon as it is drawn. It is closed by being taken off the page, which says nothing,
  // so closing it never comes back here as an answer. The cursor goes in the box only where there
  // is a real keyboard: a phone's would come up over the lower half of the dialog.
  useLayoutEffect(() => {
    const opened = dialog.current
    if (opened && !opened.open) {
      opened.showModal()
    }
    if (window.matchMedia('(hover: hover)').matches) {
      box.current?.focus()
    }
  }, [])

  // A search on its way when the dialog goes has nobody to answer.
  useEffect(() => () => inFlight.current?.abort(), [])

  useEffect(() => {
    const term = query.trim()
    // Whatever the last search said, it was about something else.
    setNothingFound(false)
    setResults([])
    if (term.length < MINIMUM_CHARACTERS) {
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
        const found = await findAlong(term, plan.route, lookingAlong, controller.signal)
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
    }, DEBOUNCE_MS)

    return () => clearTimeout(timer)
  }, [query, plan, lookingAlong])

  return (
    <dialog
      ref={dialog}
      className="stop-over"
      aria-labelledby="stop-over-title"
      // Escape. The dialog is left for whoever opened it to take away.
      onCancel={(event) => {
        event.preventDefault()
        onClose()
      }}
      // Only the darkened page around it lands on the dialog itself. Everything in it is covered.
      onClick={(event) => {
        if (event.target === dialog.current) {
          onClose()
        }
      }}
    >
      <div className="stop-over-head">
        <div className="stop-over-title">
          <h2 id="stop-over-title">Add a stop-over</h2>
          <button className="icon-button" title="Close" aria-label="Close" onClick={onClose}>
            <CrossIcon />
          </button>
        </div>
        <p className="stop-over-where">
          {lookingAlong === null ? 'Anywhere along the route' : betweenSaid(plan, lookingAlong)}
        </p>

        {hasOtherLegs(plan) && (
          <div className="stop-over-scope" role="group" aria-label="Where to look">
            <button aria-pressed={lookingAlong !== null} onClick={() => setLookingAlong(leg)}>
              This part
            </button>
            <button aria-pressed={lookingAlong === null} onClick={() => setLookingAlong(null)}>
              Whole route
            </button>
          </div>
        )}

        <div className="search">
          <input
            ref={box}
            type="search"
            value={query}
            placeholder="Search an address or a business"
            aria-label="Search for an address or a business to stop at"
            onChange={(event) => setQuery(event.target.value)}
          />
        </div>
      </div>

      <div className="stop-over-body">
        {searching && <p className="searching">Searching…</p>}

        {failed && <p className="error">{failed}</p>}

        {nothingFound && !searching && (
          <div className="stop-over-nothing">
            <p className="searching">
              {lookingAlong === null ? 'Nothing found. Try fewer words.' : 'Nothing found on this part.'}
            </p>
            {lookingAlong !== null && hasOtherLegs(plan) && (
              <button className="link" onClick={() => setLookingAlong(null)}>
                Look along the whole route
              </button>
            )}
          </div>
        )}

        {results.length > 0 && (
          <ul className="stop-over-results">
            <li className="results-note">Least out of your way first</li>
            {results.map((found) => {
              const inTrip = isInTrip(places, found)
              return (
                <li key={`${found.kind}|${found.name}|${found.latitude},${found.longitude}`}>
                  <button disabled={inTrip} onClick={() => onPick(found)}>
                    <span className="result-name">{found.name}</span>
                    {found.detail && <span className="result-detail">{found.detail}</span>}
                    <span className="result-added">
                      {inTrip ? 'Already in this trip' : addedSaid(found)}
                      {!inTrip && lookingAlong === null && (
                        <span className="result-between"> · {betweenSaid(plan, found.leg)}</span>
                      )}
                    </span>
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </div>
    </dialog>
  )
}

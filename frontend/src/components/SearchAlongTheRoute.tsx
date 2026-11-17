import { useEffect, useEffectEvent, useLayoutEffect, useMemo, useRef, useState } from 'react'
import { ChevronRightIcon, DownIcon, KindIcon } from './icons'
import { useSearchAlong } from './useSearchAlong'
import { KINDS_OF_PLACE, offersKinds, type KindOfPlace } from '../kindsOfPlace'
import { addedSaid, betweenSaid, nearTheRoute } from '../onTheWay'
import { isInTrip } from '../savedPlaces'
import { useNarrowScreen } from '../screen'
import type { Place, PlaceAlong, RoutePlan } from '../types'

/** As many places as the map shows pins for, when that many are near the route. */
export const PLACES_SHOWN = 10

interface Props {
  plan: RoutePlan
  /** The trip as it stands, to tell the places it already goes to. */
  places: Place[]
  /** The places found near the route, the least out of the way first, each time they change. */
  onFound: (found: PlaceAlong[]) => void
  /** One of them picked from the list, by its turn in it, to be shown on the map. */
  onShow: (index: number) => void
  /** Back to the search for a place. */
  onBack: () => void
}

/**
 * The search along the planned route, for a stop-over whose leg the driver does not know or does
 * not mind. Each place found goes on the leg it adds the least time to, and the other stops keep
 * their planned order, where the search box adds a stop and the order is worked out again.
 *
 * It slides in over the search box from the right, from the edge the search box shows of it. The
 * arrow on its left slides it back, and so does adding a place.
 *
 * What it finds goes on the map as numbered pins, and a place is added from its pin. The list says
 * the same with the addresses, and picking a place in it shows its pin. On a phone the list would
 * cover the pins, so once places are found it folds away to one line, and opens again from there.
 */
export default function SearchAlongTheRoute({ plan, places, onFound, onShow, onBack }: Props) {
  const box = useRef<HTMLInputElement>(null)
  const back = useRef<HTMLButtonElement>(null)
  const [query, setQuery] = useState('')
  // The kind of place picked. Typing leaves it, since a name says more.
  const [kind, setKind] = useState<KindOfPlace | null>(null)
  const { results, searching, failed, nothingFound } = useSearchAlong(plan, null, query, kind, PLACES_SHOWN)
  const near = useMemo(() => nearTheRoute(results), [results])
  const noneNear = results.length > 0 && near.length === 0
  const offeringKinds = offersKinds(query, kind)
  const narrow = useNarrowScreen()
  // The places the list was opened for on a phone. New places fold it away again.
  const [listOpenFor, setListOpenFor] = useState<PlaceAlong[] | null>(null)
  const listShown = !narrow || listOpenFor === near

  // The map shows what the list holds, and nothing once the bar is gone.
  const found = useEffectEvent(onFound)
  useEffect(() => {
    found(near)
    return () => found([])
  }, [near])
  const listing =
    offeringKinds || kind !== null || searching || failed !== null || nothingFound || results.length > 0

  // The cursor goes in the box only where there is a real keyboard. A phone's would come up over
  // the kinds of place, which are one tap away, so there the arrow back takes the focus, and the
  // focus is never left on the page.
  useLayoutEffect(() => {
    if (window.matchMedia('(hover: hover)').matches) {
      box.current?.focus()
    } else {
      back.current?.focus()
    }
  }, [])

  return (
    <div
      className="search along-bar"
      onKeyDown={(event) => {
        if (event.key === 'Escape') {
          event.preventDefault()
          onBack()
        }
      }}
    >
      <div className="search-row">
        <button
          ref={back}
          className="along-edge along-back"
          title="Back to the search for a place"
          aria-label="Back to the search for a place"
          onClick={onBack}
        >
          <ChevronRightIcon />
        </button>
        <input
          ref={box}
          type="search"
          value={query}
          placeholder="Search along the planned route"
          aria-label="Search along the planned route"
          onChange={(event) => {
            setQuery(event.target.value)
            setKind(null)
          }}
        />
      </div>

      {listing && (
        <div className="along-list">
          {offeringKinds && (
            <>
              <p className="results-note">Or pick what you need</p>
              <ul className="stop-over-kinds">
                {KINDS_OF_PLACE.map((each) => (
                  <li key={each.name}>
                    <button onClick={() => setKind(each)}>
                      <KindIcon kind={each.name} />
                      {each.label}
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}

          {kind && (
            <button className="stop-over-back" aria-label="Back to the kinds of place" onClick={() => setKind(null)}>
              <DownIcon />
              {kind.label}
            </button>
          )}

          {searching && <p className="searching">Searching…</p>}

          {failed && <p className="error">{failed}</p>}

          {nothingFound && !searching && <p className="searching">Nothing found along the route. Try fewer words.</p>}

          {noneNear && !searching && <p className="searching">Nothing found within 10 minutes of the route.</p>}

          {near.length > 0 && narrow && (
            <button className="along-toggle" onClick={() => setListOpenFor(listShown ? null : near)}>
              <span>
                {near.length} near the route, on the map
              </span>
              <span className="along-toggle-action">{listShown ? 'Hide the list' : 'Show the list'}</span>
            </button>
          )}

          {near.length > 0 && listShown && (
            <ul className="along-results">
              <li className="results-note">Least out of your way first. Pick one to see it on the map</li>
              {near.map((place, index) => {
                const inTrip = isInTrip(places, place)
                return (
                  <li key={`${place.kind}|${place.name}|${place.latitude},${place.longitude}`}>
                    <button
                      onClick={() => {
                        if (narrow) {
                          setListOpenFor(null)
                        }
                        onShow(index)
                      }}
                    >
                      <span className="along-pin along-pin-list" aria-hidden="true">
                        <span>{index + 1}</span>
                      </span>
                      <span className="along-result-text">
                        <span className="result-name">{place.name}</span>
                        {place.detail && <span className="result-detail">{place.detail}</span>}
                        <span className="result-added">
                          {inTrip ? 'Already in this trip' : addedSaid(place)}
                          {!inTrip && <span className="result-between"> · {betweenSaid(plan, place.leg)}</span>}
                        </span>
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}

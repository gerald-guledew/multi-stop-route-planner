import { useLayoutEffect, useRef, useState } from 'react'
import { ChevronRightIcon, DownIcon, KindIcon } from './icons'
import { useSearchAlong } from './useSearchAlong'
import { KINDS_OF_PLACE, offersKinds, type KindOfPlace } from '../kindsOfPlace'
import { addedSaid, betweenSaid } from '../onTheWay'
import { isInTrip } from '../savedPlaces'
import type { Place, PlaceAlong, RoutePlan } from '../types'

interface Props {
  plan: RoutePlan
  /** The trip as it stands, to tell the places it already goes to. */
  places: Place[]
  onPick: (found: PlaceAlong) => void
  /** Back to the search for a place. */
  onBack: () => void
}

/**
 * The search along the planned route, for a stop-over whose leg the driver does not know or does
 * not mind. Each place found goes on the leg it adds the least time to, and the other stops keep
 * their planned order, where the search box adds a stop and the order is worked out again.
 *
 * It slides in over the search box from the right, from the edge the search box shows of it. The
 * arrow on its left slides it back, and so does picking a place.
 */
export default function SearchAlongTheRoute({ plan, places, onPick, onBack }: Props) {
  const box = useRef<HTMLInputElement>(null)
  const [query, setQuery] = useState('')
  // The kind of place picked. Typing leaves it, since a name says more.
  const [kind, setKind] = useState<KindOfPlace | null>(null)
  const { results, searching, failed, nothingFound } = useSearchAlong(plan, null, query, kind)
  const offeringKinds = offersKinds(query, kind)
  const listing =
    offeringKinds || kind !== null || searching || failed !== null || nothingFound || results.length > 0

  // The cursor goes in the box only where there is a real keyboard. A phone's would come up over
  // the kinds of place, which are one tap away.
  useLayoutEffect(() => {
    if (window.matchMedia('(hover: hover)').matches) {
      box.current?.focus()
    }
  }, [])

  return (
    <div className="search along-bar">
      <div className="search-row">
        <button
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

          {results.length > 0 && (
            <ul className="along-results">
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
                        {!inTrip && <span className="result-between"> · {betweenSaid(plan, found.leg)}</span>}
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

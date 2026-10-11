import { isInTrip, isTheStart } from '../savedPlaces'
import type { Place, SavedPlace } from '../types'

interface Props {
  saved: SavedPlace[]
  /** The trip as it stands, to tell which saved places are already in it. */
  places: Place[]
  /** The place removed last, while it can still be put back. */
  removed: SavedPlace | null
  /** What went wrong the last time the saved places were read or changed, in words. */
  problem: string | null
  /** True while a change is on its way to the API, so nothing is pressed twice. */
  busy: boolean
  onAdd: (saved: SavedPlace) => void
  onStartFrom: (saved: SavedPlace) => void
  onRemove: (saved: SavedPlace) => void
  onPutBack: () => void
}

/**
 * The places saved earlier, each one press away from being in the trip. Nothing is drawn until
 * there is something to show, so the panel is no longer for somebody who saves nothing.
 */
export default function SavedPlaceList(props: Props) {
  const { saved, places, removed, problem, busy } = props

  if (saved.length === 0 && !removed && !problem) {
    return null
  }

  return (
    <section className="saved">
      {saved.length > 0 && <h2>Saved places</h2>}

      <ul>
        {saved.map((place) => {
          const inTrip = isInTrip(places, place)
          return (
            <li key={place.id}>
              <button
                className="saved-name"
                disabled={inTrip}
                title={inTrip ? 'Already in this trip' : 'Add to the trip'}
                aria-label={
                  inTrip ? `${place.name}, already in this trip` : `Add ${place.name} to the trip`
                }
                onClick={() => props.onAdd(place)}
              >
                {place.name}
              </button>
              {!isTheStart(places, place) && (
                <button
                  className="link"
                  title="Start the trip from here, in place of the start it has"
                  aria-label={`Start from ${place.name}`}
                  onClick={() => props.onStartFrom(place)}
                >
                  start here
                </button>
              )}
              <button
                className="link"
                disabled={busy}
                aria-label={`Remove ${place.name} from saved places`}
                onClick={() => props.onRemove(place)}
              >
                remove
              </button>
            </li>
          )
        })}
      </ul>

      {/* Removing takes one press and no question, so the way back has to be as short. */}
      {removed && (
        <p className="note">
          Removed “{removed.name}” from your saved places.{' '}
          <button className="link" disabled={busy} onClick={props.onPutBack}>
            Put it back
          </button>
        </p>
      )}

      {problem && <p className="warning">{problem}</p>}
    </section>
  )
}

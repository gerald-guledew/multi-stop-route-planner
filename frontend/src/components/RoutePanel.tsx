import type { Place, RoutePlan } from '../types'

interface Props {
  places: Place[]
  plan: RoutePlan | null
  error: string | null
  planning: boolean
  returnToStart: boolean
  onRename: (index: number, name: string) => void
  onRemove: (index: number) => void
  onReturnToStartChange: (returnToStart: boolean) => void
  onPlan: () => void
  onClear: () => void
}

const MAX_STOPS = 10

/** Hands the finished order to Google Maps, which does the actual navigating. */
function googleMapsLink(route: Place[]): string {
  const asCoordinates = (place: Place) => `${place.latitude},${place.longitude}`
  const origin = asCoordinates(route[0])
  const destination = asCoordinates(route[route.length - 1])
  const waypoints = route.slice(1, -1).map(asCoordinates).join('|')

  return `https://www.google.com/maps/dir/?api=1&origin=${origin}&destination=${destination}&waypoints=${encodeURIComponent(waypoints)}&travelmode=driving`
}

export default function RoutePanel(props: Props) {
  const { places, plan, error, planning, returnToStart } = props
  const stopCount = Math.max(places.length - 1, 0)
  const tooManyStops = stopCount > MAX_STOPS
  const saving = plan ? plan.enteredOrderDistanceKm - plan.totalDistanceKm : 0

  return (
    <aside className="panel">
      <header>
        <h1>Multi-Stop Route Planner</h1>
        <p className="lede">
          Click the map to drop your start, then each place you need to visit. Click a road,
          not a field: a pin with no street near it cannot be driven to.
        </p>
      </header>

      {places.length === 0 && <p className="empty">Nothing yet. Your first click is the start.</p>}

      <ol className="places">
        {places.map((place, index) => (
          <li key={index}>
            <span className={`pin pin-list ${index === 0 ? 'pin-start' : ''}`}>
              {index === 0 ? 'S' : index}
            </span>
            <input
              value={place.name}
              aria-label={`Name of place ${index + 1}`}
              onChange={(event) => props.onRename(index, event.target.value)}
            />
            <button className="link" onClick={() => props.onRemove(index)} aria-label="Remove">
              remove
            </button>
          </li>
        ))}
      </ol>

      {places.length > 0 && (
        <div className="controls">
          <label>
            <input
              type="checkbox"
              checked={returnToStart}
              onChange={(event) => props.onReturnToStartChange(event.target.checked)}
            />
            Return to the start
          </label>

          <div className="buttons">
            <button
              className="primary"
              onClick={props.onPlan}
              disabled={planning || stopCount < 1 || tooManyStops}
            >
              {planning ? 'Working it out…' : 'Plan the route'}
            </button>
            <button onClick={props.onClear}>Clear</button>
          </div>

          {tooManyStops && (
            <p className="warning">
              {stopCount} stops. Every order is checked, so this is capped at {MAX_STOPS}.
            </p>
          )}
        </div>
      )}

      {error && <p className="error">{error}</p>}

      {plan && (
        <section className="result">
          <h2>Best order</h2>

          <dl className="figures">
            <div>
              <dt>This order</dt>
              <dd className="big">{plan.totalDistanceKm.toFixed(2)} km</dd>
            </div>
            <div>
              <dt>The order you clicked</dt>
              <dd>{plan.enteredOrderDistanceKm.toFixed(2)} km</dd>
            </div>
            <div>
              <dt>Saved</dt>
              <dd className={saving > 0 ? 'good' : ''}>{saving.toFixed(2)} km</dd>
            </div>
          </dl>

          {/*
            Driving order, numbered the same as the pins on the map. Showing the legs as
            "from → to" instead reads badly once names and order disagree: a place called
            "Stop 4" can easily be the third one you drive to.
          */}
          <ol className="legs">
            {plan.route.map((place, index) => {
              const isStart = index === 0
              const isReturnHome = index === plan.route.length - 1 && returnToStart
              return (
                <li key={index}>
                  <span className={`pin pin-list ${isStart || isReturnHome ? 'pin-start' : ''}`}>
                    {isStart || isReturnHome ? 'S' : index}
                  </span>
                  <span className="where">{place.name}</span>
                  <span className="km">
                    {isStart ? '' : `${plan.legs[index - 1].distanceKm.toFixed(2)} km`}
                  </span>
                </li>
              )
            })}
          </ol>

          <p className="checked">{plan.ordersChecked.toLocaleString()} possible orders compared</p>

          <a className="primary button" href={googleMapsLink(plan.route)} target="_blank" rel="noreferrer">
            Open in Google Maps
          </a>
        </section>
      )}
    </aside>
  )
}

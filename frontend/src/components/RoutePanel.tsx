import PlaceSearchBox from './PlaceSearchBox'
import { isRoughGuess, roughly } from '../foundStart'
import { anyKeptInPlace, ordinal } from '../stops'
import type { DevicePosition, LocationProblem, Position } from '../location'
import type { Place, RoutePlan } from '../types'

interface Props {
  places: Place[]
  plan: RoutePlan | null
  error: string | null
  planning: boolean
  returnToStart: boolean
  onRename: (index: number, name: string) => void
  onRemove: (index: number) => void
  onToggleKeepInPlace: (index: number) => void
  onReturnToStartChange: (returnToStart: boolean) => void
  onPlan: () => void
  onClear: () => void
  onPickPlace: (place: Place) => void
  devicePosition: DevicePosition | null
  mapCentre: Position | null
  locating: boolean
  locationProblem: LocationProblem | null
  onUseMyLocation: () => void
}

const LOCATION_PROBLEMS: Record<LocationProblem, string> = {
  refused:
    'Your browser is not sharing its location with this page. Allow it beside the address bar, or pick your start on the map.',
  unavailable: 'Could not work out where you are. Pick your start on the map instead.',
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

  const here = props.devicePosition
  const startIsARoughGuess = isRoughGuess(places[0], here)

  return (
    <aside className="panel">
      <header>
        <h1>Multi-Stop Route Planner</h1>
        <p className="lede">
          Search an address or a business to drop a pin on it, or click the map for anywhere
          else. Drag a pin to move it. The boxes beside each pin are labels you can rename; they
          do not move anything.
        </p>
        <p className="lede">
          Click the street outside a place rather than the building itself. A pin inside a mall
          or a park has no road beside it and cannot be driven to.
        </p>
      </header>

      <PlaceSearchBox
        onPick={props.onPickPlace}
        devicePosition={props.devicePosition}
        start={places[0]}
        mapCentre={props.mapCentre}
      />

      {places.length === 0 && (
        <div className="empty">
          <p>Nothing yet. The first place you pick or click is your start.</p>
          <button onClick={props.onUseMyLocation} disabled={props.locating}>
            {props.locating ? 'Finding you…' : 'Start from where I am'}
          </button>
          {props.locationProblem && (
            <p className="warning">{LOCATION_PROBLEMS[props.locationProblem]}</p>
          )}
        </div>
      )}

      <ol className="places">
        {places.map((place, index) => (
          <li key={index}>
            <span
              className={`pin pin-list ${index === 0 ? 'pin-start' : ''} ${place.keepInPlace ? 'pin-kept' : ''}`}
            >
              {index === 0 ? 'S' : index}
            </span>
            <input
              value={place.name}
              placeholder="Label for this pin"
              title="A label only. The position comes from where you clicked the map."
              aria-label={`Label for place ${index + 1}`}
              onChange={(event) => props.onRename(index, event.target.value)}
            />
            {index > 0 && (
              <button
                className={`link ${place.keepInPlace ? 'kept' : ''}`}
                aria-pressed={place.keepInPlace === true}
                title="Keep this stop at this turn. The planner arranges the others around it."
                onClick={() => props.onToggleKeepInPlace(index)}
              >
                {place.keepInPlace ? 'kept' : 'keep'} {ordinal(index)}
              </button>
            )}
            <button className="link" onClick={() => props.onRemove(index)} aria-label="Remove">
              remove
            </button>
          </li>
        ))}
      </ol>

      {anyKeptInPlace(places) && (
        <p className="note">
          A kept stop stays at its turn. The planner arranges the others around it.
        </p>
      )}

      {startIsARoughGuess && here && (
        <p className="note">
          Your browser guessed this spot. By its own estimate it is good to about{' '}
          {roughly(here.accuracyMetres)}, the circle on the map. It can be further out than that.
          Drag the pin to where you really are.
        </p>
      )}

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
              // The plan carries names, not marks, so a kept stop is found again by its name.
              const keptInPlace =
                !isStart &&
                !isReturnHome &&
                places.some((entered, at) => at > 0 && entered.keepInPlace && entered.name === place.name)
              return (
                <li key={index}>
                  <span
                    className={`pin pin-list ${isStart || isReturnHome ? 'pin-start' : ''} ${keptInPlace ? 'pin-kept' : ''}`}
                  >
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

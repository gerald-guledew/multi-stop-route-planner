import { useEffect, useRef, useState, type ReactNode, type TouchEvent } from 'react'
import PlaceSearchBox from './PlaceSearchBox'
import {
  BookmarkIcon,
  CrossIcon,
  DownIcon,
  LockIcon,
  PlusIcon,
  RedoIcon,
  UndoIcon,
  UpIcon,
} from './icons'
import { bothChoices, otherChoiceSaid } from '../bothChoices'
import { chosenFor, needsAVehicle, orderGoneBy, type Order } from '../chosenFor'
import { climbSaid } from '../climb'
import { fastestSaid } from '../fastest'
import { fuelNote, litresSaid } from '../fuel'
import { howLong, legSaid } from '../duration'
import { isRoughGuess, roughly } from '../foundStart'
import { googleMapsLink } from '../googleMaps'
import { leastFuelSaid } from '../leastFuel'
import { betweenSaid, canAddOnTheWay, legAfter, type LookingAlong } from '../onTheWay'
import { canBeSaved, savedAt } from '../savedPlaces'
import { savingSaid } from '../saving'
import { useNarrowScreen } from '../screen'
import { differsFromTheShortest, shortestSaid } from '../shortestOrder'
import { anyKeptInPlace, canMove, isFull, mostStops, ordinal, tooManyToReturn, type Direction } from '../stops'
import { turnDistanceSaid, turnSaid, turnsCounted } from '../turns'
import { whatNext } from '../whatNext'
import type { DevicePosition, LocationProblem, Position } from '../location'
import type { FigureFrom, Place, PlaceAlong, RoutePlan, SavedPlace } from '../types'

interface Props {
  places: Place[]
  plan: RoutePlan | null
  /**
   * Whether the plan kept every stop at the turn it had, as it does once a stop is added on the
   * way. It then compared no orders, and has nothing to hold its own against.
   */
  orderHeld: boolean
  error: string | null
  planning: boolean
  returnToStart: boolean
  onRename: (index: number, name: string) => void
  onRemove: (index: number) => void
  onToggleKeepInPlace: (index: number) => void
  onChangeTurn: (index: number, direction: Direction) => void
  onReturnToStartChange: (returnToStart: boolean) => void
  /** Whether the order is to take the least fuel or be the fastest. */
  order: Order
  onOrderChange: (order: Order) => void
  onPlan: () => void
  onClear: () => void
  onPickPlace: (place: Place) => void
  /** A place picked to stop at on the way, found between two stops or anywhere along the plan. */
  onAddOnTheWay: (found: PlaceAlong) => void
  devicePosition: DevicePosition | null
  mapCentre: Position | null
  locating: boolean
  locationProblem: LocationProblem | null
  onUseMyLocation: () => void
  /** What is saved already, to tell which places in the trip still can be. */
  savedPlaces: SavedPlace[]
  /** True while a change to the saved places is on its way to the API. */
  changingSaved: boolean
  onSavePlace: (index: number) => void
  /** The list of saved places, drawn under the search box. */
  savedList: ReactNode
  /** The vehicle saved in the planner, drawn under the saved places. */
  vehicleSection: ReactNode
  /** Where the fuel use of that vehicle comes from. Null when there is no vehicle. */
  vehicleFigureFrom: FigureFrom | null
}

/** The orders to pick from, in the order they are offered. */
const ORDERS: { order: Order; says: string }[] = [
  { order: 'leastFuel', says: 'Least fuel' },
  { order: 'fastest', says: 'Fastest' },
]

const LOCATION_PROBLEMS: Record<LocationProblem, string> = {
  refused:
    'Your browser is not sharing its location with this page. Allow it beside the address bar, or pick your start on the map.',
  unavailable: 'Could not work out where you are. Pick your start on the map instead.',
}

/** A finger that has moved this far up or down the top of the panel meant to swipe it. */
const SWIPE_PIXELS = 24

/**
 * How the screen is used, and what the small drawings on the buttons mean. Beside the map on a
 * wide screen. On a phone it would take the whole screen, so there it sits folded away at the
 * foot of the panel.
 */
function HowItWorks() {
  return (
    <>
      <p className="lede">
        Search an address or a business to drop a pin on it, or click the map for anywhere
        else. Drag a pin to move it. On a phone, hold it until it lifts first. The box beside
        each pin is a label you can rename. It does not move anything.
      </p>
      <p className="lede">
        Click the street outside a place rather than the building itself. A pin inside a mall
        or a park has no road beside it and cannot be driven to.
      </p>
      <ul className="legend">
        <li>
          <LockIcon shut />
          <span>keeps a stop at its turn. The planner arranges the others around it</span>
        </li>
        <li>
          <BookmarkIcon filled={false} />
          <span>saves a place for next time, under its label</span>
        </li>
        <li>
          <UpIcon />
          <DownIcon />
          <span>move a stop one turn earlier or later</span>
        </li>
        <li>
          <CrossIcon />
          <span>takes a place out</span>
        </li>
        <li>
          <PlusIcon />
          <span>
            adds a stop on the way, between two stops of the planned route. The other stops keep
            their order
          </span>
        </li>
        <li>
          <UndoIcon />
          <RedoIcon />
          <span>undo the last change to the trip, or redo it, from the corner of the map</span>
        </li>
      </ul>
    </>
  )
}

/**
 * The bookmark beside a place in the trip. Once the place is saved it is filled in and no
 * longer a button, which is also how a press is seen to have worked.
 */
function SaveButton(props: {
  place: Place
  index: number
  saved: SavedPlace[]
  busy: boolean
  onSave: (index: number) => void
}) {
  const alreadySaved = savedAt(props.saved, props.place)

  if (alreadySaved) {
    return (
      <button
        className="icon-button on"
        disabled
        title={`Saved as “${alreadySaved.name}”`}
        aria-label={`Place ${props.index + 1} is saved as ${alreadySaved.name}`}
      >
        <BookmarkIcon filled />
      </button>
    )
  }

  return (
    <button
      className="icon-button"
      disabled={props.busy || !canBeSaved(props.place)}
      title="Save this place for next time, under this label"
      aria-label={`Save place ${props.index + 1}`}
      onClick={() => props.onSave(props.index)}
    >
      <BookmarkIcon filled={false} />
    </button>
  )
}

export default function RoutePanel(props: Props) {
  const { places, plan, error, planning, returnToStart, orderHeld } = props
  const stopCount = Math.max(places.length - 1, 0)
  // No more stops than the Google Maps link carries: ten one way, nine on a round trip.
  const full = isFull(places, returnToStart)
  const returnWaits = tooManyToReturn(places, returnToStart)
  // A plan that held the order compared it with no other, so it has no saving or other order to give.
  const compared = plan && !orderHeld ? plan : null
  const saving = compared ? savingSaid(compared.enteredOrderDistanceKm, compared.totalDistanceKm) : null
  const climb = plan ? climbSaid(plan.totalClimbMetres, plan.totalDescentMetres) : null
  const fuel = plan && plan.totalFuelLitres !== null ? litresSaid(plan.totalFuelLitres) : null
  // A plan is cleared whenever the vehicle changes, so one shown was made with the vehicle there is.
  const hasVehicle = props.vehicleFigureFrom !== null
  const goneBy = orderGoneBy({ order: props.order, hasVehicle })
  const chosen = chosenFor({ order: props.order, hasVehicle })
  // The other choice in one line, when the plan gives it. That says more than either held against
  // the shortest order, which is then left out.
  const both = compared ? bothChoices(compared) : null
  const quickest = compared && !both ? fastestSaid(chosen === 'time', compared) : null
  const leastFuel = compared && !both ? leastFuelSaid(goneBy === 'leastFuel', compared) : null
  // Shown only when it is another order than this one. The same figures twice say nothing.
  const shortest =
    !both && compared?.shortestOrder && differsFromTheShortest(compared) ? compared.shortestOrder : null
  const addsOnTheWay = canAddOnTheWay(places, returnToStart, plan, planning)
  // A stop on the way is looked for between two stops of the plan, or anywhere along it. It holds
  // for the plan it was asked of and no other, whose legs are not these.
  const [lookingAlong, setLookingAlong] = useState<LookingAlong | null>(null)
  const along = addsOnTheWay && lookingAlong?.plan === plan ? lookingAlong : null
  const searchBox = useRef<HTMLInputElement>(null)

  const here = props.devicePosition
  const startIsARoughGuess = isRoughGuess(places[0], here)

  // On a narrow screen the map has the screen and everything below the search box is a panel
  // that comes up from the bottom. Down, it shows one bar: where things stand and what to press
  // next. On a wide screen there is no such panel and none of this is seen.
  const narrow = useNarrowScreen()
  const [panelUp, setPanelUp] = useState(false)
  const next = whatNext(places, plan)
  const swipeStartedAt = useRef<number | null>(null)

  // The words for what went wrong are inside the panel. If it is down, nobody reads them.
  useEffect(() => {
    if (error || props.locationProblem) {
      setPanelUp(true)
    }
  }, [error, props.locationProblem])

  /**
   * All within the tap: a phone shows its keyboard only for a box given the cursor by a tap. The
   * panel goes down so that the results under the search box can be seen.
   */
  function lookAlongLeg(from: RoutePlan, leg: number) {
    setLookingAlong({ plan: from, leg })
    setPanelUp(false)
    searchBox.current?.focus()
  }

  function startSwipe(event: TouchEvent) {
    swipeStartedAt.current = event.touches[0].clientY
  }

  function endSwipe(event: TouchEvent) {
    const startedAt = swipeStartedAt.current
    swipeStartedAt.current = null
    if (startedAt === null) {
      return
    }
    const moved = event.changedTouches[0].clientY - startedAt
    if (Math.abs(moved) < SWIPE_PIXELS) {
      // A tap. The button under the finger deals with it.
      return
    }
    setPanelUp(moved < 0)
    // Or the tap that the browser makes of every touch would toggle it straight back.
    event.preventDefault()
  }

  return (
    <aside className="panel">
      <header className="intro">
        <h1>Multi-Stop Route Planner</h1>
        <HowItWorks />
      </header>

      <div className="top-bar">
        <PlaceSearchBox
          onPick={props.onPickPlace}
          onPickAlong={props.onAddOnTheWay}
          devicePosition={props.devicePosition}
          places={places}
          mapCentre={props.mapCentre}
          full={full}
          plan={addsOnTheWay ? plan : null}
          along={along}
          onLookAlong={setLookingAlong}
          box={searchBox}
        />
      </div>

      <div className={`sheet ${panelUp ? 'sheet-up' : ''}`}>
        <div className="sheet-head" onTouchStart={startSwipe} onTouchEnd={endSwipe}>
          <button
            className="sheet-handle"
            aria-expanded={panelUp}
            aria-label={panelUp ? 'Lower the trip panel' : 'Raise the trip panel'}
            onClick={() => setPanelUp((up) => !up)}
          >
            <span className="grabber" />
          </button>
          {/*
            A press anywhere on the bar that is not on its button raises or lowers the panel,
            because the handle above is a small thing to aim a thumb at.
          */}
          <div
            className="action-bar"
            onClick={(event) => {
              if (!(event.target as HTMLElement).closest('button, a')) {
                setPanelUp((up) => !up)
              }
            }}
          >
            <p>
            {next.says}
            {next.alsoSays && <span className="also">{next.alsoSays}</span>}
          </p>
            {next.step === 'locate' && (
              <button onClick={props.onUseMyLocation} disabled={props.locating}>
                {props.locating ? 'Finding you…' : 'Use my location'}
              </button>
            )}
            {next.step === 'plan' && (
              <button className="primary" onClick={props.onPlan} disabled={planning}>
                {planning ? 'Working it out…' : 'Plan the route'}
              </button>
            )}
            {next.step === 'open' && plan && (
              <a className="primary button" href={googleMapsLink(plan.route)} target="_blank" rel="noreferrer">
                Open in Google Maps
              </a>
            )}
          </div>
        </div>

        {/* Down, the rest of the panel is off the screen, so it is taken out of reach as well. */}
        <div className="sheet-body" inert={narrow && !panelUp}>
          {props.savedList}
          {props.vehicleSection}

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
            {places.map((place, index) => {
              // The plan's order, so that the "+" under a place is the drive on to the next.
              const leg = plan && addsOnTheWay ? legAfter(places, plan, index, returnToStart) : null
              return (
              <li key={index}>
                <span
                  className={`pin pin-list ${index === 0 ? 'pin-start' : ''} ${place.keepInPlace ? 'pin-kept' : ''}`}
                >
                  {index === 0 ? 'S' : index}
                </span>
                {/* The label, and under it what can be done to the place. */}
                <div className="place">
                  <input
                    value={place.name}
                    placeholder="Label for this pin"
                    title="A label only. The position comes from where you clicked the map."
                    aria-label={`Label for place ${index + 1}`}
                    onChange={(event) => props.onRename(index, event.target.value)}
                  />
                  <div className="place-actions">
                    {/* With one stop there is no other turn to move it to. */}
                    {index > 0 && stopCount > 1 && (
                      <>
                        <button
                          className="icon-button"
                          disabled={!canMove(places, index, 'up')}
                          title="Move this stop one turn earlier"
                          aria-label={`Move stop ${index} up`}
                          onClick={() => props.onChangeTurn(index, 'up')}
                        >
                          <UpIcon />
                        </button>
                        <button
                          className="icon-button"
                          disabled={!canMove(places, index, 'down')}
                          title="Move this stop one turn later"
                          aria-label={`Move stop ${index} down`}
                          onClick={() => props.onChangeTurn(index, 'down')}
                        >
                          <DownIcon />
                        </button>
                      </>
                    )}
                    {index > 0 && (
                      <button
                        className={`icon-button ${place.keepInPlace ? 'on' : ''}`}
                        aria-pressed={place.keepInPlace === true}
                        title={
                          place.keepInPlace
                            ? `Kept ${ordinal(index)}. Press to let the planner move it.`
                            : `Keep this stop ${ordinal(index)}. The planner arranges the others around it.`
                        }
                        aria-label={`Keep stop ${index} at this turn`}
                        onClick={() => props.onToggleKeepInPlace(index)}
                      >
                        <LockIcon shut={place.keepInPlace === true} />
                      </button>
                    )}
                    <SaveButton
                      place={place}
                      index={index}
                      saved={props.savedPlaces}
                      busy={props.changingSaved}
                      onSave={props.onSavePlace}
                    />
                    <button
                      className="icon-button remove"
                      title="Take this place out of the trip"
                      aria-label={`Remove place ${index + 1}`}
                      onClick={() => props.onRemove(index)}
                    >
                      <CrossIcon />
                    </button>
                  </div>
                </div>
                {plan && leg !== null && (
                  <button
                    className="icon-button add-on-the-way"
                    title={`Add a stop ${betweenSaid(plan, leg)}`}
                    aria-label={`Add a stop ${betweenSaid(plan, leg)}`}
                    onClick={() => lookAlongLeg(plan, leg)}
                  >
                    <PlusIcon />
                  </button>
                )}
              </li>
              )
            })}
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
              Move the pin to where you really are.
            </p>
          )}

          {places.length > 0 && (
            <div className="controls">
              <label className={returnWaits ? 'waiting' : undefined}>
                <input
                  type="checkbox"
                  checked={returnToStart}
                  disabled={returnWaits}
                  onChange={(event) => props.onReturnToStartChange(event.target.checked)}
                />
                <span>
                  Return to the start
                  {returnWaits && <small>Needs {mostStops(true)} stops or fewer to enable</small>}
                </span>
              </label>
              {/* A plan on its way was asked for with these as they stood. */}
              <div className="chosen-for" role="radiogroup" aria-label="What the order is chosen for">
                {ORDERS.map(({ order, says }) => {
                  const waiting = needsAVehicle(order, hasVehicle)
                  return (
                    <label key={order} className={waiting ? 'waiting' : undefined}>
                      <input
                        type="radio"
                        name="chosen-for"
                        checked={goneBy === order}
                        disabled={planning || waiting}
                        onChange={() => props.onOrderChange(order)}
                      />
                      <span>
                        {says}
                        {waiting && <small>Needs your vehicle details to enable</small>}
                      </span>
                    </label>
                  )
                })}
              </div>

              <div className="buttons">
                <button
                  className="primary"
                  onClick={props.onPlan}
                  disabled={planning || stopCount < 1}
                >
                  {planning ? 'Working it out…' : 'Plan the route'}
                </button>
                <button onClick={props.onClear}>Clear</button>
              </div>
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
                {plan.totalDurationSeconds !== null && (
                  <div>
                    <dt>Driving time</dt>
                    <dd>{howLong(plan.totalDurationSeconds)}</dd>
                  </div>
                )}
                {climb && (
                  <div>
                    <dt>Climb</dt>
                    <dd>{climb}</dd>
                  </div>
                )}
                {fuel && (
                  <div>
                    <dt>Fuel</dt>
                    <dd>{fuel}</dd>
                  </div>
                )}
                {shortest && (
                  <div>
                    <dt>The shortest order</dt>
                    <dd>{shortestSaid(shortest, chosen)}</dd>
                  </div>
                )}
                {compared && (
                  <div>
                    <dt>The order you clicked</dt>
                    <dd>{compared.enteredOrderDistanceKm.toFixed(2)} km</dd>
                  </div>
                )}
                {saving && (
                  <div>
                    <dt>{saving.label}</dt>
                    <dd className={saving.good ? 'good' : ''}>{saving.figure}</dd>
                  </div>
                )}
              </dl>

              {both && <p className="other-choice">{otherChoiceSaid(both)}</p>}
              {quickest && <p className="note">{quickest}</p>}
              {leastFuel && <p className="note">{leastFuel}</p>}
              {orderHeld && (
                <p className="note">A stop on the way leaves the other stops in the order they had.</p>
              )}
              {plan.totalDurationSeconds !== null && (
                <p className="note">
                  Driving time is for clear roads. It allows nothing for traffic, or for the
                  time you spend at each stop.
                </p>
              )}
              {climb && (
                <p className="note">
                  Climb adds up every rise of 10 m or more along the roads, and the same for
                  down. It is read off the height of the ground, not of the road, so take it as
                  an estimate. A high bridge counts as level.
                </p>
              )}
              {fuel ? (
                <p className="note">
                  {fuelNote(props.vehicleFigureFrom ?? 'dashboard', plan?.fuelCountsHills === true, plan?.fuelCountsSpeed === true)}
                </p>
              ) : (
                <p className="note">Add your vehicle above to see the fuel this trip takes.</p>
              )}

              {/*
                Driving order, numbered the same as the pins on the map. Showing the legs as
                "from → to" instead reads badly once names and order disagree: a place called
                "Stop 4" can easily be the third one you drive to.
              */}
              <ol className="legs">
                {plan.route.map((place, index) => {
                  const isStart = index === 0
                  const leg = isStart ? null : plan.legs[index - 1]
                  const legClimb = leg ? climbSaid(leg.climbMetres, leg.descentMetres) : null
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
                      {/* How far and how long, and under it how hilly, when the roads say. */}
                      <span className="km">
                        {leg ? legSaid(leg) : ''}
                        {legClimb && <span className="climb">{legClimb}</span>}
                      </span>
                      {/* How to drive there, along the road drawn on the map. Closed until asked for. */}
                      {leg && leg.turns.length > 0 && (
                        <details className="turns">
                          <summary>{turnsCounted(leg.turns)}</summary>
                          <ol>
                            {leg.turns.map((turn, at) => (
                              <li key={at}>
                                <span className="turn-said">{turnSaid(turn, place.name)}</span>
                                <span className="turn-km">{turnDistanceSaid(turn.distanceMetres)}</span>
                              </li>
                            ))}
                          </ol>
                        </details>
                      )}
                    </li>
                  )
                })}
              </ol>

              {plan.legs.some((leg) => leg.turns.length > 0) && (
                <p className="note">The turns follow the roads drawn on the map. Google Maps picks its own.</p>
              )}

              {compared && (
                <p className="checked">{compared.ordersChecked.toLocaleString()} possible orders compared</p>
              )}

              <a className="primary button" href={googleMapsLink(plan.route)} target="_blank" rel="noreferrer">
                Open in Google Maps
              </a>
            </section>
          )}

          <details className="how">
            <summary>How this works</summary>
            <HowItWorks />
          </details>
        </div>
      </div>

    </aside>
  )
}

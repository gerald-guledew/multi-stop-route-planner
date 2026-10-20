import L from 'leaflet'
import { useEffect, useEffectEvent, useMemo, useRef, useState } from 'react'
import {
  Circle,
  CircleMarker,
  MapContainer,
  Marker,
  Polyline,
  Popup,
  TileLayer,
  useMap,
  useMapEvents,
} from 'react-leaflet'
import { heightAt } from '../api'
import { sitsOn } from '../foundStart'
import { closeEnoughToCheck, everywhereTheTripGoes, type Picked } from '../framing'
import { heightSaid, spotOf } from '../height'
import type { DevicePosition, Position } from '../location'
import {
  afterHolding,
  afterMoving,
  droppedAt,
  holdFor,
  landing,
  pressed,
  type Grip,
  type Point,
} from '../pinDrag'
import { coveredAtTheTopOfTheMap } from '../screen'
import type { Place, RoutePlan } from '../types'

// Auckland, because that is where this was built and tested.
const DEFAULT_CENTRE: [number, number] = [-36.8485, 174.7621]

interface Props {
  places: Place[]
  plan: RoutePlan | null
  unroutablePlace?: string
  /** Where the browser says this device is. Null if it was not asked, or said no. */
  devicePosition: DevicePosition | null
  /** How many times the browser has been asked where the device is. */
  askedTimes: number
  /** The place picked last from the search or the saved places. */
  picked: Picked | null
  /** How many times a plan has been asked for and the answer is in. */
  plannedTimes: number
  onMapClick: (latitude: number, longitude: number) => void
  onMovePlace: (index: number, latitude: number, longitude: number) => void
  onCentreChange: (centre: Position) => void
}

/**
 * Numbered pins, drawn in HTML rather than with Leaflet's default icon. It avoids the
 * broken-image problem bundlers cause with Leaflet's image paths, and it lets the pin show
 * its position in the route. Each says which place it stands for, to be picked up by.
 */
type PinState = 'entered' | 'planned' | 'problem'

function numberedIcon(
  label: string,
  state: PinState,
  keptInPlace: boolean,
  index: number,
): L.DivIcon {
  return L.divIcon({
    className: '',
    html: `<span class="pin pin-${state}${keptInPlace ? ' pin-kept' : ''}" data-place="${index}">${label}</span>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  })
}

/**
 * One place's pin, and its popup.
 *
 * The map is drawn again whenever the page is, and the page is drawn again each time the map
 * comes to rest, because search wants to know where the map is looking. Leaflet takes a new
 * position, icon or popup content as a change to the pin, and an open popup answers any change
 * by panning the map back to show itself. So each is made again only when what it shows has
 * changed. Otherwise the map could not be moved away from a pin whose popup is open.
 */
function Pin({
  place,
  index,
  label,
  state,
  keptInPlace,
  height,
  onOpen,
}: {
  place: Place
  index: number
  label: string
  state: PinState
  keptInPlace: boolean
  height: string | null
  onOpen: () => void
}) {
  const { name, latitude, longitude } = place
  const position = useMemo<[number, number]>(() => [latitude, longitude], [latitude, longitude])
  const icon = useMemo(
    () => numberedIcon(label, state, keptInPlace, index),
    [label, state, keptInPlace, index],
  )
  const content = useMemo(
    () => (
      <>
        <strong>{name}</strong>
        <br />
        {latitude.toFixed(5)}, {longitude.toFixed(5)}
        {height && (
          <>
            <br />
            {height}
          </>
        )}
      </>
    ),
    [name, latitude, longitude, height],
  )

  return (
    // Not draggable to Leaflet: PickUpPins moves pins.
    <Marker position={position} icon={icon} title="Drag to move" eventHandlers={{ popupopen: onOpen }}>
      {/* A popup that opens near the top of a phone's map is slid down clear of the search box. */}
      <Popup autoPanPaddingTopLeft={[5, 5 + coveredAtTheTopOfTheMap()]}>{content}</Popup>
    </Marker>
  )
}

/**
 * Picks pins up and moves them, by the rules in pinDrag.ts. Leaflet's own dragging takes a pin
 * the moment a finger touches it, which is how a thumb meant for the map moved pins. So Leaflet
 * is not told the pins can be dragged, and this moves them instead.
 *
 * A pin can land in the wrong spot: a rough location, a click on a building rather than its
 * street. Moving it puts it right without losing its name or its place in the list.
 *
 * While a finger rests on a pin the map still has it, and moving off moves the map. Once the
 * pin comes away the map is held still, the pin is faded where it was, and a larger one follows
 * the finger. Putting a pin down is no click, so the click that follows is swallowed. It would
 * open the popup, or with a mouse let go elsewhere, add a stop there.
 */
function PickUpPins({ onMovePlace }: { onMovePlace: Props['onMovePlace'] }) {
  const map = useMap()
  // The pin being carried, as drawn: its label, its look, and where it would land.
  const [carried, setCarried] = useState<{ label: string; look: string; at: Point } | null>(null)
  const move = useEffectEvent(onMovePlace)

  useEffect(() => {
    const container = map.getContainer()
    let grip: Grip | null = null
    let pointerId = -1
    let pin: HTMLElement | null = null
    let timer: ReturnType<typeof setTimeout> | undefined

    function pinUnder(target: EventTarget | null): HTMLElement | null {
      return target instanceof Element ? target.closest<HTMLElement>('.pin[data-place]') : null
    }

    function onTheMap(event: PointerEvent): Point {
      const point = map.mouseEventToContainerPoint(event)
      return { x: point.x, y: point.y }
    }

    function show(now: Grip) {
      if (now.stage === 'carrying' && pin) {
        setCarried({ label: pin.textContent ?? '', look: pin.className, at: landing(now) })
      }
    }

    function comeAway(now: Grip) {
      // The map has followed the finger so far. From here only the pin does.
      map.dragging.disable()
      pin?.closest('.leaflet-marker-icon')?.classList.add('pin-picked-up')
      show(now)
    }

    function swallowTheNextClick() {
      const swallow = (event: MouseEvent) => {
        event.stopPropagation()
        event.preventDefault()
      }
      window.addEventListener('click', swallow, { capture: true, once: true })
      // A click from letting go comes at once, if at all.
      setTimeout(() => window.removeEventListener('click', swallow, { capture: true }), 400)
    }

    function letGo() {
      clearTimeout(timer)
      if (grip?.stage === 'carrying') {
        map.dragging.enable()
        swallowTheNextClick()
      }
      pin?.closest('.leaflet-marker-icon')?.classList.remove('pin-picked-up')
      grip = null
      pointerId = -1
      pin = null
      setCarried(null)
    }

    function onDown(event: PointerEvent) {
      if (grip) {
        // A second finger: a pinch, not a pin.
        if (event.pointerId !== pointerId) {
          letGo()
        }
        return
      }
      const under = pinUnder(event.target)
      if (!under || !event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) {
        return
      }
      grip = pressed(event.pointerType, onTheMap(event))
      pointerId = event.pointerId
      pin = under
      const hold = holdFor(grip)
      if (hold !== null) {
        timer = setTimeout(() => {
          if (grip) {
            grip = afterHolding(grip)
            comeAway(grip)
          }
        }, hold)
      }
    }

    function onMove(event: PointerEvent) {
      if (!grip || event.pointerId !== pointerId) {
        return
      }
      const before = grip
      const now = afterMoving(grip, onTheMap(event))
      if (now === null) {
        // Moved off before the pin came away: the map has the finger.
        letGo()
      } else if (before.stage === 'resting' && now.stage === 'carrying') {
        grip = now
        comeAway(now)
      } else {
        grip = now
        show(now)
      }
    }

    function onUp(event: PointerEvent) {
      if (!grip || event.pointerId !== pointerId) {
        return
      }
      const at = droppedAt(grip)
      const index = Number(pin?.dataset.place)
      letGo()
      if (at) {
        const spot = map.containerPointToLatLng([at.x, at.y])
        move(index, spot.lat, spot.lng)
      }
    }

    function onCancel(event: PointerEvent) {
      if (grip && event.pointerId === pointerId) {
        letGo()
      }
    }

    // A mouse pressed on a pin is the pin's. Let through, it would start moving the map.
    function keepTheMapStill(event: MouseEvent) {
      if (event.button === 0 && pinUnder(event.target)) {
        event.stopPropagation()
      }
    }

    container.addEventListener('pointerdown', onDown, true)
    container.addEventListener('mousedown', keepTheMapStill, true)
    document.addEventListener('pointermove', onMove)
    document.addEventListener('pointerup', onUp)
    document.addEventListener('pointercancel', onCancel)
    return () => {
      clearTimeout(timer)
      if (grip?.stage === 'carrying') {
        map.dragging.enable()
      }
      container.removeEventListener('pointerdown', onDown, true)
      container.removeEventListener('mousedown', keepTheMapStill, true)
      document.removeEventListener('pointermove', onMove)
      document.removeEventListener('pointerup', onUp)
      document.removeEventListener('pointercancel', onCancel)
    }
  }, [map])

  if (!carried) {
    return null
  }
  return (
    <div className="carried" style={{ left: carried.at.x, top: carried.at.y }}>
      <span className={carried.look}>{carried.label}</span>
      <span className="carried-stem" />
      <span className="carried-dot" />
    </div>
  )
}

function ClickToAddStop({ onMapClick }: { onMapClick: Props['onMapClick'] }) {
  useMapEvents({
    click: (event) => onMapClick(event.latlng.lat, event.latlng.lng),
  })
  return null
}

/**
 * Where this device is: a dot, and while that is only the browser's guess, a circle around it
 * as wide as the browser's own doubt. On a laptop that circle can cover several houses. Drawing
 * it says so, where a pin alone would claim more than anybody knows.
 *
 * Once the person has dragged the guess to the right spot there is no doubt left to draw.
 *
 * Neither takes clicks, so a click inside the circle still adds a place like anywhere else.
 */
function YouAreHere({ at }: { at: DevicePosition }) {
  const centre: [number, number] = [at.latitude, at.longitude]

  return (
    <>
      {!at.correctedByHand && (
        <Circle
          center={centre}
          radius={at.accuracyMetres}
          interactive={false}
          pathOptions={{ className: 'you-are-here-doubt' }}
        />
      )}
      <CircleMarker
        center={centre}
        radius={6}
        interactive={false}
        pathOptions={{ className: 'you-are-here' }}
      />
    </>
  )
}

/**
 * Shows the start the browser found, close enough to judge it.
 *
 * A start found for you usually falls inside what the map already shows, so nothing else moves
 * the map, and at that distance the pin looks exact and the circle of doubt is two pixels wide.
 * This frames the circle instead: a street for a rough fix, a suburb for a very rough one.
 *
 * It acts when a new position arrives, and only if that position became the start. If a start
 * was picked while the browser was still working it out, the map stays where it was put.
 *
 * The browser can come back with a better answer to the same asking. The map follows that too,
 * but only while it is still where the last framing left it. Once somebody has moved the map,
 * it is theirs, and a better answer moves the pin and nothing else.
 */
function ShowTheStartFoundForYou({
  places,
  at,
  askedTimes,
}: {
  places: Place[]
  at: DevicePosition | null
  askedTimes: number
}) {
  const map = useMap()
  const framed = useRef<{ forAsking: number; centre: L.LatLng; zoom: number } | null>(null)

  useEffect(() => {
    if (!at || !sitsOn(places[0], at)) {
      return
    }
    // A pin just dropped by hand is already where the person is looking.
    if (at.correctedByHand) {
      return
    }

    const last = framed.current
    const firstAnswerToThisAsking = last === null || last.forAsking !== askedTimes
    const mapIsWhereWeLeftIt =
      last !== null && map.getZoom() === last.zoom && map.getCenter().equals(last.centre)
    if (!firstAnswerToThisAsking && !mapIsWhereWeLeftIt) {
      return
    }

    const doubt = L.latLng(at.latitude, at.longitude).toBounds(at.accuracyMetres * 2)
    // Without animation, so the view has settled by the time it is remembered.
    map.fitBounds(doubt, {
      paddingTopLeft: [60, 60 + coveredAtTheTopOfTheMap()],
      paddingBottomRight: [60, 60],
      maxZoom: 17,
      animate: false,
    })
    framed.current = { forAsking: askedTimes, centre: map.getCenter(), zoom: map.getZoom() }
    // Only a new position should move the map. Adding or renaming a place later must not.
  }, [at, map])

  return null
}

/**
 * Tells the page where the map is looking: once when it opens, then each time it comes to rest.
 * Search uses it to put the nearest match first while the trip has no start yet.
 */
function ReportCentre({ onCentreChange }: { onCentreChange: Props['onCentreChange'] }) {
  const map = useMapEvents({
    moveend: () => report(),
  })

  function report() {
    const centre = map.getCenter()
    onCentreChange({ latitude: centre.lat, longitude: centre.lng })
  }

  // Opening the map is not a move, so nothing would be reported until the first drag.
  useEffect(report, [map])

  return null
}

/**
 * Goes to a place picked from the search or the saved places, close enough to check it is the
 * right one. A search result can be anywhere in the country, and a pin dropped off screen looks
 * exactly like nothing happening. A click on the map needs none of this: it lands where
 * somebody is already looking.
 */
function GoToThePlacePicked({ picked }: { picked: Picked | null }) {
  const map = useMap()

  useEffect(() => {
    if (picked) {
      map.flyTo([picked.at.latitude, picked.at.longitude], closeEnoughToCheck(map.getZoom()))
    }
  }, [picked, map])

  return null
}

/**
 * Shows the whole trip once a plan is asked for and the answer is in: every place, and the
 * roads between them if there is a plan. Going to each place picked leaves the map close to the
 * last one, so this is what brings the trip back into one view.
 *
 * Nothing else about the trip moves the map. Renaming, removing or dragging leaves the view
 * where you put it.
 */
function ShowTheWholeTrip({
  places,
  plan,
  plannedTimes,
}: {
  places: Place[]
  plan: RoutePlan | null
  plannedTimes: number
}) {
  const map = useMap()
  const plannedBefore = useRef(plannedTimes)

  useEffect(() => {
    if (plannedTimes === plannedBefore.current) {
      return
    }
    plannedBefore.current = plannedTimes

    const points = everywhereTheTripGoes(places, plan)
    if (points.length > 0) {
      map.flyToBounds(L.latLngBounds(points), {
        paddingTopLeft: [40, 40 + coveredAtTheTopOfTheMap()],
        paddingBottomRight: [40, 40],
        // A trip around a few streets still shows the suburb it is in.
        maxZoom: 15,
      })
    }
  }, [plannedTimes, places, plan, map])

  return null
}

export default function RouteMap({
  places,
  plan,
  unroutablePlace,
  devicePosition,
  askedTimes,
  picked,
  plannedTimes,
  onMapClick,
  onMovePlace,
  onCentreChange,
}: Props) {
  // Once planned, pins are numbered by driving order instead of the order they were clicked.
  const plannedOrder = plan?.route.map((place) => place.name) ?? []

  // The height of each spot a pin has stood on, asked for when its popup first opens there.
  const [heights, setHeights] = useState<Record<string, number | null>>({})

  function askHowHigh(place: Place) {
    const spot = spotOf(place)
    if (spot in heights) {
      return
    }
    heightAt(place).then(
      (metres) => setHeights((known) => ({ ...known, [spot]: metres })),
      // No answer leaves the popup as it was, and the next opening asks again. A planner out
      // of reach is said where it matters, when a plan is asked for.
      () => undefined,
    )
  }

  function labelFor(place: Place, index: number): string {
    if (plannedOrder.length === 0) {
      return index === 0 ? 'S' : String(index)
    }
    const position = plannedOrder.indexOf(place.name)
    return position <= 0 ? 'S' : String(position)
  }

  return (
    <MapContainer center={DEFAULT_CENTRE} zoom={12} className="map">
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />

      <ClickToAddStop onMapClick={onMapClick} />
      <PickUpPins onMovePlace={onMovePlace} />
      <ReportCentre onCentreChange={onCentreChange} />
      <GoToThePlacePicked picked={picked} />
      <ShowTheWholeTrip places={places} plan={plan} plannedTimes={plannedTimes} />

      <ShowTheStartFoundForYou places={places} at={devicePosition} askedTimes={askedTimes} />

      {devicePosition && <YouAreHere at={devicePosition} />}

      {places.map((place, index) => (
        <Pin
          key={`${place.name}-${place.latitude}-${place.longitude}`}
          place={place}
          index={index}
          label={labelFor(place, index)}
          state={place.name === unroutablePlace ? 'problem' : plan ? 'planned' : 'entered'}
          keptInPlace={index > 0 && place.keepInPlace === true}
          height={heightSaid(heights[spotOf(place)])}
          onOpen={() => askHowHigh(place)}
        />
      ))}

      {/*
        One line per leg. When the API sends the road it follows, draw that. When distances are
        straight lines there is no road to draw, so the leg is dashed to say so rather than
        pretending a line through the harbour is a drive.
      */}
      {plan?.legs.map((leg, index) => {
        const from = plan.route[index]
        const to = plan.route[index + 1]

        return leg.path.length > 0 ? (
          <Polyline key={index} positions={leg.path} weight={5} opacity={0.8} />
        ) : (
          <Polyline
            key={index}
            positions={[
              [from.latitude, from.longitude],
              [to.latitude, to.longitude],
            ]}
            dashArray="6 10"
            weight={3}
          />
        )
      })}
    </MapContainer>
  )
}

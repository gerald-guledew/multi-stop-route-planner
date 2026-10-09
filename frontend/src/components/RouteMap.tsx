import L from 'leaflet'
import { useEffect, useRef } from 'react'
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
import { sitsOn } from '../foundStart'
import type { DevicePosition, Position } from '../location'
import type { Place, RoutePlan } from '../types'

// Auckland, because that is where this was built and tested.
const DEFAULT_CENTRE: [number, number] = [-36.8485, 174.7621]

interface Props {
  places: Place[]
  plan: RoutePlan | null
  unroutablePlace?: string
  /** Where the browser says this device is. Null if it was not asked, or said no. */
  devicePosition: DevicePosition | null
  onMapClick: (latitude: number, longitude: number) => void
  onMovePlace: (index: number, latitude: number, longitude: number) => void
  onCentreChange: (centre: Position) => void
}

/**
 * Numbered pins, drawn in HTML rather than with Leaflet's default icon. It avoids the
 * broken-image problem bundlers cause with Leaflet's image paths, and it lets the pin show
 * its position in the route.
 */
function numberedIcon(label: string, state: 'entered' | 'planned' | 'problem'): L.DivIcon {
  return L.divIcon({
    className: '',
    html: `<span class="pin pin-${state}">${label}</span>`,
    iconSize: [28, 28],
    iconAnchor: [14, 14],
  })
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
 */
function ShowTheStartFoundForYou({ places, at }: { places: Place[]; at: DevicePosition | null }) {
  const map = useMap()

  useEffect(() => {
    if (!at || !sitsOn(places[0], at)) {
      return
    }
    // A pin just dropped by hand is already where the person is looking.
    if (at.correctedByHand) {
      return
    }
    const doubt = L.latLng(at.latitude, at.longitude).toBounds(at.accuracyMetres * 2)
    map.fitBounds(doubt, { padding: [60, 60], maxZoom: 17 })
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
 * Moves the map when a place is added somewhere it is not showing.
 *
 * A click always lands inside the view, so this does nothing for clicks. A search result or
 * your own location can be anywhere in the country, and a pin dropped off screen looks exactly
 * like nothing happening.
 *
 * Only an added place moves the map. Renaming or removing one leaves the view where you put it.
 */
function KeepNewPlacesInView({ places }: { places: Place[] }) {
  const map = useMap()
  const countBefore = useRef(0)

  useEffect(() => {
    const added = places.length > countBefore.current
    countBefore.current = places.length
    if (!added) {
      return
    }

    const pins = places.map((place) => L.latLng(place.latitude, place.longitude))
    if (pins.every((pin) => map.getBounds().contains(pin))) {
      return
    }

    if (pins.length === 1) {
      // Close enough to recognise the street, without zooming out if already closer.
      map.setView(pins[0], Math.max(map.getZoom(), 14))
    } else {
      map.fitBounds(L.latLngBounds(pins), { padding: [40, 40], maxZoom: 15 })
    }
  }, [places, map])

  return null
}

export default function RouteMap({
  places,
  plan,
  unroutablePlace,
  devicePosition,
  onMapClick,
  onMovePlace,
  onCentreChange,
}: Props) {
  // Once planned, pins are numbered by driving order instead of the order they were clicked.
  const plannedOrder = plan?.route.map((place) => place.name) ?? []

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
      <ReportCentre onCentreChange={onCentreChange} />
      <KeepNewPlacesInView places={places} />

      <ShowTheStartFoundForYou places={places} at={devicePosition} />

      {devicePosition && <YouAreHere at={devicePosition} />}

      {places.map((place, index) => (
        <Marker
          key={`${place.name}-${place.latitude}-${place.longitude}`}
          position={[place.latitude, place.longitude]}
          icon={numberedIcon(
            labelFor(place, index),
            place.name === unroutablePlace ? 'problem' : plan ? 'planned' : 'entered',
          )}
          // A pin can land in the wrong spot: a rough location, a click on a building rather
          // than its street. Dragging puts it right without losing its name or its place in
          // the list.
          draggable
          title="Drag to move"
          eventHandlers={{
            dragend: (event) => {
              const droppedAt = (event.target as L.Marker).getLatLng()
              onMovePlace(index, droppedAt.lat, droppedAt.lng)
            },
          }}
        >
          <Popup>
            <strong>{place.name}</strong>
            <br />
            {place.latitude.toFixed(5)}, {place.longitude.toFixed(5)}
          </Popup>
        </Marker>
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

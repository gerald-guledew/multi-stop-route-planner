import L from 'leaflet'
import { MapContainer, Marker, Polyline, Popup, TileLayer, useMapEvents } from 'react-leaflet'
import type { Place, RoutePlan } from '../types'

// Auckland, because that is where this was built and tested.
const DEFAULT_CENTRE: [number, number] = [-36.8485, 174.7621]

interface Props {
  places: Place[]
  plan: RoutePlan | null
  unroutablePlace?: string
  onMapClick: (latitude: number, longitude: number) => void
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

export default function RouteMap({ places, plan, unroutablePlace, onMapClick }: Props) {
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

      {places.map((place, index) => (
        <Marker
          key={`${place.name}-${place.latitude}-${place.longitude}`}
          position={[place.latitude, place.longitude]}
          icon={numberedIcon(
            labelFor(place, index),
            place.name === unroutablePlace ? 'problem' : plan ? 'planned' : 'entered',
          )}
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

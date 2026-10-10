import { useEffect, useEffectEvent, useState } from 'react'
import { ApiError, optimizeRoute } from './api'
import { afterABetterAnswer, afterDragging } from './foundStart'
import { toggleKeptInPlace, withoutPlace } from './stops'
import {
  followPosition,
  type DevicePosition,
  type LocationProblem,
  type Position,
} from './location'
import RouteMap from './components/RouteMap'
import RoutePanel from './components/RoutePanel'
import type { Place, RoutePlan } from './types'

export default function App() {
  const [places, setPlaces] = useState<Place[]>([])
  const [returnToStart, setReturnToStart] = useState(true)
  const [plan, setPlan] = useState<RoutePlan | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [unroutablePlace, setUnroutablePlace] = useState<string | undefined>()
  const [planning, setPlanning] = useState(false)
  const [locating, setLocating] = useState(false)
  const [locationProblem, setLocationProblem] = useState<LocationProblem | null>(null)
  const [mapCentre, setMapCentre] = useState<Position | null>(null)
  // Where the browser last said this device is. It is asked when the page opens and when
  // "Start from where I am" is pressed, and listened to for half a minute after each, in case
  // it comes back with better. After that nothing follows the device.
  const [devicePosition, setDevicePosition] = useState<DevicePosition | null>(null)
  // One asking is one question put to the browser. The page puts the first, unprompted, because
  // most trips start from where you are. The button puts the rest.
  const [asking, setAsking] = useState({ times: 1, byButton: false })

  // The browser answers seconds later, and may answer more than once. These two see the places
  // and the position as they are when it does, not as they were when it was asked.
  const onAnswer = useEffectEvent((here: DevicePosition, isFirst: boolean) => {
    setLocating(false)

    if (isFirst) {
      // Search looks from here from now on, whether or not it also becomes the start.
      setDevicePosition(here)
      // If a start was picked while the browser was working it out, that choice stands.
      if (places.length === 0) {
        setPlaces([{ name: 'My location', latitude: here.latitude, longitude: here.longitude }])
      }
      return
    }

    const better = afterABetterAnswer(places, devicePosition, here)
    setPlaces(better.places)
    setDevicePosition(better.devicePosition)
    if (better.movedTheStart) {
      // Any previous answer was worked out for where the start used to be.
      setPlan(null)
      setError(null)
      setUnroutablePlace(undefined)
    }
  })

  const onProblem = useEffectEvent((problem: LocationProblem) => {
    setLocating(false)
    // Permission can be taken back. A position from before it was is not one to go on using.
    if (problem === 'refused') {
      setDevicePosition(null)
    }
    // Saying no to the page's own question is an answer, not an error, so nothing is shown.
    // Pressing the button is asking for an answer, so a failure then is worth explaining.
    if (asking.byButton) {
      setLocationProblem(problem)
    }
  })

  // The listening lasts as long as the asking it belongs to. Asking again, or leaving the
  // page, stops it.
  useEffect(() => {
    setLocating(true)
    setLocationProblem(null)
    return followPosition(onAnswer, onProblem)
  }, [asking])

  function addPlace(latitude: number, longitude: number) {
    setPlaces((current) => [
      ...current,
      { name: current.length === 0 ? 'Start' : `Stop ${current.length}`, latitude, longitude },
    ])
    // Any previous answer describes a different set of places, so it goes.
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function addSearchedPlace(place: Place) {
    setPlaces((current) => [...current, place])
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function movePlace(index: number, latitude: number, longitude: number) {
    const moved = afterDragging(places, devicePosition, index, latitude, longitude)
    setPlaces(moved.places)
    setDevicePosition(moved.devicePosition)
    // Any previous answer was worked out for where the pin used to be.
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function renamePlace(index: number, name: string) {
    setPlaces((current) => current.map((place, at) => (at === index ? { ...place, name } : place)))
  }

  function removePlace(index: number) {
    setPlaces((current) => withoutPlace(current, index))
    setPlan(null)
  }

  function toggleKeepInPlace(index: number) {
    setPlaces((current) => toggleKeptInPlace(current, index))
    // A plan made while the stop was free, or kept, no longer says what was asked for.
    setPlan(null)
  }

  function clearAll() {
    setPlaces([])
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
    setLocationProblem(null)
  }

  async function planRoute() {
    const [start, ...stops] = places
    setPlanning(true)
    setError(null)
    setUnroutablePlace(undefined)

    try {
      setPlan(await optimizeRoute(start, stops, returnToStart))
    } catch (caught) {
      setPlan(null)
      if (caught instanceof ApiError) {
        setError(caught.message)
        setUnroutablePlace(caught.place)
      } else {
        setError('Could not reach the API. Is the backend running on port 8080?')
      }
    } finally {
      setPlanning(false)
    }
  }

  return (
    <main className="layout">
      <RoutePanel
        places={places}
        plan={plan}
        error={error}
        planning={planning}
        returnToStart={returnToStart}
        onRename={renamePlace}
        onRemove={removePlace}
        onToggleKeepInPlace={toggleKeepInPlace}
        onReturnToStartChange={setReturnToStart}
        onPlan={planRoute}
        onClear={clearAll}
        onPickPlace={addSearchedPlace}
        devicePosition={devicePosition}
        mapCentre={mapCentre}
        locating={locating}
        locationProblem={locationProblem}
        onUseMyLocation={() => setAsking((last) => ({ times: last.times + 1, byButton: true }))}
      />
      <RouteMap
        places={places}
        plan={plan}
        unroutablePlace={unroutablePlace}
        devicePosition={devicePosition}
        askedTimes={asking.times}
        onMapClick={addPlace}
        onMovePlace={movePlace}
        onCentreChange={setMapCentre}
      />
    </main>
  )
}

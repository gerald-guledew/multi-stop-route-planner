import { useEffect, useRef, useState } from 'react'
import { ApiError, optimizeRoute } from './api'
import { currentPosition, type LocationProblem, type Position } from './location'
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
  // Where the browser last said this device is. Read when the page opens and when "Start from
  // where I am" is pressed. It is not followed as the device moves.
  const [devicePosition, setDevicePosition] = useState<Position | null>(null)
  const askedOnLoad = useRef(false)

  // Most trips start from where you are, so offer that before anything is clicked. The
  // browser asks permission itself. Saying no is an answer, not an error, so nothing is shown.
  useEffect(() => {
    if (askedOnLoad.current) {
      return
    }
    askedOnLoad.current = true
    void startFromMyLocation(false)
  }, [])

  async function startFromMyLocation(askedByButton: boolean) {
    setLocating(true)
    setLocationProblem(null)
    try {
      const here = await currentPosition()
      // Search looks from here from now on, whether or not it also becomes the start.
      setDevicePosition(here)
      // The answer can take seconds. If a start was picked meanwhile, that choice stands.
      setPlaces((current) => (current.length === 0 ? [{ name: 'My location', ...here }] : current))
    } catch (problem) {
      // Permission can be taken back. A position from before it was is not one to go on using.
      if (problem === 'refused') {
        setDevicePosition(null)
      }
      // Pressing the button is asking for an answer, so a failure then is worth explaining.
      if (askedByButton) {
        setLocationProblem(problem as LocationProblem)
      }
    } finally {
      setLocating(false)
    }
  }

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

  function renamePlace(index: number, name: string) {
    setPlaces((current) => current.map((place, at) => (at === index ? { ...place, name } : place)))
  }

  function removePlace(index: number) {
    setPlaces((current) => current.filter((_, at) => at !== index))
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
        onReturnToStartChange={setReturnToStart}
        onPlan={planRoute}
        onClear={clearAll}
        onPickPlace={addSearchedPlace}
        devicePosition={devicePosition}
        mapCentre={mapCentre}
        locating={locating}
        locationProblem={locationProblem}
        onUseMyLocation={() => startFromMyLocation(true)}
      />
      <RouteMap
        places={places}
        plan={plan}
        unroutablePlace={unroutablePlace}
        onMapClick={addPlace}
        onCentreChange={setMapCentre}
      />
    </main>
  )
}

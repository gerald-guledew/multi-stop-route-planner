import { useState } from 'react'
import { ApiError, optimizeRoute } from './api'
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
      />
      <RouteMap
        places={places}
        plan={plan}
        unroutablePlace={unroutablePlace}
        onMapClick={addPlace}
      />
    </main>
  )
}

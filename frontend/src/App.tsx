import { useEffect, useEffectEvent, useState } from 'react'
import {
  ApiError,
  getVehicle,
  listSavedPlaces,
  NotAnswering,
  optimizeRoute,
  removeSavedPlace,
  removeVehicle,
  savePlace,
  saveVehicle,
} from './api'
import { afterABetterAnswer, afterDragging } from './foundStart'
import { afterChange, correctedThroughout, redone, startingWith, undone, type Trip } from './history'
import { asPlace, startingFrom } from './savedPlaces'
import { defaultLabel, moved, toggleKeptInPlace, withoutPlace, type Direction } from './stops'
import {
  followPosition,
  type DevicePosition,
  type LocationProblem,
  type Position,
} from './location'
import RouteMap from './components/RouteMap'
import RoutePanel from './components/RoutePanel'
import SavedPlaceList from './components/SavedPlaceList'
import UndoRedo from './components/UndoRedo'
import VehicleSection from './components/VehicleSection'
import type { Place, RoutePlan, SavedPlace, Vehicle } from './types'

export default function App() {
  // The trip, and the trip as it was before each change, so that a change can be undone.
  // Factor in road climbs is off unless ticked: the order is then chosen for distance alone.
  const [history, setHistory] = useState(() =>
    startingWith({ places: [], returnToStart: true, factorInRoadClimbs: false }),
  )
  const { places, returnToStart, factorInRoadClimbs } = history.present
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
  // The places saved earlier. They live in the API's database, so this is a copy, read when the
  // page opens and read again after every change, so that it never drifts from the real list.
  const [savedPlaces, setSavedPlaces] = useState<SavedPlace[]>([])
  const [savedProblem, setSavedProblem] = useState<string | null>(null)
  const [changingSaved, setChangingSaved] = useState(false)
  // The saved place removed last. Removing takes one press, so one press must undo it.
  const [removedSavedPlace, setRemovedSavedPlace] = useState<SavedPlace | null>(null)
  // The vehicle saved in the planner, read when the page opens. Each change answers with the
  // vehicle as saved, or none, so there is nothing to read again afterwards.
  const [vehicle, setVehicle] = useState<Vehicle | null>(null)
  const [vehicleProblem, setVehicleProblem] = useState<string | null>(null)
  const [changingVehicle, setChangingVehicle] = useState(false)
  // Counted up each time a saved place takes over as the start. Nothing is added to the trip
  // then, so the map would not otherwise know there is somewhere new to show.
  const [startReplacedTimes, setStartReplacedTimes] = useState(0)

  // The browser answers seconds later, and may answer more than once. These two see the places
  // and the position as they are when it does, not as they were when it was asked.
  const onAnswer = useEffectEvent((here: DevicePosition, isFirst: boolean) => {
    setLocating(false)

    if (isFirst) {
      // Search looks from here from now on, whether or not it also becomes the start.
      setDevicePosition(here)
      // If a start was picked while the browser was working it out, that choice stands.
      if (places.length === 0) {
        changePlaces(() => [
          { name: 'My location', latitude: here.latitude, longitude: here.longitude },
        ])
      }
      return
    }

    const better = afterABetterAnswer(places, devicePosition, here)
    setDevicePosition(better.devicePosition)
    if (better.movedTheStart) {
      // No change to undo. The trip as it was, before and after, starts from the better answer
      // wherever it started from the guess, so undoing never brings the guess back.
      setHistory((current) =>
        correctedThroughout(current, (trip) => ({
          ...trip,
          places: afterABetterAnswer(trip.places, devicePosition, here).places,
        })),
      )
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

  // Leaving the page while the list is on its way is not a failure, so it says nothing.
  useEffect(() => {
    const leaving = new AbortController()
    listSavedPlaces(leaving.signal)
      .then(setSavedPlaces)
      .catch(() => {
        // Said briefly, whatever the cause. If the planner is not answering, the first search
        // or plan says so in full, and saying it here as well puts the same words up twice.
        if (!leaving.signal.aborted) {
          setSavedProblem('Your saved places could not be read.')
        }
      })
    return () => leaving.abort()
  }, [])

  useEffect(() => {
    const leaving = new AbortController()
    getVehicle(leaving.signal)
      .then(setVehicle)
      .catch(() => {
        if (!leaving.signal.aborted) {
          setVehicleProblem('Your vehicle could not be read.')
        }
      })
    return () => leaving.abort()
  }, [])

  /**
   * Changes the trip in a way that can be undone. Changes in the same run are undone together:
   * a label typed letter by letter is undone in one press.
   */
  function change(next: (trip: Trip) => Trip, run?: string) {
    setHistory((current) => afterChange(current, next(current.present), run))
  }

  function changePlaces(next: (places: Place[]) => Place[], run?: string) {
    change((trip) => ({ ...trip, places: next(trip.places) }), run)
  }

  function undo() {
    setHistory(undone)
    forgetThePlan()
  }

  function redo() {
    setHistory(redone)
    forgetThePlan()
  }

  /** Any answer there was describes a trip that is no longer the one shown. */
  function forgetThePlan() {
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function addPlace(latitude: number, longitude: number) {
    changePlaces((current) => [
      ...current,
      { name: defaultLabel(current.length), latitude, longitude },
    ])
    // Any previous answer describes a different set of places, so it goes.
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function addSearchedPlace(place: Place) {
    changePlaces((current) => [...current, place])
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function movePlace(index: number, latitude: number, longitude: number) {
    const moved = afterDragging(places, devicePosition, index, latitude, longitude)
    changePlaces(() => moved.places)
    setDevicePosition(moved.devicePosition)
    // Any previous answer was worked out for where the pin used to be.
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function renamePlace(index: number, name: string) {
    changePlaces(
      (current) => current.map((place, at) => (at === index ? { ...place, name } : place)),
      `label ${index}`,
    )
    // A name that was taken is put right by typing another, so the complaint about it goes.
    setSavedProblem(null)
  }

  function removePlace(index: number) {
    changePlaces((current) => withoutPlace(current, index))
    setPlan(null)
  }

  function toggleKeepInPlace(index: number) {
    changePlaces((current) => toggleKeptInPlace(current, index))
    // A plan made while the stop was free, or kept, no longer says what was asked for.
    setPlan(null)
  }

  /** Makes one change to the saved places, then reads the list again. Says whether it worked. */
  async function changeSavedPlaces(change: () => Promise<unknown>): Promise<boolean> {
    setChangingSaved(true)
    try {
      await change()
      setSavedPlaces(await listSavedPlaces())
      setSavedProblem(null)
      return true
    } catch (caught) {
      // No answer at all says nothing about whether the change went through, so this does
      // not claim either way.
      setSavedProblem(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof NotAnswering
            ? `${caught.message} Then reload the page to see what is saved.`
            : 'Something went wrong. Reload the page to see what is saved.',
      )
      return false
    } finally {
      setChangingSaved(false)
    }
  }

  async function saveAPlace(index: number) {
    if (await changeSavedPlaces(() => savePlace(places[index]))) {
      // The offer to put a removed place back lasts until the list is changed again.
      setRemovedSavedPlace(null)
    }
  }

  async function removeASavedPlace(saved: SavedPlace) {
    if (await changeSavedPlaces(() => removeSavedPlace(saved.id))) {
      setRemovedSavedPlace(saved)
    }
  }

  async function putBackTheRemovedPlace() {
    if (removedSavedPlace && (await changeSavedPlaces(() => savePlace(removedSavedPlace)))) {
      setRemovedSavedPlace(null)
    }
  }

  /** Makes one change to the vehicle and keeps what the API answers. Says whether it worked. */
  async function changeVehicle(change: () => Promise<Vehicle | null>): Promise<boolean> {
    setChangingVehicle(true)
    try {
      setVehicle(await change())
      setVehicleProblem(null)
      // A plan's fuel was worked out for the vehicle there was.
      setPlan(null)
      return true
    } catch (caught) {
      setVehicleProblem(
        caught instanceof ApiError
          ? caught.message
          : caught instanceof NotAnswering
            ? `${caught.message} Then reload the page to see the vehicle saved.`
            : 'Something went wrong. Reload the page to see the vehicle saved.',
      )
      return false
    } finally {
      setChangingVehicle(false)
    }
  }

  function startFromASavedPlace(saved: SavedPlace) {
    changePlaces((current) => startingFrom(current, saved))
    setStartReplacedTimes((times) => times + 1)
    // Any previous answer was worked out from another start.
    setPlan(null)
    setError(null)
    setUnroutablePlace(undefined)
  }

  function changeTurn(index: number, direction: Direction) {
    changePlaces((current) => moved(current, index, direction))
    // A plan compares itself with the order entered, and holds kept stops to their turns.
    // Both have just changed.
    setPlan(null)
  }

  function changeReturnToStart(returns: boolean) {
    change((trip) => ({ ...trip, returnToStart: returns }))
    // A plan made for a round trip is not the plan for a one-way one, or the other way about.
    setPlan(null)
  }

  function changeFactorInRoadClimbs(factorIn: boolean) {
    change((trip) => ({ ...trip, factorInRoadClimbs: factorIn }))
    // A plan chosen for distance alone is not the plan for distance and climb together.
    setPlan(null)
  }

  function clearAll() {
    changePlaces(() => [])
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
      setPlan(await optimizeRoute(start, stops, returnToStart, factorInRoadClimbs, vehicle))
    } catch (caught) {
      setPlan(null)
      if (caught instanceof ApiError) {
        setError(caught.message)
        setUnroutablePlace(caught.place)
      } else if (caught instanceof NotAnswering) {
        setError(caught.message)
      } else {
        setError('Something went wrong while planning. Try again.')
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
        onChangeTurn={changeTurn}
        onReturnToStartChange={changeReturnToStart}
        factorInRoadClimbs={factorInRoadClimbs}
        onFactorInRoadClimbsChange={changeFactorInRoadClimbs}
        onPlan={planRoute}
        onClear={clearAll}
        onPickPlace={addSearchedPlace}
        devicePosition={devicePosition}
        mapCentre={mapCentre}
        locating={locating}
        locationProblem={locationProblem}
        onUseMyLocation={() => setAsking((last) => ({ times: last.times + 1, byButton: true }))}
        savedPlaces={savedPlaces}
        changingSaved={changingSaved}
        onSavePlace={saveAPlace}
        savedList={
          <SavedPlaceList
            saved={savedPlaces}
            places={places}
            removed={removedSavedPlace}
            problem={savedProblem}
            busy={changingSaved}
            onAdd={(saved) => addSearchedPlace(asPlace(saved))}
            onStartFrom={startFromASavedPlace}
            onRemove={removeASavedPlace}
            onPutBack={putBackTheRemovedPlace}
          />
        }
        vehicleSection={
          <VehicleSection
            vehicle={vehicle}
            problem={vehicleProblem}
            busy={changingVehicle}
            onSave={(given) => changeVehicle(() => saveVehicle(given))}
            onRemove={() =>
              changeVehicle(async () => {
                await removeVehicle()
                return null
              })
            }
          />
        }
        vehicleFigureFrom={vehicle?.figureFrom ?? null}
      />
      <RouteMap
        places={places}
        plan={plan}
        unroutablePlace={unroutablePlace}
        devicePosition={devicePosition}
        askedTimes={asking.times}
        startReplacedTimes={startReplacedTimes}
        onMapClick={addPlace}
        onMovePlace={movePlace}
        onCentreChange={setMapCentre}
      />
      <UndoRedo
        // A plan on its way was asked for with the trip as it stands.
        canUndo={history.past.length > 0 && !planning}
        canRedo={history.future.length > 0 && !planning}
        onUndo={undo}
        onRedo={redo}
      />
    </main>
  )
}

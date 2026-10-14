import { useEffect, useEffectEvent, useState } from 'react'
import { listMakes, listModels, listVariants, listYears } from '../api'
import { variantKey, variantSaid, variantToPick } from '../vehicle'
import type { Car, CarVariant } from '../types'

interface Props {
  /** The car saved already, shown picked when it is being changed. Null for a first one. */
  saved: Car | null
  /** Told of the engine picked, or of null while the car is not picked down to its engine. */
  onPick: (found: CarVariant | null) => void
}

/**
 * Finds a car in NZTA's vehicle register in four steps: the make, the model, the year, then
 * the engine. Each list is asked for once the step before it is picked, and picking again
 * further up empties the lists below.
 */
export default function CarFinder({ saved, onPick }: Props) {
  // A list is null while it is on its way.
  const [makes, setMakes] = useState<string[] | null>(null)
  const [make, setMake] = useState(saved?.make ?? '')
  const [models, setModels] = useState<string[] | null>(null)
  const [model, setModel] = useState(saved?.model ?? '')
  const [years, setYears] = useState<number[] | null>(null)
  const [year, setYear] = useState<number | null>(saved?.year ?? null)
  const [variants, setVariants] = useState<CarVariant[] | null>(null)
  const [picked, setPicked] = useState('')
  const [problem, setProblem] = useState<string | null>(null)

  // Said the same way whichever list it was. Leaving the form is no failure, so it says nothing.
  function sayWhenNotRead(leaving: AbortController) {
    return () => {
      if (!leaving.signal.aborted) {
        setProblem('The list of cars could not be read. Enter your vehicle yourself instead.')
      }
    }
  }

  useEffect(() => {
    const leaving = new AbortController()
    listMakes(leaving.signal).then(setMakes).catch(sayWhenNotRead(leaving))
    return () => leaving.abort()
  }, [])

  useEffect(() => {
    if (make === '') {
      return
    }
    const leaving = new AbortController()
    listModels(make, leaving.signal).then(setModels).catch(sayWhenNotRead(leaving))
    return () => leaving.abort()
  }, [make])

  useEffect(() => {
    if (make === '' || model === '') {
      return
    }
    const leaving = new AbortController()
    listYears(make, model, leaving.signal).then(setYears).catch(sayWhenNotRead(leaving))
    return () => leaving.abort()
  }, [make, model])

  // The engines arrive after the year is picked, and one of them may be picked for the driver.
  const onEnginesArrived = useEffectEvent((arrived: CarVariant[]) => {
    setVariants(arrived)
    const pick = variantToPick(arrived, saved)
    setPicked(pick ? variantKey(pick) : '')
    onPick(pick)
  })

  useEffect(() => {
    if (make === '' || model === '' || year === null) {
      return
    }
    const leaving = new AbortController()
    listVariants(make, model, year, leaving.signal).then(onEnginesArrived).catch(sayWhenNotRead(leaving))
    return () => leaving.abort()
  }, [make, model, year])

  function pickMake(chosen: string) {
    setMake(chosen)
    setModels(null)
    setModel('')
    setYears(null)
    setYear(null)
    forgetTheEngine()
  }

  function pickModel(chosen: string) {
    setModel(chosen)
    setYears(null)
    setYear(null)
    forgetTheEngine()
  }

  function pickYear(chosen: number) {
    setYear(chosen)
    forgetTheEngine()
  }

  /** Another make, model or year has other engines, so none of them is picked yet. */
  function forgetTheEngine() {
    setVariants(null)
    setPicked('')
    onPick(null)
  }

  function pickEngine(key: string) {
    setPicked(key)
    onPick(variants?.find((found) => variantKey(found) === key) ?? null)
  }

  if (makes !== null && makes.length === 0) {
    return <p className="note">No cars are loaded in this planner yet. Enter your vehicle yourself.</p>
  }

  return (
    <>
      <label>
        Make
        <select value={make} disabled={makes === null} onChange={(event) => pickMake(event.target.value)}>
          <option value="" disabled>
            {makes === null ? 'Loading…' : 'Pick the make'}
          </option>
          {makes?.map((choice) => (
            <option key={choice}>{choice}</option>
          ))}
        </select>
      </label>
      <label>
        Model
        <select value={model} disabled={models === null} onChange={(event) => pickModel(event.target.value)}>
          <option value="" disabled>
            Pick the model
          </option>
          {models?.map((choice) => (
            <option key={choice}>{choice}</option>
          ))}
        </select>
      </label>
      <label>
        Year
        <select
          value={year ?? ''}
          disabled={years === null}
          onChange={(event) => pickYear(Number(event.target.value))}
        >
          <option value="" disabled>
            Pick the year
          </option>
          {years?.map((choice) => (
            <option key={choice}>{choice}</option>
          ))}
        </select>
      </label>
      <label>
        Engine
        <select value={picked} disabled={variants === null} onChange={(event) => pickEngine(event.target.value)}>
          <option value="" disabled>
            Pick the engine
          </option>
          {variants?.map((found) => (
            <option key={variantKey(found)} value={variantKey(found)}>
              {variantSaid(found)}
            </option>
          ))}
        </select>
      </label>
      {problem && <p className="warning">{problem}</p>}
    </>
  )
}

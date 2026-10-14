import { useState, type FormEvent } from 'react'
import { SIZES, vehicleSaid, vehicleToSave, type Way } from '../vehicle'
import CarFinder from './CarFinder'
import type { CarVariant, Vehicle, VehicleSize, VehicleToSave } from '../types'

interface Props {
  /** The vehicle saved in the planner. Null until one is saved. */
  vehicle: Vehicle | null
  /** What went wrong the last time the vehicle was read or changed, in words. */
  problem: string | null
  /** True while a change is on its way to the API, so nothing is pressed twice. */
  busy: boolean
  /** Says whether the vehicle was saved, so the form closes only when it was. */
  onSave: (vehicle: VehicleToSave) => Promise<boolean>
  onRemove: () => void
}

/**
 * The vehicle the planner works out fuel for. It is saved in the planner, so it is given once
 * and not for every trip. One line while it stands, a short form while it is given.
 *
 * A driver finds the car in the register by make, model, year and engine, or enters its size
 * and the fuel use its dashboard shows. The dashboard's figure is the better one, so it can be
 * typed for a car found in the register too, and it is used instead of the register's.
 */
export default function VehicleSection(props: Props) {
  const { vehicle, problem, busy } = props
  const [entering, setEntering] = useState(false)
  const [way, setWay] = useState<Way>('find')
  const [found, setFound] = useState<CarVariant | null>(null)
  const [size, setSize] = useState<VehicleSize>('medium')
  const [typed, setTyped] = useState('')
  const [typingProblem, setTypingProblem] = useState<string | null>(null)

  function startEntering() {
    // Opened the way the saved vehicle was given, so changing it starts from what it is.
    setWay(vehicle !== null && vehicle.car === null ? 'enter' : 'find')
    setFound(null)
    setSize(vehicle?.size ?? 'medium')
    setTyped(vehicle?.figureFrom === 'dashboard' ? String(vehicle.litresPer100Km) : '')
    setTypingProblem(null)
    setEntering(true)
  }

  function chooseWay(chosen: Way) {
    setWay(chosen)
    setTypingProblem(null)
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    const toSave = vehicleToSave(way, { size, found, typed })
    if ('problem' in toSave) {
      setTypingProblem(toSave.problem)
      return
    }
    if (await props.onSave(toSave)) {
      setEntering(false)
    }
  }

  if (!entering) {
    return (
      <section className="vehicle">
        {vehicle ? (
          <>
            <h2>Your vehicle</h2>
            <p>
              {vehicleSaid(vehicle)}{' '}
              <button className="link" disabled={busy} onClick={startEntering}>
                Change
              </button>{' '}
              <button className="link" disabled={busy} onClick={props.onRemove}>
                Remove
              </button>
            </p>
          </>
        ) : (
          <button className="link" disabled={busy} onClick={startEntering}>
            Add your vehicle
          </button>
        )}
        {problem && <p className="warning">{problem}</p>}
      </section>
    )
  }

  return (
    <section className="vehicle">
      <h2>Your vehicle</h2>
      <form onSubmit={save}>
        <div className="ways" role="radiogroup" aria-label="How to give your vehicle">
          <label>
            <input type="radio" name="way" value="find" checked={way === 'find'} onChange={() => chooseWay('find')} />
            Find your car
          </label>
          <label>
            <input
              type="radio"
              name="way"
              value="enter"
              checked={way === 'enter'}
              onChange={() => chooseWay('enter')}
            />
            Enter it yourself
          </label>
        </div>
        {/* Both ways stay in the form while it is open, so going back to one finds it as it was left. */}
        <div hidden={way !== 'find'}>
          <CarFinder saved={vehicle?.car ?? null} onPick={setFound} />
          <p className="note">
            Lab figures from NZTA's vehicle register. Real driving uses more, so type what your
            dashboard shows if you know it.
          </p>
        </div>
        <div hidden={way !== 'enter'}>
          <label>
            Size
            <select value={size} onChange={(event) => setSize(event.target.value as VehicleSize)}>
              {SIZES.map((choice) => (
                <option key={choice.size} value={choice.size}>
                  {choice.says}
                </option>
              ))}
            </select>
          </label>
        </div>
        <label>
          {way === 'find' ? 'Dashboard figure, L/100 km, if you know it' : 'Fuel use, L/100 km'}
          <input
            inputMode="decimal"
            // An example figure in a box that may be left empty would look filled in.
            placeholder={way === 'enter' ? '7.5' : undefined}
            value={typed}
            onChange={(event) => {
              setTyped(event.target.value)
              setTypingProblem(null)
            }}
          />
        </label>
        {way === 'enter' && <p className="note">As your dashboard shows it. Petrol cars only, for now.</p>}
        {typingProblem && <p className="warning">{typingProblem}</p>}
        {problem && <p className="warning">{problem}</p>}
        <div className="buttons">
          <button className="primary" type="submit" disabled={busy}>
            {busy ? 'Saving…' : 'Save'}
          </button>
          <button type="button" onClick={() => setEntering(false)}>
            Cancel
          </button>
        </div>
      </form>
    </section>
  )
}

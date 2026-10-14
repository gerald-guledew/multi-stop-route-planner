import { useState, type FormEvent } from 'react'
import { litresFrom, SIZES, vehicleSaid } from '../vehicle'
import type { Vehicle, VehicleSize } from '../types'

interface Props {
  /** The vehicle saved in the planner. Null until one is saved. */
  vehicle: Vehicle | null
  /** What went wrong the last time the vehicle was read or changed, in words. */
  problem: string | null
  /** True while a change is on its way to the API, so nothing is pressed twice. */
  busy: boolean
  /** Says whether the vehicle was saved, so the form closes only when it was. */
  onSave: (vehicle: Vehicle) => Promise<boolean>
  onRemove: () => void
}

/**
 * The vehicle the planner works out fuel for. It is saved in the planner, so it is entered
 * once and not for every trip. One line while it stands, a short form while it is entered.
 */
export default function VehicleSection(props: Props) {
  const { vehicle, problem, busy } = props
  const [entering, setEntering] = useState(false)
  const [size, setSize] = useState<VehicleSize>('medium')
  const [typed, setTyped] = useState('')
  const [typingProblem, setTypingProblem] = useState<string | null>(null)

  function startEntering() {
    setSize(vehicle?.size ?? 'medium')
    setTyped(vehicle ? String(vehicle.litresPer100Km) : '')
    setTypingProblem(null)
    setEntering(true)
  }

  async function save(event: FormEvent) {
    event.preventDefault()
    const read = litresFrom(typed)
    if ('problem' in read) {
      setTypingProblem(read.problem)
      return
    }
    if (await props.onSave({ litresPer100Km: read.litres, size })) {
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
        <label>
          Fuel use, L/100 km
          <input
            inputMode="decimal"
            placeholder="7.5"
            value={typed}
            onChange={(event) => {
              setTyped(event.target.value)
              setTypingProblem(null)
            }}
          />
        </label>
        <p className="note">As your dashboard shows it. Petrol cars only, for now.</p>
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

import { describe, expect, it } from 'vitest'
import {
  afterChange,
  CHANGES_KEPT,
  correctedThroughout,
  redone,
  startingWith,
  undone,
  type History,
  type Trip,
} from './history'
import type { Place } from './types'

const mall: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }
const office: Place = { name: 'Office', latitude: -36.8485, longitude: 174.7621 }

const empty: Trip = { places: [], returnToStart: true, order: 'leastFuel' }

function going(...places: Place[]): Trip {
  return { ...empty, places }
}

/** The trip with one place given another label. */
function labelled(trip: Trip, index: number, name: string): Trip {
  return { ...trip, places: trip.places.map((place, at) => (at === index ? { ...place, name } : place)) }
}

function typed(history: History, index: number, ...letterByLetter: string[]): History {
  return letterByLetter.reduce(
    (sofar, name) => afterChange(sofar, labelled(sofar.present, index, name), `label ${index}`),
    history,
  )
}

describe('undoing and redoing changes to the trip', () => {
  it('undoes a change, and redoes it', () => {
    const added = afterChange(startingWith(empty), going(mall))

    expect(undone(added).present).toBe(empty)
    expect(redone(undone(added)).present).toEqual(going(mall))
  })

  it('undoes changes one at a time, the last first', () => {
    const first = going(mall)
    const second = going(mall, office)
    const history = afterChange(afterChange(startingWith(empty), first), second)

    expect(undone(history).present).toBe(first)
    expect(undone(undone(history)).present).toBe(empty)
  })

  it('has nothing to undo before the first change, and nothing to redo before an undo', () => {
    const history = startingWith(empty)
    const changed = afterChange(history, going(mall))

    expect(undone(history)).toBe(history)
    expect(redone(changed)).toBe(changed)
  })

  it('cannot redo once something else is changed after an undo', () => {
    const undoneOnce = undone(afterChange(startingWith(empty), going(mall)))
    const changedInstead = afterChange(undoneOnce, going(office))

    expect(changedInstead.future).toEqual([])
    expect(redone(changedInstead)).toBe(changedInstead)
  })

  it('undoes a box unticked like any other change', () => {
    const unticked = afterChange(startingWith(empty), { ...empty, returnToStart: false })

    expect(undone(unticked).present.returnToStart).toBe(true)
  })

  it('undoes picking the fastest order like any other change', () => {
    const picked = afterChange(startingWith(empty), { ...empty, order: 'fastest' })

    expect(undone(picked).present.order).toBe('leastFuel')
    expect(redone(undone(picked)).present.order).toBe('fastest')
  })

  it('undoes picking the least fuel back to the order picked before', () => {
    const fastest = afterChange(startingWith(empty), { ...empty, order: 'fastest' })
    const leastFuel = afterChange(fastest, { ...empty, order: 'leastFuel' })

    expect(undone(leastFuel).present.order).toBe('fastest')
  })

  it('takes a change that changes nothing as no change', () => {
    const history = afterChange(startingWith(empty), going(mall))

    expect(afterChange(history, { ...history.present })).toBe(history)
  })

  it('keeps the last 100 changes, and lets the older ones go', () => {
    let history = startingWith(empty)
    for (let change = 1; change <= CHANGES_KEPT + 5; change++) {
      history = afterChange(history, going({ ...mall, name: `Change ${change}` }))
    }

    expect(history.past).toHaveLength(CHANGES_KEPT)
    let oldest = history
    while (oldest.past.length > 0) {
      oldest = undone(oldest)
    }
    expect(oldest.present.places[0].name).toBe('Change 5')
  })
})

describe('typing a label', () => {
  const twoPlaces = afterChange(startingWith(empty), going(mall, office))

  it('is one change to undo, however many letters it took', () => {
    const history = typed(twoPlaces, 0, 'M', 'Mu', 'Mum')

    expect(history.present.places[0].name).toBe('Mum')
    expect(undone(history).present.places[0].name).toBe('Sylvia Park')
  })

  it('is a change of its own for each place labelled', () => {
    const first = typed(twoPlaces, 0, 'S', 'Shops')
    const both = typed(first, 1, 'W', 'Work')

    expect(undone(both).present).toBe(first.present)
  })

  it('is a change of its own again after anything else is changed', () => {
    const shops = typed(twoPlaces, 0, 'Shops')
    const unticked = afterChange(shops, { ...shops.present, returnToStart: false })
    const labelledAgain = typed(unticked, 0, 'Shops2')

    expect(undone(labelledAgain).present).toBe(unticked.present)
  })

  it('is a change of its own again after an undo', () => {
    const undoneOnce = undone(typed(twoPlaces, 0, 'Shops'))
    const labelledAgain = typed(undoneOnce, 0, 'Market')

    expect(undone(labelledAgain).present).toBe(undoneOnce.present)
  })
})

describe('a correction made all through the history', () => {
  const guess: Place = { name: 'My location', latitude: -36.85, longitude: 174.76 }
  const better: Place = { ...guess, latitude: -36.8501 }
  const putRight = (trip: Trip): Trip => ({
    ...trip,
    places: trip.places.map((place) => (place === guess ? better : place)),
  })

  it('reaches the trip as it is and as it was, so undoing does not bring the guess back', () => {
    const history = afterChange(afterChange(startingWith(empty), going(guess)), going(guess, mall))
    const corrected = correctedThroughout(history, putRight)

    expect(corrected.present.places[0]).toBe(better)
    expect(undone(corrected).present.places[0]).toBe(better)
  })

  it('reaches what can be redone', () => {
    const undoneOnce = undone(afterChange(afterChange(startingWith(empty), going(guess)), going(guess, mall)))
    const corrected = correctedThroughout(undoneOnce, putRight)

    expect(redone(corrected).present.places[0]).toBe(better)
  })

  it('is no change to undo', () => {
    const history = afterChange(startingWith(empty), going(guess))

    expect(correctedThroughout(history, putRight).past).toHaveLength(history.past.length)
  })
})

import { describe, expect, it } from 'vitest'
import {
  anyKeptInPlace,
  anyStopOver,
  canMove,
  defaultLabel,
  isFull,
  mostStops,
  moved,
  ordinal,
  stopOverSaid,
  toggleKeptInPlace,
  tooManyToReturn,
  turnForANewStop,
  withLabelsInStep,
  withoutPlace,
  withoutTheWayBack,
  withStopAt,
  withStopOverLetGo,
} from './stops'
import type { Place } from './types'

const home: Place = { name: 'Home', latitude: -36.9146, longitude: 174.8392 }
const office: Place = { name: 'Office', latitude: -36.8485, longitude: 174.7621 }
const mall: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }
const dairy: Place = { name: 'Corner dairy', latitude: -36.9, longitude: 174.8 }

describe('saying which turn a stop has', () => {
  it.each([
    [1, '1st'],
    [2, '2nd'],
    [3, '3rd'],
    [4, '4th'],
    [10, '10th'],
    // The teens do not follow their last digit.
    [11, '11th'],
    [12, '12th'],
    [13, '13th'],
    [21, '21st'],
    [22, '22nd'],
    [111, '111th'],
  ])('%d is "%s"', (turn, said) => {
    expect(ordinal(turn)).toBe(said)
  })
})

describe('keeping a stop in its place', () => {
  it('marks the stop and leaves the others as they were', () => {
    expect(toggleKeptInPlace([home, office, mall], 1)).toEqual([
      home,
      { ...office, keepInPlace: true },
      mall,
    ])
  })

  it('lets a kept stop go again', () => {
    const kept = toggleKeptInPlace([home, office, mall], 1)

    expect(toggleKeptInPlace(kept, 1)[1].keepInPlace).toBe(false)
  })

  it('will not mark the start, which is first anyway', () => {
    const places = [home, office]

    expect(toggleKeptInPlace(places, 0)).toBe(places)
  })

  it('does not change the list it was given', () => {
    const places = [home, office]
    toggleKeptInPlace(places, 1)

    expect(places).toEqual([home, office])
  })
})

describe('knowing whether anything is kept', () => {
  it('is false for a list nobody has marked', () => {
    expect(anyKeptInPlace([home, office, mall])).toBe(false)
  })

  it('is true once a stop is kept', () => {
    expect(anyKeptInPlace([home, { ...office, keepInPlace: true }, mall])).toBe(true)
  })

  it('is false again when the stop is let go', () => {
    expect(anyKeptInPlace([home, { ...office, keepInPlace: false }])).toBe(false)
  })
})

describe('taking a place out of the list', () => {
  it('removes it and keeps the rest in order', () => {
    expect(withoutPlace([home, office, mall], 1)).toEqual([home, mall])
  })

  it('leaves a kept stop kept when a different stop goes', () => {
    const keptMall = { ...mall, keepInPlace: true }

    expect(withoutPlace([home, office, keptMall], 1)).toEqual([home, keptMall])
  })

  it('stops keeping a stop that has just become the start', () => {
    const keptOffice = { ...office, keepInPlace: true }

    expect(withoutPlace([home, keptOffice, mall], 0)).toEqual([office, mall])
  })

  it('copes with the last place going', () => {
    expect(withoutPlace([home], 0)).toEqual([])
  })
})

describe('moving a stop to another turn', () => {
  it('moves it one turn earlier', () => {
    expect(moved([home, office, mall, dairy], 3, 'up')).toEqual([home, office, dairy, mall])
  })

  it('moves it one turn later', () => {
    expect(moved([home, office, mall, dairy], 1, 'down')).toEqual([home, mall, office, dairy])
  })

  it('will not move the first stop up past the start', () => {
    const places = [home, office, mall]

    expect(canMove(places, 1, 'up')).toBe(false)
    expect(moved(places, 1, 'up')).toBe(places)
  })

  it('will not move the last stop down, there being no turn after it', () => {
    const places = [home, office, mall]

    expect(canMove(places, 2, 'down')).toBe(false)
    expect(moved(places, 2, 'down')).toBe(places)
  })

  it('will not move the start, which is no stop', () => {
    const places = [home, office, mall]

    expect(canMove(places, 0, 'down')).toBe(false)
    expect(moved(places, 0, 'down')).toBe(places)
  })

  it('has nowhere to move an only stop', () => {
    expect(canMove([home, office], 1, 'up')).toBe(false)
    expect(canMove([home, office], 1, 'down')).toBe(false)
  })

  it('says a stop in the middle can go either way', () => {
    expect(canMove([home, office, mall, dairy], 2, 'up')).toBe(true)
    expect(canMove([home, office, mall, dairy], 2, 'down')).toBe(true)
  })

  it('knows nothing of a turn that is not in the list', () => {
    const places = [home, office, mall]

    expect(canMove(places, 7, 'up')).toBe(false)
    expect(moved(places, 7, 'up')).toBe(places)
  })

  it('knows nothing of the turn just past the end, though the one above it is real', () => {
    const places = [home, office, mall]

    expect(canMove(places, 3, 'up')).toBe(false)
    expect(moved(places, 3, 'up')).toBe(places)
  })

  it('leaves a kept stop kept, at the turn it moves to', () => {
    const keptMall = { ...mall, keepInPlace: true }

    expect(moved([home, office, keptMall], 2, 'up')).toEqual([home, keptMall, office])
  })

  it('leaves a kept stop kept when another stop changes places with it', () => {
    const keptOffice = { ...office, keepInPlace: true }

    expect(moved([home, keptOffice, mall], 2, 'up')).toEqual([home, mall, keptOffice])
  })

  it('does not change the list it was given', () => {
    const places = [home, office, mall]

    moved(places, 1, 'down')

    expect(places).toEqual([home, office, mall])
  })
})

describe('a label nobody has changed', () => {
  // Three pins dropped on the map and never named.
  const first: Place = { name: 'Start', latitude: -36.85, longitude: 174.76 }
  const second: Place = { name: 'Stop 1', latitude: -36.86, longitude: 174.77 }
  const third: Place = { name: 'Stop 2', latitude: -36.87, longitude: 174.78 }
  const fourth: Place = { name: 'Stop 3', latitude: -36.88, longitude: 174.79 }

  const labels = (places: Place[]) => places.map((place) => place.name)
  const latitudes = (places: Place[]) => places.map((place) => place.latitude)

  it('is "Start" for the start and "Stop" with its turn for a stop', () => {
    expect([0, 1, 2, 10].map(defaultLabel)).toEqual(['Start', 'Stop 1', 'Stop 2', 'Stop 10'])
  })

  it('goes on saying the turn when a stop is moved up', () => {
    const rearranged = moved([first, second, third, fourth], 3, 'up')

    expect(latitudes(rearranged)).toEqual([-36.85, -36.86, -36.88, -36.87])
    expect(labels(rearranged)).toEqual(['Start', 'Stop 1', 'Stop 2', 'Stop 3'])
  })

  it('goes on saying the turn when a stop is taken out before it', () => {
    const left = withoutPlace([first, second, third, fourth], 1)

    expect(latitudes(left)).toEqual([-36.85, -36.87, -36.88])
    expect(labels(left)).toEqual(['Start', 'Stop 1', 'Stop 2'])
  })

  it('becomes "Start" when the start is taken out and a stop takes over', () => {
    expect(labels(withoutPlace([first, second, third], 0))).toEqual(['Start', 'Stop 1'])
  })

  it('still stops being kept on becoming the start', () => {
    const keptSecond = { ...second, keepInPlace: true }

    expect(withoutPlace([first, keptSecond, third], 0)[0]).toEqual({
      name: 'Start',
      latitude: -36.86,
      longitude: 174.77,
    })
  })

  it('stays kept through a change of label', () => {
    const keptFourth = { ...fourth, keepInPlace: true }

    expect(moved([first, second, third, keptFourth], 3, 'up')[2]).toEqual({
      name: 'Stop 2',
      latitude: -36.88,
      longitude: 174.79,
      keepInPlace: true,
    })
  })

  it('is left alone once somebody has typed a name over it', () => {
    const named = { ...fourth, name: 'The office' }

    expect(labels(moved([first, second, third, named], 3, 'up'))).toEqual([
      'Start',
      'Stop 1',
      'The office',
      'Stop 3',
    ])
  })

  it('is not taken for untouched because it reads like the label of another turn', () => {
    // Called "Stop 7" while third in the list: somebody typed that, or it would say "Stop 3".
    const typed = { ...fourth, name: 'Stop 7' }

    expect(labels(moved([first, second, third, typed], 3, 'up'))).toEqual([
      'Start',
      'Stop 1',
      'Stop 7',
      'Stop 3',
    ])
  })

  it('is not changed for a place whose turn is the same as before', () => {
    const rearranged = moved([first, second, third, fourth], 3, 'up')

    expect(rearranged[0]).toBe(first)
    expect(rearranged[1]).toBe(second)
  })

  it('leaves a place that was not in the list before as it is', () => {
    const newcomer: Place = { name: 'Stop 7', latitude: -36.9, longitude: 174.8 }

    expect(withLabelsInStep([first, second], [first, newcomer, second])).toEqual([
      first,
      newcomer,
      { ...second, name: 'Stop 2' },
    ])
  })
})

describe('the most stops a trip takes', () => {
  /** A start and this many stops. */
  function trip(stops: number): Place[] {
    return [home, ...Array.from({ length: stops }, () => office)]
  }

  it('is ten one way and nine on a round trip', () => {
    expect(mostStops(false)).toBe(10)
    expect(mostStops(true)).toBe(9)
  })

  it('fills a one-way trip at ten stops, the start not counted', () => {
    expect(isFull(trip(9), false)).toBe(false)
    expect(isFull(trip(10), false)).toBe(true)
  })

  it('fills a round trip at nine', () => {
    expect(isFull(trip(8), true)).toBe(false)
    expect(isFull(trip(9), true)).toBe(true)
  })

  it('never fills a trip with no stops yet', () => {
    expect(isFull([], true)).toBe(false)
    expect(isFull([home], true)).toBe(false)
  })

  it('holds a one-way trip of ten stops back from returning to its start', () => {
    expect(tooManyToReturn(trip(10), false)).toBe(true)
    expect(tooManyToReturn(trip(9), false)).toBe(false)
  })

  it('never holds a round trip back from going one way again', () => {
    expect(tooManyToReturn(trip(9), true)).toBe(false)
    expect(tooManyToReturn(trip(10), true)).toBe(false)
  })
})

const shop: Place = { name: 'Shop', latitude: -36.9, longitude: 174.8 }
const work: Place = { name: 'Office', latitude: -36.85, longitude: 174.76 }
const onTheWay: Place = { name: 'Fuel', latitude: -36.88, longitude: 174.78, stopOver: true }
const alsoOnTheWay: Place = { name: 'Bakery', latitude: -36.87, longitude: 174.77, stopOver: true }
const free = ({ stopOver: _, ...place }: Place): Place => place

describe('a new stop', () => {
  it('goes at the end of the list', () => {
    expect(turnForANewStop([mall, shop, onTheWay, work], false)).toBe(4)
    expect(turnForANewStop([mall, shop, onTheWay, work], true)).toBe(4)
  })

  it('is the start when there is nothing else', () => {
    expect(turnForANewStop([], false)).toBe(0)
    expect(turnForANewStop([], true)).toBe(0)
  })

  it('goes before a stop-over on the way back to the start and the stop it comes after', () => {
    expect(turnForANewStop([mall, shop, work, onTheWay], true)).toBe(2)
    expect(turnForANewStop([mall, shop, work, onTheWay, alsoOnTheWay], true)).toBe(2)
  })

  it('goes after a stop-over that ends a one-way trip, which is on no way back', () => {
    expect(turnForANewStop([mall, shop, onTheWay], false)).toBe(3)
  })

  it('moves a label nobody changed along with the turn it takes', () => {
    const stopOne = { ...shop, name: 'Stop 1' }

    expect(withStopAt([mall, stopOne], 1, work).map((place) => place.name)).toEqual([
      'Sylvia Park',
      'Office',
      'Stop 2',
    ])
  })
})

describe('letting go of a stop-over', () => {
  const trip = [mall, shop, onTheWay, work]

  it('makes it a stop like any other', () => {
    expect(withStopOverLetGo(trip, 2)).toEqual([mall, shop, free(onTheWay), work])
  })

  it('happens to one that ends a trip that no longer drives back', () => {
    expect(withoutTheWayBack([mall, shop, work, onTheWay])).toEqual([mall, shop, work, free(onTheWay)])
  })

  it('leaves the others when the trip no longer drives back', () => {
    expect(withoutTheWayBack(trip)).toEqual(trip)
  })
})

describe('a place leaving the list', () => {
  const trip = [mall, shop, onTheWay, work]

  it('takes the leg of the stop-over after it', () => {
    expect(withoutPlace(trip, 1)).toEqual([mall, free(onTheWay), work])
  })

  it('takes the leg of the stop-over before it', () => {
    expect(withoutPlace(trip, 3)).toEqual([mall, shop, free(onTheWay)])
  })

  it('takes the leg of every stop-over on that leg', () => {
    expect(withoutPlace([mall, shop, onTheWay, alsoOnTheWay, work], 1)).toEqual([
      mall,
      free(onTheWay),
      free(alsoOnTheWay),
      work,
    ])
  })

  it('leaves the leg of a stop-over on another leg', () => {
    const another = [mall, shop, work, onTheWay, { ...shop, name: 'Last' }]

    expect(withoutPlace(another, 1)[2]).toEqual(onTheWay)
  })

  it('leaves the other stop-over on the leg when it is a stop-over itself', () => {
    expect(withoutPlace([mall, shop, onTheWay, alsoOnTheWay, work], 2)).toEqual([
      mall,
      shop,
      alsoOnTheWay,
      work,
    ])
  })

  it('takes the legs at both ends of a round trip when it is the start', () => {
    const left = withoutPlace([mall, onTheWay, shop, work, alsoOnTheWay], 0)

    expect(left).toEqual([free(onTheWay), shop, work, free(alsoOnTheWay)])
  })
})

describe('what a stop-over says', () => {
  it('says the two places either side of it', () => {
    expect(stopOverSaid([mall, shop, onTheWay, work], 2, false)).toBe('between Shop and Office')
  })

  it('counts the start as the place after it on the way back', () => {
    expect(stopOverSaid([mall, shop, onTheWay], 2, true)).toBe('between Shop and Sylvia Park')
  })

  it('says only the place before it when nothing follows', () => {
    expect(stopOverSaid([mall, shop, onTheWay], 2, false)).toBe('after Shop')
  })

  it('is one of them or there are none', () => {
    expect(anyStopOver([mall, shop])).toBe(false)
    expect(anyStopOver([mall, onTheWay])).toBe(true)
  })
})

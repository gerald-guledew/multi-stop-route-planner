import { describe, expect, it } from 'vitest'
import {
  addedSaid,
  betweenSaid,
  canAddOnTheWay,
  hasOtherLegs,
  heldInPlannedOrder,
  inPlannedOrder,
  legAfter,
  namedAs,
  pinLabel,
  searchAlongOpen,
  searchAlongOpenedOn,
  withStopOn,
} from './onTheWay'
import type { Leg, Place, PlaceAlong, RoutePlan } from './types'

const start: Place = { name: 'Sylvia Park', latitude: -36.917, longitude: 174.8414 }
const museum: Place = { name: 'Museum', latitude: -36.8604, longitude: 174.7778 }
const tower: Place = { name: 'Sky Tower', latitude: -36.8485, longitude: 174.7621 }
const airport: Place = { name: 'Airport', latitude: -37.007, longitude: 174.783 }
const fuel: Place = { name: 'Z Ellerslie', latitude: -36.899, longitude: 174.813 }

/** The plan of a trip, driving to its places in the order given. */
function planOf(...route: Place[]): RoutePlan {
  return {
    route,
    legs: [],
    totalDistanceKm: 40,
    totalDurationSeconds: 2700,
    totalClimbMetres: null,
    totalDescentMetres: null,
    totalFuelLitres: null,
    fuelCountsHills: null,
    fuelCountsSpeed: null,
    enteredOrderDistanceKm: 44,
    shortestOrder: null,
    fastestOrder: null,
    leastFuelOrder: null,
    ordersChecked: 6,
  }
}

function found(more: Partial<PlaceAlong> = {}): PlaceAlong {
  return { ...fuel, kind: 'poi', detail: 'Ellerslie', leg: 0, kmAdded: 1.2, secondsAdded: 240, ...more }
}

describe('the list taking the order of the plan', () => {
  it('reads as the trip is driven', () => {
    const planned = planOf(start, museum, tower, airport)

    expect(inPlannedOrder([start, tower, museum, airport], planned, false)).toEqual([
      start,
      museum,
      tower,
      airport,
    ])
  })

  it('leaves a kept stop kept', () => {
    const kept = { ...airport, keepInPlace: true }
    const planned = planOf(start, museum, tower, kept)

    expect(inPlannedOrder([start, tower, museum, kept], planned, false)?.[3]).toBe(kept)
  })

  it('does not count the way back to the start as one more place', () => {
    const planned = planOf(start, museum, tower, start)

    expect(inPlannedOrder([start, tower, museum], planned, true)).toEqual([start, museum, tower])
  })

  it('moves a label nobody changed along with its turn', () => {
    const first = { ...tower, name: 'Stop 1' }
    const second = { ...museum, name: 'Stop 2' }
    const planned = planOf(start, second, first)

    expect(inPlannedOrder([start, first, second], planned, false)).toEqual([
      start,
      { ...second, name: 'Stop 1' },
      { ...first, name: 'Stop 2' },
    ])
  })

  it('gives back the very same list when the plan drives it as it stands', () => {
    const trip = [start, museum, tower]

    expect(inPlannedOrder(trip, planOf(start, museum, tower), false)).toBe(trip)
  })

  it('takes nothing from a plan for other places', () => {
    expect(inPlannedOrder([start, tower], planOf(start, museum), false)).toBeNull()
    expect(inPlannedOrder([start, tower, museum], planOf(start, tower), false)).toBeNull()
  })

  it('takes nothing from a plan that goes to one spot twice', () => {
    expect(inPlannedOrder([start, tower, museum], planOf(start, tower, tower), false)).toBeNull()
  })
})

describe('the plan under the names the trip shows', () => {
  const legOf = (from: string, to: string) => ({ from, to }) as Leg

  it('renames the places and the legs between them', () => {
    const planned = {
      ...planOf(start, { ...museum, name: 'Stop 2' }, { ...tower, name: 'Stop 1' }),
      legs: [legOf('Sylvia Park', 'Stop 2'), legOf('Stop 2', 'Stop 1')],
    }
    const list = [start, { ...museum, name: 'Stop 1' }, { ...tower, name: 'Stop 2' }]

    const named = namedAs(planned, list)

    expect(named.route.map((place) => place.name)).toEqual(['Sylvia Park', 'Stop 1', 'Stop 2'])
    expect(named.legs.map((leg) => [leg.from, leg.to])).toEqual([
      ['Sylvia Park', 'Stop 1'],
      ['Stop 1', 'Stop 2'],
    ])
  })

  it('renames the start at both ends of a round trip', () => {
    const planned = planOf({ ...start, name: 'Start' }, museum, { ...start, name: 'Start' })

    const named = namedAs(planned, [{ ...start, name: 'Home base' }, museum])

    expect(named.route.map((place) => place.name)).toEqual(['Home base', 'Museum', 'Home base'])
  })
})

describe('the leg after a place in the list', () => {
  const trip = [start, museum, tower]

  it('is the leg the plan drives from it to the next place', () => {
    expect(legAfter(trip, planOf(start, museum, tower), 0, false)).toBe(0)
    expect(legAfter(trip, planOf(start, museum, tower), 1, false)).toBe(1)
  })

  it('is none after the last place of a trip that ends there', () => {
    expect(legAfter(trip, planOf(start, museum, tower), 2, false)).toBeNull()
  })

  it('is the way back after the last place of a round trip', () => {
    expect(legAfter(trip, planOf(start, museum, tower, start), 2, true)).toBe(2)
  })

  it('is none when the plan drives another way, as it does while the list is out of step', () => {
    expect(legAfter(trip, planOf(start, tower, museum), 0, false)).toBeNull()
    expect(legAfter(trip, planOf(start, tower, museum), 1, false)).toBeNull()
  })
})

describe('adding a stop on the way', () => {
  // Entered as the tower, then the museum, then the airport. The plan drives to the museum first.
  const trip = [start, tower, museum, airport]
  const planned = planOf(start, museum, tower, airport)

  it('puts it straight after the place its leg sets off from', () => {
    expect(withStopOn(trip, planned, 1, fuel)).toEqual([start, tower, museum, fuel, airport])
  })

  it('puts it straight after the start on the first leg', () => {
    expect(withStopOn(trip, planned, 0, fuel)).toEqual([start, fuel, tower, museum, airport])
  })

  it('puts it after the last stop on the way back to the start', () => {
    const roundTrip = planOf(start, museum, tower, airport, start)

    expect(withStopOn(trip, roundTrip, 3, fuel)).toEqual([start, tower, museum, airport, fuel])
  })

  it('moves a label nobody changed along with its turn', () => {
    const stop1: Place = { name: 'Stop 1', latitude: -36.86, longitude: 174.77 }
    const stop2: Place = { name: 'Stop 2', latitude: -36.85, longitude: 174.76 }

    expect(withStopOn([start, stop1, stop2], planOf(start, stop1, stop2), 0, fuel).map((place) => place.name))
      .toEqual(['Sylvia Park', 'Z Ellerslie', 'Stop 2', 'Stop 3'])
  })
})

describe('a stop-over in the planned order', () => {
  it('stays marked when the list takes the order of the plan', () => {
    const fuelOnTheWay = { ...fuel, stopOver: true }
    const planned = planOf(start, airport, museum, fuelOnTheWay, tower)

    const ordered = inPlannedOrder([start, museum, fuelOnTheWay, tower, airport], planned, false)

    expect(ordered?.[3]).toBe(fuelOnTheWay)
  })

  it('is sent as one, with every stop kept for the plan that adds it', () => {
    const fuelOnTheWay = { ...fuel, stopOver: true }
    const planned = planOf(start, museum, tower)
    const list = withStopOn([start, museum, tower], planned, 0, fuelOnTheWay)

    const held = heldInPlannedOrder(list, planned, false, 0, fuelOnTheWay)

    expect(held?.[1]).toEqual({ ...fuelOnTheWay, keepInPlace: true })
  })
})

describe('planning with a stop on the way', () => {
  const planned = planOf(start, museum, tower, airport)

  it('keeps every stop at the turn the plan gave it, with the new one between the two', () => {
    const trip = withStopOn([start, tower, museum, airport], planned, 1, fuel)

    expect(heldInPlannedOrder(trip, planned, false, 1, fuel)).toEqual([
      start,
      { ...museum, keepInPlace: true },
      { ...fuel, keepInPlace: true },
      { ...tower, keepInPlace: true },
      { ...airport, keepInPlace: true },
    ])
  })

  it('sends the start as it is, since nothing moves it', () => {
    const kept = { ...start, keepInPlace: true }
    const trip = withStopOn([kept, tower, museum, airport], planned, 0, fuel)

    expect(heldInPlannedOrder(trip, planned, false, 0, fuel)?.[0]).toEqual(start)
  })

  it('leaves out the way back to the start, which the plan of a round trip ends with', () => {
    const roundTrip = planOf(start, museum, tower, airport, start)
    const trip = withStopOn([start, tower, museum, airport], roundTrip, 3, fuel)

    expect(heldInPlannedOrder(trip, roundTrip, true, 3, fuel)?.map((place) => place.name)).toEqual([
      'Sylvia Park',
      'Museum',
      'Sky Tower',
      'Airport',
      'Z Ellerslie',
    ])
  })

  it('sends the names the trip shows, after the labels have moved', () => {
    const stop1: Place = { name: 'Stop 1', latitude: -36.86, longitude: 174.77 }
    const stop2: Place = { name: 'Stop 2', latitude: -36.85, longitude: 174.76 }
    const plan = planOf(start, stop2, stop1)
    const trip = withStopOn([start, stop1, stop2], plan, 0, fuel)

    expect(heldInPlannedOrder(trip, plan, false, 0, fuel)?.map((place) => place.name)).toEqual([
      'Sylvia Park',
      'Z Ellerslie',
      'Stop 3',
      'Stop 2',
    ])
  })

  it('holds nothing when the plan drives to a place the trip no longer has', () => {
    expect(heldInPlannedOrder([start, tower, fuel], planned, false, 0, fuel)).toBeNull()
  })
})

describe('what a stop on the way adds', () => {
  it('says the minutes it adds', () => {
    expect(addedSaid(found({ secondsAdded: 240 }))).toBe('+4 min')
  })

  it('says the hours of a long way round', () => {
    expect(addedSaid(found({ secondsAdded: 3900 }))).toBe('+1 h 5 min')
  })

  it('says a stop that adds under a minute is on the way', () => {
    expect(addedSaid(found({ secondsAdded: 59 }))).toBe('On the way')
    expect(addedSaid(found({ secondsAdded: 0 }))).toBe('On the way')
  })

  it('says the distance it adds on straight lines', () => {
    expect(addedSaid(found({ secondsAdded: null, kmAdded: 1.24 }))).toBe('+1.2 km')
    expect(addedSaid(found({ secondsAdded: null, kmAdded: 0.04 }))).toBe('On the way')
  })

  it('says which two places its leg runs between', () => {
    expect(betweenSaid(planOf(start, museum, tower), 1)).toBe('between Museum and Sky Tower')
  })

  it('reads as a sentence with a place that is only near an address', () => {
    const planned = planOf(start, { ...museum, name: 'Near 3 Titoki Street, Parnell' }, tower)

    expect(betweenSaid(planned, 1)).toBe('between near 3 Titoki Street, Parnell and Sky Tower')
  })
})

describe('the plan going by the names of the trip', () => {
  it('leaves alone the places it is not given a name for', () => {
    const named = namedAs(planOf(start, museum, tower), [{ ...museum, name: 'Lunch' }])

    expect(named.route.map((place) => place.name)).toEqual(['Sylvia Park', 'Lunch', 'Sky Tower'])
  })

  it('keeps both of two names given one after the other', () => {
    const once = namedAs(planOf(start, museum, tower), [{ ...museum, name: 'Lunch' }])
    const twice = namedAs(once, [{ ...tower, name: 'Dinner' }])

    expect(twice.route.map((place) => place.name)).toEqual(['Sylvia Park', 'Lunch', 'Dinner'])
  })
})

describe('what a pin says', () => {
  it('says its turn in the list before planning', () => {
    expect(pinLabel(tower, 2, null)).toBe('2')
    expect(pinLabel(start, 0, null)).toBe('S')
  })

  it('says its turn on the plan once there is one', () => {
    const planned = planOf(start, tower, museum)

    expect(pinLabel(museum, 1, planned)).toBe('2')
    expect(pinLabel(tower, 2, planned)).toBe('1')
    expect(pinLabel(start, 0, planned)).toBe('S')
  })

  it('finds a pin renamed since the plan was made', () => {
    expect(pinLabel({ ...tower, name: 'Lunch' }, 1, planOf(start, tower))).toBe('1')
  })

  it('tells apart two pins with the same name', () => {
    const planned = planOf(start, tower, { ...museum, name: tower.name })

    expect(pinLabel({ ...museum, name: tower.name }, 2, planned)).toBe('2')
  })

  it('says S for the start on a round trip, which the plan lists twice', () => {
    expect(pinLabel(start, 0, planOf(start, tower, start))).toBe('S')
  })
})

describe('how much of a plan there is to look along', () => {
  it('has more than the one leg when the trip does', () => {
    expect(hasOtherLegs(planOf(start, museum, tower))).toBe(true)
  })

  it('has only that leg when the trip is one drive', () => {
    expect(hasOtherLegs(planOf(start, museum))).toBe(false)
  })
})

describe('whether a stop can be added on the way', () => {
  const plan = planOf(start, museum, tower)
  /** The start and as many stops. */
  const trip = (stops: number): Place[] => [
    start,
    ...Array.from({ length: stops }, (_, n) => ({ name: `Stop ${n + 1}`, latitude: -36.8 - n / 100, longitude: 174.7 })),
  ]

  it('can once the trip is planned', () => {
    expect(canAddOnTheWay(trip(2), false, plan, false)).toBe(true)
  })

  it('cannot before there is a plan, which says where the way goes', () => {
    expect(canAddOnTheWay(trip(2), false, null, false)).toBe(false)
  })

  it('cannot while a plan is on its way', () => {
    expect(canAddOnTheWay(trip(2), false, plan, true)).toBe(false)
  })

  it('cannot once the trip has all the stops the Google Maps link takes', () => {
    expect(canAddOnTheWay(trip(9), false, plan, false)).toBe(true)
    expect(canAddOnTheWay(trip(10), false, plan, false)).toBe(false)
    expect(canAddOnTheWay(trip(8), true, plan, false)).toBe(true)
    expect(canAddOnTheWay(trip(9), true, plan, false)).toBe(false)
  })
})

describe('the search along the planned route', () => {
  const plan = planOf(start, museum, tower)

  it('is open on the plan it was opened on', () => {
    expect(searchAlongOpen(plan, plan, true)).toBe(true)
  })

  it('is closed until it is opened', () => {
    expect(searchAlongOpen(null, plan, true)).toBe(false)
  })

  it('closes when the plan is another or there is none', () => {
    expect(searchAlongOpen(plan, planOf(start, tower, museum), true)).toBe(false)
    expect(searchAlongOpen(plan, null, false)).toBe(false)
  })

  it('closes when no stop can be added on the way', () => {
    expect(searchAlongOpen(plan, plan, false)).toBe(false)
  })
})

describe('the plan the search along the route stays open on', () => {
  const plan = planOf(start, museum, tower)

  it('is the plan it was opened on while it is open', () => {
    expect(searchAlongOpenedOn(plan, plan, true)).toBe(plan)
  })

  it('is forgotten once it closes', () => {
    expect(searchAlongOpenedOn(plan, null, false)).toBeNull()
    expect(searchAlongOpenedOn(plan, planOf(start, tower, museum), true)).toBeNull()
  })

  it('stays forgotten when the same plan comes back', () => {
    // Planning starts, so no stop can be added, and it fails, which leaves the plan as it was.
    const whilePlanning = searchAlongOpenedOn(plan, plan, false)
    expect(whilePlanning).toBeNull()
    expect(searchAlongOpenedOn(whilePlanning, plan, true)).toBeNull()
  })
})

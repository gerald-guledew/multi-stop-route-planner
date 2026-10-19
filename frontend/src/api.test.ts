import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  getVehicle,
  heightAt,
  listMakes,
  listModels,
  listSavedPlaces,
  listVariants,
  listYears,
  NotAnswering,
  optimizeRoute,
  removeSavedPlace,
  removeVehicle,
  savePlace,
  saveVehicle,
  searchPlaces,
} from './api'

/** Stands in for the API: answers every request the same way and remembers what was asked. */
function apiThatAnswers(status: number, body: unknown) {
  const fetched = vi.fn(async (_url: string, _init?: RequestInit) => ({
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  }))
  vi.stubGlobal('fetch', fetched)
  return fetched
}

/**
 * Stands in for a planner that never answers, as one on a sleeping computer does not. The
 * request stays open until it is called off, which is how a real one behaves.
 */
function apiThatNeverAnswers() {
  const fetched = vi.fn(
    (_url: string, init?: RequestInit) =>
      new Promise<never>((_resolve, reject) => {
        const signal = init?.signal
        if (signal?.aborted) {
          reject(signal.reason)
        }
        signal?.addEventListener('abort', () => reject(signal.reason))
      }),
  )
  vi.stubGlobal('fetch', fetched)
  return fetched
}

/**
 * Whether a promise has settled yet, without waiting for it. A promise that has settled runs
 * what was attached to it before anything queued afterwards, so two turns of the queue are
 * enough to find out.
 */
async function hasSettled(promise: Promise<unknown>): Promise<boolean> {
  let settled = false
  const note = () => {
    settled = true
  }
  promise.then(note, note)
  await Promise.resolve()
  await Promise.resolve()
  return settled
}

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('searching for a place', () => {
  const notCancelled = new AbortController().signal

  it('asks for the words typed, eight at most', async () => {
    const fetched = apiThatAnswers(200, [])

    await searchPlaces('new world', null, notCancelled)

    expect(fetched.mock.calls[0][0]).toBe('/api/v1/places/search?q=new+world&limit=8')
  })

  it('says where it is looking from, to about a hundred metres and no closer', async () => {
    const fetched = apiThatAnswers(200, [])

    await searchPlaces('new world', { latitude: -36.914655, longitude: 174.839159 }, notCancelled)

    expect(fetched.mock.calls[0][0]).toBe('/api/v1/places/search?q=new+world&limit=8&near=-36.915%2C174.839')
  })

  it('can be cancelled, so a slow answer cannot overwrite a newer one', async () => {
    const fetched = apiThatNeverAnswers()
    const controller = new AbortController()

    const search = searchPlaces('bassett', null, controller.signal)
    search.catch(() => undefined)
    const passedOn = fetched.mock.calls[0][1]?.signal
    expect(passedOn?.aborted).toBe(false)
    controller.abort()

    expect(passedOn?.aborted).toBe(true)
    await expect(search).rejects.toBeDefined()
  })

  it('hands back what the API found', async () => {
    const found = [{ kind: 'poi', name: 'New World Remuera', detail: '10 Clonbern Rd', latitude: -36.88, longitude: 174.8 }]
    apiThatAnswers(200, found)

    await expect(searchPlaces('new world', null, notCancelled)).resolves.toEqual(found)
  })

  it('fails with what the API said was wrong', async () => {
    apiThatAnswers(400, { title: 'Bad Request', detail: "Failed to convert 'near' with value: 'here'" })

    await expect(searchPlaces('new world', null, notCancelled)).rejects.toThrow(
      "Failed to convert 'near' with value: 'here'",
    )
  })
})

describe('asking how high a spot is', () => {
  const maungawhau = { latitude: -36.8778, longitude: 174.7639 }

  it('sends the spot in the body, where logs do not keep it', async () => {
    const fetched = apiThatAnswers(200, { heightMetres: 195 })

    await expect(heightAt(maungawhau)).resolves.toBe(195)

    const [url, init] = fetched.mock.calls[0]
    expect(url).toBe('/api/v1/heights/lookup')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual(maungawhau)
  })

  it('gives null when the planner knows no height there', async () => {
    apiThatAnswers(200, { heightMetres: null })

    await expect(heightAt(maungawhau)).resolves.toBeNull()
  })

  it('fails with what the API said was wrong', async () => {
    apiThatAnswers(400, { title: 'Bad Request', detail: 'Invalid request content.' })

    await expect(heightAt(maungawhau)).rejects.toThrow('Invalid request content.')
  })
})

describe('the vehicle saved in the planner', () => {
  const car = { litresPer100Km: 7.5, size: 'medium' as const }
  const swift = { make: 'SUZUKI', model: 'SWIFT', year: 2018, engineLitres: 1.2 }
  const foundSwift = { litresPer100Km: 4.8, figureFrom: 'register', size: null, car: swift }

  it('is saved as the register describes it when it was found there', async () => {
    const fetched = apiThatAnswers(200, foundSwift)

    await expect(saveVehicle({ car: swift, litresPer100Km: null })).resolves.toEqual(foundSwift)

    // No figure is sent, so the register's is used.
    expect(JSON.parse(String(fetched.mock.calls[0][1]?.body))).toEqual({ car: swift })
  })

  it("is saved with the dashboard's figure when one was typed for a car found in the register", async () => {
    const fetched = apiThatAnswers(200, foundSwift)

    await saveVehicle({ car: swift, litresPer100Km: 5.6 })

    expect(JSON.parse(String(fetched.mock.calls[0][1]?.body))).toEqual({ car: swift, litresPer100Km: 5.6 })
  })

  it('is none until one is saved', async () => {
    apiThatAnswers(204, undefined)

    await expect(getVehicle()).resolves.toBeNull()
  })

  it('is read when one is saved', async () => {
    const fetched = apiThatAnswers(200, car)

    await expect(getVehicle()).resolves.toEqual(car)
    expect(fetched.mock.calls[0][0]).toBe('/api/v1/vehicle')
  })

  it('is saved by sending its two figures, in place of the one there was', async () => {
    const fetched = apiThatAnswers(200, car)

    await expect(saveVehicle(car)).resolves.toEqual(car)

    const [url, init] = fetched.mock.calls[0]
    expect(url).toBe('/api/v1/vehicle')
    expect(init?.method).toBe('PUT')
    expect(JSON.parse(String(init?.body))).toEqual(car)
  })

  it('is removed', async () => {
    const fetched = apiThatAnswers(204, undefined)

    await removeVehicle()

    expect(fetched.mock.calls[0][1]?.method).toBe('DELETE')
  })

  it('fails with what the API said was wrong', async () => {
    apiThatAnswers(400, { title: 'Bad Request', detail: 'Validation failed' })

    await expect(saveVehicle(car)).rejects.toThrow('Validation failed')
  })
})

describe('finding a car in the register', () => {
  it('lists the makes', async () => {
    const fetched = apiThatAnswers(200, ['MAZDA', 'SUZUKI'])

    await expect(listMakes()).resolves.toEqual(['MAZDA', 'SUZUKI'])
    expect(fetched.mock.calls[0][0]).toBe('/api/v1/cars/makes')
  })

  it('lists the models of a make, with the make written safely into the address', async () => {
    const fetched = apiThatAnswers(200, ['DEFENDER'])

    await listModels('LAND ROVER')

    expect(fetched.mock.calls[0][0]).toBe('/api/v1/cars/models?make=LAND+ROVER')
  })

  it('lists the years of a model', async () => {
    const fetched = apiThatAnswers(200, [2018, 2017])

    await expect(listYears('MAZDA', 'CX-5')).resolves.toEqual([2018, 2017])
    expect(fetched.mock.calls[0][0]).toBe('/api/v1/cars/years?make=MAZDA&model=CX-5')
  })

  it('lists the engines of a year, each with its figure', async () => {
    const swift = { make: 'SUZUKI', model: 'SWIFT', year: 2018, engineLitres: 1.2 }
    const fetched = apiThatAnswers(200, [{ car: swift, litresPer100Km: 4.8, cars: 1896 }])

    await expect(listVariants('SUZUKI', 'SWIFT', 2018)).resolves.toEqual([
      { car: swift, litresPer100Km: 4.8, cars: 1896 },
    ])
    expect(fetched.mock.calls[0][0]).toBe('/api/v1/cars/variants?make=SUZUKI&model=SWIFT&year=2018')
  })

  it('fails with what the API said was wrong', async () => {
    apiThatAnswers(400, { title: 'Bad Request', detail: "Required parameter 'make' is not present." })

    await expect(listModels('')).rejects.toThrow("Required parameter 'make' is not present.")
  })
})

describe('planning a route', () => {
  const start = { name: 'Sky Tower', latitude: -36.8485, longitude: 174.7621 }
  const stops = [{ name: 'Takapuna', latitude: -36.787, longitude: 174.774 }]

  it('posts the start, the stops and whether to come back', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })

    await optimizeRoute(start, stops, false, 'distance', null)

    const [url, init] = fetched.mock.calls[0]
    expect(url).toBe('/api/v1/routes/optimize')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({
      start,
      stops,
      returnToStart: false,
      factorInRoadClimbs: false,
      fastest: false,
    })
  })

  it('sends the fuel use of the vehicle and its weight, when there is one', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })

    await optimizeRoute(start, stops, true, 'distance', { litresPer100Km: 7.5, kilograms: 1270 })

    const sent = JSON.parse(String(fetched.mock.calls[0][1]?.body))
    expect(sent.vehicle).toEqual({ litresPer100Km: 7.5, kilograms: 1270 })
  })

  it('sends the fuel use alone for a vehicle whose weight is not known', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })

    await optimizeRoute(start, stops, true, 'distance', { litresPer100Km: 7.5, kilograms: null })

    const sent = JSON.parse(String(fetched.mock.calls[0][1]?.body))
    expect(sent.vehicle).toEqual({ litresPer100Km: 7.5 })
  })

  it('sends no vehicle when there is none', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })

    await optimizeRoute(start, stops, true, 'distance', null)

    const sent = JSON.parse(String(fetched.mock.calls[0][1]?.body))
    expect(sent).not.toHaveProperty('vehicle')
  })

  it('asks for road climbs to be factored in when told to', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })

    await optimizeRoute(start, stops, true, 'distanceAndClimb', null)

    const sent = JSON.parse(String(fetched.mock.calls[0][1]?.body))
    expect(sent.factorInRoadClimbs).toBe(true)
    expect(sent.fastest).toBe(false)
    expect(sent.returnToStart).toBe(true)
  })

  it('asks for the fastest order when told to', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })

    await optimizeRoute(start, stops, true, 'time', null)

    const sent = JSON.parse(String(fetched.mock.calls[0][1]?.body))
    expect(sent.fastest).toBe(true)
    expect(sent.factorInRoadClimbs).toBe(false)
  })

  it('says which stops have to keep their place', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })
    const office = { name: 'Office', latitude: -36.8485, longitude: 174.7621, keepInPlace: true }

    await optimizeRoute(start, [office, ...stops], true, 'distance', null)

    const sent = JSON.parse(String(fetched.mock.calls[0][1]?.body))
    expect(sent.stops[0].keepInPlace).toBe(true)
    // A stop nobody marked is sent without the mark, which the API reads as free to move.
    expect(sent.stops[1]).toEqual(stops[0])
  })

  it('names the place no road reaches, so the map can point at it', async () => {
    apiThatAnswers(422, { title: 'Unroutable place', detail: 'Cannot route to Airport.', place: 'Airport' })

    const failure = await optimizeRoute(start, stops, true, 'distance', null).catch((caught: unknown) => caught)

    expect(failure).toBeInstanceOf(ApiError)
    expect((failure as ApiError).place).toBe('Airport')
    expect((failure as ApiError).message).toBe('Cannot route to Airport.')
  })

  it('still fails sensibly when the error has no body to read', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({
        ok: false,
        status: 500,
        json: async () => {
          throw new SyntaxError('not json')
        },
      })),
    )

    await expect(optimizeRoute(start, stops, true, 'distance', null)).rejects.toThrow('Request failed with status 500')
  })
})

describe('saved places', () => {
  const home = { id: 7, name: 'Home', latitude: -36.9146416667, longitude: 174.8392303333 }

  it('asks for the list', async () => {
    const fetched = apiThatAnswers(200, [home])

    await expect(listSavedPlaces()).resolves.toEqual([home])

    expect(fetched.mock.calls[0][0]).toBe('/api/v1/saved-places')
  })

  it('can stop waiting for the list, when the page is left', async () => {
    const fetched = apiThatNeverAnswers()
    const leaving = new AbortController()

    const list = listSavedPlaces(leaving.signal)
    list.catch(() => undefined)
    const passedOn = fetched.mock.calls[0][1]?.signal
    expect(passedOn?.aborted).toBe(false)
    leaving.abort()

    expect(passedOn?.aborted).toBe(true)
    await expect(list).rejects.toBeDefined()
  })

  it('saves a place under its name, to the last digit of where it is', async () => {
    const fetched = apiThatAnswers(201, home)

    await expect(
      savePlace({ name: 'Home', latitude: -36.9146416667, longitude: 174.8392303333 }),
    ).resolves.toEqual(home)

    const [url, request] = fetched.mock.calls[0]
    expect(url).toBe('/api/v1/saved-places')
    expect(request?.method).toBe('POST')
    expect(request?.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(JSON.parse(request?.body as string)).toEqual({
      name: 'Home',
      latitude: -36.9146416667,
      longitude: 174.8392303333,
    })
  })

  it('does not save that a stop was kept in its place, which belongs to one trip', async () => {
    const fetched = apiThatAnswers(201, home)

    await savePlace({ name: 'Home', latitude: -36.87, longitude: 174.78, keepInPlace: true })

    expect(JSON.parse(fetched.mock.calls[0][1]?.body as string)).not.toHaveProperty('keepInPlace')
  })

  it('fails with what the API said when the name is taken', async () => {
    apiThatAnswers(409, {
      title: 'Name already saved',
      detail: 'A place called "Home" is already saved. Remove it first, or save this one under another name.',
    })

    const refusal = savePlace({ name: 'Home', latitude: -36.87, longitude: 174.78 })

    await expect(refusal).rejects.toBeInstanceOf(ApiError)
    await expect(refusal).rejects.toThrow('A place called "Home" is already saved.')
  })

  it('removes a place by its id', async () => {
    const fetched = apiThatAnswers(204, undefined)

    await expect(removeSavedPlace(7)).resolves.toBeUndefined()

    expect(fetched.mock.calls[0][0]).toBe('/api/v1/saved-places/7')
    expect(fetched.mock.calls[0][1]?.method).toBe('DELETE')
  })

  it('fails with what the API said when there is no such place to remove', async () => {
    apiThatAnswers(404, { title: 'No such saved place', detail: 'No saved place has the id 7.' })

    await expect(removeSavedPlace(7)).rejects.toThrow('No saved place has the id 7.')
  })
})

describe('telling whether a promise has settled', () => {
  it('says yes for one that has resolved, and for one that has rejected', async () => {
    const refused = Promise.reject(new Error('no'))

    expect(await hasSettled(Promise.resolve(1))).toBe(true)
    expect(await hasSettled(refused)).toBe(true)
  })

  it('says no for one still waiting', async () => {
    expect(await hasSettled(new Promise(() => undefined))).toBe(false)
  })
})

describe('a planner that does not answer', () => {
  const notCancelled = new AbortController().signal
  const home = { name: 'Home', latitude: -36.87, longitude: 174.78 }
  const office = { name: 'Office', latitude: -36.85, longitude: 174.76 }

  it('is given up on after eight seconds of searching, and said to be not answering', async () => {
    vi.useFakeTimers()
    apiThatNeverAnswers()

    const search = searchPlaces('sylvia park', null, notCancelled)
    const outcome = expect(search).rejects.toBeInstanceOf(NotAnswering)
    await vi.advanceTimersByTimeAsync(8_000)

    await outcome
  })

  it('is still being waited for just before the eight seconds are up', async () => {
    vi.useFakeTimers()
    apiThatNeverAnswers()

    const search = searchPlaces('sylvia park', null, notCancelled)
    search.catch(() => undefined)
    await vi.advanceTimersByTimeAsync(7_900)

    expect(await hasSettled(search)).toBe(false)
  })

  it('is given longer when planning, which compares every order', async () => {
    vi.useFakeTimers()
    apiThatNeverAnswers()

    const planning = optimizeRoute(home, [office], true, 'distance', null)
    const outcome = expect(planning).rejects.toBeInstanceOf(NotAnswering)
    await vi.advanceTimersByTimeAsync(8_000)
    expect(await hasSettled(planning)).toBe(false)
    await vi.advanceTimersByTimeAsync(22_000)

    await outcome
  })

  it.each([
    ['reading the saved places', () => listSavedPlaces()],
    ['saving a place', () => savePlace(home)],
    ['removing a saved place', () => removeSavedPlace(7)],
  ])('is given up on after eight seconds of %s', async (_what, call) => {
    vi.useFakeTimers()
    apiThatNeverAnswers()

    const waiting = call()
    const outcome = expect(waiting).rejects.toBeInstanceOf(NotAnswering)
    await vi.advanceTimersByTimeAsync(8_000)

    await outcome
  })

  it('is said to be not answering when the request cannot get through at all', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )

    await expect(searchPlaces('sylvia park', null, notCancelled)).rejects.toBeInstanceOf(NotAnswering)
  })

  it.each([502, 503, 504])(
    'is said to be not answering when something in front of it answers %d in its place',
    async (status) => {
      apiThatAnswers(status, undefined)

      await expect(searchPlaces('sylvia park', null, notCancelled)).rejects.toBeInstanceOf(NotAnswering)
    },
  )

  it('is taken at its word when it answers with a failure of its own', async () => {
    apiThatAnswers(500, { title: 'Internal Server Error', detail: 'Something broke.' })

    const failure = searchPlaces('sylvia park', null, notCancelled)

    await expect(failure).rejects.toBeInstanceOf(ApiError)
    await expect(failure).rejects.toThrow('Something broke.')
  })

  it('says what to check, in words fit to show', () => {
    expect(new NotAnswering().message).toBe(
      'The planner is not answering. If it runs on your own computer, check that it is switched on and awake.',
    )
  })

  it('is not blamed when the search was called off for a newer one', async () => {
    apiThatNeverAnswers()
    const controller = new AbortController()

    const search = searchPlaces('sylv', null, controller.signal)
    controller.abort()

    await expect(search).rejects.not.toBeInstanceOf(NotAnswering)
  })

  it('is not blamed when the search was called off before it began', async () => {
    apiThatNeverAnswers()
    const controller = new AbortController()
    controller.abort()

    await expect(searchPlaces('sylv', null, controller.signal)).rejects.not.toBeInstanceOf(NotAnswering)
  })

  it('is not timed any more once it has answered', async () => {
    vi.useFakeTimers()
    apiThatAnswers(200, [])

    await searchPlaces('sylvia park', null, notCancelled)

    expect(vi.getTimerCount()).toBe(0)
  })

  it('is not timed any more once it has refused', async () => {
    vi.useFakeTimers()
    apiThatAnswers(409, { detail: 'A place called "Home" is already saved.' })

    await expect(savePlace(home)).rejects.toBeInstanceOf(ApiError)

    expect(vi.getTimerCount()).toBe(0)
  })
})

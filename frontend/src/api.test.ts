import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  listSavedPlaces,
  NotAnswering,
  optimizeRoute,
  removeSavedPlace,
  savePlace,
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

    await searchPlaces('new world', { latitude: -36.868955, longitude: 174.783159 }, notCancelled)

    expect(fetched.mock.calls[0][0]).toBe('/api/v1/places/search?q=new+world&limit=8&near=-36.869%2C174.783')
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

describe('planning a route', () => {
  const start = { name: 'Sky Tower', latitude: -36.8485, longitude: 174.7621 }
  const stops = [{ name: 'Takapuna', latitude: -36.787, longitude: 174.774 }]

  it('posts the start, the stops and whether to come back', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })

    await optimizeRoute(start, stops, false)

    const [url, init] = fetched.mock.calls[0]
    expect(url).toBe('/api/v1/routes/optimize')
    expect(init?.method).toBe('POST')
    expect(JSON.parse(String(init?.body))).toEqual({ start, stops, returnToStart: false })
  })

  it('says which stops have to keep their place', async () => {
    const fetched = apiThatAnswers(200, { route: [], legs: [] })
    const office = { name: 'Office', latitude: -36.8485, longitude: 174.7621, keepInPlace: true }

    await optimizeRoute(start, [office, ...stops], true)

    const sent = JSON.parse(String(fetched.mock.calls[0][1]?.body))
    expect(sent.stops[0].keepInPlace).toBe(true)
    // A stop nobody marked is sent without the mark, which the API reads as free to move.
    expect(sent.stops[1]).toEqual(stops[0])
  })

  it('names the place no road reaches, so the map can point at it', async () => {
    apiThatAnswers(422, { title: 'Unroutable place', detail: 'Cannot route to Airport.', place: 'Airport' })

    const failure = await optimizeRoute(start, stops, true).catch((caught: unknown) => caught)

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

    await expect(optimizeRoute(start, stops, true)).rejects.toThrow('Request failed with status 500')
  })
})

describe('saved places', () => {
  const home = { id: 7, name: 'Home', latitude: -36.8689416667, longitude: 174.7832303333 }

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
      savePlace({ name: 'Home', latitude: -36.8689416667, longitude: 174.7832303333 }),
    ).resolves.toEqual(home)

    const [url, request] = fetched.mock.calls[0]
    expect(url).toBe('/api/v1/saved-places')
    expect(request?.method).toBe('POST')
    expect(request?.headers).toEqual({ 'Content-Type': 'application/json' })
    expect(JSON.parse(request?.body as string)).toEqual({
      name: 'Home',
      latitude: -36.8689416667,
      longitude: 174.7832303333,
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

    const planning = optimizeRoute(home, [office], true)
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

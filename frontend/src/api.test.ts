import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  ApiError,
  listSavedPlaces,
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

afterEach(() => {
  vi.unstubAllGlobals()
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
    const fetched = apiThatAnswers(200, [])
    const controller = new AbortController()

    await searchPlaces('bassett', null, controller.signal)

    expect(fetched.mock.calls[0][1]).toEqual({ signal: controller.signal })
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
        status: 502,
        json: async () => {
          throw new SyntaxError('not json')
        },
      })),
    )

    await expect(optimizeRoute(start, stops, true)).rejects.toThrow('Request failed with status 502')
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
    const fetched = apiThatAnswers(200, [])
    const leaving = new AbortController()

    await listSavedPlaces(leaving.signal)

    expect(fetched.mock.calls[0][1]).toEqual({ signal: leaving.signal })
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
    expect(fetched.mock.calls[0][1]).toEqual({ method: 'DELETE' })
  })

  it('fails with what the API said when there is no such place to remove', async () => {
    apiThatAnswers(404, { title: 'No such saved place', detail: 'No saved place has the id 7.' })

    await expect(removeSavedPlace(7)).rejects.toThrow('No saved place has the id 7.')
  })
})

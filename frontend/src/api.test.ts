import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, optimizeRoute, searchPlaces } from './api'

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

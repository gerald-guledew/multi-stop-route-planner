import type { Position } from './location'
import type { FoundPlace, Place, ProblemDetail, RoutePlan, SavedPlace } from './types'

/**
 * An error the API explained properly, rather than a bare status code.
 * `place` is set when a stop has no road near it, so the map can point at it.
 */
export class ApiError extends Error {
  readonly place?: string
  readonly fieldErrors: { field: string; message: string }[]

  constructor(problem: ProblemDetail, status: number) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.place = problem.place
    this.fieldErrors = problem.errors ?? []
  }
}

/**
 * Throws what the API said was wrong. Every refusal it sends carries a problem details body
 * worth showing the user, whatever the status.
 */
async function refused(response: Response): Promise<never> {
  const problem = (await response.json().catch(() => ({}))) as ProblemDetail
  throw new ApiError(problem, response.status)
}

/**
 * Address and business lookup for the type-ahead.
 *
 * <p>`near` is where the search is looking from, so the nearest of several matches comes
 * first. It is sent to three decimal places, about 100 metres. Ranking needs no more, and a
 * URL is the part of a request that gets written to logs, which is no place for an exact
 * position.
 *
 * <p>Takes an AbortSignal because keystrokes outrun the network: without cancelling the
 * previous request, a slow answer for "bass" can arrive after the answer for "bassett road"
 * and overwrite it.
 */
export async function searchPlaces(
  query: string,
  near: Position | null,
  signal: AbortSignal,
): Promise<FoundPlace[]> {
  const parameters = new URLSearchParams({ q: query, limit: '8' })
  if (near) {
    parameters.set('near', `${near.latitude.toFixed(3)},${near.longitude.toFixed(3)}`)
  }

  const response = await fetch(`/api/v1/places/search?${parameters}`, { signal })

  if (!response.ok) {
    return refused(response)
  }

  return (await response.json()) as FoundPlace[]
}

export async function optimizeRoute(
  start: Place,
  stops: Place[],
  returnToStart: boolean,
): Promise<RoutePlan> {
  const response = await fetch('/api/v1/routes/optimize', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ start, stops, returnToStart }),
  })

  if (!response.ok) {
    return refused(response)
  }

  return (await response.json()) as RoutePlan
}

/**
 * The places saved so far, in name order.
 *
 * <p>Takes an AbortSignal so that leaving the page while the list is on its way does not end
 * in an answer nobody is waiting for.
 */
export async function listSavedPlaces(signal?: AbortSignal): Promise<SavedPlace[]> {
  const response = await fetch('/api/v1/saved-places', { signal })

  if (!response.ok) {
    return refused(response)
  }

  return (await response.json()) as SavedPlace[]
}

/**
 * Saves a place under its name. A name can be saved once, whatever the capitals, and the API
 * refuses a second one in words fit to show.
 */
export async function savePlace(place: Place): Promise<SavedPlace> {
  const response = await fetch('/api/v1/saved-places', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    // Only what a saved place is. A stop's mark to keep its turn belongs to one trip.
    body: JSON.stringify({
      name: place.name,
      latitude: place.latitude,
      longitude: place.longitude,
    }),
  })

  if (!response.ok) {
    return refused(response)
  }

  return (await response.json()) as SavedPlace
}

export async function removeSavedPlace(id: number): Promise<void> {
  const response = await fetch(`/api/v1/saved-places/${id}`, { method: 'DELETE' })

  if (!response.ok) {
    return refused(response)
  }
}

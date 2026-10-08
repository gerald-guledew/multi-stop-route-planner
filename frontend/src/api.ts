import type { Position } from './location'
import type { FoundPlace, Place, ProblemDetail, RoutePlan } from './types'

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
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail
    throw new ApiError(problem, response.status)
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
    // Both 400 and 422 carry a problem details body worth showing the user.
    const problem = (await response.json().catch(() => ({}))) as ProblemDetail
    throw new ApiError(problem, response.status)
  }

  return (await response.json()) as RoutePlan
}

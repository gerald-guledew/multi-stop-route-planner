import type { Place, ProblemDetail, RoutePlan } from './types'

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

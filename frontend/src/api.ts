import type { Position } from './location'
import type { FoundPlace, Place, ProblemDetail, RoutePlan, SavedPlace, Vehicle } from './types'

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
 * No answer came back at all. The planner is switched off, asleep or out of reach. Not the
 * same as a refusal, where the planner answered and said no.
 *
 * The words are for whoever is looking at the screen. They say what to check, because the
 * usual cause is the plainest one: the computer the planner runs on has gone to sleep.
 */
export class NotAnswering extends Error {
  constructor() {
    super(
      'The planner is not answering. If it runs on your own computer, check that it is switched on and awake.',
    )
    this.name = 'NotAnswering'
  }
}

/** A search or a saved place is answered in a blink. This long without an answer means none is coming. */
const A_QUICK_ANSWER_MS = 8_000

/** Planning compares every order of up to ten stops on real roads, so it is given longer. */
const A_PLAN_MS = 30_000

/**
 * What a tunnel or a proxy in front of the planner answers when the planner behind it does
 * not: bad gateway, service unavailable, gateway timeout. Something answered, but it was not
 * the planner.
 */
const ANSWERED_IN_ITS_PLACE = [502, 503, 504]

/**
 * Asks the API, and gives up after a time.
 *
 * A request to a computer that is asleep neither fails nor answers. Left alone the browser
 * waits for minutes, and the screen sits there showing nothing. So every request has a limit,
 * and running out of it is reported the same way as not getting through at all.
 *
 * So is an answer that came from something in front of the planner, in its place.
 *
 * The request can also be called off by whoever made it: a newer search, or the page being
 * left. That is passed on as it is. It is not the planner's doing.
 */
async function ask(url: string, patienceMs: number, request: RequestInit = {}): Promise<Response> {
  const calledOff = request.signal ?? undefined
  const limit = new AbortController()
  const outOfPatience = setTimeout(() => limit.abort(), patienceMs)
  const passItOn = () => limit.abort()
  calledOff?.addEventListener('abort', passItOn)
  if (calledOff?.aborted) {
    limit.abort()
  }

  let response: Response
  try {
    response = await fetch(url, { ...request, signal: limit.signal })
  } catch (caught) {
    if (calledOff?.aborted) {
      throw caught
    }
    throw new NotAnswering()
  } finally {
    clearTimeout(outOfPatience)
    calledOff?.removeEventListener('abort', passItOn)
  }

  if (ANSWERED_IN_ITS_PLACE.includes(response.status)) {
    throw new NotAnswering()
  }
  return response
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

  const response = await ask(`/api/v1/places/search?${parameters}`, A_QUICK_ANSWER_MS, { signal })

  if (!response.ok) {
    return refused(response)
  }

  return (await response.json()) as FoundPlace[]
}

/**
 * `factorInRoadClimbs` asks for an order chosen for distance and climb together. A planner with
 * no heights to go on answers with the shortest order all the same.
 *
 * `vehicle` is what the fuel is worked out for. Without one the plan says nothing about fuel.
 * Only its fuel use is sent, since that is all the planner reads of it so far.
 */
export async function optimizeRoute(
  start: Place,
  stops: Place[],
  returnToStart: boolean,
  factorInRoadClimbs: boolean,
  vehicle: Vehicle | null,
): Promise<RoutePlan> {
  const fuel = vehicle ? { vehicle: { litresPer100Km: vehicle.litresPer100Km } } : {}
  const response = await ask('/api/v1/routes/optimize', A_PLAN_MS, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ start, stops, returnToStart, factorInRoadClimbs, ...fuel }),
  })

  if (!response.ok) {
    return refused(response)
  }

  return (await response.json()) as RoutePlan
}

/**
 * How high the ground is at a spot, in whole metres above sea level. Null when the planner has
 * nothing to go on there: it was started without heights, or the spot is outside the height
 * files it holds.
 *
 * <p>A POST, so the exact spot travels in the body and not in the URL, which is what logs keep.
 */
export async function heightAt(at: Position): Promise<number | null> {
  const response = await ask('/api/v1/heights/lookup', A_QUICK_ANSWER_MS, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ latitude: at.latitude, longitude: at.longitude }),
  })

  if (!response.ok) {
    return refused(response)
  }

  return ((await response.json()) as { heightMetres: number | null }).heightMetres
}

/**
 * The places saved so far, in name order.
 *
 * <p>Takes an AbortSignal so that leaving the page while the list is on its way does not end
 * in an answer nobody is waiting for.
 */
export async function listSavedPlaces(signal?: AbortSignal): Promise<SavedPlace[]> {
  const response = await ask('/api/v1/saved-places', A_QUICK_ANSWER_MS, { signal })

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
  const response = await ask('/api/v1/saved-places', A_QUICK_ANSWER_MS, {
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

/** The vehicle saved in the planner. Null until one is saved: the API then answers 204. */
export async function getVehicle(signal?: AbortSignal): Promise<Vehicle | null> {
  const response = await ask('/api/v1/vehicle', A_QUICK_ANSWER_MS, { signal })

  if (!response.ok) {
    return refused(response)
  }

  return response.status === 204 ? null : ((await response.json()) as Vehicle)
}

/** Saves the vehicle in place of the one there was. */
export async function saveVehicle(vehicle: Vehicle): Promise<Vehicle> {
  const response = await ask('/api/v1/vehicle', A_QUICK_ANSWER_MS, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ litresPer100Km: vehicle.litresPer100Km, size: vehicle.size }),
  })

  if (!response.ok) {
    return refused(response)
  }

  return (await response.json()) as Vehicle
}

export async function removeVehicle(): Promise<void> {
  const response = await ask('/api/v1/vehicle', A_QUICK_ANSWER_MS, { method: 'DELETE' })

  if (!response.ok) {
    return refused(response)
  }
}

export async function removeSavedPlace(id: number): Promise<void> {
  const response = await ask(`/api/v1/saved-places/${id}`, A_QUICK_ANSWER_MS, { method: 'DELETE' })

  if (!response.ok) {
    return refused(response)
  }
}

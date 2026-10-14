/** Mirrors the API contract in docs/api.md. */

export interface Place {
  name: string
  latitude: number
  longitude: number
  /**
   * Set on a stop that has to be visited at the turn it has in the list, with the others
   * arranged around it. The first call of the day that cannot wait, say. Never set on the
   * start, which is first anyway.
   */
  keepInPlace?: boolean
}

/** One search result: a street address, or a named place such as a shop or a school. */
export interface FoundPlace extends Place {
  kind: 'address' | 'poi'
  /** Where a named place is, to tell two with the same name apart. Null for an address. */
  detail: string | null
}

/** A place somebody named and saved, so it can be picked again without typing. */
export interface SavedPlace {
  /** What it is removed by. The API hands it out when the place is saved. */
  id: number
  name: string
  latitude: number
  longitude: number
}

export interface Leg {
  from: string
  to: string
  distanceKm: number
  /** Driving time on clear roads, with nothing for a stop. Null on straight-line distances. */
  durationSeconds: number | null
  /**
   * How much this leg goes up, in metres added along the road. Null unless the planner read
   * the roads with their heights.
   */
  climbMetres: number | null
  /** How much it comes down, as a number above zero. Null whenever the climb is. */
  descentMetres: number | null
  /** The road this leg follows, as [latitude, longitude] pairs. Empty on straight-line distances. */
  path: [number, number][]
}

/** What the shortest order comes to: how far it is, and how much it climbs and comes down. */
export interface ShortestOrder {
  distanceKm: number
  climbMetres: number
  descentMetres: number
}

export interface RoutePlan {
  route: Place[]
  legs: Leg[]
  totalDistanceKm: number
  /** Driving time for the whole trip. Null on straight-line distances. */
  totalDurationSeconds: number | null
  /** Climb and descent for the whole trip. Null unless the roads were read with their heights. */
  totalClimbMetres: number | null
  totalDescentMetres: number | null
  enteredOrderDistanceKm: number
  /**
   * Set when the order was chosen for distance and climb together, so that it can be held
   * against the shortest. Null when it was chosen for distance alone. That includes a planner
   * asked to factor in road climbs that has no heights to go on.
   */
  shortestOrder: ShortestOrder | null
  ordersChecked: number
}

/** RFC 9457 problem details, as the API returns for 400 and 422. */
export interface ProblemDetail {
  title?: string
  status?: number
  detail?: string
  errors?: { field: string; message: string }[]
  place?: string
}

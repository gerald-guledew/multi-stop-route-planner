/** Mirrors the API contract in docs/api.md. */

export interface Place {
  name: string
  latitude: number
  longitude: number
}

/** One search result: a street address, or a named place such as a shop or a school. */
export interface FoundPlace extends Place {
  kind: 'address' | 'poi'
  /** Where a named place is, to tell two with the same name apart. Null for an address. */
  detail: string | null
}

export interface Leg {
  from: string
  to: string
  distanceKm: number
  /** The road this leg follows, as [latitude, longitude] pairs. Empty on straight-line distances. */
  path: [number, number][]
}

export interface RoutePlan {
  route: Place[]
  legs: Leg[]
  totalDistanceKm: number
  enteredOrderDistanceKm: number
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

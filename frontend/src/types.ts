/** Mirrors the API contract in docs/api.md. */

export interface Place {
  name: string
  latitude: number
  longitude: number
}

export interface Leg {
  from: string
  to: string
  distanceKm: number
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

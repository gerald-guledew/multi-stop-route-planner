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

/** How big a vehicle is. It stands in for the weight, which few drivers know. */
export type VehicleSize = 'small' | 'medium' | 'large'

/** A car as NZTA's vehicle register describes it, and as a driver would. */
export interface Car {
  /** As the register writes it, such as "SUZUKI". */
  make: string
  model: string
  year: number
  /** To the nearest tenth of a litre, such as 1.2. */
  engineLitres: number
}

/** One car the register lists, with the figure most cars like it carry. */
export interface CarVariant {
  car: Car
  /** The official figure: the combined figure of the test the car was approved with. */
  litresPer100Km: number
  /** How many of these cars are registered. */
  cars: number
}

/** Where a vehicle's fuel use comes from: its dashboard, or the register's official figure. */
export type FigureFrom = 'dashboard' | 'register'

/**
 * The vehicle saved in the planner. Petrol cars only for now. Entered by hand, with a size, or
 * found in the register, with its make, model, year and engine.
 */
export interface Vehicle {
  /** From 2 to 30. */
  litresPer100Km: number
  figureFrom: FigureFrom
  /** Null for a car found in the register. */
  size: VehicleSize | null
  /** Null for a vehicle entered by hand. */
  car: Car | null
}

/**
 * A vehicle as it is sent to be saved. Entered by hand, with the dashboard's figure, or found
 * in the register, with the dashboard's figure only if the driver knows it.
 */
export type VehicleToSave =
  | { size: VehicleSize; litresPer100Km: number }
  | { car: Car; litresPer100Km: number | null }

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
  /** The fuel this leg takes, for the vehicle sent with the plan. Null when none was sent. */
  fuelLitres: number | null
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
  /** The fuel the whole trip takes, for the vehicle sent with the plan. Null when none was sent. */
  totalFuelLitres: number | null
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

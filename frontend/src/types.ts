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
  /**
   * Set on a stop added on the way between two others. It stays between the places either side
   * of it in the list, and the three move as one. Never set on the start.
   */
  stopOver?: boolean
}

/** One search result: a street address, or a named place such as a shop or a school. */
export interface FoundPlace extends Place {
  kind: 'address' | 'poi'
  /** Where a named place is, to tell two with the same name apart. Null for an address. */
  detail: string | null
}

/** A place to stop at on the way, and what stopping there adds to the leg it is a stop on. */
export interface PlaceAlong extends FoundPlace {
  /** The leg, counting from 0: from the place the plan drives to at that turn to the next. */
  leg: number
  /** How much further that leg is with the stop on it. */
  kmAdded: number
  /** How much longer it takes, on clear roads and with no time spent there. Null on straight lines. */
  secondsAdded: number | null
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
  /** What town roads take: the figure split as the register splits the car's, or the usual way. */
  townLitresPer100Km: number
  /** What the open road takes, split the same way. */
  openRoadLitresPer100Km: number
  /** Null for a car found in the register. */
  size: VehicleSize | null
  /** Null for a vehicle entered by hand. */
  car: Car | null
  /** What it weighs empty, to the nearest 10 kg. Null when the register no longer lists the car. */
  kilograms: number | null
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
  /** The turns along that road, from setting off to arriving. Empty on straight-line distances. */
  turns: Turn[]
}

/** Which way a turn goes. */
export type TurnWay =
  | 'setOff'
  | 'continue'
  | 'slightLeft'
  | 'left'
  | 'sharpLeft'
  | 'slightRight'
  | 'right'
  | 'sharpRight'
  | 'keepLeft'
  | 'keepRight'
  | 'uTurn'
  | 'roundabout'
  | 'leaveRoundabout'
  | 'ferry'
  | 'arrive'

/** One turn along a leg's road, as the planner knows it. What a driver is told is up to the screen. */
export interface Turn {
  turn: TurnWay
  /** The road it goes onto, by its name, or by its number when it has none. Null when the map gives neither. */
  street: string | null
  /**
   * The road the drive after it mostly follows, when it goes onto one the map does not name, such
   * as a motorway's on-ramp. Null otherwise.
   */
  follows: string | null
  /** Where the signs point. Null where none do. */
  towards: string | null
  /** The name of the motorway exit it takes. Null for any other turn. */
  motorwayExit: string | null
  /** Which exit of a roundabout to take, counting from where it is entered. Null for any other turn. */
  roundaboutExit: number | null
  /** The bearing it sets off on, in degrees clockwise from north. Null for any turn but setting off. */
  heading: number | null
  /** How far to drive after it, before the next turn. Nought on arriving. */
  distanceMetres: number
}

/**
 * What an order other than the plan's comes to, the shortest or the other choice: how far it is,
 * how long it takes, how much it climbs, and the fuel it takes.
 */
export interface OrderTotals {
  distanceKm: number
  /** Driving time on clear roads. Null on straight-line distances. */
  durationSeconds: number | null
  /** Null unless the planner read the roads with their heights. */
  climbMetres: number | null
  /** Null whenever the climb is. */
  descentMetres: number | null
  /** For the vehicle sent with the plan. Null when none was sent. */
  fuelLitres: number | null
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
  /**
   * Whether the fuel counts the hills: not without heights, nor for a vehicle sent without its
   * weight. Null when no vehicle was sent.
   */
  fuelCountsHills: boolean | null
  /**
   * Whether the fuel takes the town figure on town roads and the open road's elsewhere: not on
   * straight lines. Null when no vehicle was sent.
   */
  fuelCountsSpeed: boolean | null
  enteredOrderDistanceKm: number
  /**
   * Set when the order was chosen for driving time or for fuel, so that it can be held against
   * the shortest. Null when it was chosen for distance alone. That includes a planner asked for
   * the fastest order or the least fuel that measures straight lines.
   */
  shortestOrder: OrderTotals | null
  /** Beside the least fuel, what the fastest order comes to. Null when the roads have no times. */
  fastestOrder: OrderTotals | null
  /**
   * Beside the fastest, what the order taking the least fuel comes to. Null without a vehicle,
   * and on straight lines.
   */
  leastFuelOrder: OrderTotals | null
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

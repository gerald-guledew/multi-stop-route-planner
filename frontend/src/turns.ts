import { ordinal } from './stops'
import type { Turn } from './types'

/**
 * A turn said the way a driver is told it: "Turn left onto Great South Road", "At the
 * roundabout, take the 2nd exit onto Albert Road", "Keep left at the Market Road exit towards
 * Newmarket".
 *
 * Put together from what the planner knows of the road, so every kind of turn reads the same
 * way. Where the map names no road, the turn says only which way it goes. `arrivingAt` is the
 * name of the stop the leg ends at, for the last turn.
 */
export function turnSaid(turn: Turn, arrivingAt: string): string {
  switch (turn.turn) {
    case 'setOff':
      return (turn.heading === null ? 'Set off' : `Head ${compassPoint(turn.heading)}`) + on(turn)
    case 'continue':
      return along('Continue', turn)
    case 'slightLeft':
      return along('Bear left', turn)
    case 'left':
      return along('Turn left', turn)
    case 'sharpLeft':
      return along('Turn sharp left', turn)
    case 'slightRight':
      return along('Bear right', turn)
    case 'right':
      return along('Turn right', turn)
    case 'sharpRight':
      return along('Turn sharp right', turn)
    case 'keepLeft':
      return along('Keep left', turn)
    case 'keepRight':
      return along('Keep right', turn)
    case 'uTurn':
      return along('Make a U-turn', turn)
    case 'roundabout':
      return along(
        turn.roundaboutExit === null
          ? 'Go into the roundabout'
          : `At the roundabout, take the ${ordinal(turn.roundaboutExit)} exit`,
        turn,
      )
    case 'leaveRoundabout':
      return along('Leave the roundabout', turn)
    case 'ferry': {
      const route = turn.street ?? turn.follows
      return route === null ? 'Take the ferry' : `Take the ferry, ${route}`
    }
    case 'arrive':
      return `Arrive at ${arrivingAt}`
  }
}

/**
 * How far to drive after a turn, before the next one: "140 m", "1.3 km". To the nearest 10 m
 * under a kilometre, since the roads are drawn no finer, and to a tenth of a kilometre above.
 * Nothing on arriving, when there is nothing left to drive.
 */
export function turnDistanceSaid(metres: number): string {
  if (metres <= 0) {
    return ''
  }
  const tens = Math.max(10, Math.round(metres / 10) * 10)
  return tens < 1000 ? `${tens} m` : `${(metres / 1000).toFixed(1)} km`
}

/**
 * How many turns a leg takes, for the line that opens them: "12 turns". Setting off and arriving
 * are steps of the list and not turns.
 */
export function turnsCounted(turns: Turn[]): string {
  const count = turns.filter((turn) => turn.turn !== 'setOff' && turn.turn !== 'arrive').length
  if (count === 0) {
    return 'No turns'
  }
  return count === 1 ? '1 turn' : `${count} turns`
}

const COMPASS_POINTS = ['north', 'north-east', 'east', 'south-east', 'south', 'south-west', 'west', 'north-west']

/** A bearing in degrees as one of the eight points of the compass. */
export function compassPoint(degrees: number): string {
  const eighths = Math.round((((degrees % 360) + 360) % 360) / 45) % 8
  return COMPASS_POINTS[eighths]
}

/** The road the drive sets off on, or the one it then follows, when the map names either. */
function on(turn: Turn): string {
  if (turn.street !== null) {
    return ` on ${turn.street}`
  }
  return turn.follows === null ? '' : `, then follow ${turn.follows}`
}

/**
 * A turn's first words, then the motorway exit it takes, the road it goes onto, or the one it
 * then follows when the map does not name the first, and where the signs point.
 */
function along(start: string, turn: Turn): string {
  const exit = turn.motorwayExit === null ? '' : ` at the ${turn.motorwayExit} exit`
  const onto =
    turn.street !== null ? ` onto ${turn.street}` : turn.follows !== null ? `, then follow ${turn.follows}` : ''
  const towards = turn.towards === null ? '' : ` towards ${turn.towards}`
  return `${start}${exit}${onto}${towards}`
}

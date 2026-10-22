import { describe, expect, it } from 'vitest'
import { compassPoint, turnDistanceSaid, turnSaid, turnsCounted } from './turns'
import type { Turn } from './types'

/** A turn with nothing said of it but which way it goes, and whatever else is given. */
function aTurn(more: Partial<Turn>): Turn {
  return {
    turn: 'left',
    street: null,
    follows: null,
    towards: null,
    motorwayExit: null,
    roundaboutExit: null,
    heading: null,
    distanceMetres: 100,
    ...more,
  }
}

describe('saying a turn', () => {
  it('sets off on the bearing given, on the road when the map names it', () => {
    expect(turnSaid(aTurn({ turn: 'setOff', heading: 346.5 }), 'Museum')).toBe('Head north')
    expect(turnSaid(aTurn({ turn: 'setOff', heading: 320.7, street: 'The Strand' }), 'Devonport')).toBe(
      'Head north-west on The Strand',
    )
  })

  it('names the road a turn goes onto', () => {
    expect(turnSaid(aTurn({ turn: 'left', street: 'Te Putu Avenue' }), 'Museum')).toBe('Turn left onto Te Putu Avenue')
    expect(turnSaid(aTurn({ turn: 'sharpRight', street: 'Percy Street' }), 'Devonport')).toBe(
      'Turn sharp right onto Percy Street',
    )
    expect(turnSaid(aTurn({ turn: 'continue', street: 'Quadrant Road' }), 'Airport')).toBe('Continue onto Quadrant Road')
  })

  it('bears left or right for a slight turn', () => {
    expect(turnSaid(aTurn({ turn: 'slightLeft', street: 'Museum Circuit' }), 'Museum')).toBe(
      'Bear left onto Museum Circuit',
    )
    expect(turnSaid(aTurn({ turn: 'slightRight', street: 'Blomfield Spa' }), 'Devonport')).toBe(
      'Bear right onto Blomfield Spa',
    )
  })

  it('says only which way a turn goes when the map names no road', () => {
    expect(turnSaid(aTurn({ turn: 'right' }), 'Airport')).toBe('Turn right')
    expect(turnSaid(aTurn({ turn: 'keepLeft' }), 'Museum')).toBe('Keep left')
  })

  it('names the motorway exit and where the signs point', () => {
    expect(
      turnSaid(aTurn({ turn: 'keepLeft', motorwayExit: 'Gillies Avenue', towards: 'Airport' }), 'Airport'),
    ).toBe('Keep left at the Gillies Avenue exit towards Airport')
    expect(
      turnSaid(aTurn({ turn: 'keepRight', street: 'George Bolt Memorial Drive', towards: 'Airport' }), 'Airport'),
    ).toBe('Keep right onto George Bolt Memorial Drive towards Airport')
    expect(turnSaid(aTurn({ turn: 'keepLeft', towards: 'Manukau, Hamilton' }), 'Airport')).toBe(
      'Keep left towards Manukau, Hamilton',
    )
  })

  it('names the road an unnamed turn leads onto, such as the motorway after its on-ramp', () => {
    expect(turnSaid(aTurn({ turn: 'sharpRight', follows: 'Auckland Southern Motorway' }), 'Devonport')).toBe(
      'Turn sharp right, then follow Auckland Southern Motorway',
    )
    expect(
      turnSaid(aTurn({ turn: 'keepLeft', follows: 'Auckland Southern Motorway', towards: 'Manukau, Hamilton' }), 'Airport'),
    ).toBe('Keep left, then follow Auckland Southern Motorway towards Manukau, Hamilton')
    expect(turnSaid(aTurn({ turn: 'setOff', heading: 0, follows: 'Mount Wellington Highway' }), 'Museum')).toBe(
      'Head north, then follow Mount Wellington Highway',
    )
  })

  it('goes onto the road a turn names, and not the one it then follows', () => {
    expect(turnSaid(aTurn({ turn: 'left', street: 'Te Putu Avenue', follows: 'Somewhere Else' }), 'Museum')).toBe(
      'Turn left onto Te Putu Avenue',
    )
  })

  it('counts the exits of a roundabout', () => {
    expect(turnSaid(aTurn({ turn: 'roundabout', roundaboutExit: 2, street: 'Albert Road' }), 'Devonport')).toBe(
      'At the roundabout, take the 2nd exit onto Albert Road',
    )
    expect(turnSaid(aTurn({ turn: 'roundabout', roundaboutExit: 3 }), 'Airport')).toBe(
      'At the roundabout, take the 3rd exit',
    )
  })

  it('goes into a roundabout the drive ends inside, and leaves one it starts inside', () => {
    expect(turnSaid(aTurn({ turn: 'roundabout' }), 'Airport')).toBe('Go into the roundabout')
    expect(turnSaid(aTurn({ turn: 'leaveRoundabout', street: 'Queens Parade' }), 'Devonport')).toBe(
      'Leave the roundabout onto Queens Parade',
    )
  })

  it('says a U-turn and a ferry as a driver would', () => {
    expect(turnSaid(aTurn({ turn: 'uTurn' }), 'Museum')).toBe('Make a U-turn')
    expect(turnSaid(aTurn({ turn: 'ferry' }), 'Waiheke')).toBe('Take the ferry')
    expect(turnSaid(aTurn({ turn: 'ferry', street: 'Interislander' }), 'Picton')).toBe('Take the ferry, Interislander')
  })

  it('arrives at the stop the leg ends at', () => {
    expect(turnSaid(aTurn({ turn: 'arrive', distanceMetres: 0 }), 'Auckland War Memorial Museum')).toBe(
      'Arrive at Auckland War Memorial Museum',
    )
  })
})

describe('saying how far to drive after a turn', () => {
  it('gives metres to the nearest 10 under a kilometre', () => {
    expect(turnDistanceSaid(68)).toBe('70 m')
    expect(turnDistanceSaid(140)).toBe('140 m')
    expect(turnDistanceSaid(993)).toBe('990 m')
  })

  it('gives kilometres to a tenth above', () => {
    expect(turnDistanceSaid(1067)).toBe('1.1 km')
    expect(turnDistanceSaid(6255)).toBe('6.3 km')
    expect(turnDistanceSaid(998)).toBe('1.0 km')
  })

  it('says a short stretch is 10 m and not nought', () => {
    expect(turnDistanceSaid(4)).toBe('10 m')
  })

  it('says nothing on arriving', () => {
    expect(turnDistanceSaid(0)).toBe('')
  })
})

describe('counting the turns of a leg', () => {
  const setOff = aTurn({ turn: 'setOff', heading: 0 })
  const arrive = aTurn({ turn: 'arrive', distanceMetres: 0 })

  it('counts neither setting off nor arriving', () => {
    expect(turnsCounted([setOff, aTurn({ turn: 'left' }), aTurn({ turn: 'roundabout' }), arrive])).toBe('2 turns')
    expect(turnsCounted([setOff, aTurn({ turn: 'right' }), arrive])).toBe('1 turn')
  })

  it('says so when the drive takes no turn at all', () => {
    expect(turnsCounted([setOff, arrive])).toBe('No turns')
  })
})

describe('naming a bearing', () => {
  it('names the nearest of the eight points of the compass', () => {
    expect(compassPoint(0)).toBe('north')
    expect(compassPoint(346.5)).toBe('north')
    expect(compassPoint(330.16)).toBe('north-west')
    expect(compassPoint(90)).toBe('east')
    expect(compassPoint(203.9)).toBe('south-west')
    expect(compassPoint(360)).toBe('north')
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { followPosition, type DevicePosition, type LocationProblem } from './location'

type Answer = (found: { coords: { latitude: number; longitude: number; accuracy: number } }) => void
type Failure = (error: { code: number; PERMISSION_DENIED: number }) => void

/** Stands in for the browser. A test says what it answers, and when. */
function aBrowser() {
  let answer: Answer = () => {}
  let failure: Failure = () => {}
  const clearWatch = vi.fn()
  const watchPosition = vi.fn((onAnswer: Answer, onFailure: Failure, _options: unknown) => {
    answer = onAnswer
    failure = onFailure
    return 7
  })
  vi.stubGlobal('navigator', { geolocation: { watchPosition, clearWatch } })

  return {
    watchPosition,
    clearWatch,
    /** The browser reports a position it rates as good to this many metres. */
    says: (accuracy: number, latitude = -36.8688, longitude = 174.7817) =>
      answer({ coords: { latitude, longitude, accuracy } }),
    refuses: () => failure({ code: 1, PERMISSION_DENIED: 1 }),
    cannotTell: () => failure({ code: 2, PERMISSION_DENIED: 1 }),
    timesOut: () => failure({ code: 3, PERMISSION_DENIED: 1 }),
  }
}

/** Collects what the app was told. */
function aListener() {
  const answers: { accuracyMetres: number; isFirst: boolean }[] = []
  const problems: LocationProblem[] = []
  return {
    answers,
    problems,
    onAnswer: (position: DevicePosition, isFirst: boolean) =>
      answers.push({ accuracyMetres: position.accuracyMetres, isFirst }),
    onProblem: (problem: LocationProblem) => problems.push(problem),
  }
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('asking the browser where the device is', () => {
  it('passes on the first answer, and how sure the browser is of it', () => {
    const browser = aBrowser()
    const positions: DevicePosition[] = []

    followPosition((position) => positions.push(position), () => {})
    browser.says(93, -36.86877, 174.78173)

    expect(positions).toEqual([{ latitude: -36.86877, longitude: 174.78173, accuracyMetres: 93 }])
  })

  it('asks for the best the device can do', () => {
    const browser = aBrowser()

    followPosition(() => {}, () => {})

    expect(browser.watchPosition.mock.calls[0][2]).toEqual({
      enableHighAccuracy: true,
      timeout: 10_000,
      maximumAge: 60_000,
    })
  })
})

describe('listening for a better answer', () => {
  it('passes on a later answer the browser rates as better', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.says(900)
    browser.says(65)
    browser.says(30)

    expect(app.answers).toEqual([
      { accuracyMetres: 900, isFirst: true },
      { accuracyMetres: 65, isFirst: false },
      { accuracyMetres: 30, isFirst: false },
    ])
  })

  it('drops a later answer the browser rates the same or worse', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.says(65)
    browser.says(65)
    browser.says(140)

    expect(app.answers).toEqual([{ accuracyMetres: 65, isFirst: true }])
  })

  it('measures a later answer against the best so far, not the latest', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.says(65)
    browser.says(140)
    browser.says(90)

    expect(app.answers).toHaveLength(1)
  })

  it('stops once an answer is within 20 metres', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.says(65)
    expect(browser.clearWatch).not.toHaveBeenCalled()
    browser.says(20)
    expect(browser.clearWatch).toHaveBeenCalledWith(7)

    browser.says(5)
    expect(app.answers.map((answer) => answer.accuracyMetres)).toEqual([65, 20])
  })

  it('stops at once when the first answer is already that good', () => {
    const browser = aBrowser()

    followPosition(() => {}, () => {})
    browser.says(8)

    expect(browser.clearWatch).toHaveBeenCalledWith(7)
  })

  it('stops half a minute after the first answer', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.says(65)
    vi.advanceTimersByTime(29_999)
    expect(browser.clearWatch).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1)
    expect(browser.clearWatch).toHaveBeenCalledWith(7)

    browser.says(30)
    expect(app.answers).toHaveLength(1)
  })

  it('does not start the half minute until there is an answer', () => {
    const browser = aBrowser()

    followPosition(() => {}, () => {})
    vi.advanceTimersByTime(60_000)

    expect(browser.clearWatch).not.toHaveBeenCalled()
  })

  it('stops when told to, and passes on nothing after that', () => {
    const browser = aBrowser()
    const app = aListener()

    const stop = followPosition(app.onAnswer, app.onProblem)
    browser.says(65)
    stop()
    browser.says(30)
    browser.refuses()

    expect(browser.clearWatch).toHaveBeenCalledWith(7)
    expect(app.answers).toHaveLength(1)
    expect(app.problems).toEqual([])
  })

  it('can be told to stop twice without harm', () => {
    const browser = aBrowser()

    const stop = followPosition(() => {}, () => {})
    stop()
    stop()

    expect(browser.clearWatch).toHaveBeenCalledTimes(1)
  })
})

describe('when the browser cannot or will not say', () => {
  it('reports a refusal as a refusal, and stops', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.refuses()

    expect(app.problems).toEqual(['refused'])
    expect(browser.clearWatch).toHaveBeenCalledWith(7)
  })

  it('reports every other failure as unavailable', () => {
    for (const fail of ['cannotTell', 'timesOut'] as const) {
      const browser = aBrowser()
      const app = aListener()

      followPosition(app.onAnswer, app.onProblem)
      browser[fail]()

      expect(app.problems).toEqual(['unavailable'])
    }
  })

  it('says nothing about a hiccup once it has an answer', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.says(65)
    browser.timesOut()
    browser.says(30)

    expect(app.problems).toEqual([])
    expect(app.answers.map((answer) => answer.accuracyMetres)).toEqual([65, 30])
  })

  it('still reports a refusal after an answer, because permission can be taken back', () => {
    const browser = aBrowser()
    const app = aListener()

    followPosition(app.onAnswer, app.onProblem)
    browser.says(65)
    browser.refuses()

    expect(app.problems).toEqual(['refused'])
  })

  it('reports unavailable when the browser cannot be asked at all', () => {
    vi.stubGlobal('navigator', {})
    const app = aListener()

    const stop = followPosition(app.onAnswer, app.onProblem)

    expect(app.problems).toEqual(['unavailable'])
    expect(() => stop()).not.toThrow()
  })
})

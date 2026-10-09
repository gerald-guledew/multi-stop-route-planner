import { afterEach, describe, expect, it, vi } from 'vitest'
import { currentPosition } from './location'

type Answer = (found: { coords: { latitude: number; longitude: number; accuracy: number } }) => void
type Failure = (error: { code: number; PERMISSION_DENIED: number }) => void

/** Stands in for the browser, which answers through one of two callbacks. */
function browserThat(respond: (answer: Answer, failure: Failure, options: unknown) => void) {
  vi.stubGlobal('navigator', { geolocation: { getCurrentPosition: respond } })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('asking the browser where the device is', () => {
  it('passes on the position and how sure the browser is of it', async () => {
    browserThat((answer) =>
      answer({ coords: { latitude: -36.86877, longitude: 174.78173, accuracy: 93 } }),
    )

    await expect(currentPosition()).resolves.toEqual({
      latitude: -36.86877,
      longitude: 174.78173,
      accuracyMetres: 93,
    })
  })

  it('reports a refusal as a refusal', async () => {
    browserThat((_, failure) => failure({ code: 1, PERMISSION_DENIED: 1 }))

    await expect(currentPosition()).rejects.toBe('refused')
  })

  it.each([
    [2, 'no position could be worked out'],
    [3, 'it took too long'],
  ])('reports every other failure as unavailable: code %d, %s', async (code) => {
    browserThat((_, failure) => failure({ code, PERMISSION_DENIED: 1 }))

    await expect(currentPosition()).rejects.toBe('unavailable')
  })

  it('reports unavailable when the browser cannot be asked at all', async () => {
    vi.stubGlobal('navigator', {})

    await expect(currentPosition()).rejects.toBe('unavailable')
  })

  it('asks for the best the device can do, and gives up after ten seconds', async () => {
    let asked: unknown
    browserThat((answer, _, options) => {
      asked = options
      answer({ coords: { latitude: 0, longitude: 0, accuracy: 5 } })
    })

    await currentPosition()

    expect(asked).toEqual({ enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 })
  })
})

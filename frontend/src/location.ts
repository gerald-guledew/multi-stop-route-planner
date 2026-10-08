export interface Position {
  latitude: number
  longitude: number
}

export type LocationProblem = 'refused' | 'unavailable'

/**
 * Where the browser says this device is.
 *
 * Wrapped in a promise because the browser's own call takes two callbacks, and reports three
 * kinds of failure the rest of the app has no use for. Two are enough: the person said no, or
 * the device could not tell.
 *
 * Browsers only answer on a secure page. That means https, or localhost while developing.
 */
export function currentPosition(): Promise<Position> {
  return new Promise((resolve, reject: (problem: LocationProblem) => void) => {
    if (!('geolocation' in navigator)) {
      reject('unavailable')
      return
    }

    navigator.geolocation.getCurrentPosition(
      (found) => resolve({ latitude: found.coords.latitude, longitude: found.coords.longitude }),
      (error) => reject(error.code === error.PERMISSION_DENIED ? 'refused' : 'unavailable'),
      // A phone's GPS is worth waiting a few seconds for. A fix from the last minute will do.
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    )
  })
}

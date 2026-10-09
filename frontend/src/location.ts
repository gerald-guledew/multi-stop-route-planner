export interface Position {
  latitude: number
  longitude: number
}

/**
 * Where this device is, as far as the app knows. It begins as the browser's guess, which comes
 * with the browser's own idea of how far out it could be.
 *
 * A phone with GPS is usually within a few metres. A laptop has no GPS and works from the Wi-Fi
 * networks it can see, which can put it a street away.
 */
export interface DevicePosition extends Position {
  /**
   * The browser's own estimate of its error, in metres. An estimate, not a limit: a laptop that
   * said 90 metres was found 130 metres out.
   */
  accuracyMetres: number
  /** Set once the person has dragged the guess to where they really are. Then nothing is in doubt. */
  correctedByHand?: boolean
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
export function currentPosition(): Promise<DevicePosition> {
  return new Promise((resolve, reject: (problem: LocationProblem) => void) => {
    if (!('geolocation' in navigator)) {
      reject('unavailable')
      return
    }

    navigator.geolocation.getCurrentPosition(
      (found) =>
        resolve({
          latitude: found.coords.latitude,
          longitude: found.coords.longitude,
          accuracyMetres: found.coords.accuracy,
        }),
      (error) => reject(error.code === error.PERMISSION_DENIED ? 'refused' : 'unavailable'),
      // A phone's GPS is worth waiting a few seconds for. A fix from the last minute will do.
      { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
    )
  })
}

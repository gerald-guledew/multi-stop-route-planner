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

/** How long to go on listening after the first answer. A phone's GPS has settled by then. */
const LISTEN_FOR_MS = 30_000

/** Close enough that listening for better is not worth a phone's battery. */
const GOOD_ENOUGH_METRES = 20

/**
 * Asks the browser where this device is, then goes on listening for a while.
 *
 * The first answer is often the roughest. A phone answers at once from the mobile network or
 * Wi-Fi, and comes back seconds later with GPS. So `onAnswer` is called for the first answer,
 * and again for each later one that the browser itself rates as better. An answer it rates the
 * same or worse is dropped, which keeps a pin from wandering between equally rough guesses.
 *
 * Listening stops by itself half a minute after the first answer, or sooner once an answer
 * is within 20 metres. It is not tracking: nothing follows the device after that.
 *
 * `onProblem` is called if the browser refuses, or cannot answer at all. The browser reports
 * three kinds of failure and the rest of the app has no use for more than two: the person said
 * no, or the device could not tell. Once there is an answer, only a refusal is still worth
 * reporting, because permission can be taken back.
 *
 * Browsers only answer on a secure page. That means https, or localhost while developing.
 *
 * @returns a function that stops the listening early
 */
export function followPosition(
  onAnswer: (position: DevicePosition, isFirst: boolean) => void,
  onProblem: (problem: LocationProblem) => void,
): () => void {
  if (!('geolocation' in navigator)) {
    onProblem('unavailable')
    return () => {}
  }

  let best: DevicePosition | null = null
  let stopped = false
  let watch: number | undefined
  let giveUp: ReturnType<typeof setTimeout> | undefined

  function stop() {
    if (stopped) {
      return
    }
    stopped = true
    clearTimeout(giveUp)
    if (watch !== undefined) {
      navigator.geolocation.clearWatch(watch)
    }
  }

  watch = navigator.geolocation.watchPosition(
    (found) => {
      if (stopped) {
        return
      }
      const answer: DevicePosition = {
        latitude: found.coords.latitude,
        longitude: found.coords.longitude,
        accuracyMetres: found.coords.accuracy,
      }

      if (best === null) {
        best = answer
        giveUp = setTimeout(stop, LISTEN_FOR_MS)
        onAnswer(answer, true)
      } else if (answer.accuracyMetres < best.accuracyMetres) {
        best = answer
        onAnswer(answer, false)
      }

      if (best.accuracyMetres <= GOOD_ENOUGH_METRES) {
        stop()
      }
    },
    (error) => {
      if (stopped) {
        return
      }
      const refused = error.code === error.PERMISSION_DENIED
      // With an answer in hand, a later hiccup changes nothing. The answer still stands.
      if (best !== null && !refused) {
        return
      }
      stop()
      onProblem(refused ? 'refused' : 'unavailable')
    },
    // A phone's GPS is worth waiting a few seconds for. A fix from the last minute will do
    // as a first answer.
    { enableHighAccuracy: true, timeout: 10_000, maximumAge: 60_000 },
  )

  // A browser answers later, never during the call above. A stand-in in a test might not.
  if (stopped) {
    navigator.geolocation.clearWatch(watch)
  }

  return stop
}

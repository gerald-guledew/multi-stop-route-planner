/**
 * How the order chosen compares with the order the stops were entered in, as a label and a
 * figure: "Saved", "0.56 km".
 *
 * An order chosen for distance is never longer than the one entered. An order chosen for time
 * or fuel can be, and "Saved -0.43 km" would be an odd thing to read. So a longer order is
 * called longer.
 */
export interface Saving {
  label: string
  figure: string
  /** True when there is a saving to be pleased about. */
  good: boolean
}

/** Half of the last digit shown. Both distances arrive rounded to a hundredth of a kilometre. */
const NO_DIFFERENCE_KM = 0.005

export function savingSaid(enteredOrderKm: number, thisOrderKm: number): Saving {
  const saved = enteredOrderKm - thisOrderKm

  if (saved <= -NO_DIFFERENCE_KM) {
    return { label: 'Longer than you clicked by', figure: `${(-saved).toFixed(2)} km`, good: false }
  }
  return {
    label: 'Saved',
    figure: `${Math.max(saved, 0).toFixed(2)} km`,
    good: saved >= NO_DIFFERENCE_KM,
  }
}

import type { FigureFrom } from './types'

/**
 * An amount of fuel said the way a pump shows it: "2.8 L". To a tenth of a litre, because the
 * figure behind it is an average, the dashboard's or the register's, and anything finer would
 * claim more than is known.
 *
 * A short drive takes a little, not none, so it is "under 0.1 L" rather than "0.0 L".
 */
export function litresSaid(litres: number): string {
  if (litres < 0.05) {
    return 'under 0.1 L'
  }
  return `${litres.toFixed(1)} L`
}

/**
 * What the fuel of a plan rests on, said under it. A dashboard average holds the hills and
 * town driving a car usually does. A figure from the register comes from a laboratory test,
 * and real driving uses more, so the plan says so.
 */
export function fuelNote(figureFrom: FigureFrom): string {
  const figure =
    figureFrom === 'register'
      ? "Fuel is your car's lab figure for every kilometre, and real driving uses more."
      : "Fuel is your vehicle's figure for every kilometre."
  return `${figure} Hills and speed do not change it yet.`
}

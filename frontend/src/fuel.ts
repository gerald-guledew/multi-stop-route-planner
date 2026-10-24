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
 * What the fuel of a plan rests on, said under it: the dashboard's figure, or the official one
 * from the register when none was typed, whether it was taken higher in town than on the open
 * road, and whether the hills were counted.
 */
export function fuelNote(figureFrom: FigureFrom, countsHills: boolean, countsSpeed: boolean): string {
  const figure = figureFrom === 'register' ? "your car's official figure" : "your vehicle's figure"
  const byRoad = countsSpeed ? `${figure}, higher in town and lower on the open road` : `${figure} for every kilometre`
  if (countsHills) {
    return `Fuel is ${byRoad}, with more uphill and less downhill.`
  }
  return countsSpeed ? `Fuel is ${byRoad}. Hills do not change it.` : `Fuel is ${byRoad}. Hills and speed do not change it.`
}

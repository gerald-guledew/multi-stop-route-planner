/**
 * An amount of fuel said the way a pump shows it: "2.8 L". To a tenth of a litre, because the
 * figure behind it is a dashboard average and anything finer would claim more than is known.
 *
 * A short drive takes a little, not none, so it is "under 0.1 L" rather than "0.0 L".
 */
export function litresSaid(litres: number): string {
  if (litres < 0.05) {
    return 'under 0.1 L'
  }
  return `${litres.toFixed(1)} L`
}

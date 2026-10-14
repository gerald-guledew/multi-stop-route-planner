import type { Vehicle, VehicleSize } from './types'

/** The same limits as the API's. A figure outside them is refused here, before it is sent. */
export const LEAST_LITRES_PER_100_KM = 2
export const MOST_LITRES_PER_100_KM = 30

/** The sizes in the order a list shows them, each as a driver would say it. */
export const SIZES: { size: VehicleSize; says: string }[] = [
  { size: 'small', says: 'Small car, such as a city hatchback' },
  { size: 'medium', says: 'Medium car, such as a family hatchback or sedan' },
  { size: 'large', says: 'Large car, SUV or people mover' },
]

const SIZE_IN_SHORT: Record<VehicleSize, string> = {
  small: 'Small car',
  medium: 'Medium car',
  large: 'Large car or SUV',
}

/** The vehicle in a few words, such as "Medium car, 7.5 L/100 km". */
export function vehicleSaid(vehicle: Vehicle): string {
  return `${SIZE_IN_SHORT[vehicle.size]}, ${vehicle.litresPer100Km} L/100 km`
}

/**
 * Reads the fuel use as it was typed. A comma is taken for a decimal point, as some keyboards
 * offer only that. Says what is wrong in words when it cannot be used.
 */
export function litresFrom(typed: string): { litres: number } | { problem: string } {
  const tidy = typed.trim().replace(',', '.')
  if (tidy === '') {
    return { problem: 'Type the fuel use your dashboard shows, such as 7.5.' }
  }
  // Number() takes "1e1" and " 7 " as numbers too. Only digits with one point are a figure.
  if (!/^\d+(\.\d+)?$/.test(tidy)) {
    return { problem: 'Type the fuel use as a number, such as 7.5.' }
  }
  const litres = Number(tidy)
  if (litres < LEAST_LITRES_PER_100_KM || litres > MOST_LITRES_PER_100_KM) {
    return {
      problem: `A petrol car uses between ${LEAST_LITRES_PER_100_KM} and ${MOST_LITRES_PER_100_KM} L/100 km.`,
    }
  }
  return { litres }
}

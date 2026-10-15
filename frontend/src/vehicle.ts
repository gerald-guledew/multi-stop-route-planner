import type { Car, CarVariant, Vehicle, VehicleSize, VehicleToSave } from './types'

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

/** An engine size as it is said, always with its tenth: "1.2 L", "2.0 L". */
export function engineSaid(litres: number): string {
  return `${litres.toFixed(1)} L`
}

/** A car as the register describes it: "2018 SUZUKI SWIFT 1.2 L". */
export function carSaid(car: Car): string {
  return `${car.year} ${car.make} ${car.model} ${engineSaid(car.engineLitres)}`
}

/**
 * The vehicle in a few words: "Medium car, 7.5 L/100 km" when entered by hand. A car found in
 * the register is named, and its figure called the official one unless it is the dashboard's.
 */
export function vehicleSaid(vehicle: Vehicle): string {
  if (vehicle.car === null) {
    return `${SIZE_IN_SHORT[vehicle.size ?? 'medium']}, ${vehicle.litresPer100Km} L/100 km`
  }
  const figure = vehicle.figureFrom === 'register' ? ' (official figure)' : ''
  return `${carSaid(vehicle.car)}, ${vehicle.litresPer100Km} L/100 km${figure}`
}

/** Tells the engines of one year apart, as a list's value. */
export function variantKey(found: CarVariant): string {
  return String(found.car.engineLitres)
}

/**
 * Which of a year's engines to show picked when the list arrives: the one saved, when the
 * saved car is being changed, or the only one there is. None otherwise, so the driver picks.
 */
export function variantToPick(variants: CarVariant[], saved: Car | null): CarVariant | null {
  const theSavedOne = variants.find(
    (found) =>
      saved !== null &&
      found.car.make === saved.make &&
      found.car.model === saved.model &&
      found.car.year === saved.year &&
      found.car.engineLitres === saved.engineLitres,
  )
  return theSavedOne ?? (variants.length === 1 ? variants[0] : null)
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

/** The same, for a car found in the register, where the dashboard's figure may be left out. */
export function dashboardLitresFrom(typed: string): { litres: number | null } | { problem: string } {
  return typed.trim() === '' ? { litres: null } : litresFrom(typed)
}

/** The two ways to give the vehicle: found in NZTA's register, or, for a car it does not list, entered by hand. */
export type Way = 'find' | 'enter'

/**
 * What the form saves, from what was picked and typed the way that is chosen. Entered by hand,
 * the dashboard's figure is needed. Found in the register, the car is needed down to its
 * engine, and the dashboard's figure only if one was typed.
 */
export function vehicleToSave(
  way: Way,
  given: { size: VehicleSize; found: CarVariant | null; typed: string },
): VehicleToSave | { problem: string } {
  if (way === 'enter') {
    const read = litresFrom(given.typed)
    return 'problem' in read ? read : { size: given.size, litresPer100Km: read.litres }
  }
  if (given.found === null) {
    return { problem: 'Pick your car down to its engine, or enter it yourself.' }
  }
  const read = dashboardLitresFrom(given.typed)
  return 'problem' in read ? read : { car: given.found.car, litresPer100Km: read.litres }
}

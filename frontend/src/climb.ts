/**
 * How much a drive goes up and how much it comes down, said in words: "120 m up, 95 m down".
 *
 * Both are added along the road, so a drive over a hill to a place at the same height says so
 * twice. Null when the roads were read without their heights, and then nothing is shown.
 *
 * A level drive is "0 m up, 0 m down". That is an answer, and not the same as not knowing.
 */
export function climbSaid(climbMetres: number | null, descentMetres: number | null): string | null {
  if (climbMetres === null || descentMetres === null) {
    return null
  }
  return `${metres(climbMetres)} up, ${metres(descentMetres)} down`
}

/** A comma in the thousands, the way a road sign for a pass has it: "1,076 m". */
export function metres(howMany: number): string {
  return `${howMany.toLocaleString('en-NZ')} m`
}

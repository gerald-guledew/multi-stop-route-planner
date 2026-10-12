import { useEffect, useState } from 'react'

/**
 * A screen this narrow is laid out the way a phone needs: the map fills it, the search box
 * floats over the top, and the trip is a panel that comes up from the bottom. The same width
 * is written in styles.css, and the two have to agree.
 */
const NARROW = '(max-width: 800px)'

/** How much of the top of the map the floating search box covers on a narrow screen. */
const SEARCH_BOX_COVERS = 56

export function isNarrowScreen(): boolean {
  return window.matchMedia(NARROW).matches
}

/**
 * How far down from the top of the map a pin has to be to show. Framing a set of pins without
 * allowing for this can leave the topmost one under the search box.
 */
export function coveredAtTheTopOfTheMap(): number {
  return isNarrowScreen() ? SEARCH_BOX_COVERS : 0
}

/** Whether the screen is narrow right now, kept up to date as it is turned or resized. */
export function useNarrowScreen(): boolean {
  const [narrow, setNarrow] = useState(isNarrowScreen)

  useEffect(() => {
    const query = window.matchMedia(NARROW)
    const update = () => setNarrow(query.matches)
    query.addEventListener('change', update)
    return () => query.removeEventListener('change', update)
  }, [])

  return narrow
}

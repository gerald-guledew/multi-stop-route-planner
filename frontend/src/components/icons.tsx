import type { ReactNode } from 'react'
import type { PlaceKindName } from '../kindsOfPlace'

/**
 * The small drawings on the buttons. Drawn here, a line or two each, because a few shapes do
 * not need a library. A drawing says nothing to a screen reader, so each one is hidden from it
 * and the button around it carries the words.
 */
function Icon({ children }: { children: ReactNode }) {
  return (
    <svg
      className="icon"
      viewBox="0 0 20 20"
      width="20"
      height="20"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {children}
    </svg>
  )
}

export function UpIcon() {
  return (
    <Icon>
      <path d="M10 16V4M5 9l5-5 5 5" />
    </Icon>
  )
}

export function DownIcon() {
  return (
    <Icon>
      <path d="M10 4v12M5 11l5 5 5-5" />
    </Icon>
  )
}

export function ChevronLeftIcon() {
  return (
    <Icon>
      <path d="M12 4l-6 6 6 6" />
    </Icon>
  )
}

export function ChevronRightIcon() {
  return (
    <Icon>
      <path d="M8 4l6 6-6 6" />
    </Icon>
  )
}

export function PlusIcon() {
  return (
    <Icon>
      <path d="M10 4v12M4 10h12" />
    </Icon>
  )
}

export function CrossIcon() {
  return (
    <Icon>
      <path d="M5 5l10 10M15 5 5 15" />
    </Icon>
  )
}

/** A padlock: open while the stop is free to move, shut and filled in once it is kept. */
export function LockIcon({ shut }: { shut: boolean }) {
  return (
    <Icon>
      <rect x="4" y="9" width="12" height="8" rx="2" fill={shut ? 'currentColor' : 'none'} />
      <path d={shut ? 'M7 9V6.5a3 3 0 0 1 6 0V9' : 'M7 9V6.5a3 3 0 0 1 6 0'} />
    </Icon>
  )
}

/** Two links of a chain: a stop-over held to the two stops either side of it. */
export function LinkIcon() {
  return (
    <Icon>
      <path d="m8.5 11.5 3-3" />
      <path d="M9.5 6.5 11 5a3.2 3.2 0 0 1 4.5 4.5L14 11" />
      <path d="M10.5 13.5 9 15a3.2 3.2 0 0 1-4.5-4.5L6 9" />
    </Icon>
  )
}

/** A bookmark, filled in once the place is saved. */
export function BookmarkIcon({ filled }: { filled: boolean }) {
  return (
    <Icon>
      <path d="M6 3.5h8v13l-4-3-4 3z" fill={filled ? 'currentColor' : 'none'} />
    </Icon>
  )
}

/** An arrow curling back to the left. */
export function UndoIcon() {
  return (
    <Icon>
      <path d="M7.5 4.5 4 8l3.5 3.5" />
      <path d="M4 8h7.5a4.5 4.5 0 0 1 0 9H9" />
    </Icon>
  )
}

/** The same arrow, curling on to the right. */
export function RedoIcon() {
  return (
    <Icon>
      <path d="M12.5 4.5 16 8l-3.5 3.5" />
      <path d="M16 8H8.5a4.5 4.5 0 0 0 0 9H11" />
    </Icon>
  )
}

/** One drawing for each kind of place. */
const KIND_DRAWINGS: Record<PlaceKindName, ReactNode> = {
  fuel: (
    <path d="M4 17V4.5A1.5 1.5 0 0 1 5.5 3h5A1.5 1.5 0 0 1 12 4.5V17M3 17h10M4 9h8M12 8h1.5A1.5 1.5 0 0 1 15 9.5V14a1.2 1.2 0 0 0 2.4 0V7.5L15 5" />
  ),
  food: <path d="M5 3v5a2 2 0 0 0 4 0V3M7 3v14M15 17V3c-2 1-3 3-3 6 0 1.5 1 2.5 3 2.5" />,
  cafe: (
    <path d="M4 8h10v4a5 5 0 0 1-10 0V8zM14 9h1.5a2 2 0 0 1 0 4H14M7 5.5c0-1 1-1 1-2.5M11 5.5c0-1 1-1 1-2.5" />
  ),
  toilets: (
    <>
      <circle cx="6" cy="4.5" r="1.4" />
      <path d="M4.2 8h3.6v4.5H7V17H5v-4.5h-.8z" />
      <circle cx="14" cy="4.5" r="1.4" />
      <path d="M14 7.5l2.6 6h-5.2zM13 13.5V17M15 13.5V17" />
    </>
  ),
  dairy: (
    <path d="M3 8l1.5-4h11L17 8M3 8v1.5a2.3 2.3 0 0 0 4.7 0 2.3 2.3 0 0 0 4.6 0 2.3 2.3 0 0 0 4.7 0V8zM4.5 11.5V17h11v-5.5M8.5 17v-3.5h3V17" />
  ),
  groceries: <path d="M3 8h14l-1.5 8.5h-11zM6 8l3-5M14 8l-3-5M8 11v3M12 11v3" />,
  beach: (
    <>
      <circle cx="13.5" cy="6" r="2.5" />
      <path d="M2.5 13.5c1.5 0 1.5-1 3-1s1.5 1 3 1 1.5-1 3-1 1.5 1 3 1 1.5-1 3-1M2.5 17c1.5 0 1.5-1 3-1s1.5 1 3 1 1.5-1 3-1 1.5 1 3 1 1.5-1 3-1" />
    </>
  ),
  park: (
    <path d="M10 17v-6M10 13l-2.5-2M10 11.5l2.5-2M10 3a4.5 4.5 0 0 1 4.3 5.8A3.2 3.2 0 0 1 13 15H7a3.2 3.2 0 0 1-1.3-6.2A4.5 4.5 0 0 1 10 3z" />
  ),
  lookout: <path d="M2.5 16.5L8 6.5l3.5 6 2-3 4 7zM6.4 9.4L8 11l2-1.5" />,
  tourist: (
    <>
      <path d="M3 7h3l1.5-2h5L14 7h3v9.5H3z" />
      <circle cx="10" cy="11.5" r="3" />
    </>
  ),
  zoo: (
    <>
      <circle cx="4.8" cy="9.2" r="1.4" />
      <circle cx="8.2" cy="5.8" r="1.4" />
      <circle cx="11.8" cy="5.8" r="1.4" />
      <circle cx="15.2" cy="9.2" r="1.4" />
      <path d="M10 9.5c-2 0-4 3-4 5a2 2 0 0 0 2.6 1.9 5 5 0 0 1 2.8 0A2 2 0 0 0 14 14.5c0-2-2-5-4-5z" />
    </>
  ),
  mall: <path d="M4.5 7h11l1 10h-13zM7 9V6a3 3 0 0 1 6 0v3" />,
}

export function KindIcon({ kind }: { kind: PlaceKindName }) {
  return <Icon>{KIND_DRAWINGS[kind]}</Icon>
}

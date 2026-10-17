import type { ReactNode } from 'react'

/**
 * The small drawings on the buttons. Drawn here, a line or two each, because seven shapes do
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

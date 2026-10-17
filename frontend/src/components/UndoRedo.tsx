import { RedoIcon, UndoIcon } from './icons'

interface Props {
  canUndo: boolean
  canRedo: boolean
  onUndo: () => void
  onRedo: () => void
}

/**
 * Undo and redo, over the corner of the map across from its zoom buttons, and drawn like them.
 * A stop added by mistake, or a pin dragged by a thumb that meant to move the map, is one press
 * from being put back.
 */
export default function UndoRedo({ canUndo, canRedo, onUndo, onRedo }: Props) {
  return (
    <div className="undo-redo">
      <button title="Undo" aria-label="Undo the last change to the trip" disabled={!canUndo} onClick={onUndo}>
        <UndoIcon />
      </button>
      <button title="Redo" aria-label="Redo the change undone last" disabled={!canRedo} onClick={onRedo}>
        <RedoIcon />
      </button>
    </div>
  )
}

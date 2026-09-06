import { useId } from 'react'
import { fieldLabelsForMode, formatMissingList } from '../lib/missingInputHint'
import { isReadyToSurprise, missingInputsForSurprise } from '../state/selectors'
import type { AlterEgoSession } from '../state/reducer'

/**
 * 009 Surprise Me — secondary action that fills every category with a
 * random pick and fires the same generation flow Generate does. Mirrors
 * {@link GenerateButton}'s accessibility pattern one-for-one (FR-912):
 *
 * <ul>
 *   <li>Native {@code <button type="button">} with a clear accessible
 *       name ("Surprise Me").</li>
 *   <li>Disabled when photo or name are missing, or while a generation
 *       is in flight (FR-902). {@code disabled} + {@code aria-disabled}
 *       carry the state to both sighted users and assistive tech.</li>
 *   <li>Disabled-state hint via {@code aria-describedby} → a live
 *       {@code role="status"} paragraph that names only the missing
 *       photo / name (FR-903 — never the five category grids).</li>
 *   <li>Keyboard activation via the native button contract (Enter /
 *       Space) — no extra wiring required.</li>
 * </ul>
 */

interface Props {
  session: AlterEgoSession
  isSubmitting: boolean
  onSurprise: () => void
}

export function SurpriseMeButton({ session, isSubmitting, onSurprise }: Props) {
  const hintId = useId()
  const missing = missingInputsForSurprise(session)
  const ready = isReadyToSurprise(session)
  const disabled = !ready || isSubmitting

  const hint =
    missing.length > 0
      ? `Still needed: ${formatMissingList(missing, fieldLabelsForMode(session.photoMode))}.`
      : isSubmitting
        ? 'Generating…'
        : null

  return (
    <div className="surprise-me-button">
      <button
        type="button"
        onClick={onSurprise}
        disabled={disabled}
        aria-disabled={disabled || undefined}
        aria-describedby={hint ? hintId : undefined}
      >
        Surprise Me
      </button>
      {hint ? (
        <p id={hintId} role="status" className="surprise-me-button__hint">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

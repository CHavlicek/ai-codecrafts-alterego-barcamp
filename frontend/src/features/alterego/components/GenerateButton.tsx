import { useId } from 'react'
import { fieldLabelsForMode, formatMissingList } from '../lib/missingInputHint'
import { isReadyToGenerate, missingInputs } from '../state/selectors'
import type { AlterEgoSession } from '../state/reducer'

/**
 * Generate button — gates on {@link isReadyToGenerate} per FR-009, and
 * when disabled, renders a visible + accessible hint of what's still
 * missing per FR-010.
 *
 * Stateless from the reducer's perspective: takes the session state and
 * an onSubmit callback. The parent (AlterEgoPage) wires this to
 * {@code useGenerateAlterEgo().submit} from T072.
 *
 * <p>009 T003 — label map + list formatter now live in
 * {@code ../lib/missingInputHint} so {@code SurpriseMeButton} can share
 * a single source of truth for the disabled-state hint copy.
 */

interface Props {
  session: AlterEgoSession
  isSubmitting: boolean
  onSubmit: () => void
}

export function GenerateButton({ session, isSubmitting, onSubmit }: Props) {
  const hintId = useId()
  const missing = missingInputs(session)
  const ready = isReadyToGenerate(session)
  const disabled = !ready || isSubmitting

  const hint = !ready
    ? `Still needed: ${formatMissingList(missing, fieldLabelsForMode(session.photoMode))}.`
    : isSubmitting
      ? 'Generating…'
      : null

  return (
    <div className="generate-button">
      <button
        type="button"
        onClick={onSubmit}
        disabled={disabled}
        aria-describedby={hint ? hintId : undefined}
      >
        Generate my alter ego
      </button>
      {hint ? (
        <p id={hintId} role="status" className="generate-button__hint">
          {hint}
        </p>
      ) : null}
    </div>
  )
}

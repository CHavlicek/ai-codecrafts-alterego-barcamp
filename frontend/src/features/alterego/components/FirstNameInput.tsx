import { useId, useState, type ChangeEvent, type FocusEvent } from 'react'
import type { PhotoMode } from '../types'
import {
  FIRST_NAME_ERROR_MESSAGE,
  validateFirstName,
  type FirstNameValidation,
} from '../validation/firstName'

interface Props {
  value: string
  onChange: (value: string) => void
  /**
   * 011 (group photos): photo composition mode. Drives the visible
   * label and the leading "First name" → "Group name" relabel applied
   * to the validator's error copy:
   *   - {@code 'single'} → label "First name", errors say "First name…"
   *   - {@code 'group'}  → label "Group name", errors say "Group name…"
   * The stored value, validation rules, and trim semantics are
   * identical in both modes — only the surface copy changes.
   */
  mode?: PhotoMode
}

/**
 * Controlled text input for the user-facing name. Live validation runs
 * on every render via {@link validateFirstName}; the inline error is
 * announced to assistive tech through {@code aria-describedby} +
 * {@code role="status"} on a polite live-region (per spec 011 input-
 * validation FR-1105 + US3).
 *
 * <p>011 (group photos) delta: in {@code group} mode the label reads
 * "Group name" and the leading "First name" in every error message is
 * rewritten to "Group name" so the copy stays consistent with the label.
 * The validator itself is mode-agnostic.
 */
export function FirstNameInput({ value, onChange, mode = 'single' }: Props) {
  const inputId = useId()
  const hintId = useId()
  const [touched, setTouched] = useState(false)

  const validation: FirstNameValidation = validateFirstName(value)
  // Surface "empty" only after the user has interacted with the field —
  // otherwise the field is red the moment the page loads. All other error
  // codes surface as soon as the value triggers them, because the user
  // has clearly typed something.
  const showError = !validation.ok && (touched || validation.code !== 'empty')

  const isGroup = mode === 'group'
  const labelText = isGroup ? 'Group name' : 'First name'
  // The canonical error messages are written with "First name" as the
  // leading subject (see validation/firstName.ts FIRST_NAME_ERROR_MESSAGE).
  // In group mode the surface copy needs to mirror the visible label, so
  // we rewrite the leading subject — case-insensitive replace covers any
  // future reuse mid-sentence too. The validator stays single source of
  // truth; only the rendered string is reskinned.
  const errorMessage = !validation.ok
    ? FIRST_NAME_ERROR_MESSAGE[validation.code].replace(/first name/gi, labelText)
    : null

  const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
    onChange(e.target.value)
  }
  const handleBlur = (_: FocusEvent<HTMLInputElement>) => {
    setTouched(true)
  }

  return (
    <div className="first-name-input">
      <label htmlFor={inputId}>{labelText}</label>
      <input
        id={inputId}
        type="text"
        // Browser autofill "given-name" only makes sense in single mode;
        // in group mode the field is a team / collective name and we
        // disable autofill so the suggestion menu does not surface a
        // first name from the user's address book.
        autoComplete={isGroup ? 'off' : 'given-name'}
        value={value}
        onChange={handleChange}
        onBlur={handleBlur}
        aria-describedby={showError ? hintId : undefined}
        aria-invalid={showError ? true : undefined}
      />
      {showError && errorMessage ? (
        <p
          id={hintId}
          role="status"
          aria-live="polite"
          className="first-name-input__hint first-name-input__hint--error"
        >
          {errorMessage}
        </p>
      ) : null}
    </div>
  )
}

import { X } from '../options'
import { EMAIL_ERROR_MESSAGE, validateEmail } from '../validation/email'

interface Props {
  /** Controlled value — typically {@code session.email}. */
  value: string
  /** Fires the {@code EmailChanged} reducer action. */
  onChange: (next: string) => void
  /** When {@code true}, both the input AND the clear-X are disabled —
   *  used while a Generate request is in flight so the user cannot
   *  mutate the recipient mid-request. */
  disabled?: boolean
  /** Hard character cap on the trimmed value; default 254 per
   *  research.md R7. The HTML input enforces the raw cap as
   *  defence-in-depth — validation logic measures the trimmed length. */
  maxLength?: number
}

/**
 * 023 (issue #57) — single-line optional email input rendered between
 * the numbered category groups and the first-name input on the Setup
 * tab (FR-2301). Mirrors the {@link CustomRoleInput} contract exactly:
 * controlled value, trailing clear-X visible only when non-empty,
 * disabled propagates to both the input and the X. The validator is
 * canonical — see {@link ../validation/email.ts}.
 *
 * <p>Blank-equivalent (empty or whitespace-only) values are treated as
 * valid per FR-2303: the optional field accepts an empty state without
 * any inline error. Non-blank values that fail the validator produce a
 * `role="alert"` inline message keyed by the validator's failure code
 * (FR-2305).
 *
 * <p>The clear-X uses the same `X` icon and CSS-class shape as
 * {@code CustomRoleInput} (research.md R8 — duplicate-don't-extract
 * pattern; the two callers share the look via `.email-input` and
 * `.custom-role-input` rule blocks in `frontend/src/index.css`).
 */
export function EmailInput({ value, onChange, disabled = false, maxLength = 254 }: Props) {
  const hasValue = value.length > 0
  const validation = validateEmail(value)
  // FR-2303 / FR-2305: only surface the inline error for non-blank values.
  // A blank input is a legitimate state for an optional field.
  const showError = hasValue && value.trim().length > 0 && !validation.ok
  const errorMessage = !validation.ok ? EMAIL_ERROR_MESSAGE[validation.code] : null

  return (
    <div className="email-input">
      <label className="email-input__label" htmlFor="email-input">
        Email <span className="email-input__optional">(optional)</span>
      </label>
      <p id="email-input-hint" className="email-input__hint">
        Add your email to have your alter ego sent to you — leave it blank to skip.
      </p>
      <div className="email-input__field">
        <input
          id="email-input"
          type="email"
          inputMode="email"
          autoComplete="email"
          spellCheck={false}
          className="email-input__control"
          aria-label="Email"
          aria-invalid={showError || undefined}
          aria-describedby={
            showError ? 'email-input-hint email-input-error' : 'email-input-hint'
          }
          value={value}
          maxLength={maxLength}
          disabled={disabled}
          onChange={(e) => onChange(e.target.value)}
        />
        {hasValue ? (
          <button
            type="button"
            className="email-input__clear"
            aria-label="Clear email"
            disabled={disabled}
            onClick={() => onChange('')}
          >
            <span aria-hidden="true" className="email-input__clear-icon">
              <X width={16} height={16} strokeWidth={2} />
            </span>
          </button>
        ) : null}
      </div>
      {showError && errorMessage ? (
        <p id="email-input-error" role="alert" className="email-input__error">
          {errorMessage}
        </p>
      ) : null}
    </div>
  )
}

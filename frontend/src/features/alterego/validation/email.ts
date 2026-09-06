/**
 * 023 (issue #57) — Email validator. Pure, synchronous, no React deps.
 *
 * Optional field semantics: a blank (or whitespace-only) value is
 * considered valid (FR-2303). A non-blank value is validated against
 * a pragmatic single-line grammar plus a 254-char cap on the trimmed
 * value (RFC 5321 practical envelope cap; research.md R7).
 *
 * The regex matches the FE pre-gate; the backend defends with Jakarta's
 * `@Email` constraint (Hibernate's pragmatic regex). The two don't need
 * to be byte-identical because the FE pre-gate ensures only valid
 * values reach the wire on the happy path.
 *
 * Used by:
 *   - {@link ./email.test.ts}
 *   - `components/EmailInput.tsx` (live inline error)
 *   - `state/selectors.ts` (`emailValidity`, `missingInputs`,
 *     `isReadyToGenerate`, `isReadyToSurprise` — Setup-tab gating)
 *   - `components/SendAsEmailButton.tsx` (disabled-state gating)
 */

export type EmailErrorCode = 'invalid_format' | 'too_long'

export type EmailValidation = { ok: true; trimmed: string } | { ok: false; code: EmailErrorCode }

/** RFC 5321 practical envelope cap; covers the LHS@RHS form a participant
 *  would realistically type. The backend's `@Size(max = 254)` mirrors. */
const MAX_TRIMMED_LENGTH = 254

/** Pragmatic single-line address grammar: `local@domain.tld`-shaped,
 *  no whitespace anywhere, at least one dot in the domain. Anchored. */
const PRAGMATIC_EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

export function validateEmail(value: string): EmailValidation {
  if (value == null) return { ok: true, trimmed: '' }

  const normalised = value.normalize('NFC')
  const trimmed = normalised.trim()

  // FR-2303: blank (or whitespace-only) → ok with trimmed === ''.
  if (trimmed.length === 0) {
    return { ok: true, trimmed: '' }
  }

  // Length cap is measured on the TRIMMED value so trailing whitespace
  // never causes a false `too_long` (test pin).
  if (trimmed.length > MAX_TRIMMED_LENGTH) {
    return { ok: false, code: 'too_long' }
  }

  if (!PRAGMATIC_EMAIL.test(trimmed)) {
    return { ok: false, code: 'invalid_format' }
  }

  return { ok: true, trimmed }
}

/**
 * Inline-error copy keyed by the validator's failure codes. Matches the
 * spec FR-2305 "clear, non-technical message identifying the field as
 * the source".
 */
export const EMAIL_ERROR_MESSAGE: Record<EmailErrorCode, string> = {
  invalid_format: 'Please enter a valid email address (for example, name@example.com).',
  too_long: 'Email address is too long (254 characters or fewer).',
}

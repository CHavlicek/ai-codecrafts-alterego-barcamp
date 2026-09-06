/**
 * 011 — First-name validator. Pure, synchronous, no React dependencies.
 *
 * CANONICAL RULES — keep in lock-step with the backend sibling at
 * `backend/src/main/java/com/aiavatar/alterego/model/validation/FirstNameValidator.java`.
 * The rule list itself lives in `specs/011-input-validation/data-model.md`
 * (Families A–E). A backend integration test pins the canonical phrase set
 * so a one-sided drift fails the suite.
 *
 * Used by:
 *   - {@link ./firstName.test.ts} (direct tests)
 *   - `components/FirstNameInput.tsx` (live inline error + Generate gating)
 *   - `state/selectors.ts` (`missingInputs`, `isReadyToGenerate`,
 *     `isReadyToSurprise` — the submit guards)
 */

export type FirstNameErrorCode = 'empty' | 'too_long' | 'invalid_chars' | 'looks_like_instructions'

export type FirstNameValidation = { ok: true } | { ok: false; code: FirstNameErrorCode }

const MAX_CODE_POINTS = 50

// Family C — Unicode invisibles & bidi controls. Encoded as code-point
// ranges (rather than a literal-character regex) because:
//   1. Literal U+200x chars in regex source trip ESLint's
//      `no-irregular-whitespace` rule.
//   2. Editors and serialisation pipelines can silently mangle them.
//   3. The intent (which ranges) is clearer in numeric form anyway.
const UNICODE_INVISIBLE_RANGES: ReadonlyArray<readonly [number, number]> = [
  [0x200b, 0x200f], // ZWSP, ZWNJ, ZWJ, LTR mark, RTL mark
  [0x202a, 0x202e], // bidi embedding & override
  [0x2060, 0x2064], // word joiner / invisible operators
  [0x2066, 0x2069], // bidi isolates
  [0xfeff, 0xfeff], // BOM / zero-width no-break space
]

function containsUnicodeInvisible(s: string): boolean {
  for (let i = 0; i < s.length; i++) {
    const cp = s.charCodeAt(i)
    for (const [lo, hi] of UNICODE_INVISIBLE_RANGES) {
      if (cp >= lo && cp <= hi) return true
    }
  }
  return false
}

// Family D — structural injection markers.
const STRUCTURAL_MARKER = /[<>`{}[\]\\|]|\$\{/

// Family E — instruction-shaped phrases. Whole-word match: bounded on both
// sides by either string boundary or any non-letter character (Unicode-aware
// via `\p{L}`). Order longest-first inside the alternation to avoid the
// regex preferring a shorter prefix.
const INSTRUCTION_PHRASE =
  /(?<![\p{L}])(ignore all previous|ignore previous|ignore prior|disregard previous|system prompt|you are now|act as|as an ai|assistant:|system:|user:|<\||<\/s>|<s>|prompt injection|jailbreak)(?![\p{L}])/iu

function containsAsciiControl(s: string): boolean {
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i)
    if (c <= 0x1f || c === 0x7f) return true
  }
  return false
}

export function validateFirstName(value: string): FirstNameValidation {
  if (value === null || value === undefined) return { ok: false, code: 'empty' }

  const normalised = value.normalize('NFC')
  const trimmed = normalised.trim()

  if (trimmed.length === 0) return { ok: false, code: 'empty' }

  // Family A — length in user-perceived (code-point) units, measured on the
  // trimmed value (a real name with accidental leading/trailing whitespace
  // should still pass). `Array.from(s).length` iterates code points, handling
  // astral-plane chars correctly (where `s.length` would over-count UTF-16
  // code units).
  if (Array.from(trimmed).length > MAX_CODE_POINTS) {
    return { ok: false, code: 'too_long' }
  }

  // Families B–E run on the UN-TRIMMED normalised value. Doing them post-trim
  // would let a leading or trailing U+FEFF (BOM) slip past silently, because
  // JavaScript's `String.prototype.trim()` strips Unicode whitespace
  // including U+FEFF — Java's `String.trim()` does not. Running these checks
  // pre-trim keeps the two stacks' verdicts identical and matches the spec
  // intent that invisibles are rejected wherever they appear.

  // Family B — ASCII control characters (0x00–0x1F + 0x7F).
  if (containsAsciiControl(normalised)) {
    return { ok: false, code: 'invalid_chars' }
  }

  // Family C — Unicode invisibles / bidi controls.
  if (containsUnicodeInvisible(normalised)) {
    return { ok: false, code: 'invalid_chars' }
  }

  // Family D — structural injection markers.
  if (STRUCTURAL_MARKER.test(normalised)) {
    return { ok: false, code: 'invalid_chars' }
  }

  // Family E — instruction-shaped phrases.
  if (INSTRUCTION_PHRASE.test(normalised)) {
    return { ok: false, code: 'looks_like_instructions' }
  }

  return { ok: true }
}

/**
 * User-facing English error copy keyed by the validator's failure codes.
 * Matches the messages enumerated in
 * `specs/011-input-validation/data-model.md` § "Error taxonomy".
 */
export const FIRST_NAME_ERROR_MESSAGE: Record<FirstNameErrorCode, string> = {
  empty: 'First name is required.',
  too_long: 'First name must be 50 characters or fewer.',
  invalid_chars:
    'First name cannot contain invisible, control, or special characters such as `<`, `>`, or backticks.',
  looks_like_instructions:
    'First name contains content that looks like instructions to the model. Please enter a real name.',
}

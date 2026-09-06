# Data Model: Input Validation for First Name

**Feature**: 011-input-validation
**Date**: 2026-04-27

This feature does not introduce a persistent entity (extends 001 FR-016/017/024 — strictly no persistence). It tightens the constraints on one existing in-flight value.

## Entity: `firstName` (in-flight only)

| Attribute | Type | Constraint | Source of truth |
|---|---|---|---|
| `firstName` | `string` (Unicode) | `1 ≤ codePointCount(NFC(trim(value))) ≤ 50` | Spec FR-1101; frontend `validation/firstName.ts`; backend `@Size(min=1, max=50)` + `@ValidFirstName` on `AlterEgoRequest.firstName()` |
| `firstName` | `string` (Unicode) | Must NOT contain any character or substring listed in **Rule families A–E** below | Spec FR-1104; frontend `validateFirstName(value)`; backend `FirstNameValidator.isValid(value, ctx)` |

### Lifecycle

```
[user types] → React state (verbatim, untrimmed)
            → live validator (component re-render)
            → on submit: trim + NFC + validate → request body { firstName, ... }
            → backend: @Valid pipeline (Bean Validation) → controller method body
            → on success: trim + NFC at the boundary, prompt builder interpolates
            → on rejection: ProblemDetailAdvice → 400 Problem+JSON, no provider call
```

The value is **never** persisted: not to disk, not to database, not to cache, not to logs. Process-memory lifetime is bounded by the duration of one Generate request.

### State

The value has no states beyond "draft (in component)" → "submitted (in flight)" → "discarded after response". No pending / approved / rejected enum is needed; rejection produces a 400 and the value is dropped at the controller boundary without further processing.

## Rule families

The five rule families below are the **canonical rule list**. They MUST be expressed in the same order, with the same wording, in both `firstName.ts` and `FirstNameValidator.java`.

### Pipeline

Both validators apply the rules to the result of `Normalizer.NFC(value)` (Java) / `value.normalize('NFC')` (TS). **Family A (length) is measured on the trimmed value; Families B–E run against the un-trimmed normalised value.** The asymmetry exists because JavaScript's `String.prototype.trim()` strips Unicode whitespace including U+FEFF (per ECMAScript `WhiteSpace`), while Java's `String.trim()` only strips chars ≤ 0x20. Running B–E pre-trim makes both stacks produce identical verdicts on inputs like `"﻿Paula"` — without that, the JS validator would silently strip the leading BOM and let it through.

### Family A — Length

- After `trim()` then NFC normalisation, the Unicode code-point count must be `≤ 50`.
- After `trim()`, the value must be non-empty (preserves existing `@NotBlank` behaviour).

### Family B — ASCII control characters

Reject any code point in the range `U+0000–U+001F` (which covers `\n`, `\r`, `\t`, `\0`, etc.) or `U+007F` (DEL).

### Family C — Unicode invisibles & bidi controls

Reject any of these code points:

- `U+200B`, `U+200C`, `U+200D` — zero-width space, ZWNJ, ZWJ
- `U+200E`, `U+200F` — LTR mark, RTL mark
- `U+202A` … `U+202E` — bidi embedding & override
- `U+2060` … `U+2064` — word joiner / invisible operators
- `U+2066` … `U+2069` — bidi isolates
- `U+FEFF` — BOM / zero-width no-break space

### Family D — Structural injection markers

Reject any value that contains any of:

- The single characters: `<`, `>`, `` ` ``, `{`, `}`, `[`, `]`, `\`, `|`
- The substring `${` (template-injection bait)
- A run of three or more consecutive backticks (`` ``` ``)

### Family E — Instruction-shaped phrases (case-insensitive, whole word)

Reject any value that contains, case-insensitively, any of:

`ignore previous`, `ignore prior`, `ignore all previous`, `disregard previous`, `system prompt`, `you are now`, `act as`, `as an ai`, `assistant:`, `system:`, `user:`, `<|`, `</s>`, `<s>`, `prompt injection`, `jailbreak`

"Whole word/phrase" means: the match must be bounded by a non-letter character (or string boundary) on both sides, where "letter" is Unicode general-category `L`. This prevents `Yu Aren` (which contains `u are` as a substring) from false-positive-matching `you are now`.

## Error taxonomy

The validator on each side returns a single failure code per offending rule family (it does not need to enumerate every offending character — knowing the family is enough for the user-facing message).

| Family | Frontend `code` | Backend Bean-Validation message key | User-facing message |
|---|---|---|---|
| A length | `too_long` | `firstName.tooLong` | "First name must be 50 characters or fewer." |
| A empty | `empty` | (existing `@NotBlank`) | "Please enter your first name." |
| B / C | `invalid_chars` | `firstName.invalidChars` | "First name cannot contain invisible or control characters." |
| D | `invalid_chars` | `firstName.invalidChars` | "First name cannot contain `<`, `>`, backticks, or other special characters." |
| E | `looks_like_instructions` | `firstName.looksLikeInstructions` | "First name contains content that looks like instructions to the model. Please enter a real name." |

Backend response bodies (`ProblemDetail`) MUST surface the field name (`firstName`) and the message key, but MUST NOT echo the rejected value (FR-1107).

## Out of scope (follow-ups, **not** part of this feature)

- Wrapping the user value in a structured prompt slot (e.g., quoted + escaped) inside `GeminiPromptBuilder`. This is the proper defence-in-depth measure and is recommended as the next feature on this surface — but the issue explicitly asks for **input validation**, and bundling prompt re-architecture into the same change broadens scope and risk.
- A grapheme-cluster-based length count via `Intl.Segmenter`. The current code-point count is consistent across both stacks and accurate enough for a 50-char cap; revisit if user feedback shows astral-plane edge cases.
- Server-side rate limiting on rejection. Not a goal of this feature; covered by future infrastructure work.

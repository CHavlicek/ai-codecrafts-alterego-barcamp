# Phase 0 Research: Input Validation for First Name

**Feature**: 011-input-validation
**Date**: 2026-04-27

No `NEEDS CLARIFICATION` markers remain in the plan. Research items below are best-practice and integration-pattern decisions surfaced by the Technical Context.

---

## Decision 1 — Where the canonical rule list lives

**Decision**: Two thin sibling modules, hand-mirrored:
- `frontend/src/features/alterego/validation/firstName.ts`
- `backend/src/main/java/com/aiavatar/alterego/model/validation/FirstNameValidator.java`

Each module starts with a `// CANONICAL RULES — keep in lock-step with the sibling on the other side` block listing the same five rule families in the same order (length, ASCII controls, Unicode invisibles, structural chars + `${` + triple-backtick, instruction phrases). A backend integration test (`AlterEgoControllerInputValidationTest`) pins the phrase set so a one-sided change will fail the suite.

**Rationale**: This codebase already builds frontend and backend in lockstep but has no shared package, no codegen, and no schema-first toolchain (no OpenAPI generator wired). Introducing one for one validator is over-engineered (violates the project's lean POC posture). The lower-cost guard is two short modules + a parity assertion that runs on every backend test run.

**Alternatives considered**:
- **Generate from a shared JSON schema** — rejected: no codegen pipeline today, would add a build step and a dependency to maintain for a 30-line rule set.
- **Frontend imports from backend `/validation-rules` endpoint** — rejected: adds a network round-trip and a runtime coupling for what is fundamentally static config. Also doesn't solve drift, only hides it.
- **Backend-only validation, frontend disables submit blindly** — rejected: violates SC-005 (sub-200 ms inline feedback) and produces a hostile UX where the user only learns of the problem after a failed POST.

---

## Decision 2 — How the backend signals the rejection

**Decision**: Use Jakarta Bean Validation. Add a custom `@ValidFirstName` annotation backed by a `ConstraintValidator<ValidFirstName, String>`. Apply it on `AlterEgoRequest.firstName()` alongside the (relaxed) `@Size(min=1, max=50)`. The existing `ProblemDetailAdvice` already converts Bean Validation violations to RFC 7807 `ProblemDetail` with HTTP 400 and never echoes photo bytes — extend it minimally to ensure the offending **value** is also not echoed for any field whose violation comes from `@ValidFirstName` (the `firstName` field in particular).

**Rationale**: The backend already has a Bean Validation pipeline, a `@SpringBootTest`-level integration test for it, and a unified error format. Adding a sibling `@ValidFirstName` annotation matches the existing idiom (`@NotBlank @Size(...)`), keeps the controller free of validation logic, and the shape of the response payload doesn't change for callers — only the rule set does.

**Alternatives considered**:
- **Explicit `if`-checks in the controller** — rejected: scatters validation logic, defeats the existing `@Valid` pipeline and the centralised `ProblemDetailAdvice`.
- **Custom exception type + custom advice** — rejected: introduces a parallel error-format branch the frontend must learn. The Bean Validation path already returns a structured `errors` array keyed by field; reuse it.
- **422 instead of 400** — rejected: the existing pipeline returns 400 for `@Valid` violations. The HTTP `4XX` requirement in the issue is satisfied by 400; introducing 422 here would split the convention with no caller benefit.

---

## Decision 3 — Frontend live-validation cadence

**Decision**: Validate on every keystroke (the React component already re-renders on each `onChange`); the inline error message renders when the value has been touched (focus-then-blur or any keystroke after at least one character has been typed) and the value is invalid. The Generate button is disabled while invalid. No debouncing — the validator is a synchronous regex pass, well under 1 ms.

**Rationale**: SC-005 gives a 200 ms budget; a synchronous validator beats it by orders of magnitude. Debouncing would only add user-visible lag for no benefit on this size of input. The "touched" gate prevents a confusing red-error-on-empty-field flash before the user has typed anything.

**Alternatives considered**:
- **Validate on blur only** — rejected: the user would type a 70-character value, leave the field, and only then learn it's wrong. Worse UX than current 40-char hint.
- **Validate on submit only** — rejected: fails SC-005.
- **Debounce 150 ms** — rejected: synchronous regex is sub-millisecond; debouncing adds latency without smoothing anything.

---

## Decision 4 — Length is measured on the trimmed, NFC-normalised value

**Decision**: Both sides apply `s.trim()` then `.normalize('NFC')` (TS) / `Normalizer.normalize(s, Form.NFC).strip()` (Java) before counting code points (`Array.from(s).length` in TS to handle astral plane characters; `s.codePointCount(0, s.length())` in Java). Length cap is 50.

**Rationale**: A user's Unicode code-point count is what matches the "50 characters" in the spec. JavaScript's `String.length` counts UTF-16 code units (so `"𝕊"` is length 2) and Java's `String.length()` is the same — both would over-count emoji and astral-plane letters. Fixing this on both sides keeps the rule symmetric.

NFC normalisation collapses precomposed-vs-decomposed forms (`é` written as `e` + combining acute) so an attacker cannot pad a 51-glyph string into a 50-character codepoint count by exploiting one form on the frontend and the other on the backend.

**Alternatives considered**:
- **Bytes** — rejected: penalises non-ASCII names (a 30-character Japanese name in UTF-8 is 90 bytes).
- **`String.length` / UTF-16 code units** — rejected: over-counts emoji and astral characters; gives an inconsistent user experience.
- **Grapheme clusters via `Intl.Segmenter`** — rejected for now: more accurate user-perceived length but adds complexity for marginal benefit on a 50-char cap. Documented as a future refinement in `data-model.md`.

---

## Decision 5 — Rule set scope (defence-in-depth posture)

**Decision**: Treat this validator as a **rate-limiter on trivial attacks**, not a content-security boundary. The deny-list catches the public, copy-paste-style jailbreaks and structural injection markers; it explicitly does not promise to defend against a determined attacker (who can paraphrase any phrase). Mitigation in depth — escaping the user value inside the prompt, or using structured prompt formats — is **out of scope** for this feature and called out in `data-model.md` as a follow-up.

**Rationale**: This matches the spec's Assumptions section. Promising more than this validator can deliver would invite over-reliance and gives a false sense of security to future feature work.

**Alternatives considered**:
- **Move user value into a dedicated structured prompt slot (e.g., wrap in quotes + escape interior quotes)** — recommended as a follow-up but not bundled here. The issue explicitly asks for input validation; prompt re-architecture is a separate, larger change.
- **Use an LLM-based content classifier** — rejected: latency, cost, and round-trip with a third-party service for every request fails the project's no-persistence + simplicity posture, and the false-positive rate on real names is unacceptable.

---

## Decision 6 — Test layout and the "rule parity" test

**Decision**: Three test surfaces:

1. **Frontend unit tests** — `firstName.test.ts` exhaustively walks every rule (length boundary 49/50/51, each control char, each invisible code point, each structural char, each phrase, plus a 30-name happy-path corpus shared with the backend test).
2. **Frontend component tests** — `FirstNameInput.test.tsx` adds three cases: live error appears when value invalid, error is wired via `aria-describedby`, Generate button is disabled while invalid.
3. **Backend** — split into a fast unit test (`FirstNameValidatorTest` — same corpus as frontend) and one Spring Boot integration test (`AlterEgoControllerInputValidationTest`) that POSTs each invalid kind, asserts 400, asserts the response body does **not** contain the rejected value (FR-1107), and asserts the language-model provider is **not called** (mock provider client).

The 30-name happy-path corpus is duplicated verbatim in both unit suites; if a frontend rule change accidentally rejects a name the backend still allows, both sides will fail the same fixture and the drift is obvious in the diff.

**Rationale**: Cheap, deterministic, and matches Principle III's TDD posture (90 % coverage gate, mandatory integration test).

---

## Open questions

None.

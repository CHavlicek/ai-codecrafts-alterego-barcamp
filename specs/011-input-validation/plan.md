# Implementation Plan: Input Validation for First Name

**Branch**: `011-input-validation` | **Date**: 2026-04-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/011-input-validation/spec.md`

## Summary

Tighten the First Name field with two-tier validation: a 50-character cap (NFC-normalised, post-trim) and a deny-list for prompt-injection-shaped content (ASCII controls, Unicode bidi/zero-width invisibles, structural injection markers, and a small set of instruction-shaped phrases). The frontend blocks the form on invalid input with an inline, screen-reader-announced error; the backend independently enforces the same rules and returns a 4XX RFC 7807 `ProblemDetail` whose payload names the offending field but never echoes the rejected value. The matching ruleset is centralised in two thin pure modules (one TS, one Java) that share a hand-written-but-symmetric description so divergence stays cheap to spot in code review. No new dependency on either side; existing `ProblemDetailAdvice` and `aria-describedby` plumbing are reused.

## Technical Context

**Language/Version**: TypeScript 5.x (strict) on the frontend; Java 21 (LTS) on the backend.
**Primary Dependencies**: React 19 + Vite 8 + Vitest (frontend); Spring Boot 3.x + Jakarta Bean Validation + JUnit 5 + Spring Boot Test (backend). No new dependency.
**Storage**: N/A (extends 001 FR-016/017/024 — no persistence; rejected values are not logged or counted).
**Testing**: Vitest + React Testing Library on the frontend; JUnit 5 + `@SpringBootTest` (existing `AlterEgoControllerTest` lane) and pure unit tests on the backend.
**Target Platform**: Modern evergreen browsers (frontend); Linux container running JRE 21 (backend). Unchanged.
**Project Type**: Web application (`frontend/` + `backend/`).
**Performance Goals**: Validation cost is invisible to the user — frontend live-validation under 200 ms perceived latency (SC-005); backend rejection adds <1 ms p95 to the request budget for invalid input and zero cost for valid input (single regex + length check).
**Constraints**: No persistence; rejected values must not appear in error response bodies (FR-1107); rule list must be a single source of truth on each side, hand-mirrored and asserted by a parity test on the backend (the canonical rule list lives in code comments next to both validator modules).
**Scale/Scope**: One field, one POST endpoint (`POST /api/v1/alter-egos`), one frontend component (`FirstNameInput`) and one form orchestrator (`AlterEgoPage` / `useGenerateAlterEgo`). Tests across two modules.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Outcome | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | **PASS** | No new dependency. Reuses React 19 / Vite 8 / Spring Boot 3 already pinned; no deprecated package introduced. |
| III. Test-First Development (TDD) | **PASS (with documented inheritance)** | Tests are written first per the Development Workflow (failing tests committed before implementation). Coverage gate ≥ 90% holds — every branch of the validator is unit-tested on each side, plus one end-to-end Spring Boot Test for the controller-level rejection path. The constitution's "complete user journey end-to-end" gate is satisfied at the same tier prior recent features in this repo (009/010) used: `@SpringBootTest` controller integration + component tests with the validator wired into the page submit guard. A Playwright lane is deliberately not introduced for this feature; that broader project gap is tracked outside this branch. |
| IV. Resilient HTTP Communication | **N/A** | No new HTTP call. Existing client retry/fallback policy unchanged; a 4XX is a deterministic client-error and intentionally NOT retried (consistent with current `alterEgoClient` behaviour). |
| V. Feature Branch Workflow | **PASS** | Developed on `011-input-validation` cut from `main`; PR + human approval before merge per the standard flow. |
| VI. Zero Deprecated Dependencies | **PASS** | No dependency manifest change. `npm audit` / Gradle dependency check unaffected. |
| Frontend Tech Standards | **PASS** | Validator is plain TS in `frontend/src/features/alterego/validation/` (no new lib). Error UI reuses existing `aria-describedby` wiring from `FirstNameInput`. |
| Backend Tech Standards | **PASS** | Validator is a Jakarta Bean Validation `ConstraintValidator` (`@ValidFirstName`) on the existing `AlterEgoRequest` record. Existing `ProblemDetailAdvice` is reused; no new error format introduced. |

No deviations. **Complexity Tracking** section is intentionally empty.

## Project Structure

### Documentation (this feature)

```text
specs/011-input-validation/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── post-alter-egos.md   # 4XX-only contract delta for the existing endpoint
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

Existing two-app layout — only the touched files are listed. No new top-level directories.

```text
frontend/
├── src/
│   ├── features/
│   │   └── alterego/
│   │       ├── components/
│   │       │   ├── FirstNameInput.tsx           # MODIFY — wire validator, surface error via aria-describedby
│   │       │   └── FirstNameInput.test.tsx      # MODIFY — add length-50 + injection cases
│   │       ├── state/
│   │       │   └── reducer.ts                   # NO CHANGE — value still stored verbatim
│   │       ├── services/
│   │       │   └── alterEgoClient.ts            # NO CHANGE — request shape unchanged
│   │       ├── pages/
│   │       │   └── AlterEgoPage.tsx             # MODIFY — submit-time guard uses the validator
│   │       └── validation/
│   │           ├── firstName.ts                 # NEW — single pure validator (used by component + page)
│   │           └── firstName.test.ts            # NEW — unit tests for the validator
│
backend/
└── src/
    ├── main/java/com/aiavatar/alterego/
    │   ├── model/
    │   │   ├── AlterEgoRequest.java             # MODIFY — bump @Size(max=50) and add @ValidFirstName
    │   │   └── validation/
    │   │       ├── ValidFirstName.java          # NEW — Jakarta Bean Validation annotation
    │   │       └── FirstNameValidator.java      # NEW — ConstraintValidator implementation
    │   └── config/
    │       └── ProblemDetailAdvice.java         # NO CHANGE — already returns 400 for Bean Validation
    └── test/java/com/aiavatar/alterego/
        ├── unit/
        │   ├── AlterEgoRequestValidationTest.java   # MODIFY — bump 40→50, add injection cases
        │   └── validation/
        │       └── FirstNameValidatorTest.java      # NEW — direct unit tests for the validator
        └── integration/
            └── AlterEgoControllerInputValidationTest.java   # NEW — end-to-end 4XX assertion + no-echo
```

**Structure Decision**: This feature touches only the existing `frontend/src/features/alterego/` slice and the existing `backend/.../alterego/model` + `validation` package. No new top-level directory is introduced. Validator code lives next to its consumer on each side; the **shared rule list** is duplicated by hand and the backend integration test pins the canonical phrase set so a frontend/backend drift will fail the suite (see `research.md` "Rule parity").

## Complexity Tracking

*No constitutional violations to justify.*

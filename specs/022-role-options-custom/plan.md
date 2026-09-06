# Implementation Plan: New Options for the Role Category

**Branch**: `022-role-options-custom` | **Date**: 2026-05-11 | **Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `/specs/022-role-options-custom/spec.md`

## Summary

Extend the Setup-tab Role category in two additive directions:

1. **Three new prefab options** — *HR*, *Administration*, *Customer Relations* — added to the existing six-element `Archetype` enum end-to-end (frontend option list, backend Java enum, both prompt builders, fallback poster, accent resolver, Surprise-Me sampling pool).
2. **Free-form custom-role input** under the prefab grid. When the input's trimmed value is non-empty it (a) silently clears any prefab selection in the reducer, (b) blurs and disables the prefab pills, (c) becomes the *role of record* sent to the backend, and (d) flows through the same prompt + overlay path as a prefab label. A trailing `X` button clears the input with no confirmation.

Technical approach: keep the existing wire shape and add one optional `customRole` string field (≤ 100 chars, trimmed) to `AlterEgoUserSelections`. Class-level validation requires *either* a non-null `archetype` *or* a non-blank `customRole`. The backend computes a single `roleLabel()` helper that downstream prompt builders + the text-overlay service consume — they stop reading `request.archetype().label()` and read `request.roleLabel()` instead. `Archetype` still travels on the wire (for accent-resolver keying); when the role-of-record is custom and archetype is absent the backend defaults the accent key deterministically. No new third-party dependency, no schema migration, no persistence.

## Technical Context

**Language/Version**: TypeScript 5.x (strict) on the frontend; Java 21 (LTS) on the backend. Unchanged from 002 / 006 / 016 / 020 / 021.
**Primary Dependencies**: React 19 + Vite 8 + Vitest + React Testing Library + Playwright (frontend); Spring Boot 3.x + Jakarta Bean Validation + JUnit 5 + Spring Boot Test + Mockito (backend). **No new runtime, test, or build dependency.** The `X` icon is already re-exported from `options.ts:113` (`Camera, Aperture, RotateCcw, Check, X`).
**Storage**: N/A. Inherits 001 FR-016 / FR-017 / FR-024 — no persistence. The prefab selection, the custom-role string, and the derived role-of-record live in browser session memory (frontend) and on the request thread's stack (backend) for the duration of one Generate request only. Nothing is logged or cached.
**Testing**: Vitest + RTL for the new `CustomRoleInput` component, the `ArchetypeGrid` disabled-state variant, the reducer's `CustomRoleChanged` branch, and the gating selector. JUnit 5 + Spring Boot Test for the widened DTO validation, the prompt-builder role-label substitution (Gemini + fal.ai), and the controller acceptance contract. One Playwright happy-path spec exercises the precedence + `X`-clear UI flow end-to-end.
**Target Platform**: Modern evergreen browsers (Chromium ≥ 110, Firefox ≥ 110, Safari ≥ 16) for the frontend; Linux JDK 21 server for the backend. Unchanged.
**Project Type**: Web application — two-tier (`frontend/` React/TS + `backend/` Java Spring Boot) per CLAUDE.md.
**Performance Goals**: End-to-end Generate p50 wall-clock time within ±10% of the post-021 baseline (SC-2207). The new input is component-local state — keystroke handling MUST stay ≤ 16 ms per change so typing is jank-free. Prompt-builder substitution adds one branch + one string read — negligible.
**Constraints**: Custom-role string ≤ 100 characters after `String.trim()` on both ends of the wire; whitespace-only inputs are treated as empty (FR-2204 / FR-2210 / SC-2205). Existing `archetype` wire values + relative order are immutable for the six engineering roles (preserves contract from 002 / 021). The three new wire values follow kebab-case convention. Image prompt MUST continue to forbid rendered text — the role label is communicated as a *direction*, never as a string to render (FR-2213, carried over from 021 SC-2102).
**Scale/Scope**: Same single-tenant POC posture as 001–021. One Generate request per Setup → Generate flow, one role-of-record value transported per request. No concurrency, no rate-limit, no caching layer added.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance | Notes |
|---|---|---|
| I. Modern & Secure Stack | ✅ | No new dependency. Continues React 19 / Vite 8 / Spring Boot 3.x. No deprecated package introduced (no `npm install` runs as part of this feature). |
| III. Test-First (TDD + ≥ 90% coverage + integration tests) | ✅ | All new tests land before the production code that satisfies them: reducer / selector unit tests, `CustomRoleInput` + `ArchetypeGrid` component tests, prompt-builder unit tests (Gemini + fal.ai), `AlterEgoUserSelections` validation test, controller integration test, one Playwright E2E. Coverage gate ≥ 90% MUST hold on every touched module. |
| IV. Resilient HTTP | ✅ | Wire surface widens additively (one optional field on the existing `selections` JSON part); the resilient-fetch retry + FE-side fallback paths are untouched. Fallback poster path now consumes `roleLabel()` instead of `archetype.label()` — same single decision point. |
| V. Feature Branch Workflow | ✅ | Branch `022-role-options-custom` cut from `main` by SpecKit's `create-new-feature.sh`. PR with explicit human approval before merge. |
| VI. Zero Deprecated Dependencies | ✅ | No dependency churn. `npm audit` + `./gradlew dependencyCheckAnalyze` continue to pass on `main`. |

**No violations. No Complexity Tracking entries needed.**

## Project Structure

### Documentation (this feature)

```text
specs/022-role-options-custom/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── alter-egos.openapi.delta.yaml   # Additive contract delta vs. 002 baseline
├── checklists/
│   └── requirements.md  # Already created by /speckit.specify
└── tasks.md             # Created by /speckit.tasks (NOT by /speckit.plan)
```

### Source Code (repository root)

Only files actually touched by this feature are listed. Anything not listed below MUST be unchanged at PR time.

```text
frontend/src/features/alterego/
├── types.ts                              # +3 Archetype literal values; +optional customRole on Selections
├── options.ts                            # +3 ARCHETYPE_OPTIONS entries (HR / Administration / Customer Relations)
├── state/
│   ├── reducer.ts                        # +CustomRoleChanged action; clears archetype on non-blank custom; clears customRole on SurpriseMePicked
│   └── selectors.ts                      # missingInputs: archetype satisfied by EITHER archetype OR trimmed customRole; +customRoleOfRecord helper
├── components/
│   ├── ArchetypeGrid.tsx                 # accepts disabled prop; forwards to SelectionGrid
│   ├── SelectionGrid.tsx                 # accepts disabled prop; renders blurred pointer-events:none with tabIndex=-1 + aria-disabled
│   ├── CustomRoleInput.tsx               # NEW — single-line text input + trailing X clear button (≤ 100 chars)
│   └── SetupLayout.tsx                   # renders <CustomRoleInput/> immediately under <ArchetypeGrid/> in the step-1 group; passes disabled flag down
├── services/
│   └── alterEgoClient.ts                 # outbound selections: includes customRole when trimmed value non-empty; omits otherwise (forward-compat)
└── styles/                                # +.archetype-grid.is-disabled + .custom-role-input rules (CSS module or tokens.css)

frontend/src/features/alterego/__tests__/  (mirrored alongside files)
├── state/reducer.test.ts                 # +CustomRoleChanged tests; +SurpriseMePicked clears customRole
├── state/selectors.test.ts               # +gating tests for the OR rule
├── components/CustomRoleInput.test.tsx   # NEW
├── components/ArchetypeGrid.test.tsx     # +disabled state coverage
└── e2e/role-custom.e2e.ts                # NEW Playwright spec

backend/src/main/java/com/aiavatar/alterego/
├── model/
│   ├── Archetype.java                    # +HR / ADMINISTRATION / CUSTOMER_RELATIONS enum values
│   ├── AlterEgoUserSelections.java       # archetype relaxed from @NotNull; +String customRole (@Size max=100); +class-level @RoleOfRecordPresent; +roleLabel() helper
│   ├── AlterEgoRequest.java              # +customRole; +roleLabel() helper
│   └── validation/
│       ├── RoleOfRecordPresent.java      # NEW class-level annotation
│       └── RoleOfRecordPresentValidator.java  # NEW validator: archetype != null OR customRole.trim() non-empty
├── service/
│   ├── AlterEgoService.java              # generate(userSelections,…) passes customRole through; text-overlay reads roleLabel()
│   ├── AccentResolver.java               # nullable archetype: when null, use a stable default key for accent (BACKEND_DEV); curated map untouched
│   ├── fallback/FallbackPosterProvider.java  # accepts null archetype and treats it as a stable default for accent/fallback art
│   ├── gemini/GeminiPromptBuilder.java   # reads request.roleLabel() instead of label(ROLE_LABELS, request.archetype())
│   ├── gemini/GeminiCharacterPromptBuilder.java  # same swap (bio prompt also consumes roleLabel())
│   └── falai/FalAiPromptBuilder.java     # same swap

backend/src/test/java/com/aiavatar/alterego/
├── model/AlterEgoUserSelectionsValidationTest.java  # NEW — class-level constraint coverage
├── service/gemini/GeminiPromptBuilderTest.java      # +custom role label is substituted verbatim
├── service/gemini/GeminiCharacterPromptBuilderTest.java   # +same
├── service/falai/FalAiPromptBuilderTest.java        # +same
└── controller/AlterEgoControllerIntegrationTest.java # +happy path with customRole + archetype absent
```

**Structure Decision**: Existing two-tier `frontend/` + `backend/` layout from 001 onwards. No new top-level directories. The new frontend component (`CustomRoleInput.tsx`) lives alongside the other Setup components; the new backend validator lives in the existing `model/validation/` package next to `ValidFirstName`.

## Phase 0 — Research

See [research.md](./research.md) for the resolved-decisions catalogue covering:

- **R1**: Where the "Role" label / `Archetype` enum sits in the codebase and which call sites need updating
- **R2**: How to widen the wire contract without breaking older clients
- **R3**: How to relax `archetype` from `@NotNull` without losing the OR-of-fields invariant
- **R4**: How `AccentResolver` (keyed on `Archetype` × `Universe`) behaves when archetype is null
- **R5**: How to make a CSS-blurred + pointer-events-none grid genuinely keyboard-inert
- **R6**: How `SurpriseMePicked` should interact with `customRole` in the reducer
- **R7**: Why neither the bio prompt nor the image prompt needs a "custom vs. prefab" branch
- **R8**: Why the text-overlay path doesn't need to truncate harder than today
- **R9**: How to test the no-rendered-text guarantee for custom strings without adding an OCR dependency
- **R10**: Icons for the three new prefab options

## Phase 1 — Design & Contracts

### Entities

See [data-model.md](./data-model.md) for the full entity catalogue.

### Wire contract

The contract delta is additive — see [contracts/alter-egos.openapi.delta.yaml](./contracts/alter-egos.openapi.delta.yaml):

- `selections.archetype` enum widens with three new values: `hr`, `administration`, `customer-relations`. (Existing six values keep their wire strings + relative order.)
- `selections.customRole` (new, optional, `string`, `maxLength: 100`, may be omitted, empty string, or non-empty after trim).
- Class-level constraint: `archetype` is required UNLESS `customRole` is present AND its trimmed value is non-empty. Violations return RFC 7807 `400 Bad Request` (existing handler).
- No other field is changed. The response shape is byte-identical to today's.

### Backwards compatibility

- Old clients (002..021) that send only `archetype` (and no `customRole` field at all) continue to work — `customRole` is treated as absent and the role-of-record falls back to `archetype.label()`.
- The three new prefab `archetype` values are additive — old backends would 400 on them, but feature 022 ships frontend + backend together so this is a non-issue in practice (single repo, single deploy).

### Constitution Check (post-design re-evaluation)

| Principle | Compliance | Notes |
|---|---|---|
| I. Modern & Secure Stack | ✅ | Unchanged. |
| III. Test-First | ✅ | Test files in the structure tree above land first in commit order (see `/speckit.tasks` output). |
| IV. Resilient HTTP | ✅ | Wire surface widens additively; resilient-fetch + FE-side fallback paths are untouched. |
| V. Feature Branch Workflow | ✅ | Single PR from `022-role-options-custom` → `main`. |
| VI. Zero Deprecated Deps | ✅ | `npm audit` + `./gradlew dependencyCheckAnalyze` continue to pass. |

**No violations introduced by the Phase 1 design. No Complexity Tracking entries.**

## Complexity Tracking

> Empty — no Constitution-Check violations to justify.

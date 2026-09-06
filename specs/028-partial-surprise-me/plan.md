# Implementation Plan: Partial Surprise Me — preserve explicit picks

**Branch**: `028-partial-surprise-me` | **Date**: 2026-05-20 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/028-partial-surprise-me/spec.md`

## Summary

Flip the 009 Surprise Me action from "overwrite everything" to "fill in the blanks." Any category whose current session value is non-empty — whether the user typed/clicked it or a previous Surprise Me wrote it — is preserved on the next click; only *empty* categories get a uniform random draw. Only **Start Over** and a **browser page refresh** clear category state. The wire contract, the random selector, the gating rule, the email + first-name handling, the auto-tab-switch animation, and the post-image pipeline are all unchanged. This is a behavioral pivot inside the existing `surprise()` hook + the existing `SurpriseMePicked` reducer branch — no new component, no new UI surface, no new action.

**Technical approach**: pure frontend, no reducer schema change, no new selector slot, no new wire field. Two surgical edits — (1) `surprise()` in `useGenerateAlterEgo.ts` reads the current session before rolling and overrides the random pick for any non-empty category; (2) `SurpriseMePicked`'s reducer branch stops clearing `customRole` and stops blindly overwriting `archetype` / `universe` / `artStyle` when those slots are already populated. The 022 precedence rule between prefab Role and Custom Role becomes load-bearing: it is the invariant that lets a single non-empty Role slot count as explicit without provenance tracking. Frontend-only feature; backend untouched.

## Technical Context

**Language/Version**: TypeScript 5.7 (strict). Frontend only — no backend code change.
**Primary Dependencies**: React 19, Vite 8, Vitest + React Testing Library + Playwright (existing). **No new runtime, test, or build dependency.**
**Storage**: N/A — inherits 001 FR-016 / FR-017 / FR-024 no-persistence posture. Session state continues to live in the browser tab for the lifetime of the tab; Start Over and a page refresh are the only clearing mechanisms (spec FR-2814).
**Testing**: Vitest + RTL for the reducer-branch delta, the `surprise()` hook delta, and the selector-side classification helper. The existing Playwright E2E suite already covers the Setup → Generate → Alter Ego happy path; one new Playwright scenario covers US1 (preserve one explicit pick across a Surprise Me click).
**Target Platform**: Modern evergreen browsers (Chromium / Firefox / Safari) on desktop + mobile, same as 023 / 025.
**Project Type**: Web application (`frontend/` + `backend/`); this feature touches `frontend/` only.
**Performance Goals**: Click → tab switch → request fire MUST be perceptually instant — same paint frame as today's 009 / 025 path (FR-2809). The classification step is O(visible-categories) — currently three constant-time checks — so it is unmeasurable relative to the surrounding mutation tick.
**Constraints**: No backend change. No new HTTP call. No wire-contract change (FR-2812). No new state slot. The 022 prefab-vs-custom Role precedence invariant is reused, not duplicated. The 023 `email` and the first-name pass-through (FR-2813) are preserved without new code.
**Scale/Scope**: ≈ 1 reducer-branch edit (`SurpriseMePicked` in `state/reducer.ts`), 1 hook edit (`surprise()` in `hooks/useGenerateAlterEgo.ts`), 1 new pure helper (`mergeWithSurprise` or similar — name finalised in research.md) to compose the classification + roll, ≈ 5 new test cases across reducer + hook + selector tiers, and 1 new Playwright scenario. Surface area is the same order as 025.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Justification |
|---|---|---|
| I. Modern & Secure Technology Stack | ✅ | No new dependency. Reuses React 19 / Vite 8 / Vitest / Playwright already pinned in `frontend/package.json`. |
| III. Test-First Development (TDD) | ✅ | New behavior lands as failing-first tests in three tiers — reducer (5 cases: empty / one-explicit / both-explicit / custom-role-explicit / repeat-click-no-reroll), hook (2 cases: explicit pass-through + roll merge), Playwright E2E (1 case: US1). Frontend coverage stays ≥ 90% — the new code is small and fully exercised. Feature is FE-only so the backend integration-test clause is dormant. |
| IV. Resilient HTTP Communication | ✅ | This feature adds no new HTTP call. The Generate path (POST `/api/v1/alter-egos`) is unchanged — its 5-attempt exponential-back-off retry + stub fallback policy (003 / 016) is inherited verbatim through the existing `surprise()` → `mutation.mutate()` reuse. |
| V. Feature Branch Workflow | ✅ | Branch `028-partial-surprise-me` cut from `main` per `create-new-feature.sh` numbering. |
| VI. Zero Deprecated Dependencies | ✅ | No new dependency to audit. |
| VII. Layer Convention (backend) | N/A | Frontend-only feature — backend layers untouched. |
| VIII. Provider Seam | N/A | No provider code touched. |
| IX. Test Pyramid | ✅ | Backend pyramid untouched. Frontend additions sit in the unit tier (Vitest reducer + hook + selector tests) plus one Playwright scenario in the existing E2E folder. No new Spring slice, no new contract suite. |

No gate violations. **Complexity Tracking** section is empty (see below).

## Project Structure

### Documentation (this feature)

```text
specs/028-partial-surprise-me/
├── plan.md              # This file
├── spec.md              # /speckit.specify + /speckit.clarify output
├── research.md          # Phase 0 — classification placement, reducer-branch shape, merge function design
├── data-model.md        # Phase 1 — AlterEgoSession delta (none) + new behavioral invariants
├── contracts/           # Phase 1 — frontend UI contract only (no HTTP)
│   └── ui-contract.md
├── quickstart.md        # Phase 1 — reviewer/operator validation steps for US1..US4
├── checklists/
│   └── requirements.md  # /speckit.specify validation checklist
└── tasks.md             # Phase 2 — /speckit.tasks output (NOT created here)
```

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── hooks/
│   │   │   ├── useGenerateAlterEgo.ts          # MODIFIED — surprise() reads session, merges picks with explicit values before dispatching SurpriseMePicked + building Selections
│   │   │   └── useGenerateAlterEgo.test.tsx    # MODIFIED — two new cases: pass-through for explicit; merge for partial
│   │   ├── lib/
│   │   │   ├── randomSelections.ts             # UNCHANGED — the uniform draw stays as-is
│   │   │   ├── mergeSurpriseWithExplicit.ts    # NEW — pure helper: (session, fullRoll) → SurpriseMePicks (final picks, explicit values winning)
│   │   │   └── mergeSurpriseWithExplicit.test.ts  # NEW — covers FR-2801..FR-2806 in isolation, no React
│   │   ├── state/
│   │   │   ├── reducer.ts                      # MODIFIED — SurpriseMePicked branch: drop the unconditional customRole clear, drop the unconditional overwrite of archetype/universe/artStyle; instead, write only into empty slots (FR-2805 + FR-2814)
│   │   │   ├── reducer.test.ts                 # MODIFIED — extend existing 'SurpriseMePicked (009 / 020)' + 'SurpriseMePicked clears customRole (022 / US3)' suites; rename the latter to 'SurpriseMePicked preserves customRole (028)' and invert its assertions
│   │   │   └── selectors.ts                    # UNCHANGED — isReadyToSurprise / missingInputsForSurprise are gate-only and don't depend on the new behavior
│   │   └── components/
│   │       └── SurpriseMeButton.tsx            # UNCHANGED — button surface (label, ARIA, position, gating) is untouched per spec § Assumptions
│   └── e2e/                                    # OR wherever Playwright specs live
│       └── partial-surprise-me.spec.ts         # NEW — single scenario exercising US1: pick Universe, leave Role + ArtStyle empty, click Surprise Me, assert request body + post-response Setup state
└── ...

backend/                                        # UNCHANGED — no Java / Spring file touched
```

**Structure Decision**: The repository's web-application split (`frontend/` + `backend/`) established by 002 and reaffirmed by 024 is preserved. This feature is a pure frontend refinement on top of 009's Surprise Me action and 022's Custom Role precedence — no new top-level directory, no module boundary crossed. The one new helper file (`mergeSurpriseWithExplicit.ts`) sits next to the existing `randomSelections.ts` in `lib/` and follows the same "pure utility, no React, injectable RNG" pattern that 009 introduced.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

*(empty — no gate violations)*

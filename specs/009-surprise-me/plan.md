# Implementation Plan: Surprise Me Button

**Branch**: `009-surprise-me` (artefacts); dev on `claude/speckit-implementation-D6gzi`
**Date**: 2026-04-24
**Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `specs/009-surprise-me/spec.md` (issue #9)

## Summary

Add a "Surprise Me" button to the Setup tab that, when clicked, picks one option uniformly at random from each of the five category lists defined in `frontend/src/features/alterego/options.ts` (Pose, Archetype, Universe, Vibe, Art Style), commits those picks to the session via the existing `*Selected` reducer actions, and then fires the same mutation pipeline the normal Generate button uses. A single new reducer action `SurpriseMePicked` is introduced so all five picks land in one React commit (preserving `useReducer`'s "one dispatch per render" ergonomics and keeping reducer tests against a single well-named action rather than five chained dispatches). Button enablement is a new selector `isReadyToSurprise(state) → boolean` — a less strict sibling of `isReadyToGenerate` that requires only photo + firstName. The randomizer itself is a pure utility `randomSelections(rng?)` that takes an optional RNG for deterministic tests; production code passes `Math.random`. The outbound request path, the tab auto-switch, the 007 gating, the 003 fallback semantics, and the Start-over reset are all reused verbatim — no contract change, no backend delta, no new HTTP call.

## Technical Context

**Language/Version**: TypeScript 5.x strict (frontend) — unchanged from 002..008.
**Primary Dependencies**: React 19, Vite 8, TanStack Query v5 (reused via `useGenerateAlterEgo`). No new dependency.
**Storage**: N/A (no persistence — consistent with 001 FR-016 / 017 / 024; spec FR-911 restates this for Surprise Me).
**Testing**: Vitest + React Testing Library (unit/component); Playwright (E2E). Unchanged.
**Target Platform**: Evergreen browsers already targeted by the existing shell (Chromium, Firefox, WebKit).
**Project Type**: Web application (React SPA under `frontend/`).
**Performance Goals**: Randomization budget ≤ 1 ms per click (NFR-901 — trivially held by five `Math.floor(Math.random() * n)` calls). Time-to-poster within 5 % of normal Generate (SC-904).
**Constraints**: No new runtime dependency; no new HTTP call (spec FR-911); must not change the outbound `Selections` wire shape (backend contract unchanged); unit test line coverage ≥ 90 % on the changed files (Constitution Principle III); must compose with 007 tab gating (Surprise Me during a normal Generate in-flight stays disabled); must preserve the 005 entrance animation (Surprise Me clicks set `reason: 'generate'` exactly like Generate does).
**Scale/Scope**: Single feature touching ≈ 8 frontend files (`reducer.ts` + test, `selectors.ts` + test, new `lib/randomSelections.ts` + test, new `components/SurpriseMeButton.tsx` + test, `SetupLayout.tsx`, `hooks/useGenerateAlterEgo.ts`) plus one CSS block in `styles/` and one Playwright spec. No backend file changes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | PASS | No new dep; React 19 / TS strict; pure React + ARIA + CSS. |
| III. Test-First Development (TDD) | PASS | Red-Green-Refactor on: (a) `randomSelections` (pure, seeded RNG makes assertions deterministic — uniform pick from each category, independence across categories, no NaN / out-of-range index), (b) `reducer` — new `SurpriseMePicked` action commits all five category values in one transition, (c) `selectors` — `isReadyToSurprise` truth table (photo × firstName × phase) with the FR-902 invariant `isReadyToGenerate(s) ⇒ isReadyToSurprise(s)` (spec Edge Cases), (d) `SurpriseMeButton` — disabled state rendering + keyboard activation + hint copy, (e) `useGenerateAlterEgo.triggerSurprise()` fires the same action sequence a normal Generate does, (f) Playwright E2E for US1 + US2. Coverage gate ≥ 90 % holds because every new branch is exercised. |
| IV. Resilient HTTP Communication | N/A (reuses existing) | Surprise Me reuses the existing resilient client in `services/alterEgoClient.ts`. No new HTTP call is introduced (spec FR-911). |
| V. Feature Branch Workflow | PASS (with deviation) | Development is pinned to `claude/speckit-implementation-D6gzi` by explicit task instruction; the SpecKit artefacts live under `specs/009-surprise-me/` so the 3-digit sequential prefix stays discoverable. Mirrors the deviation already used for 005 and 007. PR description will note the branch pinning. |
| VI. Zero Deprecated Dependencies | PASS | Zero additions → zero `npm audit` delta. |

**Gate verdict**: PASS. No unjustified violations. One documented branch-naming deviation (matching precedent set by 005 and 007).

## Project Structure

### Documentation (this feature)

```text
specs/009-surprise-me/
├── plan.md              # This file
├── research.md          # Phase 0: randomizer placement, single-dispatch-vs-chained, button gating predicate, RNG seam, reuse of useGenerateAlterEgo, 007 composition, 005 animation
├── data-model.md        # Phase 1: session-shape unchanged; new action `SurpriseMePicked`; new selector `isReadyToSurprise`; utility `randomSelections(rng?)`
├── quickstart.md        # Phase 1: how to run & manually validate the 3 user stories + the edge cases
├── checklists/
│   └── requirements.md  # Spec-quality checklist (from /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

No `contracts/` directory is produced for this feature — the backend `Selections` contract is byte-identical to 006's (FR-906, A-903). The 002 / 003 / 006 OpenAPI surface is untouched.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── lib/
│   │   │   ├── randomSelections.ts                # NEW: pure utility — pickOne<T>(list, rng) + randomSelections(rng?)
│   │   │   └── randomSelections.test.ts           # NEW: seeded-RNG determinism + uniform pick + out-of-range guard
│   │   ├── state/
│   │   │   ├── reducer.ts                         # + `SurpriseMePicked` action + case that overwrites pose/archetype/universe/artStyle/vibe in one transition (phase → 'picking')
│   │   │   ├── reducer.test.ts                    # + SurpriseMePicked covers all five; phase lands on 'picking'; prior picks are overwritten
│   │   │   ├── selectors.ts                       # + `isReadyToSurprise(state): boolean` (photo && firstName.trim() && phase !== 'generating')
│   │   │   └── selectors.test.ts                  # + truth table + invariant `isReadyToGenerate ⇒ isReadyToSurprise`
│   │   ├── components/
│   │   │   ├── SurpriseMeButton.tsx               # NEW: mirrors GenerateButton's shape — accessible disabled-state, hint copy listing only photo/name, keyboard-activatable
│   │   │   ├── SurpriseMeButton.test.tsx          # NEW: enabled/disabled transitions, hint text, Enter/Space activation, click-no-op-when-disabled
│   │   │   └── SetupLayout.tsx                    # + render SurpriseMeButton next to GenerateButton; pass onSurprise callback
│   │   ├── hooks/
│   │   │   ├── useGenerateAlterEgo.ts             # + `surprise()` method: rng-pick → dispatch SurpriseMePicked → submit (photoBlob, selections built from the picks); preserves the onMutate ordering so 007 gating + 005 animation fire exactly once
│   │   │   └── useGenerateAlterEgo.test.tsx       # + surprise-path action sequence + payload invariants
│   │   └── AlterEgoPage.tsx                       # + wire onSurprise = () => { if (!isReadyToSurprise) return; surprise({photoBlob, firstName}) }
│   └── styles/                                    # + .surprise-me-button CSS block (accent-distinct from Generate; keeps action row layout-stable on narrow viewports per NFR-902)
└── tests/
    └── e2e/
        └── surprise-me.spec.ts                    # NEW: no-category-picks happy path; Setup reflects picks after return; disabled without name/photo; does not regress normal Generate
```

**Structure Decision**: Single web-app layout under the existing `frontend/` tree. Feature 002's `features/alterego/` module owns all the relevant seams — reducer, selectors, the generate mutation, and the Setup layout — so this feature amends those files in place and adds two new small siblings (`lib/randomSelections.ts`, `components/SurpriseMeButton.tsx`). The RNG is injected as an optional parameter (`Math.random` default) rather than a module-level import so tests can pass a seeded RNG and assert exact picks — the smallest seam compatible with deterministic testing. Backend is untouched; no Gradle or Java file is in scope.

## Complexity Tracking

*No Constitution violations to justify.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *(none)* | — | — |

## Phase Outputs

- **Phase 0 Research**: [./research.md](./research.md) — decisions on (R1) where randomization lives (component / hook / reducer / utility), (R2) one aggregate action vs five chained dispatches, (R3) RNG seam for deterministic tests, (R4) reuse strategy for `useGenerateAlterEgo`, (R5) predicate shape for `isReadyToSurprise`, (R6) composition with 007 tab gating, (R7) preservation of the 005 entrance animation, (R8) button placement in `SetupLayout`, (R9) accessibility parity (aria-label, disabled-state, keyboard), (R10) Vibe inclusion in the randomization set.
- **Phase 1 Design**: [./data-model.md](./data-model.md) — documents the `SurpriseMePicked` action payload, the `isReadyToSurprise` predicate contract, and the `randomSelections(rng?)` signature. [./quickstart.md](./quickstart.md) — how to run the feature locally, the three manual validation flows (fresh / reseed-then-Generate / disabled-until-name), and where the new tests live.

No `contracts/` produced; no `update-agent-context.sh` technology delta (identical stack to 002..008, already captured in `CLAUDE.md`).

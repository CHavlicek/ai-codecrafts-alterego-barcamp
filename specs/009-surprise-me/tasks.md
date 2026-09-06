---
description: "Task list for feature 009-surprise-me"
---

# Tasks: Surprise Me Button (009)

**Input**: Design documents from `/specs/009-surprise-me/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, quickstart.md

**Tests**: MANDATORY per Constitution Principle III (Test-First Development). Every task below with a `test` filename MUST be written and committed in a failing state before the matching implementation task is committed. Unit line coverage MUST reach ≥ 90% on the new files.

**Organization**: Tasks are grouped by the three user stories from `spec.md`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 — see spec.md
- File paths are absolute from repo root.

## Path Conventions

Frontend under `frontend/` (React 19 + TypeScript strict, Vite 8). Backend is not touched by this feature (Surprise Me is frontend-only per spec FR-911, A-903).

- Unit/component tests (Vitest + RTL): colocated `<Thing>.test.ts` / `.test.tsx`
- E2E journey tests (Playwright): `frontend/tests/e2e/<journey>.spec.ts`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: None — the feature reuses the existing `frontend/` scaffold and the 002 `features/alterego/` module. No project initialization, no new tooling, no new dependency.

*(No Phase 1 tasks.)*

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pure helpers that every user story builds on. These MUST land first because US1, US2, and US3 all call `randomSelections`, `isReadyToSurprise`, and (for the hint copy) a shared missing-input-labelling helper.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T001 [P] Write failing test for `pickOne` + `randomSelections` in `frontend/src/features/alterego/lib/randomSelections.test.ts` — covers (a) seeded RNG `() => 0` picks index 0 in every category; (b) seeded RNG `() => 0.999` picks last index; (c) each of the five categories is drawn from its matching options array (enumerate `POSE_OPTIONS.map(o => o.value)` etc.); (d) `pickOne(emptyList)` throws; (e) clamps on out-of-contract `rng() = 1`. Verify the test file imports from the yet-to-exist `./randomSelections` and fails with a module-not-found / type error.
- [X] T002 Implement `pickOne<T>` + `randomSelections` in `frontend/src/features/alterego/lib/randomSelections.ts` per the contract in `specs/009-surprise-me/data-model.md` § "New Utility". Imports the five `*_OPTIONS` arrays from `../options`; exports `randomSelections(rng?: () => number): SurpriseMePicks` and `pickOne<T>(list, rng?): T`. Confirms T001 passes.
- [X] T003 Extract the existing `FIELD_LABELS` record + `formatMissingList` helper from `frontend/src/features/alterego/components/GenerateButton.tsx` into a new shared module `frontend/src/features/alterego/lib/missingInputHint.ts` (moved verbatim; exports `FIELD_LABELS`, `formatMissingList`). Update `GenerateButton.tsx` to import from the new module. No behavior change. Add a colocated `missingInputHint.test.ts` covering the four list-length branches (0, 1, 2, 3+) — test MUST be written first and fail before the extraction commit that makes it pass. This keeps the US3 Surprise Me hint copy and the existing Generate hint copy coming from a single source of truth.

**Checkpoint**: Foundation ready — `randomSelections` and the shared hint helper are importable by every user story.

---

## Phase 3: User Story 1 — Surprise Me generates an alter ego with no category picks (Priority: P1) 🎯 MVP

**Goal**: A user with just a photo and a name can click Surprise Me and receive an AI-generated alter ego, with the request payload carrying valid random picks for all five categories.

**Independent Test**: Run the Playwright spec from `quickstart.md` § "US1". Assert the outbound request body contains valid enum values for `pose`, `archetype`, `universe`, `artStyle`, and `vibe`; assert the Your Alter Ego tab becomes active and the poster renders.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T004 [P] [US1] Extend `frontend/src/features/alterego/state/reducer.test.ts` with `SurpriseMePicked` case coverage: (a) assigns all five category fields from the payload; (b) transitions `phase` to `'picking'`; (c) overwrites any prior user picks in the same session (explicit setup: start with `pose: 'heroic'`, assert it becomes the payload's pose); (d) does NOT mutate `firstName`, `photoBlob`, `photoPreviewUrl`, `activeTab`, `generateAutoSwitchNonce`, `errorMessage`, `result`; (e) **FR-913 guard**: after `SurpriseMePicked`, a subsequent `PhotoSelected` and a subsequent `FirstNameChanged` each leave `pose / archetype / universe / vibe / artStyle` unchanged. Tests MUST reference the new action type and fail to compile until T008 lands.
- [X] T005 [P] [US1] Extend `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx` with a `surprise({ photoBlob, firstName })` test: (a) dispatches `SurpriseMePicked` first; (b) then the existing `ActiveTabChanged → 'alter-ego' (reason: 'generate')`; (c) then `GenerateSubmitted`; (d) outbound request body carries the exact selections committed by `SurpriseMePicked` (stub `randomSelections` with a deterministic one-shot via `vi.mock` or pass a seeded RNG if the hook accepts one; preferred: spy on the dispatch sequence and inspect the submitted mutation variables). MUST fail until T009 lands.
- [X] T006 [P] [US1] Create failing E2E spec `frontend/tests/e2e/surprise-me.spec.ts`: (a) take a photo (reuse the mock-camera helper from the 004 spec), (b) type a name, (c) click Surprise Me, (d) assert the loading indicator then the poster appear on the Your Alter Ego tab, (e) assert the request body (intercept via Playwright `route.request().postDataJSON()`) has non-null values for all five category fields and the exact typed name.

### Implementation for User Story 1

- [X] T007 [P] [US1] Add `SurpriseMePicks` interface to `frontend/src/features/alterego/state/reducer.ts` (export) and extend `AlterEgoAction` union with `{ type: 'SurpriseMePicked'; picks: SurpriseMePicks }`.
- [X] T008 [US1] Add the `SurpriseMePicked` reducer case in `frontend/src/features/alterego/state/reducer.ts` per `data-model.md` § "New Action: SurpriseMePicked". Imports: `SurpriseMePicks` from the same file (self-import via the type). Confirms T004 passes. (Depends on T007.)
- [X] T009 [US1] Add `surprise({ photoBlob, firstName })` method to `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts`: imports `randomSelections` from `../lib/randomSelections`, dispatches `SurpriseMePicked` with the picks, then calls the existing `mutation.mutate({ photoBlob, selections })` with a `Selections` object built from the picks + trimmed `firstName`. Export: add `surprise` to the hook's return shape next to `submit` and `isPending`. Preserves FR-908 byte-identical post-generation path. Confirms T005 passes.
- [X] T010 [US1] Wire the hook's new `surprise` into `frontend/src/features/alterego/AlterEgoPage.tsx`: add `const handleSurprise = () => { if (!state.photoBlob || state.firstName.trim().length < 1) return; surprise({ photoBlob: state.photoBlob, firstName: state.firstName }) }`. Pass `onSurprise={handleSurprise}` down through `SetupLayout` props. For the US1 E2E (T006), which runs before US3 ships the real button, `SetupLayout` should render a single hidden-but-present element with `data-testid="surprise-me-trigger"` wired to `onSurprise` — the Playwright spec clicks that element. US3's T019 replaces that hidden element with the real `<SurpriseMeButton>` (the `data-testid` stays on the real button so the E2E spec is unchanged between phases). No scratch code past T021.

**Checkpoint**: Surprise Me mechanic works end-to-end at the hook + reducer layer. The button UI is still US3's responsibility — US1's MVP can be validated either by calling `handleSurprise` directly in the E2E test or by temporarily binding it to the existing Generate button for the duration of this phase.

---

## Phase 4: User Story 2 — Surprise Me reflects the randomly chosen options back in the Setup form (Priority: P1)

**Goal**: After a Surprise Me click resolves, returning to the Setup tab shows exactly one selected option in each of the five category grids.

**Independent Test**: After a Surprise Me lands, click back to the Setup tab; assert `aria-checked="true"` exists on exactly one option in each of the five grids; assert that a subsequent Generate click uses those same values as the payload.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T011 [P] [US2] Extend `frontend/src/features/alterego/state/reducer.test.ts` with an ordering invariant: after dispatching `SurpriseMePicked` followed by `ActiveTabChanged → 'alter-ego' (reason: 'generate')` + `GenerateSubmitted`, the five category fields still hold the `SurpriseMePicked` picks (i.e. the two subsequent actions do NOT reset them). MUST fail until the reducer case in T008 is stable — if T008 landed correctly, this test will already pass, so frame it as a regression guard.
- [X] T012 [P] [US2] Extend `frontend/tests/e2e/surprise-me.spec.ts` with a "Setup reflects picks" step after the poster renders: click back to tab 1, assert `aria-checked="true"` on one option in each of the five `radiogroup`s; pick one different option in one grid, click Generate, assert the second request carries that overridden value plus the four previously-randomised values (covers Acceptance 3 of US2).

### Implementation for User Story 2

- [X] T013 [US2] (No new production code beyond T008.) Confirm that the existing `*Grid` components (`PoseGrid`, `ArchetypeGrid`, `UniverseGrid`, `VibeGrid`, `ArtStyleGrid`) already render `aria-checked="true"` based on the `value` prop sourced from `session.*`, and that `SetupLayout` re-renders when the reducer state changes — both are already true after 006/007. Document the verification in a code comment referencing spec FR-907. Mark the phase green when T011 + T012 pass.

**Checkpoint**: The Setup form now correctly reflects the Surprise Me picks across tab switches and survives a subsequent manual override + re-generate loop.

---

## Phase 5: User Story 3 — Surprise Me respects Name and photo gating (Priority: P2)

**Goal**: The Surprise Me button is a first-class UI citizen: visible on the Setup tab, disabled until both photo and Name are present, accessible to keyboard + screen reader users, and inert during an in-flight generation.

**Independent Test**: Manual + Playwright: confirm the button renders, honours the four disabled-state conditions from the spec Edge Cases, announces its disabled state to assistive tech, and only triggers the flow when clicked in an enabled state.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T014 [P] [US3] Create failing unit test `frontend/src/features/alterego/state/selectors.test.ts` additions: (a) `isReadyToSurprise` truth table — photo × firstName × phase, enumerating at least the 8 boundary cases; (b) invariant: for a random sample of realistic sessions, `isReadyToGenerate(s)` implies `isReadyToSurprise(s)`; (c) `missingInputsForSurprise` returns `['photo', 'firstName']` for an empty session, `['firstName']` when only photo is present, `['photo']` when only firstName is present, `[]` when both are present. Tests MUST fail with a module-export error until T016 lands.
- [X] T015 [P] [US3] Create failing component test `frontend/src/features/alterego/components/SurpriseMeButton.test.tsx`: (a) renders button with accessible name "Surprise Me"; (b) disabled + `aria-disabled="true"` when session has no photo / no name / `phase === 'generating'`; (c) hint text from `formatMissingList` when disabled, no hint when enabled; (d) click no-op when disabled (fires `onSurprise` zero times); (e) Enter and Space activate when enabled; (f) click calls `onSurprise` exactly once when enabled; (g) `aria-describedby` wired to the hint node. Tests MUST fail until T017 lands.
- [X] T016 [P] [US3] Extend `frontend/tests/e2e/surprise-me.spec.ts` with the gating scenarios from spec US3 Acceptance 1..4: disabled-without-photo, disabled-without-name, disabled-with-neither, disabled-while-Generate-in-flight.

### Implementation for User Story 3

- [X] T017 [US3] Add `isReadyToSurprise` and `missingInputsForSurprise` (plus `SurpriseRequiredInput` type) to `frontend/src/features/alterego/state/selectors.ts` per `data-model.md` § "New Selector". Confirms T014 passes.
- [X] T018 [US3] Create `frontend/src/features/alterego/components/SurpriseMeButton.tsx` per `data-model.md` § "New Component". Reuses `formatMissingList` + `FIELD_LABELS` from `lib/missingInputHint.ts` (T003). Same CSS class naming convention as `GenerateButton` (e.g. `.surprise-me-button`, `.surprise-me-button__hint`). Confirms T015 passes.
- [X] T019 [US3] Render `<SurpriseMeButton>` next to `<GenerateButton>` in `frontend/src/features/alterego/components/SetupLayout.tsx`: wrap them in a `div.setup-layout__actions` flex row; `SetupLayout` gains an `onSurprise: () => void` prop, threaded from `AlterEgoPage`.
- [X] T020 [US3] Add CSS for `.surprise-me-button`, `.surprise-me-button__hint`, and `.setup-layout__actions` in the existing styles file (inspect `frontend/src/styles/` structure; most likely adds to the same stylesheet that hosts `.generate-button`). Accent must visibly differ from Generate (e.g. a distinct outline or secondary token) without introducing a new runtime dependency. Preserves NFR-902 (no layout shift).
- [X] T021 [US3] Remove the temporary MVP rig noted in T010 (if any); `AlterEgoPage.handleSurprise` is now wired to the real `SurpriseMeButton` via `onSurprise`. Confirms T016 passes end-to-end.

**Checkpoint**: All three user stories are independently functional. The feature is ready for polish.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final quality gates and documentation alignment.

- [X] T022 [P] Run `npm run lint` from `frontend/` — resolve any new warnings on the files this feature touched.
- [X] T023 [P] Run `npm test -- --coverage` from `frontend/` — confirm line coverage ≥ 90 % on every new or modified file (`randomSelections.ts`, `missingInputHint.ts`, `reducer.ts`, `selectors.ts`, `SurpriseMeButton.tsx`, `useGenerateAlterEgo.ts`, `SetupLayout.tsx`, `AlterEgoPage.tsx`).
- [X] T024 [P] Run `npm run build` from `frontend/` — confirm a clean production build with no TypeScript or Vite errors.
- [X] T025 Run the full Playwright E2E suite from `frontend/` (`npm run test:e2e` or equivalent) to confirm no regression in existing specs (`tab-access-gating.spec.ts`, the 002/004/005/006 specs).
- [X] T026 Run through `specs/009-surprise-me/quickstart.md` manually in a real browser — US1, US2, US3, and the Edge Cases section — and tick each one off.
- [X] T027 [P] Add a "Recent Changes" bullet for 009 in `CLAUDE.md` (the `update-agent-context.sh` tool should already have added the tech delta — this task adds the one-paragraph summary next to the 007 and 008 bullets).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 Setup**: No tasks (feature reuses existing scaffold).
- **Phase 2 Foundational (T001–T003)**: BLOCKS all user stories.
- **Phase 3 US1 (T004–T010)**: Depends on Phase 2. Delivers the MVP.
- **Phase 4 US2 (T011–T013)**: Depends on Phase 3 for the reducer case (T008 is the thing US2 asserts the invariants of).
- **Phase 5 US3 (T014–T021)**: Depends on Phase 2 for the hint helper. US3's `SurpriseMeButton` can be built in parallel with US1 mechanics, but T021 depends on T010 (removes the temporary rig).
- **Phase 6 Polish**: Depends on Phase 5.

### User Story Dependencies

- **US1**: independent MVP — can be validated with a temporary direct-invocation E2E rig without the button UI (see T010).
- **US2**: functionally dependent on US1 (the reducer case is what US2 verifies survives), but no additional production code.
- **US3**: independent of US2 but gated on T003 (shared hint helper) from Phase 2.

### Within Each User Story

- Tests first (Principle III): T001 → T002; T004 → T008; T014 → T017; T015 → T018; T006 → T009 + T010.
- Reducer types before reducer cases: T007 → T008.
- Reducer before hook: T008 → T009.
- Hook before page wiring: T009 → T010.
- Selector before button: T017 → T018.
- Button before layout: T018 → T019.
- Layout before E2E flip: T019 → T021.

### Parallel Opportunities

- **Phase 2**: T001 and T003 can run in parallel; T002 depends on T001.
- **Phase 3 tests**: T004, T005, T006 in parallel.
- **Phase 3 implementation**: T007 can run in parallel with T006 (different files).
- **Phase 5 tests**: T014, T015, T016 in parallel.
- **Phase 6**: T022, T023, T024, T027 in parallel.

---

## Parallel Example: User Story 1 test phase

```bash
# Launch all three US1 failing tests in parallel:
Task: "Extend frontend/src/features/alterego/state/reducer.test.ts with SurpriseMePicked case coverage (T004)"
Task: "Extend frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx with the surprise() action sequence (T005)"
Task: "Create frontend/tests/e2e/surprise-me.spec.ts with the US1 happy-path scenario (T006)"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Phase 2 foundation (T001–T003).
2. Phase 3 tests (T004–T006) — confirm all three fail.
3. Phase 3 implementation (T007–T010).
4. **STOP and VALIDATE**: run `surprise-me.spec.ts` US1 scenario; confirm the flow works end-to-end.
5. Defer the button UI; the temporary rig in T010 is enough to demo.

### Incremental Delivery

1. MVP shipped behind the temporary rig.
2. Layer US2 (T011–T013) — no new production code, just guards.
3. Layer US3 (T014–T021) — the button becomes a first-class UI citizen.
4. Polish (T022–T027).

---

## Notes

- [P] tasks touch different files with no ordering dependency.
- The backend is not touched by this feature.
- The temporary MVP rig in T010 is short-lived — T021 removes it as soon as US3 lands.
- Commit after each task or logical group; the first commit of each user story should be the failing tests.
- FR numbers referenced throughout map to `specs/009-surprise-me/spec.md`.

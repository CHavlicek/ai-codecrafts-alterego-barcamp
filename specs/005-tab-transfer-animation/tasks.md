---

description: "Task list for feature 005-tab-transfer-animation"
---

# Tasks: Tab Transfer Animation on Generate

**Input**: Design documents from `/specs/005-tab-transfer-animation/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

**Tests**: Test tasks are MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). They MUST be written before any implementation task in the same user story, MUST fail before production code is committed, and MUST drive unit line coverage to ≥ 90 %.

**Organization**: Tasks are grouped by user story. US1 (animate Generate-triggered hand-off) is the MVP. US2 (`prefers-reduced-motion` override) is also P1 and ships alongside.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks — safe to run in parallel.
- **[Story]**: Which user story this task belongs to (US1 or US2).
- All paths are repo-root-relative; the feature is frontend-only.

## Path Conventions

Frontend (React 18+ strict TS, Vite) under `frontend/src/features/alterego/…`; Vitest colocated `*.test.tsx` next to source; Playwright journey specs under `frontend/tests/e2e/*.spec.ts`. No backend change.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the working tree is ready. No dependencies added.

- [X] T001 Confirm branch is `claude/animation-transferring-to-your-alter-ego-tab-6S2Mi`, tree is clean, and `frontend/` installs cleanly (`cd frontend && npm install`) — no new dependency introduced by this feature per FR-410.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Plumb the animation signal through the reducer layer. Both user stories consume it.

**⚠️ CRITICAL**: No user-story work can begin until this phase is complete.

- [X] T002 Extend reducer types in `frontend/src/features/alterego/state/reducer.ts`: export `type ActiveTabChangeReason = 'manual' | 'generate'`; extend the `ActiveTabChanged` action to `{ type: 'ActiveTabChanged'; tab: ActiveTab; reason?: ActiveTabChangeReason }`; add `generateAutoSwitchNonce: number` to `AlterEgoSession`; update `initialAlterEgoSession()` to return `generateAutoSwitchNonce: 0`.
- [X] T003 Update reducer logic in `frontend/src/features/alterego/state/reducer.ts`: on `ActiveTabChanged` with `reason === 'generate'`, ALWAYS produce a new state object and set `generateAutoSwitchNonce: state.generateAutoSwitchNonce + 1` (even when `state.activeTab === action.tab` — Generate re-clicks must animate per spec Acceptance 4); on `ActiveTabChanged` without `reason === 'generate'` when `state.activeTab === action.tab` return `state` unchanged to preserve referential equality; on manual tab flips leave the counter unchanged; no other action touches the counter. (Depends on T002.)
- [X] T004 [P] Update reducer unit tests in `frontend/src/features/alterego/state/reducer.test.ts` (tests-first, must fail before T003 is committed): (a) existing `ActiveTabChanged` assertion updated to also expect `generateAutoSwitchNonce === 0` on the manual flip path, (b) new test "reason: 'generate' increments generateAutoSwitchNonce and flips the tab", (c) new test "reason: 'generate' on the already-active tab still bumps the counter (no-op tab, new reference)", (d) new test "nonce survives unrelated actions (GenerateSubmitted, GenerateSucceeded) unchanged", (e) preserved test "manual no-op returns the same state reference". (Depends on T002.)

**Checkpoint**: Foundational reducer wiring complete; both user-story phases can now begin.

---

## Phase 3: User Story 1 — Pleasant hand-off on Generate (Priority: P1) 🎯 MVP

**Goal**: Clicking "Generate my alter ego" animates the incoming Alter Ego panel (cross-fade + 12 px upward slide, ~320 ms ease-out), while keeping `aria-selected` instant and preserving Setup form state.

**Independent Test**: Fill all required Setup selections with a photo, click Generate. Observe `data-animating="true"` on the Alter Ego tabpanel in the same React commit as the tab change; observe the visual fade + slide complete over ~320 ms; switch back to Setup — every input value is preserved (SC-105 invariant from 002).

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> Write these tests FIRST and confirm they FAIL before committing the implementation tasks.

- [X] T005 [P] [US1] Update `useGenerateAlterEgo` unit test in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx` to assert the captured `ActiveTabChanged` dispatch equals `{ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' }` and is dispatched BEFORE `GenerateSubmitted`.
- [X] T006 [P] [US1] Add TabsShell Vitest tests in `frontend/src/features/alterego/components/TabsShell.test.tsx`: (a) "animates in when dispatch is `{ reason: 'generate' }` — the alter-ego panel has `data-animating='true'` and class `tabs-shell__panel--animate-in`"; (b) "`onAnimationEnd` clears both the attribute and the class"; (c) "400 ms safety timeout clears the attribute even if `animationend` never fires" (use `vi.useFakeTimers()`).
- [X] T007 [P] [US1] Augment Playwright E2E `frontend/tests/e2e/tabs-shell.spec.ts` — in the existing "auto-switch on Generate" test (~line 78), add BEFORE the existing `aria-selected=true` assertion: `await expect(page.getByRole('tabpanel', { name: /your alter ego/i })).toHaveAttribute('data-animating', 'true')`. The existing `aria-selected` assertion must remain and continue to pass (FR-402 / 002 FR-108).

### Implementation for User Story 1

- [X] T008 [P] [US1] Create `frontend/src/features/alterego/hooks/useTabAnimationSignal.ts` — thin selector hook returning `{ generateAutoSwitchNonce: number }` from `useAlterEgoSession().state`.
- [X] T009 [US1] Update `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts` — change the `onMutate` dispatch at line 43 to `{ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' }`. Update the leading JSDoc to mention the `reason`. (Depends on T002.)
- [X] T010 [US1] Update `frontend/src/features/alterego/components/TabsShell.tsx` — consume `useTabAnimationSignal()`; add local `isAnimatingIn` state and `useEffect` keyed on `[generateAutoSwitchNonce]` that sets `isAnimatingIn = true` whenever the counter increments (guarded so initial mount with nonce === 0 does not fire); cleanup registers a 400 ms `setTimeout` fallback. On the alter-ego tabpanel only: add `className={['tabs-shell__panel', isAnimatingIn ? 'tabs-shell__panel--animate-in' : ''].filter(Boolean).join(' ')}`, `data-animating={isAnimatingIn ? 'true' : 'false'}`, `onAnimationEnd={() => setIsAnimatingIn(false)}`. Leave the setup panel untouched. Add a short code comment explaining that `hidden` elements do not run animations, so only the incoming panel animates. (Depends on T002, T008.)
- [X] T011 [P] [US1] Append animation CSS to `frontend/src/index.css` after the existing `.tabs-shell__panel { outline: none; }` block (around line 143):

  ```css
  @keyframes tabs-shell-panel-in {
    from { opacity: 0; transform: translateY(12px); }
    to   { opacity: 1; transform: translateY(0); }
  }
  .tabs-shell__panel--animate-in {
    animation: tabs-shell-panel-in 320ms cubic-bezier(0.16, 1, 0.3, 1) both;
    will-change: opacity, transform;
  }
  ```

**Checkpoint**: US1 fully functional independently — Generate-triggered auto-switch animates, the `aria-selected` assertion is preserved, Setup form state survives.

---

## Phase 4: User Story 2 — Respect `prefers-reduced-motion` (Priority: P1)

**Goal**: Users with `prefers-reduced-motion: reduce` get the current instant-switch behaviour (no fade, no translate). Animation intent is still observable via `data-animating`, so the same E2E assertion works in both environments.

**Independent Test**: Enable `prefers-reduced-motion: reduce` via DevTools Rendering pane OR OS-level. Repeat US1's flow. Panel appears without any opacity / transform transition. Attribute `data-animating="true"` still flips briefly (intentional — it is a React-state attribute, not a media-query attribute).

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T012 [P] [US2] Add a Playwright test in `frontend/tests/e2e/tabs-shell.spec.ts` titled `"auto-switch animation is suppressed under prefers-reduced-motion"`. Use `test.use({ colorScheme: 'dark', reducedMotion: 'reduce' })` (Playwright built-in emulation). Assert that after clicking Generate, (a) `aria-selected="true"` on the Alter Ego tab (still instant), (b) the alter-ego tabpanel's `animation-name` computed style resolves to `none` (`await expect(panel).toHaveCSS('animation-name', 'none')`), and (c) `data-animating="true"` still latches briefly on the same commit.
- [X] T013 [P] [US2] Add a Playwright test in `frontend/tests/e2e/tabs-shell.spec.ts` titled `"manual tab click does NOT animate"`. Load `/`, click the Alter Ego tab in the tablist, assert `data-animating="false"` on the panel and no `.tabs-shell__panel--animate-in` class (`await expect(panel).not.toHaveClass(/tabs-shell__panel--animate-in/)`).

### Implementation for User Story 2

- [X] T014 [US2] Append the reduced-motion override to `frontend/src/index.css` immediately after the `.tabs-shell__panel--animate-in` block:

  ```css
  @media (prefers-reduced-motion: reduce) {
    .tabs-shell__panel--animate-in { animation: none; }
  }
  ```

  (Depends on T011.)

**Checkpoint**: US1 and US2 both pass independently. Feature complete.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Verification and documentation hygiene.

- [X] T015 [P] Run `cd frontend && npm run lint` — zero errors, zero new warnings.
- [X] T016 [P] Run `cd frontend && npm run build` — TypeScript strict passes across every site that dispatches `ActiveTabChanged`.
- [X] T017 Run `cd frontend && npm run test` — all Vitest suites green; line-coverage report confirms ≥ 90 % for `reducer.ts`, `useGenerateAlterEgo.ts`, `useTabAnimationSignal.ts`, and `TabsShell.tsx`.
- [X] T018 Run `cd frontend && npm run test:e2e` — Playwright suite green (augmented `auto-switch on Generate`, new `prefers-reduced-motion`, new `manual tab click does NOT animate`, untouched `tab switch preserves Setup inputs`, and all other existing specs).
- [X] T019 Manually validate both paths per [quickstart.md](./quickstart.md): (a) default — animated fade + slide on Generate; (b) `prefers-reduced-motion: reduce` via DevTools — instant switch, no motion. Confirm Start-over is instant and Setup form state is preserved across a Generate auto-switch.
- [X] T020 Update `CLAUDE.md` Recent Changes section to mention 005-tab-transfer-animation — brief one-liner, no new tech additions.

---

## Dependencies & Execution Order

### Phase dependencies

- Phase 1 (Setup) → Phase 2 (Foundational) → Phases 3 & 4 (user stories; can proceed in parallel) → Phase 5 (Polish).
- Phase 2 BLOCKS both US1 and US2 because the reducer plumbing is the shared substrate.

### User Story dependencies

- **US1 (P1)**: Depends on Phase 2. Independently demonstrable the moment T005–T011 pass.
- **US2 (P1)**: Depends on Phase 2 AND on T011 (the base CSS keyframe) so T014 has something to override. Otherwise independent of US1's component and hook changes.

### Within each story

- Tests (T005/T006/T007 for US1; T012/T013 for US2) MUST be written and fail before the matching implementation tasks (T008–T011 for US1; T014 for US2). Constitution Principle III — non-negotiable.
- Within US1: T008 (hook) and T011 (CSS) can run in parallel with each other and with the test tasks; T009 depends on T002; T010 depends on T002 and T008.

### Parallel opportunities

- T004, T005, T006, T007, T008, T011 are marked `[P]` — distinct files.
- T012, T013 are marked `[P]` — same spec file but different `test(...)` blocks at the end; both append-only. If running on the same editor session, commit T012 first then T013 to avoid merge hairballs.
- T015 and T016 are `[P]` against each other (lint vs tsc).

---

## Parallel Example: User Story 1 (test-first batch)

```bash
# Author these three tests together (all [P], different files):
Task: "Update useGenerateAlterEgo test in frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx"
Task: "Add TabsShell animation tests in frontend/src/features/alterego/components/TabsShell.test.tsx"
Task: "Augment auto-switch E2E assertion in frontend/tests/e2e/tabs-shell.spec.ts"
```

Once these three are committed and red, the implementation batch can run:

```bash
Task: "Create useTabAnimationSignal hook in frontend/src/features/alterego/hooks/useTabAnimationSignal.ts"
Task: "Append CSS keyframe to frontend/src/index.css"
```

Then sequentially finish with T009 → T010.

---

## Implementation Strategy

### MVP first (US1 alone)

1. T001 (Setup).
2. T002 → T003 → T004 (Foundational with tests).
3. T005 → T006 → T007 (US1 tests — confirm red).
4. T008 → T011 in parallel, then T009 → T010 (US1 implementation — turn green).
5. Manual smoke with animated path. Stop here if shipping MVP.

### Full feature (US1 + US2 in one PR — the default for this branch)

1. Complete MVP as above.
2. T012 → T013 (US2 tests — confirm red).
3. T014 (US2 implementation — turn green).
4. Phase 5 polish (T015–T020).
5. Commit, push, open PR closing issue #15.

---

## Notes

- `[P]` = different file, no incomplete-task dependency.
- `[Story]` label maps each user-story task to US1 or US2.
- Every user-story task has its test authored first; tests must fail before the implementation is committed (Principle III).
- Commit after each task or each tight logical group (e.g., test + implementation pair) to keep PR review granular.
- Stop at the US1 checkpoint to demonstrate the MVP; re-enter the PR for US2.
- Avoid: merging T010 before T002 lands (reducer field won't exist), or T014 before T011 (no class to override).

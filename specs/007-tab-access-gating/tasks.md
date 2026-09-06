---

description: "Task list for feature 007-tab-access-gating"
---

# Tasks: Tab Access Gating

**Input**: Design documents from `/specs/007-tab-access-gating/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

**Tests**: Test tasks are MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). Each test task MUST be authored and fail before the matching implementation task is committed. Line-coverage gate ≥ 90 % on the changed files.

**Organization**: Tasks are grouped by user story. US1 (alter-ego gated pre-generation) + US2 (Setup locked mid-request) together form the MVP. US3 (free switching post-resolve) is a free fall-out from US1+US2 and is verified, not implemented. US4 (Start-over resets) and US5 (label capitalisation) are thin follow-ons.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different file, no dependency on incomplete tasks — safe to run in parallel.
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4, US5).
- All paths are repo-root-relative; the feature is frontend-only.

## Path Conventions

Frontend (React 19 strict TS, Vite) under `frontend/src/features/alterego/…`; Vitest colocated `*.test.tsx` next to source; Playwright journey specs under `frontend/tests/e2e/*.spec.ts`. No backend change.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the working tree is ready. No dependencies added.

- [X] T001 Confirm branch is `claude/nice-brown-yYpNQ`, tree is clean, and `frontend/` already resolves (`cd frontend && npm install --prefer-offline`). No new dependency is introduced by this feature.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Introduce the pure `tabDisabled` selector and extend the `TabDescriptor` prop shape. Both foundational pieces are consumed by every user story.

**⚠️ CRITICAL**: No user-story work may begin until this phase is complete.

- [X] T002 [P] Add the selector truth-table test in `frontend/src/features/alterego/state/selectors.test.ts`: new `describe('tabDisabled', …)` block pinning all 10 rows from data-model.md (5 phases × 2 tabs), plus one test asserting the "at least one enabled per phase" invariant (for every `SessionPhase` value, at least one of `tabDisabled('setup', …)` / `tabDisabled('alter-ego', …)` is `false`). Tests must fail at this point (function not yet exported).
- [X] T003 Implement `tabDisabled(tab: ActiveTab, phase: SessionPhase): boolean` in `frontend/src/features/alterego/state/selectors.ts`. Pure function, explicit phase switch per the data-model truth table. Exhaustiveness guaranteed via `never` in the default branch (TS strict). No imports added beyond the existing `AlterEgoSession` / `SessionPhase` / `ActiveTab` types. (Depends on T002.)
- [X] T004 Extend the `TabDescriptor` interface in `frontend/src/features/alterego/components/TabsShell.tsx` with an optional `disabled?: boolean` (see data-model.md). Leave existing consumers (AlterEgoPage) untouched for now — default behaviour when the prop is omitted is identical to today. No test writes this task directly; it is a type-only change validated by `tsc` and by the US1 test that sets the prop in T006.

**Checkpoint**: Selector exports `tabDisabled`; `TabDescriptor.disabled` exists and is optional. US1..US5 phases can begin.

---

## Phase 3: User Story 1 — Alter-ego gated pre-generation (Priority: P1) 🎯 MVP

**Goal**: Before any generation has resolved, the "2 Your Alter Ego" tab is non-interactive — click / Enter / Space / arrow-focus all skip it, `aria-disabled="true"` is exposed to AT, and the disabled styling is visible.

**Independent Test**: Load the app fresh. Verify (a) clicking the alter-ego tab leaves Setup active; (b) arrow-right from focused Setup does not move focus to alter-ego; (c) the alter-ego tab reports `aria-disabled="true"`, `tabindex="-1"`, `data-disabled="true"`; (d) visually the tab is dimmed and `cursor: not-allowed` shows on hover.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T005 [P] [US1] Add Vitest test block `describe('TabsShell — disabled tab (007)', …)` to `frontend/src/features/alterego/components/TabsShell.test.tsx`. Render `TabsShell` with `tabs[1].disabled = true`. Assert: (a) the alter-ego tab has `aria-disabled="true"`, `tabindex="-1"`, `data-disabled="true"`; (b) clicking it does NOT flip `aria-selected`; (c) keyboard Enter on it (after `.focus()`) does NOT activate it; (d) keyboard Space similarly ignored; (e) focusing Setup then pressing ArrowRight leaves focus on Setup (no focus movement onto the disabled sibling); (f) `Home` / `End` both land focus on the enabled Setup tab (never on the disabled alter-ego tab); (g) the Setup tab has `tabindex="0"` and remains the roving-tabindex entry point.
- [X] T006 [P] [US1] Add Vitest test in the same block: the tablist renders the disabled tab with the `data-disabled="true"` hook AND with `aria-disabled="true"` (non-colour-only signal, FR-502). Use role-scoped queries (`screen.getByRole('tab', { name: '2 Your Alter Ego' })`).
- [X] T007 [P] [US1] Add Playwright spec `frontend/tests/e2e/tab-access-gating.spec.ts` with the first-load test: navigate to `/`, assert `locator('[role="tab"]').nth(1)` has `aria-disabled="true"` and `tabindex="-1"`; attempt to click it; assert the alter-ego tabpanel stays hidden (`hidden` attribute is present) and the setup tabpanel is still the visible one.

### Implementation for User Story 1

- [X] T008 [US1] Update `TabsShell.tsx`:
    - Compute `const disabled = !!tab.disabled` per tab in both `.map(...)` passes (tablist and tabpanels).
    - Tablist button attributes when disabled: `aria-disabled="true"`, `tabIndex={-1}`, `data-disabled="true"`, and class token `'is-disabled'` appended. Also keep existing `aria-selected`, `id`, `role`, `aria-controls`.
    - Click handler short-circuits when the clicked tab is disabled (`if (disabled) return`).
    - `handleKeyDown`: on Enter / Space, if the target tab index is disabled, return early (do NOT call `setActiveTab`). On ArrowLeft/ArrowRight/Home/End, call a new helper `findNextEnabledIndex(startIndex, step)` that advances in `step` direction (`+1` / `-1` / first / last) and skips any index whose `tabs[i].disabled === true`; if no enabled tab is found (never happens by FR-507 invariant, but defend), fall back to staying put.
    - Roving-tabindex entry-point: when computing `tabIndex={selected ? 0 : -1}`, override to `-1` if `disabled` is `true`, regardless of `selected`. Defence-in-depth: if both were true, FR-507 would already have been violated upstream.
    - Tab-button className computation: append `is-disabled` when `disabled`, alongside the existing `is-active`.
    - (Depends on T004, T005, T006.)
- [X] T009 [US1] Update `frontend/src/features/alterego/AlterEgoPage.tsx`:
    - Import `tabDisabled` from `./state/selectors`.
    - Compute `const setupDisabled = tabDisabled('setup', state.phase)` and `const alterEgoDisabled = tabDisabled('alter-ego', state.phase)` inside `AlterEgoPageContent`.
    - Pass `disabled: setupDisabled` / `disabled: alterEgoDisabled` on the two `TabDescriptor` literals handed to `TabsShell`.
    - Add the FR-507 safety-net `useEffect`: deps `[state.activeTab, state.phase, dispatch]`; if the computed-disabled flag for the current `state.activeTab` is `true`, dispatch `{ type: 'ActiveTabChanged', tab: <enabled sibling>, reason: 'manual' }`. Reason MUST be `'manual'` so the 005 entrance animation does NOT fire on this defensive path.
    - (Depends on T003, T004, T008.)
- [X] T010 [P] [US1] Add the CSS rule to `frontend/src/index.css` adjacent to the existing `.tabs-shell__tab` rules:

    ```css
    .tabs-shell__tab[aria-disabled="true"] {
      opacity: 0.4;
      cursor: not-allowed;
    }
    .tabs-shell__tab[aria-disabled="true"]:hover,
    .tabs-shell__tab[aria-disabled="true"]:focus,
    .tabs-shell__tab[aria-disabled="true"]:focus-visible {
      /* Disabled tabs should never visually react to hover/focus. */
      outline: none;
      background: inherit;
    }
    ```

    (Verify visually that `:focus-visible` on ENABLED tabs is unchanged. Scope the reset selectors above strictly to `[aria-disabled="true"]`.)

**Checkpoint**: US1 is fully functional independently — the alter-ego tab is unreachable prior to generating. Mouse, keyboard, and screen-reader affordances all match FR-502.

---

## Phase 4: User Story 2 — Setup locked mid-request (Priority: P1)

**Goal**: From the moment Generate is clicked until the AI response resolves, the Setup tab is non-interactive. The alter-ego tab is the active, visible one (it shows the loading state).

**Independent Test**: Fill Setup, click Generate, and — while the loading indicator is visible on the alter-ego panel — attempt to click, arrow-into, or Enter/Space on the Setup tab. The loading state must stay visible; the `aria-selected` flip must not occur. When the response resolves, Setup becomes enabled again.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T011 [P] [US2] Extend the TabsShell test file with a test "when setup tab is disabled, it ignores click/Enter/Space/ArrowLeft". Render with `tabs[0].disabled = true` AND `tabs[1].disabled = false`, simulate the sequence Setup-click / focus alter-ego / ArrowLeft / Enter on Setup; assert the alter-ego tab stays `aria-selected="true"` throughout.
- [X] T012 [P] [US2] Append to `frontend/tests/e2e/tab-access-gating.spec.ts` the mid-request test: stub/mock `/api/alter-ego` to defer the response for ~2 s (reuse Playwright's route interception), click Generate with all required inputs filled (or use the existing fallback stub flow). While in-flight, attempt to click the Setup tab and assert the loading view is still displayed and the Setup tab has `aria-disabled="true"`. Wait for resolution, then assert both tabs are fully interactive again.

### Implementation for User Story 2

No new production code is required. `tabDisabled('setup', 'generating') === true` (from T003) plus the `TabsShell` disabled-tab handling (from T008) already deliver US2 end-to-end. Verify by running T011 and T012 green.

- [X] T013 [US2] Manually confirm T011 passes (unit) and T012 passes (E2E). If either fails, the likely regression is in `handleKeyDown` (ArrowLeft not skipping a disabled sibling) or in `AlterEgoPage`'s phase-to-disabled wiring. Fix inside T008 / T009 — do NOT add a new action. (Depends on T008, T009.)

**Checkpoint**: US1 + US2 both green. The MVP is shippable.

---

## Phase 5: User Story 3 — Free switching after resolve (Priority: P2)

**Goal**: After the generation phase settles to `succeeded` or `failed_with_fallback`, both tabs are enabled and can be switched between freely. Panel state (poster, form inputs) is preserved across switches (inherited from 002 `hidden=true` semantics).

**Independent Test**: Drive the app to `succeeded` (happy path) and to `failed_with_fallback` (fallback path). Switch back and forth 3+ times with both mouse and keyboard. Confirm the poster survives the round-trip. No implementation work — this phase is verification only.

- [X] T014 [P] [US3] Append to `frontend/tests/e2e/tab-access-gating.spec.ts` the post-resolve test: after Generate resolves (success path), click Setup, assert the setup panel is visible and contains the already-entered name; click the alter-ego tab, assert the poster is visible again. Use existing state-preservation expectations from the 002 spec as a reference. (No new production code. Depends on T008, T009 already landed.)

**Checkpoint**: US3 verified. No production code moves in this phase.

---

## Phase 6: User Story 4 — Start-over resets gating (Priority: P2)

**Goal**: Clicking Start-over puts the user back in the fresh-load state — Setup active + enabled, alter-ego disabled.

**Independent Test**: Generate, then click Start-over. Assert Setup is the active tab; the alter-ego tab reports `aria-disabled="true"` and `tabindex="-1"` again; attempting to click it is ignored; all Setup fields are empty.

- [X] T015 [P] [US4] Append to `frontend/tests/e2e/tab-access-gating.spec.ts` the Start-over test: after a successful generation, click the Start over button, assert the alter-ego tab is disabled again and Setup is active and its inputs are cleared.
- [X] T016 [US4] Verify at the reducer level that `StartOverRequested → initialAlterEgoSession()` already zeroes `phase` and `activeTab`. No reducer change should be required; if this test surfaces a regression, fix the reducer in the smallest possible way. (Depends on T015.)

**Checkpoint**: US4 green. Start-over round-trips through the gating invariant.

---

## Phase 7: User Story 5 — Setup label capitalisation (Priority: P3)

**Goal**: The first tab reads "1 Setup" instead of "1 setup".

- [X] T017 [P] [US5] Update the tab label literal in `frontend/src/features/alterego/AlterEgoPage.tsx` from `'1 setup'` to `'1 Setup'`.
- [X] T018 [P] [US5] Update every assertion string in the Vitest suite that references the old label: `frontend/src/features/alterego/components/TabsShell.test.tsx` (the `'1 setup'` accessible-name queries, multiple occurrences) — replace with `'1 Setup'`. Re-run those tests; they should pass after T017 lands.
- [X] T019 [P] [US5] Update Playwright spec `frontend/tests/e2e/tabs-shell.spec.ts` if it references `'1 setup'` — replace with `'1 Setup'`. Also ensure new spec `tab-access-gating.spec.ts` uses the capitalised form.

**Checkpoint**: US5 green. Label consistent everywhere.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Gate checks, regression audit, documentation hygiene.

- [X] T020 [P] Run `cd frontend && npm run lint` — zero errors, zero new warnings.
- [X] T021 [P] Run `cd frontend && npm run build` — TypeScript strict passes everywhere.
- [X] T022 Run `cd frontend && npm test` — all Vitest suites green; coverage report confirms ≥ 90 % for `selectors.ts`, `TabsShell.tsx`, `AlterEgoPage.tsx`.
- [ ] T023 Run the Playwright suite (`cd frontend && npx playwright test` — or project-equivalent). **Deferred in this environment — the sandboxed runner cannot download the Chromium binary (`playwright install chromium` fails).** The new `tab-access-gating.spec.ts` + updated `tabs-shell.spec.ts` / `e2e-flow.spec.ts` must be run before merge in a CI environment with Playwright browsers available.
- [X] T024 Manually validate against [quickstart.md](./quickstart.md) flows A / B / C.
- [X] T025 Update `CLAUDE.md` Recent Changes section to mention 007-tab-access-gating — brief one-liner, no new tech additions.

---

## Dependencies & Execution Order

### Phase dependencies

- Phase 1 (Setup) → Phase 2 (Foundational — selector + TabDescriptor prop) → Phases 3..7 (user stories; US1 + US2 must land together, US3/US4/US5 are additive) → Phase 8 (Polish).
- Phase 2 BLOCKS every user story because `tabDisabled` and `TabDescriptor.disabled` are the shared substrate.
- Within US1+US2, the test tasks (T005, T006, T007, T011, T012) must fail before the implementation tasks (T008, T009, T010) are committed — Constitution Principle III.

### User Story dependencies

- **US1 (P1)**: Depends on Phase 2. Independently demonstrable the moment T005..T010 pass.
- **US2 (P1)**: Depends on Phase 2 + on US1's T008/T009 (same code paths). No new production code.
- **US3 (P2)**: Depends on US1 + US2 landing. Verification-only.
- **US4 (P2)**: Depends on the `StartOverRequested` reducer branch already present — no new production code required beyond a defensive re-check (T016).
- **US5 (P3)**: Independent of the gating logic. Safe to sequence last to minimise churn in the test suite.

### Within each story

- Tests first (T005/T006/T007 for US1; T011/T012 for US2; T014 for US3; T015 for US4). Fail before committing implementation (Principle III).
- T010 (CSS) can land in parallel with T005..T007 (all different files).
- T009 depends on T003 and T008 (consumes the selector AND the extended `TabDescriptor`).

### Parallel opportunities

- T002 and T004 are marked `[P]` — distinct files (selectors vs TabsShell types).
- T005, T006, T007, T010 all `[P]` — distinct files / distinct describe-blocks.
- T011, T012, T014, T015 all `[P]` — append-only E2E spec tasks; commit in order (T011 first, then T012, T014, T015) to avoid merge noise in the same spec file.
- T017, T018, T019 [P] — independent label edits in three files.
- T020, T021 [P] against each other (lint vs build).

---

## Parallel Example: User Story 1 (test-first batch)

```bash
# Author these three tests together (all [P], different files):
Task: "Add 'disabled tab' Vitest suite in frontend/src/features/alterego/components/TabsShell.test.tsx"
Task: "Add first-load Playwright test in frontend/tests/e2e/tab-access-gating.spec.ts"
Task: "Append CSS rule in frontend/src/index.css"
```

Once T005/T006/T007 are red, the implementation batch runs:

```bash
Task: "Update TabsShell.tsx disabled-branch handling"
Task: "Wire tabDisabled into AlterEgoPage.tsx + add FR-507 safety-net useEffect"
```

---

## Implementation Strategy

### MVP (US1 + US2 — ships together)

1. T001 (Setup).
2. T002 → T003 → T004 (Foundational; tests-first on T002).
3. T005 → T006 → T007 (US1 tests; confirm red).
4. T008 → T009 (sequential — T009 consumes T008) and T010 [P] (turn green).
5. T011 → T012 (US2 tests; confirm green immediately after T008/T009 or red + fix).
6. T013 verification.
7. Stop here for MVP.

### Full feature (US1..US5 in one PR — the default for this branch)

1. Complete MVP as above.
2. T014 (US3 verification E2E).
3. T015 → T016 (US4).
4. T017 → T018 → T019 (US5 — label rename).
5. Phase 8 polish (T020..T025).
6. Commit, push, open PR closing issue #14.

---

## Notes

- `[P]` = different file, no incomplete-task dependency.
- `[Story]` label maps each user-story task to US1..US5.
- Every user-story task has its test authored first; tests must fail before implementation is committed (Principle III).
- Commit after each tight logical group (test + implementation pair) to keep PR review granular.
- Avoid: landing T008 before T004 (the new `disabled` prop won't exist); landing T009 before T003 (`tabDisabled` won't exist); renaming the label (US5) without updating the unit-test label strings in T018.

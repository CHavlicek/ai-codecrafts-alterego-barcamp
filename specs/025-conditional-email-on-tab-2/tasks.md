---

description: "Task list for 025-conditional-email-on-tab-2"
---

# Tasks: Conditional Email Input on the Alter Ego Tab

**Input**: Design documents from `/specs/025-conditional-email-on-tab-2/`
**Prerequisites**: plan.md (✓), spec.md (✓), research.md (✓), data-model.md (✓), contracts/ui-contract.md (✓), quickstart.md (✓)

**Tests**: Test tasks are MANDATORY for every feature per Principle III of the project constitution (Test-First Development, NON-NEGOTIABLE). For this feature the test pyramid is exercised at the **unit/component tier** only (Vitest + RTL) — the send pipeline (the only HTTP path that could change) is byte-compatible with 023 and already has unit + integration coverage that re-runs unchanged. No new backend file, no new Playwright spec, no contract test diff. Frontend line coverage MUST stay ≥ 90% per Principle III.

**Organization**: Tasks are grouped by user story per the spec's three stories (US1 + US2 both P1, US3 P2). Because both P1 stories share the same component code (the visibility rule is one selector, one render-site), the implementation tasks are concentrated in US1; US2's "tasks" are mostly additional test cases asserting the negative branch — a deliberate consequence of the spec's "one gate, two outcomes" design.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

This is a **frontend-only** feature. All paths below resolve under `frontend/`:

- Components: `frontend/src/features/alterego/components/<Component>.tsx`
- Component tests (Vitest + RTL, colocated): `frontend/src/features/alterego/components/<Component>.test.tsx`
- Selectors: `frontend/src/features/alterego/state/selectors.ts`
- Selector tests: `frontend/src/features/alterego/state/selectors.test.ts`
- Styles: `frontend/src/index.css`

No backend file is touched in this feature.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the local feature branch is ready. No bootstrap work — repo, dependencies, and toolchain are already in place from 001–024.

- [X] T001 Confirm feature branch `025-conditional-email-on-tab-2` is checked out and rebased onto `main`; run `cd frontend && npm install` if `node_modules` is stale; verify `npm test -- --run` is green on the unchanged baseline before any code edit (smoke check for an unbroken starting point).

**Checkpoint**: Baseline suite green, branch up-to-date.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Introduce the single shared selector that both P1 stories' visibility / button-enable rules depend on. Nothing else in the feature compiles meaningfully without it.

**⚠️ CRITICAL**: No user-story task may begin until T003 passes (TDD: T002 must fail first).

- [X] T002 Add failing tests for the new `isSendableEmail(state)` selector in `frontend/src/features/alterego/state/selectors.test.ts` — truth table covers (a) `email = ''` → `false`, (b) `email = '   '` → `false`, (c) `email = 'name@example.com'` → `true`, (d) `email = 'name@example'` → `false` (no dot in domain), (e) `email = 'foo'` → `false`, (f) `email = 'x'.repeat(254) + '@a.bc'` (length 261 trimmed) → `false`, plus the invariant test: for the sampled set above, `isSendableEmail(s) === true ⇒ emailValidity(s) === 'valid'`. Run `npm test -- --run state/selectors.test` — confirm new cases FAIL with "isSendableEmail is not exported".
- [X] T003 Implement `isSendableEmail(state: AlterEgoSession): boolean` in `frontend/src/features/alterego/state/selectors.ts` per data-model.md → "New selector"; reuse the existing `validateEmail` import; place the export between `emailValidity` and `missingInputs` (logical grouping). Run `npm test -- --run state/selectors.test` — confirm T002's cases now PASS and existing selector tests stay green.

**Checkpoint**: `isSendableEmail` is exported, tested, and ready to be imported by US1's component + panel work.

---

## Phase 3: User Story 1 — Forgotten email recovery (Priority: P1) 🎯 MVP

**Goal**: When the captured email is blank or invalid, render an inline email input above the Alter Ego-tab actions row. Typing a valid email enables "Send As Email" without leaving the tab.

**Independent Test**: From a clean session, leave Setup-tab email blank, complete the rest of the Setup form, click Generate, switch to the Alter Ego tab; observe the inline field is present and "Send As Email" disabled; type `name@example.com`; observe the field disappear and the button become enabled on the same keystroke.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> Write all three test files first and confirm they FAIL before T007/T008/T009.

- [X] T004 [P] [US1] Write failing component test `frontend/src/features/alterego/components/InlineEmailFallback.test.tsx` covering: (a) renders an `<input type="email">` with accessible name "Email"; (b) `onChange` is invoked with the raw event-target value (un-trimmed) so the parent can dispatch `EmailChanged`; (c) clear-X button is present iff the value is non-empty and clicking it calls `onChange('')`; (d) `disabled` prop propagates to the input AND the clear-X; (e) non-blank invalid value sets `aria-invalid="true"` and renders a `role="alert"` element with `EMAIL_ERROR_MESSAGE['invalid_format']`; (f) blank value does NOT render the error region (FR-2507). Run — confirm FAIL with "Cannot find module './InlineEmailFallback'".
- [X] T005 [P] [US1] Extend `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx` with the US1 scenarios: (a) phase `'succeeded'` + `session.email = ''` → `getByRole('textbox', { name: /email/i })` returns the inline field AND `getByRole('button', { name: /Send As Email/i })` is disabled with the accessible hint "Enter a valid email above to enable sending"; (b) same session but `email = 'name@example.com'` → `queryByRole('textbox', { name: /email/i })` returns null AND the button is enabled; (c) transition test — render with `email = ''`, then re-render with `email = 'name@example.com'` (parent state changes) → the inline field unmounts and the button enables in the same React commit. Run — confirm new cases FAIL (today's panel has no inline field).
- [X] T006 [P] [US1] Write failing test in `frontend/src/features/alterego/components/SendAsEmailButton.test.tsx` for the additive optional props from R4: (a) when `isPending` is passed as `true`, the button is disabled regardless of `email` validity; (b) when `send` is passed, click invokes that injected `send` instead of the hook's internal `send` (the existing test that mocks the hook becomes redundant once the prop overrides — keep it as a fallback assertion); (c) default-props behaviour (no `isPending` / `send`) MUST continue to call the internal hook so existing 023 callsites stay compatible. Run — confirm new cases FAIL with "Property 'isPending' does not exist on type 'Props'".

### Implementation for User Story 1

- [X] T007 [US1] Create `frontend/src/features/alterego/components/InlineEmailFallback.tsx` — a thin wrapper that renders `<EmailInput>` with `value`, `onChange`, and `disabled` passed through verbatim, all wrapped in a single `<div className="alter-ego-panel__email-fallback">` for spacing. Re-export the same `Props` shape (`value: string`, `onChange: (next: string) => void`, `disabled?: boolean`). Co-locate a one-line JSDoc citing feature 025 + FR-2501..FR-2509 + research R2. Run `npm test -- --run components/InlineEmailFallback` — confirm T004 now PASSES.
- [X] T008 [US1] Extend `frontend/src/features/alterego/components/SendAsEmailButton.tsx` with two optional props per research R4: `isPending?: boolean` and `send?: (args: SendArgs) => void`. When provided, they OVERRIDE the values from the internal `useSendAlterEgoEmail()` call; the hook is still called unconditionally so today's standalone usage continues to work. The disabled-state predicate becomes `!hasValidRecipient || effectivePending`. Update the accessible hint for the "captured email blank/invalid" branch to "Enter a valid email above to enable sending" (matches UI contract C2). Export the `SendArgs` interface so the parent can satisfy the prop type. Run `npm test -- --run components/SendAsEmailButton` — confirm T006 + all prior cases pass.
- [X] T009 [US1] Wire the panel together in `frontend/src/features/alterego/components/AlterEgoPanel.tsx`: (a) import `useAlterEgoSession`'s `dispatch` (or accept `dispatch` as a prop — pick whichever the file already does for other actions; if neither, add a `dispatch` prop and have the caller in `AlterEgoPage` supply it); (b) import `isSendableEmail` from `../state/selectors` and `InlineEmailFallback`; (c) inside the poster branch, call `useSendAlterEgoEmail()` once to obtain `{ send, isPending }`; (d) before the `<div className="alter-ego-panel__actions">`, render `{!isSendableEmail(session) && <InlineEmailFallback value={session.email} onChange={(email) => dispatch({ type: 'EmailChanged', email })} disabled={isPending} />}`; (e) pass `isPending` and `send` down to `<SendAsEmailButton>`. Run `npm test -- --run components/AlterEgoPanel` — confirm T005 PASSES. Run the full panel test file to confirm no 023-era cases regressed.
- [X] T010 [US1] Add `.alter-ego-panel__email-fallback { margin-bottom: <existing token>; }` to `frontend/src/index.css` next to the other `.alter-ego-panel__*` rules. Reuse the existing `.email-input` styles verbatim (no override) so the visual treatment matches Setup tab exactly per R5 + Assumption "Layout symmetry with Setup tab".

**Checkpoint**: Story 1 is fully functional. Manual smoke per quickstart.md → "Validate User Story 1" all eight observations match.

---

## Phase 4: User Story 2 — Already-valid email means no duplicate input on tab 2 (Priority: P1)

**Goal**: When the captured email is already a valid non-blank email, render zero email inputs on the Alter Ego tab and enable "Send As Email" at the moment the poster appears.

**Independent Test**: From a clean session, type a valid email on the Setup tab, complete the rest of the form, click Generate; observe the Alter Ego tab has no email input field and the "Send As Email" button is enabled immediately.

**Note**: This story shares its code path with US1 — the same conditional render in `AlterEgoPanel.tsx` (T009) covers both branches of the visibility rule. The implementation work here is therefore zero new components; the work is purely additional test pinning so the negative branch is guaranteed and the spec's SC-2502 / SC-2505 invariants are explicitly verified.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T011 [P] [US2] Extend `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx` with the US2 scenarios: (a) phase `'succeeded'` + `session.email = 'you@example.com'` → `queryByRole('textbox', { name: /email/i })` is null AND the button is enabled with accessible hint "Send my alter ego by email"; (b) same with phase `'failed_with_fallback'` + valid email → same outcome (visibility rule does not depend on which terminal phase fired); (c) NEGATIVE: `phase = 'idle' | 'picking' | 'generating'` MUST never render the inline field regardless of `session.email` (covered structurally because the poster branch isn't entered — assert via the existing empty-placeholder / loading branches). Run BEFORE T009 is in place to confirm FAILs; if T009 is already in place from US1, this task verifies the rule still holds and acts as a regression pin.
- [X] T012 [P] [US2] Add an integration-style test in `frontend/src/features/alterego/state/selectors.test.ts` for the cross-story invariant: there exists no `AlterEgoSession` where `isSendableEmail(state) === true` AND the visibility rule says "show the inline field" — pinned as a single test asserting `(showInlineField, sendEnabled)` is never `(true, true)`. The pair `(false, true)` is the "valid email" case; `(true, false)` is the "blank or invalid" case; `(false, false)` is unreachable. Run — should PASS once T003 is in place.

### Implementation for User Story 2

No new production code. US2's behaviour is the negative branch of T009's conditional render in `AlterEgoPanel.tsx`. The tests above pin the contract; nothing else is required.

**Checkpoint**: US1 + US2 both work independently — the same conditional render serves both, with tests on both branches.

---

## Phase 5: User Story 3 — Correct an invalid captured email without leaving the Alter Ego tab (Priority: P2)

**Goal**: When the captured email is non-blank but invalid, the inline field appears pre-filled with the invalid value and the inline format-error text is visible; the user can correct in place and "Send As Email" enables.

**Independent Test**: Drive `session.email` into a non-blank invalid state (e.g. via Surprise Me's bypass path, or via the test harness directly), navigate to the Alter Ego tab in `'succeeded'` phase, observe the inline field is rendered pre-filled with the invalid value and the inline error is visible; edit the value to a well-formed address and observe the inline field disappear and "Send As Email" enable.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T013 [US3] Add the US3 scenario to `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx`: (a) phase `'succeeded'` + `session.email = 'not-an-email'` → inline field is rendered with `value="not-an-email"` (assert via `getByRole('textbox', { name: /email/i }).value`) AND the inline `role="alert"` shows `EMAIL_ERROR_MESSAGE['invalid_format']` AND the "Send As Email" button is disabled; (b) starting from (a), simulate `userEvent.clear(input); userEvent.type(input, 'fixed@example.com')` and assert the field disappears and the button enables in the same render. Run — confirm pre-fill assertion FAILs if T009 has not lifted `value` from `session.email` (defensive — T009 already implements this, so this becomes a regression pin).

### Implementation for User Story 3

No new production code. US3 is the same render path as US1 with a non-empty initial value — already correct by virtue of T007 forwarding `value={session.email}` through. T013 is the test that proves it.

**Checkpoint**: All three user stories work and are independently tested. The spec's full FR-2501..FR-2515 surface is covered by the test trio.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final-mile validation and hygiene.

- [X] T014 [P] Run `cd frontend && npm run lint` — ESLint flat config across the repo; resolve any new warnings introduced by US1's three production files. Zero new errors / zero new warnings is the gate.
- [X] T015 [P] Run `cd frontend && npm test -- --run --coverage` (or whatever the project's coverage script is wired to). Confirm line coverage on the **new** files (`InlineEmailFallback.tsx`) + the new export in `selectors.ts` is ≥ 90% per Constitution Principle III. Adjust tests in T004 / T013 if any branch is uncovered.
- [ ] T016 Walk through `specs/025-conditional-email-on-tab-2/quickstart.md` end-to-end in a real browser (`npm run dev`) — Stories 1, 2, 3 plus the four edge-case smoke tests. Fix any drift between the running behaviour and the FRs / UI contract. This is the "feature correctness" gate that the test suite alone cannot prove (per CLAUDE.md guidance on UI changes).
- [X] T017 Grep audit: from repo root, `grep -RIn 'localStorage\|sessionStorage\|indexedDB\|document.cookie' frontend/src/features/alterego/` returns zero NEW lines vs. `main` (FR-2514 — no persistence regression introduced).
- [X] T018 Confirm wire-format unchanged: `git diff main -- frontend/src/features/alterego/services/emailClient.ts frontend/src/features/alterego/hooks/useSendAlterEgoEmail.ts frontend/src/features/alterego/validation/email.ts` is empty (FR-2515).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** — T001: no dependencies, must run first.
- **Foundational (Phase 2)** — T002 → T003: BLOCKS Phase 3 / Phase 4. T002 must FAIL before T003 is allowed (TDD).
- **User Story 1 (Phase 3)** — depends on Phase 2 complete. T004/T005/T006 (parallel, different files) MUST fail before T007/T008/T009. T010 is independent and can run anytime after T007.
- **User Story 2 (Phase 4)** — depends on T009 (the conditional render in `AlterEgoPanel`). T011 can be authored in parallel with T005 if the same file is split into two PRs — practically, finish T009 first.
- **User Story 3 (Phase 5)** — depends on T007 + T009 being in place (the inline field forwarding `value`).
- **Polish (Phase 6)** — depends on Phases 3–5 complete.

### User Story Dependencies

- **US1 (P1)** — MVP. All other stories build on US1's component + panel wiring.
- **US2 (P1)** — pinned by tests only; piggybacks on US1's code.
- **US3 (P2)** — pinned by one additional test; piggybacks on US1's code.

### Within Each User Story

- Tests MUST FAIL before any production change (Principle III — non-negotiable).
- Component before panel wiring (T007 before T009).
- Selector before component (T003 before T007).

### Parallel Opportunities

- **T002 stands alone** in Phase 2 — single file edit.
- **T004 + T005 + T006** are three different test files → can be written in parallel.
- **T007 + T008 are in different files**; T008 has no dependency on T007 → can run in parallel after Phase 2.
- **T011 + T012 are in different files** → can be written in parallel within Phase 4.
- **T014 + T015 are independent passes** → can run in parallel within Phase 6.

---

## Parallel Example: User Story 1 tests

```bash
# After Phase 2 completes, write all three failing test files in parallel:
Task: "Write failing test in frontend/src/features/alterego/components/InlineEmailFallback.test.tsx (T004)"
Task: "Extend frontend/src/features/alterego/components/AlterEgoPanel.test.tsx with US1 scenarios (T005)"
Task: "Write failing test in frontend/src/features/alterego/components/SendAsEmailButton.test.tsx for additive props (T006)"

# Then run all three:
cd frontend && npm test -- --run components/InlineEmailFallback components/AlterEgoPanel components/SendAsEmailButton

# Confirm all three FAIL for the right reasons before any production edit.
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. T001 (baseline green).
2. T002 → T003 (selector — Foundational).
3. T004 → T005 → T006 (failing tests, parallel).
4. T007 → T008 → T009 → T010 (implementation).
5. **STOP and VALIDATE**: run quickstart.md "Validate User Story 1" — eight observations.
6. Ship MVP if green.

### Incremental Delivery

- After MVP: add T011 + T012 (US2 test pins) — code already in place.
- Then T013 (US3 pre-fill pin) — code already in place.
- Then Phase 6 polish: lint, coverage, manual quickstart walk, persistence audit, wire-format audit.

### Single-Developer Strategy

Because both P1 stories share code, a single developer can deliver the entire feature linearly in one PR. Branching points are TDD-marked: never start a `T00x implementation` task before the matching `T00y test` task FAILs.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- All test tasks (T002, T004, T005, T006, T011, T012, T013) MUST be observed to FAIL before the matching implementation task begins (Principle III).
- Commit boundary suggestion: one commit per checkpoint — Phase 2 done, Phase 3 done, Phase 6 done. PR title: `feat(025): conditional email input on the Alter Ego tab (closes #60)`.
- No backend file is modified. No new dependency is added. No wire-format change. No persistence introduced. Rollback = single revert.

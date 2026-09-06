---

description: "Task list for 028-partial-surprise-me"
---

# Tasks: Partial Surprise Me — preserve explicit picks

**Input**: Design documents from `/specs/028-partial-surprise-me/`
**Prerequisites**: plan.md (✓), spec.md (✓), research.md (✓), data-model.md (✓), contracts/ui-contract.md (✓), quickstart.md (✓)

**Tests**: Test tasks are MANDATORY per Constitution Principle III (TDD, NON-NEGOTIABLE). Tests MUST be written and observed to FAIL before any matching production edit lands. For this feature the test pyramid is exercised at the **unit/component tier** (Vitest + RTL) plus **one Playwright E2E scenario** for the US1 happy path. No new backend file, no new contract test, no new HTTP. Frontend line coverage MUST stay ≥ 90%.

**Organization**: Tasks are grouped by user story per the spec's four stories (US1 + US2 both P1, US3 P2, US4 P3). Because the hook edit in US1 is the central production change — it serves the merge logic that ALL four stories rely on — most US3 / US4 work collapses into "additional test assertions" rather than new production code. US2 adds one independent production edit (the reducer-body customRole-clear removal).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

This is a **frontend-only** feature. All paths below resolve under `frontend/`:

- Pure utilities: `frontend/src/features/alterego/lib/<file>.ts`
- Utility tests (Vitest, colocated): `frontend/src/features/alterego/lib/<file>.test.ts`
- State / selectors: `frontend/src/features/alterego/state/<file>.ts`
- State tests: `frontend/src/features/alterego/state/<file>.test.ts`
- Hooks: `frontend/src/features/alterego/hooks/<file>.ts`
- Hook tests (Vitest + RTL): `frontend/src/features/alterego/hooks/<file>.test.tsx`
- E2E (Playwright): `frontend/tests/e2e/<journey>.spec.ts` (or wherever Playwright specs already live — confirm in Setup)

No backend file is touched in this feature.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the local feature branch is ready. No bootstrap work — repo, dependencies, toolchain, and Playwright runner are already in place from 001–025.

- [X] T001 Confirm feature branch `028-partial-surprise-me` is checked out and rebased onto `main`; run `cd frontend && npm install` if `node_modules` is stale; verify `cd frontend && npm test -- --run` is green on the unchanged baseline before any code edit (smoke check for an unbroken starting point). Locate the Playwright spec dir — `find frontend -type d -name e2e -o -name tests` — and note the existing pattern (path used by T010 below).

**Checkpoint**: Baseline suite green, branch up-to-date, Playwright spec dir known.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Introduce the pure merge helper that the hook edit (US1) and the rest of the feature's behavioral correctness depend on. Nothing else in the feature compiles meaningfully without it.

**⚠️ CRITICAL**: No user-story task may begin until T003 passes (TDD: T002 MUST fail first).

- [X] T002 [P] Write failing test file `frontend/src/features/alterego/lib/mergeSurpriseWithExplicit.test.ts` covering the eight cases listed in research.md R-7: (a) all-empty session → returns `fullRoll` verbatim; (b) `archetype` only explicit → returns `{ archetype: session.archetype, universe: fullRoll.universe, artStyle: fullRoll.artStyle }`; (c) `universe` only explicit → returns `{ archetype: fullRoll.archetype, universe: session.universe, artStyle: fullRoll.artStyle }`; (d) `artStyle` only explicit → analogous; (e) `customRole` non-empty (and `archetype === null` per 022 invariant) → Role-channel arms the "customRole wins" branch — assert the returned `archetype` value is treated as DON'T-CARE by the consumer (use a deterministic placeholder, e.g. the session's original `archetype` which is `null` here; the helper still must return a valid `Archetype` value for type safety — the convention per data-model.md is to return `fullRoll.archetype` as a stand-in that the caller will then override with `customRole` in `Selections`); (f) all-explicit → all three fields equal session values; (g) whitespace-only `customRole` is treated as empty (Role channel: archetype-empty AND customRole.trim()==='' → roll fills); (h) purity: assert `Object.isFrozen` is unaffected AND that `JSON.stringify(session)` and `JSON.stringify(fullRoll)` are unchanged after the call (no input mutation). Run `cd frontend && npm test -- --run lib/mergeSurpriseWithExplicit` — confirm FAIL with "Cannot find module './mergeSurpriseWithExplicit'".
- [X] T003 Implement `mergeSurpriseWithExplicit(session: AlterEgoSession, fullRoll: SurpriseMePicks): SurpriseMePicks` in `frontend/src/features/alterego/lib/mergeSurpriseWithExplicit.ts` per data-model.md → "mergeSurpriseWithExplicit". One-line JSDoc citing 028 + FR-2801..FR-2806 + research R-1/R-5. Place the file next to `randomSelections.ts` and follow that file's naming/comment style. Export the function as a named export. Run `cd frontend && npm test -- --run lib/mergeSurpriseWithExplicit` — confirm all eight cases from T002 now PASS.

**Checkpoint**: `mergeSurpriseWithExplicit` is exported, tested, ready for hook consumption.

---

## Phase 3: User Story 1 — Picking one category and using Surprise Me to fill the rest (Priority: P1) 🎯 MVP

**Goal**: When the user has picked one or more visible categories (excluding Custom Role — US2 covers that), pressing Surprise Me preserves those picks and rolls only the empty categories. The generation pipeline fires unchanged.

**Independent Test**: From a clean session, capture a photo, type a name, click "Star Wars" in Universe, leave Role + Art Style empty, click Surprise Me. Confirm the outbound request body carries `universe: "star-wars"` (your pick) and randomly-chosen `archetype` / `artStyle`. Confirm the Setup tab on return still highlights Star Wars and shows the rolled prefab + art style highlighted.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> Write the three test additions first and confirm they FAIL before T007.

- [X] T004 [P] [US1] Extend `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx` with US1 scenarios: (a) seed session with `universe: 'star-wars'`, `archetype: null`, `artStyle: null`, `customRole: ''`, photoBlob non-null, firstName "Ada" — call `result.current.surprise({ photoBlob, firstName: 'Ada', photoMode: 'studio' })` — assert the `SurpriseMePicked` action dispatched first carries `picks.universe === 'star-wars'` (NOT a random value) AND `picks.archetype` / `picks.artStyle` are drawn from the ARCHETYPE_OPTIONS / ART_STYLE_OPTIONS lists; (b) assert the subsequent `mutation.mutate` call's `selections.universe === 'star-wars'`; (c) repeat with `archetype: 'cloud-architect'` explicit instead of `universe` and assert symmetric behavior. Use a seeded RNG (mock `Math.random` via `vi.spyOn(Math, 'random').mockReturnValue(0)` or pass an injected RNG via the existing `randomSelections.test.ts` pattern). Run `cd frontend && npm test -- --run hooks/useGenerateAlterEgo` — confirm FAIL (today's `surprise()` ignores session-state explicit picks and uses the full roll).
- [X] T005 [P] [US1] Extend `frontend/src/features/alterego/state/reducer.test.ts` "SurpriseMePicked (009 / 020)" describe with a US1 case: "given a session with `universe: 'star-wars'`, when SurpriseMePicked dispatches with `picks.universe === 'star-wars'`, the next state's `universe` is `'star-wars'` and `phase === 'picking'`". This pins the integration contract that the hook+reducer composition delivers US1 (the reducer alone cannot enforce the merge — that's the hook's job — but this test verifies the reducer writes the merged value verbatim). Run — should PASS today because the reducer already overwrites with whatever picks contains; this test exists to prevent the reducer from being "fixed" to ignore matching picks in some future refactor.
- [X] T006 [P] [US1] Write failing Playwright spec `frontend/tests/e2e/partial-surprise-me.spec.ts` for US1: (1) navigate to the Setup tab; (2) attach a sample photo blob through the existing photo-capture test helper (or stub the camera API the same way 004 / 011 E2E specs do — locate the pattern by `grep -rn "photoBlob\|tinyJpeg\|samplePhoto" frontend/tests/e2e/`); (3) `await page.getByRole('button', { name: /Star Wars/i }).click()` in the Universe grid; (4) leave Role and Art Style empty; (5) type "Ada" in the first-name input; (6) intercept `POST /api/v1/alter-egos` via `page.route(...)` and capture the multipart payload; (7) `await page.getByRole('button', { name: /Surprise Me/i }).click()`; (8) assert the captured payload's `selections` JSON part has `universe === 'star-wars'` and non-null `archetype` + `artStyle`; (9) assert the page now shows the Alter Ego tab with a poster image; (10) `await page.getByRole('tab', { name: /Setup/i }).click()` once enabled, assert the Universe grid still has Star Wars selected. Run `cd frontend && npx playwright test partial-surprise-me` — confirm FAIL.

### Implementation for User Story 1

- [X] T007 [US1] Modify `surprise()` in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts` per research.md R-3: (a) import `mergeSurpriseWithExplicit` from `../lib/mergeSurpriseWithExplicit`; (b) `useAlterEgoSession()` already returns `{ dispatch }`; extend the destructure to `const { state, dispatch } = useAlterEgoSession()` (the hook's return type already exposes state — verify by reading `frontend/src/features/alterego/hooks/useAlterEgoSession.ts`); (c) inside `surprise()`, replace `const picks = randomSelections()` with `const fullRoll = randomSelections(); const picks = mergeSurpriseWithExplicit(state, fullRoll);`; (d) keep the `dispatch({ type: 'SurpriseMePicked', picks })` line; (e) build `Selections` from the merged picks AND mirror the customRole branch from `AlterEgoPage.tsx` `handleSubmit` (lines 64 + 89): `const trimmedCustomRole = state.customRole.trim(); const selections: Selections = { archetype: picks.archetype, universe: picks.universe, artStyle: picks.artStyle, photoMode: args.photoMode, firstName: args.firstName.trim(), ...(trimmedCustomRole.length > 0 ? { customRole: trimmedCustomRole } : {}) }`; (f) preserve the existing `mutation.mutate({ photoBlob: args.photoBlob, selections })` call. The JSDoc above `surprise()` needs a 028 delta paragraph citing FR-2801..FR-2806 + the merge-helper indirection. Run `cd frontend && npm test -- --run hooks/useGenerateAlterEgo lib/mergeSurpriseWithExplicit state/reducer` — confirm T004 + T005 now PASS, and the existing 009/020/022 cases still pass. Run `cd frontend && npx playwright test partial-surprise-me` — confirm T006 now PASSES.

**Checkpoint**: US1 (and structurally US3 + US4) are functional. US2 still broken (custom role survives the HTTP call but the reducer still clears it from session state on return to Setup tab).

---

## Phase 4: User Story 2 — Surprise Me preserves a non-empty Custom Role (Priority: P1)

**Goal**: When the Custom Role text input has a non-empty trimmed value, pressing Surprise Me preserves the typed string in session state (and on the wire), does NOT pick a prefab Archetype underneath it, and rolls only the remaining empty categories (Universe + Art Style).

**Independent Test**: From a clean session, capture a photo, type a name, type "Distinguished Spreadsheet Wrangler" in Custom Role, leave Universe + Art Style empty, click Surprise Me. Confirm the outbound request body carries `customRole: "Distinguished Spreadsheet Wrangler"`, omits `archetype` (or sends `null`), and carries random `universe` + `artStyle`. Confirm the Custom Role input still displays the typed string on return to Setup; the prefab grid is still blurred.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T008 [P] [US2] **Invert** the existing `'SurpriseMePicked clears customRole (022 / US3)'` describe block in `frontend/src/features/alterego/state/reducer.test.ts` (lines ~583–620). Rename to `'SurpriseMePicked preserves customRole (028)'`. Replace the three tests with: (a) "preserves a non-empty customRole when picks include an archetype" — same setup as the original test 1 but assert `next.customRole === 'Tester'` (was `''`); (b) "leaves a blank customRole blank" — same setup as the original test 2 but assert `next.customRole === ''` AS a no-op (the value was already blank — the reducer doesn't modify it); (c) "respects 022 invariant — when both archetype and customRole are seeded, the reducer overwrites archetype with picks.archetype but does NOT touch customRole" — same setup as the original test 3 but assert `next.customRole === 'Tester'` (was `''`). Run `cd frontend && npm test -- --run state/reducer` — confirm the three test names FAIL (production code still clears).
- [X] T009 [P] [US2] Extend `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx` with the US2 case: seed session with `customRole: 'Distinguished Spreadsheet Wrangler'`, `archetype: null`, `universe: null`, `artStyle: null`, photoBlob non-null, firstName "Ada" — call `surprise(...)` — assert the `mutation.mutate` call's `selections.customRole === 'Distinguished Spreadsheet Wrangler'` (trimmed match) AND `selections.archetype` carries the rolled archetype (the data-model.md stand-in — it's effectively don't-care but must be a valid Archetype value for type safety, the serialiser drops it when customRole is present per `alterEgoClient.ts` lines 74–87 — verify by reading those lines). Also assert the dispatched `SurpriseMePicked` action's `picks.universe` and `picks.artStyle` are non-null rolled values. Run — confirm FAIL (today's hook does not include `customRole` in Selections at all). Note: this test will pass automatically once T007 lands the customRole branch in surprise() — it acts as a regression pin against future Selections-builder drift.
- [X] T010 [P] [US2] Extend `frontend/tests/e2e/partial-surprise-me.spec.ts` with a second scenario for US2: (1) baseline setup as US1 but (2) type "Distinguished Spreadsheet Wrangler" into the Custom Role input by `await page.getByLabel(/Custom Role|Your own role/i).fill('Distinguished Spreadsheet Wrangler')`; (3) leave Universe + Art Style untouched (the prefab grid blurring from 022 is visual — Playwright doesn't need to assert blur; if you want, assert `await page.getByRole('button', { name: /Cloud Architect/i }).isDisabled()` or similar); (4) click Surprise Me with the same `page.route` interception; (5) assert the captured payload's `selections.customRole === 'Distinguished Spreadsheet Wrangler'` AND `selections.archetype` is absent OR a valid Archetype value (the serialiser drops it when customRole is present; we accept either shape because the serialiser logic is downstream and might cache the value); (6) after the poster lands and the Setup tab re-enables, assert `await expect(page.getByLabel(/Custom Role/i)).toHaveValue('Distinguished Spreadsheet Wrangler')`. Run — confirm FAIL (today's reducer clears customRole; the assertion in step 6 will fire).

### Implementation for User Story 2

- [X] T011 [US2] Remove the `customRole: ''` line from the `case 'SurpriseMePicked':` branch in `frontend/src/features/alterego/state/reducer.ts` (currently line ~187). Update the JSDoc above the branch: replace the "022 (issue #50) FR-2209: Surprise Me clears the custom-role input…" paragraph with a 028 paragraph: "028 (issue #TBD) FR-2814: Surprise Me MUST NOT clear customRole. The 022 FR-2209 clear is superseded — see 028 spec.md § Clarifications and data-model.md → State transitions. The 022 prefab-vs-custom invariant continues to hold via the CustomRoleChanged action; this branch does not need to enforce it." Run `cd frontend && npm test -- --run state/reducer hooks/useGenerateAlterEgo` — confirm T008 + T009 now PASS, and the existing 009/020 cases still pass. Run `cd frontend && npx playwright test partial-surprise-me` — confirm T010 PASSES.

**Checkpoint**: US1 + US2 both work independently. The entire "preserve explicit picks" surface is functional.

---

## Phase 5: User Story 3 — Surprise Me with no picks behaves like today's 009 randomizer (Priority: P2)

**Goal**: Back-compat. From a fully blank Setup form, Surprise Me rolls every visible category (matches 009 baseline exactly).

**Independent Test**: From a clean session, capture a photo, type a name, leave EVERYTHING empty, click Surprise Me. Confirm the outbound request carries valid randomly-chosen `archetype`, `universe`, `artStyle`. Confirm the Setup tab shows all three picks highlighted on return.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

> Note: this story's behavior was the **only** 009 behavior. T007 + T011 above are the production changes; the existing 009 reducer + hook test suite (the `'SurpriseMePicked (009 / 020)'` describe and the hook's pre-028 happy-path test) already pin this story. T012 below is a regression-pin extension only.

- [X] T012 [P] [US3] Extend the `'SurpriseMePicked (009 / 020)'` describe in `frontend/src/features/alterego/state/reducer.test.ts` (around line 296) with one explicit 028-era test: "given a fully blank initial session, when SurpriseMePicked dispatches with full-roll picks (archetype + universe + artStyle all rolled), the next state's archetype/universe/artStyle equal picks (no merge, no preservation — the merge happened in the hook upstream)". The test should be a near-duplicate of an existing 009 case but its name should cite "(US3 — 028 back-compat regression pin)". Run — should PASS today because the reducer overwrites whatever picks carries. The point of this task is documentary: future readers see why the US3 behavior is preserved despite the 028 merge logic.

### Implementation for User Story 3

No new production code. T007 (the hook's `mergeSurpriseWithExplicit` call) returns `fullRoll` verbatim when the session is empty — the helper's branch coverage in T002 case (a) pins this exact behavior. The reducer fix from T011 doesn't affect US3 (the all-blank session has no customRole to preserve in the first place).

**Checkpoint**: US3 is functional and regression-pinned. The 009 baseline behavior is preserved through the 028 pivot.

---

## Phase 6: User Story 4 — Surprise Me when every visible category is already explicit (Priority: P3)

**Goal**: Robustness. When the user has filled every visible category before clicking Surprise Me, the click MUST still fire the generation pipeline with zero substitutions (no error, no silent no-op).

**Independent Test**: From a clean session, capture a photo, type a name, pick a prefab Role + a Universe + an Art Style explicitly, click Surprise Me. Confirm the outbound request body's category values match the user's picks exactly (no substitution). Confirm the poster renders normally.

### Tests for User Story 4 (MANDATORY — must fail before implementation) ⚠️

- [X] T013 [P] [US4] Extend `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx` with the US4 case: seed session with all three categories explicit (`archetype: 'cloud-architect'`, `universe: 'star-wars'`, `artStyle: 'oil-painting'`, `customRole: ''`) — call `surprise(...)` — assert `mutation.mutate` was called once with `selections.archetype === 'cloud-architect'`, `selections.universe === 'star-wars'`, `selections.artStyle === 'oil-painting'`. Also assert the dispatched `SurpriseMePicked` action's `picks` is byte-equal to those three values (no random substitution). Mock `Math.random` to return `0.999` so the underlying `randomSelections()` would have picked the LAST option in each list — if the merge is wrong, the assertion fails clearly because the wire payload would carry the rolled values. Run — should PASS once T007 is in place; this task acts as a regression pin against future merge bugs.

### Implementation for User Story 4

No new production code. T007 + T011 cover the all-explicit branch via the helper's "all-explicit → returns session values" case (T002 case (f)).

**Checkpoint**: All four user stories are functional and independently tested.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Final-mile validation and hygiene.

- [X] T014 [P] Run `cd frontend && npm run lint` — ESLint flat config across the repo; resolve any new warnings introduced by the helper file, the hook edit, and the reducer edit. Zero new errors / zero new warnings is the gate.
- [X] T015 [P] Run `cd frontend && npm test -- --run --coverage`. Confirm line coverage on the new file `mergeSurpriseWithExplicit.ts` is 100% (it has eight branches, all covered by T002). Confirm `useGenerateAlterEgo.ts`'s `surprise()` body retains ≥ 90% coverage. Confirm `reducer.ts`'s `case 'SurpriseMePicked'` branch is fully covered. Adjust tests in T002 / T004 / T009 if any branch is uncovered.
- [ ] T016 Walk through `specs/028-partial-surprise-me/quickstart.md` _(deferred — no interactive browser session in this run; the Playwright spec exercises US1+US2 end-to-end which covers the high-value cases; reviewer to walk the full §2..§7 quickstart before merge)_ end-to-end in a real browser (`npm run dev`) — US1, US2, US3, US4 plus the "fresh roll requires Start Over" edge case from quickstart §6. Verify the troubleshooting hints (§7) trigger correctly if you deliberately introduce a regression (e.g., temporarily re-add `customRole: ''` to the reducer branch and confirm US2's quickstart fails as described).
- [X] T017 Grep audit: from repo root, `grep -RIn 'localStorage\|sessionStorage\|indexedDB\|document.cookie' frontend/src/features/alterego/` returns zero NEW lines vs. `main` (FR-2814 implicit — no persistence regression introduced; Surprise Me's "preserve" semantics live in in-memory session state ONLY).
- [X] T018 Wire-format audit: `git diff main -- frontend/src/features/alterego/services/alterEgoClient.ts frontend/src/features/alterego/types.ts` is empty (FR-2812 — outbound HTTP body shape unchanged). If the diff is non-empty, the wire contract drifted; reject the change.
- [X] T019 Backend sanity: `./gradlew :backend:test` from repo root is green (Surprise Me is frontend-only — this MUST not regress; if it does, the 028 spec assumption "no backend change" was violated).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** — T001: no dependencies, must run first.
- **Foundational (Phase 2)** — T002 → T003: BLOCKS Phases 3–6. T002 must FAIL before T003.
- **User Story 1 (Phase 3)** — depends on Phase 2 complete. T004 / T005 / T006 are different files → parallel-writable; all three MUST fail before T007.
- **User Story 2 (Phase 4)** — depends on T007 being in place (the customRole pass-through in the Selections builder). T008 / T009 / T010 are different files → parallel-writable; all three MUST fail before T011.
- **User Story 3 (Phase 5)** — depends on T007 (back-compat is structurally covered). T012 is a regression pin only.
- **User Story 4 (Phase 6)** — depends on T007 + T011 (all-explicit → no-op). T013 is a regression pin only.
- **Polish (Phase 7)** — depends on Phases 2–6 complete.

### User Story Dependencies

- **US1 (P1)** — MVP. T007 (hook edit) is the central change. Once delivered, US3 + US4 are structurally functional.
- **US2 (P1)** — independent production task (T011 — reducer one-line removal). Tests rely on T007 having merged the customRole pass-through into `Selections`.
- **US3 (P2)** — no production code; covered by T002 case (a) + T007.
- **US4 (P3)** — no production code; covered by T002 case (f) + T007.

### Within Each User Story

- Tests MUST FAIL before any production change (Principle III — non-negotiable).
- Helper before hook (T003 before T007).
- Hook edit before reducer fix (T007 before T011) — strictly speaking they're independent, but Phase 4 tests piggyback on T007's Selections shape, so this order is cleaner.

### Parallel Opportunities

- **T002 stands alone** in Phase 2 — single file edit.
- **T004 + T005 + T006** are three different files (hook test + reducer test + new Playwright spec) → parallel-writable.
- **T008 + T009 + T010** are three different files (reducer test + hook test + Playwright spec extension) → parallel-writable.
- **T012 + T013** are independent test extensions in two different files → parallel-writable.
- **T014 + T015** are independent passes → parallel-runnable.
- **T017 + T018 + T019** are three independent grep/diff/test runs → parallel-runnable.

---

## Parallel Example: User Story 1 tests

```bash
# After Phase 2 completes, write all three failing test files in parallel:
Task: "Extend frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx with US1 cases (T004)"
Task: "Extend frontend/src/features/alterego/state/reducer.test.ts with US1 case (T005)"
Task: "Write failing Playwright spec frontend/tests/e2e/partial-surprise-me.spec.ts (T006)"

# Then run all three:
cd frontend && npm test -- --run hooks/useGenerateAlterEgo state/reducer
cd frontend && npx playwright test partial-surprise-me

# Confirm all three FAIL for the right reasons before T007 lands.
```

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. T001 (baseline green, Playwright dir located).
2. T002 → T003 (helper — Foundational).
3. T004 → T005 → T006 (failing tests, parallel).
4. T007 (hook edit — single implementation task).
5. **STOP and VALIDATE**: run quickstart.md §2 "Validate US1" — six observations.
6. Ship MVP if green.

### Incremental Delivery

- After MVP: T008 → T009 → T010 (US2 failing tests, parallel) → T011 (US2 reducer fix) → quickstart §3 walk.
- Then T012 (US3 regression pin) → quickstart §4 walk.
- Then T013 (US4 regression pin) → quickstart §5 walk.
- Then Phase 7 polish: lint, coverage, manual quickstart walk, persistence audit, wire-format audit, backend sanity.

### Single-Developer Strategy

A single developer can deliver the entire feature linearly in one PR. Branching points are TDD-marked: never start a `T00x implementation` task before the matching `T00y test` task FAILs.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- All test tasks (T002, T004, T005, T006, T008, T009, T010, T012, T013) MUST be observed to FAIL before the matching implementation task begins (Principle III). T005, T012, T013 are regression-pins and may pass on the first run AFTER T007 is in place — the intent is documentary.
- Commit boundary suggestion: one commit per checkpoint — Phase 2 done, Phase 3 done, Phase 4 done, Phase 7 done. PR title: `feat(028): partial Surprise Me preserves explicit picks (closes #TBD)`.
- No backend file is modified. No new dependency is added. No wire-format change. No persistence introduced. Rollback = single revert.

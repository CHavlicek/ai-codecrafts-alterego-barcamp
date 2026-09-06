---

description: "Task list for 020 — Hide Vibe and Pose categories from the Setup UI"
---

# Tasks: Hide Vibe and Pose Categories from the Setup UI

**Input**: Design documents from `/specs/020-hide-vibe-pose/`
**Prerequisites**: plan.md, spec.md (both required); research.md, data-model.md, contracts/, quickstart.md (all present).

**Tests**: MANDATORY per Constitution Principle III (Test-First, NON-NEGOTIABLE). Every test below MUST be written before the production code that satisfies it and MUST run red on commit. Coverage gate ≥ 90 % per module holds after the change (verified in Phase 6).

**Organization**: Tasks are grouped by user story. The spec has three P1 stories. They are technically interlinked (the frontend cannot reach a working backend if the frontend ships first), so the **Implementation Strategy** section at the bottom states the merge order: Foundational → US2 → US1 → US3 → Polish. The phases below follow spec priority order (US1 → US2 → US3) for traceability; the dependency arrows in **Dependencies & Execution Order** drive actual sequencing.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks).
- **[Story]**: User story this task belongs to (US1 / US2 / US3). Setup, Foundational, and Polish phases carry no story label.
- Every task description ends with the exact repo-root-relative file path it touches (or creates / deletes).

## Path Conventions

- Backend production: `backend/src/main/java/com/aiavatar/alterego/...`
- Backend tests: `backend/src/test/java/com/aiavatar/alterego/...`
- Frontend (all): `frontend/src/features/alterego/...`

---

## Phase 1: Setup

**Purpose**: Confirm working tree state. No new tooling, no new dependency. The branch `020-hide-vibe-pose` is already cut from `main`.

- [X] T001 Confirm working tree is on branch `020-hide-vibe-pose` and clean of unrelated tracked changes (`git status -sb`).
- [X] T002 Create the new backend sub-package directory `backend/src/main/java/com/aiavatar/alterego/service/random/` (empty; production class lands in Phase 2).
- [X] T003 [P] Create the new backend test sub-package directory `backend/src/test/java/com/aiavatar/alterego/unit/random/` (empty; test class lands in Phase 2).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add the two new building blocks that every user story depends on — the random selector and the public-DTO split. Pure additions; nothing existing is modified yet, so this phase cannot break the build.

**⚠️ CRITICAL**: No user story phase may begin until this phase is complete and green.

### Tests first (Principle III, must fail red before production code)

- [X] T004 Write `RandomCategorySelectorTest` covering: (a) `pickUniform` returns one of `Pose.values()`; (b) uniformity across 10 000 draws with a seeded `RandomGenerator`, asserting each of the 4 Pose constants appears ≥ 2125 times; (c) same uniformity check for `Vibe.values()`; (d) empty enum throws `IllegalStateException`; (e) injected `RandomGenerator` is honoured (deterministic test fixture); (f) two consecutive calls with a seeded RNG match the deterministically-expected indices — in `backend/src/test/java/com/aiavatar/alterego/unit/random/RandomCategorySelectorTest.java`. Run `./gradlew test --tests RandomCategorySelectorTest` and confirm it fails because the class does not yet exist.
- [X] T005 [P] Write `AlterEgoUserSelectionsValidationTest` covering: (a) a valid record produces zero violations; (b) `@NotNull` violation on each of `archetype` / `universe` / `artStyle`; (c) `@NotBlank` + `@ValidFirstName` violations on `firstName`; (d) absence of the `pose` and `vibe` fields is structurally enforced (a static-style sanity assertion: `Stream.of(AlterEgoUserSelections.class.getRecordComponents()).noneMatch(c -> c.getName().equals("pose") || c.getName().equals("vibe"))`). File: `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoUserSelectionsValidationTest.java`. Run and confirm red on missing class.

### Production code (turns the above tests green)

- [X] T006 Implement `RandomCategorySelector` as a `@Component` Spring bean with one public method `<E extends Enum<E>> E pickUniform(Class<E> clazz)`, a public no-arg constructor using `RandomGenerator.getDefault()`, and a package-private constructor accepting an injected `RandomGenerator` for tests. Throws `IllegalStateException` if `getEnumConstants()` is null or empty. File: `backend/src/main/java/com/aiavatar/alterego/service/random/RandomCategorySelector.java`. Re-run T004 — all assertions must now pass.
- [X] T007 [P] Implement `AlterEgoUserSelections` Java record with components `archetype` (`@NotNull Archetype`), `universe` (`@NotNull Universe`), `artStyle` (`@NotNull ArtStyle`), `firstName` (`@NotBlank @Size(max = 50) @ValidFirstName String`), `photoMode` (`PhotoMode`, optional). No `pose`, no `vibe`. File: `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoUserSelections.java`. Re-run T005 — green.

**Checkpoint**: Both new building blocks compile and are fully unit-tested. The existing controller / service / DTO chain is unchanged and still uses `AlterEgoRequest`. Build is green; no public contract has shifted yet.

---

## Phase 3: User Story 1 — Simpler Setup tab without Pose and Vibe (Priority: P1)

**Goal**: Setup tab renders exactly three numbered theme groups (1 Archetype / 2 Universe / 3 Art Style), no Pose or Vibe anywhere — DOM, accessibility tree, tab order, source map. Generate gates on photo + Archetype + Universe + Art Style + first name. Surprise Me gating unchanged. (Spec FR-2001 / FR-2002 / FR-2003 / FR-2004 / FR-2005 / FR-2010 / FR-2011 / FR-2015 / FR-2050 / FR-2051 / FR-2052 / FR-2060.)

**Independent Test**: `npm test` passes; in the browser at `npm run dev`, the Setup tab shows three numbered groups in correct order and no Pose / Vibe elements. The Generate button enables on photo + Archetype + Universe + Art Style + first name.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T008 [P] [US1] Update `SetupLayout.test.tsx` to assert: (a) the rendered DOM contains exactly three numbered groups with `data-step="1"`, `data-step="2"`, `data-step="3"`; (b) the visible label text of group 1 is "Archetype", group 2 is "Universe", group 3 is "Art Style"; (c) no element with `role="group"` (or other roles) carries the label "Pose" or "Vibe"; (d) `queryByLabelText('Pose')` and `queryByLabelText('Vibe')` return `null`; (e) the dispatch sequence on user interaction no longer includes `PoseSelected` or `VibeSelected`. File: `frontend/src/features/alterego/components/SetupLayout.test.tsx`. Run `npm test -- SetupLayout` — red until T013–T014.
- [X] T009 [P] [US1] Update `reducer.test.ts` to: (a) delete the `PoseSelected` and `VibeSelected` cases; (b) tighten the `SurpriseMePicked` case to assert the action no longer carries `pose` or `vibe`; (c) add a structural assertion that `AlterEgoSession` (returned by `initialSession()`) has no `pose` or `vibe` keys (`expect(Object.keys(initialSession())).not.toContain('pose')`). File: `frontend/src/features/alterego/state/reducer.test.ts`. Run `npm test -- reducer` — red until T015–T016.
- [X] T010 [P] [US1] Update `selectors.test.ts` to assert: (a) `RequiredInput` no longer includes `'pose'`; (b) `missingInputs({ ... photo, archetype, universe, artStyle, firstName all set, no pose })` returns `[]`; (c) `isReadyToGenerate(...)` returns `true` for that state. File: `frontend/src/features/alterego/state/selectors.test.ts`. Run `npm test -- selectors` — red until T017.

### Implementation for User Story 1

- [X] T011 [P] [US1] Delete `frontend/src/features/alterego/components/PoseGrid.tsx`.
- [X] T012 [P] [US1] Delete `frontend/src/features/alterego/components/PoseGrid.test.tsx`.
- [X] T013 [P] [US1] Delete `frontend/src/features/alterego/components/VibeGrid.tsx`.
- [X] T014 [P] [US1] Delete `frontend/src/features/alterego/components/VibeGrid.test.tsx`.
- [X] T015 [US1] Modify `SetupLayout.tsx`: remove the `PoseGrid` and `VibeGrid` imports; remove the two corresponding `<div className="setup-layout__numbered-group">` blocks; renumber the remaining three groups to `data-step="1"` (Archetype), `data-step="2"` (Universe), `data-step="3"` (Art Style); keep Name + Generate / Surprise Me row in place. File: `frontend/src/features/alterego/components/SetupLayout.tsx`. Re-run T008 — green.
- [X] T016 [US1] Modify `reducer.ts`: (a) drop `pose: Pose | null` and `vibe: Vibe | null` from `AlterEgoSession`; (b) drop the `PoseSelected` and `VibeSelected` variants from `AlterEgoAction`; (c) delete those two cases from the reducer switch; (d) update the `SurpriseMePicked` reducer branch to no longer write `pose` / `vibe` (since the action payload no longer carries them after T026 lands); (e) update `initialSession()` to omit `pose` / `vibe`. File: `frontend/src/features/alterego/state/reducer.ts`. Re-run T009 — green.
- [X] T017 [US1] Modify `selectors.ts`: drop `'pose'` from the `RequiredInput` union; remove the `if (!state.pose) missing.push('pose')` line from `missingInputs`. File: `frontend/src/features/alterego/state/selectors.ts`. Re-run T010 — green.
- [X] T018 [P] [US1] Modify `types.ts`: delete the `Pose` and `Vibe` type aliases; drop the `pose` and `vibe?` fields from `Selections`. File: `frontend/src/features/alterego/types.ts`.
- [X] T019 [P] [US1] Modify `options.ts`: delete `POSE_OPTIONS`, `VIBE_OPTIONS`, and the unused icon imports they relied on (`Shield`, `Eye`, `Wand2`, `Book`, `Hammer`, `Moon`, `Bolt`, `Feather` — only the ones not used elsewhere; keep any still referenced by other grids); delete the `pose` and `vibe` entries from `ACCENT_VARS`. File: `frontend/src/features/alterego/options.ts`.
- [X] T020 [US1] Modify `alterEgoClient.test.ts`: update the multipart-body assertions to expect the `selections` JSON without `pose` and `vibe` keys. File: `frontend/src/features/alterego/services/alterEgoClient.test.ts`.
- [X] T021 [US1] Run the full frontend suite (`cd frontend && npm test`). All previously-failing US1 tests must be green; no regression elsewhere.

**Checkpoint US1**: The Setup tab UI is fully cleaned up. Frontend builds, frontend tests pass. The frontend now sends the **new-shape** request (no `pose`, no `vibe`) which the backend will reject (`@NotNull Pose pose` still in place on the old `AlterEgoRequest`) — this is expected and will be resolved by US2.

---

## Phase 4: User Story 2 — Generate uses a random Pose and Vibe behind the scenes (Priority: P1)

**Goal**: The backend accepts the new-shape `selections` JSON (no `pose`, no `vibe`), rolls one Pose and one Vibe value per request via `RandomCategorySelector`, and passes a fully-populated `AlterEgoRequest` to the prompt builders. Existing prompt-building logic is bit-for-bit preserved. (Spec FR-2020 / FR-2021 / FR-2022 / FR-2023 / FR-2024 / FR-2025 / FR-2040 / FR-2041 / FR-2042.)

**Independent Test**: `./gradlew test` is green; `curl -F 'photo=…' -F 'selections={"archetype":"engineer","universe":"star-wars","artStyle":"oil-painting","firstName":"Mia","photoMode":"shoulders-up"}' http://localhost:8080/api/v1/alter-egos` returns 200. Repeating the same curl call produces requests whose internal rolled Pose / Vibe varies (verified via slice test, not by parsing logs).

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T022 [US2] Update `AlterEgoControllerContractTest`: (a) the existing happy-path fixture's JSON drops `pose` and `vibe`, and the test asserts 200; (b) add a new test `requestBodyWithoutPoseOrVibeProduces200` covering only the new shape; (c) add `clientSuppliedPoseAndVibeAreIgnored` — send 50 requests with `pose: "heroic"` and `vibe: "rebel"` set, capture the resolved `AlterEgoRequest` via a `@MockBean` on the relevant prompt builder (or a partial test-double seam already exposed by the service tests), and assert ≥ 2 distinct Pose values and ≥ 2 distinct Vibe values across the captures. File: `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java`. Run `./gradlew test --tests AlterEgoControllerContractTest` — red.
- [X] T023 [US2] Add `AlterEgoServiceTest.rollsServerSidePoseAndVibePerRequest` (or update the existing one): with a stubbed `RandomCategorySelector` returning a programmed sequence, assert the resolved `AlterEgoRequest` handed to the (mocked) prompt-builder seam carries the expected Pose and Vibe. Also assert that the user's supplied (`archetype`, `universe`, `artStyle`, `firstName`, `photoMode`) flow through verbatim. File: `backend/src/test/java/com/aiavatar/alterego/service/AlterEgoServiceTest.java`. Red.
- [X] T024 [US2] Delete `AlterEgoRequestValidationTest` — the public DTO is now `AlterEgoUserSelections` and its validation is covered by T005. File: `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoRequestValidationTest.java`. (This task is independent of the implementation work below; can run in parallel with T022 / T023 in code-review terms but must land in the same PR.)

### Implementation for User Story 2

- [X] T025 [US2] Modify `AlterEgoController.generate(...)`: change the `@Valid @RequestPart("selections") AlterEgoRequest selections` parameter to `@Valid @RequestPart("selections") AlterEgoUserSelections userSelections`; update the downstream call into `AlterEgoService.generate(...)` accordingly. File: `backend/src/main/java/com/aiavatar/alterego/controller/AlterEgoController.java`.
- [X] T026 [US2] Modify `AlterEgoService.generate(...)`: change the signature to accept `AlterEgoUserSelections userSelections`; at the top of the method, inject (constructor-wire) the `RandomCategorySelector`, call `pickUniform(Pose.class)` and `pickUniform(Vibe.class)`, and construct a server-internal `AlterEgoRequest` via its existing constructor; pass that resolved record to the remainder of the method unchanged. Add the `RandomCategorySelector` to the service's constructor parameter list. File: `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java`. Re-run T022 / T023 — green.
- [X] T027 [US2] Verify `AlterEgoRequest.java` is **not modified** (its field set is preserved as the resolved-request shape). File: `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`. No production change; this is a deliberate non-edit checkpoint.
- [X] T028 [US2] Verify the three prompt builders (`GeminiPromptBuilder.java`, `GeminiCharacterPromptBuilder.java`, `FalAiPromptBuilder.java`) are **not modified**. Each one continues to consume `AlterEgoRequest` and produce the same prompt strings. File paths under `backend/src/main/java/com/aiavatar/alterego/service/gemini/` and `backend/src/main/java/com/aiavatar/alterego/service/falai/`. No production change; this is a deliberate non-edit checkpoint.
- [X] T029 [US2] Run the full backend suite (`cd backend && ./gradlew test`). All US2 tests green; existing service / prompt-builder tests green; no new failures elsewhere.

**Checkpoint US2**: Backend accepts the new request shape, ignores client-supplied `pose` / `vibe` (Jackson default), and rolls fresh values per request. Prompt-building logic is preserved. Together with US1, the end-to-end flow now works in a browser.

---

## Phase 5: User Story 3 — Surprise Me still randomises everything, including Pose and Vibe (Priority: P1)

**Goal**: `randomSelections()` and the `SurpriseMePicked` action no longer mention Pose or Vibe; the `useGenerateAlterEgo.surprise(...)` entry point no longer commits Pose / Vibe to session state or sends them to the backend. The server rolls Pose / Vibe for Surprise Me requests exactly as for plain Generate (no extra wiring needed — same controller endpoint). (Spec FR-2030 / FR-2031.)

**Independent Test**: `npm test -- randomSelections useGenerateAlterEgo` is green. In the browser, pressing Surprise Me with photo + first name set populates only Archetype / Universe / Art Style visibly, kicks off generation, and the resulting request body matches the new shape (no `pose`, no `vibe`).

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T030 [P] [US3] Update `randomSelections.test.ts`: drop the `pose` and `vibe` assertions; assert `SurpriseMePicks` has only `archetype`, `universe`, `artStyle`; assert that 50 draws cover every Archetype / Universe / ArtStyle constant at least once. File: `frontend/src/features/alterego/lib/randomSelections.test.ts`. Red until T032.
- [X] T031 [P] [US3] Update `useGenerateAlterEgo.test.ts`: in the `surprise(...)` test path, assert the dispatched `SurpriseMePicked` action's `picks` field has only `archetype`, `universe`, `artStyle`; assert the subsequent mutation receives a `Selections` payload with no `pose` / `vibe` keys. File: `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.ts`. Red until T033.

### Implementation for User Story 3

- [X] T032 [US3] Modify `randomSelections.ts`: drop the `pose` and `vibe` fields from the `SurpriseMePicks` interface; remove their lines from the `randomSelections(rng)` return object. File: `frontend/src/features/alterego/lib/randomSelections.ts`. Re-run T030 — green.
- [X] T033 [US3] Modify `useGenerateAlterEgo.ts`: in `surprise(...)`, drop the `pose: picks.pose` and `vibe: picks.vibe` properties from the `selections` object handed to `mutation.mutate(...)`. The `SurpriseMePicked` dispatch already carries the trimmed `picks` after T032. File: `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts`. Re-run T031 — green.
- [X] T034 [US3] Run the full frontend suite (`cd frontend && npm test`). All three stories' tests green; no regression.

**Checkpoint US3**: All three stories' acceptance scenarios from `spec.md` pass. The feature is functionally complete.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Verify the constitution gates and the spec's measurable success criteria, then run quickstart.md end-to-end.

- [X] T035 [P] Run `cd frontend && npm test -- --coverage` and verify line coverage ≥ 90 % (Constitution III). Run `cd backend && ./gradlew test jacocoTestReport` and verify the same. Capture the two coverage summaries in the PR description.
- [X] T036 [P] Run `cd frontend && npm run lint`. Zero new warnings or errors.
- [X] T037 [P] Run `cd frontend && npm audit --omit=dev` and `cd backend && ./gradlew dependencyCheckAnalyze`. No new HIGH / CRITICAL advisories (Constitution VI).
- [X] T038 [P] Search the repo for residual references to "Pose" or "Vibe" on the frontend (`rg -n 'Pose|Vibe' frontend/src`). The only legitimate matches should be unrelated identifiers (none expected in `features/alterego/`). Confirm zero hits under `frontend/src/features/alterego/`. File: report attached to the PR description.
- [X] T039 Run the quickstart.md recipe end-to-end (sections 2–4: manual UI check, Generate twice, ignore-old-shape curl). Attach the curl outputs to the PR description.
- [X] T040 Lighthouse accessibility audit on the Setup tab (Constitution-adjacent; spec SC-005). Score ≥ pre-change baseline; no new `aria-*` warnings, no new focus-order findings. Attach a screenshot of the Lighthouse report to the PR description.
- [X] T041 Smoke-test the print artefact (quickstart.md section 7). Visually identical to a pre-change run modulo the rolled Pose / Vibe variation. Confirm on PR description.

**Checkpoint POLISH**: All gates green. Feature ready for PR review.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → no dependencies; complete first.
- **Foundational (Phase 2)** → depends on Phase 1; **BLOCKS all user-story phases**.
- **US1 (Phase 3)** → depends on Foundational. Touches frontend only. Can be implemented in parallel with US2 phase 4 tasks by a second developer, but **its merge depends on US2 having landed first**, because once US1 ships the frontend will send the new-shape request that only a US2-fixed backend can accept.
- **US2 (Phase 4)** → depends on Foundational. Touches backend only. Can be implemented and merged independently — accepts the old shape (extra keys ignored) and the new shape.
- **US3 (Phase 5)** → depends on Phase 3 (it edits files that Phase 3 already changes: the same reducer and the same `useGenerateAlterEgo` hook). Run after US1 to avoid merge conflicts.
- **Polish (Phase 6)** → depends on all three user-story phases being complete.

### Within Each User Story

- All `Tests for User Story N` tasks must be written and failing before any "Implementation for User Story N" task is committed (Constitution III).
- Within a single user story, deletions (T011–T014, T024) can land before edits; edits to a single file (`reducer.ts`, `SetupLayout.tsx`, `AlterEgoController.java`, `AlterEgoService.java`) are serial — only one task per file at a time.

### Parallel Opportunities

- **Phase 2**: T005 || T007 (independent files); T004 must precede T006 (TDD red→green on the same class).
- **Phase 3**: T008 || T009 || T010 || T011 || T012 || T013 || T014 || T018 || T019 (all different files / pure deletions); T015 / T016 / T017 / T020 are each a single-file edit that must follow their corresponding test task.
- **Phase 4**: T022 || T023 || T024 (independent files); T025 and T026 each edit a single file and must follow the tests; T027 / T028 are non-edit verification checkpoints.
- **Phase 5**: T030 || T031 (independent files); T032 / T033 each edit a single file and must follow the tests.
- **Phase 6**: T035 / T036 / T037 / T038 all `[P]`.

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests in parallel:
Task: "Update SetupLayout.test.tsx (T008)"
Task: "Update reducer.test.ts (T009)"
Task: "Update selectors.test.ts (T010)"

# After tests are red, launch all US1 deletions in parallel:
Task: "Delete PoseGrid.tsx (T011)"
Task: "Delete PoseGrid.test.tsx (T012)"
Task: "Delete VibeGrid.tsx (T013)"
Task: "Delete VibeGrid.test.tsx (T014)"

# In parallel with the per-file edits T015/T016/T017/T020:
Task: "Modify types.ts (T018)"
Task: "Modify options.ts (T019)"
```

---

## Implementation Strategy

### Recommended merge order (single PR)

For a small team this feature is best shipped as **one PR on branch `020-hide-vibe-pose`** containing all phases. The PR is internally ordered so that, at any commit, the codebase compiles:

1. Setup (T001–T003).
2. Foundational (T004 → T006; T005 → T007 in parallel).
3. **US2 backend** (T022–T029) — backend accepts both old and new request shapes.
4. **US1 frontend** (T008–T021) — frontend stops sending Pose / Vibe.
5. **US3 cleanup** (T030–T034) — Surprise Me final cleanup.
6. Polish (T035–T041).

This order means the backend is always "newer" than the frontend; old and new frontends both work against the new backend during the PR's commit-by-commit review.

### Two-developer split

- Developer A: Phase 2 (T004–T007) → Phase 4 (T022–T029).
- Developer B: After Foundational is merged into the feature branch — Phase 3 (T008–T021) → Phase 5 (T030–T034).
- Both: Phase 6 together.

### MVP-only scope

If for any reason only one story can ship: **US2 alone** (Phase 4) is the safest stand-alone increment because it accepts both old and new request shapes without breaking any client. US1 and US3 in isolation would break the kiosk end-to-end and are not viable as MVP slices on their own.

---

## Notes

- `[P]` tasks operate on disjoint files; verify before parallelising.
- Constitution III: every implementation task in Phases 3–5 has a preceding test task in the same phase. Do not commit production code before its test is red on disk.
- No new dependency is added in any task (Constitution I, VI).
- The five backend "non-edit checkpoints" (T027 for `AlterEgoRequest.java`; T028 for the three prompt builders) are deliberate — they remind reviewers that the existing prompt-building logic is preserved bit-for-bit (FR-2040 / FR-2041).
- Branch on completion: open a PR from `020-hide-vibe-pose` → `main`, request explicit human approval (Constitution V).

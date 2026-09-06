---

description: "Task list for feature 019-remove-art-styles"
---

# Tasks: Remove Line Art, Low-Poly 3D, and Pixel Art from Art Style category

**Input**: Design documents from `/specs/019-remove-art-styles/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). Tests in this feature are **fixture- and assertion-edits** of existing tests rather than newly-authored modules — the spec is subtractive — but the same RED → GREEN discipline applies: every assertion change must FAIL against today's nine-member source-of-truth before the source-of-truth is trimmed, then GREEN after.

**Organization**: Tasks are grouped by user story (US1, US2) so each story can be completed and verified independently. Inside each story the order is *frontend RED → frontend GREEN → backend RED → backend GREEN* per stack half, because TypeScript and `javac` each light up their stack's stale fixtures by compile error.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (`[US1]`, `[US2]`); Setup / Foundational / Polish phases carry no story label
- Include exact file paths in descriptions

## Path Conventions

This feature is web-app subtractive: it touches `frontend/src/features/alterego/` and `backend/src/main/java/com/aiavatar/alterego/` plus their colocated tests. No new directories, no new files (except this `tasks.md` and the `019` contract delta already published). All paths are absolute from the repo root.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the branch, working tree, and toolchains are ready. There is no new project to scaffold — the change is subtractive within existing modules.

- [X] T001 Confirm working tree is clean and on branch `019-remove-art-styles` (no untracked changes outside this feature dir); run `git status` and resolve before continuing
- [X] T002 [P] Smoke-build frontend baseline (RED expected only after T011/T012): `cd frontend && npm ci && npm run lint && npm test -- --run` — capture the pre-change baseline as green
- [X] T003 [P] Smoke-build backend baseline (RED expected only after T030/T031): `cd backend && ./gradlew check` — capture the pre-change baseline as green

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: One unconditional precondition — verify the 019 OpenAPI contract delta is published and self-consistent before any code change starts referencing the new (six-member) wire surface.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T004 Verify `specs/019-remove-art-styles/contracts/alter-egos.openapi.yaml` lists exactly six `ArtStyle` enum values (`oil-painting`, `watercolor`, `pop-art`, `renaissance-portrait`, `japanese-woodblock`, `cel-shaded`), in this declaration order, with the three removed values named in `info.description`. No code change — review-only checkpoint.

**Checkpoint**: Contract delta verified ⇒ user-story implementation can begin.

---

## Phase 3: User Story 1 — Manual Setup hides the three retired options (Priority: P1) 🎯 MVP

**Goal** (spec §User Story 1): A user opens the Setup tab and sees exactly six Art Style tiles; Line Art, Low-Poly 3D, and Pixel Art are absent everywhere in the UI. Surviving tiles keep their labels, wire values, icons, and relative order (FR-1901, FR-1904).

**Independent Test**: With the change deployed, open Setup → Art Style. Six tiles render; none labelled Pixel Art, Low-Poly 3D, or Line Art; tile order is `oil-painting → watercolor → pop-art → renaissance-portrait → japanese-woodblock → cel-shaded`. Keyboard navigation only reaches six tiles. DOM has no `data-value` for retired wire values.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> RED step — every change below either flips an existing assertion (9 → 6) or removes a parameterised case that exercised a retired value. Run `npm test -- --run` and confirm the modified tests RED before continuing.

- [X] T010 [P] [US1] Flip cardinality assertion to six tiles and drop retired-value rows in `frontend/src/features/alterego/components/ArtStyleGrid.test.tsx`; assert tile order `['oil-painting','watercolor','pop-art','renaissance-portrait','japanese-woodblock','cel-shaded']` and assert `screen.queryByText(/pixel art|low-poly|line art/i)` returns `null` (SC-1901, FR-1901, FR-1904)
- [X] T011 [P] [US1] Update `Selections` fixture `artStyle` from any retired value to `'oil-painting'` in `frontend/src/features/alterego/state/reducer.test.ts` (lines 93, 108, 119, 121, 128, 130, 131, 137, 416) — verify TS errors RED before changing source
- [X] T012 [P] [US1] Update `Selections` fixture `artStyle` to `'oil-painting'` in `frontend/src/features/alterego/state/selectors.test.ts` (lines 28, 62, 246) — verify TS errors RED before changing source
- [X] T013 [P] [US1] Update `Selections` / scenario fixtures to swap retired values for `'oil-painting'` in `frontend/src/features/alterego/components/GenerateButton.test.tsx` — verify TS errors RED before changing source
- [X] T014 [P] [US1] Update fixtures in `frontend/src/features/alterego/components/SetupLayout.test.tsx` (any `artStyle: 'pixel-art' | 'low-poly-3d' | 'line-art'` → `'oil-painting'`)
- [X] T015 [P] [US1] Update fixtures in `frontend/src/features/alterego/components/PrintArtefact.test.tsx`
- [X] T016 [P] [US1] Update fixtures in `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx`
- [X] T017 [P] [US1] Update fixtures in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx`; keep the `ART_STYLE_OPTIONS.map((o) => o.value)).toContain(state.artStyle)` assertion (lines 9 + 255) — its strength increases under the new six-member set
- [X] T018 [P] [US1] Update fixtures in `frontend/src/features/alterego/services/alterEgoClient.test.ts`

### Implementation for User Story 1

> GREEN step — trim the two source-of-truth declarations on the frontend. The TS compiler will now confirm every fixture site is consistent; any remaining TS error is a fixture missed in T011–T018 — fix it.

- [X] T019 [US1] Remove the three retired string literals (`'pixel-art' | 'low-poly-3d' | 'line-art'`) from the `ArtStyle` union in `frontend/src/features/alterego/types.ts` (lines 45–47 of the current file). This compile-locks the contract (FR-1901, FR-1907)
- [X] T020 [US1] Remove the three retired rows (`{ value: 'pixel-art', ... }`, `{ value: 'low-poly-3d', ... }`, `{ value: 'line-art', ... }`) from `ART_STYLE_OPTIONS` in `frontend/src/features/alterego/options.ts` (lines 105–107 of the current file). Preserve the relative order of the six survivors (FR-1904). The `randomSelections.ts:62` randomiser and the `ArtStyleGrid.tsx` renderer both consume this array and will pick up the trimmed set automatically (research R1)
- [X] T021 [US1] Run `cd frontend && npm run lint && tsc --noEmit && npm test -- --run` — fix any remaining TS error by swapping a missed retired-value fixture to `'oil-painting'`; repeat until green
- [X] T022 [US1] Manual UI smoke check via `npm run dev`: load Setup → Art Style; confirm six tiles, surviving labels, surviving order, no retired tile anywhere; keyboard-navigate the grid; inspect DOM for absence of `data-value="pixel-art|low-poly-3d|line-art"`

**Checkpoint**: User Story 1 is independently verifiable. Surprise Me may still pick a retired value at this point — that is fixed in User Story 2. Do NOT ship 019 with only US1 complete.

---

## Phase 4: User Story 2 — Surprise Me never picks a retired option (Priority: P1)

**Goal** (spec §User Story 2): Surprise Me's Art Style pick is always one of the six survivors; repeated presses (interactive or simulated) never yield a retired value (FR-1902, SC-1902).

**Independent Test**: Run `randomSelections.test.ts` — every committed `artStyle` across ≥ 1 000 uniform-RNG draws is a member of the six-survivor set. Plus a manual smoke press of Surprise Me ~20 times in the dev server shows only survivor picks.

**Note**: Because `randomSelections.ts:62` reads `ART_STYLE_OPTIONS`, completing US1's frontend GREEN (T019–T020) already removes the retired values from the pool — US2 is the **verification layer** that proves it and strengthens the deterministic guard. There is no separate randomiser source-of-truth to edit.

### Tests for User Story 2 (MANDATORY — must fail before verification) ⚠️

> The first sub-task is the new assertion that fails until US1's GREEN lands; the rest are guards.

- [X] T023 [P] [US2] In `frontend/src/features/alterego/lib/randomSelections.test.ts`, add a new test `it('never picks Pixel Art, Low-Poly 3D, or Line Art across ≥1000 uniform-RNG draws', ...)` that asserts the committed `artStyle` across 1 000 calls is always a member of `ART_STYLE_OPTIONS.map((o) => o.value)` AND is not `'pixel-art' | 'low-poly-3d' | 'line-art'` (SC-1902). Use a uniform RNG (`Math.random` is fine; or seed via the existing injectable `rng` param)
- [X] T024 [P] [US2] In `frontend/src/features/alterego/components/SurpriseMeButton.test.tsx`, update any fixture or scenario that asserted Surprise Me could commit `'pixel-art' | 'low-poly-3d' | 'line-art'` — swap to a surviving value (`'oil-painting'`). Verify that the test exercising "pressing Surprise Me commits an Art Style" still passes against the trimmed array

### Implementation for User Story 2

> No source change required — the randomiser already reads the single source of truth that US1 trimmed (research R1). This phase is verification-only.

- [X] T025 [US2] Run `cd frontend && npm test -- randomSelections.test.ts SurpriseMeButton.test.tsx` and confirm green; if the 1 000-draw test (T023) fails, root-cause: either US1's T019/T020 is incomplete or `randomSelections.ts` has been forked from `ART_STYLE_OPTIONS` — fix at the source
- [X] T026 [US2] Manual UI smoke check via `npm run dev`: press Surprise Me ≥ 20 times; the picked Art Style tile is always one of the six survivors

**Checkpoint**: User Stories 1 AND 2 both work end-to-end on the frontend. The frontend can ship in isolation behind a feature flag — but the **backend changes below are required for FR-1903 (stale-tab rejection)**, so do not ship the frontend without them.

---

## Phase 5: Cross-stack alignment — Backend Art Style enum (covers FR-1903, FR-1907, FR-1904)

**Goal**: Mirror the frontend trim on the backend. The Java `ArtStyle` enum becomes six members; the three prompt-builder label maps drop their three retired entries; `ArtStyle.fromWire` then rejects retired wire values automatically (research R3), satisfying FR-1903 with no new code.

**Independent Test**: Submit a Generate request with `"artStyle":"pixel-art"` → HTTP 400 RFC 7807 problem-detail. Submit the same request with `"artStyle":"oil-painting"` → HTTP 200 (or 200-with-fallback) as before. `EnumsTest` asserts `ArtStyle.values().length == 6` and surviving members keep their `wire()` / `label()` strings.

**Note**: This phase is not labelled `[US1]` or `[US2]` because it cuts across both — but it is mandatory for the feature to ship. Think of it as "infrastructure for both user stories on the wire-spec side".

### Tests for Phase 5 (MANDATORY — must fail before implementation) ⚠️

- [X] T027 [P] Flip cardinality assertion `ArtStyle.values().length` from 9 to 6 and verify the surviving members' `wire()` / `label()` strings in `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java`
- [X] T028 [P] In `backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoControllerInputValidationIT.java`, add a parameterised case `@ValueSource(strings = {"pixel-art","low-poly-3d","line-art"})` asserting that a request body carrying that value for `selections.artStyle` returns HTTP 400 with an RFC 7807 problem-detail body and that the response body does NOT contain any surviving Art Style value substituted in place of the rejected one (FR-1903, FR-1907)
- [X] T029 [P] Delete the three retired-value parameterised rows in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`, `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiCharacterPromptBuilderTest.java`, `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPromptBuilderTest.java` (and any sibling `@ParameterizedTest` over `ArtStyle.values()`)
- [X] T030 [P] Swap retired-value fixtures (`ArtStyle.PIXEL_ART | LOW_POLY_3D | LINE_ART`) for `ArtStyle.OIL_PAINTING` in every remaining backend test that uses them as a placeholder `Selections.artStyle`. The list is the ~28 files identified in research R6 (e.g. `RecordInvariantsTest`, `StubImageGeneratorTest`, `AlterEgoServiceTest`, `AlterEgoControllerContractTest`, `GenerateAlterEgoGeminiIT`, `GenerateAlterEgoFalAiIT`, `PosterFrameOverlayIT`, etc.). `./gradlew compileTestJava` after T031 will list any site missed
- [X] T031 Run `cd backend && ./gradlew test` and confirm: the tests in T027 / T028 / T029 RED; the swapped fixtures in T030 still compile but the production code is unchanged so any test that previously passed by exercising a retired value RED. Commit RED state

### Implementation for Phase 5

> GREEN step — trim the enum and the three label maps. `javac` errors at any test site that still references `ArtStyle.PIXEL_ART | LOW_POLY_3D | LINE_ART` are fixtures missed in T030 — fix each by swapping to `ArtStyle.OIL_PAINTING`.

- [X] T032 Remove the `PIXEL_ART`, `LOW_POLY_3D`, `LINE_ART` constants from `backend/src/main/java/com/aiavatar/alterego/model/ArtStyle.java` (lines 24–26 of the current file). Preserve `OIL_PAINTING`, `WATERCOLOR`, `POP_ART`, `RENAISSANCE_PORTRAIT`, `JAPANESE_WOODBLOCK`, `CEL_SHADED` verbatim including their `wire` and `label` strings (FR-1904)
- [X] T033 [P] Remove the three `ART_STYLE_LABELS.put(ArtStyle.PIXEL_ART, …)`, `ART_STYLE_LABELS.put(ArtStyle.LOW_POLY_3D, …)`, `ART_STYLE_LABELS.put(ArtStyle.LINE_ART, …)` lines (lines 55–59 of the current file) in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`
- [X] T034 [P] Remove the analogous three lines (lines 75–77 of the current file) in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiCharacterPromptBuilder.java`
- [X] T035 [P] Remove the analogous three lines (lines 60–64 of the current file) in `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java`
- [X] T036 Run `cd backend && ./gradlew compileTestJava` — fix any `cannot find symbol` diagnostic by swapping the missed fixture to `ArtStyle.OIL_PAINTING`. Then `./gradlew check` and confirm green (the validation IT in T028 now returns 400 via the existing `ArtStyle.fromWire` IllegalArgumentException path; research R3)

**Checkpoint**: Backend mirrors frontend. FR-1903 stale-tab rejection is verified by integration test. The full stack can ship.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Confirm constitution gates, run the quickstart, and update the agent context entry.

- [X] T037 [P] Run `cd frontend && npm test -- --coverage --run` and confirm ≥ 90 % line coverage holds (Constitution Principle III). The change is symmetric (removed production lines balance removed test lines), so the ratio should be preserved — if it dropped, audit which removed line lost coverage
- [X] T038 [P] Run `cd backend && ./gradlew check jacocoTestReport` and confirm ≥ 90 % line coverage holds across the backend modules touched by 019
- [X] T039 [P] Run the manual verification protocol in `specs/019-remove-art-styles/quickstart.md` §3 against the local dev server (FR-1901, FR-1902, FR-1903, FR-1904, FR-1906)
- [X] T040 Verify `CLAUDE.md`'s `## Active Technologies` and `## Recent Changes` carry a 019 entry consistent with the plan (the agent-context update script writes this; this task confirms the result and adjusts wording if needed)
- [ ] T041 Open the PR per `quickstart.md` §2f: `gh pr create --base main --title "feat(019): retire Pixel Art, Low-Poly 3D, Line Art (closes #49)" --body "<see spec.md>"`. Request explicit human approval (Constitution Principle V)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No upstream dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Setup; the contract-delta verification is a hard gate
- **User Story 1 (Phase 3)**: Depends on Foundational; delivers FR-1901 + FR-1904 (frontend)
- **User Story 2 (Phase 4)**: Depends on User Story 1 GREEN (T019–T021), because `randomSelections.ts` reads the same array — US2 is the verification layer over US1's source-of-truth trim
- **Phase 5 (cross-stack alignment)**: Depends on Foundational; can start in parallel with Phase 3 if a second pair of hands is available (different files, different test suite), but must complete before shipping because FR-1903 requires the backend trim
- **Polish (Phase 6)**: Depends on Phases 3, 4, and 5 all complete

### Within Each Story

- RED tests first (assertion / fixture edits); confirm they fail or that the compiler refuses
- GREEN by trimming the source-of-truth declarations (frontend type alias + options array; backend enum + label maps)
- Refactor only if compiler complains in unexpected places (it shouldn't — research R2/R4 audited the surface)

### Parallel Opportunities

- T002 and T003 (baseline smoke builds) run in parallel — different stacks
- T010–T018 (frontend RED edits) all touch different test files and can run in parallel
- T027–T030 (backend RED edits) all touch different test files and can run in parallel
- T033, T034, T035 (the three prompt-builder edits) all touch different files and can run in parallel
- Phase 3 (frontend) and Phase 5 (backend) can run in parallel if staffed by two people
- T037, T038, T039 (polish verification) all run independently

---

## Parallel Example: User Story 1 RED

```bash
# Launch all frontend RED-edit tasks in parallel — different files, no dependencies on each other:
Task: "T010 — flip cardinality + drop retired rows in frontend/src/features/alterego/components/ArtStyleGrid.test.tsx"
Task: "T011 — swap fixtures to 'oil-painting' in frontend/src/features/alterego/state/reducer.test.ts"
Task: "T012 — swap fixtures in frontend/src/features/alterego/state/selectors.test.ts"
Task: "T013 — swap fixtures in frontend/src/features/alterego/components/GenerateButton.test.tsx"
Task: "T014 — swap fixtures in frontend/src/features/alterego/components/SetupLayout.test.tsx"
Task: "T015 — swap fixtures in frontend/src/features/alterego/components/PrintArtefact.test.tsx"
Task: "T016 — swap fixtures in frontend/src/features/alterego/components/AlterEgoPanel.test.tsx"
Task: "T017 — swap fixtures in frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx"
Task: "T018 — swap fixtures in frontend/src/features/alterego/services/alterEgoClient.test.ts"
```

```bash
# Launch all backend RED-edit tasks in parallel:
Task: "T027 — flip ArtStyle.values().length from 9 to 6 in backend/.../unit/EnumsTest.java"
Task: "T028 — add stale-tab rejection IT case in backend/.../integration/AlterEgoControllerInputValidationIT.java"
Task: "T029 — drop retired rows from three PromptBuilder tests"
Task: "T030 — swap retired fixtures to ArtStyle.OIL_PAINTING across ~28 backend test files"
```

```bash
# Launch the three prompt-builder GREEN edits in parallel — different files:
Task: "T033 — remove three ART_STYLE_LABELS.put lines in GeminiPromptBuilder.java"
Task: "T034 — remove three ART_STYLE_LABELS.put lines in GeminiCharacterPromptBuilder.java"
Task: "T035 — remove three ART_STYLE_LABELS.put lines in FalAiPromptBuilder.java"
```

---

## Implementation Strategy

### MVP scope

User Stories 1 and 2 are both **P1 in the spec** and are not independently shippable in practice: the spec's FR-1907 forbids retired-value emissions on any surface, so shipping only the manual-grid trim would leave Surprise Me as a violating emitter. The MVP is **User Story 1 + User Story 2 together** on the frontend, **plus Phase 5 on the backend** to satisfy FR-1903's stale-tab rejection. There is no smaller shippable slice.

### Incremental delivery

1. Complete Phase 1 + Phase 2 (foundation: clean tree, contract delta verified)
2. Complete Phase 3 (US1 — frontend manual grid)
3. Complete Phase 4 (US2 — Surprise Me verification; no source change needed if US1 GREEN is correct)
4. Complete Phase 5 (backend mirror + stale-tab rejection)
5. Complete Phase 6 (polish: coverage + quickstart + PR)

### Parallel team strategy

With two developers:

1. Both complete Phases 1 + 2 together (~30 min)
2. Then:
   - Developer A: Phases 3 + 4 (frontend)
   - Developer B: Phase 5 (backend)
3. Merge converges at Phase 6; one of them runs the polish and opens the PR

---

## Notes

- The whole feature is subtractive. There is no new file to create except this `tasks.md` itself; the `019` contract delta was published during `/speckit.plan`.
- `[P]` tasks touch different files; tasks without `[P]` either depend on a previous task in the same phase or write to a shared source-of-truth file (e.g. T019 alone trims `types.ts`, T032 alone trims `ArtStyle.java`).
- Every fixture swap defaults to the **first surviving value** (`'oil-painting'` on the frontend, `ArtStyle.OIL_PAINTING` on the backend) because it is alphabetically first, broadly representative, and the original 006 declaration's first member — using it consistently avoids hidden test bias toward an under-exercised style.
- The 1 000-draw test in T023 is the literal embodiment of SC-1902. Tune the draw count down to ≥ 200 only if Vitest flakiness becomes an issue; the spec's measurable outcome is ≥ 1 000.
- Verify tests fail (RED) before each GREEN step. Constitution Principle III is non-negotiable.
- Commit after each task or logical group; the change set is mechanical but the audit trail matters for the PR review.
- Avoid: editing both `types.ts` and `options.ts` in the same commit without first committing the RED test edits — that collapses the RED → GREEN signal into a single diff and weakens the constitution-Principle-III paper trail.

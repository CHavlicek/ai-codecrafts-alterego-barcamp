---

description: "Task list for 026 — Wording Updates for User Roles"
---

# Tasks: 026 — Wording Updates for User Roles

**Input**: Design documents from `/specs/026-role-label-wording/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/public-http.md ✅, quickstart.md ✅

**Tests**: MANDATORY per Constitution Principle III (Test-First Development). Both user stories piggy-back on existing test files (`ArchetypeGrid.test.tsx`, `EnumsTest.java`) that already pin the *old* wording — flipping them to assert the *new* wording is the Red step. No new test file is added; coverage is structurally preserved at ≥ 90%.

**Organization**: Two P1 user stories from spec.md — independently testable and implementable in parallel because they live on opposite sides of the stack (frontend Setup grid vs. backend poster text).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

AI-Avatar is a web application (Java backend + React/TypeScript frontend) per the project constitution. Paths used below:

- **Backend (Java 21 + Spring Boot 3, Gradle Kotlin DSL)**
  - Production sources: `backend/src/main/java/com/aiavatar/alterego/domain/model/`
  - Unit tests: `backend/src/test/java/com/aiavatar/alterego/unit/`
- **Frontend (React 19 + Vite 8 + TypeScript strict)**
  - Component data: `frontend/src/features/alterego/options.ts`
  - Component tests (Vitest + RTL): colocated under `frontend/src/features/alterego/components/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the working tree is clean and the four old labels live where the plan said they did. No new tooling.

- [X] T001 Confirm pre-feature state: from repo root, run `grep -rn "Backend Dev\|Frontend Dev\|Platform Eng\.\|\"HR\"\|'HR'" frontend/src/features/alterego/options.ts backend/src/main/java/com/aiavatar/alterego/domain/model/Archetype.java frontend/src/features/alterego/components/ArchetypeGrid.test.tsx backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java` and confirm exactly the matches enumerated in `specs/026-role-label-wording/plan.md` "Source Code" section appear and no others.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None. This feature has no foundational/shared prerequisites — both user stories operate on pre-existing data tables that already ship and are already wired into their consumers.

**Checkpoint**: No-op. Proceed directly to Phase 3.

---

## Phase 3: User Story 1 — Setup-tab Role grid (Priority: P1) 🎯 MVP

**Goal**: The Setup-tab Role grid renders **Backend Developer** / **Frontend Developer** / **Platform Engineer** / **People Operations** for the four renamed Roles, with the other five Roles unchanged and the wire identifiers / icons / ordering byte-identical.

**Independent Test**: Run `npm test -- src/features/alterego/components/ArchetypeGrid.test.tsx` from `frontend/` — all assertions about rendered label text must pass with the new wording. Manually open the app at `http://localhost:5173`, advance to the Setup tab, and confirm the four cells visually match the new wording (quickstart.md User Story 1 walkthrough).

**Maps to spec FRs**: FR-2601, FR-2602, FR-2603, FR-2604, FR-2605, FR-2608, FR-2609, FR-2610.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> **NOTE: These tests already exist and currently assert the OLD wording. Flip them to assert the NEW wording FIRST. Run `npm test` and confirm they FAIL against the unmodified `options.ts`. THEN proceed to T003.**

- [X] T002 [US1] In `frontend/src/features/alterego/components/ArchetypeGrid.test.tsx`, update the expected-labels list (lines 17–22) so the seven labels in declaration order read: `'Cloud Architect'`, `'Backend Developer'`, `'Frontend Developer'`, `'AI Engineer'`, `'Platform Engineer'`, `'Data Engineer'`, `'People Operations'`, `'Administration'`, `'Customer Relations'` (nine labels total — confirm the test currently lists all nine; if it lists a subset, update only the names that overlap). Also update both `screen.getByRole('radio', { name: 'HR' })` call-sites (currently lines 53 and 93) to `screen.getByRole('radio', { name: 'People Operations' })`. Then run `npm test -- src/features/alterego/components/ArchetypeGrid.test.tsx` from `frontend/` and confirm the test FAILS — that is the Red step.

### Implementation for User Story 1

- [X] T003 [US1] In `frontend/src/features/alterego/options.ts`, update the four `label:` strings in the `archetypeOptions` array (currently at lines 63, 64, 66, 70):
  - `value: 'backend-dev'` → `label: 'Backend Developer'` (was `'Backend Dev'`)
  - `value: 'frontend-dev'` → `label: 'Frontend Developer'` (was `'Frontend Dev'`)
  - `value: 'platform-eng'` → `label: 'Platform Engineer'` (was `'Platform Eng.'` — drop trailing period)
  - `value: 'hr'` → `label: 'People Operations'` (was `'HR'`)

  Do **not** touch `value:`, `icon:`, array order, the surrounding `archetypeOptions` declaration, the `Option` type, or any of the other five `archetypeOptions` entries. Re-run `npm test -- src/features/alterego/components/ArchetypeGrid.test.tsx` and confirm GREEN.

- [X] T004 [US1] Run the full frontend Vitest suite from `frontend/`: `npm test`. Confirm every test passes (no regression in other components that consume `archetypeOptions`, e.g. `SurpriseMeButton` or `SetupLayout`).

**Checkpoint**: Setup grid renders the new wording on every render, automatically inheriting into any consumer that reads `archetypeOptions[].label` (including Surprise Me re-renders per spec FR-2609 and research R4).

---

## Phase 4: User Story 2 — Poster Role text (Priority: P1)

**Goal**: The Role text painted onto the generated alter-ego poster reads in the new wording whenever the guest's chosen Role is one of the four renamed prefab options. Free-form custom Roles continue to pass through verbatim.

**Independent Test**: Run `./gradlew unitTest --tests com.aiavatar.alterego.unit.EnumsTest` from `backend/` — `Archetype.HR.label() == "People Operations"` (and the three other renamed assertions) must pass. Manually exercise quickstart.md User Story 2: pick each renamed Role on Setup, generate, and confirm the poster's printed Role text matches the Setup grid character-for-character.

**Maps to spec FRs**: FR-2601, FR-2602, FR-2603, FR-2604, FR-2606, FR-2607, FR-2608, FR-2610.

**Independence from US1**: US1 and US2 touch disjoint files (frontend `options.ts` + frontend test ↔ backend `Archetype.java` + backend test). They share no source, no dependency, no test runner; either can ship without the other and both are independently demonstrable. The two together satisfy the "Setup and poster wording match" consistency the issue asks for.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

> **NOTE: `EnumsTest.java` currently asserts the OLD wording for HR (line 59) and BACKEND_DEV (line 93), and may also assert the old wording for FRONTEND_DEV and PLATFORM_ENG (verify by reading the full file first). Flip those assertions to the NEW wording. Run gradle and confirm RED.**

- [X] T005 [US2] In `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java`, update every `assertEquals(...)` that targets `Archetype.{HR,BACKEND_DEV,FRONTEND_DEV,PLATFORM_ENG}.label()` so the expected strings are:
  - `Archetype.BACKEND_DEV.label()` expects `"Backend Developer"` (was `"Backend Dev"`)
  - `Archetype.FRONTEND_DEV.label()` expects `"Frontend Developer"` (was `"Frontend Dev"`)
  - `Archetype.PLATFORM_ENG.label()` expects `"Platform Engineer"` (was `"Platform Eng."`)
  - `Archetype.HR.label()` expects `"People Operations"` (was `"HR"`)

  Leave the assertions for the five untouched Roles exactly as they are. Also update the inline comment at line 54 (currently states *"UI labels mirror the spec exactly ('HR' not 'Human Resources')"*) so it no longer claims the UI label is `'HR'`; rewrite it to read approximately *"UI labels mirror the new spec exactly (issue #62 / 026): 'People Operations' is the guest-facing label; the model-side grounding string in `GeminiPromptBuilder.ROLE_LABELS` remains 'Human Resources' by intent (see 026/research R2)."* Then run `./gradlew unitTest --tests com.aiavatar.alterego.unit.EnumsTest` from `backend/` and confirm the test FAILS — that is the Red step.

### Implementation for User Story 2

- [X] T006 [US2] In `backend/src/main/java/com/aiavatar/alterego/domain/model/Archetype.java`, update the second constructor argument on four enum constants (currently lines 19, 20, 22, 28):
  - `BACKEND_DEV("backend-dev", "Backend Developer")` (was `"Backend Dev"`)
  - `FRONTEND_DEV("frontend-dev", "Frontend Developer")` (was `"Frontend Dev"`)
  - `PLATFORM_ENG("platform-eng", "Platform Engineer")` (was `"Platform Eng."`)
  - `HR("hr", "People Operations")` (was `"HR"`)

  Do **not** touch the wire string (the first constructor argument), the enum constant identifiers (`BACKEND_DEV`, …), the enum declaration order, the `wire()` / `label()` / `fromWire()` methods, or any of the five untouched constants. Re-run `./gradlew unitTest --tests com.aiavatar.alterego.unit.EnumsTest` and confirm GREEN.

- [X] T007 [US2] Run the full backend test pyramid from `backend/`: `./gradlew test`. Confirm every tier passes — in particular:
  - `unitTest` — `EnumsTest` green; the other unit suites unaffected.
  - `serviceTest` — unaffected.
  - `contractTest` — green; the public HTTP contract is byte-identical (spec SC-2604). This is the canary against accidental wire mutation.
  - `integrationTest` — green; integration suites that grep for `"Backend Developer"` in the *prompt* body continue to find it (the prompt-side `ROLE_LABELS` is intentionally not touched — see research R2).
  - `archTest` — green; no new cross-layer dependency was introduced.

**Checkpoint**: `Archetype.label()` returns the new wording for the four renamed enums. `AlterEgoUseCase` → `StageContext.role` → `TextStage` paints it onto every poster whose Role isn't a free-form custom string.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Final cross-stack verification — both halves of the feature consistent with each other and with the public HTTP contract.

- [ ] T008 [P] Run the manual quickstart sweep documented in `specs/026-role-label-wording/quickstart.md` end-to-end against a freshly-started `npm run dev` (frontend) + `./gradlew bootRun` (backend): walk the Setup-grid check for all nine Roles (User Story 1 section), then the poster-text check for the four renamed Roles (User Story 2 section, ~5 min per Role per SC-2605), then the custom-Role edge case, the Surprise Me edge case, and the DevTools wire-payload check (confirms the request body still sends `"archetype": "backend-dev"` etc., not display strings — SC-2604).
- [X] T009 [P] Confirm no stray references to the old labels remain in production code: from repo root run `grep -rn "Backend Dev\|Frontend Dev\|Platform Eng\.\|'HR'\|\"HR\"" frontend/src backend/src/main --include="*.ts" --include="*.tsx" --include="*.java"`. Expected: zero matches. (Comment hits in `frontend/src/features/alterego/components/PosterView.tsx` and `backend/src/main/java/com/aiavatar/alterego/domain/policy/AccentResolver.java` are out-of-scope per research §"Out of scope"; if grep returns *those* you may leave them — but no other hit is acceptable.)
- [X] T010 Confirm no stray references to the old labels remain in test fixtures that are *expected* to track the UI labels: from repo root run `grep -rn "Backend Dev\|Frontend Dev\|Platform Eng\.\|'HR'\|\"HR\"" frontend/src/features/alterego/components/ArchetypeGrid.test.tsx backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java`. Expected: zero matches. (Fixture strings in `unit/application/pipeline/{TextStage,FrameStage,PosterPipeline}Test.java` that pass `"Backend Dev"` as an arbitrary `StageContext.role` input are out-of-scope per research §"Out of scope" — do **not** touch them.)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 has no dependency — can start immediately.
- **Foundational (Phase 2)**: No tasks — instantly satisfied.
- **User Story 1 (Phase 3)**: Depends on T001. Internally: T002 (red) → T003 (impl) → T004 (full suite).
- **User Story 2 (Phase 4)**: Depends on T001. Internally: T005 (red) → T006 (impl) → T007 (full suite). **Independent of US1** — can be done concurrently by a different developer or in parallel by the same one across two terminals.
- **Polish (Phase 5)**: Depends on T004 AND T007 (both stories green) since the manual quickstart needs both halves in place to demonstrate end-to-end consistency. T008/T009/T010 are mutually independent and parallelizable.

### User Story Dependencies

- **US1 (P1)**: No dependency on US2. Delivers the Setup-grid half on its own. Without US2 the poster still shows the old wording — that is an intermediate-merge state, not a permanent one; the feature only closes once both stories ship.
- **US2 (P1)**: No dependency on US1. Delivers the poster half on its own. Without US1 the Setup grid still shows the old wording.

The two stories share the same priority (P1) because the issue and the spec demand both surfaces; one without the other reads as half-done.

### Within Each User Story

- The Red test task MUST run before its sibling implementation task (Principle III).
- Within a story there is no second-tier parallelization — each story is two sequential file edits + one full-suite run.

### Parallel Opportunities

- **T002 ∥ T005**: Both red-test edits sit on different files, different test runners, different stacks. Run in parallel.
- **T003 ∥ T006**: Same — both implementation edits sit on different files, different runners. Run in parallel after their respective Red step has been observed.
- **T004 ∥ T007**: Both full-suite runs can fire concurrently (Vitest in `frontend/`, Gradle in `backend/`).
- **T008 ∥ T009 ∥ T010**: All three polish tasks are independent.

---

## Parallel Example: Both user stories in parallel

```bash
# Terminal A — User Story 1 (frontend)
cd frontend
# T002: edit ArchetypeGrid.test.tsx with new labels
npm test -- src/features/alterego/components/ArchetypeGrid.test.tsx   # confirm RED
# T003: edit options.ts with new labels
npm test -- src/features/alterego/components/ArchetypeGrid.test.tsx   # confirm GREEN
# T004:
npm test                                                              # full suite green

# Terminal B — User Story 2 (backend) — in parallel with Terminal A
cd backend
# T005: edit EnumsTest.java with new expected labels + updated comment
./gradlew unitTest --tests com.aiavatar.alterego.unit.EnumsTest        # confirm RED
# T006: edit Archetype.java with new constructor label arguments
./gradlew unitTest --tests com.aiavatar.alterego.unit.EnumsTest        # confirm GREEN
# T007:
./gradlew test                                                         # full pyramid green
```

---

## Implementation Strategy

### MVP First

Either user story qualifies as a partial MVP. If a single developer is squeezed for time, ship US1 first (frontend-only commit, no Gradle build, fastest validation loop) and US2 in a same-day follow-on. The feature only **closes the issue** once both ship and the Setup grid + poster show the same wording.

### Incremental Delivery

1. T001 — pre-feature inventory grep.
2. T002 + T003 + T004 — User Story 1: frontend Setup grid green (commit + push).
3. T005 + T006 + T007 — User Story 2: backend poster text green (commit + push).
4. T008 + T009 + T010 — manual quickstart sweep, grep guard, fixture-stability guard. (Open PR; request review.)
5. Merge to `main` only after explicit human PR approval (Principle V). Post-merge CI runs full test suite automatically.

### Parallel Team Strategy

With two developers:

1. T001 done by either developer.
2. Developer A: T002 → T003 → T004 (US1, frontend).
3. Developer B: T005 → T006 → T007 (US2, backend).
4. Both converge on T008 + T009 + T010 together.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story (US1 / US2) for traceability.
- Each user story is independently completable and testable.
- **Verify tests fail before implementing** (Principle III, non-negotiable). For both stories the red test reuses an existing test file — there is no "write a new test" beat; the beat is "flip the expected string and confirm it goes red."
- Commit after each user story (one focused commit per story; one final polish commit if T008/T009/T010 turn up follow-on work).
- Stop at any checkpoint to validate independently.
- Avoid: touching the `wire()` string, the `Archetype` enum identifiers, the option array order, the icon references, the `Option`/`Archetype` types, the provider-side `ROLE_LABELS` maps, the pipeline test fixtures, or any comment-only reference to "Backend Dev" outside the four files named in plan.md "Source Code".

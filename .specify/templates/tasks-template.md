---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Test tasks are MANDATORY for every feature per Principle III of the project constitution (Test-First Development, NON-NEGOTIABLE). They MUST be written before any implementation task in the same user story, MUST fail before production code is committed, and MUST include at least one end-to-end integration test per feature. Unit line coverage MUST reach ≥ 90%.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

AI-Avatar is a web application (Java backend + React/TypeScript frontend) as pinned by the project constitution. Paths shown below use this layout:

- **Backend (Java 21 + Spring Boot 3, Gradle Kotlin DSL)**
  - Production sources: `backend/src/main/java/com/aiavatar/<feature>/...`
  - Unit tests: `backend/src/test/java/com/aiavatar/<feature>/<Thing>Test.java`
  - Integration tests (`@SpringBootTest`): `backend/src/test/java/com/aiavatar/<feature>/<Journey>IT.java`
  - Resources / migrations: `backend/src/main/resources/`
- **Frontend (React 18+ with TypeScript strict, Vite)**
  - Components / pages: `frontend/src/components/<Feature>/<Component>.tsx`, `frontend/src/pages/<Page>.tsx`
  - Services (HTTP / state): `frontend/src/services/<feature>.ts`
  - Unit/component tests (Vitest + RTL): colocated `<Component>.test.tsx`
  - E2E journey tests (Playwright): `frontend/tests/e2e/<journey>.spec.ts`

Replace `com.aiavatar` / `<feature>` with the concrete package and feature slug chosen in plan.md.

<!-- 
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.
  
  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/
  
  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment
  
  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create `backend/` and `frontend/` project structure per implementation plan
- [ ] T002 Initialize backend (Gradle Kotlin DSL + Spring Boot 3.x on Java 21) in `backend/build.gradle.kts` and frontend (Vite + React 18 + TypeScript strict) in `frontend/package.json`
- [ ] T003 [P] Configure tooling: ESLint flat config + Prettier in `frontend/eslint.config.js` / `frontend/.prettierrc`; SonarQube Gradle plugin + Checkstyle in `backend/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T004 Setup database schema and migrations framework
- [ ] T005 [P] Implement authentication/authorization framework
- [ ] T006 [P] Setup API routing and middleware structure
- [ ] T007 Create base models/entities that all stories depend on
- [ ] T008 Configure error handling and logging infrastructure
- [ ] T009 Setup environment configuration management

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) 🎯 MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> **NOTE: Write these tests FIRST and ensure they FAIL before implementation.**

- [ ] T010 [P] [US1] Backend contract test for [endpoint] in `backend/src/test/java/com/aiavatar/<feature>/contract/[Endpoint]ContractTest.java` (JUnit 5 + `@SpringBootTest`)
- [ ] T011 [P] [US1] E2E journey test for [user journey] in `frontend/tests/e2e/[journey].spec.ts` (Playwright)

### Implementation for User Story 1

- [ ] T012 [P] [US1] Create [Entity1] JPA entity in `backend/src/main/java/com/aiavatar/<feature>/model/[Entity1].java`
- [ ] T013 [P] [US1] Create [Entity2] JPA entity in `backend/src/main/java/com/aiavatar/<feature>/model/[Entity2].java`
- [ ] T014 [US1] Implement [Service] in `backend/src/main/java/com/aiavatar/<feature>/service/[Service].java` (depends on T012, T013)
- [ ] T015 [US1] Implement [endpoint] controller in `backend/src/main/java/com/aiavatar/<feature>/controller/[Feature]Controller.java`
- [ ] T016 [US1] Wire frontend to [endpoint] via resilient client (Principle IV) in `frontend/src/services/[feature].ts` and render in `frontend/src/components/[Feature]/[Component].tsx`
- [ ] T017 [US1] Add validation (Bean Validation on backend DTO, form validation on frontend) and error handling

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [ ] T018 [P] [US2] Backend contract test for [endpoint] in `backend/src/test/java/com/aiavatar/<feature>/contract/[Endpoint]ContractTest.java`
- [ ] T019 [P] [US2] E2E journey test for [user journey] in `frontend/tests/e2e/[journey].spec.ts`

### Implementation for User Story 2

- [ ] T020 [P] [US2] Create [Entity] JPA entity in `backend/src/main/java/com/aiavatar/<feature>/model/[Entity].java`
- [ ] T021 [US2] Implement [Service] in `backend/src/main/java/com/aiavatar/<feature>/service/[Service].java`
- [ ] T022 [US2] Implement [endpoint] controller in `backend/src/main/java/com/aiavatar/<feature>/controller/[Feature]Controller.java` and frontend view in `frontend/src/components/[Feature]/[Component].tsx`
- [ ] T023 [US2] Integrate with User Story 1 components (if needed)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [ ] T024 [P] [US3] Backend contract test for [endpoint] in `backend/src/test/java/com/aiavatar/<feature>/contract/[Endpoint]ContractTest.java`
- [ ] T025 [P] [US3] E2E journey test for [user journey] in `frontend/tests/e2e/[journey].spec.ts`

### Implementation for User Story 3

- [ ] T026 [P] [US3] Create [Entity] JPA entity in `backend/src/main/java/com/aiavatar/<feature>/model/[Entity].java`
- [ ] T027 [US3] Implement [Service] in `backend/src/main/java/com/aiavatar/<feature>/service/[Service].java`
- [ ] T028 [US3] Implement [endpoint] controller in `backend/src/main/java/com/aiavatar/<feature>/controller/[Feature]Controller.java` and frontend view in `frontend/src/components/[Feature]/[Component].tsx`

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Documentation updates in docs/
- [ ] TXXX Code cleanup and refactoring
- [ ] TXXX Performance optimization across all stories
- [ ] TXXX [P] Additional backend unit tests (JUnit 5 + Mockito) in `backend/src/test/java/com/aiavatar/<feature>/` and frontend unit/component tests (Vitest + RTL) colocated as `*.test.ts`/`*.test.tsx` — reach ≥ 90% line coverage per Principle III
- [ ] TXXX Security hardening
- [ ] TXXX Run quickstart.md validation

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Principle III — non-negotiable)
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together (tests are mandatory — Principle III):
Task: "Backend contract test for [endpoint] in backend/src/test/java/com/aiavatar/<feature>/contract/[Endpoint]ContractTest.java"
Task: "E2E journey test for [user journey] in frontend/tests/e2e/[journey].spec.ts"

# Launch all entities for User Story 1 together:
Task: "Create [Entity1] JPA entity in backend/src/main/java/com/aiavatar/<feature>/model/[Entity1].java"
Task: "Create [Entity2] JPA entity in backend/src/main/java/com/aiavatar/<feature>/model/[Entity2].java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

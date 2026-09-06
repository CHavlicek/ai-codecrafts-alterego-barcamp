---

description: "Tasks: Input Validation for First Name (issue #30)"
---

# Tasks: Input Validation for First Name

**Input**: Design documents from `/specs/011-input-validation/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/post-alter-egos.md, quickstart.md

**Tests**: Test tasks are MANDATORY for every feature per Principle III of the project constitution (Test-First Development, NON-NEGOTIABLE). Each test task in this list is written FIRST, asserted to FAIL, then satisfied by the matching implementation task.

**Organization**: Tasks are grouped by user story so each story is independently implementable, testable, and deployable as an increment.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to a user story (US1, US2, US3) — only present in User Story phases
- All paths are absolute relative to the repo root.

## Path Conventions

- Backend: `backend/src/main/java/com/aiavatar/alterego/...` (production), `backend/src/test/java/com/aiavatar/alterego/...` (tests).
- Frontend: `frontend/src/features/alterego/...` (production), colocated `*.test.ts(x)` (Vitest + RTL).

---

## Phase 1: Setup

**Purpose**: This feature adds no new dependency, no new top-level directory, and no new tooling. The "setup" step is a single sanity check that the existing test commands run green on the current branch.

- [X] T001 Run `cd backend && ./gradlew test` and `cd frontend && npm test` on the `011-input-validation` branch and confirm both lanes are green before any change is made (baseline gate).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Centralise the canonical rule list as code-level documentation so US1 and US2 tests reference one source of truth.

**⚠️ CRITICAL**: No user-story work can begin until this phase is complete.

- [X] T002 [P] Add a `// CANONICAL RULES — Family A–E (length / ASCII controls / Unicode invisibles / structural / phrases). Keep in lock-step with FirstNameValidator.java on the backend.` block as a top-of-file comment in `frontend/src/features/alterego/validation/firstName.ts` (file is created in T010; for now the rule list lives only in `specs/011-input-validation/data-model.md` — this task creates the file with ONLY the comment block + an empty `validateFirstName` stub that throws so all callers fail until US1 lands).
- [X] T003 [P] Add the matching `// CANONICAL RULES — Family A–E …` comment block at the top of `backend/src/main/java/com/aiavatar/alterego/model/validation/FirstNameValidator.java` (created as a stub class implementing `ConstraintValidator<ValidFirstName, String>` whose `isValid` throws `UnsupportedOperationException` so all callers fail until US1 lands).
- [X] T004 [P] Create `backend/src/main/java/com/aiavatar/alterego/model/validation/ValidFirstName.java` — Jakarta Bean Validation annotation (`@Constraint(validatedBy = FirstNameValidator.class)`, `@Target(FIELD)`, `@Retention(RUNTIME)`) with default message `firstName.invalidChars` and `groups()`/`payload()`.
- [X] T005 [US-shared] Define a small shared **happy-path corpus** of 30 ordinary first names (mix of ASCII, accented Latin, CJK, hyphenated, apostrophed) committed identically in two test resource files: `frontend/src/features/alterego/validation/firstName.fixtures.ts` and `backend/src/test/resources/firstname-happy-path.txt` (one name per line). The frontend fixture re-exports the names as a string array; the backend test reads the .txt file at runtime. Same content, same order — so a one-sided change is visible in the diff.

**Checkpoint**: Foundation ready — both validators are wired into the pipeline (compiling but failing) and the happy-path corpus is shared.

---

## Phase 3: User Story 1 — Stop oversized + injection-shaped names from reaching the model (Priority: P1) 🎯 MVP

**Goal**: At both layers, reject any First Name longer than 50 NFC code points OR matching any rule in Families B–E. Frontend blocks submit with an inline error; backend returns 400 RFC 7807 without echoing the rejected value, and never calls the language-model provider on a rejected request.

**Independent Test**: Backend integration test posts each invalid kind, asserts 400 + no-echo + provider-not-called. Frontend unit + component tests assert the validator + form submit guard. Quickstart steps 1–4 pass manually.

> User Story 2 in the spec also asks for clear inline rejection of injection-shaped values; that work is fully covered by the same validator + the component error wiring delivered here. There is no separate phase for US2 — both stories are satisfied by US1's deliverables. (See "Story-to-Phase mapping" at the bottom of this file.)

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T006 [P] [US1] Backend unit test for the validator in `backend/src/test/java/com/aiavatar/alterego/unit/validation/FirstNameValidatorTest.java` covering: 30 happy-path names from `firstname-happy-path.txt`; length boundaries 49 (allow) / 50 (allow) / 51 (reject); each Family-B ASCII control char (sample: `\n`, `\r`, `\t`, `\0`, `\x01`, `\x1f`, `\x7f`); each Family-C invisible (`U+200B`, `U+200E`, `U+202E`, `U+2066`, `U+FEFF`); each Family-D structural char (`<`, `>`, `` ` ``, `{`, `[`, `\`, `|`, the substring `${`, three backticks); each Family-E phrase (case-insensitive whole-word) — and a "near miss" that must NOT match (`Yu Aren` does not trigger `you are now`).
- [X] T007 [P] [US1] Update `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoRequestValidationTest.java`: bump the existing `@Size` boundary expectations from 40→50 and add cases that assert `@ValidFirstName` violations are surfaced as Bean Validation errors.
- [X] T008 [US1] Backend integration test in `backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoControllerInputValidationTest.java` (`@SpringBootTest` + `MockMvc` + a mock provider): for each invalid family, POST a multipart request and assert (a) status `400`, (b) `Content-Type: application/problem+json`, (c) `errors[*].field` contains `firstName`, (d) the response body **does not** contain the rejected value as a substring (FR-1107), (e) the mock provider client was **not** called (`verify(provider, never()).generate(...)`), (f) FR-1110 sanity check — capture the application log via a `ListAppender` attached to the root logger during the test and assert the rejected value does not appear in any captured log message.
- [X] T009 [P] [US1] Frontend unit test for the validator in `frontend/src/features/alterego/validation/firstName.test.ts` (Vitest): same shape as T006 — happy-path corpus, length boundaries 49/50/51, every Family-B/C/D character, every Family-E phrase, the "Yu Aren" near-miss negative case.

### Implementation for User Story 1 — Backend

- [X] T010 [US1] Implement `FirstNameValidator.isValid(String, ConstraintValidatorContext)` in `backend/src/main/java/com/aiavatar/alterego/model/validation/FirstNameValidator.java` per data-model Families A–E. Trim → NFC normalise → reject by family in order (length, ASCII controls, Unicode invisibles, structural markers, instruction phrases). On rejection, call `ctx.disableDefaultConstraintViolation(); ctx.buildConstraintViolationWithTemplate("{firstName.tooLong}" | "{firstName.invalidChars}" | "{firstName.looksLikeInstructions}").addConstraintViolation();` so the error code surfaces in the `errors[].code` array.
- [X] T011 [US1] Modify `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`: change `@Size(min=1, max=40)` to `@Size(min=1, max=50)` on `firstName`, and add `@ValidFirstName` on the same field. Keep `@NotBlank`. Update the record's javadoc / inline comment to point to `specs/011-input-validation/data-model.md`.
- [X] T012 [US1] Verify `backend/src/main/java/com/aiavatar/alterego/config/ProblemDetailAdvice.java` does not echo the offending value for `firstName` violations. If the advice currently includes `rejectedValue` for any field, scope it to NOT include the value when the field is `firstName` OR when the violation code starts with `firstName.`. Add a unit test for the advice covering this redaction in `backend/src/test/java/com/aiavatar/alterego/unit/config/ProblemDetailAdviceFirstNameRedactionTest.java`.
- [X] T013 [US1] Run `cd backend && ./gradlew test` — all tests T006/T007/T008/T012 must now pass.

### Implementation for User Story 1 — Frontend

- [X] T014 [US1] Implement `validateFirstName(value: string): { ok: true } | { ok: false, code: 'empty' | 'too_long' | 'invalid_chars' | 'looks_like_instructions' }` in `frontend/src/features/alterego/validation/firstName.ts` per data-model Families A–E. Pure function: trim → NFC normalise → check length via `Array.from(s).length` (code points) → check Families B/C/D via a single precompiled `RegExp` per family → check Family E via case-insensitive whole-word regex bounded by `[^\\p{L}]` (Unicode property escape).
- [X] T015 [US1] Update `frontend/src/features/alterego/components/FirstNameInput.tsx`: import `validateFirstName`; compute `validation = validateFirstName(value)` on every render; render an inline error message (one of the four messages from data-model "Error taxonomy") inside the existing hint slot, gated on `touched && !validation.ok`; wire `aria-describedby` and `aria-invalid` to that error node; replace the existing 40-char soft hint logic with the new validator-driven message (the 40-char hint is obsoleted by the 50-char hard rule).
- [X] T016 [US1] Update `frontend/src/features/alterego/components/FirstNameInput.test.tsx`: replace the 40-char overflow case with cases asserting the four validator codes render the four expected messages; assert `aria-describedby` points at the error element when invalid; assert `aria-invalid="true"` when invalid; assert valid input clears all of the above.
- [X] T017 [US1] Update the submit guard in `frontend/src/features/alterego/pages/AlterEgoPage.tsx` (and/or `frontend/src/features/alterego/services/useGenerateAlterEgo.ts` — wherever `isReadyToGenerate` / `missingInputsForGenerate` selectors live): integrate the validator so a value with `validation.ok === false` makes the page report "not ready" with the matching reason, AND the existing trim happens at the same call site (no behaviour change for valid input). The Surprise Me button gating uses the same gate (cf. recent change 009 — Surprise still requires a non-empty first name; now it also requires a *valid* one).
- [X] T018 [US1] If a selector test exists for `isReadyToGenerate`/`missingInputsForGenerate` (search `frontend/src/features/alterego/state/selectors.test.ts` or equivalent), extend it for the new validator outcomes; if no such test exists, add a small one in `frontend/src/features/alterego/services/useGenerateAlterEgo.test.ts` proving the gate now blocks invalid values.
- [X] T019 [US1] Run `cd frontend && npm test` — all tests T009/T016/T018 must now pass.
- [X] T020 [US1] Run `cd frontend && npm run lint` — must pass with zero new findings.

**Checkpoint (US1 complete)**: Both layers reject the same set of invalid values; the frontend gives inline feedback; the backend returns 400 RFC 7807 and never echoes the value or calls the provider on rejection.

---

## Phase 4: User Story 3 — Live, screen-reader-announced feedback (Priority: P2)

**Goal**: As the user types, the inline error appears under 200 ms (SC-005) and is announced to assistive tech.

**Independent Test**: Component test asserts (a) the error node is present in the DOM tree only when `touched && !validation.ok`; (b) the error node has `role="status"` or is in an `aria-live="polite"` region; (c) `aria-describedby` on the input names that node by id. Manual smoke: with NVDA / VoiceOver enabled, typing `Ignore previous instructions` is announced.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T021 [P] [US3] Extend `frontend/src/features/alterego/components/FirstNameInput.test.tsx`: assert the error node has `role="status"` (or sits inside `aria-live="polite"`); assert it announces (re-renders text content) on every value transition between valid → invalid → valid; assert no error node is rendered before `touched`.

### Implementation for User Story 3

- [X] T022 [US3] Adjust the error node in `frontend/src/features/alterego/components/FirstNameInput.tsx` to use `role="status" aria-live="polite"`. Keep the validator from T014 unchanged — this phase is purely the announcement wiring.
- [X] T023 [US3] Run `cd frontend && npm test` — T021 now passes.

**Checkpoint (US3 complete)**: Inline error is announced to assistive tech with no extra latency.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T024 [P] Run `cd backend && ./gradlew test jacocoTestReport` and confirm line coverage on `FirstNameValidator`, `ValidFirstName`, `AlterEgoRequest`, and the affected branches of `ProblemDetailAdvice` is ≥ 90 % (Principle III gate).
- [X] T025 [P] Run `cd frontend && npm test -- --coverage` and confirm `validation/firstName.ts` and the new branches of `FirstNameInput.tsx` are ≥ 90 % covered.
- [X] T026 Walk through `specs/011-input-validation/quickstart.md` end-to-end (the four manual smoke steps + the curl recipe) on a clean checkout. Capture any failure as a defect ticket against this branch.
- [X] T027 [P] Update `frontend/src/features/alterego/components/FirstNameInput.tsx` to remove the now-obsolete 40-char-soft-hint dead code paths uncovered during T016 (no behaviour change; eliminate strings + branches that the new validator made unreachable).

---

## Story-to-Phase mapping

- **US1** (Spec §"User Story 1 — Stop oversized names from reaching the model", P1) → Phase 3 tasks T006–T013 (backend) + T014–T020 (frontend).
- **US2** (Spec §"User Story 2 — Stop prompt-injection-shaped names from reaching the model", P1) → fully delivered by the same validator + form-guard work in Phase 3 (no separate code path; the rules from Family B–E enforce it). Acceptance scenarios from US2 are covered by tests T006, T008, T009, T016, T018.
- **US3** (Spec §"User Story 3 — Clear, immediate feedback while typing", P2) → Phase 4 tasks T021–T023.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)** → independent.
- **Phase 2 (Foundational)** → depends on Phase 1.
- **Phase 3 (US1)** → depends on Phase 2; Phase 3 backend work (T006–T013) and Phase 3 frontend work (T014–T020) can run in parallel by two developers because they touch disjoint trees and share only the rule-list comment block (committed in Phase 2).
- **Phase 4 (US3)** → depends on Phase 3 frontend (T015 must exist for T022 to amend).
- **Phase 5 (Polish)** → depends on Phases 3 and 4.

### Within each phase

- Tests are written FIRST and asserted to FAIL (Principle III). Implementation tasks then turn them green.
- T011 (modify `AlterEgoRequest`) must follow T004 (annotation exists) and T010 (validator implemented) — otherwise the controller will refuse to start.

### Parallel opportunities

- T002, T003, T004 are `[P]` — three different files.
- T006 and T009 are `[P]` — different test trees.
- T021 amends a frontend test file in Phase 4 and is `[P]` against any other Phase 5 polish.
- Two developers can split Phase 3 backend ↔ frontend cleanly.

---

## Implementation Strategy

### MVP (US1)

1. T001 (baseline green) → T002–T005 (foundational) → T006–T020 (US1 tests + impl).
2. STOP, validate quickstart.md steps 1–4. This is the MVP — issue #30 is satisfied at this point.

### Increment 1 (US3 — accessibility polish)

3. T021–T023 — add the screen-reader announcement wiring.

### Polish

4. T024–T027 — coverage, quickstart walkthrough, dead-code cleanup.

---

## Notes

- No new runtime dependency. No persistence. No new top-level directory.
- Per Principle III, every test task here must be committed in the FAIL state before its matching implementation task is started.
- `ProblemDetailAdvice` may already redact rejected values; T012 verifies and otherwise tightens. Treat this as a guard, not a refactor.

---
description: "Task list for feature 022-role-options-custom — new Role-category prefab options + free-form custom-role input"
---

# Tasks: New Options for the Role Category

**Input**: Design documents from `/Users/dmytrokorniienko/aiavatar/specs/022-role-options-custom/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/alter-egos.openapi.delta.yaml, quickstart.md

**Tests**: MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). Every test task MUST be written and FAIL before the implementation task that satisfies it is committed. Unit line coverage MUST reach ≥ 90% on every touched module; an end-to-end Playwright spec is mandatory.

**Organization**: Tasks are grouped by user story (US1 — three new prefab options; US2 — custom-role input + precedence; US3 — Surprise Me clears custom). US1 and US2 are both P1 in the spec but **independently shippable** — US1 is a pure enum-additive change with no UI structure change; US2 carries the bigger reducer + DTO surface. US3 is a P2 polish that depends on US2's reducer changes.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Different file, no dependency on an incomplete task — safe to run in parallel.
- **[Story]**: Maps task to user story (US1 / US2 / US3) for traceability.
- File paths are absolute or repo-root-relative. Existing files marked "extend"; new files marked "create".

## Path Conventions

- Backend: `backend/src/main/java/com/aiavatar/alterego/...` (production), `backend/src/test/java/com/aiavatar/alterego/...` (tests, with `unit/` and `integration/` sub-trees mirrored from existing structure).
- Frontend: `frontend/src/features/alterego/...` (production), colocated `*.test.tsx`/`*.test.ts` (unit/component), `frontend/tests/e2e/*.spec.ts` (Playwright).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm baseline on `022-role-options-custom` branch is green before any code change.

- [X] T001 Verify pre-feature baseline: run `./gradlew test` (backend) and `npm test` (frontend, from `frontend/`) on branch `022-role-options-custom` with **no source changes**. All tests must pass; record any pre-existing flake. No file edits.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None. US1 (three new prefab values) and US2 (custom-role input) have **no shared blocking prerequisite** — US1 is a pure enum-additive delta and US2 introduces independent surface (new field, new validator, new UI component). Both stories can be staffed in parallel after Phase 1.

> Intentionally empty. Proceed to Phase 3.

**Checkpoint**: Foundation ready — user-story implementation can now begin.

---

## Phase 3: User Story 1 — Three new prefab Role options (Priority: P1) 🎯 MVP

**Goal**: Add HR, Administration, Customer Relations as three additional prefab options end-to-end (frontend type/option list, backend enum, prompt builders, Surprise Me sampling pool). Existing six engineering roles unchanged in wire value, label, and relative order.

**Independent Test**: Pick *HR* / *Administration* / *Customer Relations* in turn on the Setup tab, Generate, verify (a) the poster's text overlay shows the chosen label, (b) the generated image evokes the role via visual cues (no rendered text inside the image), and (c) Surprise Me draws uniformly from nine options (over a sample of ≥ 90 rolls, each value appears within ±25% of `n/9`).

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T002 [P] [US1] Extend `unit/EnumsTest.java` with three new round-trip cases for `Archetype.fromWire("hr") == HR`, `…("administration") == ADMINISTRATION`, `…("customer-relations") == CUSTOMER_RELATIONS`; assert `.wire()` and `.label()` for each — `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java`
- [X] T003 [P] [US1] Extend `unit/gemini/GeminiPromptBuilderTest.java` with three new prompt-line cases that pin the prefab labels expanded for the model: `HR → "Human Resources"`, `ADMINISTRATION → "Administration / Operations"`, `CUSTOMER_RELATIONS → "Customer Relations / Support"`. Assert the line appears in BOTH single and group prompt variants — `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`
- [X] T004 [P] [US1] Extend `unit/falai/FalAiPromptBuilderTest.java` analogously to T003 — `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPromptBuilderTest.java`
- [X] T005 [P] [US1] Extend `unit/gemini/GeminiCharacterPromptBuilderTest.java` to pin the bio-prompt role-label substitution for each of the three new values — `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiCharacterPromptBuilderTest.java`
- [X] T006 [P] [US1] Extend `unit/FallbackPosterProviderTest.java` to cover the three new archetype values flowing through the fallback poster (accent-key happy path) — `backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java`
- [X] T007 [P] [US1] Extend `ArchetypeGrid.test.tsx` to assert the nine pill order and labels (`'Cloud Architect'..'Customer Relations'`); assert keyboard arrow navigation reaches the new pills — `frontend/src/features/alterego/components/ArchetypeGrid.test.tsx`
- [X] T008 [P] [US1] Create `frontend/src/features/alterego/lib/randomSelections.test.ts` (or extend if present) with a seeded-RNG test that draws from `ARCHETYPE_OPTIONS` and asserts each of the nine wire values appears across a fixed-seed sample; pin the order — `frontend/src/features/alterego/lib/randomSelections.test.ts`

### Implementation for User Story 1

- [X] T009 [P] [US1] Extend `Archetype` Java enum with `HR("hr","HR")`, `ADMINISTRATION("administration","Administration")`, `CUSTOMER_RELATIONS("customer-relations","Customer Relations")` after `DATA_ENGINEER`. Preserve existing six entries — `backend/src/main/java/com/aiavatar/alterego/model/Archetype.java`
- [X] T010 [P] [US1] Extend `GeminiPromptBuilder.ROLE_LABELS` static initialiser with three entries: `HR → "Human Resources"`, `ADMINISTRATION → "Administration / Operations"`, `CUSTOMER_RELATIONS → "Customer Relations / Support"` — `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`
- [X] T011 [P] [US1] Extend `FalAiPromptBuilder.ROLE_LABELS` analogously to T010 — `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java`
- [X] T012 [P] [US1] Extend `GeminiCharacterPromptBuilder.ROLE_LABELS` analogously to T010 (keeps the image prompt + bio prompt label vocabulary in sync) — `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiCharacterPromptBuilder.java`
- [X] T013 [P] [US1] Verify `AccentResolver.hashFallback` modulo logic remains correct for the 9-element enum (no new curated entries required — hash-fallback covers the new values). Add a one-line Javadoc note acknowledging the 9-value pool — `backend/src/main/java/com/aiavatar/alterego/service/AccentResolver.java`
- [X] T014 [P] [US1] Extend the `Archetype` TypeScript union with `'hr' | 'administration' | 'customer-relations'` — `frontend/src/features/alterego/types.ts`
- [X] T015 [US1] Extend `ARCHETYPE_OPTIONS` with three new entries (imports: `ClipboardList`, `MessageCircle`, `Users` from `lucide-react`): `{ value: 'hr', label: 'HR', icon: Users }`, `{ value: 'administration', label: 'Administration', icon: ClipboardList }`, `{ value: 'customer-relations', label: 'Customer Relations', icon: MessageCircle }`. Preserve existing six entries and their order. Depends on T014 — `frontend/src/features/alterego/options.ts`

**Checkpoint**: User Story 1 is fully functional. Pick HR / Administration / Customer Relations → Generate → poster + image work end-to-end. Surprise Me draws from nine. Ready to demo / merge as MVP.

---

## Phase 4: User Story 2 — Custom-role text input (Priority: P1)

**Goal**: A single-line free-form text input under the Role grid (≤ 100 chars after trim). When the input's trimmed value is non-empty: (a) it becomes the role-of-record for image prompt, bio prompt, and text overlay; (b) any prior prefab selection is silently cleared in the reducer; (c) the prefab grid renders blurred, keyboard-inert, and click-inert; (d) a trailing `X` button clears the input with no confirmation.

**Independent Test**: Type `Tester` (or any non-blank string ≤ 100 chars) into the custom-role input with no prefab selected. Verify (a) prefab pills blur and become un-clickable, (b) Generate is enabled, (c) poster's text overlay reads `Tester`, (d) generated image evokes the typed role. Press `X`, verify input clears and prefab pills become clickable again with **no confirmation** and no prefab restored.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T016 [P] [US2] Create `unit/validation/RoleOfRecordPresentValidatorTest.java` covering the truth table from data-model.md (archetype set / customRole non-blank / both / neither) — `backend/src/test/java/com/aiavatar/alterego/unit/validation/RoleOfRecordPresentValidatorTest.java`
- [X] T017 [P] [US2] Extend `unit/AlterEgoUserSelectionsValidationTest.java` with cases asserting the class-level `@RoleOfRecordPresent` constraint: archetype=null + customRole="Tester" → valid; archetype=null + customRole="   " → invalid; archetype=HR + customRole=null → valid; customRole length > 100 → invalid. Reject violations with the expected ConstraintViolation message — `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoUserSelectionsValidationTest.java`
- [X] T018 [P] [US2] Extend `unit/AlterEgoRequestValidationTest.java` analogously for the server-internal `AlterEgoRequest` shape (also adds the `pose` + `vibe` fields) — `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoRequestValidationTest.java`
- [X] T019 [P] [US2] Extend `unit/RecordInvariantsTest.java` with a test for `AlterEgoRequest.roleLabel()` against the data-model.md truth table — `backend/src/test/java/com/aiavatar/alterego/unit/RecordInvariantsTest.java`
- [X] T020 [P] [US2] Extend `unit/gemini/GeminiPromptBuilderTest.java` with a custom-role substitution case: archetype=null + customRole="Tester" → prompt line carries `"Tester"` verbatim (single + group). Assert the no-rendered-text composition note remains byte-identical — `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`
- [X] T021 [P] [US2] Extend `unit/falai/FalAiPromptBuilderTest.java` analogously to T020 — `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPromptBuilderTest.java`
- [X] T022 [P] [US2] Extend `unit/gemini/GeminiCharacterPromptBuilderTest.java` to assert the bio prompt also consumes `roleLabel()` (custom case + prefab case) — `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiCharacterPromptBuilderTest.java`
- [X] T023 [P] [US2] Extend `unit/AlterEgoServiceTest.java` with two cases: (a) custom-role request → text-overlay receives the trimmed custom string in the role-label slot; (b) prefab-only request → text-overlay still receives `archetype.label()` (no regression) — `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoServiceTest.java`
- [X] T024 [P] [US2] Extend `unit/FallbackPosterProviderTest.java` to assert the fallback path renders the role-of-record (custom string trimmed) when archetype is null, and defaults the accent key to `Archetype.BACKEND_DEV` per R4 — `backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java`
- [X] T025 [P] [US2] Extend `contract/AlterEgoControllerContractTest.java` with two new happy-path contract cases: (a) customRole only (archetype omitted) → 200; (b) customRole=null + archetype=HR → 200 — `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java`
- [X] T026 [P] [US2] Extend `contract/AlterEgoControllerErrorContractTest.java` with two new 400 cases: (a) both archetype and customRole absent; (b) customRole present but trimmed blank, archetype absent. Assert RFC 7807 body shape with the expected detail — `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerErrorContractTest.java`
- [X] T027 [P] [US2] Extend `integration/AlterEgoControllerInputValidationIT.java` with a customRole-length-101 + customRole-only-happy-path round trip — `backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoControllerInputValidationIT.java`
- [X] T028 [P] [US2] Extend `integration/GenerateAlterEgoTextOverlayIT.java` to assert the rendered text overlay shows the trimmed custom string when customRole is set — `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoTextOverlayIT.java`
- [X] T029 [P] [US2] Create `CustomRoleInput.test.tsx` covering: render with `value=""` hides the X button; typing fires `onChange` with raw value; setting `value="Tester"` shows the X; clicking X fires `onChange('')`; pressing Space / Enter while X is focused fires `onChange('')`; `maxLength={100}` enforced; aria-label `"Custom role"` present — `frontend/src/features/alterego/components/CustomRoleInput.test.tsx`
- [X] T030 [P] [US2] Extend `state/reducer.test.ts`: `CustomRoleChanged` with non-blank trimmed payload sets `customRole` AND clears `archetype` to null AND sets phase to `'picking'`; with blank/whitespace payload leaves `archetype` untouched. `SurpriseMePicked` clears `customRole` to `''`. `StartOverRequested` resets `customRole` to `''`. `ArchetypeSelected` does NOT clear `customRole` (defense-in-depth note) — `frontend/src/features/alterego/state/reducer.test.ts`
- [X] T031 [P] [US2] Extend `state/selectors.test.ts`: `missingInputs` returns `archetype` in the missing list iff BOTH `state.archetype === null` AND `customRoleOfRecord(state) === ''`. `isReadyToGenerate` is true when only customRole satisfies the role gate. `customRoleOfRecord` trims whitespace, returns `''` for whitespace-only — `frontend/src/features/alterego/state/selectors.test.ts`
- [X] T032 [P] [US2] Extend `ArchetypeGrid.test.tsx` with a `disabled` prop matrix: when `disabled`, the wrapping div carries `aria-disabled="true"`; every option button is `aria-disabled="true"` and `tabIndex={-1}`; clicking an option does NOT fire `onChange`; arrow keys do NOT fire `onChange`; the `is-disabled` CSS class is present — `frontend/src/features/alterego/components/ArchetypeGrid.test.tsx`
- [X] T033 [P] [US2] Extend `SetupLayout.test.tsx`: renders `<CustomRoleInput/>` immediately under `<ArchetypeGrid/>` inside the step-1 numbered group; `ArchetypeGrid` receives `disabled={true}` when the session's customRole trims to non-empty, `disabled={false}` otherwise; CustomRoleInput's `disabled` is wired to `isSubmitting` — `frontend/src/features/alterego/components/SetupLayout.test.tsx`
- [X] T034 [P] [US2] Extend `services/alterEgoClient.test.ts`: when `selections.customRole.trim()` is non-empty the JSON body's `customRole` is the trimmed value AND `archetype` is omitted; when blank the JSON omits `customRole` entirely; correlation-id header still threaded — `frontend/src/features/alterego/services/alterEgoClient.test.ts`
- [X] T035 [P] [US2] Create `frontend/tests/e2e/role-custom.e2e.ts` (Playwright): happy-path E2E exercising typing → prefab blur → X clear → re-enable → pick prefab → Generate. Assert poster text overlay receives the role-of-record — `frontend/tests/e2e/role-custom.e2e.ts`

### Implementation for User Story 2

- [X] T036 [P] [US2] Create `RoleOfRecordPresent` annotation: `@Target({TYPE}) @Retention(RUNTIME) @Constraint(validatedBy = RoleOfRecordPresentValidator.class)`; default message `"either archetype must be set or customRole must be non-blank"` — `backend/src/main/java/com/aiavatar/alterego/model/validation/RoleOfRecordPresent.java`
- [X] T037 [P] [US2] Create `RoleOfRecordPresentValidator implements ConstraintValidator<RoleOfRecordPresent, Object>`: pattern-match on `AlterEgoUserSelections` / `AlterEgoRequest`; returns true when archetype != null OR (customRole != null && !customRole.isBlank()) — `backend/src/main/java/com/aiavatar/alterego/model/validation/RoleOfRecordPresentValidator.java`
- [X] T038 [US2] Extend `AlterEgoUserSelections`: drop `@NotNull` from `archetype`; add `@Size(max = 100) String customRole`; add class-level `@RoleOfRecordPresent`. Update Javadoc to note the 022 delta. Depends on T036/T037 — `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoUserSelections.java`
- [X] T039 [US2] Extend `AlterEgoRequest`: drop `@NotNull` from `archetype`; add `String customRole` field (with `@Size(max = 100)`); update `withTrimmedFirstName()` to thread customRole through the new record constructor; add class-level `@RoleOfRecordPresent`; add `roleLabel()` helper per data-model.md (customRole.trim() if non-blank, else archetype.label(), else defensive "Engineer"). Depends on T036/T037 — `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`
- [X] T040 [US2] Update `AlterEgoService.generate(AlterEgoUserSelections, …)`: forward `userSelections.customRole()` into the resolved `AlterEgoRequest` constructor (now 8 args). Update the two text-overlay call sites at the FallbackPosterProvider path and the happy path to use `request.roleLabel()` instead of `request.archetype().label()`. Update the `fallbackProvider.poster(archetype, universe)` call site to handle null archetype (delegate the default to the provider). Depends on T039 — `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java`
- [X] T041 [P] [US2] Update `GeminiPromptBuilder.appendCategoryLines` to read `request.roleLabel()` instead of `label(ROLE_LABELS, request.archetype())`. Keep the "render as visual cues — NOT as text" framing byte-identical. Update Javadoc to note the 022 delta — `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`
- [X] T042 [P] [US2] Update `FalAiPromptBuilder` to read `request.roleLabel()` analogously — `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java`
- [X] T043 [P] [US2] Update `GeminiCharacterPromptBuilder` to read `request.roleLabel()` (so the bio prompt also picks up custom roles) — `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiCharacterPromptBuilder.java`
- [X] T044 [P] [US2] Update `FallbackPosterProvider.poster(Archetype, Universe)` signature to accept a nullable archetype; default to `Archetype.BACKEND_DEV` for accent-key purposes when null (per R4). Document the choice in the method's Javadoc — `backend/src/main/java/com/aiavatar/alterego/service/fallback/FallbackPosterProvider.java`
- [X] T045 [P] [US2] Update `AccentResolver.deriveAccent(Archetype, Universe)` to accept a nullable archetype with the same `BACKEND_DEV` defaulting (keeps the lookup centralised); add a Javadoc note — `backend/src/main/java/com/aiavatar/alterego/service/AccentResolver.java`
- [X] T046 [P] [US2] Extend the `Selections` TS interface: change `archetype: Archetype` → `archetype: Archetype | null`; add `customRole?: string` (optional). Update Javadoc — `frontend/src/features/alterego/types.ts`
- [X] T047 [US2] Extend reducer in `state/reducer.ts`: add `customRole: string` to `AlterEgoSession`; add to `initialAlterEgoSession()` as `''`; add `CustomRoleChanged` action type; reducer branch sets `customRole`, clears `archetype` when trimmed payload non-blank, sets phase `'picking'`; `SurpriseMePicked` additionally sets `customRole: ''`. Update Javadoc with the 022 delta. Depends on T046 — `frontend/src/features/alterego/state/reducer.ts`
- [X] T048 [P] [US2] Extend `state/selectors.ts`: add exported `customRoleOfRecord(state)` helper (returns trimmed string or `''`); update `missingInputs` to satisfy the archetype gate via EITHER `state.archetype` OR non-empty trimmed customRole. Update Javadoc — `frontend/src/features/alterego/state/selectors.ts`
- [X] T049 [P] [US2] Extend `SelectionGrid`: add `disabled?: boolean` prop. When true: short-circuit `handleClick` and `handleKeyDown`; set every option button's `tabIndex={-1}` and `aria-disabled="true"`; set wrapper `aria-disabled="true"`; add `is-disabled` class to the fieldset for CSS targeting — `frontend/src/features/alterego/components/SelectionGrid.tsx`
- [X] T050 [P] [US2] Extend `ArchetypeGrid`: accept and forward `disabled?: boolean` to `SelectionGrid`; rename the visible label from `"Engineer role"` to `"Role"` (the three new options are not engineering — see spec FR-2201) — `frontend/src/features/alterego/components/ArchetypeGrid.tsx`
- [X] T051 [P] [US2] Create `CustomRoleInput.tsx`: controlled `<input type="text" maxLength={100} aria-label="Custom role">` + trailing `<button aria-label="Clear custom role">` rendering the imported `X` icon, visible only when `value !== ''`. Props: `{ value, onChange, disabled?, maxLength? = 100 }` — `frontend/src/features/alterego/components/CustomRoleInput.tsx`
- [X] T052 [US2] Extend `SetupLayout.tsx`: import `CustomRoleInput`; compute `const customRoleActive = session.customRole.trim().length > 0`; pass `disabled={customRoleActive}` to `<ArchetypeGrid />`; render `<CustomRoleInput value={session.customRole} onChange={(customRole) => dispatch({ type: 'CustomRoleChanged', customRole })} disabled={isSubmitting} />` immediately below `<ArchetypeGrid />` inside the step-1 numbered group. Depends on T047 / T049 / T050 / T051 — `frontend/src/features/alterego/components/SetupLayout.tsx`
- [X] T053 [P] [US2] Extend `services/alterEgoClient.ts → buildMultipartBody`: emit `customRole: selections.customRole.trim()` into the JSON only when the trimmed value is non-empty; otherwise omit the key entirely. When customRole is included AND archetype is null, omit the `archetype` key as well — `frontend/src/features/alterego/services/alterEgoClient.ts`
- [X] T054 [P] [US2] Extend `fallback/fallbackPoster.ts`: read the role-of-record (`selections.customRole?.trim() || ARCHETYPE_LABEL[selections.archetype]`) instead of `archetype.label`. Handle null archetype gracefully (use the custom string if present, else a stable default like `'Engineer'` matching the backend's defensive label) — `frontend/src/features/alterego/fallback/fallbackPoster.ts`
- [X] T055 [P] [US2] Add CSS rules for `.selection-grid.is-disabled` (`filter: blur(2px); opacity: 0.5; pointer-events: none; cursor: not-allowed;`) and `.custom-role-input` (input + trailing `X` button positioning). Either extend `frontend/src/index.css` (where `.selection-grid` already lives) or add a colocated CSS-module — `frontend/src/index.css`

**Checkpoint**: User Story 2 is fully functional. Typing a custom role overrides prefabs, X clears, Generate uses the role-of-record end-to-end. All US2 tests green; coverage gate ≥ 90% on every touched module.

---

## Phase 5: User Story 3 — Surprise Me clears custom (Priority: P2)

**Goal**: Surprise Me always produces a prefab role of record. If the custom-role input is non-empty when the user presses Surprise Me, it is silently cleared in the same dispatch so the rolled prefab pill is authoritative.

**Independent Test**: Pre-fill the custom-role input with `Tester` (prefab pills blur), then press Surprise Me. Verify (a) the custom input is empty, (b) exactly one prefab pill is selected uniformly from the nine, (c) prefab pills are un-blurred and clickable, (d) Universe + Art Style are also rolled.

> **Note**: The reducer change that satisfies this story (SurpriseMePicked clears customRole) is already part of T047 in US2 (data-model.md says the action's reducer branch clears customRole). This phase adds the **explicit US3 tests** that pin the behaviour, plus a UI-level test against the SurpriseMeButton interaction. No new production code is expected unless the US2 implementation has drifted.

### Tests for User Story 3 (MANDATORY — must fail before any refinement) ⚠️

- [X] T056 [P] [US3] Extend `state/reducer.test.ts` with a dedicated SurpriseMePicked-clears-customRole test: starting state has `customRole: 'Tester'` and `archetype: null`; dispatch `SurpriseMePicked` with synthetic picks; assert post-state has `customRole === ''` and `archetype === picks.archetype` — `frontend/src/features/alterego/state/reducer.test.ts`
- [X] T057 [P] [US3] Extend `SurpriseMeButton.test.tsx`: render with a session that has `customRole: 'Tester'`; simulate the SurpriseMe click; assert the resulting reducer state (or the visible UI after rerender) shows the custom input empty AND the prefab grid un-blurred (no `is-disabled` class on the ArchetypeGrid wrapper) — `frontend/src/features/alterego/components/SurpriseMeButton.test.tsx`
- [X] T058 [P] [US3] Extend `frontend/tests/e2e/role-custom.e2e.ts` (created in T035) or add a new spec covering the US3 flow end-to-end: type `Tester` → press Surprise Me → input empty + prefab grid clickable + Generate succeeds — `frontend/tests/e2e/role-custom.e2e.ts`

### Implementation for User Story 3

- [X] T059 [US3] Confirm the SurpriseMePicked reducer branch from T047 already clears `customRole`. If T056/T057/T058 fail, refine the reducer in `frontend/src/features/alterego/state/reducer.ts` to align with the expected behaviour — `frontend/src/features/alterego/state/reducer.ts`

**Checkpoint**: All three user stories are independently functional. Custom + Surprise Me interaction is predictable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Bring touched modules to the constitution's quality gates and align user-facing artefacts.

- [X] T060 [P] Update `CLAUDE.md` "Active Technologies" + "Recent Changes" sections to record the 022 entry (one line per section, mirroring 021's entry style) — `CLAUDE.md`
- [X] T061 [P] Run `npm run lint` in `frontend/` and resolve any new warnings or errors; confirm Vitest line coverage ≥ 90% on every touched module (`reducer.ts`, `selectors.ts`, `SelectionGrid.tsx`, `ArchetypeGrid.tsx`, `CustomRoleInput.tsx`, `SetupLayout.tsx`, `alterEgoClient.ts`, `fallbackPoster.ts`) — `frontend/`
- [X] T062 [P] Run `./gradlew check` and confirm JaCoCo line coverage ≥ 90% on every touched backend module (`Archetype.java`, `AlterEgoUserSelections.java`, `AlterEgoRequest.java`, `RoleOfRecordPresent*.java`, `AlterEgoService.java`, `GeminiPromptBuilder.java`, `FalAiPromptBuilder.java`, `GeminiCharacterPromptBuilder.java`, `FallbackPosterProvider.java`, `AccentResolver.java`) — `backend/`
- [X] T063 [P] Run `./gradlew sonar` (requires SonarQube on `localhost:9000` per constitution Development Workflow step 6); resolve any NEW issues — `backend/`
- [X] T064 [P] Run `npm audit` (`frontend/`) and `./gradlew dependencyCheckAnalyze` (`backend/`) — resolve any HIGH/CRITICAL advisories before merging (constitution Principle VI) — repo root
- [X] T065 Re-render `specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml` reference fixture if the project pins one, OR open a follow-up issue to fold the 022 delta from `specs/022-role-options-custom/contracts/alter-egos.openapi.delta.yaml` into the canonical 002 contract on next contract sweep — `specs/`
- [X] T066 Run the quickstart.md smoke flows (US1, US2, US3) against the local dev stack; record the outcome in the PR description — `specs/022-role-options-custom/quickstart.md`
- [X] T067 Open the PR from `022-role-options-custom` → `main`. PR description: link the spec, list the FR coverage matrix, attach the screenshots of the new Role grid (9 pills) + custom-role-input + blurred-prefab state. Request explicit human approval per constitution Principle V — GitHub

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies; T001 first.
- **Phase 2 (Foundational)**: Empty.
- **Phase 3 (US1)**: Depends only on Phase 1.
- **Phase 4 (US2)**: Depends only on Phase 1. **Does NOT depend on Phase 3** — US1 and US2 can be staffed in parallel.
- **Phase 5 (US3)**: Depends on Phase 4 (T047 introduces the SurpriseMePicked clear behaviour).
- **Phase 6 (Polish)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **US1**: Independent. Pure additive change to enum + option list + prompt-builder label maps.
- **US2**: Independent of US1. Reducer + DTO + UI surface; touches the prompt builders' READ site (`request.roleLabel()`) but not the label maps that US1 extends.
- **US3**: Depends on US2 (reducer's SurpriseMePicked clear). Cannot be tested in isolation.

### Within Each User Story

- Tests MUST be written and fail before implementation (Principle III). For US1, that means the test commit references new enum values that don't compile — that compile failure IS the failing-test signal. After the impl commit (T009/T015) the tests compile and pass.
- Models / type definitions before consumers (e.g. T036/T037 before T038/T039 in US2).
- Backend before frontend within a single story is NOT required — both halves can land in parallel as long as both sets of tests are red before either set of impl is committed.

### Parallel Opportunities

- All Phase 3 tests (T002–T008) are [P] — different files.
- All Phase 3 implementations (T009–T015) are [P] except T015 which depends on T014.
- All Phase 4 tests (T016–T035) are [P] — different files.
- Phase 4 implementation: T036/T037 are [P]; T038 + T039 depend on them; T040 depends on T039; T041/T042/T043/T044/T045/T046 are [P]; T047 depends on T046; T048/T049/T050/T051/T053/T054/T055 are [P]; T052 depends on T047/T049/T050/T051.
- Phase 5 tests (T056–T058) are [P]; T059 is conditional.
- Phase 6 tasks (T060–T064) are [P]; T065/T066/T067 are sequential at the end.
- US1 and US2 can be done by two developers in parallel (no shared file ownership).

---

## Parallel Example: User Story 2 tests

```bash
# Spawn all US2 backend tests as failing markers in parallel:
Task: "RoleOfRecordPresentValidatorTest — truth table for the OR constraint"
Task: "AlterEgoUserSelectionsValidationTest — class-level constraint cases"
Task: "AlterEgoRequestValidationTest — class-level constraint + pose/vibe"
Task: "RecordInvariantsTest — roleLabel() helper truth table"
Task: "GeminiPromptBuilderTest — custom-role substitution"
Task: "FalAiPromptBuilderTest — custom-role substitution"
Task: "GeminiCharacterPromptBuilderTest — bio prompt swap"
Task: "AlterEgoServiceTest — text-overlay receives roleLabel()"
Task: "FallbackPosterProviderTest — null archetype defaulting"
Task: "AlterEgoControllerContractTest — customRole-only + prefab-only happy paths"
Task: "AlterEgoControllerErrorContractTest — 400 cases"
Task: "AlterEgoControllerInputValidationIT — length boundary"
Task: "GenerateAlterEgoTextOverlayIT — overlay shows trimmed custom string"

# Spawn all US2 frontend tests as failing markers in parallel:
Task: "CustomRoleInput.test.tsx — typing, X clear, maxLength, focus order"
Task: "reducer.test.ts — CustomRoleChanged + SurpriseMePicked clear"
Task: "selectors.test.ts — gating via EITHER archetype OR custom"
Task: "ArchetypeGrid.test.tsx — disabled prop matrix"
Task: "SetupLayout.test.tsx — CustomRoleInput placement + disabled wiring"
Task: "alterEgoClient.test.ts — customRole serialisation"
Task: "role-custom.e2e.ts — happy-path E2E"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1.
2. Complete Phase 3 (US1) — three new prefab options, full pipeline.
3. **STOP and VALIDATE**: smoke-test US1's three new pills end-to-end.
4. Merge as MVP. The spec is partially satisfied (US1) but functionally complete and demoable.

### Incremental Delivery (recommended)

1. Phase 1 → Phase 3 (US1) → demo / merge / move on.
2. Phase 4 (US2) → custom-role input working end-to-end → demo / merge.
3. Phase 5 (US3) → Surprise Me coexistence → demo / merge.
4. Phase 6 (Polish) → coverage gates, lint, SonarQube, PR open.

### Parallel Team Strategy

- Developer A: Phase 3 (US1) — small, ~7 test tasks + ~7 impl tasks, mostly enum-additive.
- Developer B: Phase 4 (US2) — bigger; 20 tests + 20 impl tasks. Can start immediately after Phase 1.
- Developer A picks up Phase 5 (US3) and Phase 6 (Polish) once both branches are integrated.

---

## Notes

- **Backwards compatibility**: T038/T039 widen the DTOs additively and relax `@NotNull` on `archetype`. Old clients (002..021) that send only the original six `archetype` values continue to validate. The OR-validator catches the "neither field present" malformed-request case at the validation boundary — no controller-layer branching required.
- **No new dependency**: every new icon is already in `lucide-react`; every new test uses existing JUnit 5 / Vitest / Playwright stacks.
- **No persistence**: extends 001 FR-016 / FR-017 / FR-024 unchanged.
- Constitution gates: Principle III (TDD) — tests before implementation in every phase; ≥ 90% coverage on touched modules; one Playwright E2E (T035). Principle IV (Resilient HTTP) — wire surface additive, retry + fallback paths untouched. Principle V (branch + PR) — single PR at T067. Principle VI — `npm audit` + `./gradlew dependencyCheckAnalyze` at T064.
- **Definition of done**: all 67 tasks ticked, all green tests, coverage gate met, SonarQube clean, PR approved.

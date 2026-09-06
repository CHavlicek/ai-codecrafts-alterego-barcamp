---

description: "Tasks for 021 — Add Engineer Role to the AI Image Generation Input Prompt"
---

# Tasks: 021 — Add Engineer Role to the AI Image Generation Input Prompt

**Input**: Design documents from `/specs/021-engineer-role-prompt/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/image-prompt-contract.md ✅, quickstart.md ✅

**Tests**: Test tasks are MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). They MUST be written before any implementation task in the same user story and MUST fail before production code is committed. Unit line coverage MUST stay ≥ 90%. The Generate path's existing `@SpringBootTest` integration test (`GenerateAlterEgoGeminiIT`) is extended with one parametrised assertion in this feature — see T004.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1 = P1, US2 = P2)
- File paths are absolute from the repo root.

## Path Conventions

This feature is **backend-only**. No frontend file is touched. No new file is created. Paths used below:

- Production sources: `backend/src/main/java/com/aiavatar/alterego/service/{gemini,falai}/*.java`
- Unit tests: `backend/src/test/java/com/aiavatar/alterego/unit/{gemini,falai}/*Test.java`
- Integration tests (`@SpringBootTest`): `backend/src/test/java/com/aiavatar/alterego/integration/*IT.java`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Confirm the working tree is in the right state. No initialization is needed — the codebase, the branch, and the SpecKit artefacts are already in place from `/speckit.specify` and `/speckit.plan`.

- [X] T001 Confirm clean working tree on branch `021-engineer-role-prompt` (run `git status` — only untracked files like `poster-frame-long-bottom.png` and `specs/021-engineer-role-prompt/` are acceptable; the working tree under `backend/` MUST be clean before the RED step in Phase 3).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None. The feature requires no schema change, no new module, no new dependency, no new wire surface. The role enum (`Archetype`), the request DTO (`AlterEgoRequest`), and the controller wiring are already in place from prior features (002, 011, 014). No Phase-2 tasks are needed.

**Checkpoint**: User-story implementation can begin immediately after T001.

---

## Phase 3: User Story 1 — Chosen engineering role visibly shapes the cinematic poster (Priority: P1) 🎯 MVP

**Goal**: Both image prompt builders (`GeminiPromptBuilder`, `FalAiPromptBuilder`) emit a `- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): <label>` line on the **SINGLE** variant's attribute block, positioned after `Universe` and before `Art style`. The composition-notes no-rendered-text line in `buildSingle` is reinforced to forbid transcribing the role label. The role label vocabulary matches `GeminiCharacterPromptBuilder.ROLE_LABELS` byte-for-byte (FR-2102). Pair-wise role-only deltas produce distinct prompt strings.

**Independent Test**: Run `./gradlew test` from `backend/` after Phase 3 completes; all unit tests in `GeminiPromptBuilderTest` and `FalAiPromptBuilderTest` MUST pass, the parametrised integration assertion in `GenerateAlterEgoGeminiIT` MUST pass for all six `Archetype` values, and the SINGLE-variant byte-for-byte fixture asserts the new prompt shape verbatim. Optionally, a smoke run against a live Gemini provider with a SINGLE-mode photo produces an image whose props/environment/attire visually evoke the chosen role (manual; covered by `quickstart.md`'s SC-2101 pass — out of scope for the unit-test bar but checked before merge).

### Tests for User Story 1 (MANDATORY — must fail RED before implementation) ⚠️

> **NOTE: Write/edit these tests FIRST and ensure they FAIL before implementation.** All three tasks edit different files, so they can be developed in parallel.

- [X] T002 [P] [US1] In `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`, invert the role-exclusion regression-locks and add the new role-inclusion contract:
  - Replace `archetypeRoleIsNotPassedToImageGenerationAi` with `archetypeRoleAppearsInTheImagePrompt` — asserts the prompt contains `"Engineering role ("` and `"Cloud Architect"` for a `CLOUD_ARCHITECT` request.
  - Replace `changingRoleNoLongerChangesPromptSinceRoleIsExcluded` with `changingRoleChangesPromptSubstring` — asserts pair `(BACKEND_DEV, CLOUD_ARCHITECT)` produces distinct prompts, and each prompt contains its own Prompt label (`Backend Developer` / `Cloud Architect`) per the table in `specs/021-engineer-role-prompt/data-model.md`.
  - In `purelyVisualAxesAppearInTheTemplate`, drop the `assertFalse(prompt.contains("Engineering role:"))` line and add `assertTrue(prompt.contains("Engineering role ("))`. Keep the `Name:` and firstName negative assertions verbatim.
  - Replace the `expected` string in `explicitSinglePhotoModeProducesTodaysBaselinePrompt` with the SINGLE-variant fixture from `specs/021-engineer-role-prompt/contracts/image-prompt-contract.md` (byte-for-byte).
  - Add `everyArchetypeProducesAUniquePromptLine` — iterate `Archetype.values()`, build a prompt per archetype with all other fields constant, assert all six prompts are mutually distinct AND each prompt contains its own Prompt label.
  - Add `engineeringRoleLineFollowsUniverseAndPrecedesArtStyle` — index-of assertions: universe < role < art-style line indexes.
  - Add `compositionNoteForbidsTranscribingTheRoleLabel` — `assertTrue(prompt.contains("NO transcribing the engineering-role label"))`.
  - Update the JavaDoc on `displayLabelsAreHumanReadableNotWireValues` to additionally pin every `Archetype` to its Prompt label substring.

- [X] T003 [P] [US1] In `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPromptBuilderTest.java`, mirror the inversion and additions from T002:
  - Replace `roleArchetypeIsNotPassedToImageGenerationAi` with `roleArchetypeAppearsInTheImagePrompt` — iterate `Archetype.values()`, build a SINGLE-mode prompt per archetype, assert `prompt.contains("Engineering role (")` and `prompt.contains(<Prompt label>)` for each.
  - Add `changingRoleChangesPromptSubstring` — pair `(BACKEND_DEV, CLOUD_ARCHITECT)` asserts distinct prompts.
  - Add `engineeringRoleLineFollowsUniverseAndPrecedesArtStyle` — same index-of ordering assertion as T002.
  - Add `compositionNoteForbidsTranscribingTheRoleLabel` — `assertTrue(prompt.toLowerCase().contains("no transcribing the engineering-role label"))`.
  - Keep `firstNameIsNotPassedToImageGenerationAi` unchanged (FR-1812 / 017 (refined) regression-lock preserved).

- [X] T004 [P] [US1] In `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiIT.java`, add a new `@ParameterizedTest @EnumSource(Archetype.class)` method `generatedPromptIncludesEachArchetypeLabel` that:
  - Builds an `AlterEgoRequest` with the parameter archetype + a fixed photo + fixed Universe / ArtStyle / firstName.
  - Posts to `POST /api/alter-ego` via `MockMvc`.
  - Captures the prompt argument passed to the mocked `GeminiClient` (use an `ArgumentCaptor<String>` on the existing client mock).
  - Asserts the captured prompt contains the archetype's Prompt label (per `specs/021-engineer-role-prompt/data-model.md`).
  - Does NOT introduce a live-provider call (per Principle III: production-profile wiring is not mocked away, but provider HTTP credits are not consumed in CI).

- [X] T005 [US1] Run `./gradlew test` from `backend/`. Verify the test suite is RED — the new/inverted tests in T002, T003, and T004 must fail against today's `GeminiPromptBuilder` / `FalAiPromptBuilder`. Commit the failing tests in a single commit with message `test(021): RED — invert role-exclusion locks and add role-inclusion contract (refs #54)`. Request explicit approval of the test suite per Constitution Principle III item 2 before proceeding to implementation.

### Implementation for User Story 1

- [X] T006 [P] [US1] In `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`:
  - Add `import com.aiavatar.alterego.model.Archetype;`.
  - Add a `private static final Map<Archetype, String> ROLE_LABELS = new EnumMap<>(Archetype.class);` field alongside the existing label maps.
  - Populate `ROLE_LABELS` in the static initializer with the six entries from `specs/021-engineer-role-prompt/data-model.md` (identical to `GeminiCharacterPromptBuilder.ROLE_LABELS`).
  - In `appendCategoryLines`, emit the role line **between** the existing Universe line and the existing Art-style line:
    ```java
    sb.append("- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): ")
            .append(label(ROLE_LABELS, request.archetype())).append('\n');
    ```
  - In `buildSingle`, change the closing composition-notes no-text line to the strengthened form ending in `, and NO transcribing the engineering-role label.` — copy the line verbatim from `specs/021-engineer-role-prompt/contracts/image-prompt-contract.md`.
  - Update the `appendCategoryLines` JavaDoc: remove the "engineering role are deliberately NOT included" prose; replace it with a one-line note that the role is included as a *visual* scene direction (props / environment / attire / activity) and link to `specs/021-engineer-role-prompt/` for context. Keep the first-name exclusion explanation untouched.

- [X] T007 [P] [US1] In `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java`, mirror the changes from T006:
  - Add `import com.aiavatar.alterego.model.Archetype;`.
  - Add `ROLE_LABELS` `EnumMap` with the same six entries.
  - In `appendCategoryLines`, emit the new role line in the same position (between Universe and Art-style).
  - In `buildSingle`, strengthen the no-text composition-notes line to the form ending in `, and NO transcribing the engineering-role label.`.
  - Update the `appendCategoryLines` JavaDoc: remove the "engineering role are deliberately NOT included" prose; replace it with a one-line note referencing `specs/021-engineer-role-prompt/` and the 016 R11 fork-rather-than-share decision.

- [X] T008 [US1] Run `./gradlew test` from `backend/`. Verify GREEN — all tests in `GeminiPromptBuilderTest`, `FalAiPromptBuilderTest`, and `GenerateAlterEgoGeminiIT` pass. If anything is RED, fix the production code (NOT the tests). Commit the implementation in a single commit with message `feat(021): inject engineering role into image prompts (refs #54)`.

**Checkpoint**: US1 is complete. Both image builders' SINGLE-variant prompts now contain the role line and the strengthened no-text line. The MVP — "the chosen role visibly influences the cinematic poster" — is exercisable via the existing Setup → Generate flow with any single-subject photo.

---

## Phase 4: User Story 2 — Group photo: every face shares the same engineering role (Priority: P2)

**Goal**: The `GROUP`-variant prompt path emitted by both image builders also includes the new role line (it already does after T006/T007 because `appendCategoryLines` is shared between `buildSingle` and `buildGroup`), and its composition-notes no-text line is reinforced to forbid transcribing the role label — completing the parity between `SINGLE` and `GROUP` variants.

**Independent Test**: Run `./gradlew test` from `backend/` after Phase 4 completes; the new GROUP-variant unit assertions in `GeminiPromptBuilderTest` and `FalAiPromptBuilderTest` pass. Optionally, a smoke run with a group photo (≥2 faces) on the Setup tab produces a poster where every rendered subject shares the same role-coded scene.

### Tests for User Story 2 (MANDATORY — must fail RED before implementation) ⚠️

> **NOTE: After T006/T007 land, the shared `appendCategoryLines` already emits the role line in GROUP prompts. The GROUP-variant role-line assertion will therefore pass without further code change — but the GROUP-variant composition-note strengthening assertion will fail RED until T011/T012 lands.**

- [X] T009 [P] [US2] In `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`, add three new GROUP-variant tests (do NOT modify the existing `groupPhotoModeUsesPluralWording`, `groupPhotoModeOmitsVibeLineWhenVibeIsNull`, `groupPhotoModeIncludesVibeLineWhenVibeIsSet` tests):
  - `groupPhotoModeIncludesEngineeringRoleLine` — build a `PhotoMode.GROUP` request with `Archetype.CLOUD_ARCHITECT`; assert prompt contains `"Engineering role ("` and `"Cloud Architect"`.
  - `groupPhotoModeRoleLineFollowsUniverseAndPrecedesArtStyle` — same index-of ordering assertion as the SINGLE variant.
  - `groupPhotoModeCompositionNoteForbidsTranscribingTheRoleLabel` — `assertTrue(prompt.contains("NO transcribing the engineering-role label"))`.

- [X] T010 [P] [US2] In `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPromptBuilderTest.java`, mirror the three GROUP-variant tests from T009:
  - `groupModeIncludesEngineeringRoleLine` — iterate `Archetype.values()` with `PhotoMode.GROUP`; assert each Prompt label appears.
  - `groupModeRoleLineFollowsUniverseAndPrecedesArtStyle` — index-of ordering assertion.
  - `groupModeCompositionNoteForbidsTranscribingTheRoleLabel` — `assertTrue(prompt.toLowerCase().contains("no transcribing the engineering-role label"))`.

### Implementation for User Story 2

- [X] T011 [US2] In `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`, update the `buildGroup` method's closing composition-notes no-text line to the same strengthened form used in `buildSingle` (ending in `, and NO transcribing the engineering-role label.`). Keep the "render EVERY person visible", "Do NOT invent additional people", and "Arrange the group so every face is clearly visible" clauses untouched. The role line is already emitted by the shared `appendCategoryLines` helper updated in T006 — no further change to the GROUP attribute block is needed.

- [X] T012 [US2] In `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java`, mirror T011 — update only the `buildGroup` composition-notes no-text line. The role line is already emitted by the shared helper updated in T007.

- [X] T013 [US2] Run `./gradlew test` from `backend/`. Verify GREEN. Commit the GROUP-variant implementation with message `feat(021): add engineering role to GROUP-mode image prompt (refs #54)`.

**Checkpoint**: US2 is complete. Both `SINGLE` and `GROUP` variant prompts have role line + strengthened no-text composition note for both image providers. Feature is functionally complete at the unit/integration level.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Coverage gate, static analysis, manual provider-validation pass against the user-facing success criteria, and PR preparation.

- [X] T014 [P] Run `./gradlew jacocoTestReport` from `backend/`. Open `build/reports/jacoco/test/html/index.html`. Verify line coverage for `com.aiavatar.alterego.service.gemini` and `com.aiavatar.alterego.service.falai` packages is ≥ 90% per Principle III. If either dropped, add coverage by extending the new GROUP-variant or SINGLE-variant tests — do NOT add narrative tests that don't actually exercise more lines.

- [ ] T015 [P] Run `./gradlew sonar` from `backend/` (requires a self-hosted SonarQube on `localhost:9000` per Constitution Technology Standards). Resolve every NEW issue raised by Sonar on the touched files. If SonarQube isn't running locally, flag this in the PR description as a known gap; do not commit a `-x sonar` skip.

- [ ] T016 Manual provider-validation pass per `specs/021-engineer-role-prompt/quickstart.md` (Manual provider-validation pass section). Execute SC-2101..SC-2106 with the live provider (Gemini profile is the default). Record the per-role identifiability sample (≥6 generations per role), the zero-text-bleed audit across the ≥30-image sample, the pair-wise identifiability check, the Surprise-Me parity check, the latency-budget check, and the fallback-profile smoke check. Capture the results in a short Markdown comment to be appended to the PR description. If SC-2102 reveals **any** rendered text inside the image, follow the rollback plan in `quickstart.md` — do NOT merge a partial fix.

- [ ] T017 Open a pull request from `021-engineer-role-prompt` to `main`. Title: `feat(021): add engineering role to image-generation prompt`. PR body MUST include:
  - A link to issue #54.
  - The before/after `SINGLE`-variant prompt diff (visible directly from the test fixture change).
  - The T016 validation-pass results.
  - The coverage and Sonar status from T014/T015.
  Request explicit human approval per Constitution Principle V before merging.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001 — no dependencies.
- **Phase 2 (Foundational)**: empty — skip.
- **Phase 3 (US1, P1)**: Depends on T001.
- **Phase 4 (US2, P2)**: Depends on Phase 3 completion. US2's GROUP-variant role-line assertions assume the shared `appendCategoryLines` helper has been updated by T006/T007; US2's composition-note strengthening builds on that base.
- **Phase 5 (Polish)**: Depends on Phase 4 completion (or Phase 3 if shipping the SINGLE-only MVP).

### User Story Dependencies

- **US1 (P1)** — Can start immediately after T001. The MVP.
- **US2 (P2)** — Builds on US1's shared-helper change. Cannot be implemented standalone (US2's tests assume the role line is already in the shared helper).

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Principle III — non-negotiable). Sequence: T002/T003/T004 → T005 (RED gate) → T006/T007 → T008 (GREEN gate). Same shape for US2: T009/T010 → T011/T012 → T013.

### Parallel Opportunities

- **Within Phase 3**: T002, T003, T004 are all in different files → run in parallel. T006 and T007 are in different files → run in parallel.
- **Within Phase 4**: T009 and T010 are in different files → run in parallel. T011 and T012 are in different files → run in parallel.
- **Within Phase 5**: T014 (Jacoco) and T015 (Sonar) are independent → run in parallel.

---

## Parallel Example: User Story 1 (Phase 3)

```bash
# RED step — launch all three test edits in parallel (different files):
Task: "Invert + extend GeminiPromptBuilderTest.java per T002"
Task: "Invert + extend FalAiPromptBuilderTest.java per T003"
Task: "Extend GenerateAlterEgoGeminiIT.java with per-Archetype prompt-capture assertion per T004"

# After T005's RED-gate commit + approval, launch the two implementation edits in parallel:
Task: "Add role line + strengthen no-text line in GeminiPromptBuilder.java per T006"
Task: "Add role line + strengthen no-text line in FalAiPromptBuilder.java per T007"
```

## Parallel Example: User Story 2 (Phase 4)

```bash
# RED step — launch the two new GROUP-variant test additions in parallel (different files):
Task: "Add three GROUP-variant tests to GeminiPromptBuilderTest.java per T009"
Task: "Add three GROUP-variant tests to FalAiPromptBuilderTest.java per T010"

# After RED, launch the two GROUP-variant implementation edits in parallel:
Task: "Strengthen buildGroup composition-note line in GeminiPromptBuilder.java per T011"
Task: "Strengthen buildGroup composition-note line in FalAiPromptBuilder.java per T012"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001 — verify clean tree.
2. T002 / T003 / T004 in parallel — write RED tests.
3. T005 — verify RED, commit, request approval.
4. T006 / T007 in parallel — implement.
5. T008 — verify GREEN, commit.
6. **STOP and VALIDATE**: Run the relevant slice of T016 against a live provider with a SINGLE-mode photo — confirm SC-2101 (per-role identifiability) and SC-2102 (zero text bleed). If the bleed test fails, **rollback per quickstart.md** before continuing to Phase 4.
7. Optionally demo / preview here. Do NOT merge to `main` yet — US2 (group photos) is a meaningful coverage gap.

### Incremental Delivery

1. Setup → Foundational (empty) → US1 → checkpoint → US2 → Polish → merge.
2. Each checkpoint runs the relevant slice of T016 (SINGLE for US1; SINGLE + GROUP for US2).
3. Only one PR — there is no per-story PR strategy because the change-set is small (5 files) and the user stories share the same review surface (prompt-builder wording).

### Parallel Team Strategy

This feature's change-set is too small to benefit from parallel-team work. One developer can complete US1 + US2 in a single afternoon. If somehow parallelised, the natural split is by image provider: Developer A owns the Gemini-side (T002, T006, T009, T011) and Developer B owns the fal.ai-side (T003, T007, T010, T012). The integration test (T004) and the manual validation (T016) stay single-owner.

---

## Notes

- The change-set is intentionally small: **2 production files** + **3 test files**, no new files, no new dependencies, no wire change.
- **The load-bearing risk is text bleed** — the model rendering the role label as decorative banner text inside the image. This is exactly what the 017 (refined) workaround was protecting against. The rollback path in `quickstart.md` exists for a reason: if T016's SC-2102 audit reveals **any** text bleed, do not paper over it — rework the prompt wording or revert.
- The "role line" wording is pinned by the byte-for-byte SINGLE-variant fixture in T002. Any change to the wording during implementation requires updating that fixture in `quickstart.md`'s contract reference and in T002 itself — keep them in sync.
- Avoid: vague tasks ("update tests"), same-file conflicts (don't run T002 and T009 in parallel — they edit the same file in different phases; the phase ordering enforces sequencing), cross-story dependencies that break independence (US2 explicitly builds on US1's shared-helper change — this is documented and intentional, not an oversight).

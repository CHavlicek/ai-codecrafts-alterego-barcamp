# Tasks: Group Photos (011)

> Dependency-ordered task list. Follow TDD per constitution Principle III — write the failing test, then the implementation.

## Phase A — Frontend

- **T001** Extend `frontend/src/features/alterego/types.ts`: add `PhotoMode = 'single' | 'group'` and add `photoMode: PhotoMode` to `Selections`.
- **T002** Extend `frontend/src/features/alterego/options.ts`: import `User, Users` from `lucide-react`; add `PHOTO_MODE_OPTIONS: ReadonlyArray<EnumOption<PhotoMode>>` with the two pairs; re-export `Users` alongside the existing `Camera, Aperture, RotateCcw, Check, X` re-exports.
- **T003** Extend `frontend/src/features/alterego/state/reducer.ts`:
  - Add `photoMode: PhotoMode` to `AlterEgoSession`.
  - Set `photoMode: 'single'` in `initialAlterEgoSession()`.
  - Add `{ type: 'PhotoModeSelected'; photoMode: PhotoMode }` to `AlterEgoAction`.
  - Add the reducer branch returning `{ ...state, photoMode: action.photoMode, phase: 'picking' }`.
- **T004** Add tests to `frontend/src/features/alterego/state/reducer.test.ts`:
  - `photoMode` initial value is `'single'`.
  - `PhotoModeSelected` flips to `'group'`.
  - `PhotoModeSelected` flips back to `'single'`.
  - `StartOverRequested` resets `photoMode` to `'single'`.
  - Flipping mode does not touch any other session field.
- **T005** Create `frontend/src/features/alterego/components/PhotoModeSwitch.tsx` as a thin wrapper around `SelectionGrid`, exposing props `{ value: PhotoMode; onChange(m: PhotoMode): void }`. Use heading "Single or Group Photo", accent var `--color-accent-pose` (reuse; see research.md R4) or add a new token if visual review requires it.
- **T006** Create `frontend/src/features/alterego/components/PhotoModeSwitch.test.tsx`:
  - Renders both options with their labels.
  - Default value `'single'` → Single has `aria-checked="true"`.
  - Click Group fires `onChange('group')`.
  - Re-clicking Single when already checked does not fire `onChange`.
  - Arrow keys move focus between options with wrap-around.
  - Lucide icons are `aria-hidden="true"`.
  - Heading is associated with the radiogroup via `aria-labelledby`.
- **T007** Extend `frontend/src/features/alterego/components/SetupLayout.tsx`:
  - Import `PhotoModeSwitch`.
  - Add a `.setup-layout__numbered-group` with `data-step="0"` as the FIRST numbered group, above Pose.
  - Wire `value={session.photoMode}` and `onChange={(photoMode) => dispatch({ type: 'PhotoModeSelected', photoMode })}`.
- **T008** Extend `SetupLayout.test.tsx` (if the test file exercises the layout structure): add a test that the switch mounts with the default mode and that changing the mode dispatches a `PhotoModeSelected` action.
- **T009** Extend `frontend/src/features/alterego/AlterEgoPage.tsx`:
  - In `handleSubmit`, include `photoMode: state.photoMode` in the `selections` object.
  - In `handleSurprise`, pass `photoMode: state.photoMode` through to `surprise(...)`.
- **T010** Extend `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts`:
  - Add `photoMode: PhotoMode` to `SurpriseArgs`.
  - Include `photoMode: args.photoMode` when building the `Selections` for the mutation call.
- **T011** Extend `useGenerateAlterEgo.test.tsx` / `AlterEgoPage.test.tsx`: assert that both the normal Generate and the Surprise Me paths emit `photoMode` in the outbound `Selections`.

## Phase B — Backend

- **T012** Create `backend/src/main/java/com/aiavatar/alterego/model/PhotoMode.java`:
  ```java
  public enum PhotoMode {
      @JsonProperty("single") SINGLE,
      @JsonProperty("group")  GROUP
  }
  ```
- **T013** Extend `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`:
  - Add trailing component `PhotoMode photoMode` (nullable — no `@NotNull`).
  - Add method `public PhotoMode effectivePhotoMode() { return photoMode != null ? photoMode : PhotoMode.SINGLE; }`.
  - Update `withTrimmedFirstName()` to preserve the new component.
- **T014** Extend `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java` (or add `PhotoModeTest.java` alongside it) to cover the wire values `"single"` / `"group"` and the Jackson round-trip.
- **T015** Extend `backend/src/test/java/com/aiavatar/alterego/unit/RecordInvariantsTest.java`: assert `effectivePhotoMode()` defaults to `SINGLE` when the field is null and echoes the field otherwise.
- **T016** Extend `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`:
  - Branch in `build(...)` on `request.effectivePhotoMode()`.
  - SINGLE variant: unchanged.
  - GROUP variant per `data-model.md`.
- **T017** Extend `backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilderTest.java` (or create it if absent):
  - Regression lock: SINGLE variant's full output for a fixed input is byte-identical to the pre-feature prompt.
  - GROUP variant contains the plural phrasing "group portrait poster of the people", the "EVERY person" instruction, the "Do NOT invent additional people" negative constraint, and "Group name:" label.
  - Both variants include the five category labels when all are set.
  - GROUP variant omits the `- Vibe / tone:` line when `vibe` is null.
- **T018** Extend `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java` (+/- `AlterEgoControllerErrorContractTest.java`):
  - Absent `photoMode` → 200 OK (forward-compat for old clients).
  - `photoMode: "single"` → 200 OK.
  - `photoMode: "group"` → 200 OK.
  - `photoMode: "team"` (unknown) → 400 Problem Details.
- **T019** Extend an integration test (e.g. `GenerateAlterEgoGeminiIT`) to round-trip `photoMode: "group"` through the full pipeline and assert the captured outbound prompt includes the plural phrasing.

## Phase C — E2E + accessibility

- **T020** Create `frontend/tests/e2e/group-photos.spec.ts`:
  - Landing on Setup shows the switch with Single checked by default.
  - Clicking Group flips `aria-checked`.
  - With a photo + name present, clicking Generate while in Group mode sends `selections.photoMode === "group"` in the outbound multipart body (intercepted via `page.route`).
  - Start-over resets the switch back to Single.
- **T021** Extend `frontend/tests/e2e/axe-scan.spec.ts` if necessary so the new switch is in the scanned region. Expect zero new violations.

## Phase D — Ship

- **T022** Run `cd frontend && npm run lint && npm run build && npm run test && npm run test:coverage`. All green; coverage on new code ≥ 90%.
- **T023** Run `cd backend && ./gradlew test`. All green.
- **T024** Spot-check the Playwright spec: `cd frontend && npx playwright test group-photos.spec.ts` (if a local browser is available; skip with a note if not).
- **T025** Commit all artefacts (spec/plan/research/data-model/quickstart/tasks/checklists + code + tests) in one or two logical commits.
- **T026** Push `claude/nice-brown-QNhLb` and open PR closing #10. Include a manual-walkthrough checklist for the reviewer in the PR description.

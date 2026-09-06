---

description: "Task list for 002-sleek-tabbed-ui — Initial Styling and Layout (Tabbed Setup Experience)"
---

# Tasks: Initial Styling and Layout — Tabbed Setup Experience

**Input**: Design documents from `/specs/002-sleek-tabbed-ui/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: MANDATORY per Principle III (Test-First Development, NON-NEGOTIABLE). Every test task within a user story MUST be written and MUST fail before the matching implementation task is committed. Unit line coverage MUST reach ≥ 90% per module; one integration test per feature minimum. These gates are not negotiable.

**Organization**: Tasks are grouped by user story. Within each story, tests precede implementation. Two P1 stories (US1 tab shell, US2 form restyle) both qualify as MVP — US1 alone is the smallest shippable slice.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: different file, no dependencies on incomplete tasks → parallelizable
- **[Story]**: US1 / US2 / US3 — maps task to a spec.md user story
- Include exact file paths

## Path Conventions

- **Backend (Java 21 + Spring Boot 3)**: `backend/src/main/java/com/aiavatar/alterego/**`, tests at `backend/src/test/java/com/aiavatar/alterego/**`
- **Frontend (React 18 + TypeScript strict, Vite)**: `frontend/src/features/alterego/**`, colocated `*.test.tsx` (Vitest + RTL), Playwright specs at `frontend/tests/playwright/**`
- **Shared styles**: `frontend/src/styles/tokens.css`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Minimal shared prep — this feature rides on the 001 scaffold and does not add any new runtime dependencies.

- [X] T001 Extend `frontend/src/styles/tokens.css` with per-sub-group accent aliases (`--color-accent-pose`, `--color-accent-role`, `--color-accent-universe`, `--color-accent-vibe`) resolving to existing hex tokens per research.md §R4. Do not add new hex values; this is purely alias plumbing so components can read one variable per sub-group.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Nothing architectural blocks US1 vs. US2 vs. US3 here — the 001 scaffold already provides the runtime, routing, reducer, HTTP client, and test rigs. The single foundational item is documenting the zero-dependency-delta stance so reviewers can verify it at PR time.

**⚠️ CRITICAL**: US1 work can begin immediately after T001 / T002.

- [X] T002 Verify and record zero runtime dependency changes: run `npm ls --depth=0` in `frontend/` and `./gradlew dependencies` in `backend/`; paste the output excerpts into the PR description for the branch. No `package.json` or `build.gradle.kts` edits are permitted by this feature (Principle I + VI). Verified: frontend dep list unchanged (25 deps incl. `@axe-core/playwright`, `@tanstack/react-query`, `vitest`, `playwright` — no new additions).

**Checkpoint**: Foundation ready — US1, US2, US3 implementation can now begin in parallel per team capacity.

---

## Phase 3: User Story 1 — Two-tab workflow shell (Priority: P1) 🎯 MVP

**Goal**: Render two top-level tabs (`1 setup` / `2 Your Alter Ego`) with a sleek dark treatment, the correct WAI-ARIA tabs pattern (manual activation per research.md §R1), keyboard-only operability, a calm empty-state placeholder on the Alter Ego tab pre-generation, and auto-switch-to-Alter Ego when Generate is pressed (FR-108). Setup-tab content is the existing 001 form (to be restyled in US2) — US1 ships the shell around it.

**Independent Test**: Per spec.md §User Story 1 Independent Test. Load the app, confirm both tabs render with correct active/inactive styling, arrow-key navigation works with manual Enter/Space activation, the Alter Ego tab shows the placeholder copy, and pressing Generate on the (as-yet 001-style) form immediately flips the active tab to Alter Ego. Start-over returns the active tab to Setup.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T003 [P] [US1] Write `frontend/src/features/alterego/components/TabsShell.test.tsx` covering: tablist roles & aria-selected state, Arrow/Home/End keyboard navigation with roving tabindex, manual activation via Enter/Space (per research.md §R1), both panels always in DOM with `hidden` on the inactive one (per §R8), visible focus ring.
- [X] T004 [P] [US1] Write `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx` covering: empty-state copy "Your alter ego will appear here once you press **Generate** on the Setup tab." (per §R3), loading state renders the existing `<GenerationLoading />`, poster state renders `<PosterView />` + `<StartOverButton />` when `session.result` is set.
- [X] T005 [P] [US1] Write `frontend/src/features/alterego/state/reducer.test.ts` (or extend existing) for the new `ActiveTabChanged` action, the extended `StartOverRequested` branch that resets `activeTab` to `'setup'`, and the initial state (`activeTab === 'setup'`).
- [X] T006 [P] [US1] Write `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx` asserting that the mutation's `onMutate` dispatches `ActiveTabChanged` with `{ tab: 'alter-ego' }` before the fetcher fires (per §R2 + FR-108).
- [X] T007 [P] [US1] Write `frontend/tests/playwright/tabs-shell.spec.ts` covering: tab labels ("1 setup" / "2 Your Alter Ego"), side-by-side layout, non-colour-only active indicator (e.g. aria-selected + underline), keyboard-only navigation across tabs (Tab, Right/Left, Enter), pre-generation empty-state visible on tab 2, no broken/blank state at any moment. Run the spec's structural-assertion section from research.md §R9 and archive a 1440 px screenshot to `test-results/setup-tab-1440.png`.

### Implementation for User Story 1

- [X] T008 [US1] Extend `frontend/src/features/alterego/state/reducer.ts`: add `activeTab: 'setup' | 'alter-ego'` to state (default `'setup'`); add `{ type: 'ActiveTabChanged'; tab: 'setup' | 'alter-ego' }` action; extend the `StartOverRequested` branch to reset `activeTab` to `'setup'`. Keep every other action shape and effect as is.
- [X] T009 [P] [US1] Add `frontend/src/features/alterego/hooks/useActiveTab.ts` — thin selector returning `{ activeTab, setActiveTab }` that wraps `useAlterEgoSession` + a dispatcher.
- [X] T010 [P] [US1] Create `frontend/src/features/alterego/components/TabsShell.tsx` implementing the WAI-ARIA manual-activation tabs pattern (§R1). Props: `tabs: { id, label, panel: ReactNode }[]`. Render `role="tablist"` + two `role="tab"` + two `role="tabpanel"` elements; use roving tabindex; toggle `hidden` + `aria-hidden` on the inactive panel per §R8; call `setActiveTab` from `useActiveTab` on Enter/Space.
- [X] T011 [P] [US1] Create `frontend/src/features/alterego/components/AlterEgoPanel.tsx` rendering one of three states based on session `phase` + `result`: (a) empty placeholder (icon + the copy from §R3), (b) `<GenerationLoading />`, (c) `<PosterView />` + `<StartOverButton />`. Keep all existing 001 sub-components in their current form — this component is a pure layout switch.
- [X] T012 [US1] Refactor `frontend/src/features/alterego/AlterEgoPage.tsx` to compose `<TabsShell>` with two panels and remove the phase-driven full-page swap. Per `/speckit.analyze` I2 + the US1+US2 co-ship decision, skip the US1-interim step of embedding the *existing* 001 form — go straight to the US2 target: Setup panel = `<SetupLayout>` (which US2 creates in T060), Alter Ego panel = `<AlterEgoPanel>`. This task now sequences **after** T060; it folds what used to be US2's T061 into this single page-refactor.
- [X] T013 [US1] Update `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts` to dispatch `{ type: 'ActiveTabChanged', tab: 'alter-ego' }` from the TanStack Query mutation's `onMutate` callback (§R2 + FR-108). No change to the fetcher or retry/back-off logic.
- [X] ~~T014~~ Removed per `/speckit.analyze` I3 (no-op check is not a real task). App.tsx remains pointed at `<AlterEgoPage />` from 001; reviewer confirms in PR.

**Checkpoint**: US1 complete. Tabs render, auto-switch works, keyboard-only navigation works, empty state shows, Start-over returns to tab 1. The Setup tab still contains the 001 form (Pose + Colour + old-Archetype + old-Universe + Name). The feature can ship as an MVP at this boundary if desired.

---

## Phase 4: User Story 2 — Sleek Setup form (Priority: P1)

**Goal**: Replace the Setup tab's inner content with the sleek two-column layout from the mockup: left column YOUR PHOTO + Camera/Upload pills, right column numbered Pose / Engineer Role / Universe / Vibe sub-groups + Name + Generate. Drop the Colour picker; add the optional Vibe picker; re-theme Archetype to engineering roles and Universe to the six mockup values. Update the backend contract to match, including the stub/fallback accent derivation from `(archetype, universe)` per research.md §R6.

**Independent Test**: Per spec.md §User Story 2 Independent Test. On 1440 px, the Setup tab renders two columns with the section headings, sub-group counts, option counts, selection behaviours, and keyboard reachability described in FR-110–FR-123. At 375 px, the layout collapses to one column. Backend accepts the new payload (no `colour`, optional `vibe`, new archetype/universe enums) and returns differentiated stub output per `(archetype, universe[, vibe])`.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

#### Backend tests

- [X] T015 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java` for the new enum value sets: `Archetype` = {CLOUD_ARCHITECT, BACKEND_DEV, FRONTEND_DEV, AI_ENGINEER, PLATFORM_ENG, DATA_ENGINEER}; `Universe` = {MARVEL, STAR_WARS, CYBERPUNK, THE_OFFICE, INDIANA_JONES, LORD_OF_THE_RINGS}; `Vibe` = {BUILDER, THINKER, REBEL, ARCHITECT}. Remove `Colour` assertions. Assert JSON round-trip for every wire value (kebab-case).
- [X] T016 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoRequestValidationTest.java` — drop every `colour`-related assertion; add: null vibe is valid; valid vibe is accepted; no-vibe + all other fields valid passes Bean Validation.
- [X] T017 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/unit/StubCharacterGeneratorTest.java` — parametrised over every `(Archetype, Universe)` pair, plus a sample of vibe-present vs. vibe-absent cases. Assert the hero title line 2, tagline, superpowers, and quote differ across pairs (FR-131 "plausible differentiated content").
- [X] T018 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/unit/StubImageGeneratorTest.java` — add a parametrised test covering every `(Archetype, Universe)` pair asserting `deriveAccent` returns a non-null `AccentTone` with a hex drawn from the six-value palette (§R6). Determinism assertion: call twice per pair, expect equal results.
- [X] T019 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java` — assert the fallback poster's accent colour is derived from `(archetype, universe)` using the same logic as the primary stub (`deriveAccent` may be shared or replicated; either way, output must match for identical inputs).
- [X] T020 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java` to validate against `specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml` (v2.0.0). Cover: happy path with vibe present, happy path with vibe omitted, happy path with vibe=null JSON.
- [X] T021 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerErrorContractTest.java` — remove the `colour`-invalid 400 test; add: 400 on invalid archetype (e.g. `code-breaker`, an old 001 value), 400 on invalid universe (e.g. `harry-potter`), 400 on invalid vibe. An unknown `colour` field in the JSON must NOT cause a 400 (silently ignored by Jackson — per the OpenAPI note).
- [X] T022 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoIT.java` to submit the new payload shape (no colour, optional vibe) and assert the full response envelope against the v2 OpenAPI schema.
- [X] T023 [P] [US2] Update `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFallbackIT.java` — same payload shape; assert fallback behaviour + accent derivation survives the forced-failure path.

#### Frontend tests

- [X] T024 [P] [US2] Write `frontend/src/features/alterego/components/VibeGrid.test.tsx` — single-selection, deselect-on-second-click, keyboard operability, aria-pressed, zero-selection is a valid state (FR-118).
- [X] T025 [P] [US2] Write `frontend/src/features/alterego/components/SetupLayout.test.tsx` — two-column layout at ≥ 1024 px with YOUR PHOTO + ROLE/UNIVERSE/KEYS headings; single-column stack at narrow viewports with Photo first; four sub-group count in the right column; Name input present between Vibe and Generate; Generate at column bottom (FR-123).
- [X] T026 [P] [US2] Update `frontend/src/features/alterego/components/PoseGrid.test.tsx` — retain 4-option Heroic/Stealthy/Mystical/Scholar (unchanged wire values per data-model.md); update selection-state assertions to the new pill styling + accent-var contract; keep single-selection behaviour.
- [X] T027 [P] [US2] Update `frontend/src/features/alterego/components/ArchetypeGrid.test.tsx` — replace the 001 option set with the six engineering-role values; display label "Engineer role"; emoji prefix on each option; selection shows cyan outline.
- [X] T028 [P] [US2] Update `frontend/src/features/alterego/components/UniverseGrid.test.tsx` — replace options with the six mockup values in the mandated order; gold outline on selection.
- [X] T029 [P] [US2] Delete `frontend/src/features/alterego/components/ColourGrid.test.tsx` and `frontend/src/features/alterego/components/ColourGrid.tsx` (component removal follows in T044).
- [X] T030 [P] [US2] Update `frontend/src/features/alterego/components/FirstNameInput.test.tsx` — label text "Your name (for personalized character)", placeholder "e.g. Paula", programmatic label association.
- [X] T031 [P] [US2] Update `frontend/src/features/alterego/components/GenerateButton.test.tsx` — placement under Name (column-width), disabled-until-complete with Vibe excluded from the gating check (FR-122), click triggers `submit` from `useGenerateAlterEgo` which in turn auto-switches the tab (already under test in T006).
- [X] T032 [P] [US2] Update `frontend/src/features/alterego/AlterEgoPage.test.tsx` — full Setup→Generate→Alter Ego→Start-over flow against the new option set; re-generate without Start-over (FR-124) replaces the old poster with loading then new poster; tab-switch preservation test (SC-105).
- [X] T033 [P] [US2] Extend `frontend/src/features/alterego/state/reducer.test.ts` with `VibeSelected` action (select, deselect-on-same), and the initial state delta (no `colour`; `vibe === null`).
- [X] T034 [P] [US2] Write `frontend/tests/playwright/setup-form.spec.ts` covering: structural assertions (tab/column/section/sub-group/option counts per R9), labels match the mockup, keyboard tabbing order, SC-104 viewport behaviour at 1440 px and 375 px.
- [X] T035 [P] [US2] Update `frontend/tests/playwright/e2e-flow.spec.ts` (or create if absent) for the new end-to-end flow: fill Setup → Generate → auto-switch → poster → change Role on Setup → Generate again → new poster replaces (FR-124).

#### Accessibility tests

- [X] T036 [P] [US2] Add `axe-core` scans to `frontend/tests/playwright/setup-form.spec.ts` for the Setup tab in three content states: empty, partially filled, fully filled; target zero serious/critical violations (SC-103).

### Implementation for User Story 2

#### Backend

- [X] T037 [P] [US2] Create `backend/src/main/java/com/aiavatar/alterego/model/Vibe.java` — enum with `BUILDER`, `THINKER`, `REBEL`, `ARCHITECT`; `@JsonValue` returning kebab-case; `@JsonCreator` parsing kebab-case.
- [X] T038 [US2] Replace enum values in `backend/src/main/java/com/aiavatar/alterego/model/Archetype.java` with CLOUD_ARCHITECT / BACKEND_DEV / FRONTEND_DEV / AI_ENGINEER / PLATFORM_ENG / DATA_ENGINEER (kebab-case wire values). Keep the `@JsonValue` / `@JsonCreator` pattern.
- [X] T039 [US2] Replace enum values in `backend/src/main/java/com/aiavatar/alterego/model/Universe.java` with MARVEL / STAR_WARS / CYBERPUNK / THE_OFFICE / INDIANA_JONES / LORD_OF_THE_RINGS (kebab-case wire values).
- [X] T040 [US2] Delete `backend/src/main/java/com/aiavatar/alterego/model/Colour.java`. Remove every `import` and reference in the main sources (fix compile errors encountered by running `./gradlew compileJava`).
- [X] T041 [US2] Update `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java` to drop the `colour` record component, add a nullable `Vibe vibe` component (no `@NotNull`), and update `withTrimmedFirstName()` accordingly. Adjust the record's constructor order to match the OpenAPI schema order for readability.
- [X] T042 [US2] Introduce `AccentTone` as a private record (or internal class) inside the `service/stub/` package, plus a `deriveAccent(Archetype, Universe)` helper per research.md §R6 (curated-cell map + deterministic hash fallback across the six-value palette). Co-locate with `StubImageGenerator` so it is the single source of truth.
- [X] T043 [US2] Update `backend/src/main/java/com/aiavatar/alterego/service/stub/StubCharacterGenerator.java` — rebuild the lookup tables that generate hero title line 2, tagline, superpowers, and quote for the new Archetype × Universe space; include a vibe-present nuance (e.g. the rebel-vibe adjective appears in line 2 when vibe != null) per FR-131 + FR-132. Keep the existing differentiation intent from 001 FR-015.
- [X] T044 [US2] Update `backend/src/main/java/com/aiavatar/alterego/service/stub/StubImageGenerator.java` — re-key every `(Archetype, Universe)` branch to the new enum values; call `deriveAccent` for the accent-colour input to the poster renderer. Remove every `Colour` import.
- [X] T045 [US2] Update `backend/src/main/java/com/aiavatar/alterego/service/fallback/FallbackPosterProvider.java` — invoke the shared `deriveAccent` (or a replicated equivalent) so the fallback poster's accent is consistent with the primary stub for identical inputs.
- [X] T046 [US2] Update `backend/src/main/java/com/aiavatar/alterego/controller/AlterEgoController.java` — nothing to change in the mapping (Bean Validation does the heavy lifting), but verify the Jackson configuration accepts unknown properties silently (default is `FAIL_ON_UNKNOWN_PROPERTIES = false` in Spring Boot 3); if someone tightened it elsewhere, adjust so a stray `colour` field is ignored, not 400.
- [X] T047 [US2] Update the controller contract path referenced by tests — `backend/src/test/java/com/aiavatar/alterego/testsupport/OpenApiSpecPath.java` should now point to `specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml` (version 2.0.0). Keep a fallback to the 001 path if the file-loader supports versioned lookup; otherwise just update the literal.
- [X] T048 [US2] Update `backend/src/test/java/com/aiavatar/alterego/testsupport/SamplePhotos.java` if it hard-codes any `Colour` / old enum value; otherwise leave as is.

#### Frontend

- [X] T049 [US2] Update `frontend/src/features/alterego/types.ts` — remove `Colour`, replace `Archetype` union, replace `Universe` union, add `Vibe` union, update `Selections` to drop `colour` and add optional `vibe?: Vibe`. Run `tsc --noEmit` and fix every downstream error (the compiler is the rename tool per §R5).
- [X] T050 [US2] Update `frontend/src/features/alterego/options.ts` — delete `COLOUR_OPTIONS`; replace `ARCHETYPE_OPTIONS` with the six engineering-role entries (each `{ value, label, emoji, accentVar }`); replace `UNIVERSE_OPTIONS` with the six mockup universes; add `VIBE_OPTIONS` for the four vibes. Emoji glyphs per FR-116/117/118 + Assumptions.
- [X] T051 [US2] Update `frontend/src/features/alterego/state/reducer.ts` — drop `colour` from state + drop `ColourSelected` action; add `vibe: Vibe | null` to state (default `null`) + `VibeSelected` action with deselect-on-same semantics; update `initialAlterEgoSession()` accordingly.
- [X] T052 [US2] Delete `frontend/src/features/alterego/components/ColourGrid.tsx` (test already removed in T029).
- [X] T053 [P] [US2] Create `frontend/src/features/alterego/components/VibeGrid.tsx` — 2×2 pill grid, optional-selection semantics (zero or one), `aria-pressed` for non-colour-only selected state, consumes `--color-accent-vibe` for the selected outline.
- [X] T054 [P] [US2] Restyle `frontend/src/features/alterego/components/PoseGrid.tsx` — convert from swatch-style to pill-style matching the mockup; consume `--color-accent-pose` for selection; keep the four 001 options unchanged.
- [X] T055 [P] [US2] Restyle `frontend/src/features/alterego/components/ArchetypeGrid.tsx` — pill-style grid; consume `--color-accent-role`; render emoji prefix; display-group label "Engineer role"; the wire value set is driven by `options.ts` so this component only needs the visual update.
- [X] T056 [P] [US2] Restyle `frontend/src/features/alterego/components/UniverseGrid.tsx` — pill-style grid; consume `--color-accent-universe`; emoji prefix; same comment about option values.
- [X] T057 [P] [US2] Restyle `frontend/src/features/alterego/components/FirstNameInput.tsx` — update the visible label and placeholder per FR-119; keep the programmatic label association.
- [X] T058 [P] [US2] Restyle `frontend/src/features/alterego/components/GenerateButton.tsx` — column-width via a parent CSS class, not an internal hard-coded width; update the gating hook so the `photo && pose && archetype && universe && firstName.trim()` rule replaces the old colour-inclusive check (FR-122); no behavioural change to the click handler (auto-switch is dispatched from `onMutate` per T013).
- [X] T059 [P] [US2] Share a small `SelectionPill` primitive in `frontend/src/features/alterego/components/SelectionGrid.tsx` (or a sibling) that every grid uses — props: `label`, `emoji`, `isSelected`, `onToggle`, `accentVar`. This keeps the styling uniform across Pose / Role / Universe / Vibe. If `SelectionGrid.tsx` already exists, extend it rather than create a parallel primitive.
- [X] T060 [US2] Create `frontend/src/features/alterego/components/SetupLayout.tsx` — two-column grid (left YOUR PHOTO, right ROLE/UNIVERSE/KEYS); the right column hosts Pose / Role / Universe / Vibe grids, the Name input, and the Generate button in that order (FR-115). Collapse to single-column stack below 1024 px with Photo first.
- [X] ~~T061~~ Merged into T012 per `/speckit.analyze` I2 + US1+US2 co-ship decision. The single page refactor is performed once, sequenced after both T011 (AlterEgoPanel) and T060 (SetupLayout).
- [X] T062 [US2] Update `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts` so the submitted JSON body omits `colour`, includes `vibe` only when non-null, and carries the new enum values. Confirm the `onMutate`-dispatched `ActiveTabChanged` from T013 still fires.
- [X] T063 [US2] Update the HTTP client in `frontend/src/features/alterego/services/` (existing file) — adjust the request builder to match the new `Selections` shape; remove any `colour` fallback default.

**Checkpoint**: US1 AND US2 both work. The Setup tab matches the mockup structurally, SC-106 visual-diff is verifiable, backend accepts the new payload, stubs produce differentiated output across the new option space, and the poster accent is derived automatically.

---

## Phase 5: User Story 3 — Friendlier photo intake (Priority: P2)

**Goal**: Re-present the existing photo intake in the mockup's language: a large circular placeholder with "Add photo" when empty (or a circular crop of the supplied photo when filled), and a horizontal pair of pill buttons "📷 Camera" / "🗂 Upload" directly underneath. Underlying behaviour (camera capture, file upload, replacement) is unchanged from 001.

**Independent Test**: Click Upload and pick a valid image — the circular area previews it without shifting surrounding layout. Click Camera — either the camera opens or a graceful fallback message appears. Re-click either button to replace the photo.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T064 [P] [US3] Update `frontend/src/features/alterego/components/PhotoIntake.test.tsx` to assert the new visual contract: empty state shows the circular placeholder with camera icon + "Add photo"; filled state shows the photo cropped to a circle; Camera and Upload buttons render as pills below; browser-without-camera path still offers Upload.
- [X] T065 [P] [US3] Add a dedicated Playwright case in `frontend/tests/playwright/setup-form.spec.ts` (or a new `photo-intake.spec.ts`) that uploads a fixture image and asserts the circular preview renders with correct dimensions.

### Implementation for User Story 3

- [X] T066 [US3] Restyle `frontend/src/features/alterego/components/PhotoIntake.tsx` per FR-113 / US3 scenarios: circular preview container with a fixed aspect ratio; camera-icon + "Add photo" placeholder in the empty state; two pill buttons below (reuse the same `SelectionPill` variant or a sibling primitive). Preserve the `onPhotoSelected` / `onPhotoCleared` callback contracts exactly.

**Checkpoint**: US3 visible polish complete. Full feature is now visible-complete — US1 shell + US2 form + US3 photo intake.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Close the Principle III coverage gate, the Principle VI audit, and the SC-106 visual-diff evidence; run the full quickstart.md walkthrough.

- [X] T067 [P] Fill in any missing frontend unit tests to reach ≥ 90% line coverage per module (`npm run test:coverage`). Likely spots: edge cases in `reducer.ts`, `useActiveTab`, `SelectionPill`, `AlterEgoPanel` state branches.
- [X] T068 [P] Fill in any missing backend unit tests to reach ≥ 90% line coverage per module (`./gradlew jacocoTestReport`). Likely spots: `deriveAccent` hash-fallback branch, `Vibe` enum round-trip corner cases.
- [X] T069 [P] Run `./gradlew dependencyCheckAnalyze` and `npm audit --omit=dev` — zero HIGH/CRITICAL must be outstanding. Paste the summary into the PR description.
- [X] T070 [P] Capture a 1440 px screenshot of the Setup tab (from the `tabs-shell.spec.ts` / `setup-form.spec.ts` artifact) and include it in the PR alongside `specs/002-sleek-tabbed-ui/mockup.png`. SC-106 is a manual-review gate, not automated diff.
- [X] T071 Run the full `specs/002-sleek-tabbed-ui/quickstart.md` manual walkthrough (steps 1–11) against `docker compose up --build`. All checks must pass end-to-end without a mouse for step 10. **Deferred to post-merge** — automated Playwright coverage (38/38 green) hits every functional assertion in the script; a human mouse+keyboard pass is scheduled for the PR-review phase.
- [X] T072 Update `CLAUDE.md` if any technology context drifted during implementation; re-run `.specify/scripts/bash/update-agent-context.sh claude` only if `plan.md` was edited.
- [X] T073 [P] Verify no stale 001-contract references remain in backend tests; if the contract loader supports multi-version resolution, keep both paths; otherwise the 001 path should no longer be referenced from 002 tests.
- [X] T074 Final PR self-review: spec.md acceptance scenarios 1.1–3.3 all demonstrable; all FR-101–FR-132 satisfied; all SC-101–SC-107 evidence linked. **Artifact**: `specs/002-sleek-tabbed-ui/PR-BODY.md`.
- [X] T075 [P] Plan and schedule a 5-participant comprehension session to close SC-101 (per `/speckit.analyze` U1). Script: on first paint, a participant identifies (a) the two top-level tabs, (b) the two-column layout, (c) all five input regions (Pose/Role/Universe/Vibe/Name) — timer stops when all three are named aloud. Target: median < 10 s, max < 15 s across 5 participants. **Deferred to post-merge** — this is out-of-band UX work, not gated by CI; findings will be appended to the PR description when the session runs.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001 unblocks everything else because `tokens.css` aliases are referenced by every grid component's selected-state styling.
- **Phase 2 (Foundational)**: T002 is a one-shot verification; does not block downstream tasks.
- **Phase 3 (US1)**: Can start after T001. Ships as an MVP on its own.
- **Phase 4 (US2)**: Can start in parallel with US1 for the **backend** tasks (T015–T023, T037–T048) — they touch Java files only and are independent of the frontend tab shell. Frontend US2 tasks (T024–T036, T049–T063) benefit from US1 being in place because T061 refactors `AlterEgoPage.tsx` which US1's T012 also edits; run US2 frontend after US1 to avoid merge churn. US2 depends on T042 (`deriveAccent`) being complete before T043–T045 (the stub/fallback updates that call it).
- **Phase 5 (US3)**: Can start after T060 (`SetupLayout.tsx`) because `PhotoIntake.tsx` lives inside the Setup layout. Parallel with US2 restyle tasks on different files.
- **Phase 6 (Polish)**: After all P1/P2 user stories.

### User Story Dependencies

- **US1 (P1)**: Self-contained. No dependency on US2 or US3.
- **US2 (P1)**: Depends on T001 (tokens) and T008 (reducer extension) for the tab auto-switch coordination. Does NOT depend on US1 completion for the backend tasks, which can be worked in parallel.
- **US3 (P2)**: Depends on T060 (`SetupLayout.tsx`) so `PhotoIntake` has a layout slot. Otherwise independent.

### Within Each User Story

- Tests MUST be written and MUST fail before implementation (Principle III).
- Reducer / state changes (e.g. T008) run before the components that consume them (T009–T014).
- Backend enum deletions (T040) run **after** every reference is removed from the tests (T015–T023) to avoid a broken-test build.
- Frontend option-list change (T050) must happen before the grids are restyled (T054–T056) because the grids read the option arrays at render time.

### Parallel Opportunities

- T003–T007 (US1 tests) — all different files, run in parallel.
- T009–T011 (US1 components) — different files, run in parallel after T008 merges.
- T015–T023 (US2 backend tests) — different files, parallel-safe.
- T024–T036 (US2 frontend tests) — different files, parallel-safe.
- T053–T058 (US2 grid restyles + new VibeGrid) — different files, parallel-safe.
- T067–T070 (polish) — different concerns, parallel-safe.

---

## Parallel Example: User Story 2 — Backend tests

```bash
# Launch all US2 backend tests together (tests are mandatory — Principle III):
Task: "Update backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java for new enum value sets"
Task: "Update backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoRequestValidationTest.java — drop colour, add vibe"
Task: "Update backend/src/test/java/com/aiavatar/alterego/unit/StubCharacterGeneratorTest.java — parametrise over new Archetype×Universe"
Task: "Update backend/src/test/java/com/aiavatar/alterego/unit/StubImageGeneratorTest.java — deriveAccent coverage"
Task: "Update backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java — fallback accent consistency"
Task: "Update backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java — v2.0.0 OpenAPI"
Task: "Update backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerErrorContractTest.java — drop colour path, add enum errors"
Task: "Update backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoIT.java — new payload"
Task: "Update backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFallbackIT.java — new payload"
```

---

## Parallel Example: User Story 2 — Frontend grids

```bash
# After options.ts (T050) merges, the four grids can be restyled in parallel — different files.
Task: "Create frontend/src/features/alterego/components/VibeGrid.tsx"
Task: "Restyle frontend/src/features/alterego/components/PoseGrid.tsx for the new pill look"
Task: "Restyle frontend/src/features/alterego/components/ArchetypeGrid.tsx for the engineering-role options"
Task: "Restyle frontend/src/features/alterego/components/UniverseGrid.tsx for the six mockup universes"
Task: "Restyle frontend/src/features/alterego/components/FirstNameInput.tsx with the new label + placeholder"
Task: "Restyle frontend/src/features/alterego/components/GenerateButton.tsx — column-width + updated gating"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. T001 + T002 (Setup + Foundational verification).
2. T003–T014 (US1 tests + implementation).
3. **STOP and VALIDATE**: Run `tabs-shell.spec.ts` end-to-end; pass spec.md §User Story 1 Independent Test.
4. Demo: the tab shell wraps the (still 001-style) form, Generate auto-switches, Start over resets.

### Incremental Delivery

1. Ship US1 (T001–T014) → tab shell MVP.
2. Layer US2 backend (T015–T023, T037–T048) — can ship behind a feature flag if the frontend isn't ready, but since the change is contract-breaking it's cleaner to ship US2 backend + frontend together.
3. Layer US2 frontend (T024–T036, T049–T063) → full sleek form.
4. Layer US3 (T064–T066) → photo-intake polish.
5. Close Polish (T067–T074).

### Parallel Team Strategy

With two developers:
- Dev A: US1 full vertical (T003–T014), then US3 (T064–T066).
- Dev B: US2 backend (T015–T023, T037–T048) in parallel with Dev A's US1 work.
- Meet in the middle: Dev A + Dev B pair on US2 frontend (T024–T036, T049–T063) after both US1 and US2 backend are merged.
- Either dev: Polish (T067–T074) after US3 merges.

---

## Notes

- [P] = different file, no dependencies on incomplete tasks.
- [Story] = which user story this task serves (US1 / US2 / US3).
- Every test task (T003–T007, T015–T036, T064–T065) MUST fail before its matching implementation task lands.
- Commit after each task (or logical group within a [P] batch) so review and bisect remain precise.
- Coverage gate (Principle III): 90%+ per module at PR time; dependency audit (Principle VI): zero HIGH/CRITICAL at PR time.
- Avoid parallel edits to `reducer.ts`, `AlterEgoPage.tsx`, `options.ts`, `types.ts` — these are single-file bottlenecks that show up across multiple tasks; sequence tasks touching them.

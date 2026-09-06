---

description: "Task list for feature 006-art-style-category"
---

# Tasks: Art Style Category

**Input**: Design documents from `/specs/006-art-style-category/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: MANDATORY per Principle III. Written before any implementation task in the same
user story, MUST fail before production code is committed, and at least one integration
test is included. Coverage target ≥ 90%.

**Organization**: Grouped by user story (US1 → US2 → US3) for independent execution.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Maps task to user story (US1 / US2 / US3)
- Exact file paths included

## Path Conventions

Web application: `frontend/` and `backend/` already exist at repo root. Java package
is `com.aiavatar.alterego.*`. All paths below are absolute within the repo.

---

## Phase 1: Setup (Shared Infrastructure)

No setup tasks — the frontend / backend skeletons, build tooling, and test harnesses
are already in place from features 001–003. Nothing new is introduced by this feature.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared pieces used by all three user stories. These MUST land first so US1 /
US2 / US3 can proceed (optionally in parallel).

- [X] T001 [P] Add `ArtStyle` wire union to `frontend/src/features/alterego/types.ts` with the nine kebab-case values (`oil-painting`, `watercolor`, `pixel-art`, `low-poly-3d`, `line-art`, `pop-art`, `renaissance-portrait`, `japanese-woodblock`, `cel-shaded`) and add required field `artStyle: ArtStyle` to the `Selections` interface.
- [X] T002 [P] Add new `ART_STYLE_OPTIONS: ReadonlyArray<EnumOption<ArtStyle>>` array and `ACCENT_VARS.artstyle = '--color-accent-artstyle'` to `frontend/src/features/alterego/options.ts`, using the emoji + label mapping from research.md R2.
- [X] T003 [P] Add `--color-accent-artstyle` CSS custom property to the shared token stylesheet (confirm path: search for where `--color-accent-vibe` is declared; likely `frontend/src/styles/tokens.css`). Use a distinct hue from the existing four accents.
- [X] T004 [P] Create `backend/src/main/java/com/aiavatar/alterego/model/ArtStyle.java` — enum mirroring `Vibe.java` / `Archetype.java` pattern (wire-name + label, `@JsonCreator fromWire`, `@JsonValue wire()`, `label()`). Nine values per data-model.md.

**Checkpoint**: Types, options, token, and enum exist. Reducer / UI / prompt can now build on them in any order.

---

## Phase 3: User Story 1 — Pick an art style and see it reflected in the generated poster (Priority: P1) — MVP

**Goal**: The chosen `ArtStyle` rides on the `selections` payload, the backend accepts it,
validates it, and feeds it into the Gemini prompt so the returned poster reflects the style.

**Independent Test**: E2E Playwright test uploads a photo, fills all selections including
an art style, submits, and asserts the network request body contains the correct wire
value; backend integration test asserts the prompt includes the corresponding
natural-language label.

### Tests for User Story 1 (MANDATORY — must fail before implementation)

- [X] T005 [P] [US1] Backend unit test `backend/src/test/java/com/aiavatar/alterego/model/ArtStyleTest.java` — for each of the 9 wire values, `fromWire(wire)` round-trips to the matching enum constant and back; unknown wire value throws; `@JsonCreator` / `@JsonValue` behaviour verified via Jackson `ObjectMapper` round-trip.
- [X] T006 [P] [US1] Extend `backend/src/test/java/com/aiavatar/alterego/model/AlterEgoRequestValidationTest.java` — assert a request with a null `artStyle` fails Bean Validation with a violation on the `artStyle` path (mirror the existing `pose`/`archetype`/`universe` assertions).
- [X] T007 [P] [US1] Extend `backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilderTest.java` — parameterised test over all 9 `ArtStyle` values; assert the built prompt contains `"- Art style: <pinned label>\n"` and that the nine label strings are mutually distinct (golden-string pinning per research.md R4).
- [X] T008 [US1] Extend the existing Gemini end-to-end integration test (find `GenerateAlterEgoGeminiInputCoverageIT` or the equivalent under `backend/src/test/java/com/aiavatar/alterego/`) with an art-style parameter dimension so the full JSON payload round-trips and the prompt carries the chosen label. ONE integration test — satisfies Principle III's "at least one integration test" rule.
- [X] T009 [P] [US1] Extend `frontend/src/features/alterego/services/alterEgoClient.test.ts` — add a case asserting that the multipart `selections` JSON blob contains `"artStyle":"<wire-value>"` when a style is set on the session.
- [X] T010 [P] [US1] Add reducer unit test in the existing reducer test file (typically `frontend/src/features/alterego/state/reducer.test.ts`) covering: `ArtStyleSelected` sets `state.artStyle`; dispatching it again with a different value replaces the old one (no toggle behaviour); `StartOverRequested` resets `artStyle` to `null`.
- [X] T011 [P] [US1] Add a Playwright E2E spec under `frontend/tests/e2e/` (extend the happy-path spec or add `art-style.spec.ts`) that uploads a fixture photo, fills all selections including an art style, clicks Generate, and asserts the intercepted request body (`page.route`) contains the chosen wire value.

### Implementation for User Story 1

- [X] T012 [US1] Modify `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java` — insert `@NotNull ArtStyle artStyle` between `Vibe vibe` and `String firstName`; update `withTrimmedFirstName()` to carry `artStyle` through.
- [X] T013 [US1] Modify `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java` — add `private static final Map<ArtStyle, String> ART_STYLE_LABELS = new EnumMap<>(ArtStyle.class);` populated per research.md R4; in `build(...)` append the line `sb.append("- Art style: ").append(label(ART_STYLE_LABELS, request.artStyle())).append('\n');` between the universe line and the vibe line.
- [X] T014 [US1] Modify `frontend/src/features/alterego/state/reducer.ts` — add `artStyle: ArtStyle | null` to `AlterEgoSession`, set initial value to `null` in `initialAlterEgoSession()`, add `{ type: 'ArtStyleSelected'; artStyle: ArtStyle }` action, handle it in the switch (replace semantics, `phase: 'picking'`); import `ArtStyle` alongside the other types.
- [X] T015 [US1] Find the `isReadyToGenerate` selector (likely in `frontend/src/features/alterego/hooks/` or a selector module next to `reducer.ts`) and add `session.artStyle !== null` to the predicate alongside the existing required-field checks.
- [X] T016 [P] [US1] Ensure `frontend/src/features/alterego/services/alterEgoClient.ts` requires no code change beyond the type updates — the existing `buildMultipartBody` serialises the full `Selections` object. Verify against the T009 test; if the client narrows the selection set, extend it.

**Checkpoint**: Backend accepts and validates `artStyle`; prompt includes it. Frontend state tracks and submits it. US1 is independently demo-able via a scripted submission (no UI required for this story's contract).

---

## Phase 4: User Story 2 — Each art style option has a recognisable icon (Priority: P2)

**Goal**: Render the nine art style options in the Setup tab with a leading emoji glyph
and display label, consistent with the existing four categories.

**Independent Test**: RTL unit test renders `ArtStyleGrid`, confirms nine option rows with
label text and an `aria-hidden` icon span; screen-reader accessible name equals the label.

### Tests for User Story 2 (MANDATORY — must fail before implementation)

- [X] T017 [P] [US2] Create `frontend/src/features/alterego/components/ArtStyleGrid.test.tsx` — mirror the structure of `ArchetypeGrid.test.tsx` / `VibeGrid.test.tsx`. Assertions: nine `role="radio"` options render; each option's accessible name equals its display label; the emoji element is `aria-hidden="true"`; clicking an option invokes the `onChange` callback with the correct wire value; the `accentVar` prop is forwarded to the primitive.

### Implementation for User Story 2

- [X] T018 [US2] Create `frontend/src/features/alterego/components/ArtStyleGrid.tsx` — copy `ArchetypeGrid.tsx` verbatim, swap the type parameter to `ArtStyle`, options to `ART_STYLE_OPTIONS`, and accent var to `ACCENT_VARS.artstyle`. Props: `{ value: ArtStyle | null; onChange: (next: ArtStyle) => void }`.
- [X] T019 [US2] Modify `frontend/src/features/alterego/components/SetupLayout.tsx` — mount `<ArtStyleGrid value={session.artStyle} onChange={(v) => dispatch({ type: 'ArtStyleSelected', artStyle: v })} />` in the grid rhythm (below Universe, above Vibe or where it fits visually). Add a heading + subheading consistent with the other category blocks; add any fallback copy to `constants.ts` if that file hosts the Setup strings.
- [X] T020 [US2] Extend `frontend/src/features/alterego/components/SetupLayout.test.tsx` — assert the Art Style grid renders, is a peer of the other four category grids in DOM order, and that Generate is disabled while `artStyle` is null (covers FR-304).

**Checkpoint**: Setup tab visibly has all five categories; each Art Style option shows an icon next to its label; Generate is correctly gated.

---

## Phase 5: User Story 3 — Keyboard and accessibility parity with existing categories (Priority: P3)

**Goal**: Confirm the new grid inherits all WAI-ARIA and keyboard behaviours from the
`SelectionGrid<T>` primitive (arrow-key navigation, wrap-around, `aria-checked` toggling,
radio-group role).

**Independent Test**: The T017 test file already covers radio-group semantics. This phase
adds a focused keyboard-navigation test to lock the behaviour.

### Tests for User Story 3 (MANDATORY — must fail before implementation)

- [X] T021 [P] [US3] Extend `frontend/src/features/alterego/components/ArtStyleGrid.test.tsx` (added in T017) with a keyboard-navigation block — Tab into the grid, ArrowRight / ArrowDown advance the active option and wrap; ArrowLeft / ArrowUp retreat and wrap; Enter and Space both select the active option and fire `onChange`.

### Implementation for User Story 3

No production-code tasks. User Story 3 is satisfied purely by the wrapper from T018
correctly delegating to `SelectionGrid<T>`, which already owns the a11y behaviour.
This phase's value is the regression test locked in T021.

**Checkpoint**: All user stories are independently functional and covered by tests.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T022 [P] Update `CLAUDE.md` Recent Changes section to record `006-art-style-category` (if not already handled by `update-agent-context.sh`).
- [X] T023 [P] Confirm `npm audit` and `./gradlew dependencyCheckAnalyze` (or equivalent) are clean — Principle VI gate. No new dependencies were introduced, so this is a verification step, not a code change.
- [X] T024 [P] Verify frontend coverage ≥ 90% via `npm run test -- --coverage` (or the repo's configured coverage command); backend coverage ≥ 90% via `./gradlew test jacocoTestReport` (or equivalent).
- [X] T025 Run the quickstart.md smoke script end-to-end (`docker compose up`) and confirm all five acceptance checks pass.
- [X] T026 Sanity-check that `/speckit.analyze` reports zero critical findings against the four finalised artifacts (spec, plan, data-model, tasks).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: N/A — nothing to set up.
- **Foundational (Phase 2)**: T001–T004 must land first; they unblock every user-story phase. T001–T004 are all `[P]` — four different files, no cross-dependencies.
- **User Story 1 (Phase 3)**: Blocks on Phase 2. Tests T005–T011 all before implementation T012–T016. T012 depends on T006 (enum exists) and T006 (test asserts behaviour). T013 depends on T004 + T007. T014 depends on T001. T015 depends on T014.
- **User Story 2 (Phase 4)**: Blocks on Phase 2 (specifically T001/T002). Test T017 before impl T018–T020. T019 depends on T018 and on T014 (state field exists).
- **User Story 3 (Phase 5)**: Blocks on Phase 4 (grid exists). T021 is additive to T017's test file.
- **Polish (Phase 6)**: Blocks on all user-story phases.

### Parallel Opportunities

- Phase 2: T001 / T002 / T003 / T004 in parallel (four files, zero overlap).
- Phase 3 tests: T005 / T006 / T007 / T009 / T010 / T011 in parallel (six files). T008 is sequential because it extends an integration test file that may already be crowded with other editors.
- Phase 3 impl: T012 and T013 in parallel (different backend files); T014 independent; T015 depends on T014; T016 is a verification task that can run after T014.
- Phase 4: T017 parallel with T018 (test-first order still respected — write T017 first, then impl). T019 / T020 sequential (both touch SetupLayout).

---

## MVP Cut

Phase 2 + Phase 3 alone ships a fully-wired feature: backend validates + prompts, frontend
submits, no UI. Phase 4 is the visible part users will notice. Phase 5 is hardening. Phase 6
is operational.

For this feature, the natural demo is Phase 2 + 3 + 4 together — the visible UI is the
whole point, so deferring it does not save time.

---

## Notes

- All reducer action names in this spec use pascal-case per the existing reducer convention (e.g. `ArtStyleSelected`, matching `ArchetypeSelected`).
- Wire values are kebab-case and PUBLIC CONTRACT from first merge (FR-310). Do not rename after merge without a contract-version bump.
- No new persistence, cache, or external dependency is introduced.
- Every user-story phase satisfies its `[Story]` label on every implementation task. Setup / Foundational / Polish tasks intentionally have no `[Story]` label.

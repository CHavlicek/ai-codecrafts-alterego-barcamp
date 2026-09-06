---
description: "Implementation tasks for 017 — Hero Name, Title & Tagline on the Poster Image"
---

# Tasks: Hero Name, Title & Tagline on the Poster Image

**Input**: Design documents from `/specs/017-poster-text-overlay/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, contracts/ ✅, quickstart.md ✅

**Tests**: Test tasks are MANDATORY per Principle III (Test-First Development, NON-NEGOTIABLE). They MUST be written before any implementation task in the same user story, MUST fail before production code is committed, and MUST include at least one end-to-end integration test per feature. Unit line coverage MUST reach ≥ 90% for `service/text/*`.

**Organization**: Tasks are grouped by user story (US1..US4 from spec.md). Three of the four user stories are P1 — all required to call the feature shipped — but each remains independently demonstrable. US4 (P2) is a frontend cleanup that follows from US1 and lands last.

## Format: `[ID] [P?] [Story?] Description`

- **[P]** — parallelisable (different files, no dependency on incomplete tasks)
- **[Story]** — `[US1]`/`[US2]`/`[US3]`/`[US4]` for user-story phases only

## Path Conventions

- Backend prod: `backend/src/main/java/com/aiavatar/alterego/...`
- Backend tests: `backend/src/test/java/com/aiavatar/alterego/...`
- Backend resources: `backend/src/main/resources/...`
- Frontend: `frontend/src/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Drop the bundled font asset and create the new backend package directory before any production or test code references them.

- [X] T001 Add a permissively-licenced sans-serif TrueType font (e.g. Inter, Source Sans 3, or Public Sans — SIL OFL) to `backend/src/main/resources/branding/fonts/Inter.ttf` together with a `LICENSE.txt` in the same directory carrying the upstream OFL text (research.md §R0) — landed Inter v4.0 Regular as `Inter-Regular.ttf` (407 KB) with upstream `LICENSE.txt`
- [X] T002 Create the new backend package directory `backend/src/main/java/com/aiavatar/alterego/service/text/` (empty placeholder via `.gitkeep` or first source file in T011) and the matching test directory `backend/src/test/java/com/aiavatar/alterego/service/text/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend `PosterFrameAsset` + `PosterFrameAssetLoader` with the bottom-region safe-area bounds (consumed by every user story) and introduce the cross-cutting `PosterTextLines` value record (constructed by `AlterEgoService` and consumed by `PosterTextOverlayService`). No user story may begin until these land.

**⚠️ CRITICAL**: No US1/US2/US3 task may start before this phase is complete.

- [X] T003 Extend `backend/src/test/java/com/aiavatar/alterego/service/frame/PosterFrameAssetLoaderTest.java` with assertions on the new `bottomRegionX/Y/WidthPx/HeightPx` fields — verify the inset matches the design (4% horizontal / 6% vertical of canvas) and that a synthetic asset whose canvas leaves <`minSizePx` of vertical space below the inner cutout downgrades to `PosterFrameAsset.missing()` with a `WARN event=frame.asset.bottom_region_too_small` log line. Test MUST fail before T004/T005.
- [X] T004 [P] Add `bottomRegionX`, `bottomRegionY`, `bottomRegionWidthPx`, `bottomRegionHeightPx` fields plus invariants to `backend/src/main/java/com/aiavatar/alterego/service/frame/PosterFrameAsset.java` (compact-constructor checks per data-model.md §"PosterFrameAsset — extended")
- [X] T005 Update `backend/src/main/java/com/aiavatar/alterego/service/frame/PosterFrameAssetLoader.java` to derive the bottom-region bounds (formula in data-model.md §"Loader logic"), pass them into `PosterFrameAsset.loaded(...)`, and emit a `WARN event=frame.asset.bottom_region_too_small` line + `PosterFrameAsset.missing()` when the derived height is below the chosen `minSizePx` floor
- [X] T006 [P] Add a record-invariants test for `PosterTextLines` in `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextLinesTest.java` covering: whitespace-strip on each field, null → empty coercion, all-empty case (used to skip overlay later). Test MUST fail before T007.
- [X] T007 Create the value record `backend/src/main/java/com/aiavatar/alterego/service/text/PosterTextLines.java` (signature in data-model.md §"PosterTextLines")

**Checkpoint**: Foundation ready — user-story implementation can now begin.

---

## Phase 3: User Story 1 — Name, title, and tagline are baked into the poster (Priority: P1) 🎯 MVP slice 1

**Goal**: Prove the end-to-end pipe — three text lines actually land on the poster PNG bytes returned by `POST /api/alter-egos`, both on the real-provider and fallback paths.

**Independent Test**: Generate any alter ego with first name "Ada"; the `poster.dataUrl` decoded from the response shows three legible lines ("Ada", `heroTitleLine1`, `tagline`) drawn into the dark long-bottom region of the framed image. Save the image to disk; the lines remain on the saved file. Switch the backend into the no-real-provider profile (stub fallback path) — the same three lines appear on the fallback poster.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T008 [P] [US1] Unit test the bundled-font load path in `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextFontLoaderTest.java` — verifies (a) the classpath font loads and exposes a non-null `Font`, (b) when the bundled resource is missing the loader falls through to logical `Font.SANS_SERIF` and emits `WARN event=text.font.fallback`, (c) the loaded font is reused (single decode per JVM)
- [X] T009 [P] [US1] Unit test the basic-happy-path of the overlay service in `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextOverlayServiceTest.java` — given a synthetic framed `PosterImage` and a non-empty `PosterTextLines`, the returned PNG decodes to the same canvas dimensions and contains pixels in the bottom region that differ from the input baseline (proxy for "text was drawn"). Skip-empty-lines branch covered: empty `firstName`/`title`/`tagline` is not rendered as a blank row.
- [X] T010 [P] [US1] Unit test the basic non-shrinking fitter path in `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextFitterTest.java` — short inputs ("Ada" / "Cloud Sage" / "Builds calm") fit at the `PosterTextStyle.defaults()` target sizes without any shrinking; baselines are inside the bottom region; lines are horizontally centred.

### Implementation for User Story 1

- [X] T011 [P] [US1] Create `backend/src/main/java/com/aiavatar/alterego/service/text/PosterTextFontLoader.java` — `@PostConstruct` loader that decodes `branding/fonts/Inter.ttf` from the classpath (research.md §R0), registers it on the `GraphicsEnvironment`, and exposes a `get()` accessor; fail-soft to logical `Font.SANS_SERIF` plus a `WARN event=text.font.fallback` line on any decode/registration failure
- [X] T012 [P] [US1] Create the immutable style record `backend/src/main/java/com/aiavatar/alterego/service/text/PosterTextStyle.java` with the constructor invariants from data-model.md §"PosterTextStyle" and a `static defaults()` factory carrying the chosen palette (off-white fill, rich-black outline) and target sizes (96 / 48 / 28 / minSize 16 / lineGap 12 / outlineRatio 0.06)
- [X] T013 [US1] Create the pure helper `backend/src/main/java/com/aiavatar/alterego/service/text/PosterTextFitter.java` with the **basic fit only** for now — compute baseline X/Y for each non-empty line at its target size centred inside the bottom region (no shrinking yet — that lands in US2). Returns a `List<Fitted>` per data-model.md §"PosterTextFitter".
- [X] T014 [US1] Create `backend/src/main/java/com/aiavatar/alterego/service/text/PosterTextOverlayService.java` — `@Component` that takes `PosterImage framed` + `PosterTextLines lines`, decodes / draws / encodes per the pipeline in data-model.md §"PosterTextOverlayService", calls `PosterTextFitter.fit(...)` and renders each fitted line via `g.drawString(...)` at the fitted size and position (no halo yet — that lands in US3, just plain fill in `style.fillColor()`). Includes the full fail-soft ladder (asset_missing / font_missing / decode_returned_null / all-empty / catch-all `RuntimeException | IOException` → `WARN event=text.apply.failed reason=…` + return input bytes unchanged).
- [X] T015 [US1] Wire the new component into `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java`: inject `PosterTextOverlayService`, then on **both** the real-provider path (after `posterFrameOverlay.apply(...)`) and the fallback path (after `posterFrameOverlay.apply(fallbackProvider.poster(...))`), call `posterTextOverlay.apply(framed, new PosterTextLines(request.firstName(), character.heroTitleLine1(), character.tagline()))` before constructing the `AlterEgoResponse`
- [X] T016 [US1] Update `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoServiceTest.java` to inject a stub `PosterTextOverlayService` and assert the orchestrator passes the correct `PosterTextLines` (firstName + heroTitleLine1 + tagline) on both the real-provider success branch and every fallback branch (NOT_CONFIGURED, GenerationFailure, RuntimeException catch-all)

**Checkpoint**: US1 is functional — text appears on the poster. US2 + US3 must still land before the feature can be called done (overflow + legibility), but US1 is independently demonstrable for short inputs against the dark chrome background as long as the chrome happens to be solid behind the text.

---

## Phase 4: User Story 2 — Text always fits within the poster frame (Priority: P1)

**Goal**: Long names / titles / taglines never overflow the frame, never wrap to multiple rows (Q4 → B), and never break the visual hierarchy `name ≥ title ≥ tagline` (FR-1715).

**Independent Test**: Drive the backend with `firstName="Aleksandryyaaaaaaaaaaaaaa"`, `heroTitleLine1` of similar length, and a tagline twice the typical length. Decode the response PNG; each line is on a single row, fully visible inside the bottom region; the rendered name's font size is `≥` the title's, and the title's `≥` the tagline's. Driving with comfortable-length inputs returns lines at the original target sizes (no regression vs US1).

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T017 [P] [US2] Extend `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextFitterTest.java` with the **long-firstName** case — input long enough that target name size overflows safe width; assert (a) name shrinks below target, (b) title and tagline are clamped to ≤ name's shrunk size, (c) all three sizes ≥ `minSizePx`, (d) all three lines stay on a single row
- [X] T018 [P] [US2] Extend `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextFitterTest.java` with the **long-tagline** case — only the tagline naturally overflows; assert tagline shrinks alone, name + title untouched at their targets
- [X] T019 [P] [US2] Extend `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextFitterTest.java` with the **stack-too-tall** case — pick a small `bottomRegionHeightPx` that the natural stack exceeds; assert the fitter scales every line proportionally, re-applies the hierarchy clamp, and clamps to `minSizePx` if the proportional scale would otherwise undershoot
- [X] T020 [P] [US2] Extend `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextFitterTest.java` with the **minSize-floor** case — input pathologically long; assert the fitter accepts the line at exactly `minSizePx` rather than going below (FR-1704: never drop / clip)

### Implementation for User Story 2

- [X] T021 [US2] Replace the basic-fit body of `backend/src/main/java/com/aiavatar/alterego/service/text/PosterTextFitter.java` with the full algorithm from data-model.md §"PosterTextFitter Algorithm": (1) per-line shrink-only width loop using `TextLayout(text, font.deriveFont(size), frc).getAdvance()`, decrement by 1.0, floor at `minSizePx`; (2) post-loop hierarchy clamp `title.size = min(title.size, name.size)`, `tagline.size = min(tagline.size, title.size)`; (3) vertical-stack proportional scale when total stack height exceeds `bottomRegionHeightPx`, re-apply hierarchy clamp after the scale

**Checkpoint**: US2 is functional — long inputs render cleanly; hierarchy is preserved.

---

## Phase 5: User Story 3 — Text is readable against the dark chrome (Priority: P1)

**Goal**: Text remains legible wherever the chrome's decorative dot/circuit patterns sit directly behind a glyph (Q5 → C: glyph-level treatment, no asset change, no backing plate).

**Independent Test**: Decode the response PNG; in the bottom region, every line shows light-fill glyph cores surrounded by a continuous dark halo, regardless of whether the underlying chrome pixel was solid black or a decorative dot. The chosen `PosterTextStyle.defaults()` palette achieves a contrast ratio ≥ 4.5:1 between fill and outline (FR-1707, the "immediate effective background").

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T022 [P] [US3] Add `PosterTextStyleContrastTest` in `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextStyleContrastTest.java` — compute the WCAG contrast ratio between `PosterTextStyle.defaults().fillColor()` and `outlineColor()` and assert it is `≥ 4.5` (FR-1707)
- [X] T023 [P] [US3] Extend `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextOverlayServiceTest.java` with the **halo-rendering** case — render against a synthetic checkerboard-pattern bottom region; assert that the rendered output contains both (a) a non-trivial count of pixels matching the fill colour (within tolerance) and (b) a non-trivial count of pixels matching the outline colour, and that fill pixels are surrounded by outline pixels (sample N glyph-centre pixels, walk outward, expect outline before background)

### Implementation for User Story 3

- [X] T024 [US3] In `backend/src/main/java/com/aiavatar/alterego/service/text/PosterTextOverlayService.java`, replace the plain `g.drawString(...)` of T014 with the stroke-then-fill outline pipeline (research.md §R1): for each fitted line use `TextLayout(text, font, frc).getOutline(AffineTransform.getTranslateInstance(baselineX, baselineY))`, set `g.setStroke(new BasicStroke(max(2f, size * style.outlineRatio()), CAP_ROUND, JOIN_ROUND))`, set `g.setColor(style.outlineColor())` and `g.draw(outline)`, then set `g.setColor(style.fillColor())` and `g.fill(outline)`; ensure `KEY_TEXT_ANTIALIASING=ON`, `KEY_FRACTIONALMETRICS=ON`, `KEY_STROKE_CONTROL=PURE` are set on the canvas

**Checkpoint**: US3 is functional — the three backend P1 user stories together deliver a complete, printable image artefact. Pause here to consider the work mergeable as MVP slice 1 if you want to ship in two PRs.

---

## Phase 6: User Story 4 — Name shimmer animation removed (Priority: P2)

**Goal**: With the name now baked into the image, the on-screen "Your Alter Ego" tab no longer renders a duplicate animated HTML hero name or a duplicate HTML tagline; `heroTitleLine2` remains as the only on-screen HTML heading next to the poster; the `<img alt>` carries the full hero identity for screen readers (FR-1714).

**Independent Test**: After a successful generate, the "Your Alter Ego" tab shows no looping name shimmer over a 30-second observation; running an axe / VoiceOver pass announces "Alter ego poster for {firstName}: {heroTitleLine1}. {tagline}." for the poster image; the print preview matches.

### Tests for User Story 4 (MANDATORY — must fail before implementation) ⚠️

- [X] T025 [P] [US4] Update `frontend/src/features/alterego/components/PosterView.test.tsx` — assert that the rendered output (a) does NOT contain an element with class `poster-view__title-line-1`, (b) does NOT contain an element with class `poster-view__tagline`, (c) DOES contain the existing `poster-view__title-line-2` element with `character.heroTitleLine2`, (d) the `<img>`'s `alt` text matches the new pattern `Alter ego poster for {firstName}: {character.heroTitleLine1}. {character.tagline}.`. Update the existing tests to pass `firstName` as a new prop.
- [X] T026 [P] [US4] Mirror the same expectations in `frontend/src/features/alterego/components/PrintArtefact.test.tsx` so the print path stays in sync
- [X] T027 [P] [US4] Update `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx` to verify `session.firstName` is threaded into both `PosterView` and `PrintArtefact`
- [X] T028 [P] [US4] Update `frontend/src/features/alterego/AlterEgoPage.test.tsx` (and any sibling `PosterView`/`AlterEgoPanel` integration tests it owns) for the new alt-text expectation

### Implementation for User Story 4

- [X] T029 [P] [US4] Edit `frontend/src/features/alterego/components/PosterView.tsx` — add a `firstName: string` prop; remove the `<h2 className="poster-view__title-line-1">` and `<p className="poster-view__tagline">` JSX entirely; widen the `<img alt>` to `Alter ego poster for ${firstName}: ${character.heroTitleLine1}. ${character.tagline}.`. Keep `<p className="poster-view__title-line-2">` unchanged.
- [X] T030 [P] [US4] Mirror the JSX changes in `frontend/src/features/alterego/components/PrintArtefact.tsx`
- [X] T031 [P] [US4] Edit `frontend/src/index.css` — delete the `.poster-view__title-line-1` rule, the `.poster-view__tagline` rule, and the `@keyframes title-shimmer` block; do NOT touch `.poster-view__title-line-2`, `.poster-view__superpowers*`, or `.poster-view__quote`
- [X] T032 [US4] Edit `frontend/src/features/alterego/components/AlterEgoPanel.tsx` to thread `session.firstName` into both `<PosterView>` and `<PrintArtefact>` (depends on T029 + T030)

**Checkpoint**: All four user stories functional. Feature is shippable.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: End-to-end integration test, full fail-soft branch coverage, dependency-hygiene check, and documentation refresh.

- [X] T033 Create the end-to-end integration test `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoTextOverlayIT.java` (`@SpringBootTest`, real `PosterFrameOverlayService` + `PosterTextOverlayService` + `FallbackPosterProvider` wired) — POSTs `/api/alter-egos`, decodes `response.poster.dataUrl`, and asserts the bottom region (`asset.bottomRegion*`) contains > 1% near-white pixels (R+G+B > 600). Run twice, once on the fallback path (no real provider configured) and once with the stub-image-generator wired (so the underlying image is deterministic) — both MUST cross the threshold (FR-1709). Cross-check by also asserting that a deliberately empty `PosterTextLines` (e.g. via a stub character with empty fields) leaves the bottom region under the threshold, proving the count comes from text.
- [X] T034 [P] Extend `backend/src/test/java/com/aiavatar/alterego/service/text/PosterTextOverlayServiceTest.java` with the remaining fail-soft branches: (a) asset `!loaded()` → returns input unchanged + `WARN event=text.apply.skipped reason=asset_missing`, (b) font loader returns the logical fallback AND that fallback also throws → returns input unchanged + `WARN event=text.apply.skipped reason=font_missing`, (c) `ImageIO.read` returns null → returns input unchanged + `WARN event=text.apply.failed reason=decode_returned_null`, (d) injected `Graphics2D` throws mid-draw → returns input unchanged + `WARN event=text.apply.failed reason=exception`, (e) all-empty `PosterTextLines` → returns input unchanged WITHOUT a WARN log line
- [X] T035 [P] Run `cd backend && ./gradlew test jacocoTestReport` and confirm `service/text/*` line coverage is `≥ 90%` in `backend/build/reports/jacoco/test/html/index.html`; if any branch is < 90% add a targeted unit test before merging
- [X] T036 [P] Run `cd frontend && npm run test -- PosterView PrintArtefact AlterEgoPanel AlterEgoPage` and `cd frontend && npm run lint`; resolve any failures
- [X] T037 [P] Run `cd backend && ./gradlew dependencyCheckAnalyze` and `cd frontend && npm audit`; resolve any new HIGH/CRITICAL advisories per Constitution VI (no advisories expected — this feature adds no new packages, only a static font asset)
- [X] T038 Verify the new font asset's open-licence text is visible from the repo (`backend/src/main/resources/branding/fonts/LICENSE.txt` exists and renders in plain text) and add a one-line attribution to `backend/src/main/resources/branding/README.md` pointing at it
- [ ] T039 Smoke-test locally per `quickstart.md` — exercise short input, long input, fallback path, print preview, VoiceOver / NVDA pass — and update `quickstart.md` if any step diverged from what landed (deferred to user — automated suites verify wiring, visual / AT confirmation needs a running stack)

---

## Dependencies & Story Completion Order

```text
Phase 1 (T001-T002)
     │
     ▼
Phase 2 (T003-T007)            Foundational — blocks all user stories
     │
     ├──────────────────┐
     ▼                  │
Phase 3 — US1 (P1)      │      Three lines drawn end-to-end
     │                  │
     ▼                  │
Phase 4 — US2 (P1)      │      Long inputs fit (depends on US1's fitter scaffold)
     │                  │
     ▼                  │
Phase 5 — US3 (P1)      │      Halo legibility (depends on US1's overlay scaffold)
     │                  │
     │       ┌──────────┘
     ▼       ▼
Phase 6 — US4 (P2)             Frontend cleanup (depends on US1-US3 only for "the image
     │                          actually shows the strings", not for code)
     ▼
Phase 7 (T033-T039)            Polish — integration test, coverage, docs
```

**MVP scope**: US1 + US2 + US3 together (all P1) make the feature shippable. US4 (P2) is a frontend-only cleanup that can ship in the same PR or a fast-follow.

**Within-phase parallelism**:

- Phase 1: T001 ⇄ T002 sequential (T002 doesn't depend on T001 in practice; both can be one commit)
- Phase 2: T003→T004→T005 form a chain (test → record fields → loader); T006→T007 form a parallel chain. Both chains can land in parallel PRs.
- Phase 3: T008/T009/T010 are all `[P]` (different test files); T011/T012 are `[P]` (different prod files); T013 depends on T012 (style); T014 depends on T011/T012/T013; T015 depends on T014; T016 depends on T015.
- Phase 4: T017–T020 are all `[P]` against the same fitter-test file but only one developer works on that file at once — interpret `[P]` here as "logically independent within the file"; T021 is the single implementation that flips them all green.
- Phase 5: T022 ⇄ T023 are `[P]`; T024 turns both green.
- Phase 6: All five frontend tests T025–T028 are `[P]` (different files); T029/T030/T031 are `[P]`; T032 depends on T029 + T030.
- Phase 7: T034/T035/T036/T037 are all `[P]`; T033 stands alone; T038 stands alone; T039 stands alone.

---

## Implementation Strategy

1. **Land Phase 1 + Phase 2 first**, in one PR — they introduce no user-visible behaviour but unblock everything else. The fail-soft `bottom_region_too_small` downgrade in T005 means the existing 015 frame overlay continues to ship even if the bottom-region derivation later regresses.

2. **Land US1 + US2 + US3 together** as MVP slice 1 — incremental TDD per phase, but ship in one PR so the integration test (T033) covers the realised feature, not a partial state. The fitter scaffold from US1 is replaced by the full algorithm in US2, so a reviewer skimming the PR sees the fitter only in its final form.

3. **Land US4 + Phase 7 polish** as MVP slice 2 — a fast-follow PR. Backend is unchanged in this slice; the frontend deletes more code than it adds. Print testing happens in the smoke pass of T039.

4. **Coverage gate**: enforce ≥ 90% on `service/text/*` (T035) before opening MVP slice 1's PR. The fitter's branch density makes this trivial to hit; the overlay service's fail-soft ladder needs all five WARN cases (T034) to clear.

5. **Constitution check**: re-run `./gradlew dependencyCheckAnalyze` and `npm audit` (T037) before merge. No new packages — clean by construction.

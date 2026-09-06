---

description: "Tasks: 015 — Branded Poster Frame & 10×15 Print-Ready Aspect Ratio"
---

# Tasks: Branded Poster Frame & 10×15 Print-Ready Aspect Ratio

**Input**: Design documents from `/Users/dmytrokorniienko/aiavatar/specs/015-frame-overlay/`
**Prerequisites**: plan.md (✅), spec.md (✅), research.md (✅), data-model.md (✅), quickstart.md (✅), contracts/ (intentionally empty — see plan.md note)
**Branch**: `015-frame-overlay`

**Tests**: MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). Each test task in this list MUST be written and observed FAILING before its corresponding implementation task is started/committed. Coverage gate: ≥90% line coverage on new code (`com.aiavatar.alterego.service.frame` package); ≥1 end-to-end `@SpringBootTest` integration test.

**Organization**: Tasks are grouped by user story (US1, US2, US3) so each story can be implemented and tested independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1 / US2 / US3); Setup / Foundational / Polish phases carry no story label.

## Path Conventions

This is a Java 21 + Spring Boot 3 backend / TS-strict React 18+ frontend web application. **This feature is backend-only** — no frontend file changes. The repo's test layout uses `unit/`, `service/<package>/`, `contract/`, and `integration/` subdirs under `backend/src/test/java/com/aiavatar/alterego/`. Integration tests use the **`*IT.java`** suffix (matching existing 003 / 008 conventions like `BrandingOverlayIT.java`).

- Backend production sources: `backend/src/main/java/com/aiavatar/alterego/...`
- Backend resources: `backend/src/main/resources/...`
- Backend unit tests: `backend/src/test/java/com/aiavatar/alterego/<package>/<Thing>Test.java`
- Backend integration tests (`@SpringBootTest`): `backend/src/test/java/com/aiavatar/alterego/integration/<Journey>IT.java`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify the prerequisites that `/speckit.specify` and `/speckit.plan` already laid down, and create the new package directory before any code is written.

- [X] T001 Verify the frame asset is staged at `backend/src/main/resources/branding/poster-frame.png` (RGBA, 1024×1536, transparent inner rectangle) — run `file backend/src/main/resources/branding/poster-frame.png` and confirm `8-bit/color RGBA, 1024 x 1536`. If missing, copy from the issue's attachment and flood-fill its inner rectangle to alpha=0 per the `/speckit.specify` post-processing notes in `specs/015-frame-overlay/checklists/requirements.md`.
- [X] T002 [P] Create the new Java package directory `backend/src/main/java/com/aiavatar/alterego/service/frame/` (empty for now; T004/T006/T010 populate it).
- [X] T003 [P] Create the new test directory `backend/src/test/java/com/aiavatar/alterego/service/frame/` (empty for now; T005/T011/T015/T020/T024/T028 populate it).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The frame asset record + loader are needed by every user story — all three stories ultimately call `PosterFrameOverlayService`, which depends on `PosterFrameAssetLoader`. These cannot be deferred to a single user story.

**⚠️ CRITICAL**: No user story work (US1, US2, US3) can begin until this phase is complete.

- [X] T004 Create `PosterFrameAsset` record at `backend/src/main/java/com/aiavatar/alterego/service/frame/PosterFrameAsset.java` per the shape pinned in `data-model.md` (fields: `BufferedImage image`, `int canvasWidthPx/HeightPx`, `int innerX/Y/WidthPx/HeightPx`, `boolean loaded`; static factories `loaded(image, innerX, innerY, innerW, innerH)` and `missing()`; constructor invariants: when `loaded=true` validate non-null image, positive canvas dims, non-empty inner rectangle, inner rectangle inside canvas).
- [X] T005 Write FAILING unit test `PosterFrameAssetLoaderTest` at `backend/src/test/java/com/aiavatar/alterego/service/frame/PosterFrameAssetLoaderTest.java` covering: (a) **loaded happy path** — real classpath asset decodes to RGBA `BufferedImage`, computed inner-rectangle bounding box matches the asset's transparent area within ±2 px, `loaded=true`, AND **two consecutive `loader.get()` calls return the same `PosterFrameAsset` instance** (reference identity `==`) **AND the same underlying `BufferedImage` reference** (verifies SC-1507: asset read once, cached, reused across requests); on the success path the loader emits exactly one `INFO` line `event=frame.asset.loaded canvasWidthPx=1024 canvasHeightPx=1536 innerX=… innerY=… innerWidthPx=… innerHeightPx=…`; (b) **missing resource** — loader pointed at a non-existent classpath path returns `PosterFrameAsset.missing()` and emits one `WARN` line `event=frame.asset.missing reason=not_found`; (c) **decode_returned_null** — loader pointed at a non-image file returns `missing()` with `WARN` `reason=decode_returned_null`; (d) **decode_failed** — IOException propagation returns `missing()` with `WARN` `reason=decode_failed`; (e) **opaque-everywhere asset** — synthetic 100% opaque PNG with no `alpha==0` pixels returns `missing()` with `WARN` `reason=no_inner_rectangle`. Use Mockito + `LoggerFactory`/Logback ListAppender to capture log lines. Run `./gradlew :backend:test --tests "*PosterFrameAssetLoaderTest"` and confirm all five tests FAIL (class-not-found is acceptable).
- [X] T006 Implement `PosterFrameAssetLoader` at `backend/src/main/java/com/aiavatar/alterego/service/frame/PosterFrameAssetLoader.java` per `research.md` R-1503: `@Component`, `@PostConstruct init()` that reads `ClassPathResource("branding/poster-frame.png")` once, decodes via `ImageIO.read`, computes the alpha=0 bounding box (single O(W×H) scan: track minX/minY/maxX/maxY of pixels with `(rgba >>> 24) == 0`), stores a `PosterFrameAsset` in a final field, exposes `get()` for the overlay service. On successful decode emit one `INFO`-level structured log line `event=frame.asset.loaded canvasWidthPx=… canvasHeightPx=… innerX=… innerY=… innerWidthPx=… innerHeightPx=…` (operator-visible boot signal — referenced by `quickstart.md` §"Manual verification — happy path" step 2; pairs with the WARN-level fail-soft branches). Fail-soft branches per T005 cases (b)..(e). Re-run T005 tests and confirm all PASS.
- [X] T007 Refactor: prepare the AlterEgoService wiring swap — keep both 008 `BrandingOverlayService` and the soon-to-be-added `PosterFrameOverlayService` resolvable simultaneously. **Do NOT delete 008 code yet** (T013 deletes it after the new overlay is wired). At this checkpoint the new package has the asset record + loader; `AlterEgoService` is unchanged. The build still compiles and all existing tests still pass.

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 — Every generated alter-ego is wrapped in the new frame (Priority: P1) 🎯 MVP

**Goal**: Replace the 008 top-right two-logo overlay with a single full-bleed frame asset that wraps every poster (real-provider AND fallback) with the new event chrome — SQUER mark top-left, `<CODE/CRAFTS> 2026` wordmark top-right, gradient rounded border, decorative dot/circuit-line patterns. The character image fills the inner area and is fully visible (the inner rectangle is transparent in the asset).

**Independent Test**: Run one end-to-end Generate (with real provider OR fallback) and on the rendered poster confirm: (a) frame chrome present at the four cardinal regions; (b) character image visible inside the inner rectangle; (c) **no** standalone two-logo stack in the top-right corner; (d) saved poster bytes start with the PNG signature `89 50 4E 47 0D 0A 1A 0A`. Maps to `spec.md` SC-1501 + manual quickstart §"happy path".

### Tests for User Story 1 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T008 [P] [US1] Write FAILING unit test `PosterFrameOverlayServiceTest#applies_frame_chrome_to_real_provider_image` at `backend/src/test/java/com/aiavatar/alterego/service/frame/PosterFrameOverlayServiceTest.java`. Given a synthetic 2:3 input `PosterImage` (e.g. 1024×1536 PNG of a flat colour like `0x224488`) and a real `PosterFrameAssetLoader` pointing at the bundled asset, when `apply(input)` runs, then the result has: `mediaType="image/png"`, `widthPx=1024`, `heightPx=1536`, the byte array starts with the PNG signature, and at known frame-chrome pixel coordinates (e.g. SQUER mark roughly at `(90, 50)`) the decoded canvas pixel is **NOT** the input's flat colour (proving the frame chrome was drawn on top), while at the inner rectangle's centre (e.g. `(512, 768)`) the decoded canvas pixel IS the input's flat colour (proving the character image shows through). Run and confirm FAIL (class-not-found acceptable).
- [X] T009 [P] [US1] Write FAILING unit test `PosterFrameOverlayServiceTest#fail_soft_returns_input_when_asset_missing` at the same file. Given a `PosterFrameAssetLoader` whose `get()` returns `PosterFrameAsset.missing()` (mocked), when `apply(input)` runs, then the result is byte-equal to `input` AND a `WARN` line `event=frame.apply.skipped reason=asset_missing` is emitted. Run and confirm FAIL.
- [X] T010 [P] [US1] Write FAILING unit test `AlterEgoServiceTest#wires_poster_frame_overlay_on_both_paths` (extend or update the existing `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoServiceTest.java`). Replace the assertion that `brandingOverlay.apply(...)` was called with one that `posterFrameOverlay.apply(...)` was called — once on the real path (after `imageGenerator.generate(...)`) and once on the fallback path (after `fallbackProvider.poster(...)`). Add a constructor-arg test that the service no longer accepts a `BrandingOverlayService` parameter. Run and confirm FAIL.

### Implementation for User Story 1

- [X] T011 [US1] Implement `PosterFrameOverlayService` at `backend/src/main/java/com/aiavatar/alterego/service/frame/PosterFrameOverlayService.java`: `@Component` with constructor-injected `PosterFrameAssetLoader`; `apply(PosterImage input) -> PosterImage` per the pipeline pseudo-code in `data-model.md` §"Composition pipeline" — short-circuit return input when `!asset.loaded()` (US1 fail-soft path, FR-1512); otherwise decode input bytes via `ImageIO.read`, allocate `BufferedImage(canvasWidthPx, canvasHeightPx, TYPE_INT_ARGB)` canvas, draw character image fitted into `(innerX, innerY, innerWidthPx, innerHeightPx)` preserving the character's own aspect (US2 letterbox correction lands in T018 — for US1, just stretch to fill the inner rectangle as a placeholder; US2's T018 replaces this with aspect-preserving fit), draw `asset.image()` at `(0,0)` on top, encode via `ImageIO.write(canvas, "png", out)`, return new `PosterImage(bytes, "image/png", canvasWidthPx, canvasHeightPx)`. Wrap in `try/catch (RuntimeException | IOException)` → log `WARN event=frame.apply.failed` and return input unchanged. Re-run T008 and T009 — both PASS.
- [X] T012 [US1] Refactor `AlterEgoService` at `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java`: replace constructor parameter `BrandingOverlayService brandingOverlay` with `PosterFrameOverlayService posterFrameOverlay`; replace both `brandingOverlay.apply(...)` call sites (one on real path line 65, one on fallback path line 99) with `posterFrameOverlay.apply(...)`; delete the unused import. Re-run T010 — PASSES.
- [X] T013 [US1] Delete the **entire 008 overlay code path in a single atomic commit** (research.md R-1506) — production classes, unit tests, integration tests, helpers, and asset PNGs all together. Bundling these into one commit is **load-bearing**: the integration tests in `integration/Branding*IT.java` static-import `BrandingOverlayService` / `LogoAsset` / `LogoId`, so deleting only the production classes (or only the unit tests) would leave the build red between commits. Files to delete:
  - **Production code (`backend/src/main/java/com/aiavatar/alterego/service/branding/`)**:
    - `BrandingOverlayService.java`
    - `LogoAsset.java`
    - `LogoAssetLoader.java`
    - `LogoId.java`
    - the now-empty `service/branding/` directory itself
  - **Unit tests (`backend/src/test/java/com/aiavatar/alterego/service/branding/`)**:
    - `BrandingOverlayServiceTest.java`
    - `LogoAssetLoaderTest.java`
    - the now-empty `service/branding/` test directory itself
  - **Integration tests (`backend/src/test/java/com/aiavatar/alterego/integration/`)**:
    - `BrandingOverlayIT.java`
    - `BrandingOverlayFallbackIT.java`
    - `BrandingOverlayDegradedIT.java`
    - `BrandingTestSupport.java` (helper used only by the three above)
    - `GenerateAlterEgoGeminiNoLogoLeakIT.java` (no logos to leak — the equivalent 015 test for "no frame bytes leaked to Gemini" is T026)
  - **Resources (`backend/src/main/resources/branding/`)**:
    - `squer-logo.png`
    - `codecrafts-logo.png`
- [X] T014 [US1] Verify the deletion left a clean compile by running `./gradlew :backend:compileJava :backend:compileTestJava` and `grep -r "BrandingOverlayService\|LogoAssetLoader\|LogoAsset\|LogoId\|squer-logo\.png\|codecrafts-logo\.png" backend/`. Both must show zero references. If anything references a deleted symbol or asset, finish the corresponding deletion before moving on. (This task is the safety net for T013's atomic-commit invariant; T035 in the polish phase re-runs the same grep on a clean checkout for final certification.)
- [X] T015 [US1] Run `./gradlew :backend:test`; confirm all remaining tests pass and the build is green. Manually verify per `quickstart.md` §"Manual verification — happy path" steps 1–6: real-provider Generate produces a poster with the new frame chrome and no top-right two-logo stack.

**Checkpoint**: At this point, User Story 1 is fully functional and testable independently. Real-provider posters carry the new frame; fallback posters too (because `AlterEgoService` calls `posterFrameOverlay.apply` on both paths in T012). The 2:3 ratio is **not yet** asked of the AI (US2) — Gemini may return any ratio and the overlay still produces a 1024×1536 PNG (because the canvas IS the frame asset's), but the prompt still says "3:4 aspect ratio" until US2 lands.

---

## Phase 4: User Story 2 — Output is portrait 10×15 (2:3) so booth prints come out right (Priority: P1)

**Goal**: Lock the generator's output to portrait 2:3 (10×15) by stating the ratio in the Gemini prompt and best-effort setting the typed `aspectRatio` parameter; non-conforming provider responses are letterboxed inside the frame's inner rectangle (FR-1511); fallback poster bumps to 1024×1536 natively. Output encoding tightens to **always PNG** (FR-1509) so the frame's alpha channel composites cleanly.

**Independent Test**: Run one Generate; saved poster's `height/width` ratio is `1.5 ± 0.015` (2:3) AND first 8 bytes are the PNG signature AND the outbound prompt body contains `2:3 aspect ratio` (and **no** `3:4 aspect ratio`). Maps to spec.md SC-1502 + SC-1503 + SC-1509 + manual quickstart §"happy path" steps 7–9.

### Tests for User Story 2 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T016 [P] [US2] Write FAILING unit test `GeminiPromptBuilderTest#single_prompt_states_2_3_aspect_ratio` at `backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilderTest.java` (new directory if it doesn't exist; the existing project does not yet have `service/gemini/` test subdir). Given a fixed `AlterEgoRequest` with `effectivePhotoMode() == SINGLE`, when `prompt = builder.build(request)`, then `prompt.contains("2:3 aspect ratio")` is `true` AND `prompt.contains("3:4 aspect ratio")` is `false` AND the rest of the prompt body is byte-equal to a captured expected fixture (everything outside the ratio line is regression-locked). Run and confirm FAIL.
- [X] T017 [P] [US2] Write FAILING unit test `GeminiPromptBuilderTest#group_prompt_states_2_3_aspect_ratio` at the same file, mirroring T016 for `effectivePhotoMode() == GROUP`. Run and confirm FAIL.
- [X] T018 [P] [US2] Write FAILING unit test `PosterFrameOverlayServiceTest#letterboxes_wrong_ratio_input_inside_inner_rectangle` at the existing `backend/src/test/java/com/aiavatar/alterego/service/frame/PosterFrameOverlayServiceTest.java`. Given a synthetic 1:1 (e.g. 1024×1024) input PNG of a flat colour, when `apply(input)` runs with the real frame loader, then the result is `image/png`, `widthPx=1024`, `heightPx=1536`, the input's flat colour appears in a centred sub-rectangle of the inner area whose own ratio is 1:1 (proving the character image was scaled aspect-preservingly), the bands above/below the centred sub-rectangle inside the inner rectangle have alpha=0 (transparent letterbox per R-1504), the input's colour does NOT appear at the corners of the inner rectangle (proving no stretching), AND a `WARN` line `event=frame.apply.ratio_mismatch inputWidth=1024 inputHeight=1024` is emitted. Run and confirm FAIL.
- [X] T019 [P] [US2] Write FAILING unit test `PosterFrameOverlayServiceTest#always_encodes_output_as_png_even_when_input_is_jpeg` at the same file. Given a synthetic 1024×1536 JPEG-encoded input `PosterImage` of a flat colour, when `apply(input)` runs with the real loader, then the result has `mediaType="image/png"` AND the bytes start with the PNG signature `89 50 4E 47 0D 0A 1A 0A`. Run and confirm FAIL.
- [X] T020 [P] [US2] Write FAILING unit test `FallbackPosterProviderTest#emits_2_3_portrait_dimensions` (extend the existing `backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java`). Given any `(archetype, universe)` pair, when `provider.poster(...)` is called, then the returned `PosterImage` has `widthPx=1024` AND `heightPx=1536` AND `heightPx/(double)widthPx` is within `1.5 ± 0.015`. Run and confirm FAIL.
- [X] T021 [P] [US2] Write FAILING unit test `GeminiClientTest#request_body_includes_image_aspect_ratio_2_3` at `backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiClientTest.java` (new file). Use a Mockito-mocked `HttpClient` that captures the outbound `HttpRequest`'s body string. Given a stubbed successful 2:3 PNG response, when `client.generateImage(modelId, prompt, photo)` runs, then the captured request body parses as JSON AND `root.path("generationConfig").path("imageConfig").path("aspectRatio").asText()` equals `"2:3"`. Run and confirm FAIL.

### Implementation for User Story 2

- [X] T022 [US2] Update `GeminiPromptBuilder` at `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`: in `buildSingle(...)` change the `Composition notes` line `"- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.\n"` to `"- Portrait orientation, 2:3 aspect ratio (10×15 vertical, print-ready), dramatic rim lighting.\n"`; mirror the same change in `buildGroup(...)`. Re-run T016 and T017 — both PASS.
- [X] T023 [US2] Update `GeminiClient` at `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiClient.java`: in `renderRequestBody(...)` after the existing `generationConfig.put("candidateCount", 1)` line, add an `imageConfig` sub-object with `aspectRatio="2:3"` (`generationConfig.putObject("imageConfig").put("aspectRatio", "2:3");`). Re-run T021 — PASSES.
- [X] T024 [US2] Replace the placeholder stretching logic in `PosterFrameOverlayService.apply(...)` (T011 used naive stretch-to-fill) with the aspect-preserving letterbox per `research.md` R-1504: compute the input character image's aspect (`charAspect = charWidth / (double) charHeight`), the inner rectangle's aspect (`innerAspect = innerWidthPx / (double) innerHeightPx`), if `Math.abs(charAspect - innerAspect) <= 0.005 * innerAspect` draw fitted to fill the inner rectangle; else compute a centred sub-rectangle inside the inner rectangle that preserves `charAspect` and emit `WARN event=frame.apply.ratio_mismatch inputWidth=… inputHeight=…`. The bands outside the sub-rectangle but inside the inner rectangle inherit the canvas's `TYPE_INT_ARGB` zero-alpha pixels by default (no explicit fill needed — AWT's `BufferedImage.TYPE_INT_ARGB` initialises to alpha=0). Re-run T018 — PASSES.
- [X] T025 [US2] Update `FallbackPosterProvider` at `backend/src/main/java/com/aiavatar/alterego/service/fallback/FallbackPosterProvider.java`: change `private static final int POSTER_WIDTH = 900` → `1024` and `POSTER_HEIGHT = 1200` → `1536`. The existing draw code is pixel-relative (margins from edges, centred text); recompute the rectangle bounds from the new constants. Re-run T020 — PASSES; re-run the existing `FallbackPosterProviderTest` cases and fix any pixel-position assertions that hard-coded the old 900×1200 dimensions.
- [X] T026 [US2] Write integration test `GenerateAlterEgoGeminiNoFrameBytesLeakIT` at `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiNoFrameBytesLeakIT.java` (replaces deleted `GenerateAlterEgoGeminiNoLogoLeakIT`). `@SpringBootTest` with WireMock-stubbed Gemini; capture the outbound request body; assert that the body does NOT contain any byte sequence from `branding/poster-frame.png` (load the asset, assert its first 64 bytes do not appear as a substring in the request body). Verifies FR-1502/1504 spirit (asset bundled, not transmitted) — the equivalent of 008's no-logo-leak guarantee. Run and confirm PASSES (the new client only adds the typed `aspectRatio` string, never the asset bytes).
- [ ] T027 [US2] Manually verify per `quickstart.md` §"Manual verification — happy path" steps 7–9: saved PNG dimensions are 2:3 within tolerance; first 8 bytes are the PNG signature; backend log includes `2:3 aspect ratio` in the prompt body and excludes `3:4 aspect ratio`.

**Checkpoint**: User Story 2 fully functional. Real-provider Generate emits 2:3 PNGs (asked of Gemini in two channels); fallback emits 2:3 PNGs natively; wrong-ratio responses are letterboxed; encoding is always PNG.

---

## Phase 5: User Story 3 — Fallback posters are framed and 2:3 too (Priority: P2)

**Goal**: End-to-end coverage that **both** real-provider and fallback paths arrive at 2:3 PNG with frame chrome — exercised at the `@SpringBootTest` level (the constitution-mandated integration test). The behaviour is already implemented by US1+US2 (because `AlterEgoService` calls `posterFrameOverlay.apply` on both paths and the fallback now natively emits 2:3); US3's job is to **prove** it via integration tests and add the fail-soft degraded-asset coverage.

**Independent Test**: Run one fallback Generate (with `GEMINI_API_KEY` unset); the rendered poster carries the new frame chrome AND its `height/width` is 1.5 within tolerance. Also run one Generate with the frame asset deliberately removed; the user-facing poster still renders (un-framed but 2:3) and a `WARN` log line records the missing asset. Maps to spec.md SC-1504 + SC-1505 + manual quickstart §"fallback path" and §"fail-soft".

### Tests for User Story 3 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T028 [P] [US3] Write `PosterFrameOverlayIT` at `backend/src/test/java/com/aiavatar/alterego/integration/PosterFrameOverlayIT.java` — `@SpringBootTest` (mirrors deleted `BrandingOverlayIT`). Wire the real `PosterFrameAssetLoader` + `PosterFrameOverlayService` + a stub `GeminiClient` returning a synthetic 2:3 PNG. POST `/api/alter-ego/generate` with a multipart photo + setup; assert the response's `poster.dataUrl` decodes to a PNG of dimensions 1024×1536, the byte at coordinate `(90, 50)` is non-uniform with the stub's flat colour (frame chrome present), and `meta.outcome == "real"`. Run and confirm PASSES (the underlying behaviour is already implemented by US1+US2; this test certifies it end-to-end).
- [X] T029 [P] [US3] Write `PosterFrameOverlayFallbackIT` at `backend/src/test/java/com/aiavatar/alterego/integration/PosterFrameOverlayFallbackIT.java` — `@SpringBootTest` (mirrors deleted `BrandingOverlayFallbackIT`). Force the fallback path via a stub `ImageGenerator` that throws `GenerationFailure(NETWORK_ERROR, …)`. POST `/generate`; assert the response's poster decodes to a PNG of 1024×1536 with frame chrome at `(90, 50)`, `meta.outcome == "fallback"`, and `meta.reason == "network_error"`. Run and confirm PASSES.
- [X] T030 [P] [US3] Write `PosterFrameOverlayDegradedIT` at `backend/src/test/java/com/aiavatar/alterego/integration/PosterFrameOverlayDegradedIT.java` — `@SpringBootTest` (mirrors deleted `BrandingOverlayDegradedIT`). Use a Spring `@TestConfiguration` to override `PosterFrameAssetLoader` with a stub whose `get()` returns `PosterFrameAsset.missing()`. POST `/generate` with a stub `ImageGenerator` returning a 2:3 PNG; assert the response's poster decodes to a PNG of dimensions matching the stub's input (i.e. un-framed pass-through), `meta.outcome == "real"`, and at least one `WARN`-level log line containing `event=frame.apply.skipped` was emitted (capture via Logback ListAppender). Run and confirm PASSES.

### Implementation for User Story 3

No production-code changes are required — the behaviour US3 asserts is already implemented by US1+US2. The work in Phase 5 is purely the integration test trio (T028/T029/T030). After they all pass, run the manual fallback + fail-soft walkthroughs:

- [ ] T031 [US3] Manually verify per `quickstart.md` §"Manual verification — fallback path": with `GEMINI_API_KEY` unset, run one Generate; confirm the fallback stub renders inside the frame chrome at 1024×1536 PNG, the 003 fallback notice banner is present.
- [ ] T032 [US3] Manually verify per `quickstart.md` §"Manual verification — fail-soft": move `branding/poster-frame.png` aside, restart backend, run one Generate; confirm the un-framed character image is returned, the startup log carries one `event=frame.asset.missing` line, and no per-request decode noise.

**Checkpoint**: All three user stories independently verified end-to-end. The booth pipeline emits framed 2:3 PNG posters on real-provider, fallback, and degraded-asset paths — every path delivers a printable booth artefact.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Constitution gates (coverage, dependency hygiene, /speckit.analyze consistency check) and final cleanup.

- [X] T033 [P] Run `./gradlew :backend:jacocoTestReport :backend:jacocoTestCoverageVerification`; verify line coverage on the new `com.aiavatar.alterego.service.frame` package is ≥ 90% (Constitution Principle III). Also verify that overall backend coverage has not regressed below 90% — the deleted 008 code came with its own deleted tests, so the net is balanced.
- [ ] T034 [P] Run `./gradlew :backend:dependencyCheckAnalyze` (or the project's chosen vulnerability scanner); confirm no new HIGH/CRITICAL CVEs (Constitution Principle VI). No new dependencies are added by this feature, so this run is regression-only.
- [X] T035 [P] Run `./gradlew :backend:test` end-to-end one more time on a clean checkout of the branch; all unit + contract + integration tests must pass. Confirm none of the deleted 008 test classes are referenced anywhere (`grep -r "BrandingOverlayService\|LogoAssetLoader\|LogoAsset\|LogoId" backend/`).
- [ ] T036 [P] Run `/speckit.analyze` from the repo root; confirm cross-artifact consistency between `spec.md`, `plan.md`, `tasks.md`. Resolve any reported divergence (e.g. a referenced FR not covered by a task) before opening the PR.
- [ ] T037 Run the `quickstart.md` happy-path walkthrough one final time on a fresh `./gradlew :backend:bootRun` + `npm run dev` to certify the merged behaviour matches the spec narratively. Capture two screenshots (real-path framed poster, fallback-path framed poster) and one annotated photo showing the printed 10×15 cm output looks edge-to-edge with no white bars (uses 010 print path).
- [ ] T038 Open PR from `015-frame-overlay` to `main` per Constitution Principle V. PR description references issue #39 and includes the screenshots from T037. Request explicit human approval; do not merge until approved and the post-merge CI run is green.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)** — no dependencies; can start immediately.
- **Phase 2 (Foundational)** — depends on Setup; **BLOCKS** all user-story work.
- **Phase 3 (US1 — frame chrome)** — depends on Foundational. Independent of US2 and US3. Delivers the MVP (frame visibly applied on every poster). T013 is an **atomic single-commit deletion** of the entire 008 overlay path (production + unit + integration tests + asset PNGs); T014 is the post-deletion clean-compile check that confirms the build stayed green through that delete.
- **Phase 4 (US2 — 2:3 ratio)** — depends on Foundational. Independent of US1 and US3 in concept, but T024 modifies `PosterFrameOverlayService.apply(...)` introduced in T011 (US1), so **T024 sequentially follows T011**. The other US2 tasks (T022 prompt builder, T023 client, T025 fallback dims, T026 leak IT) are file-disjoint from US1's files and can run in parallel with US1's late tasks.
- **Phase 5 (US3 — fallback + fail-soft, integration tests)** — depends on US1 (`AlterEgoService` calls `posterFrameOverlay`) and US2 (fallback dims + always-PNG). Integration tests T028/T029/T030 can run in parallel with each other (different test classes, no shared mutable state).
- **Phase 6 (Polish)** — depends on all three user stories complete.

### Within Each User Story

- Constitution Principle III: tests precede implementation; tests must FAIL before the implementation task that satisfies them is committed.
- Models / records before services that consume them.
- Service implementations before wiring changes in `AlterEgoService`.
- Wiring changes before deletion of replaced 008 code.

### Parallel Opportunities

- **Setup**: T002 and T003 are file-disjoint (production vs test directories) and parallel.
- **Foundational**: T004 (record) and T005 (loader test) are file-disjoint; T005 written, then T004 implemented, then T006 implementation re-runs T005. T007 is a no-op staging step.
- **US1 tests**: T008, T009, T010 each touch a different test class — fully parallel.
- **US2 tests**: T016/T017 (same file, but additive), T018/T019 (same file, additive), T020 (different file), T021 (new file) — T020 and T021 fully parallel with T016–T019 (different files).
- **US2 implementation**: T022 (prompt) and T023 (client) and T025 (fallback) and T026 (leak IT) all touch different production files and can run in parallel; T024 sequentially follows T011 because it modifies the same file.
- **US3 integration tests**: T028, T029, T030 all in different files, fully parallel.
- **Polish**: T033, T034, T035, T036 all parallel-safe (read-only or independent reports).

### Cross-Story File-Edit Sequencing (avoid merge conflicts)

| File | US1 task | US2 task | Order |
|---|---|---|---|
| `service/frame/PosterFrameOverlayService.java` | T011 (create) | T024 (modify letterbox logic) | T011 → T024 |
| `service/frame/PosterFrameOverlayServiceTest.java` | T008/T009 (write) | T018/T019 (extend) | T008/T009 → T018/T019 |
| `service/AlterEgoService.java` | T012 (refactor) | — | (US1 only) |
| `unit/FallbackPosterProviderTest.java` | — | T020 (extend) | (US2 only) |
| `unit/AlterEgoServiceTest.java` | T010 (refactor) | — | (US1 only) |

---

## Parallel Example: User Story 2 Tests

```bash
# Launch all FAILING tests for User Story 2 together (Constitution Principle III — write before implementing):
Task: "Write FAILING GeminiPromptBuilderTest#single_prompt_states_2_3_aspect_ratio in backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilderTest.java"
Task: "Write FAILING GeminiPromptBuilderTest#group_prompt_states_2_3_aspect_ratio in backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilderTest.java"
Task: "Write FAILING PosterFrameOverlayServiceTest#letterboxes_wrong_ratio_input_inside_inner_rectangle in backend/src/test/java/com/aiavatar/alterego/service/frame/PosterFrameOverlayServiceTest.java"
Task: "Write FAILING PosterFrameOverlayServiceTest#always_encodes_output_as_png_even_when_input_is_jpeg in backend/src/test/java/com/aiavatar/alterego/service/frame/PosterFrameOverlayServiceTest.java"
Task: "Write FAILING FallbackPosterProviderTest#emits_2_3_portrait_dimensions in backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java"
Task: "Write FAILING GeminiClientTest#request_body_includes_image_aspect_ratio_2_3 in backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiClientTest.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete **Phase 1: Setup** (T001–T003).
2. Complete **Phase 2: Foundational** (T004–T007) — frame asset record + loader available.
3. Complete **Phase 3: User Story 1** (T008–T015) — frame chrome visibly wraps every poster, 008 code deleted.
4. **STOP and VALIDATE**: run one Generate in real-provider mode; confirm the frame chrome appears and the two-logo stack is gone.
5. Demo if ready — note that the AI is still asked for 3:4 (US2 not landed yet), so the character image will be slightly stretched into the 2:3 frame's inner rectangle until US2 lands.

### Incremental Delivery

1. Setup + Foundational → frame asset + loader ready.
2. **US1 lands** → MVP ready (frame chrome on every poster; AI still asked for 3:4).
3. **US2 lands** → 2:3 ratio asked of AI + always-PNG + fallback at 2:3 + wrong-ratio letterbox correction. Booth print station now receives correctly-shaped posters.
4. **US3 lands** → integration-test certification of the full matrix (real / fallback / degraded). No production-code change in this slice — pure assurance.
5. **Polish** → coverage gate, CVE scan, /speckit.analyze, PR.

### Single-Developer Strategy (recommended for this feature)

This feature is small (~10 files touched, mostly backend). One developer should own it end-to-end. The dependency graph above keeps file-edit conflicts to zero by ordering US1 tasks (T011) before US2's later tasks (T024) on the same file, and the cross-story file table makes sequencing explicit.

### Parallel-Pair Strategy (if the team has bandwidth)

After Foundational completes:
- Developer A drives Phase 3 (US1 — frame chrome rendering, AlterEgoService refactor, 008 deletion).
- Developer B drives Phase 4 (US2 — prompt builder, client typed param, fallback dims, leak IT) — all on **disjoint files** from Developer A's work, except T024 which Developer B picks up after Developer A's T011 lands.
- Both developers converge on Phase 5 integration tests; one writes T028, the other T029, third optional pair on T030.

---

## Notes

- **No frontend file changes** in this feature. The frontend's `<img>` element in the poster slot already renders the data URL the backend returns; the only observable change is that the data URL is now always `data:image/png;base64,…`.
- **No new HTTP endpoint, no new DTO field.** The framed PNG bytes ride inside the existing 003 `Poster.dataUrl` field; `Content-Type` becomes always `image/png` (FR-1509). `AlterEgoResponse.ResponseMeta.outcome/reason` is unchanged.
- **No persistence introduced.** 001 FR-016 / 017 / 024 carry through — the framed bytes live in process memory for one HTTP request only.
- **Constitution gates**: Principle I (no new deps — JDK built-in stack only), Principle III (tests-first, ≥90% coverage, ≥1 integration test — all satisfied by Phase 5 tests), Principle IV (resilient HTTP — unchanged; framing is local), Principle V (branch + PR + explicit approval — T038), Principle VI (zero deprecated deps — T034). All ✅ per the plan.md Constitution Check.
- **Commit cadence**: commit after each task or each tightly-coupled task pair. After T015, after T027, and after T032 are natural demo points.

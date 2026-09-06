---

description: "Task list for feature 008 — Logo Branding Overlay on Generated Alter-Ego Images"
---

# Tasks: Logo Branding Overlay on Generated Alter-Ego Images

**Input**: Design documents from `/specs/008-logos-on-generated-images/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, quickstart.md ✓ — no contracts/ (internal transform; public wire shape unchanged — see research R-708)

**Tests**: Test tasks are MANDATORY per Principle III (Test-First Development, NON-NEGOTIABLE). Every test task below MUST be written before — and observed to FAIL against — its paired implementation task. Coverage gate: ≥ 90% line coverage on new classes.

**Organization**: Tasks are grouped by user story (US1 — every poster branded; US2 — nothing leaks to Gemini; US3 — fallback posters branded too).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 / US2 / US3 when applicable

## Path Conventions

Backend only. No frontend source changes — FR-705.

- Production sources: `backend/src/main/java/com/aiavatar/alterego/service/branding/…`
- Modified seam: `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java`
- Resources: `backend/src/main/resources/branding/…`
- Unit tests: `backend/src/test/java/com/aiavatar/alterego/service/branding/…Test.java`
- Integration tests (`@SpringBootTest`): `backend/src/test/java/com/aiavatar/alterego/integration/…IT.java`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Stage the logo assets and their package home.

- [x] T001 Copy the two uploaded logo files from the repo root into `backend/src/main/resources/branding/` under canonical names: `SQUER-Logo-without-font-white.png` → `backend/src/main/resources/branding/squer-logo.png`; `codecrafts.png` → `backend/src/main/resources/branding/codecrafts-logo.png`. Leave the originals at the repo root untouched (they were uploaded by the issue author on the feature branch).
- [x] T002 [P] Create the empty production package directory `backend/src/main/java/com/aiavatar/alterego/service/branding/` and the mirroring test package `backend/src/test/java/com/aiavatar/alterego/service/branding/` — ensures subsequent `[P]` tasks do not race on `mkdir`.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: None — this feature has no cross-story blocking infrastructure beyond Phase 1. The Spring context, logging, `AlterEgoService`, `PosterImage`, and test harnesses from 001/002/003 are already in place.

**Checkpoint**: Foundation ready — user-story work (US1 / US2 / US3) may start.

---

## Phase 3: User Story 1 — Every generated alter-ego carries the event and sponsor branding (Priority: P1) 🎯 MVP

**Goal**: Every successful real-provider Generate returns a poster with the SQUER logo in the top-right corner and the CodeCrafts logo directly below it, both at equal rendered width, proportional margins, and aspect-preserving heights. Covers FR-701, FR-702, FR-703, FR-705, FR-706, FR-707, FR-709, FR-710, FR-712, FR-713.

**Independent Test**: Run the stub-profile backend and frontend locally (`./gradlew bootRun` + `npm run dev`), press Generate, and visually verify the top-right layout. Then run the unit + integration tests in T003..T006 — all pass; the corner-pixel assertions prove branding pixels reached the output.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [x] T003 [P] [US1] Write `backend/src/test/java/com/aiavatar/alterego/service/branding/LogoAssetLoaderTest.java` — covers: (a) both logos load from classpath (assert `loaded == true`, `sourceWidthPx > 0`, `sourceHeightPx > 0`); (b) caching — two calls to the accessor return the same `BufferedImage` instance reference; (c) missing-resource fail-soft — construct a `LogoAssetLoader` with a bogus classpath path and assert `loaded == false` and that a `WARN` log is emitted (capture via `ListAppender`).
- [x] T004 [P] [US1] Write `backend/src/test/java/com/aiavatar/alterego/service/branding/BrandingOverlayServiceTest.java` — covers the five pixel-level invariants: (a) dimensions preserved (output `widthPx`/`heightPx` == input); (b) MIME preserved (PNG→PNG, JPEG→JPEG; parameterize with two synthetic `PosterImage` inputs, one per MIME); (c) top-right pixel differs from input at the overlay region (sampled ~2% in from the top-right corner); (d) top-left pixel unchanged from input; (e) fail-soft — pass a `PosterImage` whose `bytes` are a valid PNG-magic header but corrupt body; assert `apply()` returns the input unchanged and emits a `WARN`.
- [x] T005 [P] [US1] Write `backend/src/test/java/com/aiavatar/alterego/integration/BrandingOverlayIT.java` (`@SpringBootTest` with the `default` / stub profile) — `POST /api/alter-ego` end-to-end; decode the base64 from `poster.dataUrl`; assert `widthPx`/`heightPx` match expected stub dims (900×1200); assert `mediaType == "image/png"`; assert the sampled top-right pixel differs from the corresponding pixel produced by a branding-disabled control run (use a test-profile override that replaces `BrandingOverlayService` with a pass-through bean for the control capture).
- [x] T006 [P] [US1] Write a micro-benchmark-style unit test in `BrandingOverlayServiceTest` — run `apply()` 20× on a 900×1200 synthetic PNG and assert the median elapsed time is under 100 ms and the max is under 250 ms on the CI JVM (loose bounds; SC-707 is a local-laptop target, CI headroom is 3×). This guards SC-707 as a regression signal, not as a hard production gate.

### Implementation for User Story 1

- [x] T007 [US1] Create `backend/src/main/java/com/aiavatar/alterego/service/branding/LogoId.java` — enum with constants `SQUER` and `CODECRAFTS`; carries the classpath resource path per constant (`"branding/squer-logo.png"`, `"branding/codecrafts-logo.png"`).
- [x] T008 [US1] Create `backend/src/main/java/com/aiavatar/alterego/service/branding/LogoAsset.java` — immutable record `LogoAsset(LogoId id, BufferedImage image, int sourceWidthPx, int sourceHeightPx, boolean loaded)`. Include a `LogoAsset missing(LogoId id)` static factory for the fail-soft path (returns a sentinel with `loaded == false` and `image == null`).
- [x] T009 [US1] Create `backend/src/main/java/com/aiavatar/alterego/service/branding/LogoAssetLoader.java` — `@Component`; `@PostConstruct init()` reads both `ClassPathResource`s and decodes via `ImageIO.read`. On failure for a given logo: log `WARN` (`event=branding.logo.missing id=<SQUER|CODECRAFTS> resource=<path>`) and hold a `LogoAsset.missing(id)` instead. Expose `LogoAsset get(LogoId id)`. This completes the test in T003.
- [x] T010 [US1] Create `backend/src/main/java/com/aiavatar/alterego/service/branding/BrandingOverlayService.java` — `@Component`; constructor-injected with `LogoAssetLoader`. Public `PosterImage apply(PosterImage input)`:
  - Decode input bytes to `BufferedImage` via `ImageIO.read`. If the decode returns `null` or throws, catch → log `WARN` (`event=branding.apply.failed reason=decode`) → return `input` unchanged.
  - Build a working canvas `BufferedImage(widthPx, heightPx, TYPE_INT_ARGB)` for PNG or `TYPE_INT_RGB` for JPEG (per research R-706). Draw the decoded input onto the canvas via `Graphics2D.drawImage` with `VALUE_INTERPOLATION_BICUBIC` + `VALUE_RENDER_QUALITY` + `VALUE_ANTIALIAS_ON`.
  - Compute geometry per research R-703 (logo width = `max(48, round(0.15 × widthPx))`, margins = `round(0.04 × widthPx)`, gap = `round(0.02 × widthPx)`). Skip any `LogoAsset` whose `loaded == false`; if both are missing, return `input` unchanged.
  - Draw each loaded logo scaled to its computed width (`Graphics2D.drawImage(src, x, y, w, h, null)` — implicit bicubic from the rendering hint). Dispose the `Graphics2D`.
  - Re-encode to byte array via `ImageIO.write(canvas, "png"|"jpeg", ByteArrayOutputStream)`. If `write` returns `false` or throws, catch → log `WARN` → return `input` unchanged.
  - On success, construct and return a new `PosterImage(bytes, input.mediaType(), input.widthPx(), input.heightPx())`.
  - Any uncaught `RuntimeException` from the above path MUST NOT escape — wrap the whole body in a single outer try/catch that logs the throwable and returns `input` (FR-711 per-request fail-soft).
  This completes the test in T004.
- [x] T011 [US1] Modify `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java` — add `BrandingOverlayService brandingOverlay` to the constructor (between `fallbackProvider` and `retryTemplate` to minimise diff churn). In `generate(...)` line ~62, wrap the real path: `PosterImage poster = brandingOverlay.apply(retryTemplate.execute(ctx -> imageGenerator.generate(character, request, photo)));`. Do NOT wrap the `retryTemplate.execute` itself — the overlay is post-retry. This completes the IT in T005 for the real path (and US3's IT for the fallback path via T013).

**Checkpoint**: US1 is fully functional for real-provider runs. Un-branded posters for fallbacks are still possible (handled in US3, T013).

---

## Phase 4: User Story 2 — Branding is applied without leaking the logos to the AI provider (Priority: P1)

**Goal**: Prove — not merely assume — that the overlay implementation from US1 does not smuggle any logo bytes, filenames, or textual references into the outbound Gemini request. Covers FR-704, SC-702.

**Independent Test**: Extend the existing Gemini IT to assert the captured outbound request body (via WireMock) contains no `squer` / `codecrafts` / `logo` substrings and no base64 blob whose SHA-256 matches either logo file's SHA-256.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [x] T012 [P] [US2] Extend `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiIT.java` (or add a sibling `GenerateAlterEgoGeminiNoLogoLeakIT.java` if the original is difficult to modify safely) — run the existing happy-path flow under the `gemini` profile, capture the WireMock request body, then assert: (a) no case-insensitive substring `squer` appears anywhere; (b) no case-insensitive substring `codecrafts` appears anywhere; (c) no case-insensitive substring `logo` appears outside the legitimate prompt scaffolding established in 003 (use a fixed allow-list of substrings already present in the 003 IT's snapshot); (d) compute SHA-256 of `backend/src/main/resources/branding/squer-logo.png` and `…/codecrafts-logo.png`; assert neither hash appears as a base64-decoded chunk in the request body.

### Implementation for User Story 2

- [x] T013 [US2] No production code change required — US2 is a **verification-only** story. The FR-704 / SC-702 invariant holds by construction because `BrandingOverlayService` is invoked **after** `imageGenerator.generate(...)` in `AlterEgoService` (T011) and is not referenced anywhere in the Gemini request-assembly code path (`GeminiPromptBuilder` and `GeminiClient`). Mark T013 complete as soon as T012 passes green.

**Checkpoint**: US2 proven — both the US1 happy path AND the no-leak invariant are locked in by tests.

---

## Phase 5: User Story 3 — Fallback posters are branded too, so no attendee walks away un-branded (Priority: P2)

**Goal**: When Gemini is unreachable, unconfigured, rate-limited, or misbehaves, the fallback stub poster (003 FR-214) carries identical branding. Covers FR-708, SC-703.

**Independent Test**: Run the backend with `GEMINI_API_KEY` unset (forces fallback), press Generate, and verify pixel-level that the fallback poster has the same top-right branding layout as a real-provider run. Covered by the existing IT from T005 (extended below) plus a new negative-path branch.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [x] T014 [P] [US3] Extend `BrandingOverlayIT` (from T005) with a `@Nested` class `FallbackPathBranding` that configures the test `ApplicationContext` to force the fallback path (either by throwing `GenerationFailure.noApiKey()` from a test-`@Primary` `ImageGenerator` bean, or by removing the `gemini` profile entirely). Invoke `POST /api/alter-ego`; decode the base64 from `poster.dataUrl`; assert the response's `meta.outcome == "FALLBACK"` (003-inherited) AND the top-right pixel assertion used in T005 holds on the fallback bytes too.
- [x] T015 [P] [US3] Add a negative-path test in `BrandingOverlayIT` — provide a test `@Primary` bean that replaces `LogoAssetLoader` with one where both logos return `LogoAsset.missing(…)`. Invoke `POST /api/alter-ego`. Assert: HTTP 200, a valid `poster.dataUrl` (starts with `data:image/png;base64,`), dims preserved, and a `WARN` captured by a `ListAppender` listening on `BrandingOverlayService`. Guards FR-711 / SC-706 under Spring wiring.

### Implementation for User Story 3

- [x] T016 [US3] Modify `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java` — in `handleFallback(...)` at line ~95, wrap the fallback path: `PosterImage fallbackPoster = brandingOverlay.apply(fallbackProvider.poster(request.archetype(), request.universe()));`. Keep the rest of the method unchanged. Completes the IT in T014.

**Checkpoint**: Branding is now present on both outcomes — the attendee never sees an un-branded poster (except in the degraded `both-logos-missing` case, which is FR-711 fail-soft and visibly logged).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T017 [P] Run the full backend test suite: `./gradlew test`. All pre-existing tests from 001/002/003 MUST still pass — branding is additive and does not change the public wire shape.
- [ ] T018 [P] Run `./gradlew jacocoTestReport` (or equivalent coverage task) and confirm line coverage for the new `branding` package is ≥ 90%. Add targeted unit tests only where coverage gaps trace to untested failure branches.
- [x] T019 [P] Refresh `CLAUDE.md` / agent context with the new package name if `.specify/scripts/bash/update-agent-context.sh claude` has not already captured it during planning.
- [ ] T020 Manual quickstart walkthrough — run `specs/008-logos-on-generated-images/quickstart.md` steps 1–5 end-to-end and tick each row.
- [ ] T021 [P] Confirm no new CVEs / deprecated deps introduced: `./gradlew dependencyCheckAnalyze` (Principle VI). Expected: no change from the previous baseline because no Maven coordinate was added.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies.
- **Foundational (Phase 2)**: Empty for this feature.
- **US1 (Phase 3)**: Depends on Setup.
- **US2 (Phase 4)**: Depends on US1 (T011) — the outbound-request assertion needs the service wired so that the real-provider path actually exercises branding.
- **US3 (Phase 5)**: Depends on US1 (T010 defines the branding service that US3 also uses on the fallback path). Does **not** depend on US2 — can be implemented in parallel with US2 once US1 is green.
- **Polish (Phase 6)**: Depends on all three stories being green.

### Within Each User Story

- Tests (T003/T004/T005/T006 for US1; T012 for US2; T014/T015 for US3) MUST be written and observed to FAIL before any implementation task in the same story is committed (Principle III).
- Entities (`LogoId`, `LogoAsset`) before services (`LogoAssetLoader`, `BrandingOverlayService`) before seam modification (`AlterEgoService`).

### Parallel Opportunities

- T003, T004, T005, T006 — four independent test files — all `[P]` and writable in parallel.
- T014, T015 — both added to the same `BrandingOverlayIT` file; technically serial on the file, but logically independent.
- T017–T021 in Phase 6 — `[P]` across runner invocations.

---

## Parallel Example: User Story 1

```bash
# Launch all four test files for US1 together:
Task: "Write LogoAssetLoaderTest.java — classpath + caching + missing-resource fail-soft"
Task: "Write BrandingOverlayServiceTest.java — geometry, MIME, dims, fail-soft, timing"
Task: "Write BrandingOverlayIT.java — @SpringBootTest end-to-end on real + fallback paths"
Task: "Write the micro-benchmark assertion block inside BrandingOverlayServiceTest (SC-707 regression guard)"
```

---

## Implementation Strategy

### MVP (US1 only)

1. Complete Phase 1 (T001, T002).
2. Write US1 tests (T003..T006); verify they fail.
3. Implement US1 (T007..T011); verify tests pass.
4. Manual quickstart walkthrough of step 2 in `quickstart.md` (frontend renders branded poster on real-provider run).
5. **Deployable as MVP** — booth attendees on a working API key see branded posters.

### Incremental Delivery

1. MVP landed above.
2. Add US2 test (T012). US2 flips to green immediately (T013 is verification-only).
3. Add US3 tests (T014, T015); implement T016. Fallback posters now also branded.
4. Polish (Phase 6). PR opens for human review (constitution step 8).

---

## Notes

- No entity lives in `model/`; `LogoAsset` is a service-internal record under `service/branding/` because it never crosses an HTTP or persistence boundary (data-model.md clarifies this).
- Every new class and test file listed above belongs to this feature and only this feature — no cross-feature edits outside `AlterEgoService.java` (one file, two lines).
- Commit after each task or logical group; open PR to close #16 after T021 is green.

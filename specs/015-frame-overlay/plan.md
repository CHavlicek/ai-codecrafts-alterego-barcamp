# Implementation Plan: Branded Poster Frame & 10×15 Print-Ready Aspect Ratio

**Branch**: `015-frame-overlay` | **Date**: 2026-05-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/015-frame-overlay/spec.md`

## Summary

Replace 008's two-logo top-right corner overlay with a single bundled **frame asset** (`backend/src/main/resources/branding/poster-frame.png`, 1024×1536 RGBA, transparent inner rectangle) that wraps every poster with the new event branding (SQUER mark, `<CODE/CRAFTS> 2026` wordmark, gradient rounded border, decorative chrome). At the same time, lock the generator's output to **portrait 2:3 (10×15)** by stating the ratio in the Gemini prompt and best-effort setting the typed `aspectRatio` parameter if the model accepts it; non-conforming provider responses are letterboxed inside the frame's inner rectangle (FR-1511) so the booth print station always receives a 2:3-shaped poster. Encoding tightens to **always PNG** so the frame's alpha channel composites cleanly. Fail-soft: if the frame asset is missing or compositing fails, the un-framed character image is still returned at 2:3 and a `WARN` line is logged. Mirrors 008's structural pattern (`*AssetLoader` → `*OverlayService` → wired into `AlterEgoService` on both real-provider and fallback paths) so the diff is structurally minimal.

## Technical Context

**Language/Version**: Java 21 (LTS) on the backend; TypeScript 5.x (strict) on the frontend — both unchanged.
**Primary Dependencies**: Spring Boot 3.x (existing); `javax.imageio.ImageIO` + `java.awt.Graphics2D` (JDK built-in, already used by 008's `BrandingOverlayService` and 001's `FallbackPosterProvider`); Jackson (existing, used to render the Gemini request body). **No new third-party dependency** — the spec's "no new image library" assumption holds.
**Storage**: N/A. FR-1501..FR-1513 inherit 001 FR-016/017/024 — no persistence. The composited bytes live in process memory for one HTTP request; the frame asset lives in process memory as an `@PostConstruct`-loaded singleton for the JVM lifetime, mirroring 008's `LogoAssetLoader`.
**Testing**: JUnit 5 + Mockito for unit tests (`PosterFrameOverlayService`, `PosterFrameAssetLoader`, `GeminiPromptBuilder` regression locks); Spring Boot Test (`@SpringBootTest`) for the integration test exercising the framing step end-to-end on both real-provider stub and fallback paths. Constitution Principle III gate: ≥90% line coverage on new code, ≥1 integration test exercising the full Generate journey.
**Target Platform**: Same backend runtime container as 003/008 (`eclipse-temurin:21-jre-alpine`). The headless JDK ships `Graphics2D` for in-memory `BufferedImage`; no X server required.
**Project Type**: Web application — `backend/` (Spring Boot) + `frontend/` (Vite/React TS). This feature is **backend-only**: the frontend `<img>` tag already renders the data URL the backend returns (002 poster slot).
**Performance Goals**: Median framing-step overhead < 100 ms, p95 < 250 ms on a typical developer laptop (SC-1508). Total `Generate press → poster rendered` budget is unchanged from 003 SC-207 (median < 15 s, p95 < 30 s).
**Constraints**: Constitution Principle I (no new CVEs), Principle III (TDD + 90% coverage + ≥1 integration test), Principle IV (resilient HTTP — unchanged; framing is purely local), Principle VI (zero deprecated deps). 001 FR-016 (no persistence) holds. 003 FR-216 (no extra identifying data to Gemini) holds — the frame is composited locally, never sent to Gemini.
**Scale/Scope**: One-shot booth pipeline; one Generate per attendee. No concurrency complexity beyond the existing `@Service` singleton wiring. Asset bundle adds ~180 KB of PNG to the backend artefact.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Gate | Verdict | Notes |
|---|---|---|
| **I. Modern & Secure Technology Stack** | ✅ PASS | No new dependency. Re-uses Java 21 + Spring Boot 3.x already on the backend; re-uses the JDK's `ImageIO` / `Graphics2D` already used by 008 and `FallbackPosterProvider`. |
| **III. Test-First Development (TDD, 90% coverage, ≥1 integration test)** | ✅ PASS (planned) | Phase 1 lists the failing-test set covering: prompt regression (2:3 wording for SINGLE and GROUP variants); frame asset loader (loaded / missing / decode-fail / RGBA preservation); frame overlay service (real path / fallback path / wrong-ratio corrective letterbox / missing-asset fail-soft / always-PNG output); end-to-end `@SpringBootTest` covering real-provider stub + fallback both arrive at 2:3 and carry the frame chrome. |
| **IV. Resilient HTTP Communication** | ✅ PASS | This feature does not introduce new outbound HTTP. The 003 `RetryTemplate` wrapping `imageGenerator.generate(...)` is unchanged. Local compositing has no retry semantics — fail-soft (FR-1512) returns the un-framed image instead. |
| **V. Feature Branch Workflow** | ✅ PASS | Branch `015-frame-overlay` cut from `main` via `create-new-feature.sh`; PR + explicit human approval required to merge. |
| **VI. Zero Deprecated Dependencies** | ✅ PASS | No dependency change. `npm audit` and `./gradlew dependencyCheckAnalyze` will run unchanged. |
| **Technology Standards (Backend table)** | ✅ PASS | Component is a Spring `@Service`/`@Component` using built-in JDK image stack. No deviation from the table. |
| **Test-stack constraint (`@SpringBootTest`, no production-profile mocking)** | ✅ PASS | Integration test wires the real `PosterFrameAssetLoader` + real `PosterFrameOverlayService` + an injected stub `GeminiClient` (the existing 003 test-fixture pattern); the framing path is exercised against actual classpath bytes. |

**Verdict**: All gates pass. No `Complexity Tracking` entries needed.

## Project Structure

### Documentation (this feature)

```text
specs/015-frame-overlay/
├── plan.md              # This file
├── research.md          # Phase 0 — pinned decisions (R-1501..R-1506)
├── data-model.md        # Phase 1 — PosterFrame, FramedPoster entities (replaces 008 LogoAsset/LogoId)
├── quickstart.md        # Phase 1 — manual verification + automated-test guide
├── contracts/           # Phase 1 — see note below; this feature does not change external contracts
└── tasks.md             # Phase 2 (created by /speckit.tasks)
```

> **Contracts directory note.** This feature does **not** introduce a new external contract:
> - The HTTP response shape (`AlterEgoResponse` from 003) is unchanged — the framed bytes ride inside the existing `Poster` field; `Content-Type` becomes always `image/png` (FR-1509) but the field shape and validation rules are unchanged.
> - The Gemini outbound request body adds at most one optional field (typed aspect-ratio parameter) inside the existing `generationConfig` block; the change is fully backward-compatible from the provider's side.
> Phase 1 leaves `contracts/` empty intentionally and the structural-decision narrative is captured inline in `data-model.md` and `research.md`.

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/aiavatar/alterego/
│   │   │   ├── service/
│   │   │   │   ├── AlterEgoService.java                       # MODIFIED: swap brandingOverlay → posterFrameOverlay
│   │   │   │   ├── branding/
│   │   │   │   │   ├── BrandingOverlayService.java            # DELETED (or kept as @Deprecated dead code; see R-1506)
│   │   │   │   │   ├── LogoAsset.java                         # DELETED
│   │   │   │   │   ├── LogoAssetLoader.java                   # DELETED
│   │   │   │   │   └── LogoId.java                            # DELETED
│   │   │   │   ├── frame/                                     # NEW package
│   │   │   │   │   ├── PosterFrameAsset.java                  # NEW: record { BufferedImage image; int innerX,Y,W,H; boolean loaded }
│   │   │   │   │   ├── PosterFrameAssetLoader.java            # NEW: @PostConstruct, ClassPathResource("branding/poster-frame.png")
│   │   │   │   │   └── PosterFrameOverlayService.java         # NEW: apply(PosterImage) → PosterImage (framed, PNG)
│   │   │   │   └── gemini/
│   │   │   │       ├── GeminiPromptBuilder.java               # MODIFIED: 3:4 → 2:3 (10×15 portrait) in both SINGLE and GROUP variants
│   │   │   │       └── GeminiClient.java                      # MODIFIED: add generationConfig.imageConfig.aspectRatio="2:3" (best-effort)
│   │   │   └── service/fallback/
│   │   │       └── FallbackPosterProvider.java                # MODIFIED: 900×1200 → 1024×1536 (2:3) so fallback already arrives at 2:3
│   │   └── resources/
│   │       └── branding/
│   │           ├── poster-frame.png                           # ADDED (already staged on this branch — RGBA, 1024×1536, transparent inner)
│   │           ├── squer-logo.png                             # DELETED (was used by 008; superseded — R-1506)
│   │           └── codecrafts-logo.png                        # DELETED (was used by 008; superseded — R-1506)
│   └── test/
│       └── java/com/aiavatar/alterego/
│           ├── service/
│           │   ├── AlterEgoServiceTest.java                   # MODIFIED: assert posterFrameOverlay invoked, brandingOverlay reference removed
│           │   ├── branding/                                  # DELETED directory (LogoAssetLoaderTest, BrandingOverlayServiceTest)
│           │   ├── frame/                                     # NEW package
│           │   │   ├── PosterFrameAssetLoaderTest.java        # NEW: loaded / missing / decode-fail / RGBA preservation
│           │   │   └── PosterFrameOverlayServiceTest.java     # NEW: real path / fallback path / wrong-ratio corrective / missing-asset / always-PNG
│           │   └── gemini/                                    # NEW directory
│           │       └── GeminiPromptBuilderTest.java           # NEW: regression-lock 2:3 wording in SINGLE and GROUP variants
│           └── integration/
│               └── AlterEgoFlowFrameIntegrationTest.java      # NEW @SpringBootTest: end-to-end real+fallback both arrive at 2:3 with frame chrome

frontend/
└── (unchanged)
```

**Structure Decision**: This is a backend-only delta on the existing 002-tabbed web app. The frontend's `<img>` element in the poster slot already renders whatever data URL the backend returns — no frontend file changes. The backend changes are scoped to one new package (`service/frame`), one prompt builder edit, one Gemini client edit (best-effort typed parameter), one fallback provider dimension bump, one `AlterEgoService` wiring swap, and the deletion of 008's `branding/` package. Asset additions: one PNG (already staged); deletions: two PNGs (the old logos).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitution violations. This section is intentionally empty.

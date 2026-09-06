# Implementation Plan: Hero Name, Title & Tagline on the Poster Image

**Branch**: `017-poster-text-overlay` | **Date**: 2026-05-06 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/017-poster-text-overlay/spec.md`

## Summary

Bake three lines into the poster PNG before it leaves the backend — the user's
first name (`Selections.firstName`), the hero title (`character.heroTitleLine1`),
and the tagline (`character.tagline`) — drawn into the dark "long-bottom"
region of the 015 frame asset that sits below the character cutout. Each line
is rendered on a single row at a configurable target size, shrunk as needed
to fit the safe horizontal width, and rendered with a strong drop-shadow /
outline halo so it remains legible over the chrome's decorative dot/circuit
patterns. The safe-area bounds are derived from the existing
`PosterFrameAsset` (the bottom region = canvas area beneath the
transparent inner cutout, with a small symmetric inset). On the frontend,
the `<img>` poster `alt` text is widened to carry all three strings (so
screen-reader users still hear the hero's identity without depending on
the rasterised text), the visible HTML rendering of `heroTitleLine1` and
`tagline` (and the looping `title-shimmer` animation on the name) is
removed, and `heroTitleLine2` remains as the only on-screen HTML heading
next to the poster.

The feature reuses the established 008 / 015 composition pattern (decode →
draw → encode PNG, fail-soft on any error) and adds **one** new backend
service (`PosterTextOverlayService`) plus a small AWT text-fitting helper.
No new HTTP call, no new request/response field, no persistence, no new
third-party library.

## Technical Context

**Language/Version**: Java 21 (LTS) backend; TypeScript 5.x strict frontend — both unchanged.
**Primary Dependencies**: Spring Boot 3.5 (existing); `javax.imageio.ImageIO` + `java.awt.Graphics2D` + `java.awt.font.TextLayout` / `FontMetrics` (JDK built-in, already used by 008's `BrandingOverlayService` and 015's `PosterFrameOverlayService`); React 19 + Vite 8 + Vitest + RTL on the frontend (existing). **No new third-party runtime or test dependency.**
**Storage**: N/A. FR-1701..FR-1716 inherit 001 FR-016 / 015 FR-1501..FR-1513 unchanged — strings live in process memory for one Generate request only; the bundled poster-frame asset and any bundled font asset live in process memory as `@PostConstruct` singletons for the JVM lifetime.
**Testing**: Backend — JUnit 5 + Mockito (unit) and Spring Boot Test + WireMock (integration), covering the new `PosterTextOverlayService` and the extended `AlterEgoService` wiring. Frontend — Vitest + React Testing Library covering `PosterView`, `AlterEgoPanel`, and an updated `AlterEgoPage` test for the alt-text contract.
**Target Platform**: Linux server (backend Docker on `eclipse-temurin:21-jre-alpine`); evergreen browsers (frontend), unchanged.
**Project Type**: Web application — `backend/` (Java/Spring) + `frontend/` (React/TS), both already populated by 001..016.
**Performance Goals**: One additional `BufferedImage` decode + draw + PNG encode at 1024×1536 ARGB after the existing frame overlay step. Worst-case ≈ 10–20 ms on a warm JVM; well inside the 20-second end-to-end progress budget set by 013 / FR-1305 and the 30-second fal.ai per-attempt budget set by 016.
**Constraints**: No persistence (FR-1711). Fail-soft (FR-1716 + R-1504 carryover): if the bundled font is missing or text rendering throws, return the previously-framed poster bytes unchanged + a structured WARN line, mirroring 015's `event=frame.apply.skipped/failed` discipline. No log line ever carries the rendered strings (FR-1711). Coverage gate ≥ 90% for the new service.
**Scale/Scope**: Single-user POC; one Generate per HTTP request; no concurrency or scale concerns beyond existing image pipeline. Code scope: ~250 LOC backend (one service + one value record + one helper) + ~30 LOC frontend deletes/edits.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Gates derived from constitution v1.0.2 — Core Principles + Technology Standards.

| Principle / Standard | Compliance |
|---|---|
| **I. Modern & Secure Technology Stack** | ✅ Java 21 + Spring Boot 3.5 (backend) and React 19 + TS strict + Vite 8 (frontend) unchanged. No deprecated/CVE-flagged additions. |
| **III. Test-First Development (TDD — NON-NEGOTIABLE)** | ✅ Plan calls for failing-tests-first commits across `PosterTextOverlayServiceTest` (unit), `GenerateAlterEgoTextOverlayIT` (integration), and `PosterView.test.tsx` (component) before any production code lands. Coverage ≥ 90% for the new service is explicitly designed in (every branch — short input, long input, hierarchy clamp, missing-font fail-soft, missing-asset fail-soft — has a test). At least one integration test exercises the full Generate journey end-to-end (`@SpringBootTest` + WireMock for fal.ai + `MockMultipartFile` photo). Production-profile wiring is not mocked away — `AlterEgoService` is wired with the real `PosterFrameOverlayService` + `PosterTextOverlayService`. |
| **IV. Resilient HTTP Communication** | ✅ Not applicable to new code paths (this feature does not introduce a new outbound HTTP call). The existing retry/fallback discipline around `CharacterGenerator` and `ImageGenerator` is unchanged. The new text overlay step has its own equivalent fail-soft (return un-textified bytes + WARN), modelled on 015's pattern. |
| **V. Feature Branch Workflow** | ✅ Working on `017-poster-text-overlay` cut from `main`; PR-with-explicit-approval before merge per workflow. |
| **VI. Zero Deprecated Dependencies** | ✅ No new Maven/npm dependency. Optional bundled font (see research.md R0) is a static `.ttf` asset, not a package — no advisory surface area added. `npm audit` and `./gradlew dependencyCheckAnalyze` will be re-run before merge per workflow step 6. |
| **Tech standards — Backend** | ✅ All work happens in existing Spring Boot service classes; uses JDK-built-in `java.awt`. JUnit 5 + Mockito for unit tests; `@SpringBootTest` for integration tests. |
| **Tech standards — Frontend** | ✅ React 19 + TypeScript strict; Vitest + RTL for tests; native `fetch` via existing resilient client unchanged; ESLint + Prettier pass. |

**Result**: All gates pass — no Complexity Tracking entries required.

## Project Structure

### Documentation (this feature)

```text
specs/017-poster-text-overlay/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan)
├── data-model.md        # Phase 1 output (/speckit.plan)
├── quickstart.md        # Phase 1 output (/speckit.plan)
├── contracts/
│   └── README.md        # Public contract delta (none — explicit "no API change" note)
├── checklists/
│   └── requirements.md  # /speckit.specify + /speckit.clarify validation
├── spec.md              # /speckit.specify + /speckit.clarify outputs
└── tasks.md             # Phase 2 output (/speckit.tasks — NOT created here)
```

### Source Code (repository root)

```text
backend/
└── src/
    ├── main/
    │   ├── java/com/aiavatar/alterego/
    │   │   ├── service/
    │   │   │   ├── AlterEgoService.java         # MODIFY — wire PosterTextOverlayService after frame overlay
    │   │   │   ├── frame/
    │   │   │   │   ├── PosterFrameAsset.java       # MODIFY — extend with bottomRegion bounds
    │   │   │   │   └── PosterFrameAssetLoader.java # MODIFY — derive bottom-region bounds at load time
    │   │   │   └── text/
    │   │   │       ├── PosterTextOverlayService.java  # NEW
    │   │   │       ├── PosterTextLines.java           # NEW (record: firstName, title, tagline)
    │   │   │       ├── PosterTextStyle.java           # NEW (record: per-line target size + colour + outline + hierarchy invariant)
    │   │   │       ├── PosterTextFitter.java          # NEW (pure helper: shrink-only width fit with hierarchy clamp)
    │   │   │       └── PosterTextFontLoader.java      # NEW (loads bundled font via @PostConstruct, fail-soft to logical SansSerif)
    │   │   └── model/
    │   │       └── (no changes — request/response shape unchanged)
    │   └── resources/
    │       └── branding/
    │           ├── poster-frame.png             # UNCHANGED (Q5 — no asset modification)
    │           └── fonts/
    │               └── Inter.ttf                # NEW bundled font (open-licence; one file)
    └── test/
        └── java/com/aiavatar/alterego/
            ├── service/
            │   ├── frame/
            │   │   └── PosterFrameAssetLoaderTest.java   # MODIFY — assert bottomRegion bounds
            │   └── text/
            │       ├── PosterTextOverlayServiceTest.java # NEW
            │       ├── PosterTextFitterTest.java         # NEW
            │       └── PosterTextFontLoaderTest.java     # NEW
            └── integration/
                └── GenerateAlterEgoTextOverlayIT.java    # NEW — end-to-end pixel assertion

frontend/
└── src/
    └── features/alterego/
        ├── components/
        │   ├── PosterView.tsx                    # MODIFY — remove visible h2/title-line-1 + tagline, widen alt text
        │   ├── PosterView.test.tsx               # MODIFY — assert removed lines, widened alt
        │   ├── AlterEgoPanel.tsx                 # MODIFY only if PosterView prop signature changes (firstName)
        │   ├── PrintArtefact.tsx                 # MODIFY mirroring PosterView changes (010 print path)
        │   └── PrintArtefact.test.tsx            # MODIFY mirroring PosterView test changes
        ├── AlterEgoPage.tsx                      # MODIFY only if firstName threading needs touching
        └── AlterEgoPage.test.tsx                 # MODIFY for new alt-text expectation
└── src/
    └── index.css                                 # MODIFY — drop .poster-view__title-line-1 + .poster-view__tagline + @keyframes title-shimmer
```

**Structure Decision**: Web application split (`backend/` + `frontend/`) — already
established by 001..016. New backend code is isolated under `service/text/`
mirroring the existing `service/frame/` package layout from 015 so the two
overlay stages sit side-by-side with parallel asset/loader/service shapes.
No top-level reorganisation; no new modules.

## Complexity Tracking

> No Constitution Check violations — this section is intentionally left empty.

## Phase 0: Outline & Research

See [research.md](./research.md). Key open questions resolved there:

- **R0** Font availability inside `eclipse-temurin:21-jre-alpine` and the choice between (a) JDK logical "SansSerif" and (b) a bundled `.ttf`. **Decision: bundle Inter (open-licence)** so output is deterministic across hosts and Docker images, and to dodge the Alpine `fontconfig` story.
- **R1** Drop-shadow / outline rendering technique in `Graphics2D`. **Decision: stroke-then-fill the glyph outline** (`TextLayout.getOutline(...)` → `g.setStroke(BasicStroke); g.draw(outline); g.fill(outline)`) — sharp, predictable, and resolution-independent unlike `BufferedImageOp` blur.
- **R2** Where to compute the bottom-region safe area. **Decision: derive it in `PosterFrameAssetLoader`** at startup (alongside the existing alpha-zero scan) and add fields to `PosterFrameAsset`. Mirrors 015's "asset-derived geometry" idea.
- **R3** Order of services in `AlterEgoService`. **Decision: text overlay runs *after* frame overlay** so text sits on top of the chrome's decorative patterns (Q5 → drop-shadow legibility, no asset change).
- **R4** Frontend wiring for the wider `alt` text. **Decision: thread `firstName` into `PosterView`** as a prop sourced from the session reducer's last-submitted selections (already retained for Start Over).
- **R5** Pixel-level integration-test assertion. **Decision: assert that the bottom region of the response PNG contains a non-trivial number of light pixels** (proxy for "text was drawn") rather than full OCR; deterministic and avoids OCR dependency.

## Phase 1: Design & Contracts

### Data model

See [data-model.md](./data-model.md). Summary:

- **No public-API change.** The HTTP request and response shapes are identical to 016. The user-facing change is the pixel content of the response's `poster.dataUrl` plus the on-screen layout of the "Your Alter Ego" tab.
- **New backend value records** (in-process only, no JSON exposure):
  - `PosterTextLines(firstName, title, tagline)` — the strings to be rendered.
  - `PosterTextStyle(...)` — target font sizes per role + light/dark palette + outline width.
- **`PosterFrameAsset` gains** `bottomRegionX/Y/Width/Height` fields, computed once by the loader from the alpha-zero bbox + canvas dimensions.

### Contracts

See [contracts/README.md](./contracts/README.md) — no contract change. The
file is a deliberate "no-delta" note so future readers know the OpenAPI
spec was not touched.

### Quickstart

See [quickstart.md](./quickstart.md) for: how to verify the feature
locally (start backend + frontend, generate, eyeball the bottom region,
print preview), how to run the new tests, and how to swap the font asset.

### Agent context update

After writing the artifacts, run
`.specify/scripts/bash/update-agent-context.sh claude` to refresh
`CLAUDE.md`'s "Active Technologies" / "Recent Changes" sections so future
sessions inherit this feature's context.

---

**Re-evaluated Constitution Check after Phase 1 design**: Still passing —
no new violations introduced by the data model or composition order.

# Implementation Plan: Logo Branding Overlay on Generated Alter-Ego Images

**Branch**: `008-logos-on-generated-images` | **Date**: 2026-04-24 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/008-logos-on-generated-images/spec.md`

## Summary

Every poster the backend returns (real Gemini output **and** fallback stub) is branded with the SQUER logo in the top-right corner and the CodeCrafts logo directly below it, both rendered at equal width and proportional margins. The overlay runs on the backend in a single post-generation transform that sits on the seam where the real and fallback paths converge (inside `AlterEgoService.generate()`), so both paths get branded by a single call. The logos are shipped as classpath resources under `backend/src/main/resources/branding/`, loaded and decoded once at bean initialization, and reused across requests; they are never sent to Gemini. If compositing fails (missing asset, decode error, `Graphics2D` exception) the un-branded poster is returned and a `WARN` log line is emitted — users never see a broken poster because of a branding bug (FR-711).

Technical approach:

- One new `@Component` — `BrandingOverlayService` — with a single public entry point `apply(PosterImage): PosterImage`.
- `AlterEgoService` calls `brandingOverlayService.apply(poster)` on both the real path (after `imageGenerator.generate(...)`) and the fallback path (after `fallbackProvider.poster(...)`) before constructing `AlterEgoResponse.Poster.fromImage(...)`.
- Compositing uses the JDK-built-in `BufferedImage` + `Graphics2D` + `ImageIO` stack (no new dependency).
- Logo width **15%** of output image width, top/right margin **4%** each, inter-logo gap **2%** — all pinned inside the ranges given in spec SC-705. Minimum readable logo width floor: **48 px**.

## Technical Context

**Language/Version**: Java 21 (LTS) backend only. No frontend change.
**Primary Dependencies**: Spring Boot 3.x (existing); `javax.imageio.ImageIO` + `java.awt.Graphics2D` (JDK built-in; no new dependency). No new third-party image library — the existing runtime's 2D graphics stack is sufficient (spec Assumption: Rendering quality).
**Storage**: N/A. FR-016/017/020/024 continue to apply; the composited bytes live in process memory for the duration of the request only; the decoded logo `BufferedImage` instances live in process memory for the lifetime of the backend process as part of the artefact, not user data (spec Assumption: No persistence).
**Testing**: JUnit 5 + Mockito (unit, existing), Spring Boot Test `@SpringBootTest` (integration, existing). No Testcontainers needed — this feature has no DB, network, or container-only concern.
**Target Platform**: `eclipse-temurin:21-jre-alpine` container (existing deployment image). Alpine ships a headless JRE; Graphics2D for in-memory `BufferedImage` + `ImageIO` PNG/JPEG encoders run on headless JREs without additional native libs.
**Project Type**: Web application backend module (Option 2 — `frontend/` + `backend/` already present).
**Performance Goals**: SC-707 — compositing overhead median < 100 ms, p95 < 250 ms on a typical developer laptop. Overall end-to-end bound inherited from 003 SC-207 (`Generate press → poster rendered`: median < 15 s, p95 < 30 s) MUST NOT regress.
**Constraints**: FR-704 (zero logo bytes/URLs/filenames in outbound Gemini requests — prompt text unchanged from 003 FR-201). FR-709 (preserve original pixel dimensions). FR-710 (preserve original MIME type). FR-711 (fail-soft).
**Scale/Scope**: Single-user booth demo — one concurrent Generate at a time. Scale considerations are not the bottleneck.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Initial Gate (pre-research)

| Principle | Status | Notes |
|---|---|---|
| I — Modern & Secure Technology Stack | ✅ Pass | Feature introduces **zero** new third-party dependencies. Uses only the JDK 21 built-in `java.awt` / `javax.imageio` primitives already available on `eclipse-temurin:21-jre-alpine`. No new npm package on the frontend (frontend is untouched). No deprecated or CVE-flagged packages added. |
| III — Test-First Development (TDD) | ✅ Pass | Phase 1 artefacts enumerate the failing tests (unit + integration) that MUST be written and reviewed before production code. Tests cover FR-701..FR-713 and SC-701..SC-707. Integration test uses `@SpringBootTest` per the constitution; no mocking of production-profile wiring. Coverage gate (≥ 90%) held by adding unit tests for every branch in `BrandingOverlayService`. |
| IV — Resilient HTTP Communication | ✅ Pass | **Not directly applicable** — this feature adds no new outbound HTTP call. The existing `GeminiClient` retry/fallback (003) is untouched. FR-711's fail-soft behaviour (return un-branded image + WARN log) is the in-process analogue of Principle IV's "stub fallback response" posture. |
| V — Feature Branch Workflow | ⚠️ Noted (not a violation) | Spec lives under `specs/008-logos-on-generated-images/` (SpecKit prefix-based lookup); actual development is carried out on the Claude-assigned branch `claude/implement-speckit-feature-da7Wt` per task constraints, then landed via PR with human approval (constitution step 8–9). The PR will close issue #16. |
| VI — Zero Deprecated Dependencies | ✅ Pass | No new dependency added → nothing to audit. Existing `npm audit` / `dependencyCheckAnalyze` status unchanged. |

**Result**: No violations. Proceed to Phase 0.

### Post-Design Re-check

Re-evaluated after drafting `research.md`, `data-model.md`, `quickstart.md`:

- No deviation from the gate. No new third-party dependency was introduced in Phase 0/1. TDD order is preserved in the upcoming `tasks.md`. The agent context file is updated to reflect Java 21 graphics usage, with no new stack entry.
- Complexity Tracking section remains empty (no exceptions).

**Result**: Post-design gate passed. Ready for `/speckit.tasks`.

## Project Structure

### Documentation (this feature)

```text
specs/008-logos-on-generated-images/
├── plan.md                    # This file
├── research.md                # Phase 0 — decisions + alternatives
├── data-model.md              # Phase 1 — LogoAsset, BrandedPoster
├── quickstart.md              # Phase 1 — how to verify locally
├── checklists/
│   └── requirements.md        # spec quality checklist (from /speckit.specify)
└── tasks.md                   # /speckit.tasks output (NOT created here)
```

No `contracts/` directory — this feature is an internal backend transform; it changes no request or response shape. The public HTTP contract (`POST /api/alter-ego`, `AlterEgoResponse`) is inherited from 001/002/003 unchanged.

### Source Code (repository root)

```text
backend/
├── src/
│   ├── main/
│   │   ├── java/com/aiavatar/alterego/
│   │   │   └── service/
│   │   │       ├── AlterEgoService.java                 # MODIFIED — inject BrandingOverlayService; call .apply() on both paths
│   │   │       └── branding/                            # NEW PACKAGE
│   │   │           ├── BrandingOverlayService.java      # NEW — overlay entry point, fail-soft
│   │   │           └── LogoAssetLoader.java             # NEW — classpath load + decode, cached
│   │   └── resources/
│   │       └── branding/                                # NEW DIRECTORY
│   │           ├── squer-logo.png                       # NEW — copied from repo root, renamed
│   │           └── codecrafts-logo.png                  # NEW — copied from repo root, renamed
│   └── test/
│       └── java/com/aiavatar/alterego/
│           ├── service/
│           │   └── branding/                            # NEW PACKAGE
│           │       ├── BrandingOverlayServiceTest.java  # NEW — unit tests: geometry, MIME, dims, fail-soft
│           │       └── LogoAssetLoaderTest.java         # NEW — unit tests: classpath loading, caching
│           └── integration/
│               └── BrandingOverlayIT.java               # NEW — @SpringBootTest end-to-end (real + fallback paths branded)

frontend/                                                # UNTOUCHED — FR-705
```

**Structure Decision**: Backend-only module expansion. A new `com.aiavatar.alterego.service.branding` sub-package keeps the overlay concern local (one seam in `AlterEgoService`, two new collaborators). Logo binaries live at `backend/src/main/resources/branding/` so they are bundled with the JAR and served via `ClassPathResource` — the deployment image needs no extra volume mount or download step.

## Complexity Tracking

> **No constitution violations. This table is empty by design.**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |

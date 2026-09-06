# Implementation Plan: Initial POC — AI Alter Ego Generator

**Branch**: `001-initial-poc` | **Date**: 2026-04-21 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/001-initial-poc/spec.md`

## Summary

Build a full-stack POC of the AI Alter Ego generator: the user supplies a photo + a pose + a colour + an archetype + a universe + a first name, and receives a hero-style poster with their face on it plus generated character text (hero title, tagline, three superpowers, quote). The third-party character-generation and image-generation integrations are **stubbed on the backend**; the UI exercises the real end-to-end flow (React ↔ Spring Boot, resilient HTTP, fallback poster on failure). No persistence anywhere: the photo is held in process memory for one request's duration and discarded. WCAG 2.1 AA is the accessibility baseline.

The technical approach: Spring Boot 3 backend (Java 21, Gradle Kotlin DSL) exposing a single `POST /api/v1/alter-egos` multipart endpoint; stubbed `CharacterGenerator` + `ImageGenerator` interfaces behind a service layer so real providers slot in later. React 18 + TypeScript strict frontend (Vite) with a feature-scoped `alterego` module, `useReducer` + Context for the session state, a resilient `fetch` wrapper, and Playwright E2E + `@axe-core/playwright` for the WCAG gate.

## Technical Context

**Language/Version**:
- Backend: Java 21 (LTS) — constitutional Principle I / Technology Standards.
- Frontend: TypeScript 5.x (strict mode) + React 18+ — constitutional Principle I / Technology Standards. **Note**: the repo root currently holds a Vite + React 19 JSX scaffold. That scaffold will be absorbed into `frontend/` during task T002/T003 and migrated to TS strict; it does not satisfy the constitution as-is.

**Primary Dependencies**:
- Backend: Spring Boot 3.x, Spring Web, Spring Validation, Spring Retry (`RetryTemplate`), Micrometer, SLF4J + Logback, JUnit 5, Mockito, `@SpringBootTest`.
- Frontend: React 18, Vite (current major), TanStack Query v5 (for the generate mutation + its retry/fallback semantics), `useReducer` + `React.Context` for UI state (Zustand deferred — see research.md R3), Vitest + React Testing Library, Playwright, `@axe-core/playwright`, `eslint-plugin-jsx-a11y`.

**Storage**: N/A for this POC. FR-016 + FR-020 + FR-024 explicitly require **no persistence** — not to disk, database, cache, or logs. The SQLite / JPA / Hibernate stack pinned by the constitution's Technology Standards is intentionally **not wired** in this feature; it lands in a later feature when real persistence is required.

**Testing**:
- Backend unit: JUnit 5 + Mockito (`backend/src/test/java/com/aiavatar/alterego/*Test.java`).
- Backend contract: `@SpringBootTest(webEnvironment = MOCK)` + MockMvc or `WebTestClient` hitting the controller (`*ContractTest.java`).
- Backend integration: `@SpringBootTest(webEnvironment = RANDOM_PORT)` with the full stub pipeline exercised end-to-end (`*IT.java`).
- Frontend unit/component: Vitest + React Testing Library, colocated as `*.test.tsx`.
- Frontend E2E + a11y: Playwright + `@axe-core/playwright` (`frontend/tests/e2e/*.spec.ts`).

**Target Platform**:
- Runtime: Docker containers per constitutional Deployment table (`eclipse-temurin:21-jre-alpine` backend, `nginx:alpine` frontend static). Docker Compose at repo root for dev.
- User: modern desktop/tablet browser with camera + `<input type="file">` support (Assumptions § spec.md).

**Project Type**: Web application (frontend + backend). Triggers Project Structure Option 2.

**Performance Goals**:
- SC-001: ≤ 3 s end-to-end from Generate → poster rendered, p95, stubs on.
- SC-004: ≤ 5 s to fallback poster when stubs are forced to fail.
- SC-006: ≤ 200 ms "Start over" reset.
- Backend request budget (internal): ≤ 500 ms p95 for `/api/v1/alter-egos` including the stub generators; leaves ≥ 2.5 s headroom for network + render.

**Constraints**:
- **No persistence anywhere** (FR-016, FR-020, FR-024).
- **No real third-party service reachable** during normal operation (FR-014).
- **No API credentials in the frontend** (FR-017).
- Resilient HTTP per Principle IV: 5 attempts with exponential back-off + randomised jitter, visible fallback on final failure (FR-018, FR-019).
- WCAG 2.1 AA (FR-020..FR-023, SC-007). Enhanced a11y (reduced-motion, focus trap/restore, high-contrast mode, keyboard-operable camera capture) deferred per FR-026.
- Unit coverage ≥ 90% on both sides (Principle III).
- Zero HIGH/CRITICAL CVEs at merge time (Principle VI).

**Scale/Scope**:
- POC, not production traffic. Designed for a single-digit concurrent demo load; no horizontal-scale targets.
- Feature surface: 1 backend endpoint, 1 frontend route, ~12 React components, ~6 Java classes under `com.aiavatar.alterego.*`.

**Decisions deferred from /speckit.clarify and resolved in Phase 0** (see [research.md](./research.md)):
1. Observability baseline for the POC.
2. Stub response strategy (character + image).
3. Photo transport format and size constraints.
4. Accessibility tooling specifics (how SC-007 is actually checked).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

**Principle I — Modern & Secure Technology Stack (NON-NEGOTIABLE)** ✅
- React 18+, TypeScript strict, Vite (latest major), Spring Boot 3.x on Java 21. No deprecated packages planned.
- Action: `npm audit` + `./gradlew dependencyCheckAnalyze` run in CI; Principle VI gate applies at merge.

**Principle III — Test-First Development (NON-NEGOTIABLE)** ✅
- tasks.md (generated next by `/speckit.tasks`) will order tests before implementation per the repo's `tasks-template.md`.
- Every FR has a testable acceptance path; SC-005's 4-D test matrix (pose × colour × archetype × universe) becomes a parameterised snapshot/contract test on the stub output.
- Integration test exercising the full user journey: Playwright E2E covering photo intake → selections → Generate → success poster → Start over (and a force-failure variant for SC-004).
- Coverage gate ≥ 90% on both sides. Backend integration tests use `@SpringBootTest` with the real stub pipeline (not mocked away) per Principle III.

**Principle IV — Resilient HTTP Communication** ✅
- Frontend: `resilientFetch` wrapper using TanStack Query's `retry: 5` + exponential back-off + randomised jitter; on final failure the mutation returns a canned fallback payload that maps to an offline poster.
- Backend: Spring `RetryTemplate` around future real-provider calls; in this POC the stubs don't make HTTP calls, so the retry policy is wired but has no real-world effect until a real provider lands. A deliberate failure-injection toggle (`spring.profiles.active=force-stub-failure`) exists for test runs that need to validate SC-004.
- Error surface: FR-023 requires error messages to be programmatically associated and politely announced.

**Principle V — Feature Branch Workflow** ✅
- On `001-initial-poc`, cut from `main`, compliant with constitution v1.0.2 wording.

**Principle VI — Zero Deprecated Dependencies** ✅
- All picks are current majors; no known HIGH/CRITICAL CVEs. Pinning: exact minor in `package.json` (`~18.3.1`-style) and Gradle BOM-driven versions for the Spring ecosystem.

**Technology Standards table compliance** ✅
- Every pick above corresponds to an Approved Choice in the constitution's Frontend / Backend / Deployment tables.
- Intentional non-picks (flagged, not violations):
  - **SQLite / Spring Data JPA / Hibernate**: not wired in this feature because no persistence is in scope. Lands in a follow-up feature.
  - **Spring Security + JWT**: not in scope — POC has no auth.
  - **Redis cache**: not in scope — no caching in scope.
  - **Recharts, Lucide React, react-grid-layout**: not in scope for this feature — no charts, minimal iconography (SVG-inline), no grid layout.

No gate violations → proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-initial-poc/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   └── alter-egos.openapi.yaml
├── checklists/
│   └── requirements.md  # from /speckit.specify + /speckit.clarify
└── spec.md              # feature specification
```

### Source Code (repository root)

```text
backend/
├── build.gradle.kts
├── settings.gradle.kts
└── src/
    ├── main/
    │   ├── java/com/aiavatar/alterego/
    │   │   ├── AlterEgoApplication.java         # @SpringBootApplication
    │   │   ├── controller/
    │   │   │   └── AlterEgoController.java      # POST /api/v1/alter-egos (multipart)
    │   │   ├── service/
    │   │   │   ├── AlterEgoService.java         # orchestrates character + image generators
    │   │   │   ├── CharacterGenerator.java      # interface
    │   │   │   ├── ImageGenerator.java          # interface
    │   │   │   ├── stub/
    │   │   │   │   ├── StubCharacterGenerator.java
    │   │   │   │   └── StubImageGenerator.java
    │   │   │   └── fallback/
    │   │   │       └── FallbackPosterProvider.java   # canned content for FR-018
    │   │   ├── model/
    │   │   │   ├── AlterEgoRequest.java          # record: selections + photo metadata
    │   │   │   ├── AlterEgoResponse.java         # record: GeneratedCharacter + poster data URL
    │   │   │   ├── GeneratedCharacter.java       # record
    │   │   │   ├── Pose.java / Colour.java / Archetype.java / Universe.java  # enums
    │   │   │   └── PhotoPayload.java             # in-memory holder, cleared post-request
    │   │   └── config/
    │   │       ├── WebConfig.java                # CORS (dev)
    │   │       ├── MultipartConfig.java          # 5MB limit
    │   │       └── RetryConfig.java              # Spring Retry template (Principle IV)
    │   └── resources/
    │       ├── application.yml
    │       ├── logback-spring.xml                # JSON structured logs + request correlation
    │       └── stubs/posters/                    # pre-rendered PNG fixtures keyed by archetype × colour
    └── test/
        ├── java/com/aiavatar/alterego/
        │   ├── unit/                             # pure unit tests
        │   ├── contract/
        │   │   └── AlterEgoControllerContractTest.java
        │   └── integration/
        │       └── GenerateAlterEgoIT.java       # @SpringBootTest full pipeline
        └── resources/
            └── fixtures/                         # sample photos + expected outputs

frontend/
├── package.json
├── vite.config.ts                                # dev proxy /api → http://localhost:8080
├── tsconfig.json                                 # strict: true
├── eslint.config.js                              # flat config + jsx-a11y
├── .prettierrc
├── index.html
└── src/
    ├── main.tsx
    ├── App.tsx
    ├── features/alterego/
    │   ├── components/
    │   │   ├── PhotoIntake.tsx                   # camera + upload
    │   │   ├── PoseGrid.tsx
    │   │   ├── ColourGrid.tsx                    # FR-021: name labels alongside swatch
    │   │   ├── ArchetypeGrid.tsx
    │   │   ├── UniverseGrid.tsx
    │   │   ├── FirstNameInput.tsx
    │   │   ├── GenerateButton.tsx                # gating per FR-009/FR-010
    │   │   ├── GenerationLoading.tsx             # ARIA live region (FR-022)
    │   │   ├── PosterView.tsx                    # rendered result
    │   │   └── StartOverButton.tsx
    │   ├── hooks/
    │   │   ├── useAlterEgoSession.ts             # useReducer + Context
    │   │   └── useGenerateAlterEgo.ts            # TanStack Query mutation + resilient client
    │   ├── services/
    │   │   └── alterEgoClient.ts                 # multipart POST, retry + jitter + fallback
    │   ├── fallback/
    │   │   └── fallbackPoster.ts                 # canned content mirror of backend fallback
    │   ├── state/
    │   │   ├── reducer.ts
    │   │   └── context.tsx
    │   └── types.ts                              # AlterEgoRequest / Response shape
    ├── components/
    │   ├── LiveRegion.tsx                        # shared ARIA polite live region
    │   └── VisuallyHidden.tsx
    ├── lib/
    │   └── resilientFetch.ts                     # retry + back-off + jitter + fallback contract
    └── styles/
        └── tokens.css                            # dark theme, pink/purple accents carried from design
└── tests/
    ├── e2e/
    │   ├── generate-happy-path.spec.ts           # SC-001..SC-003, SC-005 sample
    │   ├── generate-fallback.spec.ts             # SC-004
    │   ├── keyboard-walkthrough.spec.ts          # SC-007 keyboard-only
    │   └── axe-scan.spec.ts                      # SC-007 automated a11y scan
    └── integration/                              # cross-component / hook tests when needed
```

**Structure Decision**: Web-app layout (Option 2 from the plan template). `backend/` hosts the Java 21 + Spring Boot 3 API; `frontend/` hosts the React 18 + TS (strict) + Vite UI. The existing repo-root Vite + React 19 JSX scaffold (`src/`, `index.html`, `vite.config.js`, `package.json`, `public/`, `eslint.config.js`) will be absorbed into `frontend/` during setup tasks — that absorb step includes migrating `App.jsx` / `main.jsx` to TS strict, converting the ESLint flat config to include `jsx-a11y`, and re-pinning dependencies. No source code is lost; the scaffold just moves.

A single `docker-compose.yml` at the repo root wires the two services for dev, per the constitutional Deployment table.

## Post-Phase-1 Constitution Re-Check

*Completed after Phase 1 design artifacts (data-model.md, contracts/, quickstart.md) were produced. See the end of those artifacts for any amendments that fed back here.*

**Status**: all five in-scope principles remain green after Phase 1. The data-model's in-memory-only lifetime and the OpenAPI contract's multipart schema jointly satisfy FR-014..FR-017 and FR-020/FR-024 (no-persistence constraints) without introducing any new dependency choices that would require a MINOR constitution amendment. The Complexity Tracking section stays empty.

## Complexity Tracking

*No gate violations recorded. This section intentionally empty.*

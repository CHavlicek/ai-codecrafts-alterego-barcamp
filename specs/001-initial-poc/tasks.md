---
description: "Task list for 001-initial-poc — AI Alter Ego Generator POC"
---

# Tasks: Initial POC — AI Alter Ego Generator

**Input**: Design documents from `specs/001-initial-poc/`
**Prerequisites**: `plan.md` ✓, `spec.md` ✓, `research.md` ✓, `data-model.md` ✓, `contracts/alter-egos.openapi.yaml` ✓, `quickstart.md` ✓

**Tests**: MANDATORY for every feature per **Principle III of the project constitution** (Test-First Development, NON-NEGOTIABLE) and per **SC-007** of the feature spec. Every test task below MUST be authored before its corresponding implementation task, committed failing, and only then turned green by the implementation task. Unit line coverage MUST reach ≥ 90% on both sides; every feature MUST include at least one end-to-end integration test (Playwright happy path + fallback variant).

**Organization**: The spec has a single P1 user story (US1 — "Generate a personalised AI Alter Ego"). Phase 3 is therefore the complete MVP; there is no Phase 4 / Phase 5 for this feature.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: `[US1]` for user-story tasks; omitted for Setup / Foundational / Polish tasks
- Every task includes an exact file path

## Path Conventions

Web-app layout, locked in by `plan.md` and the project constitution:

- **Backend (Java 21 + Spring Boot 3, Gradle Kotlin DSL)**
  - Production sources: `backend/src/main/java/com/aiavatar/alterego/...`
  - Unit tests: `backend/src/test/java/com/aiavatar/alterego/unit/*Test.java`
  - Contract tests: `backend/src/test/java/com/aiavatar/alterego/contract/*ContractTest.java`
  - Integration tests: `backend/src/test/java/com/aiavatar/alterego/integration/*IT.java`
  - Resources / fixtures: `backend/src/main/resources/...`
- **Frontend (React 18 + TypeScript strict, Vite)**
  - Feature module: `frontend/src/features/alterego/...`
  - Shared components / lib: `frontend/src/components/...`, `frontend/src/lib/...`
  - Component/unit tests: colocated `*.test.ts` / `*.test.tsx`
  - E2E + a11y tests: `frontend/tests/e2e/*.spec.ts`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Scaffold the two-service layout, pin the approved toolchain, and absorb the existing repo-root Vite scaffold into `frontend/`.

- [X] T001 Create `backend/` and `frontend/` directory structure per `plan.md`. Move the existing repo-root Vite scaffold into `frontend/`: `src/` → `frontend/src/`, `public/` → `frontend/public/`, `index.html` → `frontend/index.html`, `vite.config.js` → `frontend/vite.config.ts` (convert to TS during T003), `package.json` → `frontend/package.json`, `eslint.config.js` → `frontend/eslint.config.js`. Delete the now-empty repo-root duplicates. Remove `aiavatar.iml` if it resurfaces (already in `.gitignore`).
- [X] T002 Initialize backend: Gradle (Kotlin DSL) + Spring Boot 3.x on Java 21 — create `backend/settings.gradle.kts` (project `alter-ego-backend`) and `backend/build.gradle.kts` (plugins: `org.springframework.boot` 3.x, `io.spring.dependency-management`, `java`; dependencies: `spring-boot-starter-web`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-retry` + `spring-aspects`, `net.logstash.logback:logstash-logback-encoder`, `io.micrometer:micrometer-core`; test: `spring-boot-starter-test`, `org.junit.jupiter:junit-jupiter`, `org.mockito:mockito-core`, `com.atlassian.oai:swagger-request-validator-core`).
- [X] T003 Initialize frontend: migrate the moved Vite scaffold to TypeScript strict — add `frontend/tsconfig.json` (`strict: true`, `noUncheckedIndexedAccess: true`), rename `App.jsx` → `App.tsx` and `main.jsx` → `main.tsx`, rewrite `vite.config.js` as `vite.config.ts` with the dev proxy `/api` → `http://localhost:8080`, update `frontend/package.json` scripts (`dev`, `build`, `preview`, `test`, `test:coverage`, `test:e2e`, `test:a11y`, `lint`, `generate:api-types`) and dependencies (`react@^18`, `react-dom@^18`, `@tanstack/react-query@^5`, `typescript`, `vite`, `@vitejs/plugin-react`, `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `@playwright/test`, `@axe-core/playwright`, `openapi-typescript`, `eslint`, `eslint-plugin-react-hooks`, `eslint-plugin-react-refresh`, `eslint-plugin-jsx-a11y`, `prettier`).
- [X] T004 [P] Configure frontend lint + format: `frontend/eslint.config.js` (flat config) extends `js.configs.recommended`, `reactHooks.configs.flat.recommended`, `reactRefresh.configs.vite`, and adds `eslint-plugin-jsx-a11y` rules. `frontend/.prettierrc` with the repo-wide style.
- [X] T005 [P] Configure backend static analysis + coverage: in `backend/build.gradle.kts` add the `org.sonarqube` 7.x Gradle plugin, `checkstyle`, and `jacoco` with `jacocoTestCoverageVerification { violationRules { rule { limit { counter='LINE'; minimum=0.90 } } } }` to enforce ≥ 90% line coverage per Principle III.
- [X] T006 [P] Create repo-root `docker-compose.yml` wiring `backend` (multi-stage: `eclipse-temurin:21-jdk-alpine` → `eclipse-temurin:21-jre-alpine`) on port 8080 and `frontend` (multi-stage: `node:20-alpine` → `nginx:alpine`) on port 5173 with `nginx.conf` proxying `/api/**` to `backend:8080`. Named volume `aiavatar-db` is declared but unused in this feature (future compatibility with the constitutional Deployment table).
- [X] T007 [P] Create backend `Dockerfile` at `backend/Dockerfile` (multi-stage Gradle build then JRE runtime).
- [X] T008 [P] Create frontend `Dockerfile` at `frontend/Dockerfile` (multi-stage Vite build then Nginx serve) plus `frontend/nginx.conf` with `/api/**` proxy.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The minimum wiring that every other task depends on: application entrypoints, shared HTTP/retry/logging config, and cross-cutting frontend primitives. No business logic lives here.

**⚠️ CRITICAL**: User Story 1 cannot begin until this phase is complete.

- [X] T009 Create Spring Boot application entrypoint `backend/src/main/java/com/aiavatar/alterego/AlterEgoApplication.java` (`@SpringBootApplication`, `@EnableRetry`) plus `backend/src/main/resources/application.yml` (port 8080, `spring.servlet.multipart.max-file-size=5MB`, actuator exposure of `health,info,metrics`, `spring.profiles.active=default`).
- [X] T010 [P] Create backend structured-logging baseline: `backend/src/main/resources/logback-spring.xml` using `logstash-logback-encoder` (JSON appender) with a custom `PhotoRedactionFilter` in `backend/src/main/java/com/aiavatar/alterego/config/PhotoRedactionFilter.java` that strips any MDC or message field named `photo`, `photoBytes`, or `imageData` per FR-016.
- [X] T011 [P] Create backend CORS + problem-detail config: `backend/src/main/java/com/aiavatar/alterego/config/WebConfig.java` (allowed origin `http://localhost:5173` for dev profile) and `backend/src/main/java/com/aiavatar/alterego/config/ProblemDetailAdvice.java` (`@ControllerAdvice` returning RFC 7807 responses, stripping any multipart bytes from thrown exceptions).
- [X] T012 [P] Create backend multipart + retry config: `backend/src/main/java/com/aiavatar/alterego/config/MultipartConfig.java` (5 MB max) and `backend/src/main/java/com/aiavatar/alterego/config/RetryConfig.java` exposing a `RetryTemplate` bean with 5 attempts, exponential back-off, randomised jitter per constitutional Principle IV.
- [X] T013 [P] Create frontend theme tokens: `frontend/src/styles/tokens.css` (dark theme; pink/purple accents carried from the design reference; DM Sans / DM Serif Display via `<link>` in `frontend/index.html`).
- [X] T014 [P] Create frontend shared a11y primitives: `frontend/src/components/LiveRegion.tsx` (`aria-live="polite"` region mounted once in `App.tsx`) and `frontend/src/components/VisuallyHidden.tsx` (CSS-only visually-hidden helper).
- [X] T015 [P] Create frontend resilient HTTP client: `frontend/src/lib/resilientFetch.ts` — thin `fetch` wrapper with 5 attempts, exponential back-off, randomised jitter, and a `fallback` hook per constitutional Principle IV.
- [X] T016 Wire TanStack Query provider into `frontend/src/main.tsx` (depends on T003).

**Checkpoint**: Backend boots (`./gradlew bootRun` returns 200 on `/actuator/health`); frontend boots (`npm run dev` renders an empty `App.tsx`); both containers come up under Docker Compose.

---

## Phase 3: User Story 1 — Generate a personalised AI Alter Ego (Priority: P1) 🎯 MVP

**Goal**: A user takes or uploads a photo, picks a pose / colour / archetype / universe / first name, presses Generate, and receives a hero-style poster with their face on it plus generated character text. Third-party integrations are stubbed on the backend; resilient HTTP handles retries and falls back to canned content on failure.

**Independent Test**: Open `http://localhost:5173`, complete the end-to-end flow once in the happy path (stubs green) and once in the force-failure path (stubs red) — both produce a complete, rendered poster within their SC budgets.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> **NOTE: Write these tests FIRST and ensure they FAIL before implementation.**

#### Backend contract tests (against `contracts/alter-egos.openapi.yaml`)

- [X] T017 [P] [US1] Contract test `POST /api/v1/alter-egos` happy path — returns 200 with `meta.outcome=success`, validated against OpenAPI via `swagger-request-validator-core`, in `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java`.
- [X] T018 [P] [US1] Contract test error responses (400 blank firstName, 400 bad enum, 413 photo > 5 MB, 415 bad MIME) — all match `ProblemDetail` schema — in `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerErrorContractTest.java`.

#### Backend integration tests (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, real stub pipeline)

- [X] T019 [P] [US1] Integration test full happy-path pipeline — multipart POST → 200 response with non-empty `poster.dataUrl` and `meta.outcome=success` — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoIT.java`.
- [X] T020 [P] [US1] Integration test force-stub-failure — activates `spring.profiles.active=force-stub-failure`; same POST returns 200 with `meta.outcome=fallback` and canned content (SC-004) — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFallbackIT.java`.
- [X] T021 [P] [US1] Integration test log redaction — uses `OutputCaptureExtension` to assert no photo bytes, `photo` / `photoBytes` / `imageData` field names, or base64 blobs appear anywhere in log output for a successful generation (FR-016) — in `backend/src/test/java/com/aiavatar/alterego/integration/LogRedactionIT.java`.
- [X] T022 [P] [US1] Integration test correlation-ID echo — client-supplied `X-Request-Id` header is echoed in response header and `meta.correlationId` body field; absent header → server generates a UUID and echoes it — in `backend/src/test/java/com/aiavatar/alterego/integration/CorrelationIdIT.java`.

#### Backend unit tests

- [X] T023 [P] [US1] Enum wire-value test — `Pose`, `Colour`, `Archetype`, `Universe` serialise to the exact strings in `contracts/alter-egos.openapi.yaml` — in `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java`.
- [X] T024 [P] [US1] Bean-validation test for `AlterEgoRequest` — `@NotNull` on each enum, `@NotBlank @Size(min=1,max=40)` on `firstName`, trimming behaviour — in `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoRequestValidationTest.java`.
- [X] T025 [P] [US1] `StubCharacterGeneratorTest` — deterministic pick from the input tuple hash across all 48 (archetype × 8) variants; same input → same output; different inputs → different outputs over a sampled matrix — in `backend/src/test/java/com/aiavatar/alterego/unit/StubCharacterGeneratorTest.java`.
- [X] T026 [P] [US1] `StubImageGeneratorTest` — correctly selects the pre-rendered PNG for `(archetype, colour)` and composites a non-zero photo region via `Graphics2D`; output MIME and dimensions match `PosterImage` constraints — in `backend/src/test/java/com/aiavatar/alterego/unit/StubImageGeneratorTest.java`.
- [X] T027 [P] [US1] `FallbackPosterProviderTest` — returns a fully-populated `GeneratedCharacter` + `PosterImage` satisfying `AlterEgoResponse` schema — in `backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java`.
- [X] T028 [P] [US1] `AlterEgoServiceTest` — happy path calls both generators; transient failure from either triggers retry (RetryTemplate); permanent failure falls back to `FallbackPosterProvider` and sets `meta.outcome=fallback` — in `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoServiceTest.java`.

#### Frontend unit / component tests (Vitest + React Testing Library)

- [X] T029 [P] [US1] Reducer test — every one of the 11 actions (`PhotoSelected`, `PhotoCleared`, `PoseSelected`, `ColourSelected`, `ArchetypeSelected`, `UniverseSelected`, `FirstNameChanged`, `GenerateSubmitted`, `GenerateSucceeded`, `GenerateFailedWithFallback`, `StartOverRequested`) × valid source phases; invalid transitions are no-ops — in `frontend/src/features/alterego/state/reducer.test.ts`.
- [X] T030 [P] [US1] Selector test — `isReadyToGenerate` true iff all five selections present and `firstName.trim().length >= 1`; `missingInputs` returns the ordered list of missing inputs — in `frontend/src/features/alterego/state/selectors.test.ts`.
- [X] T031 [P] [US1] `PhotoIntake.test.tsx` — camera grant + capture; camera-denied falls back to upload; file-type rejection of non-`image/jpeg|png`; size rejection of >5 MB after downscale attempt — in `frontend/src/features/alterego/components/PhotoIntake.test.tsx`.
- [X] T032 [P] [US1] `PoseGrid.test.tsx` — renders 4 options, arrow-key navigation between radio buttons, space/enter selects, `aria-checked` correct — in `frontend/src/features/alterego/components/PoseGrid.test.tsx`.
- [X] T033 [P] [US1] `ColourGrid.test.tsx` — renders 6 swatches each with a visible text label + `aria-label` (FR-021), arrow-key nav, no colour-only affordance — in `frontend/src/features/alterego/components/ColourGrid.test.tsx`.
- [X] T034 [P] [US1] `ArchetypeGrid.test.tsx` — renders 6 archetypes, keyboard navigation, accessible labels — in `frontend/src/features/alterego/components/ArchetypeGrid.test.tsx`.
- [X] T035 [P] [US1] `UniverseGrid.test.tsx` — renders 4 universes, keyboard navigation, accessible labels — in `frontend/src/features/alterego/components/UniverseGrid.test.tsx`.
- [X] T036 [P] [US1] `FirstNameInput.test.tsx` — trim behaviour, 1..40 char validation, oversize hint (non-blocking), associated error message via `aria-describedby` (FR-023) — in `frontend/src/features/alterego/components/FirstNameInput.test.tsx`.
- [X] T037 [P] [US1] `GenerateButton.test.tsx` — disabled when `isReadyToGenerate` is false; missing-input hint lists missing fields (FR-009, FR-010); enabled and clickable when all inputs present — in `frontend/src/features/alterego/components/GenerateButton.test.tsx`.
- [X] T038 [P] [US1] `GenerationLoading.test.tsx` — renders live-region announcement on mount (FR-022); announces success/fallback text on transition — in `frontend/src/features/alterego/components/GenerationLoading.test.tsx`.
- [X] T039 [P] [US1] `PosterView.test.tsx` — renders the two-line hero title (line 1 uppercased), tagline, exactly three superpowers, quote, and `<img>` with the response `dataUrl` — in `frontend/src/features/alterego/components/PosterView.test.tsx`.
- [X] T040 [P] [US1] `StartOverButton.test.tsx` — dispatches `StartOverRequested`; revokes the active `photoPreviewUrl`; completes within a synchronous tick (SC-006 performance bound is verified in the E2E test, correctness here) — in `frontend/src/features/alterego/components/StartOverButton.test.tsx`.
- [X] T041 [P] [US1] `resilientFetch.test.ts` — 5 attempts with exponential back-off + jitter; resolves on first 2xx; falls back via the `fallback` hook after final failure; respects request-id forwarding — in `frontend/src/lib/resilientFetch.test.ts`.
- [X] T042 [P] [US1] `downscalePhoto.test.ts` — input larger than 1024×1024 is downscaled while preserving aspect ratio; output MIME is `image/jpeg`; quality ~0.85; images already within bounds pass through with only format conversion — in `frontend/src/features/alterego/lib/downscalePhoto.test.ts`.
- [X] T043 [P] [US1] `useGenerateAlterEgo.test.ts` — TanStack Query mutation posts multipart body with correct parts; on 200/success dispatches `GenerateSucceeded`; on final failure dispatches `GenerateFailedWithFallback` with fallback content; propagates `X-Request-Id` into `console.error` correlation tag — in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.ts`.

#### Frontend E2E + accessibility tests (Playwright + @axe-core/playwright)

- [X] T044 [P] [US1] Playwright E2E happy path — photo upload → selections → Generate → poster rendered within **≤ 3 s p95** across 3 runs (SC-001, SC-002, SC-003); Start-over resets UI in **< 200 ms** (SC-006); poster contains face, accent colour, two-line title, tagline, three superpowers, quote (SC-005, sampled) — in `frontend/tests/e2e/generate-happy-path.spec.ts`.
- [X] T045 [P] [US1] Playwright E2E fallback path — **DEVIATION**: instead of launching the backend with `SPRING_PROFILES_ACTIVE=force-stub-failure` and hitting the real endpoint, Playwright intercepts `POST /api/v1/alter-egos` via `page.route(...)` and returns 500 on every attempt. `resilientFetch` exhausts its retries and the client's `fallback` hook synthesises "The Resilient" locally — same user-visible outcome (SC-004, FR-018, FR-023) with no backend dependency; backend's own `GenerateAlterEgoFallbackIT` (T020) covers the server side. In `frontend/tests/e2e/generate-fallback.spec.ts`.
- [X] T046 [P] [US1] Playwright keyboard-only walkthrough — Tab/Shift-Tab/Space/Enter drives the entire flow (photo → all selections → Generate → view poster → Start over) with zero `page.mouse` usage; asserts `document.activeElement` matches expected element at each step (SC-007, FR-020) — in `frontend/tests/e2e/keyboard-walkthrough.spec.ts`.
- [X] T047 [P] [US1] Playwright + @axe-core/playwright — scan runs across four page states (entry screen, loading, success, force-failure fallback); asserts **zero `serious` or `critical` violations** (SC-007) — in `frontend/tests/e2e/axe-scan.spec.ts`.

### Implementation for User Story 1

> **NOTE: Every task in this block MUST be preceded by a committed, failing test task from above.**

#### Backend — models + fixtures (parallelisable)

- [X] T048 [P] [US1] Create enums `Pose`, `Colour`, `Archetype`, `Universe` with `@JsonValue` wire names matching OpenAPI, in `backend/src/main/java/com/aiavatar/alterego/model/Pose.java`, `Colour.java`, `Archetype.java`, `Universe.java`.
- [X] T049 [P] [US1] Create `GeneratedCharacter` record (`heroTitleLine1`, `heroTitleLine2`, `tagline`, `superpowers[3]`, `quote`) in `backend/src/main/java/com/aiavatar/alterego/model/GeneratedCharacter.java`.
- [X] T050 [P] [US1] Create `PosterImage` record (`bytes: byte[]`, `mediaType: String`, `widthPx: int`, `heightPx: int`) in `backend/src/main/java/com/aiavatar/alterego/model/PosterImage.java`.
- [X] T051 [P] [US1] Create `PhotoPayload` record (`bytes: byte[]`, `mediaType: String`) — request-scoped holder, no setters, no logging hooks — in `backend/src/main/java/com/aiavatar/alterego/model/PhotoPayload.java`.
- [X] T052 [P] [US1] Create `AlterEgoRequest` record with Bean Validation annotations (`@NotNull` on each enum; `@NotBlank @Size(min=1,max=40)` on `firstName`) in `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`.
- [X] T053 [P] [US1] Create `AlterEgoResponse` and `ResponseMeta` records matching the OpenAPI response schema in `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoResponse.java`.
- [X] T054 [P] [US1] Bundle the character fixture library at `backend/src/main/resources/stubs/characters.json` — 48 variants (6 archetypes × 8 per archetype) per research.md R2. Each variant: `archetype`, `heroTitleLine2Template`, `tagline`, `superpowers[3]`, `quote`.
- [X] T055 [P] [US1] **DEVIATION (procedural rendering)**: instead of bundling 36 pre-rendered PNGs, `StubImageGenerator` (T059) renders the poster on the fly via `java.awt.Graphics2D`: linear gradient from a darkened accent colour to black, accent-coloured border, user photo composited into a fixed face window at `(270,200)–(630,560)`, hero title (lines 1+2) + tagline + universe/pose/archetype annotations. Output: 900×1200 PNG. Same data-model contract (`PosterImage` shape unchanged); same response shape (data URL in JSON). Same 1,728-output combinatorial differentiation.

#### Backend — service interfaces + stubs (sequential on shared interface files)

- [X] T056 [US1] Define `CharacterGenerator` interface in `backend/src/main/java/com/aiavatar/alterego/service/CharacterGenerator.java` (one method: `GeneratedCharacter generate(AlterEgoRequest req)`).
- [X] T057 [US1] Define `ImageGenerator` interface in `backend/src/main/java/com/aiavatar/alterego/service/ImageGenerator.java` (one method: `PosterImage generate(GeneratedCharacter character, AlterEgoRequest req, PhotoPayload photo)`).
- [X] T058 [US1] Implement `StubCharacterGenerator` in `backend/src/main/java/com/aiavatar/alterego/service/stub/StubCharacterGenerator.java` — loads `characters.json`, hashes `(pose, colour, archetype, universe, firstName)` via SHA-256 low 31 bits, picks a variant modulo 8, substitutes uppercased firstName into `heroTitleLine1`; active under default profile (depends on T048–T049, T054, T056).
- [X] T059 [US1] Implement `StubImageGenerator` in `backend/src/main/java/com/aiavatar/alterego/service/stub/StubImageGenerator.java` — resolves `{archetype}_{colour}.png`, composites the photo into the reserved window using `javax.imageio.ImageIO` + `java.awt.Graphics2D.drawImage`, returns `PosterImage` (depends on T050–T051, T055, T057).
- [X] T060 [US1] Implement `FallbackPosterProvider` in `backend/src/main/java/com/aiavatar/alterego/service/fallback/FallbackPosterProvider.java` — serves a single canned `GeneratedCharacter` + `PosterImage` from bundled fixtures; profile-neutral (depends on T049–T050).
- [X] T061 [US1] Implement force-failure stub doubles in `backend/src/main/java/com/aiavatar/alterego/service/stub/ForceFailureCharacterGenerator.java` and `.../ForceFailureImageGenerator.java` — active under `spring.profiles.active=force-stub-failure` (via `@Profile`), throw `StubGenerationException` (depends on T056–T057).
- [X] T062 [US1] Implement `AlterEgoService` in `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java` — orchestrates `CharacterGenerator` then `ImageGenerator`, wraps each call in the `RetryTemplate` bean (T012), on final failure invokes `FallbackPosterProvider` and returns a response with `meta.outcome=fallback` (depends on T056–T060, T012).

#### Backend — controller (gates the whole endpoint)

- [X] T063 [US1] Implement `AlterEgoController` in `backend/src/main/java/com/aiavatar/alterego/controller/AlterEgoController.java` — `POST /api/v1/alter-egos` consumes `multipart/form-data`, accepts `@RequestPart("photo") MultipartFile` + `@Valid @RequestPart("selections") AlterEgoRequest`, validates photo MIME and size, generates / echoes `X-Request-Id` via `MDC`, returns `AlterEgoResponse` with the correct `meta.outcome` (depends on T051–T053, T062, T011).

#### Frontend — types + state (parallelisable, TS files independent)

- [X] T064 [P] [US1] Generate TypeScript API types from `contracts/alter-egos.openapi.yaml` into `frontend/src/features/alterego/types.ts` via `openapi-typescript`; wire `npm run generate:api-types` and a CI drift check per quickstart § 7. **DEVIATION**: types are hand-authored in `types.ts` for clearer naming + extra UI metadata in `options.ts`. The `npm run generate:api-types` script is wired and writes to `types.generated.ts` for CI drift detection (separate file, gitignored locally).
- [X] T065 [P] [US1] Create `frontend/src/features/alterego/state/reducer.ts` — 11 action types, 6-phase state machine per data-model.md, pure functions, no side effects.
- [X] T066 [P] [US1] Create `frontend/src/features/alterego/state/selectors.ts` — `isReadyToGenerate`, `missingInputs`.
- [X] T067 [P] [US1] Create `frontend/src/features/alterego/state/context.tsx` — `AlterEgoContext` + `AlterEgoProvider` combining `useReducer` with the reducer from T065. **DEVIATION**: split into `state/context.ts` (the context object + types) + `state/AlterEgoProvider.tsx` (the component) so the component file keeps a clean component-only export surface (`react-refresh/only-export-components` rule).
- [X] T068 [P] [US1] Create `frontend/src/features/alterego/hooks/useAlterEgoSession.ts` — thin hook consuming `AlterEgoContext` and exposing typed action dispatchers.
- [X] T069 [P] [US1] Create `frontend/src/features/alterego/lib/downscalePhoto.ts` — uses `createImageBitmap` + `OffscreenCanvas` (fallback `HTMLCanvasElement` for browsers without OffscreenCanvas) → JPEG q=0.85, 1024×1024 max preserving aspect ratio.
- [X] T070 [P] [US1] Create `frontend/src/features/alterego/fallback/fallbackPoster.ts` — FE-side canned mirror of the backend fallback, used when even the HTTP request fails (network off) so `resilientFetch`'s `fallback` hook always has something to return.

#### Frontend — services + hook (sequential on client file)

- [X] T071 [US1] Create `frontend/src/features/alterego/services/alterEgoClient.ts` — builds the `multipart/form-data` body (photo as `Blob`, selections as a JSON `Blob`), wraps `resilientFetch` (T015), threads `X-Request-Id` through request and response (depends on T015, T064, T069, T070).
- [X] T072 [US1] Create `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts` — TanStack Query `useMutation` wrapping `alterEgoClient`, dispatches `GenerateSucceeded` / `GenerateFailedWithFallback` to the session reducer (depends on T065, T071).

#### Frontend — components (parallelisable, each component in its own file)

- [X] T073 [P] [US1] `PhotoIntake.tsx` — camera (getUserMedia) + upload, live preview, MIME/size validation, retake (FR-001..FR-003), client-side downscale via `downscalePhoto` (T069) — in `frontend/src/features/alterego/components/PhotoIntake.tsx`.
- [X] T074 [P] [US1] `PoseGrid.tsx` — radio-group pattern, 4 options (heroic, stealthy, mystical, scholar), arrow-key navigation — in `frontend/src/features/alterego/components/PoseGrid.tsx`.
- [X] T075 [P] [US1] `ColourGrid.tsx` — 6 swatches each with a visible text label and `aria-label` (FR-021); selection visible without colour — in `frontend/src/features/alterego/components/ColourGrid.tsx`.
- [X] T076 [P] [US1] `ArchetypeGrid.tsx` — 6 options — in `frontend/src/features/alterego/components/ArchetypeGrid.tsx`.
- [X] T077 [P] [US1] `UniverseGrid.tsx` — 4 options — in `frontend/src/features/alterego/components/UniverseGrid.tsx`.
- [X] T078 [P] [US1] `FirstNameInput.tsx` — controlled input, trim-on-blur, 1..40 chars, error announcement via `aria-describedby` (FR-023) — in `frontend/src/features/alterego/components/FirstNameInput.tsx`.
- [X] T079 [P] [US1] `GenerateButton.tsx` — disabled until `isReadyToGenerate`; `missingInputs` rendered as a visible hint and announced via live region (FR-009, FR-010, FR-022) — in `frontend/src/features/alterego/components/GenerateButton.tsx`.
- [X] T080 [P] [US1] `GenerationLoading.tsx` — loading indicator + `aria-live="polite"` announcement ("Generating your alter ego…") (FR-011, FR-022) — in `frontend/src/features/alterego/components/GenerationLoading.tsx`.
- [X] T081 [P] [US1] `PosterView.tsx` — renders `<img src={response.poster.dataUrl}>`, `heroTitleLine1` / `heroTitleLine2` (line 1 uppercased styling), tagline, three superpowers, quote; includes the non-blocking error banner (rendered iff `meta.outcome === 'fallback'`) announced via live region (FR-012, FR-018, FR-023) — in `frontend/src/features/alterego/components/PosterView.tsx`.
- [X] T082 [P] [US1] `StartOverButton.tsx` — dispatches `StartOverRequested`; revokes `photoPreviewUrl` via `URL.revokeObjectURL`; focus returns to photo intake (FR-013, SC-006) — in `frontend/src/features/alterego/components/StartOverButton.tsx`.

#### Frontend — page composition (sequential on App.tsx + AlterEgoPage.tsx)

- [X] T083 [US1] Create `frontend/src/features/alterego/AlterEgoPage.tsx` — composes `AlterEgoProvider` + all components, mounts `LiveRegion` once, wires `useGenerateAlterEgo`, switches render between the form phase and the poster phase based on session state (depends on T067, T072, T073–T082).
- [X] T084 [US1] Register `AlterEgoPage` as the app route in `frontend/src/App.tsx` and add `LiveRegion` mount (depends on T014, T083).

**Checkpoint**: User Story 1 is now fully functional — manual happy-path and fallback walkthroughs per quickstart.md §§ 3-4 both succeed; all tests from T017–T047 are green.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Coverage top-up, CVE gate, cleanup of temporary artefacts, quickstart dry-run.

- [X] T085 [P] Backend coverage top-up: add unit tests under `backend/src/test/java/com/aiavatar/alterego/unit/` until `./gradlew jacocoTestCoverageVerification` passes the ≥ 90% line threshold (Principle III). Gaps to prioritise: exception paths in `AlterEgoService`, `ProblemDetailAdvice` edge cases, `PhotoRedactionFilter` sensitivity.
- [X] T086 [P] Frontend coverage top-up: add colocated `*.test.tsx` / `*.test.ts` tests under `frontend/src/` until `npm run test:coverage` reports ≥ 90% line coverage per Principle III.
- [X] T087 [P] Backend CVE audit — **DEVIATION**: OWASP `dependency-check` Gradle plugin NOT added (would bloat the build.gradle.kts and require an NVD API key + periodic feed refresh for a POC). Principle VI allows "or equivalent"; pinned deps (Spring Boot 3.5.0, logstash-logback-encoder 8.0, swagger-request-validator 2.43.0, JUnit 5 / Mockito current) were manually reviewed at pin time — no known HIGH/CRITICAL advisories. Adding `org.owasp.dependencycheck` is queued as a follow-up feature.
- [X] T088 [P] Frontend CVE audit: run `npm audit --audit-level=high` (Principle VI); resolve or justify any HIGH/CRITICAL advisories before merge.
- [X] T089 [P] Remove `aiavatar-mock.html` from the repo root per spec Assumptions ("throwaway artefact") and the quickstart "done" checklist. Keep the `.gitignore` entry for safety.
- [X] T090 [P] Dry-run `specs/001-initial-poc/quickstart.md`: `docker compose up --build`, walk the happy-path (§ 3) and the fallback path (§ 4) manually, then `docker compose down`. Record any deviation in the PR description.
- [X] T091 Refresh `CLAUDE.md` agent context once implementation is complete: rerun `.specify/scripts/bash/update-agent-context.sh claude` and sanity-check the result for legibility.

---

## Dependencies & Execution Order

### Phase dependencies

- **Phase 1 (Setup)** — no prior dependencies.
- **Phase 2 (Foundational)** — depends on Phase 1. **BLOCKS** Phase 3.
- **Phase 3 (US1)** — depends on Phase 2.
- **Phase N (Polish)** — depends on Phase 3.

### Within Phase 3 (test-first ordering)

1. All **test** tasks (T017–T047) — authored + committed failing **before** any implementation task.
2. Backend **models + fixtures** (T048–T055) — most `[P]`; fixture tasks (T054, T055) independent of code tasks.
3. Backend **service interfaces** (T056, T057) — two small sequential tasks; unlock stubs.
4. Backend **stub / fallback implementations** (T058–T061) — `[US1]`-tagged; T058 depends on T048/T049/T054/T056; T059 depends on T050/T051/T055/T057; T060 depends on T049/T050; T061 depends on T056/T057.
5. Backend **service** (T062) — depends on T056–T060 + T012.
6. Backend **controller** (T063) — depends on T051–T053, T062, T011.
7. Frontend **types + state + lib** (T064–T070) — `[P]`-safe, independent files.
8. Frontend **client + hook** (T071, T072) — sequential; T071 blocks T072.
9. Frontend **components** (T073–T082) — all `[P]`-safe (each in its own file); block T083.
10. Frontend **page composition** (T083, T084) — sequential; T083 before T084.

### Parallel opportunities

- **Phase 1**: T004–T008 are all `[P]` — configure tooling, Dockerfiles, and Compose concurrently.
- **Phase 2**: T010–T015 are all `[P]` — logging, config, theme, shared UI primitives, resilient fetch can be authored in parallel.
- **Phase 3 tests**: T017–T047 are all `[P]` — 31 test files across two stacks authored concurrently.
- **Phase 3 models + fixtures**: T048–T055 all `[P]`.
- **Phase 3 components**: T073–T082 all `[P]`.
- **Phase N**: T085–T090 all `[P]`.

---

## Parallel Example: all US1 tests, kicked off together

```bash
# Launch all US1 test-authoring tasks in parallel (tests are mandatory — Principle III):
Task: "Backend contract test for POST /api/v1/alter-egos happy path in backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java"
Task: "Backend contract test for error responses in backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerErrorContractTest.java"
Task: "Backend integration test GenerateAlterEgoIT in backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoIT.java"
Task: "Backend integration test GenerateAlterEgoFallbackIT in backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFallbackIT.java"
Task: "Backend integration test LogRedactionIT in backend/src/test/java/com/aiavatar/alterego/integration/LogRedactionIT.java"
Task: "Backend integration test CorrelationIdIT in backend/src/test/java/com/aiavatar/alterego/integration/CorrelationIdIT.java"
Task: "Backend unit test EnumsTest in backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java"
Task: "Backend unit test AlterEgoRequestValidationTest in backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoRequestValidationTest.java"
Task: "Backend unit test StubCharacterGeneratorTest in backend/src/test/java/com/aiavatar/alterego/unit/StubCharacterGeneratorTest.java"
Task: "Backend unit test StubImageGeneratorTest in backend/src/test/java/com/aiavatar/alterego/unit/StubImageGeneratorTest.java"
Task: "Backend unit test FallbackPosterProviderTest in backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java"
Task: "Backend unit test AlterEgoServiceTest in backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoServiceTest.java"
# …and all 19 frontend component / hook / lib / E2E / a11y test tasks from T029–T047.
```

---

## Implementation Strategy

### MVP scope

This feature has a single P1 user story, so **Phase 3 in its entirety is the MVP**. There is no Phase 4 / Phase 5 trimming to do. Incremental delivery within US1:

1. Complete Phase 1 (Setup).
2. Complete Phase 2 (Foundational). **Checkpoint**: both services boot cleanly.
3. Author every Phase-3 test (T017–T047) and commit them **failing**.
4. Implement the Phase-3 backend (T048–T063); run backend tests to green.
5. Implement the Phase-3 frontend (T064–T084); run frontend tests to green.
6. Manually dry-run quickstart.md §§ 3–4 (happy + fallback).
7. Run Phase N polish (coverage + CVE gates + mock removal + quickstart replay).

### Notes

- `[P]` = different files, no in-flight dependencies on incomplete tasks.
- `[US1]` = belongs to User Story 1; every implementation task in Phase 3 carries this label.
- Setup, Foundational, and Polish tasks have no story label.
- Tests MUST be written first and MUST fail before implementation (Principle III — non-negotiable). Commit failing tests separately so green implementation commits show as clean deltas.
- No cross-story dependencies exist — there is only one story.
- Commit after each task or logical group; prefer small green commits.

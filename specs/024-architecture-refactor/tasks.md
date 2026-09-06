---
description: "Task list for 024 — Architecture and Design Refactoring"
---

# Tasks: Architecture and Design Refactoring

**Input**: Design documents from `/specs/024-architecture-refactor/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Test tasks are MANDATORY per Constitution Principle III (Test-First Development, NON-NEGOTIABLE). They MUST be written and FAIL before implementation tasks in the same user story. The new ArchUnit `arch/` tier counts as test code and follows the same rule.

**Organization**: Tasks are grouped by user story (US1–US5). All five stories ship on this branch (clarified Q3).

**Path Conventions**:

- Backend (Java 21 + Spring Boot 3, Gradle Kotlin DSL):
  - Production: `backend/src/main/java/com/aiavatar/alterego/...`
  - Tests, by tier: `backend/src/test/java/com/aiavatar/alterego/{unit,service,contract,integration,arch}/...`
- Frontend (React 19 + TypeScript strict + Vite):
  - Source: `frontend/src/features/alterego/...`
  - Tests: colocated `*.test.tsx` / `*.test.ts`; E2E in `frontend/tests/e2e/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Land the cross-cutting prerequisites that are not themselves part of any single user story but every story depends on. **Constitution amendment lands first** (research.md R5) so every subsequent commit is gated against v1.1.0.

- [X] T001 Amend project constitution `.specify/memory/constitution.md` from v1.0.2 → v1.1.0: add Principles VII (Layer Convention), VIII (Provider Seam), IX (Test Pyramid); reword Principle III's "Backend integration tests MUST use `@SpringBootTest` with an in-process SQLite DB or Testcontainers" to "Backend integration tests MUST use `@SpringBootTest`. When a persistence layer is wired, that layer MUST use an in-process SQLite DB or Testcontainers" (resolves pre-existing tension between Principle III and the no-persistence posture inherited from features 001–023); update Sync Impact Report and version trailer; leave Principle II slot intentionally absent (FR-2429, FR-2430)
- [X] T002 Add ArchUnit 1.3.x as `testImplementation` in `backend/build.gradle.kts`; record entry in `CLAUDE.md` Active Technologies log per FR-2427
- [X] T003 [P] Add per-tier Gradle test tasks (`unitTest`, `serviceTest`, `contractTest`, `integrationTest`, `archTest`) in `backend/build.gradle.kts`, each filtering by package; wire the default `test` task to depend on all five (FR-2422, US4 enabler)
- [X] T004 [P] Reserve `frontend/STATE.md` as an empty placeholder (zero-byte or single header line). Full content lands in T052 within its owning user story.

**Checkpoint**: Constitution at v1.1.0, ArchUnit available, Gradle tiers wired. Subsequent commits can be evaluated against the new principles.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Skeleton packages + new port interfaces. Until this is done, no user story can land because the orchestration refactor (US1), the layered moves (US2), and the observability filter (US5) all depend on the new package paths and ports existing.

**⚠️ CRITICAL**: No user story work begins until this phase completes.

- [X] T005 Create empty target packages in `backend/src/main/java/com/aiavatar/alterego/`: `boundary/http/`, `boundary/logging/`, `application/`, `application/port/`, `application/pipeline/`, `domain/model/`, `domain/prompt/`, `domain/policy/`, `infrastructure/config/`, `infrastructure/provider/gemini/`, `infrastructure/provider/falai/`, `infrastructure/provider/stub/`, `infrastructure/provider/fallback/`, `infrastructure/overlay/frame/`, `infrastructure/overlay/text/`, `infrastructure/email/`, `infrastructure/photo/` (one `.gitkeep` per directory)
- [X] T006 [P] Declare `ImageGeneratorPort` interface in `backend/src/main/java/com/aiavatar/alterego/application/port/ImageGeneratorPort.java` per `contracts/image-generator.spi.md` (methods: `generate`, `provider`, `externalRetry`)
- [X] T007 [P] Declare `CharacterGeneratorPort` interface in `backend/src/main/java/com/aiavatar/alterego/application/port/CharacterGeneratorPort.java` (same shape: `generate(AlterEgoRequest)`, `provider()`)
- [X] T008 [P] Declare `EmailSenderPort` interface in `backend/src/main/java/com/aiavatar/alterego/application/port/EmailSenderPort.java` (method: `send(SendAlterEgoEmailRequest, AlterEgoResponse) → SendAlterEgoEmailResponse`)
- [X] T009 [P] Move `GenerationFailure` from `service/` to `backend/src/main/java/com/aiavatar/alterego/application/port/GenerationFailure.java` (same class, new package)
- [X] T010 [P] Move all `model/` types to `backend/src/main/java/com/aiavatar/alterego/domain/model/` (verbatim — see data-model.md "verbatim moves" list); update all imports across the codebase via global rename

**Checkpoint**: Skeleton + ports + domain types in place. The build still compiles because old `service.ImageGenerator` etc. remain alongside the new ports as siblings until the next phase rewires consumers.

---

## Phase 3: User Story 1 — Provider seam (Priority: P1) 🎯 MVP

**Goal**: Adding a fourth generation provider becomes a single new module + one configuration entry, with **zero** edits to existing orchestration, controller, or unrelated provider code. Fallback is reached through the same port as a normal generation.

**Independent Test**: A maintainer can author a hypothetical `KangarooImageGenerator` implementing `ImageGeneratorPort`, profile-gated via `ProviderProfileGuard`, with zero modifications to `AlterEgoUseCase`, `AlterEgoController`, or any other provider's source; the contract test suite continues to pass. ArchUnit `NoProviderBranchingTest` blocks any reintroduction of provider-identity branching.

### Tests for User Story 1 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T011 [P] [US1] ArchUnit rule in `backend/src/test/java/com/aiavatar/alterego/arch/NoProviderBranchingTest.java`: classes in `application.*` MUST NOT have imports under `infrastructure.provider.{gemini,falai,stub,fallback}.*` and MUST NOT contain string literals matching `"stub"`, `"gemini"`, `"falai"` (FR-2402)
- [X] T012 [P] [US1] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/application/AlterEgoUseCaseTest.java` with mocked `ImageGeneratorPort` and `CharacterGeneratorPort`: covers happy path, `GenerationFailure` → fallback path, `RuntimeException` → `MALFORMED_RESPONSE` fallback path, asserting outcome / provider / attemptedProvider / reason fields are byte-identical to today's `AlterEgoService` (FR-2425)
- [X] T013 [P] [US1] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/infrastructure/provider/fallback/FallbackImageGeneratorTest.java`: returns a non-null `PosterImage` for every `(Archetype, Universe)` pair without throwing
- [X] T014 [P] [US1] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/infrastructure/provider/stub/StubImageGeneratorTest.java`: invoking `generate(...)` throws `GenerationFailure(NOT_CONFIGURED)` with `attemptedProvider="none"` (the new behaviour replacing today's short-circuit in the orchestrator)

### Implementation for User Story 1

- [X] T015 [P] [US1] Move Gemini image classes to `backend/src/main/java/com/aiavatar/alterego/infrastructure/provider/gemini/` (`GeminiClient`, `GeminiImageGenerator`, `GeminiPromptBuilder`); `GeminiImageGenerator` implements `ImageGeneratorPort` — note that the legacy method `wantsExternalRetry()` is renamed to `externalRetry()` on the new port (per `contracts/image-generator.spi.md`); provider-identity returns `Provider.GEMINI`
- [X] T016 [P] [US1] Move fal.ai image classes to `backend/src/main/java/com/aiavatar/alterego/infrastructure/provider/falai/` (`FalAiClient`, `FalAiImageGenerator`, `FalAiPromptBuilder`); `FalAiImageGenerator` implements `ImageGeneratorPort`; provider-identity returns `Provider.FALAI`
- [X] T017 [P] [US1] Move stub image classes to `backend/src/main/java/com/aiavatar/alterego/infrastructure/provider/stub/`; modify `StubImageGenerator.generate` to throw `GenerationFailure(NOT_CONFIGURED, attemptedProvider="none")` instead of returning a stub poster (research.md R4)
- [X] T018 [P] [US1] Move `FallbackPosterProvider` to `backend/src/main/java/com/aiavatar/alterego/infrastructure/provider/fallback/`; create new `FallbackImageGenerator` in the same package implementing `ImageGeneratorPort` and delegating to the existing `FallbackPosterProvider`; provider returns `Provider.STUB` (the wire-format value used today for fallbacks)
- [X] T019 [P] [US1] Move Gemini character classes to `backend/src/main/java/com/aiavatar/alterego/infrastructure/provider/gemini/` and stub character generator to `infrastructure/provider/stub/`; the stub character generator throws `GenerationFailure(NOT_CONFIGURED)` consistent with T017; the Gemini character generator implements `CharacterGeneratorPort`
- [X] T020 [US1] Move `AccentResolver` + `AccentTone` to `backend/src/main/java/com/aiavatar/alterego/domain/policy/` (depended on by Gemini/falai prompt builders; T015/T016 won't compile without this move)
- [X] T021 [US1] Rename `AlterEgoService` → `AlterEgoUseCase` and move to `backend/src/main/java/com/aiavatar/alterego/application/AlterEgoUseCase.java`; rewrite the orchestration body per `contracts/image-generator.spi.md` "Orchestrator usage contract" — single try/catch around `ImageGeneratorPort.generate`, no string-comparison branch on provider identity; preserves all log lines and response fields verbatim (FR-2425). Depends on T015–T020.
- [X] T022 [US1] Update profile-gating in `infrastructure/config/ProviderProfileGuard.java` so `FallbackImageGenerator` is **always** wired as a separate bean (constructor parameter to `AlterEgoUseCase`), and exactly one of `{StubImageGenerator, GeminiImageGenerator, FalAiImageGenerator}` is wired as the `primary` `ImageGeneratorPort` based on the active profile
- [X] T023 [US1] Wire constructor of `AlterEgoUseCase` to accept `ImageGeneratorPort primary, ImageGeneratorPort fallback` via `@Qualifier("primary")` / `@Qualifier("fallback")`; delete the post-move-empty `service/` subpackages (`gemini/`, `falai/`, `stub/`, `fallback/`)

**Checkpoint**: US1 acceptance scenarios all pass. The orchestrator has no provider-identity branch. Contract tests against `specs/001-initial-poc/contracts/alter-egos.openapi.yaml` are green (FR-2413).

---

## Phase 4: User Story 2 — Layered map (Priority: P1)

**Goal**: A new contributor can answer "where does input validation / prompt construction / provider call / overlays happen?" from folder names alone, within 15 minutes. Overlays compose as a named pipeline of independently-testable stages.

**Independent Test**: A reader unfamiliar with the codebase opens `backend/src/main/java/com/aiavatar/alterego/` and sees exactly four layered subdirectories. Each "where does X happen?" question maps to a single distinct folder. The `PosterPipeline` class is the only call site for image post-processing.

### Tests for User Story 2 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T024 [P] [US2] ArchUnit rule in `backend/src/test/java/com/aiavatar/alterego/arch/LayerBoundariesTest.java`: applied to `main/java/com.aiavatar.alterego.**` only (test code is excluded). Dependency direction `boundary → application → domain ← infrastructure`; no reverse edges; the only class permitted outside the four top-level layer packages is `AlterEgoApplication.java`
- [X] T024a [P] [US2] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/infrastructure/provider/gemini/GeminiPromptBuilderTest.java`: builder produces deterministic byte-for-byte output for a fixed `AlterEgoRequest`; runs in < 50 ms with no `HttpClient` collaborator wired (FR-2407 part b)
- [X] T024b [P] [US2] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/infrastructure/provider/falai/FalAiPromptBuilderTest.java`: parallel assertion for fal.ai prompt builder (FR-2407 part b)
- [X] T025 [P] [US2] ArchUnit rule in `backend/src/test/java/com/aiavatar/alterego/arch/DomainPurityTest.java`: no class under `domain.*` imports `org.springframework.*`, `jakarta.servlet.*`, `javax.imageio.*`, or any package under `infrastructure.*` (FR-2406)
- [X] T026 [P] [US2] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/application/pipeline/PosterPipelineTest.java`: pipeline of `[FrameStage, TextStage]` applies in order; pipeline of `[]` returns input unchanged; pipeline preserves immutability of input
- [X] T027 [P] [US2] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/application/pipeline/FrameStageTest.java`: applies the frame overlay to a fixed input image (1px non-null byte array fixture); does not invoke text overlay (FR-2423 — independent per stage)
- [X] T028 [P] [US2] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/application/pipeline/TextStageTest.java`: applies the text overlay using `StageContext(firstName, roleOfRecord, quote)`; does not invoke frame overlay (FR-2423)
- [X] T029 [P] [US2] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/domain/prompt/RoleOfRecordTest.java`: returns prefab archetype label when archetype non-null and customRole blank; returns trimmed customRole when customRole non-blank (FR-2408)

### Implementation for User Story 2

- [X] T030 [P] [US2] Move `AlterEgoController` and `AlterEgoEmailController` to `backend/src/main/java/com/aiavatar/alterego/boundary/http/`
- [X] T031 [P] [US2] Move `ProblemDetailAdvice` from `config/` to `backend/src/main/java/com/aiavatar/alterego/boundary/http/`
- [X] T032 [P] [US2] Move `PhotoRedactionFilter` from `config/` to `backend/src/main/java/com/aiavatar/alterego/boundary/logging/`
- [X] T033 [P] [US2] Move all `@Configuration` classes to `backend/src/main/java/com/aiavatar/alterego/infrastructure/config/`: `HttpClientConfig`, `MultipartConfig`, `RetryConfig`, `WebConfig`, `ProviderProfileGuard`, `EmailConfigured`, `EmailProperties`, `GeminiProperties`, `FalAiProperties`
- [X] T034 [P] [US2] Move frame overlay classes (`PosterFrameAsset`, `PosterFrameAssetLoader`, `PosterFrameOverlayService`) to `backend/src/main/java/com/aiavatar/alterego/infrastructure/overlay/frame/`
- [X] T035 [P] [US2] Move text overlay classes (`PosterTextFonts`, `PosterTextFitter`, `PosterTextLines`, `PosterTextOverlayService`, `PosterTextStyle`) to `backend/src/main/java/com/aiavatar/alterego/infrastructure/overlay/text/`
- [ ] T036 [P] [US2] Move `service/email/` classes to `backend/src/main/java/com/aiavatar/alterego/infrastructure/email/`; rename `AlterEgoEmailService` → `JavaMailEmailSender` implementing `EmailSenderPort`; consolidate `EmailNotConfiguredException` into `EmailDeliveryFailure` with a typed reason
- [X] T037 [P] [US2] Move `service/photo/` classes (`PhotoReducer`, `PhotoReductionConfig`) to `backend/src/main/java/com/aiavatar/alterego/infrastructure/photo/`
- [X] T038 [P] [US2] Move `RandomCategorySelector` to `backend/src/main/java/com/aiavatar/alterego/domain/policy/`
- [X] T039 [P] [US2] Extract pure `ImagePrompt` value type to `backend/src/main/java/com/aiavatar/alterego/domain/prompt/ImagePrompt.java` (the result of prompt construction, currently inline `String`)
- [X] T040 [P] [US2] Extract pure `CharacterPrompt` value type to `backend/src/main/java/com/aiavatar/alterego/domain/prompt/CharacterPrompt.java`
- [X] T041 [US2] Create `RoleOfRecord` helper in `backend/src/main/java/com/aiavatar/alterego/domain/prompt/RoleOfRecord.java`; replace every call site of `AlterEgoRequest.roleLabel()` with `RoleOfRecord.from(request)`; mark `AlterEgoRequest.roleLabel()` `@Deprecated` then remove (FR-2408)
- [X] T042 [P] [US2] Create `StageContext` record in `backend/src/main/java/com/aiavatar/alterego/application/pipeline/StageContext.java` with fields `firstName`, `roleOfRecord`, `quote`
- [X] T043 [P] [US2] Create sealed interface `PosterStage` in `backend/src/main/java/com/aiavatar/alterego/application/pipeline/PosterStage.java` (`permits FrameStage, TextStage`)
- [X] T044 [P] [US2] Create `FrameStage` in `backend/src/main/java/com/aiavatar/alterego/application/pipeline/FrameStage.java`, `@Order(0)`, delegates to `PosterFrameOverlayService`
- [X] T045 [P] [US2] Create `TextStage` in `backend/src/main/java/com/aiavatar/alterego/application/pipeline/TextStage.java`, `@Order(1)`, delegates to `PosterTextOverlayService` using `StageContext`
- [X] T046 [US2] Create `PosterPipeline` final class in `backend/src/main/java/com/aiavatar/alterego/application/pipeline/PosterPipeline.java`; accepts `List<PosterStage>` via constructor injection; applies in order. Depends on T042–T045
- [X] T047 [US2] Refactor `AlterEgoUseCase` (from T021) to call `posterPipeline.apply(raw, stageContext)` instead of two imperative overlay calls; preserves all log lines and response fields verbatim. Depends on T046
- [X] T048 [US2] Create `SendAlterEgoEmailUseCase` in `backend/src/main/java/com/aiavatar/alterego/application/SendAlterEgoEmailUseCase.java`; rewire `AlterEgoEmailController` to call the use case (which calls `EmailSenderPort`) instead of the email service directly (US2 — symmetry with `AlterEgoUseCase`)
- [X] T049 [US2] Delete now-empty old packages: `service/`, `controller/`, `model/` (top-level), `config/` (top-level); update remaining imports

**Checkpoint**: US2 acceptance scenarios pass. Four top-level layer folders visible. ArchUnit rules green. The `PosterPipeline` is the only image post-processing call site.

---

## Phase 5: User Story 3 — Frontend state separation (Priority: P2)

**Goal**: Server state, session state, and component-local UI state each have one designated mechanism. New UI capabilities that don't need to mutate session state don't introduce a new reducer action.

**Independent Test**: A maintainer adds a hypothetical boolean Setup-time selector touching no more than three files outside `frontend/src/features/alterego/components/`, one reducer action, and the request-shape file. The custom ESLint rule blocks `useReducer` outside `state/`.

### Tests for User Story 3 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T050 [P] [US3] ESLint rule integration test in `frontend/tests/lint/state-rule.test.ts`: assert that a fixture file using `useReducer` outside `features/alterego/state/` produces the expected lint error; a fixture using `useReducer` inside `state/` is accepted
- [ ] T051 [P] [US3] Vitest re-render test in `frontend/src/features/alterego/state/selectors.test.ts` (extension): an irrelevant state change to a selector's inputs does NOT trigger consumer re-render — uses `@testing-library/react`'s `renderHook` + `React.Profiler` callback count (FR-2417)

### Implementation for User Story 3

- [X] T052 [P] [US3] Author `frontend/STATE.md` documenting the three rules (server / session / component-local) on one screen; reference the existing hooks (`useGenerateAlterEgo`, `useSendAlterEgoEmail`) and the existing `AlterEgoProvider` as the canonical seams (research.md R6); ensure the content satisfies the fixtures used by T050's ESLint rule integration test
- [X] T053 [US3] Add custom ESLint rule in `frontend/eslint.config.js`: `no-reducer-outside-state` — forbid imports / calls to `useReducer` from any path NOT matching `frontend/src/features/alterego/state/**`; rule body is ~15 lines using ESLint's flat-config rule API; record the rule's purpose in a comment

**Checkpoint**: US3 acceptance scenarios pass. ESLint rejects a misplaced `useReducer`. Selector memoization is asserted.

---

## Phase 6: User Story 4 — Test pyramid (Priority: P2)

**Goal**: Unit tier runs in < 10 s with no Spring context. Each tier runs from a standard Gradle task. Contract tests pin the HTTP shape and fail first if a request/response shape changes incompatibly.

**Independent Test**: Run `./gradlew :backend:unitTest` cold — completes < 10 s, no `@SpringBootTest` annotation loaded. Run `./gradlew :backend:contractTest --tests '*Email*'` — exercises only the email contract.

### Tests for User Story 4 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T054 [P] [US4] ArchUnit rule in `backend/src/test/java/com/aiavatar/alterego/arch/UnitTierIsolationTest.java`: no class under `unit/` may import `org.springframework.boot.test.*`, `org.springframework.boot.autoconfigure.*`, or any `@SpringBootTest`-bearing meta-annotation (FR-2420)
- [ ] T055 [P] [US4] Gradle test-tier smoke test in `backend/src/test/java/com/aiavatar/alterego/contract/TierAssignmentTest.java` (runs in `contractTest` tier): asserts that the JUnit 5 `@DisplayName` set returned by each tier's task matches an expected manifest count (sanity check that no test was misclassified during T056)

### Implementation for User Story 4

- [X] T056 [US4] Survey existing tests under `backend/src/test/java/com/aiavatar/alterego/`; move any test whose package does not match its tier (unit / service / contract / integration / arch) to the correct tier subpackage; report the count moved
- [X] T057 [US4] Confirm `swagger-request-validator-mockmvc` is wired into both `/api/alter-ego` and `/api/alter-ego/email` contract tests in `backend/src/test/java/com/aiavatar/alterego/contract/`; assert both **request AND response** validation is enabled against `specs/001-initial-poc/contracts/alter-egos.openapi.yaml` and `specs/023-email-send-image/contracts/alter-egos-email.openapi.yaml`
- [X] T058 [US4] Verify wall-clock budgets locally: `time ./gradlew :backend:unitTest` < 10 s, `time ./gradlew :backend:test` < 2 min on cold cache; if exceeded, identify the slowest offenders and either move them to a higher tier or budget the exception (SC-003)

**Checkpoint**: US4 acceptance scenarios pass. Each tier is sliceable. Unit tier is fast and Spring-free.

---

## Phase 7: User Story 5 — Observability (Priority: P3)

**Goal**: A failing request's seam, cause, and correlation ID are visible from log + Problem-Detail response alone. Photo bytes and provider API keys never appear in logs.

**Independent Test**: Inject a simulated failure at each of the four seams (validation, provider, overlay, email); each produces a log line + Problem-Detail response sharing one correlation ID, identifying the seam and the cause.

### Tests for User Story 5 (MANDATORY — must FAIL before implementation) ⚠️

- [X] T059 [P] [US5] Unit test in `backend/src/test/java/com/aiavatar/alterego/unit/boundary/http/CorrelationIdFilterTest.java`: generates 12-char base32 id when `X-Correlation-Id` header is absent; propagates inbound id when valid; replaces malformed inbound id (too long / non-alphanumeric / empty) with a fresh id; clears MDC in `finally`
- [X] T060 [P] [US5] Service test in `backend/src/test/java/com/aiavatar/alterego/service/boundary/http/ProblemDetailAdviceTest.java`: response body of every Problem-Detail carries `properties.correlationId` matching the request thread's MDC value
- [X] T061 [P] [US5] Integration test in `backend/src/test/java/com/aiavatar/alterego/integration/RedactionCoverageIT.java`: boots full app, fires a generate request with a known photo blob and a known fake API key in `application.yml`, captures all log output via a Logback `ListAppender<ILoggingEvent>` attached to the root logger in `@BeforeEach` (pattern already used by 003's `PhotoRedactionFilterIT`); asserts neither the photo bytes (SHA-256 fingerprint compared) nor the API-key value appears in any captured event (FR-2415, SC-008)
- [ ] T062 [P] [US5] Integration test in `backend/src/test/java/com/aiavatar/alterego/integration/SeamFailureDiagnosabilityIT.java`: parametrised over 4 seam-failure scenarios (validation reject, provider 5xx, overlay throws, email SMTP failure); asserts each case emits one log line containing `seam=`, `cause=`, `correlationId=` and one Problem-Detail response carrying the same `correlationId` (SC-006)

### Implementation for User Story 5

- [X] T063 [US5] Create `CorrelationIdFilter` in `backend/src/main/java/com/aiavatar/alterego/boundary/http/CorrelationIdFilter.java`: implements `OncePerRequestFilter`, registers as `@Order(1)` (before any logging), reads `X-Correlation-Id` header with input bounds (FR-2412); generates 12-char base32 id on absence/invalid; places into SLF4J MDC under key `correlationId`; writes back as response header; clears MDC in `finally`
- [X] T064 [US5] Update `ProblemDetailAdvice` (now at `boundary/http/`) to read `MDC.get("correlationId")` and set `problemDetail.setProperty("correlationId", value)` on every advice response; do not mention secrets in any property
- [ ] T065 [US5] Audit all log sites across `application/`, `infrastructure/`, `boundary/`: each must emit `StructuredArguments.kv("correlationId", MDC.get("correlationId"))` (most already do via `correlationId` UUID propagation — keep behaviour identical, just route through MDC instead of the explicit `UUID` parameter)
- [X] T066 [US5] Extend `PhotoRedactionFilter` (now at `boundary/logging/`) to also redact: (a) any property whose key matches `(?i).*api[_-]?key.*` or `(?i).*authorization.*`; (b) any value matching the configured `gemini.apiKey` / `falai.apiKey` at runtime; (c) any value matching `recipientEmail` field from the most recent request body. Verify by `RedactionCoverageIT` (T061)
- [X] T067 [US5] Add an explicit "seam" structured-arg to every catch-and-log site: `seam=validation` (boundary), `seam=provider` (application use case catch), `seam=overlay` (pipeline stages catch), `seam=email` (email use case catch); this is the field `SeamFailureDiagnosabilityIT` (T062) asserts

**Checkpoint**: US5 acceptance scenarios pass. Every failure path emits the seam + cause + correlationId triple. No sensitive bytes in logs.

---

## Phase 8: Polish & Cross-Cutting

**Purpose**: Verify the merged refactor against the quickstart and tidy up.

- [ ] T068 [P] Run `specs/024-architecture-refactor/quickstart.md` end-to-end; capture the seven step outputs as evidence in a PR comment
- [X] T069 [P] Update `CLAUDE.md` Recent Changes log with a 024 entry summarising the layer move, provider seam, pipeline, frontend STATE.md, test tiers, observability, and constitution amendment
- [X] T070 Audit `CLAUDE.md` (must run **after** T002 and T069 have landed): ensure the 024 Active Technologies entry names ArchUnit + `archTest` Gradle task + the four layer names + the v1.1.0 constitution bump; trim only duplicates from earlier features that the auto-generator left in — never trim a current entry. Drop the `[P]` marker; this task is ordered.
- [X] T071 Re-run full Gradle test (`./gradlew :backend:test`) + frontend (`npm test` + `npm run test:e2e`) on cold caches; confirm green; record wall-clock numbers for SC-003 in the PR description. **Explicit coverage note in the PR description**: this run is the verification for FR-2410 (overlay assets loaded once at startup — existing `@PostConstruct` lifecycle preserved by the moves in T034/T035, asserted by the existing service-tier tests for `PosterFrameAssetLoader` and `PosterTextFonts`) and FR-2419 (Surprise Me / Start Over reducer atomicity — preserved by the no-op-reducer-change posture of `024`, asserted by the existing `reducer.test.ts`).
- [X] T072 Confirm `git grep -E "service\.(gemini|falai|stub|fallback)\.|controller\.AlterEgo"` returns no production-code matches (only commit message / spec references) — proves the old paths are gone
- [ ] T073 [P] Run `npm audit --audit-level=high` in `frontend/` and `./gradlew :backend:dependencyCheckAnalyze`; resolve every HIGH/CRITICAL advisory before merge (Principle VI). The ArchUnit 1.3.x addition (T002) is the only new dependency in `024`; advisories surfaced by anything else are pre-existing and should be ticketed separately.
- [X] T074 [P] Run `./gradlew :backend:jacocoTestReport` and `(cd frontend && npm run test:coverage)`; confirm each module reports ≥ 90% line coverage (Principle III). If a module regresses below 90% versus pre-`024` baseline, add unit tests in the appropriate `unit/` package before merge; refactoring MUST NOT lower the coverage floor.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)** — no dependencies, must complete first; **T001 (constitution) MUST be the first commit** (research.md R5)
- **Phase 2 (Foundational)** — depends on Phase 1; BLOCKS all user stories
- **Phase 3 (US1)** — depends on Phase 2; can run in parallel with Phase 4 across distinct files
- **Phase 4 (US2)** — depends on Phase 2; **partial overlap with Phase 3** (US2's domain/prompt/policy moves must precede US1's `AlterEgoUseCase` refactor); see "Critical cross-phase dependency" below
- **Phase 5 (US3)** — frontend-only; depends only on Phase 1 (`STATE.md` from T004)
- **Phase 6 (US4)** — depends on Phase 1 (Gradle tier tasks from T003) and ideally Phases 3+4 having moved tests into the right tier packages
- **Phase 7 (US5)** — depends on Phase 2 (boundary package exists) and Phase 4 (boundary/http/ classes moved)
- **Phase 8 (Polish)** — depends on all preceding phases

### Critical cross-phase dependency

US1's T021 (refactor `AlterEgoUseCase`) depends on:

- T015, T016, T017, T018, T019, T020 (all provider + AccentResolver moves) — within US1
- T038 (`RandomCategorySelector` move) and T041 (`RoleOfRecord` helper) from US2 — because the orchestrator imports these

Recommendation: land US2's domain/policy moves (T038–T041) **before** US1's T021, even though they're listed in a later phase. This is the one place the per-phase ordering is strict.

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Principle III)
- Moves to new packages can run in parallel (different files) — marked [P]
- Class-renames (e.g. `AlterEgoService` → `AlterEgoUseCase`) come after their dependencies' moves
- Story complete before moving to next priority — except where the critical dependency above forces interleaving

### Parallel Opportunities

- All Setup tasks marked [P] (T003, T004) can run in parallel
- All Foundational tasks marked [P] (T006, T007, T008, T009, T010) can run in parallel
- Within US1: T011–T014 (tests), then T015–T019 (provider moves), are all [P] within each group
- Within US2: T024–T029 (tests), then T030–T040 (moves), are all [P] within each group
- US3, US4, US5 can run in parallel across team members once their respective prerequisites land

---

## Parallel Example: User Story 1 tests

```bash
# Launch all US1 tests in parallel (Principle III — they must fail first):
Task: "ArchUnit NoProviderBranchingTest in backend/src/test/java/com/aiavatar/alterego/arch/NoProviderBranchingTest.java"
Task: "AlterEgoUseCase mocked-port unit test in backend/src/test/java/com/aiavatar/alterego/unit/application/AlterEgoUseCaseTest.java"
Task: "FallbackImageGenerator unit test in backend/src/test/java/com/aiavatar/alterego/unit/infrastructure/provider/fallback/FallbackImageGeneratorTest.java"
Task: "StubImageGenerator unit test in backend/src/test/java/com/aiavatar/alterego/unit/infrastructure/provider/stub/StubImageGeneratorTest.java"

# Then launch all US1 provider moves in parallel:
Task: "Move Gemini image classes to infrastructure/provider/gemini/, implement ImageGeneratorPort"
Task: "Move fal.ai image classes to infrastructure/provider/falai/, implement ImageGeneratorPort"
Task: "Move stub classes; convert StubImageGenerator to throw GenerationFailure(NOT_CONFIGURED)"
Task: "Move FallbackPosterProvider and add FallbackImageGenerator wrapper"
Task: "Move Gemini character classes and stub character generator"
```

---

## Implementation Strategy

### MVP (US1 + US2)

The clarified scope is "all five user stories ship on `024`" (Q3). However, the **MVP that proves the refactor's thesis** is US1 + US2: the provider seam is stable and the layered map exists. With those two, every subsequent story is a tightening.

1. Complete Phase 1 (Setup) — constitution at v1.1.0, ArchUnit available
2. Complete Phase 2 (Foundational) — skeleton + ports
3. Complete Phase 3 (US1) and Phase 4 (US2), respecting the critical cross-phase dependency
4. **STOP and VALIDATE** — run quickstart.md steps 1, 2, 3 (provider seam + layered map + tests)

### Incremental delivery within branch `024`

After MVP:

5. Add US3 (frontend STATE.md + ESLint rule) — quickstart step 4
6. Add US4 (test pyramid verification) — quickstart step 1
7. Add US5 (observability) — quickstart step 5
8. Run Polish phase
9. Merge

Branch `024` is not merged to `main` until **all five stories meet their acceptance scenarios** (per the clarified Q3 scope).

### Parallel Team Strategy

With multiple developers (post-Phase 2):

- Developer A: US1 (Phase 3)
- Developer B: US2 (Phase 4), coordinating the cross-phase dependency with A
- Developer C: US3 (Phase 5) — frontend, fully independent
- Developer D: US4 (Phase 6), running after A+B's moves to verify tier classifications
- Developer E: US5 (Phase 7), running after A+B have created `boundary/http/`

---

## Notes

- [P] tasks operate on different files with no in-phase dependency
- [Story] label maps task to its user story for traceability against spec.md
- Each story should reach its checkpoint and have all acceptance scenarios pass before the next story is merged
- Verify each test FAILS before writing the implementation (Principle III)
- Commit after each task or logical task group; squash on merge if desired
- Avoid: introducing new persistence, new public HTTP fields, new visible UX, new runtime dependencies beyond ArchUnit (FR-2414, FR-2413, FR-2426, FR-2427)

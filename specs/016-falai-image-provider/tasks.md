---

description: "Task breakdown for feature 016-falai-image-provider"
---

# Tasks: fal.ai Image-Generation Provider — Pluggable Alongside Gemini

**Input**: Design documents from `/specs/016-falai-image-provider/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/alter-egos.openapi.yaml`, `quickstart.md`

**Tests**: MANDATORY per Principle III (Test-First Development — NON-NEGOTIABLE). Every test task MUST be written, pushed, and observed to FAIL before its paired implementation task is started. Unit line coverage gate ≥ 90% (Jacoco on backend, Vitest `coverage` on frontend).

**Organization**: Tasks grouped by user story (US1, US2, US3) so each story is an independently testable increment. **MVP is US1 alone.**

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Different files, no dependency on earlier incomplete tasks in this phase → safe to run in parallel.
- **[Story]**: Present on user-story phase tasks only (US1 / US2 / US3). Absent on Setup / Foundational / Polish.
- Paths are absolute within the repo (`backend/…`, `frontend/…`).

## Path Conventions

- **Backend**: `backend/src/main/java/com/aiavatar/alterego/…` · `backend/src/test/java/com/aiavatar/alterego/{unit,contract,integration}/…` · `backend/src/main/resources/application.yml`
- **Frontend**: `frontend/src/features/alterego/…` with colocated `*.test.ts`/`*.test.tsx` · E2E at `frontend/tests/e2e/…`
- **Feature new sub-packages** (created in this feature): `backend/src/main/java/com/aiavatar/alterego/service/falai/`, `backend/src/main/java/com/aiavatar/alterego/service/photo/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Configuration scaffold + `application.yml` block + `docker-compose.yml` env wiring + new package directories. No behaviour change visible to users yet.

- [X] T001 [P] Add `aiavatar.falai.*` config block to `backend/src/main/resources/application.yml` (api-key, model-id, endpoint-url, submit-timeout-ms, poll-timeout-ms, fetch-timeout-ms, end-to-end-timeout-ms, poll-initial-interval-ms, poll-max-interval-ms, max-input-bytes, max-input-longest-edge, reduced-target-longest-edge, reduced-jpeg-quality — values per `research.md` R6) with env-var-overridable defaults
- [X] T002 [P] Document the dual-profile-refusal rule (FR-1605) in an inline comment block above the `aiavatar.falai.*` block in `backend/src/main/resources/application.yml`: "Activating `gemini` AND `falai` simultaneously refuses startup — `ProviderProfileGuard` throws at @PostConstruct"
- [X] T003 [P] Update `docker-compose.yml` (repo root) `backend` service `environment:` block to thread `FAL_AI_API_KEY: ${FAL_AI_API_KEY:-}`, `FAL_AI_MODEL_ID: ${FAL_AI_MODEL_ID:-}`, `FAL_AI_END_TO_END_TIMEOUT_MS: ${FAL_AI_END_TO_END_TIMEOUT_MS:-}`. Do NOT thread any fal.ai env var into the `frontend` service (FR-1611)
- [X] T004 [P] Create the new sub-package directory `backend/src/main/java/com/aiavatar/alterego/service/falai/` (created on first file landed in T020+)
- [X] T005 [P] Create the new sub-package directory `backend/src/main/java/com/aiavatar/alterego/service/photo/` (created on first file landed in T015+)

**Checkpoint**: Gradle resolves; Spring boots on `default` profile with the new config keys present (unused). No behaviour change to existing users.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Wire-shape extension + interface delta + shared `Provider` enum + `PhotoReducer` refactor that EVERY user story depends on. Must complete before any US1/US2/US3 task begins, because the additive `provider` field is a multi-file coordinated change (backend + contract + frontend types) and the `PhotoReducer` move is a precondition for both 003's Gemini path and 016's new fal.ai path.

**⚠️ CRITICAL**: This phase contains the wire-contract change and the orchestrator/interface change. Both must land in a coordinated commit-set so that at the end of Phase 2 the existing test suite is green again on the `default` and `gemini` profiles, with the new `provider` field threaded through.

### Tests (MUST FAIL first — Principle III)

- [X] T006 [P] Unit test for the new `Provider` enum: wire-value round-trip (`@JsonValue` + `@JsonCreator`), rejection of unknown values, three values `gemini`/`falai`/`stub` — in `backend/src/test/java/com/aiavatar/alterego/unit/ProviderTest.java` (JUnit 5)
- [X] T007 [P] Extend `AlterEgoResponseTest`: (a) `ResponseMeta.real(UUID, Provider.GEMINI)` and `.real(UUID, Provider.FALAI)` factories serialize `provider` and `outcome=real`, no `reason`; (b) `ResponseMeta.fallback(UUID, FallbackReason)` always sets `provider=stub`; (c) compact-constructor rejects `(REAL, STUB)`, `(FALLBACK, GEMINI)`, `(FALLBACK, FALAI)`, `provider == null`; (d) `provider` field is always present in the JSON — in `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoResponseTest.java`
- [X] T008 [P] Update contract test: `AlterEgoControllerContractTest` validates response JSON against `specs/016-falai-image-provider/contracts/alter-egos.openapi.yaml` v4.0.0 (mandatory `meta.provider` enum-bound to `[gemini, falai, stub]`); add a stub-profile case asserting `provider="stub"`, `outcome="fallback"`, `reason="not_configured"` — in `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java`
- [X] T009 [P] Update `AlterEgoServiceTest`: (a) on a stubbed `ImageGenerator.providerName() == "stub"` happy path, the response now reports `outcome=fallback`, `reason=not_configured`, `provider=stub` (the wire-contract semantic shift documented in plan §Summary and OpenAPI v4.0.0); (b) on a stubbed `ImageGenerator.providerName() == "gemini"` happy path, response reports `outcome=real`, `provider=gemini`; (c) the structured log line carries `provider` and `attemptedProvider` keys — in `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoServiceTest.java`
- [X] T010 [P] New `PhotoReductionConfigTest` — assert compact-constructor invariants (positive numbers; `reducedTargetLongestEdge ≤ maxLongestEdge`; `reducedJpegQuality ∈ (0, 1]`) — in `backend/src/test/java/com/aiavatar/alterego/unit/PhotoReductionConfigTest.java`
- [X] T011 Update `PhotoReducerTest` to drive the reducer with an explicit `PhotoReductionConfig` parameter on every call (existing assertions on pass-through / re-encode / pixel-cap behaviour are preserved; just add the config arg) — in `backend/src/test/java/com/aiavatar/alterego/unit/photo/PhotoReducerTest.java`
- [X] T012 [P] Update frontend service test: `alterEgoClient.test.ts` — every mock response gains `meta.provider`; assert the type roundtrip; assert the stub-profile fixture has `provider: 'stub'` and the gemini-profile fixture has `provider: 'gemini'` — in `frontend/src/features/alterego/services/alterEgoClient.test.ts`

### Implementation

- [X] T013 [P] Create `Provider` enum in `backend/src/main/java/com/aiavatar/alterego/model/Provider.java` (3 values per data-model.md §1, `@JsonValue` wire, `@JsonCreator` lookup, mirrored after `Outcome` and `FallbackReason`)
- [X] T014 Update `AlterEgoResponse.java`: extend `ResponseMeta` with mandatory `Provider provider` field; add compact-constructor validation per data-model.md §2 (the 3-tuple invariant matrix); rewrite static factories — `ResponseMeta.real(UUID, Provider)` and `ResponseMeta.fallback(UUID, FallbackReason)` (the latter always sets `provider=Provider.STUB`); preserve `@JsonInclude(NON_NULL)` for the existing `reason` field — in `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoResponse.java`
- [X] T015 [P] Create `PhotoReductionConfig` record in `backend/src/main/java/com/aiavatar/alterego/service/photo/PhotoReductionConfig.java` (per data-model.md §5, with compact-constructor invariants per T010)
- [X] T016 Move `PhotoReducer` from `backend/src/main/java/com/aiavatar/alterego/service/gemini/PhotoReducer.java` to `backend/src/main/java/com/aiavatar/alterego/service/photo/PhotoReducer.java`; change its API to take a `PhotoReductionConfig` argument on every call (replacing the `GeminiProperties`-bound constructor field); the internal Thumbnailator-driven pass-through / re-encode logic is unchanged (depends on T015 — uses the new config record)
- [X] T017 Update `GeminiProperties.java`: add a `photoReductionConfig()` adaptor method that returns a `PhotoReductionConfig` populated from `maxInputBytes` / `maxInputLongestEdge` / `reducedTargetLongestEdge` / `reducedJpegQuality` — in `backend/src/main/java/com/aiavatar/alterego/config/GeminiProperties.java`
- [X] T018 Update `GeminiImageGenerator.java`: switch from injecting the old `PhotoReducer` constructor to injecting the moved one + calling `reducer.reduce(photo, props.photoReductionConfig())`; add `@Override public String providerName() { return "gemini"; }`; default `wantsExternalRetry()` (true) is fine — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiImageGenerator.java` (depends on T016, T017, T020)
- [X] T019 Update `StubImageGenerator.java`: add `@Override public String providerName() { return "stub"; }` — in `backend/src/main/java/com/aiavatar/alterego/service/stub/StubImageGenerator.java`
- [X] T020 Update `ImageGenerator.java` interface: add `String providerName()`; add `default boolean wantsExternalRetry() { return true; }` — in `backend/src/main/java/com/aiavatar/alterego/service/ImageGenerator.java`
- [X] T021 Update `AlterEgoService.generate(...)`: (a) compute `attemptedProvider` from `imageGenerator.providerName()` (mapping `"stub"` → `"none"`, otherwise the literal name); (b) consult `imageGenerator.wantsExternalRetry()` and skip `RetryTemplate.execute` when false; (c) on the happy path, build `ResponseMeta.real(correlationId, Provider.fromWire(imageGenerator.providerName()))` — but only when `providerName() != "stub"`; if the wired generator IS the stub, the orchestrator routes through the fallback branch with `reason=NOT_CONFIGURED` (this is the FR-1612-aligned contract shift); (d) thread `provider` AND `attemptedProvider` into both the structured INFO log line (real) and the WARN log line (fallback) per FR-1613 — in `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java` (depends on T013, T014, T020)
- [X] T022 [P] Update frontend `types.ts`: add `export type Provider = 'gemini' | 'falai' | 'stub'` and `provider: Provider` on `ResponseMeta` (mandatory, never null); update the JSDoc on the file's contract-mirror header to reference 016 — in `frontend/src/features/alterego/types.ts`
- [X] T023 [P] Update `useGenerateAlterEgo.ts` JSDoc only — note that `meta.provider` is observed but not consumed (FR-1622); discriminator on `meta.outcome` is unchanged — in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts`

**Checkpoint**: Full test suite (`./gradlew test`, `npm test`) is **green again**. The user-visible UI is unchanged. Default-profile runs now report `outcome=fallback, reason=not_configured, provider=stub` (was `outcome=real` in 003; this is the FR-1612-aligned shift). Gemini-profile runs report `outcome=real, provider=gemini` exactly as before plus the new `provider` field.

---

## Phase 3: User Story 1 — Run the same generation flow against fal.ai instead of Gemini, by configuration alone (Priority: P1) 🎯 MVP

**Goal**: On a `falai` profile with a valid `FAL_AI_API_KEY`, pressing Generate produces a fal.ai-sourced poster image on the Your Alter Ego tab, with `meta.provider: "falai"`, `meta.outcome: "real"` in the response. The user-visible flow is identical to a Gemini run; only the upstream HTTP call differs.

**Independent Test**: Run `GenerateAlterEgoFalAiIT` (backend integration, WireMock-stubbed queue happy path) — test must pass. Manually: `export FAL_AI_API_KEY=<real>; SPRING_PROFILES_ACTIVE=falai docker compose up`; complete Setup; press Generate; observe a generated poster and `meta.provider: "falai"`, `meta.outcome: "real"` in browser devtools.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T024 [P] [US1] Unit test `FalAiPropertiesTest`: env-var binding (every key reads from its `${FAL_AI_*}` env var); `isConfigured()` reflects blank vs non-blank api-key; sensible defaults (`model-id == "fal-ai/nano-banana-pro/edit"`, `endpoint-url == "https://queue.fal.run"`, `endToEndTimeoutMs == 30000`, `pollInitialIntervalMs == 1000`, `pollMaxIntervalMs == 5000`); `photoReductionConfig()` adaptor returns a record with the four matching fields — in `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPropertiesTest.java`
- [X] T025 [P] [US1] Unit test `FalAiPromptBuilderTest`: every Setup field (pose, archetype, universe, vibe, art-style, firstName) contributes a distinct natural-language token to the composed prompt (FR-1607 "measurably influence" precondition); the opening verb is `"Edit"` (research.md R3 / R11); when vibe is null the corresponding line is omitted — in `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPromptBuilderTest.java`
- [X] T026 [P] [US1] Unit test `FalAiClientTest`: assertions on the queue happy path with a mocked `HttpClient` + a deterministic `Clock` — submit POST body matches the R3 shape (data-URL `image_urls[0]`, `prompt`, `num_images=1`, `aspect_ratio="3:4"`, `output_format="jpeg"`); poll loop honours the deadline (last poll past `deadline` throws `GenerationFailure(TIMEOUT)`); result-fetch parses `images[0].url` and downloads the bytes; one transient HTTP 503 on submit is retried inside-budget; deadline-overrun in the poll loop maps to `TIMEOUT`; status `FAILED` with `detail: "request blocked: nsfw"` maps to `SAFETY_REFUSED` — in `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiClientTest.java`
- [X] T027 [P] [US1] Unit test `FalAiImageGeneratorTest`: blank `apiKey` short-circuits with `GenerationFailure(NOT_CONFIGURED)` pre-HTTP; `wantsExternalRetry()` returns `false`; `providerName()` returns `"falai"`; on success returns the `PosterImage` produced by the mocked `FalAiClient`; deadline is captured once before the client call — in `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiImageGeneratorTest.java`
- [X] T028 [P] [US1] Integration test `GenerateAlterEgoFalAiIT`: `@SpringBootTest(properties = "spring.profiles.active=falai")`, WireMock stubs (a) submit POST → 200 with `{request_id, status_url, response_url, status: "IN_QUEUE"}`, (b) poll GET → 200 with `status: "IN_PROGRESS"` once then `status: "COMPLETED"`, (c) result GET → 200 with `images[0].url` pointing at a WireMock-served tiny valid JPEG, (d) image-bytes GET → 200 with that JPEG. Asserts `meta.outcome="real"`, `meta.provider="falai"`, no `reason`, the response body's `poster.dataUrl` starts `data:image/png;base64,` (post-frame), `Authorization: Key <api-key>` header is sent, the API key does not leak into the response body — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFalAiIT.java`

### Implementation for User Story 1

- [X] T029 [P] [US1] Create `FalAiProperties.java` `@ConfigurationProperties(prefix = "aiavatar.falai")` record in `backend/src/main/java/com/aiavatar/alterego/config/FalAiProperties.java` (full shape per data-model.md §4, including `isConfigured()` and `photoReductionConfig()`); registered in `AlterEgoApplication.@EnableConfigurationProperties`
- [X] T030 [P] [US1] Create `FalAiPromptBuilder.java` in `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java` — mirrors the Gemini prompt skeleton but with the "Edit the reference photo to render the person as their alter ego." opener (research.md R3 / R11); display-label maps for every Setup enum (pose, archetype, universe, vibe, art-style — copy the existing 003 `GeminiPromptBuilder` maps verbatim per R11)
- [X] T031 [US1] Create `FalAiClient.java` in `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiClient.java` — single public method `PosterImage generateImage(FalAiProperties props, String prompt, PhotoPayload reduced, Instant deadline)`; depends on the constructor-injected `HttpClient`, `ObjectMapper`, and `Clock` (the latter for testability); implements the submit→poll→fetch loop per research.md R3/R5/R8 with the exception-mapping table from R5; package-private record DTOs (`FalAiSubmitResponse`, `FalAiStatusResponse`, `FalAiResultResponse`) co-located (depends on T029)
- [X] T032 [US1] Create `FalAiImageGenerator.java` in `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiImageGenerator.java` — `@Component @Profile("falai") @Primary` (mirrors 003's GeminiImageGenerator wiring); implements `ImageGenerator`; constructor wires `FalAiClient`, `FalAiPromptBuilder`, the moved `PhotoReducer`, `FalAiProperties`, `Clock`; pre-HTTP short-circuit for blank api-key; computes `deadline = clock.instant().plusMillis(props.endToEndTimeoutMs())` once; calls `client.generateImage(props, prompt, reduced, deadline)`; overrides `providerName() → "falai"` and `wantsExternalRetry() → false` (depends on T029, T030, T031, T020)
- [X] T033 [US1] Frontend test fixtures already updated in Phase 2 (T012); the additive `provider` field is mandatory in the type so every fixture in the codebase already includes it. No `testSupport.tsx` changes required.
- [X] T034 [US1] New Playwright e2e `frontend/tests/e2e/generate-falai.spec.ts` — stubs the network response with `meta.provider: 'falai'`, asserts the user-visible flow lands on a complete poster identical to Gemini, asserts no provider-specific UI surface (FR-1622); helpers.ts extended with `provider` override field defaulting `outcome=fallback ⇒ stub`, `outcome=real ⇒ gemini`

**Checkpoint**: At this point, **MVP is shippable**. With `SPRING_PROFILES_ACTIVE=falai FAL_AI_API_KEY=<key>` an operator gets a real fal.ai poster on Generate; without the key (still under `falai` profile) the response falls back to the stub with `reason=not_configured`. `default` and `gemini` profiles continue to behave as in Phase 2.

---

## Phase 4: User Story 2 — All my Setup inputs still shape the generated image when fal.ai is the provider (Priority: P1)

**Goal**: Pose, archetype, universe, vibe, art-style, firstName, and the photo each measurably influence the fal.ai-generated image — exactly the FR-1607 / SC-1602 contract.

**Independent Test**: Run `FalAiPromptBuilderTest` and a parametrised integration test (`GenerateAlterEgoFalAiInputAxisIT`) covering one A/B comparison per axis (archetype, universe, pose, art-style, vibe-present-vs-absent); each axis must produce a *different prompt* on the wire (the only thing we can deterministically assert against WireMock). The model-side image-difference assertion is left to the manual SC-1602 walkthrough.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T035 [P] [US2] Integration test `GenerateAlterEgoFalAiInputAxisIT`: covers archetype, universe, pose, art-style, and vibe-present-vs-absent axes — captures the submit-request body via WireMock and asserts pair-wise prompt-string differences. Vibe-absent additionally asserts the `Vibe / tone:` line is omitted.

### Implementation for User Story 2

- [X] T036 [US2] Review gate ✓ — `FalAiPromptBuilder` (T030) routes every Setup field (pose, archetype, universe, vibe-when-present, art-style, firstName) through the prompt template; T035 + `FalAiPromptBuilderTest` (T025) lock in pair-wise distinctness as a regression test.

**Checkpoint**: User Stories 1 AND 2 both work independently. The `falai` profile's prompt is full-fidelity to the Gemini path's prompt.

---

## Phase 5: User Story 3 — Graceful degradation when fal.ai is unavailable (Priority: P2)

**Goal**: When fal.ai is selected but unavailable (no key, network error, timeout, queue stall, rate-limit, malformed response, safety refusal), the user gets the same generic single-variant fallback notice and the same stub poster they'd see for a Gemini failure. The response carries `provider=stub`, `outcome=fallback`, and a `reason` from the FR-1615 enumeration. The dual-profile-activation refusal is enforced.

**Independent Test**: Run `GenerateAlterEgoFalAiFailureIT` (parametrised matrix) and `ProviderProfileGuardIT` — both must pass. Manually: `SPRING_PROFILES_ACTIVE=falai docker compose up` (no `FAL_AI_API_KEY`) → poster renders, `provider=stub, reason=not_configured` in devtools; then `SPRING_PROFILES_ACTIVE=gemini,falai docker compose up` → backend exits non-zero with `event=startup.fatal reason=ambiguous_provider`.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T037 [P] [US3] Unit test `ProviderProfileGuardTest` — covers both-profiles-active throw, profile-order-independence, single-profile no-throw, default-profile no-throw, unrelated-profile coexistence.
- [X] T038 [P] [US3] Integration test `ProviderProfileGuardIT` — boots the full Spring context with `--spring.profiles.active=gemini,falai`; asserts `BeanCreationException` (Spring Boot's wrapper of the `@PostConstruct` failure) with `IllegalStateException` root cause naming both profiles.
- [X] T039 [P] [US3] Parametrised integration test `GenerateAlterEgoFalAiFailureIT` — covers connection-reset, HTTP 503, HTTP 429, submit per-attempt timeout, queue stall, malformed submit, and FAILED-with-safety-detail. Each row asserts `outcome=fallback, provider=stub, reason=<expected>` plus a complete poster.
- [X] T040 [P] [US3] Integration test `FalAiTimeoutBudgetIT` — CI-friendly variant uses a `@TestConfiguration` advancing `Clock` that jumps past `endToEndTimeoutMs` on its second tick, forcing the deadline check inside the poll loop. Asserts `reason=timeout, provider=stub` and elapsed wall-clock < 10 s.
- [X] T041 [P] [US3] `LogRedactionIT` extended with a nested `@SpringBootTest` under `falai` profile — asserts the structured log carries `provider=falai` AND `attemptedProvider=falai`; asserts neither the API key, nor the CDN URL, nor any photo data URL leaks into application logs.
- [X] T042 [P] [US3] `GenerateAlterEgoIT` updated in Phase 2 (T009) — default-profile happy path now asserts `provider="stub"`, `outcome="fallback"`, `reason="not_configured"`.
- [X] T043 [P] [US3] `GenerateAlterEgoGeminiIT` updated — happy-path success now also asserts `meta.provider == GEMINI`.
- [X] T044 [P] [US3] `GenerateAlterEgoGeminiFailureIT.assertFallbackOutcome(...)` extended — every fallback row in the matrix now also asserts `meta.provider == STUB`.

### Implementation for User Story 3

- [X] T045 [US3] `ProviderProfileGuard` `@Configuration` class with `@PostConstruct` check that throws `IllegalStateException` (carrying a message that names both active real-provider profiles) when both `gemini` and `falai` are present in `Environment.getActiveProfiles()`. Fatal `event=startup.fatal reason=ambiguous_provider` log line per research.md R7.
- [X] T046 [US3] Review gate ✓ — `FALLBACK_NOTICE_COPY` in `frontend/src/features/alterego/constants.ts` is byte-identical to its 003 wording (`"Showing a preview image — live AI generation isn't available right now."`). FR-1615 parity rule holds: the user-visible fallback for fal.ai is indistinguishable from Gemini's.

**Checkpoint**: All three user stories pass independently. Default / `gemini` / `falai` profiles all behave per spec; `gemini,falai` refuses startup; every fallback path serves the user a complete poster with the generic notice.

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Documentation, accessibility validation, dependency audit, manual SC validation. No new behaviour.

- [X] T047 [P] Created `backend/PROVIDERS.md` — three profiles (`default`/`gemini`/`falai`), env-var matrix, FR-1605 refusal, 30 s end-to-end budget, gotcha table.
- [X] T048 [P] Existing `frontend/tests/e2e/axe-scan.spec.ts` covers loading / success / fallback states under the gemini-mocked happy path (which is byte-identical to the falai happy path per FR-1622). The fixture's `meta.provider` field was added to keep TypeScript strict typing happy. SC-1610 satisfied — no fal.ai-specific UI was introduced, so the existing scan's coverage carries over.
- [ ] T049 [P] (operator) Run `./gradlew dependencyCheckAnalyze` and `npm audit` before merge. 016 adds **no new runtime dependency** (research.md R2), so the baseline established by 003/014 is expected to hold.
- [ ] T050 [P] (operator) Run `npm run test:coverage` and `./gradlew jacocoTestReport jacocoTestCoverageVerification` before merge — assert ≥ 90% line coverage gate per Constitution Principle III.
- [ ] T051 (operator) Manual SC-1601..SC-1611 walkthrough per `quickstart.md` — record observed `meta.provider` values and wall-clock timings in the PR description.
- [ ] T052 [P] (operator) Run SonarQube analysis (`./gradlew sonar` against `localhost:9000`) — resolve all NEW issues per Constitution Development Workflow step 6.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories. The wire-shape additive change (`provider` field) and `PhotoReducer` move are coordinated multi-file changes that must land before any provider work begins.
- **User Stories (Phase 3+)**: All depend on Foundational. Within Phase 2, T013 (Provider enum) → T014 (ResponseMeta extension) → T021 (AlterEgoService) is a strict chain; T015 → T016 → T018 (PhotoReducer move + Gemini rewire) is a parallel strict chain; T020 (interface delta) → T018, T019, T021 (consumers).
- **US1 (Phase 3)**: Depends on Foundational. Within US1, T029 → T031 → T032 (FalAiProperties → FalAiClient → FalAiImageGenerator) is a strict chain; T030 (PromptBuilder) is parallel to that chain.
- **US2 (Phase 4)**: Depends on US1 (specifically T030 + T032). Mostly a review-and-test gate.
- **US3 (Phase 5)**: Depends on Foundational + US1 implementation. T045 (ProviderProfileGuard) is independent of every other implementation task and could land in Phase 2 or Phase 5 — placed in Phase 5 because the user-facing failure modes it protects against are US3's domain.
- **Polish (Phase N)**: Depends on all desired user stories.

### User Story Dependencies

- **US1 (P1) — MVP**: Can start after Foundational. No cross-story dependencies.
- **US2 (P1) — input axis**: Most assertions are met by US1's prompt builder; US2 adds the parametrised axis test to lock in FR-1607 against future regressions.
- **US3 (P2) — graceful degradation**: Depends on US1 (`FalAiImageGenerator` existing); independently testable thereafter.

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Principle III — non-negotiable).
- Models and config records before services (e.g. T029 `FalAiProperties` before T031 `FalAiClient`).
- Services before integration with the orchestrator (T031 before T032).
- Story complete before moving to next priority.

### Parallel Opportunities

- **Phase 1**: T001 / T002 / T003 / T004 / T005 all `[P]` — different files, no dependencies.
- **Phase 2 tests**: T006 / T007 / T008 / T009 / T010 / T012 all `[P]` (different test files); T011 is sequential because it edits the existing `PhotoReducerTest`.
- **Phase 2 impl**: T013 / T015 / T022 / T023 are `[P]` (different files); the rest form dependency chains.
- **US1 tests**: T024 / T025 / T026 / T027 / T028 all `[P]`.
- **US1 impl**: T029 / T030 / T033 / T034 are `[P]`; T031 → T032 is the sequential chain.
- **US3 tests**: T037 / T038 / T039 / T040 / T041 / T042 / T043 / T044 all `[P]`.
- **Polish**: T047 / T048 / T049 / T050 / T052 all `[P]`.

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests together (they MUST fail before implementation):
Task: "Unit test FalAiPropertiesTest in backend/src/test/java/com/aiavatar/alterego/unit/FalAiPropertiesTest.java"
Task: "Unit test FalAiPromptBuilderTest in backend/src/test/java/com/aiavatar/alterego/unit/FalAiPromptBuilderTest.java"
Task: "Unit test FalAiClientTest in backend/src/test/java/com/aiavatar/alterego/unit/FalAiClientTest.java"
Task: "Unit test FalAiImageGeneratorTest in backend/src/test/java/com/aiavatar/alterego/unit/FalAiImageGeneratorTest.java"
Task: "Integration test GenerateAlterEgoFalAiIT in backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFalAiIT.java"

# Once all five tests fail and are pushed, launch parallel implementation:
Task: "Create FalAiProperties in backend/src/main/java/com/aiavatar/alterego/config/FalAiProperties.java"
Task: "Create FalAiPromptBuilder in backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java"
Task: "Update generate-flow e2e in frontend/tests/e2e/generate-flow.spec.ts"
# Then sequentially: FalAiClient → FalAiImageGenerator
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (config keys + env wiring + new package dirs).
2. Complete Phase 2: Foundational (wire-contract additive `provider` field + `PhotoReducer` move + `ImageGenerator` interface delta + `AlterEgoService` rewire). **Gate**: full test suite green; default-profile run now reports `outcome=fallback, provider=stub`; gemini-profile run reports `outcome=real, provider=gemini`.
3. Complete Phase 3: User Story 1 (`FalAiProperties` → `FalAiPromptBuilder` → `FalAiClient` → `FalAiImageGenerator`; integration happy-path test green).
4. **STOP and VALIDATE**: Manual quickstart §2c + browser devtools check.
5. Deploy / demo if ready. **MVP**: an operator who sets `SPRING_PROFILES_ACTIVE=falai FAL_AI_API_KEY=<k>` gets fal.ai-generated posters; no other UX change.

### Incremental Delivery

1. Setup + Foundational → contract migration ready (no behaviour change).
2. Add US1 → fal.ai happy path works → demo (MVP).
3. Add US2 → input-axis assertions lock in regression-resistance → demo.
4. Add US3 → graceful degradation + dual-profile guard → ship.
5. Polish → docs + accessibility + dep audit + manual SC walkthrough.

### Parallel Team Strategy

With multiple developers:

1. Team completes Phase 1 + Phase 2 together (the wire-contract + interface change is a shared seam — single PR).
2. Once Phase 2 is in:
   - **Developer A**: US1 (T024..T034) — the fal.ai happy-path slice.
   - **Developer B**: US3's `ProviderProfileGuard` slice (T037, T038, T045) — independent of US1's `FalAiImageGenerator`.
   - **Developer C**: Test-only updates to existing 003 tests (T042, T043, T044) — additive `provider` assertions; no implementation file edits.
3. After US1 lands, US2 + US3's failure-mode tests (T035, T039, T040, T041) follow in either order.

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks in the same phase.
- [Story] label maps task to specific user story for traceability (US1, US2, US3); Setup / Foundational / Polish tasks are unlabelled.
- Each user story should be independently completable and testable.
- Verify tests fail before implementing (`git push` the test commit, observe red CI, then commit the implementation).
- Commit after each task or logical group.
- Stop at any checkpoint to validate story independently.
- The wire-contract change in Phase 2 (additive `provider`, plus the FR-1612-aligned default-profile semantic shift to `outcome=fallback`) is the riskiest single landing — keep it tightly reviewed.
- This feature adds **no new runtime dependency**. If you find yourself wanting to introduce one (e.g. fal.ai's published Java SDK), reread `research.md` R2 first.
- The 30 s end-to-end fal.ai budget is the spec contract (FR-1614a); honour it via the `Clock`-injectable deadline check in `FalAiClient`, not via an outer `CompletableFuture.orTimeout(...)` race.

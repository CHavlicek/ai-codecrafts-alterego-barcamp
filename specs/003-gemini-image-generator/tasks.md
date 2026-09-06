---

description: "Task breakdown for feature 003-gemini-image-generator"
---

# Tasks: Gemini Image Generator — Real AI-Generated Alter-Ego Poster

**Input**: Design documents from `/specs/003-gemini-image-generator/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/alter-egos.openapi.yaml`, `quickstart.md`

**Tests**: MANDATORY per Principle III (Test-First Development — NON-NEGOTIABLE). Every test task MUST be written, pushed, and observed to FAIL before its paired implementation task is started. Unit line coverage gate ≥ 90% (Jacoco on backend, Vitest `coverage` on frontend).

**Organization**: Tasks grouped by user story (US1, US2, US3) so each story is an independently testable increment. MVP is US1 alone.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Different files, no dependency on earlier incomplete tasks in this phase → safe to run in parallel.
- **[Story]**: Present on user-story phase tasks only (US1 / US2 / US3). Absent on Setup / Foundational / Polish.
- Paths are absolute within the repo (`backend/…`, `frontend/…`).

## Path Conventions

- **Backend**: `backend/src/main/java/com/aiavatar/alterego/…` · `backend/src/test/java/com/aiavatar/alterego/{unit,contract,integration}/…` · `backend/src/main/resources/application.yml`
- **Frontend**: `frontend/src/features/alterego/…` with colocated `*.test.ts`/`*.test.tsx` · E2E at `frontend/tests/e2e/…`
- **Feature new sub-package**: `backend/src/main/java/com/aiavatar/alterego/service/gemini/` (created by T008)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependency additions and configuration scaffold so Phase 2 code compiles.

- [x] T001 Add Thumbnailator runtime dependency (`implementation("net.coobird:thumbnailator:0.4.20")`) to `backend/build.gradle.kts`
- [x] T002 [P] Add WireMock test-scope dependency (`testImplementation("org.wiremock:wiremock-standalone:3.3.1")`) to `backend/build.gradle.kts`
- [x] T003 [P] Bump Spring multipart limit from 5MB → 20MB: set `spring.servlet.multipart.max-file-size: 20MB` and `spring.servlet.multipart.max-request-size: 20MB` in `backend/src/main/resources/application.yml`
- [x] T004 [P] Add `aiavatar.gemini.*` config block (api-key, model-id, endpoint-url, request-timeout-ms, max-input-bytes, max-input-longest-edge, reduced-target-longest-edge, reduced-jpeg-quality — values per `research.md` R6) in `backend/src/main/resources/application.yml`
- [x] T005 [P] Add Spring profile activation rule: include `gemini` in `spring.profiles.active` when `GEMINI_API_KEY` env var is non-blank, via `spring.config.activate.on-profile` + `application-gemini.yml` OR a `@Profile` condition in `GeminiImageGenerator` (choose `@Profile` per plan — no new yml file needed) — document the chosen mechanism in an inline comment in `backend/src/main/resources/application.yml`
- [x] T006 [P] Update `docker-compose.yml` (repo root) `backend` service `environment:` block to thread `GEMINI_API_KEY: ${GEMINI_API_KEY:-}`, `GEMINI_MODEL_ID: ${GEMINI_MODEL_ID:-}`, `GEMINI_REQUEST_TIMEOUT_MS: ${GEMINI_REQUEST_TIMEOUT_MS:-}`. Do NOT thread any Gemini env var into the `frontend` service (FR-211)
- [x] T007 [P] Create the new sub-package directory `backend/src/main/java/com/aiavatar/alterego/service/gemini/` (and the matching test directory `backend/src/test/java/com/aiavatar/alterego/unit/gemini/` — can be created by first file landed)

**Checkpoint**: Gradle resolves new deps; Spring boots on `default` profile with the new config keys present (unused). No behaviour change visible to users yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Contract + shared types + wire rename that EVERY user story depends on. Must complete before any US1/US2/US3 task begins, because the rename breaks existing tests and both frontend and backend must update atomically.

**⚠️ CRITICAL**: The `Outcome.SUCCESS → REAL` rename (T009) is a coordinated multi-file change. All dependent edits in this phase must land in the same commit-set as T009 to keep `main` green.

### Tests (MUST FAIL first — Principle III)

- [x] T008 [P] Unit test for the new `FallbackReason` enum: wire-value round-trip (`@JsonValue` + `@JsonCreator`), rejection of unknown values — in `backend/src/test/java/com/aiavatar/alterego/unit/FallbackReasonTest.java` (JUnit 5)
- [x] T009 [P] Unit test extending `AlterEgoResponseTest` for (a) renamed `Outcome.REAL` wire value `"real"`, (b) `ResponseMeta.real(UUID)` factory returns `reason == null`, (c) `ResponseMeta.fallback(UUID, FallbackReason)` carries the reason, (d) compact-constructor rejects `REAL + non-null reason` and `FALLBACK + null reason`, (e) `@JsonInclude(NON_NULL)` so `reason` is absent from JSON when null — in `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoResponseTest.java`
- [x] T010 [P] Update contract test: `AlterEgoControllerContractTest` asserts response JSON against `specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml` v3.0.0 (outcome enum `["real","fallback"]`, optional `reason` enum-bound) — in `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java`
- [x] T011 [P] Update unit test: `AlterEgoServiceTest` — happy path now asserts `outcome == REAL` (not `SUCCESS`); catch-all RuntimeException branch now asserts `reason == FallbackReason.MALFORMED_RESPONSE`; add new case for `GenerationFailure(NOT_CONFIGURED) → ResponseMeta.fallback(NOT_CONFIGURED)` — in `backend/src/test/java/com/aiavatar/alterego/unit/AlterEgoServiceTest.java`
- [x] T012 [P] Update frontend service test: `alterEgoClient.test.ts` — rename all `outcome: 'success'` fixtures to `outcome: 'real'`; add an assertion that an unknown outcome wire value is treated as fallback (graceful-degradation hint from OpenAPI description) — in `frontend/src/features/alterego/services/alterEgoClient.test.ts`

### Implementation

- [x] T013 [P] Create `FallbackReason` enum (6 values per data-model.md §1.3, `@JsonValue` wire, `@JsonCreator` lookup) in `backend/src/main/java/com/aiavatar/alterego/model/FallbackReason.java`
- [x] T014 [P] Create `GenerationFailure extends RuntimeException` carrying a `FallbackReason reason` field (per data-model.md §2.2) in `backend/src/main/java/com/aiavatar/alterego/service/GenerationFailure.java`
- [x] T015 Update `AlterEgoResponse.java`: rename `Outcome.SUCCESS → REAL` with wire value `"real"`; extend `ResponseMeta` with optional `FallbackReason reason` field; add `@JsonInclude(NON_NULL)` on the record; add compact-constructor validation (REAL ⇒ null reason, FALLBACK ⇒ non-null reason); add static factories `ResponseMeta.real(UUID)` / `ResponseMeta.fallback(UUID, FallbackReason)` — in `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoResponse.java` (blocks T013/T014 if they imported anything — but they don't; T015 is independent of T013/T014)
- [x] T016 Update `AlterEgoService.generate(...)` to (a) use `ResponseMeta.real(correlationId)` on the happy path, (b) on `catch (GenerationFailure gf)`, populate `ResponseMeta.fallback(correlationId, gf.reason())`, (c) on `catch (RuntimeException other)` fall back with `reason = FallbackReason.MALFORMED_RESPONSE`, (d) log exactly one structured event per run (level INFO on success, WARN on fallback) with fields `event=generation.completed`, `outcome`, `correlationId`, and — on fallback — `reason` (FR-219 — further log-redaction test in US3 phase) — in `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java` (depends on T013, T014, T015)
- [x] T017 [P] Update `ResponseMeta` TypeScript type: `outcome: 'real' | 'fallback'`; add `reason?: FallbackReason`; add `type FallbackReason = 'not_configured' | 'network_error' | 'rate_limited' | 'timeout' | 'malformed_response' | 'safety_refused'`; unknown outcome defaults to `fallback` — in `frontend/src/features/alterego/services/alterEgoClient.ts`
- [x] T018 [P] Update `useGenerateAlterEgo.ts` discriminator: `result.meta.outcome === 'real'` drives `GenerateSucceeded`; any other value (including unknown) drives `GenerateFailedWithFallback` — the `reason` field is ignored for UX purposes per FR-214 — in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts`

**Checkpoint**: Full test suite (backend `./gradlew test`, frontend `npm test`) is **green again** after this phase. No behaviour change to end users; every existing run still takes the stub path but now reports `outcome: fallback, reason: malformed_response` — wait, no: under `default` profile with the stub returning normally, the happy path throws **no** exception, so `AlterEgoService` takes the REAL branch — which is wrong because stub-is-not-real.

> **Note for T016**: The `default` profile's `StubImageGenerator` returns normally today. After this phase but before US1 lands, a default-profile run would report `outcome: REAL` — a false positive. This is intentional and temporary: it indicates the rename-only state. Phase 3 (US1) installs the `@Profile("gemini")` `GeminiImageGenerator` and leaves the stub as the **fallback path only**, invoked via `FallbackPosterProvider`. The `default` profile's final behaviour (always fallback, reason=`not_configured`) is completed by T027 (wire the profile-switch).

---

## Phase 3: User Story 1 — Generate a real AI-rendered alter-ego from my Setup inputs (Priority: P1) 🎯 MVP

**Goal**: On a `gemini` profile with a valid `GEMINI_API_KEY`, pressing Generate produces a Gemini-sourced poster image on the Your Alter Ego tab, with `meta.outcome: "real"` in the response.

**Independent Test**: Run `GenerateAlterEgoGeminiIT` (backend integration, WireMock-stubbed happy path) — test must pass. Manually: `export GEMINI_API_KEY=<real>; docker compose up`; complete Setup; press Generate; observe a generated poster on the Your Alter Ego tab and `meta.outcome: "real"` in the browser devtools response.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [x] T019 [P] [US1] Unit test `GeminiPropertiesTest` — asserts env-var binding (`GEMINI_API_KEY`, `GEMINI_MODEL_ID`, `GEMINI_ENDPOINT_URL`, timeouts, size caps) and defaults per `research.md` R6; asserts `isConfigured()` true/false on blank/non-blank key — in `backend/src/test/java/com/aiavatar/alterego/unit/GeminiPropertiesTest.java`
- [x] T020 [P] [US1] Unit test `GeminiPromptBuilderTest` (happy path only — field-coverage variants are US2's T030) — asserts the builder produces a non-empty prompt containing the first name for a representative `AlterEgoRequest` — in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`
- [x] T021 [P] [US1] Unit test `PhotoReducerTest` — asserts (a) under-threshold photo passes through bytes-identical, (b) over-threshold photo is resized to `reducedTargetLongestEdge` with JPEG mime, (c) never up-scales, (d) EXIF-rotated phone photo orients correctly (test fixture: a small JPEG with rotation flag) — in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/PhotoReducerTest.java`
- [x] T022 [P] [US1] Unit test `GeminiClientTest` — mocks `java.net.http.HttpClient` via constructor-injection seam; asserts (a) POST URL `{endpointUrl}/models/{modelId}:generateContent`, (b) `x-goog-api-key` header carries the key, (c) request body contains `contents[0].parts[0].text` (prompt) + `contents[0].parts[1].inline_data.{mime_type, data}` (base64 photo) + `generationConfig.responseModalities == ["IMAGE"]`, (d) 200 + valid inline_data → returns a `PosterImage` with decoded bytes + mime + dimensions extracted via ImageIO — in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiClientTest.java`
- [x] T023 [P] [US1] Unit test `GeminiImageGeneratorTest` (happy path only — exception-mapping variants are US3's T041) — asserts a successful `client.generateImage(...)` return produces a non-null `PosterImage`; when `GeminiProperties.isConfigured()` returns false, the generator throws `GenerationFailure(NOT_CONFIGURED)` before any HTTP work — in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiImageGeneratorTest.java`
- [x] T024 [P] [US1] Integration test `GenerateAlterEgoGeminiIT` — `@SpringBootTest` with `@ActiveProfiles("gemini")`; WireMock on a random port, stubbed to return HTTP 200 + a valid Gemini response body carrying a tiny base64-encoded PNG; `@DynamicPropertySource` points `aiavatar.gemini.endpoint-url` at WireMock + sets `aiavatar.gemini.api-key = "test-key"`; performs a real `POST /api/v1/alter-egos` multipart request; asserts response status 200, `meta.outcome == "real"`, absent `meta.reason`, `poster.dataUrl` is a valid data-URL, WireMock records exactly one request to the expected URL carrying the expected auth header — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiIT.java`

### Implementation for User Story 1

- [x] T025 [P] [US1] Create `GeminiProperties` record `@ConfigurationProperties(prefix = "aiavatar.gemini")` per data-model.md §2.1, including `isConfigured()` helper — in `backend/src/main/java/com/aiavatar/alterego/config/GeminiProperties.java`; register via `@EnableConfigurationProperties(GeminiProperties.class)` on `AiAvatarApplication` (or equivalent existing `@SpringBootApplication` class)
- [x] T026 [P] [US1] Create `GeminiPromptBuilder` (`@Component`) — implements the template from `research.md` R3 with wire-value placeholders for enum labels (proper display-label maps deferred to US2's T032); always includes first name; includes vibe clause only when `request.vibe() != null` — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`
- [x] T027 [P] [US1] Create `PhotoReducer` (`@Component`) using Thumbnailator per data-model.md §2.3: pass-through when within both thresholds, else scale longest-edge to `reducedTargetLongestEdge` as JPEG at `reducedJpegQuality`, preserving EXIF orientation; returns a new `PhotoPayload` — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/PhotoReducer.java`
- [x] T028 [P] [US1] Create `GeminiClient` (`@Component`) wrapping `java.net.http.HttpClient` — constructor accepts the `HttpClient` for testability (Spring provides a default bean in `RetryConfig` or a new `@Configuration`); `generateImage(modelId, prompt, photo)` method builds the JSON body via Jackson `ObjectMapper`, sets `x-goog-api-key` header, applies `GeminiProperties.requestTimeoutMs` per-request timeout, parses the response per `research.md` R3, returns `PosterImage`; happy-path exception mapping to `GenerationFailure(MALFORMED_RESPONSE)` for parse failures; `HttpTimeoutException` → `GenerationFailure(TIMEOUT)`; OTHER exception-mapping deferred to T041 — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiClient.java`
- [x] T029 [US1] Create `GeminiImageGenerator` (`@Component @Profile("gemini")`) implementing `ImageGenerator` — composes `GeminiProperties.isConfigured()` check + `PhotoReducer.reduce(photo)` + `GeminiPromptBuilder.build(request)` + `GeminiClient.generateImage(...)`; throws `GenerationFailure(NOT_CONFIGURED)` pre-HTTP when not configured — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiImageGenerator.java` (depends on T025, T026, T027, T028)

**Checkpoint**: `./gradlew test` is green. `GenerateAlterEgoGeminiIT` passes. Manual demo per `quickstart.md` §3 with a real key produces a Gemini-generated poster. MVP complete.

---

## Phase 4: User Story 2 — All my Setup inputs shape the generated image (Priority: P1)

**Goal**: Each Setup field (pose, role, universe, vibe, firstName, photo) measurably influences the outbound Gemini request — proven by request-body assertions and prompt-axis coverage tests. Delivers SC-202's automatable half (visual differentiation observer-check remains a manual QA step in the Polish phase).

**Independent Test**: Run `GenerateAlterEgoGeminiInputCoverageIT` (parametrised; varies one Setup axis at a time with all others held constant; captures outbound WireMock requests; asserts the request body differs on the expected axis only). Unit: run `GeminiPromptBuilderTest` — field-coverage variants — all pass.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [x] T030 [P] [US2] Extend `GeminiPromptBuilderTest` with parametrised field-coverage cases: (a) changing role changes the prompt substring for role, (b) changing universe changes the universe substring, (c) changing pose changes the pose substring, (d) vibe-present produces a vibe clause; vibe-absent produces NO vibe clause, (e) firstName always appears verbatim in the prompt, (f) display labels are the human-readable variants ("Cloud Architect", "Star Wars", "Lord of the Rings", etc.) NOT the wire values ("cloud-architect", "star-wars", "lord-of-the-rings") — in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`
- [x] T031 [P] [US2] Integration test `GenerateAlterEgoGeminiInputCoverageIT` (`@SpringBootTest` + `@ActiveProfiles("gemini")` + WireMock) — parametrised with 6 pairs of Setup inputs (one axis varied per pair: role, universe, pose, vibe-present-vs-absent, firstName, **photo**); for each pair, issues both requests, captures WireMock's received request bodies, asserts the axis-varied token differs between the two and all other tokens match. Axes 1–5 parse the embedded prompt to verify the text token differs (or, for vibe-absent, that the vibe clause is present in one run and absent in the other). The **photo** axis uses two distinct JPEG fixtures (`backend/src/test/resources/fixtures/photo-a.jpg` and `photo-b.jpg`) and asserts `contents[0].parts[1].inline_data.data` base64 differs between runs, closing US2 acceptance scenario #4's request-side verification (the visual face-anchoring check stays manual, covered by T054) — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiInputCoverageIT.java`

### Implementation for User Story 2

- [x] T032 [P] [US2] Add display-label maps to `GeminiPromptBuilder`: `Map<Archetype, String>` ("Cloud Architect", "Backend Developer", "Frontend Developer", "AI Engineer", "Platform Engineer", "Data Engineer"), `Map<Universe, String>` ("Marvel superhero universe", "Star Wars", "Cyberpunk neo-noir", "The Office sitcom", "Indiana Jones adventure", "Lord of the Rings"), `Map<Pose, String>` ("heroic, chest forward", "stealthy, low profile", "mystical, ethereal", "scholarly, thoughtful"), `Map<Vibe, String>` ("builder / tinkerer", "thinker / strategist", "rebellious", "architectural, measured"); replace the T026 wire-value placeholders in the prompt template with these maps — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`
- [x] T033 [US2] Verify (no code change expected) that `GeminiClient`'s request body includes the photo bytes as `inline_data` — the unit test `GeminiClientTest` (T022) already asserts this shape; if the T031 integration test reveals the photo is missing, fix the request-body composition in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiClient.java` (no file change expected if T022 was done correctly)

**Checkpoint**: All automated input-coverage tests green. The manual SC-202 observer walkthrough (4-of-5 trials per axis) is deferred to the Polish phase (T057).

---

## Phase 5: User Story 3 — Graceful degradation when the real provider is unavailable (Priority: P2)

**Goal**: Every provider-boundary failure mode maps to a `FallbackReason`, a fallback poster, a generic user-visible notice (FR-214), and a structured backend log line — without blocking subsequent user interaction.

**Independent Test**: Run `GenerateAlterEgoGeminiFailureIT` (parametrised fault matrix) and `GenerateAlterEgoFallbackIT` (default profile, no key). Both must pass. Manually: follow `quickstart.md` §5.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [x] T034 [P] [US3] Unit test extension for `GeminiImageGeneratorTest` — the exception-mapping table from `research.md` R5 as parametrised test rows: HttpTimeoutException → `TIMEOUT`; HTTP 429 → `RATE_LIMITED`; HTTP 500 → `NETWORK_ERROR`; HTTP 4xx non-429 → `MALFORMED_RESPONSE`; `IOException`/`ConnectException`/`UnknownHostException` → `NETWORK_ERROR`; `SSLException` → `NETWORK_ERROR`; 200 body missing `candidates[0]…inline_data.data` → `MALFORMED_RESPONSE`; 200 body with `promptFeedback.blockReason = "SAFETY"` → `SAFETY_REFUSED`; 200 body with `candidates[0].finishReason = "IMAGE_SAFETY"` → `SAFETY_REFUSED` — in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiImageGeneratorTest.java`
- [x] T035 [P] [US3] Unit test extension for `GeminiClientTest` — same rows as T034 but asserted at the client layer (the thin HTTP wrapper is where the exception types first materialise) — in `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiClientTest.java`
- [x] T036 [P] [US3] Integration test `GenerateAlterEgoGeminiFailureIT` — parametrised with the SC-205 matrix exactly as enumerated in `quickstart.md` §5 (6 rows: network error / 5xx / 429 / timeout / malformed / safety); each row: stub WireMock with the fault, POST `/api/v1/alter-egos`, assert 200 response with `meta.outcome == "fallback"` AND `meta.reason == <expected code>`; subsequent healthy-stub request (re-stub WireMock happy) produces `outcome: "real"` (no sticky failure state, FR-214 scenario 3) — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiFailureIT.java`
- [x] T037 [P] [US3] Update `GenerateAlterEgoFallbackIT` (existing) — under `default` profile (no Gemini profile active, no key); asserts `outcome: "fallback", reason: "not_configured"`; character text still populated; fallback poster mime/dimensions valid — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFallbackIT.java`
- [x] T038 [P] [US3] Update `GenerateAlterEgoIT` (existing) — adjust existing assertions to match the new `outcome: "fallback", reason: "not_configured"` shape under `default` profile (this test used to assert `outcome: "success"`, which no longer exists) — in `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoIT.java`
- [x] T039 [P] [US3] Update `LogRedactionIT` (existing) — extend to assert (a) one structured log event per run with `event=generation.completed`, `outcome=<wire value>`, and — on fallback — `reason=<wire value>`, (b) neither the API key value nor photo bytes appear in **any** log line (existing `PhotoRedactionFilter` behaviour; new assertion covers the new log event too) — in `backend/src/test/java/com/aiavatar/alterego/integration/LogRedactionIT.java`
- [x] T040 [P] [US3] Frontend component test for the generic fallback banner copy (FR-214): `PosterView.test.tsx` asserts that when `errorMessage` is non-null the banner renders exactly the single FR-214 canonical copy string (imported from a shared constant — see T044), and NOT a provider name ("Gemini", "Google"), NOT a reason code ("not_configured", etc.), regardless of the `errorMessage` content passed — in `frontend/src/features/alterego/components/PosterView.test.tsx`
- [x] T041 [P] [US3] Frontend hook test `useGenerateAlterEgo.test.ts` — (a) `outcome: "fallback"` with any `reason` drives `GenerateFailedWithFallback` with the SAME generic `errorMessage` (FR-214); (b) `reason` field value is NOT surfaced in the `errorMessage`; (c) unknown outcome wire value is treated as fallback — in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.ts`

### Implementation for User Story 3

- [x] T042 [US3] Complete `GeminiClient` exception-mapping: extend T028's happy-path mapping to the full R5 table — `HttpTimeoutException → TIMEOUT`, HTTP 4xx (non-429) → `MALFORMED_RESPONSE`, HTTP 429 → `RATE_LIMITED`, HTTP 5xx → `NETWORK_ERROR`, `ConnectException`/`UnknownHostException`/`SSLException`/other `IOException` → `NETWORK_ERROR`, JSON parse failure or missing `inline_data.data` → `MALFORMED_RESPONSE`, `promptFeedback.blockReason == "SAFETY"` or `finishReason == "IMAGE_SAFETY"` → `SAFETY_REFUSED` — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiClient.java` (depends on T028)
- [x] T043 [P] [US3] Verify `GeminiImageGenerator` propagates `GenerationFailure` from `GeminiClient` unchanged (no extra wrapping) — no new code expected; the test in T034 is the gate — in `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiImageGenerator.java`
- [x] T044 [P] [US3] Create frontend constant `FALLBACK_NOTICE_COPY` (canonical string: *"Showing a preview image — live AI generation isn't available right now."*) and export it from a new file `frontend/src/features/alterego/constants.ts` (or the closest existing constants module) — consumed by T045 and T046
- [x] T045 [US3] Update `useGenerateAlterEgo.ts` — when `outcome !== 'real'`, dispatch `GenerateFailedWithFallback` with `errorMessage = FALLBACK_NOTICE_COPY` (ignore any `reason` value); on network / resilient-client fallback path, use the same constant — in `frontend/src/features/alterego/hooks/useGenerateAlterEgo.ts` (depends on T044, T017, T018 from Foundational)
- [x] T046 [US3] Update `PosterView.tsx` fallback banner copy: read the constant from T044; ensure the banner renders exactly that string (no interpolation of `reason`, no provider name); keep existing ARIA live-region behaviour unchanged (FR-214 announces politely per 001 FR-022) — in `frontend/src/features/alterego/components/PosterView.tsx` (depends on T044)
- [x] T047 [US3] Verify (no code change expected) that `AlterEgoService` continues to emit the structured log line added in T016 on every run — confirm the line is emitted on both REAL and FALLBACK paths; the `LogRedactionIT` assertion in T039 is the gate. If T039 reveals the line is missing on the REAL path, add an `INFO`-level emission symmetric to the WARN case — in `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java`

**Checkpoint**: All six fault rows pass in `GenerateAlterEgoGeminiFailureIT`. `GenerateAlterEgoFallbackIT` passes. Frontend shows the generic FR-214 banner on every non-real outcome. Demo checklist from `quickstart.md` §7 reproducible end-to-end. All SC-204, SC-205, SC-206, SC-208 criteria verifiable.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Constitution-gated cleanup, coverage sign-off, and the manual SC-202 walkthrough that no automated test can cover.

- [ ] T048 [P] Run `./gradlew dependencyCheckAnalyze` (from `backend/`); resolve any HIGH/CRITICAL CVE advisories raised by Thumbnailator or WireMock (none expected per research.md R4/R7) before merge — Principle VI gate
- [x] T049 [P] Run `npm audit --production` (from `frontend/`); resolve any HIGH/CRITICAL advisories before merge — Principle VI gate
- [x] T050 [P] Generate Jacoco coverage report (`./gradlew jacocoTestReport` in `backend/`); confirm ≥ 90% line coverage for every new module under `com.aiavatar.alterego.service.gemini.*` plus edited modules (`AlterEgoService`, `AlterEgoResponse`, `FallbackReason`, `GenerationFailure`); artefact at `backend/build/reports/jacoco/test/html/index.html` — Principle III gate
- [x] T051 [P] Generate Vitest coverage (`npm test -- --coverage` in `frontend/`); confirm ≥ 90% line coverage for the touched modules (`alterEgoClient.ts`, `useGenerateAlterEgo.ts`, `PosterView.tsx`, `constants.ts`); artefact at `frontend/coverage/index.html` — Principle III gate
- [ ] T052 [P] Run `./gradlew sonar` (requires SonarQube on `localhost:9000` per Development Workflow step 6); resolve all NEW issues before committing
- [ ] T053 [P] Update repo-root `README.md` if it documents env-var setup: add `GEMINI_API_KEY` (optional, enables real provider) and — optionally — `GEMINI_MODEL_ID`, `GEMINI_REQUEST_TIMEOUT_MS`. If no env-var section exists today, skip this task (don't create a new section just for this)
- [ ] T054 Manual SC-202 observer walkthrough — with a real key, run 5 A/B pairs each on the role / universe / pose axes (15 pairs total) against a fresh user's photo; confirm observers identify differences in ≥ 4 of 5 pairs per axis; capture a brief notes file at `specs/003-gemini-image-generator/sc-202-results.md` recording pass/fail per pair (not committed to main if it contains faces — decide at PR time whether to redact or keep local-only)
- [ ] T055 Manual SC-203 large-photo walkthrough — upload an 8-megapixel phone photo; confirm the run completes, the outbound WireMock-captured (or real Gemini if feasible) request body is under 4 MB post-reduction; record observed reduced size in `specs/003-gemini-image-generator/sc-203-results.md`
- [ ] T056 Manual SC-207 timing walkthrough — run 20 consecutive successful Generate cycles against real Gemini; record each wall-clock Generate-press → poster-rendered duration; confirm median < 15 s and p95 < 30 s; record at `specs/003-gemini-image-generator/sc-207-results.md`
- [ ] T057 Run the `quickstart.md` §7 demo checklist end-to-end (with key → without key → with key again) and tick each box; capture any discrepancy as a follow-up issue rather than silently fixing
- [x] T058 [P] Run `@axe-core/playwright` scans across the "Your Alter Ego" tab in three states — (a) loading (trigger via a long-delay WireMock stub or by intercepting the network request in Playwright), (b) real-provider success (happy-path fixture), (c) fallback (fault-injected run) — and assert **zero `serious` or `critical`** violations in all three; run as part of the frontend E2E suite with the spec at `frontend/tests/e2e/accessibility-alter-ego.spec.ts`; also assert that the `[role="status"]` / `aria-live` region announces the state transitions (loading → success and loading → fallback), inheriting the region's existing implementation from 002 per FR-214 / 001 FR-022 — closes SC-208

**Checkpoint**: All gates green. Ready for PR per Development Workflow step 8.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No upstream dependency. Can start immediately on `main`-derived branch.
- **Phase 2 (Foundational)**: Depends on Phase 1 completion (needs Thumbnailator + WireMock + config scaffold) for T013–T018 to compile / run. **Blocks all user stories** — the wire rename in T015 must land before any US1/US2/US3 test fixture that expects the new shape.
- **Phase 3 (US1)**: Depends on Phase 2. Delivers MVP.
- **Phase 4 (US2)**: Depends on Phase 3 (extends `GeminiPromptBuilder` created in T026 and the integration-test scaffold from T024).
- **Phase 5 (US3)**: Depends on Phase 3 for `GeminiClient` (T028) existing before T042 extends its exception-mapping. US3 does NOT depend on US2.
- **Phase 6 (Polish)**: Depends on Phases 3, 4, and 5 complete. Manual walkthroughs (T054–T057) require a working `gemini`-profile deployment.

### User Story Dependencies

- **US1 (P1 — MVP)**: Depends only on Phase 2. Can be shipped alone.
- **US2 (P1)**: Depends on US1 (uses `GeminiPromptBuilder` + integration test scaffold from US1). Sequential after US1.
- **US3 (P2)**: Depends on US1 (extends `GeminiClient` exception mapping). Independent of US2 — US3 can start in parallel with US2 once US1 is done.

### Within Each User Story

- All test tasks in a phase are written and observed to FAIL before any implementation task in the same phase (Principle III).
- Config / properties before services (T025 before T029).
- Adapter unit tests (T020–T023) before orchestration unit tests (T029-scope; covered under T023).
- Unit tests before integration tests within a phase (T019–T023 before T024).
- Backend exception mapping (T042) before the integration test that asserts it (T036) — but the TDD ordering is: write T036 first, watch it fail (because T042 is incomplete), then implement T042 until T036 goes green.

### Parallel Opportunities

- **Phase 1**: T001–T006 are independent — all [P], all parallelisable.
- **Phase 2 tests (T008–T012)**: different files, all [P], parallelisable.
- **Phase 2 implementation**: T013, T014, T017, T018 are all [P]. T015 and T016 are sequential (T016 depends on T015).
- **Phase 3 tests (T019–T024)**: all [P], parallelisable.
- **Phase 3 implementation**: T025, T026, T027, T028 are all [P]; T029 depends on them all.
- **Phase 4**: T030, T031 [P]. T032 [P] implements, T033 is verification-only (no code change expected).
- **Phase 5 tests (T034–T041)**: all [P].
- **Phase 5 implementation**: T043, T044 [P]. T045 depends on T044 + Foundational. T046 depends on T044. T042 is sequential after T028 (same file).
- **Phase 6**: T048–T053 and T058 all [P]. Manual walkthroughs T054–T057 sequential (require a running deployment with a real key).

---

## Parallel Example: User Story 1

```bash
# Phase 3 — launch all US1 test files in parallel (all different files):
Task: "Unit test GeminiPropertiesTest in backend/src/test/java/com/aiavatar/alterego/unit/GeminiPropertiesTest.java"
Task: "Unit test GeminiPromptBuilderTest in backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java"
Task: "Unit test PhotoReducerTest in backend/src/test/java/com/aiavatar/alterego/unit/gemini/PhotoReducerTest.java"
Task: "Unit test GeminiClientTest in backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiClientTest.java"
Task: "Unit test GeminiImageGeneratorTest (happy) in backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiImageGeneratorTest.java"
Task: "Integration test GenerateAlterEgoGeminiIT in backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiIT.java"

# After tests fail, launch US1 implementation in parallel (4 independent files):
Task: "Create GeminiProperties in backend/src/main/java/com/aiavatar/alterego/config/GeminiProperties.java"
Task: "Create GeminiPromptBuilder in backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java"
Task: "Create PhotoReducer in backend/src/main/java/com/aiavatar/alterego/service/gemini/PhotoReducer.java"
Task: "Create GeminiClient in backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiClient.java"

# Finally, sequentially:
Task: "Create GeminiImageGenerator (depends on all four above) in backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiImageGenerator.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) — T001–T007.
2. Complete Phase 2 (Foundational) — T008–T018. The wire rename breaks things; land the coordinated edits in a single commit-set so CI stays green.
3. Complete Phase 3 (US1) — T019–T029.
4. **STOP and VALIDATE**: run `GenerateAlterEgoGeminiIT` + manual key-in-hand walkthrough.
5. Deploy / demo. MVP shipped.

### Incremental Delivery

1. Setup + Foundational → Foundation ready.
2. US1 → MVP (`outcome: "real"` end-to-end with a key; default-profile `outcome: "fallback", reason: "not_configured"` without a key).
3. US2 → display-label maps + input-coverage tests → perceptibly differentiated outputs across roles/universes/poses.
4. US3 → full fault matrix green; fallback banner copy finalised; log lines emitted.
5. Polish → coverage + audits + manual SC walkthroughs → ready for PR.

### Parallel Team Strategy

Three developers after Phase 2:

- Dev A: US1 (T019–T029) — the MVP critical path.
- Dev B: waits for US1 T028 (`GeminiClient`), then picks up US3 (T034–T047) in parallel with Dev A's later US1 tasks and Dev C's US2.
- Dev C: waits for US1 T026 (`GeminiPromptBuilder`) + T024 (integration-test scaffold), then picks up US2 (T030–T033).

All three converge on Phase 6 Polish.

---

## Notes

- [P] tasks touch different files and have no incomplete upstream dependency.
- Every test task MUST fail before its paired implementation task is committed (Principle III — non-negotiable).
- Commit after each task or small logical group; maintain green CI on `main` by sequencing the Phase-2 rename carefully (single squash-merge, not a series of small commits that land a half-renamed tree).
- US1 alone is a complete MVP: a key-in-hand user gets a real Gemini poster; a key-less user gets a correct fallback with the new wire shape.
- Avoid: mixing US1 / US2 / US3 edits into the same file concurrently (they all touch `GeminiClient` or `GeminiPromptBuilder`); if two stories overlap a file, the order enforced in **Within Each User Story** above applies.
- The frontend `reason` field is received and typed (T017) but NEVER rendered to the user (FR-214). Any PR that surfaces the `reason` value in the UI fails review — T040 encodes this gate.

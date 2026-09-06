---

description: "Task list for feature 014-real-character-generator"
---

# Tasks: Real (LLM-backed) Character Generator

**Input**: Design documents from `/Users/dmytrokorniienko/aiavatar/specs/014-real-character-generator/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/wire-stability.md, quickstart.md

**Tests**: Test tasks are **MANDATORY** per Principle III of the project constitution (Test-First Development, NON-NEGOTIABLE). Every test below MUST be written and verified to FAIL before its corresponding implementation task is started. Unit line coverage MUST reach ≥ 90% per module; at least one end-to-end integration test exercises the complete user journey.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story. The five user stories from spec.md are implemented in priority order: US1+US2+US3 (P1 — the MVP increment), then US4 (P2 — operator-facing toggle), then US5 (P3 — single-key ergonomics).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4, US5)
- Include exact absolute file paths in descriptions

## Path Conventions

AI-Avatar is a web application (Java backend + React/TypeScript frontend) per the project constitution. This feature touches the backend only; the frontend tree is not modified.

- **Backend (Java 21 + Spring Boot 3, Gradle Kotlin DSL)**
  - Production sources: `backend/src/main/java/com/aiavatar/alterego/...`
  - Unit tests: `backend/src/test/java/com/aiavatar/alterego/unit/<Thing>Test.java`
  - Integration tests (`@SpringBootTest`): `backend/src/test/java/com/aiavatar/alterego/integration/<Journey>IT.java`
  - Test support helpers: `backend/src/test/java/com/aiavatar/alterego/testsupport/<Helper>.java`
  - Resources / config: `backend/src/main/resources/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Verify external preconditions before any code is touched. No production code is added in this phase.

- [X] T001 Verify the default Gemini text model id `gemini-2.5-flash` is reachable on `https://generativelanguage.googleapis.com/v1beta` against a developer API key (the same `GEMINI_API_KEY` 003 already uses). Document any divergence in `specs/014-real-character-generator/research.md` §R1. No production code change unless the model id needs adjustment. **Status**: Manual operator check — performed by the operator at `quickstart.md` §2 time. No CI gate.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Extend `GeminiProperties` and `application.yml` with the two new text-side fields that **every** user-story task below depends on, and add the shared WireMock test helper that the integration tests in US1, US2, and US4 all import.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. The new properties and the `GeminiTextWireMockStubs` helper are imports that every later task references.

- [X] T002 Failing unit test (Principle III): extended `backend/src/test/java/com/aiavatar/alterego/unit/GeminiPropertiesTest.java` with binding cases for `text-model-id` (default `gemini-2.5-flash`) + `text-request-timeout-ms` (default `15000`); existing 003 field bindings preserved. Constructor calls in `GeminiImageGeneratorTest`, `GeminiClientTest`, `PhotoReducerTest` updated to the 10-arg shape (compile-fix only, behaviour unchanged).
- [X] T003 [P] Implementation: extended `GeminiProperties` record with `textModelId` + `textRequestTimeoutMs` at the trailing position; `isConfigured()` unchanged.
- [X] T004 [P] Implementation: extended `application.yml` with `aiavatar.gemini.text-model-id: ${GEMINI_TEXT_MODEL_ID:gemini-2.5-flash}` and `text-request-timeout-ms: ${GEMINI_TEXT_REQUEST_TIMEOUT_MS:15000}`; inline 014 commentary added.
- [X] T005 [P] Implementation: created `backend/src/test/java/com/aiavatar/alterego/testsupport/GeminiTextWireMockStubs.java` with `happyPath(WireMockServer, String textModelId)`, `envelopeWrappingText(String)`, `envelopeWithFinishReason(String)`, `envelopeWithBlockReason(String)`, and the canonical `HAPPY_PATH_TRAIT_JSON` constant. Uses `urlPathEqualTo` to avoid colliding with image-side stubs.

**Checkpoint**: `GeminiProperties` carries the two new fields, `application.yml` advertises both env vars, and the WireMock helper is available to all integration tests. User-story implementation can now begin.

---

## Phase 3: User Story 1 — Receiving an LLM-authored character (Priority: P1) 🎯 MVP

**Goal**: Generate a Generate request under `SPRING_PROFILES_ACTIVE=gemini GEMINI_API_KEY=<valid-key>` and receive a poster whose `heroTitleLine2`, `tagline`, three `superpowers`, and `quote` are authored on the fly by Gemini for the user's specific Setup tuple, NOT loaded from `stubs/characters.json`.

**Independent Test**: Run a single `gemini`-profile request with WireMock returning the canonical happy-path body from T005. Assert `meta.outcome=real`, the four traits match the WireMock fixture (NOT the stub fixtures), and `heroTitleLine1` is the user's first name uppercased.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

> **Write these tests FIRST. Run them. Confirm they FAIL. Only then begin implementation.**

- [X] T006 [P] [US1] `GeminiCharacterPromptBuilderTest.java` — created. Asserts every Setup field contributes distinct substring, English-only instruction, `EXACTLY THREE` + `100`-character rules, vibe-line-omitted-when-null, firstName interpolated verbatim incl. apostrophes.
- [X] T007 [P] [US1] `GeminiCharacterClientTest.java` — created (happy-path subset). Asserts URL shape, `x-goog-api-key` header, `Content-Type`, single text part, `responseMimeType=application/json` + `responseSchema` declaring the four-trait shape with `superpowers` 3-element minItems/maxItems.
- [X] T008 [P] [US1] `GeminiCharacterResponseParserTest.java` — created (happy-path subset). Asserts four-trait round-trip, `firstName.toUpperCase` substitution wins over any JSON `heroTitleLine1`, NFC normalisation + trim of stored values.
- [X] T009 [P] [US1] `GeminiCharacterGeneratorTest.java` — created. NOT_CONFIGURED short-circuit (blank/null/whitespace key) without contacting collaborators; happy-path InOrder verification; firstName trim is applied before reaching the prompt builder and parser; every non-NOT_CONFIGURED `FallbackReason` from the client propagates unchanged.
- [X] T010 [US1] `GenerateAlterEgoGeminiCharacterIT.java` — created and **green**. Verifies `meta.outcome=real`, traits NOT in `stubs/characters.json` cloud-architect variants, exactly one POST per endpoint, `x-goog-api-key` header present.
- [X] T011 [US1] Extended `AlterEgoServiceTest.java` — parametrised over every `FallbackReason`: a character-side `GenerationFailure(reason)` threads the reason into `meta.reason` and the image generator is never invoked. Added `InOrder` regression-lock on the sequential character→image ordering.

### Implementation for User Story 1

- [X] T012 [P] [US1] `GeminiCharacterPromptBuilder.java` — created. Display-label maps for all five enums; renders the structured-output prompt per research §R3.
- [X] T013 [P] [US1] `GeminiCharacterClient.java` — created. Includes the **full** R5 failure-classification table (HttpTimeoutException → TIMEOUT, ConnectException/UnknownHostException/SSLException/IOException → NETWORK_ERROR, 504 → TIMEOUT, 429 → RATE_LIMITED, other 5xx → NETWORK_ERROR, 4xx → MALFORMED_RESPONSE, promptFeedback.blockReason / finishReason ∈ {SAFETY, RECITATION} → SAFETY_REFUSED, malformed body → MALFORMED_RESPONSE) — landed alongside US1 since the failure paths are tiny and the test surface naturally extends them. WARN structured logging at every classification point per research §R11.
- [X] T014 [P] [US1] `GeminiCharacterResponseParser.java` — created with the **full** FR-1406/FR-1407/FR-1408 guard suite (NFC + trim + non-blank + ≤ 100-codepoint + exactly-3-superpowers + every-required-key-present), since T021's malformed-fixture matrix would otherwise need a placeholder implementation. WARN structured logging with fixed-vocabulary `violation` labels per research §R11.
- [X] T015 [US1] `GeminiCharacterGenerator.java` — created. `@Component @Profile("gemini") @Primary`. NOT_CONFIGURED pre-HTTP short-circuit; firstName trim before delegating to prompt builder and parser.

**Checkpoint**: User Story 1 — happy path under the `gemini` profile produces LLM-authored character text. T010 IT is green. The MVP is shippable behind the existing operator toggle (`SPRING_PROFILES_ACTIVE=gemini` + `GEMINI_API_KEY=<key>`), but DO NOT merge yet — the failure path (US2) and the shape contract (US3) are NON-NEGOTIABLE before this can go to `main` (the spec marks them P1 alongside US1).

---

## Phase 4: User Story 2 — Honest fallback when the LLM call fails (Priority: P1)

**Goal**: Every provider-boundary failure (network / timeout / 429 / 5xx / malformed body / safety refusal / not-configured) surfaces as `meta.outcome=fallback` with a typed `meta.reason`. The orchestrator's existing fallback contract (001 FR-018, 003 FR-218) applies to character-text failures exactly the way it already applies to image failures.

**Independent Test**: Drive each row of the WireMock fault matrix (research §R9) and assert the resulting `meta.reason` matches the expected `FallbackReason`. Force the `force-stub-failure` profile and confirm SC-1404's ≤ 5 s budget still holds.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T016 [P] [US2] Extended `GeminiCharacterClientTest.java` with the full R5 failure-classification matrix — 18 cases covering every row: HttpTimeoutException, Connect/UnknownHost/SSLException, IOException, HTTP 500/502/503/504/429/400/401/403/404, non-JSON outer body, missing-candidates body, missing-text-part body, text-part-not-JSON body, promptFeedback.blockReason, finishReason=SAFETY, finishReason=RECITATION. All green.
- [X] T017 [US2] `GenerateAlterEgoGeminiCharacterFailureIT.java` created — 18 fault rows per research §R9 (connection-reset, 5xx, 429, fixed-delay timeout, 504, 4xx, malformed body, parser-level shape violations, safety refusals). Each asserts `meta.outcome=fallback` + correct typed `meta.reason` + `FallbackPosterProvider`'s "The Resilient" character + valid PNG poster. All green.
- [X] T018 [US2] Extended `GenerateAlterEgoGeminiNotConfiguredIT.java` — strengthened assertions to verify BOTH paths short-circuit pre-HTTP (asserts `heroTitleLine2 == "The Resilient"`, `tagline == "DEGRADED, NOT DEFEATED."`, exactly 3 superpowers — values that only `FallbackPosterProvider.character` produces). Green.

### Implementation for User Story 2

- [X] T019 [US2] Implemented inline with T013 — `GeminiCharacterClient` carries the full R5 failure-classification table.
- [X] T020 [US2] Implemented inline with T013 — every classification point in `GeminiCharacterClient` emits a fixed-schema WARN line via `kv("phase","gemini-text-call"), kv("status",<int-or-"n/a">), kv("reason", <wire>)`. Never logs prompt or response body.

**Checkpoint**: User Story 2 — every provider-boundary failure on the text path now produces a typed fallback. T017's full WireMock matrix is green. SC-1404 / SC-004 (forced-fallback ≤ 5 s) is preserved because the new code adds no new latency on the failure path (each fault terminates the per-attempt cycle as quickly as the image-side equivalent).

---

## Phase 5: User Story 3 — Trait shape and length stay safe for the existing poster layout (Priority: P1)

**Goal**: Every successful response materialises into a `GeneratedCharacter` with EXACTLY four traits, EXACTLY 3 superpowers, every trait non-blank and ≤ 100 NFC-normalised Unicode code points after trimming. Any violation routes to fallback (FR-1407).

**Independent Test**: Run the parametrised parser test fixtures — every malformed shape variant produces `MALFORMED_RESPONSE`; every well-formed variant produces a valid `GeneratedCharacter`. Run the IT-level assertion that an LLM response with a 105-codepoint tagline routes the request to fallback.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T021 [P] [US3] Extended `GeminiCharacterResponseParserTest.java` with full malformed-fixture matrix — non-object body (array/primitive/null/JSON-null) variants, missing-key (parametrised over the four required keys), wrong-superpower-count (0/1/2/4/5/10), superpowers-as-string, trait-as-number, trait-as-null, blank-trait variants (empty/space/tab/newline), blank-superpower-entry, exactly-100-codepoint trait passes, 101-codepoint trait fails, padded-with-whitespace-but-trimmed-to-100 passes, NFC normalisation of combining-acute, ZWJ-emoji-family code-point boundary (14 families = 98 codepoints passes, 15 = 105 fails), tolerates extra keys. **30 cases, all green.**
- [X] T022 [US3] Verified `GenerateAlterEgoGeminiCharacterFailureIT` (T017) covers parser-level violations end-to-end — `missingHeroTitleLine2`, `wrongSuperpowerCount`, `overlongTrait`, `blankTraitAfterTrim` rows all present and green. SC-1402 thus pinned at the IT layer too.

### Implementation for User Story 3

- [X] T023 [US3] Implemented inline with T014 — `GeminiCharacterResponseParser` carries the full FR-1406 / FR-1407 guard suite: type checks, NFC + trim + non-blank + ≤100-codepoint per trait, exactly-3-superpowers, every required key present, extras tolerated.
- [X] T024 [US3] Implemented inline with T014 — fixed-vocabulary `violation` labels (`non_object`, `missing_key:<key>`, `wrong_type:<key>`, `superpowers_count:<n>`, `trait_blank:<key>`, `trait_too_long:<key>`) emitted as `kv("phase","gemini-text-parse"), kv("reason","malformed_response"), kv("violation",<label>)` WARN structured args. Never logs the offending value.

**Checkpoint**: User Story 3 — the LLM is held to the trait-shape contract at the boundary. Every "looks plausible but wouldn't fit on the poster" response routes to fallback rather than producing a broken poster. The MVP increment (US1 + US2 + US3) is now complete and shippable.

---

## Phase 6: User Story 4 — Operator-controlled rollout (Priority: P2)

**Goal**: Operators flip between real and stub character paths via `SPRING_PROFILES_ACTIVE` + `GEMINI_API_KEY`, mirroring the image-side mechanism 003 established. The deterministic stub stays available for unit / integration / E2E tests; `force-stub-failure` continues to drive the SC-1404 fallback budget.

**Independent Test**: Restart the backend with `SPRING_PROFILES_ACTIVE=default` → verify `StubCharacterGenerator` answers and zero outbound text calls are made. Restart with `SPRING_PROFILES_ACTIVE=gemini` (key set) → verify `GeminiCharacterGenerator` answers, the stub is dormant, and the existing 5 `gemini`-profile ITs all still pass with the new text-endpoint WireMock baseline.

### Tests for User Story 4 (MANDATORY — must fail before implementation) ⚠️

- [X] T025 [US4] Extended `GenerateAlterEgoGeminiIT.java` — `GeminiTextWireMockStubs.happyPath` baseline in `@BeforeEach`; `text-model-id` + `text-request-timeout-ms` properties; tightened in-test image stubs from `urlPathMatching(".*")` to model-specific path. Both happy-path scenarios green.
- [X] T026 [P] [US4] Extended `GenerateAlterEgoGeminiFailureIT.java` — same baseline + tighter URL pattern. Re-registered the text baseline after the `wireMock.resetAll()` in the "no sticky error" scenario. All 7 fault rows green.
- [X] T027 [P] [US4] Extended `GenerateAlterEgoGeminiInputCoverageIT.java` — added text baseline; tightened image stub to `urlPathEqualTo`; updated `sendAndCaptureOutboundBody` to filter to image-only events (Generate now triggers two outbound calls). All 6 input-axis tests green.
- [X] T028 [P] [US4] Extended `GenerateAlterEgoGeminiNoLogoLeakIT.java` — added text baseline; tightened image stub; expanded the no-leak invariant to BOTH outbound bodies (FR-704 applies to every Gemini call). Updated count assertion from 1 to 2. Test green.

### Implementation for User Story 4

- [X] T029 [US4] `StubCharacterGenerator @Profile` narrowed from `{"default", "gemini"}` → `"default"`. Class body byte-unchanged. `StubCharacterGeneratorTest` continues to pass without modification (FR-1416 verified in T034 below).

**Checkpoint**: User Story 4 — operator toggle works in both directions. `SC-1408` ("operator can flip stub-vs-real with a single profile + env-var change — no code change") is satisfied. The five existing `@ActiveProfiles("gemini")` integration tests still pass with their new WireMock baselines.

---

## Phase 7: User Story 5 — Reuse one Gemini API key across image and character paths (Priority: P3)

**Goal**: A single `GEMINI_API_KEY` env var configures both the image and the character paths. Independent overrides for model id and request timeout work without cross-contamination.

**Independent Test**: Set `GEMINI_API_KEY` once, confirm both paths are configured. Override `GEMINI_TEXT_MODEL_ID` and confirm only the text path's model id changes; override `GEMINI_MODEL_ID` and confirm only the image path's model id changes.

### Tests for User Story 5 (MANDATORY — must fail before implementation) ⚠️

- [X] T030 [P] [US5] Created `SharedGeminiApiKeyWiringIT.java` (in `integration/` to avoid `@SpringBootConfiguration` discovery conflicts with `GeminiPropertiesTest.TestConfig` in the unit package). Asserts that under `@ActiveProfiles("gemini")`, `CharacterGenerator` resolves to `GeminiCharacterGenerator`, `ImageGenerator` resolves to `GeminiImageGenerator`, and both consume the same `GeminiProperties.apiKey()`.
- [X] T031 [P] [US5] Created `GeminiPropertiesEnvIT.java` with four `@Nested @SpringBootTest` classes covering the (image-override, text-override) truth table: defaults, text-only override, image-only override, both overridden. Asserts cross-contamination is impossible.

### Implementation for User Story 5

- [X] T032 [US5] No production code change required (all wiring already shipped in T003). T030 + T031 pass as expected against the post-T003 `GeminiProperties` shape.

**Checkpoint**: User Story 5 — single-key ergonomics confirmed by both unit and integration assertions. Operators have a one-secret-binds-both-paths story; no code outside the config record contributes to it.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Verify the constitutional gates, FR-1413 logging discipline, FR-1416 regression-net invariants, and the operator runbook are all green before opening the PR.

- [X] T033 [P] Extended `LogRedactionIT.java` with `event=generation.completed` exactly-once assertion (FR-1411). Added new `LogRedactionGeminiTextIT.java` under `@ActiveProfiles("gemini")` covering both happy and malformed-text paths: asserts no `firstName`/prompt/response-body leakage in either case, the parser's fixed-vocabulary `violation` label IS present, and exactly one `generation.completed` event per request. All green.
- [X] T034 [P] FR-1416 invariants verified — `git diff --stat HEAD` on `StubCharacterGeneratorTest.java`, `RecordInvariantsTest.java`, `AlterEgoControllerContractTest.java` returns empty; targeted `./gradlew test --tests "..."` for each passes without modification.
- [X] T035 [P] `./gradlew jacocoTestCoverageVerification` green — ≥ 90% line coverage across modules.
- [ ] T036 [P] `./gradlew sonar` — **deferred**: SonarQube not running locally in this environment. Run before PR per Workflow §6.
- [X] T037 [P] `npm audit --omit=dev` green (0 vulnerabilities). `./gradlew dependencyCheckAnalyze` — **deferred**: plugin not configured; run before PR per Principle VI.
- [X] T038 [P] `npm run lint && npm test && npm run build` all green (frontend untouched by this feature; 482/482 tests pass).
- [ ] T039 `quickstart.md` validation — **deferred**: requires manual invocations against running backend. Modes (a) and (c) are pinned by the integration test suite; modes (b) and (d) need an operator (live API key + manual force-stub-failure run).
- [X] T040 `CLAUDE.md` auto-update verified — speckit's Phase-1 hook appended `(014-real-character-generator)` entries to "Active Technologies" + "Recent Changes" sections during `/speckit.plan`.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: T001 has no dependencies. Run first; the rest of the work proceeds with confidence in the model id.
- **Phase 2 (Foundational)**: Depends on Phase 1. T002 must precede T003. T003 + T004 can run in parallel after T002. T005 can run in parallel with T002–T004 (different file).
- **Phase 3–5 (US1, US2, US3 — all P1)**: Depend on Phase 2. The MVP increment is **US1 → US2 → US3 in this order** because US2 extends `GeminiCharacterClient` (created in US1) and US3 extends `GeminiCharacterResponseParser` (created in US1). Within each story, tests precede implementation per Principle III.
- **Phase 6 (US4 — P2)**: Split across PR boundaries — see Implementation Strategy.
  - **T025–T028 (the four existing-IT WireMock baselines)** MUST merge in the **MVP PR** alongside Phase 3 (T015 specifically), because `@Primary` on `GeminiCharacterGenerator` would otherwise win the bean tiebreak under `@ActiveProfiles("gemini")` and the four existing `gemini`-profile ITs (`GenerateAlterEgoGeminiIT`, `…FailureIT`, `…InputCoverageIT`, `…NoLogoLeakIT`) would issue real outbound text calls and fail. Once T015 lands, T025–T028 can run in parallel with each other (different test files); T025 typically lands first as the canonical baseline for the helper from T005.
  - **T029 (the production change — narrowing `StubCharacterGenerator @Profile("default")`)** can stand alone in a small follow-up PR. It is behaviour-equivalent to the post-MVP-PR state (the `@Primary` tiebreak already steers traffic correctly); narrowing the stub's profile annotation is purely cosmetic cleanup.
- **Phase 7 (US5 — P3)**: Depends on Phase 2 only. Could in principle run in parallel with Phases 3–6, but realistically the unit tests (T030/T031) want the post-T003 `GeminiProperties` shape to compile against, and that's already in Foundational. No production code change in US5 (T032 is the empty shell).
- **Phase 8 (Polish)**: Depends on every preceding phase being complete. T033–T038 can all run in parallel (different gates / commands). T039 + T040 are sequential by virtue of being the final operator-facing checks.

### User Story Dependencies (re-statement)

| Story | Priority | Depends on (production code) |
|---|---|---|
| US1 | P1 | Phase 2 |
| US2 | P1 | US1 (extends `GeminiCharacterClient`) |
| US3 | P1 | US1 (extends `GeminiCharacterResponseParser`) |
| US4 | P2 | US1 + US2 + US3 (the new beans must exist before the stub's profile narrows). **Note**: T025–T028 ship in the MVP PR (see Phase 6 dependency note); only T029 lands as the standalone US4 cleanup PR. |
| US5 | P3 | Phase 2 only (no production-code change beyond Foundational) |

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Principle III — non-negotiable).
- Within US1: Builders + Client + Parser (T012–T014) can land in parallel; the Generator class (T015) depends on all three.
- Within US2: Client failure-mapping (T019) and structured logging (T020) are sequential by virtue of touching the same file, but T020 is a small append to T019's diff.
- Within US3: Parser shape guards (T023) and structured logging (T024) are sequential, same reason as US2.

### Parallel Opportunities (per phase)

- **Phase 2**: T003 ⫽ T004 (after T002), T005 ⫽ all of the above (different file).
- **Phase 3 — US1 tests**: T006 ⫽ T007 ⫽ T008 ⫽ T009 (four different test files).
- **Phase 3 — US1 impl**: T012 ⫽ T013 ⫽ T014 (three different prod files); T015 last (depends on the three).
- **Phase 4 — US2 tests**: T016 ⫽ T017 ⫽ T018 (three different test files).
- **Phase 5 — US3 tests**: T021 (single file); T022 is a verification, not a separate file.
- **Phase 6 — US4 tests**: T026 ⫽ T027 ⫽ T028 (three different existing test files); T025 first since it pins the canonical baseline.
- **Phase 7 — US5 tests**: T030 ⫽ T031 (different files).
- **Phase 8 — Polish**: T033 ⫽ T034 ⫽ T035 ⫽ T036 ⫽ T037 ⫽ T038 (different files / different commands).

---

## Parallel Example: User Story 1 (the MVP)

```bash
# Step A — write the four failing unit tests in parallel (different files):
Task: "Create GeminiCharacterPromptBuilderTest.java per T006"
Task: "Create GeminiCharacterClientTest.java per T007 (happy-path subset)"
Task: "Create GeminiCharacterResponseParserTest.java per T008 (happy-path subset)"
Task: "Create GeminiCharacterGeneratorTest.java per T009"

# Step B — confirm all four tests fail. Now write the failing IT and the AlterEgoServiceTest extension:
Task: "Create GenerateAlterEgoGeminiCharacterIT.java per T010"
Task: "Extend AlterEgoServiceTest.java with sequential-ordering and character-failure-routing cases per T011"

# Step C — implement the three new collaborators in parallel (different prod files):
Task: "Implement GeminiCharacterPromptBuilder.java per T012"
Task: "Implement GeminiCharacterClient.java per T013 (happy path only — no failure mapping yet)"
Task: "Implement GeminiCharacterResponseParser.java per T014 (happy path only — no shape guards yet)"

# Step D — wire the generator class. T015 depends on the three above.
Task: "Implement GeminiCharacterGenerator.java per T015"
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 + 3 — all P1)

1. Complete Phase 1 (T001).
2. Complete Phase 2 (T002 → T003 ⫽ T004 ⫽ T005).
3. Complete Phase 3 (US1) — happy path under `gemini` profile produces LLM-authored text.
4. Complete Phase 4 (US2) — every provider-boundary failure routes to typed fallback.
5. Complete Phase 5 (US3) — trait shape and length contract enforced at the parser.
6. **STOP and VALIDATE**: Run all three P1 user stories' independent-test recipes from spec.md. Run `./gradlew test` end-to-end. Run a manual smoke test from `quickstart.md` §2. The MVP is shippable *after* this validation, not before.
7. Continue to US4 / US5 only when the P1 increment is green.

### Incremental Delivery

The natural shipping cadence is:
- **PR 1 (Foundational)**: T001 → T005. Small, additive, no behaviour change. Easy review.
- **PR 2 (MVP)**: Phases 3 + 4 + 5 **plus T025–T028** (the four existing-IT WireMock baselines from Phase 6). The P1 increment plus the test-side fix-ups that the new `@Primary` annotation forces. Reasoning: the moment T015 lands, `GeminiCharacterGenerator @Profile("gemini") @Primary` becomes a `CharacterGenerator` candidate alongside the still-broad `StubCharacterGenerator @Profile({"default","gemini"})`; the `@Primary` tiebreak resolves to the Gemini bean under `@ActiveProfiles("gemini")`, so the four existing `gemini`-profile integration tests (`GenerateAlterEgoGeminiIT`, `…FailureIT`, `…InputCoverageIT`, `…NoLogoLeakIT`) would issue real outbound text calls and fail unless the WireMock baselines from T025–T028 ship in the same PR. The user stories themselves (US1/US2/US3) are also tightly coupled (same set of new classes), so splitting them would force a "half-done LLM character path" interim state on `main`; T025–T028 join them for the same "must merge atomically to keep `main` green" reason.
- **PR 3 (US4 cleanup)**: T029 only. Narrows `StubCharacterGenerator @Profile("default")`. Behaviour-equivalent to PR 2's end state — PR 2's `@Primary` tiebreak already routes traffic to the Gemini bean under `@ActiveProfiles("gemini")`; this PR removes the now-dormant Stub registration under the `gemini` profile for clarity. Trivial 1-line review.
- **PR 4 (US5)**: Phase 7. Assertion-only; could even be merged with PR 3 if the team prefers fewer round-trips. Production code change is zero.
- **PR 5 (Polish)**: Phase 8. Constitutional / coverage / lint / SonarQube / dependency-audit / quickstart-validation gate. Goes immediately before merge to `main`.

### Parallel Team Strategy

With multiple developers:

1. Dev A: drives PR 1 (Foundational) end-to-end; opens PR 2 against the post-PR-1 base.
2. Dev B: writes US1's failing tests (T006–T011) in parallel with Dev A's Foundational work, holds PR until Foundational merges.
3. Dev C: in parallel with Dev B's implementation (T012–T015), drafts US2's tests (T016–T018) and US3's tests (T021).
4. Dev A returns to PR 3 (US4) once PR 2 lands; Dev D handles PR 4 (US5) — both can run in parallel since they touch disjoint files.
5. Dev B picks up PR 5 (Polish).

---

## Notes

- [P] tasks = different files, no dependencies on incomplete tasks
- [Story] label maps task to specific user story for traceability
- Each P1 user story is independently testable but shares production code with its siblings — the MVP increment is best shipped as one PR (see Implementation Strategy)
- Tests fail BEFORE implementation in every phase — Principle III is non-negotiable
- Commit after each task or logical group; the PR cadence above suggests natural commit boundaries
- The 8 existing files this feature edits are: `GeminiProperties.java`, `application.yml`, `StubCharacterGenerator.java` (1-line annotation change), `AlterEgoServiceTest.java`, `GeminiPropertiesTest.java`, `GenerateAlterEgoGeminiIT.java`, `GenerateAlterEgoGeminiFailureIT.java`, `GenerateAlterEgoGeminiInputCoverageIT.java`, `GenerateAlterEgoGeminiNoLogoLeakIT.java`, `GenerateAlterEgoGeminiNotConfiguredIT.java`, `LogRedactionIT.java`. Everything else is a new file.
- The 11 new files this feature creates are: 4 production classes, 5 unit test classes, 2 integration test classes, 1 testsupport helper, 1 dedicated wiring test (T030's `SharedGeminiApiKeyWiringTest`). Plus the spec/plan/research/data-model/contracts/quickstart docs already on disk.
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independence beyond the MVP-coupling already documented above.

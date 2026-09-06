# Implementation Plan: Real (LLM-backed) Character Generator

**Branch**: `014-real-character-generator` | **Date**: 2026-04-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/014-real-character-generator/spec.md`

## Summary

This feature swaps the backend's stub `CharacterGenerator` for a real Google Gemini text-generation call, leaving every other behaviour established by 001/002/003 intact. Strict scope: **one Java seam (`CharacterGenerator`) gets a new implementation under the existing `gemini` Spring profile; the wire contract does not change** (FR-1415); the frontend is not touched (FR-1416 limits this feature to *adding* tests). The new bean lives next to `GeminiImageGenerator` in `com.aiavatar.alterego.service.gemini` and reuses the existing `aiavatar.gemini.api-key` so a single `GEMINI_API_KEY` env var configures both paths (FR-1414, US-5).

The new components are:
- **`GeminiCharacterGenerator`** (`@Profile("gemini")`, `@Primary`): pre-HTTP `NOT_CONFIGURED` short-circuit, then delegates to the prompt builder, the HTTP client, and the response parser. Mirrors `GeminiImageGenerator`'s shape exactly so a reviewer who knows 003 knows this.
- **`GeminiCharacterPromptBuilder`** (stateless, pure): maps the `AlterEgoRequest` setup tuple into a tightly-templated **structured-JSON** prompt that instructs the LLM to return one `{ heroTitleLine2, tagline, superpowers[3], quote }` object — English only (FR-1418), no extra prose, with the trait-length rule in the prompt itself.
- **`GeminiCharacterClient`** (`HttpClient` wrapper): `POST {endpoint}/models/{textModelId}:generateContent` with `responseMimeType: application/json` + `responseSchema`, returning the parsed JSON node. All the failure-classification work `GeminiClient` already does for the image path (timeout / 429 / 5xx / safety-refusal / malformed-body) is mirrored verbatim — same exception-mapping table, same `FallbackReason` enum, same `GenerationFailure` carrier.
- **`GeminiCharacterResponseParser`**: enforces FR-1406 (4 traits, exactly 3 superpowers, every trait non-blank ≤ 100 NFC code points; English-only language guard implemented in the prompt + relied on through Gemini's JSON-schema mode — see research §R3). Anything malformed → `GenerationFailure(MALFORMED_RESPONSE)`.

Existing `StubCharacterGenerator` profile narrows from `{"default", "gemini"}` → `"default"` to mirror how the image side already works (`StubImageGenerator @Profile("default")` vs `GeminiImageGenerator @Profile("gemini")`); under `SPRING_PROFILES_ACTIVE=gemini` the new Gemini character bean is the only `CharacterGenerator` candidate, exactly as the new Gemini image bean is the only `ImageGenerator` candidate. `ForceFailureCharacterGenerator @Profile("force-stub-failure")` is unchanged so SC-004 / SC-1404 remain end-to-end testable.

`GeminiProperties` is extended with two text-side fields — `textModelId` (default `gemini-2.5-flash`) and `textRequestTimeoutMs` (default **15000** per spec Q3) — plus matching env vars (`GEMINI_TEXT_MODEL_ID`, `GEMINI_TEXT_REQUEST_TIMEOUT_MS`). The shared `apiKey` (env: `GEMINI_API_KEY`) is unchanged. Image-side `modelId` and `requestTimeoutMs` are unchanged (FR-1417).

`AlterEgoService` keeps its current sequential **character → image** pipeline (research §R6). Each generator is wrapped in its own `RetryTemplate` execution exactly as today; on any final failure of *either* generator the existing `catch (GenerationFailure)` block populates `meta.outcome=fallback` + `meta.reason=<typed>` with no orchestration change. The structured `event=generation.completed` log line stays single (FR-1411).

No new Java dependency. No new frontend dependency. No new persistence (FR-1412). No new OpenAPI version.

## Technical Context

**Language/Version**: Java 21 (LTS) backend; TypeScript 5.x (strict) frontend. Unchanged from 001/002/003.

**Primary Dependencies**:
- Backend (existing, reused): Spring Boot 3.5, Spring Web MVC, Bean Validation, Spring `RetryTemplate`, `java.net.http.HttpClient`, Jackson, Logback + logstash-logback-encoder, `@SpringBootTest` + `MockMvc`, swagger-request-validator-mockmvc, **WireMock 3.x** (test-scope, already present from 003).
- Backend (new): **none**. The text-generation call uses the same `HttpClient` + Jackson + `RetryTemplate` stack as 003. No SDK is added (the constitutional Principle I + VI argument from 003 R2 applies unchanged: Google's Java SDK pulls a large gRPC/protobuf/guava/auth0-jwks transitive surface that this hand-rolled REST call avoids).
- Frontend: untouched. The existing `AlterEgoResponse.character` shape is the contract; no frontend file in this feature's diff.

**Storage**: N/A. No persistence — FR-1412 extends 001 FR-016 / FR-017 / FR-024 unchanged. The composed text prompt, the LLM response body, and the parsed `GeneratedCharacter` live only in process memory for one HTTP request. No in-RAM cache (FR-1420).

**Testing**: Backend — JUnit 5 + Mockito + `@SpringBootTest` + `MockMvc` + WireMock. Frontend — no new tests required (no frontend change). TDD per Principle III. Unit line coverage ≥ 90% per module (existing Jacoco gate).

**Target Platform**: Backend runs on `eclipse-temurin:21-jre-alpine` in Docker Compose. Frontend on `nginx:alpine` serving a Vite build. Egress to `https://generativelanguage.googleapis.com/` (already required by 003) is sufficient — no new outbound destination.

**Project Type**: Web application — `frontend/` (React + TS) + `backend/` (Java + Spring Boot) + `docker-compose.yml` at repo root. Unchanged.

**Performance Goals**:
- The character call's per-attempt timeout is **15 s** (FR-1417, env-overridable). The image call's 25 s default is unchanged.
- 95th-percentile wall-clock total-request latency MUST NOT regress by more than 15 s above the 003 baseline (SC-1409). Sequential orchestration means the worst case is `image_timeout + character_timeout = 25 s + 15 s = 40 s` after retries — still inside the budget the spec set.
- Median character-call end-to-end (Gemini text-only on `gemini-2.5-flash`) is empirically 2–5 s; median total Generate request rises by ~3 s on the happy path.

**Constraints**:
- Resilient HTTP (Principle IV): 5 attempts + exponential back-off + randomised jitter + fallback. Reuses the existing `RetryConfig`'s `RetryTemplate` bean. The character call is already inside `retryTemplate.execute(...)` in `AlterEgoService` — no new retry scaffolding.
- No persistence (FR-1412 / FR-1420). Logs MUST NOT include `firstName`, photo, or the raw LLM response body in plain text (FR-1413). The existing `PhotoRedactionFilter` already protects against the photo path; the new code MUST NOT log fields named `firstName`, `prompt`, `responseBody`, or any value derived from them. Operator visibility comes through the existing `event=generation.completed` line + `correlationId` only.
- Trait length rule: ≤ **100 NFC-normalised Unicode code points** per trait (clarification Q1). Same canonical unit the 011 `ValidFirstName` validator uses for `firstName` — one consistent rule across the codebase.
- Output language: **English only** (FR-1418, clarification Q2). Enforced primarily in the prompt; secondary defence is that the response parser MAY (but is not required to) reject obviously non-English strings — research §R3 settles where the line is.
- Prompt-injection posture: no character-generator-local sanitization layer; `firstName` is interpolated as-is, trusting the 011 validator at the controller boundary + the FR-1407 shape parser as defence-in-depth (FR-1419, clarification Q4).
- No caching/memoization (FR-1420, clarification Q5).
- Spring multipart limit: unchanged from 003 (20 MB). The character call sends only text + JSON, well under any practical limit.
- WCAG 2.1 AA: unchanged (no UI change).
- Zero deprecated dependencies (Principle VI): no new dependency added.

**Scale/Scope**:
- Backend: 4 new Java classes (`GeminiCharacterGenerator`, `GeminiCharacterClient`, `GeminiCharacterPromptBuilder`, `GeminiCharacterResponseParser`), 1 edited config record (`GeminiProperties` gains `textModelId` + `textRequestTimeoutMs`), 1 edited stub annotation (`StubCharacterGenerator @Profile("default")` only), 1 edited yaml (`application.yml` adds `aiavatar.gemini.text-model-id` + `aiavatar.gemini.text-request-timeout-ms`). ~7 production files; ~8–10 new/edited test files.
- Frontend: 0 files. The TypeScript types for `AlterEgoResponse.character` already match the unchanged wire shape.
- Contracts: no OpenAPI version bump. The 003 OpenAPI file (`specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml`) remains the source of truth; this feature's `contracts/` folder records that fact and adds a wire-stability note. See research §R8.

## Constitution Check

Evaluating each active principle of Constitution v1.0.2.

| Principle | Gate status | Evidence |
|---|---|---|
| **I. Modern & Secure Technology Stack (NON-NEGOTIABLE)** | ✅ PASS | Java 21 + Spring Boot 3.5, React 18 + TS strict — unchanged. **No new dependency added** in either build. The text call uses the same `java.net.http.HttpClient` + Jackson stack 003 already audited. WireMock is already a test-scope dependency. |
| **III. Test-First Development (NON-NEGOTIABLE)** | ✅ PASS (planned) | Tasks phase orders tests first: `GeminiCharacterGeneratorTest` (unit, Mockito-mocked client), `GeminiCharacterClientTest` (unit, Mockito-mocked `HttpClient` — exception mapping mirrors 003 R5), `GeminiCharacterPromptBuilderTest` (unit, asserts every Setup field contributes a distinct token; English-only instruction present; trait-length contract baked into the prompt), `GeminiCharacterResponseParserTest` (unit, parametrised over fixture transcripts: shape-correct, missing field, four superpowers, overlong tagline, blank trait, bad JSON, English-only guard), `GeminiPropertiesTest` updated for the new fields, `AlterEgoServiceTest` updated to verify the character path's `GenerationFailure` paths thread through unchanged, `GenerateAlterEgoGeminiCharacterIT` (integration, WireMock-stubbed text endpoint, success path), `GenerateAlterEgoGeminiCharacterFailureIT` (parametrised WireMock fault matrix mirroring 003's image matrix), and edits to existing `gemini`-profile integration tests adding text-endpoint WireMock stubs (without these the existing tests would issue real outbound text calls). Frontend: no new tests required. Coverage gate ≥ 90% per module. |
| **IV. Resilient HTTP Communication** | ✅ PASS | The character call sits inside `imageGenerator`'s sibling `retryTemplate.execute(...)` block in `AlterEgoService` (already wired). The 5-attempt + exponential back-off + jitter policy applies unchanged. `GenerationFailure extends RuntimeException` is retryable under the default policy. Final-failure fallback to `FallbackPosterProvider.character(firstName)` is reused via the existing `catch (GenerationFailure)` branch in `AlterEgoService` — no new control-flow surface. The new pre-HTTP `NOT_CONFIGURED` short-circuit is identical to the image-side one. |
| **V. Feature Branch Workflow** | ✅ PASS | Branch `014-real-character-generator` cut from `main` via `create-new-feature.sh`. Merge requires PR review per the constitution. |
| **VI. Zero Deprecated Dependencies** | ✅ PASS | No dependency added. `./gradlew dependencyCheckAnalyze` + `npm audit` continue to run at PR time as a Phase-2 task. Existing transitive surface from 003 is unchanged. |

**Gate**: PASS — no violations. The `## Complexity Tracking` section remains intentionally empty.

### Re-evaluation after Phase 1 (Design & Contracts)

After drafting the data model (`data-model.md`), the wire-stability contract note (`contracts/wire-stability.md`), and the operator runbook (`quickstart.md`), all principles still pass:

- No unexpected dependency surfaces during design. The text-side Gemini REST shape is the same `generateContent` endpoint the image side uses, with `responseMimeType: "application/json"` + `responseSchema` instead of `responseModalities: ["IMAGE"]`. Fits the existing `HttpClient` + Jackson stack identically.
- TDD ordering is preserved — unit tests for the four new classes precede their implementation; the integration matrix sits under `backend/src/test/java/com/aiavatar/alterego/integration/`.
- The wire shape does not change. There is no contract-breaking change in this feature; existing OpenAPI contract tests, `RecordInvariantsTest`, and `StubCharacterGeneratorTest` continue to pass without modification (FR-1416).

**Post-design gate**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/014-real-character-generator/
├── plan.md                    # This file (/speckit.plan command output)
├── research.md                # Phase 0 output — text model id, prompt + structured-output shape, parser strategy, exception mapping, sequential vs parallel
├── data-model.md              # Phase 1 output — GeminiProperties extension, four new service classes, profile-narrowing of StubCharacterGenerator
├── quickstart.md              # Phase 1 output — running locally with/without GEMINI_API_KEY for the text path; fault-injection matrix
├── contracts/
│   └── wire-stability.md      # Phase 1 output — explicit "no contract change" record, with pointer to the 003 OpenAPI as the source of truth
├── checklists/
│   └── requirements.md        # /speckit.specify output (passing)
├── spec.md                    # Feature spec (frozen after /speckit.clarify, with Q1–Q5 integrated)
└── tasks.md                   # NOT created by /speckit.plan — /speckit.tasks output
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/aiavatar/alterego/
│   ├── config/
│   │   ├── GeminiProperties.java                       # (edit) add textModelId, textRequestTimeoutMs; existing fields unchanged
│   │   ├── HttpClientConfig.java                       # (unchanged — single shared HttpClient, both image and character calls reuse it)
│   │   └── RetryConfig.java                            # (unchanged — single RetryTemplate bean)
│   ├── model/
│   │   ├── AlterEgoRequest.java                        # (unchanged)
│   │   ├── GeneratedCharacter.java                     # (unchanged — invariant: exactly 3 superpowers; the 100-codepoint cap is enforced by the parser, not the record)
│   │   ├── FallbackReason.java                         # (unchanged — closed enum reused as-is per FR-1410)
│   │   └── AlterEgoResponse.java                       # (unchanged — wire shape pinned by FR-1415)
│   ├── service/
│   │   ├── AlterEgoService.java                        # (unchanged — sequential character→image pipeline kept; both calls already retry-wrapped)
│   │   ├── CharacterGenerator.java                     # (unchanged — interface signature pinned by FR-1401)
│   │   ├── ImageGenerator.java                         # (unchanged)
│   │   ├── GenerationFailure.java                      # (unchanged — reused as the boundary exception)
│   │   ├── stub/
│   │   │   ├── StubCharacterGenerator.java             # (edit) annotation narrows from @Profile({"default","gemini"}) to @Profile("default") — see research §R8
│   │   │   ├── ForceFailureCharacterGenerator.java     # (unchanged — still @Profile("force-stub-failure"); SC-1404 preserved)
│   │   │   ├── StubImageGenerator.java                 # (unchanged)
│   │   │   └── ForceFailureImageGenerator.java         # (unchanged)
│   │   ├── fallback/
│   │   │   └── FallbackPosterProvider.java             # (unchanged — still produces the canned character + fallback poster on outer fallback)
│   │   └── gemini/
│   │       ├── GeminiImageGenerator.java               # (unchanged)
│   │       ├── GeminiClient.java                       # (unchanged — image-side HTTP wrapper; text path gets its own client to keep failure-mapping per-endpoint clean)
│   │       ├── GeminiPromptBuilder.java                # (unchanged — image-side prompt builder)
│   │       ├── PhotoReducer.java                       # (unchanged)
│   │       ├── GeminiCharacterGenerator.java           # (new) @Component @Profile("gemini") @Primary implements CharacterGenerator
│   │       ├── GeminiCharacterClient.java              # (new) thin HTTP wrapper around HttpClient — POST + JSON parse for the text endpoint
│   │       ├── GeminiCharacterPromptBuilder.java       # (new) builds the structured-JSON text prompt from AlterEgoRequest fields
│   │       └── GeminiCharacterResponseParser.java      # (new) parses the JSON response → GeneratedCharacter; enforces FR-1406 shape + length + non-blank rules
│   └── controller/AlterEgoController.java              # (unchanged)
├── src/main/resources/
│   ├── application.yml                                 # (edit) add `aiavatar.gemini.text-model-id` (env: GEMINI_TEXT_MODEL_ID; default `gemini-2.5-flash`) and `aiavatar.gemini.text-request-timeout-ms` (env: GEMINI_TEXT_REQUEST_TIMEOUT_MS; default 15000)
│   └── logback-spring.xml                              # (unchanged — PhotoRedactionFilter continues to protect FR-1413 since the new code never logs the photo)
└── src/test/java/com/aiavatar/alterego/
    ├── unit/
    │   ├── GeminiCharacterGeneratorTest.java           # (new) mocks GeminiCharacterClient; asserts: NOT_CONFIGURED short-circuit when apiKey blank, prompt + photoless call dispatched, GenerationFailure propagation
    │   ├── GeminiCharacterClientTest.java              # (new) mocks java.net.http.HttpClient; asserts the full R5-style failure-mapping table for the text endpoint (timeout / 504 → TIMEOUT; 429 → RATE_LIMITED; ConnectException / 5xx → NETWORK_ERROR; bad-JSON / missing-field / non-2xx 4xx → MALFORMED_RESPONSE; promptFeedback.blockReason / finishReason=SAFETY → SAFETY_REFUSED)
    │   ├── GeminiCharacterPromptBuilderTest.java       # (new) asserts every Setup field contributes a distinct substring (FR-1405); the English-only instruction is present; the explicit ≤100-codepoint and exactly-3-superpowers rules are present in the prompt; the optional vibe is included only when non-null; firstName is interpolated as-is (FR-1419 — no escaping pass)
    │   ├── GeminiCharacterResponseParserTest.java      # (new) parametrised over fixtures: happy path → GeneratedCharacter; missing field, four superpowers, blank trait, overlong trait, non-JSON body, JSON-but-wrong-types — all → MALFORMED_RESPONSE; firstName uppercase substitution is applied at parse time so the raw `heroTitleLine1` from the LLM (if any) is overwritten (FR-1408); NFC normalisation + code-point counting verified for emoji + combining-mark fixtures
    │   ├── GeminiPropertiesTest.java                   # (edit) add binding tests for textModelId and textRequestTimeoutMs (defaults + env override); existing fields' bindings continue to pass
    │   ├── AlterEgoServiceTest.java                    # (edit, narrow scope) add cases asserting that a character-side GenerationFailure routes to handleFallback with the right reason (mirroring the existing image-side cases); the sequential ordering (character runs before image) is regression-locked
    │   └── StubCharacterGeneratorTest.java             # (UNCHANGED — FR-1416)
    ├── contract/
    │   └── AlterEgoControllerContractTest.java         # (UNCHANGED — wire shape is pinned)
    └── integration/
        ├── GenerateAlterEgoGeminiCharacterIT.java      # (new) `gemini` profile, WireMock-stubbed text endpoint with a valid happy-path JSON body; asserts outcome=real, character traits come from the stub response (NOT from stubs/characters.json)
        ├── GenerateAlterEgoGeminiCharacterFailureIT.java # (new) `gemini` profile + WireMock fault matrix for the text endpoint (network reset, 5xx, 429, fixed-delay timeout, malformed body, safety refusal); each asserts outcome=fallback with the correct meta.reason; SC-1402 trait-shape contract is checked on the fallback character too
        ├── GenerateAlterEgoGeminiIT.java               # (edit) extend the existing happy-path integration test with a WireMock stub for the text endpoint so the test passes under the narrowed StubCharacterGenerator profile
        ├── GenerateAlterEgoGeminiFailureIT.java        # (edit) add a text-endpoint WireMock baseline so the existing image-failure assertions are unaffected by the new outbound text call
        ├── GenerateAlterEgoGeminiInputCoverageIT.java  # (edit) add a text-endpoint WireMock baseline (same as above)
        ├── GenerateAlterEgoGeminiNoLogoLeakIT.java     # (edit) add a text-endpoint WireMock baseline (same as above)
        ├── GenerateAlterEgoGeminiNotConfiguredIT.java  # (edit) extend assertion: with apiKey blank, BOTH paths short-circuit to NOT_CONFIGURED — the response is a fallback character + fallback poster
        ├── LogRedactionIT.java                         # (edit) add an assertion that the new text-call log surface does NOT include `firstName`, the prompt body, or the raw LLM response body (FR-1413)
        ├── GenerateAlterEgoIT.java                     # (UNCHANGED — `default` profile, StubCharacterGenerator + StubImageGenerator continue to answer)
        └── GenerateAlterEgoFallbackIT.java             # (UNCHANGED — `force-stub-failure` profile, both stubs throw, FallbackPosterProvider answers; SC-1404 / SC-004 preserved)
```

**Structure Decision**: Two-project web app (`backend/` + `frontend/`), unchanged from 001/002/003. The new character-side Gemini code lives in the existing `com.aiavatar.alterego.service.gemini` package so the seam between stub and real provider is obvious at the package level — and so future readers see the image side and character side as siblings of one provider, not two unrelated integrations. Frontend is untouched.

## Complexity Tracking

*(Intentionally empty — Constitution Check passes without violations.)*

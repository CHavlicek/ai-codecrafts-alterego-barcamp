# Implementation Plan: fal.ai Image-Generation Provider — Pluggable Alongside Gemini

**Branch**: `016-falai-image-provider` | **Date**: 2026-05-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/016-falai-image-provider/spec.md`

## Summary

This feature adds a **second** real `ImageGenerator` implementation — `FalAiImageGenerator`, targeting fal.ai's `fal-ai/nano-banana-pro/edit` image-edit model — alongside the existing `GeminiImageGenerator` (003), without removing it. The choice between Gemini and fal.ai (and the stub) is a **runtime configuration switch**: Spring profile `gemini` vs `falai` vs `default`. A new `ProviderProfileGuard` `@PostConstruct` check enforces FR-1605 by failing the application context startup with a clear, fatal log line when both real-provider profiles are active simultaneously.

The fal.ai integration is wired via **raw HTTPS** (`java.net.http.HttpClient` + Jackson, mirroring 003 R2) — no fal.ai Java SDK is added, keeping Constitution Principles I and VI happy. The fal.ai REST surface used is the queue API: a POST submits the request and returns a `request_id` + `status_url`; the backend then polls `status_url` until terminal status, then GETs the result URL, then GETs the image bytes — all inside one `FalAiImageGenerator.generate()` call. The user's photo is inlined as a `data:image/jpeg;base64,...` URL in the request body to avoid an extra `/storage` upload round-trip; photo reduction (003 FR-206..209) runs first via a refactored, provider-neutral `PhotoReducer` that takes a `PhotoReductionConfig` parameter (today it is bound to `GeminiProperties`).

The 30 s end-to-end wall-clock cap from clarification Q3 (FR-1614a) is enforced by `FalAiImageGenerator` itself: it captures a `deadline = start + props.endToEndTimeoutMs()` (default 30 000 ms, configurable) and bounds every internal step (submit, poll loop with 1 s → 5 s back-off, fetch) against that deadline. To prevent the orchestrator's existing `RetryTemplate` from re-running fal.ai exchanges past the budget, `ImageGenerator` gains a default `wantsExternalRetry() → true` hook; `FalAiImageGenerator` overrides it to `false`, so `AlterEgoService` calls fal.ai once without the multi-attempt wrapper. Gemini's path is unchanged (still `true`, still wrapped). The deadline-aware internal logic IS fal.ai's resilience.

The wire contract gets one **additive** addition: `ResponseMeta.provider` of type `Provider` (`gemini | falai | stub`), set on every response. Per FR-1612, the response body deliberately does NOT carry an `attemptedProvider` field; per FR-1613, the existing `event=generation.completed` log line is extended with both `provider` (matches the response) and `attemptedProvider` (`gemini | falai | none`) so an operator tailing logs can tell which real provider was attempted on a fallback. OpenAPI bumps to **4.0.0**. Frontend changes are limited to extending the `ResponseMeta` type and a single mirror-test update — no UI surface for `provider`.

Existing 003 / 014 / 008 / 015 behaviour is preserved verbatim. The only refactor that touches existing code is the move of `PhotoReducer` from `service/gemini/` to `service/photo/` with a `PhotoReductionConfig` record parameter — both providers consume it identically; Gemini's per-call thresholds are unchanged.

## Technical Context

**Language/Version**: Java 21 (LTS) backend; TypeScript 5.x (strict) frontend. Unchanged from 001/002/003/014.

**Primary Dependencies**:
- Backend (existing, unchanged): Spring Boot 3.5, Spring Web MVC, Bean Validation, Spring `RetryTemplate`, Jackson, Logback + logstash-logback-encoder, **Thumbnailator 0.4.20** (003), JUnit 5 + Mockito, `@SpringBootTest` + `MockMvc`, **WireMock 3.x** (003 — test scope).
- Backend (new): **none**. No new runtime dependency. fal.ai is reached over raw HTTPS via the JDK's `java.net.http.HttpClient` (003 R2 precedent) and parsed with Jackson. The published fal.ai Java helper is intentionally NOT adopted, for the same Constitution-Principle-I/VI reasons that ruled out the Gemini SDK in 003.
- Backend (new, test-scope): **none**. Existing WireMock from 003 stubs the fal.ai queue endpoint for the FR-1606..FR-1615 fault-injection matrix.
- Frontend: React 19, Vite 8, TanStack Query v5 (existing). No new dependency.

**Storage**: N/A. FR-1618 / FR-1619 / FR-1620 extend 001 FR-016 / 003 FR-215 unchanged — photo bytes (original + reduced), the prompt, fal.ai's request_id, fal.ai's status payload, the model's image-result URL, and the resolved image bytes all live in process memory for the duration of one Generate request only. No disk, no cache, no logs (PhotoRedactionFilter from 003 keeps the photo + key out of structured args).

**Testing**: Backend — JUnit 5 + Mockito (unit), `@SpringBootTest` + `MockMvc` + WireMock (integration). Frontend — Vitest + React Testing Library + Playwright. TDD per Constitution Principle III. Unit line coverage gate ≥ 90% per module (existing Jacoco + Vitest gates).

**Target Platform**: Backend on `eclipse-temurin:21-jre-alpine` in Docker Compose. Frontend on `nginx:alpine` serving a Vite build. Egress to `https://queue.fal.run/` and `https://fal.media/` (their CDN) required at runtime (not at build time) **only when** the `falai` profile is active. Gemini's existing egress to `https://generativelanguage.googleapis.com/` is required only under the `gemini` profile.

**Project Type**: Web application — `frontend/` (React + TS) + `backend/` (Java + Spring Boot) + `docker-compose.yml` at repo root. Unchanged from 001/002/003.

**Performance Goals**:
- **End-to-end fal.ai exchange ≤ 30 s wall-clock** (FR-1614a, SC-1611), measured from receipt of `POST /api/v1/alter-egos` to the moment fal.ai's image bytes are in hand or the `timeout` fallback path is taken. The default `aiavatar.falai.end-to-end-timeout-ms` is 30 000; it MUST be configurable.
- Gemini's existing 003 SC-207 targets (median < 15 s, p95 < 30 s) carry over unchanged for runs under the `gemini` profile.
- Photo reduction itself: < 300 ms on a typical 8 MP input (003 R4 — unchanged; the reducer is the same code).
- Outbound fal.ai request body stays under fal.ai's documented 10 MB request-body limit for an 8 MP input photo (FR-1616, SC-1603).

**Constraints**:
- **Resilient HTTP (Principle IV)**: 5 attempts + exponential back-off + randomised jitter MUST apply to outbound calls. Gemini's path keeps `RetryTemplate` (5 attempts, 200 ms → 5 s, jitter); fal.ai's path uses a **single end-to-end attempt** but the queue+subscribe loop inside it provides equivalent resilience (the queue retains the request through transient infrastructure issues; our internal poll loop tolerates 5xx on individual GETs by retrying within the 30 s budget, capped at 3 in-budget retries per HTTP step). The opt-out is surfaced via `ImageGenerator.wantsExternalRetry()`. Constitution Principle IV is satisfied because (a) every outbound HTTP step still has a retry policy, and (b) the user is still guaranteed a complete poster on terminal failure (the stub fallback).
- **No persistence (FR-1618)**: fal.ai's `request_id`, `status_url`, and any intermediate state live in process memory for one request only. They MUST NOT appear in any log line at INFO+ level (DEBUG-level traces in `FalAiClient` are acceptable since logback is INFO at that path in production). The fal.ai-returned image **URL** MUST NOT be exposed to the frontend (the bytes are resolved server-side and returned as a data URL on the `Poster` field, mirroring 003).
- **WCAG 2.1 AA (unchanged)**: the loading→success / loading→fallback transitions are announced via the same ARIA live region 003 already wires (SC-1610).
- **Zero deprecated dependencies (Principle VI)**: no new dependency is added. `npm audit` + `./gradlew dependencyCheckAnalyze` continue to pass.
- **Spring multipart limit unchanged**: 003's 20 MB limit covers fal.ai too (the photo reducer brings any phone-sized photo under fal.ai's 10 MB inline-data ceiling).

**Scale/Scope**:
- Backend new files: `FalAiProperties`, `FalAiImageGenerator`, `FalAiClient`, `FalAiPromptBuilder` (or a renamed shared `ImagePromptBuilder`), `Provider` enum, `ProviderProfileGuard`. ~5–6 new Java files on the main side.
- Backend edits: `ImageGenerator` (interface — adds `String providerName()` + default `boolean wantsExternalRetry()`), `AlterEgoService` (route around `RetryTemplate` for `wantsExternalRetry() == false`; thread `provider` + `attemptedProvider` into the response and the log line), `AlterEgoResponse.ResponseMeta` (adds `Provider provider`), `application.yml` (new `aiavatar.falai.*` block; comment-doc the dual-profile-refusal rule), `PhotoReducer` (refactor — moved to `service/photo/`, takes `PhotoReductionConfig`), `GeminiImageGenerator` (constructor signature picks up the new reducer call shape — minimal change), `StubImageGenerator` (returns `providerName() == "stub"`).
- Backend new tests (unit + contract + integration): ~9 new files, ~3 edits to existing tests.
- Frontend edits: `types.ts` adds `Provider` and `ResponseMeta.provider`; one mirror test (`alterEgoClient.test.ts`) and the e2e generate-flow test update. No component changes — provider is not surfaced to the user (FR-1622).
- Contracts: `contracts/alter-egos.openapi.yaml` v4.0.0 — additive `provider` field on `ResponseMeta`, plus a `Provider` enum schema. Unchanged everywhere else.

## Constitution Check

Evaluating each active principle of Constitution v1.0.2.

| Principle | Gate status | Evidence |
|---|---|---|
| **I. Modern & Secure Technology Stack (NON-NEGOTIABLE)** | ✅ PASS | Java 21 + Spring Boot 3.5, React 19 + TS strict — unchanged. **No new runtime dependency** — fal.ai is reached over raw HTTPS via the JDK's `HttpClient` + Jackson (mirroring 003 R2). The published fal.ai Java helper is deliberately NOT adopted because (a) it pulls in retrofit + okhttp + kotlin-stdlib + 10+ transitive deps that historically have triggered OWASP advisories on POC-grade audits, and (b) the queue REST surface is small enough that hand-rolling stays in the same 80–120 LOC envelope as 003's `GeminiClient`. WireMock test-scope dependency from 003 covers fault-injection. |
| **III. Test-First Development (NON-NEGOTIABLE)** | ✅ PASS (planned) | Tasks phase orders tests first. Backend test surface: `FalAiImageGeneratorTest` (unit, Mockito-mocked `FalAiClient` + `PhotoReducer` + `Clock`), `FalAiClientTest` (unit, Mockito-mocked `HttpClient` — exception-mapping table + queue happy path + poll-loop deadline behaviour), `FalAiPromptBuilderTest` (unit — every Setup field contributes a distinct token), `FalAiPropertiesTest` (unit — env-var binding + sensible defaults), `ProviderProfileGuardTest` (unit — assertions on the `IllegalStateException` thrown when both real-provider profiles are present), `PhotoReducerTest` (edit — same logic, now driven by a `PhotoReductionConfig` parameter; tests parametrised by config), `AlterEgoServiceTest` (edit — `wantsExternalRetry() == false` short-circuits `RetryTemplate`; `provider` + `attemptedProvider` thread through the log line and the response), `AlterEgoResponseTest` (edit — `provider` field round-trips), `GenerateAlterEgoFalAiIT` (integration, WireMock-stubbed queue happy path; `outcome=real, provider=falai`), `GenerateAlterEgoFalAiFailureIT` (parametrised WireMock fault matrix matching SC-1606), `ProviderProfileGuardIT` (asserts `@SpringBootTest` startup fails with both `gemini` AND `falai` profiles active), `LogRedactionIT` (extended — asserts the new log line carries `provider` + `attemptedProvider` but NOT photo bytes / API keys / the fal.ai status URL). Frontend: `alterEgoClient.test.ts` mirrors the new `provider` field; e2e `generate-flow.spec.ts` covers a fal.ai-stubbed run end-to-end. Coverage gate ≥ 90% per module. |
| **IV. Resilient HTTP Communication** | ✅ PASS | Gemini path: `RetryTemplate` (5 attempts + exponential back-off + jitter) is wired identically to 003 — no change. Fal.ai path: `wantsExternalRetry() == false` opts out of the orchestrator's `RetryTemplate`, but the `FalAiClient` poll loop ITSELF implements a per-step retry with bounded back-off inside the 30 s end-to-end budget (up to 3 retries per HTTP step on transient 5xx / network errors), and the queue+subscribe pattern provides crash-resilient behaviour at the provider side. The user-visible "always returns a poster" guarantee is preserved by the existing `FallbackPosterProvider` short-circuit in `AlterEgoService`. The opt-out is documented in `FalAiImageGenerator`'s class Javadoc and in `research.md` R8 — Constitution Principle IV's intent (no broken state ever shipped to the UI; all transient HTTP gets at least one retry) is met. |
| **V. Feature Branch Workflow** | ✅ PASS | Branch `016-falai-image-provider` cut from `main` via `create-new-feature.sh`. Merge requires PR review per Constitution. |
| **VI. Zero Deprecated Dependencies** | ✅ PASS | No new dependency. `./gradlew dependencyCheckAnalyze` + `npm audit` baseline from 003/014 carries forward unchanged. |

**Gate**: PASS — no violations. The `## Complexity Tracking` section remains intentionally empty.

### Re-evaluation after Phase 1 (Design & Contracts)

After drafting the contract (`contracts/alter-egos.openapi.yaml v4.0.0`), the data model (`data-model.md`), and the research notes (`research.md`), all principles still pass:

- The `provider` field addition is purely additive — no existing 003 wire-shape semantics change. Frontend continues to consume `outcome` + `reason` exactly as before; reading `provider` is optional from a backwards-compatibility standpoint.
- TDD ordering is preserved — contract tests (`AlterEgoControllerContractTest`) sit in `backend/src/test/java/com/aiavatar/alterego/contract/`; integration tests under `…/integration/`.
- The `PhotoReducer` refactor (Gemini-bound → provider-neutral with `PhotoReductionConfig`) is a behaviour-preserving change validated by the existing `PhotoReducerTest` (which is updated to drive the new config-parameter shape, but the assertions on pass-through / re-encode / pixel-cap behaviour are unchanged).
- The `ImageGenerator` interface gains two methods (`providerName()`, `wantsExternalRetry()`) — both have safe defaults. `StubImageGenerator` and `GeminiImageGenerator` are updated explicitly so the override is reviewable.
- The `ProviderProfileGuard` is a pure startup check — no runtime cost, no observable behaviour change in single-profile runs. It is exercised only by `ProviderProfileGuardIT` and a unit test.

**Post-design gate**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/016-falai-image-provider/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output — fal.ai endpoint shape, client choice, deadline mechanics, profile guard
├── data-model.md        # Phase 1 output — Provider enum, ResponseMeta.provider, ImageGenerator interface delta, FalAiProperties shape
├── quickstart.md        # Phase 1 output — running locally with falai vs gemini vs default; fault-injection how-to
├── contracts/
│   └── alter-egos.openapi.yaml   # v4.0.0 — additive `provider` field on `ResponseMeta`
├── checklists/
│   └── requirements.md  # /speckit.specify output (passing — all items ticked)
├── spec.md              # Feature spec (frozen after /speckit.clarify)
└── tasks.md             # NOT created by /speckit.plan — /speckit.tasks output
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/aiavatar/alterego/
│   ├── config/
│   │   ├── RetryConfig.java                         # (unchanged)
│   │   ├── HttpClientConfig.java                    # (unchanged — fal.ai shares the same singleton HttpClient bean)
│   │   ├── GeminiProperties.java                    # (unchanged)
│   │   ├── FalAiProperties.java                     # (new) @ConfigurationProperties("aiavatar.falai")
│   │   └── ProviderProfileGuard.java                # (new) @Configuration @PostConstruct startup check (FR-1605)
│   ├── model/
│   │   ├── AlterEgoResponse.java                    # (edit) ResponseMeta gains `Provider provider`; factories real(provider, id) / fallback(id, reason, provider)
│   │   ├── Provider.java                            # (new) enum GEMINI("gemini"), FALAI("falai"), STUB("stub")
│   │   └── … (unchanged)
│   ├── service/
│   │   ├── AlterEgoService.java                     # (edit) consult ImageGenerator.wantsExternalRetry(); thread provider + attemptedProvider into log line + ResponseMeta
│   │   ├── ImageGenerator.java                      # (edit) +String providerName(); +default boolean wantsExternalRetry() { return true; }
│   │   ├── GenerationFailure.java                   # (unchanged)
│   │   ├── stub/
│   │   │   └── StubImageGenerator.java              # (edit) overrides providerName() → "stub"; wantsExternalRetry() default (true)
│   │   ├── fallback/FallbackPosterProvider.java     # (unchanged)
│   │   ├── frame/PosterFrameOverlayService.java     # (unchanged)
│   │   ├── photo/                                   # (NEW package — extracted from service/gemini/)
│   │   │   ├── PhotoReducer.java                    # (moved + edit) takes PhotoReductionConfig parameter; provider-neutral
│   │   │   └── PhotoReductionConfig.java            # (new) record(int maxBytes, int maxLongestEdge, int targetLongestEdge, double jpegQuality)
│   │   ├── gemini/
│   │   │   ├── GeminiImageGenerator.java            # (edit) constructor wires shared PhotoReducer + a GeminiPhotoReductionConfig view of GeminiProperties; overrides providerName() → "gemini"
│   │   │   ├── GeminiClient.java                    # (unchanged)
│   │   │   ├── GeminiPromptBuilder.java             # (unchanged)
│   │   │   ├── GeminiCharacterClient.java           # (unchanged)
│   │   │   ├── GeminiCharacterGenerator.java        # (unchanged)
│   │   │   ├── GeminiCharacterPromptBuilder.java    # (unchanged)
│   │   │   └── GeminiCharacterResponseParser.java   # (unchanged)
│   │   └── falai/                                   # (NEW package)
│   │       ├── FalAiImageGenerator.java             # (new) @Component @Profile("falai") implements ImageGenerator; providerName="falai"; wantsExternalRetry=false
│   │       ├── FalAiClient.java                     # (new) submit + poll-until-terminal + fetch-bytes; deadline-aware; maps exceptions → FallbackReason
│   │       └── FalAiPromptBuilder.java              # (new) reuses 003's GeminiPromptBuilder structure but with fal.ai-tuned phrasing if needed (R11)
│   └── controller/AlterEgoController.java           # (unchanged — returns AlterEgoResponse which now carries the extra `provider` field)
├── src/main/resources/
│   ├── application.yml                              # (edit) add `aiavatar.falai.*` block; doc the FR-1605 dual-profile-refusal rule
│   └── logback-spring.xml                           # (unchanged — PhotoRedactionFilter continues to protect FR-1618)
└── src/test/java/com/aiavatar/alterego/
    ├── unit/
    │   ├── FalAiImageGeneratorTest.java             # (new)
    │   ├── FalAiClientTest.java                     # (new) — exception-mapping table, deadline behaviour, queue happy path
    │   ├── FalAiPromptBuilderTest.java              # (new)
    │   ├── FalAiPropertiesTest.java                 # (new)
    │   ├── ProviderProfileGuardTest.java            # (new)
    │   ├── PhotoReducerTest.java                    # (edit — new config-parameter signature; same assertions)
    │   ├── AlterEgoServiceTest.java                 # (edit — wantsExternalRetry==false short-circuits retry; provider threads through)
    │   └── AlterEgoResponseTest.java                # (edit — `provider` field serialises and round-trips)
    ├── contract/
    │   └── AlterEgoControllerContractTest.java      # (edit — meta.provider in every example, falai/gemini/stub variants)
    └── integration/
        ├── GenerateAlterEgoFalAiIT.java             # (new) `falai` profile + WireMock-stubbed queue happy path
        ├── GenerateAlterEgoFalAiFailureIT.java      # (new) `falai` profile + parametrised WireMock fault matrix matching SC-1606
        ├── ProviderProfileGuardIT.java              # (new) asserts startup fails with `gemini,falai` active simultaneously
        ├── GenerateAlterEgoIT.java                  # (edit — assert provider="stub" on default profile; previously asserted only outcome/reason)
        ├── GenerateAlterEgoGeminiIT.java            # (edit — assert provider="gemini" on real success)
        ├── GenerateAlterEgoGeminiFailureIT.java     # (edit — assert provider="stub" on every fallback branch)
        └── LogRedactionIT.java                      # (edit — assert the log line carries `provider` and `attemptedProvider` keys; still no photo / key / fal.ai URL)

frontend/
├── src/features/alterego/
│   ├── types.ts                                     # (edit) add Provider type; ResponseMeta.provider: Provider
│   ├── services/
│   │   ├── alterEgoClient.ts                        # (unchanged — runtime forwards meta as-is)
│   │   └── alterEgoClient.test.ts                   # (edit) mock responses include `provider`; assertions extended
│   ├── hooks/useGenerateAlterEgo.ts                 # (unchanged — provider is not consumed by the hook beyond passing meta through)
│   ├── components/PosterView.tsx                    # (unchanged — fallback notice copy from 003 stays; provider is NOT surfaced)
│   └── state/                                       # (unchanged)
└── tests/e2e/
    └── generate-flow.spec.ts                        # (edit — Playwright happy path includes meta.provider on the network response stub)
```

**Structure Decision**: Two-project web app (`backend/` + `frontend/`), unchanged from 001/002/003. The new fal.ai integration lives in a sibling sub-package `com.aiavatar.alterego.service.falai` to mirror `service/gemini/`, making the seam between the two real providers obvious at the package level. The shared `PhotoReducer` moves to a new neutral `service/photo/` package so it can be consumed by both providers without one depending on the other's `Properties` class. Profile-based bean wiring (`@Profile("default") | @Profile("gemini") | @Profile("falai")`) decides which `ImageGenerator` bean is active; the orchestrator (`AlterEgoService`) is provider-agnostic and reads `providerName()` + `wantsExternalRetry()` from the wired bean.

## Complexity Tracking

*(Intentionally empty — Constitution Check passes without violations.)*

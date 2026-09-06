# Implementation Plan: Gemini Image Generator — Real AI-Generated Alter-Ego Poster

**Branch**: `003-gemini-image-generator` | **Date**: 2026-04-22 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/003-gemini-image-generator/spec.md`

## Summary

This feature swaps the backend's stub `ImageGenerator` for a real Google Gemini image-generation call, leaving every other behaviour established by 001 and 002 intact. The backend gains a `GeminiImageGenerator` that composes a text prompt from the user's Setup inputs (pose, role/archetype, universe, vibe, first name), attaches the user's photo as a reference image, calls the Gemini image-generation REST endpoint, and returns the generated PNG in the same `PosterImage` shape today's stub produces. A new `PhotoReducer` sits in front of the Gemini call to down-sample overlarge photos to fit the provider's per-request limits. Configuration (API key, model id, endpoint, timeouts, resize thresholds) lives in a new `GeminiProperties` class bound from `application.yml` + environment variables; when no key is configured the backend stays up and every request falls back to the existing `StubImageGenerator` (the constitutional Principle IV resilient-HTTP retry policy already bridges the Gemini call transparently). The wire contract adds a structured `meta.reason` field to `AlterEgoResponse.ResponseMeta` carrying one of six fixed fallback codes (per FR-218), and renames the success outcome wire value from `success` → `real` so a successful "real provider was used" run is distinguishable from a stub-era response. The frontend's single touchpoint is updating `useGenerateAlterEgo` to read the new `outcome: "real" | "fallback"` discriminator; `PosterView`'s existing non-blocking banner already renders a fallback notice (FR-214's generic single-variant message replaces the 002-era copy). No user-visible chrome is added beyond the (existing) banner.

## Technical Context

**Language/Version**: Java 21 (LTS) backend; TypeScript 5.x (strict) frontend. Unchanged from 001/002.
**Primary Dependencies**:
  - Backend (existing): Spring Boot 3.5, Spring Web MVC, Bean Validation, Spring `RetryTemplate`, Jackson, Logback + logstash-logback-encoder, JUnit 5 + Mockito, `@SpringBootTest` + `MockMvc`, swagger-request-validator-mockmvc.
  - Backend (new): **Thumbnailator 0.4.20** (`net.coobird:thumbnailator`) for photo reduction (single-jar, no transitive deps, no known CVEs); **no Gemini SDK** — the Gemini REST API is called via the existing Spring-provided `java.net.http.HttpClient` + Jackson wrapped in `RetryTemplate`, for the reasons captured in `research.md`.
  - Backend (new, test-only): **WireMock** (`org.wiremock:wiremock-standalone:3.x` or `com.github.tomakehurst:wiremock-jre8-standalone`) for fault-injection integration tests against the Gemini endpoint.
  - Frontend: React 18, Vite, TanStack Query v5, React Context + `useReducer`, Vitest + React Testing Library, Playwright. No new dependencies.
**Storage**: N/A. No persistence — FR-215 extends 001 FR-016 unchanged: the photo bytes (original + reduced variant) live only in-process for the duration of one Generate request.
**Testing**: Backend — JUnit 5 + Mockito + `@SpringBootTest` + `MockMvc` + WireMock (new). Frontend — Vitest + RTL + Playwright. TDD per Principle III. Unit line coverage ≥ 90% per module (existing Jacoco + Vitest `coverage` gates).
**Target Platform**: Backend runs on `eclipse-temurin:21-jre-alpine` in Docker Compose. Frontend on `nginx:alpine` serving a Vite build. Egress to `https://generativelanguage.googleapis.com/` required at runtime (not at build time).
**Project Type**: Web application — `frontend/` (React + TS) + `backend/` (Java + Spring Boot) + `docker-compose.yml` at repo root. Unchanged from 001/002.
**Performance Goals**:
  - Median Generate-press → poster-rendered **< 15 s** successful case, p95 **< 30 s** (SC-207). Dominant component is the Gemini round-trip; the retry/back-off policy (5 attempts, 200ms → 5s) bounds the worst case.
  - Photo reduction step itself **< 300 ms** on a typical backend image (8 MP input) — a soft target, not a gate.
  - Outbound request payload **under Gemini's per-request limit** with input photos up to 8 MP (SC-203).
**Constraints**:
  - Resilient HTTP (Principle IV): 5 attempts + exponential back-off + randomised jitter + fallback. Re-uses the existing `RetryConfig`'s `RetryTemplate` bean; the Gemini call is executed inside `imageGenerator.generate()` which already sits inside `retryTemplate.execute(...)` in `AlterEgoService`. No new retry scaffolding is introduced; the only addition is typed exceptions so the orchestrator can record a `reason` code on final fallback.
  - No persistence (FR-215). `PhotoRedactionFilter` in `logback-spring.xml` already drops log events carrying photo fields; the new code MUST NOT introduce log fields named `photo`, `photoBytes`, or `imageData` that would bypass the filter (they wouldn't — the filter is keyed on field name, not object identity).
  - WCAG 2.1 AA (unchanged).
  - Zero deprecated dependencies (Principle VI). Thumbnailator is current, no CVEs. WireMock test-scope only.
  - Spring multipart max-file-size bumped from **5 MB → 20 MB** so 8-MP phone photos reach the backend (the resize then brings them under Gemini's limit). This is a minor `application.yml` change; no API shape change.
**Scale/Scope**:
  - Backend: 1 new adapter (`GeminiImageGenerator`), 1 new reducer (`PhotoReducer`), 1 new config class (`GeminiProperties`), 1 new typed exception (`GenerationFailure` + `FallbackReason` enum), 1 enum rename (`Outcome.SUCCESS → REAL`), 1 DTO extension (`ResponseMeta.reason`), 1 orchestrator edit (`AlterEgoService`). ~10 modified or new Java files on the main side, ~6 new/modified test files.
  - Frontend: 1 reducer/action edit (rename `'success' → 'real'` wire value mapping), 1 client test update, copy tweak in the fallback banner. ~4 TS files touched.
  - Contracts: one OpenAPI file (`alter-egos.openapi.yaml`) re-versioned to 3.0.0 with the `reason` field added and the outcome enum renamed.

## Constitution Check

Evaluating each active principle of Constitution v1.0.2.

| Principle | Gate status | Evidence |
|---|---|---|
| **I. Modern & Secure Technology Stack (NON-NEGOTIABLE)** | ✅ PASS | Java 21 + Spring Boot 3.5, React 18 + TS strict — unchanged. Thumbnailator 0.4.20 is actively maintained, zero known CVEs, MIT-licensed, single-jar with no transitive deps. WireMock 3.x is a current test-only dependency. No Google SDK adoption that would drag in an old transitive dep surface. |
| **III. Test-First Development (NON-NEGOTIABLE)** | ✅ PASS (planned) | Tasks phase orders tests first: `GeminiImageGeneratorTest` (unit, Mockito-mocked `HttpClient`), `PhotoReducerTest` (unit), `GeminiPropertiesTest` (unit), `AlterEgoServiceTest` (updated — exception mapping), `FallbackReasonTest` (unit), `GenerateAlterEgoGeminiIT` (integration, WireMock-stubbed Gemini endpoint, success path), `GenerateAlterEgoGeminiFailureIT` (integration, WireMock-injected faults matching the SC-205 matrix), `AlterEgoControllerContractTest` (updated — new `reason` field + renamed enum), `LogRedactionIT` (updated — asserts `outcome` + `reason` log line AND absence of photo/key). Frontend: `useGenerateAlterEgo.test.ts` updated for the `'real'` wire value; `PosterView.test.tsx` verifying the new generic fallback copy. Coverage gate ≥ 90% per module. |
| **IV. Resilient HTTP Communication** | ✅ PASS | Gemini call is executed inside `imageGenerator.generate()` which is already wrapped by `retryTemplate.execute(...)` in `AlterEgoService`. The 5-attempt + exponential back-off + jitter policy (`RetryConfig.java`) applies unchanged. Final-failure fallback to the stub is retained via the existing `catch (RuntimeException)` branch in `AlterEgoService`. New typed `GenerationFailure extends RuntimeException` is caught by the same branch (it's a RuntimeException), preserving the existing control flow while carrying a `FallbackReason` code. |
| **V. Feature Branch Workflow** | ✅ PASS | Branch `003-gemini-image-generator` cut from `main` via `create-new-feature.sh`. Merge requires PR review per the constitution. |
| **VI. Zero Deprecated Dependencies** | ✅ PASS | Thumbnailator: current, no CVEs, MIT. WireMock: current, Apache-2.0, test-scope only (no runtime weight). `./gradlew dependencyCheckAnalyze` + `npm audit` run at PR time as a Phase-2 task. |

**Gate**: PASS — no violations. The `## Complexity Tracking` section remains intentionally empty.

### Re-evaluation after Phase 1 (Design & Contracts)

After drafting the contract (`contracts/alter-egos.openapi.yaml`) and the data model (`data-model.md`), all principles still pass:

- No unexpected dependencies surface during design. The Gemini REST shape is straightforward (POST JSON, receive JSON with inline base64 image parts) and fits the existing `HttpClient` + Jackson stack.
- TDD ordering is preserved — contract-level tests sit in `backend/src/test/java/com/aiavatar/alterego/contract/` (existing convention) and integration tests under `…/integration/`.
- The wire-rename from `success` → `real` is the only contract-breaking change, and it is atomic (backend + frontend + contract + examples updated in the same PR). Constitutionally there's no external-consumer gate — this contract is internal to the repo.

**Post-design gate**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/003-gemini-image-generator/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output — Gemini API shape, client choice, resize lib, config pattern
├── data-model.md        # Phase 1 output — ResponseMeta delta, FallbackReason enum, GeminiProperties, service seams
├── quickstart.md        # Phase 1 output — running locally with/without GEMINI_API_KEY; fault-injection how-to
├── contracts/
│   └── alter-egos.openapi.yaml   # Updated OpenAPI v3.0.0 — reason field added, outcome renamed
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
│   │   └── GeminiProperties.java                    # (new) @ConfigurationProperties("aiavatar.gemini")
│   ├── model/
│   │   ├── AlterEgoResponse.java                    # (edit) ResponseMeta gains optional `reason`; Outcome.SUCCESS → REAL (wire "success" → "real")
│   │   ├── PhotoPayload.java                        # (unchanged)
│   │   ├── PosterImage.java                         # (unchanged)
│   │   ├── AlterEgoRequest.java                     # (unchanged)
│   │   ├── Pose.java / Archetype.java / Universe.java / Vibe.java  # (unchanged — 002's enums)
│   │   └── FallbackReason.java                      # (new) enum — NOT_CONFIGURED, NETWORK_ERROR, RATE_LIMITED, TIMEOUT, MALFORMED_RESPONSE, SAFETY_REFUSED
│   ├── service/
│   │   ├── AlterEgoService.java                     # (edit) catch GenerationFailure typed exception, thread FallbackReason into ResponseMeta
│   │   ├── CharacterGenerator.java                  # (unchanged)
│   │   ├── ImageGenerator.java                      # (unchanged — interface signature unchanged)
│   │   ├── GenerationFailure.java                   # (new) RuntimeException carrying a FallbackReason
│   │   ├── stub/
│   │   │   ├── StubCharacterGenerator.java          # (unchanged — text stays stubbed)
│   │   │   └── StubImageGenerator.java              # (edit) stay `@Profile("default")`; keep existing behaviour as the fallback
│   │   ├── fallback/FallbackPosterProvider.java     # (unchanged — still provides the final safety net)
│   │   └── gemini/
│   │       ├── GeminiImageGenerator.java            # (new) @Component @Profile("gemini") implements ImageGenerator
│   │       ├── GeminiClient.java                    # (new) thin HTTP wrapper around HttpClient — POST + JSON parse
│   │       ├── GeminiPromptBuilder.java             # (new) builds the text prompt from AlterEgoRequest fields
│   │       └── PhotoReducer.java                    # (new) Thumbnailator-backed resize to fit GeminiProperties.maxInputBytes / maxInputPixels
│   └── controller/AlterEgoController.java           # (unchanged — returns AlterEgoResponse which now carries the extra field)
├── src/main/resources/
│   ├── application.yml                              # (edit) bump multipart.max-file-size 5MB → 20MB; add `aiavatar.gemini.*` block; profile `gemini` active when GEMINI_API_KEY env var set
│   └── logback-spring.xml                           # (unchanged — PhotoRedactionFilter continues to protect FR-215)
└── src/test/java/com/aiavatar/alterego/
    ├── unit/
    │   ├── GeminiImageGeneratorTest.java            # (new) mocks HttpClient; asserts prompt composition, photo passing, exception-mapping to FallbackReason
    │   ├── PhotoReducerTest.java                    # (new) asserts threshold behaviour (under → pass-through, over → resized), mediaType preserved, no re-encode under
    │   ├── GeminiPromptBuilderTest.java             # (new) asserts every Setup field contributes a distinct token to the prompt (FR-203)
    │   ├── GeminiPropertiesTest.java                # (new) asserts env-var binding + sensible defaults
    │   ├── GenerationFailureTest.java               # (new) trivial — enum + exception round-trip
    │   ├── AlterEgoServiceTest.java                 # (edit) verify GenerationFailure → ResponseMeta.reason; verify REAL outcome on happy path
    │   ├── AlterEgoResponseTest.java                # (edit) wire-value tests for REAL + reason serialization
    │   └── FallbackPosterProviderTest.java          # (unchanged)
    ├── contract/
    │   ├── AlterEgoControllerContractTest.java      # (edit) new ResponseMeta shape (reason field), renamed outcome enum
    │   └── AlterEgoControllerErrorContractTest.java # (unchanged — error shapes don't change)
    └── integration/
        ├── GenerateAlterEgoIT.java                  # (edit) under `default` profile (no Gemini): outcome = "fallback" with reason = "not_configured"; character text still rendered
        ├── GenerateAlterEgoGeminiIT.java            # (new) `gemini` profile + WireMock-stubbed happy path: outcome = "real", no reason
        ├── GenerateAlterEgoGeminiFailureIT.java     # (new) `gemini` profile + parametrised WireMock fault matrix (SC-205): network error, 5xx, 429, timeout, malformed JSON, 200 with safety-refusal → each asserts the correct FallbackReason wire value
        ├── GenerateAlterEgoFallbackIT.java          # (edit) default-profile fallback path — same matrix semantics, reason = not_configured
        └── LogRedactionIT.java                      # (edit) extends to assert the new per-run structured log line contains `outcome` + (on fallback) `reason` but NOT `photo`/`apiKey`

frontend/
├── src/features/alterego/
│   ├── hooks/
│   │   └── useGenerateAlterEgo.ts                   # (edit) map wire `outcome: "real"` → phase `'succeeded'`; `outcome: "fallback"` → `'failed_with_fallback'`; no raw `reason` surfaced to the user
│   ├── services/
│   │   ├── alterEgoClient.ts                        # (edit) type update: `ResponseMeta.outcome: 'real' | 'fallback'`; optional `reason: FallbackReason`
│   │   └── alterEgoClient.test.ts                   # (edit) mirror rename
│   ├── components/
│   │   ├── PosterView.tsx                           # (edit) copy change to the FR-214 generic single-variant message; banner render condition unchanged
│   │   └── PosterView.test.tsx                      # (edit) new copy assertion
│   └── state/
│       └── reducer.ts                               # (edit if wire is read here; the reducer today is outcome-agnostic — verify)
└── tests/e2e/
    └── generate-flow.spec.ts                        # (edit if exists) Playwright: happy-path assertion continues to pass after the wire rename
```

**Structure Decision**: Two-project web app (`backend/` + `frontend/`), unchanged from 001/002 and aligned with the constitution's Technology Standards. The Gemini adapter lives in a new sub-package `com.aiavatar.alterego.service.gemini` so the seam between the stub and the real provider is obvious at the package level. Profile-based wiring (`@Profile("default")` vs `@Profile("gemini")`) decides which `ImageGenerator` bean is active — the orchestrator (`AlterEgoService`) is provider-agnostic.

## Complexity Tracking

*(Intentionally empty — Constitution Check passes without violations.)*

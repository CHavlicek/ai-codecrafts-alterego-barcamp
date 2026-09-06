# Phase 1 — Quickstart: Real (LLM-backed) Character Generator

**Feature**: 014-real-character-generator
**Date**: 2026-04-27

This document is the operator runbook for the new character path. It covers: how to run the backend in each of the three modes the feature exposes (stub character, real character, forced-fallback), how the new env vars interact with the existing 003 ones, and how to fault-inject during local development. Frontend is unchanged — there is no frontend quickstart entry.

## TL;DR

| Mode | `SPRING_PROFILES_ACTIVE` | `GEMINI_API_KEY` | Character source | Image source | `meta.outcome` (typical) |
|---|---|---|---|---|---|
| Stub character + stub image (local dev / CI default) | `default` (or unset) | (any value, ignored) | Fixture-driven `StubCharacterGenerator` | Procedural `StubImageGenerator` | `fallback` with `reason=not_configured` |
| Real character + real image (production / live demo) | `gemini` | a valid Gemini API key | LLM-authored `GeminiCharacterGenerator` | Gemini image API via `GeminiImageGenerator` | `real` |
| Real-profile but no key (defensive fail-fast) | `gemini` | unset / blank | Both pre-HTTP short-circuit | Both pre-HTTP short-circuit | `fallback` with `reason=not_configured` |
| Forced fallback (E2E / SC-1404 / SC-004) | `force-stub-failure` | (any value, ignored) | `ForceFailureCharacterGenerator` (throws) | `ForceFailureImageGenerator` (throws) | `fallback` (reason depends on first throw) |

Behaviour change vs. before this feature: under the **`gemini` profile with an API key**, the character text is now LLM-authored. Every other row in the table behaves exactly as before.

## 1. Run locally — stub mode (no Gemini call)

This is the default for local development and the entire test suite. Nothing crosses the network from the backend's outbound surface.

```bash
cd backend
./gradlew bootRun
```

Or equivalently with explicit profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=default ./gradlew bootRun
```

The frontend Vite dev server proxies `/api` to `http://localhost:8080` (existing config), so:

```bash
cd frontend
npm run dev
```

…and any Generate request returns a poster with character text drawn from `backend/src/main/resources/stubs/characters.json` and an image rendered procedurally by `StubImageGenerator`. `meta.outcome=fallback`, `meta.reason=not_configured` (because no Gemini key is set; this is the "default profile, no real provider" baseline that 003 established).

## 2. Run locally — real character + real image

You need a Gemini API key. Generate one from [aistudio.google.com/apikey](https://aistudio.google.com/apikey) — same key that 003's image path needs.

```bash
cd backend
export SPRING_PROFILES_ACTIVE=gemini
export GEMINI_API_KEY="<your-key>"

# Optional overrides (defaults shown)
export GEMINI_TEXT_MODEL_ID=gemini-2.5-flash
export GEMINI_TEXT_REQUEST_TIMEOUT_MS=15000
export GEMINI_MODEL_ID=gemini-3.1-flash-image-preview      # image side, unchanged from 003
export GEMINI_REQUEST_TIMEOUT_MS=25000                     # image side, unchanged from 003

./gradlew bootRun
```

Then run a Generate request through the frontend (`npm run dev`) or via curl against the backend directly. The response will carry `meta.outcome=real`, `character` traits authored by Gemini, and a `poster.dataUrl` produced by Gemini's image model.

### Single key, two paths

`GEMINI_API_KEY` is the only required secret. Both the image and the character paths read it via `GeminiProperties.apiKey()`. Setting two separate keys is **not supported by this feature** (FR-1414 / spec US-5). If you need to limit one path's quota independently, do it in Google AI Studio's project / quota settings, not at this layer.

### Independent model overrides

`GEMINI_MODEL_ID` and `GEMINI_TEXT_MODEL_ID` are independent (FR-1414, spec US-5 acceptance scenario 2). Override one without affecting the other:

```bash
export GEMINI_TEXT_MODEL_ID=gemini-2.5-pro      # try the pro tier for text only
# GEMINI_MODEL_ID continues to default to gemini-3.1-flash-image-preview for image
```

## 3. Run locally — real-profile but no key (defensive fail-fast)

```bash
cd backend
SPRING_PROFILES_ACTIVE=gemini ./gradlew bootRun        # GEMINI_API_KEY left unset
```

Both the character generator and the image generator detect the blank key at construction-time call and throw `GenerationFailure(NOT_CONFIGURED)` pre-HTTP. The orchestrator's `catch (GenerationFailure)` block runs the fallback path, returning `meta.outcome=fallback` with `meta.reason=not_configured`. **Zero outbound network traffic.** Useful for verifying the no-key-no-traffic invariant before deploying.

## 4. Run locally — forced fallback (E2E / SC-1404)

```bash
cd backend
SPRING_PROFILES_ACTIVE=force-stub-failure ./gradlew bootRun
```

Both generators throw on every call. The orchestrator's fallback path serves a `FallbackPosterProvider` poster with the canned "The Resilient" character. SC-004 / SC-1404 budget: the response should arrive in well under 5 seconds median. Useful for Playwright runs that pin the fallback UI.

## 5. Diagnostic logging

The single structured `event=generation.completed` line is unchanged. After this feature, when the character path fails, the line carries `outcome=fallback reason=<wire>` exactly as 003 already produced for image-side failures. There is no second log line per request (FR-1411).

To diagnose a `malformed_response` from the text path, look one level down at the parser's WARN line:

```text
phase=gemini-text-parse reason=malformed_response violation=trait_too_long:tagline correlationId=<uuid>
```

The fixed `violation` enumeration (research §R11) tells you which check failed without leaking the LLM's raw response body. To see the body itself during diagnosis, attach a debugger or transiently raise the logger to TRACE — there is no INFO-level "here's the prompt I built" line, by design (FR-1413).

## 6. Fault-injecting the text endpoint locally — WireMock

`GenerateAlterEgoGeminiCharacterFailureIT` already covers every R5/R9 row in CI. To fault-inject locally without writing a test, point `GEMINI_ENDPOINT_URL` at a WireMock instance:

```bash
# Terminal 1: start WireMock standalone (already on the test classpath; use the standalone JAR or dev npm equivalent)
java -jar wiremock-standalone-3.3.1.jar --port 8089 --root-dir /tmp/wm

# Terminal 1 — register a slow text-endpoint stub (simulating a 25 s response)
curl -X POST http://localhost:8089/__admin/mappings -H 'Content-Type: application/json' -d '{
  "request": {
    "method": "POST",
    "urlPathPattern": "/v1beta/models/.+:generateContent"
  },
  "response": {
    "status": 200,
    "fixedDelayMilliseconds": 25000,
    "headers": { "Content-Type": "application/json" },
    "jsonBody": { "candidates": [{ "content": { "parts": [{ "text": "{}" }] } }] }
  }
}'

# Terminal 2: backend pointed at WireMock instead of real Gemini
cd backend
export SPRING_PROFILES_ACTIVE=gemini
export GEMINI_API_KEY=fake-key-for-wiremock
export GEMINI_ENDPOINT_URL=http://localhost:8089/v1beta
./gradlew bootRun
```

A Generate request now hits WireMock. The 25 s delay exceeds the 15 s `textRequestTimeoutMs`, so each retry throws `HttpTimeoutException` → `GenerationFailure(TIMEOUT)` → fallback. Inspect the response: `meta.outcome=fallback`, `meta.reason=timeout`.

Swap the WireMock body to e.g. `"finishReason": "SAFETY"` to drive `safety_refused`; swap to a non-JSON body to drive `malformed_response`; configure a `Fault.CONNECTION_RESET_BY_PEER` to drive `network_error`. Same matrix as research §R9.

## 7. Pre-merge sanity loop

Before opening the PR (Workflow §6 — Analyse, §8 — PR), run:

```bash
cd backend
./gradlew test                          # full backend suite, including new IT classes
./gradlew jacocoTestCoverageVerification # ≥ 90% per Principle III
./gradlew sonar                          # against localhost:9000 SonarQube; resolve all NEW issues
./gradlew dependencyCheckAnalyze         # zero HIGH/CRITICAL CVEs (Principle VI)
```

Frontend has no new code, but the pipeline still runs:

```bash
cd frontend
npm run lint && npm test && npm run build
```

A green run on all five commands clears the constitutional gates this feature is required to honour.

## 8. Rollback

The behaviour-controlling toggle is `SPRING_PROFILES_ACTIVE`. To roll back at runtime — if the LLM-authored captions ever produce something the operator wants off the air — restart the backend with `SPRING_PROFILES_ACTIVE=default`. The character text snaps back to the deterministic stub; the image snaps back to the procedural stub; `meta.outcome=fallback` resumes for every request. No code change, no rebuild.

The feature was designed with this rollback in mind (SC-1408): every behavioural change this feature ships is gated behind exactly one profile flag and one env var.

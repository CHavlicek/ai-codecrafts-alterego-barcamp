# Phase 1 — Quickstart: fal.ai Image-Generation Provider

**Feature**: 016-falai-image-provider
**Date**: 2026-05-06

A working developer's guide to running, switching, and validating the three image-generation paths (`stub`, `gemini`, `falai`) introduced or extended by this feature. Pairs with [research.md](./research.md) for design rationale and [data-model.md](./data-model.md) for type details.

---

## 1. Prerequisites

- **Java 21** + Gradle wrapper (project root).
- **Node 20+** + npm (for the frontend).
- **Docker Compose** (for the production-like multi-container path).
- An optional **Gemini API key** (`GEMINI_API_KEY`) for the `gemini` profile.
- An optional **fal.ai API key** (`FAL_AI_API_KEY`) for the `falai` profile. Sign up at https://fal.ai and create a key under your account; the key has the form `<id>:<secret>`.

You can run the project end-to-end with no real-provider keys at all — the `default` profile serves the stub poster on every Generate request.

---

## 2. Run locally — pick a profile

### 2a. Default (stub-only) — no real provider

```sh
# Backend
cd backend
./gradlew bootRun

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
```

Open http://localhost:5173, complete the Setup form, press **Generate**.

**Expected**: poster renders from the 001 stub procedural renderer; the response body's `meta.outcome == "fallback"`, `meta.reason == "not_configured"`, `meta.provider == "stub"`. The user-visible UI shows the 003 generic fallback notice ("Showing a preview image — live AI generation isn't available right now.").

### 2b. `gemini` profile — real Google Gemini

```sh
cd backend
SPRING_PROFILES_ACTIVE=gemini \
  GEMINI_API_KEY=<your-google-api-key> \
  ./gradlew bootRun
```

**Expected on success**: `meta.outcome == "real"`, `meta.provider == "gemini"`, no `reason` field; the central poster image was returned by Gemini's `gemini-3.1-flash-image-preview` model.

**Expected when the call fails** (network / 429 / 5xx / timeout / safety refusal): `meta.outcome == "fallback"`, `meta.provider == "stub"`, `meta.reason ∈ {network_error, rate_limited, timeout, malformed_response, safety_refused}`; user sees the same generic notice.

### 2c. `falai` profile — real fal.ai

```sh
cd backend
SPRING_PROFILES_ACTIVE=falai \
  FAL_AI_API_KEY=<your-fal.ai-api-key> \
  ./gradlew bootRun
```

**Expected on success**: `meta.outcome == "real"`, `meta.provider == "falai"`, no `reason` field; the central poster image was produced by fal.ai's `nano-banana-pro/edit` model. The end-to-end run completes within the 30 s wall-clock cap (FR-1614a, SC-1611). The progress indicator's count-up to 99% is unchanged from 013.

**Expected on fallback** (any of: no key, network error, 5xx, per-step timeout, queue stalled past 30 s, malformed response, safety refusal): `meta.outcome == "fallback"`, `meta.provider == "stub"`, `meta.reason ∈ {not_configured, network_error, rate_limited, timeout, malformed_response, safety_refused}`. The user sees the identical generic notice — the UI for a fal.ai fallback is indistinguishable from a Gemini fallback (FR-1615 parity).

### 2d. Verifying the active provider — operator-visible only

The user-visible UI deliberately does NOT show which provider produced the poster (FR-1622). To confirm provider selection:

1. **Browser DevTools → Network tab.** Inspect the `POST /api/v1/alter-egos` response body's `meta.provider` field.
2. **Backend log tail.** Each Generate request emits exactly one structured log line at level `INFO` (real success) or `WARN` (fallback):
   ```
   event=generation.completed outcome=real      provider=falai  attemptedProvider=falai correlationId=…
   event=generation.completed outcome=fallback  provider=stub   attemptedProvider=falai reason=timeout correlationId=…
   event=generation.completed outcome=fallback  provider=stub   attemptedProvider=none  reason=not_configured correlationId=…
   ```
   The `attemptedProvider` key (FR-1613) is the **only** way to tell which real provider was attempted on a fallback — the response body carries `provider == "stub"` regardless (FR-1612 / clarification Q2).

---

## 3. Multi-profile activation refusal (FR-1605)

Activating both `gemini` AND `falai` simultaneously is a misconfiguration:

```sh
# DON'T — this will refuse to start
SPRING_PROFILES_ACTIVE=gemini,falai \
  GEMINI_API_KEY=… FAL_AI_API_KEY=… \
  ./gradlew bootRun
```

**Expected**: backend logs a fatal error and exits with a non-zero status code. No HTTP server starts. The log line is:

```
event=startup.fatal reason=ambiguous_provider profiles=[gemini, falai]
… IllegalStateException: Refusing to start: both 'gemini' and 'falai' profiles are active. Set spring.profiles.active to exactly one of {gemini, falai} (or neither for stub).
```

Remove one of the two profiles and restart.

---

## 4. Validate end-to-end — pick a profile and exercise

### 4a. Happy-path smoke (manual)

```sh
# Profile: falai
SPRING_PROFILES_ACTIVE=falai FAL_AI_API_KEY=<key> ./gradlew bootRun &
cd ../frontend && npm run dev
```

In a browser at http://localhost:5173:

1. Upload a photo (8 MP phone capture is fine — backend reduces it).
2. Pick Pose / Archetype / Universe / (optional) Vibe / Art Style; type a first name.
3. Press **Generate**.
4. Wait for the poster — should appear within ~10–25 s on a healthy fal.ai run.
5. Confirm `meta.provider == "falai"` in the network response.
6. Press **Start over** and run again with a different archetype — confirm a visibly different image (FR-1607).

### 4b. Fault-injection (automated)

Backend integration tests cover the spec's SC-1606 matrix without ever calling the real fal.ai service. Each test stubs WireMock to inject a specific failure mode and asserts the resulting `outcome` / `reason` / `provider` triple:

```sh
cd backend
./gradlew test --tests "*GenerateAlterEgoFalAi*"
```

Tests included:

- `GenerateAlterEgoFalAiIT` — happy path, `outcome=real, provider=falai`.
- `GenerateAlterEgoFalAiFailureIT` — parametrised matrix: no key, network error, HTTP 5xx, per-step timeout, queue stall (>30 s end-to-end), 429, malformed response, safety refusal. Each asserts `outcome=fallback, provider=stub, reason=<expected>`.
- `ProviderProfileGuardIT` — boots `@SpringBootTest` with `spring.profiles.active=gemini,falai` and asserts the context fails to initialise with an `IllegalStateException`.

### 4c. Provider switch round-trip (SC-1604)

The spec mandates 5 round-trip toggles between `gemini` and `falai` produce a renderable poster every time. Manual procedure:

```sh
# Cycle 1: gemini
SPRING_PROFILES_ACTIVE=gemini GEMINI_API_KEY=<g-key> ./gradlew bootRun
# … generate via UI, confirm meta.provider == "gemini"
# Ctrl-C

# Cycle 2: falai
SPRING_PROFILES_ACTIVE=falai FAL_AI_API_KEY=<f-key> ./gradlew bootRun
# … generate, confirm meta.provider == "falai"
# Ctrl-C

# Repeat 3–5 alternating.
```

Each cycle MUST produce a complete poster on the next Generate press, and the `meta.provider` field MUST reflect the current profile (FR-1602).

---

## 5. Frontend behaviour

The frontend gains **no new UI surface** in this feature. Specifically:

- No provider toggle, no model picker, no provider badge.
- The fallback notice copy is unchanged from 003 (single generic single-variant message).
- `frontend/src/features/alterego/types.ts` adds the `Provider` union type and the new `provider: Provider` field on `ResponseMeta`. Component code does not consume `meta.provider` — it is operator metadata.

If you want to surface `provider` for *development* purposes (e.g. a tiny corner badge during a demo), add it as a feature-flagged dev-only overlay; it MUST NOT ship to end users (FR-1622).

---

## 6. Configuration reference

`backend/src/main/resources/application.yml` gains an `aiavatar.falai.*` block parallel to the existing `aiavatar.gemini.*` block:

```yaml
aiavatar:
  falai:
    api-key: ${FAL_AI_API_KEY:}                               # blank → NOT_CONFIGURED at first request
    model-id: ${FAL_AI_MODEL_ID:fal-ai/nano-banana-pro/edit}  # FR-1610
    endpoint-url: ${FAL_AI_ENDPOINT_URL:https://queue.fal.run}
    submit-timeout-ms: ${FAL_AI_SUBMIT_TIMEOUT_MS:8000}
    poll-timeout-ms: ${FAL_AI_POLL_TIMEOUT_MS:5000}
    fetch-timeout-ms: ${FAL_AI_FETCH_TIMEOUT_MS:10000}
    end-to-end-timeout-ms: ${FAL_AI_END_TO_END_TIMEOUT_MS:30000}   # FR-1614a — wall-clock cap
    poll-initial-interval-ms: ${FAL_AI_POLL_INITIAL_INTERVAL_MS:1000}
    poll-max-interval-ms: ${FAL_AI_POLL_MAX_INTERVAL_MS:5000}
    max-input-bytes: ${FAL_AI_MAX_INPUT_BYTES:4194304}
    max-input-longest-edge: ${FAL_AI_MAX_INPUT_LONGEST_EDGE:1536}
    reduced-target-longest-edge: ${FAL_AI_REDUCED_TARGET_EDGE:1024}
    reduced-jpeg-quality: ${FAL_AI_REDUCED_JPEG_QUALITY:0.85}
```

Every key is env-var overridable; defaults work for the developer machine.

---

## 7. Common gotchas

| Symptom | Likely cause | Fix |
|---|---|---|
| App refuses to start with `event=startup.fatal reason=ambiguous_provider` | Both `gemini` and `falai` profiles active | Remove one from `SPRING_PROFILES_ACTIVE` |
| `meta.provider == "stub"`, `reason: "not_configured"` while you set the key | Active profile is wrong (e.g. you set `FAL_AI_API_KEY` but forgot `SPRING_PROFILES_ACTIVE=falai`) | Profile activation is **explicit** — env-var key alone does not flip profiles (FR-1602 / FR-1604) |
| Poster fallback after exactly 30 s every time | fal.ai queue is slow / model warming | Tune `FAL_AI_END_TO_END_TIMEOUT_MS` upward (the spec default is 30 s; per-environment override is allowed, FR-1614a) |
| `meta.provider == "falai"` but Gemini key was also set in the env | Expected — the active profile wins, the inactive provider's key is ignored (clarification Q1) | None |
| 413 Photo too large | Photo > 20 MB (Spring multipart cap from 003) | Reduce client-side or upload a smaller photo; backend reducer handles up to the multipart cap |

---

## 8. What's untouched

This feature does not modify:

- The Gemini text-generation path (014) — character text continues to come from `GeminiCharacterGenerator` when `GEMINI_API_KEY` is set, regardless of which **image** profile is active.
- The poster frame (015), the logo overlay (008), the print artefact (010), or the progress indicator (013).
- The `GenerateRequest` shape (002 FR-130) — Setup form fields are passed identically to whichever image generator is wired.
- The 001 / 003 fallback poster look-and-feel.

If you change any of those during this feature's implementation, you are out of scope.

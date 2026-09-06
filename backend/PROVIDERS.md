# Image-Generation Providers

The backend supports three image-generation paths, selected at runtime via Spring profile. The selection is an operator concern — the user-visible UI is identical across all three (FR-1622 / SC-1610 parity).

## Profile matrix

| Profile | Image source | Required env vars | Behaviour summary |
|---|---|---|---|
| `default` (no real-provider profile) | 001 stub poster | none | `outcome=fallback, reason=not_configured, provider=stub` on every Generate. The user sees a complete poster (frame-composited) plus the generic 003 fallback notice. (016 FR-1603 / FR-1612.) |
| `gemini` | Google Gemini `gemini-3.1-flash-image-preview` (003) | `GEMINI_API_KEY` | Real Gemini run on success → `outcome=real, provider=gemini`. Per-call retry via 003's `RetryTemplate` (5 attempts, exponential back-off + jitter). Fallback on any failure → `outcome=fallback, provider=stub` with the 003 FR-218 reason code. |
| `falai` | fal.ai `nano-banana-pro/edit` (016) | `FAL_AI_API_KEY` | Real fal.ai queue exchange on success → `outcome=real, provider=falai`. Deadline-aware `FalAiClient` enforces a 30-second wall-clock cap (FR-1614a). In-budget retries per HTTP step substitute for the orchestrator's 5-attempt wrapper (`wantsExternalRetry()=false`). Fallback on any failure → `outcome=fallback, provider=stub` with a reason from the 003 FR-218 enumeration. |

Activating both `gemini` AND `falai` simultaneously is a misconfiguration. `ProviderProfileGuard.@PostConstruct` refuses startup with a fatal `event=startup.fatal reason=ambiguous_provider profiles=[…]` log line and the JVM exits non-zero (FR-1605).

## How to activate each profile

```sh
# Default — stub-only (no real API calls; useful for offline development and CI)
./gradlew bootRun

# Gemini real provider
SPRING_PROFILES_ACTIVE=gemini GEMINI_API_KEY=<your-google-key> ./gradlew bootRun

# fal.ai real provider
SPRING_PROFILES_ACTIVE=falai FAL_AI_API_KEY=<your-fal-key> ./gradlew bootRun

# DON'T — refuses to start
SPRING_PROFILES_ACTIVE=gemini,falai ... ./gradlew bootRun
```

Setting an `*_API_KEY` env var alone does **not** activate the corresponding profile — profile activation is an explicit operator choice (clarification Q1).

## Verifying which provider served a request

The user-visible UI deliberately doesn't show the provider. Two operator-facing channels expose it:

1. **Response body** — `meta.provider` carries `"gemini"` / `"falai"` / `"stub"` on every response (FR-1612). Inspect via browser DevTools → Network tab → response body. Note the response body NEVER reveals which real provider was *attempted-and-failed* on a fallback (FR-1612 / clarification Q2).

2. **Backend log line** — every Generate run emits exactly one structured `event=generation.completed` log line carrying both `provider` (matches the response body) and `attemptedProvider` (`gemini` / `falai` / `none`). The `attemptedProvider` field is the only place that records which real provider was attempted on a fallback (FR-1613).

Sample log lines:
```
event=generation.completed outcome=real     provider=falai  attemptedProvider=falai correlationId=…
event=generation.completed outcome=fallback provider=stub   attemptedProvider=falai reason=timeout correlationId=…
event=generation.completed outcome=fallback provider=stub   attemptedProvider=none  reason=not_configured correlationId=…
```

## fal.ai-specific tunables

| Property | Default | Env var | Purpose |
|---|---|---|---|
| `aiavatar.falai.api-key` | (blank) | `FAL_AI_API_KEY` | fal.ai API key. Blank → `NOT_CONFIGURED` fallback per FR-1604. |
| `aiavatar.falai.model-id` | `fal-ai/nano-banana-pro/edit` | `FAL_AI_MODEL_ID` | Model identifier appended to the queue endpoint URL (FR-1610). |
| `aiavatar.falai.endpoint-url` | `https://queue.fal.run` | `FAL_AI_ENDPOINT_URL` | Queue REST base URL. |
| `aiavatar.falai.submit-timeout-ms` | `8000` | `FAL_AI_SUBMIT_TIMEOUT_MS` | Per-attempt timeout for the submit POST. |
| `aiavatar.falai.poll-timeout-ms` | `5000` | `FAL_AI_POLL_TIMEOUT_MS` | Per-attempt timeout for each subscribe / status GET. |
| `aiavatar.falai.fetch-timeout-ms` | `10000` | `FAL_AI_FETCH_TIMEOUT_MS` | Per-attempt timeout for the result GET and the image-bytes CDN GET. |
| `aiavatar.falai.end-to-end-timeout-ms` | `30000` | `FAL_AI_END_TO_END_TIMEOUT_MS` | **Wall-clock cap** across the full exchange (FR-1614a). NOT a per-attempt timeout that resets on retry. |
| `aiavatar.falai.poll-initial-interval-ms` | `1000` | `FAL_AI_POLL_INITIAL_INTERVAL_MS` | Initial poll back-off interval. |
| `aiavatar.falai.poll-max-interval-ms` | `5000` | `FAL_AI_POLL_MAX_INTERVAL_MS` | Poll back-off interval ceiling. |
| `aiavatar.falai.max-input-bytes` | `4194304` (4 MB) | `FAL_AI_MAX_INPUT_BYTES` | Photo-reduction pass-through ceiling — encoded byte size. |
| `aiavatar.falai.max-input-longest-edge` | `1536` | `FAL_AI_MAX_INPUT_LONGEST_EDGE` | Photo-reduction pass-through ceiling — pixel longest edge. |
| `aiavatar.falai.reduced-target-longest-edge` | `1024` | `FAL_AI_REDUCED_TARGET_EDGE` | Resize target on over-threshold input. |
| `aiavatar.falai.reduced-jpeg-quality` | `0.85` | `FAL_AI_REDUCED_JPEG_QUALITY` | JPEG quality factor (0, 1]. |

## Timeout envelope (production)

The fal.ai exchange touches multiple timeouts that have to nest correctly. From outermost to innermost, the round-trip is:

```
browser ─→ CloudFront (origin_read_timeout=60s)
            └─→ EC2 backend (FAL_AI_END_TO_END_TIMEOUT_MS, default 30s)
                 └─→ fal.ai (per-step submit/poll/fetch timeouts, 8/5/10s)
```

`FAL_AI_END_TO_END_TIMEOUT_MS` MUST stay **below** CloudFront's `origin_read_timeout` with margin. Recommended max in production (with CF default of 60 s): **55_000 ms**. Going higher means CloudFront 504s the upstream while the backend is still legitimately waiting on fal.ai — user sees the frontend's `FALLBACK / Even when the robots sleep.` SVG instead of either provider response.

For longer end-to-end budgets (>55 s), open an AWS Support quota-increase ticket for CloudFront's origin-read-timeout (max 180 s). See `deploy/README.md` § "fal.ai timeout envelope" for the full matrix.

## Common gotchas

| Symptom | Likely cause | Fix |
|---|---|---|
| Backend exits with `event=startup.fatal reason=ambiguous_provider` | Both `gemini` and `falai` profiles active | Remove one from `SPRING_PROFILES_ACTIVE`. |
| `meta.provider == "stub"`, `reason: "not_configured"` while you set the key | Active profile is wrong (e.g. `FAL_AI_API_KEY` set but `SPRING_PROFILES_ACTIVE=default`) | Profile activation is **explicit** — env-var key alone does not flip profiles. |
| Poster fallback after exactly 30 s every time on `falai` | fal.ai queue is slow / model warming | Tune `FAL_AI_END_TO_END_TIMEOUT_MS` upward; the spec default is 30 s but the value is configurable per environment (FR-1614a). |
| `meta.provider == "falai"` but Gemini key was also set in the env | Expected — the active profile wins, the inactive provider's key is ignored (clarification Q1). | None. |

## What's untouched

This feature does not modify:
- The Gemini text-generation path (014). Character text continues to come from `GeminiCharacterGenerator` when `GEMINI_API_KEY` is set, regardless of which **image** profile is active. Under `falai` profile alone, character text falls back to the 001 `StubCharacterGenerator` (the stub is now `@Profile({"default", "falai"})`).
- The poster frame (015), the logo overlay (008), the print artefact (010), or the progress indicator (013).
- The `GenerateRequest` shape (002 FR-130) — Setup form fields are passed identically to whichever image generator is wired.
- The 001 / 003 fallback poster look-and-feel.

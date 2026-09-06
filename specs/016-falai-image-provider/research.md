# Phase 0 — Research: fal.ai Image-Generation Provider

**Feature**: 016-falai-image-provider
**Date**: 2026-05-05
**Status**: All items resolved. No `NEEDS CLARIFICATION` remain.

This document resolves the planning-phase open questions that `spec.md` deferred: concrete numbers, library choices, prompt shape, exception mapping, profile-guard mechanics, deadline mechanics, and the contract-bump consequence. Each item follows the **Decision / Rationale / Alternatives** format consistent with 003 / 014.

---

## R1. fal.ai model identifier — default and configurability

**Decision**: Default model id is **`fal-ai/nano-banana-pro/edit`** — the exact identifier named in Issue #40. Configurable via `aiavatar.falai.model-id` (env var `FAL_AI_MODEL_ID`) per FR-1610. The endpoint base URL is `https://queue.fal.run/` (the queue-based REST surface) and is configurable via `aiavatar.falai.endpoint-url` (env var `FAL_AI_ENDPOINT_URL`).

**Rationale**:

- FR-1610 mandates a configurable identifier whose default matches the issue. fal.ai's published model catalogue at https://fal.ai/models shows `fal-ai/nano-banana-pro/edit` as a current image-edit model on the GA queue surface as of 2026-05-05.
- A configuration knob lets us re-point at sibling models (`fal-ai/nano-banana-pro` non-edit variant, `fal-ai/flux/dev`, etc.) without code edits if `nano-banana-pro/edit` is renamed or retired upstream.

**Alternatives considered**:

- Hard-coding the model id: violates FR-1610.
- Defaulting to a different fal.ai model (e.g. `fal-ai/flux-pro/v1.1`): would require justification against the issue's named target. Rejected.

---

## R2. fal.ai client — published SDK vs. hand-rolled HTTP

**Decision**: **Hand-rolled HTTPS** against `https://queue.fal.run/{model-path}` using Java 21's `java.net.http.HttpClient` + Jackson, mirroring 003's `GeminiClient`. **No fal.ai Java SDK is added** to the dependency graph.

**Rationale**:

- fal.ai's published Java helper (`ai.fal.client:fal-client`) pulls in Retrofit2, OkHttp3, kotlin-stdlib (and its transitives), Moshi, and ~10 other libraries. On a POC where the dependency audit must stay clean (Constitution Principle VI; OWASP `dependencyCheckAnalyze` runs at PR time), introducing those for one outbound endpoint is disproportionate.
- The fal.ai queue REST surface we need is **three endpoints**, all JSON: `POST {model}` (submit), `GET {model}/requests/{request_id}/status` (poll), `GET {model}/requests/{request_id}` (fetch result). One additional URL (`images[0].url` from the result body) returns the raw image bytes from fal.ai's CDN. Hand-rolling this is ~150 lines of Jackson mapping — comparable to `GeminiClient`.
- The existing `HttpClient` bean (`HttpClientConfig.java`) is already provider-agnostic and is reused unchanged. Auth is a single HTTP header (`Authorization: Key <FAL_AI_API_KEY>`), no OAuth.
- Hand-rolling keeps the deadline-aware control flow (R8) trivial; injecting deadline logic into a third-party SDK's call chain is more friction.

**Alternatives considered**:

- `ai.fal.client:fal-client`: 10+ transitive deps, higher CVE surface, and the SDK's `subscribe()` method does not expose a deadline parameter — we'd have to wrap it in a `CompletableFuture.orTimeout(...)` and live with the cancellation semantics. Rejected.
- OkHttp directly (no SDK): would still add OkHttp + okio. `HttpClient` is JDK-built-in, zero deps. Rejected.
- Spring `RestClient` / `WebClient`: either would work; `HttpClient` chosen for symmetry with 003 and minimal surface area.

---

## R3. fal.ai request shape — submit, poll, fetch

**Decision**: Three-step queue pattern, all over the same `https://queue.fal.run/` host. The user's photo is inlined as a `data:image/jpeg;base64,...` URL in the request body's `image_urls` array — no separate upload to fal.ai's `/storage` endpoint.

**Step 1 — Submit** (`POST {endpoint}/{model-id}`):

```json
{
  "prompt": "<composed prompt — see below>",
  "image_urls": ["data:image/jpeg;base64,<reduced-photo-base64>"],
  "num_images": 1,
  "aspect_ratio": "3:4",
  "output_format": "jpeg"
}
```

Headers: `Authorization: Key <FAL_AI_API_KEY>`, `Content-Type: application/json`, `Accept: application/json`.

Successful submit returns `200 OK` with a body of shape:

```json
{
  "request_id": "abc123",
  "status_url": "https://queue.fal.run/fal-ai/nano-banana-pro/edit/requests/abc123/status",
  "response_url": "https://queue.fal.run/fal-ai/nano-banana-pro/edit/requests/abc123",
  "status": "IN_QUEUE"
}
```

**Step 2 — Poll** (`GET status_url` returned by submit):

Loop until terminal state (`COMPLETED` or `FAILED`), respecting the 30 s end-to-end deadline (R8). Body:

```json
{
  "status": "IN_QUEUE" | "IN_PROGRESS" | "COMPLETED" | "FAILED",
  "queue_position": 3,
  "logs": [...]
}
```

Initial poll interval **1 s**, doubling per attempt up to a **5 s** cap, with **±20 % random jitter** on each interval (Principle IV "randomised jitter" applied to the polling cadence).

**Step 3 — Fetch result** (`GET response_url` once `status == "COMPLETED"`):

```json
{
  "images": [{ "url": "https://fal.media/files/...", "width": 1024, "height": 1024, "content_type": "image/jpeg" }],
  "seed": 12345,
  "prompt": "..."
}
```

Then `GET images[0].url` over plain HTTPS to fetch the image bytes (no auth header — fal.ai CDN URLs are pre-signed, time-limited).

**Prompt template** (filled by `FalAiPromptBuilder`, identical structural skeleton to 003's `GeminiPromptBuilder`, line breaks preserved):

```
Edit the reference photo to render the person as their alter ego.

The subject's face, hair, skin tone, approximate age, and general build MUST closely match the reference photo. Render the subject as an alter ego with the following attributes:

- Name: <firstName>
- Engineering role: <role-display-label>
- Fictional universe / aesthetic: <universe-display-label>
- Pose / stance: <pose-display-label>
- Vibe / tone: <vibe-display-label-or-omitted>
- Art style: <art-style-display-label>

Composition notes:
- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.
- Clear focus on the subject; the universe aesthetic is the setting, not the subject.
- No overlaid text, logos, or watermarks — text is composited downstream.
```

The opening verb is **"Edit"** (not 003's "Generate") because `nano-banana-pro/edit` is image-edit-conditioned on the input photo. Both providers use the same five Setup-input substitutions (006 introduced the art style line, which is included).

**Rationale**:

- **Inline base64 in `image_urls`** vs the alternative of uploading to `/storage` first: same number of bytes go over the wire either way (the photo has to reach fal.ai), but inlining is one fewer round-trip and zero state on fal.ai's storage side. Confirmed at fal.ai's REST docs that `image_urls` accepts data URLs alongside `https://` and storage URLs. Eliminates a class of failure modes (storage-upload 5xx, signed-URL expiry between upload and submit) without paying anything.
- **Polling vs server-sent-events / WebSocket subscribe**: fal.ai's "subscribe" surface is currently a long-polling SSE stream. Java 21's `HttpClient` does not have ergonomic SSE support without adding a third-party library; the polling pattern with bounded cadence is equivalent in resource cost on a single concurrent request, fits the deadline check trivially (R8), and is provider-portable.
- **`num_images: 1, aspect_ratio: "3:4", output_format: "jpeg"`**: minimum cost (one image, not four), aligned to the existing 900×1200 poster slot, JPEG to keep the data URL compact in the response (the image bytes are downstream of size-sensitive paths — the data URL embedded in `Poster.dataUrl` flows back to the browser).
- **`Authorization: Key <key>` header** (no `Bearer`): per fal.ai's documented auth scheme.
- **Same prompt structure as 003**: minimises cognitive overhead reading the two prompt builders, keeps SC-1602's parity with 003 SC-202 plausible (each Setup field continues to contribute a distinct natural-language token).

**Alternatives considered**:

- Uploading the photo to `/storage` first, then passing the storage URL: extra round-trip, extra failure mode, more code. Rejected.
- Skipping `aspect_ratio` and trusting fal.ai's default: documented default is 1:1, which would not fit the existing poster slot. Rejected.
- Using SSE / streaming subscribe instead of polling: would require adding `okhttp-sse` or hand-rolling the SSE parser. Marginal latency improvement at the cost of code complexity. Rejected.
- Using Gemini's prompt verbatim: works, but "Generate a portrait" doesn't telegraph the input-photo-conditioning to an image-edit model. The subtle shift to "Edit the reference photo" anchors the model on the input.

---

## R4. Photo reduction — reuse, refactor, configure

**Decision**: **Refactor** the existing `PhotoReducer` (today bound to `GeminiProperties`, in `service/gemini/`) into a provider-neutral component. Move it to a new `com.aiavatar.alterego.service.photo` package and change its API to take a `PhotoReductionConfig` parameter on every call:

```java
public PhotoPayload reduce(PhotoPayload photo, PhotoReductionConfig config);
```

with

```java
public record PhotoReductionConfig(
    int maxBytes,                     // pass-through ceiling
    int maxLongestEdge,               // pass-through ceiling (px)
    int reducedTargetLongestEdge,     // resize target (px)
    double reducedJpegQuality          // 0.0–1.0
) {}
```

`GeminiImageGenerator` and `FalAiImageGenerator` each construct (or are injected with) their own `PhotoReductionConfig` derived from their respective `Properties` records. The reducer's internal logic — pass-through vs Thumbnailator-resize, EXIF auto-rotate, never up-scale — is unchanged.

Default fal.ai thresholds:

```yaml
aiavatar:
  falai:
    max-input-bytes: ${FAL_AI_MAX_INPUT_BYTES:4194304}        # 4 MB — same as Gemini default; well within fal.ai's 10 MB body ceiling
    max-input-longest-edge: ${FAL_AI_MAX_INPUT_LONGEST_EDGE:1536}
    reduced-target-longest-edge: ${FAL_AI_REDUCED_TARGET_EDGE:1024}
    reduced-jpeg-quality: ${FAL_AI_REDUCED_JPEG_QUALITY:0.85}
```

These are independently overridable from Gemini's thresholds (FR-1616 — separate config keys).

**Rationale**:

- Two image-generation providers want photo reduction with the same shape but possibly different numbers — duplicating Thumbnailator-driven resize logic in two `*PhotoReducer` classes is boilerplate. A config-parameter signature is a 30-line refactor and the existing `PhotoReducerTest` updates trivially.
- Defaults match Gemini's because fal.ai's documented body limit (~10 MB) is larger than Gemini's per-image inline data ceiling (~4 MB), so the Gemini-tuned numbers are conservative for fal.ai too. They are independently configurable so a future "fal.ai accepts higher-res inputs without surcharge" tuning is one env-var change.

**Alternatives considered**:

- A second `FalAiPhotoReducer` class living in `service/falai/`: more code, no benefit. Rejected.
- Static default thresholds shared via a single record: would need to be redefined in two places when the providers diverge. Rejected.

---

## R5. fal.ai exception → `FallbackReason` mapping

**Decision**: `FalAiImageGenerator` catches provider-boundary failures and throws a single typed `GenerationFailure(FallbackReason reason, Throwable cause)` — same exception type 003 uses. The mapping table follows the same shape as 003 R5 with three additions specific to fal.ai's queue semantics:

| Condition | `FallbackReason` |
|---|---|
| `FalAiProperties.apiKey` is null/blank at call time — checked up-front, no outbound HTTP | `NOT_CONFIGURED` |
| `java.net.ConnectException`, `UnknownHostException`, `SSLException`, or non-timeout `IOException` on **any** of submit / poll / result-fetch / image-bytes-fetch | `NETWORK_ERROR` |
| HTTP `429 Too Many Requests` on submit OR `RESOURCE_EXHAUSTED` / `RATE_LIMIT_EXCEEDED` in any response body | `RATE_LIMITED` |
| `HttpTimeoutException` on any per-step call OR `Instant.now() > deadline` reached during the poll loop OR `status == "IN_QUEUE"` / `"IN_PROGRESS"` exceeds the 30 s end-to-end budget | `TIMEOUT` |
| HTTP 2xx on submit but body lacks `request_id`; HTTP 2xx on result fetch but body lacks `images[0].url`; image-bytes-fetch returns non-image content-type or zero bytes; any base64 decode failure on the inlined photo construction | `MALFORMED_RESPONSE` |
| HTTP 2xx on poll with `status == "FAILED"` AND the failure detail mentions safety / content-policy / NSFW (case-insensitive substring match against `"safety"`, `"content_policy"`, `"nsfw"`, `"unsafe"`); HTTP 4xx with body indicating the request was refused for safety reasons | `SAFETY_REFUSED` |
| HTTP 5xx other than 504 on submit / poll / fetch — after in-step retries exhausted within budget | `NETWORK_ERROR` |
| HTTP 4xx (other than 429) on submit/poll/fetch (e.g. 401/403 — bad key; 400 — bad request; 404 — model id wrong) | `MALFORMED_RESPONSE` (operator details in backend logs per FR-1620) |
| HTTP 2xx on poll with `status == "FAILED"` and no recognisable safety substring | `MALFORMED_RESPONSE` (treat as "the model refused for a non-safety reason"; operator inspects logs) |
| Any other `RuntimeException` escaping the `FalAiClient` seam | `MALFORMED_RESPONSE` (catch-all, parity with 003) |

**Rationale**:

- The enum is closed (spec FR-1615 mandates the same six codes 003 FR-218 pinned). No new codes are introduced; fal.ai-specific concepts like queue-stall map onto `TIMEOUT`, deliberate model-side refusal maps onto `SAFETY_REFUSED`, "we don't know why it failed" maps onto `MALFORMED_RESPONSE`.
- Substring-matching the safety classification is loose, but fal.ai's failure detail strings are not a stable enumeration we can switch on; the safest fallback for a "FAILED but no safety hint" case is `MALFORMED_RESPONSE`, which still serves the user a complete poster.
- The deadline-overrun → `TIMEOUT` mapping is the contract enforcement of FR-1614a / SC-1611.

**Alternatives considered**:

- Adding a new `QUEUE_STALL` reason code: would require a spec amendment; the spec deliberately keeps the enumeration closed. `TIMEOUT` is the right semantic match. Rejected.
- Distinct codes for "auth error" vs "malformed body": already collapsed into `MALFORMED_RESPONSE` by 003; no reason to diverge here.

---

## R6. Configuration pattern — `FalAiProperties`

**Decision**: A `@ConfigurationProperties(prefix = "aiavatar.falai")` record-style `FalAiProperties` bound from `application.yml`, with env-var overrides — mirroring 003's `GeminiProperties` exactly.

```yaml
aiavatar:
  falai:
    api-key: ${FAL_AI_API_KEY:}
    model-id: ${FAL_AI_MODEL_ID:fal-ai/nano-banana-pro/edit}
    endpoint-url: ${FAL_AI_ENDPOINT_URL:https://queue.fal.run}
    submit-timeout-ms: ${FAL_AI_SUBMIT_TIMEOUT_MS:8000}
    poll-timeout-ms: ${FAL_AI_POLL_TIMEOUT_MS:5000}
    fetch-timeout-ms: ${FAL_AI_FETCH_TIMEOUT_MS:10000}
    end-to-end-timeout-ms: ${FAL_AI_END_TO_END_TIMEOUT_MS:30000}
    poll-initial-interval-ms: ${FAL_AI_POLL_INITIAL_INTERVAL_MS:1000}
    poll-max-interval-ms: ${FAL_AI_POLL_MAX_INTERVAL_MS:5000}
    max-input-bytes: ${FAL_AI_MAX_INPUT_BYTES:4194304}
    max-input-longest-edge: ${FAL_AI_MAX_INPUT_LONGEST_EDGE:1536}
    reduced-target-longest-edge: ${FAL_AI_REDUCED_TARGET_EDGE:1024}
    reduced-jpeg-quality: ${FAL_AI_REDUCED_JPEG_QUALITY:0.85}
```

The `falai` Spring profile is activated explicitly (`SPRING_PROFILES_ACTIVE=falai`), independently of the `FAL_AI_API_KEY` env var. When the profile is active but the key is blank, `FalAiImageGenerator.generate()` short-circuits to `GenerationFailure(NOT_CONFIGURED)` pre-HTTP — same shape as 003.

**Rationale**:

- Direct symmetry with `GeminiProperties` makes the two providers visually parallel and reduces "wait, why is this different?" friction during code review.
- The submit / poll / fetch timeouts are split because fal.ai has three distinct outbound HTTP steps with different latency profiles. The end-to-end timeout is the spec-mandated 30 s cap (FR-1614a, SC-1611). All four are env-var overridable so a slow CI environment can tune up without code changes.
- `endpoint-url: https://queue.fal.run` (no trailing slash; the model-id appended at request time is `/{model-id}`).

**Alternatives considered**:

- A single shared `*Properties` interface: premature abstraction; the two providers happen to share a shape today but their config might diverge as fal.ai-specific knobs (queue priority, num_images, output_format) grow.
- Auto-activating `falai` profile when `FAL_AI_API_KEY` is set: rejected for clarity — the spec clarification Q1 makes profile activation an explicit operator choice.

---

## R7. Multi-profile activation refusal — `ProviderProfileGuard` (FR-1605)

**Decision**: A `@Configuration` class containing a `@PostConstruct` method that inspects `Environment.getActiveProfiles()` at startup and throws `IllegalStateException` (which Spring Boot reports as a context-failure → exit non-zero) when both `gemini` and `falai` are present.

```java
@Configuration
public class ProviderProfileGuard {
    private static final Logger log = LoggerFactory.getLogger(ProviderProfileGuard.class);
    private final Environment env;

    public ProviderProfileGuard(Environment env) { this.env = env; }

    @PostConstruct
    public void guardAgainstAmbiguousProvider() {
        Set<String> active = Set.of(env.getActiveProfiles());
        if (active.contains("gemini") && active.contains("falai")) {
            String msg = "Refusing to start: both 'gemini' and 'falai' profiles are active. "
                       + "Set spring.profiles.active to exactly one of {gemini, falai} (or neither for stub).";
            log.error("event=startup.fatal reason=ambiguous_provider profiles={}", active);
            throw new IllegalStateException(msg);
        }
    }
}
```

**Rationale**:

- `@PostConstruct` runs at context-init time, before the controller is wired and before any `Generate` request is served. Spring Boot's startup wraps the `IllegalStateException` and exits with a non-zero status code — exactly the "fail loud" semantic the spec clarification mandates.
- Using `Environment.getActiveProfiles()` (not `getDefaultProfiles()`) ensures the check applies only to operator-set active profiles. The `default` profile case (neither `gemini` nor `falai` active) passes silently — that's the stub-only path FR-1603 describes.
- The structured ERROR log line lets ops see the misconfiguration immediately on log tail without needing to dig through stack traces.

**Alternatives considered**:

- An `EnvironmentPostProcessor` running earlier in the lifecycle: would catch the misconfig before any other beans are constructed, but the lifecycle is more obscure; `@PostConstruct` on a `@Configuration` class is conventional Spring and easy to test.
- Falling back deterministically (e.g. "if both active, picked alphabetically"): explicitly rejected by the spec clarification (Q1 → A: "refuse to start").
- Throwing during a custom `ApplicationListener<ApplicationEnvironmentPreparedEvent>`: fires before the application context exists, so logging is partly noisy. Rejected.

---

## R8. 30 s end-to-end deadline — opt-out from `RetryTemplate`

**Decision**: Add two methods to the `ImageGenerator` interface:

```java
public interface ImageGenerator {
    PosterImage generate(GeneratedCharacter character, AlterEgoRequest request, PhotoPayload photo);

    /** Provider name surfaced in ResponseMeta.provider and in the FR-1613 log line. */
    String providerName();   // "gemini" | "falai" | "stub"

    /** Whether AlterEgoService should wrap calls in the 5-attempt RetryTemplate.
     *  Default true for backwards-compatible Gemini/stub behaviour. */
    default boolean wantsExternalRetry() { return true; }
}
```

`FalAiImageGenerator` overrides `wantsExternalRetry() → false`. `AlterEgoService.generate()` becomes:

```java
PosterImage rawPoster = imageGenerator.wantsExternalRetry()
    ? retryTemplate.execute(ctx -> imageGenerator.generate(character, request, photo))
    : imageGenerator.generate(character, request, photo);
PosterImage poster = posterFrameOverlay.apply(rawPoster);
```

Inside `FalAiImageGenerator.generate()`, the deadline is captured once:

```java
Instant deadline = Instant.now().plusMillis(props.endToEndTimeoutMs());
return client.generateImage(props, prompt, reduced, deadline);
```

`FalAiClient.generateImage(...)`:

1. Checks `apiKey` non-blank (else `NOT_CONFIGURED`).
2. Submits with `props.submitTimeoutMs()` per-attempt timeout. On transient 5xx / network error, retries up to **3 in-budget attempts** with 200 ms → 2 s exponential back-off + jitter, each attempt checking `Instant.now() < deadline` before issuing.
3. Polls `status_url` with bounded interval (`pollInitialIntervalMs` → `pollMaxIntervalMs`, ±20% jitter, doubling). Each poll has `props.pollTimeoutMs()` per-attempt timeout. The loop checks `Instant.now() < deadline` before every poll. On transient poll failure, the next iteration retries naturally — no separate retry counter (the budget IS the retry budget here).
4. On terminal `COMPLETED`, GETs `response_url` (per-attempt timeout `props.fetchTimeoutMs()`, up to 2 in-budget retries on transient failures) → parses `images[0].url` → GETs the CDN URL → returns the bytes wrapped in `PosterImage`.
5. If at any step `Instant.now() >= deadline`, throws `GenerationFailure(TIMEOUT)`.

**Rationale**:

- **Why an `ImageGenerator`-interface flag, not a runtime decision in `AlterEgoService`**: the orchestrator must NOT special-case "if `falai` profile then …" — provider awareness should live in the `ImageGenerator` implementation, behind a generic capability flag. `wantsExternalRetry()` is that flag.
- **Why default-true**: existing implementations (`GeminiImageGenerator`, `StubImageGenerator`) do not need to be modified beyond the new `providerName()` override. Backwards compatibility within the codebase.
- **Why deadline-aware in-step retries (not a single attempt)**: Constitution Principle IV requires "every HTTP call to a backend or third-party API MUST implement a retry policy". Skipping retry inside the queue+subscribe loop would arguably violate that. Per-step retries bounded by the end-to-end deadline satisfy both the spec's 30 s cap AND Principle IV's resilience intent. The retry is finer-grained than 003's 5-attempt sledgehammer because the queue+subscribe pattern already absorbs much of what RetryTemplate would otherwise re-run.
- **Why `Instant`-based deadline (not `nanoTime`)**: `Instant.now()` is monotonic for short windows on supported platforms and lets test code inject a `Clock` for determinism. `nanoTime` is more accurate but harder to mock.

**Alternatives considered**:

- A custom `RetryPolicy` bean that aborts after N elapsed seconds across attempts: complicated to write and test; the Spring Retry API makes "deadline across attempts" awkward (each attempt restarts the policy state).
- Per-provider distinct `RetryTemplate` beans wired by `@Qualifier`: would require either splitting `RetryConfig` or adding profile-conditional beans. More machinery than the interface flag.
- Using `CompletableFuture.orTimeout(...)` to enforce the deadline at the orchestrator level: races against the underlying HTTP attempt; cancelling an in-flight `HttpClient.send()` is non-trivial.

---

## R9. Provider discriminator — wire shape and log line

**Decision**:

**Wire shape** (additive, version 4.0.0): `ResponseMeta` gains a mandatory `provider` field of enum type `Provider` with values `"gemini" | "falai" | "stub"`. The existing `outcome` and `reason` fields keep 003 semantics. Per FR-1612, **no `attemptedProvider` field is added** to the response body.

```java
public enum Provider {
    GEMINI("gemini"),
    FALAI("falai"),
    STUB("stub");
    // @JsonValue / @JsonCreator mirror Outcome's pattern.
}

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMeta(Outcome outcome, UUID correlationId, FallbackReason reason, Provider provider) {
    public ResponseMeta {
        // Compact-constructor invariants:
        if (outcome == Outcome.REAL && reason != null) throw new IAE(...);
        if (outcome == Outcome.FALLBACK && reason == null) throw new IAE(...);
        if (outcome == Outcome.REAL && provider == Provider.STUB) throw new IAE(
            "outcome REAL requires a real provider; got STUB");
        if (outcome == Outcome.FALLBACK && provider != Provider.STUB) throw new IAE(
            "outcome FALLBACK requires provider STUB; got " + provider);
        if (provider == null) throw new IAE("provider is required");
    }

    public static ResponseMeta real(UUID correlationId, Provider provider) {
        return new ResponseMeta(Outcome.REAL, correlationId, null, provider);
    }

    public static ResponseMeta fallback(UUID correlationId, FallbackReason reason) {
        return new ResponseMeta(Outcome.FALLBACK, correlationId, reason, Provider.STUB);
    }
}
```

**Log line** (extends 003 FR-219 / FR-1613):

```
log.info("event=generation.completed outcome=real provider={} correlationId={}",
    provider.wire(), correlationId,
    StructuredArguments.kv("event", "generation.completed"),
    StructuredArguments.kv("outcome", "real"),
    StructuredArguments.kv("provider", provider.wire()),
    StructuredArguments.kv("attemptedProvider", provider.wire()),  // matches `provider` on real success
    StructuredArguments.kv("correlationId", correlationId));
```

```
log.warn("event=generation.completed outcome=fallback provider=stub attemptedProvider={} reason={} correlationId={}",
    attemptedProvider, reason.wire(), correlationId,
    StructuredArguments.kv("event", "generation.completed"),
    StructuredArguments.kv("outcome", "fallback"),
    StructuredArguments.kv("provider", "stub"),
    StructuredArguments.kv("attemptedProvider", attemptedProvider),  // "gemini" | "falai" | "none"
    StructuredArguments.kv("reason", reason.wire()),
    StructuredArguments.kv("correlationId", correlationId),
    cause);
```

`attemptedProvider` is derived in `AlterEgoService` from the wired `imageGenerator.providerName()`:
- If the wired generator is `StubImageGenerator` (default profile): `"none"`.
- Otherwise: `imageGenerator.providerName()` (`"gemini"` or `"falai"`).

**Rationale**:

- **Compact-constructor invariants** make illegal states unrepresentable at the type system level (Outcome × Provider is a 2×3 matrix; only 3 combos are legal: `(REAL, GEMINI)`, `(REAL, FALAI)`, `(FALLBACK, STUB)`). A `ResponseMeta` carrying `(REAL, STUB)` or `(FALLBACK, GEMINI)` cannot be constructed — the constructor throws.
- **`attemptedProvider` is a log-only field** to comply with FR-1612 / clarification Q2. It lives in the structured args of the existing `event=generation.completed` log line. Operators reading JSON-formatted logs have direct access; the user does not.
- **`"none"` rather than `null` for the no-real-provider case** keeps the structured-log JSON shape consistent (every field always present) and makes Splunk/Loki/Grafana queries trivial (`attemptedProvider:none` filters cleanly).
- **The default profile's "stub-only" path now emits `outcome=fallback, reason=not_configured, provider=stub`** — a behaviour shift from 003 (where default profile emitted `outcome=real`). This is mandated by spec US1 acceptance scenario #3 and by FR-1612's invariant. Tests are updated accordingly.

**Alternatives considered**:

- Keeping `outcome=real` for the default-profile stub path (preserving 003 semantics): contradicts US1 acceptance #3 explicitly.
- Adding `provider` only to `outcome=real` responses: less consistent; the response shape becomes conditional, which is harder to validate against the OpenAPI schema. Rejected.
- Using `null` instead of `"none"` for `attemptedProvider`: tooling-hostile in JSON logs.

---

## R10. Fault-injection testing — extending the WireMock matrix

**Decision**: Extend 003's WireMock-based integration tests to cover the `falai` profile. Use the same WireMock dependency (test-scope, no new addition). New integration tests:

- `GenerateAlterEgoFalAiIT` — happy path. Stubs:
  1. `POST /fal-ai/nano-banana-pro/edit` → 200 with `{request_id, status_url, ...}`.
  2. `GET /…/status` → 200 with `status: "COMPLETED"` (after one stubbed `IN_PROGRESS` response).
  3. `GET /…/{request_id}` → 200 with `images[0].url` pointing back at WireMock.
  4. `GET /<image-cdn-stub>` → 200 with a tiny valid JPEG.
  Asserts `outcome=real, provider=falai`, no `reason`.

- `GenerateAlterEgoFalAiFailureIT` — parametrised matrix matching SC-1606:

  | Case | Stub | Expected `reason` |
  |---|---|---|
  | No fal.ai key | profile `falai` + `FAL_AI_API_KEY=""` | `not_configured` |
  | Network error | `Fault.CONNECTION_RESET_BY_PEER` on submit | `network_error` |
  | HTTP 5xx | submit returns `503` for all in-step retries | `network_error` |
  | Timeout (per-step) | submit `withFixedDelay(10000)` exceeding `submitTimeoutMs=8000` | `timeout` |
  | Queue stall | poll repeatedly returns `status: "IN_PROGRESS"` for >30 s | `timeout` |
  | Rate limit | submit returns `429` with `Retry-After: 30` | `rate_limited` |
  | Malformed | submit returns `200` with `{garbage: true}` (no `request_id`) | `malformed_response` |
  | Safety refusal | poll returns `status: "FAILED", detail: "request blocked: nsfw"` | `safety_refused` |

- `ProviderProfileGuardIT` — `@SpringBootTest(properties = "spring.profiles.active=gemini,falai")` asserts that context fails to start with an `IllegalStateException` whose message names both profiles.

**Rationale**:

- WireMock is already in the project as a test-scope dep (003); no new dependency.
- The matrix mirrors 003's SC-205 matrix exactly so a code reviewer can diff the two test classes side-by-side and see precisely where fal.ai diverges (queue-stall is the new fault mode; 4 of the others are renamed adaptations of 003's faults).
- For the **30 s end-to-end timeout**, SC-1611 wants ≤ 31 s wall-clock with 1 s tolerance. The integration test injects a `Clock` (constructor-injected into `FalAiClient` for testability) so the deadline check fires deterministically without sleeping for 30 s in CI. The test calls `client.generateImage(props, prompt, photo, fixedDeadlineInThePast)` and asserts `GenerationFailure(TIMEOUT)`. A separate "smoke" test (`@Tag("slow")`) actually sleeps to validate end-to-end timing in CI.

**Alternatives considered**:

- Using a real fal.ai key in CI: introduces network flakes and cost. Rejected.
- Spying on `HttpClient` with Mockito instead of WireMock: works for unit tests (already in use) but bypasses URL parsing, header wiring, and JSON serialisation. WireMock is the integration-test choice.

---

## R11. Reusing 003's `GeminiPromptBuilder` skeleton vs forking

**Decision**: **Fork** to a new `FalAiPromptBuilder` class. The two builders share the same Setup-input → display-label maps (one per enum), but the prompt opener differs (`"Edit the reference photo to render the person as their alter ego."` for fal.ai vs `"Generate a cinematic portrait poster of the person in the reference photo."` for Gemini) because `nano-banana-pro/edit` is image-edit-conditioned and reads "Edit" as a meaningful input verb.

**Rationale**:

- The display-label maps are pure data and could be extracted to a shared utility (`SetupLabels.java` in `service/` or `model/`). For the first iteration of this feature we accept duplicated `Map<Enum, String>` literals to keep the change surface small; if/when a third real provider is added, the extraction becomes worthwhile.
- The two builders have no compile-time coupling — neither imports the other — so divergence (e.g. fal.ai-specific prompt-engineering tweaks discovered during testing) does not require coordinating two changes.

**Alternatives considered**:

- A shared `ImagePromptBuilder` interface with provider-specific implementations: defensible, but the interface shape would have exactly one method (`String build(AlterEgoRequest)`) and exactly two implementations — premature abstraction for the size of the win.
- Reusing `GeminiPromptBuilder` literally and changing only the opening line via a constructor parameter: tempting but couples fal.ai's behaviour to changes in the Gemini-named class. Rejected.

---

## R12. Contract version — 3.0.0 → 4.0.0

**Decision**: Bump `contracts/alter-egos.openapi.yaml` from `3.0.0` (003's last revision) to **`4.0.0`**. The version bump is **not** mandated by an incompatibility (the `provider` field is purely additive at the JSON-payload level), but it follows the project convention that each feature with a wire-shape change owns a major version, and reflects the meaningful semantic shift (the default-profile stub run now reports `outcome: "fallback"` rather than `outcome: "real"` per R9).

The contract file is **co-located in the new feature directory** (`specs/016-falai-image-provider/contracts/alter-egos.openapi.yaml`) — superseding 003's copy. Build / CI references the latest file.

**Rationale**:

- Keeping the OpenAPI version in sync with the feature number-of-most-recent-wire-shape-change makes change attribution easy.
- Frontend's hand-authored `types.ts` mirror gets the additive `provider` field plus a new `Provider` union type; the `npm run generate:api-types` smoke target (003 R8) re-validates against the updated YAML.

**Alternatives considered**:

- Stay on 3.0.0 (additive change, no major bump): would understate the semantic shift in the default-profile outcome.
- Bump to 3.1.0 (additive minor): defensible, but the project convention so far has been 1.0.0 per feature; staying with that convention.

---

## Summary table — research items vs spec FRs

| Research item | Resolves spec requirement(s) |
|---|---|
| R1 — model id default + configurability | FR-1610, FR-1606 |
| R2 — hand-rolled HTTPS over JDK `HttpClient` | FR-1601, FR-1614, Constitution Principles I + VI |
| R3 — submit/poll/fetch + inline data URL | FR-1606, FR-1614, edge cases (queue, CDN URL) |
| R4 — provider-neutral `PhotoReducer` | FR-1616, FR-1617 |
| R5 — exception → `FallbackReason` mapping | FR-1614, FR-1615 |
| R6 — `FalAiProperties` config record | FR-1610, FR-1611, FR-1614a (timeouts) |
| R7 — `ProviderProfileGuard` startup check | FR-1605, clarification Q1 |
| R8 — `wantsExternalRetry()` opt-out + deadline | FR-1614a, SC-1611, clarification Q3 |
| R9 — `Provider` discriminator + log-line extension | FR-1612, FR-1613, clarification Q2 |
| R10 — WireMock matrix extension | FR-1614, FR-1615, SC-1606, SC-1611 |
| R11 — separate `FalAiPromptBuilder` | FR-1606, FR-1607 |
| R12 — OpenAPI v4.0.0 | FR-1612, FR-1622 |

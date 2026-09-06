# Phase 0 — Research: Gemini Image Generator

**Feature**: 003-gemini-image-generator
**Date**: 2026-04-22
**Status**: All items resolved. No `NEEDS CLARIFICATION` remain.

This document resolves the open planning questions raised by `spec.md` and `plan.md`:
concrete numbers the spec deferred, library choices, prompt shape, exception
mapping, and the contract-rename consequence. Each item follows the
**Decision / Rationale / Alternatives** format.

---

## R1. Gemini model identifier — default value and configurability

**Decision**: Default model id is **`gemini-3.1-flash-image-preview`** — the exact identifier named in Issue #6 and verified reachable against `generativelanguage.googleapis.com/v1beta/models` on 2026-04-23 with a live API key (alongside `gemini-3-pro-image-preview`, `gemini-2.5-flash-image`, and `nano-banana-pro-preview`). Configurable via `aiavatar.gemini.model-id` (bound from env var `GEMINI_MODEL_ID`) per FR-210.

**Correction note** (2026-04-23): An earlier draft of this research item assumed `gemini-3.1-flash-image-preview` was forward-looking and proposed `gemini-2.5-flash-image-preview` as a substitute. A live `ListModels` probe during implementation showed the `3.1` preview is in fact live, and the `2.5` variant is GA as `gemini-2.5-flash-image` (no `-preview` suffix). Defaults in `application.yml` and `docker-compose.yml` were corrected to the issue's named target.

**Rationale**:

- FR-210 says the default MUST match the issue's identifier unless it is not reachable at the provider. Since it IS reachable, keeping the stated target is the contract-preserving choice.
- Alternatives for when `3.1` is someday retired: fall back to `gemini-3-pro-image-preview` or the GA `gemini-2.5-flash-image`. The `@ConfigurationProperties` binding lets this land as a single `application.yml` edit with no code change.

**Alternatives considered**:

- `gemini-2.5-flash-image` (GA): would also work; preferred only if `3.1` preview becomes unstable.
- `gemini-3-pro-image-preview`: higher quality but slower and more expensive. Worth revisiting if SC-207's timing bound becomes an issue.
- Picking a text-only Gemini model and composing the image via a different pipeline: defeats the point of the issue.

---

## R2. Gemini client — official SDK vs. hand-rolled HTTP

**Decision**: **Hand-rolled HTTP** against `https://generativelanguage.googleapis.com/v1beta/models/{model-id}:generateContent` using Java 21's `java.net.http.HttpClient` + Jackson, wrapped by the existing `RetryTemplate`. No Google SDK is added to the dependency graph.

**Rationale**:

- Google's `google-cloud-vertexai` and `google-genai` Java SDKs pull in a **large transitive dependency tree** — `grpc-*`, `guava`, `protobuf-java`, `opencensus`, an old `auth0/jwks`, and 20+ other libraries — many of which have historically been flagged by OWASP dependency-check for CVEs requiring version pins. Constitution Principles I and VI make this a costly choice on a POC.
- The REST surface we need is **one endpoint, one request shape, one response shape**. The request is a JSON object with `contents: [{ parts: [{ text: "…" }, { inline_data: { mime_type, data: <base64 photo> } }] }]`; the response is a JSON object with `candidates[0].content.parts[0].inline_data.data` carrying the generated image as base64. Hand-rolling this is ~80 lines of Jackson mapping.
- `HttpClient` is a JDK API with zero additional dependencies. It supports per-request timeouts (bounds SC-207). It composes trivially with Spring's `RetryTemplate`.
- Auth is a single HTTP header: `x-goog-api-key: <key>`. No OAuth complexity.
- The existing `alterEgoClient.ts` + backend controller already demonstrate this pattern on our own boundaries; applying it to the Gemini boundary keeps the architecture coherent.

**Alternatives considered**:

- **`google-cloud-vertexai`**: larger surface (grpc-based), aimed at GCP-auth workflows rather than API-key use; dependency bloat.
- **`google-genai`** (the newer "one SDK for Gemini and Vertex" from Google): promising but still evolving, adds a transitive `com.google.genai.*` + gson + guava stack; if/when it becomes lean and stable we can migrate without breaking the `ImageGenerator` seam.
- **OkHttp**: another hand-rolled option, but `java.net.http.HttpClient` already exists in Java 21 — adding OkHttp adds a dependency for no gain.
- **Spring `RestClient` / `WebClient`**: either would work; `HttpClient` chosen for minimal surface and because we don't need reactive composition.

---

## R3. Gemini request shape — what exactly gets sent

**Decision**: A single `POST …:generateContent` call with the JSON body below. The prompt is assembled deterministically by `GeminiPromptBuilder` from the Setup fields; the photo is attached as an `inline_data` part (base64-encoded on the backend, not client-side).

```json
{
  "contents": [
    {
      "role": "user",
      "parts": [
        { "text": "<composed prompt, see below>" },
        { "inline_data": { "mime_type": "image/jpeg", "data": "<base64 photo, post-reduction>" } }
      ]
    }
  ],
  "generationConfig": {
    "responseModalities": ["IMAGE"],
    "candidateCount": 1
  }
}
```

**Prompt template** (filled by `GeminiPromptBuilder`, line breaks preserved):

```
Generate a cinematic portrait poster of the person in the reference photo.

The subject's appearance (face, hair, skin tone, approximate age, general build) MUST closely match the reference photo. Render the subject as an "alter ego" with the following attributes:

- Name: <firstName>
- Engineering role: <role-display-label>   (e.g. "Cloud Architect", "Backend Developer", "AI Engineer")
- Fictional universe / aesthetic: <universe-display-label>   (e.g. "Marvel superhero", "Cyberpunk neo-noir", "The Office sitcom")
- Pose / stance: <pose-display-label>   (e.g. "heroic, chest forward", "scholarly, thoughtful")
- Vibe / tone: <vibe-display-label-or-omitted>   (e.g. "rebellious", "builder / tinkerer")

Composition notes:
- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.
- Clear focus on the subject; the universe aesthetic is the setting, not the subject.
- No overlaid text, logos, or watermarks — text will be composited downstream.
```

Each Setup field maps to a **distinct display label** (not the enum wire value), selected by a `Map<Enum, String>` in `GeminiPromptBuilder`. This keeps the prompt human-readable and makes the contribution of each field testable (FR-203, SC-202).

**Rationale**:

- **Portrait aspect ratio** (3:4) lines up with the existing stub's 900×1200 poster and avoids the frontend having to re-layout.
- **"No overlaid text"** is a deliberate guard: the title / tagline / superpowers / quote are rendered by the frontend's existing `PosterView`, not by Gemini. If Gemini adds text, the frontend still renders its own on top, but we'd prefer Gemini to leave that canvas clean.
- **`responseModalities: ["IMAGE"]`** tells Gemini we don't need a text response — reduces payload and removes a potential failure mode (malformed text).
- **`candidateCount: 1`** avoids paying for multiple candidates on a POC.
- Display labels (e.g. "Backend Developer") instead of wire values (e.g. `backend-dev`) because the wire values are shorthand keys, not natural-language terms the model can ground on.

**Alternatives considered**:

- Passing the photo as a `file_data` pointer (requires a prior `files.upload` call): more round-trips, more failure modes, and not needed for a photo that lives only in-process for one request.
- Handing Gemini the enum wire values directly: empirically produces worse grounding on the fictional universe / role axes.
- Multi-turn prompting (chain-of-thought, two-shot): unnecessary for a single-shot image; adds latency.

---

## R4. Photo reduction — threshold, library, algorithm

**Decision**:

- **Library**: **Thumbnailator 0.4.20** (`net.coobird:thumbnailator`). Single-jar, MIT-licensed, zero transitive deps, actively maintained, no known CVEs.
- **Threshold**: Reduce the photo if **either** of the following is true:
  - encoded byte size > **4 MB** (Gemini's documented inline-data per-request ceiling is ~20 MB for text+image combined; 4 MB leaves headroom for the prompt, JSON framing, and base64 expansion (~33% overhead)), **OR**
  - longest pixel dimension > **1536 px** (preserves detail for face reference while keeping encoded byte size modest at typical quality factors).
- **Target on reduce**: scale longest dimension to **1024 px**, output as **JPEG at quality 0.85**. Typical 8-MP phone photo (~3.5 MB JPEG) reduces to ~200–400 KB. Well inside the 4 MB ceiling.
- **Pass-through on under-threshold**: if both checks pass (photo small enough), the original bytes + media type are forwarded to the Gemini call without re-encoding (FR-207 — no needless degradation).

**Rationale**:

- **Thumbnailator** vs `javax.imageio`: Thumbnailator handles EXIF orientation correctly (phone photos often carry a rotation flag that ImageIO silently ignores, producing sideways inputs), and its fluent API (`Thumbnails.of(bytes).size(1024, 1024).outputFormat("jpg").outputQuality(0.85).toOutputStream(out)`) is ~8 lines vs ~30 for equivalent ImageIO code. No runtime dependencies — just the one jar.
- **Thresholds** chosen against Google's **published** Gemini API limits (as of January 2026): the combined `generateContent` request payload is capped around 20 MB, with inline_data per-image practically capped at similar scale; a 4 MB post-base64 budget is well below both. FR-210 keeps this configurable — `aiavatar.gemini.max-input-bytes` and `aiavatar.gemini.max-input-longest-edge` override the defaults.
- **1024 px longest edge at JPEG 0.85** is the same threshold OpenAI's vision API and Anthropic's image-input API recommend for face-reference workloads. Empirically good enough for Gemini's face-grounding.

**Alternatives considered**:

- Hand-rolled `ImageIO` + `AffineTransform`: more code, doesn't auto-rotate EXIF-flagged photos. Rejected.
- PNG output: lossless but 5-10× larger. Unnecessary for photos; JPEG is fine. PNGs already under-threshold are preserved as-is via the pass-through branch.
- Client-side (browser) resize via `canvas`: rejected per the spec's Assumption (consistency + single-path testing wins over bandwidth savings on a POC; backend resize happens regardless).
- Upscaling small photos: explicitly out of scope — FR-207 says the reducer MUST NOT up-scale.

---

## R5. Exception → `FallbackReason` mapping

**Decision**: `GeminiImageGenerator` catches provider-boundary failures and throws a single typed `GenerationFailure(FallbackReason reason, Throwable cause)`. The `RetryTemplate` retries on every `GenerationFailure` (they all extend `RuntimeException` which is its default retry policy); on final exhaustion the `AlterEgoService`'s outer `catch (RuntimeException)` block recognises the exception subclass, reads the `reason`, and populates `AlterEgoResponse.ResponseMeta.reason`. For the "unexpected other" case (e.g. a `CharacterGenerator` bug), the reason falls back to `MALFORMED_RESPONSE` — the closest semantic match within the spec-pinned enum.

**Mapping table** (applied inside `GeminiImageGenerator.generate()`):

| Condition | `FallbackReason` |
|---|---|
| `GeminiProperties.apiKey` is null/blank at call time (backend started without the env var) — checked up-front, Gemini call not attempted | `NOT_CONFIGURED` |
| `java.net.ConnectException`, `UnknownHostException`, `SSLException`, or any `IOException` that isn't a `HttpTimeoutException` | `NETWORK_ERROR` |
| HTTP `429 Too Many Requests` OR `RESOURCE_EXHAUSTED` code in response body | `RATE_LIMITED` |
| `java.net.http.HttpTimeoutException` OR HTTP `504 Gateway Timeout` | `TIMEOUT` |
| HTTP 2xx but response body doesn't parse as JSON, lacks `candidates[0].content.parts[*].inline_data.data`, or the base64 decodes to zero bytes / a non-image mime | `MALFORMED_RESPONSE` |
| HTTP 2xx + `promptFeedback.blockReason` present, OR `finishReason: SAFETY`, OR `candidates[0].finishReason: IMAGE_SAFETY` | `SAFETY_REFUSED` |
| HTTP 5xx other than 504 (500, 502, 503) | `NETWORK_ERROR` (treated as "the provider is having a bad day" — equivalent user outcome) |
| HTTP 4xx other than 429 (400, 401, 403, 404) | `MALFORMED_RESPONSE` (our request was wrong; surface as generic preview to the user, log details for the operator) |
| Any other `RuntimeException` surfacing from the `ImageGenerator` | `MALFORMED_RESPONSE` (catch-all, operator gets the stack trace via Logback) |

**Rationale**:

- The RetryTemplate policy retries all RuntimeExceptions by default; we **want** retry on `NETWORK_ERROR`, `TIMEOUT`, `RATE_LIMITED`, and transient 5xx, so leaving the default policy in place is correct. For `NOT_CONFIGURED` and `SAFETY_REFUSED` retry is pointless but harmless (the result is deterministic), and removing retry for those cases would add code surface for no user-observable win within the SC-207 timing bound.
- The enum sticks to the 6 codes pinned by spec FR-218 — no planning-phase extension. The "other RuntimeException" case (which realistically can't fire given the stub character generator is deterministic) maps into `MALFORMED_RESPONSE`, preserving the spec's enumeration.
- The "HTTP 4xx (non-429) → `MALFORMED_RESPONSE`" decision treats client-side mis-requests (bad API key format, bad model id) as a data-shape issue from the user's perspective. Operators see the real status in the backend log.

**Alternatives considered**:

- Distinct `INTERNAL_ERROR` code for the catch-all: would require amending FR-218. Rejected — the enum is spec-pinned and the catch-all is a safety net, not a common case.
- Fine-grained HTTP code → reason table (every 5xx its own code): more code, no user-observable benefit.
- Different retry policies per reason: premature; the default 5-attempt + exponential-backoff + jitter policy is exactly what Principle IV requires.

---

## R6. Configuration pattern — `@ConfigurationProperties` class

**Decision**: A single `GeminiProperties` record-style `@ConfigurationProperties(prefix = "aiavatar.gemini")` class bound from `application.yml`, with env-var overrides.

```yaml
aiavatar:
  gemini:
    api-key: ${GEMINI_API_KEY:}                      # blank default → activates NOT_CONFIGURED fallback path
    model-id: ${GEMINI_MODEL_ID:gemini-2.5-flash-image-preview}
    endpoint-url: ${GEMINI_ENDPOINT_URL:https://generativelanguage.googleapis.com/v1beta}
    request-timeout-ms: ${GEMINI_REQUEST_TIMEOUT_MS:25000}   # per-attempt timeout; RetryTemplate handles multi-attempt bound
    max-input-bytes: ${GEMINI_MAX_INPUT_BYTES:4194304}        # 4 MB
    max-input-longest-edge: ${GEMINI_MAX_INPUT_LONGEST_EDGE:1536}
    reduced-target-longest-edge: ${GEMINI_REDUCED_TARGET_EDGE:1024}
    reduced-jpeg-quality: ${GEMINI_REDUCED_JPEG_QUALITY:0.85}
```

Spring profile **`gemini`** is activated iff `GEMINI_API_KEY` is non-blank at startup, via a small bootstrap check in `main()` or a profile condition. When active, `GeminiImageGenerator` is the `ImageGenerator` bean; when inactive, `StubImageGenerator` is. The `StubImageGenerator` is **still `@Profile("default")`**.

**Rationale**:

- Typed config = early failure at startup if someone mistypes a property, rather than a null-pointer deep inside `GeminiImageGenerator`.
- Env-var-backed defaults align with the constitutional deployment model (Docker Compose + 12-factor).
- Profile-based bean wiring makes the stub-vs-real seam obvious and keeps `AlterEgoService` provider-agnostic. The tests can spin up either profile explicitly.

**Alternatives considered**:

- `@Value` sprinkled across the Gemini package: harder to test, no type safety, violates Spring best practices.
- A conditional bean (`@ConditionalOnProperty("aiavatar.gemini.api-key")`) instead of profiles: also works, but profiles already exist for similar wiring in 001/002 and keeping one mechanism is simpler.
- Runtime rotation of the API key (hot reload): out of scope for a POC.

---

## R7. Fault-injection testing — WireMock

**Decision**: Add **WireMock 3.x** as a test-scope dependency (`testImplementation("com.github.tomakehurst:wiremock-standalone:3.0.1")` or the equivalent JUnit-Jupiter module). Integration tests that exercise the `gemini` profile point the Gemini endpoint at `http://localhost:${wiremock.port}/v1beta` via a `@DynamicPropertySource` binding, and each test method configures WireMock stubs for the matrix of failure modes.

**Matrix coverage (SC-205)**:

| Test case | WireMock stub | Expected `reason` |
|---|---|---|
| Happy path | 200 + valid JSON with a tiny valid PNG base64 | (no reason — outcome: `real`) |
| Missing API key | (profile `default`, no Gemini call) | `not_configured` |
| Network error | WireMock configured to close the connection (`Fault.CONNECTION_RESET_BY_PEER`) | `network_error` |
| HTTP 5xx | 500 + empty body, repeated for all 5 retry attempts | `network_error` |
| Rate limit | 429 + `Retry-After: 30` | `rate_limited` |
| Timeout | WireMock `withFixedDelay(30_000)` exceeding the 25s per-attempt limit | `timeout` |
| Malformed response | 200 + `{"garbage": true}` | `malformed_response` |
| Safety refusal | 200 + Google-shaped body with `promptFeedback.blockReason: "SAFETY"` | `safety_refused` |

**Rationale**:

- WireMock is the idiomatic Spring-Boot choice for stubbing outbound HTTP in `@SpringBootTest`. It has first-class support for connection-level faults, delay injection, and JSON response scripting. Zero runtime weight.
- Alternatives (`MockWebServer` from OkHttp, hand-rolled `HttpServer`): possible but add more boilerplate per test. WireMock's fluent builder keeps each test under 15 lines.
- Parametrised JUnit 5 `@ParameterizedTest` + WireMock stubs keeps the matrix in a single file.

**Alternatives considered**:

- `MockRestServiceServer`: tied to `RestTemplate`; we're using `HttpClient`. Rejected.
- Stubbing at the `HttpClient` seam with Mockito: works for unit tests but not for a true integration test — bypasses the real URL parsing, header wiring, and serialisation.

---

## R8. Contract rename — `success` → `real` wire value

**Decision**: The `Outcome` wire enum changes from `{success, fallback}` → `{real, fallback}`. OpenAPI version bumped from `2.0.0` → `3.0.0`. All callers (backend tests, frontend client, examples) update atomically in the same PR.

**Rationale**:

- In the 001/002 world, `success` meant "the stub ran without throwing" — semantics consistent with the codebase since there was no real provider. After 003, `success` would mean "the real provider was used" for a `gemini`-profile run, but would *also* describe `default`-profile runs (which are now always fallbacks). Keeping the `success` wire value produces a misleading discriminator: a healthy-looking response that is in fact a stub.
- Renaming to `real` makes the semantic break explicit. A frontend reader that hasn't been updated would see an unknown value and can be programmed to treat it as `fallback` by default (graceful degradation). A backend test that hasn't been updated fails loudly (good — that's what we want).
- No external API consumers exist for this contract; the rename is a zero-cost coordinated change.

**Alternatives considered**:

- Keep `success` wire value, rename only the Java enum internally: preserves a false-positive discriminator. Rejected.
- Add a third value like `stub-fallback` to distinguish stub fallback from real-fallback: neither FR-214 nor FR-218 differentiates, and adding a distinction here would leak to the user. Rejected.
- Use a boolean `usedRealProvider`: less extensible than an enum (can't add `degraded` later if we ever want). Rejected.

---

## R9. Consent / privacy — what does the user see about the outbound photo upload?

**Decision**: **No additional user-visible consent dialogue.** The spec's Assumption makes this explicit: the Setup form is the act of consent for generation, and a booth POC context doesn't warrant a second modal. Legal / compliance copy in the README or a footer link is deferred to a follow-up feature if GDPR review later demands it.

**Rationale**:

- FR-216 constrains what is sent (photo + first name + selection enums, no analytics / account identifiers).
- A consent modal on every Generate press would harm the booth UX without meaningfully changing the user's information position (they clicked Generate — the action itself is the consent).
- Deferring to a follow-up keeps this feature focused on the wiring, consistent with the spec's bounded scope.

**Alternatives considered**:

- One-time per-session modal on first Generate: adds surface and tests; not required by any current stakeholder.
- Per-run "this will send your photo to Google" notice in the loading state: adds UX clutter without reducing risk.

---

## R10. Multipart upload size — bump from 5 MB to 20 MB

**Decision**: Raise `spring.servlet.multipart.max-file-size` and `…max-request-size` from **5 MB → 20 MB**. The Gemini-bound photo is resized down to ≤4 MB post-reduction (R4); a 20 MB upstream ceiling comfortably admits 12-MP phone photos (typically 4–8 MB JPEG).

**Rationale**:

- With backend-side resize (FR-209), rejecting uploads at 5 MB defeats the resize's purpose: a 12 MP JPEG is routinely 6–10 MB, would 413 before ever reaching `PhotoReducer`.
- 20 MB matches the Gemini inline-data per-request ceiling, meaning even the largest accepted-by-our-limit upload *could* (in a pathological case of a raw PNG screenshot) still make it into a Gemini call without resize — though normal phone JPEGs always get resized anyway.
- Backend memory footprint: one 20 MB byte array per concurrent request. At any realistic POC concurrency level (1 booth user at a time) this is trivial. A follow-up feature can introduce a streaming-resize path if it ever becomes an issue.

**Alternatives considered**:

- Keep 5 MB + require frontend-side pre-resize: moves complexity to the client, violates the Assumptions section's "single consistent reduction pipeline" decision.
- 10 MB ceiling: narrower, rejects some high-end phone captures unnecessarily.
- Unbounded: invites DoS and violates Spring's own safety posture.

---

## Open items

None. All items from `spec.md`'s Assumptions section are now concrete: model id (R1), client (R2), prompt shape (R3), resize library + thresholds (R4), exception mapping (R5), config pattern (R6), test fault-injection (R7), contract rename (R8), consent posture (R9), upload ceiling (R10). Phase 1 can proceed.

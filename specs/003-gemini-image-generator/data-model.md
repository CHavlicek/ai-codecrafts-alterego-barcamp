# Phase 1 — Data Model: Gemini Image Generator

**Feature**: 003-gemini-image-generator
**Date**: 2026-04-22

This document captures the data-shape changes introduced by 003 — contract-visible DTOs and backend-internal service types. Entities already defined by 001/002 (e.g. `AlterEgoRequest`, `Pose`, `Archetype`, `Universe`, `Vibe`, `PhotoPayload`, `PosterImage`, `GeneratedCharacter`) are **unchanged** unless explicitly listed below.

No persistence is introduced — every type below is in-memory-only for the duration of a single HTTP request (FR-215).

---

## 1. Contract-visible changes

### 1.1 `AlterEgoResponse.ResponseMeta` (edit)

Existing shape (001/002):

```java
public record ResponseMeta(Outcome outcome, UUID correlationId) { }
```

New shape (003):

```java
public record ResponseMeta(
        Outcome outcome,
        UUID correlationId,
        /**
         * Fallback reason code. Present (non-null) iff outcome == FALLBACK.
         * Drives backend logs (FR-219) and automated tests (SC-205).
         * Not surfaced to the end user — the frontend shows the generic
         * FR-214 message regardless.
         */
        FallbackReason reason
) {
    /** Convenience factory for the happy path. */
    public static ResponseMeta real(UUID correlationId) {
        return new ResponseMeta(Outcome.REAL, correlationId, null);
    }

    /** Convenience factory for the fallback path. */
    public static ResponseMeta fallback(UUID correlationId, FallbackReason reason) {
        return new ResponseMeta(Outcome.FALLBACK, correlationId, reason);
    }
}
```

**Jackson**: `@JsonInclude(JsonInclude.Include.NON_NULL)` is applied at the record level so the `reason` field is **omitted from the JSON** on the happy path rather than serialised as `"reason": null`. This keeps the wire shape clean and matches the contract (see `contracts/alter-egos.openapi.yaml`).

### 1.2 `AlterEgoResponse.Outcome` (rename)

Existing:

```java
public enum Outcome {
    SUCCESS("success"),
    FALLBACK("fallback");
    // …@JsonValue wire
}
```

New:

```java
public enum Outcome {
    REAL("real"),
    FALLBACK("fallback");
    // …@JsonValue wire
}
```

Wire-value rename `"success"` → `"real"`. Java-side rename `SUCCESS` → `REAL`. See research.md R8 for the rationale. All callers update in the same PR (backend tests, frontend client, OpenAPI examples).

### 1.3 `FallbackReason` (new)

New enum, spec-pinned (FR-218):

```java
public enum FallbackReason {
    NOT_CONFIGURED("not_configured"),
    NETWORK_ERROR("network_error"),
    RATE_LIMITED("rate_limited"),
    TIMEOUT("timeout"),
    MALFORMED_RESPONSE("malformed_response"),
    SAFETY_REFUSED("safety_refused");

    private final String wire;
    FallbackReason(String wire) { this.wire = wire; }
    @JsonValue public String wire() { return wire; }
    @JsonCreator public static FallbackReason fromWire(String value) { /* lookup, throw if unknown */ }
}
```

Lives under `com.aiavatar.alterego.model`. Its six values are a closed set — no extension without spec amendment.

**Validation rule (runtime)**: `ResponseMeta.reason` is non-null iff `outcome == FALLBACK`. Enforced in the compact constructor of `ResponseMeta`:

```java
public ResponseMeta {
    if (outcome == Outcome.REAL && reason != null) {
        throw new IllegalArgumentException("reason must be null when outcome is REAL");
    }
    if (outcome == Outcome.FALLBACK && reason == null) {
        throw new IllegalArgumentException("reason is required when outcome is FALLBACK");
    }
}
```

---

## 2. Backend-internal types (not on the wire)

### 2.1 `GeminiProperties` (new)

Spring `@ConfigurationProperties` record. Bound from `aiavatar.gemini.*` in `application.yml`; env-var-overridable (research.md R6).

```java
@ConfigurationProperties(prefix = "aiavatar.gemini")
public record GeminiProperties(
        String apiKey,                        // env: GEMINI_API_KEY; blank → NOT_CONFIGURED
        String modelId,                       // default: "gemini-2.5-flash-image-preview"
        String endpointUrl,                   // default: https://generativelanguage.googleapis.com/v1beta
        int requestTimeoutMs,                 // default: 25000; per-attempt timeout
        int maxInputBytes,                    // default: 4_194_304 (4 MB)
        int maxInputLongestEdge,              // default: 1536 (px)
        int reducedTargetLongestEdge,         // default: 1024 (px)
        double reducedJpegQuality             // default: 0.85
) {
    /** @return true if an API key is configured (non-blank). */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }
}
```

Used by: `GeminiImageGenerator`, `PhotoReducer`, `GeminiClient`.

### 2.2 `GenerationFailure` (new)

Runtime exception carrying a `FallbackReason` code from the adapter boundary to the orchestrator.

```java
public class GenerationFailure extends RuntimeException {
    private final FallbackReason reason;

    public GenerationFailure(FallbackReason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason);
    }

    public FallbackReason reason() { return reason; }
}
```

Caught exclusively by `AlterEgoService`'s outer `catch (RuntimeException)` block, which reads `reason()` and populates the response. Propagates through `RetryTemplate` normally (RuntimeException → retried per policy).

### 2.3 `PhotoReducer` (new service class, not a DTO)

Signature:

```java
@Component
public class PhotoReducer {
    private final GeminiProperties props;

    /**
     * Returns {@code photo} unchanged if it is already within both size limits
     * (byte size ≤ maxInputBytes AND longest edge ≤ maxInputLongestEdge).
     * Otherwise returns a new {@link PhotoPayload} with the image scaled to
     * {@code reducedTargetLongestEdge} and re-encoded as JPEG at
     * {@code reducedJpegQuality}.
     *
     * Never up-scales. Never persists. Preserves EXIF orientation.
     */
    public PhotoPayload reduce(PhotoPayload photo) { /* Thumbnailator */ }
}
```

Thread-safe (stateless; the `props` ref is a single configuration record).

### 2.4 `GeminiImageGenerator` (new `ImageGenerator` impl)

```java
@Component
@Profile("gemini")
public class GeminiImageGenerator implements ImageGenerator {
    private final GeminiClient client;
    private final GeminiPromptBuilder promptBuilder;
    private final PhotoReducer reducer;
    private final GeminiProperties props;

    @Override
    public PosterImage generate(GeneratedCharacter character,
                                AlterEgoRequest request,
                                PhotoPayload photo) {
        if (!props.isConfigured()) {
            throw new GenerationFailure(FallbackReason.NOT_CONFIGURED,
                    "Gemini API key not configured", null);
        }
        PhotoPayload reduced = reducer.reduce(photo);
        String prompt = promptBuilder.build(request);
        return client.generateImage(props.modelId(), prompt, reduced);
        // client throws GenerationFailure on provider-boundary errors per R5
    }
}
```

Runs inside `retryTemplate.execute(...)` (already wired in `AlterEgoService`). The `@Profile("gemini")` conditional wiring means the `default` profile still gets `StubImageGenerator`.

### 2.5 `GeminiClient` (new HTTP wrapper)

Thin wrapper around `java.net.http.HttpClient`. Methods:

```java
public PosterImage generateImage(String modelId, String prompt, PhotoPayload photo) {
    // Build POST .../models/{modelId}:generateContent
    // Body: { contents: [{ role: "user", parts: [{text}, {inline_data:{mime_type,data}}] }],
    //         generationConfig: { responseModalities: ["IMAGE"], candidateCount: 1 } }
    // Headers: x-goog-api-key, Content-Type: application/json
    // Timeout: props.requestTimeoutMs
    // Map exceptions per R5 table → throw GenerationFailure(reason, ...)
    // On success: decode base64 from candidates[0].content.parts[*].inline_data.data
    //             return new PosterImage(bytes, mime, width, height)
}
```

Width/height are read from the decoded image via `ImageIO.read(new ByteArrayInputStream(bytes)).getWidth/Height()` — small but non-zero overhead; acceptable.

### 2.6 `GeminiPromptBuilder` (new service)

Pure function. Maps enum wire values → human-readable display labels (Role: "Cloud Architect", Universe: "Star Wars", etc.) and composes the prompt template per research.md R3.

```java
@Component
public class GeminiPromptBuilder {
    public String build(AlterEgoRequest request) { /* template fill */ }
}
```

Stateless. Unit-tested by asserting that every Setup field contributes a distinct substring to the prompt (FR-203 coverage at the prompt-shape level, separate from the end-to-end visual validation of SC-202).

---

## 3. Entity relationship diagram (updated)

```
AlterEgoRequest  ──┐
                   │
PhotoPayload  ─────┼──► AlterEgoService.generate()
                   │       │
                   │       ├─► retryTemplate.execute( characterGenerator.generate(request) )
                   │       │         ├─► StubCharacterGenerator  (@Profile default — always active)
                   │       │         └─► returns GeneratedCharacter
                   │       │
                   │       ├─► retryTemplate.execute( imageGenerator.generate(character, request, photo) )
                   │       │         ├─► @Profile default:  StubImageGenerator   ─► returns PosterImage
                   │       │         └─► @Profile gemini:   GeminiImageGenerator ─► PhotoReducer ─► GeminiClient ─► Gemini API
                   │       │                                                       │
                   │       │                                                       └─ (on failure) throws
                   │       │                                                          GenerationFailure(FallbackReason)
                   │       │
                   │       └─► returns AlterEgoResponse { character, poster, meta }
                   │                                           ▲
                   │                          meta = ResponseMeta { outcome, correlationId, reason? }
                   │
                   └──────────────────────────────────────────┘
```

Legend:
- `@Profile default` path is the behaviour of 001/002 — now returns `outcome: FALLBACK, reason: NOT_CONFIGURED` rather than the old `outcome: SUCCESS`.
- `@Profile gemini` path is new. On success → `outcome: REAL`. On final-retry failure → `outcome: FALLBACK, reason: <mapped>`.

---

## 4. State transitions

The frontend session state machine already defined by 001/002 is **unchanged**:

```
idle ─► picking ─► generating ─► { succeeded | failed_with_fallback }
                      ▲                                │
                      └──── (start over) ──────────────┘
```

The only mapping change: `useGenerateAlterEgo` now reads `response.meta.outcome === 'real'` instead of `=== 'success'` to decide between `succeeded` and `failed_with_fallback`. `response.meta.reason` is **not** consumed by the frontend — the FR-214 notice is the same regardless.

---

## 5. Validation rules summary

| Rule | Enforced by | Source |
|---|---|---|
| `ResponseMeta.reason` non-null iff outcome=FALLBACK | `ResponseMeta` compact constructor | §1.3 |
| `FallbackReason` value in fixed 6-code set | enum type + `@JsonCreator` throws on unknown wire | §1.3 |
| `GeminiProperties.apiKey` non-blank gates the real path | `GeminiProperties.isConfigured()` + early throw of `GenerationFailure(NOT_CONFIGURED)` | §2.4 |
| Photo upload ≤ 20 MB | Spring multipart config (`application.yml`) | plan.md §Constraints |
| Photo reduced ≤ 4 MB AND ≤ 1536 px longest edge before Gemini call | `PhotoReducer.reduce()` | §2.3, research.md R4 |
| Reduced photo ≥ 1 byte and of supported mime | `PhotoPayload` constructor (already enforced) | 001 |
| Per-attempt Gemini timeout 25 s | `GeminiProperties.requestTimeoutMs` + `HttpRequest.timeout(...)` | §2.1 |
| RetryTemplate: 5 attempts, 200ms → 5s back-off, jitter | `RetryConfig` (unchanged) | Principle IV |

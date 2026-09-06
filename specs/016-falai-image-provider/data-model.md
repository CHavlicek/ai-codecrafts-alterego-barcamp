# Phase 1 — Data Model: fal.ai Image-Generation Provider

**Feature**: 016-falai-image-provider
**Date**: 2026-05-05

This document captures the domain types added or extended by this feature. Models that 003 / 014 / 008 / 015 already established are referenced but not redefined here.

---

## 1. `Provider` (new enum, wire-stable)

**Purpose**: Discriminates which path produced the response's central image. Surfaced on every `ResponseMeta` (FR-1612). Operator-visible only — frontend consumes but does not render (FR-1622).

```java
package com.aiavatar.alterego.model;

public enum Provider {
    GEMINI("gemini"),
    FALAI("falai"),
    STUB("stub");

    private final String wire;
    Provider(String wire) { this.wire = wire; }

    @JsonValue public String wire() { return wire; }

    @JsonCreator
    public static Provider fromWire(@JsonProperty String value) {
        for (Provider p : values()) if (p.wire.equals(value)) return p;
        throw new IllegalArgumentException("Unknown Provider: " + value);
    }
}
```

**Invariants**:

| Outcome | Allowed `Provider` values |
|---|---|
| `REAL` | `GEMINI`, `FALAI` |
| `FALLBACK` | `STUB` (only) |

The `ResponseMeta` compact constructor enforces these (R9).

**Wire values are stable from this feature's first merge**. Renaming any value is a breaking contract change.

**Frontend mirror** (`frontend/src/features/alterego/types.ts`):

```ts
export type Provider = 'gemini' | 'falai' | 'stub'
```

---

## 2. `ResponseMeta` (extension of 003)

**Delta**: gains a mandatory `Provider provider` field. `outcome`, `reason`, `correlationId` keep their 003 semantics unchanged.

**Java shape**:

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResponseMeta(
        Outcome outcome,
        UUID correlationId,
        FallbackReason reason,    // null on REAL; non-null on FALLBACK (003 invariant)
        Provider provider         // 016 — never null
) {
    public ResponseMeta {
        Objects.requireNonNull(provider, "provider is required");
        if (outcome == Outcome.REAL && reason != null)
            throw new IllegalArgumentException("reason must be null when outcome is REAL");
        if (outcome == Outcome.FALLBACK && reason == null)
            throw new IllegalArgumentException("reason is required when outcome is FALLBACK");
        if (outcome == Outcome.REAL && provider == Provider.STUB)
            throw new IllegalArgumentException("REAL outcome requires a real provider");
        if (outcome == Outcome.FALLBACK && provider != Provider.STUB)
            throw new IllegalArgumentException("FALLBACK outcome requires provider=STUB");
    }

    public static ResponseMeta real(UUID correlationId, Provider provider) {
        return new ResponseMeta(Outcome.REAL, correlationId, null, provider);
    }

    public static ResponseMeta fallback(UUID correlationId, FallbackReason reason) {
        return new ResponseMeta(Outcome.FALLBACK, correlationId, reason, Provider.STUB);
    }
}
```

The convenience factory for fallback always passes `Provider.STUB` because the FR-1612 invariant collapses fallback to stub.

**Frontend mirror**:

```ts
export interface ResponseMeta {
  outcome: Outcome              // 'real' | 'fallback'
  correlationId: string
  reason?: FallbackReason       // present iff outcome === 'fallback'
  provider: Provider            // 016 — always present
}
```

---

## 3. `ImageGenerator` interface (extension)

**Delta**: gains two methods. Default values preserve backwards compatibility for existing implementations.

```java
package com.aiavatar.alterego.service;

public interface ImageGenerator {
    PosterImage generate(GeneratedCharacter character, AlterEgoRequest request, PhotoPayload photo);

    /** Wire value for {@link Provider}. One of "gemini" / "falai" / "stub". */
    String providerName();

    /** Whether {@code AlterEgoService} should wrap calls in the orchestrator's
     *  multi-attempt {@code RetryTemplate}. Defaults to true (Gemini-style:
     *  per-attempt timeout, 5 retries). Returning false means the generator
     *  manages its own resilience internally (fal.ai-style: queue+subscribe
     *  with an end-to-end deadline). */
    default boolean wantsExternalRetry() { return true; }
}
```

**Implementations**:

| Class | `providerName()` | `wantsExternalRetry()` |
|---|---|---|
| `StubImageGenerator` (`@Profile("default")`) | `"stub"` | `true` (default — orchestrator retry is harmless on a deterministic stub) |
| `GeminiImageGenerator` (`@Profile("gemini")`) | `"gemini"` | `true` (default — preserves 003 retry semantics) |
| `FalAiImageGenerator` (`@Profile("falai")`) | `"falai"` | **`false`** (deadline-aware internal logic; orchestrator must not double-retry) |

**Why the default is `true`**: existing 003 / 014 tests exercise `RetryTemplate` semantics on the Gemini path. Forcing every implementation to opt in would touch more files for no gain.

---

## 4. `FalAiProperties` (new `@ConfigurationProperties`)

**Purpose**: Typed binding for the `aiavatar.falai.*` block in `application.yml` (R6).

```java
package com.aiavatar.alterego.config;

@ConfigurationProperties(prefix = "aiavatar.falai")
public record FalAiProperties(
        String apiKey,                       // FAL_AI_API_KEY; blank → NOT_CONFIGURED short-circuit
        String modelId,                      // FAL_AI_MODEL_ID; default "fal-ai/nano-banana-pro/edit"
        String endpointUrl,                  // FAL_AI_ENDPOINT_URL; default "https://queue.fal.run"
        int submitTimeoutMs,                 // per-attempt timeout for submit POST
        int pollTimeoutMs,                   // per-attempt timeout for status GET
        int fetchTimeoutMs,                  // per-attempt timeout for result GET / image-bytes GET
        int endToEndTimeoutMs,               // FR-1614a — wall-clock cap across submit + poll + fetch
        int pollInitialIntervalMs,           // initial poll back-off (default 1000)
        int pollMaxIntervalMs,               // poll back-off ceiling (default 5000)
        int maxInputBytes,                   // PhotoReducer pass-through ceiling (bytes)
        int maxInputLongestEdge,             // PhotoReducer pass-through ceiling (px)
        int reducedTargetLongestEdge,        // PhotoReducer resize target (px)
        double reducedJpegQuality            // PhotoReducer JPEG quality 0.0–1.0
) {
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Adaptor to the provider-neutral PhotoReducer's config record. */
    public PhotoReductionConfig photoReductionConfig() {
        return new PhotoReductionConfig(maxInputBytes, maxInputLongestEdge,
                reducedTargetLongestEdge, reducedJpegQuality);
    }
}
```

**Validation**: `@Validated` + Jakarta Bean Validation `@Min(1)` on every numeric, `@DecimalMin("0.1") @DecimalMax("1.0")` on `reducedJpegQuality`. Misconfiguration trips Spring Boot startup with a clear error.

---

## 5. `PhotoReductionConfig` (new — extracted from 003)

**Purpose**: Provider-neutral parameter object replacing the implicit `GeminiProperties` dependency in today's `PhotoReducer` (R4).

```java
package com.aiavatar.alterego.service.photo;

public record PhotoReductionConfig(
        int maxBytes,                       // pass-through ceiling — encoded byte size
        int maxLongestEdge,                 // pass-through ceiling — pixels
        int reducedTargetLongestEdge,       // resize target — pixels
        double reducedJpegQuality           // 0.0–1.0
) {
    public PhotoReductionConfig {
        if (maxBytes < 1)                   throw new IllegalArgumentException("maxBytes < 1");
        if (maxLongestEdge < 1)             throw new IllegalArgumentException("maxLongestEdge < 1");
        if (reducedTargetLongestEdge < 1)   throw new IllegalArgumentException("reducedTargetLongestEdge < 1");
        if (reducedJpegQuality <= 0 || reducedJpegQuality > 1)
            throw new IllegalArgumentException("reducedJpegQuality out of (0, 1]");
        if (reducedTargetLongestEdge > maxLongestEdge)
            throw new IllegalArgumentException("reducedTargetLongestEdge MUST be <= maxLongestEdge");
    }
}
```

`GeminiProperties` gains a parallel `photoReductionConfig()` adaptor method so both providers construct their config the same way.

---

## 6. `FalAiClient` — internal types

These are internal to `service/falai/` and not part of the wire contract. They model fal.ai's queue API responses for Jackson parsing.

```java
record FalAiSubmitResponse(
    String requestId,            // request_id
    String statusUrl,            // status_url
    String responseUrl,          // response_url
    String status                // "IN_QUEUE" on initial submit
) {}

record FalAiStatusResponse(
    String status,               // "IN_QUEUE" | "IN_PROGRESS" | "COMPLETED" | "FAILED"
    Integer queuePosition,       // optional
    String detail                // optional — present on FAILED
) {}

record FalAiResultResponse(
    List<ImageRef> images,
    Long seed,                   // optional
    String prompt                // optional — echoed
) {
    record ImageRef(String url, Integer width, Integer height, String contentType) {}
}
```

All three are package-private records under `com.aiavatar.alterego.service.falai`. Jackson maps via `@JsonProperty` on each accessor (snake_case on the wire, camelCase in Java).

**Lifetime**: in-memory for the duration of one Generate request (FR-1618).

---

## 7. `ProviderProfileGuard` — startup invariant

Not a domain entity per se but a configuration-time invariant that affects observable behaviour (FR-1605):

```java
@Configuration
public class ProviderProfileGuard {
    @PostConstruct
    void guard() {
        Set<String> active = Set.of(env.getActiveProfiles());
        if (active.contains("gemini") && active.contains("falai")) {
            // log fatal, throw IllegalStateException → context init fails → exit non-zero
        }
    }
}
```

State transitions: applies once at startup. No runtime state.

---

## 8. State transitions — extended sequence

The Generate request flow under each profile:

```
default profile (no real provider configured):
  [POST /api/v1/alter-egos]
    → AlterEgoService.generate()
    → CharacterGenerator.generate()    (stub)        ── via RetryTemplate
    → StubImageGenerator.generate()                  ── wantsExternalRetry=true → via RetryTemplate (deterministic)
    → PosterFrameOverlayService.apply()              ── 015 frame
    → emit log: outcome=fallback, provider=stub, attemptedProvider=none, reason=not_configured
    → return ResponseMeta.fallback(correlationId, NOT_CONFIGURED)

gemini profile + valid GEMINI_API_KEY:
  [POST /api/v1/alter-egos]
    → AlterEgoService.generate()
    → GeminiCharacterGenerator.generate()            ── via RetryTemplate (014)
    → GeminiImageGenerator.generate()                ── wantsExternalRetry=true → via RetryTemplate
        ├─ PhotoReducer.reduce(photo, geminiConfig)
        ├─ GeminiPromptBuilder.build(request)
        └─ GeminiClient.generateImage(modelId, prompt, photo)
    → PosterFrameOverlayService.apply()
    → emit log: outcome=real, provider=gemini, attemptedProvider=gemini
    → return ResponseMeta.real(correlationId, GEMINI)

falai profile + valid FAL_AI_API_KEY:
  [POST /api/v1/alter-egos]
    → AlterEgoService.generate()
    → GeminiCharacterGenerator.generate()            ── via RetryTemplate (014, unchanged — text still via Gemini)
                                                        OR StubCharacterGenerator if Gemini text is also not configured —
                                                        text-side configuration is independent of image-side
    → FalAiImageGenerator.generate()                 ── wantsExternalRetry=false → bypasses RetryTemplate
        ├─ PhotoReducer.reduce(photo, falaiConfig)
        ├─ FalAiPromptBuilder.build(request)
        ├─ deadline = now + endToEndTimeoutMs
        └─ FalAiClient.generateImage(props, prompt, photo, deadline)
            ├─ submit (≤3 in-budget retries)
            ├─ poll loop until COMPLETED|FAILED|deadline
            └─ fetch result + image bytes (≤2 in-budget retries)
    → PosterFrameOverlayService.apply()
    → emit log: outcome=real, provider=falai, attemptedProvider=falai
    → return ResponseMeta.real(correlationId, FALAI)

falai profile, key blank OR network failure:
  [POST /api/v1/alter-egos]
    → AlterEgoService.generate()
    → FalAiImageGenerator.generate() throws GenerationFailure(NOT_CONFIGURED|TIMEOUT|...)
    → caught → fallback path:
       ├─ FallbackPosterProvider.character + .poster
       ├─ PosterFrameOverlayService.apply
       └─ emit log: outcome=fallback, provider=stub, attemptedProvider=falai, reason=<...>
    → return ResponseMeta.fallback(correlationId, reason)

dual-profile activation (gemini AND falai):
  [Spring Boot startup]
    → ProviderProfileGuard.@PostConstruct fires
    → log: event=startup.fatal reason=ambiguous_provider profiles=[gemini, falai]
    → throws IllegalStateException
    → Spring exits non-zero. No HTTP server starts.
```

---

## 9. Note on character generation under the `falai` profile

Per FR-1622 / spec Assumption: this feature does NOT introduce a fal.ai text generator. Character text continues to come from `GeminiCharacterGenerator` (014) when configured, falling back to `StubCharacterGenerator` (001) otherwise. The text-side and image-side configurations are independent: an operator can run

- `SPRING_PROFILES_ACTIVE=falai` + `GEMINI_API_KEY=<key>` + `FAL_AI_API_KEY=<key>` — text via Gemini, image via fal.ai.
- `SPRING_PROFILES_ACTIVE=falai` + `FAL_AI_API_KEY=<key>` (no Gemini key) — text via stub, image via fal.ai.
- `SPRING_PROFILES_ACTIVE=falai` + neither key — text via stub, image via stub fallback (`NOT_CONFIGURED`).

The `provider` discriminator in `ResponseMeta` describes the **image** path (the central poster image is what the user looks at). Text-side provenance is not surfaced — the operator can read the existing 014 log line for that.

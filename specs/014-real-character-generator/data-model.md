# Phase 1 — Data Model: Real (LLM-backed) Character Generator

**Feature**: 014-real-character-generator
**Date**: 2026-04-27

This document captures the data-shape changes introduced by 014 — backend-internal service types, configuration extensions, and the seam-level wiring change. Entities already defined by 001/002/003/006 (e.g. `AlterEgoRequest`, `GeneratedCharacter`, `AlterEgoResponse`, `Pose`, `Archetype`, `Universe`, `Vibe`, `ArtStyle`, `PhotoMode`, `FallbackReason`, `GenerationFailure`) are **unchanged** unless explicitly listed below.

No persistence is introduced — every type below is in-memory-only for the duration of a single HTTP request (FR-1412). No caching of any form (FR-1420).

---

## 1. Contract-visible changes

**None.** This feature does not change any wire shape. `AlterEgoResponse`, `AlterEgoResponse.Outcome`, `AlterEgoResponse.ResponseMeta`, `FallbackReason`, `Selections`, and `GeneratedCharacter` all retain the shapes defined by 001/002/003/006.

The 003 OpenAPI document (`specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml`) remains the authoritative wire contract. See `contracts/wire-stability.md` in this feature for the explicit "no change" record.

---

## 2. Backend-internal types (not on the wire)

### 2.1 `GeminiProperties` — extension

Existing shape (003):

```java
@ConfigurationProperties(prefix = "aiavatar.gemini")
public record GeminiProperties(
        String apiKey,
        String modelId,
        String endpointUrl,
        int requestTimeoutMs,
        int maxInputBytes,
        int maxInputLongestEdge,
        int reducedTargetLongestEdge,
        double reducedJpegQuality
) {
    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
}
```

New shape (014) — two appended fields, all existing fields and the `isConfigured()` accessor unchanged:

```java
@ConfigurationProperties(prefix = "aiavatar.gemini")
public record GeminiProperties(
        // shared (image + text)
        String apiKey,                        // env: GEMINI_API_KEY; blank → NOT_CONFIGURED on both paths
        // image-side (003)
        String modelId,                       // env: GEMINI_MODEL_ID; default: "gemini-3.1-flash-image-preview"
        String endpointUrl,                   // env: GEMINI_ENDPOINT_URL; default: "https://generativelanguage.googleapis.com/v1beta"
        int requestTimeoutMs,                 // env: GEMINI_REQUEST_TIMEOUT_MS; default: 25000
        int maxInputBytes,                    // env: GEMINI_MAX_INPUT_BYTES; default: 4_194_304 (4 MB)
        int maxInputLongestEdge,              // env: GEMINI_MAX_INPUT_LONGEST_EDGE; default: 1536 (px)
        int reducedTargetLongestEdge,         // env: GEMINI_REDUCED_TARGET_EDGE; default: 1024 (px)
        double reducedJpegQuality,            // env: GEMINI_REDUCED_JPEG_QUALITY; default: 0.85
        // text-side (014, NEW)
        String textModelId,                   // env: GEMINI_TEXT_MODEL_ID; default: "gemini-2.5-flash"
        int textRequestTimeoutMs              // env: GEMINI_TEXT_REQUEST_TIMEOUT_MS; default: 15000  (FR-1417, clarification Q3)
) {
    public boolean isConfigured() { return apiKey != null && !apiKey.isBlank(); }
}
```

Used by: existing `GeminiImageGenerator` / `PhotoReducer` / `GeminiClient` (image-side fields) plus new `GeminiCharacterGenerator` / `GeminiCharacterClient` (text-side fields and the shared `apiKey`).

The shared `apiKey` is the single env var (`GEMINI_API_KEY`) that gates both paths — FR-1414 / spec US-5 acceptance scenario 1.

### 2.2 `StubCharacterGenerator` — profile narrowing (edit only)

Existing annotation (003+):

```java
@Component
@Profile({"default", "gemini"})
public class StubCharacterGenerator implements CharacterGenerator { /* … */ }
```

New annotation (014):

```java
@Component
@Profile("default")
public class StubCharacterGenerator implements CharacterGenerator { /* … */ }
```

**The class body is unchanged.** Only the profile metadata changes — the SHA-256-driven deterministic variant pick, the fixture loader, and the `vibe`-prepending behaviour are byte-identical to today. `StubCharacterGeneratorTest` continues to pass without modification (FR-1416).

Rationale: research §R7 (symmetry with image side; `@Primary` on the new bean is the tiebreak for any test that activates both profiles).

### 2.3 `GeminiCharacterGenerator` — new

```java
package com.aiavatar.alterego.service.gemini;

@Component
@Profile("gemini")
@Primary
public class GeminiCharacterGenerator implements CharacterGenerator {

    private final GeminiCharacterClient client;
    private final GeminiCharacterPromptBuilder promptBuilder;
    private final GeminiCharacterResponseParser parser;
    private final GeminiProperties props;

    public GeminiCharacterGenerator(GeminiCharacterClient client,
                                    GeminiCharacterPromptBuilder promptBuilder,
                                    GeminiCharacterResponseParser parser,
                                    GeminiProperties props) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.props = props;
    }

    @Override
    public GeneratedCharacter generate(AlterEgoRequest request) {
        AlterEgoRequest req = request.withTrimmedFirstName();
        if (!props.isConfigured()) {
            // Pre-HTTP short-circuit. Mirrors GeminiImageGenerator's posture
            // for FR-1403 and the symmetric image-side FR-212 from 003.
            throw new GenerationFailure(FallbackReason.NOT_CONFIGURED,
                    "GEMINI_API_KEY not configured");
        }
        String prompt = promptBuilder.build(req);
        JsonNode body = client.generateText(props.textModelId(), prompt);
        return parser.parse(body, req.firstName());
    }
}
```

Sits inside `retryTemplate.execute(...)` exactly like the existing `StubCharacterGenerator` does (`AlterEgoService` is unchanged). `@Primary` is a defensive tiebreak — a `@SpringBootTest` that activates both `default` and `gemini` profiles still resolves deterministically without changing its behaviour.

### 2.4 `GeminiCharacterPromptBuilder` — new

Stateless component, mirrors `GeminiPromptBuilder` (image side) in shape and conventions.

```java
@Component
public class GeminiCharacterPromptBuilder {

    private static final Map<Pose,      String> POSE_LABELS;
    private static final Map<Archetype, String> ROLE_LABELS;
    private static final Map<Universe,  String> UNIVERSE_LABELS;
    private static final Map<Vibe,      String> VIBE_LABELS;
    private static final Map<ArtStyle,  String> ART_STYLE_LABELS;
    static { /* same display labels as GeminiPromptBuilder — kept as a
                separate map so a future divergence in tone (the image
                wants visual cues, the text wants narrative cues) does
                not require re-coupling the two prompt builders */ }

    /**
     * Compose the structured-output text prompt. See research.md §R3 for
     * the exact template; firstName is interpolated as-is per FR-1419
     * (no escaping or sanitization beyond what 011 ValidFirstName already
     * enforces at the controller boundary).
     */
    public String build(AlterEgoRequest request) { /* template fill */ }
}
```

Unit-tested by asserting that every Setup field (pose, archetype, universe, art-style, optional vibe, firstName) contributes a distinct substring to the prompt; that the explicit ≤100-codepoint and exactly-3-superpowers rules and the English-only instruction are present; that an absent vibe omits the vibe line entirely; and that firstName is interpolated verbatim — no `\` escaping, no quote-fencing.

### 2.5 `GeminiCharacterClient` — new

Thin HTTP wrapper around the shared `java.net.http.HttpClient` bean (`HttpClientConfig`). Sibling to `GeminiClient` (image side); same exception-mapping discipline, retargeted at the text endpoint.

```java
@Component
public class GeminiCharacterClient {

    private final HttpClient httpClient;
    private final GeminiProperties props;
    private final ObjectMapper mapper;

    public GeminiCharacterClient(HttpClient httpClient,
                                 GeminiProperties props,
                                 ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * POST {endpointUrl}/models/{textModelId}:generateContent with a
     * structured-JSON generationConfig (see research.md §R3). On success,
     * extracts the `candidates[0].content.parts[*].text` field and returns
     * it as a parsed {@link JsonNode} (the embedded JSON, not the outer
     * Gemini envelope — the caller does not need to know the envelope
     * shape).
     *
     * @throws GenerationFailure on every provider-boundary failure, with a
     *         FallbackReason classified per research.md §R5.
     */
    public JsonNode generateText(String modelId, String prompt) { /* … */ }
}
```

The R5 mapping table is implemented in the same shape as `GeminiClient.classifyErrorStatus(int)` plus the `try/catch` around `httpClient.send(...)`. Per-attempt timeout is `props.textRequestTimeoutMs()` (default 15 000 ms — FR-1417). Auth header `x-goog-api-key: <apiKey>`.

### 2.6 `GeminiCharacterResponseParser` — new

Stateless component. Owns the FR-1406 / FR-1407 / FR-1408 contract (research §R4):

```java
@Component
public class GeminiCharacterResponseParser {

    private static final int MAX_TRAIT_CODEPOINTS = 100;

    /**
     * Materialise a JSON object (returned by GeminiCharacterClient.generateText)
     * into a GeneratedCharacter, enforcing FR-1406 and FR-1408.
     *
     * <p>{@code firstName} is the trimmed user-supplied first name; the
     * parser writes {@code firstName.toUpperCase(Locale.ROOT)} into the
     * resulting heroTitleLine1 regardless of what the JSON contained
     * (FR-1408 — name substitution is owned here, not by the LLM).
     *
     * @throws GenerationFailure {@link FallbackReason#MALFORMED_RESPONSE}
     *         if the JSON violates any of:
     *         (a) is not a JSON object,
     *         (b) lacks any required key (heroTitleLine2, tagline, superpowers, quote),
     *         (c) `superpowers` is not a 3-element array,
     *         (d) any trait, after NFC normalisation + trim, is blank or > 100 codepoints,
     *         (e) any string trait isn't a JSON string at all.
     */
    public GeneratedCharacter parse(JsonNode body, String firstName) { /* … */ }
}
```

Implementation notes:
- `Normalizer.normalize(s, Form.NFC).strip()` then `s.codePointCount(0, s.length())` for the length check — same canonical unit as 011 `ValidFirstName`.
- The optional English-only heuristic check is **not** in the v1 parser (research §R4: "best-effort, not required; gated on a future need"). The prompt instruction is the primary defence (FR-1418).
- Logging on violation: WARN with `kv("phase", "gemini-text-parse")`, `kv("reason", "malformed_response")`, `kv("violation", <short fixed label>)`. Never logs the JSON body.

### 2.7 Module map summary

```text
com.aiavatar.alterego
├── config/
│   └── GeminiProperties (edit)            # +textModelId, +textRequestTimeoutMs
├── service/
│   ├── stub/
│   │   └── StubCharacterGenerator (edit)  # @Profile narrows to "default"
│   └── gemini/
│       ├── GeminiCharacterGenerator (new)
│       ├── GeminiCharacterClient (new)
│       ├── GeminiCharacterPromptBuilder (new)
│       └── GeminiCharacterResponseParser (new)
```

No other module is touched. `AlterEgoService`, `CharacterGenerator` (interface), `GenerationFailure`, `FallbackReason`, `FallbackPosterProvider`, `RetryConfig`, `HttpClientConfig`, every `model/*` record — all unchanged.

---

## 3. Entity relationship diagram (updated)

```text
AlterEgoRequest  ──┐
                   │
PhotoPayload  ─────┼──► AlterEgoService.generate()
                   │       │
                   │       ├─► retryTemplate.execute( characterGenerator.generate(request) )
                   │       │         ├─► @Profile default:  StubCharacterGenerator      ──► returns GeneratedCharacter (fixture-driven)
                   │       │         ├─► @Profile gemini:   GeminiCharacterGenerator    ──► PromptBuilder ─► CharacterClient ─► Gemini text API
                   │       │         │                                                       │
                   │       │         │                                                       └─ ResponseParser ─► GeneratedCharacter
                   │       │         └─► @Profile force-stub-failure: ForceFailureCharacterGenerator ──► throws StubGenerationException
                   │       │                                                                                  (orchestrator translates to fallback)
                   │       │
                   │       ├─► retryTemplate.execute( imageGenerator.generate(character, request, photo) )
                   │       │         ├─► @Profile default:  StubImageGenerator           ──► PosterImage (procedural)
                   │       │         ├─► @Profile gemini:   GeminiImageGenerator         ──► (003 chain) ─► PosterImage
                   │       │         └─► @Profile force-stub-failure: ForceFailureImageGenerator ──► throws
                   │       │
                   │       └─► (on any GenerationFailure) ─► FallbackPosterProvider ─► AlterEgoResponse with meta.outcome=fallback,
                   │                                                                            meta.reason=<typed>
                   │
                   └─► returns AlterEgoResponse { character, poster, meta }
```

Legend:
- **`@Profile gemini` for character is new in 014.** Under that profile, the only `CharacterGenerator` candidate is `GeminiCharacterGenerator` (research §R7).
- The orchestrator order, the per-call retry, and the fallback discriminator are exactly as 003/006/007 left them.

---

## 4. State transitions

**No state-machine change.** The frontend session reducer (`idle → picking → generating → { succeeded | failed_with_fallback }`) is unchanged. `useGenerateAlterEgo` continues to read `response.meta.outcome === 'real'` to decide between `succeeded` and `failed_with_fallback`. `response.meta.reason` is not consumed by the frontend.

The backend `AlterEgoService` orchestration is unchanged. The character generator runs first (sequential — research §R6); on its failure, the image generator is not invoked, exactly as today.

---

## 5. Validation rules summary

| Rule | Enforced by | Source |
|---|---|---|
| `GeminiProperties.apiKey` non-blank gates the real character path | `GeminiProperties.isConfigured()` + early throw of `GenerationFailure(NOT_CONFIGURED)` in `GeminiCharacterGenerator` | §2.3 |
| Per-attempt text-call timeout 15 s (env-overridable) | `GeminiProperties.textRequestTimeoutMs` + `HttpRequest.timeout(...)` in `GeminiCharacterClient` | §2.1, FR-1417 |
| Trait shape: 4 traits present, exactly 3 superpowers | `GeminiCharacterResponseParser.parse(...)` | §2.6, FR-1406 |
| Each trait NFC-normalised, trimmed, non-blank, ≤ 100 Unicode code points | `GeminiCharacterResponseParser.parse(...)` | §2.6, FR-1406 + clarification Q1 |
| `heroTitleLine1` = `firstName.trim().toUpperCase(Locale.ROOT)` (LLM's value, if any, discarded) | `GeminiCharacterResponseParser.parse(...)` | §2.6, FR-1408 |
| Output language: English (primary defence: prompt; secondary: parser may add heuristic later) | `GeminiCharacterPromptBuilder.build(...)` instruction; parser hook reserved | §2.4, FR-1418 + clarification Q2 |
| `firstName` interpolated as-is into the prompt; no character-generator-local sanitization | `GeminiCharacterPromptBuilder.build(...)` | §2.4, FR-1419 + clarification Q4 |
| No caching / memoization of LLM responses | `GeminiCharacterGenerator` is stateless beyond its injected dependencies; no in-process cache, no LRU | §2.3, FR-1420 + clarification Q5 |
| RetryTemplate: 5 attempts, 200 ms → 5 s back-off, jitter | `RetryConfig` (unchanged from 003) | Principle IV |
| Logs MUST NOT carry firstName, prompt body, or LLM response body | `GeminiCharacterClient` / `…ResponseParser` log surface confined to fixed structured args | research §R11, FR-1413 |
| All R5 failure-mapping rows route through `FallbackReason` | `GeminiCharacterClient.classifyErrorStatus(...)` + try/catch table | §2.5, research §R5 |
| Pre-existing tests (`StubCharacterGeneratorTest`, OpenAPI contract test, `RecordInvariantsTest`) continue to pass without modification | The class bodies they test (StubCharacterGenerator class body, GeneratedCharacter record, AlterEgoResponse) are byte-unchanged | FR-1416 |

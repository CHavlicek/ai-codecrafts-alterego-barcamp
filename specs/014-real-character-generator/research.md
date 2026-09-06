# Phase 0 — Research: Real (LLM-backed) Character Generator

**Feature**: 014-real-character-generator
**Date**: 2026-04-27
**Status**: All items resolved. No `NEEDS CLARIFICATION` remain.

This document resolves the open planning questions raised by `spec.md` and `plan.md`: concrete numbers the spec deferred, prompt + response-format choice, parser strategy, exception-mapping table, profile wiring, the sequential-vs-parallel orchestration question (the one item the spec deliberately handed to planning), and the no-OpenAPI-bump argument. Each item follows the **Decision / Rationale / Alternatives** format.

---

## R1. Gemini text model identifier — default value and configurability

**Decision**: Default model id is **`gemini-2.5-flash`** — the GA text-generation model with structured-JSON output support, broad availability on `generativelanguage.googleapis.com/v1beta`, and a typical end-to-end latency of 2–5 seconds for the prompt size this feature builds. Configurable via `aiavatar.gemini.text-model-id` (bound from env var `GEMINI_TEXT_MODEL_ID`). The image-side `aiavatar.gemini.model-id` (default `gemini-3.1-flash-image-preview`, set by 003) is **unchanged** — text and image have independent model overrides per FR-1414 / spec US-5 acceptance scenario 2.

**Rationale**:

- `gemini-2.5-flash` is GA (no `-preview` tax in availability or stability), supports the `responseMimeType: "application/json"` + `responseSchema` knobs we need for structured output (R3), and is priced significantly below the pro family for a cost-cheap POC.
- A flash-tier model is sufficient: the task is short structured generation, not multi-paragraph reasoning. Pro-tier models would burn budget on a workload that does not benefit from the extra capacity.
- Independent override (`GEMINI_TEXT_MODEL_ID` vs `GEMINI_MODEL_ID`) preserves the operator's ability to flip text models without touching image, exactly what spec US-5 acceptance scenario 2 requires.

**Alternatives considered**:

- `gemini-2.5-pro` (text): higher quality, ~3× the cost, ~2× slower. Worth revisiting if SC-1407 (cross-combination trait pool) plateaus.
- `gemini-3-pro-preview` (text): preview-tier, less stable for a feature that needs to ship today.
- A non-Gemini provider (Anthropic, OpenAI): would defeat the explicit issue ask ("Let's use Gemini for now, since we already use it for the image generation logic and can hopefully reuse the KEY"). Pragmatically, also doubles dependency surface and operator burden.

---

## R2. Text-call client — reuse `GeminiClient` or new `GeminiCharacterClient`?

**Decision**: **New `GeminiCharacterClient`**, sibling to the existing `GeminiClient`. Both wrap `java.net.http.HttpClient`, both call `POST {endpoint}/models/{modelId}:generateContent`, both apply the same R5-style failure-mapping table. The duplication is small (~120 lines per client) and pays for itself by keeping each client's `parseSuccessBody` shape narrow: the image-side parser walks `candidates[0].content.parts[*].inline_data` for an image part; the text-side parser walks `candidates[0].content.parts[*].text` for the structured-JSON response, then hands the raw JSON to `GeminiCharacterResponseParser`.

**Rationale**:

- The image-side `GeminiClient.parseSuccessBody` is already complex enough (safety-refusal detection, finishReason guard, mime normalisation, image decoding). Splicing a second parsing branch in for the text path would couple two unrelated success shapes through a single conditional method — the kind of "tiny, future-self-cost" abstraction the project's house style explicitly avoids.
- Both clients share one `HttpClient` bean (already provided by `HttpClientConfig`). They share the same `GeminiProperties` record. They share `FallbackReason` and `GenerationFailure`. All the heavy plumbing is reused; only the per-endpoint serialisation/parsing differs.
- A reviewer who knows 003's `GeminiClient` already understands `GeminiCharacterClient` line-for-line. The cognitive cost is ~zero.

**Alternatives considered**:

- **One `GeminiClient`, two methods (`generateImage(...)`, `generateText(...)`)**: forces the class to know both response shapes; the per-endpoint exception-mapping table would have to be parameterised. Couples future text-side changes to image-side regression risk. Rejected.
- **Generic `GeminiCallExecutor<TRequest, TResponse>` + two handlers**: over-abstracted for two call sites. The request-body shape and the response-body shape both differ, so the only thing left to abstract is the HTTP scaffolding — and that's already in `HttpClient`. Rejected (would be "premature abstraction" per the house style).
- **Spring `RestClient` / `WebClient`**: same argument 003 R2 made. No new dependency wins. Rejected.

---

## R3. Prompt + response shape — structured-JSON via Gemini's `responseSchema`

**Decision**: The text call uses Gemini's structured-output mode by setting `generationConfig.responseMimeType = "application/json"` and `generationConfig.responseSchema = <JSON-Schema describing exactly the four-trait shape>`. The model's response body is therefore a JSON object that matches the schema or — if Gemini cannot satisfy it — a typed failure surfaces as a non-2xx status or a malformed body. The parser hands the parsed JSON straight to record construction; no heuristic prose extraction.

**Request body (text endpoint, illustrative)**:

```json
{
  "contents": [
    {
      "role": "user",
      "parts": [
        { "text": "<composed text prompt — see template below>" }
      ]
    }
  ],
  "generationConfig": {
    "responseMimeType": "application/json",
    "responseSchema": {
      "type": "object",
      "required": ["heroTitleLine2", "tagline", "superpowers", "quote"],
      "additionalProperties": false,
      "properties": {
        "heroTitleLine2": { "type": "string", "minLength": 1, "maxLength": 100 },
        "tagline":        { "type": "string", "minLength": 1, "maxLength": 100 },
        "superpowers": {
          "type": "array", "minItems": 3, "maxItems": 3,
          "items": { "type": "string", "minLength": 1, "maxLength": 100 }
        },
        "quote":          { "type": "string", "minLength": 1, "maxLength": 100 }
      }
    },
    "candidateCount": 1,
    "temperature": 0.9
  }
}
```

**Prompt template** (filled by `GeminiCharacterPromptBuilder`, English-only, line breaks preserved):

```text
You are writing the captions for a fictional "alter ego" trading-card poster.
Respond with ONE JSON object that exactly matches the response schema. Do not
include any prose outside the JSON object. Write every string in ENGLISH only.

The poster is for one person. Their attributes:

- First name (used for tone, NOT to repeat verbatim in line 2): <firstName>
- Engineering role: <role-display-label>
- Fictional universe / aesthetic: <universe-display-label>
- Pose / stance: <pose-display-label>
- Vibe / tone: <vibe-display-label-or-omitted>
- Art style of the poster: <art-style-display-label>

Trait constraints (each MUST be satisfied):
- heroTitleLine2: a single short hero-style title line (e.g. "The Cloud Guardrail",
  "The Pixel Diplomat"). MUST start with "The ". MUST NOT contain the first name.
  ≤ 100 characters, ≥ 1 character.
- tagline: a single short punchy line, typically 4–8 words, often UPPERCASE.
  ≤ 100 characters, ≥ 1 character.
- superpowers: EXACTLY THREE short superpower descriptions, each one short
  sentence or noun phrase. Each entry ≤ 100 characters, ≥ 1 character.
- quote: a short dev-flavoured quote, ideally ≤ 12 words. ≤ 100 characters,
  ≥ 1 character.

Voice: dry, knowing, slightly self-deprecating engineering humour. Reference
the chosen universe and vibe naturally — do not just name them.

Output: only the JSON object. No code fences, no commentary, no Markdown.
```

**Display-label maps** are reused from `GeminiPromptBuilder` for consistency: `Cloud Architect`, `Star Wars`, `heroic, chest forward`, etc. The `art-style-display-label` map is the same one 006/008 already pinned (e.g. `oil painting, visible brushstrokes and impasto texture`).

**Rationale**:

- Gemini's structured-output mode (added to `gemini-2.5-flash` in 2024-12) is the boring-correct choice: the model is *told* to emit JSON matching the schema, the API enforces that, and the parser only has to deserialise — no heuristic JSON-extraction from a Markdown code fence, no "the model started with 'Sure, here you go:'" quirk to defeat. This directly satisfies FR-1406's "the parser MUST validate against the four-trait shape *without* heuristic prose extraction" requirement in the spec's Assumptions.
- `temperature: 0.9` gives the LLM enough latitude to satisfy SC-1407 (cross-combination trait pool > 24) while the schema clamps it from going off-format. Lower temperature flattens the distribution and risks regenerating the same handful of clichés; higher invites schema violations.
- The English-only instruction in the prompt is the primary defence for FR-1418. The parser MAY add a heuristic English-detection check, but is not required to — the spec explicitly accepts that the prompt instruction is the primary defence. Empirically `gemini-2.5-flash` honours `respond in English` reliably; this is consistent with how the image prompt's `Composition notes: ... no overlaid text` is honoured.
- "MUST start with 'The '" matches the existing fixture pattern (every line-2 in `stubs/characters.json` starts with `The `). Preserves visual continuity between the real and the fallback poster.
- "MUST NOT contain the first name in line 2" addresses an edge case identified in the spec (`heroTitleLine2` is the *role descriptor*, not a re-statement of `firstName` — line 1 already carries the name). The parser does not enforce this beyond the prompt's instruction, since rejecting otherwise-valid responses for a stylistic reason would be over-strict.

**Alternatives considered**:

- **Free-form prose response, parser does heuristic JSON extraction**: brittle, fights every model release. Rejected — the spec explicitly forbids heuristic prose extraction (Assumption: "the planning MUST land on a shape that the response parser can validate against FR-1406 *without* heuristic prose extraction").
- **Function-calling / tool-use mode**: would also produce structured output, but overkill for a single object with no side-effects, and adds a `tools[]` declaration to the request. Rejected — `responseSchema` is the simpler half of the same feature.
- **Multi-turn / chain-of-thought**: unnecessary for one short structured object. Adds latency, blows the 15 s budget. Rejected.
- **Per-trait separate calls** (e.g. one call for tagline, another for quote): 4× the latency, 4× the cost. Rejected.

---

## R4. Response parser — what counts as "well-formed"

**Decision**: `GeminiCharacterResponseParser` enforces these checks on the JSON body returned by `GeminiCharacterClient.generateText(...)`:

1. The response body is a JSON object (not an array, not a primitive).
2. Required keys present: `heroTitleLine2`, `tagline`, `superpowers`, `quote`. Extras are tolerated (Gemini sometimes adds `_thinking` or similar — they're ignored).
3. `superpowers` is a JSON array of length **exactly 3** (research §R3 makes the schema enforce this, but the parser double-checks because schemas can be relaxed in error paths).
4. `heroTitleLine2`, `tagline`, every `superpowers[i]`, `quote`: each value is `String`-typed, **NFC-normalised, trimmed**, then required to be **non-blank** AND **≤ 100 Unicode code points** in length (clarification Q1). Both bounds are inclusive of the code-point count, exclusive of leading/trailing whitespace.
5. The final `GeneratedCharacter` is constructed with `heroTitleLine1 = firstName.trim().toUpperCase(Locale.ROOT)` regardless of whether the LLM's JSON contained a `heroTitleLine1` (it shouldn't; the schema doesn't ask for one). FR-1408 is satisfied at this layer, not earlier.

Any check failure → `GenerationFailure(MALFORMED_RESPONSE, "<short reason>", null)`. The orchestrator's existing fallback path runs.

**English-only enforcement**: parser-level enforcement is **best-effort, not required** — research §R3 already noted the prompt is the primary defence. The parser MAY (Phase 2 task evaluates whether to ship this) include a cheap heuristic check (e.g. all-ASCII-letters-and-common-punctuation match for ≥ 95% of characters); when it fires it raises `MALFORMED_RESPONSE`. This stays cheap and avoids brittle false positives on legitimate ASCII names containing diacritics that the LLM correctly preserved (e.g. "François"). If empirical drift later shows non-English answers slipping through, this knob can be turned up without a contract change.

**Rationale**:

- Code-point counting matches the project's canonical unit (clarification Q1) and is what Java's `String.codePointCount(0, length)` gives for free.
- NFC normalisation before counting/comparison guards against the case where Gemini emits a combining-mark sequence (e.g. `e + combining acute`) that the user reasonably perceives as 1 character but `String.length()` counts as 2. The 011 firstName validator already does this; matching that posture keeps the codebase's "what counts as a character" rule single-sourced.
- "Trim before measure" is the same rule the spec restated in FR-1406. A response like `" The Rebel Architect  "` is treated as a 19-codepoint trait, not 23.
- Tolerating extra keys is liberal-in-what-you-accept defence against future Gemini schema additions (`_metadata`, `_safetyAttributes`, etc.). The parser only fails when *required* keys are missing, mistyped, or out-of-bounds.
- Owning the `firstName.toUpperCase` substitution at parse time (not earlier) means the parser is the single place that produces a final `GeneratedCharacter`. No risk that the LLM's `heroTitleLine1` (if any) leaks through.

**Alternatives considered**:

- **Trust the schema entirely, no Java-side recheck**: schema enforcement at the API can be lenient on edge cases (Gemini has been observed returning 4-element arrays for `minItems: 3, maxItems: 3` schemas under unusual prompts). The double-check is ~10 lines; cost-effective.
- **Reject extra keys**: tightens the contract for no user benefit and breaks the first time Google adds a new diagnostic field.
- **Strict English regex**: false positives on Continental-European names. Rejected — the heuristic is gated on a future need.

---

## R5. Failure-mapping table — text endpoint

**Decision**: The text-side `GeminiCharacterClient` mirrors 003 R5's image-side mapping table verbatim, with the obvious response-shape substitutions. No new `FallbackReason` codes (FR-1410). Exhaustive table:

| Condition | `FallbackReason` |
|---|---|
| `GeminiProperties.apiKey` blank at call time — checked up-front in `GeminiCharacterGenerator`, no outbound call | `NOT_CONFIGURED` |
| `java.net.http.HttpTimeoutException` OR HTTP `504 Gateway Timeout` | `TIMEOUT` |
| HTTP `429 Too Many Requests` OR `RESOURCE_EXHAUSTED` code in response body | `RATE_LIMITED` |
| `java.net.ConnectException`, `UnknownHostException`, `SSLException`, other `IOException` (excl. `HttpTimeoutException`), or HTTP 5xx other than 504 | `NETWORK_ERROR` |
| HTTP 2xx but body doesn't parse as JSON, lacks `candidates[0].content.parts[*].text`, or the embedded text isn't itself valid JSON | `MALFORMED_RESPONSE` |
| HTTP 2xx + `promptFeedback.blockReason` present, OR `candidates[0].finishReason ∈ {SAFETY, RECITATION}` | `SAFETY_REFUSED` |
| HTTP 4xx other than 429 (400, 401, 403, 404) | `MALFORMED_RESPONSE` (mirrors 003 R5: our request was wrong) |
| FR-1406 trait-shape / length / non-blank check fails inside `GeminiCharacterResponseParser` | `MALFORMED_RESPONSE` |

`RetryTemplate` retries every `GenerationFailure` (RuntimeException) under the default policy; on final exhaustion `AlterEgoService`'s outer `catch (GenerationFailure)` block reads `reason()` and populates `meta.reason`.

**Rationale**:

- Mirroring 003's table verbatim is the cheapest correct choice: operators reading logs see consistent reason codes regardless of which provider call failed; the test matrix (R6 below) is structurally identical to the image-side matrix.
- `RECITATION` is a Gemini-specific finishReason for "the model was about to reproduce copyrighted text" — semantically the closest existing code is `SAFETY_REFUSED`. No spec amendment needed (FR-1410 explicitly chooses the closest existing reason).
- Retry on `NOT_CONFIGURED` and `SAFETY_REFUSED` is wasteful but harmless (deterministic). Adding bespoke no-retry logic would expand the retry-policy surface for no user-observable gain. Same posture 003 took.
- The parser-level malformed cases (FR-1406 violations) join the same MALFORMED_RESPONSE bucket as wire-level malformed cases. Operators see one symptom — "Gemini's text response wasn't usable" — without having to distinguish "JSON didn't parse" from "JSON parsed but had four superpowers". Both are a Gemini quality issue, neither is recoverable in-flight.

**Alternatives considered**:

- Distinct `INVALID_TRAITS` code for FR-1406 violations: would require a spec amendment (FR-1410 forbids enum extension). Rejected.
- Treat parser-level malformed as `SAFETY_REFUSED`: misclassifies a quality issue as a moderation issue. Rejected.

---

## R6. Sequential vs parallel issuance of character + image calls

**Decision**: **Sequential — character first, then image — exactly as today.** The pipeline in `AlterEgoService.generate(...)` stays:

```text
character = retryTemplate.execute(characterGenerator.generate(request));
poster    = brandingOverlay.apply(retryTemplate.execute(imageGenerator.generate(character, request, photo)));
```

No code change to the orchestrator. The new character generator simply starts taking ~3 s on the happy path under the `gemini` profile.

**Rationale**:

- **Latency budget already permits it.** SC-1409 explicitly allows up to 15 s of regression — exactly the character-call timeout from FR-1417. A sequential pipeline therefore fits the budget without a single line of orchestration change. Parallel orchestration would buy back ~3 s on the median happy path but at the cost of a meaningful complexity bump (see below).
- **Parallel orchestration is non-trivial.** The image generator's signature today is `generate(GeneratedCharacter, AlterEgoRequest, PhotoPayload)` — i.e. it consumes the character. Today the character is just used to position name/tagline visually-irrelevant to Gemini's image prompt, but `BrandingOverlayService` and the image prompt builder both reference it. To run the calls in parallel, either (a) the image generator's signature changes to drop the character (and BrandingOverlay re-acquires it later — a refactor that touches the seam), or (b) we add a `CompletableFuture.allOf(...)` orchestration with separate retry templates per call, which expands the failure-handling surface (one call retried out, one call still in flight, partial-failure semantics).
- **Failure semantics get noisier in parallel.** Today, on a `NOT_CONFIGURED` short-circuit at the character stage, the image stage isn't called — saving a round-trip and avoiding a logged image-side outcome that would just be discarded. In parallel both stages start; the image call would fire for a request that's already going to fall back. SC-1408 ("operator can flip stub-vs-real with a single profile + env-var change — no code change") gets harder to reason about.
- **Constitutional fit.** Principle IV's `RetryTemplate` is a per-call scaffolding; the existing usage wraps each generator independently. Parallel orchestration would require either two separate executor-pool calls (each managing its own retries) or a custom retry-aware future-join, both of which expand the retry surface against Principle IV's "exactly one wrapping" pattern.
- **House style.** "Don't add features, refactor, or introduce abstractions beyond what the task requires." Parallel issuance is a future-perf optimisation; the spec already pinned the budget that doesn't need it.

**Alternatives considered**:

- **Parallel via `CompletableFuture` on a fixed-size executor**: saves ~3 s p50; adds a thread pool, partial-failure semantics, and a retry-template double-execution risk. Rejected — clear cost, opaque benefit (within the SC-1409 budget).
- **Parallel via Spring `@Async`**: same cost as above + an extra Spring config surface (`@EnableAsync`, executor bean). Rejected.
- **Image-first then character (so the image starts on the larger payload sooner)**: image generator's signature requires the character. Reversing the order is more work than parallelising. Rejected.
- **Image-side call streams the response while character-call runs**: not a thing the Gemini image API supports. Rejected.

**Future hook**: if SC-1409's measured latency budget proves too generous in production (e.g. 95th-percentile total latency creeps up to user-noticeable territory), a follow-up feature can introduce parallel orchestration as a bounded-scope refactor. The character generator's seam contract does not need to change for that — it remains a pure `(AlterEgoRequest) → GeneratedCharacter` function.

---

## R7. Profile narrowing of `StubCharacterGenerator`

**Decision**: Narrow `@Profile({"default", "gemini"})` → `@Profile("default")` on `StubCharacterGenerator`. Add `@Primary` to the new `GeminiCharacterGenerator @Profile("gemini")` so any test that explicitly activates both profiles (`@ActiveProfiles({"default", "gemini"})` — currently no such test) resolves deterministically without changing its behaviour vs. today.

**Rationale**:

- **Symmetry with the image side.** Today: `StubImageGenerator @Profile("default")`, `GeminiImageGenerator @Profile("gemini") @Primary`. After this feature: `StubCharacterGenerator @Profile("default")`, `GeminiCharacterGenerator @Profile("gemini") @Primary`. One mental model for both seams.
- **No `@Primary`-with-coexisting-stubs ambiguity.** Under `SPRING_PROFILES_ACTIVE=gemini`, the only `CharacterGenerator` bean is `GeminiCharacterGenerator`; under `SPRING_PROFILES_ACTIVE=default`, the only bean is `StubCharacterGenerator`. `@Primary` is a defensive tiebreak for test-time profile combinations, not a runtime gate.
- **What this changes for existing tests.** Several `@ActiveProfiles("gemini")` integration tests today rely on `StubCharacterGenerator` answering the character call (because the gemini profile bound it). After the narrowing they get the new `GeminiCharacterGenerator`, which would issue a real outbound text call. The plan addresses this by adding **WireMock baselines** to those tests for the text endpoint — the same posture they already use for the image endpoint. This is a test-only ripple, not a production behaviour change. FR-1416 protects only `StubCharacterGeneratorTest`, the OpenAPI contract test, and `RecordInvariantsTest` — all of which run on the `default` profile and stay untouched.
- **Operator behaviour change.** Today: `SPRING_PROFILES_ACTIVE=gemini GEMINI_API_KEY=…` produced real images + stub character text. After this feature: produces real images + real character text. This *is* the feature; spec US-1 makes it the headline.

**Alternatives considered**:

- **Keep `StubCharacterGenerator @Profile({"default", "gemini"})` and rely solely on `@Primary` to override under `gemini`**: works mechanically, but two character beans then coexist in the gemini context, both consuming startup memory and (more importantly) confusing the reader who sees two implementations registered for the same seam. Rejected — `@Primary` should be a tiebreak, not a permanent regime.
- **Introduce a new profile (e.g. `gemini-text`) so operators can mix-and-match**: real ergonomics gain only if there's a need to run image-real + text-stub permanently. Spec US-4 / US-5 don't ask for that; the existing `force-stub-failure` profile already covers the testing need. Rejected (YAGNI).
- **Use `@ConditionalOnProperty(aiavatar.gemini.api-key)` instead of profiles**: 003 R6 already addressed this — keeping one mechanism (profiles) is simpler. Rejected.

---

## R8. No OpenAPI version bump — wire shape unchanged

**Decision**: This feature does **not** modify any contract file. The 003 OpenAPI document (`specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml`) remains the source of truth. This feature's `contracts/` folder records that fact and adds a stability note for posterity.

**Rationale**:

- FR-1415 explicitly pins the wire shape: "the `character` object inside `AlterEgoResponse`, with keys `heroTitleLine1`, `heroTitleLine2`, `tagline`, `superpowers`, `quote`, MUST NOT change." The 003 OpenAPI already describes that shape. Bumping the version (`3.0.0` → `3.0.1`) would be busywork.
- The 003 OpenAPI's `GeneratedCharacter.heroTitleLine2.maxLength: 80`, `tagline.maxLength: 80`, `superpowers.items.maxLength: 120`, `quote.maxLength: 140` are all **looser** than the 100-codepoint cap this feature enforces in the parser (FR-1406). That's fine: the OpenAPI declares the response *envelope* the wire can carry, while the parser is one of two authors of that envelope (the other being the fallback path's `FallbackPosterProvider.character`). Both authors stay inside the envelope. Tightening the OpenAPI to 100 would actively break the fallback character (its `quote` is 33 codepoints — no problem — but tightening anywhere is a risk-for-no-gain change at the contract layer).
- The 003 OpenAPI's max-length numbers also predate the issue's "≤ 100 characters" ask. They do not contradict it (envelope superset). A future feature that wants to advertise tighter contract limits to clients can revisit — out of scope here.
- Existing OpenAPI contract tests (`AlterEgoControllerContractTest`) assert against the 003 envelope and will continue to pass without modification (FR-1416).

**Alternatives considered**:

- **Bump to OpenAPI `3.0.1` and tighten the maxLength to 100**: actively risks breaking the existing fallback character (re-encoded into a length the previous OpenAPI promised). Rejected.
- **Add a `meta.character_source: "real" | "fallback"` discriminator**: spec US-1 acceptance scenario 1 says the LLM-authored character is rendered exactly the way stub-generated text was rendered — there's no client need to distinguish the source. The existing `meta.outcome: real | fallback` already discriminates the broader pipeline outcome. Rejected (spec-out-of-scope).

---

## R9. WireMock test matrix — text endpoint

**Decision**: Mirror 003 R7's image-side WireMock matrix verbatim, retargeted at the text endpoint. Each row of the table maps to one parameterised JUnit 5 test in `GenerateAlterEgoGeminiCharacterFailureIT`.

| Test case | WireMock stub | Expected `meta.reason` |
|---|---|---|
| Happy path (real text) | 200 + valid JSON body shaped per R3's response schema | (no reason — outcome: `real`) |
| Missing API key | (no stub — `aiavatar.gemini.api-key` left blank in the test config) | `not_configured` |
| Network error | `Fault.CONNECTION_RESET_BY_PEER` | `network_error` |
| HTTP 5xx | `503` + empty body, repeated for all 5 retry attempts | `network_error` |
| Rate limit | `429` + `Retry-After: 30` | `rate_limited` |
| Timeout | `withFixedDelay(20_000)` exceeding the 15 s per-attempt timeout (FR-1417) | `timeout` |
| Malformed body — non-JSON outer envelope | 200 + `not-even-json` | `malformed_response` |
| Malformed body — JSON envelope but text part isn't JSON | 200 + valid Gemini envelope wrapping `"plain prose, no JSON"` | `malformed_response` |
| Malformed body — missing required key | 200 + `{ "tagline": "...", "superpowers": ["a","b","c"], "quote": "..." }` (no `heroTitleLine2`) | `malformed_response` |
| Malformed body — wrong superpower count | 200 + four-element `superpowers` array | `malformed_response` |
| Malformed body — overlong trait | 200 + `tagline` of 105 codepoints | `malformed_response` |
| Malformed body — blank trait after trim | 200 + `quote: "   "` | `malformed_response` |
| Safety refusal — promptFeedback.blockReason | 200 + Gemini-shaped body with `promptFeedback.blockReason: "SAFETY"` | `safety_refused` |
| Safety refusal — finishReason | 200 + `candidates[0].finishReason: "SAFETY"` | `safety_refused` |

A second test class (`GenerateAlterEgoGeminiCharacterIT`) covers the happy path end-to-end and asserts that the parsed traits flow through to `meta.outcome=real` and that the fallback character's lines (`The Resilient`, `DEGRADED, NOT DEFEATED.`, etc.) are NOT in the response.

**Rationale**:

- WireMock is the idiomatic Spring-Boot choice for stubbing outbound HTTP in `@SpringBootTest`. Already a dependency from 003. Zero runtime weight.
- Parameterised `@MethodSource` keeps the failure matrix in a single file — the same posture 003's `GenerateAlterEgoGeminiFailureIT` uses.
- The matrix is identical in shape to 003's; an operator who has read one will recognise the other immediately.

**Alternatives considered**:

- Stubbing at the `HttpClient` seam with Mockito: works for unit tests (and we use it for `GeminiCharacterClientTest`), but bypasses URL parsing, headers, and the real serialisation surface for IT-level coverage. Rejected for the IT layer.
- One mega-IT covering both image-side and text-side fault matrices: 30+ test cases in one file, hard to read. Rejected.

---

## R10. Existing `gemini`-profile integration test ripple

**Decision**: Add a "happy-path" WireMock stub for the **text endpoint** to every existing `@ActiveProfiles("gemini")` integration test that does not already exercise the text path, so the test's assertions about the image path remain unchanged. This is the test-only consequence of R7's profile narrowing.

Affected tests:
- `GenerateAlterEgoGeminiIT` (existing image-side happy path) — add text-side WireMock stub returning a fixed valid JSON body.
- `GenerateAlterEgoGeminiFailureIT` (existing image-side fault matrix) — add text-side WireMock baseline so each image-side fault is the only failing call.
- `GenerateAlterEgoGeminiInputCoverageIT` (existing input-axis coverage) — add text-side WireMock baseline.
- `GenerateAlterEgoGeminiNoLogoLeakIT` (existing logo-overlay invariant) — add text-side WireMock baseline.
- `GenerateAlterEgoGeminiNotConfiguredIT` — extend the existing assertion: with `apiKey` blank, BOTH paths short-circuit to `NOT_CONFIGURED`, so the response is fallback character + fallback poster.

**Rationale**:

- The narrowing is the right design move (R7); the test ripple is the unavoidable cost. Each affected test gets ~5 lines of WireMock setup; total ~25 lines across 5 test classes.
- A shared test fixture (`GeminiTextWireMockStubs.happyPath()` static helper in `testsupport`) keeps the per-test surface minimal and prevents drift if the response-schema fixture ever needs an update.
- The `NotConfiguredIT` extension is the only behaviour-asserting change in the ripple — and it's the one that demonstrates US-2 acceptance scenario 1 end-to-end, so it earns its place.

**Alternatives considered**:

- Add a new `gemini-text-stubbed` profile that wires `GeminiCharacterGenerator` against an in-test stub instead of WireMock: would avoid touching the existing tests. But it bifurcates the production wiring (real-vs-test profile that doesn't exist in production), which is exactly the kind of test-shaped surface area Principle III argues against. Rejected.
- Leave the existing tests as-is and let them fire real outbound text calls during `./gradlew test`: violates the constitutional "tests don't make real network calls" implicit posture from 003. Rejected.

---

## R11. Logging surface — what gets emitted, what does not

**Decision**: The new code adds **no new structured-log events**. The existing `event=generation.completed outcome=… reason=… correlationId=…` line in `AlterEgoService` is the one and only structured event per request (FR-1411). The new components log only at DEBUG level, only on failure, and only structural metadata — never the prompt body, never the LLM response body, never `firstName`.

Concretely:
- `GeminiCharacterClient` logs at WARN (with structured args `kv("phase", "gemini-text-call")`, `kv("status", <int>)`, `kv("reason", <FallbackReason wire>)`) **only when classifying a non-2xx or an exception**. It does not log the request URL, the request body, or the response body.
- `GeminiCharacterResponseParser` logs at WARN (with structured args `kv("phase", "gemini-text-parse")`, `kv("reason", "malformed_response")`, `kv("violation", <short label>)`) when it raises a `MALFORMED_RESPONSE`. The `<short label>` is one of a fixed enumeration: `"missing_key:<key>"`, `"superpowers_count:<n>"`, `"trait_too_long:<key>"`, `"trait_blank:<key>"`, `"non_object"`, `"text_part_not_json"`. No user content.
- No DEBUG-level "here's the prompt I built" log line. If an operator needs to see the prompt during diagnosis, they can attach a debugger or temporarily increase the logger level — that's a deliberate choice, not an oversight.

**Rationale**:

- FR-1413 is explicit: "Logs MUST NOT include the user-supplied `firstName`, the photo, or the raw LLM response body in plain text." The `PhotoRedactionFilter` already exists; the new code's job is simply not to introduce *new* fields the filter would have to learn about.
- `kv("phase", …)` keeps the failure events queryable by stage (`gemini-text-call` vs `gemini-image-call` vs `gemini-text-parse`) without adding a new top-level event name (FR-1411 says one event per request).
- The fixed `<short label>` enumeration for parser violations means an operator who sees `violation=trait_too_long:tagline` immediately knows what kind of malformed response Gemini produced, without the team having to grep for free-form strings later.

**Alternatives considered**:

- **Log the prompt at INFO during development, scrub at production via logger level**: adds a "production-config-or-it-leaks" surface. Rejected — the convenience isn't worth the FR-1413 risk.
- **Log a hash of the prompt for correlation**: adds opaque metadata operators can't act on. Rejected.

---

## Open items

None. All items from `spec.md`'s Assumptions section and the planning-deferred decision (sequential vs parallel issuance, R6) are now concrete: text model id (R1), client class structure (R2), prompt + response shape (R3), parser strategy (R4), exception mapping (R5), orchestration order (R6), profile wiring (R7), contract stability (R8), test fault-injection matrix (R9), existing-test ripple (R10), and logging surface (R11). Phase 1 can proceed.

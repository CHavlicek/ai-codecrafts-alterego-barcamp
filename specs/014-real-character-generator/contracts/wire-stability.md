# Phase 1 — Wire Contract Stability Note

**Feature**: 014-real-character-generator
**Date**: 2026-04-27
**Authoritative OpenAPI document**: [`specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml`](../../003-gemini-image-generator/contracts/alter-egos.openapi.yaml) — version `3.0.0`, unchanged by this feature.

## Why this folder contains no `.yaml`

This feature deliberately introduces **no wire-shape change** to the public API. Every byte that crosses the `/api/v1/alter-egos` boundary — request body, response body, headers, status codes, problem-detail payloads — is identical before and after. Therefore there is no OpenAPI version bump, no example update, and no schema diff. This file exists to record that fact and to prevent future readers from assuming the absence of a contract file is an oversight.

The 003 OpenAPI document remains the source of truth for the wire contract. `AlterEgoControllerContractTest` continues to validate the controller against it without modification (FR-1416).

## What changes — and why none of it is on the wire

| Surface | Before 014 | After 014 | On the wire? |
|---|---|---|---|
| `AlterEgoResponse.character` keys & types | `heroTitleLine1`, `heroTitleLine2`, `tagline`, `superpowers[3]`, `quote` | (unchanged) | ❌ No change |
| `AlterEgoResponse.meta.outcome` enum | `real \| fallback` | (unchanged) | ❌ No change |
| `AlterEgoResponse.meta.reason` enum | 6 values from `FallbackReason` | (unchanged) | ❌ No change |
| `Selections` (request) shape | (unchanged from 006/011) | (unchanged) | ❌ No change |
| HTTP status codes | 200 / 400 / 413 / 415 (unchanged from 003) | (unchanged) | ❌ No change |
| Source of `heroTitleLine2` / `tagline` / `superpowers` / `quote` content | Hand-written `stubs/characters.json` fixtures | LLM-authored under `gemini` profile; same fixtures under `default` and `force-stub-failure` profiles | ✅ Behavioural — operator-observable, but *not* schema-observable |
| Backend Spring profile `gemini` reach | Image-side only | Both image and character | ✅ Behavioural |
| `GEMINI_API_KEY` env var | Activates real image path | Activates BOTH real image and real character paths | ✅ Behavioural |
| `aiavatar.gemini.text-model-id`, `…text-request-timeout-ms` config | (absent) | Two new env-overridable fields in `application.yml` | ⚠️ Config-only; not on the HTTP wire |

The "behavioural" rows are visible to operators reading logs and to end users seeing a different *flavour* of caption — but they leave the wire envelope intact. A frontend client compiled before this feature continues to deserialise the post-feature responses without modification (FR-1415).

## Why the existing `maxLength` declarations stay loose

The 003 OpenAPI declares the response envelope:

```yaml
heroTitleLine2:
  type: string
  minLength: 1
  maxLength: 80
tagline:
  type: string
  minLength: 1
  maxLength: 80
superpowers:
  type: array
  minItems: 3
  maxItems: 3
  items:
    type: string
    minLength: 1
    maxLength: 120
quote:
  type: string
  minLength: 1
  maxLength: 140
```

This feature enforces a **stricter** rule at the parser layer: every trait is ≤ **100 NFC-normalised Unicode code points** (FR-1406, clarification Q1). The OpenAPI bounds are the *envelope* the wire promises to clients; the parser bound is the *quality bar* the LLM-authored content must clear before it leaves the orchestrator. They do not contradict — every value the parser produces is also a valid OpenAPI value (each ≤ 100 codepoints is also ≤ 80–140 UTF-16 chars in any realistic character mix).

Tightening the OpenAPI to `maxLength: 100` would be busywork at best and a regression risk at worst — the existing `FallbackPosterProvider.character(...)` happens to produce values that fit easily inside both envelopes today, but any future fallback content tweak that crossed `100` while staying inside the OpenAPI envelope would silently break a future tightened OpenAPI. Keeping the OpenAPI as the looser of the two is the deliberately liberal contract.

## What a future feature would change here

A subsequent feature that *does* touch the wire (e.g. surfacing `meta.character_source: "real" | "fallback"` so a frontend can log which path produced the caption, or advertising the tighter `maxLength: 100` to clients) would:

1. Bump the OpenAPI document's `info.version` (`3.0.0` → `3.0.1` or `3.1.0` depending on additive/breaking).
2. Update `specs/003-gemini-image-generator/contracts/alter-egos.openapi.yaml` (or supersede it with a new feature-folder copy following the project's pattern of "the most recent feature owning the wire change holds the OpenAPI").
3. Update `AlterEgoControllerContractTest`'s pinned spec path constant.
4. Add the new contract examples for the new shape.

None of that work belongs in 014. This feature's discipline is precisely: change the source of the character text, change nothing else.

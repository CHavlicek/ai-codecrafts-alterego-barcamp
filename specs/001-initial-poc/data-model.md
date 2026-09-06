# Phase 1 Data Model: Initial POC — AI Alter Ego Generator

**Feature**: `001-initial-poc` | **Date**: 2026-04-21

This document fixes the shape of every entity and value object the feature touches across the frontend TypeScript and backend Java layers. **Nothing in this model is persisted** — every lifetime here is either browser-session-scoped or request-scoped on the server. See FR-016, FR-020, FR-024.

---

## Enumerations

Each enum lives in both stacks and MUST stay byte-for-byte in sync. Source of truth is the OpenAPI contract (`contracts/alter-egos.openapi.yaml`). The frontend TS enum is hand-written to match; the backend Java enum is hand-written to match; contract-tests assert the wire representation is identical.

### `Pose` *(new in spec after /speckit.clarify Q3)*

| Wire value | Display label | Intent |
|---|---|---|
| `heroic` | Heroic | Upright, full-front, powerful stance. Default recommended vibe. |
| `stealthy` | Stealthy | Crouched, shadowed, oblique composition. |
| `mystical` | Mystical | Arcane gestures, energy aura. |
| `scholar` | Scholar | Contemplative, text/rune-adjacent composition. |

**Minimum set size**: 4 (FR-004). Additional poses may be added in later features without breaking this contract.

### `Colour`

| Wire value | Display label | Hex (UI accent) |
|---|---|---|
| `blue` | Blue | `#1a8aaa` |
| `purple` | Purple | `#a855f7` |
| `red` | Red | `#e05858` |
| `cyan` | Cyan | `#00bfff` |
| `gold` | Gold | `#f0a030` |
| `green` | Green | `#3dd68a` |

**Minimum set size**: 4 (FR-005). Actual set: 6 — provides enough visual differentiation for SC-005's matrix without exploding the fixture count.

### `Archetype`

| Wire value | Display label |
|---|---|
| `bug-hunter` | Bug Hunter |
| `cloud-wizard` | Cloud Wizard |
| `code-breaker` | Code Breaker |
| `data-oracle` | Data Oracle |
| `ai-sorcerer` | AI Sorcerer |
| `deploy-lord` | Deploy Lord |

**Minimum set size**: 6 (FR-006).

### `Universe`

| Wire value | Display label | Notes |
|---|---|---|
| `star-wars` | Star Wars | Sci-fi space opera setting. |
| `harry-potter` | Harry Potter | Magical academy setting. |
| `indiana-jones` | Indiana Jones | Adventure / archaeology setting. |
| `cyberpunk` | Cyberpunk | Neon-noir future setting. (Non-IP placeholder so at least one universe is free of licensing ambiguity even at POC time.) |

**Minimum set size**: 4 (FR-007). Licensing for the IP-bearing universes is explicitly deferred per the spec's Assumptions; the `cyberpunk` entry is a safety net.

---

## Entities

### `AlterEgoSession` (frontend only)

**Purpose**: In-browser state machine driving the user's interaction. Everything the user has chosen, plus the current UI phase. Never transmitted as-is; only a projection of it (see `AlterEgoRequest`) crosses the wire.

**Lifetime**: one browser tab's session. Reset to `idle` by the `Start over` action; cleared entirely when the tab closes. **No `localStorage` / `sessionStorage` / `IndexedDB` usage** (FR-020, FR-024).

**Fields**:

| Field | Type | Constraints |
|---|---|---|
| `photoBlob` | `Blob \| null` | Original or downscaled image; cleared on reset. |
| `photoPreviewUrl` | `string \| null` | `URL.createObjectURL(photoBlob)`; revoked on reset or photo replacement. |
| `pose` | `Pose \| null` | One of the enum values. |
| `colour` | `Colour \| null` | One of the enum values. |
| `archetype` | `Archetype \| null` | One of the enum values. |
| `universe` | `Universe \| null` | One of the enum values. |
| `firstName` | `string` | 1..40 chars after trim; whitespace-only → treat as empty. |
| `phase` | `SessionPhase` | See state machine below. |
| `errorMessage` | `string \| null` | User-readable error surfaced via FR-023. Non-null iff `phase === 'failed_with_fallback'`. |
| `result` | `AlterEgoResponse \| null` | Set when `phase === 'succeeded' \| 'failed_with_fallback'`. |

**Derived**:

- `isReadyToGenerate` = all of `photoBlob`, `pose`, `colour`, `archetype`, `universe` are non-null AND `firstName.trim().length >= 1`. Drives FR-009 gating.
- `missingInputs` = ordered list of input names still missing. Drives FR-010 UI copy.

**State machine** (implemented in `reducer.ts`):

```
                    +--------+                  
                    |  idle  | <-------------------+
                    +--------+                     |
                      |                            |
                      | any input changed          |
                      v                            |
                    +--------+                     |
                    | picking|                     |
                    +--------+                     |
                      |                            |
                      | submit() && isReady        |
                      v                            |
                   +-----------+                   |
                   | generating|                   |
                   +-----------+                   |
                    |         |                    |
                success         failure             |
                (resp ok)       (resp err)          |
                    |         |                    |
                    v         v                    |
          +-----------+   +----------------------+  |
          | succeeded |   | failed_with_fallback |  |
          +-----------+   +----------------------+  |
                    |         |                    |
                    +---+-----+  Start over        |
                        |                          |
                        +--------------------------+
```

- Transitions are authored as discrete action types: `PhotoSelected`, `PhotoCleared`, `PoseSelected`, `ColourSelected`, `ArchetypeSelected`, `UniverseSelected`, `FirstNameChanged`, `GenerateSubmitted`, `GenerateSucceeded`, `GenerateFailedWithFallback`, `StartOverRequested`.
- `GenerateFailedWithFallback` carries both an `errorMessage` and a `result` — the fallback poster — so the UI still shows a complete poster per FR-018.
- `StartOverRequested` always resets regardless of current phase. Revokes any active `photoPreviewUrl` to avoid blob leaks.

**Validation (UI)**:

- `firstName`: show a non-blocking hint if user enters > 40 chars; truncate to 40 on submit. Uppercased on the server side for the hero title's line 1.
- Photo MIME: if `File.type` is not in `['image/jpeg','image/png']`, reject client-side with FR-023-compliant error message; do not POST.
- Photo size: if > 5 MB raw, try to downscale (1024 × 1024 cap); if still > 5 MB after downscale, reject with FR-023-compliant error.

---

### `AlterEgoRequest` (wire schema, multipart)

**Purpose**: What the frontend POSTs to the backend at `/api/v1/alter-egos`. Two parts in one multipart body.

**Part `photo`** (`image/jpeg` | `image/png`, ≤ 5 MB):

Raw image bytes. No filename semantics — the backend ignores `Content-Disposition; filename=…` beyond basic validation.

**Part `selections`** (`application/json`):

```json
{
  "pose": "heroic",
  "colour": "purple",
  "archetype": "cloud-wizard",
  "universe": "star-wars",
  "firstName": "Paula"
}
```

**Validation (Bean Validation on the Java record)**:

| Field | Constraint |
|---|---|
| `pose` | `@NotNull`; must deserialise to a known `Pose` value. |
| `colour` | `@NotNull`; must deserialise to a known `Colour`. |
| `archetype` | `@NotNull`; must deserialise to a known `Archetype`. |
| `universe` | `@NotNull`; must deserialise to a known `Universe`. |
| `firstName` | `@NotBlank`, `@Size(min=1, max=40)`, trimmed. Passed through as-is; the character generator uppercases line 1 of the hero title at render time. |

Any validation failure → HTTP **400** with a `ProblemDetail` body (RFC 7807) per Spring 3 defaults, announced via FR-023.

---

### `AlterEgoResponse` (wire schema, JSON)

**Purpose**: What the backend returns on a successful (or fallback-successful) generation.

```json
{
  "character": {
    "heroTitleLine1": "PAULA",
    "heroTitleLine2": "The Cloud Guardrail",
    "tagline": "STILL SHIPS ON FRIDAYS.",
    "superpowers": [
      "Rolls back with a single keystroke",
      "Hears pager alerts before they fire",
      "Speaks fluent YAML in all dialects"
    ],
    "quote": "It's always DNS. Always."
  },
  "poster": {
    "dataUrl": "data:image/png;base64,iVBORw0KG...",
    "mediaType": "image/png",
    "widthPx": 900,
    "heightPx": 1200
  },
  "meta": {
    "outcome": "success",
    "correlationId": "ce2b9b84-0c9b-4b9a-81a5-7b2e0f39a2c1"
  }
}
```

`meta.outcome` is one of:
- `success` — primary stub path completed cleanly.
- `fallback` — stub threw or `force-stub-failure` profile active; `character` + `poster` come from `FallbackPosterProvider`. Response is **still 200 OK**: the user-facing contract is "you always get a poster" (FR-018). The `meta.outcome` flag tells the frontend to surface the non-blocking error banner (FR-023) alongside the rendered poster.

`meta.correlationId` echoes the backend's generated / forwarded `X-Request-Id`; used by the frontend to tag `console.error` for cross-side log joining (see research.md R1).

---

### `GeneratedCharacter` (backend record, also shape of `character` in the response)

**Lifetime**: produced by `CharacterGenerator.generate()`, handed to `ImageGenerator.generate()` for prompt context, then serialised into the response. Never persisted.

**Fields**: `heroTitleLine1`, `heroTitleLine2`, `tagline`, `superpowers[3]`, `quote`. Types and constraints match the JSON above.

### `PosterImage` (backend record)

**Lifetime**: produced inside the backend request handler by `ImageGenerator.generate()`; base64-encoded into the response body; dropped when the handler returns.

**Fields**: `bytes: byte[]`, `mediaType: String`, `widthPx: int`, `heightPx: int`. Only `dataUrl` / `mediaType` / `widthPx` / `heightPx` cross the wire — `bytes` are encoded in-place.

### `PhotoPayload` (backend request-scoped holder)

**Lifetime**: one HTTP request. Wrapping type around the `MultipartFile`-extracted `byte[]`. Instantiated by the controller; passed to `AlterEgoService`; never assigned to a field, never logged, never cached. After the handler returns, the GC reclaims it in due course. Satisfies FR-016.

**Fields**: `bytes: byte[]`, `mediaType: String`.

**Filter/Interceptor**: a `@ControllerAdvice` wraps exceptions so photo payloads are never reflected in error responses or logs (FR-016 "request/response logs MUST redact photo payloads").

---

## Relationships (at a glance)

```
(browser)  AlterEgoSession  --projects--> AlterEgoRequest  --HTTP multipart-->  (server)
                                                                                     |
                                                                     PhotoPayload (bytes)
                                                                                     |
                                                                                     v
                                                                   AlterEgoService.generate
                                                                            |
                                                           CharacterGenerator.generate
                                                                            |
                                                                  GeneratedCharacter
                                                                            |
                                                           ImageGenerator.generate
                                                                            |
                                                                    PosterImage
                                                                            |
                                                           AlterEgoResponse  <--JSON--  (browser)
                                                                                        |
                                                               AlterEgoSession.result ←─┘
```

Every arrow in the right half of this diagram is an **in-memory** reference. Nothing in the backend-side tree escapes the request scope.

---

## Validation rules summary

| Rule | Enforced where | FR |
|---|---|---|
| All five selection fields non-null | Frontend gating + Bean Validation on backend | FR-009, FR-014 |
| `firstName` 1..40 chars after trim | Frontend input + Bean Validation | FR-008 |
| Photo MIME in `{image/jpeg, image/png}` | Frontend pre-check + Spring multipart config | Edge case in spec |
| Photo size ≤ 5 MB | Frontend pre-check + Spring multipart limit | Edge case in spec |
| No PII in logs (photo, firstName not OK to log raw) | Logback filter + request/response log policy | FR-016, privacy stance |
| API credentials never in the frontend | Separation of concerns — there are no credentials in this POC, and the pattern is locked in by placement alone | FR-017 |

## State-transition tests that will be generated from this model

(Informational — `/speckit.tasks` will expand these into T-IDs.)

- Reducer unit tests for each of the 11 action types (happy path + invalid-from-phase).
- Selector tests for `isReadyToGenerate` and `missingInputs`.
- Contract test asserting the wire shape of `AlterEgoRequest.selections` and `AlterEgoResponse` against the OpenAPI schema.
- Bean-validation tests asserting `400 Bad Request` for each bad field (null enum, blank `firstName`, too-long `firstName`, bad photo MIME, too-big photo).
- Integration test asserting the full `POST /api/v1/alter-egos` happy path returns a valid `AlterEgoResponse` with `meta.outcome=success` and a non-empty `poster.dataUrl`.
- Integration test asserting that with `spring.profiles.active=force-stub-failure`, the same POST returns `200` with `meta.outcome=fallback` and the canned content (SC-004).

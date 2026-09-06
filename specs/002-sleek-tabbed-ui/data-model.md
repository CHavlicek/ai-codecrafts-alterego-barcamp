# Phase 1 Data Model: Initial Styling and Layout — Tabbed Setup Experience

**Feature**: 002-sleek-tabbed-ui
**Date**: 2026-04-22
**Source**: Derived from `spec.md` §"Key Entities" and `research.md` decisions R1–R10. This document pins *shape*; `contracts/alter-egos.openapi.yaml` pins the wire form of the cross-boundary subset.

---

## Scope

This feature has **no persistent storage**. Every entity here is an in-memory value in either the browser session (frontend) or a single HTTP request scope (backend). The sections below are organised by locality.

---

## Frontend: session state

### `AlterEgoSession`

The single reducer state tree for the whole feature, evolved from 001. Fields marked **(new)** are added by this feature; **(dropped)** are removed; **(changed)** kept under the same name but with an updated value domain.

| Field | Type | Required | Description | Source of change |
|---|---|---|---|---|
| `phase` | `'idle' \| 'picking' \| 'generating' \| 'succeeded' \| 'failed_with_fallback'` | yes | Lifecycle of the current session (unchanged from 001). | — |
| `activeTab` | `'setup' \| 'alter-ego'` **(new)** | yes | Which top-level tab is currently visible. Initial value `'setup'`. | FR-101/102, R2, R10 |
| `photoBlob` | `Blob \| null` | yes (to generate) | User's selected photo bytes. | unchanged from 001 |
| `photoPreviewUrl` | `string \| null` | yes (when photoBlob set) | Object URL for the circular preview. Revoked on clear/reset. | unchanged from 001 |
| `pose` | `Pose \| null` | yes (to generate) | Selected pose. 001 value set preserved (Heroic/Stealthy/Mystical/Scholar). | unchanged from 001 |
| `archetype` | `Archetype \| null` **(changed)** | yes (to generate) | Selected engineering role. Value set replaced — see §Enums. | R5 |
| `universe` | `Universe \| null` **(changed)** | yes (to generate) | Selected universe. Value set replaced — see §Enums. | R5 |
| `vibe` | `Vibe \| null` **(new)** | no | Optional vibe. `null` means no selection; generation proceeds regardless. | FR-118 |
| `firstName` | `string` | yes (to generate) | Free-form name input (max 40 chars, trimmed on submit). | unchanged from 001 |
| `colour` | **(dropped)** | — | — | R5 |
| `result` | `AlterEgoResponse \| null` | populated on success/fallback | The generated character + poster. Shape unchanged from 001. | — |
| `errorMessage` | `string \| null` | populated when `phase === 'failed_with_fallback'` | Non-blocking error surfaced next to the fallback poster. | unchanged from 001 |

**Invariants**.
1. `phase === 'idle'` iff every input field is at its initial value (no photo, no selections, empty name) and `activeTab === 'setup'`.
2. When `phase === 'generating'`, `activeTab` MUST be `'alter-ego'` at dispatch time (see Transitions below). The user can then manually switch back to `'setup'` — that does NOT change `phase`.
3. `photoPreviewUrl` is always either `null` or a valid object URL. On transition to `null`, the previous URL MUST be revoked (memory-leak guard).
4. `vibe` is the only optional field. Every other non-phase field is `null`/empty until the user fills it.

**Transitions** (reducer actions — deltas vs. 001; full action list is the union of these + 001's):

| Action | Effect |
|---|---|
| `ActiveTabChanged { tab }` **(new)** | `activeTab = tab`. No other field changes. Never dispatched with the same value it already has. |
| `VibeSelected { vibe }` **(new)** | If `vibe === state.vibe`, set to `null` (deselection — FR-118). Otherwise set to the new value. |
| `GenerationStarted` (existing, dispatched from `onMutate`) | `phase = 'generating'`; existing behaviour + `activeTab = 'alter-ego'` (R2). |
| `StartOverRequested` (existing) | Full reset to initial state (including `vibe = null`, `activeTab = 'setup'`, revoke `photoPreviewUrl`). |

**What 001 actions remain semantically identical**: `PhotoSelected`, `PhotoCleared`, `PoseSelected`, `ArchetypeSelected` (new enum domain but same shape), `UniverseSelected` (same), `FirstNameChanged`, `GenerationSucceeded`, `GenerationFailedWithFallback`. `ColourSelected` is **removed**.

---

### Enums (frontend)

All enums are string literal unions in `types.ts`. Wire values are kebab-case.

#### `Pose` — *carried forward from 001 unchanged*
```ts
type Pose = 'heroic' | 'stealthy' | 'mystical' | 'scholar'
```

#### `Archetype` — **replaced** (engineering roles from the mockup)
```ts
type Archetype =
  | 'cloud-architect'
  | 'backend-dev'
  | 'frontend-dev'
  | 'ai-engineer'
  | 'platform-eng'
  | 'data-engineer'
```
Display label "Role". Option order per FR-116: Cloud Architect → Backend Dev → Frontend Dev → AI Engineer → Platform Eng. → Data Engineer.

#### `Universe` — **replaced** (six mockup values)
```ts
type Universe =
  | 'marvel'
  | 'star-wars'
  | 'cyberpunk'
  | 'the-office'
  | 'indiana-jones'
  | 'lord-of-the-rings'
```
Option order per FR-117: Marvel → Star Wars → Cyberpunk → The Office → Indiana Jones → Lord of the Rings.

#### `Vibe` — **new** (optional)
```ts
type Vibe = 'builder' | 'thinker' | 'rebel' | 'architect'
```
Option order per FR-118: Builder → Thinker → Rebel → Architect.

#### `Colour` — **removed**
No longer referenced by `Selections`, options, components, or tests. The Colour enum type is deleted from `types.ts`; `COLOUR_OPTIONS` is deleted from `options.ts`; `ColourGrid` component + test are deleted.

---

### `Selections` (frontend-only; mirror of backend request body)

Shape submitted to the backend. Must stay in lockstep with the OpenAPI `Selections` schema.

```ts
interface Selections {
  pose: Pose                    // required
  archetype: Archetype          // required
  universe: Universe            // required
  vibe?: Vibe                   // optional — omit when null
  firstName: string             // required, 1–40 chars, trimmed
}
```

**Validation** (applied before submit in the `AlterEgoPage` submit handler):
- `photoBlob !== null` (required).
- `pose`, `archetype`, `universe` are all non-null.
- `firstName.trim().length >= 1`.
- `vibe` may be `null` or a valid `Vibe` value.

If validation fails, submit is not called — this is enforced by FR-122's disabled-until-complete rule on the Generate button.

---

### Option-list shape (frontend)

Each selection grid receives its option list as an array of this shape:

```ts
interface SelectionOption<T extends string> {
  value: T           // the wire enum value (e.g. 'cloud-architect')
  label: string      // user-visible label (e.g. 'Cloud Architect')
  emoji: string      // leading glyph (e.g. '☁')
  accentVar: string  // CSS variable name for this sub-group's selected-state accent
}
```

The `accentVar` is set at the grid level (not per option) in practice; the interface is written this way for component reuse. Exact emoji/label pairs are pinned in `options.ts` and come from the mockup.

---

## Backend: request-scoped state

### `AlterEgoRequest` (Java record, Bean-Validated)

```java
public record AlterEgoRequest(
        @NotNull Pose pose,
        @NotNull Archetype archetype,
        @NotNull Universe universe,
        /* nullable */ Vibe vibe,
        @NotBlank @Size(min = 1, max = 40) String firstName
) { … }
```

**Deltas vs. 001**:
- `colour` field **dropped** from record components, validation, and `withTrimmedFirstName()`.
- `vibe` field **added**, nullable (no `@NotNull`). Bean Validation therefore accepts `null`; the service treats `null` as "no vibe selected."
- `Archetype` and `Universe` have the new value sets described above.
- Everything else carries forward.

**Invariants**: same as the frontend `Selections` invariants. Bean Validation enforces at the controller boundary; RFC 7807 `400` for failures.

### Enums (backend, mirror of frontend)

Each enum pairs a Java constant with a kebab-case JSON wire value via `@JsonValue` + `@JsonCreator`. The constants are:

- `Pose` — `HEROIC`, `STEALTHY`, `MYSTICAL`, `SCHOLAR` (unchanged).
- `Archetype` — `CLOUD_ARCHITECT`, `BACKEND_DEV`, `FRONTEND_DEV`, `AI_ENGINEER`, `PLATFORM_ENG`, `DATA_ENGINEER` (replaces 001 values).
- `Universe` — `MARVEL`, `STAR_WARS`, `CYBERPUNK`, `THE_OFFICE`, `INDIANA_JONES`, `LORD_OF_THE_RINGS` (replaces 001 values).
- `Vibe` — `BUILDER`, `THINKER`, `REBEL`, `ARCHITECT` (new).
- `Colour` — **removed as a user-visible enum**. The six hex tones remain as internal constants used by the poster accent derivation in `StubImageGenerator` and `FallbackPosterProvider`, but are no longer accepted in requests. A renamed internal-only class (or a private `record AccentTone(String hex, String name)`) replaces the public `Colour` enum so that re-introducing a colour input in a future feature doesn't reopen the old wire contract.

### Accent-colour derivation function

```java
// Signature lives on StubImageGenerator; FallbackPosterProvider uses the same logic.
AccentTone deriveAccent(Archetype archetype, Universe universe);
```

**Contract**:
- Total function (every `(archetype, universe)` pair returns a non-null `AccentTone`).
- Pure (no side effects, no time-dependence).
- Deterministic (same inputs → same output across process restarts).
- Output hex always drawn from the six-value palette: `#1a8aaa`, `#a855f7`, `#e05858`, `#00bfff`, `#f0a030`, `#3dd68a`.

**Algorithm**:
1. Look up `(archetype, universe)` in the hand-picked cell map (a subset of the 36 cells curated for narrative fit — "Star Wars × Cloud Architect → cyan," "The Office × AI Engineer → gold," etc.).
2. If the cell is absent, compute `(archetype.ordinal() * 6 + universe.ordinal()) mod 6` and index into the palette in declaration order. This guarantees total coverage without enumerating all 36 cells and keeps output deterministic.

Test coverage: parametrised JUnit test asserts the contract above for every `(archetype, universe)` pair.

---

## Cross-boundary contract summary

| Concept | Frontend type | Backend type | Wire form |
|---|---|---|---|
| Pose | `Pose` union | `com.aiavatar.alterego.model.Pose` | kebab-case string |
| Archetype (role) | `Archetype` union | `Archetype` | kebab-case string |
| Universe | `Universe` union | `Universe` | kebab-case string |
| Vibe | `Vibe?` union | `Vibe` (nullable field) | kebab-case string or omitted |
| First name | `string` | `String` | trimmed string, 1–40 |
| Photo | `Blob` | `MultipartFile` | `multipart/form-data` part `photo` |

Everything else (`GeneratedCharacter`, `Poster`, `ResponseMeta`, `AlterEgoResponse`, `ProblemDetail`) is **unchanged** from 001 in both shape and semantics. See `contracts/alter-egos.openapi.yaml` for the full OpenAPI form.

---

## Deleted shapes

| Shape | Location | Reason |
|---|---|---|
| `Colour` enum (frontend) | `types.ts` | R5 — no user-facing colour input. |
| `COLOUR_OPTIONS` | `options.ts` | Same. |
| `Colour` enum (backend, public) | `model/Colour.java` | Same. The file is deleted; a private `AccentTone` record inside the stub package replaces its internal use. |
| `ColourGrid` component + test | `features/alterego/components/ColourGrid.tsx` + `.test.tsx` | No colour picker in the new UI. |
| `ColourSelected` reducer action | `state/reducer.ts` | No longer dispatched. |
| `colour` field in `Selections` + `AlterEgoRequest` | `types.ts`, `model/AlterEgoRequest.java` | Wire contract delta. |

All deletions are load-bearing: they remove dead code paths so Principle VI's "remove deprecated" discipline stays honest.

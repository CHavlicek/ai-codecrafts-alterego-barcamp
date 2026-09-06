# Phase 1 — Data Model: 019-remove-art-styles

**Feature**: Remove Line Art, Low-Poly 3D, and Pixel Art from Art Style category
**Status**: Final

## Scope note

This feature does not introduce, modify, or remove any persisted entity. Per 001 FR-016 / FR-017 / FR-024 the AI-Avatar POC has no persistence — sessions live only in browser memory and request-scoped JVM state. The only "data" this feature changes is the membership of a single in-memory closed enumeration: `ArtStyle`.

## Entities

### `ArtStyle` (closed enumeration — modified)

The required rendering style for the generated poster (introduced by feature 006, declared as a closed set of nine wire values). This feature reduces the set to six members. The wire-value contract for the surviving members is preserved verbatim.

**Before this feature (9 members)**:

| Wire value             | Java constant            | Label                  | Status after 019 |
|------------------------|--------------------------|------------------------|------------------|
| `oil-painting`         | `OIL_PAINTING`           | Oil Painting           | **kept**         |
| `watercolor`           | `WATERCOLOR`             | Watercolor             | **kept**         |
| `pixel-art`            | `PIXEL_ART`              | Pixel Art              | **removed**      |
| `low-poly-3d`          | `LOW_POLY_3D`            | Low-Poly 3D            | **removed**      |
| `line-art`             | `LINE_ART`               | Line Art               | **removed**      |
| `pop-art`              | `POP_ART`                | Pop Art                | **kept**         |
| `renaissance-portrait` | `RENAISSANCE_PORTRAIT`   | Renaissance Portrait   | **kept**         |
| `japanese-woodblock`   | `JAPANESE_WOODBLOCK`     | Japanese Woodblock     | **kept**         |
| `cel-shaded`           | `CEL_SHADED`             | Cel-Shaded             | **kept**         |

**After this feature (6 members)**: `oil-painting`, `watercolor`, `pop-art`, `renaissance-portrait`, `japanese-woodblock`, `cel-shaded` — in this relative order (matching the 006 declaration order minus the three removed rows). No renaming, no reshuffling, no re-illustration (FR-1904).

### Validation rules

- **FR-1901 / FR-1907**: The `ArtStyle` enum MUST contain exactly the six surviving wire values listed above. No other values are permitted to deserialise.
- **FR-1903** (stale-tab safety): A request body carrying any of `pixel-art`, `low-poly-3d`, `line-art` (or any other unknown string) for `Selections.artStyle` MUST fail Jackson deserialisation via `ArtStyle.fromWire`, surfacing as an RFC 7807 `400 Bad Request` problem-detail through the existing controller-advice. No silent substitution, no default-back, no logging of the rejected value beyond what existing request-rejection logging already does.
- **FR-1902**: The frontend's `ART_STYLE_OPTIONS` array (which the Surprise Me randomiser draws from via `pickOne`) MUST be a one-to-one mirror of the surviving Java enum members — same wire values, same relative order, same labels. There is no test in 019 that proves the cross-stack mirror; the existing 006-era assertion that `Selections.artStyle` round-trips through the API contract continues to do so.

### State transitions

The `ArtStyle` enum is value-typed and has no lifecycle. There are no state transitions to model. The "transition" caused by this feature is a one-time deploy event: at `T0` (before the deploy) nine values are accepted; at `T0+ε` (after the deploy) six are.

### Cardinality assertions (test invariants)

The following invariants will be enforced by tests:

- `frontend/src/features/alterego/lib/randomSelections.test.ts` — across ≥ 1 000 simulated draws using a uniform RNG, the set of distinct committed `artStyle` values is a subset of `{oil-painting, watercolor, pop-art, renaissance-portrait, japanese-woodblock, cel-shaded}`. (SC-1902)
- `frontend/src/features/alterego/components/ArtStyleGrid.test.tsx` — the grid renders exactly six tiles with the surviving labels, in the declared order. (SC-1901)
- `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java` — `ArtStyle.values().length == 6` and each surviving member retains its pre-019 `wire()` and `label()` strings. (FR-1904)
- `backend/src/test/java/com/aiavatar/alterego/integration/AlterEgoControllerInputValidationIT.java` — a request body carrying `"artStyle":"pixel-art"` returns HTTP 400 with RFC 7807 problem-detail; the response body never silently substitutes a surviving value. (FR-1903)

## Not modelled here

- **No new entity** — none introduced.
- **No persistence schema change** — there is no persistence (001 FR-016 / FR-017 / FR-024).
- **No new request/response field** — the wire shape of `AlterEgoRequest` and `AlterEgoResponse` is unchanged; only the enum cardinality of `Selections.artStyle` shrinks.
- **No migration plan** — there is no stored data to migrate (Assumption: "No persistence to migrate").

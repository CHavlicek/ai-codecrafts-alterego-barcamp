# Phase 1 — Data Model: Art Style Category

No persistence is introduced (inherits 001 FR-016 / 003 FR-215). The "data model"
below describes the in-memory and on-the-wire shapes only.

## Entity: `ArtStyle`

Closed enumeration with nine members. Each member has three facets:

| Wire value (kebab-case) | Display label (UI) | Natural-language label (Gemini prompt) |
|---|---|---|
| `oil-painting` | Oil Painting | oil painting, visible brushstrokes and impasto texture |
| `watercolor` | Watercolor | watercolor painting, soft washes and bleeding edges |
| `pixel-art` | Pixel Art | retro pixel art, 16-bit sprite aesthetic |
| `low-poly-3d` | Low-Poly 3D | low-poly 3D render, flat-shaded faceted geometry |
| `line-art` | Line Art | clean line art, monochrome ink illustration |
| `pop-art` | Pop Art | pop art, bold flat colours and halftone dots |
| `renaissance-portrait` | Renaissance Portrait | Renaissance oil portrait, chiaroscuro lighting |
| `japanese-woodblock` | Japanese Woodblock | Japanese ukiyo-e woodblock print, hand-carved line work |
| `cel-shaded` | Cel-Shaded | cel-shaded anime, crisp outlines and flat colour fills |

### Invariants

- The wire-value set is CLOSED: an incoming `artStyle` not in the table above MUST
  fail validation at the controller boundary with an RFC 7807 `400 Bad Request`.
- Wire values are stable contract (see research §R5); renames are breaking changes.
- Display labels are allowed to change without a contract bump (UI-only).
- Natural-language labels are allowed to change without a contract bump (prompt
  engineering) but each change MUST keep the nine labels mutually distinct
  (unit-test gate).

### Lifecycle

- `artStyle` has no lifecycle independent of the session. It is set exactly once
  per `AlterEgoSession` via `ArtStyleSelected`, may be replaced any number of times
  before Generate, and is cleared on `StartOverRequested` (reset to `null`).

## Entity: `Selections` (modified)

The frontend `Selections` interface gains a required `artStyle` field:

```text
Selections {
  pose:       Pose        // required (unchanged from 002)
  archetype:  Archetype   // required (unchanged from 002)
  universe:   Universe    // required (unchanged from 002)
  vibe?:      Vibe        // OPTIONAL (unchanged from 002)
  artStyle:   ArtStyle    // required (NEW in 004)
  firstName:  string      // required (unchanged from 001)
}
```

The backend record `AlterEgoRequest` mirrors this shape:

```java
public record AlterEgoRequest(
    @NotNull Pose pose,
    @NotNull Archetype archetype,
    @NotNull Universe universe,
    Vibe vibe,                            // optional
    @NotNull ArtStyle artStyle,           // NEW, required
    @NotBlank @Size(min = 1, max = 40) String firstName
) { ... }
```

Field ordering within the record is preserved for existing fields; `artStyle` is
inserted between `vibe` and `firstName` to keep optional (`vibe`) and required
fields grouped and to minimise the diff on the existing test fixtures.

## Entity: `AlterEgoSession` (modified — frontend only)

Frontend-only in-memory state machine gains an `artStyle` slot:

```text
AlterEgoSession {
  ... existing fields ...
  artStyle: ArtStyle | null   // NEW — null means "not picked yet"
}
```

New reducer action:

```text
action ArtStyleSelected { artStyle: ArtStyle }
  → state.artStyle := action.artStyle
  → state.phase    := 'picking'
```

`StartOverRequested` continues to delegate to `initialAlterEgoSession()`, which
now returns `artStyle: null`.

## Downstream dependency: Gemini prompt

`GeminiPromptBuilder` gains a new `EnumMap<ArtStyle, String> ART_STYLE_LABELS`
populated with the "Natural-language label" column above. The `build(request)`
method appends one new line to the prompt:

```text
- Art style: <ART_STYLE_LABELS[request.artStyle()]>
```

The line is emitted after the `universe` line and before the `vibe` line.

## What did not change

- No new persistence (SQLite schema untouched — there is no such schema yet).
- No new cache key or TTL (no cache wired in this feature).
- No change to `GeneratedCharacter`, `Poster`, `ResponseMeta`, `AlterEgoResponse`.
- No change to the fallback stub image or the fallback-reason enum.

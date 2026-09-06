# Phase 0 — Research: Art Style Category

## R1 — Where does the new category slot into the existing component architecture?

**Decision**: Wrap the existing `SelectionGrid<T>` primitive
(`frontend/src/features/alterego/components/SelectionGrid.tsx`) with a new
`ArtStyleGrid` wrapper, following the `ArchetypeGrid.tsx` template exactly.

**Rationale**: `SelectionGrid<T>` already owns the WAI-ARIA radio-group semantics,
keyboard navigation (arrow keys, Tab, Enter, Space), accent-colour variable wiring,
and emoji-rendering contract. Re-using it keeps all five categories visually and
behaviourally identical. The wrapper is a four-prop passthrough (options, value,
onChange, accentVar).

**Alternatives considered**:
- Inline the grid in `SetupLayout` → rejected (duplicates a11y code, drifts from the
  other four categories).
- Generalise `SelectionGrid` to accept a `category` prop that picks options itself →
  rejected (leaks domain knowledge into a primitive and does not meaningfully reduce
  code vs. the wrapper pattern).

## R2 — Icon delivery: emoji vs SVG sprite

**Decision**: Unicode emoji glyphs, one per option, rendered inside an `aria-hidden`
span exactly the way `options.ts` defines `emoji` for the existing four categories.

**Rationale**: Consistency with Pose/Archetype/Universe/Vibe and zero new moving parts.
`SelectionGrid` already renders `option.emoji` verbatim; the feature becomes a data
change, not a rendering change. The `/icons.svg` sprite is already wired into the
site (social-media icons) but `SelectionGrid` does not consume it, and extending the
primitive to support optional `iconId` is out of scope for this feature. The PR's
post-implementation architect review will flag the SVG upgrade as a sensible
follow-up.

**Alternatives considered**:
- Add nine `<symbol>` entries to `frontend/public/icons.svg` and extend
  `SelectionGrid` with an `iconId` prop — rejected as scope creep; no existing
  category uses SVG sprites for option icons today.
- Mixed mode (emoji for some styles, SVG for others) — rejected for obvious
  inconsistency.

**Chosen glyphs** (confirmed during spec writing):
| Wire value | Display label | Emoji |
|---|---|---|
| `oil-painting` | Oil Painting | 🖼️ |
| `watercolor` | Watercolor | 💧 |
| `pixel-art` | Pixel Art | 👾 |
| `low-poly-3d` | Low-Poly 3D | 🧊 |
| `line-art` | Line Art | ✏️ |
| `pop-art` | Pop Art | 💥 |
| `renaissance-portrait` | Renaissance Portrait | 🖌️ |
| `japanese-woodblock` | Japanese Woodblock | 🗾 |
| `cel-shaded` | Cel-Shaded | 🎞️ |

## R3 — Required vs optional on the selection payload

**Decision**: `artStyle` is **required**, matching pose/archetype/universe. Frontend
`Selections.artStyle: ArtStyle` (no `?`); backend `@NotNull ArtStyle artStyle`.

**Rationale**: The issue reads prescriptive ("User should be able to choose an Art
Style…"). The poster's visual treatment is a fundamental composition cue, not a
garnish — leaving it optional would either (a) force the prompt to fall back to an
unbounded default (bad UX, unpredictable output) or (b) cause the generator to pick
whatever looks plausible, defeating the purpose of the selector. Required also keeps
the Setup-tab mental model symmetric: five pickable categories, four of them
required, one (Vibe) optional — a clean rule.

**Alternatives considered**:
- Optional with a server-side default (e.g. "photorealistic") → rejected: adds an
  implicit 10th style the UI cannot express, and users picking by accident would
  see unpredictable results.
- Required with a "surprise me" 10th option → rejected: a scope expansion not in
  the issue.

## R4 — Gemini prompt integration

**Decision**: Add an `EnumMap<ArtStyle, String> ART_STYLE_LABELS` in
`GeminiPromptBuilder` with one natural-language description per wire value. Append a
new line `"- Art style: <description>"` to the prompt immediately after the
universe line (before the vibe line), so style reads as a top-level rendering cue.

**Rationale**: The prompt builder is the single place where wire values become
natural-language cues for Gemini. Matching the existing pattern (`POSE_LABELS`,
`ROLE_LABELS`, `UNIVERSE_LABELS`, `VIBE_LABELS`) keeps the file's shape uniform.
Placing the line after universe and before vibe reads "person → role → world →
style → tone" which matches how artists describe a brief.

**Chosen label strings** (pinned by unit test):
| Wire value | Label string |
|---|---|
| `oil-painting` | oil painting, visible brushstrokes and impasto texture |
| `watercolor` | watercolor painting, soft washes and bleeding edges |
| `pixel-art` | retro pixel art, 16-bit sprite aesthetic |
| `low-poly-3d` | low-poly 3D render, flat-shaded faceted geometry |
| `line-art` | clean line art, monochrome ink illustration |
| `pop-art` | pop art, bold flat colours and halftone dots |
| `renaissance-portrait` | Renaissance oil portrait, chiaroscuro lighting |
| `japanese-woodblock` | Japanese ukiyo-e woodblock print, hand-carved line work |
| `cel-shaded` | cel-shaded anime, crisp outlines and flat colour fills |

**Alternatives considered**:
- Concatenate the display label directly (e.g. "Art style: Pixel Art") — rejected;
  the existing categories deliberately use richer prose ("heroic, chest forward")
  because Gemini responds better to descriptive cues than to single nouns.
- Inject the photographic intent into the top preamble instead of a bullet →
  rejected for uniformity with the other category lines.

## R5 — Wire-value contract stability

**Decision**: Treat all nine kebab-case wire values as part of the public API
surface from first merge. Any future rename is a breaking change requiring a bumped
contract version.

**Rationale**: The OpenAPI file under each feature spec is additive and
append-only in this repo; the wire values become inputs to the real provider and
will appear in log correlation IDs. Renames break clients.

**Alternatives considered**:
- Mark wire values as mutable during pre-1.0 → rejected: the app ships without a
  versioning story for the selections payload, so treating anything as mutable
  invites silent drift.

## R6 — Accent-colour token

**Decision**: Introduce a fifth token `--color-accent-artstyle` in
`frontend/src/styles/tokens.css` (or the existing tokens file the four other
accents live in) with a distinct hue. Wire into `ACCENT_VARS.artstyle` in
`options.ts`.

**Rationale**: The existing tokens file maps one accent per category. Reusing, say,
the Vibe accent would make the two categories visually collide on the Setup tab.

**Alternatives considered**:
- Reuse Archetype accent (desaturated) → rejected: visual clash.
- No accent (neutral grey) → rejected: breaks the established visual grammar.

## R7 — Reducer action shape

**Decision**: Add `{ type: 'ArtStyleSelected'; artStyle: ArtStyle }` and handle it
with the same case body as `ArchetypeSelected` — overwrite, no deselect toggle.

**Rationale**: Art Style is required and single-select (unlike Vibe, which allows
deselect-to-clear). `ArchetypeSelected` is the exact template.

**Alternatives considered**: None — the reducer file has a one-pattern-per-category
convention already.

## R8 — Test strategy

**Decision**: Follow TDD per Principle III. Test tasks precede implementation tasks
in `tasks.md`. Tests to write (all fail before implementation):

1. Frontend unit — `ArtStyleGrid.test.tsx`: renders nine options, keyboard nav,
   aria-checked toggling, emoji is `aria-hidden`.
2. Frontend unit — `reducer.test.ts`: `ArtStyleSelected` sets field; `StartOverRequested`
   resets to `null`.
3. Frontend unit — `alterEgoClient.test.ts`: `artStyle` present in multipart JSON
   blob.
4. Frontend unit — `SetupLayout.test.tsx`: Generate disabled until art style picked.
5. Frontend E2E — happy-path Playwright spec picks an art style; assert request
   body carries the kebab wire value.
6. Backend unit — `ArtStyleTest`: `fromWire` round-trip for each of 9 values,
   rejects unknown.
7. Backend unit — `AlterEgoRequestValidationTest`: missing `artStyle` → constraint
   violation on the record.
8. Backend unit — `GeminiPromptBuilderTest`: every `ArtStyle` produces a distinct,
   non-empty label line; golden-prompt fixture locks the nine strings.
9. Backend integration — extend existing Gemini input-coverage IT with an art-style
   parameter matrix (even one per style × one other selection is enough to verify
   end-to-end JSON round-trips).

**Rationale**: This coverage lands the constitution's ≥ 90% line-coverage gate
comfortably and exercises the constitution's "at least one integration test" rule
without inventing a new integration surface.

## Resolved unknowns

All `NEEDS CLARIFICATION` items from Technical Context are resolved:
- Language/version: TypeScript 5.x / Java 21.
- Dependencies: no new ones.
- Storage: N/A by design (no persistence).
- Testing frameworks: Vitest / RTL / Playwright / JUnit 5 / Spring Boot Test.
- Target platform: evergreen web + JVM 21.
- Performance goals: SC-303 (16 ms paint).
- Constraints: Principles I, III, IV, V, VI (V has a documented deviation).
- Scale/scope: 9 enum values, 1 new FE component, 1 new BE enum, existing files
  extended.

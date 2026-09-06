# Feature Specification: Art Style Category

**Feature Branch**: `006-art-style-category` (developed on `claude/art-style-category-P7wwR`)
**Created**: 2026-04-23
**Status**: Draft
**Input**: GitHub issue [#11](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/11) — "Art Style Category". User description verbatim:
> User should be able to choose an "Art Style" for his/her alter ego image. Possible options are: oil painting, watercolor, pixel art, low-poly 3D, line art, pop art, Renaissance portrait, Japanese woodblock, cel-shaded. Each of the art style categories should have a corresponding small icon next to the option name. The chosen art style option should be passed into the AI generator API along all the other chosen options.

## Clarifications

### Session 2026-04-23

- Q: Is Art Style a required or optional selection? → A: Required (mirrors pose / archetype / universe; Generate is gated on it). Diverges from Vibe, which remains optional.
- Q: How are the per-option icons delivered — Unicode emoji glyphs or custom SVG sprites via `/icons.svg`? → A: Unicode emoji glyphs, consistent with the four existing categories. SVG-sprite upgrade is deferred as a follow-up.
- Q: Does the new category get its own accent-colour token, or does it reuse an existing one? → A: New token — `--color-accent-artstyle` — so the five categories stay visually distinguishable on the Setup tab.

No other high-impact ambiguities detected during the coverage scan (Functional Scope, Domain Model, Interaction, Edge Cases, Integration, Terminology, and Completion Signals all Clear).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Pick an art style and see it reflected in the generated poster (Priority: P1)

A user preparing an alter ego on the Setup tab picks one of nine art styles (oil painting, watercolor, pixel art, low-poly 3D, line art, pop art, Renaissance portrait, Japanese woodblock, cel-shaded) alongside their existing picks (pose, archetype, universe, optional vibe, first name). On Generate, the returned poster visibly matches the chosen art style.

**Why this priority**: The issue is essentially "give users control over the visual treatment of their alter ego". Without this slice, the feature has no value — a silent art-style field that the model ignores would be worse than absent.

**Independent Test**: End-to-end — upload a photo, fill out the Setup form, pick a distinct art style (say pixel art), click Generate, confirm the returned poster's style is recognisably pixel art (manual visual check) and the request payload contains the chosen wire value (automated).

**Acceptance Scenarios**:

1. **Given** the user has filled in all other required Setup fields, **When** they pick any one of the nine art styles and click Generate, **Then** the backend receives `artStyle` as part of the selections payload and the Gemini prompt includes a natural-language description of that style.
2. **Given** a user has selected oil painting, **When** they change the selection to Japanese woodblock and re-generate, **Then** the second poster's rendering is visibly different from the first (reflects the new style).
3. **Given** the real provider call fails and the stub fallback is served, **When** the user had picked an art style, **Then** the fallback still renders and the user sees the generic fallback notice (art style does not affect the stub image — fallback semantics unchanged from 003).

---

### User Story 2 — Each art style option has a recognisable icon (Priority: P2)

Each of the nine options in the Art Style grid is labelled with both a display name (e.g. "Oil Painting") and a small leading icon glyph so the grid is scannable at a glance and feels consistent with the existing Pose / Archetype / Universe / Vibe grids.

**Why this priority**: The issue calls out icons explicitly ("Each of the art style categories should have a corresponding small icon next to the option name."). Polish layer on top of P1; an unlabelled-but-functional grid would still technically satisfy P1.

**Independent Test**: Open the Setup tab, confirm every art style option renders a label and a leading icon (emoji glyph) next to it; screen reader announces only the label (icon is aria-hidden — mirrors existing categories).

**Acceptance Scenarios**:

1. **Given** the Setup tab is open, **When** the user inspects the Art Style grid, **Then** each of the nine options displays a label plus a leading icon.
2. **Given** a screen reader is active, **When** the user navigates the Art Style grid, **Then** only the label is announced (the icon is decorative / `aria-hidden`).

---

### User Story 3 — Keyboard and accessibility parity with existing categories (Priority: P3)

The Art Style grid is keyboard-navigable (arrow keys move the active option, Enter selects) and exposes the WAI-ARIA radio-group semantics already used by Pose / Archetype / Universe / Vibe.

**Why this priority**: Accessibility is non-negotiable per the constitution (Principle I — modern stack implies modern a11y). This story is split out so it can be tested via a focused unit test without the full E2E loop.

**Independent Test**: Unit-test the new grid's keyboard handling and ARIA attributes in isolation (reuses the existing `SelectionGrid<T>` primitive, so the test essentially asserts that the primitive was wired correctly).

**Acceptance Scenarios**:

1. **Given** keyboard focus is on the Art Style grid, **When** the user presses ArrowRight / ArrowDown, **Then** the active option moves to the next style and wraps at the end.
2. **Given** keyboard focus is on an art style option, **When** the user presses Enter or Space, **Then** that option is selected and any prior selection is cleared.
3. **Given** the Art Style grid has rendered, **When** assistive tech inspects its ARIA role, **Then** the container is a `radiogroup` and each option is a `radio` with correct `aria-checked` state.

---

### Edge Cases

- **No art style picked**: Generate is disabled (art style is a required selection — same bar as pose / archetype / universe).
- **Unknown wire value on the server**: Validation rejects the request with RFC 7807 `400 Bad Request` (Bean Validation catches `@NotNull`; Jackson rejects unknown enum via `@JsonCreator` exception).
- **Art style value added later**: Backend `GeminiPromptBuilder.label(...)` has a defensive fallback that emits the wire form, so an unlabelled future enum value won't break the prompt; tests pin every current value to a distinct label.
- **Fallback image path (real provider unavailable)**: Art style has no effect on the stub image; the generic fallback notice is shown unchanged (003 FR-214).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-301**: The Setup tab MUST present a fifth category, "Art Style", containing exactly these nine options (display labels): Oil Painting, Watercolor, Pixel Art, Low-Poly 3D, Line Art, Pop Art, Renaissance Portrait, Japanese Woodblock, Cel-Shaded.
- **FR-302**: Each Art Style option MUST display a leading icon glyph next to its label. The icon MUST be decorative (`aria-hidden="true"`) so the accessible name is the label alone — consistent with the existing four categories.
- **FR-303**: Selecting an Art Style option MUST follow the same single-select semantics as Pose / Archetype / Universe: picking a new value replaces the old one, and there is no "deselect to clear" toggle (unlike Vibe).
- **FR-304**: Art Style MUST be a required selection. The Generate action MUST remain disabled until the user has picked an Art Style (in addition to the other existing required fields: photo, pose, archetype, universe, first name).
- **FR-305**: The chosen Art Style MUST be sent to the backend as part of the existing selections JSON payload, using a stable kebab-case wire value per option (e.g. `oil-painting`, `low-poly-3d`, `japanese-woodblock`).
- **FR-306**: The backend MUST validate the incoming `artStyle` field using the same Bean Validation regime as the other required fields (`@NotNull` + `@JsonCreator` enum parsing). A missing or unknown value MUST return an RFC 7807 `400 Bad Request`.
- **FR-307**: The backend prompt sent to the real image generator (Gemini) MUST include a natural-language description of the chosen Art Style. The description MUST be distinct for each of the nine values so the model can differentiate them.
- **FR-308**: Start-over (existing reset action) MUST clear the Art Style selection along with the rest of the session state (same reset semantics as pose / archetype / universe / vibe).
- **FR-309**: The fallback stub image path MUST be unaffected by Art Style: when the real provider fails, the generic fallback notice and stub image are returned per 003 FR-214, regardless of which Art Style was picked.
- **FR-310**: Wire values for the nine art styles MUST be treated as part of the public API contract from first merge. Changing a wire value post-release is a breaking change requiring a contract-version bump.

### Key Entities

- **ArtStyle**: Closed enumeration of nine rendering styles for the generated poster. Each value has a stable kebab-case wire name (e.g. `oil-painting`), a human-readable display label shown in the UI (e.g. "Oil Painting"), and a natural-language description used only by the backend to flavour the model prompt (e.g. "oil painting, visible brushstrokes and impasto texture"). Related to the `Selections` payload as a required field alongside `pose` / `archetype` / `universe`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-301**: 100% of the nine art styles produce visibly distinct generated posters when the real provider path is exercised (qualitative manual check across all nine; automated pinning of the prompt strings as a proxy).
- **SC-302**: A user with all other fields filled can pick an Art Style and generate a poster without any extra clicks beyond the single option click (i.e. no dialogue, no modal, no confirmation step).
- **SC-303**: The Art Style grid renders in under 16 ms on the initial Setup-tab paint (no perceptible jank — it's a pure extension of the existing grid pattern).
- **SC-304**: Unit test line coverage for the new frontend component and backend enum / prompt additions is ≥ 90% (constitution Principle III gate).
- **SC-305**: A parameterised backend test pins one distinct prompt-label string per art style; a regression that collapses two labels or leaves one missing fails the suite.

## Assumptions

- The existing Setup tab's visual rhythm (heading + subheading + grid) has room for a fifth category without a layout overhaul. If not, a layout pass is in scope for the implementation phase but out of scope for the spec.
- Icons will be rendered as Unicode emoji glyphs, consistent with the four existing categories. Upgrading to custom SVG icons via the `/icons.svg` sprite is explicitly deferred to a follow-up feature (will be flagged in the post-implementation architect review).
- Art Style is required (not optional). Rationale: the issue reads prescriptive ("User should be able to choose an Art Style …"), and the generated image's visual treatment is a fundamental composition cue, not a nice-to-have garnish. This is the only point that deviates from Vibe (which is optional per 002).
- The AI generator referenced in the issue is the existing Gemini integration wired in 003. No new provider is introduced.
- Persistence remains out of scope: art style lives in session state only and is never written to disk, cache, or logs (inherits 001 FR-016 / 003 FR-215).
- A new accent colour token `--color-accent-artstyle` is introduced so the five categories stay visually distinguishable; defaulting to one of the existing accents would blur Setup-tab readability.

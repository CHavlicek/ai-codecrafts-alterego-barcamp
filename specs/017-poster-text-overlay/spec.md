# Feature Specification: Hero Name, Title & Tagline on the Poster Image

**Feature Branch**: `017-poster-text-overlay`
**Created**: 2026-05-06
**Status**: Draft
**Input**: GitHub issue #41 — "Display Name, Title and Tagline over the image"

> Display the name, title and tagline over the image at the bottom.
> Text should not overflow the frame, it should all fit on the final image.
> Name text animation should be removed, the color should align with the
> background on which it is written to be readable.

## Clarifications

### Session 2026-05-06

- Q: Where is the text composited — server-side onto the image bytes, HTML overlay over the `<img>`, or both? → A: Server-side bake (Option A). Same pipeline as 008's `BrandingOverlayService` and 015's `PosterFrameOverlayService`; the response carries a single image artefact that already contains the text.
- Q: Which fields map to "name / title / tagline"? → A: name = `Selections.firstName` (the user's first name from Setup), title = `character.heroTitleLine1` (the hero display name from the generator), tagline = `character.tagline`. `character.heroTitleLine2` is NOT rendered into the poster's bottom region.
- Q: What happens to the existing on-screen HTML rendering of the same strings? → A: Remove the visible HTML lines for `heroTitleLine1` (the formerly shimmering hero name) and `tagline` — they live only on the poster image now. Keep `heroTitleLine2` as a small on-screen HTML heading (it isn't on the image). The poster's `alt` text carries the full identity (first name, hero title, tagline) so screen-reader users still learn the hero's identity without depending on the rasterised text.
- Q: How does the system handle text that would overflow the bottom region? → A: Shrink-only — each of the three lines stays on a single line; if a line's natural rendering exceeds the safe horizontal width, the system reduces that line's font size until it fits. No wrapping to additional rows. Long taglines may render visually small.
- Q: How does text stay legible against the bottom region's decorative chrome (dots, circuit lines)? → A: Glyph-level treatment — the frame asset is left as-is, no backing plate is drawn at composition time, and each glyph carries a strong drop-shadow / outline halo so it remains legible wherever the underlying chrome pattern sits behind it. The 4.5:1 contrast bar (FR-1707) applies to the glyph fill against its outline halo (the immediate effective background of each glyph), not against the raw chrome pattern.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Name, title, and tagline are baked into the poster (Priority: P1)

When a user generates an alter-ego poster, the user's first **name** (the
visually dominant line, sourced from the Setup form), the hero **title**
(the smaller secondary line beneath the name, sourced from the
generator's `heroTitleLine1`), and the **tagline** (the shortest
supporting line, sourced from the generator's `tagline`) are rendered
onto the poster image itself, inside the dark "long-bottom" chrome region
beneath the character artwork. The text is part of the image artefact —
not a separate caption shown next to it — so when the user prints the
poster (010), saves it, or shares it, the three lines travel with the
image.

**Why this priority**: This is the entire issue. Without it, the framed
poster the user takes home is missing the personal identity the rest of the
flow is built around. The current frame committed in 015 already reserves
the bottom region for chrome — this feature fills that region with the
character's name/title/tagline, completing the printable artefact.

**Independent Test**: Generate any alter ego (real or fallback). Inspect
the returned poster image. The hero name, title, and tagline are visible
inside the bottom dark region of the image. Save or print the image — the
text is present on the saved/printed copy without depending on any HTML
rendering.

**Acceptance Scenarios**:

1. **Given** the user enters first name "Ada" in Setup and generation
   produces hero name "Captain Spectre" with tagline "Ships chaos as a
   service", **When** the poster image is rendered, **Then** all three
   strings ("Ada", "Captain Spectre", "Ships chaos as a service") are
   legibly drawn inside the bottom dark region of the image, stacked
   top-to-bottom in that order, horizontally centred, with "Ada" visually
   dominant and "Ships chaos as a service" visually quieter.
2. **Given** the same poster is sent to print via the existing Print flow
   (010), **When** the printed sheet is produced, **Then** the name, title,
   and tagline appear on the printed sheet exactly as they appear on the
   image (because they are part of the image), without any dependency on
   HTML overlay or screen-only styling.
3. **Given** the generator returns a non-empty `heroTitleLine2` (the
   "secondary identity line" used in earlier features), **When** the
   poster image is rendered, **Then** that string does NOT appear in the
   bottom region of the image — only the user's first name, the
   `heroTitleLine1`, and the `tagline` are rendered there.

---

### User Story 2 — Text always fits within the poster frame (Priority: P1)

The name, title, and tagline must always fit inside the bottom region of
the poster — no clipping, no characters running off the edge, no
collisions with the frame's decorative chrome — regardless of how long the
generated strings turn out to be in practice.

**Why this priority**: The issue explicitly calls this out: *"Text should
not overflow the frame, it should all fit on the final image."* A poster
with a name truncated mid-letter is worse than no in-image text at all,
and would visibly ship to a real print.

**Independent Test**: Generate posters with both short ("Ada · Cloud Sage
· Builds calm") and long inputs (a name that fills the visible width at
the largest size; a tagline near the longest the generator emits in
practice). In every case, every character of the name, title, and tagline
is fully visible within the safe area of the bottom region, with no
glyphs touching or crossing the visible frame edge or the rounded
character cutout above.

**Acceptance Scenarios**:

1. **Given** an unusually long first name, **When** the poster is
   rendered, **Then** the name is shown in full on a **single line** (no
   wrapping, no ellipsis, no clipping) by reducing only the name's font
   size until it fits the safe horizontal width — while remaining the
   visually dominant line of the three.
2. **Given** an unusually long tagline, **When** the poster is rendered,
   **Then** the tagline is shown in full on a **single line** by
   reducing only the tagline's font size until it fits the safe
   horizontal width; it never wraps onto a second row, never overflows
   the visible frame edge, and never overlaps the character cutout
   above.
3. **Given** the three lines are each shrunk independently to fit their
   safe widths, **When** the poster is rendered, **Then** the visual
   hierarchy name > title > tagline is still recognisable (the name is
   never rendered smaller than the title, and the title is never
   rendered smaller than the tagline).

---

### User Story 3 — Text is readable against the dark chrome (Priority: P1)

The bottom region of the frame is dark (near-black) by design. The
overlaid text MUST be coloured so that it is comfortably readable against
that dark background — i.e. light/bright on dark — at normal viewing
distance and at the size the printed 10×15-class output will be held.

**Why this priority**: The issue calls it out directly: *"the color should
align with the background on which it is written to be readable."* Dark
text on a near-black background is not legible at any size — this gate is
binary.

**Independent Test**: View the rendered poster on a typical laptop screen
and (where possible) the printed copy. The name, title, and tagline are
each clearly readable at a glance — no straining, no squinting at faint
type — and the contrast between the text colour and the surrounding chrome
meets the WCAG AA non-decorative-text contrast bar (4.5:1 for body text;
the visually dominant name comfortably exceeds it at large display size).

**Acceptance Scenarios**:

1. **Given** the bottom chrome region is rendered in its current dark
   palette, **When** the name, title, and tagline are drawn over it,
   **Then** the foreground colour of each line achieves at least a 4.5:1
   contrast ratio against the underlying chrome at the pixel(s) directly
   beneath each glyph.
2. **Given** the chrome's decorative patterns (dots, circuit lines) sit
   inside the bottom region directly behind the text — the frame asset
   is unchanged and no backing plate is drawn — **When** the text is
   composited, **Then** each glyph is rendered with a strong drop-shadow /
   outline halo such that, at any point where a pattern pixel sits
   directly behind a glyph, the glyph remains comfortably readable; the
   patterns never reduce legibility below the AA bar.

---

### User Story 4 — The name shimmer animation is removed (Priority: P2)

Today, the on-screen "Your Alter Ego" tab shows the hero name as an HTML
heading with an animated brand-gradient shimmer. With the name now living
on the image itself, that animation is removed — the name is presented
calmly, without motion, both because a baked-in image cannot animate and
because the issue explicitly asks for the animation to go.

**Why this priority**: Strictly visual cleanup that follows from US1; the
poster is still functional with or without the animation removal, but
leaving a duplicate animated HTML name next to a static in-image name
would look broken. P2 because it is a corollary of US1 rather than the
core delivery.

**Independent Test**: Generate any alter ego. On the "Your Alter Ego" tab,
the hero name does not animate — no looping shimmer, no gradient drift,
no entrance pulse on the name itself. (The 005 tab-transfer entrance
animation on the panel as a whole is unrelated and remains.)

**Acceptance Scenarios**:

1. **Given** a generated poster is shown on the "Your Alter Ego" tab,
   **When** the user observes the panel for at least one full prior
   shimmer cycle (~10 s), **Then** the hero name shows no looping or
   periodic visual change.
2. **Given** the user respects `prefers-reduced-motion: reduce`, **When**
   the poster is rendered, **Then** there is no name-related motion to
   suppress (because there is no name animation in the first place — this
   feature does not introduce one and removes the existing one).

---

### Edge Cases

- **Empty or single-character name**: Should still render — a single
  letter does not break the layout. The name simply occupies less width.
- **Name with non-ASCII glyphs (accents, emoji-style symbols, CJK)**:
  Renders as the generator emits them, using a font that supports the
  same script range the rest of the app uses. No transliteration.
- **Name/title/tagline returned with leading or trailing whitespace**:
  Rendered trimmed; whitespace must not push text off-centre or into the
  decorative side margins.
- **Identical or near-identical title and tagline**: Rendered as-is — the
  generator's job is to produce distinct lines; this feature does not
  deduplicate.
- **Text colour dropped onto a future lighter chrome variant**: If the
  chrome ever changes from near-black to a lighter palette, the text
  colour MUST be re-evaluated against the AA contrast bar (US3) before
  the new chrome ships.
- **Real-provider images that fall back to the stub**: The fallback
  poster (which already emits at the matching aspect ratio per 015's
  recent tweak) MUST receive the same name/title/tagline treatment; a
  fallback artefact missing the user's identity is worse than a real one
  that does.
- **Rapid regeneration** (Surprise Me, Start Over → Generate again): Each
  new poster carries its own name/title/tagline; no leftover text from a
  previous session bleeds into the next image.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-1701**: The system MUST render three lines as part of the poster
  image itself, positioned inside the dark "long-bottom" chrome region
  beneath the character artwork: the user's first name (sourced from
  the Setup form's `firstName`), the hero title (sourced from the
  generator's `character.heroTitleLine1`), and the tagline (sourced
  from the generator's `character.tagline`).
- **FR-1702**: The three lines MUST appear stacked vertically in the
  order **name → title → tagline**, horizontally centred within the
  bottom region's safe area.
- **FR-1703**: The name MUST be the visually dominant line of the three
  (largest type weight/size), the title secondary, and the tagline the
  quietest — so the hierarchy is unambiguous at a glance.
- **FR-1704**: All three lines MUST always render fully — no character
  is permitted to be clipped, ellipsised, dropped, or pushed beyond the
  visible bottom region of the frame.
- **FR-1705**: When the natural render of any line would exceed the
  bottom region's safe horizontal width, the system MUST shrink **only
  that line's font size** until the line fits on a **single row**
  within the safe width. Lines MUST NOT wrap to additional rows; lines
  MUST NOT be ellipsised, truncated, or dropped. Each of the three
  lines is shrunk independently of the others; shrinking one line
  never enlarges another.
- **FR-1715**: After per-line shrinking, the visual hierarchy MUST be
  preserved — the rendered name font size MUST be ≥ the rendered title
  font size, and the rendered title font size MUST be ≥ the rendered
  tagline font size. If a smaller line's natural size would otherwise
  exceed a larger line's shrunk size, the smaller line is shrunk
  further to maintain the ordering.
- **FR-1706**: The text MUST never overlap the character image cutout
  above the bottom region nor the visible frame edge / decorative
  border on any side.
- **FR-1707**: The text colour MUST contrast with its **immediate
  effective background** such that the contrast ratio at the glyph
  pixels is at least 4.5:1 (WCAG AA non-decorative-text bar). When
  glyphs are rendered with a drop-shadow / outline halo (FR-1716), the
  immediate effective background for the contrast computation is the
  halo, not the raw chrome pixels behind it.
- **FR-1716**: The frame asset MUST NOT be modified to carve out a
  decoration-free text band, and the system MUST NOT draw a backing
  plate (solid or translucent rectangle) behind the text at composition
  time. Instead, each rendered glyph MUST carry a drop-shadow /
  outline halo strong enough that, regardless of which decorative
  chrome pattern (dots, circuit lines, gradient border) sits directly
  behind it, the glyph remains comfortably readable per FR-1707 / SC-1703.
- **FR-1708**: The previous on-screen animated rendering of the hero
  name (the looping brand-gradient shimmer on the large heading) MUST
  be removed; the name MUST NOT animate anywhere it is rendered.
- **FR-1709**: The change MUST apply to every poster the user can see —
  real-provider output (Gemini and fal.ai) **and** the fallback poster —
  so a user can never be shown an in-flight artefact missing the
  identity text.
- **FR-1710**: The text rendered into the image MUST be drawn from
  fields the system already carries — `Selections.firstName` (the user's
  first name from the Setup form), `character.heroTitleLine1` (the hero
  title), and `character.tagline`. No new generator field is required;
  no new round-trip is required. `character.heroTitleLine2` is NOT
  rendered into the bottom region.
- **FR-1711**: The change MUST preserve existing no-persistence
  guarantees — the strings, like the image bytes, live in process
  memory only for the duration of one Generate request, exactly as
  today. No caching, no logging of the strings, no disk write.
- **FR-1712**: Print (010) MUST continue to produce a printed sheet
  that shows the name/title/tagline on the poster — and because the
  text is now on the image, that requirement is satisfied by the image
  itself, regardless of any HTML rendering choices.
- **FR-1713**: The visible HTML rendering of `character.heroTitleLine1`
  (the formerly shimmer-animated hero name) and `character.tagline`
  next to the poster MUST be removed — those strings live only on the
  poster image now. `character.heroTitleLine2` MUST remain visible as
  an on-screen HTML heading (it is not rendered into the image).
- **FR-1714**: Screen-reader users MUST still be able to learn the
  hero's identity (first name, hero title, tagline) without depending
  on the rasterised text inside the image. The poster image's `alt`
  text MUST therefore carry all three strings — the user's first name,
  `character.heroTitleLine1`, and `character.tagline` — in a form a
  screen reader can announce, replacing today's narrower
  "Alter ego poster for {heroTitleLine1}" wording.

### Key Entities *(include if feature involves data)*

- **Name** — the user's first name from the Setup form
  (`Selections.firstName`). Renders as the visually dominant line of
  the three.
- **Title** — the hero's display title produced by the character
  generator (`character.heroTitleLine1`). Renders as the secondary
  line.
- **Tagline** — the short supporting line produced by the character
  generator (`character.tagline`). Renders as the quietest of the
  three.
- **Bottom region (text safe area)** — a rectangular sub-region inside
  the frame asset's dark long-bottom chrome where the three lines are
  composited. Its exact bounds are a property of the frame asset (its
  outer extent is bounded by the visible frame edge above and the
  rounded character cutout below; its inner extent is sized so the
  longest line, after FR-1705 shrinking, never crosses the visible
  frame edge). The asset itself is **not** modified to carve out a
  decoration-free band — text sits over whatever chrome pattern occupies
  those pixels, and is kept legible by the glyph drop-shadow / outline
  treatment (FR-1716).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-1701**: 100% of generated posters (real and fallback, every
  archetype × universe × art-style combination the app exposes) render
  the name, title, and tagline fully inside the bottom region with no
  clipping, no overflow, and no overlap with the character cutout or
  the decorative frame edge.
- **SC-1702**: Across a sweep of inputs covering the shortest and
  longest names/titles/taglines the generator produces in practice (and
  representative non-ASCII glyphs the app accepts), every rendered
  poster passes a visual inspection for "all text fully visible" — zero
  failures.
- **SC-1703**: The text-to-background contrast ratio for each of the
  three lines, measured at glyph pixels against the immediate
  underlying chrome, is ≥ 4.5:1 in 100% of rendered posters.
- **SC-1704**: Zero looping or periodic name animations are observable
  on the "Your Alter Ego" tab during a 30-second observation of any
  generated poster.
- **SC-1705**: A user printing a generated poster receives a printed
  sheet on which the name, title, and tagline are present and legible —
  with the same content as on screen — in 100% of print attempts. (This
  is satisfied by the text being part of the image; the printed result
  no longer depends on HTML print-CSS rendering of the name/title/
  tagline lines.)
- **SC-1706**: When a user runs Start Over and generates again
  (including via Surprise Me), the new poster shows only the new
  name/title/tagline — never any text from a previous session — in
  100% of regeneration attempts.
- **SC-1707**: A screen reader navigating the "Your Alter Ego" tab on
  100% of generated posters announces (a) the user's first name, (b)
  the hero title (`heroTitleLine1`), and (c) the tagline — sourced
  from the poster's `alt` text — without requiring any sighted
  inspection of the image.

## Assumptions

- **The text is composited into the poster image server-side** (confirmed
  in the 2026-05-06 clarification session, Q1) — not drawn as an HTML
  overlay over an `<img>` element. The composite happens in the backend
  rendering pipeline alongside 008's logo overlay and 015's frame
  overlay; the HTTP response carries a single image artefact that
  already contains the name/title/tagline. Rationale: the issue language
  ("on the final image", "all fit on the final image") describes the
  artefact, not the screen; print/save are correct without depending on
  HTML/CSS print semantics; one source of truth for the rendered text.
- **The "name", "title", and "tagline" of issue #41 map** (confirmed in
  the 2026-05-06 clarification session, Q2) **respectively to
  `Selections.firstName`** (the user's first name from the Setup form),
  `character.heroTitleLine1` (the hero display title produced by the
  014 generator), and `character.tagline`. `character.heroTitleLine2`
  is intentionally **not** rendered into the bottom region; whether it
  remains visible elsewhere on screen is governed by FR-1713 / Q3. No
  new field is added to the response contract.
- **Other character text fields (the three superpowers, the quote, and
  the unrendered `heroTitleLine2`) are out of scope** for in-image
  rendering. They remain rendered as HTML on the "Your Alter Ego" tab
  as today (subject to FR-1713 for `heroTitleLine1` / `tagline`);
  whether any of them ever join the in-image rendering is a future
  feature, not this one.
- **The bottom-region "text safe area" is a property of the frame
  asset**, derived from it (mirrors 015's `PosterFrameAssetLoader`
  approach to the transparent inner cutout). The asset shipped on the
  current branch already carries the long-bottom region; if the asset
  is later swapped, the safe-area bounds must be re-derivable from the
  new asset.
- **Text rendering uses fonts already available to the composition
  step** — no new font-asset dependency in scope. If the chosen font
  family does not cover all glyphs the generator might emit, glyphs
  outside its coverage may render with a fallback — acceptable for
  this feature.
- **The on-screen HTML rendering of name/title/tagline next to the
  poster is replaced by the in-image rendering** (confirmed in the
  2026-05-06 clarification session, Q3). The visible HTML for
  `heroTitleLine1` (the formerly shimmer-animated hero name) and
  `tagline` is removed — these strings live only on the poster image
  now. `heroTitleLine2` remains as the only on-screen HTML heading
  next to the poster, since it is not rendered into the image. Screen
  readers still learn the hero's identity via the poster image's `alt`
  text, which is widened to carry all three rendered-into-image
  strings (FR-1714).
- **No persistence is introduced.** The strings rendered into the
  pixel buffer continue to live only in process memory for the
  duration of one Generate request, mirroring 015 FR-1501..FR-1513 and
  001 FR-016 / FR-017 / FR-024.
- **No new third-party image library is required.** The existing 2D
  graphics stack (used by 008's logo overlay and 015's frame overlay)
  is sufficient for drawing antialiased text into the canvas.

# Phase 0 — Research: Poster Text Overlay

## R0 — Font availability and choice

**Decision**: Bundle a single open-licence sans-serif TTF as a classpath
asset under `backend/src/main/resources/branding/fonts/Inter.ttf`. Load
it once at startup via `Font.createFont(Font.TRUETYPE_FONT, ...)` and
register it on the JVM's `GraphicsEnvironment`. Use one face across all
three lines, varying weight/size only.

**Rationale**:

- The backend Docker image is `eclipse-temurin:21-jre-alpine`. Alpine's
  base image ships **no `fontconfig` and no system fonts**. JDK logical
  fonts ("SansSerif", "Serif", "Monospaced") rely on `fontconfig`
  resolving them to a real face on disk; in vanilla Alpine they all
  collapse to a square-glyph fallback that is unreadable. Bundling a
  TTF and registering it programmatically sidesteps the runtime-image
  font story entirely.
- A bundled font also makes rendered output **deterministic across host
  / Docker / CI**, which matters for the integration-test pixel
  assertion (R5) and for visual regressions caught later by humans.
- Inter has a permissive (SIL Open Font License) license, ships full
  Latin coverage plus most Latin-extended diacritics, and is one file —
  no font-family pack to manage. (Any equivalently-licenced sans —
  e.g. Source Sans 3, Public Sans — would be acceptable; "Inter" is
  the placeholder; final pick happens at task time.)
- The spec's Assumption "no new font-asset dependency in scope" is a
  soft constraint — it was written assuming JDK fonts would be
  available. The constraint that *actually* matters (per the spec's
  intent) is "no new third-party image library", which a bundled TTF
  does not breach.

**Alternatives considered**:

- *Logical SansSerif via JDK fontconfig*: Fragile under Alpine; would
  require either switching to a Debian-based JRE image (a deployment
  change with broader ramifications) or installing `fontconfig` +
  `ttf-dejavu` packages via the Dockerfile (still adds non-deterministic
  behaviour across hosts). Rejected.
- *Multi-family pack (sans + display + mono)*: Not needed — three lines
  with size/weight differentiation read clearly with one family. Adds
  bytes for no return.
- *Fail-soft to logical sans if the TTF fails to load*: This is the
  loader's actual fallback ladder — try the bundled TTF first; on any
  decode/registration failure, log WARN and fall through to
  `Font.SANS_SERIF`; if even that fails, the text overlay step
  short-circuits to "return previously-framed bytes unchanged" per
  FR-1716 fail-soft discipline.

## R1 — Drop-shadow / outline rendering technique

**Decision**: Render each line by extracting its **glyph outline shape**
via `TextLayout.getOutline(AffineTransform.getTranslateInstance(x, y))`,
then drawing the shape twice on the same canvas:

1. Stroke the outline with a thick `BasicStroke` (round joins/caps) in
   a fully opaque dark colour (rich black) — this is the halo that
   keeps the glyph readable over decorative chrome.
2. Fill the outline in a light colour (off-white / cream) — this is the
   visible glyph.

**Rationale**:

- Stroke-then-fill produces sharp, hint-free, resolution-independent
  haloes — no blur kernel, no `BufferedImageOp`, no temp images, no
  visible aliasing on the printed 10×15 sheet.
- It survives JPEG re-encode if a downstream step ever changes the
  output format; `BufferedImageOp` blur often softens noticeably under
  re-encode.
- The fill colour can be chosen per-line (e.g. accent cyan for the
  tagline, near-white for name + title) without touching the halo
  algorithm.

**Knobs (decided in tasks based on visual review)**:

- Stroke width ≈ 6% of the line's font size, clamped to ≥ 2 px so even
  the smallest shrunk tagline keeps a readable halo.
- `RenderingHints.KEY_ANTIALIASING = ON`, `KEY_TEXT_ANTIALIASING = ON`,
  `KEY_FRACTIONALMETRICS = ON` (already set on the canvas; `TextLayout`
  picks them up).

**Alternatives considered**:

- *`g.drawString` with a separate `g.setColor` shadow offset*: Cheaper
  to implement but produces a soft directional shadow rather than a
  uniform halo; legibility is uneven on patterns. Rejected.
- *`BufferedImage` Gaussian blur on a glyph mask*: Visually nice but
  costs a second `BufferedImage` allocation per line and softens under
  re-encode. Rejected for the higher-fidelity stroke approach.
- *Solid backing rectangle behind each line*: Already rejected by the
  spec (Q5 → C); this is the design constraint we are honouring.

## R2 — Where to compute the bottom-region safe area

**Decision**: Compute the bottom region in `PosterFrameAssetLoader` at
JVM startup, alongside the existing alpha-zero bounding-box scan. The
bottom region is defined as:

```text
bottomRegionX      = innerX
bottomRegionY      = innerY + innerHeightPx
bottomRegionWidth  = innerWidthPx
bottomRegionHeight = canvasHeightPx − bottomRegionY
```

…then **inset** symmetrically by a small margin (default 4% of canvas
width on each side, 6% of canvas height top + bottom) to keep glyphs
clear of the visible frame edge.

Add four new fields to `PosterFrameAsset`:
`bottomRegionX, bottomRegionY, bottomRegionWidth, bottomRegionHeight`.
The loader's existing logic stays single-pass; the new computation is
constant-time given the alpha-zero bbox.

**Rationale**:

- Mirrors 015's "asset-derived geometry" pattern — geometry is a
  property of the asset, computed once, never per request. Same
  fail-soft (`PosterFrameAsset.missing()`) guards apply.
- The bottom region is fully determined by the alpha-zero cutout the
  loader already finds; no new asset metadata or sidecar file needed.
- Keeping the inset as a small constant in the loader (rather than the
  service) makes the safe area visible from one place; the service
  consumes the bounds and never re-derives them.

**Alternatives considered**:

- *Hard-code the bottom-region bounds in `PosterTextOverlayService`*:
  Couples the service to today's specific asset; if the asset is later
  swapped, both files need to change in lock-step. Rejected.
- *Reserve a second alpha-zero region in the asset for the text band*:
  Would force redesigning the bundled PNG — the spec (Q5 → C) explicitly
  says the asset stays unchanged. Rejected.
- *Compute on first request, cache thereafter*: Adds first-request
  latency for no gain over the @PostConstruct path. Rejected.

## R3 — Order of services in `AlterEgoService.generate()`

**Decision**: Run **`PosterTextOverlayService.apply(...)` after
`PosterFrameOverlayService.apply(...)`**, both on the real-provider
path and on the fallback path. The orchestrator chains them as:

```java
PosterImage rawPoster   = imageGenerator.generate(...);
PosterImage framed      = posterFrameOverlay.apply(rawPoster);
PosterImage finalPoster = posterTextOverlay.apply(framed, request, character);
```

…and on the fallback path:

```java
PosterImage rawFallback = fallbackProvider.poster(...);
PosterImage framed      = posterFrameOverlay.apply(rawFallback);
PosterImage finalPoster = posterTextOverlay.apply(framed, request, fallbackCharacter);
```

**Rationale**:

- Q5 (drop-shadow legibility, no asset change) requires the text to be
  drawn **on top of** the chrome's decorative patterns — the chrome is
  drawn by the frame service, so text must come after.
- Each service stays single-purpose: frame service composes the
  character + chrome; text service composes the strings on top of the
  framed image. Independent fail-soft and independent unit tests.
- The PNG re-decode/re-encode round-trip between services costs ≈ 5–10 ms
  on 1024×1536 ARGB — negligible against the 20-second progress budget
  (013).
- FR-1709 — every poster the user can see, real or fallback, gets the
  treatment. Threading both branches through the same chain is the
  cleanest invariant: there is no state-machine choice about whether
  to apply text.

**Alternatives considered**:

- *Merge frame + text into one service*: Saves the PNG round-trip but
  conflates two concerns and complicates fail-soft (a bad font should
  not skip the frame; a bad asset should not skip the text). Rejected.
- *Pass `BufferedImage` between services to avoid the round-trip*:
  Couples the services' internals; either both stay AWT or one
  changes. The latency saved is below noise threshold. Rejected.

## R4 — Frontend wiring for the wider `alt` text

**Decision**: Add a `firstName: string` prop to `PosterView` and to
`PrintArtefact`. The session reducer already retains `selections.firstName`
across the generation lifecycle (it backs Start Over and the print path);
`AlterEgoPanel` reads `session.lastSelections.firstName` and threads it
into `<PosterView>`. The poster's `<img alt>` becomes:

```text
Alter ego poster for {firstName}: {character.heroTitleLine1}. {character.tagline}.
```

…joined with explicit punctuation so screen readers introduce natural
pauses between the three pieces of identity.

**Rationale**:

- `firstName` already lives in the session — no new fetch, no new
  reducer action, no API surface change.
- The `alt` text concatenation is deliberately three sentence-style
  fragments rather than one comma-separated run, because Voice Over
  / NVDA respect terminating punctuation when pacing announcements.
- `PrintArtefact` mirrors `PosterView`'s render in a print-only DOM
  subtree; both must change together so screen and print stay
  consistent.

**Alternatives considered**:

- *Build a "hero identity" derived selector*: Over-engineered for one
  string concatenation in two components. Rejected.
- *Rely on the rasterised text alone*: Fails FR-1714 — image bytes are
  not announced by screen readers. Rejected.

## R5 — Pixel-level integration-test assertion

**Decision**: The new `GenerateAlterEgoTextOverlayIT` integration test
calls `POST /api/alter-egos`, decodes the response's
`poster.dataUrl`, and asserts:

1. The image is the expected canvas size (`asset.canvasWidthPx ×
   canvasHeightPx`).
2. The bottom region (`bottomRegionX/Y/W/H`) contains a **non-trivial
   number of near-white pixels** — i.e. the count of pixels in the
   bottom region with `R > 200 AND G > 200 AND B > 200` exceeds a
   threshold (e.g. 1% of bottom-region pixels). On the un-textified
   baseline this count is essentially zero (the chrome is dark); on
   the textified output it jumps by orders of magnitude.
3. The same area on the **un-textified** baseline (a control image
   produced by directly invoking the frame service alone in a separate
   unit test) does **not** exceed the threshold — confirming the count
   is from text, not from the chrome itself.

**Rationale**:

- Deterministic, no OCR dependency, no flakiness from font metrics
  drifting across builds (the bundled TTF locks rendering).
- Light-pixel count is a robust proxy for "the overlay drew something
  that looks like text" — with a 4.5:1 contrast bar and an off-white
  fill on a near-black region, the rendered pixels dominate any
  decorative-chrome pixel count in the same area.
- Precise enough to catch regressions (e.g. the service no-ops on
  fail-soft → count drops to baseline → test fails).

**Alternatives considered**:

- *OCR (Tess4J / Tesseract)*: Adds a heavy native dependency, flaky on
  CI, slow. Constitution Principle VI-grade red flag. Rejected.
- *Hash the rendered bytes against a golden image*: Brittle — every
  font-substitution change would force regenerating the golden;
  hostile to maintenance. Rejected.
- *Assert the response shape only (no pixel inspection)*: Doesn't
  actually verify the feature delivered — passes trivially even if
  the overlay no-ops. Rejected.

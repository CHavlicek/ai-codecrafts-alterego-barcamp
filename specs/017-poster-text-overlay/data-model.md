# Phase 1 — Data Model: Poster Text Overlay

## Public contract delta

**None.** The `POST /api/alter-egos` request and response shapes are
identical to 016. The user-visible change is:

- The bytes of `response.poster.dataUrl` now contain three rendered
  text lines inside the dark long-bottom region of the frame.
- The frontend's `<img alt="...">` text is widened (FR-1714) to carry
  the same three strings.

No new field is added to `Selections`, `GeneratedCharacter`,
`AlterEgoResponse`, or `Poster`. No header, query parameter, or
form field is added or removed.

See [contracts/README.md](./contracts/README.md) for the explicit
"no contract change" note.

## Backend value objects (in-process only)

### `PosterTextLines`

Carries the three strings the overlay step renders.

```java
public record PosterTextLines(
        String firstName,   // from AlterEgoRequest.firstName (validated upstream)
        String title,       // from GeneratedCharacter.heroTitleLine1
        String tagline      // from GeneratedCharacter.tagline
) {
    public PosterTextLines {
        firstName = firstName == null ? "" : firstName.strip();
        title     = title     == null ? "" : title.strip();
        tagline   = tagline   == null ? "" : tagline.strip();
    }
}
```

**Rules**:

- All three fields are stripped of leading/trailing whitespace at
  construction (Edge Cases — "leading/trailing whitespace must not
  push text off-centre").
- Null inputs are coerced to empty strings (defensive — upstream
  validation should never pass nulls but the overlay must not throw).
- Empty strings are valid: the corresponding line is **skipped** at
  render time (no row drawn, no vertical space reserved). The other
  two lines remain centred in the safe area.

### `PosterTextStyle`

Per-feature constants for the rendering. Lives next to the service
so it's tweakable without rebuilding the asset.

```java
public record PosterTextStyle(
        float nameTargetSizePx,    // e.g. 96
        float titleTargetSizePx,   // e.g. 48
        float taglineTargetSizePx, // e.g. 28
        float minSizePx,           // e.g. 16   (hard floor — never shrink past this)
        float lineGapPx,           // e.g. 12   (vertical gap between rendered rows)
        Color fillColor,           // off-white, e.g. (245, 245, 240)
        Color outlineColor,        // rich black, e.g. (8, 8, 12)
        float outlineRatio         // e.g. 0.06f → strokeWidth = max(2, size * 0.06)
) {
    public static PosterTextStyle defaults() { /* the values above */ }
}
```

**Rules**:

- `nameTargetSizePx ≥ titleTargetSizePx ≥ taglineTargetSizePx` —
  enforced in the constructor (throws `IllegalArgumentException` on
  violation; covered by record-invariants test).
- `minSizePx > 0` and `≤ taglineTargetSizePx`.
- `outlineRatio ∈ (0.0, 0.2]` — anything bigger and the halo merges
  the glyphs.

### `PosterFrameAsset` — extended

Existing fields unchanged; **four new fields** capture the bottom
region computed by the loader:

```java
public record PosterFrameAsset(
        BufferedImage image,
        int canvasWidthPx,
        int canvasHeightPx,
        int innerX, int innerY, int innerWidthPx, int innerHeightPx,
        // NEW — bottom-region safe area for text overlay
        int bottomRegionX,
        int bottomRegionY,
        int bottomRegionWidthPx,
        int bottomRegionHeightPx,
        boolean loaded
) { ... }
```

**Rules** (validated in compact constructor when `loaded=true`):

- `bottomRegionWidthPx > 0 && bottomRegionHeightPx > 0`.
- `bottomRegionX ≥ 0`, `bottomRegionY ≥ innerY + innerHeightPx`
  (the bottom region sits **below** the inner cutout).
- `bottomRegionX + bottomRegionWidthPx ≤ canvasWidthPx`,
  `bottomRegionY + bottomRegionHeightPx ≤ canvasHeightPx`.

**Loader logic** (extends `PosterFrameAssetLoader.load()`):

```text
1. Existing: scan alpha=0 pixels → innerX, innerY, innerWidthPx, innerHeightPx.
2. NEW: derive
     bottomRegionX = innerX + canvasWidthPx * 0.04          // 4% inset L
     bottomRegionWidthPx = innerWidthPx - 2 * (canvasWidthPx * 0.04)
     bottomRegionY = innerY + innerHeightPx + canvasHeightPx * 0.06   // 6% gap
     bottomRegionHeightPx = canvasHeightPx - bottomRegionY - canvasHeightPx * 0.06
3. If bottomRegionHeightPx ≤ minSizePx, log WARN
   event=frame.asset.bottom_region_too_small and downgrade to
   PosterFrameAsset.missing() — so the overlay no-ops gracefully and
   the frame still renders.
```

**Migration / asset compatibility**: For the current asset
(canvas 1024×1536, inner cutout (116, 124, 783, 1057)) the derived
bottom region is approximately:

```text
bottomRegionX      = 116 + 41 ≈ 157
bottomRegionY      = 124 + 1057 + 92 ≈ 1273
bottomRegionWidthPx ≈ 783 - 82 ≈ 701
bottomRegionHeightPx ≈ 1536 - 1273 - 92 ≈ 171
```

…which is large enough for three lines at the target sizes (96 / 48
/ 28 px) with `lineGapPx=12`: total natural stack ≈ 96 + 12 + 48 + 12
+ 28 = 196 px. If the natural stack exceeds the safe height, sizes
shrink proportionally per FR-1705 / FR-1715 (covered by the fitter,
below).

## Pure helper

### `PosterTextFitter`

Stateless utility — given a `PosterTextLines`, a `PosterTextStyle`, a
`PosterFrameAsset`, and a `Font`, produces a list of "what to render
where" tuples (one per non-empty line):

```java
public final class PosterTextFitter {
    public record Fitted(String text, float sizePx, int baselineX, int baselineY) {}

    public static List<Fitted> fit(
            PosterTextLines lines,
            PosterTextStyle style,
            PosterFrameAsset asset,
            Font baseFont,
            FontRenderContext frc
    ) { /* see algorithm */ }
}
```

**Algorithm (Q4 → shrink-only, FR-1705 + FR-1715)**:

```text
For each non-empty line {firstName, title, tagline} (in that order):
  size = role-specific target from style
  loop:
    width = TextLayout(text, font.deriveFont(size), frc).getAdvance()
    if width <= bottomRegionWidthPx — bail out, size accepted
    size -= 1.0
    if size <= style.minSizePx — accept anyway (FR-1704: never drop / clip)

After per-line shrinking, enforce hierarchy invariant FR-1715:
  if title.size > name.size:    title.size = name.size
  if tagline.size > title.size: tagline.size = title.size

Position:
  Compute total stack height = sum(line.size) + (#lines − 1) * lineGapPx.
  If stack > bottomRegionHeightPx, scale every line.size by
    (bottomRegionHeightPx / stack), clamped to >= minSizePx.
    Re-validate FR-1715 (cascade clamps).
  Centre the stack vertically inside bottomRegion.
  Each line is horizontally centred at bottomRegionX + bottomRegionWidthPx/2,
    baseline computed from FontMetrics on the per-line size.
```

**Pure / deterministic**: No I/O, no mutation, no system clock. The
test (`PosterTextFitterTest`) covers:

- Short inputs → all three lines render at target sizes.
- Long name → name shrinks; title and tagline are clamped down to the
  shrunk name size (hierarchy preservation).
- Long tagline → tagline shrinks; name + title untouched.
- Stack-too-tall → proportional shrink with hierarchy maintained.
- Empty line in the middle (e.g. empty title) → the row is skipped;
  the remaining two render centred.
- All three empty → returns empty list (overlay step no-ops).

## Service

### `PosterTextOverlayService`

```java
@Component
public class PosterTextOverlayService {
    public PosterImage apply(PosterImage framed, PosterTextLines lines) { ... }
}
```

**Pipeline**:

1. If the frame asset is `!loaded()` (i.e. frame overlay also no-op'd),
   return `framed` unchanged + WARN
   `event=text.apply.skipped reason=asset_missing`.
2. If the bundled font failed to load and the logical fallback also
   failed, return `framed` unchanged + WARN
   `event=text.apply.skipped reason=font_missing`.
3. If `lines` are all empty, return `framed` unchanged (no WARN — this
   is a legitimate path under defensive coercion).
4. Decode `framed.bytes()` into a `BufferedImage`. If decode returns
   null, return `framed` unchanged + WARN
   `event=text.apply.failed reason=decode_returned_null`.
5. Run the fitter to compute the per-line sizes and positions.
6. For each fitted line, render its glyph outline (R1 — stroke then
   fill).
7. Encode the canvas as PNG, return new `PosterImage`.
8. Catch any `RuntimeException | IOException` → WARN
   `event=text.apply.failed reason=exception` + return `framed` unchanged
   (FR-1716 fail-soft, mirroring 015 FR-1512).

**Threading from `AlterEgoService`**:

- Real-provider path:
  `posterTextOverlay.apply(framed, new PosterTextLines(request.firstName(), character.heroTitleLine1(), character.tagline()))`.
- Fallback path: same, with the fallback `character`.

## State transitions

None — this feature is stateless within a request. The frame asset
and font are loaded once at startup; both are immutable for the JVM
lifetime.

## Validation rules summary

| Rule | Source | Enforced where |
|---|---|---|
| `firstName`, `title`, `tagline` are stripped of whitespace | Edge Cases | `PosterTextLines` constructor |
| Null strings are coerced to empty | Defensive | `PosterTextLines` constructor |
| Empty line is skipped, not rendered as a blank row | Edge Cases | `PosterTextFitter` |
| Hierarchy `name ≥ title ≥ tagline` (rendered px) | FR-1715 | `PosterTextFitter` (post-shrink clamp) |
| Hard minimum size `minSizePx` | FR-1705 | `PosterTextFitter` |
| Each line stays on a single row | FR-1705 (Q4 → B) | `PosterTextFitter` (no `LineBreakMeasurer`) |
| Glyph drop-shadow / outline halo | FR-1716 (Q5 → C) | `PosterTextOverlayService` |
| 4.5:1 contrast (glyph-fill vs halo) | FR-1707 | `PosterTextStyle.defaults()` colour pair |
| No persistence; strings memory-only | FR-1711 | Architecture (no DB write site touched) |
| No log line carries the strings | FR-1711 | Service log lines (only event/reason fields) |
| Fail-soft on any error | FR-1716 + 015 carryover | Service catch-blocks |

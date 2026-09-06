# Data Model — 015: Branded Poster Frame & 10×15 Print-Ready Aspect Ratio

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Research**: [research.md](./research.md) · **Date**: 2026-05-05

This feature touches **process-internal model state only**. No persistence (001 FR-016 carries through), no new HTTP request or response shape, no new database table — the spec's "Storage: N/A" is honoured. The two new in-memory entities below replace the 008 `LogoAsset` / `LogoId` records and live in the new `com.aiavatar.alterego.service.frame` package.

---

## Entity 1 — `PosterFrameAsset`

The single bundled frame PNG decoded once at backend startup and reused by every Generate request. Mirrors 008's `LogoAsset` record structurally, but carries the frame's transparent-inner-rectangle bounding box (computed at load time) so the overlay service does not have to re-discover it on every request.

### Java shape

```java
package com.aiavatar.alterego.service.frame;

import java.awt.image.BufferedImage;
import java.util.Objects;

public record PosterFrameAsset(
        BufferedImage image,        // RGBA, frame's full canvas (e.g. 1024×1536)
        int canvasWidthPx,          // image.getWidth()  — pinned for convenience
        int canvasHeightPx,         // image.getHeight() — pinned for convenience
        int innerX,                 // bounding box of alpha==0 pixels
        int innerY,
        int innerWidthPx,
        int innerHeightPx,
        boolean loaded              // false → asset missing or undecodable; overlay degrades to un-framed (FR-1512)
) {
    public PosterFrameAsset {
        if (loaded) {
            Objects.requireNonNull(image, "image must be non-null when loaded=true");
            if (canvasWidthPx <= 0 || canvasHeightPx <= 0) {
                throw new IllegalArgumentException("canvas dimensions must be positive when loaded=true");
            }
            if (innerWidthPx <= 0 || innerHeightPx <= 0) {
                throw new IllegalArgumentException("inner rectangle must be non-empty when loaded=true");
            }
            if (innerX < 0 || innerY < 0
                    || innerX + innerWidthPx > canvasWidthPx
                    || innerY + innerHeightPx > canvasHeightPx) {
                throw new IllegalArgumentException("inner rectangle must lie inside the canvas");
            }
        }
    }

    public static PosterFrameAsset loaded(BufferedImage image, int innerX, int innerY,
                                          int innerWidthPx, int innerHeightPx) {
        return new PosterFrameAsset(image,
                image.getWidth(), image.getHeight(),
                innerX, innerY, innerWidthPx, innerHeightPx,
                true);
    }

    public static PosterFrameAsset missing() {
        return new PosterFrameAsset(null, 0, 0, 0, 0, 0, 0, false);
    }
}
```

### Field semantics

| Field | Meaning | Sourced from |
|---|---|---|
| `image` | Decoded `BufferedImage` (RGBA), the frame's full canvas. `null` when `loaded=false`. | `ImageIO.read(ClassPathResource("branding/poster-frame.png").getInputStream())` |
| `canvasWidthPx` / `canvasHeightPx` | Frame's full canvas dimensions in pixels. The composited poster's final dimensions match these. | `image.getWidth()` / `image.getHeight()` |
| `innerX`, `innerY`, `innerWidthPx`, `innerHeightPx` | Axis-aligned bounding box of fully-transparent (`alpha == 0`) pixels in the frame asset. The character image is drawn into this rectangle. | Computed at `@PostConstruct` time by `PosterFrameAssetLoader` (R-1503). |
| `loaded` | `true` if the asset decoded cleanly and a non-empty transparent inner region was found; `false` otherwise. The overlay service short-circuits to "return un-framed input" when `loaded=false` (FR-1512). | `PosterFrameAssetLoader.@PostConstruct` |

### Lifecycle / validation rules

- **Load timing**: Exactly one decode at JVM startup via `@PostConstruct` on `PosterFrameAssetLoader`. SC-1507 enforces this — across 20 consecutive Generate calls, the asset is read from the classpath exactly once.
- **No reload**: There is no API or admin endpoint to reload the asset; a brand revision requires a backend restart. This matches the 008 posture and is acceptable for the booth deployment cadence.
- **Failure mode**: Resource missing, `ImageIO.read` returns null, decode IOException, or no transparent inner region detected (e.g. an opaque-everywhere asset accidentally shipped) all collapse to `PosterFrameAsset.missing()` plus a `WARN` log line. The backend starts normally; FR-1512 covers the user-facing degraded-mode behaviour.
- **Immutability**: `PosterFrameAsset` is a record — fields are final. The `BufferedImage` reference itself is mutable (Java AWT's design), but the overlay service only reads it (`Graphics2D.drawImage(asset.image(), ...)`); no concurrent writers.

---

## Entity 2 — `FramedPoster` (logical entity, not a separate type)

The poster image after the character image has been composited into the frame's inner rectangle and the frame's chrome has been drawn on top. **Not a new Java type** — it occupies the same `PosterImage` slot as 003's output:

```java
// com.aiavatar.alterego.model.PosterImage  (unchanged from 003)
public record PosterImage(
        byte[] bytes,            // PNG-encoded composited canvas
        String mediaType,        // ALWAYS "image/png" after this feature (FR-1509)
        int widthPx,             // matches PosterFrameAsset.canvasWidthPx
        int heightPx             // matches PosterFrameAsset.canvasHeightPx
) { … }
```

### Pinned values for the framed-poster instance

| Field | Pinned value (after framing) | Why pinned |
|---|---|---|
| `mediaType` | `"image/png"` | FR-1509 — alpha preservation requires PNG. |
| `widthPx` | `1024` (with the bundled asset; whatever the asset's canvas is in general) | The framed canvas IS the frame asset's canvas. |
| `heightPx` | `1536` (ditto) | Same as above. |
| `widthPx : heightPx` | exactly `2 : 3` (10×15 portrait) within ≤1% rounding tolerance | FR-1507 / SC-1503. |
| `bytes` | PNG-encoded composite; first 8 bytes are the PNG signature `89 50 4E 47 0D 0A 1A 0A`. | FR-1509 + the existing PosterImage validator. |

### Composition pipeline (what produces the FramedPoster bytes)

```
ImageGenerator.generate(...) or FallbackPosterProvider.poster(...)  (003 / 001)
       ↓ returns PosterImage (input)
PosterFrameOverlayService.apply(input)
       │   1. If !asset.loaded()           → log WARN + return input unchanged (FR-1512 fail-soft).
       │   2. ImageIO.read(input.bytes())  → BufferedImage character.
       │   3. Allocate canvas = new BufferedImage(asset.canvasWidthPx, asset.canvasHeightPx, TYPE_INT_ARGB).
       │   4. g = canvas.createGraphics() with bicubic interpolation + antialiasing.
       │   5. Compute fitted draw rect inside (asset.innerX/Y, asset.innerWidthPx/HeightPx)
       │      preserving character.aspect (= character.width / character.height).
       │      If |character.aspect / (2/3) - 1| > 0.01: log WARN "ratio mismatch" (FR-1511).
       │   6. g.drawImage(character, fittedX, fittedY, fittedW, fittedH, null).
       │   7. g.drawImage(asset.image(), 0, 0, null).
       │   8. ImageIO.write(canvas, "png", out) → bytes.
       │   9. Catch any RuntimeException/IOException → log WARN + return input unchanged (FR-1512).
       ↓ returns PosterImage (PNG-encoded, framed, 2:3)
AlterEgoService.generate(...) → AlterEgoResponse
```

---

## What is **not** introduced

To make the bounded scope explicit:

- **No new HTTP endpoint, no new HTTP method, no new field on `AlterEgoRequest` / `AlterEgoResponse`.** The framed bytes ride inside the existing `Poster` field.
- **No new persisted entity, no DB migration.** 001 FR-016 (no persistence) holds. JPA / Hibernate / SQLite stack remains unwired (CLAUDE.md "Active Technologies" line).
- **No new configuration property, no new env var.** The frame asset path is a constant in `PosterFrameAssetLoader`, mirroring 008's `LogoId` enum's `classpathResource` field.
- **No new frontend type, no new TypeScript model.** The frontend's `<img>` consumes the existing data URL (002 poster slot); the only observable change is that the data URL is now always `data:image/png;base64,…`.

---

## Removed entities (from 008)

| Entity | Disposition |
|---|---|
| `service.branding.LogoId` (enum: `SQUER`, `CODECRAFTS`, `classpathResource`) | **Deleted.** R-1506. |
| `service.branding.LogoAsset` (record: `id, image, sourceWidthPx, sourceHeightPx, loaded`) | **Deleted.** R-1506. |
| `service.branding.LogoAssetLoader` (`@PostConstruct` two-asset loader) | **Deleted.** R-1506. |
| `service.branding.BrandingOverlayService` (top-right two-logo composite) | **Deleted.** R-1506. |
| `branding/squer-logo.png` resource | **Deleted.** R-1506. |
| `branding/codecrafts-logo.png` resource | **Deleted.** R-1506. |

Git history retains the 008 implementation for archaeological reference; recovery cost is `git show <pre-015-commit>` on those paths.

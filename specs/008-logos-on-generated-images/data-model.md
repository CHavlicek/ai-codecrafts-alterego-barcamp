# Data Model — 008: Logo Branding Overlay on Generated Alter-Ego Images

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Research**: [research.md](./research.md) · **Date**: 2026-04-24

This feature adds **no persistent data, no DB rows, no API fields**. The two entities below are pure in-process constructs and never cross a wire or storage boundary.

---

## LogoAsset

A decoded, ready-to-composite logo image held in memory for the lifetime of the backend process.

| Field | Type | Notes |
|---|---|---|
| `id` | `enum LogoId { SQUER, CODECRAFTS }` | Stable identifier used for logging and for ordering in the overlay stack (SQUER above CodeCrafts). |
| `classpathResource` | `String` | Fixed per-logo classpath path: `"branding/squer-logo.png"` or `"branding/codecrafts-logo.png"`. Used by `ClassPathResource` at `@PostConstruct`. |
| `image` | `java.awt.image.BufferedImage` (ARGB) | Decoded once at bean init; reused on every request. Never mutated (only read by `Graphics2D.drawImage`). |
| `sourceWidthPx` | `int` | `image.getWidth()` — used to compute aspect-preserving height when scaling. |
| `sourceHeightPx` | `int` | `image.getHeight()`. |
| `loaded` | `boolean` | `true` if decode succeeded at bean init. If `false`, the asset is skipped by `BrandingOverlayService` (fail-soft, R-705). |

**Validation / invariants**:

- `image` is `null` iff `loaded == false`.
- `sourceWidthPx > 0 && sourceHeightPx > 0` whenever `loaded == true`.
- `LogoAsset` instances are effectively immutable after construction; no thread-safety synchronization needed (R-704).

**Lifecycle**:

- **Created** exactly once, during `LogoAssetLoader`'s `@PostConstruct` (one instance per `LogoId`).
- **Consumed** by `BrandingOverlayService.apply(PosterImage)` on every Generate request.
- **Destroyed** when the Spring context shuts down (JVM exit).

**No persistence, ever** — consistent with 001 FR-016 / 017 / 020 / 024. Logo bytes are part of the JAR artefact, not user data.

---

## BrandedPoster

The composited output of `BrandingOverlayService.apply(PosterImage)`. Not a new record type at the API layer — it re-uses the existing `PosterImage` record; this entity is documented for clarity about the transform's contract.

| Field | Type | Notes |
|---|---|---|
| `bytes` | `byte[]` | Re-encoded poster. Same MIME family as input (PNG→PNG, JPEG→JPEG) per FR-710. Differs from input bytes only in the top-right overlay region. |
| `mediaType` | `String` | Identical to the input's `mediaType`. Validated by `PosterImage`'s own constructor (`image/png` or `image/jpeg` only). |
| `widthPx` | `int` | Identical to the input's `widthPx` — FR-709 requires pixel dimensions be preserved. |
| `heightPx` | `int` | Identical to the input's `heightPx`. |

**Transformation rule** (for every pixel `(x, y)` in the input):

```text
if (x, y) ∈ topRightOverlayRegion AND at least one LogoAsset has loaded == true:
    output[x, y] = composite(input[x, y], logoPixel(x, y))   // standard alpha-over blend
else:
    output[x, y] = input[x, y]
```

where `topRightOverlayRegion` is the union of two rectangles anchored to the top-right corner, geometry per **R-703**:

- **SQUER rectangle**: right edge at `widthPx - round(0.04 × widthPx)`, top edge at `round(0.04 × widthPx)`, width = `max(48, round(0.15 × widthPx))`, height derived from source aspect ratio.
- **CodeCrafts rectangle**: right edge aligned with SQUER rectangle, top edge at `(SQUER top) + (SQUER height) + round(0.02 × widthPx)`, same width, height derived from its own source aspect ratio.

**State transitions**: none — compositing is pure (output is a function of `(input, LogoAsset[SQUER], LogoAsset[CODECRAFTS])`).

**Relationship to existing entities**:

- Consumes a `PosterImage` (from 003) and two `LogoAsset` instances; produces a `PosterImage` with identical `mediaType`, `widthPx`, `heightPx` but different `bytes`.
- Feeds into `AlterEgoResponse.Poster.fromImage(...)` unchanged (002 wire shape).

---

## No new entities on the wire

- `AlterEgoRequest` — unchanged. The request does **not** gain a "brand / no-brand" toggle; FR-708 makes branding unconditional.
- `AlterEgoResponse` / `AlterEgoResponse.Poster` / `AlterEgoResponse.ResponseMeta` — unchanged. Only the bytes inside `Poster.dataUrl` differ. The `outcome` / `reason` fields (003) continue to report the generation outcome; branding success/failure is an operator-log concern only (FR-711).

---

## Error / degraded states

| State | Trigger | Observable behaviour |
|---|---|---|
| **Both logos loaded** | Startup succeeded for both classpath resources. | Every poster carries both logos. Normal operation. |
| **One logo loaded, one missing/corrupt** | `ClassPathResource.exists() == false` or `ImageIO.read` returned `null` for one. | Every poster carries the one that loaded; the missing one is skipped. `WARN` logged at startup. |
| **Both logos missing/corrupt** | Both failed at startup. | `BrandingOverlayService.apply` short-circuits and returns the input poster unchanged. `WARN` logged at startup. |
| **Per-request composite failure** | Input `PosterImage.bytes` fails `ImageIO.read`, or `Graphics2D` throws, or `ImageIO.write` returns `false`. | `WARN` logged with correlation ID + throwable; input poster returned unchanged. |

All error states preserve the public HTTP contract: HTTP 200 with a valid `poster.dataUrl` (FR-711, SC-706).

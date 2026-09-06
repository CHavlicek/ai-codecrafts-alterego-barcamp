# Branding assets — operational notes

This directory carries the assets composited into every generated alter-ego poster:

- **`poster-frame.png`** — the chrome (introduced in 015-frame-overlay; supersedes 008's logo overlay).
- **`fonts/Unbounded-Bold.ttf`** + **`fonts/Geist-Regular.ttf`** + **`fonts/Geist-Italic.ttf`** — the three bundled typefaces used by the 017 text-overlay step (`PosterTextOverlayService`). The trio mirrors the frontend's `--font-display` (Unbounded) + `--font-body` (Geist) typographic system so the baked-on credits read as a brand artefact rather than generic sans. See [`fonts/LICENSE.txt`](./fonts/LICENSE.txt) for the upstream SIL Open Font License v1.1 notices for both projects. The fonts are loaded once at JVM startup by `PosterTextFonts`; if any TTF is missing or fails to decode, that slot falls through to JDK logical `Font.SANS_SERIF` plus a `WARN event=text.font.fallback` log line. Production deploys on Alpine-based JREs (no `fontconfig`, no system fonts) MUST keep the bundled TTFs — otherwise the fallback path renders unreadable square-glyph boxes on Alpine.

## Poster frame chrome (`poster-frame.png`)

The bundled `poster-frame.png` is the chrome that wraps every generated alter-ego poster (introduced in 015-frame-overlay; supersedes the two-logo overlay from 008). This README is the authoritative checklist for the next person who wants to **swap the asset** or **change the design's aspect ratio**.

## What the asset is

- **File**: `poster-frame.png` in this directory.
- **Format**: PNG, RGBA (alpha channel required).
- **Current canvas**: 1024 × 1536 pixels (2:3 portrait).
- **Inner cutout**: a fully transparent rounded rectangle (alpha=0) where the generated character image is composited. Currently a 783 × 1057 bounding box at `(116, 124)` — ~3:4 portrait, sitting in the upper portion of the canvas with a "long bottom" black region below carrying the chrome. Computed at startup by `PosterFrameAssetLoader.computeAlphaZeroBoundingBox(...)` — bounds are derived from the asset, not hard-coded.
- **Chrome**: SQUER mark (top-left), `<CODE/CRAFTS> 2026` wordmark (top-right), gradient rounded border around the cutout, decorative dot/circuit-line patterns down both side margins, additional decorative patterns in the bottom black region. All baked into the alpha-non-zero pixels of this PNG.

## Swapping the asset (no aspect-ratio change)

If the chrome design changes but the **aspect ratio stays the same**:

1. Replace `poster-frame.png` with the new RGBA PNG. Keep the canvas at 1024 × 1536 (or any 2:3 portrait size — the loader handles arbitrary canvas dimensions).
2. Confirm the inner area is **fully transparent** (alpha=0). If your authoring tool exports it opaque-white, run the flood-fill recipe at the bottom of this README.
3. Run `./gradlew :backend:test` — the asset-loader test asserts the inner-rectangle bounding box falls in plausible bands; if your new design moves the cutout significantly, those bands may need widening (`PosterFrameAssetLoaderTest#loadedHappyPathDecodesRgbaAndCachesAsset`).
4. `docker compose up --build` (or `npm run dev` + `./gradlew :backend:bootRun`) and visually verify a Generate.

That's it. No other code changes needed.

## Changing the design aspect ratio

If the new frame has a **different aspect ratio** (e.g. moving from 2:3 / 10×15 to 4:5 / 8×10 or 9:16 / 1080×1920), several call sites move together. Miss any one of them and either the AI returns a wrong-aspect image (logged as a `WARN event=frame.apply.ratio_mismatch` and letterboxed inside the new frame) or the frontend container crops the framed image.

### The five places that must change in lockstep

| # | File | What to change | Why |
|---|------|----------------|-----|
| 1 | `poster-frame.png` (this directory) | New RGBA PNG at the new canvas size. Inner cutout transparent. | The asset IS the design. |
| 2 | `backend/.../service/gemini/GeminiPromptBuilder.java`, `buildSingle()` and `buildGroup()` | Replace `"2:3 aspect ratio (10×15 vertical, print-ready)"` with the new ratio's wording in the `Composition notes:` line. Update **both** SINGLE and GROUP variants. | The prompt is the load-bearing channel for asking Gemini to emit at the new ratio — Gemini honours prompt text more reliably than the typed `imageConfig` parameter. |
| 3 | `backend/.../service/gemini/GeminiClient.java`, `renderRequestBody(...)` | `generationConfig.putObject("imageConfig").put("aspectRatio", "2:3")` — replace `"2:3"` with the new ratio. Gemini's typed enum currently accepts `1:1`, `3:4`, `4:3`, `9:16`, `16:9` (newer models add more). If your new ratio isn't in the enum, leave the prompt-text channel doing the work and remove this line. | Belt-and-braces signal to the provider. Models that don't recognise the field ignore it; models that do recognise it use it as the primary hint. |
| 4 | `backend/.../service/frame/PosterFrameOverlayService.java`, `TARGET_ASPECT` constant | `private static final double TARGET_ASPECT = 2.0 / 3.0;` — replace with the new ratio (e.g. `4.0 / 5.0`). | The fit logic in `fitCharacterRect(...)` computes the smallest rect of this aspect ratio that **contains** the asset's transparent inner bbox, then draws the character into that rect. A mismatch between the AI's output ratio and `TARGET_ASPECT` triggers letterboxing inside the frame. |
| 5 | `backend/.../service/fallback/FallbackPosterProvider.java`, `POSTER_WIDTH` / `POSTER_HEIGHT` constants | Set to a width × height pair at the **new ratio** that matches the new asset's canvas (e.g. `1024 × 1536` for 2:3, `1024 × 1280` for 4:5). | The fallback poster needs to emit at the new ratio natively, otherwise every fallback-path Generate emits a `WARN event=frame.apply.ratio_mismatch`. |
| 6 | `frontend/src/index.css`, `.poster-view__frame { aspect-ratio: 2 / 3 }` | Replace `2 / 3` with the new ratio. | The poster `<img>` is rendered with `object-fit: cover` inside the frame container; if the container's aspect doesn't match the source, the visible image is cropped. |

### Tests that will need updates

`grep -rn "1024\|1536\|2 / 3\|2:3" backend/src/test frontend/src` to find all the assertions on hard-coded ratio values. Concrete files at the time of writing:

- `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoIT.java` — asserts `body.poster().widthPx() == 1024 && heightPx == 1536`.
- `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoFallbackIT.java` — same assertion on the fallback path.
- `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiIT.java` — same.
- `backend/src/test/java/com/aiavatar/alterego/integration/PosterFrameOverlayIT.java` / `PosterFrameOverlayFallbackIT.java` — assert canvas dimensions + ratio tolerance.
- `backend/src/test/java/com/aiavatar/alterego/integration/PosterFrameOverlayDegradedIT.java` — asserts the un-framed pass-through is **smaller** than 1024×1536; bound moves with the asset.
- `backend/src/test/java/com/aiavatar/alterego/contract/AlterEgoControllerContractTest.java` — `jsonPath("$.poster.widthPx").value(1024)` etc.
- `backend/src/test/java/com/aiavatar/alterego/unit/FallbackPosterProviderTest.java` — `posterReturnsValidPng` and `posterRatioIsTwoToThreePortrait` (the latter's `Math.abs(ratio - 1.5) <= 0.015` becomes `Math.abs(ratio - <new>) <= 0.015`).
- `backend/src/test/java/com/aiavatar/alterego/service/frame/PosterFrameOverlayServiceTest.java` — the `letterboxesWrongRatioInputInsideTwoToThreeFitRect` test rebuilds the fit-rect geometry locally; rename + update the math when `TARGET_ASPECT` changes.
- `backend/src/test/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilderTest.java` and `GeminiClientAspectRatioTest.java` — assert the literal `"2:3 aspect ratio"` substring and `"aspectRatio":"2:3"` JSON.
- `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java` — full-prompt regression-lock string.

The full-prompt regression-lock test (`unit/gemini/GeminiPromptBuilderTest.java`) is the most labour-intensive — its `expected` text-block must be updated to the byte-identical new prompt body.

### Verification after the change

1. `./gradlew :backend:test` → all green. Coverage on `service.frame` package stays ≥ 90% per Constitution Principle III.
2. `cd frontend && npm test` → all green.
3. Manual: `./gradlew :backend:bootRun` + `npm run dev` → run one Generate; visually confirm the new frame chrome wraps the character image edge-to-edge with no gaps.
4. Verify the outbound prompt body in the backend log contains the new ratio wording (e.g. `2:3 aspect ratio` was the old literal — search for the new one).

## Inner-cutout transparency: flood-fill recipe

If you receive a new frame asset that is RGB (no alpha) or whose inner cutout is opaque-white instead of transparent, run this recipe to make the inner region truly transparent. The repository's loader requires `alpha == 0` pixels to discover the inner cutout.

```sh
python3 -m venv /tmp/pil-venv && /tmp/pil-venv/bin/pip install --quiet pillow
/tmp/pil-venv/bin/python <<'PY'
from PIL import Image
from collections import deque
import os

src = "backend/src/main/resources/branding/poster-frame.png"
img = Image.open(src).convert("RGBA")
w, h = img.size
px = img.load()

# Flood-fill from canvas centre: any near-white pixel reachable from there
# (i.e. the inner area) becomes fully transparent (alpha=0). Chrome regions
# (text, gradient border, decorative patterns) are not reachable from the
# centre because they're separated from the inner area by anti-aliased
# black/coloured pixels — so they remain opaque.
THRESHOLD = 230  # min R/G/B channel value to be considered "inner white"
seed = (w // 2, h // 2)

def is_white(rgba):
    r, g, b, a = rgba
    return a == 255 and r >= THRESHOLD and g >= THRESHOLD and b >= THRESHOLD

if not is_white(px[seed]):
    raise SystemExit(f"seed {seed} is not white; check the asset has a white inner area: {px[seed]}")

visited = bytearray(w * h)
q = deque([seed])
visited[seed[1] * w + seed[0]] = 1
filled = 0
while q:
    x, y = q.popleft()
    px[x, y] = (0, 0, 0, 0)
    filled += 1
    for nx, ny in ((x+1, y), (x-1, y), (x, y+1), (x, y-1)):
        if 0 <= nx < w and 0 <= ny < h:
            idx = ny * w + nx
            if not visited[idx] and is_white(px[nx, ny]):
                visited[idx] = 1
                q.append((nx, ny))
img.save(src, optimize=True)
print(f"transparent_pixels={filled}/{w*h} ({100.0*filled/(w*h):.1f}%)")
PY
```

If the new asset has a different "inner colour" (e.g. light grey instead of white), bump or replace the `THRESHOLD` accordingly. If the chrome happens to also have light pixels and they're connected to the inner area, the flood-fill will erase those too — fix the asset upstream (insert a hairline boundary) or use a more sophisticated mask.

## Why this lives here

This README is **operational** — it's the runbook for the next person to swap the asset. It deliberately doesn't duplicate the spec narrative (see `specs/015-frame-overlay/` for that). The spec captures **what** and **why**; this captures **how** to maintain it.

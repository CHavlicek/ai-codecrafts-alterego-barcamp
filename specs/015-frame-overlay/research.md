# Research — 015: Branded Poster Frame & 10×15 Print-Ready Aspect Ratio

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Date**: 2026-05-05

Every decision below resolves one "pinned at planning time" item left open by the spec or one "best-practice" question raised by the plan. Each entry follows the mandated format: **Decision / Rationale / Alternatives considered**.

---

## R-1501 — Aspect-ratio expression to Gemini: prompt text **plus** best-effort typed parameter

- **Decision**: Express the 10×15 / portrait 2:3 ratio in **both** channels:
  1. **Prompt text** — `GeminiPromptBuilder.buildSingle(...)` and `buildGroup(...)` change "Portrait orientation, 3:4 aspect ratio, dramatic rim lighting." → "**Portrait orientation, 2:3 aspect ratio (10×15 vertical, print-ready).** Dramatic rim lighting." in both variants. This is the load-bearing channel — the prompt is what the model honours most reliably.
  2. **Typed `imageConfig.aspectRatio`** — `GeminiClient.renderRequestBody(...)` adds `generationConfig.imageConfig.aspectRatio = "2:3"`. This is best-effort: if the model accepts it, it's an additional signal; if the model ignores or errors on it (older preview models accept only a 5-value enum: `1:1`/`3:4`/`4:3`/`9:16`/`16:9`), the corrective behaviour in FR-1511 letterboxes the response inside the frame's inner rectangle.
- **Rationale**:
  - The issue's lead requirement — *"The ratio should be passed to the image generation ai, not applied afterwards"* — is satisfied at the prompt level (the load-bearing channel on Gemini's `generateContent` for the image-generation models we use). Adding the typed parameter is belt-and-braces.
  - The corrective letterbox in FR-1511 is the safety net, not the primary mechanism (FR-1508 explicitly forbids using crop/pad as the primary mechanism). This decision is consistent with that.
  - Single-source the prompt change in `GeminiPromptBuilder` (the existing 003 entry point); single-source the typed-parameter change in `GeminiClient.renderRequestBody(...)` so that both Gemini-bound paths emit the same request shape.
- **Alternatives considered**:
  - **Prompt-only.** Rejected: leaves the typed channel unset when the model could honour it. Belt-and-braces is cheap.
  - **Typed-parameter-only, no prompt mention.** Rejected: if the model rejects "2:3" as not-in-enum, we fall back to a non-conforming response and pay the corrective letterbox cost on every request.
  - **Crop-after-the-fact in `GeminiClient.decodeImagePart(...)`.** Rejected: the issue is explicit that the ratio is asked of the AI, not imposed after; FR-1508 forbids this as the primary mechanism. Reserved for the corrective fallback only (FR-1511).

## R-1502 — Compositing strategy: frame canvas, character image sampled in

- **Decision**: The frame asset's pixel rectangle (1024×1536) is the **target canvas** for the composited poster. Compositing draws the character image first (scaled to fit the frame's transparent inner rectangle while preserving the character image's 2:3 aspect — letterboxing inside the inner rectangle if the character image arrived at a different ratio per FR-1511), then draws the frame asset on top so its alpha-anti-aliased chrome sits cleanly over the character image. The final canvas is encoded as PNG.
- **Rationale**:
  - The frame is the dominant geometry — its rounded gradient border, decorative dots, and wordmark live at fixed pixel positions inside its 1024×1536 canvas. Treating the frame as the canvas means the chrome stays pixel-perfect; only the character image is resampled (which is acceptable since modern Gemini image-gen output is already high-resolution).
  - Drawing the character image first and the frame second avoids needing a per-pixel mask: the frame's alpha channel is its own mask. Pixels inside the inner rectangle have alpha=0, so the character image shows through; pixels in the chrome region have alpha=255 (or a feathered alpha at the gradient edges), so the chrome paints on top.
  - The frame's transparent inner rectangle was prepared during `/speckit.specify` (the spec's note: flood-fill from centre, alpha=0 for white-reachable pixels). Implementation reads the frame's bounding box of alpha=0 pixels at startup to derive `innerX, innerY, innerW, innerH` once — see R-1503.
- **Alternatives considered**:
  - **Character image as canvas, frame scaled on top.** Rejected: the frame is a vector-style PNG with anti-aliased gradient and small decorative elements; resampling it down to the character image's native resolution would soften the chrome. Keeping the frame at native resolution and letting the character image be the resampled layer preserves the brand asset's quality.
  - **Per-pixel mask file** (a separate mask PNG alongside `poster-frame.png`). Rejected: the frame already encodes its own mask via its alpha channel; a separate mask is duplicated state.
  - **Two-step composite (real provider returns image at native res, frame composited at viewer time).** Rejected: places branding work on the frontend, contradicts the existing 008 architecture (overlay is backend-side), and would also need to know the frame asset URL on the frontend.

## R-1503 — Frame inner-rectangle discovery: bounding box of alpha=0 pixels at startup

- **Decision**: `PosterFrameAssetLoader.@PostConstruct` decodes the frame PNG once into a `BufferedImage` (RGBA) and computes the **axis-aligned bounding box of fully-transparent pixels** (i.e. the smallest rectangle containing all `alpha == 0` pixels). That rectangle is stored on the asset record as `innerX, innerY, innerW, innerH` and reused on every Generate. The character image is drawn into that rectangle.
- **Rationale**:
  - Robust to asset revisions: if a future brand revision moves the inner cutout, no code change is needed — the bounding box is computed from the asset itself.
  - Cheap: O(width × height) once at startup (1024×1536 = ~1.5M pixels = a few ms on a developer laptop).
  - Avoids hard-coding magic numbers like "inner rectangle starts at (95, 100)" in the overlay service, which would silently desync if the asset is replaced.
- **Alternatives considered**:
  - **Hard-code the bounding box in `PosterFrameAssetLoader`** (e.g. `innerX=95, innerY=100, innerW=834, innerH=1336`). Rejected: brittle. The spec explicitly allows asset swaps without a spec update (Assumption: "Authoritative frame asset … any swap … is an asset-level change that does not require a spec update"). Hard-coding would force every swap to also touch code.
  - **Asset metadata sidecar** (e.g. a `poster-frame.json` next to the PNG). Rejected: two files where one suffices; alpha bounding-box discovery is a 30-line method and removes the failure mode of "PNG and JSON drifted".

## R-1504 — Wrong-ratio corrective behaviour: letterbox **inside** the frame's inner rectangle

- **Decision**: When the provider returns an image whose `height / width` deviates more than 1% from 1.5 (i.e. not 2:3 portrait within the SC-1503 tolerance), the overlay service **scales the character image to fit the frame's inner rectangle preserving the character image's own aspect**, then centres it inside the inner rectangle. Empty space inside the inner rectangle (above/below or left/right of the character image, depending on the provider's ratio) is left **transparent** in the composited canvas — i.e. the colour the viewer sees in those bands is the page background (the conference web page's dark theme), since the inner rectangle of the frame is transparent and the character image only fills part of it. A `WARN`-level log line records the mismatch with the offending dimensions.
- **Rationale**:
  - **No subject crop** — FR-1511 explicitly forbids cropping the character's primary subject (face/torso) out of the frame. Letterboxing preserves the entire generated composition.
  - **Transparent letterbox bands** (rather than black or white bars) means the bands blend into the page; on a printed 10×15 photo, the bands print in the printer's "no ink" colour (typically white paper). That is the least-bad outcome — better than coloured bars that draw the eye, and better than padding the character image with stretched edge pixels (which looks artefactual).
  - **Inside the inner rectangle** — the bands sit *inside* the frame chrome, so the chrome itself remains correctly proportioned and pixel-perfect.
- **Alternatives considered**:
  - **Crop to 2:3 from the centre.** Rejected by FR-1511 — risks cutting off the subject.
  - **Stretch to 2:3.** Rejected — visually awful; faces become obviously squashed.
  - **Black or white solid bars.** Rejected — the conference page is dark-themed; black bars look like a glitch, white bars look like a printer error. Transparent is unobtrusive on both screen and print.

## R-1505 — Output encoding: always PNG; fallback canvas type ARGB

- **Decision**: The composited final poster is **always encoded as PNG** regardless of the provider's input MIME (FR-1509). The compositing canvas is `BufferedImage.TYPE_INT_ARGB` so the alpha channel is preserved through the composite (FR-1510). `ImageIO.write(canvas, "png", out)` is the only supported encoder for this feature; the input's `mediaType` is read for the decode step but ignored for encode.
  - The `PosterImage` returned by `PosterFrameOverlayService` therefore has `mediaType = "image/png"` unconditionally.
  - The 003 `PosterImage` validator already accepts `image/png` (alongside `image/jpeg`); no model change is required.
  - The frontend's data-URL consumer (002 poster slot) already handles `image/png` data URLs.
- **Rationale**:
  - The frame asset is RGBA with anti-aliased gradient edges; encoding to JPEG would discard the alpha and emit a hard rectangular white background behind the chrome, defeating the purpose. PNG preserves alpha losslessly.
  - JPEG output was a 008-era constraint to "preserve input MIME" so that a JPEG-emitting Gemini path stayed JPEG; that constraint is intentionally relaxed here (FR-1509) because the new frame requires PNG.
  - Slight payload-size increase per response (PNG > JPEG for photographic content) is acceptable: the booth pipeline is one Generate per attendee, not high-volume traffic.
- **Alternatives considered**:
  - **Conditionally encode as the input's MIME** (008's behaviour). Rejected: alpha cannot survive a JPEG round-trip; the chrome's gradient edges would acquire halo banding.
  - **Encode as WebP.** Rejected: not in PosterImage's validator's allowed set, would require a model change, and the frontend `<img>` already renders PNG cleanly.

## R-1506 — Disposition of the old 008 logo overlay code and assets: **delete**

- **Decision**: Delete (rather than keep as `@Deprecated` dead code) the entire 008 overlay path:
  - **Java**: delete `service/branding/BrandingOverlayService.java`, `LogoAsset.java`, `LogoAssetLoader.java`, `LogoId.java`, and `service/branding/` package itself.
  - **Tests**: delete `BrandingOverlayServiceTest.java`, `LogoAssetLoaderTest.java`, and `service/branding/` test directory.
  - **Resources**: delete `backend/src/main/resources/branding/squer-logo.png` and `backend/src/main/resources/branding/codecrafts-logo.png`.
  - **Wiring**: remove the `BrandingOverlayService` constructor parameter from `AlterEgoService` and replace it with `PosterFrameOverlayService`.
- **Rationale**:
  - **Spec FR-1505** is unambiguous: the old overlay step "MUST NOT be applied". Keeping the code as `@Deprecated` invites a future regression where someone re-wires it; deleting removes the temptation.
  - **Spec edge-case note** allows either keep-as-orphans or delete; deletion is the cleaner of the two and matches the project's house style (the 008 overlay is the only post-generation transform, and it's being replaced wholesale rather than extended).
  - **Constitution Principle VI ("zero deprecated dependencies")** is about external dependencies, not internal code, but the spirit applies: dead code is operational debt.
  - The git history preserves the 008 implementation for archaeological reference; recovery cost is `git show` on the deletion commit.
- **Alternatives considered**:
  - **Keep as `@Deprecated` with `@Component` removed.** Rejected: bloats the codebase; tests would need to be either kept (covering dead code) or deleted (leaving uncompiling code if tests are kept).
  - **Keep the asset PNGs as orphans.** Rejected: ~100 KB of dead bytes shipped in every backend artefact; harmless but wasteful and confusing on inspection.
  - **Repurpose the 008 `LogoAsset` record as the new `PosterFrameAsset`.** Rejected: the new entity carries inner-rectangle bounding-box fields that 008's record does not; an ad-hoc rename would obscure the structural difference. A clean new package (`service/frame/`) makes the supersession visible at the file-tree level.

## R-1507 — Fallback poster dimensions update: 900×1200 → 1024×1536

- **Decision**: Bump `FallbackPosterProvider.POSTER_WIDTH/HEIGHT` from `900×1200` (3:4) to `1024×1536` (2:3 — same canvas size as the frame asset). The accent border + text layout already uses pixel-relative positioning (margin from edges, centred text); the new dimensions only require recomputing the bordered rectangle's coordinates from the new bounds.
- **Rationale**:
  - **FR-1507 / FR-1513** require fallback posters to also arrive at 2:3 — measured at the response layer, before letterbox correction. The simplest way to honour this is for the fallback to **emit at 2:3 natively**, eliminating any post-hoc correction on the fallback path.
  - Matching the frame asset's 1024×1536 canvas size means the fallback path never triggers the corrective letterbox in `PosterFrameOverlayService` — the character image fits the inner rectangle exactly (modulo a small letterbox if the inner rectangle isn't itself 2:3, which is fine — see R-1504).
  - Reuses the existing draw code in `FallbackPosterProvider`; only two constants change. No layout regression risk.
- **Alternatives considered**:
  - **Keep fallback at 900×1200, let the corrective letterbox handle it.** Rejected: introduces a perpetual `WARN` log line on every fallback (one per Generate when Gemini is unreachable) — operational noise that obscures real provider misbehaviour.
  - **Bump to 2048×3072** (matching a likely Gemini output resolution). Rejected: doubles the fallback's memory footprint for no visible benefit; the fallback is a degraded-mode rendering, not a flagship artefact.

## R-1508 — Constitution coverage gate (Principle III, ≥90% line coverage on new code)

- **Decision**: The new `service/frame/` package is covered by:
  - **`PosterFrameAssetLoaderTest`** — `loaded` (frame asset present, RGBA, inner rectangle correctly computed), `missing` (resource not present → `LogoAsset.missing(id)`-style sentinel), `decode_failed` (corrupt bytes), `decode_returned_null` (ImageIO returns null), and `inner_rectangle_bounds_correct` (alpha=0 bounding box matches the asset's actual transparent rectangle).
  - **`PosterFrameOverlayServiceTest`** — happy path (input 2:3 → output 2:3, PNG, frame chrome composited, inner rectangle filled by character pixels); wrong-ratio corrective (input 1:1 → output 2:3 via letterbox inside inner rectangle, character primary subject not cropped, `WARN` log line emitted); missing asset (frame loader returns sentinel → un-framed but 2:3 PNG returned, `WARN` log line); always-PNG (input JPEG → output PNG); always-PNG (input PNG → output PNG); fallback poster path (1024×1536 input → output 1024×1536 framed PNG).
  - **`GeminiPromptBuilderTest`** — regression-lock the 2:3 wording in both SINGLE and GROUP variants; regression-lock that no remnant of "3:4" appears in the prompt; sanity-check the rest of the prompt body unchanged byte-identically modulo the ratio line (each variant covered by one byte-equality assertion against a captured expected string).
  - **`AlterEgoFlowFrameIntegrationTest`** (`@SpringBootTest`) — exercise the full Generate journey with a stub `GeminiClient` returning a 2:3 image; assert response `Poster` carries `mediaType=image/png`, `widthPx/heightPx` ratio = 1.5 ± tolerance, and at least one chrome pixel from the frame is present at a known coordinate (e.g. the SQUER mark's known pixel position); separate test forces the fallback path and asserts the same.
- **Rationale**: The four files together exhaust the FR-1501..FR-1513 surface area; line coverage on the new package will exceed 90% by construction (the only uncovered branches will be defensive `null`-checks on already-validated invariants). The integration test satisfies the constitution's "≥1 integration test exercising the complete user journey" gate.

## R-1509 — Print path (010) interaction: no change

- **Decision**: This feature does **not** touch the 010 print-alter-ego stylesheet or print-trigger code. Printing already invokes `window.print()` with a `@media print` rule that sizes the poster `<img>` to fit the page; because the poster now arrives at 2:3 from the backend, the print at 10×15 cm comes out edge-to-edge with no white bars.
- **Rationale**: The 010 stylesheet sets `width: 100%; height: auto;` (or equivalent) on the printed poster; aspect-ratio correctness is a property of the source image, not the print path. The 010 spec's success criteria (booth-station printability) are satisfied automatically once the source image is 2:3.
- **Alternatives considered**:
  - **Tighten the 010 print stylesheet to enforce 10×15 cm at @media print.** Out of scope here — the 010 path already does this generically; if it didn't, the right place to fix it would be a follow-up to 010, not 015.

## R-1510 — Verification of the 008 spec / docs disposition

- **Decision**: Leave the 008 spec, plan, research, data-model, quickstart, tasks, and checklists files **in place, unchanged**. The 008 feature directory is historical record. The spec's **"Supersedes"** entry in `015/spec.md` ("Dependencies" section) is the canonical signal that 008's overlay step is no longer applied at runtime after this feature lands.
- **Rationale**:
  - **SpecKit posture**: spec directories are append-only; once a feature ships, its spec is a frozen artefact for cross-referencing. Editing 008's spec to say "this is dead" would obscure the lineage.
  - **Operational signal lives in code, not docs**: the 008 overlay no longer running is enforced by deleting the code (R-1506), not by editing the spec.
- **Alternatives considered**:
  - **Mark 008's spec as `Status: Superseded` in its frontmatter.** Acceptable but not required; deferred unless the project later adopts a formal status taxonomy across spec directories.

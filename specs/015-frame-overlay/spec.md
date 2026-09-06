# Feature Specification: Branded Poster Frame & 10×15 Print-Ready Aspect Ratio

**Feature Branch**: `015-frame-overlay`
**Created**: 2026-05-05
**Status**: Draft
**Input**: GitHub issue [#39 — Apply a new Frame to every image](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/39). Exact request: *"Every generated image should be overlaid with a new frame (the one attached to this issue). Previous overlays (two logos) should not be used anymore. The generated image should be of a specific ratio: 10x15 vertical (for printing purposes). The ratio should be passed to the image generation ai, not applied afterwards."*

> **Relationship to earlier features.** This feature is a coordinated change to two parts of the existing pipeline: (1) it replaces the two-logo top-right overlay introduced in 008 with a single full-bleed frame asset that wraps the whole poster (SQUER mark, `<CODE/CRAFTS> 2026` wordmark, gradient border, decorative chrome — all baked into one PNG); and (2) it locks the generator's output aspect ratio to **10×15 portrait** (i.e. 2:3) by asking the image-generation provider for that ratio at the prompt level, rather than letting the provider pick freely or trimming on our side. It does not change the prompt's character/scene content (003 FR-201–204, 014 character generator), the response shape (003 FR-211–219), the fallback semantics (001 FR-018, 003 FR-214), the no-persistence posture (001 FR-016), or the frontend's poster rendering surface (002 poster slot). It supersedes 008's two-logo overlay; the SQUER and CodeCrafts logos still appear on every poster, but as part of the new frame rather than as separate corner overlays.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Every generated alter-ego is wrapped in the CodeCrafts × SQUER frame (Priority: P1)

When a conference attendee at the booth completes Setup and presses **Generate**, the poster that appears on "Your Alter Ego" is wrapped in the official event frame: the SQUER mark in the top-left, the `<CODE/CRAFTS> 2026` wordmark in the top-right, a blue→pink gradient border with rounded corners around the central image area, and decorative dot/circuit-line chrome down the left and right margins. The character image fills the inner area of the frame and is fully visible — the frame is transparent inside its border so no part of the alter-ego is obscured. Two-logo top-right corner overlays (the 008 layout) are gone.

**Why this priority**: This is the whole point of the issue. The conference handout has been redesigned: a single unified frame replaces the two-logo corner stack. Without this change every poster still ships under the old layout, which contradicts the issue's lead requirement and dilutes the new event branding.

**Independent Test**: Run a single end-to-end successful generation. On the rendered poster, visually verify: (a) the SQUER mark is in the top-left chrome of the frame; (b) `<CODE/CRAFTS> 2026` is in the top-right chrome; (c) a gradient rounded border surrounds the central image; (d) the alter-ego character image fills the inner area with no part covered by frame chrome; (e) no separate top-right two-logo stack is present.

**Acceptance Scenarios**:

1. **Given** a user has completed Setup and the image provider is reachable, **When** they press **Generate** and the poster renders, **Then** the rendered image is the framed composite — SQUER top-left, `<CODE/CRAFTS> 2026` top-right, gradient rounded border, decorative chrome — with the character image filling the inner area of the frame.
2. **Given** a poster has just rendered with the new frame, **When** the user presses **Start over** and generates again with different Setup inputs, **Then** the newly rendered poster also carries the same frame in the same position with the same proportional sizing — the framing is consistent run-to-run.
3. **Given** the rendered poster from this feature, **When** it is compared to a poster from the 008 layout, **Then** the standalone two-logo stack in the top-right corner is gone and is replaced by the unified frame chrome.

---

### User Story 2 — Output is portrait 10×15 (2:3) so booth prints come out right (Priority: P1)

The conference plans to print posters on standard 10×15 cm photo paper at the booth. For prints to come out correctly without trimming or unwanted whitespace, the generated image must already be at the **10×15 vertical** aspect ratio (i.e. 2:3, portrait). This ratio must be requested **from the image-generation provider as part of the generation request** — not by post-hoc cropping, padding, or letterboxing on our side. A booth attendee who downloads or prints their poster gets a print-ready 2:3 image straight from the pipeline.

**Why this priority**: The print pipeline at the booth is the visible artefact of this feature for attendees. Posting at the wrong ratio (e.g. 1:1 square or 3:4) means the booth print station either crops faces, leaves white margins, or rejects the file. Asking the provider for the correct ratio up-front (rather than cropping after) preserves the AI's compositional choices — faces stay framed, full-body shots stay full-body — which matters because cropping a 1:1 generation to 2:3 throws away pixels the AI deliberately placed.

**Independent Test**: Run one end-to-end successful generation. Inspect the returned poster image's pixel dimensions and confirm the height-to-width ratio is 1.5 (within rounding tolerance). Inspect the outbound provider request and confirm the requested aspect ratio is 2:3 portrait (or the equivalent 10:15 / "vertical 10×15"). Inspect the poster on the rendered page — the character occupies the full frame top-to-bottom without letterbox bars on the sides or top/bottom.

**Acceptance Scenarios**:

1. **Given** a successful generation, **When** the returned poster image is inspected, **Then** its `height / width` ratio is 1.5 (2:3 portrait, equivalent to 10×15) within a small rounding tolerance.
2. **Given** a successful generation, **When** the outbound provider request is inspected, **Then** the request specifies a 2:3 portrait (10×15) aspect ratio for the generated image — the ratio is part of what we ASK the provider to produce, not something we impose afterwards by cropping.
3. **Given** a successful generation, **When** the rendered poster is viewed in the "Your Alter Ego" tab, **Then** there are no letterbox bars or padding stripes inside the frame's inner area — the character image fills the full inner rectangle of the 2:3 frame.
4. **Given** many consecutive runs at the booth, **When** every returned poster is measured, **Then** every poster comes back at the 2:3 portrait ratio — the ratio does not drift run-to-run based on Setup inputs or provider mood.

---

### User Story 3 — Fallback posters are framed and 2:3 too, so no attendee walks away with the wrong artefact (Priority: P2)

When the image-generation provider is unreachable, rate-limited, unconfigured, or misbehaving (the 003 fallback scenarios), the fallback stub poster that renders in place of the real image also carries the new frame and is also at the 10×15 (2:3) portrait ratio. The booth attendee does not need to know whether their image was "real" or "fallback" — either way the poster they take to the print station is correctly framed and correctly proportioned for the printer.

**Why this priority**: The booth runs at a venue where Wi-Fi or provider quota may degrade. Falling back to an un-framed or wrong-ratio stub would either dilute event branding (un-framed) or jam the print station (wrong ratio) at exactly the moment the fallback path is most load-bearing. P2 rather than P1 because the fallback path itself already exists (001 FR-018, 003 FR-214) — this story is about extending the new frame and ratio through that path, not introducing fallback.

**Independent Test**: Start the backend with the provider deliberately unconfigured (or otherwise force a fallback), then run one generation end-to-end. On the rendered poster, visually verify the same frame chrome appears on the stub. Inspect the returned image's pixel dimensions and confirm the 2:3 portrait ratio.

**Acceptance Scenarios**:

1. **Given** the backend is started so every run falls back to the stub, **When** the user presses Generate, **Then** the fallback stub poster renders inside the new frame with the same chrome as a real-provider run.
2. **Given** the fallback stub poster from this run, **When** its pixel dimensions are measured, **Then** its `height / width` ratio is 1.5 (2:3 portrait, 10×15) — matching the real-provider output.
3. **Given** a real-provider run and a fallback run side-by-side, **When** the two rendered posters are compared, **Then** the frame appears identically positioned and sized, and both images share the same pixel-ratio shape — the framing does not give away which outcome occurred (operator visibility of the outcome stays in the response metadata per 003 FR-218).

---

### Edge Cases

- **Provider returns an image at a ratio that is not 2:3** (the model occasionally ignores the aspect-ratio hint in the prompt, or returns its model-default 1:1). The pipeline still returns a poster — see FR-1511 for the exact behaviour. The user-facing artefact is never broken; the wrong-ratio path is logged for operators and either re-fitted or letterboxed inside the 2:3 frame so the frame chrome stays correctly proportioned.
- **Provider's image dimensions differ from the frame asset's dimensions.** The frame is a vector-style PNG with a known internal layout; the character image is fitted into the frame's inner transparent rectangle preserving the character image's 2:3 aspect. The frame is the sized canvas; the character image is sampled in.
- **Frame asset is missing or fails to decode at runtime.** Fail-soft, mirroring 008 FR-711: the un-framed character image is still returned at the requested 2:3 ratio, and a `WARN`-or-higher log line records the asset failure. Users never see a broken poster because of a frame-layer bug.
- **Subject's face or full-body composition is near the very top/bottom edge of the inner area.** The frame's gradient border has visible thickness; if the character image goes edge-to-edge inside the frame's transparent inner rectangle, the border may slightly overlap the character. The implementation either (a) shrinks the character image to fit fully inside the inner rectangle, or (b) lets the gradient border sit on top of the outermost ~1% of the character pixels — both are acceptable. What is NOT acceptable is the frame chrome (logo, wordmark, decorative dots) overlapping the central subject of the image, which is structurally avoided because those elements live in the dedicated chrome regions outside the inner rectangle.
- **Output MIME type.** The frame asset is a PNG with alpha. The composited final poster MUST be encoded as PNG so the alpha channel of the frame chrome composites correctly over the character image and remains lossless for print. This is a tightening of the previous "preserve input MIME type" rule (008 FR-710): for this feature the output MIME is always PNG, regardless of what the provider returned.
- **Frame asset reload across runs.** The frame asset is loaded once at backend startup and reused across requests (mirrors 008 FR-707 for the old logos).
- **Two-logo overlay code from 008.** This feature replaces it. The old SQUER + CodeCrafts corner-stack overlay step is removed from the pipeline. The two old logo asset files MAY remain in the resource bundle as dead data or MAY be deleted — that is a planning-phase cleanup choice, not a spec-level requirement. What IS required is that the OLD overlay step does not run alongside the new frame step on the same poster.
- **Print rendering on the existing print path (010).** The print stylesheet sizes the poster `<img>` to a known on-page area. Because the image already arrives at 2:3, printing on 10×15 photo paper prints edge-to-edge with no white bars. This feature does not change the print path itself.

## Requirements *(mandatory)*

> **Carried over unchanged**: 001 FR-012 (poster composition), 001 FR-016 (no-persistence — composited bytes live in memory only), 001 FR-017/018 (credentials, fallback), 002 FR-101–132 (tabbed layout, poster rendering slot, wire shape), 003 FR-201, FR-202–219 except FR-216 below, 010 (print path) unchanged, 014 (character generator) unchanged.
>
> **Tightened by this feature**:
>   - 003 FR-201 "the prompt asks for portrait 3:4" → this feature requires the prompt to ask for **portrait 2:3 (10×15 vertical)**, with the ratio expressed at the provider-request level (prompt text and/or the provider's dedicated aspect-ratio parameter where one exists), not by post-hoc crop.
>   - 008 FR-701/702/703/712/713 (top-right two-logo stack) → **superseded** by FR-1501..FR-1505 below. The two-logo overlay step is removed.
>   - 008 FR-710 "preserve input MIME type" → tightened: composited output is **always PNG** (FR-1509), so the frame's alpha channel composites cleanly.

### Functional Requirements

**Visible frame on the rendered poster (replaces the 008 two-logo overlay)**

- **FR-1501**: Every poster the backend returns (real-provider OR fallback) MUST be returned as a single composited image consisting of the character image rendered into the inner transparent rectangle of the **conference frame asset**, with the frame chrome (SQUER mark top-left, `<CODE/CRAFTS> 2026` wordmark top-right, gradient rounded border, decorative dot and circuit-line patterns down both side margins) drawn on top of the chrome regions of the same canvas.
- **FR-1502**: The frame asset MUST be **bundled with the backend** (shipped as a classpath resource under `backend/src/main/resources/branding/`) rather than fetched over the network at runtime. The backend MUST NOT require internet access to a third-party asset host in order to frame a poster.
- **FR-1503**: The frame asset MUST be loaded **once at backend startup** (or lazily on first use and cached thereafter) and reused across requests. It MUST NOT be re-read from disk or re-decoded on every Generate call.
- **FR-1504**: The frame asset's inner area (where the character image is composited) MUST be **fully transparent** in the asset itself, so the character image is visible underneath the chrome without any white or coloured patch behind it. The asset's chrome regions (logo, wordmark, border, decorative patterns) MUST be opaque or partially-transparent as authored, with their alpha channel preserved through the composite.
- **FR-1505**: The previous **two-logo top-right corner overlay** (008 FR-701/702/703) MUST NOT be applied to posters produced by this feature. The pipeline's poster transform produces a single framed image; the legacy two-logo overlay step is removed from that pipeline.

**Output shape — 10×15 (2:3) portrait**

- **FR-1506**: The image-generation provider MUST be **asked** for output at **portrait 2:3 (10×15)** aspect ratio at request time. This ratio MUST be expressed in whatever the provider's request shape supports — at minimum the prompt text MUST state "portrait, 2:3 aspect ratio" (or equivalent "10×15 vertical"); if the provider exposes a dedicated aspect-ratio request parameter, that parameter MUST also be set to the 2:3 / portrait value.
- **FR-1507**: The aspect ratio of the **final poster returned by the backend** MUST be **2:3 portrait** within a small rounding tolerance (≤1% deviation in `height / width` from 1.5). This applies to both the real-provider path and the fallback stub path.
- **FR-1508**: The pipeline MUST NOT produce the 2:3 ratio by **cropping** or **padding** a non-2:3 provider output as the primary mechanism. The primary mechanism is the request-time ask in FR-1506. Crop/pad/letterbox is allowed ONLY as the corrective fallback in FR-1511 when the provider ignores the ask.

**Output encoding**

- **FR-1509**: The composited final poster MUST be encoded as **PNG**, regardless of the input MIME type emitted by the provider or the stub. This is required because the frame asset has alpha and PNG preserves it losslessly. The response body's `Content-Type` and any data-URL prefix the frontend consumes MUST reflect PNG.
- **FR-1510**: The composited final poster MUST preserve the alpha channel of the frame chrome (gradient edges, anti-aliased corners, partially-transparent decorative elements) so the frame reads cleanly on the rendered poster.

**Fail-soft behaviour**

- **FR-1511**: If the provider returns an image whose aspect ratio is not 2:3 portrait (within tolerance), the pipeline MUST still produce a 2:3 portrait poster. The corrective behaviour is implementation-defined within these limits: the corrective step MUST NOT crop the character's primary subject (face/torso for portrait compositions) out of the frame; padding/letterboxing inside the frame's inner rectangle is acceptable. The mismatch event MUST be logged at `WARN` level with enough detail for an operator to diagnose how often the provider ignores the ratio ask.
- **FR-1512**: If frame compositing fails for any reason (missing asset, decode error, graphics runtime exception), the backend MUST still return the **un-framed** generated image (at the requested 2:3 ratio) rather than converting the run into a failure. The failure MUST be logged at `WARN`-or-higher level with enough detail for an operator to diagnose, but MUST NOT leak asset paths, image bytes, or internal stack traces to the frontend. This mirrors 008 FR-711 for the old logo overlay.
- **FR-1513**: Coverage MUST be uniform: the new frame MUST be applied to **both** real-provider images and fallback stub images (mirrors 008 FR-708). The 2:3 ratio MUST hold on **both** paths. A booth attendee sees the same poster shape regardless of generation outcome.

### Key Entities

- **PosterFrame** — the single bundled PNG asset that wraps every poster. Loaded once at backend startup, cached as an in-memory decoded image for reuse across requests. Has a transparent inner rectangle (where the character image goes) and an opaque/anti-aliased outer chrome region (SQUER mark, `<CODE/CRAFTS> 2026` wordmark, gradient rounded border, decorative dot and circuit-line patterns). Not persisted beyond process lifetime. Replaces the pair of `LogoAsset` entities introduced in 008 (those entities are out of scope after this feature).
- **FramedPoster** — the poster image after the character image has been composited into the frame's inner rectangle. PNG-encoded, 2:3 portrait aspect, dimensions determined by the frame asset's canvas. Not a new entity at the API layer — it replaces the `PosterImage` bytes in the existing response shape (003 response). Operator-visibility metadata (003 `outcome`/`reason`) is unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-1501**: In a **manual walkthrough of 5 consecutive successful runs** with distinct Setup inputs, **100%** of the rendered posters show the new frame chrome (SQUER top-left, `<CODE/CRAFTS> 2026` top-right, gradient border, decorative side patterns) and **0%** show the legacy 008 top-right two-logo stack. *(Validates FR-1501, FR-1505.)*
- **SC-1502**: In an **inspection of the outbound provider request** across 10 consecutive runs, **100%** of requests express a 2:3 portrait (10×15) aspect-ratio ask — either in the prompt text, in a dedicated provider parameter, or both. *(Validates FR-1506.)*
- **SC-1503**: Across **20 consecutive successful runs**, **100%** of returned poster images measure as 2:3 portrait within ≤1% rounding tolerance on `height / width`, on **both** real-provider and fallback-stub paths. *(Validates FR-1507, FR-1513.)*
- **SC-1504**: In the **fault-injection matrix from 003 SC-205** (no API key, network error, provider 5xx, timeout, malformed response), **100%** of fallback posters render with the new frame chrome AND at the 2:3 portrait ratio. *(Validates FR-1513.)*
- **SC-1505**: When the frame asset is deliberately removed or corrupted (simulated fault), **100%** of subsequent runs return a usable poster — un-framed but at the correct 2:3 ratio — and a `WARN`-level log line per affected run names the failure. **0%** of runs return a 5xx or leave the frontend spinning. *(Validates FR-1512.)*
- **SC-1506**: When the provider deliberately returns a non-2:3 image (simulated fault), **100%** of runs still return a 2:3 portrait poster, and a `WARN`-level log line records the ratio mismatch with enough detail to count occurrences. The character's primary subject is not cropped out of the frame in any of those simulated cases. *(Validates FR-1511.)*
- **SC-1507**: Across **20 consecutive runs**, the frame asset is read from disk (or classpath) **exactly once** (at startup or first use) — not 20 times. *(Validates FR-1503.)*
- **SC-1508**: The median wall-clock overhead added by the framing step, measured at the backend between the character image being decoded and the response being serialised, is **under 100 ms** on a typical developer laptop; the 95th percentile is **under 250 ms**. The framing step MUST NOT push the overall `Generate press → poster rendered` timing beyond the 003 SC-207 bound (median < 15 s, p95 < 30 s). *(Validates FR-1503, FR-1501.)*
- **SC-1509**: The composited poster is encoded as **PNG** in **100%** of runs (real-provider AND fallback), and the frame chrome's anti-aliased gradient edges remain visibly clean (no halo banding, no opaque white square behind the character). *(Validates FR-1509, FR-1510, FR-1504.)*

## Assumptions

- **Authoritative frame asset.** The PNG file attached to issue #39 (and now bundled at `backend/src/main/resources/branding/poster-frame.png`) is the authoritative frame asset. It is **1024 × 1536** (2:3 portrait) RGBA with the inner rectangle fully transparent. Any swap (higher-resolution version, brand revision) is an asset-level change that does not require a spec update.
- **Aspect-ratio expression to the provider.** The provider used by 003/014 (Gemini image generation) takes free-text prompts; the existing prompt builder (`GeminiPromptBuilder`) is the natural place to state "portrait 2:3 / 10×15 vertical". If the provider's request schema also exposes a typed aspect-ratio parameter, planning may set it as well — both the prompt and the parameter then express the same ratio. The exact implementation is a planning detail.
- **"10×15" semantics.** The issue says "10×15 vertical (for printing purposes)" — interpreted as portrait 2:3 (i.e. 10 cm × 15 cm photo paper, equivalent to a 4×6 inch print). The pipeline does not need to emit specific physical-millimetre or DPI metadata; it needs to emit a 2:3-shaped image. A 1024×1536 px output is acceptable; a 2048×3072 px output is also acceptable. The canvas size is driven by the frame asset.
- **No new user-visible chrome on the page.** The frame is in-image. The poster card, tab layout, and surrounding UI (002 FR-101..132, 005 entrance animation, 010 print path) are unchanged. The `<img>` element in the poster slot already renders the data URL the backend returns — that is the rendering surface for the framed bytes.
- **Old logo asset files.** The old `squer-logo.png` and `codecrafts-logo.png` resources from 008 may remain in the resource bundle as orphans or may be deleted in this feature. Either choice is acceptable; the spec only requires that they are no longer composited (FR-1505).
- **Print path (010).** The existing `window.print()` + `@media print` rules continue to work. Because the poster already arrives at 2:3 from the backend, printing on 10×15 photo paper requires no additional layout work in this feature. If the print path needs adjustments, they are out of scope here and tracked separately.
- **No new third-party image library.** The backend's existing 2D graphics stack (the same one used by 008's overlay step — `javax.imageio.ImageIO` + `java.awt.Graphics2D`) is sufficient to composite the character image into the frame's transparent inner rectangle. No new dependency is required.
- **No persistence.** 001 FR-016 continues to apply: the composited bytes live in process memory for the duration of the request only. The frame asset lives in process memory for the lifetime of the backend process (it is part of the artefact, not user data).
- **Accessibility.** The poster's existing `alt` text (built from the character's hero title in 002) remains the accessible name; no frame-specific `alt` is added. The frame chrome is presentational brand decoration inside the composed image.
- **Subject-vs-frame occlusion.** The frame's transparent inner rectangle is sized so that the character image's primary subject (face, torso) sits comfortably inside the frame's gradient border. If the AI happens to place subject pixels at the very edge of the inner rectangle, the gradient border may slightly overlap them; this is acceptable and matches the booth artefact's printed look.
- **Two-logo regression risk.** The 008 overlay step is the only previous post-generation transform on the poster image. Removing it (FR-1505) and replacing it with the frame compositing step (FR-1501) is a single-shot replacement; no other post-generation transforms exist to coordinate with.

---

## Dependencies

- **Depends on**: 003-gemini-image-generator (the provider request/response pipeline whose prompt this feature tightens for the 2:3 ratio); 002-sleek-tabbed-ui (the rendering surface the framed image is displayed in); 001-initial-poc (the fallback stub path, the no-persistence posture, the resilient-HTTP policy inherited by 003); 010-print-alter-ego (the print path that benefits from the 2:3 ratio).
- **Supersedes**: 008-logos-on-generated-images (the two-logo top-right overlay is removed; the new frame replaces it). All 008 spec content remains historical and is not deleted, but the 008 overlay step is no longer applied at runtime after this feature lands (FR-1505).
- **Does not depend on**: any change to the frontend (the `<img>` element already renders the returned data URL); any change to the response shape or `outcome`/`reason` metadata; any new persistence layer; any new configuration system (the frame is shipped as a classpath resource).
- **Upstream constraint**: The Constitution's Technology Standards (Java 21 / Spring Boot 3 backend, React 18+ TS strict frontend) continue to apply. The framing step runs in the same process as 003's image client, using the runtime's built-in 2D graphics stack.

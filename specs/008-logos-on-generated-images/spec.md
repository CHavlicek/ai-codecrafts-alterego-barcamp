# Feature Specification: Logo Branding Overlay on Generated Alter-Ego Images

**Feature Branch**: `008-logos-on-generated-images`
**Created**: 2026-04-23
**Status**: Draft
**Input**: GitHub issue [#16 — Add Squer And CodeCrafts Logo Onto the Generated Image](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/16). Exact request: *"Official SQUER logo should be added onto each of the generated alter ego images in top right corner with some margin. Below the SQUER logo, the CodeCrafts (conference logo) show be added. Both logos should have the same width. Use the images uploaded as SQUER and CodeCrafts logos. Should not send the logo to the AI API each time, place the logo on top of the generated image."*

> **Relationship to earlier features.** This feature layers a branding overlay on top of the image emitted by the pipeline introduced in 003 (Gemini image generator) and on top of the fallback stub introduced in 001. It touches only the backend's post-generation byte transform — it does not change what is sent to Gemini (FR-216 unchanged), does not change the frontend's rendering surface (poster slot from 002), and does not change the fallback semantics (001 FR-018, 003 FR-214). Character text (title, tagline, superpowers, quote) and accent chrome (002 FR-132) are untouched.

## Clarifications

### Session 2026-04-23

- Q: Should the branding overlay apply only to real Gemini-generated images, or also to the fallback stub posters so users always see the conference + company branding regardless of outcome? → A: **Apply to both.** A single overlay step sits between the `PosterImage` (real or fallback) and the response body, so branding is visually consistent regardless of whether Gemini succeeded. This keeps the non-blocking fallback notice from becoming the only signal of degraded output and preserves the sponsor/conference surface for every user.
- Q: Is the logo width an absolute pixel value or a percentage of the output image's width? → A: **Percentage of image width** — the overlay scales with the image so the branding reads the same across the range of image sizes the provider (or stubs) can emit. The spec pins the proportion without pinning pixels (FR-703, FR-712); the planning phase picks the exact percentage inside a specified range.
- Q: What happens if a logo asset is missing at runtime (bad jar packaging, corrupt resource, decode failure)? → A: **Fail-soft.** The image is still returned without logos and an operator-visible log line records the failure. Users never see a broken poster because of a branding-layer bug (FR-711). This matches the project's overarching "always return a poster" posture (001 FR-018, 003 FR-214).

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Every generated alter-ego carries the event and sponsor branding (Priority: P1)

When a conference attendee at the CodeCrafts booth completes Setup and presses **Generate**, the poster that appears on "Your Alter Ego" carries the SQUER company logo in the top-right corner (with a visible margin from both edges) and, directly below it, the CodeCrafts conference logo. Both logos are the same width. The logos are legibly sized relative to the image and do not obscure the subject's face in typical compositions.

**Why this priority**: This is the whole point of the feature. Without it the generated images look un-branded and cannot be shared/posted as conference artefacts. The conference's sponsor (SQUER) is not visible, and the event's own branding is not visible. This is the feature's core value.

**Independent Test**: Run a single end-to-end successful generation at the booth (or locally). On the rendered poster, visually verify: (a) SQUER logo is in the top-right corner with margin from both edges; (b) CodeCrafts logo is directly below it with a visible gap; (c) both logos have the same width; (d) logos do not crowd or overlap the subject's face in a typical full-body/half-body composition.

**Acceptance Scenarios**:

1. **Given** a user has completed Setup and Gemini is reachable, **When** they press **Generate** and the poster renders, **Then** the rendered image contains the SQUER logo in the top-right corner with a visible margin and, directly below it, the CodeCrafts logo — both with the same width.
2. **Given** a poster has just rendered with branding, **When** the user presses **Start over** and generates again with different Setup inputs, **Then** the newly rendered poster also carries the same SQUER + CodeCrafts branding in the same position with the same relative sizing — branding is consistent run-to-run, not sometimes-present.
3. **Given** the Gemini provider emits images at different aspect ratios or dimensions across runs, **When** the posters render, **Then** the logos stay anchored to the top-right corner with margins that are visually proportional to the image — the branding does not drift, overflow, or become unreadably small across size variations.

---

### User Story 2 — Branding is applied without leaking the logos to the AI provider (Priority: P1)

The SQUER and CodeCrafts logo bytes are placed onto the image **after** the generator returns its output. They are not sent to the AI provider at generation time, they are not referenced in the prompt, and they are not uploaded as part of the request payload. This matters because (a) the issue is explicit about this, (b) it avoids paying for logo bytes on every request, and (c) it keeps the final branding pixel-perfect rather than subject to the provider's interpretation of a "paste this logo" prompt.

**Why this priority**: The issue calls this out explicitly (*"Should not send the logo to the AI API each time, place the logo on top of the generated image."*) and it is the load-bearing constraint that distinguishes this feature from a prompt-only approach. Getting it wrong (i.e. prompting the AI to draw the logos) is a regression from a quality, cost, and deterministic-branding standpoint.

**Independent Test**: Inspect the outbound request body sent to the Gemini provider on any successful run (via captured backend logs, a network tap, or the existing integration-test HTTP capture infrastructure) and confirm that no logo byte data, logo filename, or logo URL is present in the request. Then visually confirm the logos appear on the rendered output.

**Acceptance Scenarios**:

1. **Given** a generation run has just completed successfully, **When** the outbound request body is inspected, **Then** neither the SQUER nor the CodeCrafts logo bytes appear in the request payload.
2. **Given** the prompt text assembled for Gemini (per 003 FR-201), **When** it is inspected, **Then** it does not instruct the model to render, include, place, or reference the SQUER or CodeCrafts logos in any way.
3. **Given** many consecutive generation runs at the booth, **When** provider-side request volume is totted up, **Then** no additional per-request payload size is incurred for logo transmission (logos are bundled once with the backend, not sent per-request).

---

### User Story 3 — Fallback posters are branded too, so no attendee walks away un-branded (Priority: P2)

When Gemini is unreachable, rate-limited, unconfigured, or misbehaving (the 003 fallback scenarios), the fallback stub poster that renders in place of the real image also carries the SQUER + CodeCrafts branding in the same top-right layout. The attendee at the booth does not need to know whether their image was "real" or "fallback" — either way the poster they take away looks like a CodeCrafts × SQUER artefact.

**Why this priority**: The booth demo will be run at a venue where Wi-Fi or provider quota may degrade. Falling back to an un-branded stub would dilute the event's sponsor visibility at exactly the moment when the fallback path is most load-bearing. It is P2 rather than P1 because the fallback path itself already works (001 FR-018, 003 FR-214) — this story is about **preserving** branding through that path, not introducing the fallback.

**Independent Test**: Start the backend with `GEMINI_API_KEY` unset so every run falls back to the stub, then run one generation end-to-end. On the rendered poster, visually verify that the same top-right branding (SQUER over CodeCrafts, equal width, proportional margins) is present on the stub image — matching what a real-provider run would look like.

**Acceptance Scenarios**:

1. **Given** the backend is started with no Gemini API key, **When** the user presses Generate, **Then** the fallback stub poster renders with the SQUER + CodeCrafts branding applied in the same top-right layout as a real-provider run.
2. **Given** the provider is injected to fail (network error, timeout, malformed response), **When** the fallback poster renders, **Then** the branding is present on every failure mode — no matter which fallback reason triggered the path.
3. **Given** both a real-provider run and a fallback run in the same session, **When** the two rendered posters are compared side-by-side, **Then** the branding appears identically positioned and sized on both (the branding does not give away which outcome occurred — operator visibility of the outcome stays in the response metadata per 003 FR-218).

---

### Edge Cases

- **Generated image is unusually small (e.g. the stub is 1024×1024 but the provider emits 512×512).** Logos scale down proportionally (FR-712) so margins and sizes stay visually consistent; a hard minimum readable width is enforced so the logos do not shrink to illegibility.
- **Generated image is unusually large or portrait/landscape.** The overlay still anchors to the top-right corner; logo width is a percentage of the image's shorter or standard reference dimension (exact rule is a planning decision) so the branding does not become absurdly large on an ultra-wide image.
- **Logo asset is missing or fails to decode at runtime** (e.g. a bad jar, a corrupted resource). The image is still returned without logos and the failure is logged (FR-711). Users never see a broken poster because of a branding bug.
- **Logos have transparent pixels.** Alpha channel is preserved so the logos overlay correctly on any colour/texture behind them (FR-713).
- **Output MIME type** (PNG from Gemini, JPEG from a stub, etc.). The composited image is re-encoded in the **same** MIME type as the input (FR-710) so the `Content-Type`/data-URL prefix the frontend already consumes stays correct.
- **Consecutive runs.** Logo assets are loaded once at backend startup and reused (FR-707); they are not re-read from disk or re-decoded per request.
- **Subject's face is near the top-right.** Out of scope for this feature — the logo position is fixed; the AI composition deciding where to place the face is out-of-band. This is called out explicitly in Assumptions.

## Requirements *(mandatory)*

> **Carried over unchanged**: 001 FR-012 (poster composition), 001 FR-016 (no-persistence — branding bytes live in memory only, same as photo bytes), 001 FR-017/018 (credentials, fallback), 002 FR-101–132 (tabbed layout, poster rendering slot, wire shape), 003 FR-201–219 (real Gemini generation, reduction, fallback, operator metadata).
>
> **Not superseded by this feature**: 003 FR-216 (no identifying data to Gemini beyond photo+name+enums) — the logos are bundled with the backend and MUST NOT be sent to Gemini at all (FR-704).

### Functional Requirements

**Visible branding on the rendered poster**

- **FR-701**: Every poster the backend returns (real-provider OR fallback) MUST contain the SQUER logo composited in the **top-right corner** of the central image, with a margin from both the top edge and the right edge.
- **FR-702**: Every poster the backend returns MUST contain the CodeCrafts conference logo composited **directly below** the SQUER logo, aligned to the same right edge, with a visible vertical gap between the two logos.
- **FR-703**: The SQUER logo and the CodeCrafts logo MUST be rendered at **the same width** on every poster. The aspect ratio of each logo MUST be preserved (no squashing) — equal width means equal final rendered width, not equal rendered area.
- **FR-712**: The logo width, top margin, right margin, and inter-logo gap MUST be defined as a **proportion of the image's width** (not as absolute pixels), so the branding reads the same across the range of image sizes the provider or the stubs can emit. Exact percentages and a minimum-readable-width floor are chosen at planning time inside the ranges given in Assumptions.
- **FR-713**: The logo alpha channel MUST be preserved during compositing so each logo overlays correctly on whatever colour/texture sits behind it in the generated image.

**Out-of-band overlay, not a prompt**

- **FR-704**: The SQUER and CodeCrafts logo bytes MUST NOT be included in any outbound request to Gemini. Neither the prompt text, the request JSON, nor any multipart part sent to the provider MUST contain logo data, logo filenames, or logo URLs.
- **FR-705**: Logo compositing MUST happen on the **backend**, **after** the provider response is decoded into an image and **before** the response body carrying the poster is serialised to the frontend. The frontend's rendering surface (poster `<img>` slot) MUST NOT change — it continues to consume the existing data URL unchanged.

**Asset lifecycle**

- **FR-706**: The SQUER and CodeCrafts logo assets MUST be **bundled with the backend** (shipped as part of the artefact) rather than fetched over the network at runtime. The backend MUST NOT require internet access to a third-party asset host in order to brand a poster.
- **FR-707**: The logo assets MUST be loaded **once at backend startup** (or lazily on first use and cached thereafter) and reused across requests. They MUST NOT be re-read from disk or re-decoded on every Generate call.

**Coverage and fail-soft behaviour**

- **FR-708**: Branding MUST be applied to **both** real-provider images and fallback stub images, so a booth attendee sees consistent branding regardless of the generation outcome.
- **FR-709**: The composited image MUST preserve the **original pixel dimensions** of the generator's or the stub's output — i.e. the branding adds pixels inside the existing image rectangle, it does not resize, pad, or crop the image.
- **FR-710**: The composited image MUST preserve the **original MIME type** of the generator's or stub's output (PNG stays PNG, JPEG stays JPEG). The `Content-Type`/data-URL prefix on the response body MUST remain correct for the frontend's existing rendering path.
- **FR-711**: If logo compositing fails for any reason (missing asset, decode error, graphics runtime exception), the backend MUST still return the un-branded generated image rather than converting the whole run into a failure. The failure MUST be logged at `WARN`-or-higher level with enough detail for an operator to diagnose, but MUST NOT leak asset paths, image bytes, or internal stack traces to the frontend.

### Key Entities

- **LogoAsset** — one of the two branding images (SQUER, CodeCrafts) bundled with the backend. Loaded once, cached as an in-memory decoded image for reuse. Not persisted beyond process lifetime. Not user-visible except as composited pixels on the poster.
- **BrandedPoster** — the poster image after the overlay has been applied. Same MIME type and pixel dimensions as the input; differs only in pixel content within the top-right corner region. Not a new entity at the API layer — it replaces the `PosterImage` bytes in the existing response shape. Operator-visibility metadata (003 `outcome`/`reason`) is unchanged.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-701**: In a **manual walkthrough of 5 consecutive successful runs** with distinct Setup inputs, **100%** of the rendered posters show both logos in the top-right corner with the SQUER logo above, the CodeCrafts logo directly below, and both at equal rendered width. *(Validates FR-701, FR-702, FR-703.)*
- **SC-702**: In an **inspection of the outbound Gemini request body** across 10 consecutive runs, **0** requests contain SQUER or CodeCrafts logo bytes, filenames, URLs, or textual references. *(Validates FR-704, User Story 2.)*
- **SC-703**: In the **fault-injection matrix from 003 SC-205** (no API key, network error, provider 5xx, timeout, malformed response), **100%** of fallback posters render with the SQUER + CodeCrafts branding applied in the same top-right layout as real-provider runs. *(Validates FR-708, User Story 3.)*
- **SC-704**: Across **20 consecutive runs**, the logo assets are read from disk (or classpath) **exactly once** (at startup or first use) — not 20 times. This is validated by tracing resource-load events or by asserting the cached asset reference is the same object across runs. *(Validates FR-707.)*
- **SC-705**: On the range of image sizes the system emits in practice (the 001 stub resolution through the Gemini provider's typical output resolution), the **rendered logo width is between 10% and 20% of the image's width**, the **top margin and right margin are each between 2% and 6% of the image's width**, and the **inter-logo gap is between 1% and 4% of the image's width**. These ranges are the Assumptions-level defaults; the exact numbers are pinned in `plan.md`. *(Validates FR-712.)*
- **SC-706**: When a logo asset is deliberately removed or corrupted (simulated fault), **100%** of subsequent runs return a usable poster — un-branded in this degraded mode — and a `WARN`-level log line per affected run names the failure. **0%** of runs return a 5xx or leave the frontend spinning. *(Validates FR-711.)*
- **SC-707**: The median wall-clock overhead added by the compositing step, measured at the backend between the image being decoded and the response being serialised, is **under 100 ms** on a typical developer laptop; the 95th percentile is **under 250 ms**. The compositing MUST NOT push the overall `Generate press → poster rendered` timing beyond the 003 SC-207 bound (median < 15 s, p95 < 30 s). *(Validates FR-705, FR-707.)*

## Assumptions

- **Official logo assets.** The PNG files provided on the issue (blue SQUER square, pink CodeCrafts `</>` square) are the authoritative assets. They are bundled as classpath resources under the backend. Any swap (SVG source-of-truth, higher-resolution version, updated brand revision) is an asset-level change that does not require a spec update.
- **Logo sizing.** Both logos render at 10–20% of the image's width (planning pins the exact value — a reasonable default is 15%). Equal width is a hard requirement (FR-703); equal height is a consequence of asset aspect ratios, not a requirement.
- **Margins and gap.** Top and right margins: 2–6% of image width each (reasonable default 4%). Inter-logo gap: 1–4% of image width (reasonable default 2%).
- **Minimum readable width floor.** On very small images a hard minimum (planning-level, likely around 48px) prevents the logos from shrinking to illegibility. On oversized images the width is capped at the top end of the 10–20% range so branding does not dominate the frame.
- **Integration point.** The overlay step is a single post-generation transform sitting between the `PosterImage` and the response body in the existing pipeline; this is where "real" and "fallback" paths already converge, which is how a single step covers both (FR-708). The exact class / file is a planning detail and is pinned in `plan.md`.
- **Rendering quality.** Compositing uses the backend runtime's standard 2D graphics stack (bicubic or equivalent interpolation when scaling the logos down to the target width) to avoid jaggy edges. No new third-party image library is required — the existing runtime's `ImageIO`/`Graphics2D`-level primitives are sufficient.
- **Alpha handling.** Logos are expected to have a transparent background; alpha is preserved during composite so the logos sit cleanly on any underlying image content.
- **Subject-vs-logo occlusion.** The overlay position is fixed at top-right per the issue; if the AI-generated image happens to place the face in the top-right, the logos will overlap the face. Mitigating that via face-aware placement is **out of scope** for this feature; it can be a follow-up if it becomes a real problem in demo usage.
- **No new user-visible chrome.** The branding is in-image. No new UI elements are added to the poster card, no caption under the image, no new tab. The frontend's existing `<img>` element already renders the data URL — that is the rendering surface for the branded bytes.
- **No persistence.** 001 FR-016 continues to apply: the composited bytes live in process memory for the duration of the request only. The logo assets live in process memory for the lifetime of the backend process (they are part of the artefact, not user data).
- **Accessibility.** The poster's existing `alt` text (built from the character's hero title in 002) already describes the image to assistive technologies and continues to be the accessible name; no logo-specific `alt` is added. The logos are presentational brand marks inside the composed image.

---

## Dependencies

- **Depends on**: 003-gemini-image-generator (the pipeline that emits `PosterImage` — whether from the real provider or the fallback stub); 002-sleek-tabbed-ui (the rendering surface the branded image is displayed in); 001-initial-poc (the fallback stub path, the no-persistence posture, the resilient-HTTP policy inherited by 003).
- **Does not depend on**: any change to the frontend (the `<img>` element already renders the returned data URL); any change to the Gemini request/response shape; any new persistence layer; any new configuration system (logos are shipped as classpath resources).
- **Upstream constraint**: The Constitution's Technology Standards (Java 21 / Spring Boot 3 backend, React 18+ TS strict frontend) continue to apply. The overlay step runs in the same process as 003's Gemini client, using the runtime's built-in image stack.

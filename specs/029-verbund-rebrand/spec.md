# Feature Specification: Verbund rebrand — "AI @ Verbund 2026"

**Feature Branch**: `029-verbund-rebrand`
**Created**: 2026-09-06
**Status**: Implemented (documented post-execution)
**Input**: User description verbatim:
> The app was created by the company SQUER to be used at their event Code/Crafts. Now it is licenced to the company fifty1 (fifty1.com) to be used at their format "barcamp". This time it is the customer Verbund AG (verbund.com) for their company strategy to support the energy transformation in the course of the climate crisis. This event is officially called "AI @ Verbund 2026". To use this application at the event, we have to re-style it. Please re-style the entire UI, including the photo frame that is used to generate the photos in the look and feel of Verbund. The categories and mechanics can stay the same for now. Everywhere, where currently the SQUER logo is visible, the Verbund and fifty1 logo have to be placed. Everywhere, where the Code/Crafts logo or text is visible, the name "AI @ Verbund 2026" should be used.

> NOTE: This spec was authored **after** implementation as an adapted post-execution log (the change was executed directly, not through the full speckit specify→plan→tasks flow). It records intent, decisions, and outcomes so the rebrand is traceable alongside features 001–028. See `post-execution-log.md` for the as-built verification record.

## Clarifications

### Session 2026-09-06

- **Ownership chain**: App originally built by SQUER for their "Code/Crafts" event → licensed to fifty1 (format operator) for their "AI Experience Barcamp" format → run for the customer Verbund AG under the official event name **"AI @ Verbund 2026"**. Verbund's strategy context is the energy transformation / climate response.
- **Scope**: Visual/brand re-skin only. Categories, mechanics, generation pipeline, API contract, and no-persistence posture are all **unchanged** (this is not a behavioral feature).
- **Q: How far should the theme flip go — recolor the dark neon theme, or fully adopt Verbund's light corporate look?** → A: **Full light-corporate flip.** Verbund's identity is VERBUND-blau (deep navy `#00468E`) on white, professional, with nature/energy/hydro themes. The previous dark ink-violet "portal" theme is replaced by a light ground with navy text and a Verbund-family accent palette (energy green + hydro cyan + solar amber).
- **Q: The poster frame (`poster-frame.png`) has SQUER + `<CODE/CRAFTS> 2026` baked into the pixels — how to replace it?** → A: **Regenerate programmatically.** A committed generator script rebuilds the frame in the Verbund style, rendering the *real* VERBUND + fifty1 logos, and re-punches the transparent cutout at the identical geometry so no backend overlay/loader code or tests change.
- **Q: Logo sources for the web UI?** → A: Official files supplied by the user — Verbund `logo.svg` (single-path navy wordmark, brand colour `#00468E`) and fifty1 `fifty1_logo_black.png`.
- **Logo placement rule**: Everywhere the SQUER logo appeared → the **Verbund + fifty1** logos. Everywhere the "Code/Crafts" logo/text appeared → the string **"AI @ Verbund 2026"**.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Attendee sees a Verbund-branded app (Priority: P1)

An attendee at "AI @ Verbund 2026" opens the app. The page reads unmistakably as a Verbund experience: a light corporate ground, the VERBUND wordmark top-left and the fifty1 wordmark ("Powered by fifty1") top-right, the event title **"AI @ Verbund 2026"** in Verbund's blue→cyan→green brand gradient, and a footer crediting the AI Experience Barcamp by fifty1 for VERBUND. No SQUER or Code/Crafts marks appear anywhere.

**Why this priority**: This is the entire feature — the app cannot be shown at a Verbund event carrying another company's branding.

**Independent Test**: Load the app. Verify (a) the h1 reads "AI @ Verbund 2026", (b) the VERBUND and fifty1 logos render in the header, (c) the palette is light/navy (not dark neon), (d) no occurrence of "SQUER" or "Code/Crafts" is visible.

**Acceptance Scenarios**:

1. **Given** the app is loaded, **When** the attendee views the Setup tab, **Then** the brand header shows the VERBUND wordmark and the fifty1 wordmark, the title is "AI @ Verbund 2026", and the selection grids/buttons use Verbund-family accents (navy / energy-green / hydro-cyan / solar-amber).
2. **Given** the app is loaded, **When** the attendee inspects the browser tab, **Then** the document title is "AI @ Verbund 2026" and the favicon is a Verbund-styled mark (navy tile + energy glyph), not the old purple mark.
3. **Given** any screen state, **When** the attendee scans the page, **Then** there is no visible "SQUER", "Code/Crafts", or "CodeCrafts" text or logo.

---

### User Story 2 — Generated poster carries Verbund chrome (Priority: P1)

The attendee completes Setup and generates. The returned poster is wrapped in a Verbund-branded frame: the VERBUND wordmark and "AI @ VERBUND 2026" wordmark in a navy top band, a navy border around the portrait, and a "Powered by fifty1" footer — with the attendee's name / role / quote rendered in the frame's lower band. No SQUER / Code-Crafts chrome remains.

**Why this priority**: The poster is the take-home artefact and the most-shared surface; off-brand chrome here defeats the rebrand.

**Independent Test**: Generate a poster (or run the overlay ITs). Verify the framed 768×1152 PNG carries opaque Verbund chrome at the top-left wordmark coordinate, the transparent cutout is at the same geometry as before, and name/role/quote render legibly in the lower band clear of the fifty1 footer.

**Acceptance Scenarios**:

1. **Given** a generation completes, **When** the poster is composited, **Then** the frame chrome is the Verbund design and the character image + text overlay land correctly inside it (no letterboxing, no text/footer collision).
2. **Given** the frame asset is loaded at startup, **When** the loader discovers the transparent cutout, **Then** its geometry is identical to the previous asset (x=88, y=94, w=586, h=791) so no overlay/loader code or test needs to change.

---

### User Story 3 — Outbound email is Verbund-branded (Priority: P2)

An attendee who supplies an email receives their poster with a body and subject that reference "AI @ Verbund 2026", from a Verbund-aligned sender — not "CodeCrafts 2026".

**Why this priority**: Lower traffic than the on-screen surfaces and gated on SMTP being configured, but still an outward-facing brand touchpoint.

**Acceptance Scenarios**:

1. **Given** email is configured, **When** a poster is emailed, **Then** the subject is "Your AI Generated Alter Ego - AI @ Verbund 2026" and the body thanks the attendee "for being a part of AI @ Verbund 2026!".

## Requirements *(mandatory)*

- **FR-2901**: The web UI MUST adopt Verbund's light-corporate look & feel — light ground, VERBUND-blau (`#00468E`) text/primary, with an energy-green / hydro-cyan / solar-amber accent family. (`frontend/src/styles/tokens.css`, `frontend/src/index.css`)
- **FR-2902**: The VERBUND wordmark and the fifty1 wordmark MUST appear on every screen, replacing the SQUER logo. (`BrandHeader.tsx`, footer in `App.tsx`)
- **FR-2903**: The visible event name MUST be "AI @ Verbund 2026" everywhere the Code/Crafts logo or text previously appeared — page title, `<title>`, poster frame wordmark, email subject/body.
- **FR-2904**: The browser tab title and favicon MUST be rebranded to "AI @ Verbund 2026" / a Verbund-styled mark.
- **FR-2905**: The generated poster frame (`backend/src/main/resources/branding/poster-frame.png`) MUST be replaced with a Verbund-styled 768×1152 RGBA frame carrying the VERBUND + fifty1 + "AI @ VERBUND 2026" chrome, with the transparent inner cutout at the **same geometry** as the prior asset.
- **FR-2906**: The outbound email `from` / `subject` / body MUST reference "AI @ Verbund 2026" instead of "CodeCrafts 2026".
- **FR-2907** *(preserved)*: Categories, mechanics, the public HTTP contract (`POST /api/v1/alter-egos`, `.../email`), and the no-persistence posture MUST be unchanged (extends 001 FR-016/017/024).
- **FR-2908** *(preserved)*: The frame/text overlay stages remain fail-soft (015 FR-1512 / 017 FR-1716) — a frame swap must not introduce a hard failure path.

### Key Entities

- **Brand palette** — VERBUND-blau `#00468E`, deep navy `#002B54`, hydro cyan `#0090C7`, energy green `#5AAA46`, solar amber `#E2A12B`, off-white `#F4F7FB`, navy ink `#0B2A4A`.
- **Poster frame asset** — 768×1152 RGBA PNG; transparent cutout at x=88 y=94 w=586 h=791; text safe region y∈[954,1083]; Verbund chrome baked into alpha-non-zero pixels.
- **Brand logos** — `frontend/src/assets/brand/verbund-logo.svg`, `frontend/src/assets/brand/fifty1-logo-black.png` (also rendered into the poster frame by the generator).

## Success Criteria *(mandatory)*

- **SC-2901**: Zero visible occurrences of "SQUER" / "Code/Crafts" / "CodeCrafts" across the running UI, poster, and email.
- **SC-2902**: The full frontend test suite and full backend test suite pass unchanged in count (no regressions from the re-skin).
- **SC-2903**: A live-loaded UI renders the Verbund header logos, gradient title, and light theme; a generated poster carries the Verbund frame with correctly placed portrait + text.
- **SC-2904**: The poster frame loads at startup with the unchanged cutout geometry (verified by the loader log line and the overlay ITs).

## Assumptions

- **No persistence** — unchanged; the rebrand touches only static assets, styles, copy, and config defaults.
- **Rendering quality** — the poster frame is regenerated with the existing JDK 2D / Pillow tooling; no new runtime image library.
- **Favicon fidelity** — the favicon is an authored Verbund-styled mark, not an official Verbund brand asset; it can be swapped for an official one without code change.
- **Logo fidelity** — the header/poster logos use the official Verbund + fifty1 files supplied by the user.

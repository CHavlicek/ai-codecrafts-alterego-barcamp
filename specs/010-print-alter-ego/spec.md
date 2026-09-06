# Feature Specification: Print Alter Ego

**Feature Branch**: `010-print-alter-ego`
**Created**: 2026-04-24
**Status**: Draft
**Input**: User description: "Add a 'Print Alter Ego' action to the Alter Ego tab. It triggers the browser's native print dialog to print a two-sided page: the FRONT side shows the generated alter-ego image (with logos already composited on top by the backend — i.e. the same binary the user currently sees in the Alter Ego tab). The BACK side shows the text fields associated with the alter ego — first name, superpowers (archetype), tagline, and any other visible text/metadata (pose, universe, vibe, art style). No text appears on the front; no image appears on the back. The button must only be available after a successful (real or fallback) generation — same gating as the rest of the Alter Ego tab content. Printing must not mutate session state and must respect the no-persistence constraint. Implementation is frontend-only; reuse the already-loaded image bytes (no re-fetch). Closes issue #24."

## Clarifications

### Session 2026-04-24

- Ambiguity scan performed across Functional Scope, Domain/Data, UX Flow, Non-Functional, Integration, Edge Cases, Constraints, Terminology, and Completion Signals — no critical ambiguities detected. All decisions that could otherwise drift (back-page field list, humanization of category values, gating on fallback outcome, duplex printing responsibility, button a11y) are already pinned in FR-901…FR-914 and the Assumptions section.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Print a physical keepsake of my alter ego (Priority: P1)

As a participant who just generated their alter ego at a live event (e.g. a SQUER
CodeCrafts booth), I want to press a single "Print" button on the Alter Ego tab
so a two-sided page comes out of the nearest printer: the front shows my
illustrated alter-ego poster (with SQUER + CodeCrafts logos composited on it),
and the back lists my alter-ego text (name, superpowers, tagline, and the
Setup categories I picked). I can take that printout home as a memento.

**Why this priority**: This is the entire feature. Without it the user cannot
produce a physical artefact, which is the single value proposition of the
issue. Everything else (loading states, start-over, etc.) already works.

**Independent Test**: Generate an alter ego end-to-end (real or fallback path —
both are in scope per FR-905), press Print on the Alter Ego tab, confirm the
browser's native print dialog opens, and confirm the preview shows exactly two
pages: page 1 is the poster image filling the page with nothing else, page 2 is
the alter-ego text fields with no image. Close the dialog without printing; the
Alter Ego tab returns to its prior visual state unchanged.

**Acceptance Scenarios**:

1. **Given** the session phase is `succeeded` and the Alter Ego tab is active,
   **When** the user clicks the Print button, **Then** the browser's native
   print dialog opens with a two-page print preview (page 1 = poster image, page
   2 = text fields).
2. **Given** the session phase is `failed_with_fallback` (stub poster served
   with a non-blocking banner), **When** the user clicks the Print button,
   **Then** the same two-page print preview opens; the banner copy is NOT
   included on either page.
3. **Given** the print dialog is open, **When** the user cancels or completes
   printing, **Then** the Alter Ego tab returns to its pre-print visual state,
   session state is unchanged, no network request has fired, and the poster
   image is still visible.
4. **Given** the user has completed or cancelled printing, **When** they press
   Print a second time, **Then** the dialog opens again with the same two-page
   layout (idempotent — no "already printed" latch).

---

### User Story 2 - Know when Print is available (Priority: P2)

As a user on the Alter Ego tab before or during generation, I expect not to see
a misleading Print button that would print an empty or half-generated poster.

**Why this priority**: Gating prevents a broken first impression but does not
block the core journey. The existing panel already hides generated content
behind the `succeeded` / `failed_with_fallback` phases; Print must piggy-back on
the same gate so users never see an inconsistent affordance.

**Independent Test**: Open the app fresh, navigate through the flow, and
confirm the Print button is NOT rendered (or is inert) in the empty-state and
loading-state variants of the Alter Ego tab, and that it IS rendered in both
the real-success and fallback-success variants.

**Acceptance Scenarios**:

1. **Given** the session phase is `idle` or `generating`, **When** the user
   looks at the Alter Ego tab, **Then** the Print button is not presented as an
   active control (it may be absent entirely — see FR-904).
2. **Given** the user clicks Start Over after printing, **When** the session
   returns to its pre-generation state, **Then** the Print button disappears
   together with the poster and text.

---

### Edge Cases

- **Browser blocks pop-ups / print dialog**: The print trigger is a direct,
  user-gesture-initiated call to the browser's built-in print machinery (not a
  new window). Modern browsers do not pop-up-block this path. No custom error
  handling required; if the browser suppresses the dialog anyway, the user sees
  their own browser's notification — we do not shadow it with app UI.
- **Printer not configured on the device**: Out of scope. The native print
  dialog handles "no printers found" itself.
- **Extremely long superpower strings / tagline**: The back-side layout MUST
  wrap text rather than clip it (FR-912). The front side is pure image and is
  unaffected.
- **Printing while a second generation is in flight**: Not reachable. The Print
  button is only rendered when phase is `succeeded` or `failed_with_fallback`,
  and kicking off a new generation moves the phase to `generating`, which
  unmounts or disables the button before the next print could fire.
- **User prints the page twice**: Supported. Each press re-opens the dialog;
  no state changes between presses.
- **User navigates away mid-dialog**: The native dialog is modal at the
  browser/OS level — navigation within the SPA is blocked until the dialog
  closes. Not a failure mode we need to handle.
- **Screen readers / assistive tech**: The Print button MUST have an
  accessible name that conveys its purpose ("Print my alter ego" or equivalent)
  and MUST be reachable by keyboard (Tab + Enter/Space). See FR-913.
- **Reduced motion / dark mode / contrast**: Printing is visual output on
  paper; print styles MUST force a white background, black ink-safe text, and
  the generated image at its native aspect ratio regardless of on-screen theme.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-901**: System MUST render a Print action control on the Alter Ego
  tabpanel whenever (and only whenever) `session.phase` is `succeeded` OR
  `failed_with_fallback` AND `session.result` is present — the identical gate
  that admits the existing `PosterView` + Start Over controls (CLAUDE.md §007).
- **FR-902**: Activating the Print control (click or keyboard activation) MUST
  invoke the browser's native print dialog for the current document; it MUST
  NOT open a new tab/window, upload anything, or fetch the poster image again.
- **FR-903**: The printed output MUST consist of exactly TWO pages in the
  preview and in the emitted print job:
  - **Page 1 (Front)**: the generated poster image (the same bytes the user
    sees in the Alter Ego tab, with logos already composited by the backend per
    feature 008). No text, no decoration, no headers/footers added by the app.
  - **Page 2 (Back)**: the generated alter-ego text fields and the Setup
    selections that produced them (see FR-906). No image.
- **FR-904**: When the Print control is not available (phases `idle` /
  `generating` / pre-gen), the control MUST NOT be presented to the user as an
  actionable affordance. It MAY be absent from the DOM, or present-but-disabled
  with `aria-disabled="true"` — implementer's choice, consistent with the
  pattern already used for tab gating (CLAUDE.md §007).
- **FR-905**: Both outcomes MUST be printable: `outcome === 'real'` AND
  `outcome === 'fallback'`. The fallback banner copy MUST NOT be included on
  either printed page (it is a screen-only advisory, not part of the keepsake).
- **FR-906**: The Back page MUST include the following fields, labelled in
  plain language, when present on the session:
  - Alter-ego name (rendered from `character.heroTitleLine1` and
    `character.heroTitleLine2` — the hero title displayed on screen)
  - User's first name (`selections.firstName`)
  - Tagline (`character.tagline`)
  - The three superpowers (`character.superpowers`, as a list)
  - Quote (`character.quote`)
  - Setup choices: Pose, Archetype, Universe, Art Style (always present),
    and Vibe (only when selected — Vibe is optional per 002)
  - A small human-readable date of generation (local date, no time)
- **FR-907**: On the Back page, category values MUST be rendered in the same
  human-readable form used elsewhere in the UI (e.g. `cloud-architect` →
  "Cloud Architect") — not the raw kebab-case wire value.
- **FR-908**: The act of printing MUST NOT mutate `session` state, MUST NOT
  trigger any HTTP request to the backend, and MUST NOT write any user data
  (photo, selections, generated character, generated image) to disk, browser
  storage (localStorage / sessionStorage / IndexedDB / Cache API), cookies, or
  a server log. This restates and extends 001 FR-016 / FR-017 / FR-024 for the
  new surface.
- **FR-909**: The print layout MUST use the browser's default A4 (or Letter,
  per user printer settings) page size, one artefact per page, portrait
  orientation on both pages. The poster image on the Front page MUST be scaled
  to fit inside the printable area while preserving aspect ratio — never
  cropped, never stretched.
- **FR-910**: The Front page MUST NOT include any app chrome — no header,
  footer, URL, page number, navigation, or surrounding background colour. The
  browser's own "Headers and footers" print option is owned by the user; the
  app MUST NOT try to override it but MUST NOT rely on it being off either
  (i.e. the app layer adds no chrome of its own).
- **FR-911**: Non-print surfaces (every tab, every screen UI element that is
  not the print artefact) MUST NOT appear in the print preview. The Setup
  tabpanel, tab strip, Alter Ego panel controls (Start Over, Print itself),
  and the on-screen fallback banner are all print-hidden.
- **FR-912**: The Back page MUST wrap long text (taglines, quotes, superpower
  descriptions) across multiple lines rather than clipping. If the rendered
  content exceeds one page, the browser's default page-break behaviour is
  acceptable (the goal is a keepsake, not a fixed-layout certificate).
- **FR-913**: The Print control MUST be keyboard-operable (Tab-reachable;
  Enter and Space both activate it) and MUST have an accessible name that
  communicates the action (screen-reader text like "Print my alter ego" is
  acceptable; an icon-only control without a label is not).
- **FR-914**: Pressing Print MUST be idempotent — repeated presses open the
  dialog again with identical output. There is no "already printed" flag.

### Key Entities

- **Print Artefact** (transient, in-memory only, rendered by the frontend for
  the lifetime of the print dialog):
  - *Front face*: reference to the already-decoded poster image bytes that are
    currently being displayed in `PosterView`. No copy, no re-encode, no
    re-fetch.
  - *Back face*: a structured view over the existing `AlterEgoResponse`
    (`character` + `meta`) and `Selections` already in session state. Read-only.

No new persisted data is introduced and no existing entity changes shape.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-901**: From the moment the alter-ego poster is visible, a user can open
  the print dialog in under **2 seconds** with a single click or single
  keyboard activation — no loading spinner, no intermediate screen.
- **SC-902**: In 100% of prints, the Front page shows the generated poster
  image and nothing else (no text, no chrome); the Back page shows the
  alter-ego text and nothing else (no image). Verified by opening the print
  preview on each supported browser.
- **SC-903**: Printing the same generation twice produces byte-identical
  preview pages — zero hidden state accumulates between presses.
- **SC-904**: Zero new network requests fire as a result of pressing Print.
  Verified by inspecting the network panel during a print action.
- **SC-905**: Zero bytes of user data (photo, text, image) are written to
  browser storage or cookies as a result of pressing Print. Verified by
  comparing storage state before and after.
- **SC-906**: The Print control is reachable via `Tab` from the poster area
  and operable with both `Enter` and `Space`; it has a non-empty accessible
  name. Verified with keyboard-only navigation and screen-reader inspection.
- **SC-907**: In a user test with 5 event attendees unfamiliar with the flow,
  at least **4 of 5** succeed in printing a two-sided page on the first
  attempt without coaching.

## Assumptions

- The event venue provides a duplex-capable (two-sided) printer OR the user
  has a single-sided printer and is comfortable re-feeding the paper for the
  back. The SPA does NOT attempt to force duplex printing — that is a driver /
  user responsibility.
- "Logos on the front" are assumed to already be composited into the poster
  image bytes by the backend (feature 008 — see CLAUDE.md Recent Changes). The
  frontend does not re-composite, re-render, or fetch the logos separately.
- Supported browsers are evergreen Chrome, Firefox, Safari, and Edge on
  desktop and tablet. Mobile browsers are out of scope for print quality but
  the button MUST NOT throw or crash on mobile.
- No new dependency is introduced. Printing is done via the browser's native
  `window.print()` plus CSS print styles (`@media print`). No PDF library, no
  canvas re-encode, no server round-trip.
- No backend change. No new API contract. No OpenAPI update. The SQLite / JPA
  stack remains unwired (consistent with 001 FR-016 / 017 / 024).
- The feature is additive: no existing Alter Ego tab copy, layout, or behaviour
  changes aside from adding the Print control next to Start Over.
- Accessibility baseline is inherited from the existing TabsShell + PosterView
  components — no new WCAG work beyond correctly labelling the new button.

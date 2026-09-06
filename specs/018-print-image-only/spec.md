# Feature Specification: Print only the alter ego image

**Feature Branch**: `018-print-image-only`
**Created**: 2026-05-08
**Status**: Draft
**Input**: GitHub issue [#47](https://github.com/squer-solutions/aiavatar/issues/47) — *"Remove 2nd page from printable material"*: "Print action should now be applied to the image only. Print action should show only a single printable page with the image. The second page with texts: the name, tag lines, quote etc should be removed from the printable material."

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Print produces exactly one page, containing only the poster image (Priority: P1)

After generating an alter ego, the user prints it from the "Your Alter Ego" tab. The printed output is a single sheet of paper (or a single PDF page when the user chooses *Save as PDF*) showing only the alter ego poster image. The previously-included second page — which listed the hero title, first name, tagline, superpowers, quote, the humanised Pose / Archetype / Universe / Art Style / Vibe selections, and the "Printed" date — no longer appears in the printed material.

**Why this priority**: This is the entirety of the change requested in #47. The redundant second page wastes paper, doubles ink usage, and forces a multi-page print job for a deliverable the user thinks of as a single poster. After feature 017 baked the hero name, role, and quote directly onto the poster image, the back-side text page is also informationally redundant — everything the user wants on a printed keepsake is already inside the image itself. Shipping anything less than this is not a meaningful response to the issue.

**Independent Test**: Generate an alter ego, trigger the browser's Print action (Ctrl/Cmd+P or the in-app Print button), and inspect the print preview / paper output. Pass criteria: the dialog reports exactly **one** page; that page shows the alter ego image; no other content (hero title block, definition list of fields, "Printed" date, etc.) appears anywhere in the printable material. Repeat with *Save as PDF* — the resulting PDF has exactly one page.

**Acceptance Scenarios**:

1. **Given** the user has just generated an alter ego (real generation), **When** they invoke the Print action, **Then** the print preview shows exactly one page and that page contains only the poster image.
2. **Given** the user has just generated an alter ego that fell back to the bundled fallback poster, **When** they invoke the Print action, **Then** the print output is identical in shape to the real-generation case — exactly one page, image only — and contains no fallback-specific or diagnostic text.
3. **Given** the user previously printed an alter ego before this change shipped (and saw two pages), **When** they print again after this change, **Then** they observe one fewer page in the print dialog and on the resulting paper / PDF.
4. **Given** the user is viewing the alter ego on screen, **When** this change ships, **Then** the on-screen "Your Alter Ego" panel is visually unchanged — the hero title, tagline, superpowers, quote, and selection chips are still displayed on screen exactly as before; only the printable artefact loses the textual page.
5. **Given** the user has selected an optional Vibe (or has not selected one), **When** they invoke the Print action, **Then** in both cases the print output is exactly one page containing only the image — Vibe presence/absence has no effect on the printed material.

---

### Edge Cases

- **Print preview parity**: the browser's print preview must match what actually prints — both must show exactly one page. A fix that affects the rendered document but leaves the preview showing two pages is not acceptable.
- **Save as PDF**: when the user chooses the "Save as PDF" destination, the resulting PDF file must contain exactly one page.
- **Page orientation / paper size**: the change must not depend on a particular paper size or orientation. Whatever the user's selected paper size and orientation, the image fits onto a single page (scaling down if necessary), and there is no second page regardless.
- **Image scaling on the page**: the image sizing behaviour on the printed page is preserved from the existing print feature — fit to the printable page area, preserve aspect ratio. The change in this feature is strictly removal of the second page; it does not retune image sizing.
- **Accessibility**: the on-screen experience is unchanged, so screen-reader users continue to hear the same announcement and read the same on-screen text. The printed artefact remains decorative from an assistive-tech standpoint (it is hidden from AT in the on-screen DOM today; that posture is preserved).
- **Browser variation**: the single-page outcome must hold across the supported evergreen browsers (Chrome, Firefox, Safari, Edge). A change that produces one page in one browser but two in another is not acceptable.
- **Repeated prints in one session**: invoking Print, cancelling, and invoking Print again produces the same single-page output each time — the artefact must not accumulate or leave a stale second page on the second invocation.
- **Start-over after print**: after printing, the user clicking *Start over* and going through a new generation must continue to produce single-page prints for the new alter ego.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-1801**: The Print action MUST produce exactly one printable page.
- **FR-1802**: The single printable page MUST display only the alter ego poster image — the same image the user sees on the "Your Alter Ego" tab — and no other content.
- **FR-1803**: The printable material MUST NOT contain the hero title, first name, tagline, superpowers list, quote, humanised Pose / Archetype / Universe / Art Style / Vibe selections, "Printed" date, or any other textual or graphical content beyond the image itself.
- **FR-1804**: The browser's print preview MUST match the printed output — both MUST show exactly one page.
- **FR-1805**: When the user selects *Save as PDF* (or any equivalent print-to-file destination), the resulting file MUST contain exactly one page with the same image-only content.
- **FR-1806**: The image on the printed page MUST fit within the printable page area, preserving aspect ratio (carry-over from feature 010).
- **FR-1807**: The print output MUST be identical in shape regardless of whether the alter ego was produced by a real generation or by the bundled fallback poster (carry-over from 010 FR-905).
- **FR-1808**: The print output MUST NOT depend on whether the user selected an optional Vibe — both Vibe-set and Vibe-unset sessions MUST produce the same single-page, image-only print.
- **FR-1809**: The on-screen "Your Alter Ego" panel MUST be unchanged by this feature — all text content currently shown on screen (hero title, tagline, superpowers, quote, selection chips, etc.) MUST remain visible on screen exactly as before.
- **FR-1810**: No information from the alter ego session — including the previously-printed text fields — MUST be persisted as a result of this change (carry-over from 001 FR-016 / FR-017 / FR-024). The change is removal of output, not addition of any new storage.
- **FR-1811**: The single-page outcome MUST hold across supported evergreen browsers (Chrome, Firefox, Safari, Edge) on desktop platforms where Print is offered today.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-1801**: 100% of print invocations from the "Your Alter Ego" tab produce exactly one page (down from the current two).
- **SC-1802**: 0% of printed pages contain any of the previously-printed text fields (hero title / first name / tagline / superpowers / quote / Pose / Archetype / Universe / Art Style / Vibe / "Printed" date).
- **SC-1803**: A user who prints to paper uses exactly one sheet per alter ego — a 50% reduction in paper and ink consumption per alter ego compared to the pre-change behaviour.
- **SC-1804**: A user who saves the print as PDF gets a one-page file — verifiable by opening the PDF and observing a single page.
- **SC-1805**: Browser print preview agreement: every supported evergreen browser shows the same single-page preview, with no browser producing a stray blank or text page.
- **SC-1806**: No measurable change to the on-screen "Your Alter Ego" panel — the same text content remains visible on screen, observable by side-by-side comparison with the pre-change build.

## Assumptions

- **Image sufficiency**: After feature 017 baked the hero name, role, and quote into the poster image itself, the image alone is a sufficient printable keepsake. The textual back page is informationally redundant for the user's stated purpose (a printable poster), and removing it does not cost the user any unique information they cannot already see — both on screen (where the text remains) and inside the printed image (where 017's overlay carries name + role + quote).
- **Scope is removal-only**: This feature deletes the second page from the printable artefact. It does not redesign the first page, retune image sizing on the page, change the on-screen layout of the "Your Alter Ego" tab, or add any new printable content. The Print button's placement, label, and triggering behaviour are unchanged.
- **Print-button entry point unchanged**: The user's path to printing — the in-app Print button on the "Your Alter Ego" tab plus the standard browser Print shortcut — is unchanged. This feature only affects what the printable material looks like once the print pipeline is engaged.
- **Accessibility posture preserved**: The on-screen textual content remains the canonical source of the alter ego's metadata for screen-reader and keyboard users. No accessibility regression results from removing the print-only back page, because the print-only artefact is already hidden from assistive tech in the on-screen DOM today.
- **No persistence introduced**: Consistent with 001 FR-016 / FR-017 / FR-024 and every subsequent feature in this repo, this change introduces no persistence of any kind. It is a pure rendering-output change confined to one HTTP session in the browser.
- **Browser support boundary**: "Supported evergreen browsers" means current versions of Chrome, Firefox, Safari, and Edge on desktop. Mobile printing and legacy browsers remain out of scope, matching the implicit boundary of feature 010.

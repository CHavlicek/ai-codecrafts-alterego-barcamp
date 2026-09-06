# Research: Print Alter Ego

**Feature**: 010-print-alter-ego
**Date**: 2026-04-24
**Status**: Resolved — no NEEDS CLARIFICATION outstanding

## R1. Print trigger: `window.print()` vs hidden iframe vs canvas-to-PDF vs server-generated PDF

**Decision**: Call `window.print()` directly from the Print button's `onClick` handler. No iframe, no PDF library, no server round-trip.

**Rationale**:
- `window.print()` is a direct user-gesture-initiated call — modern browsers (Chromium, Firefox, WebKit, Edge) never pop-up-block it (spec Edge Cases).
- No new dependency (Constitution Principle VI; spec Assumptions).
- No new HTTP call (FR-908, SC-904).
- No new Blob or Object URL (FR-908, SC-905) — the poster `img` already on screen is what gets printed.
- The browser's own print dialog is a11y-mature; we inherit its keyboard handling and screen-reader behaviour for free.

**Alternatives considered**:
- **Hidden `<iframe>` with srcdoc + `frame.contentWindow.print()`**: allows "print only this thing" in theory, but in practice evergreen browsers already do the same thing via `@media print` CSS without the overhead of a secondary document, and iframes break screen-reader continuity. Rejected.
- **`html2canvas` + `jsPDF`**: would add ~180 kB gzipped of runtime dependency for zero user-visible improvement, re-rasterize the poster at a different resolution (SC-902 regression), and require maintaining a second humanization surface. Rejected on Constitution Principle VI.
- **Server-side PDF via a new backend endpoint**: direct violation of spec's "frontend-only, no re-fetch" and FR-908's no-persistence posture. Also introduces Spring Boot work for a problem native browsers already solve. Rejected.

## R2. Print DOM placement: always-mounted-hidden vs conditionally mounted on click

**Decision**: The `<PrintArtefact>` is **always mounted** while `AlterEgoPanel` is in its poster phase, styled `display: none` on screen and `display: block` inside `@media print`.

**Rationale**:
- `window.print()` is synchronous — if we mount the print DOM *in response to* the click, React's commit phase may not have flushed before the native dialog captures the snapshot, producing a blank page on some browsers (Safari most notably).
- The hidden tree costs nothing to keep mounted: it's roughly one `<img>` reference (same bytes as `PosterView` — no re-decode) and one `<dl>` with ~12 rows of text.
- Idempotent re-prints (FR-914 / SC-903) trivially hold: the same DOM is used on every press.

**Alternatives considered**:
- **Mount on Print click, unmount on `window.onafterprint`**: fragile across browsers (Safari fires `afterprint` unreliably), and introduces a mid-click render pass. Rejected.
- **React Portal into a detached document**: extra complexity for no benefit — our `@media print` rules can scope to a single class (`.print-artefact`) just as effectively. Rejected.

## R3. Front-page image scaling strategy

**Decision**: In `@media print`, the poster `<img>` on the front page is sized with `width: auto; height: auto; max-width: 100%; max-height: 100vh; object-fit: contain;` inside a `@page { size: A4 portrait; margin: 0; }` wrapper. The `<section.print-artefact__front>` is one page-break-avoided block followed by `page-break-after: always`.

**Rationale**:
- `object-fit: contain` preserves aspect ratio — never crops, never stretches (FR-909).
- `@page { size: A4 portrait; margin: 0 }` delegates paper size to the user's print settings when they override A4 with Letter (FR-909 "A4 or Letter, per user printer settings"). Margins are set to 0 so the image can bleed to the edge of the printable area on machines that support edge-to-edge; machines that don't will use their driver-enforced margin silently.
- Backend feature 008 already composited the logos *inside* the image bytes, so zero-margin is safe — the logos sit inside the safe area the backend enforced.

**Alternatives considered**:
- **`object-fit: cover`**: would crop on aspect-mismatched paper. Rejected (FR-909 forbids cropping).
- **Fixed pixel dimensions**: fails on Letter vs A4. Rejected.
- **`background-image` on a div**: loses the semantic `<img>` + `alt` text, which we still want for a11y tools that intercept print. Rejected.

## R4. Back-page content scope and ordering

**Decision**: The back page is a single `<section.print-artefact__back>` with this structure, top-to-bottom:

1. `<h2>` = `character.heroTitleLine1` + `<small>` = `character.heroTitleLine2` (the same hero title the user sees on screen)
2. `<dl>` with the following `<dt>/<dd>` rows in order:
   - "First name" → `selections.firstName`
   - "Tagline" → `character.tagline`
   - "Superpowers" → `<ul>` of the three `character.superpowers`
   - "Quote" → `<blockquote>` of `character.quote`
   - "Pose" → humanized Pose label
   - "Archetype" → humanized Archetype label
   - "Universe" → humanized Universe label
   - "Art Style" → humanized Art Style label
   - "Vibe" → humanized Vibe label *only if* `selections.vibe` is defined (FR-906)
   - "Printed" → `new Date().toLocaleDateString()` (local date, no time — FR-906)

**Rationale**: The order matches the on-screen `<PosterView>` (hero title → tagline → superpowers → quote) so the back page reads like a continuation of the on-screen view, then the Setup choices appear in the canonical order established by `SetupLayout` (Pose → Archetype → Universe → Art Style → Vibe). The date row is last because it's meta about the print itself, not about the alter ego.

**Alternatives considered**:
- **Use `dl` vs `<ul>` everywhere**: `dl` is the semantically correct term→definition structure for labelled fields; screen readers announce it distinctly from a generic list. Kept.
- **Include the `meta.outcome` / `correlationId`**: FR-905 + FR-908 forbid surfacing the fallback reason or any operator-only metadata to the user. Rejected.
- **Omit the Quote row**: the issue description says "tagline etc" — the quote is a visible character field adjacent to the tagline in `PosterView`, so its exclusion would surprise the user. Included.

## R5. Humanization source of truth: reuse `options.ts` labels vs new map

**Decision**: The new `lib/humanizeSelection.ts` helper re-reads `POSE_OPTIONS`, `ARCHETYPE_OPTIONS`, `UNIVERSE_OPTIONS`, `VIBE_OPTIONS`, `ART_STYLE_OPTIONS` from `features/alterego/options.ts` and builds a five-table lookup at module load. Signature:

```ts
export function humanizePose(v: Pose): string
export function humanizeArchetype(v: Archetype): string
export function humanizeUniverse(v: Universe): string
export function humanizeVibe(v: Vibe): string
export function humanizeArtStyle(v: ArtStyle): string
```

Unknown/future values (forward compatibility) fall through to a passthrough that Title-Cases the kebab value — never throws.

**Rationale**: `options.ts` is already the canonical place where wire values meet display labels (the Setup grid reads it). A separate hand-maintained map would drift on the next feature that adds, say, a 7th archetype. Forward-compatible passthrough guards against the React SPA rendering an alter ego whose selections enum was extended on the backend first.

**Alternatives considered**:
- **Inline string maps inside `PrintArtefact.tsx`**: drifts from `options.ts` on first addition. Rejected.
- **Derive at each use via `.find()`**: O(n) per lookup, 5 categories × ≤ 9 options = trivial, but a module-level `Record<Value, Label>` is equally simple and type-safer. Chose the Record approach.

## R6. Duplex-printing stance: app hint vs silence

**Decision**: The app does NOT render any on-screen hint about duplex printing. The spec Assumptions already states duplex is a driver / user responsibility. The Print button's accessible name is "Print my alter ego" — not "Print my alter ego (duplex)".

**Rationale**: Adding a "use duplex" badge would either mislead users on single-sided printers or duplicate a setting they can already see in the native dialog. The user's print dialog is the right place to surface duplex (it already does).

**Alternatives considered**:
- **Tooltip / info icon next to the button**: noise for the 95% case. Rejected.
- **Different copy for tablet / mobile**: out of scope (mobile print quality out of scope per Assumptions). Rejected.

## R7. CSS isolation strategy for `@media print`

**Decision**: A single `@media print` block appended to `index.css` with this structure:

```css
/* Screen: hide the print-only artefact */
.print-artefact { display: none; }

@media print {
  /* Hide everything */
  body *  { visibility: hidden; }
  /* Show ONLY the print artefact subtree */
  .print-artefact,
  .print-artefact * { visibility: visible; }
  .print-artefact {
    display: block;
    position: absolute; inset: 0;
  }
  .print-artefact__front { page-break-after: always; }
  .print-artefact__front img {
    width: auto; height: auto;
    max-width: 100%; max-height: 100vh;
    object-fit: contain;
    display: block; margin: auto;
  }
  .print-artefact__back { page-break-before: always; }
  @page { size: A4 portrait; margin: 1cm; }
}
```

**Rationale**: The `body *  { visibility: hidden; }` + `.print-artefact * { visibility: visible }` idiom is the standard battle-tested pattern for print-only DOM, supported by every evergreen browser since 2010. It satisfies FR-911 (no tab strip, no Setup panel, no banner, no Start Over) without needing a `print-only` class on every screen element. `@page margin: 1cm` (front) applies to both pages; the zero-margin image bleed is achieved by the inner image's `object-fit: contain` rule, not by eliminating `@page` margins (0-margin `@page` creates driver-dependent surprises).

**Alternatives considered**:
- **`display: none` on non-print elements**: equivalent result on screen but costlier to maintain (every future component must opt-in). Rejected.
- **Separate `print.css` imported late**: adds an extra HTTP request (or at least bundler overhead) for a ~1 KB block. Rejected.

## R8. Test seam for `window.print` in jsdom

**Decision**: In `PrintButton.test.tsx` and in the `AlterEgoPanel.test.tsx` Print assertion, stub `window.print` with a `vi.fn()` in `beforeEach` and restore it in `afterEach`. Assert `toHaveBeenCalledTimes(1)` per user activation.

**Rationale**: jsdom does not implement `window.print`; attempting to call it in tests either no-ops or throws depending on jsdom version. A `vi.spyOn(window, 'print').mockImplementation(() => {})` is the idiomatic seam used by every React print-related project. Does not require a custom mock module.

**Alternatives considered**:
- **Inject a `printFn` prop**: bigger API surface for a component whose entire job is to call `window.print`. Rejected.
- **Extract a `usePrint()` hook**: adds indirection for one function call. Rejected.

## R9. Accessibility and keyboard model

**Decision**:
- `<PrintButton>` is a `<button type="button">` with visible text "Print" and a screen-reader-only sibling span `"Print my alter ego"` under `.visually-hidden` (same pattern used elsewhere). Alternative: `aria-label="Print my alter ego"` directly on the button — equivalent. We choose the visible-text + aria-label approach: visible "Print" (matches Start Over's length/weight) plus `aria-label="Print my alter ego"` for screen readers (richer context without visual noise).
- Keyboard-reachable via `Tab` (inherits from `<button>` — no custom tabindex). Enter AND Space activate it (inherits from `<button>`; no custom keydown handler needed).
- Focus management on Start Over already covers the case where the poster unmounts; we do NOT move focus programmatically on Print click (the native dialog steals focus anyway).

**Rationale**: `<button>` gives us keyboard + screen-reader behaviour for free. FR-913 says the accessible name MUST be non-empty and communicate purpose — `aria-label="Print my alter ego"` satisfies both. We match Start Over's visual weight so the button row doesn't feel lopsided.

**Alternatives considered**:
- **Icon-only button with just `aria-label`**: FR-913 explicitly forbids icon-only without a visible label. Rejected.
- **`<a>` + `role="button"`**: more code, worse screen-reader behaviour. Rejected.

## R10. Idempotency and state-immutability guardrails

**Decision**: The Print click handler is a single-line `() => window.print()`. No `useState`, no `useRef`, no dispatch. No success/failure path — either the dialog opens (and the user dismisses or completes it) or the browser does whatever it does with an un-openable dialog, neither of which is our concern.

**Rationale**: FR-914 (idempotent) and FR-908 (no state mutation) hold by construction if we do literally nothing besides call `window.print()`. Any additional tracking (e.g. "last printed at") would be both a state mutation and a persistence leak.

**Alternatives considered**:
- **Track "have they printed?" to change button copy to "Print again"**: violates FR-914 spirit and adds state. Rejected.
- **Disable button after click for N ms to prevent double-print**: un-needed (the browser's dialog is modal) and would itself be state. Rejected.

## R11. Fallback-outcome printability

**Decision**: Print works identically when `meta.outcome === 'fallback'`. The print DOM mounts on both `phase === 'succeeded'` AND `phase === 'failed_with_fallback'` (same gate as `PosterView`). The on-screen fallback banner (`.poster-view__fallback-banner`) is print-hidden by the blanket `body *  { visibility: hidden }` rule — it's not inside `.print-artefact`.

**Rationale**: FR-905 requires both outcomes be printable. FR-908 requires we NOT leak operator-only metadata (fallback reason, correlation id). The simplest way to satisfy both: the print DOM has no dependency on `meta.outcome` — it reads only `character` + `selections`, which are identical shape in both cases (the fallback character is synthesised by `synthesiseFallbackResponse` with the same typed fields).

**Alternatives considered**:
- **Print a "generated in offline mode" watermark on fallback**: leaks information FR-908 forbids surfacing. Rejected.
- **Disable Print on fallback**: FR-905 explicitly requires fallback be printable. Rejected.

## R12. Composition with 007 tab gating and 005 entrance animation

**Decision**: Nothing to do. Gating is handled by `AlterEgoPanel`'s existing phase switch; when phase is `idle` or `generating`, `AlterEgoPanel` returns the empty-state or loading-state JSX branches and neither `<PrintButton>` nor `<PrintArtefact>` is in the tree (FR-901, FR-904). The 005 entrance animation targets the `.alter-ego-panel--poster` element — our `@media print` block sets that element to `display: block` (no transform/opacity overrides), so the screen animation and the print output are orthogonal.

**Rationale**: Composition by passive piggy-backing on existing structure. Zero new gating logic. Zero new tab-state interaction.

**Alternatives considered**:
- **Put `<PrintButton>` inside `PosterView`**: works but couples the image-display component with an action concern. Rejected — kept the action inside `AlterEgoPanel` alongside Start Over, matching the existing layout convention.
- **Introduce a new `canPrint(state)` selector**: redundant with the existing phase switch. Rejected.

---

## Resolved NEEDS CLARIFICATION markers

None — the spec's Clarifications session recorded that no external clarification was required. All ambiguities were resolved by the decisions above.

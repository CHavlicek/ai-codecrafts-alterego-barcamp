# Quickstart: Print Alter Ego

**Feature**: 010-print-alter-ego
**Audience**: anyone touching the feature for review, smoke-test, or extension.

## Run it

```bash
# From repo root:
cd frontend
npm install   # idempotent — no new dependencies for 010
npm run dev
```

Open the printed Vite URL (typically http://localhost:5173), then:

1. Click **Start camera** on the Setup tab, grant permission, snap a photo.
2. Type a first name (≥ 1 char, trimmed).
3. Pick each of Pose / Archetype / Universe / Art Style. Vibe is optional.
4. Click **Generate** (or **Surprise Me** from feature 009).
5. Wait for the poster to render on the "2 Your Alter Ego" tab.
6. Click **Print** — the browser's native print dialog opens.
7. In the print preview, confirm:
   - **Page 1**: only the poster image, filling the printable area, aspect ratio preserved, no text, no tab strip, no banner.
   - **Page 2**: the text block — hero title, first name, tagline, superpowers (3 bullets), quote, humanized Pose / Archetype / Universe / Art Style (and Vibe if picked), "Printed" date.

Cancel the dialog. Verify the on-screen poster is exactly as it was.

## Manual validation flows

### Flow A — US1 real success (P1)

Real-generation + Print, asserts US1 Acceptance 1 + 3.

1. Set `VITE_ALTER_EGO_API_BASE` so the real backend is reachable (or rely on `msw` handler if test mode).
2. Generate. Verify `meta.outcome === 'real'` (no banner).
3. Press Print. Confirm two-page preview with image on page 1, text on page 2.
4. Close dialog. Confirm no console error, no change in the poster node, no new network request in the Network tab.

### Flow B — US1 fallback outcome (P1, per FR-905)

Print must work identically when the backend fails and the FE synthesises a fallback.

1. Simulate backend failure (stop the Spring Boot app or block the API endpoint in DevTools).
2. Generate. The FE fallback path fires (see `features/alterego/services/alterEgoClient.ts` → `synthesiseFallbackResponse`). Verify the on-screen yellow "fallback" banner is visible.
3. Press Print. Confirm the print preview shows the same two-page layout.
4. **Confirm the fallback banner copy does NOT appear on either printed page** (FR-905, FR-911).

### Flow C — US2 gating (P2)

Print must not be offered before a poster exists.

1. Fresh reload the app. The "Your Alter Ego" tab is disabled (007). Even after activating it, the empty-state text is shown. Confirm there is NO Print button on screen.
2. Start Generate but intercept the request so it never resolves (or simply observe during the real latency). During the loading state, confirm there is NO Print button on screen.
3. After the poster renders, confirm the Print button IS on screen and reachable via Tab.
4. Click Start Over. Confirm the Print button disappears together with the poster.

### Flow D — Keyboard-only print (P2, SC-906)

1. Complete the flow up to poster render.
2. Put focus on the first focusable element in the poster area and Tab until the Print button is focused (visible focus ring).
3. Press **Enter**. Dialog opens. Cancel. Re-focus.
4. Press **Space**. Dialog opens. Cancel.
5. Use a screen reader (VoiceOver / NVDA / TalkBack) to confirm the button is announced as "Print my alter ego, button".

### Flow E — Idempotent re-print (FR-914, SC-903)

1. Render a poster, press Print, cancel.
2. Press Print again — confirm the preview is byte-identical.
3. Open DevTools → Application → Storage, take a snapshot. Press Print. Cancel. Take another snapshot. Diff: zero keys, zero bytes, zero new cookies.
4. Open DevTools → Network, clear. Press Print. Confirm zero requests fire.

## Automated tests

Run from `frontend/`:

```bash
npm run test          # Vitest — PrintButton, PrintArtefact, humanizeSelection, AlterEgoPanel integration
npm run test:e2e      # Playwright — tests/e2e/print-alter-ego.spec.ts
npm run lint          # Flat-config ESLint over new files + changes
npm run build         # Verifies the CSS and JSX compile with the @media print block
```

Where the new tests live:

- `frontend/src/features/alterego/components/PrintButton.test.tsx`
- `frontend/src/features/alterego/components/PrintArtefact.test.tsx`
- `frontend/src/features/alterego/components/AlterEgoPanel.test.tsx` *(extended)*
- `frontend/src/features/alterego/lib/humanizeSelection.test.ts`
- `frontend/tests/e2e/print-alter-ego.spec.ts`

## What to check in a review

1. **FR-901 / FR-904 gating**: `AlterEgoPanel` only renders `<PrintButton>` + `<PrintArtefact>` inside the `succeeded || failed_with_fallback` branch. The empty-state and loading-state branches don't mount either.
2. **FR-903 two-page layout**: `<PrintArtefact>` renders exactly `<section.print-artefact__front>` and `<section.print-artefact__back>`, in that order, as siblings, and the CSS rules ensure one page-break between them.
3. **FR-905 fallback parity**: no code path in `<PrintArtefact>` reads `meta.outcome` or `meta.reason`. The test `renders identically for fallback outcome` asserts this.
4. **FR-906 back-page scope**: every field listed in the spec appears in the back-side `<dl>`; Vibe row omitted when `session.vibe === undefined`.
5. **FR-907 humanization**: the four mandatory categories are rendered via `humanize*` helpers; no raw kebab-case leaks onto paper.
6. **FR-908 no persistence**: grep the diff for `localStorage`, `sessionStorage`, `indexedDB`, `fetch`, `XMLHttpRequest`, `document.cookie`, `createObjectURL`, `dispatch({` — hits MUST NOT appear in `PrintButton.tsx` / `PrintArtefact.tsx` / `humanizeSelection.ts`.
7. **FR-909 + R3 image scaling**: the `@media print` rule on `.print-artefact__front img` uses `object-fit: contain`, not `cover`.
8. **FR-913 a11y**: `<PrintButton>` is a `<button type="button">` with `aria-label="Print my alter ego"` (or equivalent visible + aria combination) and visible text.
9. **CSS isolation**: the `@media print` block visibility-hides everything and visibility-shows only the `.print-artefact` subtree.
10. **No new dependency**: `package.json` diff is empty (or limited to a `package-lock.json` churn from `npm install`).

## Rollback

Feature is additive. Rolling back means removing:

- `frontend/src/features/alterego/components/PrintButton.tsx`
- `frontend/src/features/alterego/components/PrintArtefact.tsx`
- `frontend/src/features/alterego/lib/humanizeSelection.ts`
- The `@media print` block (and the matching `.print-artefact { display: none }` screen-default) from `frontend/src/index.css`
- The two lines in `AlterEgoPanel.tsx` that render `<PrintButton>` + `<PrintArtefact>` in the poster branch
- Their companion `*.test.tsx` files and the e2e spec

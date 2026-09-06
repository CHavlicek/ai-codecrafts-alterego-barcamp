# Quickstart: Print only the alter ego image

**Feature**: `018-print-image-only`
**Date**: 2026-05-08
**Spec**: [./spec.md](./spec.md) | **Plan**: [./plan.md](./plan.md) | **Research**: [./research.md](./research.md) | **Data model**: [./data-model.md](./data-model.md)

This document is for a developer or reviewer who wants to validate the feature locally. It covers the local-run setup and the manual checks that map directly to FR-1801..FR-1811 and SC-1801..SC-1806.

---

## 1. Run locally

From repo root:

```bash
# Terminal 1 — backend (any provider works; Gemini stub is fine for the print check)
cd backend
./gradlew bootRun

# Terminal 2 — frontend (Vite dev server)
cd frontend
npm install   # only on first run / after pulls
npm run dev
```

Open the printed dev URL (default `http://localhost:5173`).

## 2. Drive the happy path to a poster

1. Allow camera; capture (or pick) a photo.
2. Type a first name (e.g. **Paula**).
3. Pick one option in each of the five Setup categories (Pose / Archetype / Universe / Art Style; Vibe optional).
4. Click **Generate my alter ego**. Wait for the loading view to resolve and the poster to land on the **Your Alter Ego** tab.

> Tip: the **Surprise me** button (009) fills all five categories for you and clicks Generate in one go — useful if you're iterating on the print check repeatedly.

## 3. The check that matters (FR-1801..FR-1811 / SC-1801..SC-1806)

### 3.a Single-page paper print

1. With the poster visible, press the in-app **Print my alter ego** button (or `Ctrl/Cmd+P`).
2. Inspect the **page count** in the browser's print dialog. It MUST read **1 of 1** (FR-1801, FR-1804, SC-1801, SC-1805).
3. Inspect the **preview image**. It MUST show the alter-ego poster image only — no hero title, no first name, no tagline, no superpowers list, no quote text outside the image, no Pose / Archetype / Universe / Art Style / Vibe rows, no "Printed" date row (FR-1802, FR-1803, SC-1802).
4. Optional sanity print: press **Print** to your default printer or a virtual PDF printer; confirm exactly one sheet emerges (paper) or one page in the file (virtual). 50 % less paper / ink than before — visible (SC-1803).

### 3.b Save-as-PDF page count

1. Open the print dialog (`Ctrl/Cmd+P`).
2. Set **Destination** to **Save as PDF** (Chromium / Edge), **Save to PDF** (Firefox), or **Microsoft Print to PDF** / **PDF** (other dialogs).
3. Save the file.
4. Open the resulting `.pdf` in any PDF viewer. **Page count = 1** (FR-1805, SC-1804).

### 3.c On-screen unchanged (FR-1809 / SC-1806)

1. Stay on the **Your Alter Ego** tab after the print check.
2. Confirm the on-screen panel still shows the hero title block, the first name, the tagline, the three superpowers, the quote, and the selection chips — exactly as before this feature shipped. (Easiest: side-by-side compare the screen against a screenshot from a build before this branch was merged.)

### 3.d With and without Vibe (FR-1808)

1. Generate an alter ego **with** a Vibe selected; print → 1 page, image only.
2. Click **Start over**, generate again **without** a Vibe; print → 1 page, image only. Identical structural outcome.

### 3.e Fallback parity (FR-1807)

To force a fallback outcome (no real provider call):

```bash
# Backend: temporarily set the provider env to an invalid endpoint or kill the
# provider key. Easiest: stop the backend, clear the relevant env var, restart.
unset GEMINI_API_KEY            # or: unset FALAI_API_KEY (depending on active provider)
./gradlew bootRun
```

Generate; the response will carry `meta.outcome=fallback` and the bundled fallback poster will appear. Print → still **1 page**, still **image only**. The user-visible printed material is structurally identical to the real-generation case — no fallback markers leak onto paper.

### 3.f Browser matrix (FR-1811 / SC-1805)

Repeat 3.a + 3.b in current versions of:

- **Chrome** (Chromium reference)
- **Firefox**
- **Safari** (macOS only)
- **Edge** (Chromium-based — quick re-check, mostly redundant with Chrome)

In each browser, the print preview MUST show **1 of 1**. No browser may produce a stray blank or text page.

## 4. Repeat-print idempotency (FR-1808 carry-over from 010 FR-914)

1. Print once; confirm 1 page.
2. Cancel; press **Print** again; confirm 1 page again.
3. Optionally open DevTools → Elements and inspect `.print-artefact`'s innerHTML between presses — it MUST be byte-identical (no time-varying value remains in the artefact after the back is removed).

## 5. Start-over teardown

1. From a printed state, click **Start over**.
2. Confirm the **Print my alter ego** button disappears together with the poster (gating by `phase` carry-over from 010).
3. Confirm `document.querySelector('.print-artefact')` returns `null` in DevTools (the artefact unmounts).
4. Drive a new generation; the new prints continue to be 1 page each.

## 6. Where the new tests live

- **Unit / component**: `frontend/src/features/alterego/components/PrintArtefact.test.tsx`. Run with `cd frontend && npm run test --filter print-artefact` (or equivalent Vitest filter command).
- **End-to-end**: `frontend/tests/e2e/print-alter-ego.spec.ts`. Run with `cd frontend && npx playwright test print-alter-ego`.

After this branch ships, both files assert the **image-only** invariant (no `.print-artefact__back` element, exactly one child of `.print-artefact`, single front `<section>` with one `<img>`) and the surviving 010 invariants (portal mounting, idempotent re-press, no network / storage side effects, Start-Over teardown).

## 7. Pre-merge checklist (developer)

- [ ] `cd frontend && npm run lint` clean.
- [ ] `cd frontend && npm run test` — unit suites green.
- [ ] `cd frontend && npx playwright test print-alter-ego` — E2E green.
- [ ] `cd frontend && npm run build` — production build succeeds.
- [ ] Steps 3.a, 3.b, 3.c above performed manually in at least one Chromium browser.
- [ ] Constitution III TDD ordering: **RED commit precedes the GREEN commit** in branch history (research §R5).
- [ ] No new dependency in `frontend/package.json`.
- [ ] No backend file change under `backend/`.
- [ ] No persistence surface introduced.

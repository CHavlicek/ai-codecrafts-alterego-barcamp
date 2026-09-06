# Quickstart: Partial Surprise Me

**Feature**: 028-partial-surprise-me
**Date**: 2026-05-20

Five-minute reviewer/operator validation. Assumes you have the repo cloned and `npm install` + `./gradlew build` have run at least once.

---

## 1. Run the stack

In two terminals from the repo root:

```bash
# Terminal A — backend (port 8080 by default)
./gradlew :backend:bootRun
```

```bash
# Terminal B — frontend (port 5173 by default)
cd frontend && npm run dev
```

Open `http://localhost:5173` in a browser.

> If you're on a fresh checkout and Gemini / fal.ai keys aren't configured, the backend will report `outcome=fallback, reason=not_configured` — that's fine for this feature; Surprise Me's request path is identical regardless of provider outcome.

---

## 2. Validate US1 — preserve one explicit category

1. Click the camera disc on the Setup tab and capture a photo (any photo; quality doesn't matter).
2. In the **Universe** grid, click **Star Wars** (or any one option).
3. **Leave Role and Art Style empty.** Leave Custom Role blank.
4. Type a first name (e.g., "Ada").
5. Click **Surprise Me**.

**Expected**:
- The app immediately switches to the Alter Ego tab with the loading view (005 entrance animation visible).
- A poster renders (real or fallback).
- Open the browser DevTools network tab and inspect the `POST /api/v1/alter-egos` request body: `universe` is `"star-wars"` (your pick); `archetype` and `artStyle` carry some valid random value each. `customRole` is absent.
- Click back to the Setup tab (it re-enables after the response per 007). Confirm:
  - Universe still highlights **Star Wars**.
  - The prefab Role grid now highlights some option (the rolled one).
  - The Art Style grid highlights some option (the rolled one).
  - First-name input still shows "Ada".

---

## 3. Validate US2 — preserve a non-empty Custom Role

1. (If continuing from §2, click **Start Over** to clear state — Start Over is the canonical reset per FR-2814.)
2. Capture a photo. Type "Ada".
3. In the **Custom Role** input below the prefab grid, type `Distinguished Spreadsheet Wrangler`. The prefab grid should blur (022's precedence rule).
4. Leave Universe and Art Style empty.
5. Click **Surprise Me**.

**Expected**:
- Inspect the request: `customRole` equals `"Distinguished Spreadsheet Wrangler"` (trimmed). `archetype` is absent or `null`. `universe` and `artStyle` carry random values.
- Back on Setup: the Custom Role input still displays the typed string verbatim. The prefab grid is still blurred. Universe and Art Style each show a highlighted random pick.

---

## 4. Validate US3 — full random (back-compat)

1. Click **Start Over** to reset.
2. Capture a photo. Type "Ada".
3. Leave EVERYTHING empty — no prefab Role, no Custom Role, no Universe, no Art Style.
4. Click **Surprise Me**.

**Expected**: A complete request with valid randomly chosen `archetype`, `universe`, `artStyle`. Poster renders. Setup tab on return shows random picks in all three grids. This matches the 009 baseline exactly.

---

## 5. Validate US4 — all-explicit

1. Click **Start Over** to reset.
2. Capture a photo. Type "Ada". Pick a prefab Role, a Universe, and an Art Style.
3. Click **Surprise Me**.

**Expected**: Inspect the request — the category values are exactly your three picks (no substitution). The flow proceeds to a normal poster render. From the user's vantage point this is indistinguishable from pressing **Generate**.

---

## 6. Validate the "fresh roll" path (edge case)

This validates the FR-2814 contract — Surprise Me **does not** clear previously-rolled values.

1. Start from a clean state (Start Over).
2. Capture photo + name. Click **Surprise Me** without picking anything. (US3 path; all categories get random picks.)
3. After the poster renders and the Setup tab re-enables, return to Setup. Confirm the random picks are still highlighted.
4. Without clicking Start Over, click **Surprise Me** again.

**Expected**: The request body contains **the SAME** `archetype` / `universe` / `artStyle` values as the first click. The rolled values from click #1 counted as *explicit* on click #2 per FR-2801. To get a fresh roll, the user must click **Start Over** between clicks (or refresh the page).

This is intentional behavior — Surprise Me is "fill-in-the-blanks-and-submit," not "re-roll."

---

## 7. Run the test suite

```bash
# Frontend unit + RTL tier
cd frontend && npm test

# Frontend E2E tier (Playwright)
cd frontend && npx playwright test partial-surprise-me

# Backend (untouched — sanity only)
./gradlew :backend:test
```

All tiers should be green. The 028 tests added in `mergeSurpriseWithExplicit.test.ts`, `reducer.test.ts` (extended), `useGenerateAlterEgo.test.tsx` (extended), and the new `partial-surprise-me.spec.ts` Playwright file should each show ≥ 1 passing assertion.

---

## Troubleshooting

- **Surprise Me is greyed out**: check (a) photo captured, (b) first name non-empty, (c) the email field — if you typed an invalid email, the 023 FR-2304 gate blocks Surprise Me too.
- **Universe gets re-rolled when I press Surprise Me again**: that's a regression. Re-read FR-2814 and check the reducer body — the `case 'SurpriseMePicked':` branch must NOT clear or overwrite any non-empty slot.
- **Custom Role gets cleared after Surprise Me**: that's the 022 FR-2209 behavior leaking through — this feature removes that clear. Check that the `customRole: ''` line is gone from the `SurpriseMePicked` reducer branch.

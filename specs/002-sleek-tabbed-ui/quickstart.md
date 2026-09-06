# Quickstart: Initial Styling and Layout — Tabbed Setup Experience

**Feature**: 002-sleek-tabbed-ui
**Audience**: Contributors picking up the branch, reviewers verifying the PR, and SQA running the acceptance walkthrough.

---

## Prerequisites

- Node.js 20.x, npm 10+
- Java 21 (Temurin)
- Docker (for SQLite/Redis — not required for this feature but part of the standard dev env)
- The `002-sleek-tabbed-ui` branch checked out

```bash
git checkout 002-sleek-tabbed-ui
cd /path/to/aiavatar
```

---

## Run everything (two terminals)

### Terminal 1 — backend

```bash
cd backend
./gradlew bootRun
```
Spring Boot comes up at `http://localhost:8080`. Exposes `POST /api/v1/alter-egos` per `specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml`.

### Terminal 2 — frontend

```bash
cd frontend
npm ci
npm run dev
```
Vite serves at `http://localhost:5173`. The dev proxy forwards `/api/*` to `http://localhost:8080`.

Open the app in a browser at `http://localhost:5173`.

---

## Manual acceptance walkthrough (SC-101 / SC-102 / SC-105 / SC-106 / SC-107)

Run through this on a 1440 px viewport (or resize a desktop window to match).

1. **Tab shell paints** (SC-106 structural match; SC-107 pre-generation empty state)
   - Two tabs visible: `1 setup` (active) and `2 Your Alter Ego` (inactive).
   - Click `2 Your Alter Ego` → placeholder renders: "Your alter ego will appear here once you press Generate on the Setup tab."
   - Click `1 setup` → return to the form.
2. **Setup form structural match** (SC-106)
   - Left column: **YOUR PHOTO** heading + "Real face as a direct reference" sub-label, circular "Add photo" placeholder, **Camera** / **Upload** pill buttons below.
   - Right column: **ROLE, UNIVERSE & KEYS** heading, four numbered sub-groups (Pose ×4, Engineer role ×6, Universe / Style ×6, Vibe ×4 with "(optional)" label), then "👤 Your name (for personalized character)" with the `e.g. Paula` placeholder, then the Generate button spanning the column width.
3. **Single-selection + Vibe deselect behaviour** (FR-121, FR-118)
   - Click a Pose → it shows selected (cyan outline).
   - Click a different Pose → the first deselects, the second selects.
   - Click the same Vibe twice → it toggles off.
4. **Generate gating** (FR-122)
   - Fill everything except the photo → Generate stays disabled.
   - Upload a photo → Generate enables.
5. **Auto-switch on Generate** (FR-108, SC-102)
   - Press Generate → the UI immediately shows the `2 Your Alter Ego` tab as active, loading indicator visible.
   - While loading, click `1 setup` → you return to Setup with all inputs preserved; no cancellation.
   - Click back to `2 Your Alter Ego` → loading still running (or poster if it finished in the meantime).
6. **Poster renders** (FR-107c)
   - Within ~3 s on the stubs the loading state is replaced by the poster + Start over button (per 001 FR-012/013).
7. **Re-generate without Start over** (FR-124)
   - From the poster view, click `1 setup`, change the Role, press Generate again → the `2 Your Alter Ego` tab re-activates, loading replaces the old poster, new poster replaces loading. No confirmation prompt.
8. **Start over** (FR-102 + 001 FR-013)
   - From the poster view, press Start over → every Setup input clears, active tab returns to `1 setup`.
9. **Tab-switch state preservation** (SC-105)
   - Fill the form, do NOT press Generate, switch to `2 Your Alter Ego` (sees placeholder), switch back → all selections, name text, and photo preview preserved.
10. **Keyboard-only path** (FR-104, SC-102)
    - Reload the page. Press Tab repeatedly from the page start. Verify focus reaches, in order: the tablist (left tab focused), skip to the next tab via Right arrow, back with Left, activate with Enter/Space, tab into the form controls, reach Generate last. Every focused control shows a visible focus ring.
11. **Empty Vibe** (FR-118)
    - Fill all required inputs WITHOUT picking a vibe → Generate is enabled; pressing it produces a poster as usual.

---

## Automated tests

### Frontend

```bash
cd frontend
npm test                       # Vitest unit/component run (watch: `npm test -- --watch`)
npm run test:coverage          # coverage report; gate is ≥ 90% per module
npx playwright test            # end-to-end specs (needs the backend running)
```

Key specs to keep green:
- `src/features/alterego/components/TabsShell.test.tsx` — tablist ARIA + keyboard
- `src/features/alterego/components/SetupLayout.test.tsx` — two-column structure, responsive collapse
- `src/features/alterego/components/VibeGrid.test.tsx` — optional selection + deselect
- `src/features/alterego/AlterEgoPage.test.tsx` — auto-switch on Generate, Start over flow
- `tests/playwright/tabs-shell.spec.ts` — full tab navigation
- `tests/playwright/setup-form.spec.ts` — Setup form structural assertions
- `tests/playwright/e2e-flow.spec.ts` — end-to-end with new selections + re-generate

### Backend

```bash
cd backend
./gradlew test                 # unit + integration tests
./gradlew jacocoTestReport     # coverage report; gate ≥ 90%
./gradlew dependencyCheckAnalyze   # Principle VI: zero HIGH/CRITICAL advisories
```

Key suites to keep green:
- `unit/AlterEgoRequestValidationTest` — updated for new enums, dropped `colour`, nullable `vibe`
- `unit/EnumsTest` — JSON round-trip for every enum value
- `unit/StubCharacterGeneratorTest` — every `(archetype, universe[, vibe])` combination produces distinct output
- `unit/StubImageGeneratorTest` — accent colour derivation for every pair
- `unit/FallbackPosterProviderTest` — same derivation used for the fallback poster
- `contract/AlterEgoControllerContractTest` — happy-path JSON + enum examples match OpenAPI
- `contract/AlterEgoControllerErrorContractTest` — 400 bodies for bad enum / blank name / bad MIME / >5 MB
- `integration/GenerateAlterEgoIT` — full round-trip with the new payload
- `integration/GenerateAlterEgoFallbackIT` — fallback path under forced stub failure

---

## Accessibility sanity check (SC-103)

```bash
cd frontend
npm run test:a11y              # runs axe-core over the Setup tab in its three content states
```

If `test:a11y` is not yet wired, run the Playwright `@axe-core/playwright` integration inside `tests/playwright/tabs-shell.spec.ts`. Target: zero `serious` or `critical` violations on:
- The Setup tab empty state.
- The Setup tab with all required inputs filled.
- The `2 Your Alter Ego` tab in placeholder, loading, and poster states.

A keyboard-only walkthrough of steps 5–11 above MUST succeed end-to-end without a mouse.

---

## Contract verification

OpenAPI drift check — runs in CI and locally:

```bash
cd backend
./gradlew contractTest         # springdoc-generated contract vs. specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml
```

Frontend TS type drift check:

```bash
cd frontend
npm run generate:api-types     # emits types.generated.ts from the OpenAPI source
npx tsc --noEmit               # fails if types.ts diverges from the generated shape
```

---

## Docker Compose (parity with production layout)

```bash
docker compose up --build
```

Frontend served from `http://localhost` (port 80 via nginx), backend behind nginx on `/api/`. The same manual walkthrough above should pass through this path — especially the auto-switch, which must work identically whether the frontend calls the backend through the Vite proxy or nginx.

---

## Verifying the feature as a PR reviewer

Minimum checks before approving:

1. The manual walkthrough steps 1–11 all pass against the built Docker image.
2. `./gradlew test` + `npm test` + `npx playwright test` all green.
3. Coverage ≥ 90% on both frontend and backend.
4. `npm audit` and `./gradlew dependencyCheckAnalyze` report zero HIGH/CRITICAL.
5. `contracts/alter-egos.openapi.yaml` version bumped to `2.0.0`; controller contract test passes against it.
6. `specs/002-sleek-tabbed-ui/checklists/requirements.md` is all-green with the 2026-04-22 clarification session recorded.
7. The mockup at `specs/002-sleek-tabbed-ui/mockup.png` structurally matches the rendered Setup tab at 1440 px (SC-106) — reviewer compares the screenshot artifact from step 4 of the manual walkthrough against the mockup side-by-side.

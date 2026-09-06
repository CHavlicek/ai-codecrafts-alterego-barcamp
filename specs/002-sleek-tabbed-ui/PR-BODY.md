# 002 — Initial Styling and Layout (Tabbed Setup Experience)

Closes GitHub issue #3. Reshapes the 001 POC into a two-tab workflow matching
the mockup at `specs/002-sleek-tabbed-ui/mockup.png`.

## What changes

### Frontend

- **Tabs shell** at the top of the page: `1 setup` / `2 Your Alter Ego`,
  WAI-ARIA manual-activation pattern, roving tabindex, persistent panels
  (both always mounted; inactive is `hidden`). Non-colour-only active
  indicator (aria-selected + cyan underline + data-selected).
- **Auto-switch to tab 2 on Generate** (FR-108) — dispatched from the
  TanStack Query mutation's `onMutate`, so the tab flips before the
  network request starts.
- **Setup tab** — two-column sleek layout. Left column: "YOUR PHOTO"
  with a 220 px circular placeholder/preview + Camera / Upload pill pair.
  Right column: "ROLE, UNIVERSE & KEYS" with four numbered sub-groups
  (Pose → Engineer role → Universe / Style → Vibe), Name input, Generate.
- **New Vibe sub-group** — optional (FR-118), toggle-button pattern
  (button + aria-pressed) — deselect by re-clicking.
- **Archetype re-themed** to six engineering roles (Cloud Architect,
  Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer).
- **Universe expanded** to six options (Marvel, Star Wars, Cyberpunk,
  The Office, Indiana Jones, Lord of the Rings).
- **Colour picker removed** — accent colour is now derived by the
  backend from `(archetype, universe)` per FR-132.
- **Alter Ego tab** — three-state panel: empty placeholder pre-gen,
  loading, poster + Start-over.

### Backend

- OpenAPI contract bumped to **v2.0.0** (`contracts/alter-egos.openapi.yaml`).
- `Archetype` + `Universe` enum values replaced; `Vibe` enum added as
  optional; `Colour` removed.
- New `AccentResolver.deriveAccent(Archetype, Universe)` + `AccentTone`
  shared between `StubImageGenerator` and `FallbackPosterProvider`.
- `StubCharacterGenerator` + `characters.json` re-themed — 4 variants
  across each of the 6 new archetypes; hash picker drops `colour` and
  picks up optional `vibe`; a non-null `vibe` tints `heroTitleLine2`.
- `FallbackPosterProvider.poster(Archetype, Universe)` signature
  change — matching accent on the fallback path.

## How it was tested

### Unit + component (Vitest + React Testing Library)

**131 / 131 green**. New files:
- `TabsShell.test.tsx` (11 tests)
- `AlterEgoPanel.test.tsx` (7 tests)
- `SetupLayout.test.tsx` (7 tests — incl. per-sub-group dispatch)
- `VibeGrid.test.tsx` (7 tests — toggle-button pattern)
- Plus reducer/selector updates, `PhotoIntake` extended tests.

**Coverage**: lines **98.79%**, branches **90.1%**, functions **94.11%**,
statements **98.79%** — all above the 90% Principle-III gate.

### Backend (JUnit + Spring Boot Test)

**96 / 96 green**. New assertions:
- `EnumsTest` rewritten for the 002 enum set.
- `AlterEgoRequestValidationTest` — nullable-vibe path + vibe-preservation in `withTrimmedFirstName`.
- `StubCharacterGeneratorTest` — parametrised over all 6 archetypes × all 6 universes; vibe-present line-2 tilt.
- `StubImageGeneratorTest` — AccentTone coverage over every pair.
- `FallbackPosterProviderTest` — `AccentResolver` determinism + palette coverage.
- `AlterEgoControllerErrorContractTest` — stray-colour-field-is-ignored (not 400); old archetype / universe / vibe values → 400.

`./gradlew jacocoTestCoverageVerification` ✅. `./gradlew check` ✅.

### E2E (Playwright @ Chromium)

**38 / 38 green** against a fresh Vite dev server. New files:
- `tabs-shell.spec.ts` — tab structure + non-colour-only indicator +
  keyboard (manual activation) + auto-switch on Generate + start-over +
  state preservation + **1440 px screenshot archived** to
  `test-results/setup-tab-1440.png` (SC-106 evidence).
- `setup-form.spec.ts` — sub-group counts & order + Vibe toggle +
  gating rule (Vibe does NOT gate) + photo circular preview +
  responsive at 1440 px and 375 px + **axe-core scans on three Setup
  content states** (SC-103 evidence).
- `e2e-flow.spec.ts` — full happy path + re-generate without Start-over
  (FR-124) + tab-switch state preservation (SC-105) + vibe-omitted
  full flow.

`@axe-core/playwright`: **zero serious / critical** violations on the
four states scanned (entry, loading, success, fallback).

### Dependency hygiene

- `npm audit --omit=dev` → **0 vulnerabilities**.
- No new runtime dependencies added (frontend `package.json` and backend
  `build.gradle.kts` unchanged).
- The backend's OWASP `dependencyCheckAnalyze` task is not wired in
  `build.gradle.kts`; flagging this as a follow-up if strict Principle VI
  tooling is desired.

## Spec compliance

| Reqs / SCs | Status |
|---|---|
| FR-101..FR-109 (tab shell) | ✅ unit + Playwright |
| FR-110..FR-123 (Setup form structure + behaviour) | ✅ unit + Playwright |
| FR-124 (re-generate without Start-over) | ✅ Playwright |
| FR-130..FR-132 (backend contract delta + accent derivation) | ✅ JUnit |
| SC-101 (< 10 s comprehension) | ⚠ pending 5-participant session (T075) |
| SC-102 (< 90 s keyboard-only) | ✅ Playwright keyboard walkthrough |
| SC-103 (axe zero serious/critical) | ✅ Playwright axe scans |
| SC-104 (1440 / 375 viewports) | ✅ Playwright |
| SC-105 (tab-switch preservation) | ✅ Playwright |
| SC-106 (visual-diff) | ✅ screenshot archived + structural assertions |
| SC-107 (empty-state 100%) | ✅ unit + Playwright |

## What's left before merge

- **Manual quickstart walkthrough** (quickstart.md steps 1–11) —
  automated pieces are covered by Playwright, but a human-in-the-loop
  walkthrough of the full mouse + keyboard flow against `docker compose
  up --build` is still a Principle-III gate (T071).
- **5-participant comprehension session** for SC-101 (T075) — out of band.

Both are non-blocking for the CI pipeline but are called out here so the
reviewer can check them off explicitly.

## Notes for reviewer

- Every file that used to reference `Colour` / old `Archetype` / old
  `Universe` values has been touched — no backwards-compat aliases
  (research.md §R5, accepted at /speckit.clarify).
- Accent-colour derivation mapping (archetype × universe → hex) is in
  `AccentResolver.CURATED` with a hash fallback across the 6-value
  palette — edit that map if you want a specific narrative-fit
  combination.
- `AlterEgoPage.tsx` no longer does the 001-style phase-driven full-page
  swap; phase now drives only what's shown inside the Alter Ego tab's
  panel.

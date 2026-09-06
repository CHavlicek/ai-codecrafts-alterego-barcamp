# Implementation Plan: Print Alter Ego

**Branch**: `010-print-alter-ego` (artefacts); dev on `claude/speckit-implementation-dK3Mn`
**Date**: 2026-04-24
**Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `specs/010-print-alter-ego/spec.md` (issue #24)

## Summary

Add a single **Print** button to the Alter Ego tabpanel that, on activation, invokes the browser's native print dialog (`window.print()`) and produces exactly two printed pages:

- **Page 1 (Front)**: the generated poster image — the same `poster.dataUrl` bytes already displayed on screen, scaled to fit an A4/Letter portrait page preserving aspect ratio. No text, no chrome.
- **Page 2 (Back)**: the alter-ego text fields and the Setup choices that produced them (hero title, first name, tagline, three superpowers, quote, Pose, Archetype, Universe, Art Style, optional Vibe, and local generation date). No image.

Implementation is 100% frontend. No new dependency, no new HTTP call, no backend delta, no new contract. All print behaviour is driven by CSS `@media print` rules and a small new `<PrintArtefact>` component rendered inside `AlterEgoPanel` alongside the existing `PosterView` + `StartOverButton` — the print layout is *always in the DOM* (behind `display: none` at screen sizes) so there is no race between "click" and "dialog opens". Activation is delegated to a new `<PrintButton>` (mirrors `<StartOverButton>`) whose `onClick` simply calls `window.print()`. Category wire values are humanized via the existing `options.ts` `label` field, so "Cloud Architect" prints — never `cloud-architect`. No session state is mutated; repeat presses are byte-identical (FR-914 / SC-903).

Gating is piggy-backed onto the existing `phase === 'succeeded' || phase === 'failed_with_fallback'` gate already used by `PosterView` (CLAUDE.md §007). When `AlterEgoPanel` is in its empty or loading branches, the Print button is not rendered at all (FR-901 / FR-904), so no new selector or reducer action is required.

## Technical Context

**Language/Version**: TypeScript 5.x strict (frontend) — unchanged from 002..009.
**Primary Dependencies**: React 19, Vite 8 — no new runtime dependency. Printing uses only `window.print()` and CSS `@media print` rules native to every evergreen browser.
**Storage**: N/A (no persistence — consistent with 001 FR-016 / 017 / 024; spec FR-908 restates this for Print).
**Testing**: Vitest + React Testing Library (unit/component); Playwright E2E (existing harness). `window.print` is stubbed in component tests to assert it was invoked exactly once per user activation.
**Target Platform**: Evergreen desktop browsers (Chromium, Firefox, WebKit), plus tablet Safari/Chrome — print quality on mobile is out of scope per spec Assumptions but the button MUST NOT throw on those platforms.
**Project Type**: Web application (React SPA under `frontend/`).
**Performance Goals**: Time-from-click to native-print-dialog ≤ 2000 ms (SC-901 — trivially held since the only work is a function call). No frame drop on the Alter Ego tab at screen sizes (print-hidden nodes use `display: none`, not `visibility: hidden`, so they do not enter the on-screen layout tree).
**Constraints**:
- No new runtime dependency (Constitution Principle VI; spec Assumptions).
- No new HTTP call (FR-908; SC-904).
- Zero bytes written to browser storage / cookies (FR-908; SC-905).
- Must not mutate session state (FR-908; SC-903).
- Keyboard-operable with non-empty accessible name (FR-913; SC-906).
- Unit-test line coverage ≥ 90 % on changed files (Constitution Principle III).
- Must compose with 007 tab gating — Print button vanishes together with the rest of the poster surface when phase goes back to `generating` or `idle`.

**Scale/Scope**: Single feature touching ≈ 6 frontend files:

- 3 new components / helpers (`PrintButton.tsx`, `PrintArtefact.tsx`, `lib/humanizeSelection.ts`) + their tests,
- 1 modified (`AlterEgoPanel.tsx` renders them + `AlterEgoPanel.test.tsx` updated),
- 1 CSS block appended to `index.css` (the `@media print` rules + screen hiding),
- 1 Playwright spec appended (`print-alter-ego.spec.ts`) or extended into the existing alter-ego spec.

No backend file changes. No `options.ts` change (labels already exist and are re-exported).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | PASS | No new dep; React 19 / TS strict; pure React + native browser Print API + CSS `@media print`. |
| III. Test-First Development (TDD) | PASS | Red-Green-Refactor on: (a) `<PrintButton>` — enabled vs absent rendering tied to the `isPrintable` predicate, Enter/Space activation, accessible name, `window.print` called exactly once per click (stubbed in jsdom); (b) `<PrintArtefact>` — renders a front face with the poster `<img>` and empty otherwise, renders a back face with every FR-906 field in humanized form (asserting `'cloud-architect'` → "Cloud Architect"), omits the Vibe row when `session.vibe` is `undefined`, omits no field when everything is present; (c) `humanizeSelection` helper unit tests — round-trip for every option value in every category; (d) an `AlterEgoPanel` integration assertion that Print and PrintArtefact render in the poster phase and disappear in the empty / loading phases; (e) Playwright E2E for US1 & US2 (Generate → Print opens dialog → Cancel → state unchanged; gating not offered mid-generation). Every new branch is exercised; coverage gate ≥ 90 % holds. |
| IV. Resilient HTTP Communication | N/A | No HTTP call is introduced by Print. Spec FR-908 explicitly forbids one. |
| V. Feature Branch Workflow | PASS (with deviation) | Development pinned to `claude/speckit-implementation-dK3Mn` by explicit task instruction; SpecKit artefacts live under `specs/010-print-alter-ego/` so the 3-digit sequential prefix stays discoverable. Matches precedent set by 005 / 007 / 009. PR description will note the branch pinning. |
| VI. Zero Deprecated Dependencies | PASS | Zero additions → zero `npm audit` delta. |

**Gate verdict**: PASS. No unjustified violations. One documented branch-naming deviation (matching precedent set by 005, 007, 009).

## Project Structure

### Documentation (this feature)

```text
specs/010-print-alter-ego/
├── plan.md              # This file
├── research.md          # Phase 0: window.print vs new-window vs canvas/PDF; DOM-always vs toggle-on-print; duplex stance; humanization source; back-page content scope; a11y model; test seam for window.print; print-only CSS isolation strategy
├── data-model.md        # Phase 1: no new session shape — documents the PrintArtefactView (derived view over AlterEgoResponse + Selections), the humanization map sourcing, and the screen-vs-print visibility contract
├── quickstart.md        # Phase 1: how to run & manually validate the two user stories + the edge cases (US1, US2, fallback-print, keyboard-only, idempotent re-print)
├── checklists/
│   └── requirements.md  # Spec-quality checklist (from /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

No `contracts/` directory is produced for this feature — there is no API surface, no OpenAPI change, no new wire type. The 002 / 003 / 006 OpenAPI surface is byte-identical.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── components/
│   │   │   ├── PrintButton.tsx                    # NEW: inline button ("Print my alter ego"), calls window.print()
│   │   │   ├── PrintButton.test.tsx               # NEW: renders label, Enter/Space activation, window.print called once per click, not rendered mid-generation
│   │   │   ├── PrintArtefact.tsx                  # NEW: hidden-on-screen print DOM — <section.print-artefact__front><img/></section> + <section.print-artefact__back><dl>…</dl></section>
│   │   │   ├── PrintArtefact.test.tsx             # NEW: humanization per category; Vibe row omitted when absent; all FR-906 fields present when all inputs present; no image on the back section; no text on the front section
│   │   │   ├── AlterEgoPanel.tsx                  # + render <PrintButton> next to <StartOverButton>; render <PrintArtefact> alongside <PosterView> in poster phase
│   │   │   └── AlterEgoPanel.test.tsx             # + asserts PrintButton + PrintArtefact mount when phase is succeeded/failed_with_fallback; absent in empty/loading phases
│   │   └── lib/
│   │       ├── humanizeSelection.ts               # NEW: kebab-case → display-label via options.ts lookup tables; pure; no React import
│   │       └── humanizeSelection.test.ts          # NEW: round-trip for every Pose/Archetype/Universe/Vibe/ArtStyle value; unknown value fallback = passthrough
│   ├── index.css                                  # + @media print block: hide everything except .print-artefact; .print-artefact hidden at screen; page-break between front/back; @page size/margins
│   └── App.tsx                                    # unchanged
└── tests/
    └── e2e/
        └── print-alter-ego.spec.ts                # NEW: generate → click Print → window.print intercepted → asserts the dialog was requested and the print-only DOM contains poster img + back-page fields; Start Over removes both
```

**Structure Decision**: Single web-app layout under the existing `frontend/` tree. The feature follows the same seams as 009 — a new component sibling inside `features/alterego/components/` and a pure helper under `features/alterego/lib/`. The print layout is rendered into the existing React tree rather than into a detached window or iframe so we never duplicate the poster bytes, never re-fetch the image, and never have to cross a window boundary to share state (all of which would risk violating FR-908's no-persistence / no-network constraint and complicate a11y). CSS alone decides what is visible on paper vs on screen, which is the smallest seam that satisfies FR-903 / FR-910 / FR-911.

## Complexity Tracking

*No Constitution violations to justify.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *(none)* | — | — |

## Phase Outputs

- **Phase 0 Research**: [./research.md](./research.md) — decisions on (R1) trigger mechanism (`window.print()` vs hidden iframe vs canvas-to-PDF vs server PDF), (R2) print DOM placement (always-mounted-hidden vs conditionally mounted on click), (R3) front-page image scaling strategy, (R4) back-page content scope & ordering, (R5) humanization source of truth (reuse `options.ts` vs new map), (R6) duplex printing stance (no app-level hint), (R7) CSS isolation strategy for `@media print`, (R8) test seam for `window.print` in jsdom, (R9) a11y and keyboard model, (R10) idempotency and state-immutability guardrails, (R11) fallback-outcome printability, (R12) composition with 007 tab gating and 005 entrance animation.
- **Phase 1 Design**: [./data-model.md](./data-model.md) — documents the transient `PrintArtefactView` shape (derived from the existing `AlterEgoSession` — no new session fields), the `humanizeSelection` helper contract, and the screen-vs-print visibility contract. [./quickstart.md](./quickstart.md) — how to run the feature locally, the manual validation flows (US1 real, US1 fallback, US2 gating, keyboard-only, re-print idempotent), and where the new tests live.

No `contracts/` produced; `update-agent-context.sh` adds the Print-feature note to `CLAUDE.md` (no new tech — stack identical to 002..009).

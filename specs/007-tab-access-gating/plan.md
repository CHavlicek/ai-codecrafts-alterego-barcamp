# Implementation Plan: Tab Access Gating

**Branch**: `007-tab-access-gating` (artefacts); dev on `claude/nice-brown-yYpNQ`
**Date**: 2026-04-23
**Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `specs/007-tab-access-gating/spec.md` (issue #14)

## Summary

Gate activation of the two top-level tabs on the existing session `phase`, with zero new state and no reducer changes. A pure helper `tabDisabled(tab, phase) → boolean` lives alongside the existing selectors; `TabsShell` reads a per-tab `disabled` flag (augmenting the existing `TabDescriptor` contract), applies `aria-disabled="true"` / `tabindex="-1"` / `cursor: not-allowed` / reduced-opacity styling to the disabled tab, short-circuits click handlers, skips disabled entries in Arrow/Home/End focus navigation, and — on re-render — re-anchors the roving-tabindex entry point onto whichever tab is currently enabled. `AlterEgoPage` passes the computed flags to `TabsShell` via `TabDescriptor.disabled`. The first tab's label is changed from `'1 setup'` to `'1 Setup'`. This is an additive, purely view-layer change; no reducer action, no new field on the session, no persistence, no backend, no OpenAPI delta.

## Technical Context

**Language/Version**: TypeScript 5.x strict (frontend) — unchanged from 002..006.
**Primary Dependencies**: React 19, Vite 8. No new dependency introduced.
**Storage**: N/A (no persistence — consistent with 001 FR-016 / 002 / 005 posture).
**Testing**: Vitest + React Testing Library (unit/component); Playwright (E2E). Unchanged.
**Target Platform**: Evergreen browsers already targeted by the existing shell (Chromium, Firefox, WebKit).
**Project Type**: Web application (React SPA under `frontend/`).
**Performance Goals**: Tab switch remains instantaneous; gating adds a single boolean check per render per tab (constant work). `aria-selected` flip stays in the same React commit as today.
**Constraints**: No new runtime dependency; must preserve always-mounted-panel semantics (002 FR-106 / 005 FR-406); must preserve the generate-triggered entrance animation contract (005 FR-401); unit test line coverage ≥ 90 % on the changed files (Constitution Principle III).
**Scale/Scope**: Single feature touching ≈ 5 frontend files (`TabsShell.tsx`, `TabsShell.test.tsx`, `selectors.ts`, `selectors.test.ts`, `AlterEgoPage.tsx`) plus one CSS section in `index.css` and one Playwright spec.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | PASS | No new dep; React 19 / TS strict; pure HTML / ARIA / CSS. |
| III. Test-First Development (TDD) | PASS | All new behaviour gets a failing test first. New `tabDisabled` selector test; extensions to `TabsShell.test.tsx` (disabled click/Enter/Space ignored, ArrowRight skips disabled sibling, `aria-disabled`/`tabindex` attributes, roving-tabindex entry flips to the enabled tab); Playwright spec for end-to-end gating. Coverage gate ≥ 90 % holds (the new branches in `TabsShell` and `selectors.ts` are exercised by every added test). |
| IV. Resilient HTTP Communication | N/A | View-layer only; no HTTP traffic. |
| V. Feature Branch Workflow | PASS (with deviation) | Development is pinned to `claude/nice-brown-yYpNQ` by explicit task instruction; the SpecKit artefacts live under `specs/007-tab-access-gating/` so numbering stays sequential and discoverable. Deviation is documented here and in the PR description. Mirrors the deviation used for 005. |
| VI. Zero Deprecated Dependencies | PASS | Zero additions → zero `npm audit` delta. |

**Gate verdict**: PASS. No unjustified violations. One documented branch-naming deviation (matching precedent).

## Project Structure

### Documentation (this feature)

```text
specs/007-tab-access-gating/
├── plan.md              # This file
├── research.md          # Phase 0: ARIA + roving-tabindex + focus-repair decisions
├── data-model.md        # Phase 1: no state shape change — just the tabDisabled predicate contract
├── quickstart.md        # Phase 1: how to run & manually validate gating
├── checklists/
│   └── requirements.md  # Spec-quality checklist (from /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

No `contracts/` directory is produced for this feature — the change is entirely frontend-internal and does not touch the 002 / 003 / 006 OpenAPI surface.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── state/
│   │   │   ├── selectors.ts                # + `tabDisabled(tab, phase)` pure helper
│   │   │   └── selectors.test.ts           # + exhaustive truth table for tabDisabled (5 phases × 2 tabs = 10 rows)
│   │   ├── components/
│   │   │   ├── TabsShell.tsx               # + per-tab disabled flag + aria-disabled + tabindex rewiring + Arrow/Home/End skip + click/key short-circuit
│   │   │   └── TabsShell.test.tsx          # + disabled-state test suite: click/Enter/Space ignored, arrow skip, roving-tabindex-entry repair, aria-disabled, non-colour-only disabled signal (aria-disabled + data-disabled)
│   │   └── AlterEgoPage.tsx                # pass `disabled` per tab; rename "1 setup" → "1 Setup"
│   └── index.css                           # + .tabs-shell__tab[aria-disabled="true"] rule (opacity + cursor), focus-visible still works on enabled tabs
└── tests/
    └── e2e/
        └── tab-access-gating.spec.ts       # new: first-load gating, mid-request Setup lock, post-resolve free switching, Start-over reset
```

**Structure Decision**: Single web-app layout under the existing `frontend/` tree. Feature 002's `features/alterego/` module owns the tab shell + the session reducer + the derived selectors; this feature amends those files in place without introducing new modules. The `tabDisabled` predicate is co-located with `missingInputs` / `isReadyToGenerate` in `selectors.ts` because it is the same "derived from session state" pattern. No new hook is introduced — the derivation is cheap enough to compute inline in `AlterEgoPage` before it is handed to `TabsShell`.

## Complexity Tracking

*No Constitution violations to justify.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *(none)* | — | — |

## Phase Outputs

- **Phase 0 Research**: [./research.md](./research.md) — decisions on (R1) which ARIA attribute signals "disabled tab" (`aria-disabled` vs the HTML `disabled` attribute), (R2) how disabled tabs participate in roving-tabindex focus navigation, (R3) how the active-tab invariant (FR-507) is maintained when the disabled set flips, (R4) where the derivation lives (reducer field vs selector vs inline), (R5) disabled-state visual treatment, and (R6) test observability hooks.
- **Phase 1 Design**: [./data-model.md](./data-model.md) — no state-shape change. Documents the `tabDisabled(tab, phase) → boolean` contract, the extended `TabDescriptor.disabled?: boolean` prop, and the invariant FR-507 (active tab is always enabled). [./quickstart.md](./quickstart.md) — how to run the feature locally, the three manual validation flows (fresh load, mid-request, post-resolve / Start-over), and where the new tests live.

No `contracts/` produced; no `update-agent-context.sh` technology delta (identical stack to 002..006, already captured in `CLAUDE.md`).

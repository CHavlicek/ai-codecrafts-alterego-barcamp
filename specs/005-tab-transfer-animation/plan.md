# Implementation Plan: Tab Transfer Animation on Generate

**Branch**: `005-tab-transfer-animation` (artefacts); dev on `claude/animation-transferring-to-your-alter-ego-tab-6S2Mi`
**Date**: 2026-04-23
**Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `specs/005-tab-transfer-animation/spec.md` (issue #15)

## Summary

Animate the incoming "Your Alter Ego" tabpanel when the active-tab change is triggered by the "Generate my alter ego" click. Cross-fade (opacity 0 → 1) + 12 px upward translate, ~320 ms ease-out, honouring `prefers-reduced-motion`. Manual tab clicks and Start-over remain instantaneous. Implementation stays within the existing pure-CSS stack — no new runtime dependency. Signal the animation intent via an optional `reason?: 'manual' | 'generate'` on the existing `ActiveTabChanged` action; the reducer increments a monotonic `generateAutoSwitchNonce: number` counter on every Generate dispatch, and TabsShell keys a `useEffect` on that counter through a thin selector hook (`useTabAnimationSignal`). A monotonic counter (rather than a boolean flag) is required because `GenerateSubmitted` dispatches in the same React commit and would otherwise clobber a transient flag before TabsShell reads it. An observable transient attribute (`data-animating="true"`) latches onto the alter-ego tabpanel in the same React commit as the tab change so Playwright can assert without timing the visual.

## Technical Context

**Language/Version**: TypeScript 5.x strict (frontend) — unchanged from 002/003. No backend change.
**Primary Dependencies**: React 19, @tanstack/react-query 5, Vite 8. No new dependency introduced (FR-410).
**Storage**: N/A (transient React state only; mirrors 001 FR-016 / 002 no-persistence posture).
**Testing**: Vitest + React Testing Library (unit); Playwright (E2E). Unchanged.
**Target Platform**: Evergreen browsers that already run the existing 002 shell (Chromium, Firefox, WebKit).
**Project Type**: Web application (React SPA under `frontend/`).
**Performance Goals**: Animation duration ~320 ms; no blocking work added to click handler; `aria-selected` flip stays in the same React commit as today (FR-402 / 002 FR-108).
**Constraints**: No new runtime dependency; must preserve always-mounted-panel semantics (FR-406 / 002 FR-106); unit test line coverage ≥ 90 % (Constitution Principle III).
**Scale/Scope**: Single feature touching ≈ 7 frontend files (reducer, action, two hooks, one component, CSS, tests).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | PASS | No new dep. React 19 / TS strict already in use. |
| III. Test-First Development (TDD) | PASS | All new behaviour has a failing test authored first (see tasks T003 / T005 / T009 / T010 ordered before their implementation counterparts). Coverage gate ≥ 90 % — the new reducer branch, `useTabAnimationSignal`, and TabsShell animation-wiring are all exercised by the tests listed in tasks.md. |
| IV. Resilient HTTP Communication | N/A | Feature is UI-only; no new HTTP traffic. |
| V. Feature Branch Workflow | PASS (with deviation) | Development is pinned to `claude/animation-transferring-to-your-alter-ego-tab-6S2Mi` by explicit task instruction; the SpecKit artefacts live under `specs/005-tab-transfer-animation/` so numbering stays sequential and discoverable. Deviation is documented here and in the PR description. |
| VI. Zero Deprecated Dependencies | PASS | No additions. `npm audit` delta is zero. |

**Gate verdict**: PASS. No unjustified violations. One documented deviation on the branch-naming workflow.

## Project Structure

### Documentation (this feature)

```text
specs/005-tab-transfer-animation/
├── plan.md              # This file
├── research.md          # Phase 0: motion + signal-plumbing decisions
├── data-model.md        # Phase 1: reducer action + transient state shape
├── quickstart.md        # Phase 1: how to run & manually validate
├── checklists/
│   └── requirements.md  # Spec-quality checklist (from /speckit.specify)
└── tasks.md             # Phase 2 output (/speckit.tasks)
```

No `contracts/` directory is produced for this feature — the change is entirely frontend-internal and does not touch the 002 OpenAPI surface.

### Source Code (repository root)

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── state/
│   │   │   ├── reducer.ts                # + ActiveTabChangeReason union, + generateAutoSwitchNonce counter, action `reason?:` extension
│   │   │   └── reducer.test.ts           # + nonce-path coverage, existing manual no-op assertion preserved
│   │   ├── hooks/
│   │   │   ├── useGenerateAlterEgo.ts    # dispatch { reason: 'generate' } in onMutate
│   │   │   ├── useGenerateAlterEgo.test.tsx   # assertion includes reason
│   │   │   └── useTabAnimationSignal.ts  # NEW thin selector hook
│   │   └── components/
│   │       ├── TabsShell.tsx             # + isAnimatingIn state + effect + data-animating + onAnimationEnd + 400 ms safety
│   │       └── TabsShell.test.tsx        # + animate-in / manual-click-does-not-animate / clear-on-animationend
│   └── index.css                         # + @keyframes + .tabs-shell__panel--animate-in + reduced-motion override
└── tests/
    └── e2e/
        └── tabs-shell.spec.ts            # + data-animating assertion on auto-switch; + manual-click-no-animation test
```

**Structure Decision**: Single web-app layout (frontend-only change) under the existing `frontend/` tree. Feature 002's `features/alterego/` module owns the tab shell and the session reducer; this feature amends it in place without introducing new modules. One new file (`useTabAnimationSignal.ts`) is added co-located with sibling hooks.

## Complexity Tracking

*No Constitution violations to justify.*

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| *(none)* | — | — |

## Phase Outputs

- **Phase 0 Research**: [./research.md](./research.md) — decisions on animation style/duration, signal-plumbing alternative (reducer field vs context ref vs `phase`-derivation), lifecycle (class toggle vs key remount), reduced-motion strategy, test observability.
- **Phase 1 Design**: [./data-model.md](./data-model.md) — `ActiveTabChangeReason`, extended `ActiveTabChanged` action, monotonic `generateAutoSwitchNonce` counter with reset semantics on `StartOverRequested`. [./quickstart.md](./quickstart.md) — how to run the feature locally, manually validate, and read the new tests.

No `contracts/` produced; no `update-agent-context.sh` technology delta (same stack as 002/003, already captured in `CLAUDE.md`).

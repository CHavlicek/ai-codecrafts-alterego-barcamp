# Implementation Plan: Image Generation Progress Indicator

**Branch**: `013-progress-indicator` | **Date**: 2026-04-27 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/013-progress-indicator/spec.md`

## Summary

Render a live, time-derived percentage in the centre of the existing pulsating circle on the
"Generating your alter ego…" loading view. The percent is a pure function of elapsed wall-clock
time since the loading view mounted, capped at 99% so a "100%" reading can never be displayed
while the user is still waiting (per FR-1305). Updates are pushed at random intervals in
[2 s, 10 s] using a self-rescheduling timer chain whose RNG and clock are both injectable so
the tick scheduler is deterministic under Vitest. No new network call; no backend change; no
new dependency. The whole state machine lives inside the `GenerationLoading` component and a
new pure helper module under `features/alterego/lib/`. When the generation resolves, the
loading view unmounts unchanged via the existing `useGenerateAlterEgo` flow — no "100%" frame
is ever rendered.

## Technical Context

**Language/Version**: TypeScript 5.x (strict) — frontend only. No backend changes.
**Primary Dependencies**: React 19, Vite 8, Vitest, React Testing Library — all already in `frontend/package.json`. No new runtime or test dependency.
**Storage**: N/A. No persistence — the percentage and its scheduling state live only in component state for the lifetime of one loading view (extends 001 FR-016/017/024 unchanged).
**Testing**: Vitest + React Testing Library; tests use the project's existing `vi.useFakeTimers()` pattern and pass injected `now` / `rng` fakes into the new pure helpers.
**Target Platform**: Evergreen browsers (the existing app target). The feature uses only the standard `setTimeout` / `performance.now` / DOM APIs.
**Project Type**: Web application — feature is purely frontend; backend (`backend/`) is untouched.
**Performance Goals**: Indicator imposes one timer at a time per active loading view; total wakeups during a 20 s generation are bounded at ≤ 10 (since min interval = 2 s) and typically ~3 (avg interval = 6 s). Negligible CPU/RAM cost vs. the existing pulse animation. The count-up animation between ticks runs a `requestAnimationFrame` loop over ~400 ms (~24 frames), suppressed under `prefers-reduced-motion`.
**Constraints**: Must never display "100%" while loading is in flight (FR-1305 / SC-1302). Must not trigger a screen-reader announcement on every tick (FR-1309). Must not regress the existing loading UX (SC-1306).
**Scale/Scope**: One indicator visible at a time (single in-flight generation per session). Code change is a single component + single new pure module, plus CSS for the centred number.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | ✅ Pass | No new dependency. React 19 + TypeScript strict + Vite 8 stack unchanged. |
| III. Test-First Development (TDD) | ✅ Pass | All new code is reachable from Vitest. Tasks order: failing tests for the pure progress helper → failing tests for the component → implementation. The pure helper is fully testable without a DOM at all. |
| IV. Resilient HTTP Communication | ✅ N/A | No HTTP call. The feature is purely a client-side time-driven UI affordance (FR-1311). |
| V. Feature Branch Workflow | ✅ Pass | Working on `013-progress-indicator`, cut by `create-new-feature.sh`. Will merge via PR with explicit human approval. |
| VI. Zero Deprecated Dependencies | ✅ Pass | No new packages introduced; nothing to audit beyond the existing manifest. |

**Coverage gate** (≥ 90% per Principle III): the pure module is trivially 100% coverable; the
component branches (mounted-during-generation vs. not, post-resolve unmount, tick advancement)
are all exercised by the planned `GenerationLoading.test.tsx` additions. No gate violation.

**Constraint compliance**: FR-1310 (no persistence) and FR-1311 (no new HTTP call) extend the
already-ratified no-persistence posture from 001. No backend wiring, no new dep — well inside
the constitution's Technology Standards. **No Complexity Tracking entries needed.**

## Project Structure

### Documentation (this feature)

```text
specs/013-progress-indicator/
├── plan.md              # This file
├── research.md          # Phase 0 output (decisions on timer source, RNG injection, %-formula)
├── data-model.md        # Phase 1 output (ProgressTick state shape, lifetime contract)
├── quickstart.md        # Phase 1 output (manual smoke test in dev server)
├── contracts/
│   └── useGenerationProgress.md   # Hook contract: inputs, outputs, invariants
├── checklists/
│   └── requirements.md  # Already-passed quality checklist
├── spec.md              # Feature spec
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
frontend/
└── src/
    └── features/
        └── alterego/
            ├── components/
            │   ├── GenerationLoading.tsx          # MODIFIED — render percent inside the inner disc
            │   └── GenerationLoading.test.tsx     # MODIFIED — add tick-advancement / never-100 assertions
            └── lib/
                ├── progressTicker.ts              # NEW — pure helper: percentForElapsed(), nextDelayMs()
                └── progressTicker.test.ts         # NEW — pure unit tests with seeded rng + injected now

# CSS
frontend/src/index.css                              # MODIFIED — centring rule for the new percent node
```

**Structure Decision**: The repo's `frontend/src/features/alterego/` slice is the only directory
this feature touches. The new logic splits cleanly into a pure module (`progressTicker.ts` —
two functions, no React, no timers) and a thin React layer in `GenerationLoading.tsx` that
owns the timer chain and renders the formatted percent. This mirrors the 009 pattern
(`randomSelections.ts` + thin component glue) and keeps the testable surface as pure as
possible. No new top-level directory. Backend untouched.

## Complexity Tracking

> No Constitution Check violations. Section intentionally empty.

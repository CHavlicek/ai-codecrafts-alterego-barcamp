---

description: "Task list for 013 — Image Generation Progress Indicator"
---

# Tasks: Image Generation Progress Indicator

**Input**: Design documents from `/specs/013-progress-indicator/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Mandatory per Principle III (TDD, NON-NEGOTIABLE). All test tasks below are written FIRST and MUST fail before any production code is committed for the same user story. Coverage gate ≥ 90% per Principle III.

**Organization**: Tasks are grouped by user story (US1, US2, US3) so each can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Different files, no dependencies on incomplete tasks → may run in parallel
- **[Story]**: User story label (`[US1]`, `[US2]`, `[US3]`); omitted for shared phases

## Path Conventions

This is a **frontend-only** feature (per plan.md — no backend change). All tasks
operate inside `/home/user/ai-codecrafts-alterego/frontend/src/`. The Java backend tree is
intentionally untouched.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: This feature ships **no new dependency, no new directory, no new tooling**.
The "setup" is therefore a single sanity check that the existing test pipeline still
runs green on the freshly-cut branch — i.e. we are starting from a clean baseline before
RED.

- [X] T001 Confirm baseline green on `013-progress-indicator`: run `npm run lint` and `npm run test` from `frontend/` and verify both pass on the branch HEAD before any source change.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Establish the new pure helper module's *file* in the repo so unit tests in
later stories can import from a real path. The implementation is empty/throwing here —
that's deliberate, so the very first test files in US1 will go RED.

**⚠️ CRITICAL**: No user story tests can land before this stub exists.

- [X] T002 Create stub module file `frontend/src/features/alterego/lib/progressTicker.ts` exporting `Rng`, `percentForElapsed`, `nextDelayMs`, and `formatPercent` per `specs/013-progress-indicator/contracts/progressTicker.md`. Each function body throws `new Error('not implemented')` so unit tests fail loudly.

**Checkpoint**: Importable but-failing helper module is in place; user-story phases can begin in parallel.

---

## Phase 3: User Story 1 — Watching the percentage advance during generation (Priority: P1) 🎯 MVP

**Goal**: Render a live, time-derived integer percent inside the existing pulsating
circle on the loading view, advancing as time passes and disappearing when the result
arrives.

**Independent Test**: Trigger Generate (or Surprise Me); observe the loading view; see a
`<integer>%` value rendered inside the inner disc; advance fake time in a Vitest harness
and assert the displayed value increases. The 003 stub fallback path is sufficient to
exercise this in a real browser via `npm run dev`.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T010 [P] [US1] Add unit tests for the pure helper in `frontend/src/features/alterego/lib/progressTicker.test.ts`: cover the table cases for `percentForElapsed` (0, 400, 4000, 20_000, 39_600, 40_000, 60_000, MAX_SAFE_INTEGER, -1), the monotonicity property over a 1000-point sweep from 0 to 60_000 ms, the boundary cases for `nextDelayMs` (rng → 0, 0.5, 0.99999), the integer-and-bounds invariant on a sweep, and `formatPercent` for inputs 0 / 1 / 37 / 99 / 100 / -3.
- [X] T011 [P] [US1] Extend `frontend/src/features/alterego/components/GenerationLoading.test.tsx`: add a test that mounts `<GenerationLoading />` with `vi.useFakeTimers()` and an injected `now` / `rng`, asserts an initial `0%` (or `1%`) is visible, advances time by ~10 s, and asserts the visible percent has strictly increased.

### Implementation for User Story 1

- [X] T012 [US1] Implement `percentForElapsed`, `nextDelayMs`, and `formatPercent` in `frontend/src/features/alterego/lib/progressTicker.ts` exactly per the contract (`specs/013-progress-indicator/contracts/progressTicker.md`). No React import. No side effects on import.
- [X] T013 [US1] Add a small dependency-injection seam to `frontend/src/features/alterego/components/GenerationLoading.tsx`: accept optional `now?: () => number` and `rng?: () => number` props (both default to `performance.now` / `Math.random`). Capture `startedAtMs` in a `useRef` on first render. Use `useState<number>` for `elapsedMs` (initial 0). Inside a `useEffect(() => …, [])`, schedule a `setTimeout(tick, nextDelayMs(rng))`; the tick reads `now() - startedAtMs`, calls `setElapsedMs`, and reschedules itself. The effect cleanup calls `clearTimeout(handle)` with the latest handle. Render `formatPercent(percentForElapsed(elapsedMs))` inside a new centred `<span className="generation-loading__percent" aria-hidden="true">` placed inside the existing `<p>`.
- [X] T014 [US1] Add the centring CSS rule for `.generation-loading__percent` in `frontend/src/index.css`: position `absolute`, top to match the existing `::after` (14 px), `left: 50%`, `transform: translateX(-50%)`, height/width matching the inner disc (92 px), display flex / centred, font-family + size that reads cleanly against the dark inner disc, color of the existing accent text, no transitions/animations on the text itself.

**Checkpoint**: Loading view shows a live percent that advances over fake-time in the unit suite and over real time in `npm run dev`. The pure helper module is fully covered.

---

## Phase 4: User Story 2 — Honest progress that never claims completion prematurely (Priority: P1)

**Goal**: Guarantee that the rendered string never reads "100%" while the loading view
is visible, even when the real generation runs longer than 40 s.

**Independent Test**: Mount `<GenerationLoading />` under fake timers, advance virtual
time well past 40 000 ms (e.g. 90 000 ms), and assert the rendered string is `"99%"` and
NOT `"100%"`. Same component but a different invariant from US1.

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T020 [P] [US2] In `frontend/src/features/alterego/components/GenerationLoading.test.tsx`, add a test that advances fake timers to 90_000 ms after mount and asserts `screen.queryByText('100%')` is null AND that an exact `99%` substring is visible. Also add an assertion at the precise 40_000 ms boundary that the rendered text is `99%` (cap engages exactly at the boundary, FR-1305).
- [X] T021 [P] [US2] In `frontend/src/features/alterego/lib/progressTicker.test.ts`, add a sweep test asserting `percentForElapsed(t) ≤ 99` for `t ∈ [0, 1_000_000]` (1000 samples) and that `formatPercent(100)` returns `"99%"` (defensive clamp).

### Implementation for User Story 2

- [X] T022 [US2] Verify the cap is in `progressTicker.ts` from T012 (`Math.min(99, …)`); if the US1 implementation already includes it, this task is just adding any missing test coverage and the JSDoc note `// HARD CAP — never display 100% (spec FR-1305 / SC-1302)` next to the `Math.min` line.

**Checkpoint**: All US2 tests are green and US1 tests still pass. The "never 100%" guarantee is covered by both a pure-module sweep and a component-level rendering assertion.

---

## Phase 5: User Story 3 — Visual integration with the existing pulsating circle (Priority: P2)

**Goal**: The percent is centred inside the inner disc, legible against the dark
background, and does not interfere with the existing ring spin / pulse animation or the
existing single AT announcement.

**Independent Test**: Visual smoke test in `npm run dev` per `quickstart.md` step 4 and
step 9; plus a Vitest test asserting the percent node is NOT inside an `aria-live` region
and the existing polite announcement still fires once.

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T030 [P] [US3] In `frontend/src/features/alterego/components/GenerationLoading.test.tsx`, add a test asserting the percent node has `aria-hidden="true"` AND is NOT inside any element with `aria-live`. Add a second test asserting the existing live-region announcement ("Generating your alter ego…") still fires exactly once on mount (regression guard for SC-1306 / FR-1309).
- [X] T031 [P] [US3] In the same file, add a test asserting the existing three `<li>` step entries and the existing busy state (`[aria-busy="true"]`) are unchanged after the new percent node is added (regression guard for SC-1306).

### Implementation for User Story 3

- [X] T032 [US3] Apply the `aria-hidden="true"` to the new `<span>` from T013 (no-op if already added) and confirm the rendered DOM places the span outside any `aria-live` ancestor. Adjust the CSS in `frontend/src/index.css` if the visual placement test reveals an off-centre rendering.

**Checkpoint**: All three user stories are green; the indicator is visually correct, never claims 100%, and is invisible to screen readers (which still get the existing single polite announcement).

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T040 Run `npm run lint` and `npm run test` from `frontend/` — both green, no new warnings, coverage on the two changed files (`progressTicker.ts`, `GenerationLoading.tsx`) at ≥ 90 % per Principle III.
- [X] T041 Run `npm run build` from `frontend/` — production build succeeds with no new TypeScript or bundler errors.
- [ ] T042 Run the quickstart manual smoke test (`specs/013-progress-indicator/quickstart.md`) in `npm run dev` end-to-end; capture any deviation from the documented steps as a defect rather than silently fixing it. **(Not executed in this session — Claude Code cannot drive a browser. Reviewer/owner should run this before merge.)**
- [X] T043 Update `CLAUDE.md` "Active Technologies" / "Recent Changes" entry for 013 to reflect the as-shipped behaviour (one short bullet, matching the style of 009 / 010 / 011).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 only — must pass before any RED test is written.
- **Foundational (Phase 2)**: T002 depends on T001. Blocks all user stories because user-story tests import from the stub.
- **User Story 1 (Phase 3)**: depends on T002.
- **User Story 2 (Phase 4)**: depends on US1 implementation (T012 / T013) — same files, the cap MUST coexist with the formula. Practically: implement US1 → run US1 + US2 tests together → fix any failures.
- **User Story 3 (Phase 5)**: depends on US1 implementation (T013 introduces the span; US3 verifies its a11y posture and styling).
- **Polish (Phase 6)**: depends on US1 + US2 + US3 all green.

### Within Each User Story

- Tests FIRST, must FAIL.
- Pure module (`progressTicker.ts`) before component changes (`GenerationLoading.tsx`) — the component imports the module.
- Component changes before CSS tuning — placement only matters once the node exists.

### Parallel Opportunities

- T010 and T011 are in different files and have no shared dependency → may run in parallel.
- T020 and T021 are in different files → may run in parallel.
- T030 and T031 are in the same file (`GenerationLoading.test.tsx`) but assert on disjoint cases → can be authored together but cannot be parallelised across two committers without merge conflicts.

---

## Parallel Example: User Story 1

```bash
# RED: write failing tests for the pure module and the component, in parallel:
Task: "Pure helper unit tests in frontend/src/features/alterego/lib/progressTicker.test.ts"
Task: "Component tick-advancement test in frontend/src/features/alterego/components/GenerationLoading.test.tsx"

# Then GREEN sequentially:
Task: "Implement progressTicker.ts (T012)"
Task: "Wire timer + render span in GenerationLoading.tsx (T013)"
Task: "Add CSS centring rule in frontend/src/index.css (T014)"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Run T001 (baseline green).
2. T002 (stub module).
3. T010 + T011 (RED).
4. T012 + T013 + T014 (GREEN).
5. Validate manually via `npm run dev` per `quickstart.md` step 4–5.
6. Stop here for an MVP-quality demo: the user already sees an advancing percent.

### Incremental Delivery

1. MVP shipped → trust eroded if the percent ever shows 100%.
2. Add US2 (T020 / T021 / T022) → "never 100%" guarantee covered by tests at both layers.
3. Add US3 (T030 / T031 / T032) → polish: a11y posture and visual placement.
4. Polish phase (T040–T043) → lint, build, manual quickstart, CLAUDE.md update.

### Parallel Team Strategy

This feature is small enough that it does not benefit from parallel staffing. One
developer end-to-end is the right shape.

---

## Notes

- All file paths are relative to repo root unless absolute.
- The whole feature is frontend-only; the Java backend is unchanged.
- No new runtime or test dependency is added — Vitest, RTL, React 19, Vite 8 are already present.
- The pure helper's RNG and clock are injectable (`Rng`, `now: () => number`) so every test
  is deterministic; no `Math.random` mocking, no real wall clock, no flake.
- The percent text is decorative for sighted users; AT users continue to rely on the
  existing single polite "Generating your alter ego…" announcement (FR-1309, SC-1306).

# Phase 0 Research: Tab Transfer Animation

**Feature**: 005-tab-transfer-animation
**Input**: [spec.md](./spec.md) + [plan.md](./plan.md)

All questions below correspond to decisions already pinned in the spec's *Clarifications* section or the Technical Context of plan.md. This document records the rationale and the alternatives considered so downstream tasks and review have a single source of truth.

## R1. Animation vocabulary

**Decision**: Cross-fade (opacity 0 → 1) combined with a 12 px upward translate (`translateY(12px) → translateY(0)`) on the incoming Alter Ego tabpanel only. Ease-out curve, ~320 ms.

**Rationale**:

- Cross-fade alone reads as a lazy dissolve — too passive for a click-triggered affordance.
- Translate alone reads as a mechanical slide and implies horizontal spatial navigation (which the two-tab shell already communicates via the tablist underline — duplicating it is noisy).
- Combined fade + small upward translate implies *"new content arriving from below into the workspace"*, which matches the step-1 → step-2 workflow narrative of the tab shell and is the same motion vocabulary used by modern UI kits (Material, Fluent, Primer) for "surface entering" scenarios.
- 12 px keeps the motion footprint small — below the WCAG 2.3.3 "Animation from Interactions" threshold for problematic motion.

**Alternatives considered**:

- *Fade only*: rejected — too subtle for the hand-off, reads as a loading glitch rather than a deliberate view change.
- *Slide only (no fade)*: rejected — mechanical and competes with the tablist active-indicator transition.
- *Scale up from 0.96 → 1*: rejected — implies "expanding into focus" but fights the poster's own fade/scale transitions inside `AlterEgoPanel`.
- *Horizontal slide (translateX)*: rejected — implies sibling navigation and fights the manual-click instant-flip baseline.

## R2. Duration and easing

**Decision**: 320 ms on `cubic-bezier(0.16, 1, 0.3, 1)` (ease-out-quint).

**Rationale**:

- < 200 ms reads as a hard cut / twitch; ≥ 400 ms delays the loading indicator's apparent start.
- 320 ms sits in the "responsive but visible" motion band commonly used by modern UI kits (Apple HIG 300–400 ms for surface entrances; Material `emphasized-decelerate` 400–500 ms).
- Ease-out (decelerating) fits the mental model of the panel "settling in" from below. Cubic `(0.16, 1, 0.3, 1)` gives a crisp start and a soft settle without bounce.

**Alternatives considered**:

- 200 ms linear: too abrupt.
- 400 ms `ease-in-out`: too deliberate, noticeably slower than the loading indicator's first frame.
- Spring-physics curve: requires JS orchestration; conflicts with FR-410 (no new library).

## R3. Signal-plumbing — how TabsShell knows the change was Generate-triggered

**Decision**: Extend the existing `ActiveTabChanged` action with an optional `reason?: 'manual' | 'generate'` (default `'manual'`). Reducer increments a monotonic counter `generateAutoSwitchNonce: number` on every `reason: 'generate'` dispatch (including no-op same-tab dispatches, so Generate re-clicks while the alter-ego tab is already active still animate per spec Acceptance 4). `useGenerateAlterEgo.onMutate` dispatches `{ reason: 'generate' }`; `useActiveTab.setActiveTab` keeps its default behaviour (manual clicks dispatch without the reason field and do not touch the counter).

**Rationale**:

- Action-carries-intent matches the existing Redux-style convention in the reducer and keeps it pure — no effectful observer layer.
- A **monotonic counter** is robust against the same-commit-reset hazard: `useGenerateAlterEgo.onMutate` dispatches `ActiveTabChanged` and `GenerateSubmitted` back-to-back; React batches both before the next render. Any transient boolean/enum flag cleared by `GenerateSubmitted` would be clobbered before `TabsShell` observed it. A counter survives unrelated actions unchanged, so a `useEffect` keyed on the counter reliably fires once per Generate click.
- A counter also naturally supports the **re-click-while-already-on-alter-ego** acceptance (the tab-change is a no-op but the counter still increments, triggering a fresh animation).
- The counter is observable via normal `useReducer` state, so a selector hook (`useTabAnimationSignal`) can subscribe without re-rendering TabsShell on unrelated state changes.

**Alternatives considered**:

- *Boolean or `ActiveTabChangeReason | null` flag stored on session, reset by every non-`ActiveTabChanged` action*: rejected — **does not survive the same React commit** because `GenerateSubmitted` follows `ActiveTabChanged` in `onMutate` and resets the flag before `TabsShell` sees it. This was the first design and was corrected during implementation after verifying the batching semantics.
- *Derive from `phase === 'generating' && activeTab === 'alter-ego'`*: rejected — the combination is also true on re-renders after the API returns, misfiring the animation. Also breaks when Generate is re-clicked while the alter-ego tab is already active (no `ActiveTabChanged`, so no motion trigger needed).
- *Ref on the context (no reducer change)*: rejected — refs are not observable via `useEffect` deps cleanly, forcing a subscription pattern that adds more complexity than a monotonic counter on the session.

## R4. Animation lifecycle — class toggle vs key remount

**Decision**: Local `isAnimatingIn` React state in TabsShell, set to `true` inside a `useEffect` keyed on `[generateAutoSwitchNonce]` (guarded so the initial `0` value does not fire the animation on mount); cleared by `onAnimationEnd` on the panel. A 400 ms safety `setTimeout` inside the same effect guarantees clearing under reduced motion (where `animationend` does not fire because `animation: none`).

**Rationale**:

- Class toggle on the always-mounted panel preserves 002 FR-106 / SC-105 (the Setup panel's form state must survive tab switches — re-keying the panel would unmount + remount its subtree).
- `animation-fill-mode: both` on the CSS class makes the keyframe's final state (opacity 1, translateY 0) the resting state, so removing the class does not flash a reset.
- `onAnimationEnd` is the natural completion signal; the 400 ms safety covers the reduced-motion path and any browser-backgrounded edge case (FR-409).

**Alternatives considered**:

- *`key={lastAutoSwitchKey}` remount*: rejected — unmounts the panel, violating 002 SC-105.
- *Pure CSS with `transition`*: hard to orchestrate a one-shot from a React state transition without a hidden pre-render; keyframes + onAnimationEnd are cleaner.

## R5. Reduced motion

**Decision**: Add a single `@media (prefers-reduced-motion: reduce)` override that sets `animation: none` on `.tabs-shell__panel--animate-in`. The React-state attribute `data-animating="true"` still latches briefly, because it is a state-driven attribute (not media-query-driven) — this is intentional so Playwright can assert the animation intent in both environments with a single test.

**Rationale**:

- Pure-CSS gate honours WCAG 2.3.3 without forcing the component to subscribe to `window.matchMedia` (which adds SSR / initial-render complexity).
- Users who have opted out of motion get the current instant-switch behaviour, byte-for-byte.
- Keeping the `data-animating` attribute in sync with React state (not the media query) avoids a conditional test matrix and keeps the attribute's semantics consistent: *"React wanted to animate this"*, not *"this is visibly animating right now"*.

**Alternatives considered**:

- *JS `matchMedia` check in the effect and skip setting `isAnimatingIn`*: possible, but adds a subscription lifecycle and splits the assertion target in two (different environments, different attributes). Rejected for complexity.

## R6. Test observability

**Decision**: Expose `data-animating={isAnimatingIn ? 'true' : 'false'}` on the Alter Ego tabpanel. Playwright asserts the attribute on the synchronous click commit, before the animation completes. Vitest asserts the CSS class is applied on the panel and cleared on `fireEvent.animationEnd`.

**Rationale**:

- Attribute is latched in the same React commit as `activeTab === 'alter-ego'`, so the assertion is race-free at the ARIA-level (Playwright auto-waits).
- Decoupling the test signal from the actual visual duration prevents flaky tests on slow CI.
- Vitest does not need a real browser animation engine; asserting the class and the `onAnimationEnd` handler is enough.

**Alternatives considered**:

- *Assert `opacity` via `getComputedStyle`*: flaky under headless browsers and zero-duration reduced-motion mode.
- *Custom event dispatched from the component*: overkill; the DOM attribute is standard, cheap, and directly readable.

## R7. Risk register

- **Existing reducer test** (`frontend/src/features/alterego/state/reducer.test.ts`, current `ActiveTabChanged` describe block) asserts `expect(next).toBe(initial)` on the no-op path (same tab, manual). This assertion MUST be preserved — do NOT bump `generateAutoSwitchNonce` on the manual no-op path, or referential equality breaks and causes spurious React re-renders. The Generate-triggered no-op path DOES bump the counter and returns a new reference — also asserted.
- **`hidden` vs CSS animations** — elements with the HTML `hidden` attribute (`display: none` equivalent) do not run CSS animations. We animate only the incoming (now visible) panel, so this is fine, but we will add a short code comment in `TabsShell.tsx` calling this out for future reviewers.
- **Playwright flake window** — the `data-animating="true"` assertion fires on the same React commit as `aria-selected`; both are observable before the ~320 ms animation completes. If CI ever reports flakes, the fallback is to assert the CSS class is present via `toHaveClass`, which is even more robust.
- **Hook / selector rerender noise** — `useTabAnimationSignal` reads only `generateAutoSwitchNonce`. Consumers (TabsShell) re-render on that slice's change. No global context fan-out is introduced.

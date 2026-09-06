# Phase 0 — Research: Image Generation Progress Indicator

## R1 — Source of "elapsed time"

**Decision**: `performance.now()`, captured once at component mount and re-read inside each
tick callback. Wrap the read in an injected `now: () => number` so tests can use a fake
clock without touching `Date.now`.

**Rationale**:
- `performance.now()` is a monotonic clock; unaffected by wall-clock jumps (NTP correction,
  user changing the system clock). FR-1303 ("strictly non-decreasing") is therefore an
  invariant of the input, not just the formula.
- It is unaffected by background-tab throttling for the *value it returns*, so when a
  throttled tab finally fires the next tick, the elapsed delta accurately reflects real
  time — directly satisfying the Edge Case "Tab is backgrounded mid-generation".

**Alternatives considered**:
- `Date.now()` — rejected: not monotonic; a wall-clock jump backwards would let the
  percent regress, breaking FR-1303.
- A `setInterval` counter — rejected: tick count diverges wildly from real elapsed time
  under tab throttling, breaking the Edge Case.

## R2 — Tick scheduling: `setTimeout` chain vs. `setInterval`

**Decision**: A self-rescheduling `setTimeout` chain. Each tick computes its own random
delay in [2000, 10000] ms and schedules the next. A single `useEffect` on mount captures
the chain head; its cleanup cancels the pending timeout via `clearTimeout`.

**Rationale**:
- `setInterval` cannot have a different delay each iteration without cancelling and
  rescheduling itself anyway, so a setTimeout chain is the simpler, more honest expression
  of FR-1306 ("random-length intervals … from the previous update — not on a fixed
  cadence").
- Single-handle cleanup is trivial: one `clearTimeout(handle)` in the effect's cleanup
  function. No race against a half-fired interval.

**Alternatives considered**:
- `requestAnimationFrame` loop — rejected: unrelated to the 2–10 s cadence requirement;
  would burn frames doing nothing.
- A reducer-based action stream from `useGenerateAlterEgo` — rejected as over-engineered.
  The progress is purely UI ephemera; it does not belong in `sessionReducer`.

## R3 — Percent formula

**Decision**: `percent(elapsedMs) = Math.min(99, Math.floor(100 * elapsedMs / 20_000))`.

**Rationale**:
- Linear in elapsed time, exactly mapping `20 000 ms → 100` before the cap, satisfying
  FR-1304's "20 seconds maps to fully progressed".
- `Math.floor` keeps the displayed integer non-decreasing per tick (a `Math.round` would
  occasionally reorder by ±1 between adjacent ticks at the boundary).
- The hard cap `Math.min(99, …)` is the FR-1305 / SC-1302 guarantee: irrespective of how
  long the generation runs, the displayed digit-string never reaches "100".

**Tolerance for SC-1305**: SC-1305 specifies "within ±5 percentage points" of the formula.
Since the displayed value *is* the formula's output (no separate jitter on top), this
tolerance is automatically satisfied for any tick — the ±5 budget exists only to allow
the *cadence* of ticks (i.e. the user sees "23%" for a 7-second window during which the
true value drifts up to 41%, etc.); it is not a permission to randomise the displayed
number itself.

**Alternatives considered**:
- Easing curve (e.g. ease-out so the bar slows near the cap) — rejected: dishonest about
  elapsed time; SC-1305 explicitly requires the displayed value to track real elapsed
  time. Easing also makes the never-100 cap feel like a glitch ("why did it sit at 95% so
  long?") instead of an honest plateau.
- Tick-incremented counter ("each tick adds rand(3..8)% up to 99") — rejected: drifts
  away from real elapsed time; can't honour SC-1305 if the user has a long real call.

## R4 — Random delay generator

**Decision**: `nextDelayMs(rng = Math.random) = 2000 + Math.floor(rng() * 8001)`.

- Returns an integer in `[2000, 10000]` inclusive (8001 because the upper bound of `rng()`
  is `< 1`, so `Math.floor(rng() * 8001)` covers `[0, 8000]`).
- The `rng` parameter defaults to `Math.random` and accepts any `() => number` returning
  a value in `[0, 1)`, mirroring the 009 `randomSelections.ts` contract verbatim.

**Rationale**: Single integer-arithmetic line, no floating-point boundary worry, fully
deterministic under a seeded `rng`. Matches FR-1306 inclusively for both endpoints and
satisfies SC-1304 by construction.

**Alternatives considered**:
- A "next delay" derived from a pre-computed schedule fixed at mount (e.g. shuffle of
  `[2,5,3,7,…]`) — rejected: needlessly more state to test, no user-visible benefit.
- Floating-point ms (`rng() * 8000 + 2000`) — rejected: `setTimeout` truncates anyway,
  so storing a float is just an inconsistency between test and runtime.

## R5 — Where does the timer chain live?

**Decision**: Inside `GenerationLoading.tsx`, in a single `useEffect(() => …, [])`. Two
pieces of local state: `elapsedSinceMount` (number, ms) and a single `setTimeout` handle.

**Rationale**:
- `GenerationLoading` is *only ever* mounted while a generation is in flight — its own
  mount/unmount lifecycle is exactly the "loading view lifetime" of FR-1307 and the
  Key Entity in spec.md. So the cleanup ("stop the timer when generation resolves") is
  free: React cleans up the effect when the component unmounts.
- No reducer wiring, no new context. The progress is intrinsically component-local
  ephemera — a perfect fit for `useState` + `useEffect`.

**Alternatives considered**:
- A custom hook `useGenerationProgress({ now, rng })` returning the formatted string —
  considered. Rejected because it would force every test to render a host component just
  to call the hook; the pure helper module + a small in-component effect is leaner.
- Pushing the percent into `sessionReducer` — rejected: it's not part of the session's
  transferable state (no other component cares; nothing persists across mounts).

## R6 — Accessibility

**Decision**: Render the percent as a plain `<span>` inside the existing `<p>` element.
Do **not** add `aria-live` to the span. The existing `useLiveAnnouncer` "Generating your
alter ego…" polite announcement remains the canonical AT signal that generation is in
flight; the percent is decorative for sighted users only.

**Rationale**:
- FR-1309 mandates this. A `aria-live="polite"` on a value that updates every 2–10 s
  would spam the screen-reader queue with "thirty seven percent. forty two percent. fifty
  one percent." for the entire generation, which is not progress, it's noise.
- Hiding the span via `aria-hidden="true"` is a reasonable additional belt-and-braces
  measure since the value adds nothing AT users don't already get from the polite
  announcement.

**Alternatives considered**:
- `role="progressbar"` with `aria-valuenow` — rejected: a real progressbar would imply the
  value is meaningful, and the spec is explicit (FR-1305) that it is *capped* and does not
  reflect true completion. Promising more semantically than the value can deliver is
  worse than rendering it as plain decoration.

## R7 — Visual placement

**Decision**: Centre the percent text inside the inner disc rendered by
`.generation-loading > p::after` (the dark inset under the conic-gradient ring). Use
absolute positioning relative to the existing `<p>`, with `top: 14px` to match
`::after`'s top, full-width centred via `left: 50%; transform: translateX(-50%)`, and a
height matching `::after` (92 px) so the percent sits in the visual centre of the disc.

**Rationale**: The existing pseudo-elements already establish the ring (120 px) and inner
disc (92 px). Layering an absolutely-positioned text node at the same coordinates means
the existing animations need not change at all — SC-1306 (no regression) holds by
construction.

**Alternatives considered**:
- Restructure `GenerationLoading.tsx` to wrap the ring in a positioned container —
  rejected: gratuitous DOM churn, would invalidate snapshot tests of unrelated assertions.

## R8 — Count-up animation between ticks (FR-1313)

**Decision**: Drive the *displayed* percent with a separate `useState`, animated toward
the *target* (`percentForElapsed(elapsedMs)`) via a `requestAnimationFrame` loop on each
target change. Easing is ease-out cubic; default duration 400 ms, injectable via a
`countUpDurationMs` prop. Snap to target (no rAF) when `prefers-reduced-motion: reduce`
is set or when the duration prop is `0`.

**Rationale**:
- Keeps the *source of truth* honest: the target is still elapsed-time-derived, so SC-1305
  (within ±5 pp of the formula) holds. The animation is only how we walk between two
  targets.
- An `ease-out` curve matches the perceptual feel of "the number is catching up" without
  overshoot — cleaner than linear, no surprise at the cap.
- Suppression under reduced motion is a hard requirement of the platform's accessibility
  contract, mirrored by R9's poster-emerge suppression.
- Injectable duration means unit tests can pass `0` for snap-mode (most assertions are
  about end-state) and pass a non-zero value to specifically test that intermediate
  integers are observable.

**Alternatives considered**:
- Pure CSS `@property` with a CSS variable counter — rejected: limited browser coverage
  for `@property` interpolation of integers, harder to read against the dark inset, and
  would require a separate test surface (or skipped tests) to assert intermediate values.
- Linear interpolation (no easing) — acceptable but feels mechanical; ease-out is the
  same code budget for a perceptually warmer result.

## R9 — Loading → poster transition (FR-1314)

**Decision**: Enhance the existing 005 `poster-emerge` keyframe with a circular
`clip-path` reveal that starts as a small disc anchored near the position the loading
view's inner disc occupied (`circle(46px at 50% 70px)`) and expands to fully unclipped
(`circle(150% at 50% 50%)`) over the same 900 ms / `var(--motion-ease-emerge)` schedule.
Suppress the entire animation under `prefers-reduced-motion: reduce`.

**Rationale**:
- React's commit semantics swap the loading view for the poster in a single paint, so
  the user's eye is already focused on the disc's location at the moment the poster
  mounts. A circular reveal that *originates from that location* makes the swap read as
  "the disc opened and revealed the image" rather than "loading view disappeared,
  separate poster faded in".
- Reuses the existing animation schedule and `var(--motion-ease-emerge)` — no new
  duration / easing constants.
- `clip-path` as a circle is GPU-cheap and supported in every evergreen browser; no
  layout-shift cost.
- Runs whenever the poster mounts, which is a strict superset of the post-generation
  case (e.g. also runs when the user re-generates after Start Over). That is intentional
  visual consistency; the same "circular reveal" reads cleanly in both contexts.

**Alternatives considered**:
- Cross-fade with both the loading view and the poster mounted simultaneously for a brief
  window — rejected: requires React state coordination on `AlterEgoPanel.tsx` (track
  "phase just changed from loading"), invalidates the simple phase-based `if` chain
  there, and risks a double-mount for `useEffect`-running children of either component.
- Morph the conic-gradient ring directly into the poster border — rejected: the ring is
  a pseudo-element on the loading view's `<p>`, not a real DOM node we can hand off to
  the poster's animation; would need to be hoisted to a shared parent first.

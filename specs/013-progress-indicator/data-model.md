# Phase 1 — Data Model: Image Generation Progress Indicator

> The feature is purely UI ephemera. It does not introduce any persisted entity, any wire
> field on existing API contracts, or any reducer state. The "data model" below describes
> the in-memory state shape inside `GenerationLoading.tsx` and the contract of the new
> pure helper module — that is the entirety of the model.

## In-component state (`GenerationLoading.tsx`)

```ts
type State = {
  /** Wall-clock ms (from performance.now()) at which the loading view first mounted. */
  startedAtMs: number   // captured ONCE in a useRef on first render

  /** ms since startedAtMs at the last tick. Drives the rendered percent. */
  elapsedMs: number     // initial value 0, updated on every tick
}
```

- `startedAtMs` is captured into a `useRef` on first render and NEVER reassigned for the
  lifetime of the component instance. Re-mount = new ref = fresh start (FR-1308).
- `elapsedMs` lives in `useState` so each tick triggers a re-render; the rendered value
  is `formatPercent(percentForElapsed(elapsedMs))`.

## Pure helper module (`features/alterego/lib/progressTicker.ts`)

```ts
/** Random number generator contract: returns a number in [0, 1). Defaults to Math.random. */
export type Rng = () => number

/**
 * Compute the percent value to display given elapsed ms since the loading view mounted.
 * Linear in elapsedMs, capped at 99 so a "100%" reading can never be returned.
 *
 * Invariants (asserted in tests):
 * - percentForElapsed(0)        === 0
 * - percentForElapsed(20_000)   === 99   // cap engages exactly at the 20 s boundary
 * - percentForElapsed(99_999_999) === 99 // never above 99 for any non-negative input
 * - elapsedMs < 0  → 0  (defensive; should not happen since performance.now() is monotonic)
 */
export function percentForElapsed(elapsedMs: number): number

/**
 * Random delay (ms) to wait until the next progress tick. Inclusive on both endpoints.
 *
 * Invariants:
 * - For any rng returning v ∈ [0, 1):  result ∈ [2000, 10000] (integer)
 * - rng = () => 0      → 2000
 * - rng = () => 0.99999 → 10000
 * - Output is an integer (Math.floor over an 8001-wide range)
 */
export function nextDelayMs(rng?: Rng): number

/**
 * Format an integer percent for display. Returns e.g. "37%". Inputs outside [0, 99] are
 * clamped (a defensive guard — production code only ever passes percentForElapsed's
 * output, which is already in range).
 */
export function formatPercent(percent: number): string
```

## State transitions

```text
[Component mounts]
  ├─ startedAtMs = now()
  ├─ elapsedMs   = 0
  └─ schedule first tick at +nextDelayMs(rng)
        │
        ▼
[Tick fires]
  ├─ elapsedMs := now() − startedAtMs
  ├─ display   := formatPercent(percentForElapsed(elapsedMs))
  └─ schedule next tick at +nextDelayMs(rng)
        │
        ▼
[Component unmounts (generation resolved)]
  └─ clearTimeout(handle)   // pending tick cancelled; never fires
```

The post-resolve unmount is what guarantees FR-1307: there is no "settle to 100%"
transition, no "fade out" on the loading view that visits 100% — the entire view
disappears in the same React commit that swaps in the poster (or fallback).

## Lifetime contract

| Event | Effect on indicator |
|---|---|
| Generation request starts → `GenerationLoading` mounts | Indicator begins; `elapsedMs = 0`; first tick scheduled. |
| Tick fires while still loading | `elapsedMs` advances; displayed percent recomputed; next tick scheduled. |
| Generation succeeds → `GenerationLoading` unmounts | Pending tick cancelled by effect cleanup; indicator never re-renders. Poster mounts in same commit. |
| Generation fails → `GenerationLoading` unmounts | Same as success — indicator simply stops, no terminal "100%" frame. |
| User clicks Start Over and starts a new generation | Old instance was already unmounted; new instance has a brand-new `startedAtMs` and `elapsedMs = 0`. |

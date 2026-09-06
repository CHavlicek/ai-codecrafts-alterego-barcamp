# Contract: `features/alterego/lib/progressTicker.ts`

This module is the single point of truth for the progress-indicator's numeric behaviour.
It is intentionally pure (no React, no DOM, no globals beyond `Math.random`'s default)
so it can be exhaustively tested in isolation.

## Public surface

```ts
export type Rng = () => number

export function percentForElapsed(elapsedMs: number): number
export function nextDelayMs(rng?: Rng): number
export function formatPercent(percent: number): string
```

> No additional exports. No default export. No side effects on import.

## `percentForElapsed(elapsedMs)`

| Input | Output |
|---|---|
| `0`         | `0`  |
| `200`       | `1`  |
| `4_000`     | `20` |
| `10_000`    | `50` |
| `19_800`    | `99` |
| `20_000`    | `99` (cap engages at the 20 s boundary) |
| `60_000`    | `99` |
| `Number.MAX_SAFE_INTEGER` | `99` |
| `-1`        | `0`  (defensive clamp — should not occur in practice) |

**Formula**: `Math.min(99, Math.max(0, Math.floor((100 * elapsedMs) / 20_000)))`

**Invariant 1 (FR-1303)**: For any `a ≤ b`, `percentForElapsed(a) ≤ percentForElapsed(b)`
— monotonic non-decreasing.

**Invariant 2 (FR-1305 / SC-1302)**: `percentForElapsed(x) ≤ 99` for every finite `x`.

**Invariant 3 (FR-1304)**: `percentForElapsed(20_000) === 99`.

## `nextDelayMs(rng = Math.random)`

| `rng()` returns | Output |
|---|---|
| `0`         | `2000`  |
| `0.5`       | `6000`  |
| `0.99999`   | `10000` |
| `0.99999999` | `10000` (`Math.floor(rng() * 8001)` saturates at 8000) |

**Formula**: `2000 + Math.floor(rng() * 8001)`

**Invariant 1 (FR-1306, SC-1304)**: For every legal `rng()` return value (i.e. `v ∈ [0, 1)`),
`2000 ≤ nextDelayMs(rng) ≤ 10000`. Both endpoints reachable.

**Invariant 2**: Result is always an integer.

**Invariant 3**: A buggy `rng` returning exactly `1` would yield `10001`. The contract
documents that this violates `Math.random`'s spec; the implementation does not defensively
clamp here because the parameter type is the same `() => number` returning `[0, 1)` used
by the existing 009 `randomSelections.ts`.

## `formatPercent(percent)`

| Input | Output |
|---|---|
| `0`   | `"0%"`  |
| `1`   | `"1%"`  |
| `37`  | `"37%"` |
| `99`  | `"99%"` |
| `100` | `"99%"` (defensive clamp — guards against future regressions) |
| `-3`  | `"0%"`  |

**Formula**: `${Math.min(99, Math.max(0, Math.floor(percent)))}%`

The defensive clamp on `100` is the second line of defence behind FR-1305: even if a
future regression in `percentForElapsed` were to leak a 100, the rendered string would
still read `"99%"`.

## Test surface (Vitest, all pure)

- `percentForElapsed`: 8 cases above, plus a property test ("monotonic on a 1000-point
  sweep from 0 to 60_000 ms").
- `nextDelayMs`: each of the boundary `rng()` values, plus a sweep that asserts every
  result is in `[2000, 10000]` and integer.
- `formatPercent`: each of the 6 cases above.

> All three functions are pure → tests do not need fake timers, fake clocks, or React.

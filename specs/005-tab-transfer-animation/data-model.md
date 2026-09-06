# Phase 1 Data Model: Tab Transfer Animation

**Feature**: 005-tab-transfer-animation
**Input**: [spec.md](./spec.md), [plan.md](./plan.md), [research.md](./research.md)

No persistence. No backend schema. The "data model" for this feature is confined to the in-memory session reducer additions plus one DOM-observable attribute.

## Types

### `ActiveTabChangeReason`

```ts
export type ActiveTabChangeReason = 'manual' | 'generate'
```

- `'manual'` — user clicked a tab (or keyboard-activated it) via `useActiveTab.setActiveTab`. Default when the `reason` field is omitted on `ActiveTabChanged`.
- `'generate'` — the change was dispatched by `useGenerateAlterEgo.onMutate` as part of the Setup → Alter Ego hand-off on the "Generate my alter ego" click.

Future triggers (hypothetical "Surprise Me") reuse `'generate'` unchanged.

### `ActiveTabChanged` action (extended)

```ts
| { type: 'ActiveTabChanged'; tab: ActiveTab; reason?: ActiveTabChangeReason }
```

The `reason` field is *optional*. Omission is equivalent to `'manual'` — keeps every existing call-site source-compatible.

### `AlterEgoSession` (extended field)

```ts
export interface AlterEgoSession {
  /* …existing fields… */
  /**
   * Monotonic counter — incremented on every `ActiveTabChanged` dispatched
   * with `reason: 'generate'`, including no-op same-tab dispatches (so
   * re-clicking Generate while already on alter-ego still animates). Consumed
   * by TabsShell via `useTabAnimationSignal`. A counter is used instead of a
   * boolean/enum flag because `GenerateSubmitted` is dispatched in the same
   * React commit as the auto-switch; any transient flag would be reset
   * before TabsShell's effect could read it, whereas a monotonic counter is
   * dep-array-stable across the whole commit.
   */
  generateAutoSwitchNonce: number
}
```

Default in `initialAlterEgoSession()`: `0`.

## Reducer transitions

| Action | `activeTab` | `generateAutoSwitchNonce` |
|---|---|---|
| `ActiveTabChanged { tab, reason: 'generate' }` where `state.activeTab !== tab` | `tab` | `state.generateAutoSwitchNonce + 1` |
| `ActiveTabChanged { tab, reason: 'generate' }` where `state.activeTab === tab` (no-op tab; Generate re-click) | unchanged | `state.generateAutoSwitchNonce + 1` — **MUST produce a new state reference** so the consumer's `useEffect` fires on the re-click (spec Acceptance 4) |
| `ActiveTabChanged { tab, reason: 'manual' or omitted }` where `state.activeTab !== tab` | `tab` | unchanged |
| `ActiveTabChanged { tab, reason: 'manual' or omitted }` where `state.activeTab === tab` (manual no-op) | unchanged | unchanged — **MUST return the same state reference** (research.md §R7) |
| Any other action (`PhotoSelected`, `GenerateSubmitted`, `GenerateSucceeded`, `StartOverRequested`, etc.) | per existing logic | **unchanged** — the counter survives unrelated dispatches |
| `StartOverRequested` | reset to `'setup'` via `initialAlterEgoSession()` | `0` (via `initialAlterEgoSession()`) |

### Invariants

- `generateAutoSwitchNonce` is a non-negative integer and increases monotonically except for `StartOverRequested`.
- `generateAutoSwitchNonce > 0` implies Generate has fired at least once in the current session.
- `StartOverRequested` always resets `generateAutoSwitchNonce` to `0` (via the full state replacement).
- `ActiveTabChangeReason` remains a type alias (`'manual' | 'generate'`) on the action payload; it does not persist anywhere on the session.

## Observable outputs

### DOM attribute on the Alter Ego tabpanel

- `data-animating="true"` while TabsShell's local `isAnimatingIn` state is `true`.
- `data-animating="false"` at all other times.
- Attribute is set in the same React commit as `aria-selected="true"` on the tab, so Playwright's implicit auto-wait guarantees race-free assertion.

### CSS class on the Alter Ego tabpanel

- `.tabs-shell__panel--animate-in` is present iff `isAnimatingIn && activeTab === 'alter-ego'`.
- Class triggers `animation: tabs-shell-panel-in 320ms cubic-bezier(0.16, 1, 0.3, 1) both;`.
- Under `@media (prefers-reduced-motion: reduce)`, the class resolves to `animation: none` (visual no-op) while the DOM attribute still flips for test observability.

## Derived / consumer shapes

### `useTabAnimationSignal()`

Thin selector over `useAlterEgoSession().state.generateAutoSwitchNonce`. Returns `{ generateAutoSwitchNonce: number }`. Mirrors the pattern of `useActiveTab()` to keep subscription granularity tight.

## Non-goals for the data model

- No storage, persistence, serialization, or network transfer — reducer state is the whole scope.
- No new entity relationships or cross-entity constraints.
- No retention/lifecycle policy beyond the one-shot reset described above.

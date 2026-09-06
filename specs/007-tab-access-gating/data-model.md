# Phase 1 — Data Model: Tab Access Gating

## Summary

**No session-state shape change.** The gating decision is a pure function of the existing `phase` field, computed at render time. This document pins the contract of the new pure helper and the extended component prop so Phase 2 tasks can reference them verbatim.

## Entities

### (unchanged) `AlterEgoSession`

No fields added, removed, or renamed. No new reducer actions. No change to `initialAlterEgoSession()`. No change to `StartOverRequested` semantics (it already resets `phase` to `'idle'` and `activeTab` to `'setup'`, which via the new selector automatically re-disables the alter-ego tab — exactly what FR-506 requires).

### (new, view-layer only) `TabDescriptor.disabled?: boolean`

Optional boolean prop on the existing `TabDescriptor` interface in `components/TabsShell.tsx`:

```ts
export interface TabDescriptor {
  id: ActiveTab
  label: string
  panel: ReactNode
  /**
   * 007: when true, the tab is rendered non-interactive:
   * aria-disabled="true", tabindex="-1", cursor: not-allowed,
   * reduced opacity, skipped by Arrow/Home/End focus, click/Enter/Space
   * no-op. Optional to keep the 002/005 call sites backward-compatible;
   * when omitted or false, behaviour is identical to today's shell.
   */
  disabled?: boolean
}
```

Default is `false` (backward-compatible with feature 002). `TabsShell` receives the flag from the composition root and does not derive it internally.

### (new) Pure selector `tabDisabled`

File: `frontend/src/features/alterego/state/selectors.ts`

Signature:

```ts
export function tabDisabled(tab: ActiveTab, phase: SessionPhase): boolean
```

Contract:

| phase                   | tab = `'setup'` | tab = `'alter-ego'` |
|-------------------------|-----------------|---------------------|
| `idle`                  | false           | true                |
| `picking`               | false           | true                |
| `generating`            | true            | false               |
| `succeeded`             | false           | false               |
| `failed_with_fallback`  | false           | false               |

Invariants (pinned by `selectors.test.ts`):

1. For every `phase`, at least one of `tabDisabled('setup', phase)` or `tabDisabled('alter-ego', phase)` is `false`. I.e. there is always at least one enabled tab. This is required for FR-507 to be satisfiable.
2. The function is total: every member of the `SessionPhase` union is handled.
3. The function is pure: no closures, no side effects, no allocations beyond the returned boolean.

## State transitions (already supported)

No new transitions. The existing flow already produces the sequence the spec needs:

```
(fresh load) phase=idle, activeTab=setup          →  alter-ego disabled, setup enabled       ✓
(user picks) phase=picking, activeTab=setup       →  alter-ego disabled, setup enabled       ✓
(Generate clicked, in onMutate)
  1. ActiveTabChanged → 'alter-ego', reason='generate'
  2. GenerateSubmitted                            →  phase=generating, activeTab=alter-ego
                                                     setup disabled, alter-ego enabled       ✓
(response lands)
  phase → succeeded OR failed_with_fallback       →  both tabs enabled                       ✓
(Start-over) StartOverRequested → initialAlterEgoSession()
  phase=idle, activeTab=setup                     →  alter-ego disabled, setup enabled       ✓
```

The two lines in `useGenerateAlterEgo.onMutate` (first dispatch `ActiveTabChanged`, then dispatch `GenerateSubmitted`) are what makes FR-507 hold in the happy path — when `phase` becomes `'generating'`, `activeTab` is already `'alter-ego'`, so the now-disabled Setup tab is NOT the active one. The defensive `useEffect` in `AlterEgoPage` (R3) is the safety net; it dispatches an additional `ActiveTabChanged` ONLY if a future code path were to leave the active tab on a disabled side.

## Accessibility contract (non-negotiable, from spec FR-502 + FR-508)

The `TabsShell` render of a disabled tab MUST output:

- `role="tab"` (unchanged — the element is still a tab)
- `aria-disabled="true"`
- `tabindex="-1"` (regardless of `aria-selected` — a disabled tab is never the roving-tabindex entry point)
- `data-disabled="true"` (CSS hook mirror; also lets tests assert without aria-attribute parsing quirks)
- `aria-selected` still reflects the truth (`false`, by invariant FR-507 — the active tab is never disabled)
- `onClick` handler present but returns early when `disabled`; similarly `onKeyDown` returns early on Enter/Space when the target tab index is disabled
- Arrow / Home / End key handlers skip over disabled indices

No visual element is added, removed, or reordered. The tab text and icons (if any in the future) are unchanged.

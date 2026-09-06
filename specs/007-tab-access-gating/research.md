# Phase 0 — Research: Tab Access Gating

Decisions that close every open design question from plan.md's Technical Context so implementation in Phase 1+ has no NEEDS CLARIFICATION markers.

## R1. ARIA attribute for a "disabled tab" — `aria-disabled` vs HTML `disabled`

**Decision**: Use `aria-disabled="true"` on the `<button role="tab">` elements. Do NOT use the HTML `disabled` attribute.

**Rationale**:
- The WAI-ARIA APG Tabs pattern calls out `aria-disabled="true"` as the canonical signal for a tab that is present but not currently activatable ([APG tabs pattern](https://www.w3.org/WAI/ARIA/apg/patterns/tabs/)). A disabled tab must still be reachable by assistive tech so users know it exists and will become available later; `aria-disabled` preserves that reachability.
- The HTML `disabled` attribute removes the element from the tab-sequence, makes it unfocusable by programmatic `.focus()` via some AT paths, and — critically — suppresses `click` events. We need `click` to fire so we can explicitly no-op it (and to run future analytics hooks if any), and we want the element in the accessibility tree. `aria-disabled` gives us both.
- `aria-disabled` also layers cleanly with our existing `[data-selected]` and the 005 `[data-animating]` attributes — same "attribute as state, CSS reads it" pattern the shell already uses.

**Alternatives considered**:
- HTML `disabled`: rejected for the reasons above (side-effects on events, weaker AT story).
- `aria-hidden="true"`: rejected because the disabled tabs are NOT hidden — users need to know they exist so they understand the gated flow.

## R2. Roving-tabindex participation for disabled tabs

**Decision**: Disabled tabs get `tabindex="-1"` so they are NEVER the entry point when Tab-ing into the tablist. On ArrowLeft / ArrowRight / Home / End, the focus handler skips over any disabled tab (equivalent to "disabled tabs are transparent to focus navigation"). Each navigation method still lands focus on a visible focusable tab — i.e. on the one enabled tab.

**Rationale**:
- APG Tabs authoring practice: "If activation on focus would have a side effect, arrow keys should skip disabled tabs." Our tabs use *manual* activation, so arrow keys moving focus onto a disabled tab would technically be harmless — but the visible "focus ring moved onto a greyed-out tab" UX is confusing. Skipping is the simpler, more conventional behaviour.
- With exactly two tabs and at least one guaranteed enabled (FR-507), "skip" always lands on the other one. If only one enabled tab exists, Arrow keys are a no-op — consistent with APG guidance that arrow keys that would land on an identical focused tab are no-ops.
- `tabindex="-1"` on the disabled tab makes sure pressing Tab from outside the tablist lands on the enabled tab, not on the disabled-but-first-in-DOM tab.

**Alternatives considered**:
- Leave arrow keys to move focus onto a disabled tab, rely purely on `aria-disabled` + click/Enter/Space no-op: rejected because visual focus on a visibly-disabled element contradicts common OS and Web patterns.
- Remove disabled tabs from the DOM entirely: rejected because it breaks state preservation (the whole point of `hidden=true` panels) and makes the gating feel "sticky" — users don't see what will become available.

## R3. Maintaining the "active tab is always enabled" invariant (FR-507)

**Decision**: Compute `tabDisabled` once per render in `AlterEgoPage`. Before handing the `TabDescriptor` array to `TabsShell`, `AlterEgoPage` (or a small helper) inspects the current `state.activeTab` and the two disabled flags; if they disagree, the component emits an `ActiveTabChanged → <enabled sibling>, reason: 'manual'` dispatch from an effect synchronized with the phase change. In the happy path this effect never fires, because the existing `useGenerateAlterEgo.onMutate` already flips the active tab to `'alter-ego'` before `phase` transitions to `'generating'`. The effect is the defensive safety net for any future code path that flips `phase` without first switching tabs.

**Rationale**:
- Keeps the reducer pure — no new branch needed.
- Keeps the rule in ONE place (`AlterEgoPage`) rather than sprinkled across callers.
- Uses `reason: 'manual'` for the auto-switch so the 005 animation does NOT fire on this defensive path (we do not want the entrance animation to play just because the system repaired a disabled-active state).
- The `useEffect` dep-array is `[state.activeTab, state.phase]`; the body is an `if` that dispatches only when the computed active-tab-is-disabled condition holds. React 19 strict-mode double-invoke is safe because dispatching an `ActiveTabChanged` with the same tab is a no-op by the reducer contract.

**Alternatives considered**:
- Add a new reducer action `ActiveTabGuarded` that internally performs the switch: rejected — it expands the action surface for a rule that is a consequence of existing invariants.
- Compute the "guarded active tab" during render and render the view accordingly without dispatching: rejected because render would then disagree with `state.activeTab`, which is load-bearing for Start-over semantics, the 005 animation signal, and the controlled/uncontrolled-tab contract (a tab is controlled by `state.activeTab`).

## R4. Where the derivation lives

**Decision**: A pure, exported `tabDisabled(tab: ActiveTab, phase: SessionPhase) → boolean` function in `frontend/src/features/alterego/state/selectors.ts` (same file as `missingInputs` / `isReadyToGenerate`). `AlterEgoPage` calls it once per tab (cheap) and passes the results via a new optional `disabled?: boolean` prop on `TabDescriptor`.

**Rationale**:
- Pure function → trivially unit-testable (10-row truth table: 5 phases × 2 tabs).
- Co-located with the other derived selectors that already depend on session state.
- Keeps `TabsShell` generic: it doesn't care that the disabled decision is phase-driven; it just consumes a boolean. That makes the shell reusable if future features ever add another gating rule, and it keeps the selector → view boundary clean.

**Alternatives considered**:
- A custom hook (`useTabDisabled`): rejected — no state is involved, the function is pure, and adding a hook just for a boolean derivation is over-engineering.
- Put the branching inside `TabsShell` itself: rejected — couples the generic shell to session-reducer specifics.

### Truth table (the tests pin all 10 rows)

| phase                   | `tabDisabled('setup', …)` | `tabDisabled('alter-ego', …)` |
|-------------------------|----------------------------|--------------------------------|
| `idle`                  | false                      | true                           |
| `picking`               | false                      | true                           |
| `generating`            | true                       | false                          |
| `succeeded`             | false                      | false                          |
| `failed_with_fallback`  | false                      | false                          |

## R5. Visual treatment for the disabled state

**Decision**: CSS rule targeting `.tabs-shell__tab[aria-disabled="true"]`:

```css
.tabs-shell__tab[aria-disabled="true"] {
  opacity: 0.4;
  cursor: not-allowed;
  /* inherit filter: grayscale could add here but opacity + cursor
     is already the >3:1 distinction we need for non-colour-only
     spec FR-502. */
}
```

`aria-disabled` is a bona-fide accessibility attribute (not a styling hook), so using it as the CSS selector is idiomatic and also signals to screen readers simultaneously — one attribute, two jobs. The `:focus-visible` behaviour on enabled tabs is untouched. Disabled tabs never receive the focus ring because they are never focused (per R2).

**Rationale**:
- Satisfies FR-502 "visually distinguishable … NOT rely on colour alone" — opacity change + cursor change are BOTH non-colour channels.
- Reuses the existing `.tabs-shell__tab` class (no selector duplication).
- Works under `prefers-reduced-motion` automatically (no motion is introduced).

**Alternatives considered**:
- Dedicated `.tabs-shell__tab--disabled` modifier class: rejected because the ARIA state is already the source of truth and a second class would just duplicate it (two places to update, one more bug surface).

## R6. Test observability

**Decision**:
- Unit: selector truth-table in `selectors.test.ts`; TabsShell tests use role queries (`getByRole('tab', { name: … })`) plus direct attribute assertions (`aria-disabled`, `tabindex`). No `data-testid` pollution.
- E2E: `tests/e2e/tab-access-gating.spec.ts` exercises the full flow end-to-end against the dev server, using MSW / existing stub fallback for the Gemini call so generation completes fast.

**Rationale**:
- Matches the existing component-test style (see `TabsShell.test.tsx` 002 + 005 suites).
- Keeps assertions semantic (role + aria-disabled) rather than implementation-coupled.

## Summary of choices

| # | Decision | Why it was chosen |
|---|---|---|
| R1 | `aria-disabled="true"` | Canonical APG, preserves accessibility-tree + events |
| R2 | `tabindex="-1"` + skip in Arrow/Home/End | Matches OS/browser convention; always lands on enabled sibling |
| R3 | Defensive `useEffect` in `AlterEgoPage` dispatches `'manual'` switch when active=disabled | One place, pure reducer, no 005 animation leak |
| R4 | `tabDisabled(tab, phase)` pure selector in `selectors.ts` | Trivially testable, co-located with peers |
| R5 | `[aria-disabled="true"] { opacity; cursor }` | Non-colour-only, single source of truth |
| R6 | Role queries + attribute assertions + E2E spec | Semantic, matches 002/005 style |

All NEEDS CLARIFICATION resolved. Ready for Phase 1.

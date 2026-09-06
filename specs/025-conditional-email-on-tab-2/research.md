# Research — 025: Conditional Email Input on the Alter Ego Tab

**Date**: 2026-05-15
**Status**: Complete — zero open NEEDS CLARIFICATION

This feature is small enough that "research" is really "design decisions on top of existing 023 primitives". Every section below names a concrete decision, the rationale, and the alternative we rejected.

---

## R1 — What does "captured email is valid" mean for visibility gating?

**Decision**: Add a new selector `isSendableEmail(state)` that returns `true` iff `validateEmail(state.email)` returns `{ ok: true }` **AND** the resulting `trimmed` length is `> 0`. The visibility predicate for the inline Alter Ego-tab field is `!isSendableEmail(state)`.

**Rationale**: The existing `emailValidity(state)` returns `'valid'` for a BLANK email too — that's the right shape for Setup-tab Generate gating (a blank optional field must not block Generate, per 023 FR-2303), but it is the *wrong* shape for "is this email send-ready?". `SendAsEmailButton` already inlines the right predicate today (`validation.ok && trimmed.length > 0`), so the new selector is just extracting that inline expression into one named place. Pulling it into `selectors.ts` keeps the visibility rule and the send-enabling rule lock-stepped — the spec requires them to be the same gate (FR-2505 ⇒ FR-2501/2502).

**Alternatives considered**:
- **Reuse `emailValidity === 'valid'`** — rejected: it admits blank as valid, which contradicts FR-2501.
- **Add a `'blank' | 'valid' | 'invalid'` three-state enum** — rejected: yet another selector to keep coherent. The boolean version is what every call-site actually wants; the three-state version has no second consumer.
- **Compute the boolean inline inside `AlterEgoPanel`** — rejected: violates DRY against `SendAsEmailButton` and makes the spec's "two paths share one gate" guarantee silent rather than tested.

---

## R2 — Where does the inline field live in the component tree?

**Decision**: A new `InlineEmailFallback.tsx` rendered as a child of `AlterEgoPanel.tsx`'s "poster" branch, positioned **between `PosterView` and `.alter-ego-panel__actions`** so the email field appears directly above the Start Over · Print · Send As Email row (FR-2501).

**Rationale**: Locating it inside `AlterEgoPanel` keeps the phase-gating story in one file: the inline field is only renderable in the same branch that renders the actions row, so FR-2510 (no field in pre-poster phases) is enforced structurally rather than by an extra phase check. The new component is a one-liner around the existing `EmailInput`, but extracting it (a) gives the panel-integration test a clean targetable role, (b) keeps `AlterEgoPanel` legible, (c) keeps the visibility predicate co-located with its rendering — the only place that reads `isSendableEmail` from the Alter Ego tab.

**Alternatives considered**:
- **Render `EmailInput` directly inside `AlterEgoPanel`** — rejected: scatters the visibility logic across the panel body. The wrapper costs one tiny file and pays for itself in readability + test isolation.
- **Render inside `AlterEgoPanel__actions` flex row** — rejected: the spec is explicit that the field is *above* the actions row, not in it (FR-2501). The two regions also have different a11y semantics (form-control vs button group).

---

## R3 — Do we need a new reducer action?

**Decision**: **No.** Both tabs dispatch the same `EmailChanged` action that already exists (23's reducer.ts:206-212). The captured-email field on `AlterEgoSession` is the single source of truth (FR-2504, SC-2505).

**Rationale**: The existing `EmailChanged` reducer branch (a) writes the raw value to `state.email`, (b) does not touch `phase` (defensive against mid-generation typing, important for the Alter Ego tab because the user can theoretically reach the field while the session is still in `succeeded`). It is already shape-correct for both call-sites — adding a new action would duplicate behaviour and complicate Start-Over reset.

**Alternatives considered**:
- **Introduce `AlterEgoTabEmailChanged`** — rejected: doubles the state surface for no semantic gain. Per FR-2504 there must be exactly one captured-email value; two actions writing to one field is strictly worse than one action.
- **Promote `email` to two fields (`setupEmail`, `alterEgoEmail`) and read whichever wins** — rejected: violates SC-2505. The whole point of the feature is *not* to introduce a duplicate.

---

## R4 — Disabled-while-sending behaviour for the inline field

**Decision**: When `useSendAlterEgoEmail.isPending` is true, the inline field MUST be `disabled`, mirroring the Setup-tab field's behaviour during a Setup-time submit. Because `AlterEgoPanel` already wraps `SendAsEmailButton`, which already calls `useSendAlterEgoEmail`, we lift the `isPending` value into `AlterEgoPanel` and pass it both to the new `InlineEmailFallback` and to `SendAsEmailButton`.

**Rationale**: Without this, a user could begin editing the address mid-send and visually "win" the race, ending up with a successfully-sent address that no longer matches what the input shows. The lift is small (one extra hook call moves from `SendAsEmailButton` to `AlterEgoPanel`); we pass `isPending` down as a prop so there is still a single call-site for the hook.

**Implementation note**: To keep the change minimal, the simplest path is for `SendAsEmailButton` to accept an *optional* `disabled` prop (additive) defaulting to its current internal behaviour, and have `AlterEgoPanel` (a) call `useSendAlterEgoEmail` itself, (b) pass `isPending` to both children. Alternative: keep `SendAsEmailButton`'s self-management and instead read TanStack Query's mutation-pending status via `useIsMutating` from the inline component. We prefer the prop-lift because it stays inside our existing seam (no new query-cache key, no new hook).

**Alternatives considered**:
- **Leave the inline field enabled during send** — rejected: visible inconsistency with Setup-tab convention, and the race risk is real (the user types after onClick, the request goes out with the pre-edit value).
- **Pull `useIsMutating` to detect in-flight sends from the inline component** — rejected: introduces a second source of truth for "is a send in flight" and depends on the mutation key staying stable.

---

## R5 — Visual treatment and CSS

**Decision**: Re-use the existing `.email-input` CSS rules from `frontend/src/index.css` verbatim. The new `InlineEmailFallback` adds a single wrapper class `.alter-ego-panel__email-fallback` for spacing (margin-bottom to separate from the actions row) — no new visual primitive, no new colour, no new icon. The label "Email" is reused so a screen-reader user hears the same announcement in both tabs.

**Rationale**: FR-2503 and Assumption "Layout symmetry with Setup tab" both call for visual symmetry. Reusing the established class shape means a single rule-block continues to govern the email field's look across both call-sites — future visual changes touch one place.

**Alternatives considered**:
- **Render a more compact field** (e.g. omit label) — rejected: the spec mandates the same a11y standard (FR-2513) and the same look (Assumption). Compact variants tend to drift in a11y.

---

## R6 — Test plan

**Decision**: Three new failing-first test files (Constitution Principle III), all Vitest + RTL:

1. **`state/selectors.test.ts`** (additions) — `isSendableEmail` truth table: blank → false; whitespace-only → false; well-formed → true; mis-formatted → false; over-length → false. Plus the invariant `isSendableEmail(s) ⇒ emailValidity(s) === 'valid'` (sendable implies the existing Generate-gating selector also passes — there is no state where send is enabled but Generate would have been blocked by email).

2. **`components/InlineEmailFallback.test.tsx`** — renders the same `EmailInput` contract: label, input with `type=email`, clear-X visibility tied to non-empty value, `aria-invalid` on non-blank invalid, `role="alert"` error region keyed by `EMAIL_ERROR_MESSAGE`. `onChange` dispatches `EmailChanged` with the raw (un-trimmed) value. `disabled` propagates to input + clear-X.

3. **`components/AlterEgoPanel.test.tsx`** (additions) — three new scenarios driving the panel through `succeeded` phase with three captured-email values: (a) blank → inline field is present, "Send As Email" disabled; (b) typo `"foo"` → inline field is present + pre-filled with `"foo"` + inline error visible, "Send As Email" disabled; (c) valid `"name@example.com"` → inline field is absent, "Send As Email" enabled. Plus the transition: starting from (a), typing a valid value → the field disappears (collapses) on the same render and "Send As Email" becomes enabled. The reverse — typing back into invalid — re-mounts the field at the same position.

**Rationale**: This trio covers every functional requirement (FR-2501..2515) at the smallest reasonable tier — pure logic in the selector test, isolated component contract in the wrapper test, and the visibility-rule-meets-actions-row story in the panel test. No new Playwright spec is needed because the send pipeline is unchanged (the existing 023 E2E remains valid) and the 023 send pipeline is already covered.

---

## R7 — Backwards compatibility & rollback

**Decision**: This change is purely additive on the frontend: one new selector, one new component, one prop-thread + one conditional render added in `AlterEgoPanel`. The wire format is unchanged, the reducer is unchanged, the validator is unchanged, and the Setup-tab UI is unchanged. Rollback is a single revert.

**Rationale**: Keeping the change additive removes the cost of a feature-flag and preserves the spec's no-side-effect posture. There is no migration to worry about because there is no persistence.

---

## Summary of resolved unknowns

| Area | Resolved by |
|---|---|
| Visibility predicate (blank vs. invalid vs. valid) | R1 — new `isSendableEmail` selector |
| Tree position of the inline field | R2 — inside `AlterEgoPanel`, above actions row |
| State surface (one field vs. two) | R3 — one field, existing `EmailChanged` action |
| Mid-send behaviour | R4 — prop-lift `isPending` into `AlterEgoPanel` |
| Visual treatment | R5 — reuse `.email-input` rules verbatim |
| Test approach | R6 — three Vitest files (selector + component + panel) |
| Rollback | R7 — single revert; no wire change |

No `[NEEDS CLARIFICATION]` markers remain.

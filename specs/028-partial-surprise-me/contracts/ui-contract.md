# UI Contract: Partial Surprise Me

**Feature**: 028-partial-surprise-me
**Date**: 2026-05-20

This feature exposes **no new HTTP surface** — the existing `POST /api/v1/alter-egos` contract is reused byte-identically (FR-2812). What follows is the *frontend UI contract*: the observable session-state transitions and user-visible behaviors a downstream reviewer (or any future test) should be able to rely on.

---

## Action

| Action | Payload | Effect on session |
|---|---|---|
| `SurpriseMePicked` | `{ picks: SurpriseMePicks }` (caller pre-merges per the merge helper) | Writes `picks.archetype`, `picks.universe`, `picks.artStyle` into session and transitions `phase` to `'picking'`. Does NOT clear `customRole`. Leaves every other slot untouched. |

(No new action is introduced. This is the existing 009 action with a modified reducer body — see data-model.md.)

---

## Selection rule (caller — `surprise()` in `useGenerateAlterEgo.ts`)

For each visible category at the moment the user clicks **Surprise Me**:

| Category | Explicit predicate | Resolved value sent to the reducer + the wire |
|---|---|---|
| Role (prefab) | `session.archetype !== null` | `session.archetype` (pass-through) |
| Role (custom) | `session.customRole.trim().length > 0` (and `archetype === null` per 022 invariant) | `Selections.customRole = session.customRole.trim()`, `Selections.archetype` omitted |
| Role (empty) | both above predicates false | `fullRoll.archetype` (random uniform draw) |
| Universe | `session.universe !== null` | `session.universe` (pass-through) |
| Universe (empty) | `session.universe === null` | `fullRoll.universe` (random uniform draw) |
| Art Style | `session.artStyle !== null` | `session.artStyle` (pass-through) |
| Art Style (empty) | `session.artStyle === null` | `fullRoll.artStyle` (random uniform draw) |

After the merge, the action sequence remains exactly as 009/005 defined it:

```text
SurpriseMePicked              (commits picks; phase → 'picking')
ActiveTabChanged              (tab → 'alter-ego', reason: 'generate'; bumps generateAutoSwitchNonce)
GenerateSubmitted             (phase → 'generating')
… mutation fires …
GenerateSucceeded / GenerateFailedWithFallback
```

The dispatch order is part of the contract: it is what gives the 005 entrance animation its single-frame signal.

---

## Button surface

Unchanged from 009. For completeness:

- **Label**: "Surprise Me" (no rename).
- **Disabled** iff (a) no photo present OR (b) trimmed first name is empty OR (c) `session.phase === 'generating'` OR (d) email field is non-empty AND invalid (023 FR-2304). All four conditions are the existing `isReadyToSurprise(state) === false` predicate.
- **ARIA**: `aria-disabled="true"` mirrors `disabled` when gated. Same keyboard behavior — `tabindex` stays in the document order; arrow-key handlers are not introduced or removed.
- **Position**: in the `.setup-layout__actions` flex row next to **Generate**. Same row, same order, same styling.
- **Hint copy** when disabled: lists missing photo + name only — never categories (FR-2810 + the 009 US3 inheritance).

---

## Wire payload (POST /api/v1/alter-egos)

Byte-identical to today's Generate. No new field, no new header, no new query param. Specifically:

```jsonc
{
  "archetype":  "<merged archetype | null>",     // null only when customRole is the explicit Role channel
  "universe":   "<merged universe>",             // always non-null after merge
  "artStyle":   "<merged art style>",            // always non-null after merge
  "photoMode":  "<session.photoMode>",
  "firstName":  "<session.firstName trimmed>",
  "customRole": "<session.customRole trimmed | omitted>"   // omitted when empty (022 contract)
}
```

Multipart form-data shape, file part name, and HTTP method are 023/024-defined and untouched.

---

## Backwards compatibility

- **Existing tests** that rely on Surprise Me CLEARING `customRole` (the 022 `'SurpriseMePicked clears customRole (022 / US3)'` describe in `reducer.test.ts`) MUST be updated to assert the OPPOSITE (Surprise Me **preserves** `customRole`). This is an intentional regression of the 022 FR-2209 behavior, codified by 028 FR-2814 in the spec.
- The frontend public API (component props, hook return shape, dispatched action shapes) is otherwise stable.

---

## Negative contract (things this feature explicitly does NOT change)

1. The `RandomCategorySelector` bean on the backend continues to roll Pose + Vibe for every request — Surprise Me does not opt out of that.
2. The 003/016 retry policy + the stub fallback on final HTTP failure are inherited verbatim.
3. The 007 tab gating during a generation in flight is unchanged.
4. The 005 entrance-animation signal carried by `reason: 'generate'` is unchanged.
5. The 023 email-on-tab-2 path is unchanged — `email` slot is never touched by Surprise Me (FR-2813).
6. The 010 / 018 print flow is unchanged.
7. The Start Over reset (007 / 002) is unchanged — and remains the only client-side mechanism that clears category slots.

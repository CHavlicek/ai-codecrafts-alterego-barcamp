# Data Model — 025: Conditional Email Input on the Alter Ego Tab

**Date**: 2026-05-15
**Status**: Final

## Session state delta

**None.** The existing `AlterEgoSession.email: string` field (introduced in 023) is the sole source of truth for the captured email. There is no new field, no widened type, and no new action.

```ts
// frontend/src/features/alterego/state/reducer.ts — UNCHANGED
export interface AlterEgoSession {
  // … other fields …
  email: string  // captured email; '' initial; mutated by EmailChanged; reset by StartOverRequested
}
```

## Action surface delta

**None.** Both tabs' email inputs dispatch the existing `EmailChanged` action:

```ts
// frontend/src/features/alterego/state/reducer.ts — UNCHANGED
| { type: 'EmailChanged'; email: string }
```

The reducer branch is also unchanged (it stores the raw value and does **not** touch `phase`).

## New selector

```ts
// frontend/src/features/alterego/state/selectors.ts — NEW
/**
 * 025 (issue #60) — true iff the captured email is BOTH well-formed AND
 * non-blank. Drives both:
 *   • the visibility of the Alter Ego-tab inline email fallback
 *     (FR-2501 / FR-2502): show iff !isSendableEmail
 *   • the enabled-state of {@code SendAsEmailButton} (FR-2505):
 *     enabled iff isSendableEmail (and not currently sending)
 *
 * <p>Differs from {@link emailValidity} which admits blank as valid
 * because Setup-tab Generate gating treats an optional empty field as a
 * legitimate state. This selector is the *send-ready* predicate — the
 * one that needs the trimmed value to be non-empty.
 */
export function isSendableEmail(state: AlterEgoSession): boolean {
  const v = validateEmail(state.email)
  return v.ok && v.trimmed.length > 0
}
```

### Invariant (test-pinned)

For every `AlterEgoSession s`:
- `isSendableEmail(s) ⇒ emailValidity(s) === 'valid'` (send-ready implies the existing Generate-gating selector also passes — there is no state where send is enabled but Generate would have been blocked by email).
- `isSendableEmail(s) ⇒ validateEmail(s.email).ok` (every send-ready value is valid; the reverse does not hold because blank is valid).

## Visibility predicate

The inline Alter Ego-tab email field is rendered iff **all** of the following hold:

1. The session is in a poster-visible phase: `session.phase ∈ { 'succeeded', 'failed_with_fallback' }` (FR-2510 — enforced structurally by living inside the same `AlterEgoPanel` branch that renders the actions row).
2. The visibility latch resolves to true: `!isSendableEmail(session) || hasShownInline` (FR-2501 / FR-2502 / FR-2508 amended).

`hasShownInline` is a `useState` boolean local to `AlterEgoPanel`. It is set to `true` the first time we observe `!isSendableEmail(session)` while in a terminal phase; it resets to `false` whenever we leave the terminal phases (Start Over / fresh Generate). The latch implements the "sticky-once-shown" rule introduced by the 2026-05-15 clarification: once the user sees the inline field, it stays visible for the rest of that poster session, avoiding a layout jolt mid-typing.

The button-enabled predicate is `isSendableEmail(session) AND !isPending` (FR-2505 / FR-2506) — unchanged. The field visibility and the button-enabled state are no longer lock-stepped: the field may be visible while the button is enabled (the sticky case).

## Component data flow

```
AlterEgoPanel
  ├── (calls useSendAlterEgoEmail to obtain { send, isPending })
  ├── PosterView (unchanged)
  ├── if (!isSendableEmail(session)) → InlineEmailFallback
  │     props: { value: session.email, onChange: (next) => dispatch({ type: 'EmailChanged', email: next }), disabled: isPending }
  │     ↓ delegates render to existing <EmailInput>
  └── .alter-ego-panel__actions
        ├── StartOverButton (unchanged)
        ├── PrintButton (unchanged)
        └── SendAsEmailButton
              props: { email: session.email, firstName, posterDataUrl, isPending?, send? }
              ↑ (new: prop-lift isPending + send from parent to avoid double hook calls; R4)
```

### Why `AlterEgoPanel` owns the `useSendAlterEgoEmail` call

The `useSendAlterEgoEmail` hook returns `{ send, isPending }`. Today it is called inside `SendAsEmailButton`. To keep `isPending` shared between the inline field (which needs to disable while sending) and the button (which needs to disable while sending), the cleanest move is to lift the hook call up to `AlterEgoPanel` and pass `send` + `isPending` down to `SendAsEmailButton`. This is one prop-thread; the existing `SendAsEmailButton` test continues to pass once it accepts the lifted props.

## Validation rule

**Unchanged.** The single canonical `validateEmail` rule in `frontend/src/features/alterego/validation/email.ts`:
- Trim NFC-normalised value.
- Blank → `{ ok: true, trimmed: '' }`.
- `trimmed.length > 254` → `{ ok: false, code: 'too_long' }`.
- Fails `^[^\s@]+@[^\s@]+\.[^\s@]+$` → `{ ok: false, code: 'invalid_format' }`.
- Otherwise → `{ ok: true, trimmed }`.

The Alter Ego-tab inline error reuses `EMAIL_ERROR_MESSAGE[code]` for surface text (FR-2507).

## State transitions

No new transitions. The reducer's existing `EmailChanged` branch is sufficient:

| From | Trigger | To |
|---|---|---|
| `email = ''` | `EmailChanged{ email: 'foo' }` | `email = 'foo'` (phase untouched) |
| `email = 'foo'` | `EmailChanged{ email: 'foo@example.com' }` | `email = 'foo@example.com'` (phase untouched) |
| `email = 'foo@example.com'` | `EmailChanged{ email: '' }` (via clear-X) | `email = ''` (phase untouched) |
| any | `StartOverRequested` | `email = ''` (full session reset; FR-2511) |

Visibility flips happen as a derived consequence of these transitions through `isSendableEmail`, not through any new action.

## No new persistence

The captured email continues to live only in the browser's React component state for the lifetime of one session. There is no localStorage write, no IndexedDB write, no cookie write, no server-side persistence (FR-2514 — inherits 001 FR-016 / FR-017 / FR-024 verbatim).

## No new wire format

No HTTP request or response schema changes. The `POST /api/v1/alter-egos/email` body is the same `EmailSendRequest` shape feature 023 introduced (FR-2515).

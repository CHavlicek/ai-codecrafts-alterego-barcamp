# UI Contract — 025: Conditional Email Input on the Alter Ego Tab

**Date**: 2026-05-15
**Status**: Final

This feature ships **no HTTP contract changes**. The `POST /api/v1/alter-egos` (generate) and `POST /api/v1/alter-egos/email` (send email) endpoints are unchanged in path, request body, response body, headers, and error semantics — they are inherited verbatim from feature 023.

What this document pins, therefore, is the **UI-level contract** the feature introduces — the observable behaviour the frontend exposes to the user (and to its own test suite). This is the artefact 025's reviewers should compare the running app against, in lieu of an OpenAPI diff.

---

## C1 — Visibility contract: the inline Alter Ego-tab email field

**Amended 2026-05-15** — once rendered in a given poster session, the field is sticky for the rest of that session (FR-2508 amended). The latch resets when the session leaves the terminal phases (Start Over / fresh Generate).

| Phase | `isSendableEmail(session)` now | Field has been rendered earlier in this session? | Inline field rendered? |
|---|---|---|---|
| `idle` / `picking` / `generating` | any | n/a (latch is cleared) | **No** (the panel is not in the poster branch) |
| terminal | `false` (blank OR invalid) | any | **Yes** — above the actions row, pre-filled with `session.email` |
| terminal | `true` | yes | **Yes** (sticky — keeps the layout stable while the user finishes typing) |
| terminal | `true` | no | **No** — never auto-appears for a session that began with a valid captured email |

Rules in plain English:
- The field appears the moment the captured email is not send-ready while we are looking at a poster.
- Once it has appeared, it stays for the rest of that poster session.
- It never auto-appears for a poster session that began with a valid captured email — *unless* the user later makes that email invalid (e.g. via tab round-trip + clear), in which case the field appears and then becomes sticky.
- A new poster session (Start Over → Generate again) re-evaluates from scratch.

## C2 — "Send As Email" button enabled-state contract

Button is enabled iff **both**:
- `isSendableEmail(session) === true` (FR-2505), **and**
- `useSendAlterEgoEmail.isPending === false` (FR-2505 / 023 inherited).

Otherwise disabled, with `aria-disabled` + accessible hint:
- "Enter a valid email above to enable sending" — when the captured email is blank or invalid (FR-2506; the wording supersedes 023's "Enter a valid email on the Setup tab to enable sending" in this branch, because the Alter Ego tab now offers an inline recovery field directly above the button).
- "Sending email…" — while a send is in flight (inherited from 023).

## C3 — Accessibility contract for the inline field

The inline field exposes the same a11y surface as the Setup-tab `EmailInput` (FR-2513):

- Labelled `<input type="email">` with associated `<label>` — label text "Email".
- `aria-label="Email"` on the input.
- `aria-invalid="true"` only when the value is non-blank AND fails the validator.
- `role="alert"` error region rendered with the validator's `EMAIL_ERROR_MESSAGE[code]` text when `aria-invalid="true"`.
- `autoComplete="email"`, `inputMode="email"`, `spellCheck={false}`.
- Trailing clear-X button visible only when the value is non-empty; clicking dispatches `EmailChanged{ email: '' }`.
- Disabled propagates to both the input AND the clear-X.

## C4 — State-write contract

Every keystroke / clear in the inline field MUST dispatch the existing `EmailChanged` action with the **raw** (un-trimmed, un-normalised) value:

```ts
dispatch({ type: 'EmailChanged', email: e.target.value })
```

There is **no** new action and **no** Alter Ego-tab-only state field. The Setup-tab input and the Alter Ego-tab inline field share `session.email` byte-for-byte.

## C5 — Reverse-direction contract (test-pinned)

When the user switches from the Alter Ego tab back to the Setup tab via the `TabsShell`, the Setup-tab `EmailInput` MUST display the same value the user just typed on the Alter Ego tab. (Pinned by reading from `session.email` in both places; no separate test needed beyond confirming `EmailInput`'s controlled-value contract.)

## C6 — Send pipeline contract — UNCHANGED

When the user clicks "Send As Email":

```
POST /api/v1/alter-egos/email
Content-Type: multipart/form-data; boundary=…
X-Correlation-Id: <UUID>

…the same EmailSendRequest 023 introduced (recipient, firstName, attached poster bytes)…
```

The hook, request, response, retry policy, RFC 7807 error mapping, and single-native-alert feedback are all inherited verbatim. No file under `frontend/src/features/alterego/{hooks,services,validation}` is modified by this feature.

## C7 — No-persistence contract

The captured email is never written to disk, localStorage, IndexedDB, cookies, or any server-side store. It lives only in React component state for the lifetime of one session (FR-2514 — inherits 001 FR-016 / FR-017 / FR-024). Start-Over resets it to `''` (FR-2511).

---

## How to verify this contract

| Contract clause | Verified by |
|---|---|
| C1 visibility table | `AlterEgoPanel.test.tsx` — three phase × validity scenarios + transition assertions (incl. the sticky-once-shown pin: blank → valid keeps the field visible, only the button enables; an initial-valid session never shows the field) |
| C2 button enabled-state | `AlterEgoPanel.test.tsx` — same scenarios assert `[role="button"][name="Send As Email"]` disabled/enabled state |
| C3 a11y | `InlineEmailFallback.test.tsx` — RTL `getByRole('textbox', { name: /email/i })`, `aria-invalid` and `role="alert"` assertions |
| C4 state-write | `InlineEmailFallback.test.tsx` — `userEvent.type(...)` then assert dispatched action shape |
| C5 reverse direction | Inherent — both tabs read from `session.email`. Single rendering test that mounts `EmailInput` with `session.email = 'X'` and asserts `getByDisplayValue('X')` is sufficient (already covered by `EmailInput.test.tsx`). |
| C6 send pipeline | Existing 023 tests (`SendAsEmailButton.test.tsx`, `useSendAlterEgoEmail.test.tsx`, `emailClient.test.ts`) — unchanged. |
| C7 no persistence | Code review — no new `localStorage` / `indexedDB` / `document.cookie` access introduced. ArchUnit does not apply (frontend) but reviewer-grep confirms. |

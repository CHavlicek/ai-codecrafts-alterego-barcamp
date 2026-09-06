# Quickstart — 025: Conditional Email Input on the Alter Ego Tab

**Date**: 2026-05-15
**Audience**: Reviewers + future-you doing local validation

---

## Run locally

```bash
# from repo root
cd frontend
npm install        # only if node_modules is stale
npm run dev        # Vite dev server on http://localhost:5173
```

Backend is required for the "Send As Email" click to actually dispatch (the visibility rule itself can be exercised without a backend). To run the backend:

```bash
# in a second terminal, from repo root
cd backend
./gradlew bootRun  # Spring Boot on http://localhost:8080
```

If you only want to validate the visibility rule (Stories 1 + 2 + 3 — the entire scope of this feature minus the actual send), the dev server alone is sufficient.

---

## Validate User Story 1 — Forgotten email recovery (P1)

1. Open `http://localhost:5173`.
2. On the Setup tab, capture a photo (or upload one), type a first name, pick all required categories, and **leave the Email field blank**.
3. Click **Generate**. Wait for the alter ego poster to appear.
4. Observe the Alter Ego tab: an Email input field MUST be rendered directly above the row of action buttons (Start Over · Print · Send As Email).
5. Observe the "Send As Email" button: it MUST be disabled. Hover/focus → the accessible hint MUST explain that a valid email is required.
6. Type `name@example.com` into the inline field.
7. Observe the "Send As Email" button: it MUST become enabled on the same keystroke that completes the valid format.
8. Observe the inline field: it MUST disappear, and the actions row MUST move up to reclaim the layout.
9. Click "Send As Email" — the existing 023 send flow fires (single native alert on success/failure).

✅ Story 1 passes if all eight observations match.

---

## Validate User Story 2 — Already-valid email means no duplicate field (P1)

1. Reload the app (clean session).
2. On the Setup tab, type a **valid** email (e.g. `you@example.com`) into the Setup-tab Email field, plus the rest of the inputs.
3. Click **Generate**. Wait for the alter ego poster to appear.
4. Observe the Alter Ego tab: there MUST be **no** email input field anywhere on the tab.
5. Observe the "Send As Email" button: it MUST be enabled the instant the poster becomes visible.

✅ Story 2 passes if there is no inline field and the button is immediately enabled.

---

## Validate User Story 3 — Correct an invalid captured email (P2)

This scenario is reachable via Surprise Me (which bypasses Setup-tab email validation if a malformed value is in the field). Drive it by:

1. Reload the app.
2. Type `not-an-email` into the Setup-tab Email field (you'll see the inline format error, but the field is optional so it doesn't block anything except Generate).
3. Capture a photo + type a first name.
4. Click **Surprise Me** (Surprise Me requires only photo + first name + a valid-or-blank email per existing 023 gating — `not-an-email` will block Surprise Me too; if so, clear the field instead).
5. Once Surprise Me is reachable and you've actually wired a flow that puts a non-blank invalid value into `session.email` while in `succeeded` phase, switch to the Alter Ego tab.
6. The inline field MUST be visible, **pre-filled** with the invalid value, and the inline format-error text MUST be shown below.
7. Edit to a valid email — the field disappears and "Send As Email" enables.

✅ Story 3 passes if pre-fill + inline error + correct-in-place all work.

> Note: with the current gating, Story 3 is rarely encountered in everyday use (the Setup-tab Generate / Surprise Me both block invalid-non-blank email values). The spec captures it for completeness — see spec.md → Story 3.

---

## Edge-case smoke tests

- **Round-trip via tab switching**: With the inline field visible, type `partial@`, switch to the Setup tab. The Setup-tab email input MUST show `partial@`. Switch back to the Alter Ego tab. The inline field MUST still be visible (still invalid) with `partial@` pre-filled.
- **Start Over**: With a typed inline value, click Start Over. After confirmation, the entire session resets — first-name empty, photo cleared, captured email empty.
- **Clear-X**: With `valid@example.com` typed in the inline field, the field is hidden (Send As Email enabled). Backspace one character so the value becomes invalid — the field re-mounts at the same position with the partially-typed value. Click the clear-X — `session.email` becomes empty; the field stays visible because blank is not send-ready.
- **Print path unaffected**: With or without the inline field visible, clicking Print MUST behave exactly as before (no regression — Print does not consult the captured email).

---

## Run the test suite

```bash
cd frontend
npm test -- --run                              # full Vitest suite, single run
npm test -- --run state/selectors.test         # just the selector deltas
npm test -- --run components/InlineEmail       # just the new component
npm test -- --run components/AlterEgoPanel     # the panel visibility scenarios
```

All three new test files and all existing tests MUST pass. Coverage on the new code MUST be ≥ 90% (Constitution Principle III).

The backend test suite is unchanged by this feature and need not be rerun for this PR specifically. (CI on `main` will rerun the full pyramid post-merge.)

---

## What this feature does NOT change

- No new backend Java file.
- No new HTTP endpoint, no new request body, no new response body.
- No new runtime dependency in `frontend/package.json` or `backend/build.gradle.kts`.
- No new persistence (no disk, no DB, no cache).
- No change to the Setup-tab visual layout.
- No change to the validation rule (single `validateEmail` source).
- No change to the send pipeline (single `useSendAlterEgoEmail` hook + `emailClient` + RFC 7807 mapping).

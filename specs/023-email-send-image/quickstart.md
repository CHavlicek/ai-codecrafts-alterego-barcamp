# Quickstart — Send Generated Alter Ego By Email (023)

Exercises the three click-time outcomes specified by 023:
not-configured, success, and retryable-failure. Each scenario is a
two-process setup (backend + frontend) plus an SMTP variant.

## Scenario A — Mail server NOT configured (default, ships-as)

This is the default the feature ships in. Mailing-service provisioning
is explicitly out of scope (issue #57); the seam is wired but inert.

```sh
# Terminal 1 — backend with no SMTP config
cd backend
./gradlew bootRun
# (do NOT set SPRING_MAIL_HOST)

# Terminal 2 — frontend
cd frontend
npm run dev
```

Steps:

1. Open `http://localhost:5173` in a browser.
2. Setup tab: take/upload a photo, pick a Role / Universe / Art Style,
   enter a first name, AND **enter a valid email** (e.g.,
   `someone@example.com`) in the new Email field between the category
   block and the first-name input.
3. Click Generate. Wait for the Alter Ego tab to appear with the poster.
4. Observe the **Send As Email** button to the right of the **Print**
   button. It should be **enabled** (the email field is valid).
5. Click Send As Email.
6. **Expected**: a native browser alert popup appears saying *"The
   email server is not yet configured."* No other UI change.

What's happening under the hood:

- The frontend POSTs `to`, `firstName`, and the image bytes (multipart)
  to `POST /api/v1/alter-egos/email`.
- The backend's `EmailConfigured` bean returned `false` at startup
  (because `spring.mail.host` is blank).
- `AlterEgoEmailService.send()` throws `EmailNotConfiguredException`
  before touching any `JavaMailSender`.
- `ProblemDetailAdvice` translates it to `503` with `type =
  https://aiavatar.local/problems/email/not-configured`.
- `useSendAlterEgoEmail` classifies the 503 + typed problem detail
  as `'not_configured'` → fires the alert.

## Scenario B — Mail server configured + reachable

Demonstrates the happy path. Requires a local SMTP catcher. Two
common options:

### B1 — MailHog (single-binary smoke test)

```sh
# Terminal 0 — MailHog (Docker)
docker run -p 1025:1025 -p 8025:8025 mailhog/mailhog

# Terminal 1 — backend with SMTP config pointed at MailHog
cd backend
SPRING_MAIL_HOST=localhost \
  SPRING_MAIL_PORT=1025 \
  AIAVATAR_EMAIL_FROM=alter-ego@codecrafts.local \
  ./gradlew bootRun
```

### B2 — GreenMail (as a JVM process, no Docker)

Run GreenMail's standalone JAR on port 3025; substitute
`SPRING_MAIL_PORT=3025` above.

Then in another terminal:

```sh
# Terminal 2 — frontend
cd frontend
npm run dev
```

Steps:

1. Repeat steps 1–4 from Scenario A.
2. Click Send As Email.
3. **Expected**:
   - A native alert popup appears saying *"Email sent to
     someone@example.com."*
   - In MailHog's UI (`http://localhost:8025`), the email appears with:
     - From: `alter-ego@codecrafts.local`
     - To: `someone@example.com`
     - Subject: `Your AI Generated Alter Ego - CodeCrafts 2026`
     - Body (text/plain, UTF-8):
       ```
       Hey, {YourFirstName}!

       Thank you, for being a part of CodeCrafts 2026!

       Find your AI Generated Alter Ego attached to this letter.

       Happy times!
       ```
       (with `{YourFirstName}` replaced by whatever you typed)
     - One attachment: `aiavatar-alter-ego.png` (or `.jpg` if your
       generator returned JPEG)

## Scenario C — Mail server configured but unreachable

Demonstrates the retryable-failure path (FR-2316).

```sh
# Terminal 1 — backend with SMTP pointed at a blackhole IP
cd backend
SPRING_MAIL_HOST=10.255.255.1 \
  SPRING_MAIL_PORT=25 \
  AIAVATAR_EMAIL_FROM=alter-ego@codecrafts.local \
  ./gradlew bootRun
```

Steps:

1. Repeat steps 1–4 from Scenario A.
2. Click Send As Email.
3. Wait — the project-wide `RetryTemplate` will attempt 5 sends with
   exponential backoff before giving up.
4. **Expected**: after the retries exhaust, a native alert popup
   appears saying *"Sending failed. Please try again."*
5. Click Send As Email again — the button is still clickable
   (FR-2317). The same retry-fail loop runs again.

## Optional Setup-tab validation walk-through

These exercises do not require any SMTP setup.

- Leave the Email field **blank**, click Generate → generation
  proceeds normally. On the Alter Ego tab, **Send As Email is
  disabled** (FR-2315) with an explanatory `aria-label` /
  tooltip when focused. **Print stays enabled.**
- Type `not-an-email` in the Email field → an inline error appears
  beneath the field; **Generate and Surprise Me are both disabled**
  (clarification 2026-05-11 — invalid email blocks both submit
  paths).
- Click the trailing **X** in the Email field → the field clears, the
  inline error disappears, the X disappears, and Generate /
  Surprise Me become available again (because blank is a valid
  optional state).

## How the backend tests run without a real SMTP server

Per spec / plan:

- `AlterEgoEmailControllerIntegrationTest` is `@SpringBootTest` with
  `@MockBean JavaMailSender` — exercises the controller + service
  wiring end-to-end without binding to a real SMTP socket.
- `AlterEgoEmailBodyBuilderTest` pins the exact FR-2314 byte
  sequence — a one-character drift fails the suite.
- `EmailConfiguredTest` covers both presence cases:
  `spring.mail.host=` (blank) → `false`; `spring.mail.host=smtp.test`
  → `true`.
- `SendAlterEgoEmailRequestValidationTest` runs the Bean-Validation
  rules against a hand-built fixture grid (valid, missing, malformed,
  too long).

Run `./gradlew test` from `backend/` for the full backend suite.

## How the frontend tests run

- `npm run test` from `frontend/` runs the Vitest suite — covers
  `validation/email.ts`, `EmailInput.tsx`, `SendAsEmailButton.tsx`,
  the reducer's new `EmailChanged` branch, and the
  `useSendAlterEgoEmail` mutation with `fetch` stubbed.
- `npm run e2e` (or whatever the existing E2E runner is) runs the
  Playwright `email-send.e2e.ts` happy-path scenario: enter a valid
  email, mock the backend with a 200 response, generate, click Send
  As Email, observe the success alert via Playwright's
  `page.on('dialog', …)` hook.

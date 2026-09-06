# Operator Runbook — Default outbound email to Namecheap PrivateEmail

**Feature**: 027-privateemail-smtp
**Audience**: Operator wiring a fresh deployment, or rotating credentials on an existing one.
**Time to first successful send**: ~15 min (assumes mailbox already provisioned in Namecheap).

This runbook supersedes `specs/023-email-send-image/quickstart.md` for any deployment that wants to send through Namecheap PrivateEmail (the application's default after this feature ships).

---

## 1. Prerequisite — provision a PrivateEmail mailbox

This step is outside the application:

1. In your Namecheap account, **PrivateEmail → Manage** the domain you want to send from.
2. Create (or claim) a mailbox — e.g. `alter-ego@your-domain.tld`.
3. Set a strong password. Save it in your password manager / secret store.
4. (Strongly recommended) Add the PrivateEmail-provided **SPF**, **DKIM**, and **DMARC** DNS records to the sending domain. These are not technically required for the SMTP submission to succeed, but **without them, deliverability to Gmail / Outlook / Apple Mail will be poor or zero** — many recipients will silently drop the message or land it in Spam.

You now have three pieces of information you need for step 2:

- `<your mailbox>` — e.g. `alter-ego@your-domain.tld`
- `<your mailbox password>`
- `<From address>` — must be the mailbox or one of its aliases; commonly the same value as `<your mailbox>`. See step 4 for the alignment rule.

---

## 2. Wire the three Terraform variables

The deployment pipeline reads three Terraform variables and exports them as env vars on the running backend container. Set these in your Terraform `tfvars` (or your secrets pipeline of choice — Terraform Cloud variable sets, Vault, etc.):

| Terraform variable | Type | Value | Env var exported to container | Spring property it sets |
|---|---|---|---|---|
| `smtp_username` | string (secret) | `<your mailbox>` (from step 1) | `SPRING_MAIL_USERNAME` | `spring.mail.username` |
| `smtp_password` | string (secret) | `<your mailbox password>` (from step 1) | `SPRING_MAIL_PASSWORD` | `spring.mail.password` |
| `email_from` | string (sensitive) | `<From address>` (from step 1) | `AIAVATAR_EMAIL_FROM` | `aiavatar.email.from` |

You should **not** set the host / port / encryption variables — those are the defaults that ship with the application (step 3). They are overridable if you ever want to point at a different SMTP provider (step 6), but the happy path doesn't touch them.

> **Secrets hygiene reminder**: never commit `smtp_username`, `smtp_password`, or the password value into source control. Treat all three as secrets in your Terraform module (`sensitive = true`) and source them from your secret store at apply time.

---

## 3. Defaults that ship with the application (sanity-check)

For reference — these are baked into `backend/src/main/resources/application.yml` and require no operator action unless you are pointing at a different SMTP provider:

| Spring property | Default value | Notes |
|---|---|---|
| `spring.mail.host` | `mail.privateemail.com` | PrivateEmail's documented SMTP submission host. |
| `spring.mail.port` | `587` | STARTTLS submission port. PrivateEmail also offers port 465 (implicit-TLS); see step 6. |
| `spring.mail.protocol` | `smtp` | |
| `spring.mail.properties.mail.smtp.auth` | `true` | Required by PrivateEmail. |
| `spring.mail.properties.mail.smtp.starttls.enable` | `true` | Forces STARTTLS upgrade before AUTH. |
| `spring.mail.properties.mail.smtp.starttls.required` | `true` | Refuses to send if the server can't STARTTLS. Defensive default; PrivateEmail always supports STARTTLS on 587. |

You can verify the running deployment against Namecheap's published SMTP settings page — the values above should match the **"SMTP outgoing server settings"** section under Namecheap's PrivateEmail docs.

---

## 4. From-alignment rule (PrivateEmail's hard constraint)

PrivateEmail enforces strict sender-alignment at SMTP submission time:

> **`var.email_from` MUST equal `var.smtp_username` exactly, or be a mailbox alias that you have configured in the Namecheap PrivateEmail control panel for that mailbox.**

If they don't match, PrivateEmail rejects the message at `MAIL FROM` time. The application surfaces this rejection as the existing **retryable-failure alert** ("Sending failed. Please try again.") — exactly as for any other transient SMTP failure. The user has no way to recover from this without an operator fixing the deployment.

> **Common operator mistake**: setting `email_from` to something pretty like `noreply@your-domain.tld` while `smtp_username` is `alter-ego@your-domain.tld`. PrivateEmail will reject every message until the From is brought back into alignment.

The simplest correct setup is: `email_from` exactly equals `smtp_username`.

---

## 5. Smoke-test the wiring

### 5a. Local smoke test against the bundled integration tests

The 023 integration suite covers the configured + not-configured branches against a `@MockitoBean JavaMailSender` (no real network call). Run from the repo root:

```bash
./gradlew :backend:integrationTest --tests AlterEgoEmailControllerIT --tests AlterEgoEmailControllerNotConfiguredIT
```

Expected: both classes green. This validates the wiring without touching PrivateEmail.

### 5b. End-to-end smoke test against a real PrivateEmail mailbox

With the three Terraform variables set (step 2) and the backend running locally or in your dev environment:

```bash
# Replace the recipient with your own address — you should receive the test message.
curl -X POST http://localhost:8080/api/v1/alter-egos/email \
     -F "to=<your-test-recipient>" \
     -F "firstName=Smoke-Test" \
     -F "image=@./poster-frame-long-bottom.png;type=image/png" \
     -i
```

Expected response:

```text
HTTP/1.1 200 OK
X-Request-Id: <uuid>
...
{"status":"sent"}
```

…and a message landing in your test inbox within a few seconds with subject `Your AI Generated Alter Ego - CodeCrafts 2026`.

If you see `503 not-configured`: one of `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, or `AIAVATAR_EMAIL_FROM` is blank at startup. Set it and **restart** the backend (step 7).

If you see `502 send-failed`: PrivateEmail rejected the submission. Most common causes, in order:
1. Wrong password.
2. From not aligned with the authenticated mailbox (step 4).
3. Outbound TCP port 587 blocked from the deployment network (step 7).

---

## 6. Pointing the app at a non-PrivateEmail provider (optional)

If you ever need to send through a different SMTP provider (a local relay for dev, an alternate paid provider for a different deployment), set these env vars **in addition to** the three from step 2:

| Env var | Example value | Notes |
|---|---|---|
| `SPRING_MAIL_HOST` | `smtp.example.com` | Overrides the PrivateEmail default. |
| `SPRING_MAIL_PORT` | `465` or `25` or your provider's port | Override the 587 default if needed. |
| `SPRING_MAIL_PROTOCOL` | `smtp` (or `smtps`) | Usually unchanged. |

For port 465 (implicit-TLS) you may also need to set:

```text
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=false
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true
```

To **explicitly disable email** in a given deployment (so the app surfaces the not-configured alert from day one), set:

```text
SPRING_MAIL_HOST=
```

(Explicit blank wins over the PrivateEmail default — FR-2705.)

---

## 7. Operational caveats

- **Restart required after credential change**: The `EmailConfigured` gate is a one-shot evaluation at JVM startup. After changing any of `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, `AIAVATAR_EMAIL_FROM`, or `SPRING_MAIL_HOST`, you **must** restart the backend container for the change to take effect (FR-2708).
- **Outbound port 587 must be reachable**: Many corporate / cloud networks block outbound TCP 587 by default. If your smoke-test (step 5b) returns a `502 send-failed` with a connection-timeout-shaped retry log, ask your network team to allow outbound TCP 587 from the backend container to `mail.privateemail.com`.
- **Never enable `mail.debug=true`**: Jakarta Mail's debug flag dumps the full SMTP transcript, **including the base64-encoded AUTH frame** (which contains the password). The application ships with this flag implicitly off; do not enable it in any profile, ever. If you need to debug a send failure, rely on the structured `event=email.send.failed exceptionClass=...` log line (which carries no secrets) plus the `X-Request-Id` echoed by the response.
- **PrivateEmail rate limits**: PrivateEmail enforces per-mailbox submission rate limits (~150 messages/hour on standard plans, plus tight short-window burst limits). The application makes no attempt to throttle or rate-limit its sends and will retry every transient failure 5 times with exponential backoff. At expected production load (~tens of sends across a CodeCrafts event window) this is a non-issue; if you ever drive much higher volume you'll need to revisit FR-2713.
- **No persistence**: The recipient address, the message body, the attached image, and the PrivateEmail credentials live only in process memory. The only log line emitted on success is `event=email.send.completed outcome=sent correlationId=<uuid>` — no recipient, no body, no first-name. Verified by inspection (SC-2306 / SC-2705).

# Data Model — Send Generated Alter Ego By Email (023)

No persistence. All entities below live exclusively in browser session
memory (frontend) or on the request thread / `@PostConstruct`-cached
beans (backend) for the lifetime of one running process. Nothing is
written to disk, database, cache, or logs (FR-2320 / SC-2306).

## Frontend entities

### `AlterEgoSession.email` (new field on the existing reducer state)

| Aspect | Value |
|---|---|
| Type | `string` (never `null`) |
| Initial value | `""` |
| Domain | empty string, or any string the user has typed (NFC-normalised at validation time, not at storage time) |
| Cleared by | `StartOverRequested` |
| Mutated by | `EmailChanged` action (payload: `{ email: string }`) |
| Lifetime | Browser session; never persisted; never sent on the existing `/api/v1/alter-egos` Generate request |
| Consumed by | `EmailInput.tsx` (controlled value), `SendAsEmailButton.tsx` (recipient + disabled-gate), `selectors.ts:emailValidity()`, `selectors.ts:missingInputs()` |

**Validation states** (computed by `validation/email.ts`):

| State | Inputs | Semantics |
|---|---|---|
| `'valid'` | trimmed value is `""` | Optional field left blank — Generate / Surprise Me proceed; Send button is disabled (no recipient) |
| `'valid'` | trimmed value matches `/^[^\s@]+@[^\s@]+\.[^\s@]+$/` AND length ≤ 254 | Well-formed — Generate / Surprise Me proceed; Send button enabled |
| `'invalid'` | non-blank, fails format or length cap | Generate / Surprise Me blocked with inline error; Send button disabled |

### `EmailValidation` (validator return type)

```ts
type EmailValidation =
  | { ok: true; trimmed: string }   // includes the empty-string case (trimmed === '')
  | { ok: false; code: 'invalid_format' | 'too_long' }
```

The trimmed value flows into the `to` part of the multipart Send request when
the user clicks Send As Email.

### `SendOutcome` (mutation classification)

```ts
type SendOutcome = 'sent' | 'not_configured' | 'failed'
```

Produced by `useSendAlterEgoEmail` from the response status + problem-detail
type. `'sent'` → success alert; `'not_configured'` → not-configured alert;
`'failed'` → retryable-failure alert.

## Backend entities

### `EmailConfiguration` (typed config + cached boolean)

| Aspect | Value |
|---|---|
| Source | `application.yml`: `spring.mail.host`, `spring.mail.port`, `aiavatar.email.from` (sender), `aiavatar.email.subject` (overridable; default = `"Your AI Generated Alter Ego - CodeCrafts 2026"`) |
| Cached field | `final boolean configured = (spring.mail.host != null && !spring.mail.host.isBlank())` evaluated at `@PostConstruct` |
| Lifetime | JVM process lifetime — no re-evaluation on config-file change |
| Consumed by | `AlterEgoEmailService` (short-circuits to `EmailNotConfiguredException` before any `JavaMailSender` call when `!configured`) |

**Invariant**: when `configured == false`, the `JavaMailSender` bean MAY
or MAY NOT exist (it does not when `spring.mail.host` is blank — that's
Spring Boot's auto-configuration behaviour). The service MUST consult
`configured` first and never inject `JavaMailSender` as a non-optional
dependency.

### `SendAlterEgoEmailRequest` (request DTO)

A wrapper for the multipart parts; not a `record` bound by Jackson — the
controller assembles it from `@RequestPart` parameters and applies
validation manually (Spring's multipart binding does not synthesise
Bean-Validation on multi-part parameters automatically).

| Field | Type | Validation |
|---|---|---|
| `to` | `String` | `@NotBlank`, `@Email`, `@Size(max = 254)`, trimmed before validation |
| `firstName` | `String` | `@NotBlank`, `@ValidFirstName` (reusing 011's existing validator), `@Size(max = 50)`, trimmed before composition |
| `image` | `MultipartFile` | non-null, content-type ∈ {`image/png`, `image/jpeg`}, ≤ 20 MB (existing `MultipartConfig` cap) |

Validation failures → RFC 7807 `400 Bad Request` via the existing
`ProblemDetailAdvice`.

Image MIME outside the allow-list → `415 Unsupported Media Type` with a
problem detail (mirrors the Generate controller's posture).

### `SendAlterEgoEmailResponse` (response DTO)

```java
public record SendAlterEgoEmailResponse(String status) {
    public static SendAlterEgoEmailResponse sent() {
        return new SendAlterEgoEmailResponse("sent");
    }
}
```

Wire shape: `{ "status": "sent" }`. The single-field shape is intentional —
the response carries no information the FE doesn't already have. The
status string is a stable contract: the FE classifies the response on
HTTP status code AND the `status` field for forward-compat.

### `EmailNotConfiguredException` (typed exception → 503)

Thrown by `AlterEgoEmailService` when `EmailConfiguration.isConfigured()
== false`. Mapped by an `@ExceptionHandler` in `ProblemDetailAdvice` to:

| Field | Value |
|---|---|
| HTTP status | `503 Service Unavailable` |
| `type` | `https://aiavatar.local/problems/email/not-configured` (stable URI; FE classifies on this) |
| `title` | `"Email service is not configured"` |
| `detail` | `"The mail server is not configured. Contact the operator to enable email delivery."` |
| `status` | `503` |

The FE matches on HTTP status `503` AND
`problemDetail.type.endsWith('/email/not-configured')`. Anything else
that returns 503 (e.g., a load-balancer maintenance page) is treated
as a retryable failure.

### `OutgoingAlterEgoEmail` (in-method composition value, no persistent representation)

Composed inside `AlterEgoEmailService.send()`:

| Field | Source |
|---|---|
| `from` | `aiavatar.email.from` config property |
| `to` | `SendAlterEgoEmailRequest.to` (trimmed) |
| `subject` | `aiavatar.email.subject` config property (default = FR-2313 string) |
| `body` (text/plain; UTF-8) | `AlterEgoEmailBodyBuilder.build(firstName)` — produces the exact FR-2314 byte sequence with `{firstName}` substituted |
| `attachment` | `image` bytes + the multipart's content-type, attached as `aiavatar-alter-ego.png` (or `.jpg` based on content-type) |

Lifetime: composed and sent inside one `send()` invocation; no field is
copied into MDC / structured args / logs (R5).

## State transitions

### Frontend reducer — `EmailChanged`

```
state = { ..., email: "current" }
dispatch({ type: "EmailChanged", email: "new" })
→ state = { ..., email: "new" }
```

No other field is touched. `phase` is NOT changed (an email edit isn't a
generation-flow transition). The existing `EmailChanged` does not
participate in `generateAutoSwitchNonce` or other 005/007 signals.

### Frontend reducer — `StartOverRequested`

The existing `initialAlterEgoSession()` factory now includes
`email: ''` — `StartOverRequested` already routes through that factory,
so no extra branch is needed. The Email field is cleared along with the
rest of the Setup state.

### Frontend mutation — `useSendAlterEgoEmail`

```
idle
  --click--> sending  (button disabled while sending; existing "isPending"-style flag)
sending
  --200--> idle, alert("Email sent to {to}.")
  --503 + type=not-configured--> idle, alert("The email server is not yet configured.")
  --any other non-2xx OR thrown--> idle, alert("Sending failed. Please try again.")
```

Each terminal alert is a synchronous `window.alert(...)`. The button is
re-enabled at the end of every terminal transition so the user can
retry.

### Backend service — `AlterEgoEmailService.send()`

```
1. if !emailConfiguration.isConfigured()
     throw EmailNotConfiguredException   → 503
2. compose MimeMessage (from, to, subject, body, attachment)
3. retryTemplate.execute(ctx -> javaMailSender.send(message))
   on retry exhaustion → MailException propagates → ProblemDetailAdvice
   maps to 502 Bad Gateway with a generic problem detail
4. log "event=email.send.completed outcome=sent correlationId={id}"   (no PII)
5. return SendAlterEgoEmailResponse.sent()
```

## Cross-cutting

- **Correlation**: the `X-Request-Id` header is honoured for parity with
  the Generate endpoint. Generated by the controller if absent.
- **Redaction**: `to`, `firstName`, and the message body NEVER enter
  `MDC` or structured log args. The single completion log line emits
  only `correlationId` + `outcome` (R5).
- **Multipart caps**: existing `spring.servlet.multipart.max-file-size:
  20MB` from 003 covers the image part. No new cap.

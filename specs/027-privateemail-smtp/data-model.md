# Data Model: Default outbound email to Namecheap PrivateEmail

**Feature**: 027-privateemail-smtp
**Date**: 2026-05-19

This feature does not introduce any new persisted, in-flight, or wire-format entities. It refines two **existing** configuration entities from 023 and 024.

---

## Entity 1 — `MailServerTarget` (configuration entity)

**Lifetime**: JVM process — populated at `@PostConstruct`, immutable thereafter (FR-2708 / 023 FR-2318).
**Persistence**: None. Holds operator-supplied configuration values plus the shipped defaults from `application.yml`.

### Fields

| Field | Source | New in 027? | Default | Notes |
|---|---|---|---|---|
| `host` | `spring.mail.host` ← `${SPRING_MAIL_HOST:mail.privateemail.com}` | No, default changed | `mail.privateemail.com` | Was blank in 023. Now defaults to PrivateEmail's documented SMTP submission host. |
| `port` | `spring.mail.port` ← `${SPRING_MAIL_PORT:587}` | No, default changed | `587` | Was `25` in 023. Now PrivateEmail's STARTTLS submission port. |
| `protocol` | `spring.mail.protocol` ← `${SPRING_MAIL_PROTOCOL:smtp}` | No | `smtp` | Unchanged. |
| `username` | `spring.mail.username` ← `${SPRING_MAIL_USERNAME:}` | No, semantics changed | (blank) | Now participates in the `configured` gate (FR-2706). |
| `password` | `spring.mail.password` ← `${SPRING_MAIL_PASSWORD:}` | No, semantics changed | (blank) | Now participates in the `configured` gate (FR-2706). |
| `starttlsEnable` | `spring.mail.properties.mail.smtp.starttls.enable` | **Yes** | `true` | Forces STARTTLS upgrade before AUTH. |
| `starttlsRequired` | `spring.mail.properties.mail.smtp.starttls.required` | **Yes** | `true` | Refuses to send if STARTTLS unavailable — defensive default. |
| `authEnable` | `spring.mail.properties.mail.smtp.auth` | **Yes** | `true` | Enables SMTP-AUTH dialogue (PrivateEmail requires it). |

### Validation rules

- All `spring.mail.properties.mail.smtp.*` properties land in the standard Jakarta Mail property bag that `JavaMailSenderImpl` reads — no custom validation needed.
- Operator-supplied overrides are accepted verbatim (no Spring-side coercion beyond Boot's standard binding).
- An explicit blank for `host` / `port` / `username` / `password` is honoured as an operator decision to clear the default (FR-2705). For the STARTTLS / auth flags, an explicit `false` is similarly honoured (operator can disable when pointing at a relay that doesn't speak STARTTLS).

### Lifecycle

- Populated once during Spring context refresh by `MailSenderAutoConfiguration` + the `aiavatar.email.*` property binding (`EmailProperties`).
- Read once by `EmailConfigured` at `@PostConstruct` to compute the boolean snapshot.
- Read continuously thereafter by `JavaMailSender` (created by Spring Boot) when `mailSender.send()` is called from `AlterEgoEmailService`.

---

## Entity 2 — `ConfiguredState` (boolean snapshot)

**Lifetime**: JVM process — populated at `@PostConstruct`, immutable thereafter.
**Persistence**: None. Cached `boolean` field on the `EmailConfigured` Spring component.

### Definition (semantic — code in Phase 2)

`configured = ` AND of:
1. `host` non-blank (existing — 023)
2. `username` non-blank (**new in 027** — FR-2706 (b))
3. `password` non-blank (**new in 027** — FR-2706 (c))
4. `from` non-blank (**new in 027** — FR-2706 (d))

### State transitions

There is exactly one state transition per JVM lifetime: `null` → `true` or `null` → `false`, computed at `@PostConstruct`. The boolean is never recomputed. Operator changes to any of the four inputs require a JVM restart (FR-2708).

### Consumers

- `AlterEgoEmailService.send()` — short-circuits with `EmailNotConfiguredException` when false (existing — 023). Unchanged consumer logic; the gate just becomes harder to satisfy.
- `EmailConfigured` startup log line — emits the boolean (unchanged log shape from 023).

### Observable behaviour from the public HTTP contract

The contract is unchanged from 023:

| `ConfiguredState` value | `POST /api/v1/alter-egos/email` outcome |
|---|---|
| `true` | Send pipeline runs. Possible outcomes: `200 sent` / `502 send-failed` (after retry exhaustion) / `400 validation` / `415 unsupported-media`. |
| `false` | Short-circuit. Always `503 not-configured` with `type=.../email/not-configured`. |

---

## What is NOT a new entity

- **No new request / response DTOs**. `SendAlterEgoEmailRequest` and `SendAlterEgoEmailResponse` from 023 are unchanged.
- **No new exception classes**. `EmailNotConfiguredException` (023) is reused; the four-way gate just makes it fire in more deployment configurations.
- **No new ProblemDetail types**. The two type URIs `/email/not-configured` and `/email/send-failed` are preserved.
- **No new logged events**. The two structured events `event=email.config.evaluated` (startup) and `event=email.send.completed` / `event=email.send.failed` (runtime) keep their existing shape.

---

## Relationships

```text
[operator]
    │ (writes Terraform variables)
    ▼
[Terraform module]
    │ (provisions env vars on the deployment container)
    ▼
SPRING_MAIL_USERNAME, SPRING_MAIL_PASSWORD, AIAVATAR_EMAIL_FROM
    │ (read at JVM startup)
    ▼
EmailProperties (typed: from, subject)        +        @Value-injected host/port/username/password
    │                                                       │
    └───────────────────┬──────────────────────────────────┘
                        ▼
                 EmailConfigured  ──► boolean snapshot (4-input AND)
                        │
                        ▼
            AlterEgoEmailService.send(…)
                        │
                        ▼
            JavaMailSender ──► mail.privateemail.com:587 (STARTTLS + SMTP-AUTH)
```

Read top-to-bottom: a request from the existing 023 frontend hits `AlterEgoEmailController` → `SendAlterEgoEmailUseCase` → `AlterEgoEmailService.send()`. The service inspects `EmailConfigured.isConfigured()` first; if true, hands off to the auto-configured `JavaMailSender` which has been pre-wired with the PrivateEmail target parameters from `application.yml`.

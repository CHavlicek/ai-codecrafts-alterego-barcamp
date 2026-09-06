# Research: Default outbound email to Namecheap PrivateEmail

**Feature**: 027-privateemail-smtp
**Date**: 2026-05-19
**Status**: Complete — no `NEEDS CLARIFICATION` markers in `plan.md`.

The clarifications session resolved the two open decisions before planning started. Research below validates the concrete recipes implied by those clarifications and pins the literal values that the plan deliberately did not hard-code in the spec.

---

## R1 — Namecheap PrivateEmail SMTP submission recipe (concrete defaults)

**Question**: What literal host / port / encryption mode / authentication mode should `application.yml` ship as defaults so that the application talks to PrivateEmail out of the box, with only credentials and From supplied by the operator?

**Decision**: Ship the following as defaults in `spring.mail.*`:

| Property | Default value | Source |
|---|---|---|
| `spring.mail.host` | `mail.privateemail.com` | Namecheap PrivateEmail KB article "SMTP outgoing server settings". Stable since the PrivateEmail product launched. |
| `spring.mail.port` | `587` | PrivateEmail's documented submission port for STARTTLS (modern recommendation). Port 465 (implicit-TLS) is an equally valid alternative and reachable by setting `SPRING_MAIL_PORT=465` and toggling the SSL property; we picked 587 because it aligns with Spring Boot's documented STARTTLS example and is the more universal pattern across SMTP providers — the spec's "STARTTLS on the standard encrypted submission port" assumption. |
| `spring.mail.protocol` | `smtp` | Default; matches 023. |
| `spring.mail.properties.mail.smtp.auth` | `true` | PrivateEmail requires SMTP-AUTH on submission; without this Spring's `JavaMailSender` will skip the AUTH dialogue and PrivateEmail will reject. |
| `spring.mail.properties.mail.smtp.starttls.enable` | `true` | Forces the client to issue STARTTLS before AUTH; PrivateEmail's 587 endpoint negotiates this. |
| `spring.mail.properties.mail.smtp.starttls.required` | `true` | Refuses to send if the server can't STARTTLS — defensive against MitM that strips encryption. PrivateEmail always supports STARTTLS on 587, so this is safe to enforce by default. |
| `spring.mail.username` | `${SPRING_MAIL_USERNAME:}` | Blank by default (operator-supplied). Listed as default `""` in `application.yml` so an unset env var binds cleanly. |
| `spring.mail.password` | `${SPRING_MAIL_PASSWORD:}` | Blank by default (operator-supplied). |

**Rationale**:
- 587 + STARTTLS is the modern submission convention; port 465 (implicit-TLS) remains operator-reachable via the override env vars (FR-2704).
- `starttls.required=true` is the strictest reasonable default; PrivateEmail always supports it, so we don't lose deliverability. Lifts the bar against misconfigurations that downgrade silently.
- Username/password defaults stay blank so missing-credentials triggers the new `EmailConfigured` gate (FR-2706) — see R2.

**Alternatives considered**:
- *Port 465 + implicit TLS as the default*: rejected because (a) Spring's documented example uses 587 + STARTTLS, (b) implicit-TLS requires extra `mail.smtp.ssl.*` properties that complicate the YAML, (c) the user signal "simplest possible config" prefers the more conventional path. Operators wanting 465 can override.
- *Auto-derive From from the authenticated username*: rejected at clarification (Q1, Session 2026-05-19) — From is operator-supplied via Terraform.
- *Hard-coding credentials in YAML for dev convenience*: violates FR-2702 (no secrets in source).

---

## R2 — Tightened `EmailConfigured` gate semantics

**Question**: How should the startup-configured check change to accommodate the new non-blank default host without misfiring as "configured" in deployments that have no credentials?

**Decision**: `EmailConfigured.isConfigured()` becomes a four-input AND:

```java
this.configured =
    isNonBlank(host)
    && isNonBlank(username)
    && isNonBlank(password)
    && isNonBlank(from);
```

The four inputs are wired via Spring `@Value`:

- `@Value("${spring.mail.host:}")` — already present in 023.
- `@Value("${spring.mail.username:}")` — new in 027.
- `@Value("${spring.mail.password:}")` — new in 027.
- `@Value("${aiavatar.email.from:}")` — new in 027 (reading the same property `EmailProperties.from()` already binds; using `@Value` rather than a constructor reference to `EmailProperties` lets us inspect the blank/non-blank state before any other component depends on it being non-blank).

The single startup INFO log line is extended to be a single boolean (no per-input breakdown) so the operator gets the same one-line signal as in 023, without leaking PII:

```text
event=email.config.evaluated configured=false
```

A trailing per-input mask of `present`/`absent` could help debugging but risks leaking *which* env var was forgotten in a multi-tenant deployment. The runbook (R5) carries the operator-facing checklist instead.

**Rationale**:
- Mirrors the 023 contract style: one boolean, computed once, immutable.
- Aligns with the spec clarification (Session 2026-05-19, Q1) that adds From to the gate.
- A `private static boolean isNonBlank(String s) { return s != null && !s.isBlank(); }` helper keeps each line readable.

**Alternatives considered**:
- *Different exception type for credentials-missing vs from-missing*: rejected. The frontend already classifies on the `type` URI suffix `/email/not-configured` vs `/email/send-failed` (from 023); adding sub-types would burn FE work for no UX gain — the user sees the same "not yet configured" alert either way.
- *A live SMTP handshake at startup to validate credentials*: rejected. Violates FR-2708 (one-shot config-presence, no live probe). Surfaces credential bugs at click time (existing 023 retryable-failure alert).
- *Re-evaluating per click*: rejected. Same FR-2708 / 023 FR-2318 reasoning.

---

## R3 — Spring Boot `MailSenderAutoConfiguration` interaction with the new gate

**Question**: With the new non-blank default host, Spring Boot's `MailSenderAutoConfiguration` will now create a `JavaMailSender` bean by default (it gates on `spring.mail.host` being non-blank). Does that change the bean topology for the existing 023 tests / wiring?

**Decision**: Yes, but the change is bounded and intentional:
- **Production**: `JavaMailSender` is now always present (host is never blank by default). `AlterEgoEmailService` already wraps it in `Optional<JavaMailSender>` (023) — the `Optional` is now always-populated.
- **Tests**:
  - `AlterEgoEmailControllerIT` (the configured-and-happy-path IT) sets all four properties via `@TestPropertySource` to keep `EmailConfigured.isConfigured()=true`. The `@MockitoBean JavaMailSender` is unchanged.
  - `AlterEgoEmailControllerNotConfiguredIT` (the unconfigured IT) flips its strategy: it sets `spring.mail.host` to something non-blank but leaves `spring.mail.username` / `spring.mail.password` / `aiavatar.email.from` blank, so `EmailConfigured.isConfigured()` is false and the typed 503 still fires. (We could alternatively keep the host blank and continue to rely on the 023 host-only behaviour, but the new test directly exercises the new gate which is what we care about.)
  - `EmailConfiguredTest` adds four new cases: host-only-present, host+username-present, host+username+password-present, all-four-present. The first three assert `isConfigured()=false`; the fourth asserts `true`.

**Rationale**:
- Bean-topology change is localised to "JavaMailSender is now usually present" — the existing `Optional<JavaMailSender>` defensive plumbing handles it.
- The `management.health.mail.enabled=false` line in 023's `application.yml` (which disables the actuator mail health contributor because it's incompatible with `@MockitoBean JavaMailSender`) remains correct and necessary.

**Alternatives considered**:
- *Keep the host blank in source and ship a `.env.example` / `compose.override.yml` with the PrivateEmail recipe*: rejected because it doesn't satisfy the spec's FR-2701 ("the application MUST ship with default outbound-mail configuration values that target PrivateEmail"). The defaults need to be in the running app's classpath config, not in a sample file.

---

## R4 — Terraform → env-var pathway (operator-facing convention)

**Question**: Spec FR-2703 / FR-2712 require the runbook to name the Terraform variables that feed the deployment env vars. Which Terraform variables, and which env-var names?

**Decision**: Follow the existing 023 env-var names verbatim — they are already what the Spring `@Value` placeholders resolve. The Terraform module's variable names are a 1:1 mapping (snake_case in Terraform → SCREAMING_SNAKE_CASE env var in the running container):

| Terraform variable | Env var the deployment exports | Spring property it sets |
|---|---|---|
| `var.smtp_username` | `SPRING_MAIL_USERNAME` | `spring.mail.username` |
| `var.smtp_password` | `SPRING_MAIL_PASSWORD` | `spring.mail.password` |
| `var.email_from` | `AIAVATAR_EMAIL_FROM` | `aiavatar.email.from` |

The host / port / encryption defaults are *not* exposed as Terraform variables in the typical deployment, because the spec says they should ship pre-set. If an operator needs to override them (Story 1 acceptance #3 — point at a non-PrivateEmail provider), they can add `var.smtp_host`, `var.smtp_port`, etc. — but the runbook explicitly does not list them in the happy-path setup.

**Rationale**:
- Minimises the number of secrets the Terraform module manages — three variables, all of which are bona-fide secrets (the From address is operationally sensitive but not a credential).
- The mapping is mechanical and reversible; an operator reading the runbook can find the Spring property via either column.

**Alternatives considered**:
- *Prefix everything with `AIAVATAR_EMAIL_*` for consistency*: rejected. `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD` are the canonical Spring Boot env-var bindings; renaming them to `AIAVATAR_EMAIL_USERNAME` would require either a `@Value` indirection or a startup property-relocator (`PropertySourcesPlaceholderConfigurer` shim) — both add complexity for no operator benefit.
- *Wiring secrets through a Terraform-managed secret store (Vault, AWS Secrets Manager) instead of env vars*: deferred. The runbook references the env-var pathway as the application-facing contract; the operator is free to source those env vars from a secret store at deploy time. This is a deployment-infrastructure decision, not an application decision.

---

## R5 — PII / secrets posture (logs, error responses, retry traces)

**Question**: With credentials now mandatory and surfaced in env vars, what additional log-redaction or stack-trace redaction is required?

**Decision**: None beyond 023. The audit:

- **`EmailConfigured` startup log**: emits a single boolean (`configured=true/false`), no per-input mask, no env-var values.
- **`AlterEgoEmailService` success log** (`event=email.send.completed`): carries only `correlationId`. No recipient, no body, no first-name. No `From`. Inherited from 023.
- **`AlterEgoEmailService` failure log** (`event=email.send.failed`): carries `exceptionClass={ClassName}` and `correlationId`. No exception message (which could carry SMTP server transcript including AUTH fragments). Inherited from 023.
- **`ProblemDetail` body** (RFC 7807 503 / 502): freshly-constructed detail strings; no exception message echoing. Inherited from 023 `ProblemDetailAdvice`.
- **Spring Mail / Jakarta Mail debug logs**: NOT enabled. The repo's `logging.level.com.aiavatar.alterego=DEBUG` doesn't propagate to `org.springframework.mail` or `com.sun.mail`. We do not enable mail-debug logging in any profile, ever — Jakarta Mail's `mail.debug=true` flag dumps the entire SMTP transcript including AUTH base64. Runbook (R6) calls this out as a "don't enable in prod" warning.

**Rationale**:
- The risk surface is the AUTH dialogue (base64-encoded username + password). The default Spring + Jakarta Mail stack does not log this dialogue at INFO/DEBUG levels. As long as we don't add `mail.debug=true` to `application.yml`, secrets stay out of logs.
- The retry-trace in `RetryTemplate` logs only the exception class and attempt number (Spring Retry default), no exception message — consistent with 023.

**Alternatives considered**:
- *Active redaction filter for the `Authorization`-shaped SMTP AUTH frames*: rejected as overkill. The frames never reach our code; Jakarta Mail handles them internally.
- *Emitting a separate "credentials present" / "credentials absent" startup log line*: rejected because it doubles the surface area for accidental disclosure ("credentials present, length=24" is a leaked secret-length hint).

---

## R6 — Operator runbook contents (R-by-R cross-walk of FR-2712)

**Question**: What goes into `quickstart.md` to satisfy FR-2712 (a)–(f)?

**Decision**: Six-section runbook organized to mirror the operator's chronological workflow:

1. **Provision the mailbox** — purchase / configure a PrivateEmail mailbox in Namecheap. Out-of-scope for the app, but called out as prerequisite #1 (matches FR-2712 (a) — "lists the env vars" implies the operator has already obtained a mailbox).
2. **Wire the three Terraform variables** — names + types per R4 above. (FR-2712 (a))
3. **Default connection parameters that ship in the app** — table reproducing R1 (so operators can sanity-check against PrivateEmail's docs). (FR-2712 (b))
4. **From-alignment rule** — single-paragraph callout: `var.email_from` MUST equal `var.smtp_username` or a configured alias; PrivateEmail rejects mis-aligned From at SMTP `MAIL FROM` time and the user sees the retryable-failure alert. (FR-2712 (c))
5. **Smoke-test recipe** — `./gradlew :backend:integrationTest --tests AlterEgoEmailControllerIT` for the local check; for a real PrivateEmail handshake, the runbook gives a `curl` recipe against a running backend pointed at a real mailbox + the operator's own recipient address. (FR-2712 (d))
6. **Operational caveats** — restart required after credential change (FR-2712 (e) + FR-2708); outbound TCP/587 must be reachable from the deployment network (FR-2712 (f)); do NOT enable `mail.debug=true` in any profile (R5).

**Rationale**:
- The runbook is operator-facing, not developer-facing — terse, ordered chronologically, and avoids application-internal language ("`EmailConfigured` returns `false`") in favour of operator-observable outcomes ("Send-as-Email click produces the 'not yet configured' alert").

**Alternatives considered**:
- *Combine runbook into the main `README.md`*: rejected. SpecKit convention places feature-local runbooks in the feature folder. The 023 `quickstart.md` already lives at `specs/023-email-send-image/quickstart.md`; 027's runbook supersedes that for the PrivateEmail-default deployment shape.

---

## Phase 0 result

All `NEEDS CLARIFICATION` resolved. No remaining open questions blocking Phase 1.

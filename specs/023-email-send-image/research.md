# Phase 0 Research — Send Generated Alter Ego By Email (023)

Each entry: **Decision → Rationale → Alternatives considered**. Drives the
contract surface and the test plan; consumed by Phase 1 design and by
`/speckit.tasks`.

---

## R1 — Mail seam: `spring-boot-starter-mail` (Jakarta Mail)

**Decision**: Add `org.springframework.boot:spring-boot-starter-mail` to
`backend/build.gradle.kts`. Compose and send via the auto-configured
`JavaMailSender` bean. Do NOT introduce a third-party SDK
(SendGrid/Mailgun/Postmark/AWS SES SDK).

**Rationale**:
- The starter is Spring-managed (version pinned by the existing Spring Boot
  3.x BOM), tracks Jakarta Mail's current major, and has no known
  HIGH/CRITICAL CVE at plan time → satisfies Principle I.
- Spec FR-2311 / FR-2318 require evaluating "configured" at startup; Spring
  Boot's `MailSenderAutoConfiguration` already keys on
  `spring.mail.host` non-blank → no bean is created when blank. We piggyback
  on that behaviour and avoid a custom factory.
- Mailing-service provisioning is explicitly out of scope (issue #57). A
  protocol-level seam (SMTP via JavaMailSender) keeps the integration future
  vendor-neutral: any operator-supplied SMTP relay (Postfix, MailHog,
  Gmail SMTP, SES SMTP endpoint, etc.) is configured the same way.
- Constitution Principle III's "production-profile wiring MUST NOT be mocked
  away" applies to the DB. For the SMTP socket, mocking `JavaMailSender` at
  the integration boundary is correct — the production seam (controller →
  service → JavaMailSender) is exercised end-to-end; only the actual TCP
  send is stubbed.

**Alternatives considered**:
- *Third-party SDK (SendGrid, Mailgun, Postmark)*: rejected — adds an SDK
  dependency, ties the project to one vendor, and is overkill for a POC
  that does not provision a mail service. Also each of those SDKs ships
  CVE history of its own that we'd have to track.
- *Hand-rolled SMTP client over `java.net.Socket`*: rejected — re-inventing
  Jakarta Mail, no value.
- *`org.simplejavamail:simple-java-mail`*: maintained and ergonomic, but a
  third-party dependency where the Spring-official starter already covers
  100 % of our needs. Not worth the dep audit churn (Principle VI).

---

## R2 — "Configured at startup" semantics

**Decision**: At backend startup, `EmailConfigured` (a `@Component` with
an `@PostConstruct` initialiser) reads `spring.mail.host` from the
environment and caches `host != null && !host.isBlank()` as a final
boolean. The service consults that boolean before any `JavaMailSender`
call. The check is one-shot — no live SMTP probe at startup.

**Rationale**:
- Spec clarification Q3 → config-presence only; no network call at
  startup. `spring.mail.host` is the canonical Spring property that
  drives `MailSenderAutoConfiguration` itself, so reading the SAME
  property keeps the FE-visible state aligned with whether a
  `JavaMailSender` bean even exists.
- A one-shot check at `@PostConstruct` is deterministic, has no
  startup-latency cost, and matches FR-2318's "drive the button's
  behaviour for the entire lifetime of that running application
  instance".
- The cached boolean is `final` — there is no in-flight re-evaluation
  on config-file change, restart required (spec assumption locked).

**Alternatives considered**:
- *Live SMTP probe at startup*: rejected per clarification Q3 — adds
  startup latency, introduces a "configured but unreachable" failure
  class at startup that complicates the UX surface.
- *Per-click re-evaluation*: rejected — contradicts FR-2318's lifetime
  semantics and would race with operator-restart guarantees.
- *Introduce a separate `aiavatar.email.enabled` flag*: rejected —
  duplicates `spring.mail.host`'s natural absence-as-disable semantics
  and creates a redundant footgun (host set + flag false).

---

## R3 — FE→BE wire shape for the image payload

**Decision**: `POST /api/v1/alter-egos/email` with `multipart/form-data`
body, parts: `to` (text), `firstName` (text), `image` (binary,
`image/png` or `image/jpeg`). The FE decodes `session.result.poster.dataUrl`
to a `Blob` via a tiny `dataUrlToBlob` helper before appending it to
the `FormData`.

**Rationale**:
- The existing Generate endpoint (`POST /api/v1/alter-egos`) already
  uses multipart with a binary image part — Reusing the same pattern
  keeps both endpoints stylistically aligned and lets us reuse the
  existing `ACCEPTED_PHOTO_MIMES` allow-list (003 wiring).
- Multipart with a binary part is ~33 % smaller on the wire than
  base64 inside JSON. For a typical 500 KB poster that's ~125 KB saved
  per Send click.
- `MimeMessage` composition on the backend only needs raw bytes + a
  content type — multipart hands us exactly that.

**Alternatives considered**:
- *JSON body with `image` as a base64 data URL*: rejected — 33 %
  payload bloat, plus we'd be re-parsing the data URL on the server
  to recover content-type + bytes.
- *Use the dataUrl directly inside the message body*: rejected —
  some MUAs strip data URLs from `text/html` bodies, and we want the
  image as a `MIME` attachment so it appears as "attached image" in
  every common email client.

---

## R4 — Email body: plain-text only

**Decision**: The `MimeMessage` body is set as `text/plain;
charset=UTF-8`. The exact byte sequence is what FR-2314 specifies,
with `{firstName}` substituted at composition time. No HTML body, no
multipart-alternative `text/plain` + `text/html`.

**Rationale**:
- FR-2314 specifies the body verbatim, including line breaks. A
  plain-text body preserves the exact rendering across every MUA.
- Adding HTML would force a choice between (a) escaping `{firstName}`
  (which would surprise on names containing `<` / `&` — though our
  `@ValidFirstName` rules from 011 already forbid `<`, the principle
  remains), or (b) shipping a templating engine for one message. Both
  are over-engineering.
- The attached image is a separate MIME part; the body's job is the
  greeting text.

**Alternatives considered**:
- *HTML body*: rejected — see above.
- *multipart/alternative with text + html*: rejected — same complexity
  cost; no recipient-experience benefit for a single-paragraph greeting.

---

## R5 — PII redaction in logs

**Decision**: `AlterEgoEmailService` MUST NOT put `to`, the message
body, the attachment bytes, or the substituted first name into MDC
or into Logstash structured args. The service may log a single
`event=email.send.completed outcome=sent` (or `outcome=failed reason=...`)
with `correlationId` only — no recipient, no body, no image.

**Rationale**:
- Spec FR-2320: "MUST NOT persist the recipient email, the email
  body, the subject, the attached image bytes, or any metadata about
  the send (recipient, timestamp, success/failure) to disk, database,
  cache, or logs".
- The "or logs" clause is the binding one here. SC-2306 verifies it.
- The existing `PhotoRedactionFilter` (003) demonstrates the pattern —
  but its redaction works by stripping `photo*`-named structured
  args, which only helps if the args ever exist. Cleaner approach:
  don't put PII into structured args at all. Compose locally as
  method-local variables that never leak past the method scope.

**Alternatives considered**:
- *Mirror `PhotoRedactionFilter` and write an `EmailRedactionFilter`*:
  rejected — defence in depth that fights the wrong threat. The
  primitive solution (don't log it) is both simpler and stricter.
- *Log a SHA-256 hash of the recipient address*: rejected — still PII
  under most data-protection regimes, and serves no diagnostic
  purpose because there's no second-system to correlate against.

---

## R6 — Generate / Surprise Me gating on email validity

**Decision**: Extend `selectors.ts`:
- Add an `emailValidity(state)` predicate that returns `'valid' |
  'invalid'` (`'valid'` for blank, `'valid'` for well-formed,
  `'invalid'` only for non-blank + malformed).
- Add `'email'` to the `RequiredInput` taxonomy ONLY when email is
  invalid. Blank email contributes nothing to `missingInputs`.
- `isReadyToGenerate` and `isReadyToSurprise` both incorporate
  `emailValidity(state) === 'valid'` in their guard.

**Rationale**:
- Spec clarification on locked-in assumption: "Invalid email blocks
  both Generate and Surprise Me". Modelling email as an entry in
  `RequiredInput` that is only "missing" when malformed keeps the
  existing missing-input hint UX (FirstNameInput inline error)
  consistent across all inputs.
- The blank-is-fine semantics fall out naturally: `missingInputs`
  doesn't see blank email, so Generate stays enabled.

**Alternatives considered**:
- *Carry a separate `emailInvalid` flag outside `RequiredInput`*:
  rejected — duplicates state machinery and forces a parallel
  hint-rendering path.
- *Block Send-As-Email only (let Generate proceed with a malformed
  email)*: rejected — would let the session commit an unusable
  address that the Alter Ego tab then has to grapple with.

---

## R7 — Email-format validation: pragmatic regex + Jakarta `@Email`

**Decision**:
- FE validator (`frontend/src/features/alterego/validation/email.ts`):
  `/^[^\s@]+@[^\s@]+\.[^\s@]+$/` applied to the **trimmed**, normalised
  value. Length cap 254 chars. Whitespace-only → blank.
- BE validator: Jakarta Bean Validation `@Email` on `to` plus
  `@NotBlank @Size(max = 254)`. Jakarta's default `@Email` uses the
  Hibernate Validator pragmatic regex (close enough to RFC 5321 for a
  POC).

**Rationale**:
- The two regexes don't have to be byte-identical because the FE
  pre-gates the Send button — invalid values never reach the
  backend on the happy path. The BE check is defence in depth.
- The pragmatic regex matches typical human-typed addresses and
  rejects the obvious bad ones (`a`, `a@`, `a@b`) — exactly what
  spec FR-2303 requires ("standard email format").
- 254 chars is the practical RFC 5321 cap for `<localpart@domain>`
  envelope addressing; bigger doesn't help in real-world SMTP.

**Alternatives considered**:
- *Full RFC 5322 grammar*: rejected — over-spec for a participant
  filling out an event form. Hibernate's Jakarta `@Email` is what
  the rest of Spring Boot ecosystems treat as canonical.
- *Browser `<input type="email">` validation only*: rejected —
  inconsistent across browsers and bypassable. We still set
  `type="email"` for keyboard hinting but enforce with our own
  validator.

---

## R8 — `<EmailInput/>` vs. extracting a shared `<ClearXInput/>`

**Decision**: Duplicate (don't extract). `EmailInput.tsx` is a
near-clone of `CustomRoleInput.tsx` (different label, different
validation, different `type` attribute, different `maxLength`). At
two callers, the Rule of Three has not fired — extraction is
premature.

**Rationale**:
- Three similar lines is better than a premature abstraction
  (CLAUDE.md guidance). Two clear-X inputs is "three lines"-scale.
- The two inputs diverge on enough axes (validation, inline-error
  message, accent CSS) that a shared component would either expose
  10+ props or absorb half of each caller's logic anyway.
- If a third clear-X input lands later, the extraction will be
  obvious and cheap from three real call sites — not two guessed
  ones.

**Alternatives considered**:
- *Extract `<ClearXInput/>` now*: rejected — see above. Re-evaluate
  at the third caller.
- *Share only the CSS module*: this is fine and actually likely the
  best small step. The plan lists a CSS rename (`.clear-x-input`)
  but keeps the React components separate.

---

## R9 — `SendAsEmailButton` reads the current poster bytes

**Decision**: `SendAsEmailButton` is rendered inside `AlterEgoPanel`
(the same branch that renders `PrintButton` + `PosterView` +
`PrintArtefact`). The branch already has the `session.result.poster`
in scope — pass `result.poster` + `session.email` + `session.firstName`
down to the button as plain props.

**Rationale**:
- `AlterEgoPanel` gates rendering on
  `(phase === 'succeeded' || phase === 'failed_with_fallback') &&
  session.result` — exactly the precondition the button needs.
- Passing the result via props means the button is testable in
  isolation (no ref forwarding, no context).
- The `dataUrl` is what the FE already has; converting to a `Blob`
  for the multipart body happens inside the mutation hook.

**Alternatives considered**:
- *Use a ref forwarded from `PosterView` to capture the rendered
  `<img>` element and serialise it via canvas*: rejected — adds
  canvas + tainting concerns, and we don't need it: the dataUrl IS
  the bytes we want.
- *Add a new selector / context for "current poster"*: rejected —
  the panel already has it scoped.

---

## R10 — Retry policy for SMTP send

**Decision**: Wrap the `JavaMailSender.send(MimeMessage)` call in the
existing project-wide `RetryTemplate` (configured by
`RetryConfig.java`: 5 attempts, exponential backoff with jitter).
Final failure is translated into `502 Bad Gateway` so the FE's
retryable-alert path fires.

**Rationale**:
- Constitution Principle IV: "Every HTTP call to a backend or
  third-party API MUST: implement a retry policy …". SMTP is
  protocol-adjacent — the same posture is the right default for
  consistency.
- Most SMTP failures are transient (connection-reset, temporary
  rejection 4xx). 5 attempts with backoff covers the common case.
- Final failure surfaces to the user as a retryable alert + the
  button remains clickable; the FE never enters a stuck state.

**Alternatives considered**:
- *One-shot send, no retry*: rejected — Principle IV.
- *Separate, more aggressive retry profile for mail*: rejected —
  introducing a second RetryTemplate is dependency creep; the
  existing template's profile (5/exp/jitter) is well-tuned for
  intermittent network failures generally.

---

## R11 — 503 problem-detail type for "not configured"

**Decision**: `EmailNotConfiguredException` translates to RFC 7807
`503 Service Unavailable` with `type =
"https://aiavatar.local/problems/email/not-configured"` (or whatever
stable URI the existing `ProblemDetailAdvice` uses for typed
problems). The FE classifies on `status === 503` AND
`problemDetail.type` ending in `/email/not-configured`.

**Rationale**:
- 503 communicates "service temporarily unavailable" — appropriate
  for "feature is not provisioned" without being a 4xx (the client
  did nothing wrong).
- A stable `type` URI lets the FE classify deterministically; the
  human-readable `title` / `detail` strings can change without
  breaking FE logic.
- The existing `ProblemDetailAdvice` already produces RFC 7807
  problem details — extending it is one extra `@ExceptionHandler`.

**Alternatives considered**:
- *Empty 200 with a body flag (e.g., `{status:"not_configured"}`)*:
  rejected — using a 2xx for a non-success outcome muddies the
  semantics; FE classification on `status === 200` becomes
  ambiguous.
- *412 Precondition Failed*: rejected — "the precondition" here is
  operator config, not client-supplied state. Semantically wrong.

---

## R12 — No FE-side `GET /email/config` at app boot

**Decision**: The frontend does NOT call any "is mail configured?"
endpoint on boot. The button is always visible; its disabled state
is driven by the email field's blank/invalid state ONLY. The
not-configured outcome is discovered on click via the typed 503.

**Rationale**:
- The spec assumption (Q1 from clarify) locks the FE-visible disabled
  state to "email is blank or invalid" — NOT to mail-server
  configuration. Server-config state is server-side; surfacing it
  client-side would require a second round trip + a stale-state risk
  if the operator restarts the backend.
- The typed 503 from the existing seam already handles the
  not-configured path. A separate `/email/config` endpoint would
  duplicate that signalling.
- Saves a round trip and removes a startup-order dependency.

**Alternatives considered**:
- *Probe `/email/config` at app boot*: rejected — see above. If a
  future feature genuinely needs the FE to know about backend
  capabilities (e.g., to hide the button entirely instead of showing
  the alert), that's a separate decision.

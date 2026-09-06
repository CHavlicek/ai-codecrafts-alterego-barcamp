# Implementation Plan: Send Generated Alter Ego By Email

**Branch**: `023-email-send-image` | **Date**: 2026-05-11 | **Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `/specs/023-email-send-image/spec.md`

## Summary

Two coordinated additions:

1. **Setup tab — optional `Email` field** between the category-selection block and the first-name input. Standard email-format validation only when non-blank; a trailing clear-X (visually + behaviourally identical to 022's `CustomRoleInput` clear-X). Whitespace-trimmed before validation. Retained in the reducer across Generate / Surprise Me / tab switches; cleared by `StartOverRequested`. Generate and Surprise Me gate on email *validity* (blank → valid, malformed → invalid).
2. **Alter Ego tab — "Send As Email" action button** in the existing actions row, to the right of `PrintButton`. Disabled (mirroring Generate's gating: `aria-disabled` + reduced-opacity + tooltip) when the captured email is blank or invalid. When enabled and clicked, the frontend POSTs a multipart body (`to`, `firstName`, `image` bytes) to a new `POST /api/v1/alter-egos/email` endpoint. The backend evaluates "mail server configured" **once at startup** as a configuration-presence check on `spring.mail.host` and returns:
   - `200 + {status: "sent"}` → FE shows a native `alert()` success popup
   - `503 + Problem Detail (type = /problems/email/not-configured)` → FE shows the "email server is not yet configured" alert
   - Any other 4xx/5xx → FE shows the retryable-failure alert
   The same `alert()` primitive drives all three outcomes — **no new toast/banner/snackbar system is introduced**.

Technical approach: wire `spring-boot-starter-mail` (Jakarta Mail) but do not provision SMTP — `spring.mail.host` is left blank by default so the auto-configuration creates no `JavaMailSender` bean and `EmailConfigured.isConfigured()` returns `false`. The `AlterEgoEmailController` always exists (so the FE has a stable seam to target); the service path checks `isConfigured()` first and short-circuits to a typed 503 before any `JavaMailSender` lookup. When `spring.mail.host` IS set, the same `JavaMailSender` Spring auto-configures composes and sends the MIME message; transient send failures are wrapped in the existing `RetryTemplate` (Principle IV) and surface as 502 to the FE.

No persistence anywhere. Recipient address and message body are never logged (mirrors `PhotoRedactionFilter`'s posture for photo bytes). No new toast/banner system on the FE — the issue's `alert()` primitive carries every outcome.

## Technical Context

**Language/Version**: TypeScript 5.x (strict) on the frontend; Java 21 (LTS) on the backend. Unchanged from 002 / 006 / 016 / 020 / 021 / 022.
**Primary Dependencies**: React 19 + Vite 8 + TanStack Query v5 + Vitest + React Testing Library + Playwright (frontend); Spring Boot 3.x + Jakarta Bean Validation + JUnit 5 + Spring Boot Test + Mockito (backend); **one new backend dependency**: `org.springframework.boot:spring-boot-starter-mail` (Spring Boot–managed; pulls in `jakarta.mail-api` + `org.eclipse.angus:jakarta.mail` implementation — both maintained, no known CVEs at time of plan). No new frontend dependency. The `X` icon for the clear-X is already re-exported from `frontend/src/features/alterego/options.ts:113`.
**Storage**: N/A. Inherits 001 FR-016 / FR-017 / FR-024 — no persistence. The recipient email lives in browser session state for one session; the composed `MimeMessage` lives on the request thread for the duration of one SMTP `send()` call; the image bytes accompany the request body and are not retained. Recipient address, body, subject, attached image bytes, and send-metadata (timestamp, success/failure) are not logged or cached.
**Testing**: Vitest + RTL for the new `EmailInput` component, the `SendAsEmailButton` component, the email validator, the reducer's `EmailChanged` branch, and the gating selector. JUnit 5 + Spring Boot Test for the new `EmailController` integration test (200 / 503 / 502 paths), the `AlterEgoEmailService` unit test (composes + sends correctly; redacts logs), the `EmailConfigured` startup-presence test, and the `SendAlterEgoEmailRequest` Bean-Validation test. One Playwright happy-path spec exercises (a) typing a valid email, (b) generating, (c) clicking Send As Email with a mocked-200 backend and observing the success alert. `JavaMailSender` is mocked at the integration-test boundary (per constitution III, mocking external SMTP is the right call — production-profile wiring of the controller, service, and `EmailConfigured` is exercised end-to-end; only the actual SMTP socket is stubbed).
**Target Platform**: Modern evergreen browsers (Chromium ≥ 110, Firefox ≥ 110, Safari ≥ 16) for the frontend; Linux JDK 21 server for the backend. Unchanged.
**Project Type**: Web application — two-tier (`frontend/` React/TS + `backend/` Java Spring Boot) per CLAUDE.md.
**Performance Goals**: From button click to outbound SMTP `send()` invocation: ≤ 5 s wall-clock at the controller boundary excluding upstream SMTP latency (spec SC-2304). User-facing feedback (success or retryable-failure alert) within the same envelope. `EmailInput` keystroke handling stays ≤ 16 ms per change — pure local state + cheap regex.
**Constraints**: Email field ≤ 254 chars (RFC 5321 practical cap; FE input `maxLength="254"`); whitespace trimmed before validation and before being used as the recipient. The frontend MUST omit the field from no outbound contract (it does not travel on the existing Generate `/api/v1/alter-egos` multipart at all — it travels only on the new `/email` endpoint when the user clicks Send). Recipient address + body + image bytes MUST NOT appear in any log line, MDC entry, or cached HTTP response. The `alert()` primitive is the only feedback channel for the Send action — no toast/banner is introduced.
**Scale/Scope**: Same single-tenant POC posture as 001–022. One email send per click; the button is repeatable within a session but the spec does NOT introduce throttling (a participant clicking Send three times legitimately produces three emails — FR-2317).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Compliance | Notes |
|---|---|---|
| I. Modern & Secure Stack | ✅ | Adds `spring-boot-starter-mail` only — Spring-official, version-managed by the existing Spring Boot 3.x BOM, transitive deps are Jakarta Mail API + Eclipse Angus Mail. No known HIGH/CRITICAL CVE at plan time. No deprecated package. `npm audit` is unaffected (no FE dep added). |
| III. Test-First (TDD + ≥ 90% coverage + integration tests) | ✅ | Every new module ships with a failing test first: `email.ts` validator, `EmailInput.tsx`, `SendAsEmailButton.tsx`, `reducer.ts` (EmailChanged branch), `selectors.ts` (gating), `AlterEgoEmailService`, `AlterEgoEmailController` integration test (`@SpringBootTest` with mocked `JavaMailSender`), `EmailConfigured` startup test, `SendAlterEgoEmailRequest` validation test. One Playwright E2E happy path. Coverage gate ≥ 90% MUST hold on every touched module. |
| IV. Resilient HTTP | ✅ | The new `POST /api/v1/alter-egos/email` is reached from the FE via the existing `resilientFetch` (5 retries, exponential backoff with jitter, FE-side fallback) — same wrapper the Generate path uses. On the backend, the SMTP `send()` call is wrapped in the existing `RetryTemplate` (5 attempts, exponential backoff). FE-side fallback for SMTP failure is the user-visible retryable alert + the persistent ability to click Send again — the FE never enters a broken state. |
| V. Feature Branch Workflow | ✅ | Branch `023-email-send-image` cut from `main` by SpecKit's `create-new-feature.sh`. Single PR with explicit human approval before merge. |
| VI. Zero Deprecated Dependencies | ✅ | `spring-boot-starter-mail` and its transitives are current, maintained, and CVE-clean. `./gradlew dependencyCheckAnalyze` will continue to pass; if a transitive flags HIGH/CRITICAL at PR time, that's a blocker per Principle VI and resolved before merge. |

**No violations. No Complexity Tracking entries needed.**

## Project Structure

### Documentation (this feature)

```text
specs/023-email-send-image/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── alter-egos-email.openapi.yaml   # New endpoint contract
├── checklists/
│   └── requirements.md  # Already created by /speckit.specify
└── tasks.md             # Created by /speckit.tasks (NOT by /speckit.plan)
```

### Source Code (repository root)

Only files actually touched by this feature are listed. Anything not listed below MUST be unchanged at PR time.

```text
frontend/src/features/alterego/
├── types.ts                                  # +export shape for send-email request/response (no change to AlterEgoResponse)
├── state/
│   ├── reducer.ts                            # +email: string field; +EmailChanged action; clears on StartOverRequested
│   └── selectors.ts                          # +emailValidity(state); gates Generate / Surprise Me on email validity (blank-ok, malformed-blocks)
├── validation/
│   ├── email.ts                              # NEW — pure validator: blank → ok; non-blank → format check (RFC-pragmatic regex)
│   └── email.test.ts                         # NEW — fixture-driven cases (valid, malformed, whitespace, length cap)
├── components/
│   ├── EmailInput.tsx                        # NEW — mirrors CustomRoleInput's shape; trailing X clear; inline error when invalid
│   ├── EmailInput.test.tsx                   # NEW
│   ├── SetupLayout.tsx                       # renders <EmailInput/> between the numbered category groups and <FirstNameInput/>
│   ├── SetupLayout.test.tsx                  # +position assertion + email-row presence
│   ├── SendAsEmailButton.tsx                 # NEW — disabled when email blank/invalid; click → useSendAlterEgoEmail mutation
│   ├── SendAsEmailButton.test.tsx            # NEW — disabled/enabled states, click triggers mutation, all three alert paths
│   └── AlterEgoPanel.tsx                     # renders <SendAsEmailButton/> in the actions row to the right of <PrintButton/>
├── hooks/
│   ├── useSendAlterEgoEmail.ts               # NEW — TanStack Query mutation; classifies response → success/not_configured/retryable
│   └── useSendAlterEgoEmail.test.tsx         # NEW
├── services/
│   ├── emailClient.ts                        # NEW — POSTs multipart {to, firstName, image} to /api/v1/alter-egos/email
│   └── emailClient.test.ts                   # NEW
└── lib/
    └── dataUrlToBlob.ts                      # NEW small helper if not already present — converts poster.dataUrl → Blob for multipart upload

backend/build.gradle.kts                      # + implementation("org.springframework.boot:spring-boot-starter-mail")
backend/src/main/resources/application.yml    # + spring.mail.* keys (host blank by default — feature ships unconfigured) + aiavatar.email.from / subject
backend/src/main/java/com/aiavatar/alterego/
├── config/
│   ├── EmailProperties.java                  # NEW — @ConfigurationProperties("aiavatar.email"); from-address + (optional) subject override
│   └── EmailConfigured.java                  # NEW — @PostConstruct evaluates spring.mail.host non-blank → cached boolean; consulted by service
├── controller/
│   └── AlterEgoEmailController.java          # NEW — POST /api/v1/alter-egos/email; multipart parts: to (text), firstName (text), image (file)
├── model/
│   ├── SendAlterEgoEmailRequest.java         # NEW — record (to: @NotBlank @Email, firstName: @NotBlank @ValidFirstName)
│   └── SendAlterEgoEmailResponse.java        # NEW — record (status: "sent")
└── service/
    └── email/
        ├── AlterEgoEmailService.java         # NEW — composes MimeMessage, sends via JavaMailSender, retried by RetryTemplate
        ├── AlterEgoEmailBodyBuilder.java     # NEW — produces the FR-2314 fixed body with {firstName} substitution
        └── EmailNotConfiguredException.java  # NEW — translates to 503 via ProblemDetailAdvice

backend/src/test/java/com/aiavatar/alterego/
├── controller/AlterEgoEmailControllerIntegrationTest.java   # NEW — 200 / 503 / 502 paths; @MockBean JavaMailSender
├── service/email/AlterEgoEmailServiceTest.java              # NEW — MIME composition, body substitution, log-redaction assertion
├── service/email/AlterEgoEmailBodyBuilderTest.java          # NEW — exact body string per FR-2314
├── config/EmailConfiguredTest.java                          # NEW — host-blank → unconfigured; host-set → configured
└── model/SendAlterEgoEmailRequestValidationTest.java        # NEW — Bean-Validation coverage for to + firstName

frontend/playwright-tests/  (or wherever the existing E2E lives)
└── e2e/email-send.e2e.ts                                    # NEW — happy path: enter email → generate → Send As Email → success alert
```

**Structure Decision**: Existing two-tier `frontend/` + `backend/` layout from 001 onwards. The new frontend pieces sit alongside existing `alterego/` modules; the new backend pieces split between `controller/` (existing), `model/` (existing), `service/email/` (new sub-package mirroring `service/frame/`, `service/text/`), and `config/` (existing). No new top-level directories.

## Phase 0 — Research

See [research.md](./research.md) for the resolved-decisions catalogue covering:

- **R1**: Why `spring-boot-starter-mail` (not a hand-rolled SMTP client, not a third-party SDK like SendGrid/Mailgun) is the right seam for a "wired but unprovisioned" mail server.
- **R2**: How `spring.mail.*` auto-configuration interacts with `EmailConfigured.isConfigured()` — and why a config-presence check on `spring.mail.host` is the canonical signal at startup.
- **R3**: Why the FE→BE wire shape for sending is a multipart body (`to`, `firstName`, `image`) and not data-URL-in-JSON.
- **R4**: Plain-text vs HTML email body — locked to plain-text per the exact byte sequence in FR-2314.
- **R5**: PII-redaction strategy: the recipient address and message body are never put into `MDC` or structured log args; the existing `PhotoRedactionFilter` posture is mirrored.
- **R6**: How Generate / Surprise Me gating absorbs the new "email validity" predicate without thrashing the existing `RequiredInput` taxonomy.
- **R7**: Email format validation — chosen pragmatic single-line regex on FE matched by Jakarta `@Email` on BE; alignment rationale.
- **R8**: Whether to extract a shared `<ClearXInput/>` primitive between `CustomRoleInput` and `EmailInput`. Decision: not yet — duplicate small, extract on the third caller.
- **R9**: How `SendAsEmailButton` reads the current poster bytes — via `AlterEgoPanel`'s already-available `session.result.poster` rather than introducing a new ref/context.
- **R10**: Retry policy for SMTP send — wrap `JavaMailSender.send()` in the existing project-wide `RetryTemplate` (5 attempts, exponential backoff, jitter) per Principle IV.
- **R11**: How the controller maps `EmailNotConfiguredException` → RFC 7807 `503` with a stable `type` URI (`/problems/email/not-configured`) so the FE can distinguish the not-configured alert from a generic retryable failure.
- **R12**: Why no FE-side `GET /api/v1/email/config` ping at app boot — the existing `POST /email` request/response already discriminates not-configured vs. transient failure via the typed problem detail, so a separate config-probe endpoint is over-design.

## Phase 1 — Design & Contracts

### Entities

See [data-model.md](./data-model.md) for the full entity catalogue.

Summary:
- `AlterEgoSession.email` — session-scoped optional string. Empty initial, never null. Lifetime: browser session; cleared by `StartOverRequested`.
- `SendAlterEgoEmailRequest` — multipart body wrapper. Parts: `to` (validated `@Email`, ≤ 254 chars after trim), `firstName` (validated as the existing `@ValidFirstName`), `image` (binary `image/png` or `image/jpeg`).
- `SendAlterEgoEmailResponse` — `{ status: "sent" }` on 200.
- `EmailNotConfiguredProblem` — RFC 7807 detail returned with 503, `type = /problems/email/not-configured`.
- `EmailConfiguration` — backend startup-cached boolean (`spring.mail.host` non-blank → true) + a `from` address property.

### Wire contract

See [contracts/alter-egos-email.openapi.yaml](./contracts/alter-egos-email.openapi.yaml). Summary:

- `POST /api/v1/alter-egos/email`
- Request: `multipart/form-data`
  - `to`: text/plain — validated email address
  - `firstName`: text/plain — captured first name
  - `image`: `image/png` or `image/jpeg` — the currently-displayed generated poster bytes
- Response: `application/json`
  - `200` → `{ "status": "sent" }`
  - `400` → RFC 7807 problem detail (validation failure on `to` or `firstName`)
  - `415` → RFC 7807 problem detail (image MIME outside allow-list)
  - `503` → RFC 7807 problem detail, `type = /problems/email/not-configured` (mail server not configured at startup)
  - `502` → RFC 7807 problem detail (SMTP transient failure after RetryTemplate exhausts its attempts) — FE treats this as retryable
- The existing `X-Request-Id` correlation header is honoured for parity with the Generate endpoint.

### Quickstart

See [quickstart.md](./quickstart.md) for the local how-to:

- Run the backend with no `spring.mail.host` (default): clicking Send As Email on the FE produces the "not yet configured" alert.
- Run the backend with a local SMTP catcher (e.g., MailHog / GreenMail-as-a-process at `localhost:1025`): `spring.mail.host=localhost spring.mail.port=1025`. Clicking Send As Email produces the success alert; the message is visible in the catcher's web UI.
- Run the backend with an unreachable SMTP host (e.g., `spring.mail.host=10.255.255.1` to force a TCP timeout): clicking Send As Email produces the retryable-failure alert after `RetryTemplate` exhausts its attempts.

### Backwards compatibility

- The Setup tab gains one optional input. Clients that never enter an email continue to work unchanged — Generate / Surprise Me proceed as before.
- The Alter Ego tab gains one new action button. The existing Print path is unchanged; the existing actions-row layout absorbs the new sibling without breaking print-CSS rules.
- The new `POST /api/v1/alter-egos/email` endpoint is purely additive — no existing endpoint changes shape.
- Old backends (pre-023) that the FE might hit (e.g., during a partial rollout) would 404 on the new endpoint. The FE's `useSendAlterEgoEmail` classifies an unexpected 404 the same way as a transient failure — the retryable alert is shown. This is fail-safe but not silent.

### Constitution Check (post-design re-evaluation)

| Principle | Compliance | Notes |
|---|---|---|
| I. Modern & Secure Stack | ✅ | `spring-boot-starter-mail` is the only addition; Spring-managed version. |
| III. Test-First | ✅ | Test files in the structure tree above land first in commit order (see `/speckit.tasks` output). 90 %+ coverage gate, ≥ 1 integration test (the controller integration), 1 E2E (Playwright happy path). |
| IV. Resilient HTTP | ✅ | FE→BE call uses existing `resilientFetch` (5 retries, exponential backoff, FE-side fallback). BE→SMTP call uses existing `RetryTemplate`. The user-facing fallback is the retryable alert + the button remaining clickable. |
| V. Feature Branch Workflow | ✅ | Single PR from `023-email-send-image` → `main`. |
| VI. Zero Deprecated Deps | ✅ | `npm audit` + `./gradlew dependencyCheckAnalyze` continue to pass; new starter is current. |

**No violations introduced by the Phase 1 design. No Complexity Tracking entries.**

## Complexity Tracking

> Empty — no Constitution-Check violations to justify.

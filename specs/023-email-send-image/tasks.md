---
description: "Task list for 023-email-send-image"
---

# Tasks: Send Generated Alter Ego By Email

**Input**: Design documents from `/specs/023-email-send-image/`
**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/alter-egos-email.openapi.yaml](./contracts/alter-egos-email.openapi.yaml), [quickstart.md](./quickstart.md)

**Tests**: Mandatory per Principle III (Test-First Development, NON-NEGOTIABLE) — written before any implementation task in the same user story, MUST fail before production code is committed, ≥ 90% line coverage on every touched module, ≥ 1 integration test (the controller @SpringBootTest), ≥ 1 E2E (Playwright happy path).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2)
- File paths are absolute project-relative paths

## Path Conventions (this feature)

- Backend: `backend/src/main/java/com/aiavatar/alterego/...`; tests in `backend/src/test/java/com/aiavatar/alterego/...`; resources in `backend/src/main/resources/`.
- Frontend: feature lives under `frontend/src/features/alterego/...`; component/unit tests are co-located as `*.test.tsx` / `*.test.ts`; E2E tests under `frontend/tests/e2e/`; global CSS rules in `frontend/src/index.css` (the project uses a flat stylesheet — see existing `.custom-role-input` block at `frontend/src/index.css:572`).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Make the mail seam wire-able. No story can ship its backend work until the dep + default config are in place.

- [X] T001 [P] Add `implementation("org.springframework.boot:spring-boot-starter-mail")` to the `dependencies { ... }` block in `backend/build.gradle.kts`, then run `./gradlew --refresh-dependencies build -x test` to confirm the new transitive set resolves cleanly. No code change yet — the dep must land before any backend test/source under `service/email/` compiles.
- [X] T002 [P] Extend `backend/src/main/resources/application.yml` with two new blocks (place them near the existing `aiavatar.gemini` / `aiavatar.falai` blocks so all provider-shaped config stays adjacent):
  ```yaml
  spring:
    mail:
      host: ${SPRING_MAIL_HOST:}
      port: ${SPRING_MAIL_PORT:25}
      username: ${SPRING_MAIL_USERNAME:}
      password: ${SPRING_MAIL_PASSWORD:}
      protocol: ${SPRING_MAIL_PROTOCOL:smtp}
  aiavatar:
    email:
      from: ${AIAVATAR_EMAIL_FROM:alter-ego@codecrafts.local}
      subject: ${AIAVATAR_EMAIL_SUBJECT:Your AI Generated Alter Ego - CodeCrafts 2026}
  ```
  Leaving `spring.mail.host` blank by default keeps `MailSenderAutoConfiguration` inactive and `EmailConfigured.isConfigured()` returning `false` for the ships-as state (research R1 / R2). Add a top-of-block comment referencing 023 and the FR-2311 / FR-2318 semantics so future readers don't strip it.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Skipped.** This feature lays no new shared infrastructure outside the build-dep + config defaults already covered in Phase 1. Story phases below can begin immediately after Phase 1 completes.

---

## Phase 3: User Story 1 — Capture an optional recipient email on Setup (Priority: P1) 🎯 MVP slice 1

**Goal**: The Setup tab gains a new optional `Email` input between the category-selection block and the first-name input. Standard email-format validation only when non-blank; a trailing clear-X visually + behaviourally identical to `CustomRoleInput`'s. Whitespace-trimmed. Retained in the reducer across Generate / Surprise Me / tab switches. Invalid email blocks Generate AND Surprise Me; blank email blocks neither.

**Independent Test** (spec § "User Story 1 — Independent Test"): Open the Setup tab. Observe the field positioned between the numbered category groups and the first-name input. Type `someone@example.com` → X appears. Click X → field clears, X disappears. Type `not-an-email` → Generate and Surprise Me both disable with an inline format error; correct or clear → both re-enable. Type a valid email → Generate. After the Alter Ego tab opens, return to Setup — the typed email is still there.

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T003 [P] [US1] Create `frontend/src/features/alterego/validation/email.test.ts` with a fixture-driven grid: `''` → ok with `trimmed === ''`; `'someone@example.com'` → ok; `'  someone@example.com  '` → ok with trimmed equal to the inner value; `'a@b.c'` → ok; `'not-an-email'` → `{ok:false, code:'invalid_format'}`; `'a@b'` (no dot) → invalid; `'@b.c'` / `'a@.c'` / `'a@b.'` → invalid; whitespace-only → ok with `trimmed === ''`; 255-char address → `{ok:false, code:'too_long'}`; 254-char valid address → ok.
- [X] T004 [P] [US1] Create `frontend/src/features/alterego/components/EmailInput.test.tsx` with RTL: renders a single `<input type="email">`; when value is blank the clear-X is NOT in the DOM; when value is non-blank the clear-X IS visible and clicking it fires `onChange('')`; when value fails validation an inline error message is rendered beneath the field with `role="alert"`; the input applies `maxLength={254}`; `aria-label` / `id` wiring is correct for label association.
- [X] T005 [P] [US1] Extend `frontend/src/features/alterego/state/reducer.test.ts` with: `initialAlterEgoSession().email === ''`; `EmailChanged` with `'a@b.c'` → state.email === `'a@b.c'`; `EmailChanged` does NOT change `phase` (an email edit isn't a generation transition); `StartOverRequested` resets `email` to `''`; `SurpriseMePicked` does NOT clear `email` (FR-2308 — the captured address must survive Surprise Me).
- [X] T006 [P] [US1] Extend `frontend/src/features/alterego/state/selectors.test.ts` with: `emailValidity()` returns `'valid'` for blank, `'valid'` for well-formed, `'invalid'` for malformed; `missingInputs()` adds `'email'` ONLY when invalid (blank is never missing); `isReadyToGenerate` is false when `email` is non-blank-and-malformed but otherwise unchanged from today; `isReadyToSurprise` mirrors the same gating; the invariant `isReadyToGenerate(s) ⇒ isReadyToSurprise(s)` still holds for every fixture.
- [X] T007 [P] [US1] Extend `frontend/src/features/alterego/components/SetupLayout.test.tsx` with a positional assertion: rendered DOM order in the right-hand `.setup-layout__selections` column is `<ArchetypeGrid/>` → `<CustomRoleInput/>` → `<UniverseGrid/>` → `<ArtStyleGrid/>` → `<EmailInput/>` → `<FirstNameInput/>` → `.setup-layout__actions`. The Email input lives between the last numbered category group and the first-name input — both in DOM order and visually.

### Implementation for User Story 1

- [X] T008 [P] [US1] Create `frontend/src/features/alterego/validation/email.ts` exporting `EmailValidation = { ok: true; trimmed: string } | { ok: false; code: 'invalid_format' | 'too_long' }`, `validateEmail(value: string): EmailValidation`, and `EMAIL_ERROR_MESSAGE: Record<EmailErrorCode, string>` for the two FR-2305 inline strings. Apply NFC normalisation + trim before checking; pragmatic regex `/^[^\s@]+@[^\s@]+\.[^\s@]+$/` (research R7); length cap 254 chars on the trimmed value.
- [X] T009 [US1] Extend `frontend/src/features/alterego/state/reducer.ts`: (a) add `email: string` to `AlterEgoSession` with initial value `''` in `initialAlterEgoSession()`; (b) add `{ type: 'EmailChanged'; email: string }` to the `AlterEgoAction` union; (c) add a `case 'EmailChanged'` branch that overwrites `state.email` WITHOUT changing `phase`; (d) the existing `StartOverRequested` branch already routes through `initialAlterEgoSession()` — no extra code, but verify it picks up the new field. **Do NOT touch** `SurpriseMePicked` or `GenerateSubmitted` — `email` must survive both (FR-2308).
- [X] T010 [US1] Extend `frontend/src/features/alterego/state/selectors.ts` (depends on T009): (a) export `emailValidity(state): 'valid' | 'invalid'` returning `'invalid'` only when `state.email.trim() !== ''` AND `validateEmail(state.email).ok === false`; (b) add `'email'` to the `RequiredInput` union; (c) extend `missingInputs(state)` to push `'email'` only when `emailValidity(state) === 'invalid'` — preserve the existing canonical ordering (insert `'email'` at the position that mirrors its visual position on Setup: between `'artStyle'` and `'firstName'`); (d) extend `isReadyToSurprise(state)` to also require `emailValidity(state) === 'valid'`.
- [X] T011 [P] [US1] Create `frontend/src/features/alterego/components/EmailInput.tsx` mirroring `CustomRoleInput.tsx`'s structure: controlled `value` + `onChange`, optional `disabled`, `<label>` text `"Email"`, `<input type="email" inputMode="email" autoComplete="email" maxLength={254} spellCheck={false}>`, trailing clear-X that renders only when `value.length > 0` and dispatches `onChange('')`. When `validateEmail(value)` reports a failure AND the value is non-blank, render the matching `EMAIL_ERROR_MESSAGE` text in a sibling `<p role="alert" className="email-input__error">`. Reuse the same `X` icon import that `CustomRoleInput` uses (`../options`).
- [X] T012 [US1] Update `frontend/src/features/alterego/components/SetupLayout.tsx` (depends on T011) to render `<EmailInput value={session.email} onChange={(email) => dispatch({ type: 'EmailChanged', email })} disabled={isSubmitting} />` between the closing of the last `.setup-layout__numbered-group` (the Art-Style group) and the existing `<FirstNameInput />`. Do not introduce a new numbered-group wrapper — the field is not a category, it is metadata at the same hierarchical level as first-name. Add a short JSDoc comment referencing 023 / FR-2301.
- [X] T013 [US1] Append a `.email-input` rule block in `frontend/src/index.css` modelled on the existing `.custom-role-input` rules (lines 572+). Reuse spacing, label typography, control border styling, clear-X hit-area sizing, and the `:focus-visible` ring colour so the two clear-X inputs are visually indistinguishable. Add a `.email-input__error` rule scoped to the inline-validation paragraph (small font, accent-error colour, top margin 4 px).

**Checkpoint**: User Story 1 fully functional. The Email field renders, validates, retains across tabs / Surprise Me, and gates Generate + Surprise Me correctly. No backend work yet — the field is purely Setup-tab state. MVP slice 1 is shippable as a standalone PR if desired, but Story 2 is also P1 and required by the feature's value proposition.

---

## Phase 4: User Story 2 — Send the generated image to the captured email (Priority: P1) 🎯 MVP slice 2

**Goal**: A new "Send As Email" button appears on the Alter Ego tab to the right of the Print button. It is disabled (with `aria-disabled` + reduced-opacity + tooltip) when the captured email is blank or invalid (clarification 2026-05-11 Q1). When enabled and clicked, the FE POSTs `{to, firstName, image}` to a new `POST /api/v1/alter-egos/email`. The backend evaluates `spring.mail.host` non-blank at startup; if false → 503 with `type=/problems/email/not-configured`; if true → composes a MIME message using the FR-2313 subject and FR-2314 body, attaches the poster bytes, and dispatches via `JavaMailSender` wrapped in the existing `RetryTemplate`. The FE renders one native `alert()` per terminal outcome: `'Email sent to {to}.'` / `'The email server is not yet configured.'` / `'Sending failed. Please try again.'` (clarification Q2).

**Independent Test** (spec § "User Story 2 — Independent Test"): With Story 1 already shipped and a valid email on Setup, generate a poster. Observe the Send As Email button to the right of Print. Configure the backend with `SPRING_MAIL_HOST=localhost SPRING_MAIL_PORT=1025` against MailHog → click → observe success alert + email in MailHog with the exact FR-2313 / FR-2314 / attachment shape. Re-run with `SPRING_MAIL_HOST` unset → click → observe "not yet configured" alert and zero outbound SMTP traffic. Re-run with `SPRING_MAIL_HOST=10.255.255.1` → click → after RetryTemplate exhausts, observe "Sending failed. Please try again." alert. After any of the three, click again — the action re-fires (FR-2317).

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

#### Backend tests

- [X] T014 [P] [US2] Create `backend/src/test/java/com/aiavatar/alterego/controller/AlterEgoEmailControllerIntegrationTest.java`. `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `MockMvc` + `@MockBean JavaMailSender mailSender`. Four cases: (a) configured + valid input → 200 + `{"status":"sent"}`, `mailSender.send(MimeMessage)` called once with subject = FR-2313 string and body = FR-2314 string (with `{firstName}` substituted), recipient = the trimmed `to` part, attachment content-type matching the multipart image part; (b) `spring.mail.host=""` profile → 503 + problem detail with `type` ending in `/email/not-configured`, `mailSender` never called; (c) `mailSender.send(...)` throws `MailSendException` 5 times → after RetryTemplate exhausts → 502 + problem detail; (d) malformed `to` ("not-an-email") → 400 + problem detail with field name. Honour the existing `X-Request-Id` correlation header.
- [X] T015 [P] [US2] Create `backend/src/test/java/com/aiavatar/alterego/service/email/AlterEgoEmailServiceTest.java` (pure JUnit + Mockito, no Spring context). With `EmailConfigured.isConfigured() == false`, the service throws `EmailNotConfiguredException` BEFORE touching `JavaMailSender` (verify `mailSender` mock is never invoked). With `isConfigured() == true`, it composes a `MimeMessage` with exact subject + body + attachment (assertion via `ArgumentCaptor<MimeMessage>`), then calls `mailSender.send(message)`. Add a log-capture assertion (using Logback's `ListAppender`) that the structured log line contains `event=email.send.completed`, `outcome=sent`, `correlationId=...` but does NOT contain the recipient string, the body string, or the first name (R5 PII redaction).
- [X] T016 [P] [US2] Create `backend/src/test/java/com/aiavatar/alterego/service/email/AlterEgoEmailBodyBuilderTest.java`. Pin the exact FR-2314 byte sequence with `{firstName}` substituted: a single test case with `firstName = "Dmytro"` asserts byte-equality (UTF-8) against the multi-line string literal. A second case asserts that an empty / whitespace first name is rejected by the builder's precondition (defensive — the controller's `@NotBlank` should have caught it, but the builder defends in depth).
- [X] T017 [P] [US2] Create `backend/src/test/java/com/aiavatar/alterego/config/EmailConfiguredTest.java` covering the two startup states: with `spring.mail.host=""` (or absent) the cached boolean is `false`; with `spring.mail.host=smtp.test` it is `true`. Use `ApplicationContextRunner` to spin two minimal contexts with different `spring.mail.host` properties.
- [X] T018 [P] [US2] Create `backend/src/test/java/com/aiavatar/alterego/model/SendAlterEgoEmailRequestValidationTest.java`. Build a `Validator` via `Validation.buildDefaultValidatorFactory()` and run a fixture grid against the record: `to` empty → violates `@NotBlank`; `to = "not-an-email"` → violates `@Email`; `to` length > 254 → violates `@Size`; `firstName` empty → violates `@NotBlank`; `firstName` with an instruction-shaped phrase → violates `@ValidFirstName` (reuse the existing 011 fixtures); the happy-path tuple → zero violations.

#### Frontend tests

- [X] T019 [P] [US2] Create `frontend/src/features/alterego/services/emailClient.test.ts`. Stub `fetch`. Verify the request: method POST, URL `/api/v1/alter-egos/email`, body is `FormData` with three parts named `to` / `firstName` / `image` (assert via FormData iteration), `X-Request-Id` header carries the supplied correlationId, no `Content-Type` header is explicitly set (browser fills the multipart boundary). Verify the response classification: HTTP 200 + `{status:"sent"}` → `{ outcome: 'sent' }`; HTTP 503 + problem-detail `type` ending in `/email/not-configured` → `{ outcome: 'not_configured' }`; HTTP 502 / 500 / 4xx-other / thrown → `{ outcome: 'failed' }`.
- [X] T020 [P] [US2] Create `frontend/src/features/alterego/hooks/useSendAlterEgoEmail.test.tsx`. Wrap with `QueryClientProvider`. Stub `emailClient.sendAlterEgoEmail` (or `fetch` via MSW if already used) to return each of the three outcomes. Assert `window.alert` is called once per outcome with the matching exact string (`"Email sent to someone@example.com."` / `"The email server is not yet configured."` / `"Sending failed. Please try again."`). Assert the hook's `isPending` flag flips correctly across the mutation lifecycle. Assert no PII (recipient, body) is logged via the React Query devtools client cache (which would otherwise persist mutation variables across renders).
- [X] T021 [P] [US2] Create `frontend/src/features/alterego/components/SendAsEmailButton.test.tsx`. With `email = ''` → button has `aria-disabled="true"`, click is a no-op, mutation is never invoked. With `email = 'not-an-email'` → same. With `email = 'someone@example.com'` AND a valid `session.result.poster` → button is enabled; clicking invokes the mutation with `to` / `firstName` / `image` derived from the props; `aria-label` exposes "Send my alter ego by email" or similar accessible name. Assert the tooltip / aria-label on the disabled state names the missing prerequisite (e.g., "Enter a valid email on the Setup tab to enable sending").
- [X] T022 [P] [US2] Create `frontend/tests/e2e/email-send.e2e.ts` (Playwright). Happy-path scenario: navigate to `/`, intake a photo (via the existing camera-stub helpers), pick category selections, type a valid email, click Generate. On the Alter Ego tab, intercept `POST /api/v1/alter-egos/email` with a 200 + `{status:"sent"}` mock via `page.route(...)`. Click Send As Email. Use `page.on('dialog', dialog => { expect(dialog.message()).toBe('Email sent to someone@example.com.'); dialog.accept(); })` to assert the alert content. Second case in the same spec: re-run with the route returning 503 + `{type:".../email/not-configured", title:"...", status:503}` and assert the "not yet configured" alert.

### Implementation for User Story 2

#### Backend

- [X] T023 [P] [US2] Create `backend/src/main/java/com/aiavatar/alterego/config/EmailProperties.java` as a `@ConfigurationProperties("aiavatar.email")` record with `String from` and `String subject` (subject defaults to the FR-2313 string but is overridable via `AIAVATAR_EMAIL_SUBJECT`). Annotate with `@ConfigurationPropertiesScan` registration so it's picked up — mirror how `GeminiProperties` is wired in 003 (see `backend/src/main/java/com/aiavatar/alterego/config/GeminiProperties.java`).
- [X] T024 [P] [US2] Create `backend/src/main/java/com/aiavatar/alterego/config/EmailConfigured.java` — a `@Component` with `@Value("${spring.mail.host:}") String host` and `@PostConstruct void init() { this.configured = host != null && !host.isBlank(); }`. Exposes `isConfigured()` returning the final boolean. Add a one-shot startup log line `event=email.config.evaluated configured={true|false}` (no PII; just the boolean).
- [X] T025 [P] [US2] Create `backend/src/main/java/com/aiavatar/alterego/model/SendAlterEgoEmailRequest.java` as a `record SendAlterEgoEmailRequest(@NotBlank @Email @Size(max=254) String to, @NotBlank @Size(max=50) @ValidFirstName String firstName)`. (The image part is not a record field — it travels as a `MultipartFile` parameter on the controller method, mirroring how `AlterEgoController` handles the `photo` part.)
- [X] T026 [P] [US2] Create `backend/src/main/java/com/aiavatar/alterego/model/SendAlterEgoEmailResponse.java` as `record SendAlterEgoEmailResponse(String status) { public static SendAlterEgoEmailResponse sent() { return new SendAlterEgoEmailResponse("sent"); } }`.
- [X] T027 [P] [US2] Create `backend/src/main/java/com/aiavatar/alterego/service/email/EmailNotConfiguredException.java` — extends `RuntimeException`, carries an immutable `String detail` field (default message: "The mail server is not configured. Contact the operator to enable email delivery.").
- [X] T028 [P] [US2] Create `backend/src/main/java/com/aiavatar/alterego/service/email/AlterEgoEmailBodyBuilder.java` as a `@Component` with a single static-style `build(String firstName): String` method that returns the FR-2314 byte sequence with `{firstName}` substituted. Use a multi-line text block literal so the body is visibly the same as the spec. Preconditions: throw `IllegalArgumentException` on a blank `firstName` (defensive — controller-level `@NotBlank` is the primary guard).
- [X] T029 [US2] Create `backend/src/main/java/com/aiavatar/alterego/service/email/AlterEgoEmailService.java` (depends on T023, T024, T027, T028). Inject `EmailConfigured`, `EmailProperties`, `Optional<JavaMailSender>` (so the service can be wired even when no `JavaMailSender` bean exists), `AlterEgoEmailBodyBuilder`, and the existing `RetryTemplate`. The single `send(to, firstName, imageBytes, imageContentType, correlationId)` method: (1) if `!emailConfigured.isConfigured()` throw `EmailNotConfiguredException`; (2) build the `MimeMessage` via `mailSender.get().createMimeMessage()` + `MimeMessageHelper` (multipart=true) — set `from`, `to` (trimmed), `subject` (from props), `text` (plain text from body builder), and add the image as an attachment named `aiavatar-alter-ego.png` or `aiavatar-alter-ego.jpg` based on content-type; (3) wrap `mailSender.get().send(message)` in `retryTemplate.execute(...)`; (4) log a single `event=email.send.completed outcome=sent correlationId={id}` line with NO recipient / body / first-name in the structured args (R5). Do NOT catch `MailException` — let it propagate so `ProblemDetailAdvice` can translate it to 502.
- [X] T030 [US2] Create `backend/src/main/java/com/aiavatar/alterego/controller/AlterEgoEmailController.java` (depends on T029). `@RestController @RequestMapping("/api/v1/alter-egos/email")` (single path) with `@PostMapping(consumes = MULTIPART_FORM_DATA_VALUE, produces = APPLICATION_JSON_VALUE)`. Accept `@RequestPart("to") String to`, `@RequestPart("firstName") String firstName`, `@RequestPart("image") MultipartFile image`, and the optional `X-Request-Id` header (parse-or-generate UUID, same helper as `AlterEgoController.parseOrGenerate`). Build a `SendAlterEgoEmailRequest` record and pass it through Bean Validation via an inline `Validator` (or annotate the method with `@Validated` and the params with `@Valid` — match the project's existing controller posture). Validate the image content type against `Set.of("image/png", "image/jpeg")` — non-matching → `HttpMediaTypeNotSupportedException` (415). Place the correlation ID into MDC (`requestId` key, same as `AlterEgoController.MDC_REQUEST_ID`) and remove in a `finally`. Return `200 + SendAlterEgoEmailResponse.sent()` and echo `X-Request-Id` in the response header.
- [X] T031 [US2] Update `backend/src/main/java/com/aiavatar/alterego/config/ProblemDetailAdvice.java` (depends on T027). Add an `@ExceptionHandler(EmailNotConfiguredException.class)` returning a `ProblemDetail` with `status = 503`, `type = URI.create("https://aiavatar.local/problems/email/not-configured")`, `title = "Email service is not configured"`, `detail = ex.getDetail()`. Add an `@ExceptionHandler(MailException.class)` returning a generic `ProblemDetail` with `status = 502`, `type = URI.create("https://aiavatar.local/problems/email/send-failed")`, `title = "Email send failed"`, `detail = "The mail server could not be reached after multiple attempts. Please try again."`. Neither handler logs the cause's stack trace at WARN/ERROR with PII — log at WARN with only the exception class name + correlationId.

#### Frontend

- [X] T032 [P] [US2] Create `frontend/src/features/alterego/lib/dataUrlToBlob.ts` — pure helper that accepts a `data:` URL string and returns `{ blob: Blob, mediaType: 'image/png' | 'image/jpeg' }`. Validate the prefix matches `^data:image/(png|jpeg);base64,`; throw otherwise (defensive — the FE only ever passes `session.result.poster.dataUrl`, which the backend guarantees is one of those two). Include unit tests in a co-located `dataUrlToBlob.test.ts` (count this as part of T020's coverage — no separate task ID).
- [X] T033 [US2] Create `frontend/src/features/alterego/services/emailClient.ts` (depends on T032). Export `sendAlterEgoEmail({ to, firstName, posterDataUrl, correlationId, signal? }): Promise<SendOutcome>` where `SendOutcome` is the type added in plan §3. Build `FormData` with `to`, `firstName`, and the image part (use `dataUrlToBlob` to decode the data URL). POST to `/api/v1/alter-egos/email` via the existing `resilientFetch` wrapper (`frontend/src/lib/resilientFetch.ts`) so Principle IV's retry/backoff/jitter applies. Classify the response: `response.status === 200` → `'sent'`; `response.status === 503` AND `problemDetail.type` ends with `/email/not-configured` → `'not_configured'`; anything else (any 4xx/5xx, network throw) → `'failed'`. Do NOT throw on non-2xx — return the outcome.
- [X] T034 [US2] Create `frontend/src/features/alterego/hooks/useSendAlterEgoEmail.ts` (depends on T033). Build a `useMutation` from TanStack Query that calls `sendAlterEgoEmail` and on success (any of the three outcomes) fires `window.alert(...)` with the matching string from data-model.md (`"Email sent to {to}."` / `"The email server is not yet configured."` / `"Sending failed. Please try again."`). Expose `{ send: (args) => void, isPending: boolean }`. Do NOT persist the `to` value in the mutation cache key — pass it as `variables` only.
- [X] T035 [US2] Create `frontend/src/features/alterego/components/SendAsEmailButton.tsx` (depends on T034). Props: `{ email: string; firstName: string; posterDataUrl: string }`. Compute `validity = validateEmail(email)`. When `email.trim() === ''` OR `!validity.ok` → render the button with `aria-disabled="true"`, `disabled={true}`, the same `.button` / `.print-button`-sibling CSS class (whatever the existing actions-row uses), and an `aria-label` / `title` matching the spec's FR-2315 wording. When valid → render enabled; on click call `useSendAlterEgoEmail().send({ to: email.trim(), firstName, posterDataUrl, correlationId: crypto.randomUUID() })`. Also disable while `isPending` is true so the button is not clicked twice in flight.
- [X] T036 [US2] Update `frontend/src/features/alterego/components/AlterEgoPanel.tsx` (depends on T035) to render `<SendAsEmailButton email={session.email} firstName={session.firstName} posterDataUrl={session.result.poster.dataUrl} />` as a sibling of `<PrintButton />`, immediately to its right inside the existing `.alter-ego-panel__actions` flex row. Order MUST be `StartOverButton` → `PrintButton` → `SendAsEmailButton` (FR-2310 says "right of the Print button" — Start Over already sits to the left). No re-layout of the row otherwise.
- [X] T037 [US2] Append `.send-as-email-button` (and any disabled-state modifier) styles to `frontend/src/index.css` mirroring the existing `.print-button` rule. Verify the actions-row CSS Grid / flex template still accommodates three siblings without wrapping on the narrowest supported viewport (Chromium ≥ 320 px wide is the project floor); tighten the row gap if needed.

**Checkpoint**: User Story 2 fully functional. Send As Email is gated on a valid captured email; the not-configured / sent / retryable-failure alerts all fire correctly. The feature is now end-to-end shippable.

---

## Phase 5: Polish & Cross-Cutting Concerns

- [X] T038 [P] Run `npm audit --omit=dev` from `frontend/` and `./gradlew dependencyCheckAnalyze` from `backend/` to confirm no HIGH/CRITICAL CVE is introduced by `spring-boot-starter-mail` or its transitives (Jakarta Mail API + Eclipse Angus Mail). Resolve any new findings before merge per Principle VI.
- [X] T039 [P] Verify ≥ 90% line coverage on every touched module: run `npm run test -- --coverage` from `frontend/` and `./gradlew test jacocoTestReport` from `backend/`. Coverage gate per Principle III must hold across all new files (validator, EmailInput, SendAsEmailButton, the reducer/selector deltas, the backend service, controller, body builder, EmailConfigured).
- [ ] T040 [P] Run the three quickstart.md scenarios end-to-end against a real backend: Scenario A (unconfigured), Scenario B with MailHog (or another local SMTP catcher), Scenario C (blackhole host). Confirm each alert message matches the data-model.md string exactly. Confirm MailHog shows the FR-2313 subject, FR-2314 body, and a `aiavatar-alter-ego.png` / `.jpg` attachment.
- [ ] T041 [P] PII-redaction smoke test: with Scenario B running, send one email, then `grep -E '(@example|Hey, |someone)' backend/build/logs/*.log` (or the configured logback file) and assert zero matches. The structured `event=email.send.completed` log line should be present with `correlationId` only — no recipient, no body, no first-name leakage (SC-2306).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 + T002 are independent of each other and of all other phases. Can start immediately. Both must complete before any backend task in US2.
- **Foundational (Phase 2)**: Skipped. No blocking shared infrastructure beyond Phase 1.
- **US1 (Phase 3)**: Can start immediately after Phase 1. US1 is frontend-only — it does NOT depend on T001 or T002. (In practice the two are concurrent.)
- **US2 (Phase 4)**: Backend tasks (T014–T018 tests, T023–T031 impl) depend on Phase 1 completion. Frontend tasks (T019–T022 tests, T032–T037 impl) depend on **US1 completion** because `SendAsEmailButton` consumes `session.email`, `session.firstName`, and the reducer's email-validity-gated `isReadyToGenerate` shape that US1 establishes.
- **Polish (Phase 5)**: Depends on both stories being complete.

### User Story Dependencies

- **US1 (P1)**: No dependencies on other stories. Shippable as a standalone PR — the Email field is visible, validated, retained, and gates Generate / Surprise Me. (Without US2, the field has no immediate user-visible payoff beyond being captured; but it is independently testable per the spec.)
- **US2 (P1)**: Depends on US1 — the Send button reads `session.email`, which US1 introduces. The two stories ship together as the full MVP; US1 alone is a coherent intermediate PR.

### Within Each User Story

- **US1**: Tests (T003–T007 — all parallel) must be written and fail before T008–T013. T009 (reducer extension) must precede T010 (selectors extension — selectors import the reducer type). T011 (EmailInput component) precedes T012 (SetupLayout wires it). T013 (CSS) can land alongside or after T011/T012.
- **US2**: Tests (T014–T022 — all parallel, different files) must be written and fail before T023–T037. Within backend impl: T029 (service) depends on T023 / T024 / T027 / T028; T030 (controller) depends on T029, T025, T026; T031 (ProblemDetailAdvice extension) depends on T027. Within frontend impl: T032 → T033 → T034 → T035 → T036; T037 (CSS) parallel with T035/T036.

### Parallel Opportunities

- **Phase 1**: T001 and T002 in parallel.
- **US1 tests (T003–T007)**: all five in parallel — five distinct test files.
- **US1 impl T008 + T011** in parallel (validator file vs. EmailInput component, both standalone).
- **US2 tests (T014–T022)**: all nine in parallel — nine distinct test files spread across backend + frontend.
- **US2 backend impl T023, T024, T025, T026, T027, T028**: all six in parallel — six distinct files, none import each other.
- **US2 frontend impl T032 + T037** in parallel with the T033 → T034 → T035 → T036 chain.
- **Phase 5**: T038, T039, T040, T041 all parallel.
- **Cross-story**: once Phase 1 is done, US1 frontend work and US2 backend work can proceed in parallel — they touch entirely disjoint trees. (US2 frontend must wait for US1.)

---

## Parallel Example: User Story 1 (tests)

```bash
# Launch all five US1 test files together — five different files, no shared imports:
Task: "frontend/src/features/alterego/validation/email.test.ts (T003)"
Task: "frontend/src/features/alterego/components/EmailInput.test.tsx (T004)"
Task: "frontend/src/features/alterego/state/reducer.test.ts — append EmailChanged + StartOverRequested + SurpriseMePicked cases (T005)"
Task: "frontend/src/features/alterego/state/selectors.test.ts — append emailValidity + missingInputs + isReady gating cases (T006)"
Task: "frontend/src/features/alterego/components/SetupLayout.test.tsx — append positional assertion (T007)"
```

## Parallel Example: User Story 2 (backend tests + impl)

```bash
# All nine US2 test files in parallel:
Task: "AlterEgoEmailControllerIntegrationTest.java (T014)"
Task: "AlterEgoEmailServiceTest.java (T015)"
Task: "AlterEgoEmailBodyBuilderTest.java (T016)"
Task: "EmailConfiguredTest.java (T017)"
Task: "SendAlterEgoEmailRequestValidationTest.java (T018)"
Task: "emailClient.test.ts (T019)"
Task: "useSendAlterEgoEmail.test.tsx (T020)"
Task: "SendAsEmailButton.test.tsx (T021)"
Task: "email-send.e2e.ts (T022)"

# All six US2 backend leaf-impl files in parallel (none import each other):
Task: "EmailProperties.java (T023)"
Task: "EmailConfigured.java (T024)"
Task: "SendAlterEgoEmailRequest.java (T025)"
Task: "SendAlterEgoEmailResponse.java (T026)"
Task: "EmailNotConfiguredException.java (T027)"
Task: "AlterEgoEmailBodyBuilder.java (T028)"
```

---

## Implementation Strategy

### MVP First (both P1 stories — neither is optional)

The spec marks both user stories as P1; the feature's value proposition requires both. A pragmatic shipping rhythm:

1. **Phase 1** (T001, T002) — half a day; safe, dependency-only change.
2. **Phase 3 (US1)** — frontend only. Field renders, validates, retains. Shippable as PR #1 if a staged rollout is desired (the field's user-facing payoff is limited without US2, but it is coherent).
3. **Phase 4 (US2)** — backend pipeline + frontend button. Shippable as PR #2, or bundled with PR #1.
4. **Phase 5** — coverage / audit / quickstart validation gates.

### Incremental Delivery (staged)

- **PR 1 (US1 only)**: Setup tab gains the field; nothing else changes. Backend untouched. Risk: very low.
- **PR 2 (US2 only)**: Send button appears; backend endpoint exists; mail seam ships unconfigured (Scenario A behaviour). Risk: low — production behaviour is the "not yet configured" alert until an operator sets `spring.mail.host`.

### Parallel Team Strategy

- After Phase 1 lands:
  - **Developer A**: US1 (frontend reducer + validator + EmailInput + SetupLayout wiring).
  - **Developer B**: US2 backend (EmailProperties, EmailConfigured, controller, service, body builder, ProblemDetailAdvice extension).
  - **Developer C**: US2 frontend tests (T019–T022) — these can be authored against a mocked backend even before US1 lands; the test file structure does not depend on US1's reducer-field name beyond knowing the prop contract that the plan locks in.
- Once US1 and US2-backend land, Developer C's US2-frontend impl (T032–T037) can integrate against the now-shipped reducer field.

---

## Notes

- Every task respects Principle III: test files (T003–T007 for US1, T014–T022 for US2) are written first and must FAIL before the matching production code is committed. "Fail" includes compile-failure for backend tests that reference yet-to-exist classes.
- The single new backend dependency (`spring-boot-starter-mail`, T001) is Spring-managed; no version pin required.
- No persistence is introduced anywhere — FR-2320 / SC-2306 are verified by T041 (PII-redaction smoke) and by the unit-test log-capture assertion in T015.
- The `alert()` primitive is the only feedback channel for Send As Email outcomes (clarification 2026-05-11 Q2). Do not introduce a toast / banner / snackbar component during implementation.
- The "mail server configured" check is config-presence only — never a live SMTP probe (clarification Q3). T017 pins both states; T024 implements only the presence check.

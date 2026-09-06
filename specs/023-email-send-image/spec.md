# Feature Specification: Send Generated Alter Ego By Email

**Feature Branch**: `023-email-send-image`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "checkout github project issue #57" — Add an optional email input on the Setup tab and a "Send As Email" button on the Alter Ego tab that emails the generated image to the entered address. Provision of an actual mailing service is out of scope; when no mail server is configured, clicking the button must show an alert that the email server is not yet configured.

## Clarifications

### Session 2026-05-11

- Q: When the captured email is blank or invalid, how should the "Send As Email" button behave on the Alter Ego tab? → A: Disabled with accessible indication (mirrors Generate's gating pattern — `aria-disabled` + reduced-opacity styling + tooltip/aria-label).
- Q: How should a successful send be confirmed to the user? → A: Native `alert()` popup confirming success — mirrors the not-configured alert primitive from the issue; same primitive is used for retryable failure too. No new UI system is introduced.
- Q: What does "mail server configured at application startup" mean — config presence, or a live probe? → A: Config presence only. At startup the backend checks whether the required mail-server configuration items are all present and non-blank; if yes → "configured" for the lifetime of the running process; if no → "not configured". No network call is made at startup. A configured-but-actually-broken server surfaces at click time as a retryable-failure alert (FR-2316), not as the "not configured" alert (FR-2311).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Capture an optional recipient email on Setup (Priority: P1)

A participant fills out the Setup tab. Between the category-selection block and the first-name input they see a new optional field labelled "Email". They may leave it blank — Generate / Surprise Me proceed as before — or they may type an address that follows standard email format. If they type something, a small clear-button (the same "X" affordance used today by the custom-role input on the Setup tab) appears at the trailing edge of the field; clicking it empties the field instantly. Whatever they leave in the field at the moment they click Generate or Surprise Me is retained in the session and is the address that will later be used by the Send As Email action on the Alter Ego tab.

**Why this priority**: This is the data-capture half of the feature. Without it, the Send As Email action on the Alter Ego tab has no address to use, so the whole feature is inert. It also stands on its own — the field can be shipped and validated independently of the mailing flow.

**Independent Test**: Open the Setup tab, observe the new Email field positioned between the category grids and the first-name input. Type `someone@example.com`, observe the clear-X appear, click the X, observe the field clear and the X disappear. Type `not-an-email`, attempt to Generate, observe that the action is blocked by an inline validation message; correct the value or clear the field, observe Generate become available again. Type a valid address, click Generate, switch to the Alter Ego tab, return to Setup — the address is still there.

**Acceptance Scenarios**:

1. **Given** the Setup tab is loaded with all other required inputs satisfied and the Email field is blank, **When** the user clicks Generate, **Then** generation proceeds normally (the Email field's blankness MUST NOT block submission).
2. **Given** the Email field contains a value that does not match standard email format, **When** the user clicks Generate or Surprise Me, **Then** the action is blocked, the field is marked invalid, and an inline message indicates that the email format is invalid.
3. **Given** the Email field contains any non-blank value, **When** the user looks at the trailing edge of the field, **Then** a clear-X button is visible there with the same visual treatment and behaviour as the clear-X on the custom-role input.
4. **Given** the Email field contains any non-blank value, **When** the user clicks the clear-X, **Then** the field becomes blank, the clear-X disappears, and any prior invalid-format indication is removed.
5. **Given** the user has typed a valid email and clicked Generate, **When** the user navigates to the Alter Ego tab and back to Setup, **Then** the Email field still shows the value they typed (session-scoped retention, no persistence beyond the browser session).
6. **Given** the new Email field is rendered, **When** the user inspects the Setup tab visually, **Then** the field is positioned between the category-selection block and the first-name input — both in DOM order and visually.

---

### User Story 2 - Send the generated image to the captured email (Priority: P1)

After a generation completes, the user is on the Alter Ego tab and sees their generated poster. To the right of the existing Print button there is now a third action button labelled "Send As Email". When the user clicks it (and the application's mail server is configured), the generated image is sent as an attachment to the address captured on the Setup tab, with a fixed subject and body. When the mail server is not configured, clicking the button instead surfaces an alert popup stating that the email server is not yet configured — no send is attempted.

**Why this priority**: This is the user-facing outcome of the feature — the reason the Email field on Setup exists. P1 alongside Story 1 because Story 1 has no payoff without it.

**Independent Test**: With a generated alter ego visible on the Alter Ego tab and a valid email entered on Setup, click Send As Email. With the mail server configured, observe a success indication and that the message is delivered with the fixed subject and body (testable end-to-end once a mailing service exists; until then, observe via the application's outbound-mail seam). With the mail server NOT configured, observe the alert popup with the "not yet configured" message and that no send attempt is made.

**Acceptance Scenarios**:

1. **Given** the user is on the Alter Ego tab with a generated image visible, **When** the user looks at the action row, **Then** a "Send As Email" button is present to the right of the Print button, in the same row, with visual treatment consistent with the existing action buttons.
2. **Given** the application's mail server is not configured, **When** the user clicks Send As Email, **Then** an alert popup appears with a message stating that the email server is not yet configured, and no outbound email is attempted.
3. **Given** the application's mail server is configured and a valid email was captured on Setup, **When** the user clicks Send As Email, **Then** an email is dispatched to that address with subject `Your AI Generated Alter Ego - CodeCrafts 2026` and a body of:

   ```
   Hey, {firstName}!

   Thank you, for being a part of CodeCrafts 2026!

   Find your AI Generated Alter Ego attached to this letter.

   Happy times!
   ```

   where `{firstName}` is substituted with the first name captured on Setup, and the generated image is included as an attachment.
4. **Given** the application's mail server is configured but the Email field on Setup was left blank or contains an invalid value, **When** the user reaches the Alter Ego tab, **Then** the Send As Email button is disabled with an accessible indication (`aria-disabled`, reduced-opacity styling consistent with Generate's gating, and a tooltip / aria-label naming the missing prerequisite).
5. **Given** the application's mail server is configured, **When** the send operation fails for any reason other than missing configuration (e.g., transient network/server error), **Then** a native alert popup informs the user that the send did not succeed and that they may retry; the generated image remains intact and the captured email remains in the field.
6. **Given** the application's mail server is configured and a valid recipient is captured, **When** the send completes successfully, **Then** a native alert popup confirms the send (e.g., "Email sent to {address}.") — using the same alert primitive as the not-configured and failure paths.
7. **Given** the user has just sent the email successfully, **When** the user clicks Send As Email again, **Then** the action sends again — the button is not single-use within the session.

---

### Edge Cases

- **Mail server unconfigured at startup**: covered by FR-2311 / Story 2 — the alert popup is the entire user-facing behaviour; the rest of the Send pipeline is not exercised.
- **Mail server configured but transient failure (network down, provider rejected)**: surfaced as a retryable error to the user; the image and the captured email survive.
- **Email entered as `  user@example.com  ` with surrounding whitespace**: trimmed before validation and before being used as the recipient; the visible field may still show whatever the user typed until they blur.
- **Email field contains a previously-valid address but the user clicks the clear-X while a validation message is showing**: the field clears, the validation message clears, and Generate/Surprise Me become available again (because blank is a valid optional state).
- **Surprise Me with an invalid email present**: Surprise Me is blocked in the same way Generate is — an invalid email blocks *any* Setup-level submit path.
- **Start-over after a generated session with email entered**: a full reset returns the Setup tab to its initial empty state, including the Email field (consistent with how other Setup fields behave on start-over).
- **Tab gating unchanged**: the introduction of the Email field does not change the existing tab-access rules — the Alter Ego tab is still gated on a completed generation, and the Setup tab is still gated while a generation is in flight.

## Requirements *(mandatory)*

### Functional Requirements

#### Setup tab — Email field

- **FR-2301**: The Setup tab MUST render a new single-line text input labelled "Email", positioned between the category-selection block and the first-name input — both in DOM order and visually.
- **FR-2302**: The Email field MUST be optional — leaving it blank MUST NOT block Generate, Surprise Me, or any other Setup-level action.
- **FR-2303**: The Email field MUST be validated as a standard email address ONLY when it is non-blank. A blank value MUST be considered valid.
- **FR-2304**: When the Email field contains a non-blank value that fails email-format validation, the user MUST NOT be able to submit Generate or Surprise Me until the value is either corrected or cleared.
- **FR-2305**: Invalid-format state MUST be surfaced to the user inline (next to or beneath the field) with a clear, non-technical message identifying the field as the source.
- **FR-2306**: When the Email field contains any non-blank value, a clear-X button MUST appear at the trailing edge of the input. When the field is blank, the clear-X MUST NOT be visible.
- **FR-2307**: The clear-X button MUST be visually and behaviourally consistent with the clear-X on the custom-role input on the Setup tab (same iconography, same hit area, same hover/focus treatment, same effect on click — instantaneous clearing of the field).
- **FR-2308**: The captured Email value MUST be retained in session state across Generate and Surprise Me actions and across tab navigation between Setup and Alter Ego — switching tabs or completing a generation MUST NOT clear the field.
- **FR-2309**: The Email value MUST NOT be persisted beyond the browser session — no disk, no database, no cache, no log of the address (consistent with the project's existing no-persistence posture).

#### Alter Ego tab — Send As Email action

- **FR-2310**: The Alter Ego tab MUST render a new action button labelled "Send As Email", positioned in the action row to the right of the existing Print button.
- **FR-2311**: At application startup, the backend MUST evaluate "mail server configured" purely as **the presence (non-blank) of all required mail-server configuration items** supplied by the operator. No network call / handshake / probe is performed at startup. When this presence check fails, clicking Send As Email MUST surface a native alert popup stating that the email server is not yet configured, and no outbound email MUST be attempted in that case. A configured-but-unreachable server is NOT a "not configured" condition — it surfaces at click time as a retryable-failure alert per FR-2316.
- **FR-2312**: When the application's mail server IS configured, the send pipeline MUST be wired and exercised: clicking Send As Email MUST dispatch an email to the address captured on Setup with the fixed subject and templated body (see FR-2313, FR-2314), with the currently-displayed generated image as an attachment. On a successful dispatch, the app MUST surface a native alert popup confirming the send (naming the recipient address). The same `alert()` primitive used for the not-configured path (FR-2311) MUST also be used for success and retryable-failure feedback — no toast / banner / snackbar system is introduced for this feature.
- **FR-2313**: The email subject MUST be exactly: `Your AI Generated Alter Ego - CodeCrafts 2026`.
- **FR-2314**: The email body MUST be exactly:

  ```
  Hey, {firstName}!

  Thank you, for being a part of CodeCrafts 2026!

  Find your AI Generated Alter Ego attached to this letter.

  Happy times!
  ```

  where `{firstName}` is replaced with the first name captured on the Setup tab.
- **FR-2315**: When the mail server is configured but the captured recipient email is blank or fails standard email-format validation, the Send As Email button MUST be disabled. The disabled state MUST use the same gating treatment as the Generate button (`aria-disabled="true"`, reduced-opacity styling) and MUST expose an accessible explanation of the missing prerequisite via a tooltip and/or `aria-label` (e.g., "Enter a valid email on the Setup tab to enable sending"). The button MUST NOT silently no-op, and MUST NOT surface a click-time alert/guidance for the blank/invalid case — discoverability is provided by the always-visible disabled state.
- **FR-2316**: When the mail server is configured and the send fails for a reason other than missing configuration, the app MUST surface a native alert popup informing the user that the send did not succeed and inviting them to retry (using the same `alert()` primitive as FR-2311 and FR-2312). The generated image and the captured email MUST remain available.
- **FR-2317**: Send As Email MUST remain repeatable within a session — successive clicks dispatch successive emails to the same address.
- **FR-2318**: The "mail server not configured" check MUST be evaluated at application startup (FR-2311) as a one-shot configuration-presence inspection — not a live probe — and the resulting configured/unconfigured state MUST drive the button's behaviour for the entire lifetime of that running application instance (no re-evaluation per click, no re-evaluation on config-file change without restart).

#### Cross-cutting

- **FR-2319**: The introduction of this feature MUST NOT change any existing tab-gating, generation, Surprise Me, Print, or Start-over behaviour beyond what is explicitly stated here.
- **FR-2320**: The application MUST NOT persist the recipient email, the email body, the subject, the attached image bytes, or any metadata about the send (recipient, timestamp, success/failure) to disk, database, cache, or logs — consistent with the project's existing no-persistence posture.

### Key Entities *(include if feature involves data)*

- **Recipient Email**: A session-scoped optional string captured on the Setup tab. Valid states: blank, or a string matching standard email format (after trimming surrounding whitespace). Lifetime: lives in browser session memory only; cleared by start-over; never persisted.
- **Outgoing Alter Ego Email**: A composed message dispatched from the Alter Ego tab. Attributes: `to` (the Recipient Email captured at click time), `subject` (fixed string — see FR-2313), `body` (templated string with first-name substitution — see FR-2314), `attachment` (the generated alter-ego image currently displayed). Lifetime: composed and dispatched within a single user-initiated request; not persisted.
- **Mail Server Configuration**: A configured/not-configured state evaluated **once at application startup** as a configuration-presence check (all required mail-server config items present and non-blank). Drives whether Send As Email attempts to dispatch or shows the "not yet configured" alert. A live probe is NOT performed at startup; a configured-but-unreachable server surfaces at click time as a retryable-failure alert (FR-2316). Not user-editable from within the app; changes to configuration require a restart to take effect (FR-2318).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-2301**: A first-time participant who has just generated their alter ego can locate and trigger Send As Email in under 10 seconds without prompting (button is discoverable next to Print without searching).
- **SC-2302**: 100% of clicks on Send As Email while the mail server is unconfigured result in the "not yet configured" alert — never a silent no-op, never an attempted send, never a confusing generic error.
- **SC-2303**: 100% of non-blank email values that the user types but that fail standard email-format validation are surfaced before Generate or Surprise Me submits — i.e., a malformed email is never silently accepted into a generation submission.
- **SC-2304**: When the mail server is configured, end-to-end send latency from button click to outbound message dispatched does not exceed 5 seconds under normal conditions (excluding upstream provider latency); user-facing feedback (success or retryable failure) is visible within the same envelope.
- **SC-2305**: The recipient email a user typed on Setup is still visible in the field 100% of the time after a Generate-Surprise-tab-switch-and-return round-trip, with no manual re-entry required.
- **SC-2306**: Zero traces of the recipient email or the dispatched message contents appear in disk artefacts, database rows, log lines, or cached responses produced by the application during normal operation, verified by inspection of those surfaces after a send.

## Assumptions

- **Reusing the custom-role clear-X pattern is intentional**: the issue explicitly calls out that the look-and-feel and overall logic of the clear-X must match the existing custom-role input. The team treats the existing custom-role clear-X as the canonical pattern; this feature does not introduce a new pattern.
- **Email-format validation = "standard email format"**: interpreted as the common single-line address grammar already familiar to participants (e.g., `local@domain.tld`), with whitespace trimmed before validation. The spec does not pin a specific RFC interpretation; the team will pick a pragmatic standard during planning and document it there.
- **Mail server configuration is operator-controlled, not user-controlled**: there is no in-app UI for configuring the mail server. The presence/absence of configuration is determined at application startup from operator-supplied configuration items; the spec does not enumerate which items those are because mail-service provisioning is explicitly out of scope.
- **No persistence anywhere**: extends the existing no-persistence posture documented across prior features (no logging of email, no caching of message contents, no DB row for the send) — this is a hard constraint, not a default.
- **Attachment is the currently-displayed generated image**: when the user clicks Send As Email, the attachment is the image they are looking at on the Alter Ego tab at that moment, including any frame / overlay treatments that have been applied to it as part of the generation pipeline.
- **The Send As Email button does not depend on Print being enabled**: it is a sibling action, gated independently. Print continues to be available regardless of email-field state.
- **Start-over clears the Email field**: consistent with how Start-over clears the rest of the Setup state today.
- **Invalid email blocks both Generate and Surprise Me**: when the field is non-blank and malformed, both Setup-level submit paths are blocked. This is the simplest coherent behaviour given that the captured email is later consumed by the Alter Ego-tab action; relaxing it would let Setup commit an unusable address into the session.
- **The feature touches only the Setup tab, the Alter Ego tab's action row, and the outbound-email seam**: no changes to category selection, image generation, frame overlay, poster text overlay, Print, or any other existing flow beyond accommodating the new field and the new button.

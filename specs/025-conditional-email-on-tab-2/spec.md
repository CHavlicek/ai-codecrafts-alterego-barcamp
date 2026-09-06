# Feature Specification: Conditional Email Input on the Alter Ego Tab

**Feature Branch**: `025-conditional-email-on-tab-2`
**Created**: 2026-05-15
**Status**: Draft
**Input**: GitHub issue #60 — "Add a conditional email input on the second tab"

> User should be able to enter his email on tab 2 (the Generated Alter Ego tab) if he forgot to enter it on the first tab.
>
> The email input should appear above the buttons row.
>
> Once the user enters a valid email (correct format only) the button "Send as Email" becomes active.
>
> If the user has entered the email on the first tab, the "Send as Email" button should be active after image generation finishes and no additional email input field should be shown on the second tab.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Capture a forgotten email on the Alter Ego tab (Priority: P1)

A user lands on the Alter Ego tab after generation has finished but realises they did not enter an email address on the Setup tab. They want to send themselves the generated poster without losing the result by navigating back to Setup. The Alter Ego tab offers an email field directly above the action-button row; once they type a correctly-formatted address, the "Send As Email" button activates and a single click sends the poster.

**Why this priority**: This is the entire purpose of the issue — the existing Setup-tab email is optional, and enough users forget it to motivate a recovery path on the only tab where the send action is visible. Without this story the recovery path does not exist; everything else in the spec is a refinement of this flow.

**Independent Test**: Generate an alter ego with the Setup-tab email left blank, land on the Alter Ego tab, type a valid email into the inline field above the actions row, observe the "Send As Email" button transition from disabled to enabled, click it, and confirm the existing send pipeline (handled by feature 023) is invoked exactly once.

**Acceptance Scenarios**:

1. **Given** the user completed Setup with a blank email and the Alter Ego tab is now showing the generated poster, **When** the user looks at the Alter Ego tab, **Then** an email input field is visible directly above the action-button row (Start Over · Print · Send As Email).
2. **Given** that inline email field is visible and currently blank or invalid, **When** the user inspects the "Send As Email" button, **Then** the button is disabled and its accessible hint explains that a valid email is required to enable sending.
3. **Given** that inline email field is visible, **When** the user types a correctly-formatted email address, **Then** the "Send As Email" button becomes enabled while the user is still on the Alter Ego tab AND the inline field stays visible (no layout shift; FR-2508 amended).
4. **Given** the inline email field holds a valid email and the "Send As Email" button is enabled, **When** the user clicks "Send As Email", **Then** the existing 023 send flow is dispatched with the address the user just entered and the existing single-alert success/failure feedback is shown.

---

### User Story 2 — Email already captured on Setup means no duplicate input on tab 2 (Priority: P1)

A user who already typed a valid email on the Setup tab returns to the Alter Ego tab after generation. They expect the "Send As Email" button to be ready to use immediately — no second email field should clutter the Alter Ego tab. The captured-once value is the authoritative recipient.

**Why this priority**: Equally critical, because rendering an empty or duplicate email field next to an already-captured value would confuse users about which value will actually be used and which address the send button will dispatch to. The issue calls this out explicitly. P1 alongside Story 1 — both must ship together for the feature to be coherent.

**Independent Test**: Enter a valid email on the Setup tab, generate, switch to the Alter Ego tab, confirm no email input appears anywhere on the Alter Ego tab, and confirm the "Send As Email" button is enabled the moment the poster finishes rendering.

**Acceptance Scenarios**:

1. **Given** the user entered a correctly-formatted email on the Setup tab before pressing Generate, **When** the Alter Ego tab renders the generated poster, **Then** no email input field is shown anywhere on the Alter Ego tab.
2. **Given** the user entered a correctly-formatted email on the Setup tab and the generation finished successfully, **When** the user views the Alter Ego tab, **Then** the "Send As Email" button is enabled at the same moment the poster becomes visible.

---

### User Story 3 — Correcting a captured-but-invalid email without leaving the Alter Ego tab (Priority: P2)

A user reached the Alter Ego tab with an invalidly-formatted captured email — for example via Surprise Me, which bypasses Setup-tab email validation. They want to correct it in place rather than navigate back.

**Why this priority**: P2 because the issue's literal text only describes the "forgot to enter" case (blank on Setup). However, the visibility rule is keyed on "captured email is not valid", and blank-vs-invalid both satisfy that rule, so this case falls out naturally from how Story 1 is implemented. We capture it explicitly so behaviour on an invalid captured value is not left ambiguous.

**Independent Test**: Drive the session into a state where the captured email is non-blank but invalid (Surprise Me path, or any future flow that admits such state), switch to the Alter Ego tab, see the inline email field appear pre-filled with the invalid value, correct the value, and confirm "Send As Email" enables once the corrected value validates.

**Acceptance Scenarios**:

1. **Given** the captured email is non-blank but fails the format validator, **When** the Alter Ego tab renders the generated poster, **Then** the inline email field is shown pre-filled with the current invalid value and the inline format-error text is visible.
2. **Given** the inline email field is visible and pre-filled with an invalid value, **When** the user edits the value into a correctly-formatted email, **Then** the "Send As Email" button enables.

---

### Edge Cases

- The user generates with a blank email, types into the Alter Ego-tab inline field, then clicks the Setup tab. The Setup-tab email input MUST display the same value — there is exactly one captured email per session, not two parallel ones.
- The user types into the Alter Ego-tab inline field and then presses Start Over. Start Over resets the entire session to its initial state, including the captured email, exactly as it does today for every other field.
- The user types a valid email on the Alter Ego tab, then deletes characters until the value becomes invalid or blank. The "Send As Email" button MUST disable again on the same keystroke that breaks validation, with the same accessible hint shown in Story 1 acceptance scenario 2. The inline field itself stays visible throughout — it never auto-collapses (FR-2508 amended).
- A poster generation fails into the inline-fallback state. The Alter Ego tab still renders a poster and the action-button row; the conditional email field MUST follow the same visibility rule as in Story 1/2 regardless of whether the displayed poster is a real-provider result or the fallback.
- The user is mid-keystroke in the inline email field when the user clicks Print or Start Over. The conditional field is not gating those actions; both proceed exactly as today.
- The user uses Surprise Me without entering anything on Setup. The Alter Ego tab shows the inline email field (Story 1 path) because the captured email is blank.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-2501**: The Alter Ego tab MUST display an inline email input above the action-button row (Start Over · Print · Send As Email) **only when** the session's captured email is not a valid email address (blank counts as not-valid for this rule).
- **FR-2502**: The Alter Ego tab MUST NOT display any email input above the action-button row when the session's captured email is already a valid email address.
- **FR-2503**: The inline Alter Ego-tab email input MUST use the same validity rule and the same trimmed-length cap (≤ 254 characters) as the existing Setup-tab email input introduced in feature 023. There is one validation rule for "valid email", not two.
- **FR-2504**: Typing into the inline Alter Ego-tab email input MUST update the same captured-email session-state value that the Setup-tab input writes to — there is exactly one captured email value per session, not two parallel values for the two tabs.
- **FR-2505**: The "Send As Email" button on the Alter Ego tab MUST be enabled if and only if the captured email is valid (FR-2503) AND no send is currently in flight, irrespective of which tab the email was typed on.
- **FR-2506**: When the inline Alter Ego-tab email input is visible and the captured email is blank or invalid, the "Send As Email" button MUST remain disabled and continue to expose an accessible hint explaining that a valid email is required to enable sending.
- **FR-2507**: The inline Alter Ego-tab email input MUST surface the same inline format-error feedback that the Setup-tab input surfaces for non-blank invalid values, using the same error-message text.
- **FR-2508** (amended 2026-05-15): The Alter Ego-tab email field, once rendered for a given poster session, MUST remain visible for the rest of that session — even after the captured email becomes valid. A poster session ends at Start Over or at the start of a fresh Generate; the latch resets when the session leaves the terminal phases. *Rationale: causing the field to disappear mid-typing produces a sudden layout shift that confuses users; sticky-once-shown keeps the action row's position stable while the user types.*
- **FR-2509**: When the captured email transitions from valid back to blank or invalid during a poster session that did not initially render the inline field (e.g. via tab round-trip + clearing the Setup-tab field), the Alter Ego-tab email field MUST appear in the position above the actions row. From that point on, FR-2508's sticky rule keeps it visible.
- **FR-2510**: The Alter Ego-tab email field MUST NOT be rendered while the session is in the pre-poster states (empty placeholder, loading) — it is only relevant once the action-button row is itself rendered, i.e. when the poster is showing (succeeded or failed_with_fallback phases).
- **FR-2511**: Start Over MUST clear the captured email regardless of which tab it was typed on, restoring the initial session state exactly as it does for every other field today.
- **FR-2512**: Submitting "Send As Email" from the Alter Ego tab MUST dispatch the existing 023 send flow with the captured email as the recipient — there is no second send path.
- **FR-2513**: The Alter Ego-tab inline field MUST be keyboard-accessible and screen-reader-labelled to the same standard as the Setup-tab field (labelled control, `aria-invalid` on non-blank invalid values, error region announced via `role="alert"`).
- **FR-2514**: This feature MUST NOT introduce any persistence — the captured email continues to live only in browser session state for the lifetime of the session, consistent with the project's no-persistence posture (001 FR-016 / FR-017 / FR-024).
- **FR-2515**: This feature MUST NOT alter the wire format of the existing send-email request or the Setup-tab UI.

### Key Entities

- **Captured email (session-scoped, single value)**: The session-state field that holds the most recent email the user typed. There is exactly one such value per session. Both the Setup-tab email input and the new Alter Ego-tab email input read and write the same field. Reset by Start Over.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-2501**: 100% of users who reach the Alter Ego tab with a blank captured email see exactly one email input field above the Alter Ego-tab actions row.
- **SC-2502**: 100% of users who reach the Alter Ego tab with a valid captured email see zero email input fields on the Alter Ego tab.
- **SC-2503**: After a user types a correctly-formatted email into the inline Alter Ego-tab field, the "Send As Email" button transitions from disabled to enabled within the same keystroke (no perceptible delay). The inline field itself MUST stay visible — only the button changes state (FR-2508 amended).
- **SC-2504**: Users who realised on the Alter Ego tab that they forgot to enter an email can complete an emailed-poster send without ever returning to the Setup tab — the entire correction-and-send flow is reachable from the Alter Ego tab in a single sequence of inputs.
- **SC-2505**: After this change ships, the number of session-state fields representing the captured email remains exactly one (no duplication of state).

## Assumptions

- **Pre-filling the Alter Ego-tab field**: When the captured email is non-blank but invalid (e.g. typed via Surprise Me, which bypasses email validation on the Setup tab), the Alter Ego-tab field is pre-filled with the current invalid value so the user can correct in place rather than re-type. This mirrors how the Setup-tab field behaves for the same invalid value.
- **Layout symmetry with Setup tab**: The Alter Ego-tab email field reuses the exact visual treatment of the Setup-tab email input — label, single-line text input, trailing clear-X visible only when non-empty, inline error below the field for non-blank invalid values. No new visual vocabulary is introduced.
- **Phase gating**: The inline field is visible only when the poster + actions row is visible (succeeded / failed_with_fallback phases). It is never shown during the empty placeholder or loading states, because the action-button row itself is not shown in those states (existing tab-content behaviour).
- **No backend change**: The send path, the validator, the wire contract, and the no-persistence posture are unchanged. This feature is a UI refinement on top of the captured-email state already introduced in feature 023.
- **Single source of truth**: The session reducer's existing email field is the sole owner of the captured email. The new Alter Ego-tab input does not introduce a parallel field; it writes the same `EmailChanged` action the Setup-tab input writes today.

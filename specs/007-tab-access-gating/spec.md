# Feature Specification: Tab Access Gating

**Feature Branch**: `007-tab-access-gating`
**Created**: 2026-04-23
**Status**: Draft
**Input**: User description: "Gate access to the two top-level tabs so users can't reach the alter-ego tab before generating, can't return to setup mid-request, and always start back on setup after Start-over. Also: rename the setup tab label from 'setup' to 'Setup'."
**Source issue**: [#14 — Accesses to App Tabs](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/14)

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Alter-ego tab is gated until the first generation completes (Priority: P1)

A first-time visitor lands on the Setup tab. The second tab ("2 Your Alter Ego") is visibly disabled and cannot be entered in any way (click, keyboard focus, Enter/Space), because there is nothing to show there yet. The visitor must fill the Setup form and press Generate to produce a result. Only after that first generation finishes does the alter-ego tab become reachable.

**Why this priority**: This is the core of the reported bug — today, an empty/placeholder alter-ego panel is exposed before it has any content, which is confusing and breaks the "complete Setup first" flow the product is designed around.

**Independent Test**: Open the app with no photo, no selections, no prior generation. Confirm that clicking tab 2, pressing Tab to try to focus it, and pressing Enter/Space while it's "hovered" all leave the user on Setup. Run a full generation; after completion, confirm tab 2 is now reachable by click AND keyboard.

**Acceptance Scenarios**:

1. **Given** the app just loaded and no generation has happened yet, **When** the user clicks the "2 Your Alter Ego" tab, **Then** nothing happens — the Setup panel stays visible and the tab's aria-selected state does not change.
2. **Given** the app just loaded, **When** the user presses Tab to enter the tablist and then presses ArrowRight, **Then** focus does NOT move to the alter-ego tab (disabled tabs are skipped), and the alter-ego tab is exposed to assistive tech as disabled (aria-disabled="true").
3. **Given** the alter-ego tab is disabled and the user programmatically focuses it and presses Enter or Space, **When** the key fires, **Then** the Setup panel remains active (the key event is ignored).
4. **Given** a first generation has completed (succeeded OR failed-with-fallback), **When** the user clicks the alter-ego tab, **Then** the alter-ego panel becomes visible and the tab is now fully operable by mouse and keyboard.

---

### User Story 2 — Setup tab is locked while a generation is in flight (Priority: P1)

While the app is waiting for the AI to produce an image (from Generate click until the response lands), the user must not be able to go back to Setup and mutate inputs. The Setup tab is disabled for that window only; as soon as the response lands — success or fallback — it becomes reachable again.

**Why this priority**: Mid-flight input mutation would create a confusing "this poster does not match my inputs" experience once the result arrives. Disabling the tab during the one-shot in-flight window is the simplest way to prevent that divergence.

**Independent Test**: Click Generate with valid inputs. While the loading indicator is visible (and before the response resolves), try to click the Setup tab, press Tab+ArrowLeft to focus it, and press Enter/Space. Confirm the alter-ego panel keeps showing its loading state. Wait for the response. Confirm Setup becomes reachable again.

**Acceptance Scenarios**:

1. **Given** the user pressed Generate and the app is in the generating state, **When** the user clicks the "1 Setup" tab, **Then** the alter-ego panel's loading state remains visible and the tab's aria-selected state does not change.
2. **Given** the app is generating, **When** the user attempts to focus the Setup tab via keyboard and press Enter/Space, **Then** the activation is ignored and the disabled visual + aria-disabled="true" is present on the Setup tab.
3. **Given** the generation resolves successfully, **When** the promise resolves, **Then** the Setup tab transitions from disabled to enabled within the same UI update (no second click or refresh required).
4. **Given** the generation resolves with the fallback (failed-with-fallback state), **When** the fallback lands, **Then** the Setup tab also transitions from disabled to enabled (parity with success).

---

### User Story 3 — After a generation resolves, both tabs are free to switch between (Priority: P2)

Once the user has a result (real or fallback), the two tabs behave like normal tabs: either one can be activated from either one, repeatedly, with no further gating.

**Why this priority**: This is the "return to normal" post-generation state. Without it the gating rules from US1/US2 would feel sticky.

**Independent Test**: Drive the app to a resolved state (succeeded or failed-with-fallback). Click between the two tabs several times in both directions. Use keyboard (Tab to enter, ArrowLeft/ArrowRight, Enter/Space) to activate each tab. Each activation should work instantly with no gating.

**Acceptance Scenarios**:

1. **Given** the alter-ego tab is visible and the phase is succeeded, **When** the user clicks Setup, **Then** Setup becomes visible; **When** the user clicks the alter-ego tab again, **Then** the alter-ego panel re-appears — poster content preserved (panel state survives tab switches).
2. **Given** the phase is failed-with-fallback, **When** the user switches tabs back and forth, **Then** both activations succeed with no visible gating.

---

### User Story 4 — Start-over returns to the initial locked state (Priority: P2)

After a generation has resolved, the alter-ego panel exposes a Start-over button. Clicking it must put the user back into the exact starting state: Setup is active and enabled, alter-ego is disabled again (because there is no longer a generated image).

**Why this priority**: Start-over is advertised as "reset to a clean slate". If the alter-ego tab stayed enabled after a reset, the user could click it and see a blank / inconsistent panel — the same bug US1 fixes, re-introduced on Start-over.

**Independent Test**: Run a full generation. Click Start-over. Confirm the active tab is Setup, the alter-ego tab is disabled (same visual + ARIA as on first load), and all Setup fields are reset to their empty defaults.

**Acceptance Scenarios**:

1. **Given** the phase is succeeded (or failed-with-fallback) and Start-over is visible, **When** the user clicks Start-over, **Then** the active tab is Setup AND the alter-ego tab is disabled and unreachable by click or keyboard.
2. **Given** Start-over has just fired, **When** the user tries to click the alter-ego tab, **Then** nothing happens — identical behaviour to a fresh page load.

---

### User Story 5 — Setup tab label is capitalised (Priority: P3)

The visible label on the first tab reads "1 Setup" (capital S) instead of "1 setup". The numeric prefix stays.

**Why this priority**: Purely cosmetic copy fix. Kept separate from US1–US4 so it can land even if the gating work slips.

**Independent Test**: Load the app. Read the first tab's label. It says "1 Setup".

**Acceptance Scenarios**:

1. **Given** the app just loaded, **When** the user looks at the first tab, **Then** the visible text is "1 Setup" (capital S). The accessible name announced by screen readers is also "1 Setup".

---

### Edge Cases

- **Alter-ego is the active tab and a re-Generate is triggered**: After the first generation resolves the alter-ego tab is reachable and the user might be standing on it. If a flow ever re-dispatches Generate (e.g. future "regenerate" button), the generating state must re-disable the Setup tab until the second response lands. The alter-ego tab stays enabled throughout (that's where the loading state lives). [Not a currently triggered flow — documented for consistency.]
- **Active tab becomes disabled**: If the gating rules ever leave the currently-active tab as the disabled one (e.g. user is on Setup when generating begins, or the system would render the alter-ego tab as active while it's still disabled), the active tab MUST be automatically switched to the still-enabled sibling in the same UI update so the DOM invariant "the active tab is always enabled" always holds. Today the flow naturally switches to the alter-ego tab on Generate, so this is a defensive rule rather than a user-visible change, but it closes the gap.
- **Roving tabindex with the active tab disabled**: Never possible by construction of the previous rule; if it ever occurred, the tablist must still be reachable by Tab from the rest of the page. Focus then lands on the enabled sibling.
- **Screen reader announcement**: When the user tabs into a disabled tab header (e.g. via AT virtual-cursor navigation), the element must announce itself as a disabled tab, not as a plain button.
- **Prefers-reduced-motion users**: Disabling/enabling is a state change, not an animation. No motion is used to transition between the two states; the change is immediate. (The existing 005 generate-triggered entrance animation is untouched.)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-501**: System MUST display the first tab's visible label as "1 Setup" (capital S). The numeric prefix "1 " remains part of the label. The accessible name (used by screen readers) MUST match the visible label exactly.
- **FR-502**: System MUST expose an "enabled" / "disabled" state per tab. A tab in the "disabled" state MUST:
  - Be visually distinguishable from an enabled tab (reduced opacity and/or distinct greyed treatment) AND NOT rely on colour alone for that distinction.
  - Use a "not-allowed" mouse cursor while hovered.
  - Expose `aria-disabled="true"` to assistive technology.
  - Be skipped by the tablist's roving-tabindex focus cycle (tabindex="-1" AND NOT the entry point when Tab-ing into the tablist).
  - Ignore click, Enter, and Space activations (no selection change, no aria-selected flip).
- **FR-503**: System MUST render the "2 Your Alter Ego" tab in the disabled state when no generation has ever resolved in the current session (i.e. phase ∈ {idle, picking, generating}).
- **FR-504**: System MUST render the "1 Setup" tab in the disabled state while a generation is in flight (phase === generating).
- **FR-505**: System MUST render both tabs in the enabled state once any generation has resolved (phase ∈ {succeeded, failed_with_fallback}) AND keep them enabled until the session is reset.
- **FR-506**: System MUST, on Start-over, restore the pre-generation gating state: Setup enabled + active, alter-ego disabled.
- **FR-507**: System MUST guarantee that the currently-active tab is always in the "enabled" state. If a state change would leave the active tab disabled, the system MUST switch the active tab to the enabled sibling as part of the same UI update.
- **FR-508**: System MUST preserve the WAI-ARIA "Tabs with manual activation" pattern established in feature 002 — only the activation gating is added. Enabled tabs continue to respond to click, Enter, Space, ArrowLeft/ArrowRight (move focus), and Home/End (jump focus). Disabled tabs are skipped during ArrowLeft/ArrowRight navigation but still render in the tablist in their documented position.
- **FR-509**: System MUST NOT introduce any new network request, persistence, or background timer as a result of this change. The gating is a pure function of the existing session phase.

### Key Entities *(include if feature involves data)*

- **Tab disabled-state**: A derived boolean per tab, not a new field on the session. Computed at render time from the existing `activeTab` and `phase` fields. No storage, no new action, no new reducer branch required.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-501**: On a fresh session, 100% of attempts to click or keyboard-activate the alter-ego tab prior to the first resolved generation leave the user on Setup (zero successful activations).
- **SC-502**: While a generation is in flight, 100% of attempts to click or keyboard-activate the Setup tab leave the alter-ego loading state visible (zero successful activations).
- **SC-503**: Once a generation has resolved, ≥ 99% of intended tab switches complete within 100 ms of the user action (matches the "instant switch" performance of the current tablist; gating adds no measurable latency).
- **SC-504**: Accessibility audit (axe-core or equivalent) reports zero new violations on the tablist after the change; both tabs remain reachable to screen readers at all times (a disabled tab is still in the accessibility tree, just marked as disabled).
- **SC-505**: After Start-over, 100% of sessions show the alter-ego tab as disabled and the Setup tab as the active tab on the next paint.

## Assumptions

- The only two top-level tabs are "1 Setup" and "2 Your Alter Ego" (status quo after feature 002). This feature does not add or reorder tabs.
- The session reducer's existing `phase` field (idle | picking | generating | succeeded | failed_with_fallback) is the single source of truth for "has a generation resolved?". No new state is added; no persistence is introduced.
- The existing Start-over behaviour already resets `activeTab` back to 'setup' and `phase` back to 'idle' (via `StartOverRequested → initialAlterEgoSession()`). This feature relies on that and does not change the reducer contract.
- The existing generate-triggered auto-switch from Setup → alter-ego continues to fire on Generate click, preserving the 005 entrance animation. This feature does not alter when that dispatch happens.
- "Disabled" is a purely visual + ARIA + focus-skipping state; no business-logic side effects (e.g. no "queued" activations that apply later).
- The feature is frontend-only. No backend, contract, or OpenAPI change is required.
- `prefers-reduced-motion` is honoured by the existing 005 animation; this feature introduces no new motion.

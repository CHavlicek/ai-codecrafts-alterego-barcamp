# Feature Specification: Hide Vibe and Pose Categories from the Setup UI

**Feature Branch**: `020-hide-vibe-pose`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "checkout github project issue #51"

GitHub issue [#51 — Remove Categories Vibe and Pose From UI](https://github.com/squer-solutions/aiavatar/issues/51):

> User should no longer be able to choose options from the categories Vibe and/or Pose from the UI.
> These categories should be removed from the UI completely.
> The ordering numbers should be updated accordingly for the remaining categories.
> Vibe and Pose categories should remain in the business logic on the backend.
> Each "generate alter ego" or "surprise me" request should use a random option from Vibe and a random option from Pose categories.
> All the other business logic should remain the same.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Simpler Setup tab without Pose and Vibe (Priority: P1)

As a participant standing at the kiosk, when I open the Setup tab I see three theme categories — **Archetype**, **Universe**, and **Art Style** — numbered 1, 2, 3 — plus the photo column and the Name field. I never see, hear, or have to interact with Pose or Vibe controls in the UI. After picking my three choices (and adding a photo + name), Generate becomes available and produces a poster that still feels rich and varied because the system has silently chosen a Pose and a Vibe for me.

**Why this priority**: This is the entire user-visible promise of the feature — removing two of five categories reduces decision load by 40 % and is the only change a participant will perceive. Without this slice the feature is not delivered.

**Independent Test**: Open the Setup tab on a clean session in any supported browser. Confirm only three numbered theme groups are rendered (Archetype = 1, Universe = 2, Art Style = 3); no element labelled "Pose" or "Vibe" appears anywhere in the Setup tab; the page reads correctly with a screen reader and the Generate button enables once photo + Archetype + Universe + Art Style + first name are all set.

**Acceptance Scenarios**:

1. **Given** a fresh kiosk session with no selections, **When** the user opens the Setup tab, **Then** the right-hand column shows exactly three numbered theme sub-groups labelled "1 Archetype", "2 Universe", "3 Art Style" — in that order — followed by the Name field and the Generate / Surprise Me actions.
2. **Given** a fresh kiosk session, **When** the page is inspected for any control bound to Pose or Vibe, **Then** none is present in the DOM, the tab order, or the accessible name tree (including hidden / off-screen elements) — the categories are removed, not merely visually hidden.
3. **Given** a user has set a photo, an Archetype, a Universe, an Art Style, and a first name, **When** they look at the Generate button, **Then** it is enabled (Pose and Vibe are no longer prerequisites for readiness).

---

### User Story 2 — Generate uses a random Pose and Vibe behind the scenes (Priority: P1)

When the participant presses **Generate my alter ego**, the system fills in one Pose and one Vibe by picking uniformly at random from the same option lists that used to be presented in the UI, and then runs the existing generation pipeline with those picks. The resulting poster is visually consistent with what would have been produced if the user had picked those same values manually.

**Why this priority**: Without this slice, removing the UI breaks the prompt the backend expects and the feature does not work end-to-end. It must ship together with Story 1.

**Independent Test**: With Pose and Vibe controls absent from the UI, press Generate after setting photo + Archetype + Universe + Art Style + first name. Confirm a poster is produced; confirm by inspecting the request body sent for image generation (or the backend's structured logs) that a Pose value and a Vibe value were included on every request and that, across many repeated runs of the same setup, the chosen Pose and Vibe values vary and cover the full set of options roughly uniformly.

**Acceptance Scenarios**:

1. **Given** a setup with photo + Archetype + Universe + Art Style + first name and no Pose or Vibe in the UI, **When** the user presses Generate, **Then** exactly one Pose option and exactly one Vibe option from the original supported lists are attached to the generation request before it is dispatched.
2. **Given** the same user input repeated N times (N ≥ 100), **When** Generate is pressed each time, **Then** every Pose option appears at least once and every Vibe option appears at least once across the runs (i.e. selection is from the full set, not a fixed subset).
3. **Given** a single Generate request, **When** that request is retried internally as part of the existing resilience / fallback flow, **Then** the Pose and Vibe values chosen for the user's request are not silently reshuffled mid-request — the user-facing outcome (success or fallback) corresponds to one consistent (Pose, Vibe) pair for that user action.

---

### User Story 3 — Surprise Me still randomises everything, including Pose and Vibe (Priority: P1)

When the participant presses **Surprise Me**, the system picks a value at random for every category the engine needs — including the now-hidden Pose and Vibe — and runs the same generation pipeline. The visible categories (Archetype, Universe, Art Style) are committed to the Setup tab as before so the participant can see what was picked for them; Pose and Vibe are not surfaced anywhere in the UI even though they are part of the randomised input.

**Why this priority**: Surprise Me is an existing flow whose contract (one click → fully randomised result) must continue to hold after Pose and Vibe leave the UI. Failing this slice would regress feature 009.

**Independent Test**: With photo + first name set and the three visible category selections empty, press Surprise Me. Confirm Archetype / Universe / Art Style are now visibly populated in the Setup tab, the generation kicks off automatically, and the active tab switches to "Your Alter Ego" exactly as it did before. Confirm by inspecting the dispatched generation request that a Pose and Vibe value were included.

**Acceptance Scenarios**:

1. **Given** photo + first name are set and the three visible categories are empty, **When** the user presses Surprise Me, **Then** Archetype, Universe, and Art Style become visibly selected in the Setup tab and a generation is dispatched within the same user action.
2. **Given** Surprise Me has just been pressed, **When** the resulting generation request is inspected, **Then** a Pose and a Vibe value are included even though no Pose / Vibe controls exist in the UI.
3. **Given** Surprise Me is pressed repeatedly (across separate sessions), **When** the picked values are recorded, **Then** all Pose options and all Vibe options appear over time (selection is from the full set).

---

### Edge Cases

- **Old session state with Pose / Vibe still set**: If a session has lingering Pose or Vibe values from before this change (e.g. an in-flight session held across a deploy), the UI MUST NOT render any control to display or modify them, and the backend MUST treat any user-supplied Pose / Vibe input as untrusted and replace it with a fresh random pick (no client-controlled "stickiness").
- **Start over**: Pressing Start over from the Alter Ego tab MUST reset the visible session exactly as before, except there is no Pose / Vibe state to reset because none is user-controllable. The next Generate will again draw fresh random Pose and Vibe values.
- **Random pick failure**: If the random-selection step fails for any reason (e.g. an empty Pose or Vibe option list ships in a misconfigured build), Generate MUST fail closed with the existing user-visible error path rather than dispatching a request with a missing Pose or Vibe.
- **Accessibility — focus order**: Removing the two grids MUST NOT leave focus traps, "skipped" tab stops, or stale `aria-describedby` / `aria-labelledby` references in the Setup tab.
- **Visual rhythm of the renumbered groups**: With three numbered theme groups instead of five, the right-hand column MUST still feel balanced — no obviously orphaned step number, no gap where a group used to be.
- **Print artefact**: The printed poster MUST continue to render exactly as today; the user-visible artefact does not surface the chosen Pose or Vibe to begin with, so this change is invisible on print.
- **Repeated Generate with identical visible setup**: Pressing Generate twice in a row with the same visible inputs is allowed to produce different posters (since Pose and Vibe are re-rolled each time). This is acceptable and expected — the participant is no longer choosing those axes.

## Requirements *(mandatory)*

### Functional Requirements

**UI removal — Setup tab**

- **FR-2001**: The Setup tab MUST NOT render any control, label, heading, helper text, error state, or accessible-only element associated with the "Pose" category. The Pose grid is removed, not hidden.
- **FR-2002**: The Setup tab MUST NOT render any control, label, heading, helper text, error state, or accessible-only element associated with the "Vibe" category. The Vibe grid is removed, not hidden.
- **FR-2003**: The Setup tab MUST present exactly three numbered theme sub-groups, in order: "1 Archetype", "2 Universe", "3 Art Style". No step number may be skipped and no group may carry a number other than its position in this list.
- **FR-2004**: The Name field MUST remain immediately below the three numbered groups, and the Generate / Surprise Me action row MUST remain immediately below the Name field.
- **FR-2005**: The photo column (Your Photo + composition-mode toggle) MUST remain unchanged in content and position.

**Readiness — Generate button**

- **FR-2010**: The Generate button MUST be enabled when, and only when, the user has supplied: a photo, a first name (non-empty after trimming, subject to the same validation as feature 011), an Archetype, a Universe, and an Art Style. Pose and Vibe MUST NOT be part of the readiness condition.
- **FR-2011**: The Generate button's disabled-state tooltip / helper text (if any) MUST NOT mention Pose or Vibe as missing inputs.

**Readiness — Surprise Me button**

- **FR-2015**: The Surprise Me button's gating condition is unchanged in spirit (photo + first name) and MUST NOT mention Pose or Vibe.

**Random server-side selection on Generate**

- **FR-2020**: On every Generate request, the system MUST attach exactly one Pose value to the underlying generation input. The value MUST be drawn from the complete set of supported Pose options that the original Pose grid used to expose to users.
- **FR-2021**: On every Generate request, the system MUST attach exactly one Vibe value to the underlying generation input, drawn from the complete set of supported Vibe options.
- **FR-2022**: The random selection MUST be uniform across the supported options (no biased weighting, no fixed subset). Over many requests, every option for both categories MUST be reachable.
- **FR-2023**: Each Generate request MUST roll a fresh independent (Pose, Vibe) pair. Two consecutive Generate clicks with identical user input MUST be allowed to produce different Pose and Vibe values.
- **FR-2024**: If the chosen Pose or Vibe option lists are empty or otherwise unavailable at request time, the system MUST fail the Generate action with the existing user-visible error path. It MUST NOT dispatch a partial request omitting Pose or Vibe.
- **FR-2025**: Any Pose or Vibe value supplied by the client (e.g. legacy session state, hand-crafted payload) MUST be ignored and replaced by a fresh server-side random pick. Client-controlled Pose / Vibe is not a supported input.

**Random selection on Surprise Me**

- **FR-2030**: Surprise Me MUST continue to populate Archetype, Universe, and Art Style on the visible session state with a uniformly random pick from each category's option list, exactly as today (this preserves feature 009's behaviour for the categories that are still visible).
- **FR-2031**: Surprise Me MUST result in a generation request that, like Generate, carries a fresh random Pose and Vibe pair. Whether those values are committed to a hidden corner of session state, or chosen at request-build time, is an implementation detail — but the request MUST carry them.

**Backend / business logic preservation**

- **FR-2040**: The supported set of Pose options and the supported set of Vibe options MUST be preserved as-is — same options, same names, same effect on the generation prompt. This change is UI-and-readiness only; the prompt-building logic for those categories MUST NOT change.
- **FR-2041**: All other generation behaviour — prompt structure, image-provider selection, overlay composition (logos, frame, text), resilience / fallback rules, no-persistence rules from feature 001 — MUST remain unchanged.
- **FR-2042**: Removing the UI controls MUST NOT change the public shape of any saved artefact (printed poster, downloadable image). The end participant sees the same kind of poster they saw before.

**Accessibility & navigation**

- **FR-2050**: The tab order through the Setup tab MUST be photo column controls → Archetype → Universe → Art Style → Name → Surprise Me → Generate, with no stale stops where Pose or Vibe used to be.
- **FR-2051**: No `aria-*` reference (`aria-labelledby`, `aria-describedby`, `aria-controls`, etc.) anywhere in the Setup tab MUST point at an id that belonged to the removed Pose or Vibe controls.
- **FR-2052**: The keyboard, screen-reader, and pointer experience for the three remaining grids MUST be identical to today's behaviour for those same grids.

**Hygiene**

- **FR-2060**: Any user-facing copy that listed the five Setup categories (placeholder hints, help text, error messages, marketing copy on the Alter Ego tab if present) MUST be updated to reflect the new three-category UI. Pose and Vibe MUST NOT be mentioned to participants.

### Key Entities

- **Visible category selection (Setup tab)**: The three theme picks the participant makes — Archetype, Universe, Art Style. Required for Generate readiness.
- **Hidden randomised input**: The Pose and Vibe values that the system chooses on behalf of the participant for each generation request. Not surfaced anywhere in the UI; treated as derived input, not user input.
- **Generation request**: The composed input to the image-generation pipeline. Continues to carry the full set of categories (Pose + Archetype + Universe + Art Style + Vibe) — only the *source* of two of those values changes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % of fresh Setup-tab page loads in supported browsers render exactly three numbered theme groups (Archetype = 1, Universe = 2, Art Style = 3) and zero elements referencing Pose or Vibe — verified by an automated assertion that fails if any control labelled or named Pose or Vibe is reachable in the Setup tab.
- **SC-002**: 100 % of Generate requests dispatched from the UI carry a non-empty Pose value and a non-empty Vibe value — verified by an automated assertion over the generation input.
- **SC-003**: Across at least 100 Generate runs from the same visible setup, every supported Pose option and every supported Vibe option is observed at least once.
- **SC-004**: Median time from first interaction on the Setup tab to Generate-button-enabled drops measurably for a first-time user, because two of the five mandatory picks are gone. Target: ≥ 25 % shorter than the pre-change median time-to-ready for a first-time user.
- **SC-005**: Zero accessibility regressions: an automated axe / lighthouse pass on the Setup tab reports the same or fewer issues than the pre-change baseline; no new focus trap, missing label, or dangling `aria-*` reference is introduced.
- **SC-006**: Zero change in the print artefact's visible content: pixel-diffing the printable poster output for an equivalent (Archetype, Universe, Art Style) input shows no structural deltas attributable to this change (variance is allowed for the new randomised Pose / Vibe choice, which is expected).

## Assumptions

- The kiosk uses a single shared device and a single short-lived session per participant; "no persistence" rules from feature 001 (FR-016 / FR-017 / FR-024) continue to apply unchanged — neither the chosen Pose / Vibe values nor the seed used to draw them are written to disk, cache, or logs.
- The supported option lists for Pose and Vibe are the same ones that the existing Pose grid and Vibe grid render today. This change does not edit, add, or retire individual options within those lists (in contrast to feature 019, which retired individual Art Style options). If the issue's author later asks to also prune options inside Pose or Vibe, that is a separate feature.
- Uniform random selection is the right default. The issue's wording ("a random option") is taken to mean uniform across the full option list. If stakeholders later prefer weighting (e.g. for "safer" defaults), that is a follow-up.
- Random selection happens server-side (or at the latest deterministic seam that the existing generation pipeline owns), so that a client cannot pin Pose or Vibe by crafting a request. The exact seam — request handler, service layer, prompt-builder — is left to `/speckit.plan`.
- The existing Surprise Me reducer transition (one atomic dispatch that commits all visible picks and then fires the generation) continues to be the right shape; the difference is simply that Pose and Vibe no longer participate in the *committed visible* picks. Whether they live in private session state, in a derived "request payload" object, or are drawn at request-build time is an implementation detail.
- The feature is constitution-compatible: no new third-party dependency, no new persistence layer, no change to image-provider contracts, no change to print or accessibility budgets beyond the explicit FRs above.
- The test runners and tooling already in the project (Vitest + React Testing Library on the frontend, JUnit 5 + Spring Boot Test on the backend) are sufficient — no new test infrastructure is required.

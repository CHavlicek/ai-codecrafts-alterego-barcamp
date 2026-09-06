# Feature Specification: Group Photos

**Feature Branch**: `011-group-photos` (developed on `claude/nice-brown-QNhLb`)
**Created**: 2026-04-24
**Status**: Draft
**Input**: GitHub issue [#10 — Group Photos](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/10). Issue text verbatim:
> A switch is added onto the UI that user can flip to choose a single person alter ego or a group photo.
>
> As a default the switch is in a single person position.
>
> If a switch is in a group photo position, the AI generator should be informed to produce a group-augmented photo, each person included in the final generated image.

## Clarifications

### Session 2026-04-24

- **Photo intake flow is unchanged**: The user still captures a single photo from the device camera (feature 004). In "Group" mode, that photo is expected to contain multiple people and the prompt instructs the model to render each of them as an alter ego. The UI does NOT orchestrate multiple capture sessions or stitch several photos. This keeps the 004 capture contract and the 003 request contract intact.
- **Name field semantics**: The single `firstName` input remains the only free-text field. It represents the group's collective display name (e.g. "The Architects", "Team Avocado"). The backend prompt uses it as the title for the group; the character card / superpowers / quote continue to be generated from it unchanged.
- **Single vs. Group is required, never `null`**: The session always holds one of the two modes. Default is `'single'`. The switch cannot be left "empty" — this mirrors how Pose / Archetype / Universe / Art Style behave (all required), not how Vibe behaves (optional, deselectable).
- **Backend wire contract extension is backwards-compatible**: The new field on `Selections` is optional at the Bean Validation layer and defaults to `SINGLE` server-side if absent. A client running an old bundle against a new backend continues to work exactly as before, so partial rollouts are safe. The frontend always sends the field once this feature ships.
- **No new network request, no new endpoint**: Everything rides inside the existing `POST /api/v1/alter-egos` multipart body under `selections.photoMode`. This matches every prior category addition (Vibe in 002, Art Style in 006).

### Session 2026-04-25 (post-design-review)

- **Switch placement**: The toggle lives in the **photo column**, directly beneath the camera intake, not in the selections column. Reasoning: the mode is metadata about the photo (how many subjects are in the frame), not a theme pick — co-locating it with the photo intake makes the visual story "what's in the photo" + "how to read what's in the photo" read top-to-bottom in one column.
- **Switch primitive**: Single sliding-thumb toggle ({@code role="switch"}, `aria-checked` reflects the on/off state) instead of a two-option radiogroup. Both **Single** and **Group** labels remain visible inside the track for affordance, but they are decorative ({@code aria-hidden}) — the accessible name comes from the visible "Photo Mode" heading via `aria-labelledby`. Group is the on (`aria-checked="true"`) state; Single is the off state. A live `role="status"` hint announces the current mode for assistive tech.
- **Visual styling**: The control re-uses existing design tokens — pill-shaped track at `--radius-pill`, gradient thumb in the brand cyan→purple→pink palette, mono small-caps label typography to match every other Setup heading, `--motion-ease-elastic` for the thumb slide, `prefers-reduced-motion` honoured via the global motion-duration override.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Single-person flow is unchanged when the switch stays in its default position (Priority: P1)

A returning user opens the app, captures a selfie, picks every category as usual, leaves the mode switch untouched (defaults to **Single Person**), and clicks Generate. The generated poster renders their face as a solo alter-ego portrait — byte-identical experience to the pre-feature baseline.

**Why this priority**: Group mode is an opt-in. The single-person path is 99% of the product today; breaking it with a default-switched flag is unacceptable. This slice proves the switch is purely additive.

**Independent Test**: Run the full Generate flow with the switch in its default position. Verify the Gemini prompt text still says "portrait poster of the person" (singular) and the request payload carries `photoMode: 'single'` (or omits it — both MUST be honoured by the backend). The poster MUST render a single subject matching the reference photo.

**Acceptance Scenarios**:

1. **Given** a fresh session, **When** the Setup tab renders, **Then** the mode switch is visible with **Single Person** selected by default and `aria-checked="true"` on the Single option.
2. **Given** the default mode is in effect, **When** the user clicks Generate with valid inputs, **Then** the outbound `selections` JSON carries `photoMode: "single"`.
3. **Given** the backend receives `photoMode: "single"` (or no photoMode field for forward-compat), **When** the prompt is built, **Then** the prompt text contains the singular phrasing "portrait of the person" (or equivalent singular wording) and does NOT contain the group-mode phrasing.
4. **Given** the switch is in Single mode, **When** the user toggles it and then toggles it back, **Then** the session state returns to `'single'` exactly — no intermediate `null` state is ever observable.

---

### User Story 2 — Group photo flow renders every person in the reference as an alter ego (Priority: P1)

A group of 3–5 colleagues at a booth huddle in front of the kiosk camera. One of them types a team name (e.g. "The Architects"), picks a role / universe / art style / pose, flips the switch to **Group Photo**, and clicks Generate. The generated poster shows every person from the reference photo re-rendered as the same alter ego archetype in the same universe / art style — a team portrait, not a single subject.

**Why this priority**: This is the entire feature. Without the group-aware prompt branch, flipping the switch has no visible effect and the user loses trust in the control. The acceptance scenarios below explicitly pin the prompt change so the model's instruction is deterministic.

**Independent Test**: Capture (or mock) a photo with 3+ faces visible, type "The Architects", pick a role / universe / art style / pose, flip the switch to **Group Photo**, click Generate. Confirm the outbound payload carries `photoMode: "group"`, the prompt template produces the plural wording "group portrait of the people" (or equivalent), and the rendered poster contains multiple figures (validated via the Gemini response; stub fallback poster is exempt because it does not call the model).

**Acceptance Scenarios**:

1. **Given** the user flips the switch to Group, **When** the Setup tab re-renders, **Then** `aria-checked="true"` moves to the Group option and the `aria-checked` value on Single becomes `"false"`.
2. **Given** the user clicks Generate with valid inputs and `photoMode === 'group'`, **When** the request fires, **Then** the outbound `selections` JSON carries `photoMode: "group"`.
3. **Given** the backend receives `photoMode: "group"`, **When** the prompt is built, **Then** the prompt text contains the plural phrasing "group portrait of the people" (or equivalent) and the composition note refers to "all subjects" rather than "the subject".
4. **Given** Group mode is active, **When** the generation resolves successfully (real provider, not fallback), **Then** the returned poster renders ≥ 2 subjects — validated via an integration test against the stub/mock Gemini client whose prompt assertion is the source of truth (the rendered pixel count of faces is not tested by this project).

---

### User Story 3 — Switch is keyboard-operable and accessible (Priority: P2)

A user navigating the Setup tab by keyboard tabs through the form and reaches the mode switch before Pose. They press the Right Arrow to move focus from Single to Group, then Space (or Enter) to activate, then Tab away to continue. A screen reader announces "Group Photo, radio, checked" after activation.

**Why this priority**: The rest of the form is fully keyboard-operable (tab order, Arrow keys within grids, Space/Enter to activate). A new control that breaks this contract would regress 002's accessibility baseline.

**Independent Test**: With the app focused, Tab to the mode switch, use Arrow keys to move between Single and Group, press Space to activate, then Tab out. Verify aria-checked flips correctly, focus moves correctly, and activation does NOT submit the form (the switch is a mode toggle, not a submit).

**Acceptance Scenarios**:

1. **Given** focus lands on the Single option, **When** the user presses ArrowRight / ArrowDown, **Then** focus moves to Group.
2. **Given** focus is on Group and it is not yet checked, **When** the user presses Space or Enter, **Then** the session mode becomes `'group'` and Group's `aria-checked` flips to `"true"`.
3. **Given** the user presses Space on an already-checked option, **When** the event fires, **Then** nothing happens — the mode is required and cannot be deselected (mirrors Pose / Archetype / Universe / Art Style, not Vibe).
4. **Given** any mode is selected, **When** the focus leaves the fieldset via Tab, **Then** the next focusable element is the first interactive element of Pose (Step 1) — tab order preserves the existing numbered-group flow.

---

### Edge Cases

- **Single photo with one person, switch set to Group**: The request still fires; the backend prompt still instructs group mode. The model may gracefully downgrade (render a single subject) or render the same person twice — both are acceptable outcomes because the UI told the model "group" and the photo said "one person". We do not inspect the photo server-side to auto-correct the mode.
- **Group photo, switch left in Single**: Same as above but inverted — the model is told "single subject" and the photo contains several. Most likely outcome: Gemini focuses on the most prominent face. We do not second-guess the user's mode choice.
- **Surprise Me + Group mode interaction**: Surprise Me (009) randomises the five category fields and reuses the current `photoMode`. If the user had set Group before clicking Surprise Me, the outbound payload carries `photoMode: "group"`. If they hadn't, it carries `photoMode: "single"` (the default). Surprise Me does NOT randomise the mode — the mode is a composition choice tied to the photo, not a theme pick.
- **Start-over**: Resets `photoMode` back to the default `'single'` alongside every other field. Matches the 009 behaviour for category selections.
- **Mode changes mid-session after a successful generation**: Changing the switch while the alter-ego tab shows a poster does NOT invalidate the poster. Changing a mode only affects the *next* Generate click. Matches how changing a category behaves today.
- **Fallback path**: The fallback poster (FR-214 / FR-215 / FR-218 from 003) is photo-mode agnostic — the stub image is a generic placeholder. The `meta.outcome` / `meta.reason` fields carry the same values regardless of `photoMode`. The session phase transitions remain identical.
- **Switch flipped while generation is in flight**: The switch is rendered inside the Setup tab, which is disabled during the `'generating'` phase per 007 FR-504 (tab access gating). So the user cannot actually change the mode mid-request — the 007 gate covers this for free.
- **Malformed / absent `photoMode` on the wire**: Backend Bean Validation treats `photoMode` as *optional*; when absent it defaults to `SINGLE`. When present but with an unknown enum value, Jackson's default StrictEnum behavior rejects the request with 400. This matches how other enums behave and is the already-tested path.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-1001**: The Setup tab MUST render a single sliding-thumb toggle ("Photo Mode") in the photo column, directly beneath the camera intake. The toggle MUST be visually distinct from the five thematic category grids in the right column — it is metadata about the photo, not a theme pick.
- **FR-1002**: The toggle MUST use `role="switch"` with `aria-checked` reflecting the on/off state (Group = on, Single = off). The accessible name MUST come from the visible "Photo Mode" heading via `aria-labelledby`. The visible "Single" / "Group" labels inside the track MAY appear for affordance but MUST be decorative (`aria-hidden`). A live `role="status"` hint MUST announce the current mode for assistive tech.
- **FR-1003**: The default value MUST be `'single'` (off) on first render and after `StartOverRequested`. The default MUST be reflected by `aria-checked="false"` on the toggle in the initial DOM.
- **FR-1004**: Clicking (or pressing Space / Enter on) the toggle MUST dispatch a single reducer action that flips `session.photoMode` to the opposite value in one transition. There is no "no-op click": every activation toggles.
- **FR-1005**: The session state shape MUST be extended with a required, non-null field `photoMode: 'single' | 'group'` defaulting to `'single'`. No new `null`-typed field — required & total, like Pose / Archetype / Universe / Art Style, not optional like Vibe.
- **FR-1006**: The action type added MUST be named `PhotoModeSelected`, MUST carry the new mode as a typed payload, and MUST NOT touch any other field on the session (same pattern as `PoseSelected` / `ArchetypeSelected`).
- **FR-1007**: The Generate button's readiness selector (`isReadyToGenerate`) MUST NOT be changed by this feature — `photoMode` is always set (always `'single'` or `'group'`), so it never enters the `missingInputs` list. Likewise `isReadyToSurprise` is unchanged.
- **FR-1008**: The outbound request body's `selections` JSON MUST carry `photoMode` as a top-level field with the wire value `"single"` or `"group"`. The field MUST be present on every outbound request from a client that ships this feature.
- **FR-1009**: The backend `AlterEgoRequest` DTO MUST add a field `photoMode` of a new enum type `PhotoMode { SINGLE, GROUP }`. The field MUST be *nullable* (no `@NotNull`) at the Bean Validation layer so an old-client request without the field is accepted and treated as `SINGLE`. A domain default of `SINGLE` applies downstream.
- **FR-1010**: The new enum MUST surface through `GeminiPromptBuilder` as a single branch point: the template MUST emit the singular phrasing (today's wording) for `SINGLE` and a plural-subject phrasing for `GROUP`. The plural phrasing MUST include the instruction to render *every* person visible in the reference photo and MUST NOT tell the model to invent additional people who are not in the photo.
- **FR-1011**: The Start-over reducer branch MUST reset `photoMode` back to `'single'` as part of the same transition that clears every other field. No new orchestration needed — resetting inside `initialAlterEgoSession()` covers this.
- **FR-1012**: The Surprise Me action (009) MUST NOT write to `photoMode`. The outbound payload from Surprise Me MUST carry whichever mode is currently in the session. Surprise Me randomises the five category fields only.
- **FR-1013**: The mode switch MUST remain keyboard-operable per the existing primitive: focusable when enabled, activatable by Enter / Space, Arrow keys move between the two options with wrap-around. When the containing tab is disabled (per 007), the switch MUST also become non-interactive — this happens for free because the disabled tab hides its panel from the tab order.
- **FR-1014**: No new HTTP endpoint, no new persistence, no new background job, no new third-party dependency. The no-persistence posture (001 FR-016 / 017 / 024, extended by 003 FR-215) continues to hold verbatim for `photoMode`.

### Non-Functional Requirements

- **NFR-1001**: The mode switch MUST render without introducing a visible layout shift on first paint of the Setup tab compared to the pre-feature baseline.
- **NFR-1002**: The prompt-builder branch MUST keep the full prompt within the existing `~768`-byte headroom (current `StringBuilder` initial capacity in `GeminiPromptBuilder.build`). The two prompt variants MUST each be ≤ 900 bytes so the initial capacity does not need to grow.
- **NFR-1003**: Test line coverage on new code (frontend reducer branch + backend enum + prompt variant) MUST meet the constitutional ≥ 90% bar.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-1001**: A user who never touches the mode switch gets an experience byte-identical to pre-feature — measurable by diffing the outbound prompt text against the 003/006 baseline (singular wording, same composition notes).
- **SC-1002**: A user who flips the switch to Group and clicks Generate sends `photoMode: "group"` on exactly 100% of requests — measurable by a Playwright test that intercepts the multipart body.
- **SC-1003**: The backend unit test for `GeminiPromptBuilder` produces exactly two distinct prompt strings for Single vs. Group (same 5-category inputs), with the Single vs. Group distinction being exactly the subject-count instruction and nothing else.
- **SC-1004**: `StartOverRequested` leaves `photoMode === 'single'` — measurable by a unit test on the reducer's reset branch.
- **SC-1005**: The mode switch passes axe-core accessibility scan with zero new violations on the Setup tab (a11y Playwright test).

## Assumptions

- **A-1001**: The user supplies ONE photo, which may contain one or more faces. The UI does not orchestrate multi-photo capture or server-side photo composition. This is the simplest reading of the issue ("A switch is added... each person included in the final generated image") and matches the 004 camera-only-capture posture.
- **A-1002**: The backend enum is named `PhotoMode` (not `GroupMode`, not `CompositionMode`). Wire values are kebab-case matching the existing convention (`"single"`, `"group"`). Renaming the enum later is a breaking wire change; we pin the names now.
- **A-1003**: The prompt-builder group variant tells the model to include *every person from the reference photo* as an alter ego (not "generate a group photo from scratch" and not "invent teammates"). The model is expected to map each visible face to the same archetype / universe / art style / pose — a uniform treatment. This matches the issue's "each person included in the final generated image" wording.
- **A-1004**: The control is a 2-option `role="radiogroup"` (matching existing grids) rather than a `role="switch"` checkbox. Rationale: (a) the label "switch" in the issue is product wording, not ARIA; (b) a radiogroup surfaces both options' labels to AT users without requiring them to toggle to discover the alternative; (c) it matches every other composition choice on this form.
- **A-1005**: The label copy is "Single Person" / "Group Photo" with the group-mode icon being `Users` from lucide-react (a 2-person glyph). Single-mode icon is `User`. These are stable lucide icons and do not introduce a new dependency.

## Dependencies

- **D-1001**: Builds on feature 002 (tabbed Setup UI) — the switch lives in the Setup tab's selections column.
- **D-1002**: Builds on feature 003 (Gemini image generator) — the prompt branch is the only meaningful behavioural change and it rides inside `GeminiPromptBuilder`.
- **D-1003**: Integrates with feature 004 (camera capture) — the single photo captured there is the "reference photo" both modes use.
- **D-1004**: Integrates with feature 007 (tab access gating) — the switch is rendered inside the Setup tab and is automatically non-interactive while `phase === 'generating'`.
- **D-1005**: Integrates with feature 009 (Surprise Me) — Surprise Me does NOT randomise `photoMode`; the outbound payload from a Surprise Me click carries the currently-selected mode.

## Out of Scope

- Server-side detection of how many faces are in the reference photo. The user picks the mode; we trust them.
- A "Best of both" mode (e.g. generate a group photo with only the primary subject in colour). The switch is strictly binary.
- Remembering the last-chosen mode across Start-over. Start-over resets to `'single'`, matching every other session field.
- Different `firstName` semantics for group mode (plural names, comma-separated lists, etc.). The Name remains a single string; for a group it represents the group's collective name.
- Per-person attribute customisation (one archetype per teammate, different vibes). All five category choices apply uniformly to every person in the reference photo.

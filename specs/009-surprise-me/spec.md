# Feature Specification: Surprise Me Button

**Feature Branch**: `009-surprise-me` (developed on `claude/speckit-implementation-D6gzi`)
**Created**: 2026-04-24
**Status**: Draft
**Input**: GitHub issue [#9 — Surprise Me Button](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/9). Issue text verbatim:
> Add "Surprise Me" button onto the UI.
> Clicking bypasses strict requirement to select at least one option from each of the available categories and fires a randomizer for the categories.
> "Name" is still a required field.
> Name and randomly chosen category options are passed into the API call to the AI image generator API as usual.
> The other logic works the same way as a normal flow, when user selects one option from each of the categories and clicks "Generate my alter ego"

## Clarifications

### Session 2026-04-24

- Coverage scan found no high-impact ambiguities left unresolved; the five candidate decision points (Vibe included in the randomization set; overwrite-vs-fill-in semantics for existing picks; whether the photo gate applies; random-source strength; backend contract change) are all resolved in-spec via Assumptions (A-901..A-905) and FR-902/FR-905/FR-911. No interactive questions were required.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — "Surprise Me" generates an alter ego with no category picks (Priority: P1)

A user lands on the Setup tab, uploads (or captures) their photo, types a name, and clicks the new **Surprise Me** button without selecting any Pose / Engineer role / Universe / Art style / Vibe. The app picks one option per category at random, submits the same request the normal Generate button would have submitted, and transitions to the "Your Alter Ego" tab. The returned poster reflects the randomly chosen categories.

**Why this priority**: This is the entire feature. Without this slice, there is no Surprise Me — a "Surprise Me" button that still required the full category pick would be indistinguishable from the existing Generate button.

**Independent Test**: Open the app, take a selfie, type a name, leave every category grid blank, click Surprise Me. Confirm the request payload contains a valid value for `pose`, `archetype`, `universe`, `artStyle`, and `vibe`, and that the response poster renders in the alter-ego tab the same way a normal Generate response does.

**Acceptance Scenarios**:

1. **Given** a photo is present and a non-empty name is typed, and all five category grids are empty, **When** the user clicks Surprise Me, **Then** the app dispatches the same submission sequence the Generate button triggers (tab auto-switch → generating phase) and the outbound request payload carries a valid `pose`, `archetype`, `universe`, `artStyle`, and `vibe`.
2. **Given** the request resolves successfully (or with fallback), **When** the response lands, **Then** the Your Alter Ego panel renders the returned poster exactly as it would for a Generate click — no new UI path, no new error message, no new banner.
3. **Given** the post-generation tab gating rules from 007 are in effect, **When** Surprise Me resolves, **Then** both tabs become enabled and the active tab is "Your Alter Ego" — identical to the normal Generate flow.

---

### User Story 2 — Surprise Me reflects the randomly chosen options back in the Setup form (Priority: P1)

After clicking Surprise Me, the user navigates back to the Setup tab (once it re-enables after the response lands) and sees that Pose, Engineer role, Universe, Art style, and Vibe each now show a selected option — the ones the randomizer picked. This closes the feedback loop so the user can see what they got, tweak a single picker, and click Generate again for a targeted re-roll.

**Why this priority**: Without this, the outcome of a Surprise Me click is a black box — the user only sees the poster, not which categories the model was steered toward. That breaks the "I got something I like, let me refine one thing" retry loop and is explicitly called out in the feature description ("Once 'Surprise Me' resolves, the Setup form reflects the randomly chosen options"). This slice is as critical as US1 because a Surprise Me flow without state sync is a worse product than no Surprise Me at all.

**Independent Test**: After a successful Surprise Me, switch back to the Setup tab. Confirm exactly one option is highlighted in each of the five grids. Click the normal Generate button — confirm the request body matches the selections currently highlighted (no secret "shadow" state).

**Acceptance Scenarios**:

1. **Given** the user just clicked Surprise Me and the app is transitioning to the alter-ego tab, **When** the session state updates, **Then** `pose`, `archetype`, `universe`, `artStyle`, and `vibe` each hold the randomly chosen value in the session.
2. **Given** the user returns to the Setup tab after a Surprise Me resolves, **When** the Setup panel re-renders, **Then** each of the five grids shows the corresponding randomly chosen option as its `aria-checked` radio.
3. **Given** the user re-rolls one category (e.g. changes Art style) and clicks the normal Generate button, **When** the request fires, **Then** the payload contains the four previously-randomised values plus the user's manual override — i.e. Surprise Me seeds the form and a subsequent Generate is a normal Generate against that seeded state.

---

### User Story 3 — Surprise Me respects Name and photo gating (Priority: P2)

The Surprise Me button is only clickable when both (a) a photo has been supplied AND (b) the Name field is non-empty. If either is missing, the button is visually disabled, keyboard-unfocussable in its disabled state (`aria-disabled="true"` + `disabled`), and clicking / pressing Enter on it is a no-op. The disabled-state hint lists exactly which of Name and photo is missing — it does NOT list Pose / Engineer role / Universe / Art style / Vibe, since those are intentionally bypassed.

**Why this priority**: The issue text explicitly carves out Name ("'Name' is still a required field") but leaves the photo gating implicit. The photo is upstream of any image generation — there is no meaningful request to make without one — so treating it as required alongside Name is the only coherent reading. Disabling the button for the same two inputs as the normal Generate also matches the "other logic works the same way" line from the issue.

**Independent Test**: In a fresh session, confirm Surprise Me is disabled and that the hint reads "Still needed: photo and a name." (or equivalent). Supply only a photo → hint updates to "a name." Supply only a name → hint updates to "photo." Supply both → button enables. At every step, try clicking: confirm no navigation and no request fires until the button is enabled.

**Acceptance Scenarios**:

1. **Given** no photo and no Name, **When** the Setup tab renders, **Then** the Surprise Me button is disabled (`disabled` attribute present, `aria-disabled="true"`) and the visible hint identifies both as missing.
2. **Given** the user types a Name but still has no photo, **When** the Setup tab re-renders, **Then** the Surprise Me button stays disabled and the hint identifies only the photo.
3. **Given** the Surprise Me button is disabled, **When** the user clicks it (including via a programmatic `.click()`), **Then** no request fires, no tab switch happens, and no session dispatch occurs.
4. **Given** the app is currently generating (the normal Generate click is in flight), **When** the user looks at Surprise Me, **Then** Surprise Me is also disabled — exactly one generation request can be in flight at a time, matching the normal Generate button's behavior.

---

### Edge Cases

- **Photo present, Name empty**: Surprise Me is disabled. Hint reads "Still needed: a name." (same wording the normal Generate would produce if the only missing input were the Name).
- **Photo missing, Name present**: Surprise Me is disabled. Hint identifies the photo as the only missing input.
- **User has partially selected categories then clicks Surprise Me**: Surprise Me overwrites every category with a fresh random pick. Any prior user choices are replaced. The rationale: the button is labelled "Surprise Me", not "Fill in the blanks" — keeping partial picks would make the random behavior non-deterministic from the user's perspective (they'd have to remember what they picked to predict the payload).
- **Same category is re-rolled and lands on the same value**: Acceptable — the randomizer is memoryless. The session state still technically changes (the "selected" flag was already set), and the request still fires normally. No special UI message.
- **Click Surprise Me twice rapidly (double-click)**: The second click is ignored — Surprise Me is disabled as soon as the first click transitions the phase to `generating`. Matches normal-Generate behavior.
- **Click Surprise Me while the app is already generating**: Disabled — no second request. Same rule as the normal Generate button.
- **Real provider unavailable, fallback path kicks in**: Unchanged from 003 FR-214 — the generic fallback notice appears, and the Setup form still shows the randomly seeded selections from US2. The fallback path does not affect the randomization.
- **Vibe-randomised-to-a-value interaction with the optional-vibe deselect toggle (002)**: Surprise Me always picks exactly one Vibe (not null). A user can then return to Setup and click the already-selected Vibe pill to deselect it back to null, before re-clicking Generate. No regression to 002 FR-118 / FR-122.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-901**: The Setup tab MUST render a "Surprise Me" button in the vicinity of the existing "Generate my alter ego" button. The button MUST be distinguishable from Generate (separate accessible name, distinct visual treatment) so assistive tech and sighted users can tell the two actions apart.
- **FR-902**: The Surprise Me button MUST be enabled if and only if (a) a photo is present in the session AND (b) the trimmed Name is non-empty AND (c) the session is not currently in the `generating` phase. Any other combination of category selections (including none) MUST leave the button enabled.
- **FR-903**: When the Surprise Me button is in its disabled state, it MUST expose `disabled` (HTML attribute), `aria-disabled="true"`, and a visible hint explaining what is still missing. The hint MUST list only Name and/or photo — NEVER the five category grids.
- **FR-904**: Clicking an enabled Surprise Me MUST pick exactly one option per category at random, independently, from the canonical option lists defined in `frontend/src/features/alterego/options.ts`: `POSE_OPTIONS`, `ARCHETYPE_OPTIONS`, `UNIVERSE_OPTIONS`, `ART_STYLE_OPTIONS`, and `VIBE_OPTIONS`. Each pick MUST be drawn from a uniform distribution over its category (no weighting).
- **FR-905**: The randomization MUST overwrite any existing category selection in the session (including prior user picks from the same session). Surprise Me is "full random", not "fill in the blanks".
- **FR-906**: After the random picks are committed to the session, Surprise Me MUST dispatch the same sequence of actions the normal Generate button triggers: `ActiveTabChanged → 'alter-ego'` (reason: `'generate'`) followed by the mutation submission. The outbound request body MUST be shaped identically to a normal Generate request (same `Selections` contract, same photo bytes, same correlation ID scheme).
- **FR-907**: The randomly chosen category values MUST be persisted to the session state BEFORE the request fires, so that (a) the Setup form reflects the picks when the user returns to it, and (b) if the user clicks Generate afterwards, the payload matches what the Setup form visibly holds.
- **FR-908**: Post-generation behavior (phase transitions, Your Alter Ego tab auto-switch, tab gating per 007, Start-over reset per 002) MUST be byte-identical between a normal Generate click and a Surprise Me click. Surprise Me MUST NOT introduce a new phase, a new action type, or a new animation.
- **FR-909**: The randomization MUST use a uniform, non-deterministic source (no fixed seed, no memoization). Back-to-back Surprise Me clicks MUST be free to produce different results; identical results across two clicks are possible but are not guaranteed.
- **FR-910**: Start-over MUST reset the session to the initial state unchanged from today — including clearing any category values that Surprise Me wrote. No new Start-over semantics are required.
- **FR-911**: No new network request, persistence, or background timer MAY be introduced by Surprise Me. The feature MUST be implemented as a client-side seed followed by the existing Generate path. The no-persistence posture (001 FR-016 / 017 / 024, extended by 003 FR-215) is preserved verbatim — the randomized picks live only in in-process session state for the session's lifetime.
- **FR-912**: The Surprise Me button MUST remain keyboard-operable per existing primitives: focusable when enabled, activatable by Enter and Space, skipped in its disabled state if that matches the surrounding fieldset's tab order. Accessibility parity with the normal Generate button is required.
- **FR-913**: When the user changes the photo or the Name AFTER a Surprise Me click (e.g. retakes the selfie from the Your Alter Ego tab's Start-over → re-seeded flow, or edits the Name), the seeded category values MUST remain in the session until explicitly overwritten by a subsequent Surprise Me click, a manual grid selection, or Start-over. Photo/Name changes on their own MUST NOT clear the seeded categories.

### Non-Functional Requirements

- **NFR-901**: Randomization MUST complete in under 1 ms on a typical device. This is trivially met by a single `Math.random()` call per category, but is called out so any future "weighted pick" refactor keeps the budget in mind.
- **NFR-902**: The Surprise Me UI MUST render with no layout shift on the Setup tab compared to the pre-feature layout (the button joins the existing action row; it does not push the Generate button off screen on narrow viewports).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-901**: A user with a photo and a Name can generate an alter ego in **one additional click** (Surprise Me) with **zero category decisions** — measurable by counting clicks between "photo captured + name typed" and "poster visible".
- **SC-902**: 100% of Surprise Me requests that reach the backend carry a valid enum value for each of `pose`, `archetype`, `universe`, `artStyle`, and `vibe` — measurable by the existing Bean Validation + Jackson rejection count staying at zero for Surprise Me-originated requests.
- **SC-903**: After a Surprise Me click resolves, returning to the Setup tab shows exactly one selected option in each of the five category grids — measurable by an automated DOM check (`aria-checked="true"` in each grid).
- **SC-904**: The time-to-poster for a Surprise Me click is within 5% of the time-to-poster for a fully hand-picked Generate click on the same environment, since the only added work is a handful of random-index lookups.

## Assumptions

- **A-901**: Vibe is randomized alongside the four strictly-required categories, even though Vibe is optional in the normal flow (002 FR-118 / FR-122). Rationale: the issue text says "fires a randomizer for the categories" — Vibe is listed as a category on the Setup tab, and omitting it would make "Surprise Me" feel half-hearted. The user retains the ability to deselect Vibe back to null (optional-category toggle from 002) before a subsequent Generate click.
- **A-902**: Random selection uses the Web Platform's `Math.random()`. Cryptographic-strength randomness (`crypto.getRandomValues`) is not required — there is no security or fairness guarantee being made, only "feels random enough to be a surprise".
- **A-903**: The randomization is implemented entirely on the frontend. The backend contract does not change: the server receives a fully-populated `Selections` object regardless of whether a human or the randomizer picked the values.
- **A-904**: The Surprise Me button is placed on the Setup tab next to the Generate button (not in a floating header / menu / toolbar). Specific layout is a design call; this spec only requires it be reachable on the Setup tab and visually grouped with Generate.
- **A-905**: The "Name required" rule for Surprise Me is identical to the Name rule for Generate: non-empty after trimming whitespace (same `missingInputs` check used today, less the category entries).

## Dependencies

- **D-901**: Builds on feature 006 (Art style category) — `ArtStyle` is one of the categories Surprise Me must randomize.
- **D-902**: Integrates with feature 002 (Tabs shell) and 007 (Tab access gating) — Surprise Me triggers the same auto-switch-to-alter-ego-tab path and the same post-generation re-enable path.
- **D-903**: Integrates with feature 003 (Gemini image generation) — Surprise Me reuses the `useGenerateAlterEgo` mutation and the existing fallback semantics unchanged.
- **D-904**: Integrates with feature 004 (Camera-only photo capture) — photo gating (FR-902 clause (a)) reuses today's `photoBlob` presence check; no change to camera capture.

## Out of Scope

- Persisting which categories were seeded by Surprise Me vs picked by the user (no audit trail; the session only stores the current values).
- A "Surprise Me for just this category" per-grid button — out of scope for this feature; the re-roll loop is "change one grid manually, click Generate".
- Weighted or themed randomization (e.g. "match my archetype to my vibe"). The randomizer is uniform and independent per category.
- Server-side randomization. The backend contract is unchanged.
- Animating the Setup form picks as they appear when Surprise Me is clicked. The picks are committed in the same React commit as the tab auto-switch, so the user sees the final state instantly when they come back to Setup — no per-pick animation is in scope.

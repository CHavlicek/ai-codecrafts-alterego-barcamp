# Feature Specification: Partial Surprise Me — preserve explicit picks

**Feature Branch**: `028-partial-surprise-me`
**Created**: 2026-05-20
**Status**: Draft
**Input**: User description verbatim:
> User should be able to choose none or some of the categories and then press the "Surprise Me" button. If the user has chosen some of the categories explicitly, those ones should not be randomized by the "Surprise Me" feature.

## Clarifications

### Session 2026-05-20

- This is a behavioral pivot of the 009 Surprise Me action — its existing reducer comment is "full-random, not fill-in-the-blanks." This feature inverts that stance: explicit category picks are preserved through Surprise Me; only unchosen categories are randomized. No new UI surface — the existing Surprise Me button is reused.
- The custom-role text input (022) counts as an explicit Role choice when its trimmed value is non-empty. A prefab Role pick (022 / 026) also counts as explicit. The Role category is "empty" iff no prefab is selected **and** the custom-role text is blank after trim. This replaces 009's behavior where Surprise Me unconditionally cleared `customRole` and rolled a prefab on top.
- Photo + first-name gating for the Surprise Me button is unchanged from 009 / US3 — both are still required to enable the button. Categories continue to be exempt from the gate (the whole point of Surprise Me is to bypass category gating).
- Q: On a second Surprise Me click, should values that the first Surprise Me rolled be re-rolled or preserved? → A: **Preserved.** Any non-empty category value — whether typed/clicked by the user or written into the session by a previous Surprise Me — counts as *explicit* on the next Surprise Me click. No provenance is tracked. The only mechanisms that clear category values are the **Start Over** button and a **browser page refresh**; Surprise Me itself never clears anything. A user who wants a fresh roll after Surprise Me has populated every category must click Start Over (or refresh) first.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Picking one category and using Surprise Me to fill the rest (Priority: P1)

A user has a photo, types their name, and picks exactly one category that they care about — say, Universe = "Star Wars." They leave Role and Art Style empty. They press **Surprise Me**. The app picks a random Role (prefab Archetype) and Art Style on their behalf, **leaves Universe untouched at "Star Wars,"** and fires the same generation pipeline that Generate fires. The returned poster reflects the user's explicit Universe plus the randomly chosen Role and Art Style.

**Why this priority**: This is the entire feature. A Surprise Me that overwrites explicit picks is the current 009 behavior — this slice exists specifically to stop that overwrite. Without it the feature ships nothing.

**Independent Test**: Open the app, take a selfie, type a name, click "Star Wars" in the Universe grid, leave Role and Art Style empty, click Surprise Me. Verify (a) the outbound request payload's `universe` is `"star-wars"`, (b) `archetype` and `artStyle` carry valid randomly chosen values, (c) the response renders in the Alter Ego tab exactly the way a Generate response would.

**Acceptance Scenarios**:

1. **Given** a photo is present, the name is non-empty, the user has selected Universe = "Star Wars" and left Role + Art Style empty, **When** the user clicks Surprise Me, **Then** the outbound request carries `universe = "star-wars"`, a uniformly-randomly-chosen `archetype` from the prefab Role list, and a uniformly-randomly-chosen `artStyle` from the Art Style list.
2. **Given** the same starting state, **When** the user returns to the Setup tab after the response lands (per 007's tab gating), **Then** the Universe grid still shows "Star Wars" selected — exactly the value the user picked, not a re-rolled value — and Role and Art Style each show the randomly chosen option highlighted.
3. **Given** the user repeats this flow ten times with a **Start Over click between each iteration** (Start Over clears the session per FR-2814, then the user re-picks Universe = "Star Wars" and leaves Role + Art Style empty before clicking Surprise Me), **When** each Surprise Me resolves, **Then** Universe stays "Star Wars" within each iteration (zero overwrites) and the rolled Role / Art Style values **across** the ten iterations span more than one distinct value each (the randomizer is actually rolling, not stuck on a single option).

---

### User Story 2 — Surprise Me preserves a non-empty Custom Role (Priority: P1)

A user types a free-form Custom Role (e.g., "Distinguished Spreadsheet Wrangler") into the text input below the Role grid. The prefab Role grid is blurred and non-interactive (the precedence rule from 022). They leave Universe and Art Style empty. They press **Surprise Me**. The Custom Role string is preserved as the role-of-record, no prefab Role is picked, and the empty Universe + Art Style get randomly chosen values. The poster reflects the custom string.

**Why this priority**: Without this, Surprise Me would silently destroy the user's typed text — exactly the regression the user-supplied description is trying to prevent. Equivalent in importance to US1 because the Custom Role channel is the second explicit-Role expression mechanism (022) and must be respected the same way as a prefab pick.

**Independent Test**: Open the app, take a selfie, type a name, type "Distinguished Spreadsheet Wrangler" into the Custom Role input, leave Universe and Art Style empty, click Surprise Me. Verify (a) the outbound request's `customRole` is exactly the typed string (trimmed), (b) `archetype` is omitted or null (no prefab forced under the custom string), (c) `universe` and `artStyle` carry randomly chosen values, (d) after the response lands, the Custom Role text input still displays the typed string verbatim and the prefab grid is still blurred.

**Acceptance Scenarios**:

1. **Given** the user typed "Distinguished Spreadsheet Wrangler" into the Custom Role input, no prefab is selected, Universe and Art Style are empty, **When** the user clicks Surprise Me, **Then** the request carries the typed Custom Role unchanged, no prefab Archetype is forced on top, and Universe + Art Style carry uniformly randomly chosen values.
2. **Given** the same starting state, **When** the request resolves and the user returns to the Setup tab, **Then** the Custom Role input still displays the typed string, the prefab Role grid is still blurred and non-interactive (FR-2205), and Universe + Art Style each show their randomly chosen option highlighted.
3. **Given** the user typed a Custom Role consisting only of whitespace ("    "), **When** they click Surprise Me, **Then** the system treats Role as empty (per the 022 trim rule), the Custom Role string is cleared in the session, and a prefab Archetype is rolled normally.

---

### User Story 3 — Surprise Me with no picks behaves like today's 009 randomizer (Priority: P2)

A user has a photo and a name, but has not selected anything in any of the visible category grids (Role, Universe, Art Style) and has not typed a Custom Role. They press **Surprise Me**. The app picks a random value for every visible category and fires the generation pipeline. This is the existing 009 Surprise Me behavior and must continue to work — the new "fill-in-the-blanks" logic must collapse to "fill-in-everything" when nothing is explicit.

**Why this priority**: This is the back-compat slice. If this regresses, every existing user flow that starts with a blank Setup form breaks. P2 (not P1) because US1 + US2 are the new value; US3 is preservation.

**Independent Test**: In a fresh session, take a selfie, type a name, leave every category empty and the Custom Role input blank, click Surprise Me. Verify the outbound request carries valid randomly chosen values for `archetype`, `universe`, and `artStyle`, and the response renders exactly the way it does today.

**Acceptance Scenarios**:

1. **Given** a photo + name, all category grids empty, Custom Role blank, **When** the user clicks Surprise Me, **Then** every category in the outbound payload carries a uniformly randomly chosen value drawn from its option list (no `null`, no blank).
2. **Given** the same blank starting state, **When** the user repeats *Start Over → Surprise Me* ten times (Start Over resets the session per FR-2814 so each Surprise Me sees an empty Setup), **Then** the rolled values across the ten iterations span more than one distinct option in each category (the randomizer is genuinely rolling and is not category-locked).

---

### User Story 4 — Surprise Me when every visible category is already explicit (Priority: P3)

A user has filled in every visible category: prefab Role picked (or Custom Role typed), Universe picked, Art Style picked. They press **Surprise Me**. The app has nothing to randomize on the visible categories. The button still fires the generation pipeline with the user's existing picks — it does not error, does not silently no-op, does not require the user to switch to the Generate button. From the user's vantage point, Surprise Me with a fully-filled form is equivalent to pressing Generate.

**Why this priority**: A power-user convenience and a robustness check. P3 because the user could just press Generate instead — the button doesn't break, it just becomes redundant in this state. Worth specifying so the implementation has explicit guidance and doesn't introduce a hidden "Surprise Me but you picked everything → error" branch.

**Independent Test**: Pick a prefab Role, a Universe, and an Art Style. Press Surprise Me. Verify the outbound request body is identical to what Generate would have submitted from the same Setup state, and the Alter Ego tab renders normally.

**Acceptance Scenarios**:

1. **Given** photo + name present and every visible category explicitly chosen, **When** the user clicks Surprise Me, **Then** the outbound payload's category values are exactly the user's picks (zero randomized substitutions) and the response renders normally.
2. **Given** the same starting state, **When** Surprise Me resolves, **Then** the Setup tab on return shows exactly the picks the user originally made — no rolled values, no auto-clears.

---

### Edge Cases

- **Custom Role text + prefab Role pick coexisting**: Structurally impossible per 022 (Custom Role takes precedence and blurs the prefab grid). The spec inherits 022's invariant; Surprise Me does not need a special branch.
- **Custom Role string that is only whitespace**: 022's trim rule fires before Surprise Me sees the state — the Custom Role is treated as empty, Role counts as not-explicit, a prefab Archetype is rolled and the whitespace string is cleared in the same transition.
- **User starts a roll, then clicks Surprise Me again before the response lands**: Out of scope — tab gating (007) already disables the Setup tab during a generation in flight, which makes the button unreachable.
- **Photo missing or name blank**: Surprise Me button is disabled per US3 of 009 — unchanged. The disabled-state hint lists only the missing photo + name, never the categories.
- **User wants a fresh roll after a previous Surprise Me populated everything**: Surprise Me on the second click preserves all previously-committed values (they count as *explicit* per FR-2801 / FR-2814). To request a fresh roll, the user clicks **Start Over** (or refreshes the page), then picks any categories they care about, then clicks Surprise Me again. The Surprise Me button itself is not a "re-roll" trigger — it is a "fill-in-the-blanks-and-submit" trigger.
- **Randomizer drawing the same option the user already had**: Not a concern — a populated category is preserved as *explicit*, so the randomizer never re-touches it. Each Surprise Me roll is a uniform draw across the full option list of every category currently classified as *empty*.
- **Hidden categories (Pose, Vibe per 020)**: The server-side `RandomCategorySelector` continues to roll Pose + Vibe on every request regardless of which front-end button fired it. Out of scope for this front-end feature.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-2801**: System MUST classify each visible Setup category as either *explicit* (the current session-state value is non-empty, regardless of how it was set) or *empty* (the current session-state value is empty) at the moment Surprise Me is pressed. No provenance is tracked — a value written into the session by a previous Surprise Me roll counts as *explicit* on the next click.
- **FR-2802**: System MUST treat a prefab Role pick as an explicit Role.
- **FR-2803**: System MUST treat a non-empty trimmed Custom Role string as an explicit Role, and MUST NOT also force a prefab Archetype alongside it.
- **FR-2804**: System MUST treat Role as empty iff no prefab is selected AND the Custom Role string is blank after trim.
- **FR-2805**: Surprise Me MUST randomize **only** categories classified as *empty*. Categories classified as *explicit* MUST be passed through unchanged.
- **FR-2806**: Random selection for an empty category MUST be drawn uniformly at random from that category's full option list.
- **FR-2807**: After Surprise Me fires, the Setup session state MUST reflect the rolled values for previously-empty categories so a subsequent return to the Setup tab shows what was picked (closes the 009 US2 feedback-loop contract for the partial case).
- **FR-2808**: After Surprise Me fires, the Setup session state for *explicit* categories MUST remain byte-identical to what the user entered — the prefab pick, the Custom Role string, the Universe pick, the Art Style pick. No re-emission, no normalization, no whitespace collapse beyond 022's existing trim of the Custom Role on submit.
- **FR-2809**: Surprise Me MUST trigger the same submission pipeline that Generate triggers — tab auto-switch (005), generating phase, downstream HTTP request, response handling, error/fallback display. No new HTTP endpoint, no new request shape.
- **FR-2810**: The Surprise Me button MUST remain disabled until both a photo is present AND the first-name field is non-empty (unchanged from 009).
- **FR-2811**: If every visible category is *explicit*, Surprise Me MUST still fire the generation pipeline normally (with zero randomized substitutions). It MUST NOT error, MUST NOT silently no-op, MUST NOT require the user to press Generate instead.
- **FR-2812**: System MUST NOT introduce a separate request marker, header, or payload field distinguishing a Surprise Me submission from a Generate submission. The wire contract is identical in both cases (preserves 009's principle of indistinguishability).
- **FR-2813**: The email input (023) and the first-name input MUST NOT be touched by Surprise Me — neither cleared nor rolled. Their values pass through unchanged.
- **FR-2814**: The only mechanisms that clear category session values are the **Start Over** button and a **browser page refresh**. Surprise Me itself MUST NOT clear any existing values — it only writes new values into categories currently classified as *empty*. Generate similarly does not clear values. This applies symmetrically to the prefab Archetype, Custom Role, Universe, and Art Style channels.

### Key Entities

- **Category**: One of the visible Setup pickers (Role, Universe, Art Style). Each category has a closed list of options and a current session-state value that is either an option-identifier (explicit) or empty.
- **Role**: A composite category with two explicit-expression channels — a prefab Archetype pick OR a non-empty trimmed Custom Role string. Either channel marks Role as explicit; both being empty marks Role as empty.
- **Surprise Roll**: The transition that takes the current Setup state, classifies every category as explicit or empty, draws uniform random values for empty categories only, and commits a merged session state. Triggered by exactly one user input — the Surprise Me button.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-2801**: A user who picks any non-empty subset of the visible categories and presses Surprise Me sees their explicit picks unchanged in the Setup state on return in 100% of attempts. (Verifies FR-2805 + FR-2808; the regression this feature exists to prevent.)
- **SC-2802**: For each empty category, a uniform-randomness sanity check over 50 **fresh** Surprise Me rolls (each preceded by a Start Over click so the rolled category starts empty per FR-2814) yields at least 3 distinct option values for that category. (Verifies FR-2806 — the randomizer is genuinely rolling, not stuck on one value.)
- **SC-2803**: A user who fills every visible category and then presses Surprise Me reaches a rendered poster with no error message and no extra navigation steps relative to pressing Generate from the same state. (Verifies FR-2811 — the all-explicit path is not a special-case error path.)
- **SC-2804**: A user who types a non-empty Custom Role and presses Surprise Me sees the Custom Role text input still containing their exact typed string (post-trim) after the response lands in 100% of attempts. (Verifies FR-2803 + FR-2808 against the 009 regression that this feature reverses.)
- **SC-2805**: The outbound HTTP request body for a Surprise Me submission is byte-indistinguishable from the body the Generate button would have submitted given the same final session state. (Verifies FR-2809 + FR-2812 — Surprise Me is a pure session-state seeding action.)
- **SC-2806**: A user with no picks and no Custom Role who presses Surprise Me reaches a rendered poster in the same wall-clock time budget as today's 009 Surprise Me (no measurable regression on the back-compat path). (Verifies FR-2809 for the US3 slice.)

## Assumptions

- The visible Setup categories at the time of this spec are **Role** (ArchetypeGrid + CustomRoleInput), **Universe**, and **Art Style**. Hidden categories (Pose, Vibe per 020) are rolled server-side per request and are not part of the front-end Surprise Me transition. If additional categories become visible in a later feature, the FR-2801..FR-2808 mechanism extends to them by classification alone — no new rule is needed.
- The Surprise Me button surface (label, position, gating semantics, ARIA, keyboard handling) does not change. This feature is exclusively about what the button **does** when pressed, not how it looks.
- "Uniform at random" for an empty category means a uniform draw across the category's full option list at the moment the button is pressed. Re-using the existing 009 / 020 random selector is acceptable; this spec does not mandate a specific selector implementation.
- The 022 precedence rule between prefab Role and Custom Role is load-bearing for this feature: it guarantees the "both Role channels filled" state can never exist when Surprise Me reads the session. The spec relies on that invariant rather than re-asserting it.
- The 023 email-on-tab-2 work continues to apply — `SurpriseMePicked` does not touch `email` (FR-2308 inherited). FR-2813 makes this explicit for both email and first-name.
- No backend contract change. The frontend `POST /api/v1/alter-egos` body shape (with optional `customRole` per 022, no `pose`/`vibe` per 020) carries this feature unchanged.
- No persistence change. Session state lives in the browser tab for the lifetime of the tab; the explicit/empty classification is derived at button-press time from the current session, not stored.

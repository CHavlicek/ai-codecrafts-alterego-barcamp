# Feature Specification: New Options for the Role Category

**Feature Branch**: `022-role-options-custom`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "checkout github project issue #50"
**Source Issue**: [#50 — New Options for the Role category](https://github.com/squer-solutions/aiavatar/issues/50)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pick a non-engineering role from the expanded prefab list (Priority: P1)

A user on the Setup tab can choose their Role from the existing six engineering options **plus three new non-engineering options**: *HR*, *Administration*, *Customer Relations*. Picking any of the three new options behaves exactly like picking an existing engineering role — the Role-category gating for Generate is satisfied, the selection is reflected in the rendered poster's text overlay, and the role visibly shapes the generated image (props, attire, environment cues) the same way engineering roles do today.

**Why this priority**: The "Role" category currently constrains alter egos to engineering personas. The squer audience includes HR, administration, and customer-relations colleagues who today cannot pick a role that represents *them*. This is the most-requested half of issue #50 and unblocks a meaningful slice of attendees with no UX changes beyond three extra pills.

**Independent Test**: With everything else held constant, picking *HR* / *Administration* / *Customer Relations* in turn produces three Generate runs whose posters (a) successfully complete, (b) display the chosen role label in the poster's text overlay, and (c) contain visual cues that a human reviewer can identify as evoking that role — i.e. the new options participate in the full Generate pipeline, including the image prompt path introduced in feature 021.

**Acceptance Scenarios**:

1. **Given** a user with a valid photo and first name, **When** they open the Setup tab, **Then** the Role category lists nine prefab pills in this stable order: Cloud Architect, Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer, HR, Administration, Customer Relations.
2. **Given** the user picks *HR* and chooses a Universe + Art Style, **When** they press Generate, **Then** the poster renders with *HR* in the role-label position of the text overlay and the image visibly evokes the HR role.
3. **Given** the user presses **Surprise Me**, **When** the randomizer selects a Role, **Then** the picked Role is sampled uniformly from the nine prefab options (the three new options are eligible alongside the six engineering ones). Surprise Me MUST NOT generate or inject a custom role string.

---

### User Story 2 - Enter a custom role via free-form text input (Priority: P1)

Below the Role category's prefab pills, a single-line text input lets the user type their own role (up to 100 characters) when none of the nine prefab options fit. As soon as the input contains any non-whitespace character it takes precedence: the input becomes the role of record for this Generate run, and the prefab pills visually blur and become un-clickable to make the precedence visible. When the user clears the input (either by deleting characters or pressing the trailing 'X' button), the prefab pills un-blur and become clickable again. There is no confirmation prompt or alert when 'X' is pressed — it is a silent reset of the input only.

**Why this priority**: The three new prefab options cover the largest extra audiences but cannot cover every role represented at an event. The free-form input is the long-tail mechanism — a tester, a sales engineer, a founder, a designer can all type their role and still get a personalised alter ego. Same priority as User Story 1 because issue #50 ties the two together as one user-facing feature; either half on its own is shippable but the value proposition is the pair.

**Independent Test**: Type any non-whitespace string into the custom-role input, leave the prefab pills untouched (or pre-pick one), press Generate. The poster's text overlay MUST show the typed string (truncated to 100 characters, leading/trailing whitespace trimmed) and the image MUST visibly evoke that role using the same image-prompt mechanism as a prefab pick.

**Acceptance Scenarios**:

1. **Given** the Setup tab is open with no prefab Role chosen, **When** the user types `Tester` into the custom-role input, **Then** the prefab pills become visibly blurred and un-clickable (clicks and keyboard activation are no-ops; the pills lose focusability), and the Generate-gating considers Role satisfied.
2. **Given** the user has already chosen *Cloud Architect* as a prefab pill, **When** they begin typing into the custom-role input (first non-whitespace character entered), **Then** the *Cloud Architect* selection is silently cleared — no prefab pill is in the "selected" state any more — and the prefab pills become blurred and un-clickable.
3. **Given** the custom-role input contains `Tester`, **When** the user clicks the trailing 'X' button, **Then** the input is cleared, no confirmation/alert appears, and the prefab pills immediately become un-blurred and clickable again. Any previously cleared prefab selection is **not** restored — the user picks again.
4. **Given** the custom-role input contains only whitespace (spaces, tabs), **When** the user reaches the Generate button, **Then** the input does NOT take precedence: the prefab pills remain available and clickable, and Generate-gating still requires a prefab Role to be chosen (the whitespace-only input is treated as empty).
5. **Given** the user types a string longer than 100 characters, **When** they continue typing, **Then** the input refuses further characters past character 100 (or accepts them and visibly truncates) — the value transported to the backend is at most 100 characters after leading/trailing whitespace is trimmed.
6. **Given** the user has typed a valid custom role, picked Universe + Art Style, and pressed Generate, **When** the response renders, **Then** the poster's role-label text shows the trimmed custom string and the image visibly evokes that role direction.

---

### User Story 3 - Custom role and Surprise Me coexist predictably (Priority: P2)

A user who has typed something in the custom-role input can still press **Surprise Me**. Surprise Me restores a fresh random pick from the nine prefab options and clears the custom-role input as a side effect — so the result of Surprise Me is always a prefab role, and the input no longer claims precedence. After Surprise Me runs, the prefab pills are un-blurred (because the input is empty) and the randomly-picked pill is shown as selected.

**Why this priority**: Without this rule the two inputs can conflict invisibly (e.g. Surprise Me rolls *AI Engineer* but the custom input still holds `Tester` and silently takes precedence). The user pressed Surprise Me to roll the dice — the result should be visible and authoritative. Lower priority than US1 / US2 only because it's a corner-case interaction; US1 and US2 are the headline.

**Independent Test**: With the custom input pre-filled with non-whitespace text, press Surprise Me. The custom input MUST be empty afterwards, exactly one prefab Role pill MUST be selected (chosen uniformly from the nine), and the prefab pills MUST be clickable (not blurred).

**Acceptance Scenarios**:

1. **Given** the custom-role input contains `Tester` and prefab pills are blurred, **When** the user presses Surprise Me, **Then** the custom input is empty, prefab pills are un-blurred, exactly one prefab Role pill is selected (uniformly from the nine), and Universe / Art Style are also rolled per existing Surprise Me behaviour.

---

### Edge Cases

- **Whitespace-only input is treated as empty**. Per the issue, "When user enters some text (except for the blank characters and spaces) the options in the Role category should become blurred and unclickable." So a value of `"   "` or `"\t"` does NOT take precedence, does NOT blur the prefab pills, and is NOT considered a chosen role for Generate-gating. The transported value is also empty (whitespace-only is dropped before send), so the backend never receives a whitespace-only custom role.
- **Trimming**: leading and trailing whitespace are trimmed before the value is used for gating, sent to the backend, or rendered on the poster. Interior whitespace (`Senior Tester`) is preserved.
- **Hard cap at 100 characters**: enforced in the input UI. The value that reaches the backend is at most 100 characters after trimming. Behaviour at the 100-character boundary (refuse-further-typing vs. soft-truncate) is a UI-polish choice the plan can make; the contract is "transported value ≤ 100 chars after trim".
- **No-rendered-text guarantee (carry-over from 021)**: the image-generation prompt still forbids any rendered text inside the image. The custom role is communicated to the image provider as a visual/scene direction (props, environment, attire, activity), not as a string to display. The acceptance bar from 021 SC-2102 (zero rendered-text instances across the validation sample) continues to apply, including with custom roles like `"Tester"`.
- **Safety / content of custom strings**: the user can type anything within the 100-character limit. No client-side content filtering is introduced. Any safety filtering performed by the image / text providers (refusals, safety substitutions) continues to apply unchanged — those refusals route through the existing fallback path (provider returns a `safety_refused` failure → backend serves the FE-side fallback poster with the user-typed role rendered in the text overlay).
- **'X' button on empty input**: the 'X' is shown only when the input is non-empty (typical clear-button pattern). Pressing it on an empty input is impossible by definition.
- **Browser autofill**: if a password manager or browser autofill drops text into the custom-role input, that's treated identically to user-typed text (the input change triggers the same precedence rules).
- **Stub / fallback paths**: when the image provider is unavailable or returns an error, the existing FE-side fallback poster path continues to apply unchanged. The fallback poster's text overlay must render the **role of record** for this run — i.e. the custom-typed string if present, otherwise the prefab label.
- **Group photo behaviour**: the role-of-record (custom or prefab) applies uniformly to every subject in the image, mirroring 021 FR-2105.
- **Reload / navigate-away**: the custom-role input is component-local state. Navigating away from Setup and back, or reloading the page, clears it — consistent with the rest of Setup state (no persistence per 001 FR-016 / FR-017 / FR-024).

## Requirements *(mandatory)*

### Functional Requirements

#### Prefab role catalogue

- **FR-2201**: The Role category on the Setup tab MUST present nine prefab options in this stable order: Cloud Architect, Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer, HR, Administration, Customer Relations. The six engineering options preserve their existing wire values, labels, icons, and relative order from feature 002 / 006 / 021.
- **FR-2202**: The three new prefab options (HR, Administration, Customer Relations) MUST be sampled by **Surprise Me** with the same probability as each of the six engineering options — Surprise Me picks uniformly from the nine. (Extends 009 FR-901 unchanged; the option list grows from six to nine.)
- **FR-2203**: Selecting any of the three new prefab options MUST satisfy Generate-gating identically to selecting an existing engineering option (no special-casing in the gating rule).

#### Custom-role text input

- **FR-2204**: Below the Role category's prefab pills the Setup tab MUST present a single-line free-form text input ("the custom-role input") accepting up to 100 characters. The input is optional — the user may leave it empty and pick a prefab option, or type into it and leave the prefab pills unchosen.
- **FR-2205**: While the custom-role input contains any non-whitespace character (i.e. its trimmed value is non-empty), the prefab Role pills MUST be visually de-emphasised (blurred) and made un-interactive: clicks are no-ops, keyboard activation (Space / Enter) is a no-op, and the pills are removed from the keyboard tab order while in this state. The custom-role input remains keyboard-focusable throughout.
- **FR-2206**: While the custom-role input's trimmed value is empty, the prefab Role pills MUST be fully interactive (clickable, keyboard-navigable per the existing radio-group pattern) and visually un-blurred.
- **FR-2207**: When the user starts typing a non-whitespace character into the custom-role input AND a prefab Role pill is currently selected, the prefab selection MUST be silently cleared — no prefab pill is in the "selected" state while the custom input claims precedence. No alert / confirmation / undo affordance is shown.
- **FR-2208**: The custom-role input MUST display a trailing 'X' clear button whenever the input is non-empty. Activating the 'X' (mouse click, Space, or Enter while focused) MUST clear the input's value to the empty string with no confirmation or alert, and MUST restore prefab interactivity per FR-2206. Activating 'X' MUST NOT restore any previously-cleared prefab selection.
- **FR-2209**: When the user presses **Surprise Me**, the custom-role input MUST be cleared as part of the same single user-visible action that rolls fresh prefab picks, so the result of Surprise Me is always a prefab role and prefab interactivity is restored. (Refines 009 FR-901 for the Role category: Surprise Me reaches into the custom input and empties it; it does not sample a custom-role string.)

#### Generate gating and contract

- **FR-2210**: Generate-gating MUST treat the Role category as satisfied if **either** (a) a prefab Role pill is selected, or (b) the custom-role input's trimmed value is non-empty. Both being empty MUST keep Generate disabled.
- **FR-2211**: The "role of record" for a Generate request — i.e. the value used for the image prompt, the character-bio prompt, and the poster's text-overlay role label — MUST be:
  - the trimmed custom-role string when the custom-role input has a non-empty trimmed value (precedence rule per issue #50), **otherwise**
  - the human-readable label of the selected prefab Role.
- **FR-2212**: The role-of-record (custom or prefab) MUST be transported to the backend on the existing Generate request. The wire shape MAY be widened to carry a custom-role string field; any change MUST be additive and MUST keep older clients (which send only the existing `archetype` enum field) working.
- **FR-2213**: The backend MUST compose the image-generation prompt using the role-of-record as a visual/scene direction. The 021 contract holds unchanged: the role label is communicated to the model as a *direction* (props, environment, attire, activity), never as a string to render inside the image. The image prompt MUST continue to forbid rendered text inside the image regardless of whether the role-of-record is a prefab label or a custom string.
- **FR-2214**: The character-bio prompt MUST also consume the role-of-record — so the bio's role mentions match the poster's role label. If the role-of-record is a custom string, the bio prompt uses that string verbatim (subject to the same trim + 100-char cap). No separate code path or wording branch is introduced for custom roles.
- **FR-2215**: The poster's text-overlay role label (rendered by the existing 017 overlay path) MUST render the role-of-record exactly as transported (trimmed, ≤ 100 chars). Existing overlay layout, font, casing, and truncation rules apply unchanged — if a long custom string exceeds the overlay's render-width budget, the overlay's existing truncation behaviour handles it.
- **FR-2216**: The role-of-record MUST apply uniformly across every subject rendered in a group photo (extends 021 FR-2105 unchanged).

#### Resilience and no-regression

- **FR-2217**: The FE-side fallback poster path (when the image provider fails) MUST continue to function exactly as today. The fallback poster's text overlay renders the role-of-record (custom or prefab) — no change in fallback rendering beyond consuming the new value.
- **FR-2218**: The existing Surprise Me semantics for Universe and Art Style are unchanged. Only the Role category's option pool grows (six → nine) and Surprise Me additionally clears the custom-role input per FR-2209.
- **FR-2219**: All existing keyboard accessibility for the Role grid (single-Tab-stop, arrow-key navigation, visible focus indicator, screen-reader-readable labels) MUST continue to hold for the nine-option grid. The custom-role input and its 'X' clear button MUST be keyboard-operable (Tab to focus, type to edit, Tab to reach 'X', Space / Enter to activate 'X') with a visible focus indicator on both.
- **FR-2220**: No persistence is introduced. The role-of-record (custom or prefab), the prefab selection state, and the custom-input string live only in process memory for the duration of one Setup → Generate flow. This extends 001 FR-016 / FR-017 / FR-024 unchanged.

### Key Entities

- **Prefab Role** (existing, extended): the user's chosen archetype from the nine-option set. The first six (Cloud Architect, Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer) are unchanged from 002; the three new options (HR, Administration, Customer Relations) are added by this feature. Wire values for the new options follow the existing kebab-case public-contract convention; the precise wire strings are an implementation detail for the plan.
- **Custom Role Input** (new): a single-line free-form string of at most 100 characters (after trim) that, when non-empty, overrides any prefab selection and becomes the role-of-record for the next Generate request. Holds component-local UI state only; never persisted.
- **Role of Record** (new derived value): the value actually consumed by the image prompt, the bio prompt, and the poster text overlay for a given Generate run. Derived per FR-2211 from (prefab selection, custom input). The plan chooses how this is computed and transported; the spec only constrains the inputs and the consumer guarantees.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-2201**: A user can pick *HR*, *Administration*, or *Customer Relations* and complete a Generate run that produces a poster whose text overlay shows the chosen label and whose image a human reviewer can correctly identify as evoking that role at least 5 times out of 6 sample generations (≥ 83% identifiability per new role, matching the 021 SC-2101 bar for engineering roles).
- **SC-2202**: A user can type a custom role of up to 100 characters and complete a Generate run that produces a poster whose text overlay shows the trimmed custom string and whose image visibly evokes the role direction implied by that string. For a sample set of 12 plausible custom roles (e.g. *Tester*, *Sales Engineer*, *Founder*, *Designer*, *Recruiter*, *Logistics*, *Translator*, *Coach*, *Educator*, *Photographer*, *Chef*, *Mechanic*), a human reviewer correctly identifies the depicted role from the image at least 75% of the time.
- **SC-2203**: While the custom-role input contains non-whitespace text, prefab pill clicks have zero behavioural effect: across 100 simulated clicks the selected prefab state changes 0 times. (Functional gate on the un-clickable contract.)
- **SC-2204**: Pressing the 'X' clear button on a non-empty custom-role input takes the input from non-empty to empty and restores prefab interactivity in a single user action with no intermediate confirmation. Time from click-down to input-empty + pills-clickable is below 100 ms p95 (perceived instant).
- **SC-2205**: Whitespace-only custom input does NOT trigger the precedence rule: across 10 inputs consisting of only spaces / tabs, prefab pills remain interactive in every case and Generate-gating treats Role as not-yet-satisfied if no prefab is picked.
- **SC-2206**: The no-rendered-text guarantee from 021 SC-2102 holds with custom roles: across a sample of at least 30 generated images covering all nine prefab options + a representative set of custom strings, **zero** images contain rendered text inside the character cutout, on banners, on parchment scrolls, on signs, or anywhere in the scene.
- **SC-2207**: End-to-end Generate latency does not regress: median wall-clock time on the Generate path stays within ±10% of the post-021 baseline. Adding three prefab options and one optional string field MUST NOT materially affect provider response time.
- **SC-2208**: No regression in fallback path: failure-injection tests that today produce a fallback poster continue to produce one with the role-of-record (custom or prefab) rendered in the text overlay.
- **SC-2209**: Keyboard-only completion: a keyboard-only user can reach the nine-pill grid, pick a Role with arrow keys, Tab to the custom-role input, type a value, Tab to the 'X' button, activate it with Space / Enter, and proceed to Generate — all without using a mouse, with a visible focus indicator on every interactive element.

## Assumptions

- The user-facing "Role category" in issue #50 maps to what the codebase calls the *Archetype* sub-group on the Setup tab — labelled "Role" or equivalent in the UI today. This is the category whose options are currently the six engineering roles. No other "role" concept exists in the product surface.
- The three new prefab options use the same selection-pill, icon, and accent-colour pattern as the existing six. Picking icons is an implementation-detail / design choice for the plan (consistent with how 006 chose icons for Art Style options); the spec does not constrain the iconography beyond "matches the existing style".
- The custom-role input is component-local UI state and is **not** synced into the existing reducer's `archetype` field (which is an enum). The plan introduces either a separate session-state field for the custom string or widens the existing field to a discriminated union — implementation detail.
- The wire contract widens by at most one additional optional string field carrying the custom role. The existing `archetype` enum field stays exactly as it is; older clients that don't send the new field continue to work (the backend treats absent custom-role as "use prefab archetype label").
- Surprise Me does not generate or sample custom-role strings. The randomiser stays grounded in the nine-element prefab option pool, mirroring how Universe and Art Style randomisers stay within their respective fixed option pools.
- The image prompt builder (Gemini + fal.ai paths, per 016) treats the role-of-record uniformly — it does not branch on "is this prefab or custom?". The 021-introduced no-rendered-text framing and the human-readable label injection apply to both prefab labels and custom strings.
- The character-bio prompt (out of scope for behavioural change in 021) likewise consumes the role-of-record verbatim. Bio quality is acceptable as long as the model handles a free-form short noun phrase the same way it handles a known engineering-role label today; if a particular custom string elicits a weak bio that's a model-quality artefact, not a contract violation.
- Group-photo behaviour (011 + 021 FR-2105) extends unchanged: every subject in a group photo is rendered in the same role-of-record.
- No content moderation, profanity filter, or shadow-blocklist is introduced for the custom-role string. The user's 100-character input is forwarded to the providers as-is (after trim). Provider-side safety refusals route through the existing fallback path.
- Print behaviour (010 / 018) extends unchanged: whichever image + text overlay reach the printable view print exactly as they render on screen. No new print-specific handling for custom roles.
- Visual treatment of "blurred and un-clickable" prefab pills is a CSS / design detail the plan chooses (e.g. `filter: blur(2px)` + `opacity: 0.4` + `pointer-events: none` + `aria-disabled="true"` + removal from tab order). The spec constrains only the behaviour (zero click effect, zero keyboard activation effect, not in tab order, visibly de-emphasised).
- No persistence is introduced, consistent with 001 FR-016 / FR-017 / FR-024. The custom-role string lives in browser memory for the lifetime of the Setup view only.

# Feature Specification: Wording Updates for User Roles

**Feature Branch**: `026-role-label-wording`
**Created**: 2026-05-18
**Status**: Draft
**Input**: GitHub issue #62 — "Wording Updates for User Roles"

> Four Role labels currently use short or abbreviated forms. Replace them with the full, standard wording everywhere the user sees a Role in the product. The underlying identity of each Role is unchanged — only the wording shown to people changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — See the new Role wording on the Setup tab (Priority: P1)

A guest opens the experience and reaches the **Setup** step where they pick a Role. The Role grid offers nine options. Today four of those options read in abbreviated or short form ("Backend Dev", "Frontend Dev", "Platform Eng.", "HR"). After this change the guest sees the full, professional form of each ("Backend Developer", "Frontend Developer", "Platform Engineer", "People Operations"). All other Role options remain exactly as they are.

**Why this priority**: This is the surface most guests touch. The current short forms read as informal and inconsistent (one ends in a period — "Platform Eng." — the others do not; "HR" is a two-letter acronym next to multi-word labels). Updating them is the smallest unit of work that delivers the issue's full intent and is independently demonstrable.

**Independent Test**: Open the running app, advance to the Setup tab, view the Role grid, and confirm that the four target options read in the new wording while the other five Role options are unchanged. No other tab or action is required.

**Acceptance Scenarios**:

1. **Given** a guest is on the Setup tab, **When** the Role grid is shown, **Then** the option whose underlying identity is "backend-dev" reads **"Backend Developer"**.
2. **Given** a guest is on the Setup tab, **When** the Role grid is shown, **Then** the option whose underlying identity is "frontend-dev" reads **"Frontend Developer"**.
3. **Given** a guest is on the Setup tab, **When** the Role grid is shown, **Then** the option whose underlying identity is "platform-eng" reads **"Platform Engineer"** (no trailing period).
4. **Given** a guest is on the Setup tab, **When** the Role grid is shown, **Then** the option whose underlying identity is "hr" reads **"People Operations"**.
5. **Given** a guest is on the Setup tab, **When** the Role grid is shown, **Then** the five Role options not named in this feature — Cloud Architect, AI Engineer, Data Engineer, Administration, Customer Relations — remain exactly as they read today.

---

### User Story 2 — See the new Role wording on the generated alter-ego poster (Priority: P1)

A guest finishes generation and lands on the **Your Alter Ego** tab. The poster shows the guest's name and their chosen Role as readable text. When the chosen Role is one of the four renamed options, the poster shows the new wording — the same wording the guest selected on the Setup tab.

**Why this priority**: The Role label printed on the poster is read by guests and the people they show the poster to. If Setup says "Backend Developer" but the poster still says "Backend Dev", the experience reads as inconsistent and unfinished. This story closes that loop so the new wording is the wording across both surfaces.

**Independent Test**: Pick "Backend Developer" (or any of the other three renamed Roles) on the Setup tab, run a generation, and confirm the poster's Role text matches the wording shown in the grid. No backend deep-dive is required — visual confirmation on the rendered poster is sufficient.

**Acceptance Scenarios**:

1. **Given** a guest picked the "backend-dev" Role, **When** the poster renders on the Alter Ego tab, **Then** the Role text on the poster reads **"Backend Developer"**.
2. **Given** a guest picked the "frontend-dev" Role, **When** the poster renders, **Then** the Role text reads **"Frontend Developer"**.
3. **Given** a guest picked the "platform-eng" Role, **When** the poster renders, **Then** the Role text reads **"Platform Engineer"**.
4. **Given** a guest picked the "hr" Role, **When** the poster renders, **Then** the Role text reads **"People Operations"**.
5. **Given** a guest picked any of the five Roles not named in this feature, **When** the poster renders, **Then** the Role text reads exactly as it did before this change.

---

### Edge Cases

- **Free-form custom Role (introduced in feature 022) is unaffected.** When a guest types a free-form Role into the custom-role input, that string is what shows on the poster and is what the system honours. None of the four renamed prefab labels apply.
- **Surprise Me draws from the prefab list.** When Surprise Me lands on one of the four renamed Roles, the option the guest sees committed into the Setup grid reads in the new wording (Backend Developer / Frontend Developer / Platform Engineer / People Operations), not the old form.
- **In-flight session at the moment of release.** A guest who picked, say, "Backend Dev" before the update lands and is mid-generation when the new wording ships will see the new wording the next time the surface is rendered. Wire/storage identity is unchanged, so no session is broken or invalidated. (This is an artifact of the no-persistence posture — there is nothing to migrate.)
- **The two engineering labels that already use the full form ("Cloud Architect", "AI Engineer", "Data Engineer") are not touched** — only the four named in the issue change.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-2601**: The Setup-tab Role grid MUST display **"Backend Developer"** for the option whose stable identity is `backend-dev`.
- **FR-2602**: The Setup-tab Role grid MUST display **"Frontend Developer"** for the option whose stable identity is `frontend-dev`.
- **FR-2603**: The Setup-tab Role grid MUST display **"Platform Engineer"** for the option whose stable identity is `platform-eng` — with **no trailing period**.
- **FR-2604**: The Setup-tab Role grid MUST display **"People Operations"** for the option whose stable identity is `hr`.
- **FR-2605**: The Setup-tab Role grid MUST continue to display the unchanged wording for every other Role option (Cloud Architect, AI Engineer, Data Engineer, Administration, Customer Relations) — exactly as those options read in the release immediately preceding this change.
- **FR-2606**: The Role text rendered onto the generated alter-ego poster MUST use the new wording (FR-2601..FR-2604) whenever the guest's chosen Role is one of the four renamed prefab options.
- **FR-2607**: The Role text rendered onto the generated alter-ego poster MUST use the existing wording, unchanged, for every other prefab Role (and for free-form custom Roles, which already pass through verbatim).
- **FR-2608**: The set of nine prefab Roles, their relative order in the grid, their icons, and the stable identifier each option submits to the backend (e.g. `backend-dev`, `hr`) MUST NOT change. This change is wording-only.
- **FR-2609**: The "Surprise Me" action MUST be able to land on each of the nine prefab Roles, including the four renamed ones, and when it does, the option shown as picked on Setup MUST read in the new wording.
- **FR-2610**: No persistence is added or modified. Inherits 001 FR-016 / FR-017 / FR-024 unchanged: the wording is rendered from in-process configuration; nothing about the guest's Role choice is stored beyond the session.

### Key Entities

- **Role**: A single Setup-tab Role option. Each Role has a stable identity used internally (`backend-dev`, `hr`, …) and a human-readable label shown to the guest. This feature changes the human-readable label for four Roles (`backend-dev`, `frontend-dev`, `platform-eng`, `hr`) and leaves the stable identity, icon, ordering, and behaviour of every Role untouched.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-2601**: Inspecting the Setup-tab Role grid in the running app, **100%** of the four renamed Roles read in the new wording (Backend Developer / Frontend Developer / Platform Engineer / People Operations) and **0%** still read in the old wording.
- **SC-2602**: Running a generation with each of the four renamed Roles, **100%** of the resulting posters show the chosen Role in the new wording — matching, character-for-character, what the guest saw selected on Setup.
- **SC-2603**: Inspecting every surface that displays a Role label, **0** surfaces continue to show "Backend Dev", "Frontend Dev", "Platform Eng.", or the bare acronym "HR" as the *label a guest reads*. (Stable identifiers, code symbols, log fields, and any other non-guest-facing strings are out of scope for this success criterion.)
- **SC-2604**: The wire payload a guest's Setup-tab Role selection sends, and the wire payload the poster generation accepts, are **byte-identical** to the release immediately preceding this change for all nine Roles. (Verified by comparing a request captured before and after.)
- **SC-2605**: Time to verify the change end-to-end — open the app, advance to Setup, pick each renamed Role, generate, read the poster — is under **5 minutes per Role** on a fresh session. (Confirms there is no hidden flow change wrapped into the wording update.)

## Assumptions

- **"Displayed roles" in the issue means every surface a guest reads, not internal identifiers.** This includes the Setup-tab Role grid (primary surface) and the Role text printed onto the generated poster (secondary surface, also user-facing). It does not include code symbols, wire enum values, log lines, or any other non-guest-facing string — the issue states explicitly: *"No changes to accompanying enums are necessary."*
- **"HR" → "People Operations" is the wording change for the People Ops Role label across all guest-facing surfaces.** The internal identity remains the same as today (the option whose wire value is `hr`).
- **The change is wording-only.** No Role is added, removed, renamed in identity, reordered, or re-iconed. The Role grid still has nine options in the same order with the same icons. Free-form custom Role behaviour (feature 022) is unaffected.
- **Behaviour upstream of the wording stays untouched.** Surprise Me's uniform random pick across the nine prefab Roles, the Setup→Generate gating, the no-persistence posture, the validation rules on the custom-role free-text input, and every other Role-adjacent rule from features 001..025 remain exactly as they are.
- **The poster's Role text follows the same wording as the Setup grid.** The poster is a guest-facing surface that re-states the Role the guest picked; consistency between what the guest selects and what the printed poster shows is part of "displayed roles" being updated. If a downstream provider's *internal* prompt currently uses a different long-form (e.g. an internal grounding term sent to a generation model), that internal long-form is **not** in scope here unless changing it is necessary to make the poster's visible text read in the new wording.
- **No third-party Role taxonomy or HRIS-aligned ontology is being adopted.** "People Operations" is chosen as the new label by product/design preference (issue #62), not because it must align with an external standard.

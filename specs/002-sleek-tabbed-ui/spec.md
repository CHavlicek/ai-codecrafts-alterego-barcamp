# Feature Specification: Initial Styling and Layout — Tabbed Setup Experience

**Feature Branch**: `002-sleek-tabbed-ui`
**Created**: 2026-04-22
**Status**: Draft
**Input**: GitHub issue [#3 — Initial Styling and Layout](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/3). Design reference: [`mockup.png`](./mockup.png) in this directory.

> **Relationship to 001-initial-poc.** This feature reshapes the UI that 001 delivered. Where a requirement here conflicts with a 001 requirement (e.g. pose/colour pickers), this feature supersedes 001 for the visible user-facing flow; the backend contract from 001 remains the baseline and any changes are called out explicitly. Accessibility (WCAG 2.1 AA), resilience, and no-persistence guarantees from 001 continue to apply unchanged.

## Clarifications

### Session 2026-04-22

- Q: Compatibility with the 001 backend contract and option set — which strategy? → A: **Hybrid**. The Setup form's selection set is **pose, archetype (displayed as "Role"), universe, vibe, firstName, photo**. Carry 001's pose sub-group forward. Re-theme archetype's enum values (and display label "Role") to the six engineering roles from the mockup. Keep universe and expand it to the six options from the mockup. Add **vibe** as a new optional picker with four options. **Drop accent colour entirely** — 001 FR-005 and related colour-dependent poster accent behaviour are superseded. Backend contract delta: `GenerateRequest` keeps `pose`, `archetype` (new enum values), `universe` (new enum values), `firstName`; adds optional `vibe`; **removes** `colour`. Stubs must be re-keyed for the new archetype/universe values.
- Q: When the user presses Generate, where does the loading indicator render and what happens on success? → A: **Option B**. Pressing Generate immediately auto-switches the active tab to "2 Your Alter Ego"; the loading indicator (from 001 FR-011) renders inside that tab's panel; when the result lands, the poster replaces the loading state inside the same panel. The Setup tab remains reachable during and after generation so users can return to adjust their inputs without losing state. "Start over" (001 FR-013) returns the active tab to "1 setup" per FR-102.
- Q: Where does the Generate button live in the new tabbed layout? → A: **Option A**. The Generate button sits at the bottom of the right-hand "ROLE, UNIVERSE & KEYS" column, directly below the Name input. It is not sticky, not inside the tab bar, and does not span both columns.
- Q: What happens when the user presses Generate after an earlier successful generation (without Start over)? → A: **Option A**. A new generation run starts immediately — no confirmation prompt, no disabled state. The UI auto-switches to "2 Your Alter Ego" per FR-108, the previous poster is replaced by the loading indicator, and the new poster replaces the loading state when the run completes. The old poster is discarded (the POC does not cache, deduplicate, or present variants). Start over remains the explicit path to clear the entire session and return to an empty Setup form.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Navigate the two-tab workflow and recognise where I am (Priority: P1)

A first-time visitor lands on the app and immediately sees a two-tab top-level layout: **1 setup** (active) and **2 Your Alter Ego** (not yet reachable content-wise). The numbered labels communicate a two-step workflow at a glance. The user understands they are currently on the setup step and that an alter-ego result is what comes next.

**Why this priority**: The tab chrome is the frame the rest of the feature hangs off. Without it, every downstream scenario (filling the form, getting told where Generate takes them, finding their poster afterwards) loses its bearings. It is also the smallest independently demonstrable slice — a tab shell with empty/placeholder content for tab 2 is a viable, shippable MVP for this feature.

**Independent Test**: Load the app. Observe that both tabs render, "1 setup" is visibly active (distinct colour/emphasis), "2 Your Alter Ego" is visibly inactive. Confirm the active tab state is communicated by more than colour alone (label weight, position marker, or an ARIA attribute). Switch focus to the tablist via keyboard and verify the arrow keys move between tabs and Enter/Space selects.

**Acceptance Scenarios**:

1. **Given** the app has just loaded, **When** the user looks at the top of the page, **Then** two tabs labelled "1 setup" and "2 Your Alter Ego" are visible side-by-side spanning the page width, with "1 setup" visibly active.
2. **Given** the user has not yet generated an alter ego, **When** they activate the "2 Your Alter Ego" tab, **Then** the app either (a) shows a placeholder / empty state explaining that the alter ego appears after generation, or (b) visibly indicates the tab is currently unavailable — but it MUST NOT show a broken, blank, or error state.
3. **Given** the user is using only a keyboard, **When** they Tab to the tablist and press the Right/Left arrow keys, **Then** focus moves between tabs with a visible focus indicator, and pressing Enter or Space activates the focused tab.
4. **Given** a screen-reader user, **When** they encounter the tablist, **Then** it is announced as a tab control with two tabs, the current tab's selected state is announced, and the active tabpanel is associated with the active tab.

---

### User Story 2 — Complete the Setup form with a sleek, legible, dark-themed layout (Priority: P1)

A user on the Setup tab sees two columns: **Your Photo** on the left (large circular photo placeholder with Camera / Upload affordances) and **Role, Universe & Keys** on the right (numbered sub-sections for Pose, Engineer Role, Universe / Style, Vibe, and a Name field). The layout is visually calm, high-contrast against a dark background, with one accent colour per sub-section communicating which choice belongs to which question. The user can make all required selections without scrolling the page horizontally and without hunting for primary controls.

**Why this priority**: This is the core of the issue — "the UI should have a sleek modern look" and "choose your role, universe, vibe, enter your name, take a photo or upload a photo". Delivering the tab shell (Story 1) without this is visually hollow; delivering this without the shell has no navigation. Both are P1 and shipped together.

**Independent Test**: On a 1440-wide viewport, the Setup tab renders as two columns; on a narrow viewport, the columns stack without overflow. Every labelled control in the mockup (Camera, Upload, each Role / Universe / Vibe option, Name input) is tappable / clickable, announces itself to assistive tech, and visibly updates to a selected state when chosen. All interactive text meets WCAG 2.1 AA contrast against its background.

**Acceptance Scenarios**:

1. **Given** the Setup tab is active on a desktop viewport, **When** the page renders, **Then** the left column shows the **Your Photo** section (heading, sub-label "Real face as a direct reference", circular photo placeholder reading "Add photo", and two side-by-side pill buttons "📷 Camera" and "🗂 Upload"), and the right column shows the **Role, Universe & Keys** section (heading plus four numbered sub-groups — Pose, Engineer Role, Universe / Style, Vibe — and a Name input).
2. **Given** the Setup tab is rendered, **When** the user looks at the Pose sub-group, **Then** exactly four pose options are visible in a two-column grid (Heroic, Stealthy, Mystical, Scholar — continuing from 001 FR-004), each a pill button with an emoji/icon and label, and only one can be selected at a time.
3. **Given** the Setup tab is rendered, **When** the user looks at the Engineer Role sub-group, **Then** exactly six role options are visible in a two-column grid (Cloud Architect, Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer), each a pill button with an emoji/icon and label, and only one can be selected at a time.
4. **Given** the Setup tab is rendered, **When** the user looks at the Universe / Style sub-group, **Then** exactly six universe options are visible in a two-column grid (Marvel, Star Wars, Cyberpunk, The Office, Indiana Jones, Lord of Rings), each a pill button, and only one can be selected at a time.
5. **Given** the Setup tab is rendered, **When** the user looks at the Vibe sub-group (labelled "Vibe (optional)"), **Then** exactly four vibe options are visible in a two-column grid (Builder, Thinker, Rebel, Architect), with at-most-one selection allowed and zero selections also permitted.
6. **Given** the user has selected one option in a sub-group and clicks a different option in the same sub-group, **When** the second option is clicked, **Then** the first option becomes unselected and the second becomes selected (single-selection per group).
7. **Given** a Vibe option is selected, **When** the user clicks that same selected Vibe option again, **Then** it becomes unselected (Vibe supports deselection because it is optional; the other groups do not).
8. **Given** an option is selected, **When** the page is rendered, **Then** the selected state is communicated by more than colour alone (e.g. a visible border/outline, a checkmark, or an ARIA `aria-pressed` / `aria-checked` attribute), and the contrast of the selected-state outline meets WCAG 2.1 AA against the tile background.
9. **Given** a user types into the Name field, **When** the input receives focus, **Then** the placeholder ("e.g. Paula") disappears, the field accepts free-form text, and the helper label "Your name (for personalized character)" remains visible or programmatically associated with the control.
10. **Given** any interactive control on the Setup tab (tab, button, pill, text input), **When** the user focuses it via keyboard, **Then** a visible focus indicator meeting 3:1 contrast against the adjacent background is shown.

---

### User Story 3 — Pick a photo with a clearer, friendlier intake affordance (Priority: P2)

A user reaches the Setup tab and needs to supply a photo. They see a large circular placeholder with an "Add photo" hint and two pill buttons underneath — **Camera** and **Upload**. They can pick either path; once a photo is supplied, the circular placeholder previews it. They can re-trigger Camera or Upload to replace the photo.

**Why this priority**: Photo intake already works in 001 as a functional concern — this story is about re-presenting it in the mockup's visual language. Without it, the Setup tab looks unfinished, but the underlying mechanics are unchanged, so this lands alongside the form but can be iterated on.

**Independent Test**: Click Upload, pick a valid image file — the circular placeholder updates to show the image preview; the surrounding sizing / aspect does not shift the page. Click Camera — either a camera view opens (where supported) or a graceful fallback message appears (where not). Click Upload again with a new file — the preview updates to the new image.

**Acceptance Scenarios**:

1. **Given** no photo has been supplied, **When** the Setup tab renders, **Then** the circular photo area shows a camera icon and the label "Add photo", with no image preview.
2. **Given** a photo has been supplied (via Camera or Upload), **When** the page re-renders, **Then** the circular photo area shows the photo preview (cropped to the circle) instead of the "Add photo" placeholder, and the two pill buttons remain visible underneath so the user can replace the photo.
3. **Given** the browser does not grant camera access, **When** the user presses the Camera button, **Then** a recoverable message explains the situation and the Upload path remains functional and clearly offered (per 001 FR-001).

---

### Edge Cases

- **Long option labels at small viewports.** When the viewport narrows, pill labels (e.g. "Cloud Architect", "Lord of Rings") must remain legible — either through wrapping, truncation with a tooltip, or a layout collapse to a single column within each sub-group. They MUST NOT overflow the tile or be visually clipped without indication.
- **Very small viewports (mobile-sized).** The two-column page layout collapses to a single column, with **Your Photo** above **Role, Universe & Keys**, preserving order and accessibility.
- **Sub-sections overflow the fold.** With four sub-groups plus a Name field stacked vertically on the right, the page will scroll. Primary navigation (the tab bar) MUST remain accessible (either sticky or reachable via Tab order) so users can switch tabs without first scrolling back to the top.
- **User reloads the page mid-setup.** Consistent with 001 FR-024, no state is persisted: all selections and any in-progress photo are cleared on reload. The user re-enters Setup with an empty form — this behaviour is unchanged and must not regress.
- **User activates the "2 Your Alter Ego" tab without ever generating.** Tab content shows a calm empty state (e.g. "Your alter ego will appear here once you generate it"), not an error. Empty-state content MUST NOT imply that generation has failed or is broken.
- **Switching tabs discards or preserves Setup inputs?** When the user switches from Setup to Your Alter Ego and back, all entered selections, Name text, and the photo preview MUST be preserved for the current session.
- **Reduced motion / no-JS.** Honouring `prefers-reduced-motion` and no-JS operation are explicitly deferred to the follow-up accessibility feature flagged by 001 FR-026; this feature only requires that any decorative transitions added for the new look have no motion effect that lasts longer than 200 ms, so they are not themselves a violation.

## Requirements *(mandatory)*

> **Carried over from 001 unchanged**: FR-001/002/003 (photo intake), FR-004 (pose — option set preserved: Heroic, Stealthy, Mystical, Scholar), FR-008 (first name), FR-009/010 (Generate gating behaviour), FR-016/017 (no-persistence and credential boundaries), FR-018/019 (resilience), FR-020–023 (WCAG 2.1 AA), FR-024/025/026 (scope exclusions).
>
> **Superseded / replaced by this feature**:
> - **001 FR-005 (accent colour picker) is retired.** Colour is no longer a user-selectable input; the generated poster's accent treatment is decoupled from user input and derived by the stub from the (archetype, universe) pair instead.
> - **001 FR-006 (archetype option set) is re-themed.** The field name `archetype` and its wire shape remain, but its enum values change to the six engineering roles from the mockup (see FR-116). The visible label is "Engineer role" / "Role".
> - **001 FR-007 (universe option set) is expanded** from "at least four" to the six specific options in the mockup (see FR-117).
>
> **Backend contract delta**: `GenerateRequest` keeps `photo`, `pose`, `archetype`, `universe`, `firstName`; adds **optional** `vibe`; **removes** `colour`. `GeneratedPoster`'s accent-colour property is driven by the stub from (archetype, universe) rather than from a user selection. Stub payloads for the new archetype and universe enum values must be introduced.

### Functional Requirements

**Top-level tabbed layout**

- **FR-101**: The system MUST present two top-level tabs, labelled "1 setup" and "2 Your Alter Ego", that span the full page width and are visible from first paint. The numbering is part of the visible label.
- **FR-102**: Exactly one tab MUST be active at a time. The "1 setup" tab MUST be the default active tab on initial load and after "Start over" (from 001 FR-013).
- **FR-103**: The active tab MUST be communicated by a visible indicator (colour accent, underline, or equivalent) AND by at least one non-colour signal (position marker, weight, or an accessible-name/state change). Selection MUST NOT rely on colour alone.
- **FR-104**: The tablist MUST be operable by keyboard alone with a visible focus indicator: Tab / Shift+Tab to enter and leave the tablist, Left/Right arrow keys to move focus between tabs, and Enter or Space to activate the focused tab.
- **FR-105**: The tablist MUST be announced to assistive technologies as a tab control with the correct selected/inactive state per tab, and each tab MUST be programmatically associated with its tabpanel.
- **FR-106**: When the user switches tabs, all user-entered Setup inputs (selections, name, photo preview) MUST be preserved in the current session; switching tabs MUST NOT clear or reset the form.
- **FR-107**: The "2 Your Alter Ego" tab MUST render one of three states depending on session phase: (a) an empty-state placeholder pre-generation (calm explanatory text — not broken, blank, or error-like); (b) the generation loading indicator during generation (per 001 FR-011); (c) the generated poster + "Start over" controls (per 001 FR-012, FR-013) post-generation. The poster view's composition is inherited from 001 and is not re-specified here.
- **FR-108**: Pressing Generate MUST immediately (before the request completes) auto-switch the active tab to "2 Your Alter Ego" so the loading indicator and the eventual poster render there. The Setup tab MUST remain reachable during and after generation — switching back to Setup MUST NOT cancel an in-flight generation and MUST preserve the user's original Setup inputs (per FR-106). If an in-flight generation completes while the user is back on Setup, the Your Alter Ego tab's panel MUST update in the background so the result is visible when the user returns; the Setup tab MUST NOT auto-switch the user back.
- **FR-109**: The tab numbering and the auto-switch MUST NOT rely on the "2 Your Alter Ego" tab being disabled pre-generation to gate access. The tab is a navigation affordance, not a workflow gate; gating is enforced by the Generate button per FR-122.

**Setup tab — overall layout**

- **FR-110**: The Setup tab MUST present two columns on a typical desktop viewport (≥ 1024 px wide): a left column headed "YOUR PHOTO" with the sub-label "Real face as a direct reference", and a right column headed "ROLE, UNIVERSE & KEYS". At smaller viewports, the columns MUST collapse to a single column with the photo column first.
- **FR-111**: The Setup tab's visual treatment MUST follow the mockup's dark theme (near-black background, surfaces with subtle elevation, light text, a cyan primary accent, and a purple / gold secondary accent for selected states per sub-group). It MUST remain consistent with the design tokens already established in 001.
- **FR-112**: All text and interactive elements on the Setup tab MUST meet WCAG 2.1 AA contrast ratios against their backgrounds (4.5:1 for body text, 3:1 for large text and non-text interactive indicators).

**Setup tab — Photo column**

- **FR-113**: The photo column MUST present, from top to bottom: the section heading "YOUR PHOTO" with its sub-label, a large circular photo area (showing a camera icon and the label "Add photo" when empty; showing the selected photo cropped to the circle when a photo is present), and two side-by-side pill buttons labelled "📷 Camera" and "🗂 Upload".
- **FR-114**: The Camera and Upload controls MUST behave identically to the photo intake defined by 001 FR-001 and FR-002 (capture or upload a photo; allow replacement). This feature does not change the photo intake behaviour — only its presentation.

**Setup tab — Selections column**

- **FR-115**: The selections column MUST contain four numbered sub-groups and one text input, in this order: "1 Pose:", "2 Engineer role:", "3 Universe / Style:", "4 Vibe (optional):", "👤 Your name". (The mockup shows three numbered sub-groups; this feature adds Pose as a fourth sub-group at the top of the column, preserving the 001 pose concern.)
- **FR-116a**: The **Pose** sub-group MUST present exactly four options in a two-column grid, in this order: Heroic, Stealthy, Mystical, Scholar. Exactly one selection is required to proceed to generation. The options and wire values carry forward from 001 FR-004 unchanged.
- **FR-116**: The **Engineer role** sub-group MUST present exactly six options in a two-column grid, in this order: ☁ Cloud Architect, ⚙ Backend Dev, 🎨 Frontend Dev, 🤖 AI Engineer, 🔧 Platform Eng., 📊 Data Engineer. Exactly one selection is required to proceed to generation. These values replace the 001 archetype enum.
- **FR-117**: The **Universe / Style** sub-group MUST present exactly six options in a two-column grid, in this order: 👩 Marvel, 🌌 Star Wars, 🏙 Cyberpunk, 📇 The Office, 🤠 Indiana Jones, 🪔 Lord of Rings. Exactly one selection is required to proceed to generation. These values replace the 001 universe enum.
- **FR-118**: The **Vibe** sub-group MUST present exactly four options in a two-column grid, in this order: 🔨 Builder, 🧠 Thinker, ⚡ Rebel, 🗼 Architect. Zero or one selection is permitted; an already-selected Vibe option MUST be deselectable by clicking it again. Vibe is explicitly optional — the absence of a Vibe selection MUST NOT block generation. Vibe is a new input introduced by this feature.
- **FR-119**: The **Name** input MUST accept free-form text, show the placeholder "e.g. Paula" when empty, and be programmatically associated with its label "Your name (for personalized character)". It continues to fulfil the role of the first-name field from 001 FR-008.
- **FR-120**: Each selectable option (pill) MUST indicate its selected state by both a visible outline in the sub-group's accent colour AND a non-colour signal (e.g. an `aria-pressed="true"` / `aria-checked="true"` attribute, or a checkmark), so selection is never conveyed by colour alone.
- **FR-121**: Each sub-group MUST behave as a single-selection control (radio-style): selecting a new option MUST deselect the previously selected option in that same sub-group. The Vibe sub-group additionally allows zero selections (per FR-118).
- **FR-122**: The Generate control MUST be disabled until every **required** input is present: photo, pose, archetype (role), universe, and first name. Vibe MUST NOT contribute to the gating check. This re-states 001 FR-009/010 against the new field set.
- **FR-123**: The Generate control MUST be placed at the bottom of the right-hand selections column, directly below the Name input. It MUST NOT be rendered as a sticky action bar, inside the tab bar, or spanning both columns. Its visible width MUST match the width of the selections column (so it lines up with the sub-group grids above it).
- **FR-124**: When the user presses Generate after a previous generation has already produced a poster (i.e. the Your Alter Ego panel is in state (c) per FR-107), a new generation run MUST begin immediately with no confirmation prompt and no disabled state. The previous poster MUST be discarded — replaced first by the loading indicator (FR-107b) and then by the new poster (FR-107c). No caching, deduplication, or "variants" behaviour applies. Only the explicit "Start over" action (001 FR-013) clears the entire session and returns to an empty Setup form.

**Scope: backend and downstream adjustments**

- **FR-130**: The backend `GenerateRequest` contract MUST be updated in this feature to match the new form: retain `photo`, `pose`, `archetype` (new enum values), `universe` (new enum values), `firstName`; add optional `vibe`; remove `colour`. The generation loading state, poster view composition (except the colour-accent source), resilience / retry behaviour, and Start-over flow all remain as defined in 001.
- **FR-131**: The stubbed character and image generators MUST be updated to recognise the new archetype and universe enum values and to produce plausible differentiated output across them (per 001 FR-015's intent, re-keyed to the new values).
- **FR-132**: The generated poster's accent colour, previously driven by the user's colour selection (001 FR-012), MUST be derived by the stub from the (archetype, universe) pair. The poster MUST continue to render an accent colour in 100% of successful runs; only the *source* of that colour changes.

### Key Entities

- **TabState** — the in-memory state of which top-level tab is active ("setup" or "alter-ego"). A single session-level value. Not persisted.
- **SetupSelections** — the user's in-progress selections on the Setup tab: pose (required), archetype / role (required), universe (required), vibe (optional), first name (required), and photo (required). Evolves 001's `AlterEgoSession` selection shape by dropping `colour`, adding optional `vibe`, and re-themeing the archetype enum values; pose, universe, firstName, and photo are retained.
- **Pose** — the enumeration of pose / stance values, carried forward from 001 FR-004 unchanged (Heroic, Stealthy, Mystical, Scholar).
- **Archetype (Role)** — the enumeration of engineering-role values that replace the 001 archetype enum, with exactly the values listed in FR-116.
- **Universe** — the enumeration of setting values that replace the 001 universe enum, with exactly the values listed in FR-117.
- **Vibe** — a new optional enumeration, with exactly the values listed in FR-118.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-101**: On first paint of the Setup tab, a new user can identify the two top-level tabs, the two-column layout, and all five input regions (Pose / Role / Universe / Vibe / Name) in **under 10 seconds** without instructions (observed in a 5-participant usability walkthrough).
- **SC-102**: A user can complete every required Setup selection (pick pose, pick role, pick universe, type name, pick photo) plus the optional vibe and reach the end of the Setup form using **keyboard only** in **under 90 seconds**, with every control reachable in a logical Tab order.
- **SC-103**: An automated accessibility scan (axe-core or equivalent) across the Setup tab (empty state, partially filled, fully filled) reports **zero "serious" or "critical" violations**. Contrast scans confirm **100% of text/interactive elements meet WCAG 2.1 AA** against their backgrounds.
- **SC-104**: At a viewport width of 1440 px the page MUST render without horizontal scroll in **100%** of supported browsers (latest Chrome, Firefox, Safari). At 375 px (narrow mobile) the page MUST stack to a single column without horizontal overflow in the same browsers.
- **SC-105**: Switching between the two top-level tabs **preserves** all user-entered Setup inputs in **100%** of tests (selections, name, photo preview); no input is silently dropped or reset by tab switching.
- **SC-106**: In a visual-diff check against the supplied mockup at 1440 px, the layout matches the mockup's relative position, sizing, and order of every labelled element the mockup shows (tabs, section headings, sub-group labels, option count per visible sub-group, option order, Camera/Upload placement, name input placement). The **Pose** sub-group is an explicit addition beyond the mockup and sits at the top of the selections column; its presence MUST NOT be treated as a structural deviation. "Matches" means no structural deviation otherwise; exact pixel-for-pixel fidelity is not required.
- **SC-107**: The "2 Your Alter Ego" tab renders a placeholder empty state in **100%** of pre-generation visits, and does not render a broken, blank, or error-like state in any observed scenario.

## Assumptions

- The mockup supplied in GitHub issue #3 (archived as `mockup.png` in this directory) is the authoritative visual reference for this feature. A pixel-perfect copy is not required; the structural and semantic match described in FR-110–FR-121 and SC-106 is.
- All content remains English-only (no localisation in scope).
- The visual design tokens established in 001 (`src/styles/tokens.css`) are the starting point; this feature may extend them (add an accent-cyan, refine section header styling) but MUST NOT abandon the dark-theme foundation or replace the token system.
- The "Your Alter Ego" tab's *content* is deferred to a later sub-task; only the tab shell and empty-state placeholder are delivered here.
- Emoji used in option labels (☁, ⚙, 🎨, 🤖, 🔧, 📊, 👩, 🌌, 🏙, 📇, 🤠, 🪔, 🔨, 🧠, ⚡, 🗼, 👤, 📷, 🗂) are taken from the mockup and are reasonable approximations; minor substitutions are acceptable so long as each option has a unique, recognisable glyph that renders across supported browsers.
- The Generate control's placement is defined by FR-123; its enabled/disabled gating logic is defined by FR-122 (which re-states 001 FR-009/010 against the new field set). Label and visual styling are plan-level decisions within the sleek-modern dark theme established by FR-111.
- The form's eventual submission keeps all behaviour described by 001 FRs not listed as superseded here (no-persistence, retry/backoff, fallback poster, Start-over). Only the `GenerateRequest` *payload shape* changes, per the delta captured in FR-130.
- The new Setup form does not change how generation state transitions are announced to assistive technologies (001 FR-022 still applies).
- A dedicated tabbing pattern follows established practice (e.g. the WAI-ARIA Authoring Practices "Tabs" pattern). Specific library / framework choices are a planning-phase concern.

---

## Dependencies

- **Depends on**: 001-initial-poc (this feature reshapes the 001 UI and inherits its backend, accessibility, and resilience baselines).
- **Upstream constraint**: The Constitution's Technology Standards (React + TypeScript on the frontend) and Principle-level accessibility / resilience requirements continue to apply.
- **Does not depend on**: any real third-party provider; the stubs from 001 remain in place.

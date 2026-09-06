# Phase 0 Research: Initial Styling and Layout — Tabbed Setup Experience

**Feature**: 002-sleek-tabbed-ui
**Date**: 2026-04-22
**Purpose**: Resolve design ambiguities and lock architectural choices before Phase 1. All NEEDS CLARIFICATION items from `plan.md` are addressed here. Items settled during `/speckit.specify` and `/speckit.clarify` are referenced, not re-debated.

---

## R1 — WAI-ARIA Tabs pattern: automatic vs. manual activation

**Context**. FR-101–FR-105 describe a two-tab pattern with a visible focus indicator, keyboard operability, and correct assistive-tech announcements. The WAI-ARIA Authoring Practices "Tabs" pattern offers two activation modes:
- **Automatic activation**: arrow keys move focus AND immediately change the selected tab (the new tabpanel is revealed as focus moves).
- **Manual activation**: arrow keys move focus only; the user must press Enter/Space to activate the focused tab.

Acceptance scenario 1.3 in `spec.md` says: "arrow keys move focus between tabs with a visible focus indicator, and pressing Enter or Space activates the focused tab." That phrasing describes **manual** activation.

**Decision**. **Manual activation**. Arrow keys move focus only; Enter or Space activates.

**Rationale**.
- Automatic activation is typical when each tabpanel is cheap to render (all panels mounted; CSS hides inactive). Our "2 Your Alter Ego" panel can contain an in-flight generation — auto-activating it on arrow-key transit would make it too easy to interrupt a workflow the user didn't mean to touch.
- Automatic activation also interacts awkwardly with the auto-switch-on-Generate rule (FR-108): a user pressing arrow keys after Generate would immediately be sent back to Setup mid-generation, which is correct for navigation but jarring if arrow-keys are also the selection mechanism.
- Spec text in scenario 1.3 already pins manual.

**Alternatives considered**.
- Automatic activation — rejected for the reasons above.
- Custom hybrid (focus moves and Enter activates, but arrow keys on a pre-revealed tab swap into "automatic mode") — rejected as non-standard; burns the WAI-ARIA convention the audience will assume.

**Implementation notes**.
- Standard roles: `role="tablist"` on the container; `role="tab"` on each button; `role="tabpanel"` on each panel; `aria-selected` on tabs; `aria-controls` pointing from tab → tabpanel ID; `tabindex="0"` on the selected tab, `tabindex="-1"` on the others (roving tabindex).
- Keyboard handling: on the tablist, `ArrowLeft`/`ArrowRight` move the DOM focus by rotating roving tabindex and calling `.focus()` on the new tab; `Enter` or `Space` calls the component's `onTabChange`.

---

## R2 — Auto-switch on Generate: coordinating mutation state with tab state

**Context**. FR-108 requires the active tab to auto-switch to "2 Your Alter Ego" the instant Generate is pressed — before the network request starts. The user must still be able to switch back to "1 setup" during generation without cancelling anything.

**Decision**. Dispatch an `ActiveTabChanged → 'alter-ego'` action from TanStack Query's `onMutate` callback on the generation mutation. Keep the mutation itself fully owning the network lifecycle (retries, fallback, cancellation guards). Tab state is in the reducer, not in TanStack Query.

**Rationale**.
- TanStack Query mutations emit `onMutate` synchronously before the async fetcher runs. That's exactly the moment FR-108 describes ("immediately before the request completes").
- Keeping tab state in the existing reducer (not in TanStack Query) keeps persistence rules uniform: there is no persistence, and the reducer state is already the session's single source of truth.
- The user can dispatch `ActiveTabChanged → 'setup'` at any time by clicking tab 1; this does not cancel the in-flight mutation (mutations are keyed to the controlled component instance, not the visible tab). When the mutation resolves, the "2 Your Alter Ego" panel updates regardless of which tab is visible.

**Alternatives considered**.
- **Dispatch from the click handler before calling `submit`.** Rejected: introduces a timing window where the tab has switched but the mutation hasn't fired yet, making it possible (under a network error + user-double-click) for the tab state to desync from the mutation state.
- **Store active tab in TanStack Query via `queryClient.setQueryData` and derive it from mutation status.** Rejected: overloads Query's cache with UI state; spec explicitly wants the tab to remain user-controllable during and after generation.
- **Force the "2 Your Alter Ego" tab to be "virtually selected" while a mutation is pending even if the user navigated back to Setup.** Rejected: fights the user's explicit input; violates FR-108's "switching back to Setup must not cancel or auto-switch the user back."

**Implementation notes**.
- New reducer action: `{ type: 'ActiveTabChanged'; tab: 'setup' | 'alter-ego' }`.
- In `useGenerateAlterEgo.ts`, inside `onMutate`: `dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego' })`.
- On `StartOverRequested`: reducer resets `activeTab` to `'setup'` as part of the existing reset branch (satisfies FR-102).

---

## R3 — Pre-generation empty-state copy for "2 Your Alter Ego"

**Context**. FR-107(a) requires a "calm explanatory text" empty-state before any generation has run. The exact copy is plan-level.

**Decision**. Short, single-sentence copy under a subtle icon:
> "Your alter ego will appear here once you press **Generate** on the Setup tab."

**Rationale**.
- One sentence. No emoji cluster. Names the control the user must press. Directs the user back to the Setup tab explicitly so the "where do I go?" question doesn't become a bug report.
- Matches the "sleek modern" visual voice: muted, directive, no over-explanation.

**Alternatives considered**.
- A preview of the user's current Setup selections ("You'll be a Cloud Architect in Star Wars…"). Rejected: introduces a second source of truth that duplicates the Setup tab; risk of divergence when the user changes selections without returning to tab 2; adds test surface for a non-P1 concern.
- A disabled-state visual that hides the placeholder content entirely. Rejected: FR-109 makes clear the tab is *not* a workflow gate, so it should not look disabled.
- A richer illustration (mascot, animated loader preview). Rejected: scope creep; the tab is a placeholder in this sub-task.

**Implementation notes**.
- One `<p>` with the copy; a visually neutral icon (Lucide `UserCircle2` or similar) above it; both wrapped in a centred flex column.
- Bold the word "Generate" so screen readers convey emphasis matching the sighted emphasis, and provide a `<kbd>`-less rendering (no keyboard shortcut implied).

---

## R4 — Accent-colour mapping per selection sub-group

**Context**. The mockup uses distinct accent colours for the selected state in each sub-group: **Engineer Role → cyan**, **Universe → gold**, **Vibe → purple**. The spec leaves exact values plan-level.

**Decision**. Pin the mapping as follows and extend `tokens.css` accordingly:

| Sub-group | Selected-state accent | Token |
|---|---|---|
| Pose (new in layout, existing concern) | Cyan | `--color-accent-pose: #1a8aaa` (already defined as `--color-accent-blue` in 001 — alias, don't duplicate) |
| Engineer Role (archetype) | Cyan | `--color-accent-role: #00bfff` (already defined as `--color-accent-cyan` — alias) |
| Universe / Style | Gold | `--color-accent-universe: #f0a030` (already defined as `--color-accent-gold` — alias) |
| Vibe | Purple | `--color-accent-vibe: #a855f7` (already defined as `--color-accent-purple` — alias) |

The semantic aliases (`--color-accent-pose`, `--color-accent-role`, `--color-accent-universe`, `--color-accent-vibe`) are the ones components reference; they are resolved from the existing colour tokens carried forward from 001. This lets the Colour enum tokens be *removed* from runtime use in this feature without deleting their definitions in 001 (those definitions stay in `tokens.css` as the semantic-alias source, re-purposed as per-sub-group accents).

**Rationale**.
- Matches the mockup's observed hues for the three visible sub-groups.
- Pose and Role both land on cyan because the mockup's role selection pill is the visual anchor (most prominent selected state); Pose, newly added, needs a non-conflicting accent and shares the role family (both are "what you are" questions). Universe and Vibe stay gold/purple to preserve the mockup's categorisation.
- Aliasing rather than re-defining preserves Principle VI (no new duplicative dependencies or tokens) and makes it trivial to centrally re-theme later.

**Alternatives considered**.
- Four fully distinct accent colours (one per sub-group). Rejected: the mockup visibly uses three; adding a fourth adds visual noise without a scope reason.
- A single accent for every selected state. Rejected: loses the mockup's sub-group-identity signal that makes scanning the page easier.

**Implementation notes**.
- Add semantic aliases in `tokens.css` under a new `/* Sub-group accents */` block.
- Each selection-pill component reads its accent from a prop (`accentVar`) the grid passes down, not from its own local CSS, so the mapping is one-layer-from-the-pill.

---

## R5 — Enum value rename strategy: backend + frontend

**Context**. Archetype and Universe enums shrink/grow and change values. 001 shipped to a closed POC with no external callers, no real users, and no persistence, so there is no backwards-compat burden.

**Decision**. **Hard replacement**. Old values are removed entirely; no aliases, no transitional period.

- Java `Archetype.java` values replaced: `CLOUD_ARCHITECT`, `BACKEND_DEV`, `FRONTEND_DEV`, `AI_ENGINEER`, `PLATFORM_ENG`, `DATA_ENGINEER`.
- Java `Universe.java` values replaced: `MARVEL`, `STAR_WARS`, `CYBERPUNK`, `THE_OFFICE`, `INDIANA_JONES`, `LORD_OF_THE_RINGS`.
- JSON wire values (kebab-case; drives `@JsonValue` + `@JsonCreator`): `cloud-architect`, `backend-dev`, `frontend-dev`, `ai-engineer`, `platform-eng`, `data-engineer`; `marvel`, `star-wars`, `cyberpunk`, `the-office`, `indiana-jones`, `lord-of-the-rings`.
- TypeScript union types in `frontend/src/features/alterego/types.ts` replaced in lockstep.
- OpenAPI schema `enum` arrays replaced (see `contracts/alter-egos.openapi.yaml`).
- Colour enum removed on both sides (Java class deleted, TS type deleted, option list deleted, `tokens.css` Colour-specific tokens demoted to semantic-alias source per R4).
- Vibe enum introduced on both sides (Java `Vibe.java`, TS `Vibe` type).

**Rationale**.
- 001 merged on 2026-04-22 with no external consumers and no persisted data; there is no value to keep old wire strings alive.
- Aliases would force every new site of change (stubs, tests, fallback provider) to maintain two lookup tables, increasing the surface area for test/code drift without paying for any real migration.
- The wire shape already uses kebab-case strings (not ordinals), so renaming is contained — no schema migration, no database migration.

**Alternatives considered**.
- Dual-write period (accept old enum values alongside new). Rejected: no consumers need it and it doubles the validation surface.
- Soft rename with a deprecation note in the OpenAPI `description`. Rejected: same reason; additionally, keeping deprecated values in a POC contract violates Principle VI's spirit (zero deprecated surface).

**Implementation notes**.
- Rename Java enum values in one commit (constants + `@JsonValue` + `@JsonCreator`).
- Re-run `./gradlew test` to surface every reference in stubs, fallbacks, tests — the compiler plus the test run is the rename "tooling."
- For TS, `tsc --noEmit` after the `types.ts` change will identify every downstream file needing a touch.

---

## R6 — Deriving the poster accent colour from `(archetype, universe)`

**Context**. FR-132 says the stub (and fallback) must derive the poster's accent colour from the `(archetype, universe)` pair rather than from a user-picked `colour` input. A deterministic mapping keeps the feature testable.

**Decision**. Maintain a small lookup table on the backend: `(Archetype, Universe) → Colour-label` (where "Colour-label" is one of six hex values: `#1a8aaa` blue, `#a855f7` purple, `#e05858` red, `#00bfff` cyan, `#f0a030` gold, `#3dd68a` green). Populate enough of the 36-cell grid to ensure every (archetype, universe) pair has a colour; unspecified cells fall through to a deterministic hash of the pair into the six-value palette.

**Rationale**.
- Deterministic output makes SC-005-style matrix tests trivial ("every pair yields a non-null colour in the expected palette").
- Avoids the ambiguity of "implicit default from archetype alone" (silently dropping universe as an input, which would violate FR-131's "differentiated output across combinations").
- Keeps the poster's visual language diverse without adding a user-input burden.

**Alternatives considered**.
- Hash `(archetype, universe)` into a palette with no explicit overrides. Rejected: loses the narrative "Star Wars + Cloud Architect should feel cyan-ish" intent; harder to explain to stakeholders why a poster looks a given way.
- Always use a single colour (e.g. the brand gradient). Rejected: undoes the per-sub-group colour signal and makes every poster visually identical in the stubbed demo.

**Implementation notes**.
- New method `Colour deriveAccent(Archetype a, Universe u)` on `StubImageGenerator` (and mirrored in `FallbackPosterProvider`).
- Test: parametrised over every (archetype, universe) pair asserting a non-null result within the palette.

---

## R7 — Emoji consistency across supported browsers

**Context**. The mockup uses emoji as the visual anchor of every selection pill (☁, 🤖, 🔨, etc.). FR-113 + FR-116 + FR-117 + FR-118 + Assumptions all pin specific glyphs.

**Decision**. Render emoji as native Unicode, font-stack-styled. Add `font-family: "Apple Color Emoji", "Segoe UI Emoji", "Noto Color Emoji", sans-serif` as a fallback on the label span; do NOT ship a bundled emoji font. Pair every emoji with the text label it accompanies so the information never depends on the glyph rendering correctly.

**Rationale**.
- All three supported browsers (latest Chrome, Firefox, Safari) on macOS and Windows ship with a native colour emoji font. Linux + Firefox needs `Noto Color Emoji` but most distributions have it; users on an older Linux may see a monochrome fall-back, which is acceptable because the label carries the semantic content.
- Bundling an emoji font costs 3–5 MB of font payload for zero-accessibility gain; it also introduces a runtime dependency that'd need CVE monitoring per Principle VI.

**Alternatives considered**.
- Bundle Twemoji SVG sprites. Rejected: payload cost, new dependency.
- Replace all emoji with Lucide icons (already an approved icon library per the Technology Standards). Rejected for *this* feature because the mockup clearly uses emoji and part of the "sleek modern" read is the emoji-text pairing; however, the option is recorded here in case SC-106 structural-match fails on a specific platform, we swap that specific glyph to a Lucide icon without rejecting the whole approach.

**Implementation notes**.
- Option labels stay as `{ emoji, value, label }` triples in `options.ts`; the emoji is rendered inside a `<span aria-hidden="true">` so the accessible name is the label alone.

---

## R8 — Preserving state across tab switches

**Context**. FR-106 + SC-105 require Setup inputs to be preserved when the user switches to tab 2 and back. The existing 001 reducer keeps all selections in the session state; switching tabs is a pure navigation event.

**Decision**. Tabs are rendered as two persistent panels with CSS visibility (`hidden` attribute + `display: none` for aria-hidden) rather than conditionally unmounted. The Setup form inputs remain in the React tree even when tab 2 is active, so their controlled state (and the reducer state they reflect) is never torn down.

**Rationale**.
- Conditional unmounting would lose any uncontrolled focus position / scroll state within a panel even if the reducer state survives; users' re-entry to tab 1 would land in an arbitrary location.
- Reducing re-render cost is a non-issue at this scale (small forms, no expensive components).
- Keeps the WAI-ARIA semantics simple: both `<div role="tabpanel">` elements exist in the DOM at all times; the inactive one has `hidden` + `aria-hidden="true"`.

**Alternatives considered**.
- Conditional render with state hoisted to reducer. Rejected: reducer already holds selections but not focus/scroll; users expect "return to where I was."
- Use React Router with two routes. Rejected: adds a dependency for a concern that is purely session-local; deep-linking is not a spec requirement.

**Implementation notes**.
- The `TabsShell` component renders both panels inside its children and toggles their `hidden` / `aria-hidden` based on `activeTab`.

---

## R9 — Visual-regression baseline for SC-106

**Context**. SC-106 requires "no structural deviation" from the mockup at 1440 px. The team already has Playwright; adding a full visual-regression framework (Percy, Chromatic) would be scope creep.

**Decision**. Ship an assertion-based Playwright spec that checks structural landmarks (tab count, tab labels, column count, sub-group count per column, option count per sub-group, option order within each sub-group, emoji-label pairing), screenshots the Setup tab at 1440 px as an *archived artifact* (not a diff baseline), and compares visually during manual review against `mockup.png`. No automated diff tool.

**Rationale**.
- The spec explicitly says pixel-perfect is not required (SC-106 text).
- Assertion-based checks catch the structural regressions SC-106 actually cares about, without the operational cost (and HIGH/CRITICAL CVE monitoring) of adding Percy/Chromatic under Principle I/VI.
- The archived screenshot is a review aid, not a gate, which is a reasonable trade-off for a POC-level feature.

**Alternatives considered**.
- Percy/Chromatic. Rejected: new runtime dependency, API key management, monthly cost concern outside the POC budget.
- `@playwright/experimental-ct-react` with snapshot diffing. Rejected: premature; structural assertions cover the spec.

**Implementation notes**.
- One test case in `tests/playwright/tabs-shell.spec.ts`: `expect(page.locator('[role="tab"]')).toHaveCount(2)` etc., then `await page.screenshot({ path: 'test-results/setup-tab-1440.png' })`.

---

## R10 — Start-over flow in the new layout

**Context**. FR-102 says Start over returns the active tab to "setup." The button itself currently lives next to the poster (001 FR-013), which in the new layout is inside the "2 Your Alter Ego" panel.

**Decision**. Keep the Start-over button inside the poster view where 001 already places it. When pressed, it dispatches two actions in sequence: the existing `StartOverRequested` (which clears all session state per 001) and the new `ActiveTabChanged → 'setup'`. The two dispatches are batched into a single reducer call by handling the tab reset inside the `StartOverRequested` branch of the reducer itself, so there is a single state transition.

**Rationale**.
- Minimises surprise: the user pressing Start over while reading the poster on tab 2 lands immediately on an empty Setup tab.
- One reducer transition means a single re-render, no flicker of an empty poster panel before the tab switches.
- Matches FR-102's language ("the '1 setup' tab MUST be the default active tab on initial load and after 'Start over'").

**Alternatives considered**.
- Move the Start-over button into the tab bar. Rejected: conflates navigation with a destructive action; spec places it with the poster.
- Keep Start-over but require the user to manually click tab 1 after. Rejected: adds a pointless extra step; the spec implicitly links the two transitions.

**Implementation notes**.
- Extend the existing `StartOverRequested` reducer branch to reset `activeTab` to `'setup'` (in addition to resetting selections, firstName, photo).

---

## Unresolved / explicitly deferred

Items intentionally **not** resolved here (they stay deferred):

1. **`prefers-reduced-motion` honouring.** Carried forward from 001 FR-026; out of scope for this feature.
2. **Deep-linking / URL routing for each tab.** Not required by the spec; adding React Router would violate the "no new runtime dependencies" stance taken in R8. Can be revisited if a share-link feature is ever specified.
3. **Exact Start-over button wording.** A plan/style-level choice; stays as 001 specified.
4. **Visual-regression automated diffing.** See R9 — not adopted for this feature.

---

## Summary

| # | Topic | Decision | Principle impact |
|---|---|---|---|
| R1 | WAI-ARIA tabs pattern | Manual activation | Principle I/III |
| R2 | Auto-switch timing | Dispatch from `onMutate` | Principle IV (no interference) |
| R3 | Empty-state copy | Single directive sentence | UX clarity |
| R4 | Sub-group accent mapping | Pose/Role cyan, Universe gold, Vibe purple | Token hygiene |
| R5 | Enum rename strategy | Hard replacement | Principle VI |
| R6 | Poster accent derivation | Lookup table w/ hash fallback | Deterministic tests |
| R7 | Emoji rendering | Native Unicode + font-stack fallback | Principle VI |
| R8 | Tab-switch state preservation | Persistent panels with `hidden` | UX predictability |
| R9 | Visual-regression approach | Structural assertions + archived screenshot | Principle I/VI |
| R10 | Start-over flow | Single reducer transition resets tab | Spec compliance |

All items either settled or explicitly deferred. No `NEEDS CLARIFICATION` remains. Phase 1 can proceed.

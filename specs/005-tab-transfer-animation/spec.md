# Feature Specification: Tab Transfer Animation on Generate

**Feature Branch**: `005-tab-transfer-animation` (artifacts under `specs/`; developed on `claude/animation-transferring-to-your-alter-ego-tab-6S2Mi` per task instructions)
**Created**: 2026-04-23
**Status**: Draft
**Input**: GitHub issue [#15 — Animation Transferring to Your Alter Ego tab](https://github.com/squer-solutions/ai-codecrafts-alterego/issues/15). The issue asks for a smooth animation when the user is transferred to the "Your Alter Ego" tab on "Generate my alter ego" (and "Surprise Me" — a button that does not exist in the codebase today; noted out of scope).

> **Relationship to 002-sleek-tabbed-ui.** This feature refines the two-tab shell that 002 delivered. 002 FR-106 / SC-105 (both tabpanels always mounted, Setup form state survives tab switches) and 002 FR-108 (`aria-selected` flips the instant Generate is clicked) remain true; this feature layers a motion affordance on top of the already-instant auto-switch, without changing its semantics.

## Clarifications

### Session 2026-04-23

- Q: Which motion vocabulary should the incoming Alter Ego panel use? → A: Combined cross-fade (opacity 0 → 1) + small upward translate (translateY 12px → 0). A cross-fade alone reads as a lazy dissolve; a translate alone reads as a mechanical slide; together they imply "content arriving from below", which matches the "step 1 → step 2" workflow narrative of the tab shell.
- Q: What animation duration is acceptable? → A: ~320 ms on an ease-out curve. Short enough that the `aria-selected` assertion never races the animation on CI, long enough to read as a deliberate hand-off rather than a glitch. Stays inside the 200–400 ms "responsive but visible" motion band used by most modern UI kits.
- Q: Which triggers fire the animation? → A: Generate only. Manual tab clicks (either direction) and Start-over remain instantaneous, preserving 002 behaviour. The animation is strictly the Setup → Alter Ego auto-switch; any future "Surprise Me"-style trigger would dispatch the same `reason: 'generate'` signal and inherit the motion automatically.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Pleasant hand-off on Generate (Priority: P1)

A user has filled the Setup tab, picked a photo, and clicks "Generate my alter ego". The active tab switches to "2 Your Alter Ego" and, at the same time, the incoming panel fades in and glides up a small amount as it takes over the viewport. The motion lasts about a third of a second — long enough to feel like the app is introducing the new view, short enough to stay out of the way of the loading indicator that follows. Without this motion, the tab swap feels like a hard cut that coincides with the loading spinner appearing, which reads as a jarring glitch rather than a deliberate step.

**Why this priority**: This is the entire point of the issue. Shipping motion on the Generate-triggered auto-switch is the minimum viable delivery — the feature has no other mandatory slice.

**Independent Test**: Load the app at 1440 × 900, fill every required selection, pick a photo, click Generate. Observe the "2 Your Alter Ego" tab becomes the active tab and, while still at opacity < 1 / translated upward, is visibly painting in. After ~320 ms the panel is fully opaque, untranslated, and the loading indicator is visible inside it. Manual tab clicks (clicking the Setup / Alter Ego tabs by hand) do NOT animate — they flip instantly, matching today's behaviour.

**Acceptance Scenarios**:

1. **Given** the user is on the Setup tab with all required inputs filled, **When** they click "Generate my alter ego", **Then** the "Your Alter Ego" tab becomes active AND its panel visibly fades and slides in over a short, finite duration before settling in its final position.
2. **Given** the user is on the Setup tab, **When** they click the "Your Alter Ego" tab directly (with or without a prior generation), **Then** the tab-switch is instantaneous (no animation), matching 002 behaviour.
3. **Given** a generation has just started and the auto-switch animation is in progress, **When** the user looks at the Setup tab's form, **Then** all previously entered values (photo, pose, role, universe, vibe, name) are still present if they switch back — the motion does not reset any form state (002 SC-105 invariant).
4. **Given** the user presses Generate again after a previous successful generation while still on the Alter Ego tab, **When** the click fires, **Then** the Alter Ego panel re-animates (the motion re-plays) so the hand-off affordance is consistent across runs.
5. **Given** the user clicks "Start over" from the Alter Ego tab, **When** the reducer resets to the initial state and the active tab flips back to Setup, **Then** the Setup tab becomes active instantly with no motion — the animation is scoped to the Generate direction only.

---

### User Story 2 — Respect `prefers-reduced-motion` (Priority: P1)

A user with `prefers-reduced-motion: reduce` active (OS-level accessibility preference) clicks "Generate my alter ego". The tab switches to "Your Alter Ego" instantly, exactly as 002 ships today — no fade, no slide, no delay. The loading indicator and subsequent poster appear in the same instant-switch manner. The motion is a pure enhancement and never imposes on users who have opted out of animation for accessibility, vestibular, or attention reasons.

**Why this priority**: Motion can trigger symptoms for users with vestibular disorders and violates WCAG 2.3.3 (Animation from Interactions, Level AAA) if not gated on the user's preference. Honouring `prefers-reduced-motion` is non-negotiable. Shipping User Story 1 without this guard is an accessibility regression.

**Independent Test**: Enable `prefers-reduced-motion: reduce` at OS level (macOS: System Settings → Accessibility → Display → Reduce motion) OR via Chrome DevTools (Rendering → Emulate CSS media feature → `prefers-reduced-motion: reduce`). Repeat the flow from User Story 1. The tab activates instantly, the panel does not fade or translate, and the loading indicator appears immediately in place. Observable test signal (`data-animating="true"` on the alter-ego tabpanel) may still flip briefly — it is a React-state attribute, not a motion signal.

**Acceptance Scenarios**:

1. **Given** the browser/OS reports `prefers-reduced-motion: reduce`, **When** the user clicks Generate, **Then** the Alter Ego panel becomes visible without any opacity or translate transition — it is instantly fully painted at its final position.
2. **Given** the browser/OS reports `prefers-reduced-motion: no-preference` (default), **When** the user clicks Generate, **Then** the fade + slide transition plays as per User Story 1.
3. **Given** any accessibility setting, **When** the auto-switch fires, **Then** the "2 Your Alter Ego" tab has `aria-selected="true"` within the same commit as the click — the motion never delays the ARIA state transition (protects 002 FR-108).

---

### Edge Cases

- **Rapid repeat clicks on Generate.** If the user double-clicks Generate, the animation re-plays on the second click. The tab is already active; React commits the new `data-animating="true"` state and the keyframe restarts cleanly (no fractional/partial animation state).
- **Click Generate while still on the Setup tab of an already-completed generation.** The previous poster is replaced by the loading indicator under the re-playing animation; the poster being swapped out is part of the panel content, not the panel itself, so the animation target is unambiguous.
- **Tab clicked manually mid-animation.** If the user manually clicks the Setup tab while the Alter Ego panel is mid-animate-in, the tab switch is instant (per User Story 1 Acceptance 2); when they come back, the panel is already settled (no stale animation state).
- **Browser / OS change to `prefers-reduced-motion` while the app is open.** The next Generate click honours the new preference; the change does not need to take effect retroactively on a mid-flight animation.
- **Animation fails to fire / `animationend` event never dispatches** (e.g., tab backgrounded, browser suspended the animation). A safety timeout clears the transient state so the panel does not stay "mid-animation" visually or in the DOM.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-401**: System MUST animate the incoming "Your Alter Ego" tabpanel when, and only when, the active-tab change was triggered by the "Generate my alter ego" button click.
- **FR-402**: System MUST keep the active-tab change itself synchronous with the click — the `aria-selected` attribute on the "Your Alter Ego" tab MUST flip to `true` in the same commit as the click, unchanged from 002 FR-108. The animation MUST NOT delay, gate, or interrupt this ARIA transition.
- **FR-403**: System MUST NOT animate on manual tab clicks (user clicking the tab buttons in the tablist) — those remain instantaneous, preserving today's behaviour from 002.
- **FR-404**: System MUST NOT animate on "Start over" (tab returning to Setup) — scope is the Setup → Alter Ego direction triggered by Generate only.
- **FR-405**: System MUST honour the user's `prefers-reduced-motion: reduce` preference — when set, the panel MUST render at its final visual state immediately, with no opacity or transform transition.
- **FR-406**: System MUST preserve the 002 "both panels always mounted" invariant (002 FR-106 / SC-105). Adding motion MUST NOT require unmounting, re-keying, or otherwise resetting the Setup panel's form state.
- **FR-407**: System MUST keep the animation duration in a range that feels intentional but does not obstruct the loading indicator behind it — on the order of 300 ms (see Assumptions for the chosen default).
- **FR-408**: System MUST expose an observable signal — a transient DOM attribute on the Alter Ego tabpanel — that test automation can latch onto to assert the animation fired without timing the visual duration.
- **FR-409**: System MUST recover from a missed animation-end event (e.g., tab backgrounded) so the transient state never leaks beyond a short safety window — the panel MUST return to its non-animating baseline regardless.
- **FR-410**: System MUST NOT introduce a new runtime dependency for this feature (animation library, CSS framework, etc.) — the motion stays within the existing pure-CSS stack to honour Constitution Principle VI (Zero Deprecated Dependencies) and minimise bundle impact.

### Key Entities

- **ActiveTab change reason**: Describes why the active tab changed on a given dispatch. Two meaningful values today — a user-driven "manual" click and a Generate-triggered auto-switch. Used exclusively to decide whether to animate; does not affect the tab selection outcome.
- **Panel animation state (transient)**: Per-render boolean marker on the incoming "Your Alter Ego" tabpanel that latches when the animation starts and clears when it ends (or after a safety timeout). Drives both the CSS class that triggers the keyframe and the observable test-automation attribute.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-401**: 100% of Generate-triggered transfers to the Alter Ego tab, under the default `prefers-reduced-motion: no-preference` setting, visibly animate (verified by a Playwright test asserting the transient animation attribute is present on the panel within the same commit as the tab-change).
- **SC-402**: 100% of Generate-triggered transfers under `prefers-reduced-motion: reduce` complete without any visual fade or translate on the panel (verified by a Playwright test running with `prefers-reduced-motion: reduce` emulated, asserting no opacity / transform transition completes).
- **SC-403**: 100% of manual tab clicks between Setup and Alter Ego remain instantaneous — no animation fires (verified by a Playwright test asserting the transient animation attribute stays `false` / absent on manual clicks).
- **SC-404**: The active-tab ARIA state (`aria-selected="true"` on the Alter Ego tab) is observable to test automation within the same synchronous tick as the Generate click (protects 002 FR-108; preserved as a Playwright assertion).
- **SC-405**: Setup form state (photo, pose, role, universe, vibe, name) survives a Generate-triggered auto-switch and a subsequent manual return to the Setup tab with zero data loss (the 002 SC-105 invariant is carried forward as a regression test).
- **SC-406**: Unit test line coverage for the reducer branches, animation-signal hook, and TabsShell animation wiring stays at or above the Constitution's 90% gate (Principle III).

## Assumptions

- The animation duration chosen is **~320 ms**, with a cross-fade (opacity 0 → 1) and a 12-pixel upward translate (translateY 12px → 0) on an ease-out curve. Duration is short enough that test automation's `aria-selected` assertion never races against it, and long enough that the motion reads as intentional. No stakeholder indicated a preferred duration, so this is the default; `/speckit.clarify` may revisit.
- The outgoing Setup panel does NOT animate. When the active tab flips, the Setup panel is hidden instantly (preserving today's behaviour and the form-state-survival invariant). Only the incoming Alter Ego panel animates in. This keeps the motion target unambiguous and sidesteps the `display: none` / CSS-animation interaction (`hidden` elements do not run animations).
- The observable test signal is a transient DOM attribute (e.g., `data-animating="true"`) on the Alter Ego tabpanel, flipping on the same React commit as the tab change. It is a state-based attribute (not tied to the actual media query), so it remains observable even when motion is suppressed by `prefers-reduced-motion: reduce` — this is intentional so the same Playwright assertion works in both environments.
- Manual tab-click behaviour is unchanged; `useActiveTab.setActiveTab(tab)` continues to dispatch with the default "manual" reason, and the animation code path treats that as a no-op.
- No persistence requirement applies (Constitution standard for this project; the reason/state is session-scope React state only).
- The "Surprise Me" button mentioned in the GitHub issue does not exist in the codebase and is explicitly out of scope for this feature. If a "Surprise Me" button is added later, its click handler is expected to dispatch the same "generate" reason, reusing the animation automatically.
- The animation is scoped to the Setup → Alter Ego direction only; "Start over" (Alter Ego → Setup) remains instantaneous.
- The feature carries forward the 002 accessibility promises — WAI-ARIA tabs pattern, manual activation, keyboard model — with no change.

## Out of Scope

- A "Surprise Me" button, random-fill shortcut, or any alternate Generate trigger. (If added later, it reuses the "generate" reason by convention.)
- Motion on manual tab clicks, Start-over, error banners, loading indicators, or any other element beyond the Alter Ego tabpanel's entrance.
- Cross-panel choreography (e.g., Setup fading out while Alter Ego fades in).
- Motion preferences beyond the OS-level `prefers-reduced-motion` media query (no in-app toggle).
- Animation-library dependencies (Framer Motion, react-spring, Motion One, etc.); pure CSS stays the rule.

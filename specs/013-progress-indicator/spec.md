# Feature Specification: Image Generation Progress Indicator

**Feature Branch**: `013-progress-indicator`
**Created**: 2026-04-27
**Status**: Draft
**Input**: User description: "During the image generation phase a progress percentage should be shown in the center of the pulsating circle. Take 20 seconds to be 100% of the progress. The percentage progress should be updated at random intervals within those 20 seconds, no less than 2 seconds and no more than 10 seconds. The percentage shown should be an integer number with a percentage sign. The percentage shown should never actually be 100% (when image is ready — it should be shown straight away)."

Closes squer-solutions/ai-codecrafts-alterego#36.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Watching the percentage advance during generation (Priority: P1)

While the alter-ego image is being generated, the user sees the existing pulsating circle on the "Your Alter Ego" tab now display a live, advancing percentage in its centre (e.g. `7%`, `21%`, `54%`). The number gives a sense of "the system is working AND making progress" instead of just "the system is working".

**Why this priority**: This is the entire feature. Without it there is no perceivable progress signal during the (often multi-second) generation step, which is the only remaining "feels frozen" moment in the product.

**Independent Test**: Trigger a generation (Generate or Surprise Me), observe the loading state, and verify a percent value renders inside the pulsating circle, starts low, increases over time, and is replaced by the result poster as soon as the image arrives. Fully testable with the existing 003 stub fallback path — no real Gemini call needed.

**Acceptance Scenarios**:

1. **Given** the user has clicked Generate and the generation is in flight, **When** the loading view appears, **Then** an integer percentage with a `%` sign is rendered visibly centred inside the pulsating circle.
2. **Given** the loading view is shown, **When** time passes, **Then** the percentage value monotonically increases (never decreases, never resets) and updates several times during a typical generation, at irregular intervals.
3. **Given** the generation completes successfully, **When** the result becomes available, **Then** the loading view (and its percentage) is replaced immediately by the alter-ego poster — without a "100%" frame ever being shown.
4. **Given** the generation fails (real → fallback or hard failure), **When** the failure resolves, **Then** the loading view (and its percentage) is replaced immediately by whichever post-generation view normally shows for that outcome — without a "100%" frame ever being shown.
5. **Given** the user starts a second generation in the same session, **When** the loading view re-mounts, **Then** the percentage starts low again (does not retain the previous run's value).

---

### User Story 2 — Honest progress that never claims completion prematurely (Priority: P1)

The user must never see `100%` while still waiting. The number must always read strictly less than 100 for as long as the spinner is visible, even if the real generation takes far longer than the planned 20 seconds.

**Why this priority**: A `100%` reading next to a still-spinning indicator is actively misleading and undermines trust in every other progress UI in the product. Equally critical as P1 above.

**Independent Test**: Force a long generation (or simply hold the loading state open in a unit test), let elapsed time exceed 20 s, and verify the displayed value plateaus strictly below 100 and stays there until the loading view unmounts.

**Acceptance Scenarios**:

1. **Given** the loading view is mounted and 20 or more seconds have elapsed, **When** the next scheduled tick fires, **Then** the displayed value is at most 99 (never 100, never "100%").
2. **Given** the loading view is mounted, **When** any tick fires at any elapsed time, **Then** the displayed value is in the range 0–99 inclusive, written as `<integer>%`.

---

### User Story 3 — Visual integration with the existing pulsating circle (Priority: P2)

The percentage looks like it belongs inside the circle: vertically and horizontally centred, legible against the dark inner disc, and not fighting the existing pulse animation. It also appears to assistive technology as part of the loading status, not as a separate live region that announces every tick.

**Why this priority**: Important for perceived quality, but the feature would still be functionally valuable if the visual styling were imperfect.

**Independent Test**: Visual inspection in the dev server (the app currently uses a dark elevated background); also verify with a screen reader that the existing "Generating your alter ego…" announcement is still made exactly once and the percent value does not spam announcements.

**Acceptance Scenarios**:

1. **Given** the loading view is shown, **When** rendered at the standard viewport, **Then** the percent number is centred inside the inner disc of the pulsating circle (not overlapping the surrounding ring, not obscured by the existing copy).
2. **Given** a screen reader user is on the loading view, **When** the percent value updates, **Then** the screen reader does not announce every numeric tick (the existing `aria-live` "Generating your alter ego…" message remains the canonical announcement).

---

### User Story 4 — Smooth count-up and visual hand-off to the poster (Priority: P3)

The displayed percent **counts up** between two consecutive tick targets rather than jumping in a single frame, and when the generation resolves the poster **emerges out of the same circular shape** the loading disc occupied — so the swap reads as a single continuous transition rather than two separate states.

**Why this priority**: Polish — the feature is functionally complete without it. But the count-up is what makes the indicator *feel* alive, and the circular reveal is what visually closes the loop between "waiting" and "result".

**Independent Test**: In a Vitest harness, mount the loading view with a non-zero count-up duration, advance fake time so the target jumps from 0→10, sample frame-by-frame and assert at least one strictly-intermediate integer is rendered. For the transition, visual inspection in `npm run dev`: the poster should appear to bloom out of the loading disc's location.

**Acceptance Scenarios**:

1. **Given** the loading view is mounted with the default count-up duration, **When** a tick advances the target by ≥ 2, **Then** at least one intermediate integer between the previous and new target is observable in the rendered DOM before settling at the new target.
2. **Given** the user has `prefers-reduced-motion: reduce` set (or the test passes `reduceMotion={true}`), **When** a tick fires, **Then** the displayed value snaps to the new target with no intermediate frames.
3. **Given** the generation resolves successfully, **When** the loading view unmounts and the poster mounts, **Then** the poster's entrance is a circular reveal that originates near the position the loading disc occupied and expands outward, suppressed under `prefers-reduced-motion: reduce`.

---

### Edge Cases

- Image ready in under 2 seconds (e.g. stub fallback path returns near-instantly): the loading view may unmount before the very first scheduled percent update fires. In that case it is acceptable to never show a percentage, or to show an early single value (e.g. `0%` or `1%`) for a fraction of a second — but `100%` MUST NOT be shown.
- Tab is backgrounded mid-generation: when the user returns, the percentage should reflect actual elapsed time, not the number of timer ticks the browser allowed while throttled. (i.e. the value is a function of elapsed wall-clock time, not of how many timers the browser ran.)
- The user reduces motion (`prefers-reduced-motion: reduce`): the pulse animation already respects motion preferences elsewhere; in addition, the count-up animation and the circular poster reveal both suppress themselves and snap directly to their destination — the indicator's text and the poster's content remain fully visible regardless of motion preference.
- A generation lasts well beyond 20 seconds (e.g. 90 s real Gemini latency): the value plateaus in the high 90s (≤ 99) and stays there; it does not roll over, retreat, or flip to "100%".
- Two generations are triggered in quick succession (Surprise Me right after Generate, etc.): the second loading view starts a fresh percentage from scratch.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-1301**: The system MUST display an integer percentage value, suffixed with `%` (e.g. `37%`), visually centred inside the existing pulsating circle on the loading view, for as long as a generation is in flight.
- **FR-1302**: The displayed percentage MUST start low (≤ 5%) when the loading view first appears for a given generation.
- **FR-1303**: The displayed percentage MUST be a strictly non-decreasing function of time within a single generation (it never goes down, never resets, until the loading view unmounts).
- **FR-1304**: The displayed percentage MUST be derived from elapsed wall-clock time since the loading view appeared, calibrated so that 20 seconds maps to "fully progressed" — i.e. at 20 s elapsed the value sits at the cap (see FR-1305), and remains at that cap thereafter.
- **FR-1305**: The displayed percentage MUST never reach or display `100%` while the loading view is still visible. The maximum displayable value is `99%`.
- **FR-1306**: The displayed value MUST be updated at random-length intervals between 2 seconds (inclusive) and 10 seconds (inclusive), measured from the previous update — not on a fixed cadence.
- **FR-1307**: The system MUST replace the entire loading view (percentage included) with the post-generation outcome (poster on success, error/fallback view on failure) the moment the generation resolves — no "100%" frame, no fade through 100%, no settling animation that visits 100%.
- **FR-1308**: When a new generation is started, the percentage MUST restart from a low value; no carry-over from a previous run.
- **FR-1309**: The percentage value MUST NOT be announced to assistive technology on every update (i.e. it MUST NOT be inside an `aria-live="polite"` or `assertive"` region). The existing single "Generating your alter ego…" announcement remains the canonical AT signal that generation is in flight.
- **FR-1310**: The feature MUST NOT introduce any persistence — the percentage and its scheduling state live only in the page session, in line with the project's standing no-persistence posture (001 FR-016/017/024).
- **FR-1311**: The feature MUST NOT introduce any new network call or backend change — progress is a pure client-side, time-driven UI affordance.
- **FR-1312**: The randomness in update scheduling MUST be injectable for tests (i.e. the implementation must allow a deterministic source of "next interval" and "current time" so unit tests can assert exact tick behaviour).
- **FR-1313**: Between two consecutive percent values, the displayed number MUST count up smoothly through intermediate integers (rather than jump from the previous value to the next in a single frame). The animation MUST be brief (≤ ~400 ms), MUST be suppressed under `prefers-reduced-motion: reduce`, and MUST NOT change the source-of-truth: the value the animation is walking toward is still `percentForElapsed(elapsedMs)`.
- **FR-1314**: When the generation resolves successfully and the loading view is replaced by the alter-ego poster, the poster MUST emerge with a transition that visually originates from the location and shape of the loading view's pulsating circle (e.g. a circular reveal that expands outward), so the swap reads as "the disc opened and revealed the image" rather than "loading view disappeared, separate poster faded in". The transition MUST be suppressed under `prefers-reduced-motion: reduce`.

### Key Entities

- **Progress Tick**: A single update event that recomputes the displayed percent from elapsed time and schedules the next tick at a random delay in [2s, 10s]. Has no persisted form; exists only for the lifetime of one loading view.
- **Loading View Lifetime**: The window from "generation request started" to "generation resolved (success or failure)". The percentage indicator's lifetime is exactly this window — it never outlives, and never starts before, the loading view itself.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-1301**: In every observed generation (success, fallback, or failure), the user sees at least one percentage value rendered before the result appears, **except** when the generation resolves in under ~2 seconds — in which case the indicator may never tick (acceptable per Edge Cases).
- **SC-1302**: In zero observed generations does the user ever see the digits "100" inside the pulsating circle.
- **SC-1303**: Across a long generation (≥ 20 s), the displayed percent advances at least 5 distinct times (i.e. updates are perceptibly multiple, not a single jump from 0 to its plateau).
- **SC-1304**: 100% of measured update intervals during a generation fall in the inclusive range [2 s, 10 s].
- **SC-1305**: The value displayed at any tick is within ±5 percentage points of `min(99, round(100 * elapsedSeconds / 20))` — i.e. the displayed number genuinely tracks elapsed time, with small jitter allowed for the random tick cadence and for the sub-second count-up animation; it is not pure decoration.
- **SC-1306**: No regression in the existing loading view's behaviour: the "Generating your alter ego…" copy, the numbered steps list, the pulsating ring animation, and the existing single AT announcement all continue to behave exactly as before.

## Assumptions

- The "pulsating circle" referred to in the issue is the existing decorative ring rendered above the "Generating your alter ego…" copy in `GenerationLoading.tsx` (via `.generation-loading > p::before/::after`). The percent number renders inside the inner dark disc (`::after`).
- The 20-second target reflects a typical Gemini end-to-end latency for this product. The mapping `elapsed → percent` is honest about elapsed time, not a fake easing curve, but with a hard cap at 99 to honour FR-1305 even when the real call runs longer.
- The count-up animation between two percent values is purely a perceptual smoothing layer; the *target* of the animation is still derived from elapsed wall-clock time, so SC-1305 holds. The animation suppresses itself under `prefers-reduced-motion: reduce`.
- The loading→poster transition is implemented as an enhancement to the existing 005 `poster-emerge` keyframe (a circular `clip-path` that expands outward from the position the loading disc occupied). It runs whenever the poster mounts, which is a strict superset of the post-generation case — that is intentional and visually consistent.
- Updates are scheduled via a single client-side timer chain (each tick schedules the next one with a fresh random delay). This is consistent with the project's timer usage in 005 (animation nonce) and does not require any new dependency.
- The randomness source defaults to `Math.random()` but is injectable, mirroring the pattern already established by 009 (`lib/randomSelections.ts`) so the tick scheduler is unit-testable.
- The percent text is rendered as an ordinary, non-live DOM node. The existing `LiveRegion`/`useLiveAnnouncer` machinery from 003+ remains the only path through which this feature speaks to assistive technology.
- No persistence — consistent with 001 FR-016/017/024 and every subsequent feature (002…011): progress state lives only in component state for the duration of one loading view.
- The feature touches only the frontend; the backend (Java 21 / Spring Boot) is unchanged. No new dependency on either side.

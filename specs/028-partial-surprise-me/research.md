# Research: Partial Surprise Me

**Feature**: 028-partial-surprise-me
**Date**: 2026-05-20
**Author**: /speckit.plan phase 0

This document captures the design decisions resolved during Phase 0. No `NEEDS CLARIFICATION` markers remain in the spec or plan; the entries below explain *why* each choice was made and *what else was considered*.

---

## R-1: Where does the "explicit vs empty" classification live?

**Decision**: A new pure helper `mergeSurpriseWithExplicit(session, fullRoll)` in `frontend/src/features/alterego/lib/`. It accepts the current `AlterEgoSession` and the full random roll produced by the existing `randomSelections()`, and returns a `SurpriseMePicks` whose values are: the session's explicit value if non-empty for that category, else the rolled value. The Custom Role channel is checked alongside the prefab Archetype to determine whether Role is explicit (FR-2802 + FR-2803 + FR-2804).

**Rationale**:
- The classification rule is **pure and testable in isolation** — no React, no reducer, no RNG. This matches the 009 `randomSelections.ts` pattern, which the team already understands.
- Placing the merge in a helper lets both the reducer's `SurpriseMePicked` branch AND the `surprise()` hook consume the same final picks — no duplication of "is this slot empty?" logic in two places.
- The function is data-in / data-out, so the test suite is trivial: one case per non-trivial branch (FR-2801..FR-2806).

**Alternatives considered**:
- **A1: Classification inside the reducer.** Rejected — the reducer would then need to mix two concerns (rolling + classifying), and the hook would have to re-run the classification to build the outbound `Selections` payload for `mutation.mutate()`. Two sources of truth = drift risk.
- **A2: Classification inside the hook only.** Rejected — would push the merged picks into the reducer via `SurpriseMePicked`, but then a future call site that bypasses the hook (none today, but a /surprise CLI or a test fixture) would re-implement the rule. Pure helper is one place to look.
- **A3: New selector `partialPicksForSurprise(session)`.** Rejected — the function isn't a *selector* in the established sense (it doesn't read derived state from the session shape; it composes session state with an injected roll). Putting it under `lib/` next to `randomSelections.ts` keeps the naming consistent.

---

## R-2: Does the reducer's `SurpriseMePicked` branch need a schema change?

**Decision**: No schema change. Keep `SurpriseMePicks` exactly as it is today (`{ archetype, universe, artStyle }`). The reducer branch changes its *body* — it no longer unconditionally overwrites session slots, and no longer clears `customRole` — but the action shape, the `phase: 'picking'` transition, and the pass-through of `photoBlob` / `firstName` / `activeTab` / `email` are unchanged.

**Rationale**:
- The 028 invariant is that *empty slots get rolled, non-empty slots stay* — and the hook is now responsible for producing the "final picks" payload via `mergeSurpriseWithExplicit`. By the time `SurpriseMePicked` runs, every value in the payload is already the value the session should commit to. The reducer's job is just to write them.
- This keeps the diff in `reducer.ts` small and reversible. The 009/022 reducer-test suite is updated in place; no rename, no new action.
- Crucially: **the reducer must NOT clear `customRole`** any more (was FR-2209 in 022, now superseded by FR-2814). Removing that one line is the entire write-side change.

**Alternatives considered**:
- **B1: New action `SurprisePartialPicked` with a different payload.** Rejected — would force every test, every dispatch site, and the agent context note to learn two action names that mean almost the same thing. Single action with new semantics is cheaper to migrate.
- **B2: Make the reducer itself do the merge, passing it the full roll.** Rejected for the reason in R-1/A1 — the hook still needs the merged picks to build the wire payload, so the merge has to be reusable.

---

## R-3: How does `surprise()` in `useGenerateAlterEgo.ts` consume the merge?

**Decision**: `surprise()` (a) reads the current session via `useAlterEgoSession()` (it already has `dispatch` from there — adding `state` is a one-liner), (b) calls `randomSelections()` to produce the full roll, (c) calls `mergeSurpriseWithExplicit(state, fullRoll)` to produce the final picks, (d) dispatches `SurpriseMePicked` with those final picks, (e) builds `Selections` from those final picks plus `firstName` and `photoMode`. The Custom Role branch is included in the outbound `Selections` if `state.customRole.trim()` is non-empty — exactly as Generate does today.

**Rationale**:
- The dispatch order (`SurpriseMePicked` → `ActiveTabChanged` → `GenerateSubmitted`) is preserved verbatim from 009, so the 005 entrance animation still fires.
- Reading `state` in the hook costs nothing — `useAlterEgoSession()` already memoises. The only edit to the hook is replacing the line `const picks = randomSelections()` with three lines (`const roll = randomSelections(); const picks = mergeSurpriseWithExplicit(state, roll); …`) plus the Custom Role pass-through in the `Selections` literal.
- The `Selections` shape's `customRole` field is already optional per 022's wire contract — no change needed there either.

**Alternatives considered**:
- **C1: Build the merged picks inside the reducer and have the hook re-read the session after dispatch to construct `Selections`.** Rejected — React's `dispatch` is asynchronous; the hook can't read the post-dispatch state in the same tick. Would force a second `useEffect`, ugly.
- **C2: Skip the dispatch entirely if every category is already explicit (US4 path).** Rejected — would create a hidden branch ("Surprise Me did nothing visible") and breaks FR-2811's invariant that the button always fires the generation pipeline. The dispatch with merged-equals-session picks is a harmless no-op write that keeps the code path uniform.

---

## R-4: Does the wire payload change?

**Decision**: No. The outbound `POST /api/v1/alter-egos` body shape is byte-identical to today's Generate body for the same final session state. `customRole` is sent if non-empty (022 contract); `archetype`/`universe`/`artStyle` carry their final values whether user-picked or rolled. FR-2812 codifies this: Surprise Me is indistinguishable from Generate at the wire.

**Rationale**:
- Preserves the 009 design principle that the backend doesn't know which button fired the request.
- Lets us reuse every backend test, every contract test, every integration test as-is.
- The Pose + Vibe roll continues to happen server-side via the existing `RandomCategorySelector` (020 contract) — no front-end change needed for hidden categories.

**Alternatives considered**:
- **D1: Add a `surpriseMe: boolean` field for telemetry.** Rejected — explicitly out of scope per FR-2812 and the broader no-persistence / no-analytics posture of 001 / 023. If a future feature wants this, it lands in a separate spec with its own privacy review.

---

## R-5: How is "explicit" detected for the Role channel under the 022 invariant?

**Decision**: Role is *explicit* iff `archetype !== null` OR `customRole.trim().length > 0`. The two channels are checked with OR, not AND, because 022's precedence rule guarantees they cannot both be non-empty at the same time (Custom Role typing silently clears `archetype` via `CustomRoleChanged`). If both are somehow non-empty (e.g., a bug in some future code), Role is still *explicit* and gets passed through; this is the safe failure mode.

**Rationale**:
- Reusing 022's invariant means we don't need a "if both, prefer X" rule — we trust the upstream state machine.
- The OR formulation is the simplest expression of "Role is set somehow."
- Edge-tested in the spec (US2 Acceptance 3) for the whitespace-only Custom Role: the 022 `CustomRoleChanged` trim already clears the string before the user can click Surprise Me, so by the time we read the session, `customRole.trim().length === 0` and Role looks empty. No special-case needed in the merge.

**Alternatives considered**:
- **E1: Track a `roleSource: 'prefab' | 'custom' | null` field.** Rejected — provenance tracking (see spec Q1 clarification). Adds a new state slot for zero observable benefit.

---

## R-6: Testing strategy for the "randomizer is rolling" invariant under FR-2814

**Decision**: SC-2802's "50 fresh rolls span ≥ 3 distinct values per empty category" is verified at the **unit tier** by injecting a seeded RNG into `mergeSurpriseWithExplicit` indirectly (the helper accepts a roll, not an RNG — but the underlying `randomSelections(rng)` is RNG-injectable, so the test composes them). A pseudo-random run with a known seed deterministically produces a distribution that we can pin in the test fixtures. The Playwright scenario validates US1 end-to-end with real `Math.random` but does not attempt to assert the 50-roll distribution.

**Rationale**:
- Distribution assertions belong in the unit tier (fast, deterministic). The Playwright tier validates the user-visible behavior.
- The merge helper doesn't need its own RNG dependency — it takes a roll as input. This keeps the helper trivially pure and the seeded-RNG concern stays in `randomSelections.test.ts`.

**Alternatives considered**:
- **F1: Drive the 50-roll assertion via Playwright with real `Math.random`.** Rejected — slow, flaky, and adds nothing over the seeded unit test.

---

## R-7: Constitution Principle III (TDD) — concrete failing-test list before any production edit

**Decision**: The following tests MUST be written and asserted-to-fail before any production edit lands:

1. `mergeSurpriseWithExplicit.test.ts` — 8 cases covering: all-empty (full roll), one-explicit-archetype, one-explicit-universe, one-explicit-artStyle, custom-role-explicit (archetype slot null, customRole non-empty), all-explicit (zero substitutions), whitespace-only customRole (treated empty per 022), and the "merge does not mutate inputs" property.
2. `reducer.test.ts` — extend the existing `'SurpriseMePicked (009 / 020)'` describe to add: "preserves an existing archetype value when picks duplicate it", "preserves a non-empty customRole when picks include an archetype" (this case asserts the customRole-clear behavior from 022 is REMOVED), "leaves email + firstName untouched" (FR-2813 carryover).
3. `useGenerateAlterEgo.test.tsx` — extend the existing surprise-path suite with: "session with explicit Universe → outbound Selections.universe matches session, not random roll", "session with non-empty customRole → outbound Selections.customRole is the trimmed session value".
4. `partial-surprise-me.spec.ts` (Playwright, new) — single E2E scenario for US1 acceptance scenarios 1 + 2.

**Rationale**:
- Constitution III mandates the Red phase before any production code. Listing the failing tests here in research.md (Phase 0) is the explicit pre-commitment.
- The test list maps 1:1 to spec FRs — every requirement has at least one failing-first test.

**Alternatives considered**: None — TDD is non-negotiable per Constitution III.

---

## Open questions

None. All Phase 0 unknowns are resolved.

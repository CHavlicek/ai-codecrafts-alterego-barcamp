# Research — 026: Wording Updates for User Roles

**Date**: 2026-05-18
**Status**: Complete — zero open NEEDS CLARIFICATION

This is a wording-only change on top of four-year-old infrastructure (the `Archetype` enum and the `archetypeOptions` array). "Research" here means naming the five design questions a reviewer might ask and committing to an answer for each, with the rejected alternatives spelled out.

---

## R1 — Which surfaces in the codebase display a Role label to a guest?

**Decision**: Exactly two.

1. **Setup-tab Role grid** — rendered by `frontend/src/features/alterego/components/ArchetypeGrid.tsx`, which maps over `archetypeOptions` from `frontend/src/features/alterego/options.ts:62-72` and shows each `option.label` as the radio's accessible name + visible text.
2. **Generated alter-ego poster** — `backend/.../application/AlterEgoUseCase` calls `AlterEgoRequest.roleLabel()`, which returns the trimmed `customRole` if non-blank else `archetype.label()`. That string is placed into `StageContext.role` and painted onto the poster image by `infrastructure/overlay/TextStage`. So `Archetype.label()` is the only Role-label source the poster ever uses.

**Rationale**: Grep for the four old strings ("Backend Dev", "Frontend Dev", "Platform Eng.", "HR") across `frontend/src` + `backend/src/main` returns exactly two production files: `options.ts` and `Archetype.java`. Every other hit is a test fixture, a comment, or a provider-side internal-grounding string (see R2). The poster is the only secondary surface — there is no second list, dropdown, search bar, summary card, settings page, admin view, log message, or accessibility label that re-renders a Role label.

**Alternatives considered**:
- **Treat only the Setup grid as "displayed"** — rejected: the poster is *the* artefact the guest takes away; it is the most user-facing of all surfaces. If Setup says "Backend Developer" but the poster says "Backend Dev", the inconsistency is visible immediately and the feature fails SC-2602.
- **Treat the provider's internal `ROLE_LABELS` map as "displayed"** — rejected: see R2.

---

## R2 — What about the `ROLE_LABELS` maps in the Gemini / fal.ai prompt builders?

**Decision**: **Leave them untouched.**

There are three such maps:

| File | What it does | Current HR value | Current BE/FE/Platform values |
|---|---|---|---|
| `GeminiPromptBuilder.java:46-58` | Long-form Role label injected into the *image-generation* prompt sent to Gemini | `"Human Resources"` | `"Backend Developer"` / `"Frontend Developer"` / `"Platform Engineer"` |
| `FalAiPromptBuilder.java` (same shape) | Same, for fal.ai | `"Human Resources"` | same |
| `GeminiCharacterPromptBuilder.java` | Long-form Role label injected into the *bio/character-text* prompt | `"Human Resources"` | same |

These are private model-grounding strings — they never render on a guest-facing surface. The model uses them to anchor the persona; the guest never sees them.

Three observations:
1. **For Backend Dev / Frontend Dev / Platform Eng**, the prompt-side label already matches the new UI label. After this feature ships, `Archetype.label()` returns `"Backend Developer"` and `ROLE_LABELS.get(BACKEND_DEV)` also returns `"Backend Developer"`. The maps are coincidentally redundant for those three rows — but that's a refactor opportunity, not a wording change, and explicitly out of scope.
2. **For HR**, the prompt-side label is `"Human Resources"` and the new UI label is `"People Operations"`. They will diverge after this feature ships. That's intentional: `"Human Resources"` is a more general grounding term for the image-generation model (richer training-distribution match), whereas `"People Operations"` is the product/design preference for the guest-facing label. Issue #62 specifically dictates the UI wording; it does not specify a change to internal model grounding.
3. The spec's Assumption #5 explicitly excludes internal grounding strings from scope: *"If a downstream provider's internal prompt currently uses a different long-form …, that internal long-form is not in scope here unless changing it is necessary to make the poster's visible text read in the new wording."* The poster's visible text comes from `Archetype.label()`, not from `ROLE_LABELS` — so changing the maps is **not** necessary.

**Rationale**: The provider seam (Constitution Principle VIII) treats provider internals as private. The maps are part of provider-side infrastructure (`infrastructure/provider/.../*PromptBuilder.java`). Modifying them would broaden the change's blast radius from "data update in `domain` + frontend" to "data update in three infrastructure adapters", requiring updates to all the provider tests that pin those strings (`GeminiPromptBuilderTest`, `FalAiPromptBuilderTest`, `GenerateAlterEgoFalAiInputAxisIT`, `GenerateAlterEgoGeminiIT`) — all for a change the issue does not request and the guest never sees.

**Alternatives considered**:
- **Update `ROLE_LABELS.get(HR)` from `"Human Resources"` to `"People Operations"`** — rejected: out of scope per the spec's Assumption #5; broadens blast radius into four extra test files; changes a model-grounding string with no guest-visible benefit. If product later decides the model *should* see "People Operations" in the prompt too, that is a separate, very small follow-on.
- **Refactor the three maps to read from `Archetype.label()` directly** — rejected: out of scope; introduces a domain → provider dependency direction that is currently absent; would conflate "what the UI shows" with "what the model sees" forever.

---

## R3 — Test strategy: which tier(s) catch each failure mode?

**Decision**: Two existing test files cover both surfaces. No new file is added.

| Surface | Tier | File | Failure mode caught |
|---|---|---|---|
| Setup-tab Role grid | Frontend Vitest (component) | `frontend/src/features/alterego/components/ArchetypeGrid.test.tsx` | A label was forgotten, or the label list rendered in the wrong order, or `getByRole('radio', { name: 'People Operations' })` cannot find the renamed HR option. |
| Poster Role text | Backend unit | `backend/src/test/java/com/aiavatar/alterego/unit/EnumsTest.java` | `Archetype.HR.label()` (or one of the other three) returns the old string. |
| Public HTTP contract is byte-identical | Backend `@WebMvcTest` (contract tier — **untouched, must pass unchanged**) | existing `contract/` tier | The wire enum (`backend-dev`, `hr`, …) accidentally changed. |
| Integration end-to-end | Backend integration (**untouched, must pass unchanged**) | `GenerateAlterEgoGeminiIT`, etc. | Provider prompt-side `"Backend Developer"` survives the change (it does — we don't touch `ROLE_LABELS`). |
| ArchUnit layer rules | Backend `archTest` (**untouched, must pass unchanged**) | `arch/` tier | We didn't add a reverse edge. (We don't — we only touch `domain.model.Archetype`.) |

**Rationale**: Constitution Principle III says "Write tests; verify they FAIL before committing." The two existing tests already assert the old wording — they go red the instant we flip the production tables. That is the cleanest possible Red→Green path: no new test files, no new fixtures, no new branches; just the right tests already exist in the right tiers. Reusing them also pins the test coverage delta to zero — coverage is structurally unaffected.

**Alternatives considered**:
- **Write a new "wording matrix" test that drives `Archetype.label()` against a hard-coded golden table** — rejected: redundant with `EnumsTest`, which already does exactly that.
- **Add a Playwright E2E that selects each renamed Role and reads the poster text** — rejected: the unit tier + the frontend component test already prove the two surfaces individually; an E2E would re-test the integration path of features 001..024 with no Role-specific failure mode the lower tiers don't already catch. Out of proportion.

---

## R4 — Surprise Me: does it need any change?

**Decision**: **No code change.**

Surprise Me (feature 009) picks one option uniformly at random from `archetypeOptions` and dispatches a `SurpriseMePicked` action that writes the chosen `value` (wire identifier) into reducer state. The grid then re-renders with that option selected — and the **label is read from the same `archetypeOptions[].label` field we are about to update**. So the moment Surprise Me lands on the renamed HR option, the grid renders "People Operations" automatically. No code in `randomSelections.ts`, the reducer, or the `SurpriseMeButton` reads or writes a label string.

**Rationale**: Verified by tracing `Math.random()`-driven branch from `lib/randomSelections.ts → SurpriseMePicked → archetype: 'hr' in session state → ArchetypeGrid maps archetypeOptions and renders option.label`. The label is read at render-time from the same source-of-truth this feature updates.

FR-2609 in the spec is therefore satisfied by FR-2604 — no separate work item.

---

## R5 — Migration / rollback / in-flight session behaviour

**Decision**: Zero migration. Zero rollback ceremony. In-flight sessions auto-heal on re-render.

**Rationale**: The no-persistence posture (001 FR-016 / FR-017 / FR-024, reaffirmed here as FR-2610) means there is nothing in any datastore that references the old wording. A guest who picked the option whose `value` is `"backend-dev"` before the deploy and is mid-generation when the new bundle ships will, the next time the component re-renders, see "Backend Developer" — because the reducer holds `archetype: "backend-dev"` and the grid renders the *current* `archetypeOptions[].label`. The same is true for the poster path: the backend hot-loads `Archetype.label()` on every request. There is no cached label string anywhere in process memory beyond the lifetime of a single render or request.

Rollback (revert the commit) is a single git revert with the same zero-migration property.

**Alternatives considered**:
- **Coordinate a "two-step" deploy where the backend ships the new label first, then the frontend** — rejected: no order dependency exists. Either side can ship first because the wire enum is unchanged.

---

## Out of scope (named explicitly so /speckit.tasks does not pick them up)

- Updating the JSDoc/Javadoc comment examples in `PosterView.tsx:19` and `AccentResolver.java:37` that still say "Backend Dev". Comments are not guest-facing.
- Updating the literal `"Backend Dev"` fixture strings in `unit/application/pipeline/{TextStage,FrameStage,PosterPipeline}Test.java`. They are arbitrary inputs to the stage, not assertions about `Archetype.label()`.
- Adding a Playwright E2E for the four renamed Roles.
- Refactoring `GeminiPromptBuilder.ROLE_LABELS` / `FalAiPromptBuilder.ROLE_LABELS` / `GeminiCharacterPromptBuilder.ROLE_LABELS` to read from `Archetype.label()`.
- Changing the prompt-side HR label from `"Human Resources"` to `"People Operations"`.
- Reordering, re-iconing, adding, or removing any Role option.
- Adding a "back-compat" label-alias layer so old clients can still display "Backend Dev". There are no old clients — every render reads the current bundle.

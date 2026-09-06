# Implementation Plan: Group Photos

**Feature**: 011-group-photos (branch: `claude/nice-brown-QNhLb`)
**Spec**: [spec.md](./spec.md)
**Status**: Draft
**Primary Author**: Claude (task: "implement one open sub-issue" for #10)

## Constitution Check (per /speckit.plan gate)

| Principle | Compliance |
|---|---|
| **I. Modern & Secure Stack** | ✅ No new deps. Reuses React 19 + TS strict, Spring Boot 3.x, Lucide `User` / `Users` icons already tree-shakable from the existing `lucide-react`. |
| **III. TDD (≥ 90% coverage, integration test required)** | ✅ New reducer branch, selector-level no-op check, new enum, new prompt branch — all unit-testable. One Playwright E2E pins the switch → payload contract. |
| **IV. Resilient HTTP** | ✅ No new network paths. Existing `resilientFetch` wrapping `POST /api/v1/alter-egos` is unchanged. |
| **V. Feature Branch Workflow** | ⚠ Deviation: developing on `claude/nice-brown-QNhLb` rather than `011-group-photos`. Matches the branch-naming deviation pattern from features 005 / 007 / 009 / (unmerged) 010, driven by the task-runner's predetermined branch name. Documented here per constitution v1.0.1 §V note. |
| **VI. Zero Deprecated Dependencies** | ✅ No dependency change. |

**Outcome**: Constitution Check passes with the same deviation note already precedented by 005 / 007 / 009.

## Architecture Summary

**Scope**: one new binary mode field on the session (`photoMode`), one new reducer action (`PhotoModeSelected`), one new frontend component (`PhotoModeSwitch`), one new option-list constant (`PHOTO_MODE_OPTIONS`), one new field on the wire type + DTO, one branch in the Gemini prompt builder.

**Zero new endpoints, zero new dependencies, zero persistence surface.**

### Layer-by-layer

**Frontend (`frontend/src/features/alterego/`)**:

1. `types.ts` — Add `PhotoMode = 'single' | 'group'` union and add `photoMode: PhotoMode` to `Selections`.
2. `options.ts` — Add `PHOTO_MODE_OPTIONS: ReadonlyArray<EnumOption<PhotoMode>>` with labels "Single Person" / "Group Photo" and Lucide `User` / `Users` icons.
3. `state/reducer.ts` —
   - Add `photoMode: PhotoMode` to `AlterEgoSession` (initial value `'single'`).
   - Add `{ type: 'PhotoModeSelected'; photoMode: PhotoMode }` to the action union.
   - Handle the action: `{ ...state, photoMode: action.photoMode, phase: 'picking' }`. No other branches change.
4. `components/PhotoModeSwitch.tsx` (new) — Small wrapper around the existing `SelectionGrid` primitive, configured with the two-option list, single-select required (no deselect), accent var `--color-accent-mode` (a new CSS token) or reuse an existing neutral.
5. `components/SetupLayout.tsx` — Insert `<PhotoModeSwitch>` as the first `.setup-layout__numbered-group` with `data-step="0"` (or visually above the numbered groups). It lives in the selections column; the photo column is unchanged.
6. `AlterEgoPage.tsx` — Update `handleSubmit` to include `photoMode: state.photoMode` in the `selections` payload. Update `useGenerateAlterEgo.surprise` path in the same way (reads `state.photoMode` through the existing `args` shape).
7. `hooks/useGenerateAlterEgo.ts` — Update `SurpriseArgs` / the `surprise` handler to thread `photoMode` into the built `Selections`.
8. `lib/missingInputHint.ts` — No change; `photoMode` is always set so it never appears in missing-input lists.
9. `index.css` / style tokens — Optionally add a `--color-accent-mode` token. If a neutral accent looks good visually, reuse one of the existing five and skip this.

**Backend (`backend/src/main/java/com/aiavatar/alterego/`)**:

1. `model/PhotoMode.java` (new) — `enum PhotoMode { SINGLE, GROUP }` with `@JsonProperty` wire values `"single"` / `"group"` matching frontend.
2. `model/AlterEgoRequest.java` — Add `PhotoMode photoMode` as the LAST record component (after `firstName`). *Nullable* (no `@NotNull`) so old-client requests still pass Bean Validation. Add a domain accessor `effectivePhotoMode()` that returns `photoMode != null ? photoMode : PhotoMode.SINGLE` — callers always use this accessor instead of the raw getter.
3. `service/gemini/GeminiPromptBuilder.java` —
   - Branch in `build(AlterEgoRequest)`: the opening sentence and the "Clear focus on the subject" composition note each have two variants.
   - SINGLE variant: unchanged from today.
   - GROUP variant: "Generate a cinematic group portrait poster of the people in the reference photo." + "Render every person visible in the reference photo as the same alter ego archetype and in the same universe / art style / pose / vibe." + "Clear focus on all subjects; the universe aesthetic is the setting, not the subjects."
4. No controller change. `AlterEgoController.generate` hands the DTO through as before.
5. `AlterEgoService` unchanged. `FallbackPosterProvider` unchanged — fallback poster is mode-agnostic (stub image).

### State transition table (only new branch)

| Current state | Action | Resulting state |
|---|---|---|
| any | `PhotoModeSelected { photoMode: 'single' \| 'group' }` | `{ ...state, photoMode: action.photoMode, phase: 'picking' }` |
| any | `StartOverRequested` | `initialAlterEgoSession()` (includes `photoMode: 'single'`) |

### Wire contract delta

Before (006 / 009):
```json
{ "pose": "heroic", "archetype": "backend-dev", "universe": "marvel",
  "artStyle": "oil-painting", "vibe": "builder", "firstName": "Alex" }
```

After (011):
```json
{ "pose": "heroic", "archetype": "backend-dev", "universe": "marvel",
  "artStyle": "oil-painting", "vibe": "builder", "firstName": "Alex",
  "photoMode": "single" }
```

Backwards-compatible: a server accepting the new field still accepts the old shape (photoMode → null → defaulted to SINGLE).

## Phases & Milestones

### Phase A — Frontend wiring (TDD)

- **A1**: Write `reducer.test.ts` case for `photoMode` initial value + `PhotoModeSelected` branch + StartOver reset. Expect RED.
- **A2**: Extend `types.ts`, `options.ts`, `reducer.ts`, `initialAlterEgoSession()`. Expect A1 GREEN.
- **A3**: Write `PhotoModeSwitch.test.tsx` covering: render both options, default checked = Single, click Group fires `onChange('group')`, keyboard Arrow moves, re-click same is no-op, lucide icons `aria-hidden`. Expect RED.
- **A4**: Implement `PhotoModeSwitch.tsx`. Expect A3 GREEN.
- **A5**: Extend `SetupLayout.tsx` to include the switch as step 0. Add 1 test for mount + prop threading.
- **A6**: Extend `AlterEgoPage.tsx` `handleSubmit` + `handleSurprise` to include `photoMode`. Extend `useGenerateAlterEgo.ts` `surprise()` to thread it. Add 1 test pinning the payload field.

### Phase B — Backend wiring (TDD)

- **B1**: Create `PhotoMode` enum + Jackson wire-value test (`EnumsTest` additions). Expect RED → GREEN.
- **B2**: Extend `AlterEgoRequest` record, add `effectivePhotoMode()`. Update record-invariants test. Expect RED → GREEN.
- **B3**: Extend `GeminiPromptBuilder` with the branch. Add unit test pinning both variants — SINGLE must produce today's output; GROUP must produce the plural wording. Expect RED → GREEN.
- **B4**: Extend `AlterEgoControllerContractTest` + `AlterEgoControllerErrorContractTest` to assert the new field is optional (missing → OK, present valid → OK, present invalid → 400).
- **B5**: Extend at least one integration test (e.g. `GenerateAlterEgoGeminiIT`) to assert that `photoMode: "group"` round-trips through the full pipeline and the captured prompt carries the plural wording.

### Phase C — E2E + accessibility

- **C1**: Add a new Playwright spec `group-photos.spec.ts`: (a) switch defaults to Single; (b) flip to Group persists across a page re-render (confirms it's wired to state, not local); (c) click Generate with Group selected → intercept the outbound multipart body and assert `selections.photoMode === "group"`; (d) Start-over resets back to Single.
- **C2**: Extend the existing `axe-scan.spec.ts` to include the new switch in scope. Expect zero new violations.

### Phase D — Ship

- **D1**: `npm run lint`, `npm run build`, `npm run test`, `npm run test:coverage`, `npm run test:e2e` (spot check the group-photos spec if time is tight). All GREEN.
- **D2**: Backend: `./gradlew test` in `backend/`. All GREEN.
- **D3**: Commit the spec / plan / tasks / research / data-model / quickstart / checklist artifacts alongside code.
- **D4**: Push and open PR closing #10.

## Open Questions (resolved inline)

- **Q1**: "Switch" UI primitive — `role="switch"` toggle or `role="radiogroup"` with two options? → **Decision: radiogroup** (see spec.md A-1004). Gives AT users visibility of both labels and matches every other composition choice on the form.
- **Q2**: Position in the form — step 0 above Pose, or integrated into the photo column? → **Decision: step 0 in the selections column.** Reads as "how many subjects in the portrait" which is a composition choice, same family as Pose / Art Style.
- **Q3**: Wire field optional or required on the backend? → **Decision: nullable (optional at validation)**, with a domain default of `SINGLE`. Keeps old clients working, matches Vibe's nullability pattern.
- **Q4**: Surprise Me randomises photoMode too? → **Decision: no.** Mode is a photo composition choice tied to what's actually in the photo, not a theme pick. See spec.md FR-1012 and Edge Cases.

## Risks & Mitigations

- **R1**: Lucide `Users` icon exists in older major versions but the frontend uses `lucide-react ^1.8.0` — verify the import path. *Mitigation*: quick check via `grep "Users" node_modules/lucide-react/dist/*.d.ts`.
- **R2**: Prompt template drift — two variants could diverge accidentally. *Mitigation*: unit test pins the Single variant byte-exact against today's output (regression lock), and pins the Group variant against an explicit expected string.
- **R3**: Older clients still in flight after backend ships — a blank `photoMode` field on the wire must be accepted. *Mitigation*: FR-1009 + B4 contract test enumerate this case.

## Estimate

- Phase A: ~45 min (5 small frontend diffs + tests)
- Phase B: ~30 min (3 small backend diffs + tests)
- Phase C: ~20 min (Playwright + axe)
- Phase D: ~10 min (lint/build/commit/push/PR)

Total: ~1h 45m.

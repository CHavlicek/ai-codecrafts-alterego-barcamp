# Implementation Plan: 026 — Wording Updates for User Roles

**Branch**: `026-role-label-wording` | **Date**: 2026-05-18 | **Spec**: [./spec.md](./spec.md)
**Input**: Feature specification from `/specs/026-role-label-wording/spec.md`
**Issue**: GitHub #62 — "Wording Updates for User Roles"

## Summary

Four prefab Role labels currently display in short/abbreviated form and need to read as the full, standard phrase on every guest-facing surface:

| Wire (unchanged) | Old display | New display |
|---|---|---|
| `backend-dev` | "Backend Dev" | **Backend Developer** |
| `frontend-dev` | "Frontend Dev" | **Frontend Developer** |
| `platform-eng` | "Platform Eng." | **Platform Engineer** |
| `hr` | "HR" | **People Operations** |

The remaining five prefab Roles (Cloud Architect, AI Engineer, Data Engineer, Administration, Customer Relations) and every other moving part (icons, ordering, wire enums, free-form custom-Role behaviour, Surprise Me, validation, gating, no-persistence posture) are untouched.

**Technical approach**: Two surfaces hold a guest-facing Role label. (1) The Setup-tab Role grid reads from `frontend/src/features/alterego/options.ts → archetypeOptions[].label`. (2) The poster text overlay reads from `backend/.../domain/model/Archetype.label()`, which `AlterEgoUseCase` resolves into `StageContext.role` and `TextStage` paints onto the image. Both label tables are static, in-process strings — changing four entries in each is the whole change. The wire format (enum value strings on the JSON line) is untouched on both sides, so the public HTTP contract (`POST /api/v1/alter-egos`) remains byte-identical. The internal model-grounding labels in `GeminiPromptBuilder.ROLE_LABELS` / `FalAiPromptBuilder.ROLE_LABELS` / `GeminiCharacterPromptBuilder.ROLE_LABELS` are explicitly out of scope (they're private to the provider; they don't render on a guest-facing surface) — see research R2.

Tests follow Red-then-Green per Principle III. Two existing tests hard-code the old labels (`ArchetypeGrid.test.tsx` and `EnumsTest.java`); both flip from old to new strings in the same commit that flips the production tables. No new test tier, no new test file, no new dependency, no new persistence.

## Technical Context

**Language / Version**: TypeScript 5.7 strict (frontend) · Java 21 LTS (backend). Unchanged from features 002..025.
**Primary Dependencies**: React 19 + Vite 8 + Vitest + React Testing Library + Playwright (frontend); Spring Boot 3.x + Jakarta Bean Validation + JUnit 5 + Spring Boot Test + Mockito + ArchUnit 1.3 (backend). **No new runtime, test, or build dependency.**
**Storage**: N/A. Inherits 001 FR-016 / FR-017 / FR-024 — no persistence. The wording lives in JVM-loaded `enum` constants on the backend and in a bundled ES-module data array on the frontend.
**Testing**: Vitest + React Testing Library for the frontend component test that asserts on rendered Role labels; JUnit 5 unit tier for the backend `Archetype.label()` exact-string assertions. Both tiers already exist; both already have the four old-wording assertions in place — they are the failing red tests of this feature once flipped.
**Target Platform**: Browser (evergreen) + JVM 21. Unchanged.
**Project Type**: Web (frontend + backend) — Option 2 layout, already established.
**Performance Goals**: N/A. Label rendering is O(1) per option and unaffected by any runtime path.
**Constraints**: Spec SC-2604 — the wire payload must be byte-identical to the release immediately preceding this change for all nine Roles. Re-stated: do not change `Archetype.wire()`, do not change `archetypeOptions[].value`, do not reorder the array, do not change the `JsonValue`/`JsonCreator` behaviour.
**Scale / Scope**: ~4 production-line edits on the frontend + ~4 production-line edits on the backend + ~6 assertion-string edits across two existing tests. Single feature commit.

## Constitution Check

> Constitution `v1.1.0` (ratified 2026-04-21, last amended 2026-05-15). `/specify/memory/constitution.md`.

| Principle | Verdict | Notes |
|---|---|---|
| **I — Modern & Secure Stack** | ✅ PASS | No new dependency added. No version change. |
| **III — Test-First Development** | ✅ PASS | Two existing tests (`ArchetypeGrid.test.tsx`, `EnumsTest.java`) currently *pass* against the old wording. They will be flipped to assert the new wording first — they go red, then the production tables are updated, restoring green. Coverage gate ≥ 90% is structurally unaffected (no branches added, no lines removed, two string literals updated). |
| **IV — Resilient HTTP Communication** | ✅ PASS — N/A | No HTTP call is added or modified. |
| **V — Feature Branch Workflow** | ✅ PASS | Already on `026-role-label-wording` (cut from `main`, sequential 3-digit prefix). Will merge via PR with explicit human approval. |
| **VI — Zero Deprecated Dependencies** | ✅ PASS — N/A | No dependency change. |
| **VII — Layer Convention** | ✅ PASS | Only files touched on the backend are `domain/model/Archetype.java` (data) and `unit/EnumsTest.java` (test). No reverse edges; no cross-layer change; no boundary/application/infrastructure code touched. |
| **VIII — Provider Seam** | ✅ PASS — N/A | No provider, port, or `GenerationFailure` branch touched. Provider-side `ROLE_LABELS` maps intentionally left in place (see research R2). |
| **IX — Test Pyramid** | ✅ PASS | Only existing tiers exercised: `unitTest` (backend, `EnumsTest`) and frontend Vitest component tier (`ArchetypeGrid.test.tsx`). No new tier. No service / contract / integration / arch test needed — the change is data-only, the public HTTP contract is byte-identical (verified by the unmodified contract tier), and ArchUnit rules are unaffected. |

**Verdict: PASS.** No Complexity Tracking row needed.

## Project Structure

### Documentation (this feature)

```text
specs/026-role-label-wording/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 — design decisions on top of 022 primitives
├── data-model.md        # Phase 1 — Role label mapping (before/after) for both sides
├── quickstart.md        # Phase 1 — 5-minute manual smoke for the four labels
├── contracts/
│   └── public-http.md   # Phase 1 — explicit "no contract change" assertion
├── spec.md              # /speckit.specify output (already written)
└── checklists/
    └── requirements.md  # /speckit.specify output (all items pass)
# tasks.md is created later by /speckit.tasks — not in this command's output
```

### Source Code (repository root) — files touched by this feature

```text
frontend/
└── src/
    └── features/
        └── alterego/
            ├── options.ts                                # PROD: 4 label: strings
            └── components/
                └── ArchetypeGrid.test.tsx               # TEST: label list + two getByRole names

backend/
└── src/
    ├── main/java/com/aiavatar/alterego/
    │   └── domain/model/
    │       └── Archetype.java                            # PROD: 4 enum constructor labels
    └── test/java/com/aiavatar/alterego/
        └── unit/
            └── EnumsTest.java                            # TEST: 4 .label() assertions
```

**Files explicitly NOT touched (and why)**:
- `backend/.../infrastructure/provider/{gemini,falai}/{Gemini,FalAi,GeminiCharacter}PromptBuilder.java` — these `ROLE_LABELS` maps are internal model-grounding strings (one of them already says `"Backend Developer"`); they don't render on a guest-facing surface. Research R2.
- `backend/.../domain/policy/AccentResolver.java` — uses the `Archetype` enum identity (`switch`/`Map<Archetype, …>`), not its `.label()`. Untouched.
- `backend/.../unit/{application/pipeline/{TextStage,FrameStage,PosterPipeline}Test.java`, `unit/{gemini,falai}/*PromptBuilderTest.java`, `integration/GenerateAlterEgo*IT.java` — each either uses an arbitrary literal `"Backend Dev"` as a stage-input fixture (not asserting on `Archetype.label()`) or asserts on the unchanged prompt-side `"Backend Developer"`. Untouched.
- `frontend/src/features/alterego/components/PosterView.tsx` — JSDoc comment example references "Backend Dev". Comment-only; non-user-facing. Not in scope.

**Structure Decision**: existing four-layer backend + flat React-feature frontend, unchanged. No new directories.

## Complexity Tracking

> No Constitution violations. Section intentionally empty.

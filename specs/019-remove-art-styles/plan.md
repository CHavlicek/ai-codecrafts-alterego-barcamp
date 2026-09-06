# Implementation Plan: Remove Line Art, Low-Poly 3D, and Pixel Art from Art Style category

**Branch**: `019-remove-art-styles` | **Date**: 2026-05-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/019-remove-art-styles/spec.md`
**Linked issue**: [#49](https://github.com/squer-solutions/aiavatar/issues/49)

## Summary

Retire three of the nine values in the `ArtStyle` closed enum (`pixel-art`, `low-poly-3d`, `line-art`) across the entire stack — frontend type alias, frontend UI option array, frontend randomiser pool, backend Java enum, backend prompt-builder label maps, and the OpenAPI contract — and update every test that today asserts the nine-member set or exercises a retired value. The Surprise Me randomiser draws from `ART_STYLE_OPTIONS` already (`frontend/src/features/alterego/lib/randomSelections.ts:62`), so trimming the option array is the single source of truth for both the manual grid and the randomiser. No new behaviour is introduced; stale-tab requests carrying a retired value continue to be rejected by the existing `ArtStyle.fromWire` "Unknown artStyle" path with no special-case handling (FR-1903).

## Technical Context

**Language/Version**: TypeScript 5.x (strict) frontend; Java 21 (LTS) backend. Unchanged from 001/002/003/006/014/016.
**Primary Dependencies**: React 19 + Vite 8 + Vitest + RTL + Playwright (frontend); Spring Boot 3.x + JUnit 5 + Spring Boot Test (backend). **No new runtime, test, or build dependency.**
**Storage**: N/A. Inherits 001 FR-016 / FR-017 / FR-024 (no persistence). No data migration is needed because there is no data — sessions live only in browser memory.
**Testing**: Vitest + React Testing Library + Playwright (frontend); JUnit 5 + Spring Boot Test + `@SpringBootTest` (backend). All tests must update from "nine options" to "six options" and remove fixture rows referencing the three retired values.
**Target Platform**: Evergreen browsers (frontend) + JVM 21 (backend). Unchanged.
**Project Type**: Web application (`frontend/` + `backend/`).
**Performance Goals**: Unchanged. The change only shrinks a closed enum — no hot-path code is altered.
**Constraints**: Constitution Principle I (modern stack, no new CVE surface) — satisfied trivially because we *remove* rather than add. Principle III (TDD red-green-refactor + ≥90 % coverage + ≥1 integration test) — satisfied by updating existing failing tests to the new six-member set, then turning them green. Principle IV (resilient HTTP) — unaffected. No new HTTP surface.
**Scale/Scope**: The closed Art Style enum reduces from nine members to six. ~45 source/test files reference the three retired values today; the change set is touched-everywhere-it-appears but mechanically simple.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| **I. Modern & Secure Technology Stack** | PASS | No new dependency, no version bump. The change is purely subtractive within the existing TypeScript + React + Java + Spring Boot stack mandated by the constitution. |
| **III. Test-First Development (TDD)** | PASS | The plan's first concrete step is to update existing tests to assert the six-member set and to remove fixtures referencing the three retired values; those tests will RED on the current code, then turn GREEN after the source-of-truth edits. ≥90 % line coverage is preserved — we are deleting test rows that drove coverage for code we are also deleting (the three enum members and their label-map entries), so the coverage ratio is unchanged. The integration test guarantee is met by the existing `AlterEgoControllerInputValidationIT` updated to assert that a retired value now causes a 400 (FR-1903), plus the existing `randomSelections.test.ts` 1 000-draw guarantee (SC-1902). |
| **IV. Resilient HTTP Communication** | PASS / N/A | No HTTP surface changes. The retry-with-fallback policy on existing endpoints is unaffected. |
| **V. Feature Branch Workflow** | PASS | Working on `019-remove-art-styles` (3-digit sequential prefix, cut from `main`). Will land via PR with explicit human approval. |
| **VI. Zero Deprecated Dependencies** | PASS | Subtractive change; nothing to audit. |
| **TDD coverage gate (≥ 90 %)** | PASS | Removing three enum members and their label-map entries removes both production lines and test lines symmetrically; the ratio is preserved. |

**Result**: All gates PASS. No violations. **Complexity Tracking section is intentionally empty.**

## Project Structure

### Documentation (this feature)

```text
specs/019-remove-art-styles/
├── plan.md                          # This file (/speckit.plan command output)
├── spec.md                          # /speckit.specify output (already exists)
├── research.md                      # Phase 0 output (/speckit.plan command)
├── data-model.md                    # Phase 1 output (/speckit.plan command)
├── quickstart.md                    # Phase 1 output (/speckit.plan command)
├── contracts/
│   └── alter-egos.openapi.yaml      # 019 delta over 006's contract
├── checklists/
│   └── requirements.md              # /speckit.specify validation (already exists)
└── tasks.md                         # /speckit.tasks output (NOT created here)
```

### Source Code (repository root)

This is a `frontend/` + `backend/` web application (constitution Technology Standards). The touch-points are entirely additions-removed within existing modules — no new files, no new packages.

```text
frontend/
└── src/features/alterego/
    ├── types.ts                                              # remove 3 enum members from `ArtStyle` type alias
    ├── options.ts                                            # remove 3 rows from `ART_STYLE_OPTIONS` (single source of truth)
    ├── components/
    │   ├── ArtStyleGrid.tsx                                  # NO CHANGE (reads `ART_STYLE_OPTIONS`)
    │   ├── ArtStyleGrid.test.tsx                             # update assertions: 9 → 6 tiles, remove retired-value cases
    │   ├── GenerateButton.test.tsx                           # update fixtures using retired values
    │   ├── SetupLayout.test.tsx                              # update fixtures
    │   ├── SurpriseMeButton.test.tsx                         # update fixtures
    │   ├── PrintArtefact.test.tsx                            # update fixtures
    │   └── AlterEgoPanel.test.tsx                            # update fixtures
    ├── hooks/useGenerateAlterEgo.test.tsx                    # update fixtures + ART_STYLE_OPTIONS-bound assertion
    ├── lib/
    │   ├── randomSelections.ts                               # NO CHANGE (reads `ART_STYLE_OPTIONS`)
    │   └── randomSelections.test.ts                          # update index-based picks + bag-membership assertion; add 1 000-draw guard (SC-1902)
    ├── services/alterEgoClient.test.ts                       # update fixtures
    └── state/
        ├── reducer.test.ts                                   # update fixtures
        └── selectors.test.ts                                 # update fixtures

backend/
└── src/
    ├── main/java/com/aiavatar/alterego/
    │   ├── model/ArtStyle.java                               # remove `PIXEL_ART`, `LOW_POLY_3D`, `LINE_ART` (3 of 9 → 6 members)
    │   └── service/
    │       ├── gemini/GeminiPromptBuilder.java               # drop 3 ART_STYLE_LABELS entries
    │       ├── gemini/GeminiCharacterPromptBuilder.java      # drop 3 ART_STYLE_LABELS entries
    │       └── falai/FalAiPromptBuilder.java                 # drop 3 ART_STYLE_LABELS entries
    └── test/java/com/aiavatar/alterego/                      # ~28 test files: update fixtures + EnumsTest 9→6 cardinality
                                                              # AlterEgoControllerInputValidationIT: add stale-tab rejection case (FR-1903)

specs/019-remove-art-styles/
└── contracts/alter-egos.openapi.yaml                          # delta over 006: ArtStyle enum becomes 6 values (PATCH wire-spec change)
```

**Structure Decision**: Web application (frontend + backend) per the constitution. The change has **one source of truth per stack half**:

- Frontend: `frontend/src/features/alterego/options.ts` (`ART_STYLE_OPTIONS`) drives both the manual grid (`ArtStyleGrid.tsx`) and the Surprise Me randomiser (`randomSelections.ts:62`). The `ArtStyle` *type alias* in `types.ts` is the compiler-enforced contract; trimming it produces TS errors at every retired-value site (lights up exactly the set of test fixtures that need updating).
- Backend: `backend/src/main/java/com/aiavatar/alterego/model/ArtStyle.java` (Java enum) is the JSON deserialisation gate; `ArtStyle.fromWire` (line 50–55) already throws `IllegalArgumentException("Unknown artStyle: …")` for unrecognised wire values, which Spring's existing validation pipeline surfaces as a 400 — that is the FR-1903 stale-tab path with no new code.

This means the production-code change set is **5 files** (1 type alias + 1 options array on the frontend; 1 enum + 3 prompt-builder label maps on the backend) and the test change set is **~35 files** (mechanical fixture updates, lit up by the compiler).

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified.**

*Intentionally empty — Constitution Check passed with no violations.*

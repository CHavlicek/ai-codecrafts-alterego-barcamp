# Implementation Plan: Art Style Category

**Branch**: `006-art-style-category` (developed on `claude/art-style-category-P7wwR`) | **Date**: 2026-04-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/006-art-style-category/spec.md`

## Summary

Add a fifth category — **Art Style** — to the Setup tab. Nine mutually-exclusive options
(oil painting, watercolor, pixel art, low-poly 3D, line art, pop art, Renaissance
portrait, Japanese woodblock, cel-shaded) each labelled with a leading emoji glyph.
Selection is **required** for Generate to enable. The chosen wire value rides on the
existing `selections` JSON part of the multipart request and is mapped to a
natural-language description inside `GeminiPromptBuilder` so the real-provider image
reflects the style. All design decisions reuse the 002/003 patterns (`SelectionGrid<T>`,
enum + `@JsonCreator`/`@JsonValue`, `EnumMap` label table). No new runtime dependencies
on either side.

## Technical Context

**Language/Version**: TypeScript 5.x (frontend, strict) / Java 21 LTS (backend)
**Primary Dependencies**: React 18 + Vite 8, Spring Boot 3.x, Jackson, Bean Validation.
No new dependencies introduced.
**Storage**: N/A (Art Style lives only in in-memory session state; inherits
001 FR-016 / 003 FR-215 no-persistence).
**Testing**: Vitest + React Testing Library (unit), Playwright (E2E) on the frontend;
JUnit 5 + Mockito + `@SpringBootTest` on the backend. Golden-prompt unit test pins
each of the nine Gemini prompt-label strings.
**Target Platform**: Evergreen browsers (frontend) + JVM 21 container (backend).
**Project Type**: Web application (frontend/ + backend/ repo layout).
**Performance Goals**: No per-request SLO change. Setup-tab initial paint budget
stays ≤ 16 ms after adding the new grid (SC-303).
**Constraints**: Constitution Principle I (no deprecated deps), III (TDD + ≥ 90%
coverage + one integration test), IV (resilient HTTP with stub fallback on failure —
inherited from 003, unchanged here), V (feature-branch workflow), VI (zero deprecated
dependencies).
**Scale/Scope**: 9 enum values. 1 new React component (+ test). 1 new Java enum class
(+ test). 3 existing backend files touched (request DTO, prompt builder, and the
existing prompt-builder test). 5–6 existing frontend files touched (types, options,
reducer, layout, client test, tokens stylesheet).

## Constitution Check

*GATE — must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Gate | Status |
|-----------|------|--------|
| I. Modern & Secure Stack | No deprecated/CVE deps added | PASS — Zero new deps |
| III. TDD (NON-NEGOTIABLE) | Tests written first, must fail before implementation | PASS — `tasks.md` will order test tasks before implementation; >= 90% coverage gate enforced |
| IV. Resilient HTTP | Retry + stub fallback on real-provider failure | PASS — Inherited unchanged from 003; this feature does not alter the fetch wrapper or the fallback path |
| V. Feature Branch Workflow | Dedicated branch + explicit approval | DEVIATION — Branch is pre-assigned as `claude/art-style-category-P7wwR` rather than the constitution-prescribed `006-art-style-category`. Spec / plan / tasks live under `specs/006-art-style-category/` to preserve the numbered layout; documented in Complexity Tracking. |
| VI. Zero Deprecated Dependencies | `npm audit` + `./gradlew dependencyCheckAnalyze` clean | PASS — No new deps; existing audit status unchanged |

**Gate result**: PASS (with one documented deviation — see Complexity Tracking).

### Post-Design Re-check (after Phase 1)

Re-evaluated after contracts + data-model were written: no new violations surfaced.
Design is a pure extension of existing patterns; no net-new abstractions, no new
integrations, no new persistence. Gate still PASS.

## Project Structure

### Documentation (this feature)

```text
specs/006-art-style-category/
├── plan.md              # This file
├── research.md          # Phase 0 — decisions + alternatives
├── data-model.md        # Phase 1 — ArtStyle entity + Selections delta
├── quickstart.md        # Phase 1 — how to smoke-test locally
├── contracts/
│   └── alter-egos.openapi.yaml   # Phase 1 — OpenAPI delta (artStyle field + enum)
├── checklists/
│   └── requirements.md  # from /speckit.specify
├── spec.md              # from /speckit.specify + /speckit.clarify
└── tasks.md             # from /speckit.tasks (NOT created here)
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/aiavatar/alterego/
│   ├── model/
│   │   ├── ArtStyle.java                       # NEW — enum mirroring Vibe / Archetype
│   │   └── AlterEgoRequest.java                # MODIFIED — add @NotNull ArtStyle artStyle
│   └── service/gemini/
│       └── GeminiPromptBuilder.java            # MODIFIED — add ART_STYLE_LABELS, prompt line
└── src/test/java/com/aiavatar/alterego/
    ├── model/
    │   ├── ArtStyleTest.java                   # NEW — @JsonCreator/@JsonValue round-trip
    │   └── AlterEgoRequestValidationTest.java  # MODIFIED — missing artStyle -> 400
    └── service/gemini/
        └── GeminiPromptBuilderTest.java        # MODIFIED — pin label per art style

frontend/
├── src/features/alterego/
│   ├── types.ts                                # MODIFIED — ArtStyle union + Selections.artStyle
│   ├── options.ts                              # MODIFIED — ARTSTYLE_OPTIONS + ACCENT_VARS.artstyle
│   ├── state/reducer.ts                        # MODIFIED — artStyle field + ArtStyleSelected action
│   ├── components/
│   │   ├── ArtStyleGrid.tsx                    # NEW — wraps SelectionGrid<ArtStyle>
│   │   ├── ArtStyleGrid.test.tsx               # NEW — unit test (a11y + keyboard)
│   │   ├── SetupLayout.tsx                     # MODIFIED — mount <ArtStyleGrid />
│   │   └── SetupLayout.test.tsx                # MODIFIED — Generate gating
│   └── services/alterEgoClient.test.ts         # MODIFIED — serialisation assertion
├── src/styles/tokens.css                       # MODIFIED — add --color-accent-artstyle
└── tests/e2e/                                  # MODIFIED — extend happy-path E2E
```

**Structure Decision**: Option 2 — Web application. Two top-level source trees
(`frontend/`, `backend/`) already exist. No new directories. All new files land in
existing feature-scoped folders (`features/alterego/`, `model/`, `service/gemini/`).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Branch name `claude/art-style-category-P7wwR` instead of constitution-prescribed `006-art-style-category` | Branch was pre-assigned by the automation harness that invoked this session; renaming would break the harness' git-push contract. | Cannot rename: the numbered-branch requirement exists so SpecKit scripts can discover the feature dir; we satisfy the underlying goal by setting `SPECIFY_FEATURE=006-art-style-category` when invoking `check-prerequisites.sh` / `setup-plan.sh`, which routes those scripts to the correctly-numbered spec directory. No loss of traceability. |

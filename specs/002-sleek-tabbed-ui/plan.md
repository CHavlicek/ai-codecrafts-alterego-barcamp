# Implementation Plan: Initial Styling and Layout — Tabbed Setup Experience

**Branch**: `002-sleek-tabbed-ui` | **Date**: 2026-04-22 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-sleek-tabbed-ui/spec.md`

## Summary

This feature reshapes the existing 001-initial-poc user flow into a two-tab workflow ("1 setup" / "2 Your Alter Ego") with a sleek, dark-themed visual treatment matching the mockup at `./mockup.png`. The Setup tab's selection model drops `colour` (user no longer picks an accent), re-themes `archetype` to the six engineering roles from the mockup, expands `universe` to the six mockup values, adds a new optional `vibe` picker, and keeps `pose`, `firstName`, and `photo` from 001. Pressing Generate auto-switches the active tab to "2 Your Alter Ego" where the loading indicator and the resulting poster render in place; the Setup tab stays reachable throughout for in-flight re-entry. Implementation touches both the frontend (new `TabsShell`, new `SetupLayout`, new `VibeGrid`, refactor of `AlterEgoPage`, removal of `ColourGrid`) and the backend (enum value renames, drop `colour` from `AlterEgoRequest`, add optional `vibe`, re-key stub outputs, update the OpenAPI contract + examples, update `FallbackPosterProvider` to derive accent from `(archetype, universe)`).

## Technical Context

**Language/Version**: TypeScript 5.x (strict) on the frontend; Java 21 (LTS) on the backend.
**Primary Dependencies**:
  - Frontend: React 18, Vite, TanStack Query v5, React Context + `useReducer` (existing state layer from 001), Vitest + React Testing Library, Playwright.
  - Backend: Spring Boot 3.x, Spring Web MVC, Bean Validation, Spring `RetryTemplate`, JUnit 5 + Mockito, `@SpringBootTest` + `MockMvc`.
**Storage**: N/A. No persistence (constitution-compatible for this feature; mirrors 001 FR-016/017/024).
**Testing**: Vitest (frontend unit/component), Playwright (frontend E2E), JUnit 5 (backend unit), `@SpringBootTest` (backend integration). TDD per Principle III. Unit line coverage ≥ 90% per module (Principle III gate).
**Target Platform**: Modern desktop + tablet browsers (latest Chrome, Firefox, Safari). Backend runs on `eclipse-temurin:21-jre-alpine` in Docker Compose.
**Project Type**: Web application — `frontend/` (React + TS) and `backend/` (Java + Spring Boot) in a single Docker Compose deployment, established by 001.
**Performance Goals**:
  - Setup tab first paint on 1440 px desktop in < 500 ms (cold reload, no cache) — a soft target derived from "sleek" feel; not a gate.
  - Tab switch latency < 100 ms (in-memory state swap; no network).
  - Auto-switch-on-Generate transition visible to the user in < 50 ms after the click.
  - Carries over 001 SC-001 (end-to-end Generate < 3 s on stubs for 95% of runs), SC-004 (degraded poster in < 5 s), SC-006 (Start over < 200 ms).
**Constraints**:
  - No persistence (session-only state). Photo bytes live in process memory for one request only.
  - WCAG 2.1 AA throughout.
  - Resilient HTTP policy (5 retries + exponential backoff + randomised jitter + fallback) applies to the `/api/v1/alter-egos` call, per Principle IV. This is already implemented in 001 and does not change.
  - Zero deprecated dependencies (Principle VI). No new runtime dependencies anticipated.
**Scale/Scope**:
  - Single page, two tabs. Five input regions (Photo + 4 selection grids + Name). ~15 new/modified TS files, ~10 modified Java files.
  - No increase in request volume or payload size (photo payload bounded by 5 MB as before; selections JSON shrinks by one field).

## Constitution Check

Evaluating each active principle of Constitution v1.0.2.

| Principle | Gate status | Evidence |
|---|---|---|
| **I. Modern & Secure Technology Stack (NON-NEGOTIABLE)** | ✅ PASS | React 18 + TS strict (unchanged from 001). Java 21 + Spring Boot 3 (unchanged). No new runtime dependencies. No deprecated packages introduced. |
| **III. Test-First Development (NON-NEGOTIABLE)** | ✅ PASS (planned) | Tasks phase will order tests before implementation. New tests: `TabsShell.test.tsx`, `SetupLayout.test.tsx`, `VibeGrid.test.tsx`, `AlterEgoPage.test.tsx` (rewritten), Playwright tab-flow spec, `AlterEgoRequestValidationTest` updates, `StubCharacterGeneratorTest` updates, `EnumsTest` updates, contract test regeneration. Unit coverage target: ≥ 90% per module (measured by Vitest `coverage` and Jacoco). Integration test: `GenerateAlterEgoIT` updated for the new payload. |
| **IV. Resilient HTTP Communication** | ✅ PASS | No new HTTP calls introduced. Existing `/api/v1/alter-egos` mutation continues to use the 001 resilient client; retry/backoff/jitter/fallback behaviour unchanged. |
| **V. Feature Branch Workflow** | ✅ PASS | Branch `002-sleek-tabbed-ui` cut from `main` via `create-new-feature.sh`. Merge will require PR review per the constitution. |
| **VI. Zero Deprecated Dependencies** | ✅ PASS | No new dependencies. `npm audit` and `./gradlew dependencyCheckAnalyze` will run at PR time (task in Phase 2). |

**Gate**: PASS — no violations. `## Complexity Tracking` is intentionally empty.

## Project Structure

### Documentation (this feature)

```text
specs/002-sleek-tabbed-ui/
├── plan.md              # This file
├── research.md          # Phase 0 output (WAI-ARIA tabs pattern, enum-rename strategy, mutation-state coordination, etc.)
├── data-model.md        # Phase 1 output (TabState, SetupSelections, enum deltas, session phase machine)
├── quickstart.md        # Phase 1 output (how to run + verify)
├── contracts/
│   └── alter-egos.openapi.yaml   # Updated OpenAPI: drop colour, add vibe, re-enum archetype/universe
├── checklists/
│   └── requirements.md  # /speckit.specify output (passing)
├── mockup.png           # Design reference from GitHub issue #3
├── spec.md              # Feature spec (frozen after /speckit.clarify)
└── tasks.md             # NOT created by /speckit.plan — /speckit.tasks output
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/aiavatar/alterego/
│   ├── controller/AlterEgoController.java        # (edit) Bean Validation on updated request
│   ├── model/
│   │   ├── AlterEgoRequest.java                  # (edit) drop `colour`, add `vibe` Optional<Vibe>
│   │   ├── Archetype.java                        # (edit) replace enum values with the 6 engineering roles
│   │   ├── Universe.java                         # (edit) replace enum values with the 6 mockup universes
│   │   ├── Vibe.java                             # (new) enum: BUILDER, THINKER, REBEL, ARCHITECT
│   │   ├── Pose.java                             # (unchanged)
│   │   ├── Colour.java                           # (delete) no longer referenced
│   │   └── …                                     # other DTOs unchanged
│   └── service/
│       ├── stub/StubCharacterGenerator.java      # (edit) re-key lookup tables to new archetype × universe
│       ├── stub/StubImageGenerator.java          # (edit) re-key + derive accent colour from (archetype, universe)
│       └── fallback/FallbackPosterProvider.java  # (edit) derive accent colour from (archetype, universe)
├── src/test/java/com/aiavatar/alterego/
│   ├── unit/                                     # (edit) AlterEgoRequestValidationTest, EnumsTest,
│   │                                             #         StubCharacterGeneratorTest, StubImageGeneratorTest,
│   │                                             #         FallbackPosterProviderTest
│   ├── contract/AlterEgoControllerContractTest.java  # (edit) new request shape, new enum values
│   ├── contract/AlterEgoControllerErrorContractTest.java  # (edit) new enum errors, no `colour` error path
│   └── integration/
│       ├── GenerateAlterEgoIT.java               # (edit) new payload + response assertions
│       ├── GenerateAlterEgoFallbackIT.java       # (edit) same
│       └── LogRedactionIT.java                   # (unchanged — photo redaction is payload-agnostic)
└── …

frontend/
├── src/
│   ├── App.tsx                                   # (edit) mount TabsShell at root
│   ├── features/alterego/
│   │   ├── AlterEgoPage.tsx                      # (edit) compose TabsShell + SetupLayout + AlterEgoPanel; remove phase-driven page swap
│   │   ├── AlterEgoPage.test.tsx                 # (edit)
│   │   ├── components/
│   │   │   ├── TabsShell.tsx                     # (new) two-tab layout with WAI-ARIA tabs pattern
│   │   │   ├── TabsShell.test.tsx                # (new)
│   │   │   ├── SetupLayout.tsx                   # (new) two-column layout (photo + selections)
│   │   │   ├── SetupLayout.test.tsx              # (new)
│   │   │   ├── AlterEgoPanel.tsx                 # (new) empty / loading / poster states for tab 2
│   │   │   ├── AlterEgoPanel.test.tsx            # (new)
│   │   │   ├── VibeGrid.tsx                      # (new) optional-selection 2×2 grid
│   │   │   ├── VibeGrid.test.tsx                 # (new)
│   │   │   ├── ColourGrid.tsx                    # (delete) no colour picker in new layout
│   │   │   ├── ColourGrid.test.tsx               # (delete)
│   │   │   ├── PoseGrid.tsx                      # (edit) restyle to pill-grid; keep options
│   │   │   ├── PoseGrid.test.tsx                 # (edit)
│   │   │   ├── ArchetypeGrid.tsx                 # (edit) restyle + new emoji/label pairs; display label "Role"
│   │   │   ├── ArchetypeGrid.test.tsx            # (edit)
│   │   │   ├── UniverseGrid.tsx                  # (edit) restyle + 6 new options
│   │   │   ├── UniverseGrid.test.tsx             # (edit)
│   │   │   ├── PhotoIntake.tsx                   # (edit) circular preview + Camera/Upload pill pair
│   │   │   ├── PhotoIntake.test.tsx              # (edit)
│   │   │   ├── FirstNameInput.tsx                # (edit) new label "Your name (for personalized character)"
│   │   │   ├── FirstNameInput.test.tsx           # (edit)
│   │   │   ├── GenerateButton.tsx                # (edit) no colour gating; column-width; auto-switch on click
│   │   │   ├── GenerateButton.test.tsx           # (edit)
│   │   │   ├── GenerationLoading.tsx             # (unchanged — moves from page-root to AlterEgoPanel slot)
│   │   │   ├── PosterView.tsx                    # (unchanged — moves to AlterEgoPanel slot)
│   │   │   ├── StartOverButton.tsx               # (edit) on start-over also reset active tab to "setup"
│   │   │   └── StartOverButton.test.tsx          # (edit)
│   │   ├── hooks/
│   │   │   ├── useAlterEgoSession.ts             # (edit) expose `activeTab`, `setActiveTab` through context
│   │   │   ├── useGenerateAlterEgo.ts            # (edit) `onMutate` dispatches `ActiveTabChanged` to "alter-ego"
│   │   │   └── useActiveTab.ts                   # (new) thin selector hook
│   │   ├── state/
│   │   │   ├── reducer.ts                        # (edit) add `activeTab` + `vibe` to session; drop `colour`;
│   │   │   │                                      #         ActiveTabChanged, VibeSelected actions
│   │   │   ├── AlterEgoProvider.tsx              # (unchanged)
│   │   │   └── context.ts                        # (unchanged)
│   │   ├── options.ts                            # (edit) replace ARCHETYPE/UNIVERSE options; add VIBE_OPTIONS;
│   │   │                                          #         delete COLOUR_OPTIONS
│   │   ├── types.ts                              # (edit) replace Archetype/Universe unions; add Vibe; drop Colour;
│   │   │                                          #         update Selections
│   │   └── services/                             # (edit) HTTP client drops `colour`, adds `vibe`
│   └── styles/
│       └── tokens.css                            # (edit) add accent-cyan + per-subgroup selection tints;
│                                                  #         drop unused colour-accent-* tokens from the Colour enum
├── tests/
│   └── playwright/
│       ├── tabs-shell.spec.ts                    # (new) tab navigation, keyboard, empty state, auto-switch
│       ├── setup-form.spec.ts                    # (new) filling out the new Setup form
│       └── e2e-flow.spec.ts                      # (edit) full flow with new selection set
└── vite.config.ts / playwright.config.ts         # (unchanged)
```

**Structure Decision**: Retain the 001 two-project layout under `frontend/` and `backend/`. This feature is a significant UI re-shape + small backend contract delta, so it lives entirely inside those trees — no new top-level directories and no new services. The Java package tree (`com.aiavatar.alterego.*`) and the React feature folder (`frontend/src/features/alterego/`) are the natural homes for every change.

## Complexity Tracking

> No constitution violations; no simpler-alternative trade-offs to record. This section is intentionally empty.

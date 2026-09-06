# Implementation Plan: Hide Vibe and Pose Categories from the Setup UI

**Branch**: `020-hide-vibe-pose` | **Date**: 2026-05-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/020-hide-vibe-pose/spec.md`

## Summary

Remove the Pose and Vibe theme grids from the Setup tab UI, renumber the remaining three theme groups (1 Archetype / 2 Universe / 3 Art Style), and move responsibility for picking a Pose and a Vibe per request from "user clicks a tile" to "server rolls uniformly at random". The frontend's `Selections` payload drops `pose` and `vibe`; the backend's `AlterEgoRequest` DTO splits into a public-facing "user selections" DTO (no Pose / Vibe) and a server-internal "resolved request" record that prompt builders continue to consume unchanged. A new `RandomCategorySelector` bean injects uniform-random picks into the resolved request at one well-defined seam in `AlterEgoService`. No new runtime or test dependency. No persistence. Existing prompt-building logic (Gemini text + image, fal.ai) is preserved bit-for-bit by design.

## Technical Context

**Language/Version**: TypeScript 5.x (strict, frontend); Java 21 LTS (backend). Unchanged from 001..019.
**Primary Dependencies**: React 19, Vite 8, Vitest, React Testing Library, Playwright (frontend); Spring Boot 3.x, Jakarta Bean Validation, JUnit 5, Spring Boot Test, Mockito (backend). **No new runtime or test dependency.**
**Storage**: N/A. Inherits 001 FR-016 / FR-017 / FR-024 — no persistence. The rolled (Pose, Vibe) values live on the request thread's stack for the duration of one HTTP request and are neither logged nor cached. The `RandomCategorySelector` bean holds only a `RandomGenerator` instance, no state.
**Testing**: Vitest + RTL (frontend unit/component), Playwright (frontend E2E if/when triggered locally), JUnit 5 + Spring Boot Test (backend unit + contract + slice).
**Target Platform**: Evergreen browsers on the kiosk (Chrome / Edge / Safari current majors). Backend on JVM 21 in `eclipse-temurin:21-jre-alpine` container.
**Project Type**: Web — frontend + backend (Option 2 structure already established by 001).
**Performance Goals**: No measurable change. The end-to-end Generate request is dominated by image-provider latency (seconds). Adding two `RandomGenerator.nextInt(enum.length)` calls per request is O(1) at <1 μs and is below measurement noise.
**Constraints**: No new dependency (Principle I, VI). No new persistence (extends 001 FR-016). No change to printed artefact shape (FR-2042). No accessibility regression (SC-005). Public HTTP contract for `/api/v1/alter-egos` removes two fields from the request body — this is the only externally-visible contract change.
**Scale/Scope**: Single-user kiosk POC; ~1 concurrent Generate request per device. ~12 frontend files touched, ~5 backend files touched, ~7 test files added or changed.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution version 1.0.2 — evaluation:

| Principle | Gate | Verdict |
|---|---|---|
| I. Modern & Secure Technology Stack (NON-NEGOTIABLE) | No new dependency, no CVE-bearing library introduced. Uses only stdlib `RandomGenerator` on the backend and existing Vitest / RTL on the frontend. | ✅ Pass |
| III. Test-First Development (NON-NEGOTIABLE) | Phase 1 contracts (below) enumerate the failing tests first: contract test for the new request body shape, unit test for `RandomCategorySelector`, slice test for `AlterEgoService` proving Pose/Vibe are server-supplied, frontend component test for `SetupLayout` proving Pose/Vibe controls are gone, reducer test proving `pose`/`vibe` are no longer in session shape, hook test proving `surprise()` no longer commits Pose/Vibe to the visible state. Coverage gate ≥ 90% holds because (a) deleted code is removed entirely (not stubbed) and (b) the new `RandomCategorySelector` is one file with a deterministic-RNG-injectable seam — fully unit-coverable. | ✅ Pass |
| IV. Resilient HTTP Communication | No new HTTP call. The existing resilient client wrapping `/api/v1/alter-egos` is reused unchanged. The contract change is request-body-shape-only, served by the same controller. | ✅ Pass |
| V. Feature Branch Workflow | On branch `020-hide-vibe-pose` cut from `main` per `create-new-feature.sh`. PR + approval gate will apply at merge time. | ✅ Pass |
| VI. Zero Deprecated Dependencies | No package added; no package removed. `npm audit` and `./gradlew dependencyCheckAnalyze` baselines unchanged. | ✅ Pass |

**Result**: All gates pass. No Complexity Tracking entries required.

Post-Phase-1 re-check (after data-model + contracts are written): see "Constitution Re-check" at the bottom of this plan.

## Project Structure

### Documentation (this feature)

```text
specs/020-hide-vibe-pose/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output — design decisions & rejected alternatives
├── data-model.md        # Phase 1 output — visible vs. resolved request, removed reducer fields
├── quickstart.md        # Phase 1 output — local verification recipe
├── contracts/           # Phase 1 output
│   ├── alter-egos-request.md   # HTTP contract for POST /api/v1/alter-egos (new shape)
│   └── random-selector.md      # Server-internal contract for the new RandomCategorySelector bean
├── checklists/
│   └── requirements.md  # /speckit.specify output — already created
└── tasks.md             # Phase 2 output (/speckit.tasks command — NOT created here)
```

### Source Code (repository root)

The project is already a `frontend/` + `backend/` web app (established by 001). This feature touches files under both trees and adds none outside them.

```text
frontend/
├── src/
│   ├── features/alterego/
│   │   ├── components/
│   │   │   ├── SetupLayout.tsx           # MODIFY — drop PoseGrid + VibeGrid, renumber data-step 1..3
│   │   │   ├── SetupLayout.test.tsx      # MODIFY — assert Pose/Vibe absent, renumbering applied
│   │   │   ├── PoseGrid.tsx              # DELETE
│   │   │   ├── PoseGrid.test.tsx         # DELETE
│   │   │   ├── VibeGrid.tsx              # DELETE
│   │   │   └── VibeGrid.test.tsx         # DELETE
│   │   ├── state/
│   │   │   ├── reducer.ts                # MODIFY — drop pose, vibe fields + PoseSelected, VibeSelected actions
│   │   │   ├── reducer.test.ts           # MODIFY — drop the two action cases; tighten SurpriseMePicked test
│   │   │   ├── selectors.ts              # MODIFY — drop 'pose' from RequiredInput / missingInputs
│   │   │   └── selectors.test.ts         # MODIFY — adjust expected missingInputs
│   │   ├── lib/
│   │   │   ├── randomSelections.ts       # MODIFY — drop pose, vibe from SurpriseMePicks; remove from picks
│   │   │   └── randomSelections.test.ts  # MODIFY — drop pose, vibe coverage assertions
│   │   ├── hooks/
│   │   │   ├── useGenerateAlterEgo.ts    # MODIFY — surprise() no longer commits pose/vibe; Selections payload drops them
│   │   │   └── useGenerateAlterEgo.test.ts # MODIFY — adjust expectations
│   │   ├── services/
│   │   │   ├── alterEgoClient.ts         # No structural change (FormData layer); the Selections type it embeds changes
│   │   │   └── alterEgoClient.test.ts    # MODIFY — fixture no longer includes pose/vibe
│   │   ├── types.ts                      # MODIFY — Selections drops pose, vibe (Pose, Vibe types deleted from frontend)
│   │   └── options.ts                    # MODIFY — POSE_OPTIONS, VIBE_OPTIONS removed (only the frontend mirror; backend retains its enums)
│   └── ...

backend/
├── src/
│   ├── main/java/com/aiavatar/alterego/
│   │   ├── controller/AlterEgoController.java     # MODIFY — accept new public DTO (no pose, vibe)
│   │   ├── model/
│   │   │   ├── AlterEgoUserSelections.java        # NEW — public-facing DTO; archetype, universe, artStyle, firstName, photoMode
│   │   │   ├── AlterEgoRequest.java               # KEEP — becomes a server-internal "resolved" record; constructed by service after roll
│   │   │   ├── Pose.java                          # NO CHANGE — enum preserved as-is (FR-2040)
│   │   │   └── Vibe.java                          # NO CHANGE — enum preserved as-is (FR-2040)
│   │   ├── service/
│   │   │   ├── AlterEgoService.java               # MODIFY — accept AlterEgoUserSelections, roll Pose+Vibe via RandomCategorySelector, build AlterEgoRequest
│   │   │   └── random/RandomCategorySelector.java # NEW — Spring component; pickUniform(Class<? extends Enum<E>>) using injectable RandomGenerator
│   │   └── ... (prompt builders unchanged — still consume AlterEgoRequest)
│   └── test/java/com/aiavatar/alterego/
│       ├── contract/AlterEgoControllerContractTest.java       # MODIFY — request fixture drops pose/vibe; response unchanged
│       ├── unit/AlterEgoRequestValidationTest.java            # MODIFY → split: AlterEgoUserSelectionsValidationTest (NEW); old class deleted
│       ├── unit/random/RandomCategorySelectorTest.java        # NEW — uniformity, full-coverage, injected RNG determinism
│       └── service/AlterEgoServiceTest.java                   # MODIFY (or NEW slice test) — assert resolved AlterEgoRequest always carries non-null Pose + Vibe; assert client-supplied pose/vibe in JSON is rejected by deserialization
```

**Structure Decision**: Web (frontend + backend), already in place. No new top-level directories; one new sub-package `service/random/` on the backend and one new contract file under `specs/020-hide-vibe-pose/contracts/`. Frontend Pose/Vibe code is **deleted, not deprecated** — Pose and Vibe leave the frontend's vocabulary entirely. The backend retains the enums verbatim (FR-2040 preserves them as business logic).

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified.

No violations. Table omitted.

## Phase 0 — Research

See [`research.md`](./research.md). Summary of resolved decisions:

1. **Where is the random pick made?** — Server-side, inside `AlterEgoService` at the seam where it composes the prompt-builder input, via a new `RandomCategorySelector` Spring component. Justification: spec FR-2025 (server is authoritative; client-supplied values must be ignored); keeps prompt builders unchanged; testable in isolation.
2. **How is "ignore client input" enforced?** — By dropping `pose` and `vibe` from the public DTO entirely. The controller deserializes `AlterEgoUserSelections` (no Pose / Vibe fields), so an extraneous `pose` or `vibe` key in the JSON is silently ignored by Jackson's default `FAIL_ON_UNKNOWN_PROPERTIES=false` posture — verified by an explicit contract test that sends `{ "pose": "heroic", ... }` and asserts the rolled Pose may differ.
3. **Is `AlterEgoRequest` kept or split?** — Split. Public DTO = `AlterEgoUserSelections`. Server-internal "resolved" record = `AlterEgoRequest` (existing record, same field set), constructed by the service. Prompt builders are not touched. Rationale: minimum churn to prompt-building (3 builders × 2 label maps × multiple tests would otherwise need editing); cleanest test seam.
4. **RNG choice** — `java.util.random.RandomGenerator` (Java 17+ interface) with the default `L64X128MixRandom` algorithm. Not `SecureRandom` (overkill; the choice is not security-sensitive). Injectable at construction so tests can pass a deterministic seed.
5. **Frontend reducer: keep or drop `pose` / `vibe` fields?** — Drop. Spec User Story 1 acceptance scenario 2 requires the categories to be *removed*, not hidden, including from the accessible name tree. Keeping dormant reducer fields would invite drift and produce dead branches in selectors / serializers.
6. **Surprise Me semantics** — `randomSelections()` returns picks for only Archetype, Universe, ArtStyle. The `SurpriseMePicked` action drops `pose` and `vibe` from its payload. The backend rolls Pose / Vibe for both Generate and Surprise Me equally — the frontend has no reason to do it for Surprise Me anymore.
7. **Failure mode if a backend enum is empty** — Cannot happen: enums are compiled-in, length ≥ 1 at runtime by construction. The `RandomCategorySelector.pickUniform(Class)` throws `IllegalStateException` if `getEnumConstants()` returns 0 elements as a defensive guard; this surfaces through the existing controller error-handling onto the resilient frontend's fallback state (FR-2024 satisfied via existing IV. seam).

## Phase 1 — Design & Contracts

Outputs (in this directory):

- [`data-model.md`](./data-model.md) — Visible session shape (post-change), resolved request shape (server-internal), removed reducer / DTO fields.
- [`contracts/alter-egos-request.md`](./contracts/alter-egos-request.md) — HTTP request body change for `POST /api/v1/alter-egos`.
- [`contracts/random-selector.md`](./contracts/random-selector.md) — Server-internal contract for `RandomCategorySelector`.
- [`quickstart.md`](./quickstart.md) — Local verification recipe (manual & automated).

Agent context: the existing CLAUDE.md "Active Technologies" block is already complete for this feature (no new tech). The agent script will be invoked at the end of Phase 1.

## Constitution Re-check (post-design)

After Phase 1 design is captured, re-evaluate the five active gates:

- **I. Modern & Secure Technology Stack** — Confirmed: only stdlib `java.util.random.RandomGenerator` is added on the backend; no npm or Maven addition.
- **III. TDD** — Confirmed: every artefact in `tasks.md` (to be produced by `/speckit.tasks`) will be ordered as "failing test first → production code second", and the contract test in `contracts/alter-egos-request.md` is precise enough to be written before any controller edit.
- **IV. Resilient HTTP** — Confirmed: no HTTP-call surface change; the resilient client wrapper around `/api/v1/alter-egos` is untouched.
- **V. Feature Branch Workflow** — Confirmed: branch `020-hide-vibe-pose`.
- **VI. Zero Deprecated Dependencies** — Confirmed: zero dependency churn.

All gates pass post-design. No deviations.

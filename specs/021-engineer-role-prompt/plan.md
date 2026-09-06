# Implementation Plan: Add Engineer Role to the AI Image Generation Input Prompt

**Branch**: `021-engineer-role-prompt` | **Date**: 2026-05-11 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/021-engineer-role-prompt/spec.md`
**Source issue**: [#54](https://github.com/squer-solutions/aiavatar/issues/54)

## Summary

The cinematic-poster image prompt currently used by both image providers (`GeminiPromptBuilder` for Gemini, `FalAiPromptBuilder` for fal.ai) deliberately **excludes** the engineering role (Archetype). The exclusion was added in feature 017 as a workaround after the model started rendering the role string as decorative banner text inside the character cutout (e.g. *"CLOUD ARCHITECT LALO."* on a parchment scroll) even with a generic "no overlaid text" instruction. Feature 017 then strengthened the negative-text instruction by enumerating offenders (banners, scrolls, name plates).

Issue #54 reverses the role-exclusion half of that workaround: the role must influence the **image**, not just the downstream text overlay and the character-bio prompt. The strengthened negative-text instruction is kept and reinforced; the role is re-introduced **as a visual/scene direction** (props, environment, attire, activity) rather than as a label that could be transcribed.

**Technical approach**:

1. **Re-introduce the role line in both image prompt builders** (`GeminiPromptBuilder`, `FalAiPromptBuilder`). The line is phrased as a *scene direction* — e.g. `- Engineering role (render visual cues only, NOT as text): Cloud Architect`. The role label vocabulary is forked from `GeminiCharacterPromptBuilder.ROLE_LABELS` (kept duplicated per the 016 R11 "fork rather than share" decision so the three builders can diverge independently).
2. **Strengthen the no-rendered-text instruction** in both image builders to repeat the rule once *inside* the category line ("render visual cues only, NOT as text") and once in the composition-notes block (today's enumeration of banner / scroll / name plate). The composition-notes line gets one additional clarifier that explicitly forbids transcribing the role label as text.
3. **Invert / rewrite the existing tests** that pin "the role MUST NOT appear in the prompt" — those tests were the explicit regression-lock for the 017 workaround we are now reversing. The new tests pin the inverse: the role label MUST appear (so role-driven visual variation is locked in), pair-wise role deltas MUST change the prompt (SC-2103's automatable half), and the byte-for-byte `SINGLE`-variant fixture string is regenerated with the new line.
4. **Manual provider-call validation** for SC-2101 / SC-2102 / SC-2103 (per-role identifiability + zero text bleed + pair-wise distinctness). No automated OCR is added. The quickstart documents the human-review protocol and the sample size.

No frontend change, no wire-contract change, no new dependency.

## Technical Context

**Language/Version**: Java 21 (LTS) on the backend — Gradle Kotlin DSL. TypeScript 5.x (strict) on the frontend is **untouched**.
**Primary Dependencies**: Spring Boot 3.x (existing); `com.fasterxml.jackson` (existing, for prompt-adjacent payloads only — the builders themselves use plain `StringBuilder`). No new runtime dependency.
**Storage**: N/A. Extends 001 FR-016 / FR-017 / FR-024 unchanged — the role, the composed prompt string, and the provider response live only in process memory for the duration of one Generate request.
**Testing**: JUnit 5 + Mockito (backend unit). `@SpringBootTest` integration tests for the Generate path exist (`GenerateAlterEgoGeminiIT`, `GenerateAlterEgoIT`); both use mocked provider clients, so the integration coverage is asserting controller wiring rather than the live provider response. SC-2101..SC-2104 (per-role identifiability) are validated manually against a live provider per the quickstart.
**Target Platform**: Backend JVM (containerised); not platform-sensitive.
**Project Type**: Web application — backend prompt-composition change only. Frontend is untouched.
**Performance Goals**: SC-2105 — median wall-clock time on the Generate path must stay within ±10% of pre-feature baseline. Adding ~80 bytes to the prompt is well within provider token-budget noise; this is a soft check, not a measured benchmark.
**Constraints**: SC-2102 — **zero** rendered text inside the image across a ≥30-image manual-review sample. This is the load-bearing constraint and the entire reason the role was excluded in the first place. Mitigation lives in the prompt wording (see Research §R2).
**Scale/Scope**: Two files modified in `backend/src/main`, three test files modified in `backend/src/test`. No new files. No DB, no API surface change.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Principle I — Modern & Secure Technology Stack
✅ **PASS**. No new dependency. Java 21 + Spring Boot 3.x unchanged. No deprecated packages touched.

### Principle III — Test-First Development (TDD — NON-NEGOTIABLE)
✅ **PASS, with one nuance to call out**. The change inverts a load-bearing regression-lock test that pins the prior (post-017) behaviour. The TDD sequence will therefore:

1. Update / invert the existing `archetypeRoleIsNotPassedToImageGenerationAi`, `changingRoleNoLongerChangesPromptSinceRoleIsExcluded`, `purelyVisualAxesAppearInTheTemplate` (the `assertFalse(prompt.contains("Engineering role:"))` branch only), and the `SINGLE`-variant byte-for-byte fixture in `GeminiPromptBuilderTest` to assert the *new* contract (role IS in the prompt; role-only delta DOES change the prompt; role line IS present). Same set in `FalAiPromptBuilderTest`. Commit. Run `./gradlew test` — these tests MUST now fail RED against the current builder code.
2. Obtain explicit approval of the new test suite (per Principle III item 2).
3. Implement the builder change (add `appendRoleLine`-style logic to both builders; strengthen the negative-text instruction) until GREEN.
4. Refactor under green; coverage must stay ≥ 90%.

Integration test (Principle III: every feature MUST include at least one): the Generate path is already covered by `GenerateAlterEgoGeminiIT` (Gemini-profile) and `GenerateAlterEgoIT` (stub-profile). A new integration test is **not** required — the existing IT exercises the controller-to-builder wire path and will fail-fast if the builder throws on any of the six Archetype values. Instead we add one parametrised integration assertion to `GenerateAlterEgoGeminiIT` that captures the prompt actually sent to the mocked Gemini client and asserts the role label appears in it, per Archetype. (Per Principle III: backend ITs use `@SpringBootTest`; production-profile wiring is not mocked away.)

### Principle IV — Resilient HTTP Communication
✅ **PASS**. No change to HTTP-call sites. The existing retry + stub-fallback contract on `GeminiClient` / `FalAiClient` is preserved by definition — the only code change is upstream of the HTTP boundary.

### Principle V — Feature Branch Workflow
✅ **PASS**. Branch `021-engineer-role-prompt` cut from `main` by `create-new-feature.sh`. Merge requires explicit human approval.

### Principle VI — Zero Deprecated Dependencies
✅ **PASS**. No dependency change.

### Project structure / SpecKit conventions
✅ **PASS**. Spec / plan / research / data-model / contracts / quickstart all live under `specs/021-engineer-role-prompt/`. No structural deviation.

**Gate result**: **PASS** — proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/021-engineer-role-prompt/
├── plan.md              # this file
├── spec.md              # written by /speckit.specify
├── research.md          # Phase 0 output (this command)
├── data-model.md        # Phase 1 output (this command)
├── quickstart.md        # Phase 1 output (this command)
├── contracts/
│   └── image-prompt-contract.md   # Phase 1 output (prompt-shape contract)
├── checklists/
│   └── requirements.md  # written by /speckit.specify
└── tasks.md             # written by /speckit.tasks (NOT this command)
```

### Source Code (repository root)

This is a backend-only change. The directories touched:

```text
backend/
├── src/
│   ├── main/java/com/aiavatar/alterego/
│   │   ├── service/
│   │   │   ├── gemini/GeminiPromptBuilder.java          # MODIFIED — add role line + strengthen no-text
│   │   │   └── falai/FalAiPromptBuilder.java            # MODIFIED — add role line + strengthen no-text
│   │   └── model/Archetype.java                          # UNCHANGED (label() / wire() already adequate)
│   └── test/java/com/aiavatar/alterego/
│       ├── unit/gemini/GeminiPromptBuilderTest.java     # MODIFIED — invert role assertions, regenerate fixture
│       ├── unit/falai/FalAiPromptBuilderTest.java       # MODIFIED — invert role assertions
│       └── integration/GenerateAlterEgoGeminiIT.java    # MODIFIED — add per-Archetype prompt-capture assertion

frontend/                                                  # UNTOUCHED
```

**Structure Decision**: backend-only modification of two prompt builders and their tests. No new files. The frontend is not touched; the wire contract is not changed.

## Complexity Tracking

> No Constitution Check violations — table intentionally empty.

The single non-trivial design call is whether to **share** the role-label map across the three prompt builders (`GeminiPromptBuilder`, `FalAiPromptBuilder`, `GeminiCharacterPromptBuilder`) or to **duplicate** it in each. The 016 R11 decision was "fork rather than share" for image vs. text-bio prompts so the two surfaces can diverge in tone. This plan preserves that: the new `ROLE_LABELS` constant lives **independently** in each image builder, identical in content today to `GeminiCharacterPromptBuilder.ROLE_LABELS` but free to diverge later (e.g. if a fal.ai-specific role phrasing becomes useful). Sharing via a single static utility would be marginally less code today but would re-introduce the coupling 016 R11 explicitly rejected. The duplication cost is six map entries per builder — well below the "three similar lines is better than a premature abstraction" threshold.

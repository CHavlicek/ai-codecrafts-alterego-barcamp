# Quickstart — 021 Add Engineer Role to the AI Image Generation Input Prompt

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Contract**: [contracts/image-prompt-contract.md](./contracts/image-prompt-contract.md)

A self-contained recipe for implementing, testing, and manually validating this feature end-to-end.

## What you're shipping

Two backend prompt-builder files gain a new `Engineering role (...)` line on the attribute block of the image-generation prompt, and the no-rendered-text composition-note line is reinforced to explicitly forbid transcribing the role label. No frontend change. No wire-contract change. No new dependency. No persistence.

## Pre-flight (read once, skip on re-runs)

```bash
# At repo root.
git status                         # expect: clean working tree on 021-engineer-role-prompt
./gradlew --version                # expect: Gradle 8.x, JVM 21.x
```

Confirm the spec, plan, research, data-model, and contract are present:

```bash
ls specs/021-engineer-role-prompt/
# spec.md  plan.md  research.md  data-model.md  contracts/  checklists/
```

## TDD sequence

### Step 1 — RED: invert and extend the unit tests

Edit `backend/src/test/java/com/aiavatar/alterego/unit/gemini/GeminiPromptBuilderTest.java`:

1. **Replace** `archetypeRoleIsNotPassedToImageGenerationAi` with `archetypeRoleAppearsInTheImagePrompt` — asserts `prompt.contains("Cloud Architect")` and `prompt.contains("Engineering role (")`.
2. **Replace** `changingRoleNoLongerChangesPromptSinceRoleIsExcluded` with `changingRoleChangesPromptSubstring` — pair (`BACKEND_DEV` vs `CLOUD_ARCHITECT`) asserts the two prompts differ and that each contains its own role's **Prompt label** (`Backend Developer` / `Cloud Architect`).
3. **Update** `purelyVisualAxesAppearInTheTemplate` — drop the `assertFalse(prompt.contains("Engineering role:"))` line; assert `assertTrue(prompt.contains("Engineering role ("))` instead. Keep the `Name:` and firstName negative assertions.
4. **Regenerate** the byte-for-byte fixture in `explicitSinglePhotoModeProducesTodaysBaselinePrompt` to match the [contract's SINGLE fixture](./contracts/image-prompt-contract.md#gemini-image-prompt--full-byte-for-byte-fixture-single-variant).
5. **Add** `everyArchetypeProducesAUniquePromptLine` — iterate `Archetype.values()`, build a prompt for each, and assert the six prompts are mutually distinct AND each contains its own Prompt label.
6. **Add** `engineeringRoleLineFollowsUniverseAndPrecedesArtStyle` — pin the new ordering.
7. **Add** `compositionNoteForbidsTranscribingTheRoleLabel` — `assertTrue(prompt.contains("NO transcribing the engineering-role label"))`.
8. **Keep** every other test as-is (firstName exclusion, Universe / Pose / Vibe / Art-style assertions, GROUP variant assertions). Update the GROUP-variant tests only if their negative assertions overlap the role exclusion — `archetypeRoleIsNotPassedToImageGenerationAi`-style assertions in the GROUP suite become positive in the same way as the SINGLE suite.

Mirror the same set of edits in `backend/src/test/java/com/aiavatar/alterego/unit/falai/FalAiPromptBuilderTest.java` for the fal.ai builder.

Edit `backend/src/test/java/com/aiavatar/alterego/integration/GenerateAlterEgoGeminiIT.java`:

9. **Add** one parametrised assertion (`@ParameterizedTest @EnumSource(Archetype.class)`) that posts a Generate request with the given archetype, captures the prompt argument passed to the mocked `GeminiClient`, and asserts the prompt contains the **Prompt label** for that archetype.

Run:

```bash
cd backend && ./gradlew test
# Expect: GeminiPromptBuilderTest, FalAiPromptBuilderTest, and GenerateAlterEgoGeminiIT fail RED.
```

**Commit at RED**, push, request explicit approval per Constitution Principle III item 2.

### Step 2 — GREEN: implement

Edit `backend/src/main/java/com/aiavatar/alterego/service/gemini/GeminiPromptBuilder.java`:

1. **Add** `private static final Map<Archetype, String> ROLE_LABELS = new EnumMap<>(Archetype.class);` and populate it with the six entries from data-model.md (copy-paste from `GeminiCharacterPromptBuilder.ROLE_LABELS`).
2. **Update** `appendCategoryLines` to emit the role line **after** the Universe line and **before** the Art-style line:
   ```java
   sb.append("- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): ")
           .append(label(ROLE_LABELS, request.archetype())).append('\n');
   ```
3. **Update** the composition-notes line in both `buildSingle` and `buildGroup` to append `, and NO transcribing the engineering-role label` before the `. The poster's text overlay is composited downstream ...` clause. Use the exact wording from the [contract](./contracts/image-prompt-contract.md#negative-text-rule-both-builders-both-variants).
4. **Update** the `appendCategoryLines` doc-comment — replace the "engineering role are deliberately NOT included" prose with a note that the role is now included as a visual scene direction and link to the 021 spec.

Mirror the same set of edits in `backend/src/main/java/com/aiavatar/alterego/service/falai/FalAiPromptBuilder.java`.

Run:

```bash
cd backend && ./gradlew test
# Expect: GREEN.
```

### Step 3 — Coverage + refactor

```bash
cd backend && ./gradlew test jacocoTestReport
# Open build/reports/jacoco/test/html/index.html
# Confirm line coverage ≥ 90% for both prompt-builder packages.
```

If coverage dipped, add tests for the GROUP variant's role line. Refactor under green if a duplication threshold (per the "three similar lines is better than a premature abstraction" rule in CLAUDE.md) is crossed — this feature's diff should not cross it.

### Step 4 — Sonar (Development Workflow step 6)

```bash
cd backend && ./gradlew sonar
# Resolve every NEW issue before committing.
```

(Skip if SonarQube isn't running locally — flag in the PR description.)

## Manual provider-validation pass (Success Criteria SC-2101 ... SC-2104)

The automated test suite covers prompt **composition**; the user-facing success criteria are about the **rendered image** the provider produces. These cannot be verified by JUnit alone.

### Setup

```bash
# Backend on the gemini profile (default per recent commits).
cd backend && ./gradlew bootRun --args='--spring.profiles.active=gemini'

# Frontend in a second terminal.
cd frontend && npm run dev
```

Open `http://localhost:5173` in a browser. Use the camera (or a saved sample photo) for repeatability.

### SC-2101 — per-role identifiability (≥83%)

For each of the six Archetype values, generate six posters with the same Universe + Art Style + first name + photo, varying ONLY the role. A reviewer (ideally not the implementer) scores each image for the question *"does this image visually evoke role X?"* (yes/no). Target: ≥5/6 yes per role across the 36 images.

### SC-2102 — zero text bleed across ≥30 images

Across the 36 images from SC-2101, count how many contain **any** rendered text — name plates, banners, parchment scrolls, signs, captions, watermarks, logos, or transcribed role labels. **Hard fail** if the count is non-zero. If it is, the wording in the prompt needs further hardening; revisit Research §R2 alternatives before reverting.

### SC-2103 — pair-wise role identifiability (≥80%)

From SC-2101's 36 images, pick 15 random pairs that differ only in role. Show the reviewer the two images side-by-side and ask *"which role is image A, which is B?"* (multiple choice over six options for each). Target: ≥12/15 pairs correctly identified.

### SC-2104 — Surprise-Me parity (within ±5pp)

Generate 12 posters via the Surprise Me button (random role each time). Score them using SC-2101's rubric. Compare the per-role identifiability rate to SC-2101's manual-pick rate. The two rates should be within ±5 percentage points.

### SC-2105 — latency budget (within ±10% of baseline)

Capture wall-clock time on five Generate clicks before and after this feature (a `console.time` wrapper around the `useGenerateAlterEgo` call works). Compare medians.

### SC-2106 — fallback path

Run the backend on the `stub` profile or with the Gemini API key unset:

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=stub'
```

Generate a poster — the fallback poster should appear with the role rendered in its text overlay exactly as before. (Fallback uses the FE-side `FallbackPosterProvider`, not the LLM prompt, so this is a smoke check that the role-overlay path is untouched.)

## Commit / PR

Per Constitution Principle V:

1. `git add -p` (stage only the files in plan.md's "Project Structure" tree).
2. Commit with a message that references the spec and issue (`feat(021): add engineering role to image prompt (closes #54)`).
3. Push. Open PR. Reference the manual-validation report (a short Markdown comment with SC-2101..SC-2106 results).
4. Wait for explicit human approval before merging.

## Rollback plan

If the manual provider-validation pass (SC-2102) shows ≥1 text-bleed image, the wording is insufficient. Options, in order of preference:

1. Strengthen the inline clarifier further (e.g. `"render as PURELY visual cues ..."`).
2. Add the role to the no-rendered-text line as a more-explicit named offender.
3. As a last resort, revert the role line to the absent state (i.e. ship feature 017 (refined) again) and re-open issue #54. Do **not** ship a partial fix.

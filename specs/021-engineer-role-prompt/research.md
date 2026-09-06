# Phase 0 Research — 021 Add Engineer Role to the AI Image Generation Input Prompt

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Date**: 2026-05-11

## R1 — Where does the role currently flow, and what blocks it from the image prompt?

**Decision**: The engineering role (`AlterEgoRequest.archetype`) currently has **two** downstream consumers — the character-bio prompt (`GeminiCharacterPromptBuilder`, included since 014) and the downstream text overlay (`PosterTextOverlayService`, since 017). Both image prompt builders (`GeminiPromptBuilder` since 003, `FalAiPromptBuilder` since 016) deliberately **omit** the role. The omission was added in feature 017 (2026-05-08, see commit-history breadcrumbs in `GeminiPromptBuilder.appendCategoryLines` doc-comment and in `FalAiPromptBuilder.appendCategoryLines`) because the model rendered the role string as decorative banner text inside the character cutout — visible artefact captured in test docstring as *"CLOUD ARCHITECT LALO."*.

**Rationale (for the research-decision framing)**: This feature flips the role-exclusion half of that workaround. The first-name exclusion stays — that's a separate concern (first names are unbounded user input; we still don't want them rendered). The role label is closed-set (six values), human-readable, and meaningful as a visual cue (a cloud architect's scene vs. a frontend dev's scene differ in props/environment far more than they differ as written labels).

**Alternatives considered**:
- *Keep the role out of the image prompt; widen the text-overlay area to make the role more prominent.* Rejected — it does not address the spec's primary user value (the **image** should reflect the role). The whole point of issue #54 is that two users with the same Universe + Art Style currently get visually identical posters.
- *Pass the role only as a per-Archetype hard-coded scene paragraph (e.g. "Show server racks and holographic diagrams" for Cloud Architect, "Show data pipelines" for Data Engineer).* Rejected — six hand-tuned paragraphs are brittle, drift from the role label vocabulary already used in the bio prompt, and prevent the model from interpreting role + universe + art-style holistically. The closed-set role label paired with a clear *"render visual cues only, NOT as text"* directive defers the interpretation to the model where it belongs.
- *Inject the role via a separate API-side `imageStyleHint` parameter on the provider request.* Rejected — neither Gemini's `generateContent` nor fal.ai's `nano-banana-pro/edit` has a typed slot for "render this role"; the text prompt is the only channel.

## R2 — How do we re-introduce the role without re-triggering the banner-text bleed?

**Decision**: Phrase the role contribution as a **scene direction** inside a category line that explicitly repeats the no-text rule inline, and **reinforce** the composition-notes no-text instruction to list "role labels" alongside the existing offenders (banners, scrolls, name plates).

**Concrete wording — Gemini single variant** (proposed; see contract):

```text
- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): Cloud Architect
```

And the composition-notes line evolves from today's:

```text
- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos. The poster's text overlay is composited downstream — your job is the visual scene only.
```

to:

```text
- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the engineering-role label. The poster's text overlay is composited downstream — your job is the visual scene only.
```

The fal.ai variant adopts the same two changes verbatim (per 016 R11 the two builders share wording today but are kept structurally separate so they can diverge if a fal.ai-specific tweak surfaces).

**Rationale**: Two reinforcing channels — the inline "NOT as text" clarifier on the role line itself, and the explicit "NO transcribing the engineering-role label" in the no-rendered-text composition note. The inline clarifier rides directly alongside the role label so the model encounters the constraint at the same token-window as the label, which is where the prior bleed happened. The composition-note reinforcement is the belt-and-braces.

**Alternatives considered**:
- *Use the role label only as a non-textual hint, e.g. pass it lowercase and quoted to make it less "transcribable".* Rejected — opaque, fragile, and the model is perfectly capable of reading lowercase labels as text it should render. Explicit instruction beats syntactic obfuscation.
- *Add an example of forbidden output in the prompt ("DO NOT render text like 'CLOUD ARCHITECT LALO' on a banner").* Rejected — providing a negative example with the actual offending text risks the model latching onto it. The enumerated-offenders + explicit "NO transcribing the engineering-role label" is sufficient.
- *Substitute a paraphrase ("a person who designs distributed cloud systems") for the role label in the prompt.* Rejected — diverges from the bio prompt's vocabulary (FR-2102 requires the same label set as the bio for coherence) and shifts the burden onto a maintained paraphrase map that does not exist today.

## R3 — Should the role line replace one of the existing category lines, or be added as a sixth?

**Decision**: **Added as a new fourth line**, positioned **immediately after** the `Universe` line and **before** the `Art style` line — i.e. in the same position the bio prompt uses for the role (`- Engineering role:` is line #2 of the attribute block in `GeminiCharacterPromptBuilder`). The image builder's category block grows from four lines (Universe / Art style / Pose / optional Vibe) to five (Universe / Engineering role / Art style / Pose / optional Vibe).

**Rationale**: Placing the role line second matches the bio prompt's ordering, gives the model "who is this person" (universe + role) before "how is it rendered" (art style + pose). This also keeps the existing ordering constraint that `Art style` precedes `Pose` (pinned by `GeminiPromptBuilderTest.artStyleLinePrecedesPoseLineForComposition`) intact — `Art style` remains immediately before `Pose`.

**Alternatives considered**:
- *Append at the end of the block (after Vibe).* Rejected — burying the strongest user-chosen signal behind the optional Vibe weakens its weight in the model's attention window; the role is the **only** user-visible role-shaping category since 020 hid Pose and Vibe, so it should be front-loaded.
- *Replace the `Pose / stance` line.* Rejected — pose is server-rolled per request (020) and contributes a real composition cue; removing it would regress a feature unrelated to #54.

## R4 — Should the role flow through both image providers (Gemini + fal.ai) in this feature?

**Decision**: **Yes**, both. The change is purely textual — same role line and same strengthened composition-note line added to both builders. No HTTP / client / wire change.

**Rationale**: FR-2107 mandates parity across providers. The 016 feature established Gemini + fal.ai parallel paths and the existing `FalAiPromptBuilderTest.roleArchetypeIsNotPassedToImageGenerationAi` is the matching regression-lock to `GeminiPromptBuilderTest.archetypeRoleIsNotPassedToImageGenerationAi`. Updating only one would create asymmetric behaviour at runtime depending on which provider is active.

**Alternatives considered**:
- *Ship Gemini-first, fal.ai in a follow-up.* Rejected — the diff is the same size for both, the tests mirror each other, and the asymmetry would be a constitution-check yellow flag (Principle III "TDD across the surface") that a follow-up has to revisit anyway.

## R5 — What level of integration test coverage is required for this change?

**Decision**: The existing `GenerateAlterEgoGeminiIT` is extended with **one parametrised assertion** that, for each of the six `Archetype` values, captures the prompt actually sent to the mocked Gemini client and asserts the role label appears in it. No new integration-test file. The existing fal.ai equivalent (`GenerateAlterEgoFalAiIT` if present; otherwise the matching test in the same package) gets the same parametrised assertion. No live-provider integration test is added — the existing suite uses mocked clients per Principle III's `@SpringBootTest` posture without burning provider credits in CI.

**Rationale**: The change is at the prompt-composition layer, not the wire layer. Unit-test coverage of the builders (heavily expanded in this feature — six new assertion families) carries the load. The integration assertion is a thin "is the new line in the wire-bound prompt?" check that catches regressions where the builder is wired but the new line is missed.

**Alternatives considered**:
- *Add a live-provider smoke test gated by an `IT_PROVIDER_LIVE=1` env flag.* Deferred — useful for the manual SC-2101..SC-2104 validation pass but not as automated CI coverage. The quickstart documents the live-provider procedure.

## R6 — Does the role need to be NULL-safe in the image builder?

**Decision**: **No defensive null-check beyond what already exists.** `AlterEgoRequest.archetype` is `@NotNull` at the controller boundary (Bean Validation). The existing `label()` defensive default in the builder (fall back to wire form if a future enum value is added without a label) already covers the "enum exists but no label mapping" case. No additional guard at the builder layer is needed and adding one would conflict with the "trust internal code" rule in CLAUDE.md.

**Rationale**: The validation contract is established and tested. The role enum has six static values, each with a label, and the controller-boundary `@NotNull` constraint is enforced by Spring Boot's validator before any prompt builder runs. Adding a defensive branch here would be error-handling for a scenario that cannot happen.

**Alternatives considered**: none worth listing.

## Open NEEDS-CLARIFICATION items

**None.** All clarification was resolved at spec-write time; the spec carries zero `[NEEDS CLARIFICATION]` markers and the checklist passed on first iteration.

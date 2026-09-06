# Image-Prompt Contract — 021 Add Engineer Role to the AI Image Generation Input Prompt

**Feature**: [spec.md](../spec.md) · **Plan**: [../plan.md](../plan.md) · **Date**: 2026-05-11

This document pins the **shape** of the text prompt produced by both image prompt builders after this feature. It is the contract the new unit tests assert against. No HTTP / wire-level contract is changed; the `POST /api/alter-ego` request and response payloads are untouched.

## Scope

Two builders change shape:

- `com.aiavatar.alterego.service.gemini.GeminiPromptBuilder.build(AlterEgoRequest)` — produces the text prompt sent to Gemini's `generateContent` endpoint.
- `com.aiavatar.alterego.service.falai.FalAiPromptBuilder.build(AlterEgoRequest)` — produces the text prompt sent to fal.ai's `nano-banana-pro/edit` endpoint.

`GeminiCharacterPromptBuilder` (bio prompt) is **out of scope** — its prompt is unchanged.

## Gemini image prompt — full byte-for-byte fixture (SINGLE variant)

The `SINGLE` variant is regression-locked byte-for-byte by `GeminiPromptBuilderTest.explicitSinglePhotoModeProducesTodaysBaselinePrompt`. The new fixture string (replacing today's) is:

```text
Generate a cinematic portrait poster of the person in the reference photo.

The subject's appearance (face, hair, skin tone, approximate age, general build) MUST closely match the reference photo. Render the subject as an "alter ego" with the following attributes:

- Fictional universe / aesthetic: Star Wars
- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): Cloud Architect
- Art style: oil painting, visible brushstrokes and impasto texture
- Pose / stance: heroic, chest forward
- Vibe / tone: rebellious

Composition notes:
- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.
- Clear focus on the subject; the universe aesthetic is the setting, not the subject.
- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the engineering-role label. The poster's text overlay is composited downstream — your job is the visual scene only.
```

(Test fixture inputs: `Pose.HEROIC`, `Archetype.CLOUD_ARCHITECT`, `Universe.STAR_WARS`, `Vibe.REBEL`, `ArtStyle.OIL_PAINTING`, `firstName="Paula"`, `PhotoMode.SINGLE`.)

## Gemini image prompt — GROUP variant deltas

`GROUP` variant is **not** byte-for-byte locked (matches today's policy), but the following structural assertions hold:

- Opens with `"Generate a cinematic group portrait poster of the people in the reference photo."` — unchanged.
- The category block immediately after the opener follows the same five-line ordering as `SINGLE`, with `Engineering role (...)` as the **second** line.
- The strengthened no-rendered-text composition-notes line (`... NO transcribing the engineering-role label.`) appears verbatim.
- The "render EVERY person visible" and "Do NOT invent additional people" clauses are unchanged.
- No `Group name:` line, no `Name:` line, no first name in the prompt body — feature 017's first-name exclusion is preserved.

## fal.ai image prompt — structural assertions

`FalAiPromptBuilder` is not byte-for-byte locked (matches today's policy), but the following structural assertions hold for both `SINGLE` and `GROUP` variants:

- Opens with `"Edit the reference photo ..."` — unchanged (016 R3).
- The category block contains an `- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): <label>\n` line, positioned **after** the `Fictional universe / aesthetic:` line and **before** the `Art style:` line.
- The strengthened no-rendered-text composition-notes line (`... NO transcribing the engineering-role label.`) appears verbatim.
- For every `Archetype` value `a`, `prompt.contains(<prompt-label-for-a>)` is `true` (per data-model.md "Prompt label" column).
- For every pair of distinct `Archetype` values `(a, b)` with all other request fields held constant, `prompt(a) != prompt(b)` — locks SC-2103's automatable half.

## Category-line ordering (both builders, both variants)

After this feature, the attribute block grows from 4 lines (today) to **5 lines** (4 required + 1 optional Vibe):

1. `- Fictional universe / aesthetic: <Universe label>`
2. `- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): <Archetype label>`  ← **NEW**
3. `- Art style: <ArtStyle label>`
4. `- Pose / stance: <Pose label>`
5. `- Vibe / tone: <Vibe label>` (only when `request.vibe() != null`)

The pinned-by-test orderings from prior features are preserved:

- `artStyleLinePrecedesPoseLineForComposition` (GeminiPromptBuilder) — `Art style` precedes `Pose`. ✅
- 016 fal.ai mirrors the same ordering. ✅
- New ordering: `Universe` precedes `Engineering role`. (Pinned by a new test.)
- New ordering: `Engineering role` precedes `Art style`. (Pinned by a new test.)

## Negative-text rule (both builders, both variants)

The composition-notes line that forbids rendered text is **strengthened**, not replaced. Before:

```text
- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos. The poster's text overlay is composited downstream — your job is the visual scene only.
```

After:

```text
- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the engineering-role label. The poster's text overlay is composited downstream — your job is the visual scene only.
```

The full byte-for-byte SINGLE-variant fixture above is the authoritative source for the new wording.

## Things the prompt MUST still NOT contain

These prior regression-locks are preserved verbatim:

- `firstName` MUST NOT appear in the image prompt (17 (refined) — `firstNameIsNotPassedToImageGenerationAi`, `changingFirstNameNoLongerChangesPromptSinceFirstNameIsExcluded`). ✅ unchanged.
- `Name:` line MUST NOT appear. ✅ unchanged.
- `Group name:` line MUST NOT appear (017 (refined) — `groupPhotoModeUsesPluralWording`). ✅ unchanged.
- Universe wire values (e.g. `the-office`) MUST NOT appear — the human-readable Universe labels are used instead. ✅ unchanged.
- The Vibe line MUST NOT appear when `request.vibe() == null`. ✅ unchanged.

## What the prompt MUST now contain (new contract)

- For every `Archetype` value `a`, the prompt MUST contain the corresponding **Prompt label** from data-model.md (`Cloud Architect`, `Backend Developer`, `Frontend Developer`, `AI Engineer`, `Platform Engineer`, `Data Engineer`) — exactly one occurrence per request.
- The role line MUST appear on the **second** line of the attribute block (immediately after `Universe`).
- The role label MUST NOT be the raw wire form — for example, `backend-dev\n` must NOT appear; `Backend Developer\n` must appear instead.
- For every pair of distinct `Archetype` values `(a, b)` with all other request fields held constant, the resulting prompt strings MUST differ.

## Wire / HTTP contract

**Unchanged.** No new endpoint, no new header, no new request or response field. The `POST /api/alter-ego` controller and its `AlterEgoRequest` payload are byte-for-byte identical before and after this feature.

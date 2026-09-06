# Phase 1 Data Model — 021 Add Engineer Role to the AI Image Generation Input Prompt

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Date**: 2026-05-11

## Entities

This feature introduces **no new entities**. It re-uses the existing `Archetype` enum and changes the set of consumers that read it.

### `Archetype` (existing, unchanged)

Defined in `backend/src/main/java/com/aiavatar/alterego/model/Archetype.java`. Six values; wire vs. display-label split was established in 002.

| Wire value (`@JsonValue`) | UI label (`label()`) | Prompt label (this feature) |
|---|---|---|
| `cloud-architect` | Cloud Architect | Cloud Architect |
| `backend-dev` | Backend Dev | Backend Developer |
| `frontend-dev` | Frontend Dev | Frontend Developer |
| `ai-engineer` | AI Engineer | AI Engineer |
| `platform-eng` | Platform Eng. | Platform Engineer |
| `data-engineer` | Data Engineer | Data Engineer |

The **Prompt label** column mirrors `GeminiCharacterPromptBuilder.ROLE_LABELS` byte-for-byte (FR-2102 — bio and image must use the same label vocabulary). Notably the prompt label is **not** identical to the UI label for two values (`Backend Dev` → `Backend Developer`; `Platform Eng.` → `Platform Engineer`) — the prompt prefers the unabbreviated form because the bio prompt does, and consistency between bio and image is the load-bearing requirement.

The plain `Archetype.label()` accessor is intentionally **not** re-used as the prompt source — that method drives the UI grid and uses the abbreviated forms. Coupling the prompt label to `Archetype.label()` would silently introduce abbreviations into the prompt the first time the UI changes its grid wording.

### `AlterEgoRequest` (existing, unchanged)

Defined in `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`. The `archetype` field is already `@NotNull` and is already consumed by `GeminiCharacterPromptBuilder` and `PosterTextOverlayService`. This feature adds **two** additional consumers (`GeminiPromptBuilder`, `FalAiPromptBuilder`) without changing the record shape, validation, or any other field.

## Relationships — before and after

```text
                          AlterEgoRequest.archetype
                                  │
                  ┌───────────────┼───────────────┐
                  │               │               │
                  ▼               ▼               ▼
       GeminiCharacterPromptBuilder       PosterTextOverlayService    [BEFORE]
         (character bio prompt)             (poster text overlay)
                  │                               │
                  └────────── bio JSON ───────────┘

  ─────────────────────────  this feature  ─────────────────────────

                          AlterEgoRequest.archetype
                                  │
                  ┌────────┬──────┴──────┬─────────────┐
                  │        │             │             │
                  ▼        ▼             ▼             ▼
       GeminiCharacter   GeminiPrompt  FalAiPrompt   PosterText    [AFTER]
       PromptBuilder    Builder         Builder       Overlay
       (bio)            (Gemini image)  (fal.ai      Service
                                         image)      (text overlay)
                  │        │             │             │
                  ▼        ▼             ▼             ▼
              bio JSON  image          image       composited
                        bytes (G)      bytes (F)   poster bytes
```

## State / lifecycle

No new state. No state transitions. The `Archetype` value is read by all four consumers from the same `AlterEgoRequest` instance on the request thread; both image-builder reads happen at exactly one point per request (the `appendCategoryLines` helper in each builder).

## Validation rules

No new validation. The existing `@NotNull Archetype archetype` at the controller boundary continues to be the single validation point. Per Research §R6, no defensive null-check is added at the prompt-builder layer.

## Persistence

None. Extends 001 FR-016 / FR-017 / FR-024 unchanged — the role, the composed prompt string, and the provider response live only in process memory for the duration of one Generate request, and are never logged in structured form (existing `PhotoRedactionFilter` and structured-args hygiene from 003 / 016 are unchanged).

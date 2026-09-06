# Feature Specification: Add Engineer Role to the AI Image Generation Input Prompt

**Feature Branch**: `021-engineer-role-prompt`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "checkout github project issue #54"
**Source Issue**: [#54 — Add Engineer Role to the AI Image Generation Input Prompt](https://github.com/squer-solutions/aiavatar/issues/54)

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Chosen engineering role visibly shapes the cinematic poster (Priority: P1)

A user on the Setup tab picks their photo, enters their first name, and chooses an engineering role (for example **Cloud Architect**). They also pick a Universe and an Art Style, then press **Generate my alter ego**. The poster that appears on the Alter Ego tab visibly reflects the chosen role through props, attire, environment cues, or activity — not just the universe and art style. Switching only the role (everything else identical) produces a visibly different scene.

**Why this priority**: Picking the engineering role is the most distinctive choice the user makes — it's the part of the alter ego that's about *them*, not a generic universe wrapper. Today the role is collected, displayed in the poster's text overlay, and used in the generated character bio, but the **image itself ignores it**. Two users who pick the same Universe + Art Style get visually identical posters even if one picked *Cloud Architect* and the other *Data Engineer*. Closing that gap is the whole point of issue #54 and the single highest-value change in this feature.

**Independent Test**: Generate two posters with identical inputs except the engineering role. The two images must be visually distinguishable in their role-coded cues (e.g. props/environment/attire) while still rendering the same person, the same universe aesthetic, and the same art style. No text shall be rendered inside the image (existing no-text constraint, see Edge Cases).

**Acceptance Scenarios**:

1. **Given** a user with a valid photo, first name, and the role *Cloud Architect* in the *Cyberpunk* universe with *Oil Painting* art style, **When** they press Generate, **Then** the poster image shows visual cues evoking a cloud-architecture role (e.g. server racks, holographic network diagrams, distributed-systems imagery) styled as a cyberpunk oil painting — without any rendered text in the image.
2. **Given** the same photo, name, universe, art style, and pose as scenario 1, **When** the role is changed to *Data Engineer* and the user presses Generate again, **Then** the resulting poster image is visibly different in its role-coded cues (e.g. data pipelines, dashboards, query streams) while still being clearly the same person in the same universe and art style.
3. **Given** a user presses **Surprise Me**, **When** the randomizer picks an engineering role, **Then** the generated image is influenced by that randomly chosen role using the same mechanism as a manual role pick — there is no behavioural difference between user-chosen and randomizer-supplied roles for image generation.

---

### User Story 2 - Group photo: every face shares the same engineering role (Priority: P2)

A user uploads a group photo and picks the role *Backend Dev*. The generated poster renders **every** person visible in the photo as the same alter ego — Backend Dev — meaning role-coded cues apply uniformly to the group, not just to one subject. No person in the image is given a different role.

**Why this priority**: The 011-input-validation feature added group-photo handling, and the existing image prompt has an explicit "apply the same attributes uniformly to every person" instruction for groups. Extending that contract to the engineering role keeps group behaviour consistent with single-subject behaviour. Lower priority than P1 only because the single-subject path is the default and is exercised in every demo.

**Independent Test**: With a group photo and a single chosen role, every face in the generated image should be rendered in a way consistent with that role (e.g. all subjects in the same role-appropriate scene), and no subject should appear in a role different from the one selected.

**Acceptance Scenarios**:

1. **Given** a group photo with three faces and the role *AI Engineer* selected, **When** the user generates, **Then** all three subjects are rendered as AI Engineers — none of them is portrayed as a different role.

---

### Edge Cases

- **No-rendered-text guarantee must hold**: Today's image prompt deliberately excludes the engineering role to prevent the model from baking decorative banner text (e.g. *"CLOUD ARCHITECT LALO"* on a parchment scroll) inside the image — the role label is composited as a downstream text overlay. Re-introducing the role into the prompt MUST NOT regress this guarantee. The prompt must continue to forbid any rendered text inside the image (no name plates, banners, parchment scrolls, signs, captions, watermarks, or logos), and the role must be communicated to the model as a **visual/scene direction** (props, environment, attire, activity) rather than as a label that could be transcribed verbatim. The acceptance bar: across the random sample of generated images checked during validation (see Success Criteria), **zero** images contain rendered text inside the character cutout, banner, or scene.
- **Stub / fallback paths**: When the image provider is unavailable or returns an error, the existing FE-side fallback poster path continues to apply unchanged. The fallback poster is generated locally (not via the LLM prompt), so the engineering role does not need to be re-injected there — it is already part of the text overlay composited on the fallback. No regression in fallback behaviour is acceptable.
- **Character bio prompt is already role-aware**: The text-generation prompt (`GeminiCharacterPromptBuilder`) already includes the engineering role. This feature does NOT change the bio prompt — only the **image** prompts (the cinematic poster prompt for the configured image provider). The role label rendered in the character bio and the role's visual influence on the image must remain consistent (same enum value used by both prompts).
- **Pose and Vibe are hidden categories** (feature 020): they are rolled per-request server-side and continue to influence the image prompt as they do today. This feature does not change their behaviour. Engineering role joins them as a category the image prompt consumes, with the difference that the role is **user-visible / user-chooseable** (or Surprise-Me-supplied) rather than server-rolled.
- **Empty / missing role**: The Setup tab gates Generate on a role being chosen (existing behaviour from 002 / 006), so the image prompt builder will always receive a non-null role. The prompt builder MUST NOT silently omit the role line — if for some reason a request without a role reaches the builder, that is a contract violation upstream and the existing defensive-default behaviour for unknown enum values (fall back to the wire form so the prompt still includes *something*) is acceptable.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-2101**: The image-generation prompt sent to the configured image provider for every Generate request MUST include the user-selected (or Surprise-Me-rolled) engineering role as a visual/scene direction that the model can use to shape props, environment, attire, or activity in the rendered image.
- **FR-2102**: The role's contribution to the prompt MUST use a human-readable role label (e.g. *"Cloud Architect"*, *"Backend Developer"*) — not the raw wire enum value — so the model has natural-language grounding. The label vocabulary MUST match the labels the character-bio prompt already uses, so the bio and the image stay coherent for the same role pick.
- **FR-2103**: The image prompt MUST continue to forbid any rendered text inside the image. The instruction against rendered text MUST be at least as explicit as the existing one and SHOULD reinforce that the role label is a *direction*, not a string to display.
- **FR-2104**: The role MUST influence the image identically whether it was chosen manually on the Setup tab or supplied by the Surprise Me randomizer. There is no separate code path or different prompt content for the two sources.
- **FR-2105**: The role contribution to the prompt MUST apply uniformly to both the single-subject and the group-photo prompt variants. In the group variant, the role-coded scene direction MUST apply to every person rendered.
- **FR-2106**: This feature MUST NOT change which fields are collected, validated, or transported across the wire. The request payload, the `archetype` field, and the wire enum values stay exactly as they are today. Only the prompt composition on the backend changes.
- **FR-2107**: If the configured image provider changes (Gemini ↔ fal.ai, per existing 016-falai-image-provider switch), the engineering-role behaviour described in FR-2101..FR-2105 MUST hold for whichever provider is active. The text the model sees may differ in wording between providers, but the *guarantee* (role shapes the image; no rendered text) is identical.
- **FR-2108**: The character-bio (text) prompt is out of scope for this feature. The bio prompt continues to include the engineering role exactly as it does today; no behavioural change is permitted there.
- **FR-2109**: The FE-side fallback poster path (when the image provider fails) MUST continue to function exactly as today. No change to fallback rendering is permitted.
- **FR-2110**: No persistence is introduced. The role, like every other request field, lives only in process memory for the duration of one Generate request. This extends 001 FR-016 / FR-017 / FR-024 unchanged.

### Key Entities

- **Engineering Role** (existing): The user's chosen archetype from the six-option set (Cloud Architect, Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer). Already collected on the Setup tab, transported in the Generate request, used by the character-bio prompt and the downstream text overlay. This feature adds a second consumer (the image prompt) of the same value.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-2101**: For each of the six engineering roles, when everything else is held constant, the generated image contains visual cues that a human reviewer can identify as evoking that role at least 5 times out of 6 sample generations (a ≥83% identifiability rate per role).
- **SC-2102**: Across a random sample of at least 30 generated images spanning the six roles, **zero** images contain rendered text inside the character cutout, on banners, on parchment scrolls, on signs, or anywhere else in the scene — preserving today's no-rendered-text guarantee.
- **SC-2103**: For paired generations differing only in engineering role (everything else identical), a human reviewer can correctly identify which role each image depicts at least 80% of the time.
- **SC-2104**: Surprise-Me-generated posters and manually-chosen-role posters are indistinguishable in identifiability rate (within ±5 percentage points) — i.e. the role-source has no measurable effect on image quality.
- **SC-2105**: End-to-end Generate latency does not regress: median wall-clock time on the Generate path stays within ±10% of pre-feature baseline. (Adding a short role direction to the prompt should not materially affect provider response time.)
- **SC-2106**: No regression in fallback-poster path: failure-injection tests that today produce a fallback poster continue to produce one with the role rendered in its text overlay exactly as before.

## Assumptions

- The role's visual influence is delivered purely via prompt composition — no model-side fine-tuning, no embeddings, no provider-specific extension. The choice of *how* to phrase the role contribution (e.g. "Engineering role: Cloud Architect — render visual cues evoking this role through props/environment, NOT as rendered text") is an implementation detail left to the plan, constrained by FR-2103.
- The risk of decorative banner text returning is real but mitigatable through prompt wording (explicit no-text instruction, framing the role as a *scene direction* rather than a *label*). If experimentation during implementation shows the model still bakes the role string into the image despite the strengthened instruction, the plan must address that with stronger phrasing rather than reverting to the role-exclusion approach — the whole point of this feature is to make the role influence the image.
- The six current roles (Cloud Architect, Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer) are stable for this feature. Adding/removing roles is out of scope; the label vocabulary already exists in `GeminiCharacterPromptBuilder` and is reused.
- Both image-provider backends (Gemini, fal.ai) need parallel updates. Whichever provider is active at runtime is selected by existing configuration (016). This feature does not change the provider-selection mechanism.
- Validation against the no-text constraint (SC-2102) is performed by human review of generated samples — no automated OCR check is introduced. If the human-review sample reveals text bleed, that's a blocker, not a minor regression.
- The Setup-tab gating (Generate requires a role to be chosen) means the prompt builder will always receive a non-null role. No new validation is required at the prompt-builder layer beyond today's defensive default.

# Post-execution log — Verbund rebrand, Phase 2: content & theme

**Feature Branch**: `029-verbund-rebrand`
**Created**: 2026-09-06
**Status**: Implemented (documented post-execution)
**Relation to Phase 1**: The original `spec.md` + `post-execution-log.md` cover the **visual re-skin** (palette, logos, poster frame, title/font). This log covers a **second phase**: re-theming the *generated content* (image prompts, bios, and the selectable Role / Universe options) so the output — not just the chrome — fits the "AI @ Verbund 2026" audience.

**Input** (user description, verbatim):
> Now that the overall styling has been adapted, lets work on the theme. The app was created for code/crafts which is a developer style conference. The "AI @ Verbund 2026" is an event focused on the energy transition within Austria and Europe. So the theme of the generated images and texts should be overall hopeful, innovative and positive.
> - Adapt the generation prompts for the texts and the images to be more focused on that instead of being overly tech-focused.
> - Change the pre-existing role-options to be overall corporate fitting, with the various options that can be in them (software developer can still be one of them).
> - Change the pre-existing "universe / style" options to be less nerd-only but be general pop culture in the 80s, 90s and 20nds. "Marvel" and "Star Wars" can stay. Also add a "Or enter your own universe" input option for this category.

> NOTE: Executed directly (not via the full speckit specify→plan→tasks flow). This log records intent, decisions, and the as-built outcome so the change is traceable alongside features 001–028 and Phase 1 of 029.

## Clarifications (Session 2026-09-06)

Decisions confirmed with the user before implementation:

- **Q: Replace the engineering-heavy roles, or keep them and add corporate ones?** → **A: Broaden to a corporate mix.** Replace the nine dev/ops archetypes with a general corporate set; keep "Software Developer" as one option.
- **Q: Which pop-culture universe set?** → **A: A balanced, broadly recognisable ~6-item list spanning the 80s/90s/2000s, keeping Marvel & Star Wars**, plus a free-form "enter your own" input.
- **Q: How thorough should the custom-universe input be?** → **A: A full mirror of the existing custom-Role feature** — reducer state, wire field, backend DTO field, class-level validator, and prompt-builder resolution, with the same "custom clears prefab" precedence.

## Scope

- **Behavioural theme change**, not a new user flow. Generation pipeline, tab shell, no-persistence posture, HTTP endpoints, and correlation-id handling are unchanged.
- The public wire contract **does** change (new enum values; `universe` becomes optional; new optional `customUniverse` field) — acceptable because this is a pre-release POC with **no persisted data** to migrate (inherits 001 FR-016/017/024). No back-compat `fromWire` aliases were added; the old wire strings now legitimately throw `IllegalArgumentException`.

## What was built

### 1. Generation prompt tone (image + text)

Three builders, all under `backend/.../infrastructure/provider/`:

| Builder | Change |
|---|---|
| `gemini/GeminiPromptBuilder` (image) | Opening → "Generate an uplifting, cinematic portrait poster … as an inspiring alter ego for a hopeful, innovation-driven future." Added an **"Overall mood: hopeful, positive, energetic and forward-looking …"** composition note. Background note now steers toward renewable-energy optimism (open sky, sunlight, greenery, clean-energy motifs). Category line relabelled **"Engineering role" → "Professional role"** ("render as uplifting visual cues …"). "engineering-role label" → "role label". |
| `falai/FalAiPromptBuilder` (image) | Same tone/label/mood changes, keeping the "Edit the reference photo …" verb. |
| `gemini/GeminiCharacterPromptBuilder` (bio) | Role line "Engineering role:" → "Professional role:". Voice changed from *"dry, knowing, slightly self-deprecating engineering humour"* → *"warm, hopeful, inspiring and lightly witty — the energy of someone helping build a brighter, more sustainable future through innovation and clean energy. Upbeat and empowering, never cynical or overly technical."* Quote instruction → "a short uplifting, forward-looking quote". Hero-title examples "The Cloud Guardrail"/"The Pixel Diplomat" → "The Future Builder"/"The Bright-Spark Visionary". |

The three builders keep their **forked** label maps (016 R11 "fork rather than share").

### 2. Role options — `Archetype` enum (broad corporate mix)

Replaced the nine dev/ops values. New set (wire → UI label):

| Wire | UI label | Prompt-map label |
|---|---|---|
| `software-developer` | Software Developer | Software Developer |
| `project-manager` | Project Manager | Project Manager |
| `data-analyst` | Data Analyst | Data Analyst |
| `marketing-specialist` | Marketing Specialist | Marketing & Communications Specialist |
| `sales-customer-relations` | Sales & Customer Relations | Sales & Customer Relations |
| `people-culture` | People & Culture | People & Culture (HR) |
| `operations-manager` | Operations Manager | Operations Manager |
| `finance-controller` | Finance Controller | Finance & Controlling |
| `sustainability-lead` | Sustainability Lead | Sustainability & Energy-Transition Lead |

`backend/src/main/resources/stubs/characters.json` was rewritten: nine new keys, each with four variants rebranded to hopeful / energy-transition content (old titles like "The Cloud Guardrail" are gone).

### 3. Universe options — `Universe` enum (broad pop culture)

Kept `marvel` + `star-wars`; replaced the rest. New set (wire → UI label → image-prompt label):

| Wire | UI label | Image-prompt label |
|---|---|---|
| `marvel` | Marvel | Marvel superhero universe |
| `star-wars` | Star Wars | Star Wars |
| `retro-synthwave` | 80s Retro / Synthwave | 1980s retro synthwave, neon sunset and chrome |
| `nineties-sitcom` | 90s Sitcom | warm, bright 1990s sitcom set |
| `spy-thriller` | Spy Thriller | sleek 1960s-style spy thriller, adventurous and glamorous |
| `ghostbusters` | Ghostbusters | playful 1980s Ghostbusters adventure |

(The character/bio builder uses shorter forms, e.g. "1990s sitcom", "classic spy thriller".)

### 4. Custom Universe — full mirror of custom Role (022)

New free-form **"Or enter your own universe"** input with the same mechanics as custom Role:

- **Frontend**: `AlterEgoSession.customUniverse: string` (initial `''`); `CustomUniverseChanged` reducer action with the precedence rule (non-blank trimmed value clears the prefab `universe`); `CustomUniverseInput` component; `UniverseGrid` gains a `disabled` prop; `SetupLayout` blurs the grid when custom is active; `serialiseSelections` omits `universe` when null and includes `customUniverse` when non-blank; `missingInputs` gate satisfied by prefab **OR** custom; `mergeSurpriseWithExplicit` treats a non-blank custom universe as an explicit choice; both submit paths (`AlterEgoPage.handleSubmit`, `useGenerateAlterEgo.surprise`) thread the field.
- **Backend**: `AlterEgoUserSelections` and `AlterEgoRequest` gain a trailing `@Size(max=100) String customUniverse`; `universe` loses `@NotNull`; both records carry the new class-level `@UniverseOfRecordPresent` constraint (mirrors `@RoleOfRecordPresent`); new `UniverseOfRecordPresentValidator`; new `domain/prompt/UniverseOfRecord` helper; each prompt builder resolves the universe via a `resolveUniverseLabel` that honours custom precedence. Convenience constructors preserve the pre-029 call shapes.

**Invariant**: `universe != null OR customUniverse` non-blank — enforced at the boundary → RFC 7807 400 on violation.

### 5. Incidental hardening

- `AccentResolver.deriveAccent` is now null-safe on **both** dimensions (custom role → null archetype; custom universe → null universe), with curated-map defaults `SOFTWARE_DEVELOPER` / `MARVEL`. Curated cells re-keyed to the new enums.
- `StubCharacterGenerator` no longer NPEs on null archetype/universe: it defaults the variant lookup to `SOFTWARE_DEVELOPER` and folds the custom strings into the deterministic hash key. (Latent bug reachable since 022's custom-role path when the stub/fallback generator ran.)
- `RoleOfRecord`'s defensive default changed "Engineer" → "Innovator".

## Files changed

- **Backend main** (11): `Archetype`, `Universe`, `AlterEgoRequest`, `AlterEgoUserSelections`, `AlterEgoUseCase`, `AccentResolver`, `StubCharacterGenerator`, `GeminiPromptBuilder`, `GeminiCharacterPromptBuilder`, `FalAiPromptBuilder`, `stubs/characters.json`.
- **Backend new** (3): `validation/UniverseOfRecordPresent`, `validation/UniverseOfRecordPresentValidator`, `prompt/UniverseOfRecord`.
- **Frontend prod** (7): `types.ts`, `options.ts`, `state/reducer.ts`, `state/selectors.ts`, `services/alterEgoClient.ts`, `hooks/useGenerateAlterEgo.ts`, `lib/mergeSurpriseWithExplicit.ts`, `AlterEgoPage.tsx`, `components/{SetupLayout,UniverseGrid}.tsx`.
- **Frontend new** (1): `components/CustomUniverseInput.tsx`.
- **Tests**: backend all tiers + frontend Vitest suites updated to new enum values, prompt wording, and the `customUniverse` field.

## Verification

| Check | Result |
|---|---|
| `cd backend && ./gradlew test` (unit / service / contract / integration / archTest) | ✅ BUILD SUCCESSFUL |
| `cd frontend && npx vitest run` | ✅ 602 passed (40 files) |
| `cd frontend && npx tsc --noEmit` | ✅ clean |
| `cd frontend && npm run lint` | ✅ ESLint clean (2 pre-existing Prettier warnings in untouched files: `EmailInput.tsx`, `tokens.css`) |
| Backend main compile | ✅ `./gradlew compileJava` clean |

No production bug was surfaced by the test sweep — every failing test was an outdated expectation (enum literal, prompt substring, or a full-state literal missing `customUniverse`). Two genuinely-broken pre-existing test setups were fixed in passing (a `Map.ofEntries` with duplicate keys; delta tests that used the same archetype for both A/B and so couldn't produce distinct prompts).

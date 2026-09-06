# Data Model: Group Photos (011)

## New Enum: `PhotoMode`

**Frontend (`types.ts`)**
```ts
export type PhotoMode = 'single' | 'group'
```

**Backend (`model/PhotoMode.java`)**
```java
public enum PhotoMode {
    @JsonProperty("single") SINGLE,
    @JsonProperty("group")  GROUP
}
```

Wire values: `"single"` (default) / `"group"`. Pinned from first merge; renaming is a breaking wire change.

## Session State Delta

`AlterEgoSession` gains ONE required field:

| Field | Type | Initial | Notes |
|---|---|---|---|
| `photoMode` | `PhotoMode` | `'single'` | Required, never `null`. Reset by `StartOverRequested`. |

No other fields change. `generateAutoSwitchNonce`, `phase`, `photoBlob`, category fields, `firstName`, `result`, `errorMessage` etc. are all unaffected.

## New Action: `PhotoModeSelected`

```ts
| { type: 'PhotoModeSelected'; photoMode: PhotoMode }
```

**Reducer branch**:
```ts
case 'PhotoModeSelected':
  return { ...state, photoMode: action.photoMode, phase: 'picking' }
```

Matches the shape of `PoseSelected` / `ArchetypeSelected` / `UniverseSelected` / `ArtStyleSelected` exactly.

## Required-Input Set Delta

No change. `photoMode` is always set, so it never enters `missingInputs` or `missingInputsForSurprise`.

## Wire Contract Delta

`Selections` (frontend `types.ts` + backend `AlterEgoRequest`) gains one field:

```diff
  export interface Selections {
    pose: Pose
    archetype: Archetype
    universe: Universe
    vibe?: Vibe
    artStyle: ArtStyle
    firstName: string
+   photoMode: PhotoMode
  }
```

**Backend-side nullability**: the record component `PhotoMode photoMode` has NO `@NotNull` annotation. The domain accessor `effectivePhotoMode()` returns `photoMode != null ? photoMode : PhotoMode.SINGLE` so downstream code never has to deal with the nullable variant.

## Invariants

- **I-1**: For every `AlterEgoSession s`, `s.photoMode` is exactly one of `'single'` / `'group'`.
- **I-2**: `initialAlterEgoSession().photoMode === 'single'`.
- **I-3**: `alterEgoReducer(s, { type: 'StartOverRequested' }).photoMode === 'single'` for every `s`.
- **I-4**: `alterEgoReducer(s, { type: 'PhotoModeSelected', photoMode: m }).photoMode === m` for every `s` and `m`.
- **I-5**: No reducer branch other than the two above mutates `photoMode`.
- **I-6**: `missingInputs(s)` and `missingInputsForSurprise(s)` NEVER include `'photoMode'`.

## Frontend Option List

`options.ts` adds:

```ts
export const PHOTO_MODE_OPTIONS: ReadonlyArray<EnumOption<PhotoMode>> = [
  { value: 'single', label: 'Single Person', icon: User },
  { value: 'group',  label: 'Group Photo',   icon: Users },
]
```

`User` and `Users` are lucide-react icons. Verify availability on the pinned version before coding.

## Backend Prompt Builder Delta

`GeminiPromptBuilder.build(AlterEgoRequest)` branches on `request.effectivePhotoMode()`:

### SINGLE variant (baseline, unchanged)

```
Generate a cinematic portrait poster of the person in the reference photo.

The subject's appearance (face, hair, skin tone, approximate age, general build)
MUST closely match the reference photo. Render the subject as an "alter ego"
with the following attributes:

- Name: {firstName}
- Engineering role: {role}
- Fictional universe / aesthetic: {universe}
- Art style: {artStyle}
- Pose / stance: {pose}
- Vibe / tone: {vibe}

Composition notes:
- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.
- Clear focus on the subject; the universe aesthetic is the setting, not the subject.
- No overlaid text, logos, or watermarks — text will be composited downstream.
```

### GROUP variant (new)

```
Generate a cinematic group portrait poster of the people in the reference photo.

Render EVERY person visible in the reference photo as the same alter ego.
Each person's appearance (face, hair, skin tone, approximate age, general build)
MUST closely match their own face in the reference photo. Do NOT invent
additional people who are not in the reference photo. Apply the same attributes
uniformly to every person:

- Group name: {firstName}
- Engineering role: {role}
- Fictional universe / aesthetic: {universe}
- Art style: {artStyle}
- Pose / stance: {pose}
- Vibe / tone: {vibe}

Composition notes:
- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.
- Clear focus on all subjects as a group; the universe aesthetic is the setting, not the subjects.
- Arrange the group so every face is clearly visible.
- No overlaid text, logos, or watermarks — text will be composited downstream.
```

Only the opening sentence, the "subject/subjects" plurality, the Group-name vs. Name label, and two extra composition notes differ. The Vibe branch (`if vibe != null`) is unchanged in both variants.

## Glossary

- **photoMode** — the session field holding the currently-selected composition mode.
- **PhotoMode** — the frontend TS union + the backend Java enum.
- **Single mode** — value `'single'` / `SINGLE`. One subject in the reference photo, one alter ego in the output.
- **Group mode** — value `'group'` / `GROUP`. Multiple subjects in the reference photo, each rendered as the same alter-ego archetype in the output.
- **Composition choice** — an umbrella term for session fields that affect how the image is framed (pose, art style, photoMode) as opposed to theme fields (archetype, universe, vibe). Not a code term; used in the spec only.

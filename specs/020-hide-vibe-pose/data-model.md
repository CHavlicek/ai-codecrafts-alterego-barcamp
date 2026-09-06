# Data Model — 020 Hide Vibe and Pose Categories from UI

Phase 1 output. Captures the **shape changes**, not the values; the values (option lists) are preserved verbatim per FR-2040.

## Frontend

### `AlterEgoSession` (file `frontend/src/features/alterego/state/reducer.ts`)

| Field | Before | After | Note |
|---|---|---|---|
| `activeTab` | `ActiveTab` | `ActiveTab` | unchanged |
| `generateAutoSwitchNonce` | `number` | `number` | unchanged |
| `photoBlob` | `Blob \| null` | `Blob \| null` | unchanged |
| `photoPreviewUrl` | `string \| null` | `string \| null` | unchanged |
| `pose` | `Pose \| null` | **removed** | FR-2001 |
| `archetype` | `Archetype \| null` | `Archetype \| null` | unchanged |
| `universe` | `Universe \| null` | `Universe \| null` | unchanged |
| `vibe` | `Vibe \| null` | **removed** | FR-2002 |
| `artStyle` | `ArtStyle \| null` | `ArtStyle \| null` | unchanged |
| `photoMode` | `PhotoMode` | `PhotoMode` | unchanged |
| `firstName` | `string` | `string` | unchanged |
| `phase` | `SessionPhase` | `SessionPhase` | unchanged |
| `errorMessage` | `string \| null` | `string \| null` | unchanged |
| `result` | `AlterEgoResponse \| null` | `AlterEgoResponse \| null` | unchanged |

### `AlterEgoAction` (same file)

Removed variants:

- `{ type: 'PoseSelected'; pose: Pose }`
- `{ type: 'VibeSelected'; vibe: Vibe }`

Modified variants:

- `{ type: 'SurpriseMePicked'; picks: SurpriseMePicks }` — payload shape changes (see `SurpriseMePicks` below).

All other actions (`PhotoSelected`, `PhotoCleared`, `ArchetypeSelected`, `UniverseSelected`, `ArtStyleSelected`, `PhotoModeSelected`, `FirstNameChanged`, `GenerateSubmitted`, …) are unchanged.

### `RequiredInput` & `missingInputs` (`frontend/src/features/alterego/state/selectors.ts`)

```diff
- export type RequiredInput = 'photo' | 'pose' | 'archetype' | 'universe' | 'artStyle' | 'firstName'
+ export type RequiredInput = 'photo' | 'archetype' | 'universe' | 'artStyle' | 'firstName'
```

`missingInputs` drops the `if (!state.pose) missing.push('pose')` branch. `isReadyToGenerate` is unchanged in spirit (it returns `missingInputs(state).length === 0`). `isReadyToSurprise` is unchanged.

### `Selections` (`frontend/src/features/alterego/types.ts`)

```diff
 export interface Selections {
-  pose: Pose
   archetype: Archetype
   universe: Universe
-  vibe?: Vibe
   artStyle: ArtStyle
   photoMode: PhotoMode
   firstName: string
 }
```

The `Pose` and `Vibe` type aliases are deleted from the same file. The `POSE_OPTIONS` and `VIBE_OPTIONS` arrays are deleted from `frontend/src/features/alterego/options.ts`, along with their icon imports and the `pose` / `vibe` entries in `ACCENT_VARS`.

### `SurpriseMePicks` (`frontend/src/features/alterego/lib/randomSelections.ts`)

```diff
 export interface SurpriseMePicks {
-  pose: Pose
   archetype: Archetype
   universe: Universe
-  vibe: Vibe
   artStyle: ArtStyle
 }
```

`randomSelections()` now picks only from `ARCHETYPE_OPTIONS`, `UNIVERSE_OPTIONS`, `ART_STYLE_OPTIONS`.

## Backend

### New: `AlterEgoUserSelections` (file `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoUserSelections.java`)

Public-facing DTO bound from the JSON `selections` part of the multipart request.

```java
public record AlterEgoUserSelections(
    @NotNull Archetype archetype,
    @NotNull Universe universe,
    @NotNull ArtStyle artStyle,
    @NotBlank @Size(max = 50) @ValidFirstName String firstName,
    PhotoMode photoMode
) {}
```

Notes:

- **No `Pose`, no `Vibe`** — FR-2001 / FR-2002 / FR-2025. If the client sends them, Jackson silently ignores the unknown keys.
- `photoMode` remains nullable at the Bean-Validation layer (matches existing 011 behaviour).
- All other validation annotations come straight from the previous `AlterEgoRequest`.

### `AlterEgoRequest` (file `backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`)

Field list **unchanged** (Pose, Archetype, Universe, Vibe, ArtStyle, firstName, PhotoMode). The record stays in place; its **role** changes:

- Before: client-facing DTO, validated by `@Valid` on the controller.
- After: server-internal "resolved request", constructed by `AlterEgoService` after rolling Pose + Vibe.

Bean-validation annotations on its fields become inert (the record is never `@Valid`-validated again — validation happens on `AlterEgoUserSelections`). For clarity, they may stay or be removed; keeping them is the lower-churn choice. **Decision: keep them** as belt-and-braces — if a future caller accidentally builds an `AlterEgoRequest` with a null Pose, downstream NPE is louder than silent.

### New: `RandomCategorySelector` (file `backend/src/main/java/com/aiavatar/alterego/service/random/RandomCategorySelector.java`)

```java
@Component
public class RandomCategorySelector {
    private final RandomGenerator rng;

    public RandomCategorySelector() {
        this(RandomGenerator.getDefault());
    }

    RandomCategorySelector(RandomGenerator rng) {  // package-private, for tests
        this.rng = Objects.requireNonNull(rng);
    }

    public <E extends Enum<E>> E pickUniform(Class<E> clazz) {
        E[] values = clazz.getEnumConstants();
        if (values == null || values.length == 0) {
            throw new IllegalStateException("No enum constants for " + clazz.getName());
        }
        return values[rng.nextInt(values.length)];
    }
}
```

- Single responsibility: pick uniformly across an enum's full constant set.
- The injectable constructor seam (package-private) is the only test surface needed.
- No state outside the `rng` field. Thread-safety follows the chosen `RandomGenerator` — `getDefault()` returns a `L32X64MixRandom` which is not thread-safe, but the Spring bean is a singleton consumed from a request thread; concurrent requests share it. **Decision: use `ThreadLocalRandom.current()` inside `pickUniform` for thread-safety** instead of the constructor-injected `rng` — or wrap an `AtomicReference`-style guard. Simpler: keep the constructor-injectable `rng` for **deterministic test use** only, and in production rely on `RandomGenerator.getDefault()` being good enough for non-concurrent kiosk use.

Resolved: kiosk POC is single-user; concurrent requests on the same JVM are exceptional. We accept the minor "non-thread-safe `RandomGenerator`" risk for the simplicity of one injectable seam. Production uniformity is not measurably affected by occasional thread contention.

### `AlterEgoService` (file `backend/src/main/java/com/aiavatar/alterego/service/AlterEgoService.java`)

Method signature change at the entry point called by `AlterEgoController`:

```diff
- public AlterEgoResponse generate(MultipartFile photo, AlterEgoRequest selections, ...)
+ public AlterEgoResponse generate(MultipartFile photo, AlterEgoUserSelections userSelections, ...)
```

Body, at the top:

```java
Pose pose = randomCategorySelector.pickUniform(Pose.class);
Vibe vibe = randomCategorySelector.pickUniform(Vibe.class);
AlterEgoRequest resolved = new AlterEgoRequest(
    pose,
    userSelections.archetype(),
    userSelections.universe(),
    vibe,
    userSelections.artStyle(),
    userSelections.firstName(),
    userSelections.photoMode()
);
// ... existing pipeline consumes `resolved` exactly as it consumed the old `selections` ...
```

The rest of the service body (prompt building, image-provider call, fallback wiring, overlay composition) is **unchanged** — it continues to consume `AlterEgoRequest`.

### Prompt builders (3 files: `GeminiPromptBuilder.java`, `GeminiCharacterPromptBuilder.java`, `FalAiPromptBuilder.java`)

**No change.** Their input type, label maps, and prompt-string assembly are preserved (FR-2040 + FR-2041).

## State transitions

- Generate path: `AlterEgoUserSelections` (deserialized from JSON) → `AlterEgoService.generate` → `RandomCategorySelector.pickUniform(Pose.class)` × `pickUniform(Vibe.class)` → `AlterEgoRequest` (resolved) → existing pipeline unchanged.
- Surprise Me path: identical to Generate path. The frontend `randomSelections()` only rolls the **visible** categories now (Archetype, Universe, ArtStyle), commits them via `SurpriseMePicked`, then issues the same Generate request. The server independently rolls Pose + Vibe.
- Start over: visible session state resets to its initial shape, which no longer contains `pose` or `vibe`. Next Generate rolls fresh values server-side.

## What is preserved (the "no-change" contract)

- Pose options: `HEROIC`, `STEALTHY`, `MYSTICAL`, `SCHOLAR` — same wire values, same labels, same prompt-string fragments.
- Vibe options: `BUILDER`, `THINKER`, `REBEL`, `ARCHITECT` — same wire values, same labels, same prompt-string fragments.
- Printed artefact: rendered from `AlterEgoResponse` only; never contained Pose / Vibe; unchanged.
- HTTP response shape from `POST /api/v1/alter-egos`: unchanged.
- Multipart contract (the `photo` part): unchanged.

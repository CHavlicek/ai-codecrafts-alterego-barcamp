# Data Model: New Options for the Role Category

**Feature**: 022-role-options-custom · **Phase**: 1 · **Date**: 2026-05-11

This document captures the entities, fields, validation rules, derived values, and state transitions touched by this feature. The model is deliberately small — three new enum values, one new optional string field, one new reducer action, and one derived helper.

---

## Frontend entities

### `Archetype` (extended)

Type union in `frontend/src/features/alterego/types.ts`. Six values today; **nine** values after this feature.

```ts
export type Archetype =
  | 'cloud-architect'
  | 'backend-dev'
  | 'frontend-dev'
  | 'ai-engineer'
  | 'platform-eng'
  | 'data-engineer'
  | 'hr'                 // NEW
  | 'administration'     // NEW
  | 'customer-relations' // NEW
```

**Wire values**: kebab-case, public-contract; existing six are immutable; three new values follow the same convention. Renaming any value is a breaking change.

**Display labels** (in `options.ts → ARCHETYPE_OPTIONS`): `HR`, `Administration`, `Customer Relations`. Same casing/punctuation convention as existing six.

**Icons** (lucide-react): `Users`, `ClipboardList`, `MessageCircle` (see research.md R10).

**Stable order**: Cloud Architect, Backend Dev, Frontend Dev, AI Engineer, Platform Eng., Data Engineer, HR, Administration, Customer Relations. Surprise Me draws uniformly from this nine-element list (FR-2202).

---

### `Selections` (extended)

Interface in `frontend/src/features/alterego/types.ts`. Existing fields unchanged; one optional field added.

```ts
export interface Selections {
  archetype: Archetype | null   // CHANGED: was `Archetype`, now nullable when customRole takes precedence
  universe: Universe
  artStyle: ArtStyle
  photoMode: PhotoMode
  firstName: string
  customRole?: string           // NEW — optional, sent only when trimmed value non-empty
}
```

**Validation** (client-side, before send):
- `customRole` MUST be ≤ 100 characters after `trim()`. UI's `maxLength={100}` enforces this at the input level; the API client `trim()`s and re-checks before serialising.
- When `customRole.trim().length === 0`, the field MUST be **omitted from the JSON body** (not sent as `""`) — keeps the wire surface minimal and prevents `customRole: ""` from triggering edge cases on older deployments.
- Exactly one of `archetype` or `customRole` (trimmed non-empty) MUST be present in any payload sent to the wire. Both being present is a UI bug (the reducer prevents it) and would be rejected by the backend's class-level validator anyway.

---

### `AlterEgoSession` (extended)

State machine in `frontend/src/features/alterego/state/reducer.ts`. One new field, one new action, behaviour changes on two existing actions.

```ts
export interface AlterEgoSession {
  // ... existing fields ...
  archetype: Archetype | null         // unchanged shape; new clearing rule (see CustomRoleChanged below)
  customRole: string                  // NEW — single-line free-form input value; "" when not used; never null
}

export type AlterEgoAction =
  | ...
  | { type: 'CustomRoleChanged'; customRole: string }   // NEW
  // existing actions unchanged in shape; behaviour changes in handlers
```

**Initial state**: `customRole: ''` (empty string, not null).

**Reducer transitions touched**:

| Incoming action | Field changes |
|---|---|
| `CustomRoleChanged { customRole: raw }` where `raw.trim()` is non-empty | `state.customRole = raw`; `state.archetype = null`; `state.phase = 'picking'` |
| `CustomRoleChanged { customRole: raw }` where `raw.trim()` is empty | `state.customRole = raw`; `state.archetype` unchanged; `state.phase = 'picking'` (allows the user to type a space then a real char without thrashing) |
| `ArchetypeSelected { archetype: a }` | unchanged: `state.archetype = a`; `state.phase = 'picking'`. (The UI prevents this from firing while `customRole.trim()` is non-empty; defense-in-depth comment in the reducer notes this. Custom is **not** cleared here — see R6.) |
| `SurpriseMePicked { picks }` | existing overwrites + **`state.customRole = ''`** (FR-2209). |
| `StartOverRequested` | unchanged: `initialAlterEgoSession()` returns `customRole: ''` for free. |

---

### Derived values / selectors

In `frontend/src/features/alterego/state/selectors.ts`:

```ts
/** Trimmed custom role string. Returns "" when the input is blank or whitespace-only. */
export function customRoleOfRecord(state: AlterEgoSession): string {
  return state.customRole.trim()
}

/** Updated missingInputs: archetype satisfied by EITHER archetype !== null OR customRoleOfRecord(state) !== ''. */
export function missingInputs(state: AlterEgoSession): RequiredInput[] {
  const missing: RequiredInput[] = []
  if (!state.photoBlob) missing.push('photo')
  if (!state.archetype && customRoleOfRecord(state) === '') missing.push('archetype')
  if (!state.universe) missing.push('universe')
  if (!state.artStyle) missing.push('artStyle')
  if (!validateFirstName(state.firstName).ok) missing.push('firstName')
  return missing
}
```

`isReadyToGenerate(state)` continues to read `missingInputs(state).length === 0`.

`missingInputsForSurprise(state)` is unchanged — Surprise Me has never required the archetype.

`tabDisabled(tab, phase)` is unchanged — phase semantics are untouched.

---

### `CustomRoleInput` component (new)

`frontend/src/features/alterego/components/CustomRoleInput.tsx`.

```tsx
interface Props {
  value: string                          // controlled — state.customRole
  onChange: (next: string) => void       // dispatches CustomRoleChanged
  disabled?: boolean                     // wired to (phase === 'generating') so the user can't edit mid-request
  maxLength?: number                     // default 100
}
```

**Render**:
- Wrapper `<div class="custom-role-input">` containing:
  - `<label>` "Or enter your own role" (or similar; final copy is a UI choice, not a contract).
  - `<input type="text">` with `maxLength={100}`, `aria-label="Custom role"`, `value={value}`, `onChange={(e) => onChange(e.target.value)}`.
  - Trailing `<button type="button" class="custom-role-input__clear">` rendered only when `value !== ''`; carries the imported `X` icon (already re-exported at `options.ts:113`); `aria-label="Clear custom role"`; on click → `onChange('')`.

**Keyboard**:
- Tab into the input → editing begins.
- Tab from the input → focus moves to the `X` button when visible, otherwise to the next form control.
- Enter inside the input is consumed by the form's submit handler only if Generate-gating allows — same as today's `FirstNameInput`. The custom-role input itself does not submit on Enter (matches existing input UX).
- Space / Enter on the focused `X` button → `onChange('')`.

**Visual / a11y**:
- Standard text-input styling, single-line.
- The `X` clear-button is visually trailing-inside-input (typical clear pattern); on smaller viewports it remains tap-target-sized (≥ 24 × 24 px).

---

### `SelectionGrid` (extended)

`frontend/src/features/alterego/components/SelectionGrid.tsx` — gains a new prop:

```ts
interface SelectionGridProps<T extends string> {
  // ...
  disabled?: boolean        // NEW — when true the whole group is keyboard-inert + blurred
}
```

When `disabled === true`:
- The wrapping `<div role="radiogroup">` (or `role="group"` for deselect-capable) gains `aria-disabled="true"`.
- Every option button gets `aria-disabled="true"` and `tabIndex={-1}` regardless of selected-state.
- The fieldset gains the class `is-disabled` (CSS: blur, opacity, pointer-events: none, cursor: not-allowed).
- `handleClick` short-circuits early (`if (disabled) return`).
- `handleKeyDown` short-circuits early (`if (disabled) return`).

`ArchetypeGrid` passes through the new `disabled` prop:

```tsx
interface ArchetypeGridProps {
  value: Archetype | null
  onChange: (value: Archetype) => void
  disabled?: boolean         // NEW
}
```

`SetupLayout` computes the prop locally:

```tsx
const customRoleActive = session.customRole.trim().length > 0
// ...
<ArchetypeGrid
  value={session.archetype}
  onChange={(archetype) => dispatch({ type: 'ArchetypeSelected', archetype })}
  disabled={customRoleActive}
/>
<CustomRoleInput
  value={session.customRole}
  onChange={(customRole) => dispatch({ type: 'CustomRoleChanged', customRole })}
  disabled={isSubmitting}
/>
```

---

## Backend entities

### `Archetype` Java enum (extended)

`backend/src/main/java/com/aiavatar/alterego/model/Archetype.java`.

```java
public enum Archetype {
    CLOUD_ARCHITECT("cloud-architect", "Cloud Architect"),
    BACKEND_DEV("backend-dev", "Backend Dev"),
    FRONTEND_DEV("frontend-dev", "Frontend Dev"),
    AI_ENGINEER("ai-engineer", "AI Engineer"),
    PLATFORM_ENG("platform-eng", "Platform Eng."),
    DATA_ENGINEER("data-engineer", "Data Engineer"),
    HR("hr", "HR"),                                              // NEW
    ADMINISTRATION("administration", "Administration"),          // NEW
    CUSTOMER_RELATIONS("customer-relations", "Customer Relations"); // NEW
    // ... wire(), label(), fromWire() unchanged
}
```

Both prompt builders' `ROLE_LABELS` maps gain three corresponding entries:
- `HR → "Human Resources"` (longer / more model-grounded label — matches the 021 pattern where `BACKEND_DEV → "Backend Developer"` expands the UI label for the prompt).
- `ADMINISTRATION → "Administration / Operations"` (idem).
- `CUSTOMER_RELATIONS → "Customer Relations / Support"` (idem).

The expanded prompt labels are an implementation detail and are unit-tested for byte-stability.

---

### `AlterEgoUserSelections` (extended)

`backend/src/main/java/com/aiavatar/alterego/model/AlterEgoUserSelections.java`.

```java
@RoleOfRecordPresent
public record AlterEgoUserSelections(
        Archetype archetype,                       // CHANGED: @NotNull removed
        @NotNull Universe universe,
        @NotNull ArtStyle artStyle,
        @NotBlank @Size(max = 50) @ValidFirstName String firstName,
        PhotoMode photoMode,
        @Size(max = 100) String customRole         // NEW — optional, ≤ 100 chars
) {
    public PhotoMode effectivePhotoMode() {
        return photoMode != null ? photoMode : PhotoMode.SINGLE;
    }
}
```

**Validation rules**:
- `archetype`: removed `@NotNull`. May be null when `customRole` is non-blank.
- `customRole`: optional. When present, ≤ 100 chars (Bean Validation `@Size`). Blank/whitespace-only customRole + null archetype fails the class-level validator (RFC 7807 400).
- Class-level `@RoleOfRecordPresent`: enforces the OR-invariant.

---

### `AlterEgoRequest` (extended, server-internal)

`backend/src/main/java/com/aiavatar/alterego/model/AlterEgoRequest.java`. Mirrors `AlterEgoUserSelections` with the addition of `pose` + `vibe` (rolled in `AlterEgoService.generate`).

```java
@RoleOfRecordPresent
public record AlterEgoRequest(
        @NotNull Pose pose,
        Archetype archetype,                       // CHANGED: @NotNull removed
        @NotNull Universe universe,
        Vibe vibe,
        @NotNull ArtStyle artStyle,
        @NotBlank @Size(max = 50) @ValidFirstName String firstName,
        PhotoMode photoMode,
        @Size(max = 100) String customRole         // NEW
) {
    public AlterEgoRequest withTrimmedFirstName() { /* unchanged signature; trims firstName only */ }
    public PhotoMode effectivePhotoMode() { /* unchanged */ }

    /**
     * Single decision point for "what role label does this request carry?"
     * Used by both image prompt builders, the bio prompt builder, and the
     * poster text overlay. customRole takes precedence over archetype's
     * label (FR-2211). Defensive default for the unreachable
     * "both null" case so prompt builders never see an empty string.
     */
    public String roleLabel() {
        if (customRole != null && !customRole.isBlank()) {
            return customRole.trim();
        }
        return archetype != null ? archetype.label() : "Engineer";
    }
}
```

---

### `@RoleOfRecordPresent` annotation + validator (new)

`backend/src/main/java/com/aiavatar/alterego/model/validation/RoleOfRecordPresent.java` + `RoleOfRecordPresentValidator.java`.

```java
@Target({TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = RoleOfRecordPresentValidator.class)
public @interface RoleOfRecordPresent {
    String message() default "either archetype must be set or customRole must be non-blank";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

The validator uses reflection-free `instanceof` patterns to handle both `AlterEgoUserSelections` and `AlterEgoRequest`:

```java
public class RoleOfRecordPresentValidator
        implements ConstraintValidator<RoleOfRecordPresent, Object> {
    @Override
    public boolean isValid(Object value, ConstraintValidatorContext ctx) {
        if (value instanceof AlterEgoUserSelections s) {
            return hasRoleOfRecord(s.archetype(), s.customRole());
        }
        if (value instanceof AlterEgoRequest r) {
            return hasRoleOfRecord(r.archetype(), r.customRole());
        }
        return true; // unknown target → defer to other validators
    }
    private static boolean hasRoleOfRecord(Archetype a, String custom) {
        return a != null || (custom != null && !custom.isBlank());
    }
}
```

---

## Derived value: role-of-record

Single canonical reference: `AlterEgoRequest.roleLabel()`. Consumers (`GeminiPromptBuilder`, `FalAiPromptBuilder`, `GeminiCharacterPromptBuilder`, `AlterEgoService` text-overlay call site, `FallbackPosterProvider` text-overlay call site) read this helper instead of `request.archetype().label()`.

**Invariant**: `roleLabel()` returns a non-empty trimmed string for every validation-passing `AlterEgoRequest`. Tests pin this across the truth table:

| `archetype` | `customRole` | `roleLabel()` |
|---|---|---|
| `CLOUD_ARCHITECT` | `null` | `"Cloud Architect"` |
| `CLOUD_ARCHITECT` | `""` | `"Cloud Architect"` |
| `CLOUD_ARCHITECT` | `"  "` | `"Cloud Architect"` (blank custom ignored — validator allows this combo) |
| `CLOUD_ARCHITECT` | `"Tester"` | `"Tester"` (precedence) |
| `null` | `"Tester"` | `"Tester"` |
| `null` | `"  Tester  "` | `"Tester"` (trimmed) |
| `null` | `null` | rejected by validator before reaching prompt builders |
| `null` | `""` | rejected by validator |
| `null` | `"   "` | rejected by validator (blank) |

---

## State transitions (frontend reducer)

Only deltas from the current state machine are shown.

```
                       (typing non-whitespace into custom input)
                                     |
                                     v
        +-----------------+   CustomRoleChanged   +-------------------------+
        | archetype: X    | --------------------> | archetype: null         |
        | customRole: ""  |  (raw trimmed non-∅)  | customRole: "Tester"    |
        +-----------------+                       +-------------------------+
                                     |                       |
                                     |                       | SurpriseMePicked
                                     |                       v
                                     |       +---------------------------------+
                                     |       | archetype: <rolled prefab>      |
                                     |       | customRole: ""                  |
                                     |       +---------------------------------+
                                     |
                                     | (delete chars OR X clear button:
                                     |  CustomRoleChanged with raw "")
                                     v
                        +-------------------------+
                        | archetype: null         |
                        | customRole: ""          |
                        +-------------------------+
                          (user must pick prefab
                           or type again to unblock Generate)
```

The reducer transitions are minimal and the diagram above is the entire new surface area.

# Data Model: Surprise Me Button (009)

Spec: [./spec.md](./spec.md) · Plan: [./plan.md](./plan.md) · Research: [./research.md](./research.md)

## Overview

No session-shape change. Surprise Me adds exactly one new reducer action (`SurpriseMePicked`), one new selector (`isReadyToSurprise`), one new companion selector (`missingInputsForSurprise`), one new utility (`randomSelections`), one new UI component (`SurpriseMeButton`), and one new hook method (`useGenerateAlterEgo.surprise`). The outbound API contract (`Selections`) is unchanged.

## Session State (unchanged)

The `AlterEgoSession` shape is exactly as it stands after 007. Surprise Me writes to `pose`, `archetype`, `universe`, `vibe`, `artStyle`, and transitions `phase` from whatever it was to `'picking'` (then `'generating'` when the mutation starts). Fields below are the ones Surprise Me touches:

| Field | Type | Before Surprise Me | After Surprise Me |
|---|---|---|---|
| `pose` | `Pose \| null` | may be null or any value | one of `POSE_OPTIONS[i].value`, i = rng-picked |
| `archetype` | `Archetype \| null` | may be null or any value | one of `ARCHETYPE_OPTIONS[i].value` |
| `universe` | `Universe \| null` | may be null or any value | one of `UNIVERSE_OPTIONS[i].value` |
| `vibe` | `Vibe \| null` | may be null or any value | one of `VIBE_OPTIONS[i].value` (never null — A-901 / R10) |
| `artStyle` | `ArtStyle \| null` | may be null or any value | one of `ART_STYLE_OPTIONS[i].value` |
| `phase` | `SessionPhase` | `'idle' \| 'picking' \| 'succeeded' \| 'failed_with_fallback'` (never `'generating'` — button is disabled) | `'picking'` (then `'generating'` on the subsequent `GenerateSubmitted` dispatch) |

All other fields (`activeTab`, `generateAutoSwitchNonce`, `photoBlob`, `photoPreviewUrl`, `firstName`, `errorMessage`, `result`) pass through unchanged.

## New Action: `SurpriseMePicked`

```ts
| { type: 'SurpriseMePicked'; picks: SurpriseMePicks }

export interface SurpriseMePicks {
  pose: Pose
  archetype: Archetype
  universe: Universe
  vibe: Vibe
  artStyle: ArtStyle
}
```

**Reducer case** (pseudocode):

```ts
case 'SurpriseMePicked':
  return {
    ...state,
    pose: action.picks.pose,
    archetype: action.picks.archetype,
    universe: action.picks.universe,
    vibe: action.picks.vibe,
    artStyle: action.picks.artStyle,
    phase: 'picking', // matches the other *Selected cases
  }
```

**Invariants**:

- `SurpriseMePicked` is the only action that assigns all five category fields in one transition.
- `SurpriseMePicked` does NOT reset `firstName`, `photoBlob`, `photoPreviewUrl`, `activeTab`, `generateAutoSwitchNonce`, `errorMessage`, or `result`.
- Post-action, every category field is non-null. Tests pin this with `expect(next.pose && next.archetype && ... && next.vibe).not.toBeNull()`.

## New Selector: `isReadyToSurprise`

```ts
export function isReadyToSurprise(state: AlterEgoSession): boolean {
  return (
    !!state.photoBlob &&
    state.firstName.trim().length >= 1 &&
    state.phase !== 'generating'
  )
}
```

**Invariant** (tested): for every `AlterEgoSession s`, `isReadyToGenerate(s) ⇒ isReadyToSurprise(s)`. Stated in English: "any session ready to Generate is also ready to be Surprised, because Surprise Me has a strict-subset of the required inputs". Violations of this invariant indicate spec drift.

## New Selector: `missingInputsForSurprise`

```ts
export type SurpriseRequiredInput = Extract<RequiredInput, 'photo' | 'firstName'>

export function missingInputsForSurprise(state: AlterEgoSession): SurpriseRequiredInput[] {
  const missing: SurpriseRequiredInput[] = []
  if (!state.photoBlob) missing.push('photo')
  if (state.firstName.trim().length < 1) missing.push('firstName')
  return missing
}
```

Return type is a strict subset of `RequiredInput` so the existing `FIELD_LABELS` map in `GenerateButton` can be reused verbatim for the hint copy (no new label map).

## New Utility: `randomSelections`

**File**: `frontend/src/features/alterego/lib/randomSelections.ts`

```ts
type Rng = () => number // contract: 0 ≤ rng() < 1

export function pickOne<T>(list: ReadonlyArray<T>, rng: Rng = Math.random): T {
  if (list.length === 0) {
    throw new Error('pickOne: empty list')
  }
  const index = Math.floor(rng() * list.length)
  // rng() === 1 is not allowed by the contract, but we defend against it anyway.
  return list[Math.min(index, list.length - 1)]!
}

export function randomSelections(rng: Rng = Math.random): SurpriseMePicks {
  return {
    pose: pickOne(POSE_OPTIONS, rng).value,
    archetype: pickOne(ARCHETYPE_OPTIONS, rng).value,
    universe: pickOne(UNIVERSE_OPTIONS, rng).value,
    vibe: pickOne(VIBE_OPTIONS, rng).value,
    artStyle: pickOne(ART_STYLE_OPTIONS, rng).value,
  }
}
```

**Contract**:

- `pickOne` MUST return an element strictly drawn from the passed list. If `rng` returns `1` (out-of-contract but possible with buggy callers), `pickOne` clamps to the last index rather than returning `undefined`.
- `pickOne(list, () => 0)` returns `list[0]`.
- `pickOne(list, () => 0.999)` returns `list[list.length - 1]`.
- `randomSelections` calls `pickOne` exactly once per category.
- No memoization, no global state, no side effects — the only observable output is the return value.

## New Hook Method: `useGenerateAlterEgo.surprise`

```ts
function surprise(args: { photoBlob: Blob; firstName: string }): void {
  const picks = randomSelections()
  dispatch({ type: 'SurpriseMePicked', picks })
  const selections: Selections = {
    pose: picks.pose,
    archetype: picks.archetype,
    universe: picks.universe,
    vibe: picks.vibe,
    artStyle: picks.artStyle,
    firstName: args.firstName.trim(),
  }
  submit({ photoBlob: args.photoBlob, selections })
}
```

Exposed as `useGenerateAlterEgo() → { submit, surprise, isPending }`. `submit` is unchanged; `surprise` is additive.

**Ordering invariant** (tested): when `surprise()` is called,

1. `SurpriseMePicked` is dispatched first (Setup form now reflects the picks — FR-907).
2. Then the existing `submit()` runs its `onMutate`, which dispatches `ActiveTabChanged → 'alter-ego' (reason: 'generate')` and `GenerateSubmitted`.

This order is the FR-907 requirement that "picks are persisted to the session before the request fires".

## New Component: `SurpriseMeButton`

```tsx
interface Props {
  session: AlterEgoSession
  isSubmitting: boolean
  onSurprise: () => void
}
```

**Behavior**:

- Renders a `<button type="button">` with accessible name "Surprise Me".
- `disabled={!isReadyToSurprise(session) || isSubmitting}`; reflects the same condition in `aria-disabled` and an `aria-describedby` status paragraph.
- Hint copy when disabled: `"Still needed: ${formatMissingList(missingInputsForSurprise(session))}."` — reuses `formatMissingList` + `FIELD_LABELS` from `GenerateButton` (will be extracted to a shared helper in `selectors.ts` or a new `lib/missingInputHint.ts` to avoid duplication).
- `onClick`: if disabled, no-op; otherwise calls `onSurprise()`.

## Component Wiring: `SetupLayout`

`SetupLayout` gains an `onSurprise: () => void` prop and renders `<SurpriseMeButton>` in a flex row next to `<GenerateButton>`:

```tsx
<div className="setup-layout__actions">
  <GenerateButton session={session} isSubmitting={isSubmitting} onSubmit={onSubmit} />
  <SurpriseMeButton session={session} isSubmitting={isSubmitting} onSurprise={onSurprise} />
</div>
```

## `AlterEgoPage` Wiring

```tsx
const handleSurprise = () => {
  if (!state.photoBlob || state.firstName.trim().length < 1) return // defence-in-depth
  surprise({ photoBlob: state.photoBlob, firstName: state.firstName })
}
```

Passed down through `SetupLayout` as `onSurprise`. Mirrors the existing `handleSubmit` shape.

## Backend Contract (unchanged)

The backend receives the same `Selections` JSON it always has. It cannot tell whether the values came from a human click or from `randomSelections()`. No OpenAPI file changes; no Java changes.

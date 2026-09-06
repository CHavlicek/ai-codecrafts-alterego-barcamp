# Data Model: Partial Surprise Me

**Feature**: 028-partial-surprise-me
**Date**: 2026-05-20

This feature **does not change the AlterEgoSession shape**. Everything below describes the behavioral delta plus the one new pure value object the merge helper consumes.

---

## Entities

### `AlterEgoSession` (unchanged)

The session shape established by 002/006/011/022/023 stands as-is. The slots this feature reads:

| Field | Type | Source of explicit-ness | Read by `mergeSurpriseWithExplicit` |
|---|---|---|---|
| `archetype` | `Archetype \| null` | `ArchetypeSelected` action | yes — non-null ⇒ Role explicit (with custom-role channel) |
| `customRole` | `string` (default `''`) | `CustomRoleChanged` action (trim invariant from 022) | yes — `.trim().length > 0` ⇒ Role explicit |
| `universe` | `Universe \| null` | `UniverseSelected` action | yes — non-null ⇒ Universe explicit |
| `artStyle` | `ArtStyle \| null` | `ArtStyleSelected` action | yes — non-null ⇒ Art Style explicit |
| `photoBlob` | `Blob \| null` | `PhotoSelected` action | no — read for gating only (FR-2810); pass-through (FR-2813) |
| `firstName` | `string` | `FirstNameChanged` action | no — pass-through (FR-2813) |
| `email` | `string` | `EmailChanged` action | no — pass-through (FR-2813 + 023 FR-2308) |
| `phase` | `SessionPhase` | various | no — reducer-internal |
| `activeTab` | `ActiveTab` | `ActiveTabChanged` | no — reducer-internal |

**Invariant from 022 (reused here, not re-asserted)**: `customRole.trim().length > 0` ⇒ `archetype === null`. The `CustomRoleChanged` reducer branch silently clears `archetype` when the trimmed value becomes non-empty. This guarantees that classifying Role as explicit needs only an OR over the two channels (R-5 in research.md), not a conflict-resolution rule.

---

### `SurpriseMePicks` (unchanged)

```ts
interface SurpriseMePicks {
  archetype: Archetype
  universe: Universe
  artStyle: ArtStyle
}
```

Shape pinned by 009 / 020. This feature does not add a `customRole` field here — the custom-role string is read directly from the session by `surprise()` when building `Selections`, not threaded through `SurpriseMePicks`. Adding it here would imply the randomizer can produce custom-role strings, which is false and would be misleading.

---

### `mergeSurpriseWithExplicit(session, fullRoll) → SurpriseMePicks` (new)

Pure function. No side effects, no IO, no randomness (the roll is the input).

**Signature**:

```ts
function mergeSurpriseWithExplicit(
  session: AlterEgoSession,
  fullRoll: SurpriseMePicks,
): SurpriseMePicks
```

**Logic** (closure on FR-2801..FR-2806):

| Field of returned `SurpriseMePicks` | Value |
|---|---|
| `archetype` | `session.archetype` if non-null; **else** if `session.customRole.trim().length > 0` then any deterministic prefab (NOT the roll) flagged ignored by the consumer — see §"Custom-role passthrough" below; **else** `fullRoll.archetype` |
| `universe` | `session.universe` if non-null; else `fullRoll.universe` |
| `artStyle` | `session.artStyle` if non-null; else `fullRoll.artStyle` |

**Custom-role passthrough** (R-5 nuance): when `customRole` is non-empty, the *prefab Archetype* in the returned `SurpriseMePicks` is conceptually unused — the caller (`surprise()` in the hook) will set `Selections.customRole = session.customRole.trim()` and omit `Selections.archetype` from the outbound payload, matching what 022's `Selections` builder does today for the Generate path. The merge helper still returns *some* archetype value for type safety; the convention is to return the *previous* `session.archetype` (which 022's `CustomRoleChanged` already cleared to null — so this collapses to the canonical-empty fallback, see below). The merge helper does NOT roll a prefab to fill the slot when Role is explicit-via-custom.

**Canonical-empty fallback**: in the (current invariant-protected) case where `session.archetype === null` AND `customRole.trim().length === 0`, Role is empty and `fullRoll.archetype` wins — same as today's 009 behavior.

**Purity guarantee**: the function **must not** mutate `session` or `fullRoll`. Test asserts shallow equality on the inputs after the call (R-7 in research.md, test case 8).

---

## State transitions

### `SurpriseMePicked` (modified, no shape change)

| Aspect | Before (009/020/022) | After (028) |
|---|---|---|
| Action shape | `{ type: 'SurpriseMePicked', picks: SurpriseMePicks }` | unchanged |
| Writes `archetype` | unconditionally to `picks.archetype` | unconditionally to `picks.archetype` — but the *caller* now merges, so `picks.archetype` equals `session.archetype` whenever Role was explicit |
| Writes `universe` | unconditionally to `picks.universe` | same pattern as `archetype` — caller-merged |
| Writes `artStyle` | unconditionally to `picks.artStyle` | same pattern — caller-merged |
| Clears `customRole` | yes (022 FR-2209) | **no** — REMOVED (this is the visible reducer-body change) |
| Touches `email`, `firstName`, `photoBlob`, `photoPreviewUrl`, `activeTab`, `generateAutoSwitchNonce`, `errorMessage`, `result` | no | unchanged |
| Sets `phase` | `'picking'` | unchanged |

The single line of code being removed in the reducer body: `customRole: ''` from the spread inside the `case 'SurpriseMePicked':` branch. Everything else in the reducer is left alone.

---

## Wire-format change

**None.** The outbound `POST /api/v1/alter-egos` body shape is unchanged. `Selections` carries `customRole` if non-empty (022 contract) and the final `archetype`/`universe`/`artStyle` values (whether user-picked or rolled). FR-2812 codifies this.

---

## Persistence change

**None.** Inherits 001 FR-016 / FR-017 / FR-024 — no persistence. Session state lives in browser memory; Start Over and a page refresh are the only clearing mechanisms (FR-2814).

---

## Test data shape

The unit test for `mergeSurpriseWithExplicit` uses these fixtures (matching what the reducer test suite already does):

- **Empty session**: `initialAlterEgoSession()` from `state/reducer.ts`.
- **Seeded full roll**: `{ archetype: 'cloud-architect', universe: 'star-wars', artStyle: 'oil-painting' }` — three deterministic constants pulled from `options.ts`.
- **Partial-explicit session**: variants with each single field set to a deterministic non-roll value, e.g. `{ ...empty, universe: 'harry-potter' }`.
- **Custom-role session**: `{ ...empty, customRole: 'Distinguished Spreadsheet Wrangler' }`.

These shapes are stable across the existing 009 / 022 / 023 test suites; reusing them keeps the new tests trivially diff-able for reviewers.

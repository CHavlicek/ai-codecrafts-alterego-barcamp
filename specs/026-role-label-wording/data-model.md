# Data Model — 026: Wording Updates for User Roles

**Date**: 2026-05-18
**Status**: Final

## Session state delta

**None.** This feature changes data **at rest** in two static, in-process tables (frontend `archetypeOptions`, backend `Archetype` enum) and does not touch reducer shape, mutation actions, or session lifecycle in any way.

```ts
// frontend/src/features/alterego/state/reducer.ts — UNCHANGED
interface AlterEgoSession {
  // … all fields unchanged from 025 …
  archetype: ArchetypeValue | null   // wire identity — unchanged
  customRole: string                 // custom-role text — unchanged
}
```

```java
// backend/.../boundary/http/AlterEgoRequest.java — UNCHANGED
// (Archetype field continues to deserialize from the same wire values.)
```

## Action surface delta

**None.** No new reducer action, no new HTTP request shape, no new validator.

## Role-option table — before / after

### Frontend — `frontend/src/features/alterego/options.ts`

```ts
// const archetypeOptions: ReadonlyArray<Option> — order MUST NOT change
// (changed labels marked with ←)
[
  { value: 'cloud-architect',     label: 'Cloud Architect',     icon: Cloud },
  { value: 'backend-dev',         label: 'Backend Developer',   icon: Terminal }, // ← was 'Backend Dev'
  { value: 'frontend-dev',        label: 'Frontend Developer',  icon: PenTool }, // ← was 'Frontend Dev'
  { value: 'ai-engineer',         label: 'AI Engineer',         icon: BrainCog },
  { value: 'platform-eng',        label: 'Platform Engineer',   icon: Gauge },   // ← was 'Platform Eng.' (drop trailing period)
  { value: 'data-engineer',       label: 'Data Engineer',       icon: Database },
  { value: 'hr',                  label: 'People Operations',   icon: Users },   // ← was 'HR'
  { value: 'administration',      label: 'Administration',      icon: ClipboardList },
  { value: 'customer-relations',  label: 'Customer Relations',  icon: MessageCircle },
]
```

**Invariants preserved**:
- Array length = 9.
- Each `value` is byte-identical to today (SC-2604).
- Order is byte-identical to today.
- Each `icon` reference is byte-identical to today.
- Type signature of `Option` is byte-identical to today (`label: string` — no widening, no rename).

**Invariants changed**:
- The string in `label` for the four rows marked `←`.

### Backend — `backend/.../domain/model/Archetype.java`

```java
public enum Archetype {
    CLOUD_ARCHITECT("cloud-architect", "Cloud Architect"),
    BACKEND_DEV("backend-dev",         "Backend Developer"),    // ← was "Backend Dev"
    FRONTEND_DEV("frontend-dev",       "Frontend Developer"),   // ← was "Frontend Dev"
    AI_ENGINEER("ai-engineer",         "AI Engineer"),
    PLATFORM_ENG("platform-eng",       "Platform Engineer"),    // ← was "Platform Eng."
    DATA_ENGINEER("data-engineer",     "Data Engineer"),
    HR("hr",                           "People Operations"),    // ← was "HR"
    ADMINISTRATION("administration",   "Administration"),
    CUSTOMER_RELATIONS("customer-relations", "Customer Relations");

    // wire(), label(), fromWire() — all unchanged
}
```

**Invariants preserved**:
- Enum constant identifiers (`CLOUD_ARCHITECT`, `BACKEND_DEV`, …) — byte-identical.
- Enum constant **order** — byte-identical (`Archetype.values()[1]` is still `BACKEND_DEV`).
- The **wire** string (first constructor argument) for every entry — byte-identical (SC-2604).
- `@JsonValue` on `wire()` and `@JsonCreator` on `fromWire(String)` — byte-identical.
- The `label` field type and `label()` accessor signature — byte-identical.

**Invariants changed**:
- The string passed as the second constructor argument for the four rows marked `←`.

## Downstream consumers (read-only references — *no change needed in any of them*)

| Caller | Reads | Effect of this change |
|---|---|---|
| `frontend/.../ArchetypeGrid.tsx` | `archetypeOptions[].label` for the visible radio text + accessible name | Renders new wording automatically on next render. |
| `frontend/.../SurpriseMeButton.tsx` (via `SurpriseMePicked` action → reducer → grid re-render) | `archetypeOptions[].value` for the random pick; `[].label` only at re-render time | When Surprise Me lands on a renamed Role, the grid re-renders the new wording automatically. |
| `backend/.../application/AlterEgoUseCase` | `AlterEgoRequest.roleLabel()` which falls back to `archetype.label()` if `customRole` is blank | Returns the new wording for the four renamed enums. |
| `backend/.../infrastructure/overlay/TextStage` | `StageContext.role` (which the use case populated from `roleLabel()`) | Paints the new wording onto the poster. |
| `backend/.../domain/policy/AccentResolver` | The `Archetype` enum **identity** (`switch`/`Map<Archetype, …>`) — never `.label()` | Unaffected. |
| `backend/.../infrastructure/provider/{gemini,falai}/.../*PromptBuilder.java` | An *internal* `ROLE_LABELS` map keyed by `Archetype` — does **not** read `Archetype.label()` | Unaffected. Internal prompt-side wording is intentionally distinct from UI wording (see research R2). |

## State-machine / transition diagrams

**None.** No state machine touched. The reducer's `archetype` field continues to hold either `null` or one of the nine wire identifiers; the backend's `Archetype` continues to be a closed nine-member enum. No new transition, no removed transition.

## Persistence

**None.** Inherits 001 FR-016 / FR-017 / FR-024. No database, file, log line, cache entry, or session-storage key holds a Role label string at rest. The two tables above are loaded into JVM constant pool / JS module-scope on startup and read on each render or request.

# Phase 1 Data Model: Print only the alter ego image

**Feature**: `018-print-image-only`
**Date**: 2026-05-08
**Spec**: [./spec.md](./spec.md) | **Plan**: [./plan.md](./plan.md) | **Research**: [./research.md](./research.md)

## Scope

This feature does not introduce new persistent entities, new wire types, new reducer actions, or new session-state fields. The data model below documents only what changes inside the print-only **view** layer.

## Entities (frontend, transient)

### `PrintArtefactView` (derived; not stored)

A pure view derived once per render from the existing `AlterEgoSession` shape. After 018 it shrinks from a two-face composite to a single image-bearing face.

| Field | Type | Source | Required | Purpose |
|---|---|---|---|---|
| `posterDataUrl` | `string` (data URL) | `session.result.poster.dataUrl` | yes | The `src` of the printable `<img>`. Same bytes as the on-screen poster — already composited with 015's frame and 017's hero name / role / quote. |
| `mediaType` | `string` (e.g. `"image/png"`) | `session.result.poster.mediaType` | yes | Kept for completeness; not currently passed to the `<img>` element directly (the data URL carries the media type). |
| `widthPx` | `number` | `session.result.poster.widthPx` | yes | Passed as `<img width>` for layout-engine hinting. CSS sizing (`width: 100%; height: 100vh; object-fit: contain`) is what actually controls print rendering. |
| `heightPx` | `number` | `session.result.poster.heightPx` | yes | Same as `widthPx`, for `<img height>`. |
| `altText` | `string` | `composePosterAlt(session.firstName, humanizeArchetype(session.archetype), session.result.character.quote)` | yes | Accessible name on the `<img>`. The artefact root carries `aria-hidden="true"` so the image is hidden from screen readers walking the on-screen DOM, but `alt` is preserved as defence-in-depth for print-preview tooling that bypasses `aria-hidden`. Mirrors the on-screen `PosterView` alt exactly. |

**Removed fields** (compared to the pre-018 view):

| Field | Was sourced from | Why removed |
|---|---|---|
| `heroTitleLine1`, `heroTitleLine2` | `session.result.character.heroTitleLine1` / `heroTitleLine2` | Was rendered on the back face (`<h2>` + subtitle). 018 deletes the back. |
| `firstName` | `session.firstName` | Same. |
| `tagline` | `session.result.character.tagline` | Same. |
| `superpowers` | `session.result.character.superpowers` | Same. |
| `quote` | `session.result.character.quote` | Same. (Still consumed by `composePosterAlt` for `altText` — see above.) |
| `poseLabel` | `humanizePose(session.pose)` | Same. Helper deleted (research §R2). |
| `archetypeLabel` | `humanizeArchetype(session.archetype)` | Still derived for `altText`; no longer rendered as visible text. |
| `universeLabel` | `humanizeUniverse(session.universe)` | Same. Helper deleted (research §R2). |
| `artStyleLabel` | `humanizeArtStyle(session.artStyle)` | Same. Helper deleted (research §R2). |
| `vibeLabel` | `humanizeVibe(session.vibe)` (when set) | Same. Helper deleted (research §R2). |
| `printedOnLocalDate` | `new Date().toLocaleDateString()` | Same. |

After 018, `PrintArtefactView` is fully a function of `session.result.poster.*` and (for alt text only) `session.firstName`, `session.archetype`, and `session.result.character.quote`. Every other field on `AlterEgoSession` is irrelevant to the printable artefact.

## Validation rules

Inherited from the spec (no new constraints introduced):

| Rule | Source | Enforcement point |
|---|---|---|
| The artefact mounts only when `phase === 'succeeded' \|\| phase === 'failed_with_fallback'`. | 010 FR-901 / FR-904 (carry-over) | `AlterEgoPanel.tsx` decides whether to render `<PrintArtefact>`. Unchanged by 018. |
| The artefact returns `null` when `session.result`, `session.pose`, `session.archetype`, `session.universe`, or `session.artStyle` is missing. | Defence-in-depth guard from 010 | `PrintArtefact.tsx` early-return. **Preserved** by 018 — the guard still references all five fields because we want the same defensive posture as 010, even though only `result` is strictly required for the surviving image-only view. Tightening the guard to "result only" is **not** in scope for this feature (would be a refactor without product benefit). |
| The artefact must be a top-level child of `<body>` (Portal target). | 010 FR-903 + research §R2 (ibid.) | `createPortal(…, document.body)` — preserved. |
| The print output is byte-identical for `outcome=real` vs `outcome=fallback`. | 001 FR-018 / 010 FR-905 / 018 FR-1807 | After 018, this becomes trivial — `meta.outcome` / `meta.reason` are not read at all by `PrintArtefact`, and the only field that varied between renders (the `Printed` local date) is removed with the back. |

## Relationships & state transitions

No state machine change. The screen-vs-print visibility contract is the same as 010 with one term removed:

```text
                ┌──────────────────────────────────────────────────────────┐
                │ AlterEgoSession.phase                                    │
                └──────────────────────────────────────────────────────────┘
                              │
       ┌──────────────────────┼──────────────────────────┐
       ▼                      ▼                          ▼
   idle / generating      succeeded / failed_with_fallback
       │                      │
       │                      ▼
       │     ┌──────────────────────────────────────┐
       │     │ <PrintArtefact session={session}/>   │  ← portal target: document.body
       │     │                                      │
       │     │  on screen:  display: none           │
       │     │                                      │
       │     │  on print:   display: block          │
       │     │              · single page           │   ← changed in 018
       │     │              · only the <img>        │   ← changed in 018
       │     │                                      │
       │     └──────────────────────────────────────┘
       │
       ▼
   <PrintArtefact … /> NOT rendered (early return null + AlterEgoPanel doesn't mount it)
```

## Reducer / action surface

**No change.** No new actions, no new reducers, no new selectors. The session shape is unchanged. `AlterEgoSession.phase`, `session.result`, `session.firstName`, `session.archetype`, etc. all keep their existing roles in the rest of the app.

## Wire / contract surface

**No change.** No backend file changes. No OpenAPI delta. The `AlterEgoResponse` JSON shape returned by `/api/alter-ego` is byte-identical (poster + character + meta). The frontend simply renders less of what's already in `character` and `firstName` / Setup choices on the printable artefact.

## Persistence surface

**None.** Carry-over from 001 FR-016 / FR-017 / FR-024 / spec FR-1810. No disk, no database, no cookies, no localStorage / sessionStorage. The print-only DOM lives in process memory for the lifetime of the open page only.

## Migration / coexistence

Not applicable. There is no stored user data and no API consumer outside this repo. The first deploy that ships 018 will produce single-page prints from that moment forward.

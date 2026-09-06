# Data Model: Print Alter Ego

**Feature**: 010-print-alter-ego
**Date**: 2026-04-24

## Overview

The Print feature introduces **no new persisted data**, **no new session fields**, and **no new wire types**. Every value rendered on paper is already present in the existing `AlterEgoSession` shape established by features 001..009. This document exists to (a) pin down the exact derivation of the transient print view and (b) specify the `humanizeSelection` helper contract.

## 1. `AlterEgoSession` — unchanged

Feature 010 reads — but does NOT write — the following session fields already defined in `frontend/src/features/alterego/state/reducer.ts` and `features/alterego/types.ts`:

| Field | Type | Source | Used by Print? |
|---|---|---|---|
| `phase` | `'idle' \| 'generating' \| 'succeeded' \| 'failed_with_fallback'` | 001 | YES — gate for whether Print button + artefact render at all |
| `result` | `AlterEgoResponse \| null` | 001 / 003 | YES — provides `character` (back page) and `poster` (front page) |
| `result.character.heroTitleLine1` | `string` | 003 | YES — back-page heading |
| `result.character.heroTitleLine2` | `string` | 003 | YES — back-page sub-heading |
| `result.character.tagline` | `string` | 003 | YES — back-page row |
| `result.character.superpowers` | `[string, string, string]` | 003 | YES — back-page list |
| `result.character.quote` | `string` | 003 | YES — back-page blockquote |
| `result.poster.dataUrl` | `string` | 003 | YES — front-page `<img src>` |
| `result.poster.widthPx` | `number` | 003 | YES — `<img width>` for CSS aspect hint |
| `result.poster.heightPx` | `number` | 003 | YES — `<img height>` for CSS aspect hint |
| `result.poster.mediaType` | `'image/png' \| 'image/jpeg'` | 003 | NO — not needed for print |
| `result.meta.outcome` | `'real' \| 'fallback'` | 003 | NO — FR-905 says both print identically; FR-908 forbids surfacing |
| `result.meta.reason` | `FallbackReason?` | 003 | NO — FR-908 forbids surfacing |
| `firstName` | `string` | 001 | YES — back-page row |
| `pose` | `Pose \| null` | 002 | YES — back-page row (humanized) |
| `archetype` | `Archetype \| null` | 002 | YES — back-page row (humanized) |
| `universe` | `Universe \| null` | 002 | YES — back-page row (humanized) |
| `vibe` | `Vibe \| undefined` | 002 | YES *if defined* — back-page row (humanized; FR-906) |
| `artStyle` | `ArtStyle \| null` | 006 | YES — back-page row (humanized) |

**Invariant**: In the `succeeded` and `failed_with_fallback` phases the session's selection fields (`pose`, `archetype`, `universe`, `artStyle`) are non-null by construction — they were required to enter the `generating` phase. `firstName` is non-empty for the same reason. The Print artefact MAY assume these non-null at the point it renders (it's gated on `phase` already).

**No write path exists.** Print never dispatches any reducer action.

## 2. `PrintArtefactView` — transient, derived, not stored

A small in-memory view object assembled by `<PrintArtefact>` from the session just before render. Shape (for documentation — no runtime type):

```ts
interface PrintArtefactView {
  // Front face
  posterSrc: string            // = session.result.poster.dataUrl
  posterAlt: string            // = `Alter ego poster for ${character.heroTitleLine1}`
  posterIntrinsicWidth: number // = session.result.poster.widthPx
  posterIntrinsicHeight: number// = session.result.poster.heightPx

  // Back face
  heroTitleLine1: string       // = character.heroTitleLine1
  heroTitleLine2: string       // = character.heroTitleLine2
  firstName: string            // = session.firstName
  tagline: string              // = character.tagline
  superpowers: [string, string, string] // = character.superpowers
  quote: string                // = character.quote

  // Setup categories — humanized for paper
  poseLabel: string            // = humanizePose(session.pose)
  archetypeLabel: string       // = humanizeArchetype(session.archetype)
  universeLabel: string        // = humanizeUniverse(session.universe)
  artStyleLabel: string        // = humanizeArtStyle(session.artStyle)
  vibeLabel: string | null     // = session.vibe ? humanizeVibe(session.vibe) : null

  printedOn: string            // = new Date().toLocaleDateString() at render time
}
```

**Lifecycle**: Constructed during `<PrintArtefact>`'s render pass. Discarded on every re-render. Never passed to any backend, never serialized, never written to any storage. FR-908 held by construction.

## 3. `humanizeSelection` helper — contract

```ts
// frontend/src/features/alterego/lib/humanizeSelection.ts
import type { Archetype, ArtStyle, Pose, Universe, Vibe } from '../types'

export function humanizePose(v: Pose): string
export function humanizeArchetype(v: Archetype): string
export function humanizeUniverse(v: Universe): string
export function humanizeVibe(v: Vibe): string
export function humanizeArtStyle(v: ArtStyle): string
```

**Implementation sketch** (documented for consistency — actual code lives in the file):

- At module load, build five `Record<WireValue, DisplayLabel>` maps by iterating the five `*_OPTIONS` arrays from `features/alterego/options.ts` and reading the `.label` field.
- Each `humanize*` function returns `map[value] ?? titleCaseKebab(value)` where `titleCaseKebab('cloud-architect') === 'Cloud Architect'` — the forward-compatibility fallback (R5).

**Invariants asserted by the unit tests**:
- For every declared wire value in `POSE_OPTIONS` / `ARCHETYPE_OPTIONS` / `UNIVERSE_OPTIONS` / `VIBE_OPTIONS` / `ART_STYLE_OPTIONS`, the humanizer returns the same `.label` as the options array declares.
- For an unknown value passed via `as` cast, the humanizer returns a non-empty Title-Cased string (never throws).
- None of the humanizer functions produce the raw kebab-case value for any *declared* option.

## 4. Screen-vs-print visibility contract (CSS)

Not a data-model concern strictly, but pinned here so the spec's FR-903 / FR-910 / FR-911 are testable:

- `.print-artefact` is `display: none` in the screen media query (the default cascade).
- Inside `@media print`, the blanket rule `body * { visibility: hidden }` hides everything, then `.print-artefact, .print-artefact * { visibility: visible }` un-hides only the print tree, which is also set to `display: block` and `position: absolute`.
- `.print-artefact__front` ends with `page-break-after: always`; `.print-artefact__back` starts with `page-break-before: always`. Exactly two pages result from exactly two block-level children.

**Testability**: the unit test for `<PrintArtefact>` asserts (a) the root element has class `print-artefact`, (b) it contains a `.print-artefact__front` section with exactly one `<img>` and no text-rendering elements (no `<h2>`, `<dl>`, `<p>`, etc.), and (c) a `.print-artefact__back` section with no `<img>` and all of the FR-906 fields. The CSS itself is verified by a manual `npm run preview` + print-preview step in `quickstart.md` (CSS print rules are not meaningfully testable in jsdom).

## 5. What Print does NOT store

Explicit non-data-model items, affirmed by FR-908 and SC-905:

- No `localStorage` / `sessionStorage` write.
- No IndexedDB write.
- No Cache API write.
- No cookie write.
- No network call (POST, GET, or otherwise).
- No server-side log entry.
- No new Blob, no new `URL.createObjectURL()` call, no new Object URL leaked or retained.
- No React state mutation (`useState` is not used in `PrintButton` or `PrintArtefact`; they are derived-only).
- No reducer dispatch.

The test suite includes explicit assertions for the first three (storage before === storage after, network requests === 0 during the Print Playwright spec).

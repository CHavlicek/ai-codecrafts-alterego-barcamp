# Research: New Options for the Role Category

**Feature**: 022-role-options-custom · **Phase**: 0 · **Date**: 2026-05-11

This document resolves every NEEDS-CLARIFICATION or open-design question identified during planning. Every decision lists the rationale and the alternatives considered.

---

## R1 — Where "Role" / `Archetype` is consumed end-to-end

**Decision**: The category the spec calls "Role" is the `Archetype` enum end-to-end. UI label is `"Engineer role"` (see `ArchetypeGrid.tsx:17`). This feature renames the user-visible label in `ArchetypeGrid` to `"Role"` so the spec wording, the issue wording, and the new prefab options (HR, Administration, Customer Relations — which are *not* engineering) line up. The Java identifier `Archetype` stays — it's a structural-stable name (see constitution preamble re. `AI-Avatar` retention).

**Consumer call sites the role-of-record reaches** (verified by `grep -rn "archetype\\|\\.archetype()"`):

- **Frontend**:
  - Reducer: `state/reducer.ts:78` (`ArchetypeSelected`) / `state/reducer.ts:160` (`SurpriseMePicked`).
  - Selectors: `state/selectors.ts:39` (gating).
  - Surprise Me: `lib/randomSelections.ts:52`.
  - UI: `components/ArchetypeGrid.tsx`, `components/SetupLayout.tsx:82`.
  - API client: `services/alterEgoClient.ts` (via `selections.archetype`).
  - Fallback poster: `fallback/fallbackPoster.ts` reads `selections.archetype` for fallback art keying.
- **Backend**:
  - Enum + wire: `model/Archetype.java`.
  - DTO: `model/AlterEgoUserSelections.java`, `model/AlterEgoRequest.java`.
  - Service: `service/AlterEgoService.java:104` (selections → request), `:138` + `:184` (text overlay reads `request.archetype().label()`), `:181` (fallback poster keyed on archetype).
  - Prompt builders: `service/gemini/GeminiPromptBuilder.java:148`, `service/gemini/GeminiCharacterPromptBuilder.java` (bio), `service/falai/FalAiPromptBuilder.java`.
  - Accent: `service/AccentResolver.java:31` keyed on `(Archetype, Universe)`.

**Rationale**: The audit confirms the role-of-record needs to land at exactly four behavioural seams: (a) the two image prompt builders, (b) the bio prompt builder, (c) the text-overlay role-label slot, and (d) the fallback poster path. Threading a single `roleLabel()` helper through these four consumers is cheaper than threading the raw `customRole` string into each.

**Alternatives considered**:
- Per-builder `if (customRole != null) ... else ...` branching — rejected: four duplicated branches, easy to drift.
- Subclass / discriminated union of `AlterEgoRequest` — rejected: changes the type signature of every consumer for zero behavioural benefit.

---

## R2 — Widening the wire contract without breaking older clients

**Decision**: Add a single optional field `customRole: string` to the `selections` JSON object. Existing six `archetype` wire values stay; three new values (`hr`, `administration`, `customer-relations`) are added to the enum.

**Wire shape (additive delta)**:

```json
{
  "archetype": "hr",                  // or any of the now-nine wire values, OR omitted when customRole is non-blank
  "universe": "marvel",
  "artStyle": "oil-painting",
  "firstName": "Lalo",
  "photoMode": "single",
  "customRole": "Tester"              // optional; ≤ 100 chars; omitted when prefab is the role of record
}
```

**Rationale**:
- Pure addition — old clients that omit `customRole` continue to validate. Jackson's existing `FAIL_ON_UNKNOWN_PROPERTIES=false` policy means any future field additions are also forward-compatible.
- A single optional string is the smallest possible surface that satisfies FR-2212.
- Keeping the existing `archetype` enum intact (rather than collapsing it to "free-form string") preserves the AccentResolver curated palette (`service/AccentResolver.java:53`) and the fallback poster's keying logic without ceremony.

**Alternatives considered**:
- Discriminated union (`type: "prefab" | "custom"` + value) — rejected: heavier wire shape, no behavioural win, harder for an older client to ignore gracefully.
- Sentinel string in `archetype` (e.g. `archetype: "custom"`) — rejected: pollutes the closed-enum semantics and forces every consumer to special-case the sentinel.

---

## R3 — Relaxing `archetype` from `@NotNull` while preserving the OR-invariant

**Decision**: Drop `@NotNull` from `AlterEgoUserSelections.archetype()`. Introduce a class-level annotation `@RoleOfRecordPresent` whose validator asserts:

```
archetype != null  OR  (customRole != null && !customRole.trim().isEmpty())
```

Violation message: `"either archetype must be set or customRole must be non-blank"` (RFC 7807 detail). Same constraint shape applies to the server-internal `AlterEgoRequest` so the contract is fully enforced before any prompt builder sees the value.

**Rationale**:
- Bean Validation supports class-level constraints (`@Target({TYPE})`) natively — used in production by `@ValidFirstName` patterns already in this codebase. Mirrors that idiom.
- The Setup-tab gating (FR-2210) prevents the all-empty case from ever reaching the wire under normal use, so this validator is a defense-in-depth — it catches direct API calls and ensures the prompt builder never sees both fields empty.

**Alternatives considered**:
- Keep `archetype` `@NotNull` and require the frontend to send a default archetype (e.g. the last-picked one) even when `customRole` claims precedence — rejected: leaks UI bookkeeping onto the wire, surprising for a future API consumer reading the spec.
- Move the OR-validation into the controller layer — rejected: validation-at-DTO is the codebase convention and yields the RFC 7807 response shape for free via Spring's existing handler.

---

## R4 — AccentResolver behaviour when `archetype` is null

**Decision**: When `archetype` is `null`, the call sites that need to derive an accent (`AlterEgoService` → `FallbackPosterProvider.poster(archetype, universe)` and any future caller of `AccentResolver.deriveAccent`) pass a stable default of `Archetype.BACKEND_DEV` purely for accent keying. The default is documented in `AccentResolver`'s Javadoc and a one-line comment at each call site. The role-of-record displayed / prompted is **still** the custom string — the default is accent-key-only.

**Rationale**:
- AccentResolver is a deterministic colour-palette lookup (`service/AccentResolver.java:53` is a 6×6 curated `EnumMap`). It cannot key on a free-form string without redesigning the curated palette, which is well outside this feature's scope.
- Picking *any* fixed default produces the same accent for every custom-role generation — predictable and easy to reason about. `BACKEND_DEV` is chosen because it has a curated entry for every universe (no hash-fallback branch is taken), so the colour quality is the same as if the user had picked `Backend Dev`.
- The user does not see the accent colour come from the archetype — it's a one-shade design hint inside the text overlay — so the conscious "custom roles get the same shade" trade-off is acceptable.

**Alternatives considered**:
- Hash the custom-role string into one of six slots — rejected: introduces non-obvious determinism across deploys and makes future palette curation harder.
- Introduce a `defaultArchetype` constant on the resolver — equivalent to the chosen design with extra ceremony; rejected.
- Roll a random archetype for accent only (mirrors 020's Pose/Vibe approach) — rejected: turns a stable identity (`Tester` always looks the same) into a noisy one.

---

## R5 — Making the prefab grid genuinely keyboard-inert while custom is active

**Decision**: When `customRole.trim()` is non-empty, the `SelectionGrid` underlying `ArchetypeGrid` renders with all of:

1. **CSS** — class `is-disabled` adds `filter: blur(2px)`, `opacity: 0.5`, `pointer-events: none`, `cursor: not-allowed`.
2. **Focus management** — every option button gets `tabIndex={-1}` regardless of selected-state.
3. **A11y** — the wrapping `<div role="radiogroup">` gains `aria-disabled="true"`; each `<button>` also gets `aria-disabled="true"`.
4. **Defense-in-depth** — the click handler short-circuits (`if (disabled) return`) so even if a custom CSS override or a synthesised event bypasses `pointer-events`, the reducer never receives `ArchetypeSelected`.

**Why not `inert`?** Native `inert` is the cleanest solution (it handles focus + click + AT in one attribute) but browser support for the JSX `inert` prop arrived in React 19 only — and the codebase still has tests running under older test-runtime DOM polyfills. Stacking `aria-disabled` + `tabIndex={-1}` + `pointer-events: none` + click-guard is a portable equivalent that needs no extra polyfill. (We may migrate to `inert` in a follow-up once the test-runtime baseline updates.)

**Keyboard contract**:
- Tab from inside the grid: focus skips every prefab pill (tabIndex=-1) and lands on the custom-role input.
- Tab from outside the grid into the custom-role input: works the same as today.
- Arrow keys inside the grid while disabled: no-op (the SelectionGrid arrow handler short-circuits on `disabled`).
- Space / Enter inside the grid while disabled: no-op (button is `aria-disabled` and click handler short-circuits).
- Tab from custom-role input → Tab to the `X` button → Tab to subsequent grids (Universe / Art Style).

**Alternatives considered**:
- Use `<fieldset disabled>` — rejected: `<fieldset>` with `disabled` propagates to nested form controls but `<button type="button">` outside form-submission semantics has inconsistent behaviour across browsers; also clobbers our custom `role="radiogroup"` semantics.
- Render the grid conditionally (unmount when custom is active) — rejected: loses the "the prefab is visibly blurred" affordance from FR-2205 ("the user can still see they're being overridden").

---

## R6 — Reducer interaction between `CustomRoleChanged` and prefab selection

**Decision**: The reducer adds a single new action and adjusts two existing branches:

```
{ type: 'CustomRoleChanged'; customRole: string }     // NEW
```

Reducer logic:

| Action | New behaviour |
|---|---|
| `CustomRoleChanged` (incoming `customRole.trim().length > 0`) | sets `customRole`; sets `archetype: null`; transitions `phase: 'picking'` |
| `CustomRoleChanged` (incoming trimmed empty) | sets `customRole` to the raw value (which may be whitespace); leaves `archetype` untouched; `phase: 'picking'` |
| `ArchetypeSelected` | unchanged. (UI never dispatches this while custom is active because the grid is disabled; the reducer trusts the dispatch — see R5 defense-in-depth.) |
| `SurpriseMePicked` | clears `customRole: ''` in addition to its existing field overwrites (FR-2209). |
| `StartOverRequested` | unchanged — returns `initialAlterEgoSession()` which has `customRole: ''`. |

**Rationale**: The dispatch from `CustomRoleChanged` is the *single decision point* for the "custom claims precedence" rule. Letting the SelectionGrid stay enabled and trusting the reducer to ignore late `ArchetypeSelected` would be incorrect: the user could still *visually pick* a pill that wouldn't apply, contradicting the spec's "blurred + un-clickable" guarantee.

Also: we do NOT track "the most-recent prefab pick" in a separate field for accent-resolver purposes. The default per R4 is fully sufficient and avoids a parallel state field.

**Alternatives considered**:
- Have the action clear `archetype` only on the **first** non-blank keystroke — rejected: makes the reducer stateful in an irrelevant way; the trimmed-non-empty rule is idempotent and stateless.
- Reset `customRole` to empty on any `ArchetypeSelected` — rejected: never fires in practice (the grid is disabled while custom is non-empty), but if it did fire it would be a UI bug, not a reducer concern.

---

## R7 — Prompt builders and bio prompt: one branch-free path for custom + prefab

**Decision**: Both image-prompt builders (`GeminiPromptBuilder`, `FalAiPromptBuilder`) and the bio prompt builder (`GeminiCharacterPromptBuilder`) read **`request.roleLabel()`** instead of `label(ROLE_LABELS, request.archetype())`. The helper is defined on `AlterEgoRequest`:

```java
public String roleLabel() {
    if (customRole != null && !customRole.isBlank()) {
        return customRole.trim();
    }
    return archetype != null
            ? archetype.label()
            : "Engineer"; // unreachable per validator, defensive default
}
```

The prompt line stays:

```
- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): <roleLabel()>
```

**Rationale**:
- The 021-introduced no-rendered-text framing applies *to the label slot* — substituting a custom string for the prefab label does not change the framing. Whether the model reads "Cloud Architect" or "Tester" in that slot, the surrounding instructions ("render as visual cues — NOT as text") are identical.
- Keeping a single code path simplifies the SC-2206 validation: the human-review sample for the no-text guarantee covers both prefab and custom cases uniformly.

**Note**: The image-prompt builder's wording uses "Engineering role" as the label slot's preamble. With non-engineering roles in the prefab pool (HR / Administration / Customer Relations) the wording becomes slightly misleading but remains functional — the model treats it as "Role" regardless. We deliberately **do not** rephrase the preamble in this feature to preserve the byte-for-byte fixture pinned by the 021 unit tests. Rephrasing is a candidate cleanup for a separate follow-up; the spec does not require it.

**Alternatives considered**:
- Rephrase the preamble to "Role" or "Persona" — rejected for now (out of scope, would break the 021 fixture).
- Different prompt template per role type — rejected: zero behavioural benefit, more surface to maintain.

---

## R8 — Poster text overlay: no harder truncation than today

**Decision**: `PosterTextOverlayService` (017) reads `request.archetype().label()` today; the call site at `AlterEgoService.java:138` + `:184` switches to `request.roleLabel()`. The overlay's existing layout / truncation / kerning rules (see specs/017-poster-text-overlay) handle long custom strings the same way they handle the longest prefab label (`"Customer Relations"` is now the longest at 18 chars, comparable to existing `"Cloud Architect"` at 15 / `"Renaissance Portrait"` is 20 in the Art-Style line, etc.). Strings ≤ 100 chars that exceed the render-width budget get the existing ellipsis treatment.

**Rationale**: The overlay was designed to handle variable-width text gracefully. No additional clamp is required at the role-of-record layer — the spec FR-2215 explicitly defers truncation to the existing overlay path.

**Alternatives considered**:
- Pre-truncate to 30 characters at the DTO layer — rejected: arbitrary number, redundant with the overlay's own measurement-based truncation.

---

## R9 — Testing the no-rendered-text guarantee for custom strings without OCR

**Decision**: Continue the 021 human-review sampling approach (SC-2102 → SC-2206). The CI gate is a Vitest contract test that asserts the role-of-record string is passed through the request body to the wire layer and that the rendered text **overlay** displays it (UI test, not LLM test). The "did the LLM honour the no-text instruction" check stays a manual / periodic review.

**Rationale**: Adding OCR (e.g. Tesseract-WASM) to CI is a non-trivial dependency commitment that the constitution Principle I would flag. The 021 baseline accepted this manual-validation trade-off; extending the same trade-off to custom roles is consistent and proportional.

**Alternatives considered**:
- Add Tesseract-WASM to the test suite — rejected (constitution Principle I + flaky).
- Pin a list of "test custom strings" with known-good generated images — rejected: requires the LLM to be deterministic across model updates.

---

## R10 — Icons for the three new prefab options

**Decision**: Use existing `lucide-react` icons (already a dependency — `frontend/package.json`):

| Option | Wire value | Label | Icon (lucide-react) |
|---|---|---|---|
| HR | `hr` | `HR` | `Users` (group of two people — universally read as HR / people-ops) |
| Administration | `administration` | `Administration` | `ClipboardList` (admin / process imagery) |
| Customer Relations | `customer-relations` | `Customer Relations` | `MessageCircle` (conversation / support imagery) |

Each icon is rendered through the existing `SelectionGrid` pill pattern (18 px, `strokeWidth={1.75}`, `aria-hidden` wrapper) — no styling change.

**Rationale**: All three icons are stock `lucide-react` exports — no SVG authoring, no dependency churn. They visually distinguish the three new categories at a glance and follow the existing "one Lucide icon per option" convention from 002 / 006.

**Alternatives considered**:
- Inline custom SVGs — rejected: more files, harder to keep stylistically consistent with the existing pills.
- Reuse already-exported icons in `options.ts:113` (e.g. `Camera`, `Aperture`) — rejected: those are decorative-cluster icons reserved for atmosphere; misusing them in a selection pill would be visually confusing.

---

## Summary of resolved questions

All technical-context unknowns flagged in the spec's Assumptions and in the plan's Technical Context have a concrete decision above. **No NEEDS CLARIFICATION markers remain. Ready for Phase 1.**

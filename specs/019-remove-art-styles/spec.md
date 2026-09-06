# Feature Specification: Remove Line Art, Low-Poly 3D, and Pixel Art from Art Style category

**Feature Branch**: `019-remove-art-styles`
**Created**: 2026-05-11
**Status**: Draft
**Input**: User description: "checkout github project issue #49"
**Linked issue**: [#49 — Remove Line Art, Low Poly 3D Art and Pixel Art](https://github.com/squer-solutions/aiavatar/issues/49)

## Summary

The Art Style category (added in feature 006) currently offers nine options. Three of those — **Line Art**, **Low-Poly 3D**, and **Pixel Art** — should no longer be selectable by users. They must disappear from the manual Setup grid **and** from the pool the "Surprise Me" randomiser draws from. The remaining six Art Style options keep their existing wire values, labels, ordering, and behaviour. No other category (Pose, Archetype, Universe, Vibe) and no other surface (generation pipeline, validation, gating, animations) changes.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Manual Setup hides the three retired options (Priority: P1)

A user navigates to the Setup tab and opens the Art Style category. They see six options to pick from; Line Art, Low-Poly 3D, and Pixel Art are not present anywhere in the grid.

**Why this priority**: This is the user-visible heart of the change. Without it, the issue is not closed.

**Independent Test**: Open the Setup tab in a clean session — the Art Style grid renders exactly six tiles, and none of them is Line Art, Low-Poly 3D, or Pixel Art. Confirms FR-1901 in isolation.

**Acceptance Scenarios**:

1. **Given** a fresh session on the Setup tab, **When** the user looks at the Art Style category, **Then** exactly six options are offered (oil painting, watercolor, pop art, Renaissance portrait, Japanese woodblock, cel-shaded), in their existing relative order, with their existing labels and tile imagery.
2. **Given** the Setup tab is open, **When** the user attempts to scroll, search, or otherwise reach Line Art, Low-Poly 3D, or Pixel Art, **Then** none of those options appear anywhere in the UI.
3. **Given** the user has selected an Art Style and the remaining four categories, **When** they press Generate, **Then** the generation pipeline behaves exactly as before — the change is invisible to anything downstream of the user picking a style.

---

### User Story 2 — Surprise Me never picks a retired option (Priority: P1)

A user presses "Surprise Me" on the Setup tab. The randomiser commits one pick per category, including Art Style; the Art Style pick is always one of the six remaining options. Repeated presses never yield Line Art, Low-Poly 3D, or Pixel Art.

**Why this priority**: Surprise Me is a parallel path into Art Style selection. If we only fix the manual grid, the randomiser would still be able to commit a retired value and break the contract.

**Independent Test**: With the manual grid hidden, press Surprise Me many times in succession (or with a deterministic seed in tests) and confirm the committed Art Style is always drawn from the six remaining options. Confirms FR-1902 in isolation.

**Acceptance Scenarios**:

1. **Given** a session with photo + first name supplied, **When** the user presses Surprise Me, **Then** the Art Style value that gets committed to the session is always one of the six remaining options.
2. **Given** repeated Surprise Me presses across a session, **When** the picks are observed over many trials, **Then** Line Art, Low-Poly 3D, and Pixel Art are never selected.
3. **Given** the user presses Surprise Me, **When** they then look at the Setup tab, **Then** the Art Style tile that is shown as picked matches the value committed and is one of the six remaining options.

---

### Edge Cases

- **Replay of a pre-change Surprise Me / manual pick within the same browser tab**: Not possible — sessions are in-memory only (no persistence per 001 FR-016 / FR-024) and the option set is fixed at the moment the page is loaded. Once the new build is deployed, no fresh session can carry a retired Art Style value.
- **Stale tab held open across the deploy**: A user who loaded the previous build before the deploy still sees nine options in their tab. If they keep that tab and press Generate with a retired Art Style, the backend (which has also been updated) must treat the retired value as an unknown Art Style and reject the request consistently with FR-1903 — the user sees the same error they would for any other invalid Setup selection. There is no special-case fallback and no silent substitution.
- **No selection yet**: Behaviour is unchanged from feature 006/007 — Generate stays gated until the user picks an Art Style (now from six options instead of nine).
- **Reduced-motion / accessibility**: Removing tiles must not regress any existing keyboard navigation order, focus ring, ARIA labelling, or `prefers-reduced-motion` behaviour in the Art Style grid.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-1901**: The Setup Art Style category MUST offer exactly six options to the user: oil painting, watercolor, pop art, Renaissance portrait, Japanese woodblock, cel-shaded. Line Art, Low-Poly 3D, and Pixel Art MUST NOT appear in the Art Style grid in any form (tile, label, list item, accessible-name, tooltip, or hidden control).
- **FR-1902**: The "Surprise Me" randomiser MUST draw its Art Style pick only from the six remaining options. Line Art, Low-Poly 3D, and Pixel Art MUST be impossible outcomes — both in normal interactive use and under any deterministic seed used by tests.
- **FR-1903**: If a Generate request arrives carrying an Art Style value that is no longer offered (e.g. from a stale tab loaded before the deploy), the system MUST reject it consistently with how it already handles any other unknown / invalid Setup field — there is no special-case fallback, no silent substitution, and no persistence of the rejected value.
- **FR-1904**: The relative ordering, on-screen labels, wire values, tile imagery, and tooltip / accessible-name text of the six remaining Art Style options MUST be preserved unchanged. No renaming, no reshuffling, no re-illustration.
- **FR-1905**: No other Setup category (Pose, Archetype, Universe, Vibe) MUST be touched. Their options, ordering, labels, wire values, and randomiser behaviour all remain as in feature 006.
- **FR-1906**: All upstream gating (per feature 007) and downstream behaviours — Generate's enable rule, the tab-transfer animation (005), the progress indicator (013), the poster frame (015) / poster text overlay (017) / branding overlay (008) compositing chain, the Print flow (010 + 018), and the no-persistence guarantee (001 FR-016 / FR-017 / FR-024) — MUST continue to apply unchanged for the six remaining Art Styles.
- **FR-1907**: After the change ships, no surface (UI, request body emitted by the UI, randomiser, prompt-builder label map, validation messages, error responses) MUST emit or accept Line Art, Low-Poly 3D, or Pixel Art as an Art Style. There is no migration path and no "deprecated but still accepted" period.

### Key Entities

- **Art Style option set**: The closed set of values the user may pick (manually or via Surprise Me) for the Art Style category. This feature reduces it from nine members to six. All other Setup categories remain unaffected.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-1901**: 100 % of fresh page loads show exactly six Art Style tiles in the Setup grid, none of which is Line Art, Low-Poly 3D, or Pixel Art.
- **SC-1902**: Across at least 1 000 simulated Surprise Me draws (in tests, with a uniform RNG), 0 of the committed Art Style picks are Line Art, Low-Poly 3D, or Pixel Art.
- **SC-1903**: 0 % of Generate requests produced by the post-change UI carry Line Art, Low-Poly 3D, or Pixel Art as their Art Style value.
- **SC-1904**: 100 % of the six remaining Art Style options keep their pre-change label, wire value, tile imagery, and relative position in the grid — verifiable by inspecting the rendered Setup tab against the feature 006 spec.
- **SC-1905**: 100 % of pre-existing acceptance scenarios for features 001–018 that do not specifically depend on Line Art, Low-Poly 3D, or Pixel Art continue to pass unchanged.

## Assumptions

- **No persistence to migrate**: Sessions live only in browser memory (001 FR-016 / FR-024). There is no stored selection, no saved poster, no user history that could still reference a retired Art Style — so this change needs no data-migration plan.
- **Stale-tab risk is acceptable**: The window during which a user could still have a pre-deploy tab open is bounded by how long they keep the tab alive. Treating any retired-value Generate request like any other invalid request (FR-1903) is sufficient; no warm-over compatibility shim is required.
- **No backend hard-coding of the removed values is load-bearing elsewhere**: The Art Style options exist as a closed set introduced in feature 006 specifically to drive the Setup grid, the Surprise Me randomiser, and the prompt-builder label map (per the 006 changelog entry). Removing three members from that closed set does not break any other feature, because no later feature relies on those three values specifically.
- **The change is non-cosmetic for the remaining options**: The six survivors are not being relabelled, reordered, or re-illustrated — only the three retirements happen.
- **Test discipline**: Any tests that today assert "nine Art Style options" or that exercise Line Art, Low-Poly 3D, or Pixel Art specifically will be updated to reflect the new six-member set. Tests that exercise the remaining six options are expected to pass unchanged.

# Phase 0 — Research: 019-remove-art-styles

**Feature**: Remove Line Art, Low-Poly 3D, and Pixel Art from Art Style category
**Status**: Complete — no `NEEDS CLARIFICATION` markers remain on the spec; this document records the research that allowed the plan to be written with confidence.

The change is mechanical (subtractive). There are no novel technology choices, no integration patterns to invent, and no dependencies to vet. The "research" below is therefore an audit of where the retired values live so the implementation tasks can be confident-and-complete rather than discovery work.

---

## R1. Surprise Me draws from the same array as the manual grid — single source of truth

**Decision**: Edit only `ART_STYLE_OPTIONS` (and the matching type alias) on the frontend. Both the grid renderer and the randomiser will pick up the trimmed list automatically.

**Rationale**:

- `frontend/src/features/alterego/components/ArtStyleGrid.tsx:1` imports `ART_STYLE_OPTIONS` and passes it directly to its renderer at line 20 — no per-component allow-list, no filtering.
- `frontend/src/features/alterego/lib/randomSelections.ts:15,62` imports the same array and calls `pickOne(ART_STYLE_OPTIONS, rng).value` — the randomiser pool is literally the option array.
- This means `ART_STYLE_OPTIONS` is **the** source of truth for "what the user can select", manually or randomly. Trimming three rows from it satisfies both FR-1901 and FR-1902 with no per-call-site change. This is exactly the property the spec's "single closed set" framing assumed.

**Alternatives considered**:

- *Filter at the Surprise Me call site* — would keep retired values selectable in the manual grid; rejected, contradicts FR-1901.
- *Filter at the grid call site* — would keep retired values selectable in the randomiser; rejected, contradicts FR-1902.
- *Introduce a second `ENABLED_ART_STYLE_OPTIONS` alongside the existing one* — adds a fork-in-truth that future devs would have to remember; the constitution-aligned answer is to delete dead options outright (matches Principle VI "remove deprecated" thinking applied to UI options).

---

## R2. TypeScript type alias is the compiler-enforced fixture finder

**Decision**: Trim `ArtStyle` in `frontend/src/features/alterego/types.ts` first; let `tsc --noEmit` light up the exhaustive list of test fixtures that need updating.

**Rationale**:

- `ArtStyle` is a string-literal union, not just a `string`. Every fixture that writes `artStyle: 'pixel-art'` (etc.) is a typed assignment, so removing the literal from the union immediately becomes a compile error at that site. This converts a "find all references to a string" search problem into a guaranteed-complete compiler diagnostic — a foundational TDD safety net.
- The current grep already shows ~14 frontend test files referencing retired values; the compiler will not let any of them slip through, including any future test added in a parallel branch before this lands.

**Alternatives considered**:

- *Leave the type alias and only trim the options array* — would compile but allow stale fixtures to keep emitting retired wire values silently. Rejected; weakens FR-1907.
- *Add a `@deprecated` JSDoc on the three values* — does not actually remove them from the type set; rejected, contradicts the spec's "no deprecated-but-accepted period" stance.

---

## R3. Backend enum `fromWire` already implements FR-1903

**Decision**: No new validation code is needed for stale-tab requests. Remove `PIXEL_ART`, `LOW_POLY_3D`, `LINE_ART` from `ArtStyle.java`; the existing `fromWire` lookup throws `IllegalArgumentException("Unknown artStyle: " + value)` (`backend/src/main/java/com/aiavatar/alterego/model/ArtStyle.java:50-55`), which Spring's Jackson layer surfaces as a deserialisation failure → 400 problem-detail response — the same path any other unknown enum value follows today.

**Rationale**:

- The `@JsonCreator`-annotated `fromWire` is invoked during JSON binding for the `Selections.artStyle` field. Once the three constants are gone, a request body of `{"artStyle":"pixel-art", …}` deserialises to a `MethodArgumentNotValidException` or `HttpMessageNotReadableException` — both already mapped to RFC 7807 problem-detail 400s by the existing controller-advice (see `AlterEgoControllerErrorContractTest.java`).
- FR-1903 explicitly asks for "the same path any other invalid Setup field follows" — that path exists and is tested.

**Alternatives considered**:

- *Add a custom @ControllerAdvice handler for "retired art style"* — adds a special case for a transient stale-tab condition; rejected, contradicts FR-1907's "no deprecated-but-accepted period" and FR-1903's "consistent with how it already handles any other unknown / invalid Setup field".
- *Map retired values to a default (e.g. `oil-painting`) server-side* — silent substitution; rejected, contradicts FR-1907.

---

## R4. Prompt-builder label maps are dictionary keys, not control flow

**Decision**: Drop the three `ART_STYLE_LABELS.put(ArtStyle.PIXEL_ART/LOW_POLY_3D/LINE_ART, …)` entries from `GeminiPromptBuilder.java:55-59`, `GeminiCharacterPromptBuilder.java:75-77`, and `FalAiPromptBuilder.java:60-64`. Nothing else in those builders branches on the retired values.

**Rationale**:

- The label maps are pure lookup tables. Once the enum no longer contains the retired members, leaving the map entries would be a compile error (`Cannot resolve symbol PIXEL_ART`) — so removal is forced by the enum edit, not an optional cleanup.
- No prompt-builder code path special-cases the three retired styles in any other way (verified by grep across `service/gemini/` and `service/falai/`).

**Alternatives considered**:

- *Map retired values to a generic "painterly" label* — only matters if we accepted retired wire values, which FR-1907 forbids. Rejected.

---

## R5. The 006 OpenAPI contract is the wire-spec source of truth — needs a 019 delta

**Decision**: Publish a `specs/019-remove-art-styles/contracts/alter-egos.openapi.yaml` delta that redeclares the `ArtStyle` enum with six values (the 006 contract listed nine). Document the change as backwards-incompatible (a retired-value request now 400s) per FR-1903 + FR-1907.

**Rationale**:

- The 006 spec set the precedent that "wire values are kebab-case public contract from first merge" (FR-310 in 006). Removing wire values is the inverse of adding them, and deserves the same paper trail.
- The 019 contract delta lets `/speckit.analyze` and human reviewers compare against 006 and confirm the only schema change is the enum cardinality.

**Alternatives considered**:

- *No contract delta; rely on the spec prose* — weakens the audit trail; rejected.
- *Bump a version on the API* — overkill for a stale-tab acceptable-loss scenario the spec explicitly chose (Assumptions: "Stale-tab risk is acceptable").

---

## R6. Test churn is bounded and mechanical — no test rewrite

**Decision**: Edit existing test fixtures in-place; do not delete or restructure tests except for the three explicit "test the retired value" cases (which are deleted outright because the behaviour they test no longer exists).

**Audit findings** (from `grep -rln "pixel-art|low-poly-3d|line-art|PIXEL_ART|LOW_POLY_3D|LINE_ART"` over `frontend/src` + `backend/src`):

- **Frontend touch-points** (~14 files): `types.ts`, `options.ts`, plus tests in `state/`, `components/`, `hooks/`, `services/`, `lib/`. All-but-`types.ts`-and-`options.ts` are tests using one of the three retired values as a *placeholder fixture*. Pattern: `artStyle: 'pixel-art'` in a `Selections` literal — these need a one-token swap to a surviving value (`'oil-painting'` is the natural default — first in the array, broadly representative).
- **Backend touch-points** (~31 files): `ArtStyle.java`, 3 prompt-builders, and ~28 tests. Tests use `PIXEL_ART` / `LOW_POLY_3D` / `LINE_ART` as placeholder fixtures in `Selections` builders or `ArtStyle.values()`-cardinality assertions. Pattern: same one-token swap; `OIL_PAINTING` is the natural default.
- **Genuine "test the retired value"** cases:
  - `EnumsTest.java` asserts the nine-member set explicitly — update to six.
  - `GeminiPromptBuilderTest`, `GeminiCharacterPromptBuilderTest`, `FalAiPromptBuilderTest` each have a `@ParameterizedTest` row per ArtStyle value — delete the three retired-value rows.
  - `randomSelections.test.ts` line 65/74/115 picks the first / last element of `ART_STYLE_OPTIONS` deterministically; the index-bound assertions stay correct because they read from the (now-shorter) array.
  - `randomSelections.test.ts` line 86 already asserts "every drawn artStyle is a member of `ART_STYLE_OPTIONS`" — that test gains strength under the new six-member set with no source change. We additionally add an explicit 1 000-draw guard for SC-1902.

**Rationale**: This is exactly the "delete dead options outright" scenario — mechanical edits with no architectural restructuring. The 90 % coverage gate is preserved because removed production lines and removed test lines balance.

---

## R7. No-persistence posture inherited unchanged

**Decision**: Cite the existing 001 FR-016 / FR-017 / FR-024 + 006's "no persistence" stance in the data-model file; do not introduce any new persistence surface or any "art-style history" anywhere.

**Rationale**: The spec's Assumption "No persistence to migrate" is the actual technical reality — there is nowhere to migrate from, and no need for a feature-flag rollout. The deploy *is* the migration.

---

## Summary

All NEEDS CLARIFICATION items resolved. No new technology, no new dependency, no new pattern. The plan is a coordinated subtractive edit across one frontend type alias, one frontend options array, one backend enum, and three backend prompt-builder label maps, plus mechanical fixture updates in the surrounding test suite. The 019 OpenAPI contract delta records the wire-spec change, and the existing `fromWire` lookup serves FR-1903 without new code.

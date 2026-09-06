# Research — 020 Hide Vibe and Pose Categories from UI

Phase 0 output. Each block is one decision the plan depends on.

## Decision 1 — Where the random pick happens

**Decision**: Server-side, inside `AlterEgoService`, immediately before the resolved `AlterEgoRequest` is handed to the prompt builders. The pick is performed by a new Spring component `RandomCategorySelector`.

**Rationale**:
- Spec FR-2025 makes the server authoritative: any client-supplied Pose / Vibe MUST be ignored. The only way to enforce this without trust assumptions is for the value to be chosen on the server, after deserialization.
- Keeps the three prompt builders (`GeminiPromptBuilder`, `GeminiCharacterPromptBuilder`, `FalAiPromptBuilder`) untouched. FR-2040 + FR-2041 require prompt-building behaviour to remain identical.
- Tests for randomness can be focused on one tiny component (`RandomCategorySelector`) without dragging in MVC, multipart parsing, or HTTP wiring.

**Alternatives considered**:
- *Pick on the frontend right before sending the HTTP request.* Rejected: a tampered frontend / curl client could pin a value, defeating FR-2025. Also leaks the option lists to the client unnecessarily.
- *Pick inside the controller method, then pass to a now-stateful `AlterEgoRequest` builder.* Rejected: pollutes the controller with business logic and makes service-layer tests harder.
- *Pick inside each prompt builder.* Rejected: three callsites; three reasons to drift apart over time.

## Decision 2 — How "ignore client-supplied pose / vibe" is enforced

**Decision**: Drop `pose` and `vibe` from the public DTO entirely by introducing a new record `AlterEgoUserSelections`. The controller deserializes this; the existing `AlterEgoRequest` becomes a server-internal record built by the service after rolling.

**Rationale**:
- Strongest possible enforcement: the field literally does not exist on the deserialized type, so there is no place for the value to be carried into the service even by accident.
- Jackson's default `FAIL_ON_UNKNOWN_PROPERTIES=false` posture means stray `pose` / `vibe` keys in JSON are silently ignored — verified by an explicit contract test.
- Bean-Validation surface shrinks correspondingly: `@NotNull Pose pose` is gone; the (already-optional) `Vibe vibe` is gone.

**Alternatives considered**:
- *Keep `pose` and `vibe` on the DTO, `@JsonIgnore` them.* Rejected: `@JsonIgnore` on a Java record component is awkward (requires either accessor override or `@JsonProperty(access = WRITE_ONLY)`); leaves a dangling field on the type that confuses future readers.
- *Validate that client-supplied values are absent and 400 on presence.* Rejected: breaks soft compatibility with any in-flight clients that still send the field. Spec only requires the server to *ignore* them; rejecting them is unnecessarily strict.

## Decision 3 — Split `AlterEgoRequest`, or keep it as the prompt-builder input?

**Decision**: Split. `AlterEgoUserSelections` is the public DTO (no Pose / Vibe). `AlterEgoRequest` keeps its existing field set and becomes a server-internal "resolved" record constructed by `AlterEgoService` after the roll.

**Rationale**:
- Three prompt builders (`GeminiPromptBuilder`, `GeminiCharacterPromptBuilder`, `FalAiPromptBuilder`) and their label maps, the `BrandingOverlayService` callsite, and existing tests all consume `AlterEgoRequest` today. Changing that signature would force edits across at least 6 files and ~12 tests with no behaviour change.
- Splitting the public DTO from the resolved request is also the standard Spring posture for cases where server-derived fields belong in the same business object.
- Naming: "User selections" is what the user controls; "resolved request" (kept as `AlterEgoRequest` for path-length reasons) is the full set the prompt builders need.

**Alternatives considered**:
- *Pass `(AlterEgoUserSelections, Pose, Vibe)` as three arguments to every prompt builder.* Rejected: three callsites × three signatures = nine edits with no upside.
- *Make `AlterEgoRequest` carry only the user fields and pass Pose/Vibe via a side channel.* Rejected: prompt builders would then need their interface widened anyway, and the "label maps live next to the field" colocation is lost.

## Decision 4 — Random generator choice

**Decision**: `java.util.random.RandomGenerator` (Java 17+ interface) with the default `RandomGenerator.getDefault()` algorithm (currently `L32X64MixRandom` on Java 21). Constructor-injectable on `RandomCategorySelector` so tests can pass a deterministic implementation.

**Rationale**:
- Not security-sensitive: a participant guessing the next Pose has no real-world consequence. `SecureRandom` would be overkill and twice as slow.
- `RandomGenerator` is the modern stdlib API; `ThreadLocalRandom` is also acceptable but cannot be swapped in tests as cleanly.
- Uniform integer-in-range is `RandomGenerator.nextInt(bound)`, one call per category, O(1).

**Alternatives considered**:
- *`SecureRandom`*: rejected — irrelevant guarantees, slower.
- *`ThreadLocalRandom.current()` directly*: rejected — harder to deterministically stub in tests.
- *Math.random-style on the JVM*: rejected — global state, not unit-testable.

## Decision 5 — Frontend reducer: keep or drop `pose` / `vibe` fields?

**Decision**: Drop. The `AlterEgoSession` interface loses both fields. The `PoseSelected` and `VibeSelected` actions are deleted. The `Pose` and `Vibe` type aliases are deleted from `frontend/src/features/alterego/types.ts`. The `POSE_OPTIONS` and `VIBE_OPTIONS` arrays are deleted from `options.ts`.

**Rationale**:
- Spec User Story 1 acceptance scenario 2: "no element labelled 'Pose' or 'Vibe' appears anywhere in the Setup tab, including hidden / off-screen elements." Dormant reducer state is a precursor to dormant UI.
- Keeping the fields would force selectors, serializers, and tests to carry forever-null branches that future readers would correctly question.
- Delete-not-deprecate matches the repo's posture in feature 019 (retired Art Style options were removed, not hidden).

**Alternatives considered**:
- *Keep the fields, mark them deprecated.* Rejected: feature 019 set the precedent for removal.
- *Keep the fields for "future re-enabling".* Rejected: speculative; spec is explicit that hidden categories should not be re-exposed without a new feature.

## Decision 6 — Surprise Me semantics

**Decision**:
- `randomSelections()` now returns picks for only **Archetype**, **Universe**, **ArtStyle**. The `SurpriseMePicks` interface drops `pose` and `vibe`.
- The `SurpriseMePicked` action shape changes accordingly.
- `useGenerateAlterEgo.surprise()` no longer passes Pose / Vibe — they will be rolled server-side, identically to Generate.

**Rationale**:
- Single source of truth: the server rolls Pose / Vibe on both paths. The frontend stops doing it for Surprise Me because there is no UI to populate.
- Eliminates a confusing seam where Surprise Me would client-pick a Pose/Vibe value that the server would then throw away. (See Decision 1.)

**Alternatives considered**:
- *Have Surprise Me commit Pose/Vibe to a hidden corner of session state and send it.* Rejected: the server ignores it anyway (Decision 2), so it's pure churn.

## Decision 7 — Behaviour when an enum is empty

**Decision**: `RandomCategorySelector.pickUniform(Class<E>)` throws `IllegalStateException` if `clazz.getEnumConstants()` has zero elements. The exception is unhandled at the service layer (i.e., bubbles up to the controller, which already maps unhandled exceptions to a 5xx that the resilient frontend converts to its fallback state per Principle IV).

**Rationale**:
- Spec FR-2024 requires fail-closed behaviour: no partial request with missing Pose / Vibe must leave the server.
- An empty enum is a programmer error caught at the earliest possible point. The existing controller error-handling + frontend fallback already covers this case; no new path needed.
- Java enums are non-empty by construction at compile time, so this branch is purely defensive — but it costs ~3 lines and one unit test, so it stays.

**Alternatives considered**:
- *Silently fall back to a hardcoded default*: rejected — masks misconfiguration and produces silently-degraded output.

## Open questions

None remain. All `[NEEDS CLARIFICATION]` markers from `spec.md` were resolved during specification (zero markers shipped). The four implementation-detail "left to plan" items from the spec are answered above (Decisions 1, 3, 5, 6).

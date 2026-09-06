# Research: Surprise Me Button (009)

Spec: [./spec.md](./spec.md) · Plan: [./plan.md](./plan.md)

## R1 — Where does the randomization live?

**Decision**: A standalone pure utility `randomSelections(rng?)` under `frontend/src/features/alterego/lib/`. Consumed by `useGenerateAlterEgo.surprise()`; not by the reducer, not by the button component.

**Rationale**: The randomizer has zero dependence on React and on session state. Putting it in a `lib/` utility matches the project's existing convention for pure helpers (`squareCrop`, `downscalePhoto`, `cameraCapability`). That convention lets the test suite exercise the full branch space with a seeded RNG without mounting a component or standing up a reducer.

**Alternatives considered**:

- **In the button's `onClick`**: rejected — inline randomization would mix UI concerns with domain logic and block deterministic tests that don't need to mount the component.
- **In the reducer**: rejected — reducers must be pure functions of `(state, action)`. Injecting `Math.random` into a reducer violates that contract; injecting it into the action payload pushes randomness to the caller, which is exactly the "utility" approach.
- **In `useGenerateAlterEgo` inline**: rejected — the hook would grow from ~90 lines to ~130, and the randomizer would have no standalone test.

## R2 — One aggregate action vs five chained dispatches?

**Decision**: A single new reducer action `SurpriseMePicked` that commits pose / archetype / universe / artStyle / vibe in one transition.

**Rationale**:

- Five chained `*Selected` dispatches work, but they create five sequential reducer invocations and five potential intermediate states visible to subscribers, each of which fires selector re-evaluations. That's wasted work, and — more importantly — each intermediate state is a technically-invalid "partially seeded" session that a selector could momentarily react to.
- A single action keeps the reducer-invariant surface small: before the action, all five category fields could be anything; after the action, all five have valid values. The test is one line: `expect(result.pose && result.archetype && result.universe && result.artStyle && result.vibe).toBeTruthy()`.
- Action naming convention matches the existing action vocabulary (`PhotoSelected`, `PoseSelected`, `GenerateSubmitted`, `StartOverRequested` — verb-past-tense, one action per domain event).

**Alternatives considered**:

- **Five chained dispatches** (listed above).
- **Reusing `*Selected` actions but wrapping them in `ReactDOM.unstable_batchedUpdates`**: rejected — React 18+ auto-batches synchronous dispatches, but the "intermediate state visible to a subscriber" risk is still there for async consumers (React Query's `onSuccess` handler, for instance). The aggregate action is simpler and strictly more correct.

## R3 — RNG seam for deterministic tests

**Decision**: `randomSelections(rng: () => number = Math.random)`. Production call sites pass nothing; tests pass a seeded generator.

**Rationale**: `Math.random` has no seed API, which makes assertion-based tests flaky. Injecting the RNG as a default-parameterised argument is the minimal seam: zero bundle cost, zero runtime cost at production call sites, maximal assertability in tests. No third-party PRNG library is needed; tests can inline a tiny Mulberry32 or LCG if they want repeatable draws. A single-call test can just stub `rng` with `() => 0` (picks index 0 in every category).

**Alternatives considered**:

- **Module-level import of `Math.random`**: rejected — tests would need `vi.spyOn(Math, 'random')` per test, which is state-leaky and forces cleanup in `afterEach`.
- **`crypto.getRandomValues`**: rejected — A-902 documents that cryptographic strength is not required. Using it here would also couple tests to the Web Crypto API surface.
- **Accept the RNG on every `pickOne` call**: kept — `pickOne<T>(list, rng)` and `randomSelections(rng?)` share the same seam. Tests assert both.

## R4 — Reuse strategy for `useGenerateAlterEgo`

**Decision**: Add a `surprise()` method on the hook that (1) computes `randomSelections()`, (2) dispatches `SurpriseMePicked` with the picks, (3) calls the existing `submit({ photoBlob, selections })` with the picks. `submit`'s `onMutate` is unchanged — it still dispatches the auto-tab-switch and `GenerateSubmitted` in the same order.

**Rationale**: The mutation, the `onMutate` ordering, the `onSuccess`/`onError` branching, the 003 fallback semantics, and the 005 animation nonce are all already correct for a Generate click. Reusing them verbatim guarantees FR-908 (byte-identical post-generation behavior) at zero cost. Exposing `surprise()` as a named sibling of `submit()` keeps the call site (`AlterEgoPage.handleSurprise`) readable and preserves a single source of truth for the happy-path sequence.

**Alternatives considered**:

- **Inline the whole flow in the button's `onClick`**: rejected — two copies of the `ActiveTabChanged → 'alter-ego' (reason: 'generate')` + `GenerateSubmitted` ordering would rot independently.
- **Make `submit()` polymorphic** (with a `mode?: 'generate' | 'surprise'` arg): rejected — `submit` already has a clean argument shape (`{ photoBlob, selections }`). Adding a mode flag would require the caller to know "what Surprise Me means" (that selections come from the randomizer), which is exactly what a named `surprise()` method hides.

## R5 — Predicate shape for `isReadyToSurprise`

**Decision**:

```ts
export function isReadyToSurprise(state: AlterEgoSession): boolean {
  return !!state.photoBlob && state.firstName.trim().length >= 1 && state.phase !== 'generating'
}
```

Paired with a `missingInputsForSurprise(state)` that returns `('photo' | 'firstName')[]` for the button's hint copy.

**Rationale**: Mirrors `isReadyToGenerate` / `missingInputs` one-for-one, so the two can be tested against a shared invariant: `isReadyToGenerate(s) ⇒ isReadyToSurprise(s)` for every session state. That invariant is the formal statement of FR-902's "any combination of category selections leaves Surprise Me enabled". The `phase !== 'generating'` clause is what prevents a second request while a first is in flight (Edge Case 6).

**Alternatives considered**:

- **Reuse `missingInputs` and filter out category entries**: rejected — it would couple `isReadyToSurprise` to the exact list of category fields in `missingInputs`, making future category additions (hypothetical 010) brittle across two sites.
- **Move photo + firstName into their own selector and recompose**: over-engineered for two fields. If a future feature demands it, that refactor is cheap.

## R6 — Composition with 007 tab access gating

**Decision**: No change to `tabDisabled`. Surprise Me sits inside the Setup panel; once clicked, the same `ActiveTabChanged` dispatch that Generate fires transitions the Setup tab to disabled (via 007 FR-504) and the alter-ego tab to enabled-and-active. No new gating logic is required.

**Rationale**: 007's gating is a pure function of `phase`. Surprise Me's only reducer impact is transitioning `phase` through the exact same states Generate does (`picking → generating → succeeded|failed_with_fallback`). The tab gating therefore behaves identically.

**Verification**: A Playwright step in `surprise-me.spec.ts` clicks Surprise Me, confirms the Setup tab's `aria-disabled="true"` within the same render frame as the loading indicator, and confirms both tabs re-enable after the response lands.

## R7 — Preservation of the 005 entrance animation

**Decision**: The `surprise()` path reuses the existing `onMutate` handler in `useGenerateAlterEgo`, which already dispatches `ActiveTabChanged` with `reason: 'generate'`. No new animation code is added.

**Rationale**: 005's animation is keyed on the monotonic `generateAutoSwitchNonce` which increments whenever `reason: 'generate'` is dispatched (reducer case `ActiveTabChanged`). Because Surprise Me routes through the same `submit()` that Generate does, the nonce ticks and the animation plays exactly once per Surprise Me click — identical to a Generate click.

## R8 — Button placement in `SetupLayout`

**Decision**: Adjacent to the Generate button, in a shared action row at the bottom of the `setup-layout__selections` section. Generate on the left (primary), Surprise Me on the right (secondary).

**Rationale**:

- NFR-902 requires no layout shift. Wrapping the two buttons in a flex row inside the existing `<GenerateButton>` container position preserves the row height and keeps narrow-viewport behavior intact (the flex row wraps, stacking Surprise Me below Generate on phones).
- Generate is the primary affordance for users who know what they want; Surprise Me is the "I don't care, surprise me" escape hatch. Visually secondary placement matches that information hierarchy.

**Alternatives considered**:

- **A separate "Shortcut" floating panel**: rejected — scope creep and adds a third UI location for "start generation".
- **Replace Generate with a dropdown**: rejected — one more click, hides the primary affordance.

## R9 — Accessibility parity

**Decision**:

- The button is a native `<button type="button">` (matches `GenerateButton`).
- Accessible name: "Surprise Me" (the visible label).
- Disabled state: `disabled` HTML attribute + `aria-disabled="true"` + an `aria-describedby` pointing at a visually-present `role="status"` hint node (`<p>`), same pattern as `GenerateButton`.
- Keyboard: Enter and Space activate when enabled; no-op when disabled.
- Focus indicator: reuses the project's existing focus-visible token.

**Rationale**: Mirrors `GenerateButton` one-for-one so assistive tech gets a consistent experience across the two actions. No new ARIA pattern is introduced.

## R10 — Vibe inclusion in the randomization set

**Decision**: Yes — Surprise Me always picks exactly one Vibe, even though Vibe is optional in the normal flow (002 FR-118 / FR-122).

**Rationale** (A-901 in the spec):

- The issue text is "fires a randomizer for the categories" — Vibe is a category on the Setup tab.
- A Surprise Me that omitted Vibe would look half-hearted on the Setup form (one grid empty after a "full random" click).
- The user retains the ability to return to Setup and click the already-selected Vibe pill to deselect it back to `null`, then re-click Generate — the optional-vibe contract is preserved.

**Verification**: A reducer test asserts that `SurpriseMePicked` sets all five category fields to non-null values; a Playwright test confirms the Vibe grid shows an `aria-checked` option after Surprise Me lands.

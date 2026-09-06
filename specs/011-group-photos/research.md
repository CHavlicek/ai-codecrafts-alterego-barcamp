# Research: Group Photos (011)

## R1 — "Switch" UI primitive: `role="switch"` vs. `role="radiogroup"`

**Decision (post-design-review)**: Single sliding-thumb toggle, `role="switch"` with `aria-checked` reflecting the on/off state.

**Initial decision was a 2-option `role="radiogroup"`**, but a design review pointed out that the issue's "switch" wording was both copy *and* visual specification — the user wanted a real toggle, not two side-by-side options. The radiogroup version felt like a partial commitment ("two options that look like they could be a switch but aren't").

**Alternatives reconsidered**:

- **2-option radiogroup** (initial decision). Both labels simultaneously visible to AT users, identical keyboard contract to PoseGrid. But visually reads as two separate options, not "one toggle".
- **`<input type="checkbox">` styled as a switch**. Native form semantics, but invisible labels by default require extra wiring (`<label>` association) and the styling story is messier than a `<button role="switch">`.
- **Plain `<button>` with `aria-pressed`**. Works, but `role="switch"` is the dedicated ARIA pattern for a two-state toggle — it's a strict superset of the visual affordance.

**Why a single switch**: (a) matches the issue's literal "switch" wording; (b) cleanly maps "off → on" to "single → group" so AT users get a single binary announcement instead of having to track two radios; (c) the visible Single / Group labels inside the track give sighted users a clear affordance for what each side means; (d) the accessible name comes from the visible "Photo Mode" heading via `aria-labelledby`, with a live `role="status"` hint announcing the current selection after toggle so AT users know which side is currently active.

## R2 — Position in the form: photo column vs. selections column

**Decision (post-design-review)**: Photo column, directly beneath the camera intake, as a separate `.setup-layout__photo-mode` subsection with a top divider.

**Initial decision was step 0 in the selections column above Pose**, treating the mode as a "zeroth category". Design review pointed out that the mode is metadata about the photo (how many faces are in the frame), not a theme pick — co-locating it with the camera reads as one cohesive "what's in the photo" story.

**Why the photo column**: (a) the mode answers "is this photo of one person or several?" — the same column already answers "what photo?", so they belong together; (b) the selections column stays a clean five-step thematic flow (Pose / Role / Universe / Art Style / Vibe) without an out-of-family "step 0"; (c) on narrow viewports the photo column collapses first, so the toggle stays visually adjacent to the camera intake throughout the responsive ladder; (d) the visual divider (`border-top: 1px solid --color-border`) reads as "this control modulates the thing above it".

## R3 — Wire-level nullability of `photoMode`

**Decision**: Nullable on the backend (no `@NotNull`), required on the frontend (always sent). Domain default inside the backend is `SINGLE`.

**Why**: During a rollout, the backend will ship before the frontend reaches 100% of clients (or vice versa). Old frontend + new backend must keep working — an old client sends a request without `photoMode`, the new backend must accept it. Old backend + new frontend would fail closed (400 unknown property) via Jackson's default behaviour — `@JsonIgnoreProperties(ignoreUnknown = true)` isn't in play here, but this is the ONLY direction that could break under misaligned rollout, and it's safer than the alternative. To make this direction also safe we rely on the existing DeserializationFeature behaviour on the backend which accepts unknown fields by default unless configured otherwise (it is not configured in this repo's Jackson setup, verified by reading the existing pipeline). So: old server + new client also keeps working.

**Alternative considered**: `@NotNull` with a breaking-change bump. Strictly cleaner contract, but blocks partial rollout and requires zero-downtime coordination — overkill for a POC-stage feature.

## R4 — Accent / colour treatment

**Decision**: Reuse the `--color-accent-pose` or `--color-accent-role` token for consistency (or a neutral / unified token). No new CSS custom property.

**Why**: Adding a sixth accent just for a 2-option binary adds palette noise without a design payoff. The other five accents distinguish categories (pose / role / universe / vibe / artstyle); this isn't a "category" in the same sense. If visual review finds the reused token too bold, a single `--color-accent-mode` can be introduced later in a CSS-only diff.

**Alternatives considered**: brand new accent token — rejected as premature palette expansion; no accent at all — rejected because `SelectionGrid`'s visual style expects one.

## R5 — Prompt wording for GROUP mode

**Decision**: Plural subject instruction + explicit "EVERY person visible in the reference photo" clause + explicit "Do NOT invent additional people" negative constraint.

**Why each clause**:

- **"group portrait poster of the people"** — mirrors the 003 singular wording exactly so the Single variant is a character-level subset of the Group variant's first sentence modulo the word swaps. Regression-safe.
- **"EVERY person visible in the reference photo"** — Gemini's image-generation models have been observed (per external benchmarks) to drop minor faces in group photos if the prompt says "render the people" without specifying "every one". The ALL-CAPS is a lexical weight the model reliably picks up.
- **"Do NOT invent additional people"** — without this, the model can (and often does) hallucinate extra figures to make a composition feel "complete". Hard-gating that behaviour matches the user's intent: faithful group-photo alter-ego mapping.
- **"Apply the same attributes uniformly to every person"** — pins the archetype / universe / art style / pose / vibe to the whole group, not "one person per attribute". Matches assumption A-1003.
- **"Group name: {firstName}"** — in Group mode the single name field is the group's collective name; relabelling in the prompt makes that clear to the model without needing a separate wire field.

**Alternatives considered**:

- Static "group of people" wording with no count instruction. Rejected: observed to produce "2 people even if the photo has 5".
- Passing the face-count as a number in the prompt. Rejected: the backend doesn't inspect the photo, and any wrong guess (from the frontend) would harm fidelity.
- Telling the model to "match every face to its own alter ego archetype". Rejected as over-specifying: the issue doesn't ask for per-person customisation; the simple reading is the whole group shares one archetype.

## R6 — Testing strategy

**Frontend**:
- `reducer.test.ts`: add invariants I-1..I-5 from data-model.md.
- `PhotoModeSwitch.test.tsx`: 7 tests — render both options, default checked, click unchecked fires `onChange`, click checked is no-op, Arrow keyboard, Home / End, accessible name lacks icon text.
- `AlterEgoPage.test.tsx` / `SetupLayout.test.tsx`: 1-2 tests pinning that Generate and Surprise Me both send `photoMode` in the payload.

**Backend**:
- `EnumsTest` add wire-value case for `PhotoMode`.
- `GeminiPromptBuilder` unit test: assert SINGLE variant is byte-identical to today's output (regression lock), and GROUP variant contains the expected plural wording + the "EVERY person" + "Do NOT invent" clauses.
- `AlterEgoControllerContractTest` / `AlterEgoControllerErrorContractTest`: contract tests for (a) absent field → 200 OK, (b) valid `"single"` → 200 OK, (c) valid `"group"` → 200 OK, (d) invalid `"team"` → 400.
- `GenerateAlterEgoGeminiIT`: extend the existing mock-Gemini integration test to assert the outbound prompt under GROUP mode contains the plural phrasing.

**E2E**:
- `group-photos.spec.ts` (new) — switch defaults to Single; click Group; click Generate; intercept the body and assert `selections.photoMode === "group"`; Start-over resets back to Single.
- `axe-scan.spec.ts` — add the switch's container to scope; zero new violations.

## R7 — Rollout & observability

**Decision**: No new structured log field. The existing `event=generation.completed` log line already carries enough context (outcome, reason, correlationId). Adding `photoMode` would be nice-to-have but not required for debugging. Can be added later without a spec change.

**Why**: the only observable difference between Single and Group is the prompt text sent to Gemini. That's already captured (at debug level) by the Gemini client and doesn't need dedicated instrumentation. Keeping the log fields minimal matches the project's constitution-compatible minimalism.

## R8 — Why not a boolean?

**Decision**: Enum over boolean (`'single'` / `'group'` vs. `true` / `false`).

**Why**: (a) a third mode becomes straightforward to add later (e.g. "solo portrait with blurred teammates" — not in scope, but the extension point is free). (b) the wire value reads better in logs, tests, and server-side diagnostics (`"single"` is self-documenting; `false` requires the reader to know which field it's on). (c) every other mode/enum in this app is a string union for the same reasons.

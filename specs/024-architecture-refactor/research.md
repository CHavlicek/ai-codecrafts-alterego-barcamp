# Phase 0 Research — 024 Architecture Refactor

**Date**: 2026-05-15

This document records the design decisions that resolve the open questions in the plan. There are **no** `NEEDS CLARIFICATION` markers in Technical Context; the entries below are SOTA-pattern decisions, not unresolved ambiguities.

---

## R1 — Layer convention: "boundary / application / domain / infrastructure"

**Decision**: Adopt **four layers**: `boundary`, `application`, `domain`, `infrastructure`. Dependency direction is unidirectional — outer layers depend on inner ones, never the reverse.

- `boundary` — HTTP I/O. Controllers, RFC 7807 advice, request filters (correlation id, photo redaction). Depends on `application`.
- `application` — use-case orchestration and the post-image **pipeline**. Owns the ports (`ImageGeneratorPort`, `CharacterGeneratorPort`, `EmailSenderPort`) that the infrastructure adapters implement. Depends on `domain` only.
- `domain` — plain value types (`AlterEgoRequest`, `Pose`, `Archetype`, `PosterImage`, `Provider`, `FallbackReason`, ...), pure prompt construction (`ImagePrompt`, `CharacterPrompt`, `RoleOfRecord`), and policy helpers (`AccentResolver`). Depends on nothing — including Spring.
- `infrastructure` — Spring `@Configuration`, outbound provider adapters (Gemini, fal.ai, stub, fallback), JDK Graphics2D overlay implementations, JavaMail. Depends on `application` (to implement its ports) and `domain` (to map types).

**Rationale**:

- This is the ports-and-adapters (hexagonal) shape Cockburn described, with a thin HTTP `boundary` carved out from `application` to keep use-cases framework-agnostic. It is the layout most JVM teams converge on when they outgrow the default Spring `controller/service/repository` triad.
- It directly satisfies FR-2401 (four named layers visible from folder names) and FR-2402 (orchestration in `application` cannot reference concrete provider classes in `infrastructure.provider.*`, only the port).
- The boundary/application split lets us enforce in `arch/` that `domain` has zero Spring imports (FR-2406), which the current flat layout makes hard to assert.

**Alternatives considered**:

- **Three layers (`controller / service / repository`)** — the default Spring shape. Rejected: it gives no place for the post-image pipeline that isn't a service, and no place for the ports that decouple orchestration from providers. The current codebase already shows the cost: `service/` is overloaded with adapters, orchestration, overlays, and providers as siblings.
- **`core / adapter`** (Quarkus / Vaughn Vernon flavour) — collapses domain + application into a single inner ring. Rejected: the project's orchestrator is non-trivial enough (retry, fallback policy, pipeline) that giving it its own layer pays off; merging it into `domain` would re-introduce framework imports there.
- **Module-per-feature** (`features/alterego/{boundary,application,...}`) — natural in DDD with multiple bounded contexts. Rejected: the project has exactly one bounded context (alter-ego generation); adding a feature-axis split would be premature and adds churn without payoff.

---

## R2 — Enforcement: ArchUnit (test-only) for layer + provider-seam rules

**Decision**: Use **ArchUnit 1.3.x** as a `testImplementation` dependency, owning a dedicated `arch/` test tier. Three rules are non-negotiable and fail the build if violated:

1. **Layer dependency direction.** `boundary` → `application` → `domain` ← `infrastructure`. No reverse edges. (FR-2401.)
2. **No provider branching in `application`.** Classes under `application.*` MUST NOT reference any concrete type under `infrastructure.provider.gemini.*`, `infrastructure.provider.falai.*`, `infrastructure.provider.stub.*`, or `infrastructure.provider.fallback.*`. (FR-2402.)
3. **Domain purity.** Classes under `domain.*` MUST NOT have any import that starts with `org.springframework`, `jakarta.servlet`, `javax.imageio`, or any package under `infrastructure.*`. (FR-2406.)

**Rationale**:

- The whole point of the layer rename is that future contributors can violate it without realizing. A test-tier check is the cheapest enforcement that survives reviewer fatigue.
- ArchUnit is the de-facto JVM tool for this; it integrates with JUnit 5, has zero runtime footprint, and the rules above are ~15 lines of test code each.
- A test-only dependency does not affect the production artefact and is permitted by FR-2427 with this justification.

**Alternatives considered**:

- **Module system (JPMS `module-info.java`)** — strongest enforcement. Rejected: Spring Boot's `@ComponentScan` interacts awkwardly with JPMS, and the team has no other module-info-driven projects; the cost-to-benefit is poor for one feature.
- **Convention only (folder names + reviewer judgement)** — cheapest. Rejected: explicitly the failure mode the refactor is meant to prevent. The current `service/` package drift is the evidence.
- **Checkstyle ImportControl rules** — possible. Rejected: ArchUnit's API is dramatically more readable for layer rules, and the team already uses ArchUnit-adjacent assertion styles in tests.

---

## R3 — Pipeline shape: `List<PosterStage>` of a sealed interface, composed in `application.pipeline`

**Decision**: Model the post-image overlay sequence as an ordered, named list of `PosterStage` implementations. The pipeline class accepts the list via constructor injection and applies them in order. Each stage takes a `PosterImage` plus a `StageContext` (first-name, role-of-record, quote) and returns a new `PosterImage`.

```java
public sealed interface PosterStage permits FrameStage, TextStage {
    PosterImage apply(PosterImage input, StageContext ctx);
}

public final class PosterPipeline {
    private final List<PosterStage> stages;
    public PosterImage apply(PosterImage input, StageContext ctx) {
        PosterImage current = input;
        for (PosterStage stage : stages) current = stage.apply(current, ctx);
        return current;
    }
}
```

**Rationale**:

- Sealed-interface lets ArchUnit assert exactly two stages exist (frame, text) and lets the compiler check exhaustive pattern matching if we ever add stage-specific telemetry.
- The order is data, not code: easy to test (independent stage tests per FR-2423), easy to insert a new stage (e.g. branding logos as a separate stage, currently embedded elsewhere), and easy to swap order in a test fixture.
- It removes the imperative two-call sequence (`posterFrameOverlay.apply(...)` then `posterTextOverlay.apply(...)`) from `AlterEgoUseCase`, satisfying FR-2409 without breaking FR-2425 (outcomes unchanged).

**Alternatives considered**:

- **Decorator chain** (each stage wraps the next). Rejected: harder to inject a fixed order from configuration; less testable per-stage.
- **CommandBus / Spring `ApplicationEventPublisher`** — overkill, asynchronous, and would need persistence to be made reliable. Rejected outright.
- **Keep imperative chain, add tests around each step** — would satisfy FR-2423 (per-stage tests) but not FR-2409 (named pipeline). Rejected because the name *is* the documentation here; a `PosterPipeline` class signals what's going on at a glance.

---

## R4 — Provider seam: fallback as a port, selected by the orchestrator's failure path

**Decision**:

1. `ImageGeneratorPort` is the single application-facing seam. Its method is `generate(GeneratedCharacter, AlterEgoRequest, PhotoPayload) → PosterImage`. Failure modes are typed `GenerationFailure` (existing class, moved to `application.port`).
2. **`FallbackImageGenerator` implements `ImageGeneratorPort`.** It wraps `FallbackPosterProvider` (existing class, moved to `infrastructure.provider.fallback`). The orchestrator holds both ports and calls them in a single, identity-blind sequence: try `primary`, on `GenerationFailure` or any `RuntimeException` call `fallback`. The orchestrator does **not** branch on `providerName()`.
3. **Stub-as-primary case.** Today, when the wired primary is `StubImageGenerator`, `AlterEgoService` short-circuits with `FallbackReason.NOT_CONFIGURED`. Post-refactor, the same outcome is reached by having `StubImageGenerator` throw `GenerationFailure(NOT_CONFIGURED)` on every call; the orchestrator's normal catch path produces the same response. **Behaviour identical (FR-2425); structure simpler (FR-2402).**
4. **`CharacterGeneratorPort`** is parallel — stub character generator throws `GenerationFailure(NOT_CONFIGURED)` in the stub profile.

**Rationale**:

- This is the cleanest way to satisfy FR-2402 ("orchestration MUST NOT branch on provider identity") and FR-2403 ("fallback path reached through the same seam") simultaneously. Both rules become true by *construction*.
- The change is small in code (the orchestrator's `if STUB_PROVIDER.equals(wiredProvider)` block goes away; `StubImageGenerator.generate` returns a typed failure instead of a stub poster) and **observable behaviour is identical**: the same `outcome=fallback`, `provider=stub`, `attemptedProvider=...`, `reason=NOT_CONFIGURED` log line is emitted, and the same response shape is returned.
- ArchUnit rule 2 (R2) bites on this: after the refactor, the orchestrator's bytecode cannot mention any concrete provider class. The build fails if anyone reintroduces a branch.

**Alternatives considered**:

- **Spring `@Primary` + `@Qualifier("fallback")`** chain wrapper. Rejected: Spring-specific, hides the seam from `application` (which is supposed to be framework-free in R1), and complicates ArchUnit assertions.
- **Strategy pattern with `supports(request)`** discriminator. Rejected: there is no per-request routing today; only profile-time wiring. Adding `supports` would be speculative complexity.
- **Keep the orchestrator's catch + add a comment** "do not branch on identity". Rejected: not enforceable, defeats the point of the refactor.

---

## R5 — Constitution amendment mechanics (v1.0.2 → v1.1.0)

**Decision**: As an explicit deliverable of `024` (FR-2429), edit `.specify/memory/constitution.md`:

1. Bump the trailer to `**Version**: 1.1.0 | **Last Amended**: 2026-05-15` (Ratified unchanged).
2. Prepend a new entry to the Sync Impact Report at the top:

   ```
   1.1.0 (2026-05-15) — MINOR
     + VII.  Layer Convention (boundary / application / domain / infrastructure)
     + VIII. Provider Seam (single ImageGeneratorPort; fallback is a port; no
              orchestration-side identity branching; enforced by ArchUnit)
     + IX.   Test Pyramid (unit / service / contract / integration / arch tiers
              with named Gradle tasks)
   ```

3. Add the three principles as new `### VII.`/`### VIII.`/`### IX.` sections under Core Principles. The Principle II slot remains intentionally absent (FR-2430).
4. Verify the four template files listed in the existing Sync Impact Report still have no required changes (they reference only Principle III, IV, V, VI and the technology tables — none of which change).
5. **The amendment lands as the FIRST commit of the implementation phase** so that every subsequent commit on `024` is gated against v1.1.0, not v1.0.2.

**Rationale**:

- The new principles encode invariants that the rest of the refactor enforces in code. Without writing them down, the code-level enforcement (ArchUnit, layer folders) drifts into "the way one team happened to structure one feature" rather than a project-wide standard.
- SemVer MINOR is exactly the bucket the existing constitution defines for "addition of a new principle or materially expanded guidance".

**Alternatives considered**: All three alternatives (no change, PATCH only, fill Principle II) were considered and discarded during `/speckit.clarify` Q4.

---

## R6 — Frontend state seam policy (US3 — formalise the convention)

**Decision**: The frontend state model **does not move**. The existing layout is already SOTA-shaped (TanStack Query owns server state; a single reducer owns session state; component-local `useState` owns transient UI state). The refactor records and enforces the convention without code restructuring:

1. **Write the convention down** as a `frontend/STATE.md` file (one screen). Three rules: server state via `useQuery`/`useMutation` only; session state via `useAlterEgoSession()` (the existing context hook); component-local UI state via plain `useState`.
2. **ESLint rule**: forbid `useReducer` outside `frontend/src/features/alterego/state/`. Written as a custom rule in `eslint.config.js` (~10 lines). This catches the most common drift mode: a new feature spawning a second reducer.
3. **Selector-memoization audit**: assert `useAlterEgoSession()` returns memoized selectors (it already does via `React.useMemo` in the provider). No code change; add one unit test that re-rendering a component without a relevant state change does not invoke the selector body.
4. **Reducer action union remains closed.** New actions require a code review note; this is a process gate not a code gate (matches Q2's "default is don't add").

**Rationale**:

- The frontend already follows this pattern; the diff for US3 is small. Most of the work is *recording* the convention so future contributors don't introduce a Zustand store or a duplicate reducer.
- A custom ESLint rule is the lowest-friction enforcement for the single most likely drift case (second reducer). No new frontend dependency required (FR-2427: default is "don't add").

**Alternatives considered**:

- **Introduce Zustand for session state**. Rejected: gratuitous churn; the existing reducer + context already isolates re-renders correctly, and Zustand would be a brand-new dependency for zero behavioural gain.
- **Move all session state into TanStack Query** (`useQuery` with a local query client). Rejected: a category error — TanStack Query is for *server* state. Forcing local state into it requires shimming and reduces clarity.

---

## R7 — Test pyramid layout (US4 — named tiers and per-tier Gradle tasks)

**Decision**:

1. Existing tests under `backend/src/test/java/com/aiavatar/alterego/{unit,service,contract,integration}/` already follow the right convention by package name. Codify by creating per-tier **Gradle test tasks** that filter by package: `unitTest`, `serviceTest`, `contractTest`, `integrationTest`. Add a new `archTest` task for ArchUnit rules. The default `test` task aggregates all five tiers.
2. **Unit tier MUST NOT load Spring.** Enforce by an ArchUnit rule in the `arch/` tier itself: no class under `unit/` may import `org.springframework.boot.test.*` or any `@SpringBootTest`-bearing meta-annotation.
3. **Contract tier already pins HTTP shape** via `swagger-request-validator-mockmvc` against `contracts/alter-egos.openapi.yaml` (and the new `contracts/email.openapi.yaml`). Confirm both tests assert request validation AND response validation.
4. **Wall-clock targets** are SC-003 acceptance criteria: full local suite ≤ 2 min cold, unit tier ≤ 10 s. Measured by `--scan` build summary; not a Gradle task failure.

**Rationale**:

- Tier-by-package + Gradle task per tier is the pattern most JVM teams converge on once they want to slice the suite. It needs nothing new in the build script except `Test` task copies with `include "**/<tier>/**"`.
- A separate `archTest` tier keeps ArchUnit rule failures from masking unit-test failures and vice-versa.

**Alternatives considered**:

- **Convert tiers to Gradle source sets** (`src/test/unit/java`, etc.). Rejected: bigger move (must split source sets, classpath inheritance, IntelliJ imports), modest payoff over package-filtering.
- **Tag JUnit 5 tests with `@Tag("unit")` etc.** instead of packages. Rejected: less self-documenting (you can't see the tier from the file path), and packages already encode the right information.

---

## Open questions deferred to `/speckit.tasks` and implementation

These were judged plan-level-too-detailed during `/speckit.clarify`:

1. The exact filenames inside `infrastructure/provider/gemini/` etc. after the move (likely a 1:1 rename of existing files; no semantic change).
2. The sequencing of moves within `024` so each commit keeps the suite green. Likely order: (a) constitution amendment, (b) introduce new packages + duplicate ports, (c) move classes, (d) delete old packages, (e) ArchUnit rules, (f) frontend STATE.md + ESLint rule. `/speckit.tasks` will produce the canonical order.
3. Whether `FallbackImageGenerator` is a thin adapter over the existing `FallbackPosterProvider` (likely yes) or whether the existing class is renamed (likely no — preserves blame).

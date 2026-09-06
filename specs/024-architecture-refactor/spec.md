# Feature Specification: Architecture and Design Refactoring

**Feature Branch**: `024-architecture-refactor`
**Created**: 2026-05-15
**Status**: Draft
**Input**: User description: "Analyse the current project from architectural and system design point of view. Suggest refactorings to align the code with the current SOTA approaches. The overall business logic should remain the same."

## Clarifications

### Session 2026-05-15

- Q: Should the refactor define a runtime performance regression budget for `/generate` and `/email`? → A: No explicit budget — perf is best-effort, no acceptance gate.
- Q: What is the policy for adding new libraries (within the existing runtimes) during the refactor? → A: Permitted with named justification — each new library must be tied to one FR, vetted (maintained, no known CVEs, reasonable size / scope), and recorded in `CLAUDE.md`'s Active Technologies log. Default posture is "don't add".
- Q: Which of the 5 user stories are in scope for this feature branch (`024`)? → A: All five. US1 + US2 + US3 + US4 + US5 all ship on `024`. No part of the refactor is carved out to a follow-up spec.
- Q: Should the project constitution be amended as part of `024`? → A: Yes — MINOR amendment (v1.0.2 → v1.1.0). Codify the new layer convention (boundary / application / domain / infrastructure), the provider-seam contract, and the test-pyramid tier names (unit / service / contract / integration) as new principles. The Principle II TODO from the existing Sync Impact Report is **not** part of this amendment and will be considered separately.

## Context

The codebase has grown organically across 23 feature increments (`001-initial-poc` → `023-email-send-image`). Each increment added a new vertical capability (a generation provider, a UI surface, an overlay, a side-channel like email) on top of the previous one. Functionally the product is complete and shipped; structurally, the seams that were drawn for the first 3–4 features now carry responsibilities they were not designed for, and several module boundaries have become diffuse. This feature is an internal-quality refactor whose sole purpose is to bring the structure of the code in line with the responsibilities it actually has today, **without changing what the product does for the end user**.

Stakeholders are the maintainer / developer team (primary), and indirectly the end users — who benefit through fewer regressions, faster iteration on new features, and lower incident response time when a provider misbehaves.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Swap or add a generation provider without rippling changes (Priority: P1)

A maintainer needs to add a third image-generation provider (or replace one of the existing two, or temporarily disable one in production while keeping it wired for tests). Today this touches multiple unrelated parts of the codebase: orchestration, configuration, prompt construction, the fallback path, error mapping, the photo-redaction filter, and integration tests. After the refactor, adding or swapping a provider is a localized change behind a single, well-named seam.

**Why this priority**: Provider plurality (stub / Gemini / fal.ai) is the single largest source of conditional code and the most likely source of future churn (new model versions, new vendors, A/B tests, fallback policy changes). Stabilizing this seam pays back the largest amount of future change.

**Independent Test**: A maintainer can introduce a hypothetical fourth provider by adding exactly one new module that satisfies the provider contract and one new configuration entry, with **zero** edits to existing orchestration, controller, error-handling, or unrelated provider code. The system continues to pass the contract test suite end-to-end without other changes.

**Acceptance Scenarios**:

1. **Given** the refactored codebase with two existing providers, **When** a maintainer adds a third provider that implements the provider seam, **Then** the only files added or modified are the new provider's own module and one configuration entry that activates it.
2. **Given** an active provider returns a transient error, **When** the orchestration layer is asked to fulfil a generate request, **Then** the fallback path is exercised through the same seam the providers use and not through provider-specific branches.
3. **Given** a provider profile is disabled by configuration, **When** the application starts, **Then** the disabled provider's module is not instantiated and contributes no framework beans, HTTP clients, or scheduled work.

---

### User Story 2 - Reason about a request end-to-end from a single layered map (Priority: P1)

A new contributor needs to understand the full path of one Generate request — from the HTTP boundary, through input validation, through the prompt assembly, through the chosen provider, through the post-processing overlays (frame, branding, poster text), to the response — within their first day. Today this requires reading across services, configs, controllers, and overlay classes whose boundaries no longer match their names. After the refactor, the code is organized into a small number of well-named layers (boundary / application / domain / infrastructure) and a request reads top-to-bottom through them.

**Why this priority**: Onboarding speed and incident-response speed both depend on a contributor's ability to mentally trace a request. This is also the prerequisite that makes every other refactor in this feature safe to perform.

**Independent Test**: A reader with no prior context can answer, in under 15 minutes and using only file/folder names, the questions "where does input validation happen?", "where is the prompt built?", "where is the provider called?", and "where are overlays applied?" — and the answers point to distinct, non-overlapping locations.

**Acceptance Scenarios**:

1. **Given** the refactored module structure, **When** a contributor opens the project tree, **Then** the top-level layers (boundary / application / domain / infrastructure) are visible from folder names alone without reading code.
2. **Given** a request flows through the system, **When** any single layer is replaced by a test double, **Then** the layers above and below it continue to compile and pass their own unit tests without modification.
3. **Given** a cross-cutting concern (request logging, photo redaction, error-to-Problem-Detail mapping), **When** a contributor looks for its implementation, **Then** it lives in exactly one place and is wired in declaratively rather than re-invoked at each call site.

---

### User Story 3 - Add or change a UI capability without reaching into unrelated state (Priority: P2)

A frontend maintainer needs to add a new Setup-time selector, change how Surprise Me composes its selections, or add a new side-channel action on the Your-Alter-Ego tab. Today the session reducer, the selectors, the hooks layer, and the page-level components are coupled tightly enough that changes to one ripple into the others. After the refactor, the UI is organized so that locally-owned UI state, server-state (in-flight requests and responses), and cross-cutting session state are separated and each has one obvious home.

**Why this priority**: Most user-visible iteration happens on the frontend. Reducing the cost-per-UI-change accelerates product work directly. This is below the provider seam in priority because UI-side coupling is mostly contained within `features/alterego/` already and the blast radius of mistakes is smaller.

**Independent Test**: A maintainer can add a new boolean selector to the Setup tab (e.g. "include caption") whose value flows into the generate request, without touching files outside the components folder, one reducer action, and the request-shape file.

**Acceptance Scenarios**:

1. **Given** the refactored frontend, **When** a server-state concern (loading, success, error, retry of a generate or email request) is needed in a new place, **Then** it is consumed through the shared server-state seam rather than re-implemented in the component.
2. **Given** a piece of UI state belongs only to one component (e.g. a hover or focus flag), **When** it is added, **Then** it does not enter the global session reducer.
3. **Given** a selector derives a value from session state, **When** its inputs are unchanged, **Then** components consuming it do not re-render.

---

### User Story 4 - Run the full test pyramid in under two minutes locally (Priority: P2)

A maintainer runs the full backend and frontend test suites locally during normal development. Today the suite is healthy in coverage but the boundaries between unit / service / contract / integration tests are uneven, and some "unit" tests reach into real framework contexts or real provider clients. After the refactor, the pyramid is clean: unit tests are pure and fast, service tests exercise one slice with declared collaborators, contract tests pin the HTTP shape, and integration tests are the only tier that boots the full app.

**Why this priority**: Test-feedback time is a force-multiplier on every other refactor. It is P2 rather than P1 because the suite is functionally green today; this story improves *speed* and *clarity*, not correctness.

**Independent Test**: A maintainer runs the full suite locally on a cold cache and the run completes within a target wall-clock budget; the same run can be sliced by tier (unit-only, service-only, contract-only, integration-only) via standard build commands.

**Acceptance Scenarios**:

1. **Given** the refactored test layout, **When** a maintainer runs the unit tier only, **Then** no framework context is started and the tier completes in under 10 seconds.
2. **Given** a maintainer changes a single domain class, **When** they run the affected slice, **Then** unrelated integration tests are not re-run.
3. **Given** a contract test exists for an HTTP endpoint, **When** the request or response shape changes incompatibly, **Then** the contract test fails before any integration test does.

---

### User Story 5 - Observe and diagnose a failing request without reading code (Priority: P3)

When a generate or email request fails in any environment, a maintainer can determine *which seam failed and why* from logs and Problem-Detail responses alone, without attaching a debugger or re-reading source. Today the error path is consistent at the controller boundary (RFC 7807 is well-honoured), but the internal logging is uneven and some seams emit nothing while others emit chatty per-step traces.

**Why this priority**: This is a quality-of-life improvement that becomes valuable only when something goes wrong in a deployed environment. It is P3 because the system is small and most issues are caught at the contract or integration tier today.

**Independent Test**: A simulated failure injected at each of the major seams (validation, provider call, overlay pipeline, email delivery) produces a log line and a Problem-Detail response that together identify the seam, the cause, and the correlation identifier within one screen of output.

**Acceptance Scenarios**:

1. **Given** a request fails inside a provider call, **When** a maintainer reads the resulting log line, **Then** the provider name, the seam, the cause, and a correlation id are present.
2. **Given** a Problem-Detail response is returned to the client, **When** the response is inspected, **Then** it carries the same correlation id as the corresponding server log line.
3. **Given** the no-persistence posture (FR-016 / FR-017 / FR-024), **When** anything is logged or surfaced, **Then** photo bytes, email recipients, and provider API keys are never present in the output.

---

### Edge Cases

- **Single round of provider failure**: the fallback path must be reached through the same provider seam (not through a parallel branch) and the response must still mark `outcome=fallback` with the same typed reasons used today.
- **Provider profile disabled by configuration**: the disabled provider must contribute no beans, no scheduled work, and no HTTP client; the application must start cleanly when only the stub provider is active.
- **Existing public HTTP contract**: the refactor must not change request or response shapes, status codes, or RFC 7807 fields visible to the frontend. Contract tests are the authoritative oracle for "did the refactor preserve behaviour".
- **Frontend session boundary**: locally-owned UI state introduced inside a component during the refactor must not leak into the global session reducer; conversely, no global session field may be moved to local state if more than one component currently reads it.
- **Cold-start in production profile**: switching profiles between stub / Gemini / fal.ai at start-up must not require redeployment of any other component; it remains a configuration change only.
- **Constitution / no-persistence guarantee**: no refactor may introduce a cache, a temp file, a database, or log fields that retain user-supplied content beyond a single in-flight request.
- **Tests that currently rely on internal seams**: when a seam moves, the tests that pin it move with it; no test is silently weakened to "still pass" by removing its assertions.

## Requirements *(mandatory)*

### Functional Requirements

#### Code organization and seams

- **FR-2401**: The codebase MUST be organized into a small, named set of layers (boundary, application, domain, infrastructure) whose responsibilities are described in one short document and reflected in folder names.
- **FR-2402**: Generation providers (stub, Gemini, fal.ai, and any future provider) MUST sit behind a single application-facing seam; orchestration code MUST NOT branch on provider identity.
- **FR-2403**: The fallback path MUST be reached through the same provider seam as a normal generation, not through a separate branch in the orchestrator.
- **FR-2404**: Cross-cutting concerns (request correlation, photo / secret redaction, RFC 7807 mapping, request/response logging) MUST each have exactly one implementation site and MUST be wired in declaratively rather than invoked imperatively at each call site.
- **FR-2405**: Configuration that selects a provider profile MUST be the only mechanism by which a provider is enabled or disabled; no code change MUST be required to switch profiles.

#### Domain model and prompt assembly

- **FR-2406**: The domain model (request, selections, generated character, poster image) MUST be expressed as plain in-process types whose validity is enforceable without booting the framework.
- **FR-2407**: Prompt construction MUST be split into two layers, each independently testable:
  (a) **Pure value types** in `domain/prompt/` (`ImagePrompt`, `CharacterPrompt`, `RoleOfRecord`) MUST be pure functions of the validated request — no Spring, no HTTP client, no I/O.
  (b) **Provider-specific prompt builders** in `infrastructure/provider/<vendor>/` MAY hold provider-shaped payload constants but MUST be unit-testable without a live HTTP client (i.e. they MUST NOT call the network from their constructor or their build method).
- **FR-2408**: The "role-of-record" decision (prefab archetype vs. custom role) MUST remain a single canonical helper consumed by every prompt and overlay site, exactly as it is today; the refactor MUST NOT reintroduce parallel implementations.

#### Overlay and composition pipeline

- **FR-2409**: Image post-processing (frame overlay, branding logos, poster text) MUST be composed as an ordered, named pipeline of stages, each of which is independently testable on a fixed input image.
- **FR-2410**: Static overlay assets (frame asset, logo assets, fonts) MUST be loaded exactly once at application start-up; the refactor MUST NOT change this lifetime.

#### HTTP boundary

- **FR-2411**: All HTTP endpoints MUST continue to validate inputs at the boundary; validation errors MUST continue to return RFC 7807 Problem-Detail responses with the same field-level structure they return today.
- **FR-2412**: The HTTP boundary MUST emit a correlation identifier on every request and propagate it into logs and Problem-Detail responses.
- **FR-2413**: The public request and response shapes for `POST /api/alter-ego` and `POST /api/alter-ego/email` MUST be preserved byte-compatibly. Contract tests pinning these shapes MUST exist and pass.

#### No-persistence posture

- **FR-2414**: The refactor MUST preserve the no-persistence posture established by FR-016 / FR-017 / FR-024 across earlier features. No new cache, queue, temp file, database, or persistent log field MUST be introduced.
- **FR-2415**: The photo-redaction filter and equivalent secret-suppression rules MUST remain active and MUST be verified by at least one test that asserts a photo's bytes and a provider API key cannot appear in any log line.

#### Frontend state and data layer

- **FR-2416**: Frontend state MUST be separated into three named concerns: server state (in-flight requests, responses, errors for `/generate` and `/email`), session state (the user's choices across the Setup and Your-Alter-Ego tabs), and component-local UI state (transient flags scoped to one component). Each concern MUST have one designated mechanism.
- **FR-2417**: Components MUST consume derived values through memoized selectors; a state change that does not affect a selector's output MUST NOT re-render the components that depend on it.
- **FR-2418**: The reducer's action vocabulary MUST remain a closed enumeration; any new UI capability that does not need to mutate session state MUST NOT introduce a new action.
- **FR-2419**: The Surprise Me, Start Over, and generate flows MUST continue to commit their multi-field transitions atomically (one action, one re-render) exactly as they do today.

#### Tests

- **FR-2420**: The test suite MUST be organized into named tiers (unit / service / contract / integration). Unit tier MUST NOT boot a framework context.
- **FR-2421**: Contract tests MUST exist for every public HTTP endpoint and MUST be the first tier to fail if a request or response shape changes incompatibly.
- **FR-2422**: It MUST be possible to run any single tier from a standard build command (e.g. a Gradle task / npm script) without running the other tiers.
- **FR-2423**: At least one test MUST exist per pipeline stage of the post-processing pipeline (frame, branding, poster text), exercising the stage on a fixed input independently of any other stage.

#### Behaviour preservation

- **FR-2424**: The end-to-end behaviour visible to the user MUST be unchanged. Every existing acceptance scenario across features 001–023 MUST continue to pass without modification of the scenarios themselves.
- **FR-2425**: Provider outcome reporting (`outcome=real`, `outcome=fallback`, the typed `fallbackReason`) MUST remain identical to today's behaviour, including in the values it takes and the conditions under which each value is reported.
- **FR-2426**: The visible labels, copy, accent palettes, accessibility affordances (aria-live announcements, keyboard navigation, reduced-motion handling), and printing behaviour MUST be unchanged.

#### Dependency policy

- **FR-2427**: New library dependencies (within the existing runtimes — JVM / Node) MAY be added during the refactor **only** when each of the following is true: (a) the dependency is tied to a named Functional Requirement in this spec; (b) the dependency is currently maintained and free of known CVEs at the time of plan; (c) the dependency's size and transitive scope are documented in the plan; (d) the dependency is recorded in the Active Technologies log in `CLAUDE.md`. The default posture is "don't add".
- **FR-2428**: No new runtime is introduced. The constitutional Technology Standards (Java 21 + Spring Boot 3 on backend, React + TypeScript strict + Vite on frontend) continue to apply, and the refactor MUST NOT migrate to a different language or framework family.

#### Constitution amendment

- **FR-2429**: The project constitution (`.specify/memory/constitution.md`) MUST be amended as a MINOR version bump (v1.0.2 → v1.1.0) as part of this feature, codifying as new principles: (a) the layer convention (boundary / application / domain / infrastructure), (b) the provider-seam contract that all generation providers and the fallback path satisfy (per FR-2402 and FR-2403), and (c) the test-pyramid tier names and their constraints (per FR-2420–FR-2423). The Sync Impact Report at the top of the constitution MUST be updated accordingly.
- **FR-2430**: The Principle II slot, currently flagged as a deliberate TODO in the existing Sync Impact Report, MUST remain absent in v1.1.0. Filling it is out of scope for `024` and will be considered in a separate amendment.

### Key Entities

- **Generation Provider**: An adapter that converts a validated alter-ego request (photo + selections) into a generated poster image and character, or signals a typed failure. Today: stub, Gemini, fal.ai. Distinguishing attribute after refactor: each provider is an independently loadable module behind one seam.
- **Overlay Stage**: A named, ordered step in the post-processing pipeline (frame, branding, poster text). Distinguishing attribute: produces a new image bytes blob from an input image bytes blob plus its own static configuration; has no awareness of the request that produced the input.
- **Session State**: The user's choices and the current phase of one in-browser session (Setup selections, photo, generated result, active tab, generate / email phase). Distinguishing attribute: lives only in browser memory; never persisted; reset by Start Over.
- **Server State**: The frontend's mirror of in-flight or recently-completed requests against the backend (generate, email): loading / success / error / retry status, response payload. Distinguishing attribute: managed by a single data-fetching seam; never mixed into the session reducer.
- **Correlation Identifier**: A short opaque string assigned by the HTTP boundary on every inbound request, propagated through structured logs and surfaced in Problem-Detail responses so a single request can be traced end-to-end.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A new generation provider can be added by a maintainer in a single working day, including tests, with **zero** edits to existing provider, controller, orchestration, error-handling, or unrelated configuration files.
- **SC-002**: A new contributor can correctly answer the four "where does X happen?" structural questions from User Story 2 within 15 minutes of opening the repository for the first time.
- **SC-003**: The full local test suite (backend + frontend) completes a cold-cache run in under 2 minutes wall-clock on a contemporary developer laptop; the unit-only tier completes in under 10 seconds.
- **SC-004**: 100% of the acceptance scenarios across features 001–023 continue to pass after the refactor without modification of the scenarios.
- **SC-005**: 100% of public HTTP request/response shapes for `POST /api/alter-ego` and `POST /api/alter-ego/email` are unchanged, as enforced by contract tests pinning the wire format.
- **SC-006**: A simulated failure injected at any one of the four major seams (validation, provider, overlay, email) produces a log line and a Problem-Detail response that together identify the seam, the cause, and a shared correlation identifier — verifiable within one screen of output.
- **SC-007**: Zero new persistence sites are introduced (no new database, cache, queue, temp file, or persistent log field), as enforced by an automated check or by inspection against this requirement.
- **SC-008**: Zero photo bytes and zero provider API keys appear in any log output across the full integration tier, as enforced by at least one redaction test.
- **SC-009**: After the refactor, the median number of files touched by the next ten user-visible feature changes is reduced compared to a sample of the previous ten — measured as a leading indicator over the first quarter post-refactor.
- **SC-010**: Adding a new Setup-time selector that flows into the generate request touches no more than three files outside of one components folder, one reducer action, and the request-shape file.

## Assumptions

- The refactor is performed as a sequence of small, individually-mergeable changes on this single feature branch, not as one monolithic rewrite. Each step preserves green tests and is independently revertable.
- **All five user stories (US1–US5) are in scope for this feature branch (`024`).** No part is carved out to a follow-up spec. The plan will sequence the stories in priority order (P1 first), but the branch is not merged until all five stories meet their acceptance scenarios and success criteria. The "sequence of small changes" assumption above governs intra-branch cadence — it does not authorize partial-scope merges.
- The public HTTP contract (`POST /api/alter-ego`, `POST /api/alter-ego/email`) and its Problem-Detail field-level shape are part of the product surface and are out of scope for change in this feature.
- The visible product surface (labels, copy, accent palettes, accessibility affordances, animations, printing) is out of scope for change. Any change to that surface must come from a separate feature spec.
- The no-persistence posture (FR-016 / FR-017 / FR-024 across earlier features) is treated as an invariant. The refactor does not lift it.
- The constitutional Technology Standards (Java 21 + Spring Boot 3 on the backend, React + TypeScript strict + Vite on the frontend) continue to apply. The refactor does not introduce a third runtime or migrate to a different stack.
- The set of generation providers today is {stub, Gemini, fal.ai}. The refactor is sized to make a fourth easy, not to add one in this feature.
- Test framework choices (JUnit 5 + Spring Boot Test + Mockito on the backend; Vitest + React Testing Library + Playwright on the frontend) are preserved. The refactor reorganizes tests; it does not migrate them.
- Deployment topology (the existing `docker-compose.yml` and `deploy/` artefacts) is preserved. No new infrastructure components are introduced.
- "Current SOTA" is interpreted here as the well-understood patterns already implicit in the stack — ports-and-adapters / hexagonal layering on the backend, server-state vs UI-state separation on the frontend, a clean test pyramid — not as adoption of any specific named library or framework that is not already in `package.json` / `build.gradle.kts`.
- **No runtime performance gate.** End-user latency of `/generate` and `/email` is **not** an acceptance criterion of this refactor; correctness, behaviour preservation, and structural quality are. If a reviewer observes a visible runtime regression during review, they may raise it as a concern, but no automated perf check blocks merge. The test-suite time budget (SC-003) remains in force.

# Implementation Plan: Architecture and Design Refactoring

**Branch**: `024-architecture-refactor` | **Date**: 2026-05-15 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/024-architecture-refactor/spec.md`

## Summary

Internal-quality refactor (no end-user behaviour change) that:

1. Reorganizes the backend along an explicit **boundary / application / domain / infrastructure** layering so the path of one request reads top-to-bottom and a fourth generation provider can be added behind a single named seam (US1, US2).
2. Replaces the orchestrator's `if STUB_PROVIDER.equals(...)` branch with a single, uniform call into the **provider seam** — the fallback path is reached through the same seam as a normal generation (FR-2402, FR-2403).
3. Composes the post-image overlays (frame, branding, poster text) as an **ordered, named pipeline** of stages instead of imperatively-chained service calls (FR-2409, US2).
4. Formalises the **frontend state seams** (server state via TanStack Query — already in use; session state via the existing reducer; component-local UI state) into a written convention enforced by an ESLint rule (US3).
5. Reorganizes the **test pyramid** into named tiers (`unit` / `service` / `contract` / `integration`) with a separate Gradle task per tier and a wall-clock budget on the unit tier (US4).
6. Adds **request-scoped correlation IDs** that flow into structured logs and RFC 7807 Problem-Detail responses, with a redaction-coverage test verifying photo bytes + provider API keys never appear in any log line (US5).
7. **Amends the project constitution** from v1.0.2 → v1.1.0, codifying the layer convention, the provider-seam contract, and the test-pyramid tier names as new principles (FR-2429, FR-2430).

The refactor preserves the public HTTP contract (`POST /api/alter-ego`, `POST /api/alter-ego/email`) byte-compatibly, preserves all visible UX, and preserves the no-persistence posture inherited from features 001–023.

## Technical Context

**Language/Version**: Java 21 (LTS) backend; TypeScript 5.7 strict frontend. **Unchanged from 023.**
**Primary Dependencies**: Spring Boot 3.5 + Spring Retry + Jackson + Bean Validation + spring-boot-starter-mail (backend); React 19 + Vite 8 + TanStack Query v5 (frontend). **Unchanged from 023.**
**New dependencies** (each justified against one FR, vetted, recorded in `CLAUDE.md` Active Technologies per FR-2427):

- **ArchUnit 1.3.x** (backend, `testImplementation`) — required to enforce the new layer convention (FR-2401) and to fail the build if the orchestrator branches on provider identity (FR-2402). Test-only; not in runtime artefact. Apache-2.0, actively maintained, no CVEs.
- **No frontend dependency additions.** The "no global state in component-only state" rule (FR-2418) is enforced by a hand-written ESLint rule in `frontend/eslint.config.js` plus an existing-pattern review of the reducer action union.

**Storage**: N/A — no-persistence posture preserved (FR-2414).
**Testing**: JUnit 5 + Mockito + Spring Boot Test + `swagger-request-validator-mockmvc` + WireMock (backend, unchanged); Vitest + React Testing Library + Playwright + `@axe-core/playwright` (frontend, unchanged). New: ArchUnit tier added under a fresh `archTest` Gradle source set; Gradle task per tier (`unitTest`, `serviceTest`, `contractTest`, `integrationTest`, `archTest`) wired into a `test` aggregate.
**Target Platform**: Single Spring Boot service + single SPA, served via Docker Compose (backend on `eclipse-temurin:21-jre-alpine`, frontend behind `nginx:alpine`). **Unchanged.**
**Project Type**: Web application — `backend/` + `frontend/`.
**Performance Goals**: No runtime perf gate (clarified Q1). Test-suite wall-clock budget: full local suite ≤ 2 min cold cache, unit tier ≤ 10 s (SC-003).
**Constraints**: Public HTTP contract is invariant (FR-2413). No new persistence (FR-2414). Visible UX is invariant (FR-2426). Constitution amendment is part of this feature (FR-2429).
**Scale/Scope**: ~6,400 LOC backend, ~30 components + ~15 hooks/libs frontend. ~70 backend Java files, ~80 frontend `.tsx`/`.ts` files. 23 prior feature increments inform the structure.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution version evaluated: **v1.0.2** (the version this feature amends).

| Principle | Status | Notes |
|---|---|---|
| I. Modern & Secure Technology Stack | ✅ PASS | No new runtime; new test-only dependency (ArchUnit) is currently maintained, Apache-2.0, no known CVEs at time of plan. |
| III. Test-First Development (TDD) | ✅ PASS | This refactor is uniquely well-suited to TDD: behaviour-preserving changes mean tests guide every step. The pyramid reorganization itself (US4) is delivered as test moves + new Gradle wiring, written test-first against the new tier structure. The 90% coverage floor is preserved tier-by-tier. |
| IV. Resilient HTTP Communication | ✅ PASS | Retry policy + stub fallback semantics are preserved verbatim — the refactor moves where they live (into the provider seam) but does not change the policy. UI degraded-state behaviour is part of the invariant UX (FR-2426). |
| V. Feature Branch Workflow | ✅ PASS | Branch is `024-architecture-refactor` (sequential 3-digit prefix). All five user stories ship on this branch (clarified Q3). Standard PR review + post-merge full-suite run apply. |
| VI. Zero Deprecated Dependencies | ✅ PASS | ArchUnit 1.3.x and existing dependencies will pass `dependencyCheckAnalyze` at merge time. No new frontend dependency. Existing manifests remain pin-by-major. |

**Constitution amendment within this feature (FR-2429, FR-2430)**: this feature carries a MINOR bump from v1.0.2 → v1.1.0 that **adds** three principles (VII Layer Convention, VIII Provider Seam, IX Test Pyramid). The amendment is itself subject to a Constitution Check at the post-design step; that is the only place a `MINOR` bump introduces a temporary self-reference. Approach: amend the constitution file **first** in the implementation order so all subsequent code changes are gated against v1.1.0, not v1.0.2.

**Gate decision (pre-design)**: ✅ PASS — no violations require justification at this point. No entries in Complexity Tracking.

### Post-design re-check (after Phase 1)

After producing `research.md`, `data-model.md`, `contracts/`, and `quickstart.md`, the gates are re-evaluated against the **same** five principles. No new surfaces were introduced that would change the verdict:

- **R1's four-layer decision** introduces the new `boundary/application/domain/infrastructure` packages but adds no runtime dependency (all moves are within existing JARs).
- **R3's `PosterPipeline`** is a single new class + a sealed two-implementation interface. It does not violate Principle I; it does not bypass Principle IV (no HTTP calls involved); it does not contradict Principle III (it is unit-testable per stage — FR-2423).
- **R4's "fallback as a port"** preserves the constitutionally-mandated stub fallback (Principle IV) — it changes *how* the fallback is reached (through the port), not *whether* it is reached.
- **R5's constitution amendment** is the explicit deliverable; the post-amendment v1.1.0 adds three principles (VII, VIII, IX). All Phase 1 artefacts are consistent with v1.1.0 by construction (the artefacts were written assuming v1.1.0 is in force).
- **ArchUnit 1.3.x** (only new dependency) is test-only, well-maintained, no CVEs, recorded in `CLAUDE.md` Active Technologies (FR-2427).

**Gate decision (post-design)**: ✅ PASS — Phase 1 artefacts introduce no new violation. Complexity Tracking remains empty.

## Project Structure

### Documentation (this feature)

```text
specs/024-architecture-refactor/
├── plan.md              # this file (/speckit.plan output)
├── spec.md              # feature specification (already exists)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── alter-egos.openapi.yaml      # pinned HTTP contract (copy of 001's, byte-identical)
│   ├── email.openapi.yaml           # pinned HTTP contract (copy of 023's, byte-identical)
│   └── image-generator.spi.md       # post-refactor backend SPI for generation providers (US1 contract)
├── checklists/
│   └── requirements.md  # already exists (from /speckit.specify)
└── tasks.md             # produced by /speckit.tasks (NOT this command)
```

### Source Code (repository root)

The refactor reorganises the **existing** `backend/src/main/java/com/aiavatar/alterego/...` tree into named layers, and tightens the **existing** `frontend/src/features/alterego/...` tree without restructuring it. No new top-level directories are introduced.

```text
backend/src/main/java/com/aiavatar/alterego/
├── boundary/                       # HTTP I/O — controllers, advices, web config
│   ├── http/
│   │   ├── AlterEgoController.java          # moved from controller/
│   │   ├── AlterEgoEmailController.java     # moved from controller/
│   │   ├── ProblemDetailAdvice.java         # moved from config/
│   │   └── CorrelationIdFilter.java         # NEW — request-scoped MDC correlation id (FR-2412)
│   └── logging/
│       └── PhotoRedactionFilter.java        # moved from config/
├── application/                    # use-case orchestration (no framework details, no provider details)
│   ├── AlterEgoUseCase.java                 # NEW — pure orchestration, was AlterEgoService
│   ├── pipeline/
│   │   ├── PosterPipeline.java              # NEW — ordered List<PosterStage>, FR-2409
│   │   ├── PosterStage.java                 # NEW — sealed interface: apply(PosterImage, StageContext) → PosterImage
│   │   ├── FrameStage.java                  # NEW — wraps PosterFrameOverlayService
│   │   ├── TextStage.java                   # NEW — wraps PosterTextOverlayService
│   │   └── StageContext.java                # NEW — first-name, role-of-record, quote
│   └── port/
│       ├── ImageGeneratorPort.java          # NEW — application-facing SPI, was service.ImageGenerator
│       ├── CharacterGeneratorPort.java      # NEW — application-facing SPI, was service.CharacterGenerator
│       └── EmailSenderPort.java             # NEW — application-facing SPI, was implicit in service.email
├── domain/                         # plain in-process types — validity enforceable without Spring
│   ├── model/                               # AlterEgoRequest, AlterEgoUserSelections, GeneratedCharacter,
│   │                                        # PosterImage, Provider, FallbackReason, Pose, Vibe, Archetype,
│   │                                        # Universe, ArtStyle, PhotoMode, PhotoPayload
│   │                                        # — moved verbatim from model/ (no semantic change)
│   ├── prompt/                              # PURE prompt construction (was scattered)
│   │   ├── ImagePrompt.java                 # NEW — pure value type (was inline String)
│   │   ├── CharacterPrompt.java             # NEW — pure value type
│   │   └── RoleOfRecord.java                # NEW — canonical helper, replaces AlterEgoRequest.roleLabel() call sites
│   └── policy/
│       ├── AccentResolver.java              # moved verbatim from service/
│       └── RandomCategorySelector.java      # moved verbatim from service/random/
├── infrastructure/                 # framework + outbound adapters (Spring, HTTP clients, JDK image)
│   ├── config/                              # all @Configuration classes
│   │   ├── HttpClientConfig.java
│   │   ├── MultipartConfig.java
│   │   ├── RetryConfig.java
│   │   ├── WebConfig.java
│   │   ├── ProviderProfileGuard.java
│   │   ├── EmailConfigured.java
│   │   ├── EmailProperties.java
│   │   ├── GeminiProperties.java
│   │   └── FalAiProperties.java
│   ├── provider/                            # provider implementations of ImageGeneratorPort
│   │   ├── gemini/                          # moved from service/gemini/
│   │   ├── falai/                           # moved from service/falai/
│   │   ├── stub/                            # moved from service/stub/
│   │   └── fallback/                        # moved from service/fallback/ — NOW an ImageGeneratorPort too
│   ├── overlay/                             # JDK Graphics2D / ImageIO heavy lifting
│   │   ├── frame/                           # moved from service/frame/
│   │   └── text/                            # moved from service/text/
│   ├── email/                               # moved from service/email/
│   └── photo/                               # moved from service/photo/
└── AlterEgoApplication.java        # unchanged

backend/src/test/java/com/aiavatar/alterego/
├── unit/                           # pure, no Spring context; ≤ 10 s wall-clock (SC-003)
├── service/                        # one slice, declared collaborators only
├── contract/                       # @WebMvcTest + swagger-request-validator-mockmvc
├── integration/                    # @SpringBootTest end-to-end
└── arch/                           # NEW — ArchUnit tier (FR-2401, FR-2402, FR-2403)
    ├── LayerBoundariesTest.java             # boundary→application→domain→infrastructure dependency rule
    ├── NoProviderBranchingTest.java         # application.* MUST NOT reference infrastructure.provider.*.* concrete types
    └── DomainPurityTest.java                # domain.* MUST NOT depend on Spring or any infrastructure.*

frontend/src/features/alterego/
├── components/                     # unchanged in shape; existing component-local useState patterns enforced
├── state/                          # session reducer + selectors + provider — unchanged
├── hooks/
│   ├── useGenerateAlterEgo.ts              # already TanStack Query — documented as the server-state seam
│   ├── useSendAlterEgoEmail.ts             # already TanStack Query — documented as the server-state seam
│   └── ...
├── services/                       # HTTP clients — unchanged
├── lib/                            # pure utilities — unchanged
└── validation/                     # boundary validation — unchanged

frontend/eslint.config.js          # NEW rule: no `useReducer` outside features/alterego/state/
```

**Structure Decision**: **Web application** (existing). The refactor is a structural move within the existing backend tree and a tightening (no move) within the existing frontend tree. Two new top-level concepts are introduced: (a) the four backend layer folders, replacing the current `controller/`, `service/`, `model/`, `config/` flat layout; (b) the `arch/` test tier replacing reliance on convention. No new top-level project, no new module, no new runtime.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No Constitution Check violations require justification. The plan introduces:

- A new test-only dependency (ArchUnit), justified against FR-2401/FR-2402 and recorded in `CLAUDE.md`.
- A constitution amendment (v1.0.2 → v1.1.0), which is an explicit deliverable of the feature (FR-2429), not a deviation.
- A reorganisation of existing files into layered folders — moves, not new abstractions.
- A new orchestration class name (`AlterEgoUseCase`) and a `PosterPipeline` abstraction. These are not violations of the constitution; they're the structural targets the spec already calls out (FR-2402, FR-2409).

Complexity Tracking table: intentionally empty.

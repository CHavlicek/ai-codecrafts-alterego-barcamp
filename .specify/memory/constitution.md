<!--
Sync Impact Report
==================
Version change: 1.0.2 → 1.1.0 (MINOR)
Reasoning: Codifies the cross-feature conventions established by feature 024
(architecture refactor) as new principles. Adds three principles describing
the layer convention, the provider seam, and the test pyramid. Also rewords
Principle III's persistence clause as a conditional so it doesn't conflict
with the no-persistence posture inherited from features 001–023. The
Principle II slot remains intentionally absent (deferred TODO).

1.1.0 (2026-05-15) — MINOR
  + VII.  Layer Convention (boundary / application / domain / infrastructure)
  + VIII. Provider Seam (single ImageGeneratorPort; fallback is a port;
           no orchestration-side identity branching; enforced by ArchUnit)
  + IX.   Test Pyramid (unit / service / contract / integration / arch tiers
           with named Gradle tasks)
  ~ Principle III: persistence clause reworded to be conditional on a
    persistence layer being wired — resolves pre-existing tension with the
    no-persistence posture of features 001–023.

1.0.2 (2026-04-21) — PATCH
  ~ Product-description prose (preamble): "AI-generated avatar" rewritten to
    "AI-generated alter ego" to align with the feature-level canonical term.
    Project/repo name "AI-Avatar" (and Java package root `com.aiavatar`, DB
    file `aiavatar.db`, Docker volume `aiavatar-db`, npm package `aiavatar`)
    are structural identifiers and intentionally unchanged.

1.0.1 (2026-04-21) — PATCH
  ~ Principle V: feature branch-name convention updated from
    "feature/<###-short-name>" to "<###-short-name>" to match SpecKit's
    create-new-feature.sh output and check-prerequisites.sh validation.
    Also updated the Development Workflow step 1 accordingly.

1.0.0 (2026-04-21) — INITIAL RATIFICATION
  + I.   Modern & Secure Technology Stack (NON-NEGOTIABLE)
  + III. Test-First Development (TDD — NON-NEGOTIABLE)
  + IV.  Resilient HTTP Communication
  + V.   Feature Branch Workflow
  + VI.  Zero Deprecated Dependencies
  + Technology Standards (Frontend / Backend / Deployment)
  + Development Workflow
  + Governance (populated)

Templates requiring updates:
  ✅ .specify/memory/constitution.md — this file, prose aligned
  ✅ .specify/templates/tasks-template.md — no change needed (references
      `com.aiavatar` package path, which is a structural identifier)
  ✅ .specify/templates/plan-template.md — no change
  ✅ .specify/templates/spec-template.md — no change
  ✅ .specify/templates/agent-file-template.md — no change
  ✅ .specify/templates/checklist-template.md — no change
  ✅ .claude/commands/speckit.*.md — no change

Deferred items / TODOs:
  - TODO(PRINCIPLE_II): Principles are numbered I, III, IV, V, VI. Governance
    references Principle III by number, so the II slot was left intentionally
    absent. Add a Principle II or renumber III–VI via a future MINOR amendment
    if the gap is unintended.
-->

# AI-Avatar Constitution

AI-Avatar is a web application that turns a user photo, a chosen fictional
universe (e.g. Star Wars, Indiana Jones, Harry Potter), and a short set of
guiding answers about the user's interests and current/future role into an
AI-generated alter ego. This constitution governs how we build it.

"Alter ego" is the canonical user-facing product term and will be used
consistently across specs, UI copy, entity names, and API paths. "AI-Avatar"
is retained solely as the project/repo umbrella name and as the root for
structural identifiers (Java package `com.aiavatar.*`, DB file, Docker
volume, npm package) where changing it would cause needless churn.

## Core Principles

### I. Modern & Secure Technology Stack (NON-NEGOTIABLE)

All libraries, frameworks, and tooling MUST be:

- Current, actively maintained, and free from known CVEs at the time of adoption.
- Based on the modern React.js ecosystem (React 18+, TypeScript, Vite or equivalent
  SOTA bundler) for the frontend; Java 21 + Spring Boot 3.x for the backend.
- Free of deprecated, unmaintained, or security-compromised packages — such
  packages MUST NOT be introduced, even transitively when avoidable.

### III. Test-First Development (TDD — NON-NEGOTIABLE)

All features MUST follow the Red-Green-Refactor cycle strictly:

1. Write tests; verify they FAIL before committing.
2. Obtain explicit approval of the test suite before writing any production code.
3. Implement until all tests pass.
4. Refactor under green — no regressions permitted.

Additional non-negotiable gates:

- Unit test line coverage MUST be ≥ 90% for every module (frontend and backend).
- Every feature MUST include at least one integration test exercising the complete
  user journey end-to-end.
- Backend integration tests MUST use `@SpringBootTest`. When a persistence layer
  is wired, that layer MUST use an in-process SQLite DB or Testcontainers (the
  POC features 001–023 deliberately ship without a persistence layer, in which
  case this clause is dormant).
- Production-profile wiring MUST NOT be mocked away inside integration tests.

**Rationale**: TDD surfaces design flaws during planning, not debugging. The 90%
gate and mandatory integration tests protect against regression as both the widget
catalogue and backend adapter surface grow.

### IV. Resilient HTTP Communication

Every HTTP call to a backend or third-party API MUST:

- Implement a **retry policy**: 5 attempts with exponential back-off and randomised
  jitter.
- Provide a **stub fallback response** on final failure so the UI always renders in
  a degraded but functional state — a blank or fully broken state is not acceptable.
- Surface error state visually to the user with a non-blocking, recoverable message.

### V. Feature Branch Workflow

Every feature MUST:

- Be developed on a dedicated `<###-short-name>` git branch cut from `main` (e.g. `001-initial-poc`; 3-digit sequential prefix per SpecKit's `create-new-feature.sh`).
- Be merged to `main` only after **explicit human approval** via pull request review.
- Trigger a full test suite run on `main` immediately after merge; a failing suite
  MUST block further merges until resolved.

No feature work may begin directly on `main`. No merge may bypass the approval step.

**Rationale**: `main` must always be deployable. Mandatory approval and post-merge
validation prevent regressions from reaching production.

### VI. Zero Deprecated Dependencies

The dependency manifest MUST be audited before any release or on-demand:

- Run `npm audit` (frontend) and `./gradlew dependencyCheckAnalyze` or equivalent
  (backend); resolve all HIGH/CRITICAL advisories before merging.
- Remove packages flagged as deprecated by their maintainers.
- Pin major versions; unbounded ranges (`*`, `^major` spanning multiple majors) in
  `package.json` or `build.gradle.kts` are prohibited.

**Rationale**: Complements Principle I with an operational gate. Dependency hygiene
is enforced at merge time, not aspirationally.

### VII. Layer Convention

The backend MUST be organized into four named layers with one-directional
dependency flow:

- `boundary` — HTTP I/O. Controllers, RFC 7807 advice, request filters
  (correlation id, photo redaction). Depends on `application`.
- `application` — use-case orchestration and the post-image pipeline. Owns
  the ports that infrastructure adapters implement. Depends on `domain` only.
- `domain` — plain value types, pure prompt construction, policy helpers.
  Depends on nothing — including Spring.
- `infrastructure` — Spring `@Configuration`, outbound provider adapters,
  JDK Graphics2D overlays, JavaMail. Depends on `application` (to implement
  its ports) and `domain` (to map types).

Reverse edges are prohibited. The only class permitted outside the four
top-level layer packages is the Spring Boot application entry point.

**Enforcement**: ArchUnit rules in `backend/src/test/java/.../arch/`.

**Rationale**: Folder names match responsibilities; a request reads
top-to-bottom; adding a provider or a use case is a localized change.

### VIII. Provider Seam

Every generation provider (real or fallback) MUST sit behind a single
application-facing port (`ImageGeneratorPort` / `CharacterGeneratorPort`):

- Orchestration code MUST NOT branch on provider identity (no string
  comparison against `Provider` values, no `if` / `switch` over provider
  names). The fallback path MUST be reached through the same port as a
  normal generation — `FallbackImageGenerator` is itself a port
  implementation.
- Provider selection (active vs. dormant) MUST be a configuration concern
  only — no code change permitted to switch profiles.
- Each provider's failure modes MUST be expressed as a single typed
  `GenerationFailure` carrying a closed `FallbackReason` enum.

**Enforcement**: ArchUnit rule rejects any import of
`infrastructure.provider.*.*` from `application.*`.

**Rationale**: Adding a fourth provider is a single new module + one
configuration entry, with zero edits to existing orchestration.

### IX. Test Pyramid

Tests MUST be organized into five named tiers, each with its own Gradle
task and an enforceable scoping rule:

| Tier | Package | Gradle task | Loads Spring? |
|---|---|---|---|
| Unit | `unit/` | `unitTest` | No (ArchUnit-enforced) |
| Service | `service/` | `serviceTest` | One slice only |
| Contract | `contract/` | `contractTest` | `@WebMvcTest` |
| Integration | `integration/` | `integrationTest` | `@SpringBootTest` |
| Architecture | `arch/` | `archTest` | No |

- Unit tier MUST run in < 10 seconds wall-clock.
- Contract tests MUST pin the public HTTP contract (OpenAPI YAML) and MUST
  be the first tier to fail when a request or response shape changes
  incompatibly.
- Each post-processing pipeline stage MUST have an independent unit test.

**Rationale**: Fast tiers protect feedback time; named tiers protect the
shape of the suite from drift.

## Technology Standards

### Frontend (`./frontend`)

| Concern | Approved Choice |
|---|---|
| Framework | React 18+ with TypeScript (strict mode enabled) |
| Server state | TanStack Query (React Query v5+) |
| UI state | Zustand or React Context API |
| Charting | Recharts (AreaChart, BarChart, RadarChart with ResponsiveContainer) |
| Icons | Lucide React |
| Grid layout | CSS Grid; react-grid-layout for drag/resize features if needed |
| Testing — unit/component | Vitest + React Testing Library |
| Testing — integration/E2E | Playwright (preferred) or Cypress |
| Build tooling | Vite (current major version) |
| HTTP client | Native `fetch` wrapped in a resilient client utility (Principle IV) |
| Linting / formatting | ESLint (flat config) + Prettier |

**Prohibited packages (non-exhaustive)**: jQuery, Moment.js, class-components-only
libraries, any package with an open HIGH/CRITICAL CVE, any package whose npm
registry page shows "deprecated". Run `npm audit` and resolve all HIGH/CRITICAL
before every merge.

### Backend (`./backend`)

| Concern | Approved Choice |
|---|---|
| Language / runtime | Java 21 (LTS) |
| Framework | Spring Boot 3.x |
| Build | Gradle (Kotlin DSL) |
| Persistence | Spring Data JPA + Hibernate 6 + `org.xerial:sqlite-jdbc` |
| Database | SQLite (embedded, volume-mounted at `/data/aiavatar.db`) |
| Cache | Spring Cache → Redis (`redis:7-alpine`) |
| Auth | Spring Security + JWT (JJWT) |
| HTTP client | `java.net.http.HttpClient` wrapped in Spring `RetryTemplate` (Principle IV) |
| Scheduling | Spring `@Scheduled` (15-min ingestion jobs) |
| Testing — unit | JUnit 5 + Mockito |
| Testing — integration | Spring Boot Test (`@SpringBootTest`) + SQLite in-memory |
| Testing — DB isolation | Testcontainers (`testcontainers-junit-jupiter`) where real driver required |
| Static analysis | SonarQube (`org.sonarqube` Gradle plugin 7.x; self-hosted via Docker on `localhost:9000`) |

**SQLite → PostgreSQL migration path**: change `spring.datasource.url` + driver +
Hibernate dialect + replace `strftime()` with `date_trunc()` in native queries. No
application logic changes required.

### Deployment

| Concern | Approved Choice |
|---|---|
| Containerisation | Docker (multi-stage builds for both frontend and backend) |
| Orchestration | Docker Compose (single `docker-compose.yml` at repo root) |
| Frontend image | `node:20-alpine` (build) → `nginx:alpine` (serve) |
| Backend image | `eclipse-temurin:21-jdk-alpine` (build) → `eclipse-temurin:21-jre-alpine` (serve) |
| DB persistence | Named Docker volume (`aiavatar-db`) mounted into backend container |

All new dependency or infrastructure additions MUST be evaluated against these
tables. Deviations require a MINOR or MAJOR constitution amendment.

## Development Workflow

1. **Branch** — Cut `<###-short-name>` from current `main` (3-digit sequential prefix; run `.specify/scripts/bash/create-new-feature.sh`).
2. **Specify** — Create or update the feature spec (`/speckit.specify`).
3. **Plan** — Produce an implementation plan (`/speckit.plan`); pass Constitution
   Check gate.
4. **Tests first** — Write failing tests, commit, push, obtain approval before
   implementing.
5. **Implement** — Write production code until tests pass (Red → Green).
6. **Analyse** — Run `./gradlew sonar` (requires SonarQube running on
   `localhost:9000`); resolve all NEW issues before committing.
7. **Refactor** — Improve under green; coverage gate ≥ 90% MUST hold.
8. **PR** — Open pull request; request explicit human approval.
9. **Merge** — Approved PR merges to `main`; CI runs full test suite automatically.
10. **Validate** — Confirm `main` is green before starting the next feature.

## Governance

- This constitution supersedes all other development guidelines for AI-Avatar.
- Amendments follow semantic versioning:
  - **MAJOR** (`x.0.0`): Removal or incompatible redefinition of an existing principle.
  - **MINOR** (`x.y.0`): Addition of a new principle or materially expanded guidance.
  - **PATCH** (`x.y.z`): Clarifications, wording fixes, non-semantic refinements.
- All PRs touching architecture, testing strategy, or dependency choices MUST
  reference a passing Constitution Check (generated by `/speckit.plan`).
- The Governance section MUST be reviewed quarterly or upon any MAJOR amendment.
- `.specify/templates/agent-file-template.md` MUST stay in sync with the Technology
  Standards table whenever the approved choices change.
- `.specify/templates/tasks-template.md` treats test tasks as mandatory and orders
  them before implementation tasks, consistent with Principle III.

**Version**: 1.1.0 | **Ratified**: 2026-04-21 | **Last Amended**: 2026-05-15

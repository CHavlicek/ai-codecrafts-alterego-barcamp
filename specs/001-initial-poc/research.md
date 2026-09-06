# Phase 0 Research: Initial POC — AI Alter Ego Generator

**Feature**: `001-initial-poc` | **Date**: 2026-04-21

This document resolves every "NEEDS CLARIFICATION" carried into `plan.md`'s Technical Context. All four primary research questions are closed here. Four smaller, less contentious decisions are captured at the bottom so the plan doesn't leave them implicit.

---

## R1 — Observability baseline for the POC

**Decision**

- **Backend**:
  - Logging: SLF4J + Logback (Spring Boot default) with a JSON layout via `logstash-logback-encoder` — one log line per request with a generated correlation id (`X-Request-Id`; echoed in the response header).
  - Log redaction: a Logback `Filter` strips any MDC/field named `photo` / `photoBytes` / `imageData` before emit, satisfying FR-016's "request/response logs MUST redact photo payloads".
  - Metrics: Micrometer with the built-in `CompositeMeterRegistry`. No external exporter in this POC — metrics are accessible only via Spring Boot Actuator's `/actuator/metrics`. `AlterEgoService` emits `alterego.generation.duration` (timer) and `alterego.generation.outcome` (counter tagged with `result=success|fallback|failure`).
  - Health: Actuator `/actuator/health` + `/actuator/info` exposed on the same port; no security in front (POC — no auth scope).
  - Tracing: skipped. Micrometer Tracing would need an exporter to be useful; that's a production concern, not a POC one.
- **Frontend**:
  - Console-only for dev. No external telemetry (Sentry / Datadog RUM) for this POC — no user accounts and nothing to aggregate.
  - Errors from the resilient client are surfaced to the user (FR-018, FR-023), logged to `console.error`, and tagged with the backend's `X-Request-Id` if the response made it back. That correlation alone lets an operator join a frontend error to a backend log.

**Rationale**

- Spring Boot ships SLF4J + Logback + Actuator out of the box, so the incremental cost of a basic observability posture is essentially zero dependencies and one Logback XML.
- Structured JSON logging + correlation IDs are the single most useful debugging affordance for resilient-HTTP work (Principle IV); without them, diagnosing a retry-then-fallback path from logs alone is painful.
- Excluding an external exporter for Micrometer and skipping frontend RUM keeps the POC's infra footprint to zero while leaving the wiring in place for a later feature to attach whatever platform (Grafana Cloud / Datadog / OTel Collector) is eventually chosen.

**Alternatives considered**

- **Full OTel pipeline with Collector + Grafana**: right-shaped for production, wildly over-engineered for a POC. Rejected.
- **No structured logging, default text format**: cheaper but makes retry-flow debugging a grep puzzle. Rejected.
- **Sentry / Datadog RUM on the frontend**: needs an account, an SDK key, and a consent story. All out of scope for a POC that stores nothing about the user. Rejected.
- **Log each retry attempt as a separate log line**: promised debuggability but produces noisy logs. Kept to a single structured line per request with a `retries` field instead.

---

## R2 — Stub response strategy (character + image)

**Decision**

Both third-party integrations are modelled as backend interfaces with a stub implementation bundled in the `stub` package:

```java
interface CharacterGenerator { GeneratedCharacter generate(AlterEgoRequest req); }
interface ImageGenerator     { PosterImage generate(GeneratedCharacter ch, AlterEgoRequest req, byte[] photoBytes); }
```

- **Character stub** (`StubCharacterGenerator`): a pure Java service that hashes the tuple `(pose, colour, archetype, universe, firstName)` and uses the hash to deterministically pick one of **8 template variants per archetype** from a bundled JSON fixture (`backend/src/main/resources/stubs/characters.json`). Each variant defines `heroTitle` suffix / tagline / 3 superpowers / quote. The firstName is substituted into the hero title's line 1 at generation time. Deterministic, differentiated, zero network.
- **Image stub** (`StubImageGenerator`): returns a pre-rendered **PNG from `backend/src/main/resources/stubs/posters/{archetype}_{colour}.png`** (6 archetypes × 6 colours = **36 pre-rendered posters**, ~150 KB each). Not face-swapped for the POC — the user's photo is composited into a reserved portrait window on the backend using `java.awt.Graphics2D.drawImage` (no external library), then the result is base64-encoded as a data URL and returned in the JSON response.
- **Fallback** (`FallbackPosterProvider`): used when either stub throws or when `spring.profiles.active=force-stub-failure` is set. Returns a single canned poster + a canned `GeneratedCharacter` so SC-004 is testable.

**Rationale**

- Deterministic picks (hash-seeded) mean contract and integration tests can snapshot the output and assert byte-for-byte equality. Non-deterministic stubs would flake.
- Pre-rendered PNGs keep generation cost at Graphics2D-compositing speed (~30 ms in a warm JVM), well inside the 500 ms internal budget. That budget in turn keeps SC-001 (≤ 3 s) and SC-004 (≤ 5 s) comfortably achievable.
- Everything ships in `src/main/resources/` — no external network call, which is the whole point of FR-014.
- The `CharacterGenerator` / `ImageGenerator` interfaces are the seam: a follow-up feature swaps the `stub` implementations for real-provider ones without touching `AlterEgoController` or `AlterEgoService`.
- 8 × 6 character-variant × 36 pre-rendered-poster matrix gives **1,728** distinct demo outputs for the SC-005 full matrix — well beyond FR-015's "a few distinct".

**Alternatives considered**

- **Random JSON character output**: breaks snapshot tests and makes SC-005 un-assertable. Rejected.
- **WireMock simulating Claude/fal HTTP**: right for contract-testing real providers later; overkill for a POC where nothing talks to the network. Rejected now, kept in mind for the follow-up feature that wires real providers.
- **Hit `picsum.photos` or similar for placeholder images**: external network dep contradicts FR-014 and makes offline demos fail. Rejected.
- **Render SVG posters on the fly**: gives more compositional flexibility but needs a rasteriser (Batik / Apache FOP) and significantly more rendering code. Not worth it for a POC. Rejected.
- **Return an image URL pointing to a backend-served asset instead of a data URL**: requires serving an asset keyed by request id, which implies caching/TTL and a lifetime the FR-016 "only in memory for the request's duration" rule doesn't comfortably allow. Rejected — data URL keeps everything request-scoped.

---

## R3 — Photo transport format and size constraints

**Decision**

- **Request**: `POST /api/v1/alter-egos`, `Content-Type: multipart/form-data`.
  - Part `photo`: `image/jpeg` or `image/png`, max **5 MB** (enforced via Spring's `spring.servlet.multipart.max-file-size=5MB`). Server rejects other MIME types with `415 Unsupported Media Type`.
  - Part `selections`: `application/json`, strictly-typed record (`pose`, `colour`, `archetype`, `universe`, `firstName`) validated by Bean Validation annotations.
- **Response**: `application/json`. The poster image is returned as a **data URL** (`data:image/png;base64,...`) inside the JSON body, so the frontend renders it with a simple `<img src=…>` and no second request.
- **Client-side downscale**: the frontend downscales the photo to a maximum of **1024 × 1024** (preserving aspect) using `ImageBitmap` + `OffscreenCanvas` → `image/jpeg` at quality 0.85 before sending. Keeps typical payloads ≤ 500 KB and buys latency headroom for SC-001.
- **No chunked upload**, no progressive upload, no streaming. One request, one response.

**Rationale**

- Multipart is the correct HTTP idiom for binary-plus-metadata. Base64-in-JSON inflates the payload by ~33% for no benefit here.
- A 5 MB cap comfortably accommodates downscaled JPEG/PNG photos at any realistic resolution while protecting the backend from accidental DoS.
- Client-side downscale is a ~20-line utility and pays back 3–5× in transport size reduction; it also removes incidental EXIF data (privacy nice-to-have even for a POC).
- Data URL in the response means the browser can render the poster from the parsed JSON without an extra fetch, satisfying SC-001's ≤ 3 s p95 end-to-end budget.
- Single-request shape matches FR-014 literally.

**Alternatives considered**

- **Base64-encode the photo in a JSON field**: simpler parsing on both sides (one content type), but 33% payload inflation and awkward diffing for contract tests. Rejected.
- **Two-step upload (PUT photo → POST selections with returned ID)**: enables progress UIs and resumable uploads — both out of scope and contradict FR-014's "single request" framing. Rejected.
- **Return the poster as an HTTP `image/png` body, character JSON in headers**: non-standard, hostile to tooling. Rejected.
- **Return a URL to a cached asset**: as argued in R2, can't do this without a lifetime, which conflicts with FR-016. Rejected.

---

## R4 — Accessibility tooling: how SC-007 is actually checked

**Decision**

- **Automated scans**: `@axe-core/playwright` driven from the Playwright E2E suite. One `axe-scan.spec.ts` runs axe-core against four page states: entry screen, loading state (interrupted mid-request), success state, force-failure fallback state. Assertion: zero `serious` or `critical` violations. This is literally the wording of SC-007.
- **Static a11y linting**: `eslint-plugin-jsx-a11y` wired into `eslint.config.js` (flat config, constitutional requirement). Runs on every `npm run lint` and in CI.
- **Keyboard walkthrough**: `keyboard-walkthrough.spec.ts` drives Tab / Shift-Tab / Space / Enter from the entry screen all the way through `Start over`, asserting that (a) focus visibly lands on each interactive control in order, (b) `document.activeElement` matches the expected element ID at each step, (c) the full journey completes without `page.mouse`.
- **Colour picker (FR-021)** uses a visible text label per swatch plus an `aria-label` including the colour name ("Purple", "Cyan", …), so selection never depends on colour perception alone. The axe-scan catches missing labels; the keyboard walkthrough asserts arrow-key navigation within the radio-group pattern.
- **ARIA live regions (FR-022)**: a shared `<LiveRegion politeness="polite">` component mounts once in `App.tsx` and announces state transitions via a `useLiveAnnouncer()` hook. Announcement strings are covered by unit tests; live-region DOM presence is asserted by axe-core.

**Rationale**

- `@axe-core/playwright` is the de-facto standard for automated WCAG AA scans in an E2E suite. Zero new test infrastructure: the Playwright runner is already mandatory per constitutional Principle III.
- `eslint-plugin-jsx-a11y` catches common anti-patterns (missing `alt`, click-on-`div`, bad label associations) at dev time, shortening feedback loops before tests run.
- Playwright can simulate keyboard-only navigation natively; no separate tool needed.
- Together these three layers directly implement SC-007's two assertions ("zero serious/critical violations" + "keyboard-only walkthrough succeeds") as automated, CI-runnable tests. No manual a11y QA is assumed.

**Alternatives considered**

- **`vitest-axe` for component-level scans**: catches violations in isolated components but misses app-level concerns (focus management across screen transitions, live-region announcement ordering). Kept as a *supplement* if coverage drops, not a replacement. Not mandated by the plan.
- **Lighthouse CI for a11y + perf audits**: broader signal (performance, SEO, PWA), but heavier setup and no better a11y coverage than axe-core for our purposes. Deferred to a later infra feature.
- **Manual pa11y runs**: not automatable in the same test suite; requires a separate CI job and maintenance. Rejected.

---

## Smaller resolved decisions

### S1 — Frontend state management: `useReducer` + Context (not Zustand)

**Decision**: The `AlterEgoSession` state (photo, selections, generation phase) lives in a `useReducer` wrapped by a feature-scoped `AlterEgoContext`. TanStack Query owns the generation mutation separately. Zustand is **not** introduced for this feature.

**Rationale**: The session is one self-contained reducer-shaped state machine with ~6 fields and five transitions (idle → picking → generating → succeeded / failed-with-fallback → idle). `useReducer` + Context is exactly the React-standard shape for this. Introducing Zustand would add a dependency without a real payoff while the state surface is this small. The constitution's Technology Standards table permits **"Zustand or React Context API"** — this decision lands on the latter. If session complexity grows (multi-avatar history, offline queueing), a follow-up feature can promote to Zustand without a constitution amendment.

### S2 — Java package naming

**Decision**: All backend source lives under `com.aiavatar.alterego.*`. Tests mirror the same tree under `backend/src/test/java/com/aiavatar/alterego/`.

**Rationale**: Matches the tasks-template's placeholder `com.aiavatar.<feature>` and the repo/product-umbrella distinction captured in constitution v1.0.2. Feature-scoped packaging means a subsequent feature lands under `com.aiavatar.<next_feature>` without touching this tree.

### S3 — CORS / dev proxy

**Decision**: Vite dev server proxies `/api/**` → `http://localhost:8080` (see `frontend/vite.config.ts`). Backend adds no CORS in `dev` — the proxy makes requests same-origin from the browser's perspective. For Docker Compose and production, `nginx.conf` in the frontend container proxies `/api/**` to the backend service.

**Rationale**: Avoids CORS configuration drift between dev and prod. One transport mechanism (reverse proxy) handles both. Backend stays oblivious to front-end origin.

### S4 — Ports (dev)

**Decision**: Frontend Vite dev server on `5173` (Vite default); backend Spring Boot on `8080`; Actuator on the same `8080`. No port collisions between the two `npm run dev` / `./gradlew bootRun` processes.

**Rationale**: Defaults. No reason to deviate.

---

## Exit status

- ✅ All NEEDS CLARIFICATION from `plan.md` Technical Context resolved.
- ✅ No decisions require a constitution amendment.
- ✅ Ready for Phase 1 (data-model.md, contracts/, quickstart.md).

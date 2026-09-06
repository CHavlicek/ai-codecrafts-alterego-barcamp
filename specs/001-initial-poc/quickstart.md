# Quickstart: Initial POC — AI Alter Ego Generator

**Feature**: `001-initial-poc` | **Date**: 2026-04-21

End-to-end instructions to bring the feature up locally, verify the full user journey, and run the test suites. Assumes `backend/` and `frontend/` have already been scaffolded by the tasks in `tasks.md` (not yet generated at the time this plan lands).

---

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 21 (LTS) | `java --version` → `openjdk 21.x` |
| Gradle | 9.4+ | `gradle --version` (install via `brew install gradle` / `sdk install gradle 9.4`) |
| Node | 20.x (current LTS) | `node --version` → `v20.x` |
| npm | ≥ 10 (ships with Node 20) | `npm --version` |
| Docker + Compose | Current | `docker compose version` |
| A modern browser | Chromium / Firefox / Safari current | — |

The Gradle **wrapper is not committed** to this repo — use a system Gradle install for the dev loop. (If you prefer the `./gradlew` UX, run `cd backend && gradle wrapper --gradle-version 9.4` once locally; the generated wrapper files are gitignored and stay on your machine.) The Docker build uses the official `gradle` image, so Compose doesn't depend on either the wrapper or your system Gradle.

No Redis, PostgreSQL, or SonarQube daemon required for this feature — they're part of the constitutional Technology Standards but lie outside this POC's scope.

---

## 1. Bring up the stack (Docker Compose — preferred for smoke demo)

From the repo root:

```bash
docker compose up --build
```

This builds and runs:

- `backend` — `eclipse-temurin:21-jre-alpine` serving on `:8080`
- `frontend` — `nginx:alpine` serving the Vite build, reverse-proxying `/api/**` → `backend:8080` on `:5173`

Open `http://localhost:5173` in a browser. Expect the entry screen: photo intake (camera + upload), four picker grids (pose / colour / archetype / universe), first-name input, and a disabled **Generate** button.

## 2. Bring up the stack (native — preferred for dev loop)

Two terminals, from the repo root:

**Terminal A — backend**

```bash
cd backend
gradle bootRun
```

Spring Boot comes up on `:8080`. Actuator is reachable at `http://localhost:8080/actuator/health` (expect `{"status":"UP"}`).

**Terminal B — frontend**

```bash
cd frontend
npm install
npm run dev
```

Vite dev server on `:5173`. The dev proxy (`frontend/vite.config.ts`) forwards `/api/**` to `http://localhost:8080`, so the browser makes same-origin requests.

## 3. Walk the happy path manually

1. Click **Camera** (grants camera permission on first use) **or** **Upload** and pick a JPEG/PNG ≤ 5 MB. Verify the preview thumbnail appears.
2. Pick **one** pose (e.g. `Heroic`), **one** colour (e.g. `Purple`), **one** archetype (e.g. `Cloud Wizard`), **one** universe (e.g. `Star Wars`).
3. Type a first name (e.g. `Paula`).
4. Verify **Generate** is now enabled. The "missing inputs" hint has cleared.
5. Click **Generate**. Expect a loading indicator that announces "Generating your alter ego…" to assistive tech (ARIA live region).
6. Within ≤ 3 seconds (SC-001), the loading state is replaced by the poster: the user's face composed into the pre-rendered setting, with a two-line hero title (`PAULA` / `The Cloud Guardrail`), a tagline, three superpowers, and a quote.
7. Click **Start over**. Form resets in ≤ 200 ms (SC-006). No prior state visible.

## 4. Walk the fallback path manually (SC-004)

Relaunch the backend with the failure-injection profile:

```bash
cd backend
SPRING_PROFILES_ACTIVE=force-stub-failure ./gradlew bootRun
```

Repeat the happy path. Expect:

- The poster still appears (FR-018 "visibly degraded but complete poster").
- A non-blocking, recoverable error banner is rendered next to the poster: e.g. "We had trouble reaching the character service — showing you the fallback alter ego instead."
- The banner is announced politely via the live region (FR-023).
- `meta.outcome` in the underlying HTTP response is `fallback` — verify in the browser DevTools Network tab.
- Total time from click to rendered fallback poster ≤ 5 s (SC-004).

## 5. Run the backend tests

```bash
cd backend
./gradlew test                              # unit + contract
./gradlew integrationTest                   # @SpringBootTest full pipeline
./gradlew jacocoTestCoverageVerification    # asserts ≥ 90% line coverage (Principle III)
./gradlew dependencyCheckAnalyze            # Principle VI gate
```

Expect all green; coverage report at `backend/build/reports/jacoco/test/html/index.html`.

## 6. Run the frontend tests

```bash
cd frontend
npm test                    # Vitest + React Testing Library unit/component
npm run test:coverage       # asserts ≥ 90% (Principle III)
npm run test:e2e            # Playwright E2E (happy-path + fallback + keyboard-walkthrough)
npm run test:a11y           # Playwright + @axe-core/playwright — SC-007 gate
npm audit --audit-level=high # Principle VI gate (zero HIGH/CRITICAL)
npm run lint                # ESLint + jsx-a11y static checks
```

Expect zero a11y violations at `serious` or `critical` level (SC-007). The keyboard walkthrough test completes without any `page.mouse` calls.

## 7. Verify the OpenAPI contract

The canonical contract is `specs/001-initial-poc/contracts/alter-egos.openapi.yaml`. Two things lock against it:

- **Frontend** ships a generated TypeScript types file; regenerate with `npm run generate:api-types` (uses `openapi-typescript`). CI fails if the generated file drifts from the checked-in one.
- **Backend** contract-test (`AlterEgoControllerContractTest.java`) validates real controller responses against the schema using `swagger-request-validator-core`.

## 8. What "done" looks like for this feature

- [ ] Docker Compose brings the whole stack up with a single command; both happy and fallback paths are user-walkable.
- [ ] All seven Success Criteria in the spec are green in CI: **SC-001** (≤ 3 s p95), **SC-002** (≤ 60 s first-time completion, validated by manual walkthrough), **SC-003** (zero blank states across 100% of attempts), **SC-004** (≤ 5 s fallback), **SC-005** (poster fields present across the pose × colour × archetype × universe matrix — sampled), **SC-006** (< 200 ms reset), **SC-007** (axe-core zero serious/critical + keyboard-only walkthrough).
- [ ] Principle III gates (≥ 90% coverage both sides, mandatory integration test) green.
- [ ] Principle VI gates (`npm audit` / `dependencyCheckAnalyze` clean at HIGH/CRITICAL) green.
- [ ] OpenAPI contract test green on both sides; no schema drift.
- [ ] `aiavatar-mock.html` has been removed from the repo (per the `.gitignore` comment — it was POC input only).

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `POST /api/v1/alter-egos` returns 415 | Photo MIME not `image/jpeg` / `image/png` | Re-encode; the client downscale path produces JPEG automatically. |
| `POST /api/v1/alter-egos` returns 413 | Photo > 5 MB after downscale | Increase `spring.servlet.multipart.max-file-size` only for investigation; normal flow shouldn't hit this. |
| Browser shows a blank poster area after Generate | Data URL exceeded browser limit | Shouldn't happen with ~150 KB PNGs; check Network tab for truncated `dataUrl`. |
| Camera button does nothing | Browser denied permission | Check site permissions; the upload path is always available as a fallback. |
| Playwright axe-scan fails with a `serious` violation | Missing ARIA label on a new picker | Run `npm run lint` first — `jsx-a11y` usually catches this before tests. |
| Backend logs contain photo byte output | Logback filter misconfigured | Check `logback-spring.xml` — the `PhotoRedactionFilter` must be enabled in every profile. |

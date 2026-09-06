# Quickstart — Verify `024` after merge

**Audience**: a maintainer who has just merged `024-architecture-refactor` to `main` and wants to confirm all five user stories meet their acceptance scenarios on a fresh checkout.

**Prerequisites**: Docker + Docker Compose available; `npm` and a JDK 21 toolchain available locally; `.env.local` populated with at least `SPRING_PROFILES_ACTIVE=stub` (anything else requires real Gemini/fal.ai/SMTP credentials).

Time budget: **15 minutes** end-to-end.

---

## 0. Cold checkout

```bash
git checkout main && git pull
./gradlew :backend:clean
(cd frontend && rm -rf node_modules dist && npm ci)
```

---

## 1. Run the full test suite — confirm SC-003 (≤ 2 min total, ≤ 10 s unit tier)

```bash
# Backend — all five tiers
time ./gradlew :backend:test    # aggregate task — runs unit, service, contract, integration, arch

# Frontend — unit + component
time (cd frontend && npm test)
```

**Expected**: green suite, wall-clock under 2 min cold cache.

```bash
# Backend — slice by tier, confirm unit tier alone is under 10 s
time ./gradlew :backend:unitTest
time ./gradlew :backend:archTest
time ./gradlew :backend:contractTest
time ./gradlew :backend:serviceTest
time ./gradlew :backend:integrationTest
```

**Expected**: `unitTest` completes in < 10 s; `archTest` < 5 s.

✅ **US4 acceptance** verified by these wall-clock measurements.

---

## 2. Verify the provider seam — US1

Start the backend in stub profile:

```bash
SPRING_PROFILES_ACTIVE=stub ./gradlew :backend:bootRun
```

In another shell, fire a generate request:

```bash
# Use any small JPEG you have at hand. If your checkout doesn't include one,
# generate a 256×256 grey square as a test fixture:
#   convert -size 256x256 xc:grey /tmp/sample-photo.jpg     # ImageMagick, or
#   ffmpeg -f lavfi -i color=grey:s=256x256 -frames:v 1 /tmp/sample-photo.jpg

curl -s -X POST http://localhost:8080/api/alter-ego \
  -H 'Content-Type: multipart/form-data' \
  -F 'selections={"firstName":"Sam","archetype":"backend-dev","universe":"star-wars","artStyle":"oil-painting","photoMode":"upload"};type=application/json' \
  -F 'photo=@/tmp/sample-photo.jpg' \
  | jq '.meta'
```

**Expected**:

```json
{
  "outcome": "fallback",
  "provider": "stub",
  "attemptedProvider": "none",
  "reason": "NOT_CONFIGURED",
  "correlationId": "K7P3F2H9TM2X"
}
```

Confirm in the backend log:

```text
event=generation.completed outcome=fallback provider=stub attemptedProvider=none reason=NOT_CONFIGURED correlationId=K7P3F2H9TM2X
```

**The correlationId matches.** ✅ **US5 acceptance** (Scenario 2).

---

## 3. Verify the layered map — US2

Open the backend tree:

```bash
ls backend/src/main/java/com/aiavatar/alterego/
```

**Expected** — exactly these four directories plus `AlterEgoApplication.java`:

```text
AlterEgoApplication.java
boundary/
application/
domain/
infrastructure/
```

Answer the four "where does X happen?" questions from US2 by `ls` alone:

| Question | Expected answer |
|---|---|
| Where does **input validation** happen? | `boundary/http/` + `domain/validation/` |
| Where is the **prompt built**? | `domain/prompt/` |
| Where is the **provider called**? | `application/AlterEgoUseCase.java` (one call site, through `ImageGeneratorPort`) |
| Where are **overlays applied**? | `application/pipeline/` |

✅ **US2 acceptance** (Scenario 1).

Run the ArchUnit tier to confirm dependency direction:

```bash
./gradlew :backend:archTest --rerun-tasks
```

**Expected**: passes; if it fails, the merge introduced a back-edge.

✅ **US2 acceptance** (Scenario 2/3 verified by ArchUnit + the `CorrelationIdFilter` test).

---

## 4. Verify frontend state separation — US3

```bash
cd frontend && npm run lint
```

**Expected**: passes. The new ESLint rule (forbid `useReducer` outside `features/alterego/state/`) is active.

Open `frontend/STATE.md` — the three-rule convention is documented in one screen.

Open `frontend/src/main.tsx` — confirm `QueryClientProvider` is the single server-state seam.

✅ **US3 acceptance** (all three scenarios verified by ESLint + presence of `STATE.md` + the existing `useGenerateAlterEgo` / `useSendAlterEgoEmail` hooks remaining the only TanStack Query entry points).

---

## 5. Verify the redaction guarantee — US5

```bash
./gradlew :backend:integrationTest --tests '*RedactionCoverageTest'
```

**Expected**: passes. This test boots the full stack, fires a generate request with a known photo + a known fake API key in `application.yml`, and asserts neither byte appears in any log line for the duration of the request.

✅ **US5 acceptance** (Scenario 3).

---

## 6. Verify the visible UX is unchanged

```bash
docker compose up -d
# Browse to http://localhost:5173 and exercise the Setup → Generate → Result flow
```

Confirm by eye:

- All labels match pre-024 copy (FR-2426).
- The Surprise Me, Start Over, and Send-as-email actions all work.
- The print view renders correctly.
- The result tab's entrance animation plays (005), the loading percentage counts up (013), the reduced-motion path is respected if the OS preference is set.

Run the frontend E2E suite to make this assertion mechanical:

```bash
(cd frontend && npm run test:e2e)
```

**Expected**: green. The Playwright suite from features 001–023 passes unchanged.

✅ **SC-004** (all prior acceptance scenarios still pass) and **SC-005** (HTTP shapes unchanged — already verified by `contractTest` in step 1).

---

## 7. Sanity check — `CLAUDE.md` and the constitution

```bash
grep -n "024-architecture-refactor" CLAUDE.md
grep -n "1.1.0" .specify/memory/constitution.md
```

**Expected**:

- `CLAUDE.md` lists `024-architecture-refactor` in Active Technologies with the new ArchUnit entry.
- The constitution is at v1.1.0 with three new principles (VII Layer Convention, VIII Provider Seam, IX Test Pyramid).

---

## What to do if a step fails

| Step | If it fails... |
|---|---|
| 1 | The test pyramid reorganization regressed. `git bisect` against the relevant Gradle task task. |
| 2 | The provider seam regressed. Inspect `AlterEgoUseCase` — it must not branch on `Provider`. The ArchUnit `NoProviderBranchingTest` should have caught this earlier; if it didn't, the rule itself is incomplete. |
| 3 | The layer convention regressed. ArchUnit `LayerBoundariesTest` should fail; if `ls` shows a fifth top-level directory, a stray move was committed. |
| 4 | The ESLint rule was relaxed or `STATE.md` was deleted. Restore from main's history. |
| 5 | A new log site was added during `024` that doesn't go through `MDC` or doesn't redact a sensitive field. Find via `RedactionCoverageTest`'s diff output. |
| 6 | A visible UX change was introduced. Revert the offending commit; refactors are not allowed to touch user-visible behaviour (FR-2426). |
| 7 | The constitution amendment didn't land. Apply the change documented in `research.md` R5 and re-run all tiers. |

# Quickstart — Gemini Image Generator (003)

**Feature**: 003-gemini-image-generator
**Date**: 2026-04-22

This is the how-to-run-and-verify companion to `plan.md`. It covers:
1. Local dev loop (with and without a Gemini API key).
2. Docker Compose deploy.
3. Verifying which provider path (`real` vs `fallback`) a given run took.
4. Running the fault-injection matrix (SC-205 coverage).

Assumes you have the repo cloned and the 002 baseline working. No new top-level dev commands are introduced — only env-var flags and test targets.

---

## 1. Prerequisites

- Java 21 (LTS). `java -version` should report `21.*`.
- Node 20+ (for the frontend). `node -v` should report `v20.*`.
- Docker + Docker Compose (for the full-stack smoke test).
- **Optional**: a Google Gemini API key. If you don't have one, the app still works — every run will take the `fallback` path with `reason: not_configured`, which is what the fault-injection matrix (SC-204) requires anyway. Get a key from [https://aistudio.google.com/app/apikey](https://aistudio.google.com/app/apikey) if you want to exercise the `real` path.

---

## 2. Run locally without a Gemini key (fallback path always)

Two terminals.

**Terminal 1 — backend** (from `backend/`):

```sh
./gradlew bootRun
```

The Spring profile defaults to `default`; no `aiavatar.gemini.api-key` is set, so `GeminiImageGenerator` is not wired. Every Generate request takes the stub path and the response carries `meta.outcome: "fallback", meta.reason: "not_configured"`.

**Terminal 2 — frontend** (from `frontend/`):

```sh
npm install    # first time only
npm run dev
```

Open `http://localhost:5173/`. Complete the Setup form, press Generate, see a stub poster on the "Your Alter Ego" tab. The fallback banner (generic preview notice, per FR-214) is visible above the poster.

**Verify the path**:

- Browser devtools → Network tab → the `POST /api/v1/alter-egos` response. Look at the JSON body: `meta` should be `{ "outcome": "fallback", "reason": "not_configured", "correlationId": "…" }`.
- Backend log line (Terminal 1): one structured JSON event per Generate with `msg=generation.completed`, `outcome=fallback`, `reason=not_configured`. Neither the API key nor the photo bytes appear anywhere in the log (FR-215 / FR-217 / SC-206).

---

## 3. Run locally with a Gemini key (real path on success)

```sh
export GEMINI_API_KEY=<your key from aistudio.google.com>
# Optional overrides:
# export GEMINI_MODEL_ID=gemini-2.5-flash-image-preview   # default
# export GEMINI_REQUEST_TIMEOUT_MS=25000                  # default

cd backend && ./gradlew bootRun
```

The presence of `GEMINI_API_KEY` activates the `gemini` Spring profile (see `application.yml`). `GeminiImageGenerator` is wired as the `ImageGenerator` bean.

Repeat the frontend dev command in Terminal 2 (unchanged).

Complete Setup → press Generate. On the happy path, you get a **Gemini-generated poster** on the Your Alter Ego tab, no fallback banner.

**Verify the path**:

- Browser devtools → response body: `meta` should be `{ "outcome": "real", "correlationId": "…" }`. No `reason` field (JSON `NON_NULL` inclusion; FR-214 specifies reason is fallback-only).
- Backend log: `outcome=real`, no `reason` field.
- The poster image in `<img src="data:image/…">` is from Gemini — repeat the flow with a different role and confirm the image changes.

**If you see `outcome: "fallback"` despite having a key set**, check the `reason`:

| `reason` | Likely cause | What to do |
|---|---|---|
| `not_configured` | Env var didn't make it into the Spring process | Confirm `echo $GEMINI_API_KEY` in the same shell; restart `bootRun` |
| `network_error` | DNS / egress blocked | `curl -v https://generativelanguage.googleapis.com/` |
| `rate_limited` | Free-tier quota hit | Wait, or switch to a paid project |
| `timeout` | Slow network | Raise `GEMINI_REQUEST_TIMEOUT_MS` |
| `malformed_response` | Model id invalid or API shape drift | Check `GEMINI_MODEL_ID`; inspect backend log for the full Gemini error body |
| `safety_refused` | Your inputs tripped Gemini's safety filters | Try a different photo / universe / vibe |

---

## 4. Run under Docker Compose

From repo root:

```sh
# Without key — stub/fallback path always:
docker compose up --build

# With key — real path:
GEMINI_API_KEY=<your key> docker compose up --build
```

`docker-compose.yml` threads `GEMINI_API_KEY` (and the optional `GEMINI_MODEL_ID`, `GEMINI_REQUEST_TIMEOUT_MS`) into the `backend` service's environment. The frontend service does **not** receive the key (FR-211).

App at `http://localhost:5173/`, backend at `http://localhost:8080/`, `/actuator/health` should report `UP`.

---

## 5. Running the fault-injection matrix (SC-205)

The matrix is wired as a parametrised JUnit test: `GenerateAlterEgoGeminiFailureIT`. WireMock stands in for Gemini; each parameter row injects a different failure.

```sh
cd backend
./gradlew test --tests 'com.aiavatar.alterego.integration.GenerateAlterEgoGeminiFailureIT'
```

Matrix rows (one assertion per `reason` code):

| Row | Stub | Expected `reason` |
|---|---|---|
| 1 | `Fault.CONNECTION_RESET_BY_PEER` | `network_error` |
| 2 | HTTP 500 repeatedly | `network_error` |
| 3 | HTTP 429 + `Retry-After: 30` | `rate_limited` |
| 4 | `.withFixedDelay(30_000)` exceeding the 25 s per-attempt timeout | `timeout` |
| 5 | HTTP 200 + `{"garbage": true}` | `malformed_response` |
| 6 | HTTP 200 + `{"promptFeedback": {"blockReason": "SAFETY"}}` | `safety_refused` |

The `NOT_CONFIGURED` row is covered separately by `GenerateAlterEgoFallbackIT` (runs under the `default` profile so the Gemini call isn't even attempted).

Run the happy-path integration test against WireMock:

```sh
./gradlew test --tests 'com.aiavatar.alterego.integration.GenerateAlterEgoGeminiIT'
```

---

## 6. Running the full test suite and coverage gates

```sh
# Backend — unit + integration + contract tests + Jacoco coverage report
cd backend && ./gradlew test jacocoTestReport

# Frontend — unit/component tests with Vitest coverage
cd frontend && npm test -- --coverage

# Frontend — Playwright E2E
cd frontend && npm run test:e2e
```

Coverage gates (Principle III): **≥ 90% line coverage per module**, enforced by the existing Jacoco + Vitest configs. `GeminiImageGenerator`, `PhotoReducer`, `GeminiPromptBuilder`, `GeminiClient`, and `GeminiProperties` are all covered by unit tests (`src/test/java/com/aiavatar/alterego/unit/`).

---

## 7. Demo checklist (booth / conference run)

A one-page ops checklist for demoing 003 live:

- [ ] `GEMINI_API_KEY` exported in the running shell (verify: `printenv GEMINI_API_KEY | head -c 6`).
- [ ] Backend started; `curl http://localhost:8080/actuator/health` reports `UP`.
- [ ] Egress to `generativelanguage.googleapis.com` works from the demo network (quick `curl -vI https://generativelanguage.googleapis.com/` to confirm TLS handshake).
- [ ] Frontend at `http://localhost:5173/`.
- [ ] Test run with your own photo → devtools confirm `meta.outcome: "real"` on the response.
- [ ] Second test run with a different role/universe → confirm image visibly changes.
- [ ] Unplug network (or `iptables -A OUTPUT -d generativelanguage.googleapis.com -j REJECT` if you prefer) → confirm next run shows the fallback banner and `meta.outcome: "fallback", reason: "network_error"`.
- [ ] Backend log tailing (e.g. `tail -f backend/build/logs/aiavatar.log` if configured, else stdout) shows one per-run `outcome=…` line.

If any of these fail, consult the troubleshooting table in §3.

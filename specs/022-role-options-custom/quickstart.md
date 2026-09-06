# Quickstart: New Options for the Role Category

**Feature**: 022-role-options-custom · **Phase**: 1 · **Date**: 2026-05-11

A working-engineer's how-to-verify and how-to-iterate guide for this feature. Read after `spec.md` + `plan.md`.

---

## Prerequisites

- Repo cloned, on branch `022-role-options-custom`.
- Node ≥ 20 + npm available (`frontend/`).
- JDK 21 available (`backend/`).
- The `.env` for whichever image provider you want exercised (Gemini or fal.ai); not required for stub / fallback runs.

## Run the dev stack

Two terminals:

```sh
# Terminal 1 — backend (Spring Boot)
cd backend
./gradlew bootRun

# Terminal 2 — frontend (Vite)
cd frontend
npm install              # only if package-lock.json changed (this feature: no change)
npm run dev
```

Open `http://localhost:5173`. The Setup tab loads with the nine-option Role grid + custom-role input.

---

## Smoke test — the three new prefab options (User Story 1)

1. Take or upload a photo.
2. Type your first name.
3. In the Role category, scroll to the bottom three pills: **HR**, **Administration**, **Customer Relations**. Verify each is keyboard-reachable (Arrow keys from the first prefab pill should land on each in order).
4. Pick *HR*. Pick a Universe and an Art Style.
5. Press **Generate my alter ego**.
6. Verify the poster's text-overlay role label reads `HR`. Verify the generated image visibly evokes the HR role through props / environment / attire (the no-text guarantee — FR-2103 / SC-2206 — still holds; the role MUST NOT appear as transcribed text inside the image).
7. Repeat with *Administration* and *Customer Relations*.

---

## Smoke test — custom-role input (User Story 2)

1. Reset (Start Over button) to a clean Setup.
2. Take a photo + type your first name.
3. **Without picking a prefab**, click into the custom-role input and type `Tester`. Verify:
   - All nine prefab pills become visibly blurred.
   - Clicking any prefab pill has no effect (selection state does not change).
   - Tabbing past the Role grid skips every prefab pill and lands on the custom-role input directly.
4. Verify the trailing `X` button appears inside the input.
5. Pick a Universe + Art Style, press Generate. Verify the poster's text-overlay role label reads `Tester` and the image evokes a tester / QA scene.
6. Reset and pre-pick *AI Engineer*, then start typing into the custom-role input. Verify the *AI Engineer* selection is silently cleared the moment you type the first non-whitespace character (no confirmation, no alert).
7. Click the `X` button. Verify:
   - The input clears immediately.
   - No alert / confirmation appears.
   - The prefab pills un-blur and become clickable again.
   - The *AI Engineer* pill is **not** re-selected (FR-2208 — no restore).
8. Type whitespace only (spaces / tabs). Verify the prefab pills stay clickable (FR-2204 + SC-2205 — whitespace-only doesn't trigger precedence).
9. Try to type more than 100 characters. Verify the input refuses further input past character 100 (browser-level `maxLength`).

---

## Smoke test — Surprise Me + custom (User Story 3)

1. Reset, then type `Tester` into the custom-role input (prefab pills blur).
2. Press **Surprise Me**.
3. Verify:
   - The custom-role input is now empty.
   - The prefab pills are un-blurred and one is selected (uniformly from the nine — could be any).
   - Universe + Art Style are also rolled.
   - The Setup → Generating → Alter Ego tab transition still fires as in 009.

---

## Local backend assertions

```sh
cd backend
./gradlew test --tests '*AlterEgoUserSelectionsValidationTest'   # class-level OR-validator
./gradlew test --tests '*GeminiPromptBuilderTest'                # role label substitution
./gradlew test --tests '*FalAiPromptBuilderTest'                 # role label substitution
./gradlew test --tests '*GeminiCharacterPromptBuilderTest'       # bio prompt swap
./gradlew test --tests '*AlterEgoControllerIntegrationTest'      # happy path with customRole
./gradlew test                                                   # full suite (coverage gate ≥ 90%)
```

Manual API check with `curl`:

```sh
# Prefab role
curl -s -X POST http://localhost:8080/api/v1/alter-egos \
  -H 'X-Request-Id: dev-1' \
  -F 'photo=@/path/to/photo.jpg' \
  -F 'selections={"archetype":"hr","universe":"marvel","artStyle":"oil-painting","photoMode":"single","firstName":"Lalo"};type=application/json' | jq '.meta'

# Custom role (archetype omitted)
curl -s -X POST http://localhost:8080/api/v1/alter-egos \
  -H 'X-Request-Id: dev-2' \
  -F 'photo=@/path/to/photo.jpg' \
  -F 'selections={"customRole":"Tester","universe":"marvel","artStyle":"oil-painting","photoMode":"single","firstName":"Lalo"};type=application/json' | jq '.meta'

# Invalid: both archetype and customRole absent → 400 RFC 7807
curl -s -X POST http://localhost:8080/api/v1/alter-egos \
  -H 'X-Request-Id: dev-3' \
  -F 'photo=@/path/to/photo.jpg' \
  -F 'selections={"universe":"marvel","artStyle":"oil-painting","photoMode":"single","firstName":"Lalo"};type=application/json'
# Expect: HTTP 400 with detail "either archetype must be set or customRole must be non-blank"
```

---

## Local frontend assertions

```sh
cd frontend
npm run lint                                       # ESLint flat config
npx vitest run                                     # full Vitest suite (coverage gate ≥ 90% on touched modules)
npx vitest run state/reducer.test.ts               # CustomRoleChanged + SurpriseMePicked clear
npx vitest run state/selectors.test.ts             # gating: archetype OR customRole
npx vitest run components/CustomRoleInput.test.tsx # NEW component
npx vitest run components/ArchetypeGrid.test.tsx   # disabled-state coverage
npx playwright test role-custom.e2e.ts             # one E2E happy path
```

---

## Useful greps when debugging

```sh
# Where the role-of-record string lands (backend):
grep -rn 'roleLabel()' backend/src

# Where customRole flows on the frontend:
grep -rn 'customRole' frontend/src

# All 9 archetype values in one place:
grep -A 20 'enum Archetype' backend/src/main/java/com/aiavatar/alterego/model/Archetype.java
grep -A 12 'export type Archetype' frontend/src/features/alterego/types.ts
```

---

## Common pitfalls / gotchas

1. **`customRole: ""` on the wire**. The frontend MUST omit the field when blank — sending `""` is technically accepted server-side but pollutes Jackson logs in test fixtures. Pin this with a unit test (`alterEgoClient.test.ts`).
2. **Pre-blur prefab click via synthetic event**. The disabled grid is keyboard-inert AND has `pointer-events: none`, but the click handler also short-circuits in case a test runner dispatches a synthetic event that ignores CSS. Verify the handler-guard in the reducer test — dispatching `ArchetypeSelected` while `customRole.trim()` is non-empty is allowed by the reducer (defense-in-depth via UI gating).
3. **AccentResolver default when archetype is null**. The fallback poster and the accent-tone derivation default to `Archetype.BACKEND_DEV` purely as a colour-keying default. This is intentional — see research.md R4. If a UX critique surfaces "all custom roles look the same shade", that is the trade-off and out of scope for this feature.
4. **Image prompt's preamble still says "Engineering role"**. With HR / Administration / Customer Relations + custom strings, this preamble is mildly inaccurate but functional. We deliberately do NOT rephrase it in this feature to preserve the 021 byte-for-byte fixture. A follow-up may rephrase it once we re-baseline the fixture.
5. **No persistence**. The custom-role input is component-local + session reducer state only. Reloading the page clears it. This is by design (001 FR-016 / FR-017 / FR-024) and tested.
6. **Long custom strings on the poster**. The text overlay (017) handles truncation by render-width measurement. Spec FR-2215 says we don't truncate harder than today — that's already the case; the overlay test suite already covers the `"Customer Relations"` length range.

---

## What "done" looks like

- All FRs (FR-2201..FR-2220) covered by at least one test (unit / integration / E2E).
- Coverage gate ≥ 90% on every touched module (frontend + backend), per constitution Principle III.
- Manual smoke pass on the three User Story flows above.
- `./gradlew sonar` and `npm run lint` clean.
- PR opened with explicit human approval.

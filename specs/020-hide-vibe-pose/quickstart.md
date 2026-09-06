# Quickstart — 020 Hide Vibe and Pose Categories from UI

Local verification recipe for a reviewer / fresh contributor. Assumes the codebase is checked out at the `020-hide-vibe-pose` branch.

## 1. Run the test suites

```bash
# Frontend
cd frontend
npm install
npm test          # Vitest; expects PoseGrid/VibeGrid tests deleted, SetupLayout tests updated

# Backend
cd ../backend
./gradlew test    # JUnit 5; expects RandomCategorySelectorTest + updated controller contract test green
```

Coverage gate (Principle III): ≥ 90 % per module. Both `npm test -- --coverage` and `./gradlew test jacocoTestReport` must still report ≥ 90 % after the change.

## 2. Manually verify the UI change

```bash
# Terminal 1
cd backend && ./gradlew bootRun

# Terminal 2
cd frontend && npm run dev
```

Open the URL printed by Vite. On the Setup tab, confirm:

1. The right-hand column shows exactly three numbered theme sub-groups, labelled **1 Archetype**, **2 Universe**, **3 Art Style**, in that order.
2. There is **no** "Pose" or "Vibe" anywhere on the page — visible, off-screen, in DevTools "Accessibility Tree", or in the rendered HTML. Inspect with browser DevTools to confirm.
3. With a photo captured, an Archetype, a Universe, an Art Style, and a first name set — **Generate** becomes enabled.
4. **Surprise Me** is enabled once a photo + first name are set.

## 3. Manually verify the backend roll

With the dev server running, press **Generate** twice in a row with the **same** visible inputs. The two generated posters should differ (Gemini's intrinsic non-determinism notwithstanding, the prompt itself now varies because the rolled Pose and Vibe may differ between the two requests).

For a deterministic check, hit the endpoint directly:

```bash
curl -X POST http://localhost:8080/api/v1/alter-egos \
  -F 'photo=@./scratch/test-photo.jpg;type=image/jpeg' \
  -F 'selections={"archetype":"engineer","universe":"star-wars","artStyle":"oil-painting","firstName":"Mia","photoMode":"shoulders-up"};type=application/json'
```

A `200 OK` confirms the new request shape is accepted. Tail the backend logs to confirm the rolled Pose / Vibe values vary across consecutive identical requests.

## 4. Verify the "ignore client-supplied" behaviour

Send the **old** shape including `pose` and `vibe`:

```bash
curl -X POST http://localhost:8080/api/v1/alter-egos \
  -F 'photo=@./scratch/test-photo.jpg;type=image/jpeg' \
  -F 'selections={"pose":"heroic","vibe":"rebel","archetype":"engineer","universe":"star-wars","artStyle":"oil-painting","firstName":"Mia","photoMode":"shoulders-up"};type=application/json'
```

Expected: `200 OK`, and across 5+ repeats the rolled values are not pinned to `heroic` / `rebel`.

## 5. Constitution & lint gates

```bash
# Frontend
cd frontend && npm run lint && npm audit --omit=dev

# Backend
cd ../backend && ./gradlew dependencyCheckAnalyze
```

All MUST be green / clean before requesting PR review (Principle VI).

## 6. Smoke-check accessibility

In Chrome DevTools → Lighthouse → Accessibility audit on the Setup tab. The score MUST be ≥ the pre-change baseline. No new `aria-*` warnings, no new focus-order findings.

## 7. Print artefact regression

From the Alter Ego tab, press the Print button. The print-preview output MUST look identical (modulo the rolled Pose / Vibe variation) to a pre-change run. No new visible field. No layout shift.

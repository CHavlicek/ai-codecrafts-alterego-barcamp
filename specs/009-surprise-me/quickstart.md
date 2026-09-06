# Quickstart: Surprise Me (009)

Spec: [./spec.md](./spec.md) · Plan: [./plan.md](./plan.md)

## Run locally

```bash
# Frontend dev server (Vite, HMR)
cd frontend
npm install         # first time only
npm run dev

# In another shell: backend (unchanged from 003)
cd backend
./gradlew bootRun
```

Open http://localhost:5173 (the Vite default) in a browser.

## Manual validation — three user stories

### US1 — Surprise Me with no category picks

1. On the Setup tab, click the photo circle and take a selfie (or skip the tab, you need the photo first).
2. Type any non-empty name in the Name input.
3. Leave every category grid (Pose, Engineer role, Universe, Art style, Vibe) untouched.
4. Click **Surprise Me** in the action row next to Generate.
5. Expected:
   - The Your Alter Ego tab becomes active immediately (entrance animation plays per 005).
   - The loading state renders.
   - A poster appears when the response lands.
   - The 003 fallback notice appears instead if the real provider fails — unchanged behavior.

### US2 — Setup form reflects the random picks

1. Continue from US1 after the poster appears.
2. Click the **1 Setup** tab (it re-enables once the response lands per 007 FR-505).
3. Expected:
   - Each of the five category grids shows exactly one `aria-checked="true"` option.
   - Those options are the same values the randomizer picked (visible in the DOM, also in the React DevTools session state).

### US3 — Photo + Name gating

1. Start over (button on Your Alter Ego tab).
2. With no photo and no name, observe the Surprise Me button:
   - `disabled` HTML attribute present.
   - Hint text under it: `"Still needed: photo and a name."`.
3. Type a name only:
   - Hint updates to `"Still needed: photo."`.
4. Take a selfie (clear name again first):
   - Hint updates to `"Still needed: a name."`.
5. With both present:
   - Button enables. Hint disappears.
6. Try clicking while disabled at any point in the flow — nothing happens (no tab switch, no request).

## Tests

- `frontend/src/features/alterego/lib/randomSelections.test.ts` — pure utility: seeded-RNG assertions + empty-list guard + clamping on out-of-contract `rng() = 1`.
- `frontend/src/features/alterego/state/reducer.test.ts` — adds `SurpriseMePicked` case coverage.
- `frontend/src/features/alterego/state/selectors.test.ts` — `isReadyToSurprise` truth table + invariant `isReadyToGenerate ⇒ isReadyToSurprise`.
- `frontend/src/features/alterego/components/SurpriseMeButton.test.tsx` — enabled/disabled rendering, hint text, keyboard activation, disabled no-op.
- `frontend/src/features/alterego/hooks/useGenerateAlterEgo.test.tsx` — `surprise()` action sequence: `SurpriseMePicked` → `ActiveTabChanged(generate)` → `GenerateSubmitted` in that order.
- `frontend/tests/e2e/surprise-me.spec.ts` — end-to-end happy path + back-to-Setup reflects picks + disabled gating.

Run with:

```bash
cd frontend
npm test                    # Vitest unit/component (watch or CI)
npm run test:e2e            # Playwright E2E (starts the app, runs the suite)
```

## Troubleshooting

- **Random picks always look the same in tests**: you forgot to pass a real `Math.random` — the utility defaults to `Math.random` so production has zero setup. In tests, explicitly pass a seeded RNG if you want reproducible assertions.
- **Surprise Me enables without a photo**: check the `isReadyToSurprise` selector wiring — `state.photoBlob` must be truthy. The photo is a `Blob`, not a URL, so a missing photo shows up as `null`.
- **Setup tab re-enables in the middle of generation**: the 007 gating is driven by `phase`. If Surprise Me somehow lands the session in `phase === 'picking'` for longer than the React render cycle between the two dispatches, the gating briefly flickers. The ordering in `useGenerateAlterEgo.surprise()` (`SurpriseMePicked` then `submit()` whose `onMutate` fires synchronously) keeps the two dispatches in the same microtask, so no flicker is observable in practice. The Playwright spec asserts this.
- **Entrance animation doesn't play on Surprise Me**: verify the `ActiveTabChanged` dispatch in `useGenerateAlterEgo.onMutate` still carries `reason: 'generate'`. The 005 nonce increment is keyed on that field.

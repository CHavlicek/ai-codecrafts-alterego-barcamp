# Quickstart: Art Style Category

A minimal recipe to exercise the new category locally, once implementation is in.

## 1. Start the stack

From the repo root:

```bash
docker compose up --build
```

Or, for hot-reload development, run the two services separately:

```bash
# Terminal 1 — backend
cd backend
./gradlew bootRun

# Terminal 2 — frontend
cd frontend
npm install   # first run only
npm run dev
```

Then open the URL the frontend logs (default `http://localhost:5173`).

## 2. Manual smoke

1. Upload any portrait photo on the Setup tab.
2. Fill in first name + pick Pose / Archetype / Universe / Vibe as usual.
3. **New**: Art Style grid shows nine options — pick one (e.g. "Pixel Art" 👾).
4. Confirm the Generate button becomes enabled only after all five required
   selections (photo, pose, archetype, universe, art style) plus first name are set.
5. Click Generate. Watch the loading state, then the Alter Ego tab should display the
   returned poster.
6. Open DevTools → Network → the `/api/v1/alter-egos` request. Inspect the
   `selections` part of the multipart body. It MUST contain `"artStyle":"pixel-art"`.
7. Pick a different style (e.g. "Renaissance Portrait"), re-Generate, and confirm the
   returned poster visibly differs.

## 3. Automated checks

### Frontend

```bash
cd frontend
npm run lint
npm run test           # Vitest + RTL
npx playwright test    # E2E (must include the art-style flow)
npm run build
```

Coverage must stay ≥ 90% (Constitution Principle III).

### Backend

```bash
cd backend
./gradlew test         # JUnit + Mockito + @SpringBootTest integration
```

Relevant suites to watch:

- `ArtStyleTest` — `fromWire` round-trip + unknown-value rejection.
- `AlterEgoRequestValidationTest` — missing `artStyle` → 400.
- `GeminiPromptBuilderTest` — each art style produces a distinct, pinned label.
- Existing `GenerateAlterEgoGeminiInputCoverageIT` — extended with art-style matrix.

## 4. Roll-back

This feature adds nothing to disk / DB / cache (no persistence). To roll back, revert
the merge commit; no data migration or cleanup is required.

## 5. Exit criteria

- All listed automated checks green.
- Manual smoke confirms the art-style wire value reaches the backend and the returned
  poster visibly reflects the chosen style.
- PR review approved; merge to `main`.

# Quickstart: Group Photos (011)

## TL;DR

Adds a binary switch to the Setup tab: **Single Person** (default) or **Group Photo**. In Group mode the Gemini prompt instructs the model to render every person visible in the reference photo as the same alter-ego archetype / universe / art style / pose / vibe.

Zero new dependencies. Zero new endpoints. Zero persistence. Backwards-compatible wire contract (backend treats the field as optional and defaults to `SINGLE`).

## What changed

**Frontend (`frontend/src/features/alterego/`)**
- `types.ts` — new `PhotoMode` union, extended `Selections`.
- `options.ts` — new `PHOTO_MODE_OPTIONS` constant (Single / Group with `User` / `Users` icons).
- `state/reducer.ts` — new `photoMode` field (required), new `PhotoModeSelected` action.
- `components/PhotoModeSwitch.tsx` — new component (wraps `SelectionGrid`).
- `components/SetupLayout.tsx` — mounts the switch as step 0.
- `AlterEgoPage.tsx` / `hooks/useGenerateAlterEgo.ts` — thread `photoMode` into the outbound payload.

**Backend (`backend/src/main/java/com/aiavatar/alterego/`)**
- `model/PhotoMode.java` — new enum (`SINGLE` / `GROUP`).
- `model/AlterEgoRequest.java` — added trailing nullable `photoMode` + `effectivePhotoMode()` accessor.
- `service/gemini/GeminiPromptBuilder.java` — branches on mode: singular wording (unchanged) vs. plural / group wording.

## How to test locally

**Unit + integration (fast)**:
```bash
cd frontend && npm run test
cd ../backend && ./gradlew test
```

**Full sweep (what CI runs)**:
```bash
cd frontend && npm run lint && npm run build && npm run test:coverage
cd ../backend && ./gradlew check
```

**Manual walkthrough**:
```bash
cd frontend && npm run dev
# In another terminal:
cd backend && SPRING_PROFILES_ACTIVE=default ./gradlew bootRun
```

Then:
1. Open `http://localhost:5173`.
2. On the Setup tab, confirm the new "Single or Group Photo" section shows above Pose with **Single Person** checked.
3. Capture a photo (selfie works; for Group mode, take a photo with multiple faces if possible).
4. Pick a role / universe / art style / pose.
5. Type a name (in Group mode, use a team name).
6. Click Generate. The poster should reflect the mode — single subject vs. group portrait.
7. Click Start Over → the switch resets to Single.

## E2E (Playwright)

```bash
cd frontend && npx playwright test group-photos.spec.ts
```

The spec intercepts the multipart body and asserts `selections.photoMode === "group"` after flipping the switch.

## Rollback

Revert the single commit / PR. No migration. No data. No new dep. The backend's `photoMode` field is optional, so pre-11 clients and post-11 clients interoperate transparently during a partial rollout.

## Known trade-offs

- **Enum vs. boolean**: enum chosen for extensibility and self-documenting logs (research.md R8).
- **radiogroup vs. `role="switch"`**: radiogroup chosen so both labels are visible to AT users simultaneously (research.md R1).
- **Step 0 position vs. photo column**: step 0 chosen for visual parity with the existing five numbered groups (research.md R2).
- **Wire field optional vs. required**: optional for backwards-compatibility during rollout (research.md R3).

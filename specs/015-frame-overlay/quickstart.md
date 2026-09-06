# Quickstart — 015: Branded Poster Frame & 10×15 Print-Ready Aspect Ratio

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Date**: 2026-05-05

How to verify this feature locally — both manually (eyes on a rendered poster) and via the automated test suite. Mirrors the 008 quickstart format.

---

## Prerequisites

- Repo cloned, working directory `/Users/<you>/aiavatar` (or wherever you cloned it).
- Branch checked out: `015-frame-overlay`.
- `frontend/` and `backend/` configured exactly as the project README and the constitution's Technology Standards prescribe (Java 21 + Gradle Kotlin DSL on the backend; Node 20 + Vite on the frontend).
- The frame asset `backend/src/main/resources/branding/poster-frame.png` is present (it was added during `/speckit.specify` and is staged on this branch — `git status` will show it).

---

## Manual verification — happy path (real provider)

1. **Set the Gemini API key** so the real provider path is exercised end-to-end:
   ```bash
   export GEMINI_API_KEY=…   # your real Google API key with Gemini enabled
   ```
2. **Start the backend**:
   ```bash
   ./gradlew :backend:bootRun
   ```
   Wait for `Started AiAvatarApplication in N.NNN seconds`. The startup log should include exactly **one** `event=frame.asset.loaded` line (and **zero** `event=branding.logo.*` lines — the 008 path is gone).
3. **Start the frontend** in a second terminal:
   ```bash
   cd frontend && npm run dev
   ```
4. **Open** the dev URL (`http://localhost:5173` by default).
5. **Run one Generate**: complete Setup with any first name, any photo, any pose / archetype / universe / vibe / art style. Press **Generate**.
6. **Verify the rendered poster** on "Your Alter Ego":
   - The poster carries the new frame chrome — **SQUER** mark in the top-left header strip; **`<CODE/CRAFTS> 2026`** wordmark in the top-right header strip; a blue→pink gradient rounded border around the central image; small dot-matrix and circuit-line decorations down both side margins.
   - The character image fills the inner area of the frame; **no part of the character is covered** by the chrome (the frame's inner rectangle is transparent so the character shows through).
   - **No** standalone two-logo stack in the top-right corner of the central image (the 008 layout is gone).
7. **Verify the pixel ratio**: right-click the poster → "Save Image As…" → measure the saved PNG's dimensions. `height / width` should equal `1.5 ± 0.015` (i.e. 2:3 portrait, equivalently 10:15).
   - **Tip**: on macOS `sips -g pixelWidth -g pixelHeight saved.png`; on Linux `identify saved.png` (ImageMagick) or `file saved.png` (showed in the file metadata).
8. **Verify the encoding**: the saved file's first 8 bytes should be the PNG signature `89 50 4E 47 0D 0A 1A 0A`. `xxd saved.png | head -1` shows it.
9. **Verify the outbound prompt** (optional, for FR-1506): backend log at INFO/DEBUG level should include the prompt body sent to Gemini. Search for the substring `2:3 aspect ratio` (or the variant text); it MUST be present and `3:4 aspect ratio` MUST NOT be present.

---

## Manual verification — fallback path (no API key)

1. **Stop the backend** if running, then **unset the API key**:
   ```bash
   unset GEMINI_API_KEY
   ./gradlew :backend:bootRun
   ```
2. **Generate once** as above.
3. **Verify the rendered poster**:
   - The fallback stub renders (a black-on-accent-bordered card with `THE RESILIENT` / `DEGRADED, NOT DEFEATED.`).
   - The same frame chrome wraps the fallback as a real run.
   - The saved PNG is 2:3 portrait (1024×1536 with the bundled asset).
   - The frontend's poster card displays a non-blocking "fallback used" notice (the 003 banner — unchanged behaviour).

---

## Manual verification — fail-soft (frame asset deliberately broken)

1. **Stop the backend.** Move the frame asset out of the way:
   ```bash
   mv backend/src/main/resources/branding/poster-frame.png /tmp/poster-frame.png.bak
   ./gradlew :backend:bootRun
   ```
2. **Generate once.**
3. **Verify**:
   - The poster renders **un-framed** (raw character image, 2:3) — the user-facing path is not broken.
   - The backend startup log carries **one** `event=frame.asset.missing` (or `=decode_failed`) line.
   - For each Generate while the asset is missing, no per-request `WARN` is added beyond the startup line (the loader caches the missing sentinel; the overlay service short-circuits without re-decoding).
4. **Restore**:
   ```bash
   mv /tmp/poster-frame.png.bak backend/src/main/resources/branding/poster-frame.png
   ```

---

## Manual verification — wrong-ratio corrective (advanced; injectable stub)

This path is normally exercised only via the automated `PosterFrameOverlayServiceTest`'s wrong-ratio case. To exercise it manually you'd need to inject a `GeminiClient` test double that returns a 1:1 image; the integration test (`AlterEgoFlowFrameIntegrationTest`) is the canonical place to assert this. Skip this manual step unless you're debugging the corrective path.

---

## Automated test suite

Run the backend test suite:

```bash
./gradlew :backend:test
```

Expected outcome:
- All existing 001 / 002 / 003 / 014 tests still pass (unchanged paths).
- **NEW** tests that this feature adds:
  - `service.frame.PosterFrameAssetLoaderTest` — loaded / missing / decode-failed / inner-rectangle-bounds-correct.
  - `service.frame.PosterFrameOverlayServiceTest` — happy 2:3 / wrong-ratio corrective letterbox / missing-asset fail-soft / always-PNG output / fallback-path coverage.
  - `service.gemini.GeminiPromptBuilderTest` — regression-locks the 2:3 wording in both SINGLE and GROUP variants.
  - `integration.AlterEgoFlowFrameIntegrationTest` — `@SpringBootTest`, exercises real-provider stub + fallback both arrive at 2:3 PNG with frame chrome.
- **REMOVED** tests:
  - `service.branding.BrandingOverlayServiceTest` (whole class deleted with 008's overlay).
  - `service.branding.LogoAssetLoaderTest` (whole class deleted with 008's loader).

Coverage gate (`./gradlew :backend:jacocoTestCoverageVerification` or equivalent):
- Line coverage on `service/frame/` package ≥ 90% (Constitution Principle III).
- Total backend coverage stays ≥ 90% (the deleted 008 code came with its own deleted tests, so net coverage is preserved).

---

## What this quickstart deliberately does **not** cover

- **Print-station verification.** Out of scope for this quickstart; the 010 print path is unchanged. If you want to confirm a 2:3 poster prints correctly on 10×15 cm photo paper, run the 010 quickstart with this branch checked out.
- **Frontend test changes.** None — the frontend has no file changes in this feature. The existing `frontend/` test suite continues to apply.
- **Gemini API quota / billing.** As with 003 / 014, manual real-provider verification consumes a small amount of Gemini quota; running the automated suite does not (it uses an injected stub `GeminiClient`).

# Quickstart — 008: Verify the logo overlay locally

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Date**: 2026-04-24

Two paths to exercise end-to-end — one with the real Gemini provider, one with the fallback stub path. Both should produce a poster with the SQUER logo in the top-right corner and the CodeCrafts logo directly below it.

## Prereqs

- Java 21 JDK installed (`java --version` reports 21.x).
- Node 20+ and npm installed (for the frontend dev server).
- `docker` optional (if you want to run the prod container image).

## 1. Build + run the backend (stub / fallback mode — no API key needed)

```bash
cd backend
./gradlew bootRun
```

Startup logs should include either no `BrandingOverlay` WARN (happy path) or, if a logo resource is missing:

```
WARN  … LogoAssetLoader — logo asset missing: classpath branding/squer-logo.png (branding disabled for this asset)
```

## 2. Run the frontend dev server

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173`, complete Setup, upload any photo, and press **Generate**.

### What you should see

| Check | Pass criterion |
|---|---|
| SQUER logo | Top-right corner, with a visible margin from both top and right edges. |
| CodeCrafts logo | Directly below SQUER, right edges aligned, a small gap between them. |
| Equal width | Both logos visually the same width (~15% of the image's width). |
| Fallback works | Kill the backend `GEMINI_API_KEY` (unset it), rerun — the fallback stub poster still carries both logos in the same layout. |
| No regression | Character name/title/tagline/quote and accent colour from 002 all render as before. |

## 3. Run backend tests (TDD green path)

```bash
cd backend
./gradlew test --tests '*BrandingOverlay*' --tests '*LogoAssetLoader*' --tests '*BrandingOverlayIT*'
```

All four test classes should pass:

- `LogoAssetLoaderTest` (unit — classpath load + caching + missing-resource fail-soft)
- `BrandingOverlayServiceTest` (unit — pixel assertions at corners, MIME preservation, dim preservation, per-request fail-soft)
- `BrandingOverlayIT` (integration — `@SpringBootTest` end-to-end for real + fallback + degraded-logo path)
- The expanded `GenerateAlterEgoGeminiIT` (FR-704 guard — outbound Gemini request contains no logo bytes / URLs / filenames)

## 4. Sanity-check: no logos leak to Gemini

With the backend running in `gemini` profile and a real API key, tail the request-body capture log (or run the Gemini integration test in `./gradlew test --tests '*GenerateAlterEgoGeminiIT*'`). The captured outbound request body MUST NOT contain:

- The substring `squer` (case-insensitive) anywhere.
- The substring `codecrafts` (case-insensitive) anywhere.
- Any base64 payload sharing a prefix with either logo's bytes.

## 5. Manual fault-injection check (FR-711)

Rename `backend/src/main/resources/branding/squer-logo.png` to `…/squer-logo.png.bak`, rebuild, and hit Generate. Expected:

- The poster renders with only the CodeCrafts logo (one logo missing, other loaded).
- The backend logs one `WARN` at startup naming the missing resource.
- No 5xx, no frontend spinner, no broken image.

Put the file back when done.

## 6. Clean up

```bash
# backend
./gradlew clean
# frontend
rm -rf node_modules dist
```

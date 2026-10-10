# Implementation Plan: Verbund rebrand — "AI @ Verbund 2026"

**Branch**: `029-verbund-rebrand` | **Spec**: [spec.md](./spec.md) | **Status**: Implemented

## Summary

A brand-only re-skin of the AI Alter Ego app from SQUER / Code-Crafts to Verbund AG / fifty1 for the "AI @ Verbund 2026" event. Full light-corporate theme flip (VERBUND-blau on white), official Verbund + fifty1 logos in the web UI and baked into the generated poster frame, and "AI @ Verbund 2026" everywhere the old event name appeared. Categories, mechanics, HTTP contract, and no-persistence posture unchanged.

## Technical context

- **Frontend**: TypeScript 5.7 strict, React 19, Vite 8, Vitest + RTL + Playwright. No new runtime dependency. Fonts unchanged (Unbounded / Geist / Geist Mono).
- **Backend**: Java 21, Spring Boot 3.5, Gradle. No new runtime/test dependency. Poster frame regenerated with Pillow + cairosvg (build-time tooling only, not shipped in the JAR).
- **No persistence** — inherits 001 FR-016/017/024. Only static assets, CSS tokens, copy strings, and config defaults change.

## Design decisions

### D1 — Token remap, not token rename
`tokens.css` keeps every CSS-variable **name** and remaps only the **values** to the Verbund palette (e.g. legacy `--color-purple` → VERBUND-blau, `--color-pink` → energy green). This flips the whole theme with zero component churn. Hard-coded neon literals in `index.css` (aurora, glows, halation, thumbs, hex fallbacks) were swept to Verbund values by value.

### D2 — Light-corporate flip
Surfaces go white/off-white (`#F4F7FB` / `#FFFFFF`), text goes navy ink (`#0B2A4A`), atmosphere aurora recolored to hydro/energy washes, grain switched to `multiply` blend and the vignette to a soft navy so both read on a light ground.

### D3 — Poster frame regenerated programmatically
The frame chrome is baked into `poster-frame.png` pixels, so it can't be edited as separate layers. A committed generator (`backend/src/main/resources/branding/generate_poster_frame.py`) rebuilds it: white card, navy top band with the real VERBUND wordmark (rendered from `verbund-logo.svg`) + "AI @ VERBUND 2026", navy cutout border with a hydro-cyan hairline, side-margin energy accents, navy bottom band for the 017 text overlay, and a "Powered by fifty1" footer (real `fifty1-logo-black.png`). **The transparent cutout is re-punched at the identical geometry (x=88 y=94 w=586 h=791)** so `PosterFrameAssetLoader` bands, the fit geometry, and every overlay test pass unchanged. The old asset is preserved out-of-JAR at `backend/branding-src/poster-frame.squer-backup.png`.

### D4 — Text-safe-area invariant
Loader computes the text region at 6% vertical inset → y∈[954,1083]. The fifty1 footer is pinned below that band (~y=1105+) so name/role/quote never collide with it. Verified by compositing a sample poster.

### D5 — Logos as bundled assets
Official logos live under `frontend/src/assets/brand/` and are imported by `BrandHeader.tsx` (Vite fingerprints + bundles them). The `.svg`/`.png` import types come from `vite/client` — no new ambient declaration needed.

## Files changed

**Frontend**
- `src/styles/tokens.css` — full Verbund palette remap (surfaces, text, brand gradients, accents, shadows, focus ring, body atmosphere).
- `src/index.css` — atmosphere/vignette/grain recolor; swept neon literals → Verbund; new `.brand-header`, `.alter-ego-page__subtitle`, `.site-footer` styles.
- `src/features/alterego/components/BrandHeader.tsx` — **new**; VERBUND + "Powered by fifty1" logos.
- `src/features/alterego/AlterEgoPage.tsx` — mounts `BrandHeader`, title → "AI @ Verbund 2026" + subtitle.
- `src/App.tsx` — footer credit line.
- `src/assets/brand/{verbund-logo.svg,fifty1-logo-black.png}` — **new** official logos.
- `public/favicon.svg` — Verbund-styled mark.
- `index.html` — `<title>` → "AI @ Verbund 2026".
- Tests: `App.test.tsx`, `AlterEgoPage.test.tsx` — h1 assertion updated to new title.

**Backend**
- `src/main/resources/branding/poster-frame.png` — regenerated Verbund frame.
- `src/main/resources/branding/generate_poster_frame.py` — **new** generator.
- `src/main/resources/branding/README.md` — updated chrome description + regeneration runbook.
- `src/main/resources/application.yml` — email `from`/`subject` → AI @ Verbund 2026.
- `src/main/java/.../infrastructure/email/AlterEgoEmailBodyBuilder.java` — body copy → AI @ Verbund 2026.
- Tests: `AlterEgoEmailBodyBuilderTest`, `AlterEgoEmailServiceTest` — pinned strings updated in lockstep.
- `branding-src/poster-frame.squer-backup.png` — **new** (out-of-JAR backup of prior asset).

## Out of scope / preserved

- No change to categories, reducers, selectors, generation pipeline, or the public HTTP contract.
- No change to provider wiring — local Gemini runs still require the `gemini` Spring profile (see `backend/run-gemini.sh`); unrelated to this rebrand.
- SQUER "chrome" comments in overlay tests refer to the top-left wordmark **coordinate** (structural), not brand text — left as-is; the Verbund wordmark satisfies the same opaque-non-black pixel assertion.

# Post-Execution Log: Verbund rebrand — "AI @ Verbund 2026"

**Branch**: `029-verbund-rebrand` | **Executed**: 2026-09-06 | **Status**: ✅ Complete & verified

This is an **adapted post-execution log** — the rebrand was implemented directly (not via the full speckit specify→plan→tasks→implement loop), so this file records the as-built work, decisions, and verification in place of a generated `tasks.md`. Companion docs: [spec.md](./spec.md) (what/why), [plan.md](./plan.md) (how).

## What was done

A brand-only re-skin from SQUER / Code-Crafts → Verbund AG / fifty1 for the event **"AI @ Verbund 2026"**. Full light-corporate theme flip, official logos in the web UI and the generated poster frame, event-name copy everywhere, and Verbund-branded email defaults. Categories, mechanics, HTTP contract, and no-persistence posture unchanged.

## Execution phases

### Phase 1 — Frontend design tokens ✅
- Rewrote `frontend/src/styles/tokens.css` to the Verbund palette (VERBUND-blau `#00468E` on off-white `#F4F7FB`; accents energy-green `#5AAA46`, hydro-cyan `#0090C7`, solar-amber `#E2A12B`; navy ink text). Preserved every variable **name** → zero component churn.
- Recolored `index.css` atmosphere (hydro/energy aurora, navy vignette, `multiply` grain) and swept all hard-coded neon literals (`0,191,255` → `0,144,199`; `232,66,122` → `90,170,70`; `168,85,247` → `0,70,142`) plus stale hex fallbacks (`#a855f7`→`#00468e`, `#f87171`→`#d4573b`) to Verbund values.

### Phase 2 — Frontend logos + copy ✅
- Downloaded official logos → `frontend/src/assets/brand/verbund-logo.svg` (brand colour confirmed `#00468E`) + `fifty1-logo-black.png`.
- New `BrandHeader.tsx` (VERBUND left, "Powered by fifty1" right); footer credit in `App.tsx`; title → "AI @ Verbund 2026" + energy-transformation subtitle.
- `index.html` `<title>` → "AI @ Verbund 2026"; new Verbund-styled `favicon.svg`.

### Phase 3 — Backend poster frame ✅
- Committed generator `backend/src/main/resources/branding/generate_poster_frame.py` (Pillow + cairosvg) rebuilds `poster-frame.png` in the Verbund style, rendering the real VERBUND + fifty1 logos, re-punching the transparent cutout at the **identical geometry**.
- Old asset backed up out-of-JAR → `backend/branding-src/poster-frame.squer-backup.png`. Updated `branding/README.md`.

### Phase 4 — Backend email + config ✅
- `application.yml`: `from` → `alter-ego@ai-verbund-2026.local`, `subject` → "Your AI Generated Alter Ego - AI @ Verbund 2026".
- `AlterEgoEmailBodyBuilder.java`: body → "Thank you, for being a part of AI @ Verbund 2026!".
- Updated pinned strings in `AlterEgoEmailBodyBuilderTest` + `AlterEgoEmailServiceTest` in lockstep.

### Phase 5 — Verification ✅ (see below)

## Verification record

| Check | Result |
|-------|--------|
| Frontend `npm run lint` | ✅ eslint clean (one pre-existing `EmailInput.tsx` prettier warning, not touched by this change) |
| Frontend `npm run build` (tsc + vite) | ✅ builds; logos fingerprinted + bundled |
| Frontend `npm test -- --run` | ✅ **602 passed / 602** (40 files) — 2 title-assertion tests updated |
| Backend `./gradlew test` (unit/service/contract/integration/arch + JaCoCo) | ✅ **BUILD SUCCESSFUL** in ~3m; all tiers green, coverage held |
| Poster frame loader (startup log) | ✅ `event=frame.asset.loaded ... innerX=88 innerY=94 innerWidthPx=586 innerHeightPx=791 bottomRegionY=954 bottomRegionHeightPx=129` — cutout geometry identical to prior asset |
| Frame chrome pixel (68,38) | ✅ opaque `(0,70,142,255)` VERBUND-blau (satisfies overlay ITs' opaque-non-black assertion) |
| Live UI (Playwright screenshot, :5173) | ✅ Verbund header logos, "AI @ Verbund 2026" gradient title, light theme, energy-green selection accent, footer credit — no SQUER/Code-Crafts marks |
| Composite poster sample | ✅ portrait in cutout + name/role/quote in navy band, clear of the fifty1 footer |
| Grep for `SQUER` / `Code[/-]?Crafts` in shipped frontend/backend | ✅ none (only in-code doc comments describing the replacement) |

## Notes & follow-ups

- **Not a bug found during verification**: an initial UI generate returned `outcome=fallback provider=stub reason=not_configured`. Root cause was environment, **not** the rebrand — this app selects the image provider by **Spring profile**, and the backend was on `default`. Resolved by launching with `SPRING_PROFILES_ACTIVE=gemini` (helper `backend/run-gemini.sh` already sources `.env` + sets the profile). The Verbund frame + fonts loaded correctly throughout.
- **Favicon** is an authored Verbund-styled mark, not an official Verbund asset — swap for an official one if provided (no code change).
- **Overlay-test comments** still say "SQUER-mark coord" — they refer to the top-left wordmark **coordinate** (structural pixel probe), not brand text; the Verbund wordmark occupies the same spot. Left intentionally.
- **`frontend/package-lock.json`** shows modified — pre-existing before this branch (present in the initial working tree), unrelated to the rebrand.

## Regeneration runbook (poster frame)

```sh
python3 -m venv /tmp/pil-venv && /tmp/pil-venv/bin/pip install pillow cairosvg
/tmp/pil-venv/bin/python backend/src/main/resources/branding/generate_poster_frame.py
```
Edit palette/layout constants at the top of the script. Hard constraints (else backend tests fail): canvas 768×1152; transparent cutout at x=88 y=94 w=586 h=791; pixel (68,38) opaque & non-black; fifty1 footer below the text-safe band (y>1083). Full detail in `backend/src/main/resources/branding/README.md`.

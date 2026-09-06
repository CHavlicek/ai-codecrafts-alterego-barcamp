# Phase 1 — Quickstart: Poster Text Overlay (017)

## Goal

Verify locally that a generated poster carries the user's first name,
the hero title (`heroTitleLine1`), and the tagline rendered into the
dark long-bottom region of the frame, that the on-screen "Your Alter
Ego" tab no longer shows the duplicate HTML hero name + tagline (and
no longer shimmers), and that the printed sheet matches the on-screen
image.

## Prerequisites

- Java 21 (Temurin or Zulu) on `PATH`.
- Node 20+ + npm 10+ on `PATH`.
- A real `GOOGLE_GEMINI_API_KEY` or `FAL_KEY` in your shell **only if**
  you want to test the real-provider path. Without one, the backend
  routes through the stub fallback path — which **also** picks up the
  text overlay (FR-1709), so the feature is fully exercisable without
  an API key.

## Run the stack

```bash
# terminal 1 — backend
cd backend
./gradlew bootRun

# terminal 2 — frontend
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>. Capture / upload a photo, fill out
Setup with first name "Ada" + any selections, click **Generate**.

## What to check

### Image (the new behaviour)

In the resulting "Your Alter Ego" tab, the poster image's bottom dark
region shows three lines, top-to-bottom:

1. **Ada** — large, light, dominant.
2. The hero title (a `heroTitleLine1` such as "Captain Spectre") —
   medium.
3. The tagline ("Ships chaos as a service") — smaller, still readable.

All three lines have a dark halo around their glyphs so they remain
legible where the frame's decorative dot/circuit patterns sit
underneath.

### On-screen layout (the cleanup)

- The previously-shimmering large heading **above the poster** is
  gone (no looping gradient drift).
- The italic muted line (`heroTitleLine2`) **remains** below the
  poster as a small heading.
- The uppercase mono "tagline" line **above** the superpowers list
  is gone (it's on the image now).
- The superpowers list and the quote remain unchanged.

### Long-input behaviour

Open the browser dev tools and override `Selections.firstName` to a
~30-character string, click Generate again. The first name renders
on a **single line**, smaller — no wrapping, no clipping, never
overflowing the frame edge.

### Print

Click **Print poster**. In the print preview, the poster image
carries the same three lines (because the text is part of the image
now); the printed sheet is identical to what you see on screen
inside the poster image.

### Accessibility

Enable VoiceOver / NVDA and tab onto the poster image. The screen
reader announces the alt text in the form
"Alter ego poster for Ada: Captain Spectre. Ships chaos as a service."
— the user's first name, the hero title, and the tagline are all
announced even though they live in pixels.

### Fallback path (no API key)

Stop the backend, unset `GOOGLE_GEMINI_API_KEY` and `FAL_KEY`,
restart, and Generate again. The fallback poster (the dark-violet
geometric one) **also** shows the three text lines in its bottom
region — FR-1709's "every poster the user can see" requirement.

## Run the tests

```bash
# Backend
cd backend
./gradlew test
# focused — just the new bits:
./gradlew test --tests 'com.aiavatar.alterego.service.text.*'
./gradlew test --tests 'com.aiavatar.alterego.integration.GenerateAlterEgoTextOverlayIT'

# Frontend
cd frontend
npm run test
# focused — just the changed components:
npm run test -- PosterView PrintArtefact AlterEgoPanel AlterEgoPage
```

Coverage gate ≥ 90% for `service/text/*` is enforced by the existing
JaCoCo configuration.

## Swap the bundled font

The font asset lives at
`backend/src/main/resources/branding/fonts/<file>.ttf`. To swap it:

1. Drop the new TTF into the same directory.
2. Update `PosterTextFontLoader.DEFAULT_RESOURCE` if the filename
   changes.
3. Re-run `./gradlew test` — `PosterTextFontLoaderTest` and
   `PosterTextOverlayServiceTest` cover the load path; the integration
   test's pixel-count assertion has enough margin to tolerate
   font-metric drift between sans-serif faces.

## Swap the frame asset

If the bundled `branding/poster-frame.png` is later replaced with a
different design:

1. Ensure the new asset still has a transparent inner cutout
   (alpha=0 region) marking where the character image goes.
2. The bottom-region safe area is **derived** by
   `PosterFrameAssetLoader` from the cutout's bounding box — no
   constants change in `PosterTextOverlayService` or the fitter.
3. If the new asset has no usable bottom region (very short canvas
   below the cutout), the loader logs
   `event=frame.asset.bottom_region_too_small` and downgrades to
   `PosterFrameAsset.missing()` so both frame and text overlays
   short-circuit gracefully — the user still gets the un-framed
   character image.

## Common gotchas

- **Alpine fontconfig**: if you switch the backend Docker base image
  away from `eclipse-temurin:21-jre-alpine` to a Debian-based image,
  the bundled font loader still works (it doesn't depend on
  `fontconfig`) but the JDK logical-font fallback path will start
  resolving to system fonts — which produces different pixel-level
  output. The integration test's threshold has margin for this.
- **Headless AWT**: the backend already runs with
  `-Djava.awt.headless=true` (Spring Boot's default). No display
  required at any point in this feature.
- **No extra port / process**: the feature stays inside the existing
  HTTP request lifecycle — no new daemon, no new endpoint, no new
  port.

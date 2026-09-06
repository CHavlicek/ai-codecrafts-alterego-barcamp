# Quickstart — Camera-Only Photo Intake

**Feature**: 004-camera-only-capture
**Audience**: developers validating the feature locally; operators running the POC demo; QA running the Playwright matrix.

## TL;DR

This feature is **frontend-only**. No backend config changes, no new environment variables, no new dependencies. Run the existing dev loop; the difference is that the Setup tab's photo column now takes a selfie via the circle instead of via two pill buttons.

## Prerequisites

- Node.js version pinned by `frontend/package.json` (20 LTS or newer).
- A working webcam on the host machine (built-in laptop webcam is fine). If you don't have one, you're exercising the FR-312 degraded path — that's also a valid demo.
- For demo purposes only: a backend running per 003's quickstart (Gemini key optional — the fallback path is fine). Without the backend, the capture UI still works up to the Generate press.

## Running locally

```bash
cd frontend
npm install                     # only if not already installed
npm run dev                     # http://localhost:5173
```

In a second shell:

```bash
cd backend
./gradlew bootRun               # http://localhost:8080 — same as 003
```

Open <http://localhost:5173/> in a modern browser (Chrome, Firefox, Safari ≥ 14, Edge). The Setup tab renders with:

- A large circular photo area labelled **"Take photo"** (was "Add photo" in 002).
- **No** "📷 Camera" pill button below it.
- **No** "🗂 Upload photo" pill button below it.

## Happy path — taking a selfie

1. **Tap (or click, or press Enter with the circle focused)**. The browser prompts for camera permission the first time.
2. **Grant permission**. The live camera feed renders *inside* the circle in its raw un-mirrored orientation — what the viewfinder shows is exactly what the saved image and the AI will see (no flip between framing and Keep). The feed stays inside the circle — no modal overlay, no separate panel.
3. **A "Take photo" shutter button appears directly below the circle**, plus a "Cancel" secondary button.
4. **Press the shutter**. The live feed freezes into a still — the same un-mirrored orientation as the viewfinder, byte-for-byte what Gemini will receive. The shutter row is replaced by "Keep photo" (primary) and "Retake" (secondary). The camera light stays on — the stream is still open.
5. **Press "Retake"** as many times as you want — each Retake reuses the open stream (no second permission prompt, no warm-up delay). Useful for framing.
6. **Press "Keep photo"** when happy. The still is committed, the camera stream is released (the camera light turns off), and the circle now shows the committed photo cropped to a round mask — same preview treatment as 001/002/003.
7. **Complete the rest of Setup** (pose, archetype, universe, first name, optional vibe) and press Generate. Everything downstream is identical to 003.

## Testing the degraded path (FR-312)

Three ways to exercise the "camera unavailable" state:

- **Deny permission**: click "Block" when the browser prompts, or flip the site-permissions toggle to "blocked" in browser settings. Next activation of the circle should surface the polite message in the photo column. After granting permission again (without a page reload), activating the circle again must succeed (FR-314).
- **No camera hardware**: on macOS, use *System Settings → Privacy & Security → Camera* to disable the browser's camera access. On a device with no physical webcam, this is the default state.
- **Unsupported browser**: there is no realistic modern-browser lacking `getUserMedia` in the demo matrix, so to exercise `capability_missing` use Chrome devtools → Sources → *Overrides → MediaDevices* experimentally, or stub `navigator.mediaDevices` via a small devtools snippet:

```js
Object.defineProperty(navigator, 'mediaDevices', { value: undefined, configurable: true })
```

Run that in the console *before* activating the circle, then activate. The polite "browser doesn't support in-app camera capture" message should render and Generate should stay disabled.

## Test commands

From `frontend/`:

```bash
npm test                        # Vitest unit + component tests
npm run test:coverage           # same + coverage report (≥ 90% gate)
npm run test:e2e                # Playwright — includes camera-capture.spec.ts (Chromium with fake-media-stream flags)
npm run test:a11y               # axe + keyboard walkthrough (includes the new three-states scan)
```

The Playwright config already passes `--use-fake-ui-for-media-stream` and `--use-fake-device-for-media-stream` to Chromium for `camera-capture.spec.ts`. No additional setup is needed in CI.

Backend tests (from `backend/`) remain unchanged for this feature:

```bash
./gradlew test                  # no 004-related test additions
```

## Troubleshooting

- **"Camera access is needed …" message appears immediately**: either the browser has a cached permission-denied decision, or you're on a browser without `getUserMedia`. Check the site-permissions panel (padlock icon → site settings).
- **Camera light stays on after pressing Keep**: this is a bug — `useCameraStream` should have called `track.stop()` on every track. File against this feature; the hook's cleanup path is the enforcement point (see `useCameraStream.ts`).
- **Viewfinder is mirrored but saved image is not (or vice versa)**: FR-302a (revised 2026-04-23) requires a single un-mirrored orientation across the viewfinder, the still-preview, and the committed bytes. If any of those three differ, check `frontend/src/index.css` (`.photo-intake__viewfinder` must not have `transform: scaleX(-1)`) and `squareCrop.ts` (the `mirror` option is always passed as `false` by `useCameraStream.captureStill`).
- **Captured photo is rectangular, not a square**: `squareCrop` is supposed to centre-crop to 1:1. Check the `side = Math.min(w, h)` line and the `drawImage` destination is `0, 0, targetPx, targetPx`.
- **Retake triggers a second browser permission prompt**: the stream is being released and re-acquired across Retake. That's a bug — Retake must reuse the open stream (R2 / FR-301b).

## Demo cheat sheet

For the POC booth demo:

1. Emphasise **one-step capture**: tap → camera → shutter → Keep. Previous UI was tap → decide between Camera/Upload → click Camera → system camera app.
2. Show **Retake reuse**: "notice I can retake without re-granting permission" — the camera light stays on, user sees the live feed again instantly.
3. Show **clean teardown**: after Keep, the macOS/Windows camera indicator disappears immediately. Privacy-friendly.
4. If a demo device happens to deny permission: *that's a feature, not a bug* — walk through the FR-312 degraded path and point out Generate stays correctly disabled.

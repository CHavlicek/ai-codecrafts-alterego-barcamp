# Implementation Plan: Camera-Only Photo Intake — Tap the Circle, Take a Selfie

**Branch**: `004-camera-only-capture` | **Date**: 2026-04-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/004-camera-only-capture/spec.md`

## Summary

This is a **frontend-only** feature. `PhotoIntake.tsx` — today a circle-with-two-pill-buttons ("📷 Camera" + "🗂 Upload photo") that routes through a pair of hidden `<input type="file">` elements — is rewritten so the circle itself becomes the single interactive control. Activation calls `navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" } })`; the returned `MediaStream` renders **inline inside the circle** as a `<video>` element clipped to a round mask, mirrored via `transform: scaleX(-1)` so framing feels natural (FR-302a). A shutter control appears directly below the circle — in the column space the two removed pill buttons used to occupy — and on press captures the current frame to an off-screen canvas *un-mirrored* and *centre-cropped to a square* at roughly 1024×1024 (FR-303a), producing a Blob that is shown as a still-preview inside the circle. Two inline controls (**Keep photo**, **Retake**) replace the shutter: Keep commits the Blob (feeding it through the existing `downscalePhoto()` → `onPhotoSelected()` pipeline untouched) and releases the MediaStream; Retake discards the still and returns to the live feed *reusing the same open stream* (FR-301b). Cancel (Escape or an inline button) is available throughout and stops the stream. When `getUserMedia` is unavailable, denied, or returns no usable stream (FR-312's four triggers), a single polite accessible message renders inside the photo column; Generate remains disabled until a photo exists. No backend work, no new npm dependencies, no OpenAPI change — the feature swaps one client-side capture surface for another and everything downstream (downscale, `GenerateRequest`, Gemini call, poster rendering) is untouched.

## Technical Context

**Language/Version**: TypeScript 5.7 (strict) on the frontend. No backend changes — Java 21 / Spring Boot 3.5 unchanged.
**Primary Dependencies**:
  - Frontend (existing, consumed more heavily): React 19, TanStack Query v5, Vitest + React Testing Library, Playwright, `@axe-core/playwright`. The new capture surface is built on **browser-native Web APIs** — `navigator.mediaDevices.getUserMedia`, `HTMLVideoElement`, `HTMLCanvasElement.getContext('2d')`, `canvas.toBlob()`, `URL.createObjectURL`. No new runtime npm dependency is introduced; no polyfill / shim is added (per Q2 clarification — "no fallback pathway").
  - Backend: untouched. No new dependencies, no configuration change, no profile change.
**Storage**: N/A. No-persistence posture extends unchanged — captured photo bytes live only in-memory (Blob + object URL) for the session, exactly where uploaded photo bytes used to live. FR-315 restates 001 FR-016 verbatim.
**Testing**: Vitest + React Testing Library for the rewritten `PhotoIntake` (unit + component tests; `getUserMedia` mocked via `vi.spyOn(navigator.mediaDevices, 'getUserMedia')`); new pure-function unit tests for the `squareCrop` helper and the `cameraCapability` detector; Playwright end-to-end test covering the happy-path capture flow using Chromium's `--use-fake-ui-for-media-stream` + `--use-fake-device-for-media-stream` flags (fake webcam produces a predictable green-bar test pattern that we can assert on). `@axe-core/playwright` accessibility scan across the three photo states (empty, live-viewfinder / still-preview, unavailable) per SC-305. TDD per Principle III. Unit line coverage gate ≥ 90% remains.
**Target Platform**: Modern evergreen browsers on desktop (Chromium ≥ recent, Firefox ≥ recent, Safari ≥ 14) and mobile (iOS Safari ≥ 14.3 for `getUserMedia` support, Android Chrome). Older mobile Safari and restricted webviews fall through to the FR-312 degraded path (the clarified Q2 single-surface posture).
**Project Type**: Web application — frontend-only change within `frontend/`. Backend (`backend/`) and Docker Compose (`docker-compose.yml`) untouched.
**Performance Goals**:
  - Circle-activation → first live-preview frame visible **< 2 seconds** on a typical laptop webcam (soft target; camera warm-up is device-bound). This is within SC-303's 2-second budget for the *error* path, so the happy path has to be at least as fast.
  - Shutter-press → still-preview visible **< 200 ms** (canvas frame-grab is single-frame and synchronous).
  - Retake → live-preview restored **< 100 ms** (the stream is reused — no second `getUserMedia` handshake, so the only cost is a re-attach of the `<video>` element and a repaint).
**Constraints**:
  - WCAG 2.1 AA. The rewritten circle MUST be a real `<button>` (or a `<div role="button">` with `onKeyDown` handling Enter and Space) with an accessible name, visible focus ring, and announced state changes via the existing `useLiveAnnouncer` hook. Keep / Retake / shutter / cancel are real buttons. FR-309–FR-311.
  - No-persistence (FR-315). The MediaStream MUST be stopped (each track's `stop()` called) and the object URL for the still Blob MUST be revoked (`URL.revokeObjectURL`) on Keep, Cancel, component unmount, tab switch away from Setup, and page unload — whichever comes first. Browser's device indicator (green dot / camera light) MUST NOT stay on after any exit path.
  - Zero deprecated dependencies (Principle VI). No new dependencies are introduced by this feature — the capability check passes trivially.
  - The feature MUST NOT regress 001/002/003 behaviour (FR-317). Specifically the `GenerateRequest` wire shape, the downscale pipeline, the Generate-tab auto-switch, and the Gemini fallback notice remain exactly as 003 left them.
**Scale/Scope**:
  - Frontend: 1 component rewrite (`PhotoIntake.tsx`), 1 new lib pair (`cameraCapture.ts` + `squareCrop.ts`), 1 updated lib (CSS-only adjustments for the viewfinder — the circle grows an overlay layer for the `<video>`), 1 test rewrite (`PhotoIntake.test.tsx`), 2 new unit-test files, 1 new Playwright spec (`camera-capture.spec.ts`). ~8–10 TS files touched or added.
  - Backend: **0 files touched.**
  - Contracts: **none.** No OpenAPI version bump.

## Constitution Check

Evaluating each active principle of Constitution v1.0.2.

| Principle | Gate status | Evidence |
|---|---|---|
| **I. Modern & Secure Technology Stack (NON-NEGOTIABLE)** | ✅ PASS | No new npm dependencies; the capture surface is built entirely on browser-native Web APIs (MediaDevices, HTMLVideoElement, HTMLCanvasElement). React 19 / TS strict / Vite 8 unchanged. No prohibited package added. |
| **III. Test-First Development (NON-NEGOTIABLE)** | ✅ PASS (planned) | Tasks phase orders tests first: `PhotoIntake.test.tsx` (rewritten — live-preview state, still-preview state, keep/retake transitions, cancel paths, unavailable-camera paths; `getUserMedia` mocked); `cameraCapture.test.ts` (new — start/stop, track release, capability detection returning each of the four FR-312 outcomes); `squareCrop.test.ts` (new — pure function: asserts 1024×1024 centre-crop, asserts un-mirror matrix, asserts handling of smaller native resolutions); `AlterEgoPage.test.tsx` (regression — confirms the broader page behaviour is unchanged); `camera-capture.spec.ts` (new Playwright — fake-media-stream end-to-end; also exercises the Retake reuse-stream path and the unavailable path); `axe-scan.spec.ts` (extended — asserts zero serious/critical violations across empty, live, still, unavailable states per SC-305). Coverage gate ≥ 90% per module is enforced by the existing Vitest `--coverage` config. |
| **IV. Resilient HTTP Communication** | ✅ N/A | This feature adds no HTTP call. It changes only the client-side photo acquisition surface; the subsequent `POST /api/alter-egos` call is reached through the existing resilient client unchanged. |
| **V. Feature Branch Workflow** | ✅ PASS | Branch `004-camera-only-capture` cut from `main` via `create-new-feature.sh`. Merge requires PR review per the constitution. |
| **VI. Zero Deprecated Dependencies** | ✅ PASS | No new runtime or test dependencies introduced. The existing audit (`npm audit` at PR time) continues to gate merges. |

**Gate**: PASS — no violations. The `## Complexity Tracking` section remains intentionally empty.

### Re-evaluation after Phase 1 (Design & Contracts)

After drafting `research.md`, `data-model.md`, and `quickstart.md`, all principles still pass:

- Phase 0 research resolved every Technical Context unknown without requiring a new dependency (the two candidate libraries evaluated — `react-webcam` and `pica` — were rejected because native APIs suffice for the capture surface and canvas crop).
- No backend touchpoints emerged during design — the feature is cleanly frontend-scoped.
- TDD ordering is preserved; test files will be created before their production counterparts in the `/speckit.tasks` phase.
- Wire contract is untouched, so no versioning or migration question arises.

**Post-design gate**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/004-camera-only-capture/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output — MediaDevices API, stream lifecycle, square-crop + mirror transform, capability detection, accessibility notes, testing strategy
├── data-model.md        # Phase 1 output — Photo entity (unchanged), CameraAvailabilityState (new transient), capture-session state machine
├── quickstart.md        # Phase 1 output — running the feature locally (dev server + webcam; devtools MediaDevices override for faulting; Playwright fake-media flags)
├── contracts/           # (empty — no contracts change)
├── checklists/
│   └── requirements.md  # /speckit.specify output (all items ticked; 5 clarifications resolved)
├── spec.md              # Feature spec (frozen after the 2026-04-23 clarify session)
└── tasks.md             # NOT created by /speckit.plan — /speckit.tasks output
```

### Source Code (repository root)

```text
backend/
└── (untouched — 0 files modified)

frontend/
├── src/features/alterego/
│   ├── components/
│   │   ├── PhotoIntake.tsx                          # (rewrite) circle-as-button; live <video> viewfinder inside the circle; shutter + still-preview + Keep/Retake/Cancel controls; unavailable-camera message. Consumes the new hooks below.
│   │   └── PhotoIntake.test.tsx                     # (rewrite) unit tests for every state transition (idle → live → still → committed; idle → unavailable; live → cancel; still → retake → live; still → cancel; focus/ARIA assertions)
│   ├── hooks/
│   │   ├── useCameraStream.ts                       # (new) encapsulates getUserMedia start/stop, track release, error classification (returns FR-312 reason codes), stream reuse across Retake
│   │   └── useCameraStream.test.ts                  # (new) mocks navigator.mediaDevices; asserts all four FR-312 triggers map to the right internal state; asserts track.stop() called on every exit path
│   ├── lib/
│   │   ├── downscalePhoto.ts                        # (unchanged) receives the square-cropped JPEG Blob produced below and does its existing job
│   │   ├── squareCrop.ts                            # (new) pure function: given an HTMLVideoElement (or HTMLCanvasElement) + a MirrorMode + a target size, return a Blob containing the centred square un-mirrored JPEG at ~1024×1024 (or native resolution when smaller). No DOM dependency beyond the input element.
│   │   ├── squareCrop.test.ts                       # (new) pure-function tests: 16:9 input → 1:1 centred, mirror flag produces un-mirrored output, smaller-than-target input is not up-scaled, target width matches spec.
│   │   ├── cameraCapability.ts                      # (new) pure capability check: does the current user agent expose `navigator.mediaDevices.getUserMedia`? Returns `{ supported: true }` or `{ supported: false, reason: "unsupported" }` without invoking the permission prompt.
│   │   └── cameraCapability.test.ts                 # (new) asserts each branch by stubbing the navigator surface
│   ├── AlterEgoPage.tsx                             # (unchanged — the integration point is PhotoIntake.tsx)
│   └── state/
│       └── reducer.ts                               # (unchanged — PhotoSelected / PhotoCleared actions still carry Blob + URL, identical shape to 001)
├── src/components/
│   ├── LiveRegion.tsx                               # (unchanged — consumed by useLiveAnnouncer; used by PhotoIntake to announce state changes per FR-311)
│   ├── useLiveAnnouncer.ts                          # (unchanged)
│   └── VisuallyHidden.tsx                           # (unchanged — reused for accessible-only labels on the shutter / cancel icons)
├── src/App.tsx                                      # (unchanged)
├── src/index.css                                    # (edit) new rules for .photo-intake__viewfinder, .photo-intake__shutter, .photo-intake__still, .photo-intake__keep, .photo-intake__retake, .photo-intake__cancel; updates to .photo-intake__actions (now a 3-state container: shutter / keep+retake / hidden). Removes the .photo-intake__pill styles (unused after this feature; dead CSS cleanup in same commit).
└── tests/e2e/
    ├── camera-capture.spec.ts                       # (new) Playwright — uses --use-fake-ui-for-media-stream + --use-fake-device-for-media-stream; asserts happy path (activate → live → shutter → still → Keep → photo committed → Generate becomes enabled), Retake path (stream reuse — second getUserMedia call NOT observed), and mirror/un-mirror (the committed blob's width/height matches the un-mirrored square crop)
    ├── axe-scan.spec.ts                             # (edit) add assertions for each of the three photo states (empty, live-viewfinder visible, camera-unavailable message visible)
    ├── keyboard-walkthrough.spec.ts                 # (edit) add a subtest that reaches the circle via Tab, activates with Enter, tabs to the shutter, fires it, Tabs to Retake/Keep, and completes with Keep; asserts visible focus indicator at each stop
    └── generate-flow.spec.ts                        # (unchanged — continues to pass; the upstream capture surface changed but the Generate flow's observable behaviour didn't)
```

**Structure Decision**: Two-project web app (`backend/` + `frontend/`), unchanged from 001/002/003. All changes land under `frontend/src/features/alterego/` — consistent with the feature's frontend-only scope. The new camera work is split across a pure `lib/` (square crop + capability detection) and a React `hooks/` module (`useCameraStream`) so the production-code surface is narrow: `PhotoIntake.tsx` orchestrates, `useCameraStream` owns the stream lifecycle, `squareCrop.ts` owns the pixel geometry. This layering keeps the stateful DOM-adjacent logic isolated from the stateless pure-function logic and maps 1:1 onto the TDD ordering in `/speckit.tasks`.

## Complexity Tracking

*(Intentionally empty — Constitution Check passes without violations.)*

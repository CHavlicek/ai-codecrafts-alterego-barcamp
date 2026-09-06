# Research — Camera-Only Photo Intake

**Feature**: 004-camera-only-capture
**Date**: 2026-04-23

Every item below resolves a Technical-Context unknown from `plan.md` or a plan-level decision that the spec deliberately deferred ("planning-phase detail"). No `NEEDS CLARIFICATION` remains after this document — the five spec-level clarifications from the 2026-04-23 session already pinned the load-bearing UX decisions; what's left is implementation approach.

---

## R1 — MediaDevices capture: native API vs. a wrapper library

**Decision**: Use the browser-native `navigator.mediaDevices.getUserMedia({ video: { facingMode: "user" }, audio: false })` with no wrapper library. Do not add `react-webcam`, `pica`, `image-capture` polyfill, or `adapter.js`.

**Rationale**:
- `getUserMedia` is supported across the entire POC target matrix (Chromium, Firefox, Safari ≥ 14, iOS Safari ≥ 14.3, Android Chrome). Q2's clarified posture — treat "unsupported" as FR-312's fifth trigger and surface the polite message — removes the need for `adapter.js`.
- `react-webcam` wraps the same native API with a fixed DOM shape and prop contract that would force-fit the `<video>` element into a way that doesn't play cleanly with the "circle-as-viewfinder" design (FR-301: preview must render *inside the circle*, not as a sibling). We'd pay the dependency cost without using its main value-add (layout management).
- `pica` is a high-quality resize library, but the square crop this feature needs (largest centred 1:1 square at ~1024×1024 — FR-303a) is a single `canvas.drawImage(src, sx, sy, sw, sh, 0, 0, dw, dh)` call. `pica`'s Lanczos resampler is overkill for a one-shot crop of a live-webcam frame at essentially the same resolution.
- Principle VI ("zero deprecated dependencies") and Principle I prefer the lean path: no new deps to audit at PR time.

**Alternatives considered**:
- `react-webcam` — rejected for layout-fit reasons above. Ergonomic, but adds a dep for no real gain.
- `pica` — rejected; native `canvas.drawImage` is sufficient for a 1:1 crop of a ~720p–1080p frame. `downscalePhoto.ts` already owns the quality-sensitive resize step downstream.
- `adapter.js` — rejected per Q2 (no polyfill / shim pathway).
- `ImageCapture` API (`track.grabFrame()` / `track.takePhoto()`) — promising but Safari-unsupported and partially broken on Firefox. Falling back to canvas.drawImage on the `<video>` element is the uniform path.

---

## R2 — Stream lifecycle: acquire once, reuse across Retake, release on Keep/Cancel/unmount

**Decision**: Implement stream lifecycle inside a custom hook `useCameraStream`. The hook owns a single `MediaStream` reference acquired on circle activation, reused through any number of shutter → Retake cycles, and stopped (every track `track.stop()`) plus dereferenced on Keep, on Cancel, on component unmount, on `visibilitychange` away, and on page unload. Object URLs created for still-Blobs are revoked (`URL.revokeObjectURL`) on the same exits.

**Rationale**:
- Q3 committed to "Retake reuses the open stream — no second `getUserMedia` handshake". That translates directly to: one stream per capture *session*, not one stream per shutter press.
- The `<video>` element is re-used too — we pause it to freeze the last frame while the still-preview renders, then unpause on Retake. Detaching/reattaching `srcObject` is unnecessary and causes a visible flicker.
- Forgetting to release tracks leaves the device's camera indicator on (the "green dot" users notice). FR-305 calls this out explicitly and the hook's `useEffect` cleanup is the enforcement point.
- `visibilitychange` cleanup matters because 002 FR-106 lets the user tab-switch mid-capture — the spec's Outstanding item "camera-stream lifecycle on cross-tab navigation" is resolved here: leaving Setup while the live preview is open MUST stop the stream. Returning to Setup reverts the circle to its prior state (empty / prior photo). This matches FR-305's "navigating away" language.

**Alternatives considered**:
- Acquire-on-demand per shutter press — rejected, breaks Q3's reuse contract and triggers repeat permission prompts on some Firefox versions.
- Keep the stream open across the entire Setup-tab lifetime — rejected, violates FR-315's minimisation of photo-bytes-in-flight and leaves the camera indicator on longer than necessary.
- Use a singleton module-level stream — rejected, defeats React's strict-mode double-mount semantics and complicates teardown.

---

## R3 — Rendering the live preview inside the circle (clip + cover; no mirror)

**Decision**: Render a `<video autoPlay playsInline muted>` element inside the existing `.photo-intake__circle` container, absolutely positioned to fill it, with `object-fit: cover` to avoid black bars and **no** `transform: scaleX(-1)` anywhere — the viewfinder, the still-preview, and the committed bytes are all un-mirrored (FR-302a, revised 2026-04-23 after user testing rejected the original mirror-the-live-viewfinder choice). The circle's existing `border-radius: 50%` plus `overflow: hidden` already produces the round clip — no `clip-path` or SVG mask is needed.

**Rationale**:
- `playsInline` is required for iOS Safari to render video in-page rather than full-screen.
- `muted` is required for autoplay to work without user gesture prompts on some browsers (we don't need audio anyway — `audio: false` at acquisition time).
- `object-fit: cover` crops the rectangular stream to fill the round container; the user sees the largest square that fits inside the `<video>`'s visible area, which is exactly what the square crop on shutter produces — so what they frame is what they save. (SC-307 regression-proofs this.)
- **No mirroring.** Testing the original "mirror the live viewfinder, save un-mirrored" choice revealed the shutter→still transition reads as a jump cut of the user's face, eroding trust that the preview matches the saved image. A single un-mirrored orientation across viewfinder, still, and commit is the WYSIWYG contract users actually expect.

**Alternatives considered**:
- `clip-path: circle(50%)` — functionally equivalent but less browser-cache-friendly than `border-radius` and breaks the visible focus ring because focus outlines sit outside the clip. `border-radius: 50%` + `outline` on focus keeps the ring intact.
- SVG `<foreignObject>` + `<video>` — overengineered; no accessibility or layout gain.
- Mirror the live viewfinder, un-mirror the still + commit (iOS Camera convention) — tried and rejected; see FR-302a revision above.
- Mirror everything (live, still, commit) — rejected; saved image reads backwards (text on clothing), which the downstream AI pipeline and any human reviewer notice immediately.

---

## R4 — Square crop geometry and target size

**Decision**: Implement `squareCrop(source, options) → Promise<Blob>` as a pure module that takes either an `HTMLVideoElement` (happy path) or an `HTMLCanvasElement` (test path) plus `{ mirror: boolean, targetSize: number }` and returns a JPEG Blob. Algorithm:

```ts
const w = source.videoWidth ?? source.width
const h = source.videoHeight ?? source.height
const side = Math.min(w, h)                         // largest square that fits
const sx = Math.floor((w - side) / 2)               // centre-X offset
const sy = Math.floor((h - side) / 2)               // centre-Y offset
const targetPx = Math.min(side, options.targetSize) // never up-scale; down-scale to target
const canvas = new OffscreenCanvas(targetPx, targetPx) // or document.createElement('canvas')
const ctx = canvas.getContext('2d')!
ctx.drawImage(source, sx, sy, side, side, 0, 0, targetPx, targetPx)
return canvas.convertToBlob({ type: 'image/jpeg', quality: 0.92 })
```

`options.mirror: false` throughout the commit path — mirroring is a CSS concern only (R3). The `mirror` option exists so future callers (e.g. a "mirror the saved photo too" toggle) could flip without rewriting, but in this feature it's always passed as `false`.

**Target size**: `targetSize = 1024` by default. Matches 003's Assumptions §4 ("around 1024×1024 for reference-image inputs") and keeps the outbound payload well under Gemini's per-request limit even before the backend `PhotoReducer` runs (003 FR-206). Matches FR-303a.

**Rationale**:
- Centre crop matches what the user saw inside the round viewfinder (R3 — `object-fit: cover` shows the same centred square). WYSIWYG is preserved from framing to Gemini.
- Never up-scaling (via `Math.min(side, targetSize)`) respects 003 FR-207 ("preserve enough visual fidelity for Gemini … If the photo is already within the threshold, it MUST be sent as-is — the system MUST NOT re-encode unnecessarily"). A 480×480 native-resolution crop from an older webcam stays 480×480.
- `OffscreenCanvas` with `convertToBlob` is faster and doesn't pollute the DOM; the code uses a feature-detected fallback to `document.createElement('canvas')` + `canvas.toBlob(…)` for older browsers where `OffscreenCanvas.convertToBlob` isn't available. Both paths are unit-tested.
- JPEG at quality 0.92 balances fidelity against wire size. The subsequent `downscalePhoto()` (001 FR-001) further caps to 5 MB post-downscale; under normal conditions the square crop is well under that.

**Alternatives considered**:
- `targetSize = 512` — rejected, noticeably reduces face anchoring fidelity for Gemini.
- `targetSize = 2048` — rejected, payload grows to several MB before downscale and provides no fidelity benefit at the output resolution Gemini generates.
- PNG output — rejected, ~3× larger wire payload for no visible gain on a photographic face reference.

---

## R5 — Capability detection and the four FR-312 triggers

**Decision**: Capability detection happens in two layers:

1. **Static capability** (`cameraCapability.ts`): a synchronous pure function that returns `{ supported: true }` iff `typeof navigator !== "undefined" && navigator.mediaDevices?.getUserMedia` is a function. Does not invoke the permission prompt. Called at `PhotoIntake` render time to decide whether the circle initially shows the ready-to-tap empty state or the FR-312 unavailable state.

2. **Runtime classification** (`useCameraStream.start()`): invokes `getUserMedia` and, if it throws, maps the `DOMException.name` to a `CameraUnavailableReason`:

   | DOMException.name | CameraUnavailableReason | Surface copy key |
   |---|---|---|
   | `NotAllowedError`, `SecurityError` | `permission_denied` | "camera permission denied" |
   | `NotFoundError`, `OverconstrainedError` | `no_camera_hardware` | "no camera detected" |
   | `NotReadableError`, `AbortError`, `TypeError` | `stream_unavailable` | "camera is busy or unreachable" |
   | (static-capability returned false) | `capability_missing` | "browser does not support camera" |

   The surface copy MUST be generic per FR-313 — no raw `DOMException.name` values reach the UI. The distinction above is for the *internal* state machine (so logs and analytics can tell which happened) and for test assertions (SC-303's fault-injection matrix requires distinguishable outcomes). In the rendered UI, the four reasons collapse to one polite message, parameterised only by the suggested user action ("please grant camera access", "please connect a camera", "please try again", "please use a supported browser").

**Rationale**:
- Two-layer detection matches FR-312's four triggers (permission-denied, no-hardware, capability-missing, stream-unavailable) while keeping the UI copy single-variant (FR-313).
- Using `DOMException.name` is the standard MDN-documented approach and is stable across Chromium / Firefox / Safari. The `TypeError` row catches Safari-specific quirks where constraints are malformed.
- Keeping the distinction in internal state (not in user-facing copy) means SC-303's "four triggers → four internal outcomes" test matrix is meaningful without requiring four user-visible messages (which FR-313 forbids).

**Alternatives considered**:
- Probe `navigator.permissions.query({ name: "camera" })` before calling `getUserMedia` — rejected, Permissions API lookup for `camera` is still flagged on Firefox and the `getUserMedia` catch is sufficient.
- Expose `reason` to the frontend error copy — rejected, contradicts FR-313's generic-copy requirement.

---

## R6 — Accessibility: focus management across state transitions

**Decision**: Focus transitions follow a predictable pattern tied to state:

| State | Primary focus target on entry |
|---|---|
| Idle (empty circle) | Circle (button) — first focusable control in the photo column |
| Live preview | Shutter button |
| Still preview | Keep button |
| Unavailable | The unavailable-message's announcement is polite (`role="alert"`), but focus stays on the circle so the user can try again |
| Committed (photo in circle) | Stays on whatever control the user last operated (either Keep, if they just confirmed, or the Retake/clear button once they move there) |

Transitions from state to state MUST NOT steal focus away from the current input group unpredictably. Specifically: pressing the shutter advances focus to Keep; pressing Retake moves focus back to the shutter (once the live view is restored); pressing Cancel or Keep-photo releases focus to the circle (or the next focusable control in the Setup tab, per browser default).

State changes are announced via the existing `useLiveAnnouncer` hook (reused from 001). The live region messages are short and action-oriented: "Camera ready", "Photo captured. Press Keep or Retake", "Photo saved", "Camera unavailable".

**Rationale**:
- WCAG 2.1 AA 2.4.3 (focus order): keeps Tab order synchronised with visual order — circle → shutter (or Keep/Retake) → Retake/clear → rest of Setup.
- WCAG 4.1.3 (status messages) and 001 FR-022: live-region announcements replace the absence of a visible "camera is loading" spinner (a common source of silent-failure confusion).
- FR-309, FR-311, FR-301b ("order MUST be consistent between visual and keyboard traversal") all map to this pattern.

**Alternatives considered**:
- Auto-focus the shutter only after live preview is definitively streaming (`loadedmetadata`) — rejected; users reported (in 003's a11y audit) that waiting 1–2s for focus feels like the page is frozen. We move focus immediately on successful `getUserMedia`, and the live region announces "Camera ready" on `playing`.
- Focus trap within the capture sub-region — rejected, overkill for an inline control (no modal to trap into; the rest of Setup is still reachable).

---

## R7 — Testing strategy: mocks for Vitest, fake streams for Playwright

**Decision**: Three layers.

1. **Unit (Vitest + RTL)**: mock `navigator.mediaDevices.getUserMedia` with `vi.spyOn(navigator.mediaDevices, 'getUserMedia').mockResolvedValue(fakeStream)` where `fakeStream` is a hand-rolled `MediaStream`-shaped object with a stub `getVideoTracks()` returning a minimal track with a spiable `stop()`. Assert `track.stop()` is called on every exit path. For error paths, `.mockRejectedValue(new DOMException('...', 'NotAllowedError'))` etc. for each of the four mappings in R5.

2. **Pure function (Vitest)**: `squareCrop.ts` and `cameraCapability.ts` are pure, with no DOM coupling beyond input elements. `squareCrop` tests use `document.createElement('canvas')` pre-filled with a known pattern (red pixel at `(w/2, h/2)`, blue border) to assert the centre-crop arithmetic. Unit tests use Vitest's `jsdom` environment — no browser needed.

3. **End-to-end (Playwright)**: `camera-capture.spec.ts` launches Chromium with `--use-fake-ui-for-media-stream --use-fake-device-for-media-stream`. Chromium's fake device generates a known green/red test pattern; the spec asserts the captured Blob's inferred width/height match the expected 1024×1024 and that a sentinel pixel falls within expected colour ranges (noise-tolerant). Retake reuse is verified by spying on `getUserMedia` via a Playwright-injected `window.__getUserMediaCallCount` wrapper — only ONE call expected across the full "activate → shutter → Retake → shutter → Keep" flow. The unavailable path is exercised by launching without the fake-device flag and asserting the polite message renders and Generate remains disabled.

**Rationale**:
- The existing project already uses `jsdom`, Vitest, and Playwright — no new runner, no new config file.
- `--use-fake-device-for-media-stream` is the canonical Chromium flag for deterministic media tests and matches Playwright's official camera-testing recipe. Firefox's equivalent (`media.navigator.streams.fake = true`) is documented but less reliable; Chromium alone is acceptable for CI (the rest of the test matrix runs headless-Chromium).
- Keeping `squareCrop` pure makes its unit tests fast and deterministic; this is where most of the geometry risk lives and exhaustive unit coverage is cheap.

**Alternatives considered**:
- Real webcam CI — rejected; CI runners don't have webcams.
- E2E only, no unit tests — rejected, violates Principle III's ≥ 90% unit coverage gate and loses the fast feedback loop on geometry bugs.
- `msw` to mock `getUserMedia` — rejected, `msw` is HTTP-only; the DOM APIs need vitest `vi.spyOn`.

---

## R8 — Circle interactive semantics: `<button>` vs. `<div role="button">`

**Decision**: Use a real `<button type="button">` element styled to look like the existing `.photo-intake__circle`. Move the visual circle styling from the current `<div>` onto the `<button>`. Retain the nested `<img>` (for the committed photo preview) and `<video>` (for the live preview) as non-interactive children. Apply `aria-label="Take a photo of yourself"` when empty, `aria-label="Retake your photo"` when filled (because activating a filled circle re-opens the camera). Apply `aria-pressed` only if the button has a persistent toggled state — it doesn't here (each activation opens a new capture session), so skip `aria-pressed`.

**Rationale**:
- A real `<button>` gets native Enter / Space activation, native focus ring, native `disabled` semantics (used when capture is in-flight), and the right implicit `role` for screen readers. Avoids the "click handler on a div" accessibility anti-pattern and satisfies FR-309.
- The `<button>` can legitimately contain `<img>` / `<video>` / `<span>` — no HTML-parser issues. Interactive children inside an interactive button would be a problem; we don't have any.
- Switching the `aria-label` on empty vs. filled states keeps screen-reader output grounded in the action the user will actually perform (take vs. retake).

**Alternatives considered**:
- `<div role="button" tabIndex={0} onKeyDown={...}>` — rejected; manually re-implementing what the browser gives for free invites subtle bugs (Space-activation is especially easy to miss). The current code already has an interactive circle but it's not the trigger — it's purely presentation. Converting it to a real button is the cleaner fix.
- `<label>` wrapping a hidden `<input>` (what the current implementation does for upload) — rejected; we're removing that pattern entirely per FR-307.

---

## R9 — Shutter, Keep, Retake, Cancel: layout and keyboard order

**Decision**: Below the circle, render a single container whose content depends on state:

- **Live state**: `[Cancel] [Shutter] [empty-spacer-for-symmetry]` — a three-slot horizontal flex row so the shutter is visually centred. Shutter is the primary button (filled); Cancel is secondary (outline). Tab order: circle (already focused, or focusable if user Shift-Tabs) → Shutter → Cancel.
- **Still state**: `[Retake] [Keep] [empty]` — Keep primary, Retake secondary. Tab order: circle → Keep → Retake (Keep first, matching FR-301b's "Tab order MUST reach Keep first").
- **Committed state**: no slot-row; show the existing "Retake / clear" control from 001 (unchanged).
- **Unavailable state**: no slot-row; show only the polite message (with `role="status"` / `role="alert"` per R6) and leave Generate disabled.

Cancel activation: both a visible Cancel button AND the Escape key (`onKeyDown` handler on the PhotoIntake root) trigger cancel. Escape works while the PhotoIntake subtree contains focus; pressing it outside the subtree has no effect (doesn't hijack global Escape). FR-301a.

**Rationale**:
- A single slot-container avoids layout jump between live and still states (the row's height stays constant, preventing the Setup tab from re-flowing under the user).
- Placing Keep first in Tab order satisfies FR-301b and matches users' expectations (confirm is the default action; retake is the alternative).
- Escape as a cancel affordance is a well-established convention and lets keyboard-only users dismiss quickly.

**Alternatives considered**:
- Replacing the circle's inner label with inline text prompting "Press shutter to capture" — rejected, verbose; the circle itself plus the visible shutter button are sufficient affordance.
- Putting the shutter *inside* the circle (overlay icon button) — rejected, interactive-button-inside-interactive-button is an accessibility anti-pattern. Keeps controls clearly separated.

---

## R10 — Copy: the FR-310 circle label and the FR-312 unavailable message

**Decision**:

- Empty-circle label (inside the circle, visible text): **"Take photo"**. Shorter than "Take your photo" and avoids device-specific wording (no "tap" vs "click"). Works on any input modality. Screen-reader aria-label on the button remains the fuller **"Take a photo of yourself"** (R8).
- Unavailable message (rendered where the old Retake/clear error used to render): **"Camera access is needed to take your photo. Please allow camera access in your browser and try again, or connect a camera if none is available."** One sentence, generic (no provider / browser / DOMException name), guides the user through the three actionable remedies. For the `capability_missing` subcase, the text is nuanced to: **"Your browser doesn't support in-app camera capture. Please open this page in a supported browser."** — still generic with respect to *which* browser, still non-blaming. FR-313 allows this slight nuance (the remedy differs substantively) while keeping copy out of the raw-error register.

**Rationale**: The spec requires a single user-visible message for the "non-blocking" experience (FR-312) but also that the message "suggest the corrective action available to the user" (FR-312 last sentence). A single string cannot both universally fit "please connect a camera" and "please open a different browser" because the remedies are fundamentally different; the spec's own wording permits the remedy-dependent nuance, and FR-313 restricts nuance only from the *reason* side (not the *remedy* side).

**Alternatives considered**:
- "Add photo" (current 002 label) — rejected, now inaccurate (no upload means "add from where?"). "Take photo" disambiguates.
- Localising — rejected, the POC is monolingual English; localisation would be a later-feature concern.

---

## Summary

All Technical-Context unknowns resolved. Zero new npm dependencies. One file rewritten (`PhotoIntake.tsx`), three new lib / hook files, test updates mirroring each production module. The feature is self-contained within `frontend/src/features/alterego/`. Ready for Phase 1 (`data-model.md`, `contracts/` [empty], `quickstart.md`).

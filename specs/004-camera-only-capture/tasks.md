---

description: "Task breakdown for feature 004-camera-only-capture"
---

# Tasks: Camera-Only Photo Intake — Tap the Circle, Take a Selfie

**Input**: Design documents from `/specs/004-camera-only-capture/`
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `quickstart.md`. (No `contracts/` deltas — the wire surface is unchanged; see `contracts/README.md`.)

**Tests**: MANDATORY per Principle III (Test-First Development — NON-NEGOTIABLE). Every test task MUST be written, pushed, and observed to FAIL before its paired implementation task is started. Unit line coverage gate ≥ 90% (Vitest `coverage`) on each new/rewritten frontend module.

**Organization**: Tasks grouped by user story (US1, US2, US3) so each story is an independently testable increment. MVP is US1 alone.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Different files, no dependency on earlier incomplete tasks in this phase → safe to run in parallel.
- **[Story]**: Present on user-story phase tasks only (US1 / US2 / US3). Absent on Setup / Foundational / Polish.
- Paths are repo-relative (`frontend/…`). No backend paths appear because this feature does not touch `backend/`.

## Path Conventions

- **Frontend (rewrite target)**: `frontend/src/features/alterego/components/PhotoIntake.tsx` (+ colocated `PhotoIntake.test.tsx`). All other new / edited files land under `frontend/src/features/alterego/{hooks,lib}/` with colocated `*.test.ts` for Vitest.
- **Frontend (E2E)**: `frontend/tests/e2e/*.spec.ts` (Playwright). A new spec `camera-capture.spec.ts` is the primary E2E surface for this feature.
- **Shared styling**: `frontend/src/index.css` (global stylesheet used by the existing `.photo-intake__*` classes).
- **Backend**: **0 files touched.** Do NOT edit anything under `backend/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Enable Playwright's fake-media-stream mode so the new E2E spec (T010) has a deterministic camera feed in Chromium.

- [X] T001 Update `frontend/playwright.config.ts`: add `launchOptions: { args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'] }` to the existing `chromium` project's `use` block. These flags make Chromium auto-accept camera permission and emit a deterministic green/red test pattern for `getUserMedia` — required by `camera-capture.spec.ts` (T010) and harmless to every other spec (none of them call `getUserMedia`).

**Checkpoint**: `npm run test:e2e` still passes (existing specs unchanged in behaviour).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Pure helpers + the `useCameraStream` hook that every user story depends on. These MUST land before any `PhotoIntake.tsx` rewrite work in Phase 3.

**⚠️ CRITICAL**: No US1 / US2 / US3 task may start until Phase 2 is green. The hook's state machine (`idle → requesting → live → capturing → still-preview → releasing → idle`) and the `squareCrop` geometry are load-bearing for all three stories.

### Tests (MUST FAIL first — Principle III)

- [X] T002 [P] Unit test `squareCrop.test.ts` — pure-function tests for the square-crop helper per `research.md` R4 / `data-model.md` `CapturedFrame`. Cases: (a) 16:9 source → 1:1 centred output of `min(side, targetSize)` px; (b) 4:3 source → 1:1 centred; (c) already-square source → unchanged dimensions; (d) smaller-than-target source (e.g. 480×480 vs `targetSize: 1024`) NOT up-scaled — output is 480×480; (e) `mirror: false` (always passed on the commit path) produces an un-mirrored output identical to the source pixels; (f) output `Blob.type === "image/jpeg"`; (g) deterministic pixel at `(w/2, h/2)` preserved from source to output. Uses Vitest + jsdom + `document.createElement('canvas')` pre-filled with a known test pattern. In `frontend/src/features/alterego/lib/squareCrop.test.ts`
- [X] T003 [P] Unit test `cameraCapability.test.ts` — pure-function tests for the static capability detector. Cases: (a) when `navigator.mediaDevices?.getUserMedia` is a function → returns `{ supported: true }`; (b) when `navigator.mediaDevices === undefined` → returns `{ supported: false, reason: "capability_missing" }`; (c) when `navigator.mediaDevices` exists but `getUserMedia` is not a function → returns `{ supported: false, reason: "capability_missing" }`; (d) when `navigator === undefined` (SSR / Node guard) → returns `{ supported: false, reason: "capability_missing" }`. Function MUST NOT invoke the permission prompt. In `frontend/src/features/alterego/lib/cameraCapability.test.ts`
- [X] T004 [P] Unit test `useCameraStream.test.ts` — React hook tests via `@testing-library/react`'s `renderHook`. Mocks `navigator.mediaDevices.getUserMedia` via `vi.spyOn(navigator.mediaDevices, 'getUserMedia')`. Cases: (a) initial state is `idle`; (b) `start()` transitions `idle → requesting → live` when the promise resolves; (c) `stream` is non-null in `live` state with at least one stubbed video track; (d) `start()` rejection mapping — `NotAllowedError → permission_denied`, `SecurityError → permission_denied`, `NotFoundError → no_camera_hardware`, `OverconstrainedError → no_camera_hardware`, `NotReadableError → stream_unavailable`, `AbortError → stream_unavailable`, `TypeError → stream_unavailable` (seven cases); (e) when `cameraCapability()` reports unsupported, `start()` transitions directly to `unavailable` with `reason = "capability_missing"` WITHOUT invoking `getUserMedia`; (f) `captureStill()` from `live` state transitions through `capturing → still-preview` and produces a Blob + objectUrl; (g) `retake()` from `still-preview` transitions back to `live`, revokes the previous objectUrl, and does NOT call `getUserMedia` a second time (stream reuse per FR-301b); (h) `commit()` from `still-preview` transitions `releasing → idle`, stops every video track (`track.stop()` called), clears `stream` to null; (i) `cancel()` from any non-idle state transitions `releasing → idle`, stops every track, revokes the still-objectUrl if present; (j) React `useEffect` cleanup on unmount is equivalent to `cancel()`. In `frontend/src/features/alterego/hooks/useCameraStream.test.ts`

### Implementation

- [X] T005 [P] Implement `squareCrop.ts` — pure async function `squareCrop(source, { mirror, targetSize }) → Promise<Blob>` per the algorithm in `research.md` R4. Signature accepts `HTMLVideoElement | HTMLCanvasElement` (video source in production; canvas source in unit tests). Computes `side = min(videoWidth ?? width, videoHeight ?? height)`, centred offsets `sx, sy`, target `targetPx = min(side, targetSize)`. Uses `OffscreenCanvas` + `convertToBlob({ type: 'image/jpeg', quality: 0.92 })` with a feature-detected fallback to `document.createElement('canvas')` + `canvas.toBlob(...)`. Never applies a mirror transform (mirroring is CSS-only per FR-302a). In `frontend/src/features/alterego/lib/squareCrop.ts`
- [X] T006 [P] Implement `cameraCapability.ts` — synchronous pure function `cameraCapability(): { supported: true } | { supported: false, reason: "capability_missing" }` per `research.md` R5. Checks `typeof navigator !== "undefined" && navigator.mediaDevices?.getUserMedia` is a function. Does not invoke the permission prompt. Also exports the `CameraUnavailableReason` union type used by `useCameraStream` per `data-model.md`. In `frontend/src/features/alterego/lib/cameraCapability.ts`
- [X] T007 [P] Implement `useCameraStream.ts` — React hook encapsulating the `CameraSession` state machine from `data-model.md`. Owns one `MediaStream` ref, reused across Retake cycles per `research.md` R2. Exposes `{ status, unavailableReason, stillPreviewUrl, start(), captureStill(videoElement), retake(), commit(), cancel() }`. On `start()`, first consults `cameraCapability()`; if unsupported, transitions to `unavailable(capability_missing)` without invoking `getUserMedia`. Otherwise calls `getUserMedia({ video: { facingMode: "user" }, audio: false })`; on rejection, classifies the `DOMException.name` per the mapping table in `research.md` R5 and transitions to `unavailable(reason)`. `captureStill(videoElement)` calls `squareCrop(videoElement, { mirror: false, targetSize: 1024 })` and stores the Blob + `URL.createObjectURL(blob)` in state. `commit()` stops every track, revokes the still URL, and returns the Blob via promise to the caller. `cancel()` is a commit-without-return. A `useEffect` cleanup guarantees `track.stop()` + URL revoke on unmount and on page `visibilitychange → hidden`. In `frontend/src/features/alterego/hooks/useCameraStream.ts`

**Checkpoint**: `npm test` (Vitest) green. Coverage on `squareCrop.ts`, `cameraCapability.ts`, `useCameraStream.ts` individually ≥ 90% per Principle III. No UI-visible change yet — the hook and libs are not wired to `PhotoIntake.tsx`.

---

## Phase 3: User Story 1 — Tap the circle, take a selfie (Priority: P1) 🎯 MVP

**Goal**: On any modern browser with a working webcam, activating the photo circle opens a live camera preview *inside the circle*, a shutter below it captures a frame into a still-preview, Keep commits the still as the photo, Retake reuses the open stream, and Cancel/Escape aborts cleanly — all in a single inline surface with no modal, no file picker, no OS-camera-app hand-off.

**Independent Test**: Run `npx playwright test tests/e2e/camera-capture.spec.ts --project=chromium` with the fake-media-stream flags from T001. The spec drives: click circle → observe live viewfinder inside the circle → click shutter → observe still-preview + Keep/Retake controls → click Retake → observe live viewfinder restored → click shutter again → click Keep → observe committed photo cropped-to-circle → assert Generate button becomes enabled (with the other Setup inputs pre-seeded via helpers). Additionally assert that `navigator.mediaDevices.getUserMedia` was invoked **exactly once** across the full flow (stream-reuse across Retake).

### Tests for User Story 1 (MANDATORY — must fail before implementation) ⚠️

- [X] T008 [US1] Rewrite `frontend/src/features/alterego/components/PhotoIntake.test.tsx` for the happy-path state machine. Replace the entire existing test body (file-input-era tests for "Camera"/"Upload photo") with: (a) renders an accessible `<button>` labelled "Take a photo of yourself" when empty; (b) activating the button via click, Enter, and Space all call `useCameraStream.start()` (mocked); (c) when the hook status is `live`, a `<video>` element is rendered inside the circle with `srcObject`, `playsInline`, `muted`, and CSS `transform: scaleX(-1)` applied; (d) a "Take photo" shutter button appears below the circle with a visible focus ring; (e) pressing the shutter calls `captureStill(videoElement)` and transitions to `still-preview`; (f) in `still-preview`, an `<img>` of the still-Blob appears inside the circle WITHOUT the mirror transform, plus Keep and Retake buttons (Tab order: Keep first per FR-301b); (g) pressing Keep calls `commit()`, receives the Blob, runs it through `downscalePhoto`, and invokes `onPhotoSelected(blob, objectUrl)`; (h) pressing Retake calls `retake()` without a second `getUserMedia` handshake; (i) ARIA live-region announcements for each state transition ("Camera ready", "Photo captured. Press Keep or Retake", "Photo saved"); (j) committed-state preview uses the existing `.photo-intake__image` treatment — regression assertion for parity with 001/002/003.
- [X] T009 [P] [US1] Extend `frontend/tests/e2e/keyboard-walkthrough.spec.ts` — add a subtest that Tabs to the photo circle, activates with Enter, Tabs to the shutter and fires it with Space, Tabs to Keep and fires it, then Tabs to the next Setup control (first ArchetypeGrid tile or whatever follows the photo column). Assert a visible focus outline at each stop (via `await expect(focused).toBeVisible()` + screenshot visual diff on the focused element's outline). Remove the existing `page.getByLabel('Upload photo')` hop (line 23, line 103) — it no longer exists after US1/US2 land.
- [X] T010 [P] [US1] Create `frontend/tests/e2e/camera-capture.spec.ts` — Playwright E2E for the happy path and Retake-reuse assertion. Setup: inject a window hook counting `getUserMedia` invocations (`await page.addInitScript(() => { const orig = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices); (window as any).__gumCount = 0; navigator.mediaDevices.getUserMedia = (c) => { (window as any).__gumCount++; return orig(c) } })`). Flow: seed all Setup inputs except photo; click circle; wait for viewfinder `<video>` with non-zero `videoWidth`; click shutter; wait for still-preview `<img>`; click Retake; wait for viewfinder restored; click shutter; click Keep; assert the Setup tab's committed `.photo-intake__image` is present; assert Generate button is `enabled`; assert `await page.evaluate(() => (window as any).__gumCount) === 1` (stream reuse — the sole assertion distinguishing Q3's reuse guarantee).

### Implementation for User Story 1

- [X] T011 [US1] Rewrite `frontend/src/features/alterego/components/PhotoIntake.tsx`. Replace the current hidden-file-input + pill-buttons implementation with: (a) a single `<button type="button" className="photo-intake__circle" aria-label={photoPreviewUrl ? 'Retake your photo' : 'Take a photo of yourself'}>` as the circle; (b) inside the button, conditional rendering — empty state shows an icon + "Take photo" label, `live` shows `<video ref autoPlay playsInline muted>`, `still-preview` shows `<img src={stillPreviewUrl} alt="">`, committed state shows `<img src={photoPreviewUrl} ...>`; (c) the button click handler: if committed, calls `onPhotoCleared()` first then `start()`; else `start()`; (d) below the circle, a state-driven controls row — `live` renders `[Cancel] [Shutter]`, `still-preview` renders `[Retake] [Keep]`, `unavailable` renders nothing (message takes its place), committed renders the existing "Retake / clear" button from 001 (behaviour unchanged); (e) Keep's handler awaits `commit()`, passes the returned Blob through the existing `downscalePhoto()`, and calls `onPhotoSelected(reduced, url)`; (f) uses `useLiveAnnouncer` to announce each state transition per `research.md` R6; (g) consumes `useCameraStream` from Phase 2; (h) imports `squareCrop` indirectly via the hook (the hook calls `captureStill → squareCrop`). Depends on T005, T006, T007.
- [X] T012 [US1] Update `frontend/src/index.css`. Add: `.photo-intake__viewfinder` (absolute-positioned `<video>` inside the circle, `object-fit: cover`, CSS `transform: scaleX(-1)` — mirror applies only while the live state is active; use a data-attribute on the `<video>` or a conditional class so the still-preview `<img>` does not receive the transform); `.photo-intake__still` (absolute-positioned `<img>` inside the circle, no mirror transform, same `object-fit: cover`); `.photo-intake__shutter` (primary-button styling matching the existing design tokens); `.photo-intake__keep` (primary); `.photo-intake__retake` (secondary); `.photo-intake__cancel` (secondary outline); adjust `.photo-intake__actions` to host the state-dependent controls row (flex row, three slots wide, centre alignment). Add a `.photo-intake__circle:focus-visible` rule with a visible outline offset outside the `border-radius: 50%` clip per R3. Depends on T011.
- [X] T013 [US1] Wire Escape-to-cancel in `frontend/src/features/alterego/components/PhotoIntake.tsx`. Attach an `onKeyDown` handler on the PhotoIntake root div (not window) that calls `cancel()` when the key is Escape AND the hook status is `requesting | live | capturing | still-preview`. Does not hijack Escape outside the subtree. Covered by T008 subtest (i).
- [X] T014 [P] [US1] Update the peer Vitest tests that currently seed photos via `getByLabelText('Upload photo')`: rewrite the photo-seeding in `frontend/src/features/alterego/AlterEgoPage.test.tsx` (4 references at lines ~85, 94, 117, 129) and `frontend/src/App.test.tsx` (1 reference at line 26) to instead (a) mock `useCameraStream` at the module level with `vi.mock('../../hooks/useCameraStream', () => ({ useCameraStream: () => ({ status: 'idle', start: vi.fn(), ... }) }))`, or (b) for the "label present" smoke test in `App.test.tsx` assert `getByRole('button', { name: /take a photo of yourself/i })` instead of `getByLabelText('Upload photo')`. Keep the parent-component contract identical — dispatch `PhotoSelected` with a stub Blob to drive the downstream Generate-button enablement check.
- [X] T015 [P] [US1] Update `frontend/tests/e2e/helpers.ts` `seedPhoto(page)` (line ~135) and the e2e specs that call the file-input directly: `frontend/tests/e2e/setup-form.spec.ts` (lines ~148, 150, 166) and `frontend/tests/e2e/generate-happy-path.spec.ts` (line ~52). Replace `page.getByLabel('Upload photo').setInputFiles({...})` with a new `seedPhoto(page)` implementation that drives the capture UI: `await page.getByRole('button', { name: /take a photo of yourself/i }).click()`; wait for the shutter to appear; `await page.getByRole('button', { name: /take photo/i }).click()`; wait for Keep to appear; `await page.getByRole('button', { name: /keep photo/i }).click()`. Works because T001 enabled the fake-media-stream flag. Callers of `seedPhoto` elsewhere in the suite inherit the new behaviour transparently.

**Checkpoint**: `npm test` + `npm run test:e2e tests/e2e/camera-capture.spec.ts tests/e2e/keyboard-walkthrough.spec.ts tests/e2e/generate-happy-path.spec.ts tests/e2e/setup-form.spec.ts` all green. MVP complete: a user can take a selfie, keep it, and press Generate end-to-end.

---

## Phase 4: User Story 2 — No more "Camera" or "Upload photo" pill buttons (Priority: P1)

**Goal**: The Setup tab's photo column exposes exactly one interactive control (the circle), plus the existing "Retake / clear" button after a photo exists. No button, input, drag-and-drop zone, or file-picker entry point labelled or aria-named "Camera" / "Upload photo" / "📷" / "🗂" remains in the rendered DOM.

**Independent Test**: Grep the rendered Setup tab at three viewports (1280×800, 768×1024, 375×667) for `text=/camera/i`, `text=/upload/i`, and the emoji class names `photo-intake__pill--camera` / `photo-intake__pill--upload` / `photo-intake__file-input`. Each returns zero matches. The only "camera" hit acceptable is the empty-circle's internal icon (not a separate button).

### Tests for User Story 2 (MANDATORY — must fail before implementation) ⚠️

- [X] T016 [US2] Append DOM-absence subtests to `frontend/src/features/alterego/components/PhotoIntake.test.tsx`. Cases (all using `queryBy…` → `toBeNull()`): (a) `queryByRole('button', { name: /^camera$/i })` returns null in every state (empty, requesting, live, still-preview, committed, unavailable); (b) `queryByRole('button', { name: /upload photo/i })` returns null in every state; (c) `container.querySelector('input[type="file"]')` returns null in every state; (d) `container.querySelector('.photo-intake__pill')` returns null; (e) `container.querySelector('.photo-intake__file-input')` returns null. Run each assertion as a parameterised sub-`test()` for clear diagnostics per state.
- [X] T017 [P] [US2] Append DOM-absence subtests to `frontend/tests/e2e/camera-capture.spec.ts`. For each of the three photo states (empty, live-viewfinder, still-preview), assert `await page.getByRole('button', { name: /^camera$/i }).count() === 0` and `await page.getByLabel(/upload photo/i).count() === 0` and `await page.locator('input[type="file"]').count() === 0` and `await page.locator('.photo-intake__pill').count() === 0`.

### Implementation for User Story 2

- [X] T018 [US2] Delete the dead CSS for the removed pill buttons and the dead hidden-file-input wrapper from `frontend/src/index.css`. Specifically remove all rule blocks keyed on `.photo-intake__pill`, `.photo-intake__pill--camera`, `.photo-intake__pill--upload`, and `.photo-intake__file-input` (the legacy screen-reader-only class that hid the `<input type="file">`). US1's T011 rewrite removes their JSX consumers; this task removes the orphaned CSS so the stylesheet stays lean.
- [X] T019 [US2] Grep-verify the removal: run `grep -rn 'photo-intake__pill\|photo-intake__file-input' frontend/src frontend/tests` and assert **zero** hits. If any stragglers remain (likely only in archived comments), delete them in the same commit.

**Checkpoint**: T016 and T017 green. A full-suite DOM search for "Camera" / "Upload photo" / `photo-intake__pill` across `frontend/src/` returns no production-code hits. `npm run test:e2e` still passes.

---

## Phase 5: User Story 3 — Graceful handling when no camera is available (Priority: P2)

**Goal**: Each of the four FR-312 triggers (permission denied, no camera hardware, MediaDevices capability missing, stream unavailable) produces a polite, accessible, non-blocking message inside the photo column. Generate stays disabled. The user can retry (after granting permission or connecting a camera) without reloading the page. No raw `DOMException.name` ever reaches the UI.

**Independent Test**: In `camera-capture.spec.ts` (US1) + a new `camera-unavailable.spec.ts` or extension: launch Playwright WITHOUT the fake-device flag for a Chromium subproject; click the circle; assert a `role="alert"` or `role="status"` element appears within 2 seconds (SC-303 bound); assert the message text is polite and does NOT contain `NotAllowedError`, `NotFoundError`, etc. (FR-313); assert Generate remains disabled. Then override `navigator.mediaDevices` via `page.evaluate(() => { navigator.mediaDevices.getUserMedia = () => Promise.resolve(fakeStream) })` to simulate a permission-re-grant and assert the next click resumes normal flow without a page reload (FR-314).

### Tests for User Story 3 (MANDATORY — must fail before implementation) ⚠️

- [X] T020 [US3] Append unavailable-state subtests to `frontend/src/features/alterego/components/PhotoIntake.test.tsx`. Use `vi.spyOn(navigator.mediaDevices, 'getUserMedia')` to reject with each of: `new DOMException('', 'NotAllowedError')`, `new DOMException('', 'NotFoundError')`, `new DOMException('', 'NotReadableError')`. For each: (a) assert an element with `role="alert"` renders within the photo column; (b) assert the rendered message text contains none of the DOMException names or raw error strings; (c) assert Generate remains disabled (verifiable via the component-under-test's exposed state or by rendering inside a lightweight test harness that mocks the parent reducer); (d) assert the FR-312 generic message variant is shown for the first three (all collapse to one string per Q2 / FR-313), and a DIFFERENT FR-313 `capability_missing`-specific copy is shown when `cameraCapability()` is stubbed to return unsupported (R10). Also assert the "try again" path — stubbing `getUserMedia` to resolve on the next call and re-activating the circle must transition to `live` (FR-314).
- [X] T021 [P] [US3] Extend `frontend/src/features/alterego/hooks/useCameraStream.test.ts` (the one created in T004) — add exhaustive coverage for the full FR-312 trigger matrix if T004's coverage is partial: all seven `DOMException.name` → `CameraUnavailableReason` mappings from `research.md` R5 plus the `capability_missing` static-detection branch. Assert that on each `unavailable` branch, no `MediaStream` reference is retained (`stream` stays null) and no tracks are left in the `live` state. Also assert that transitioning `unavailable → idle` is a no-op for cleanup (nothing to release) and `unavailable → requesting` on a retry does NOT skip the capability check.
- [X] T022 [P] [US3] Extend `frontend/tests/e2e/camera-capture.spec.ts` with an unavailable subtest. Strategy: before the spec navigates, `page.addInitScript(() => { Object.defineProperty(navigator, 'mediaDevices', { value: undefined }) })` to simulate `capability_missing`. Then click the circle; assert the polite capability-missing message appears (substring-match on the copy pinned in R10); assert `await page.getByRole('button', { name: /generate/i }).isDisabled() === true`. Second subtest: restore `navigator.mediaDevices` mid-test via `page.evaluate` and re-activate the circle; assert the live viewfinder renders (recovery per FR-314).

### Implementation for User Story 3

- [X] T023 [US3] Extend `frontend/src/features/alterego/components/PhotoIntake.tsx` to render the `unavailable` state. When `useCameraStream` reports `status === "unavailable"`, render a `<p role="alert" className="photo-intake__error">` below the circle with the FR-312 copy; use the R10-pinned strings, branching once between `reason === "capability_missing"` (the "browser doesn't support…" variant) and the other three reasons (the generic "camera access is needed…" variant). The circle button remains enabled so the user can retry (FR-314). Announce the state transition via `useLiveAnnouncer`. Ensure this message is visually and programmatically distinct from the `externalError` region so both can render simultaneously (the rare backend-error-plus-camera-unavailable case).
- [X] T024 [US3] Verify the Generate-gating regression holds end-to-end. No code change expected — the Generate button is gated by the reducer's `photoBlob !== null` check inherited from 001 FR-009. This task is a one-line grep + visual confirmation in `frontend/src/features/alterego/state/reducer.ts` that `photoBlob` starts null and never auto-populates in `unavailable` states. Document in a comment on the task's commit message that T022's Playwright assertion is the enforcement.

**Checkpoint**: `npm test` + T022 subtest green. Manual walkthrough of `quickstart.md` §"Testing the degraded path" (all three techniques) produces the expected polite messages and Generate stays disabled. Recovery after re-granting permission works without a page reload.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Accessibility audit, coverage gate, broad regression sweep, lint/typecheck, and the manual quickstart smoke.

- [X] T025 [P] Extend `frontend/tests/e2e/axe-scan.spec.ts` to run axe-core across the three photo states (empty, live-viewfinder visible, unavailable message visible). Use the fake-media-stream project to reach the live state; use the `Object.defineProperty(navigator, 'mediaDevices', { value: undefined })` trick to reach the unavailable state. Assert zero "serious" or "critical" violations at each state. SC-305.
- [X] T026 [P] Run `cd frontend && npm run test:coverage` and confirm **≥ 90% line coverage** (Principle III gate) on each of: `frontend/src/features/alterego/components/PhotoIntake.tsx`, `frontend/src/features/alterego/hooks/useCameraStream.ts`, `frontend/src/features/alterego/lib/squareCrop.ts`, `frontend/src/features/alterego/lib/cameraCapability.ts`. If any module is below the gate, add targeted unit tests in its colocated `*.test.ts(x)` to close the gap.
- [X] T027 [P] Run the full Playwright regression matrix (`cd frontend && npm run test:e2e`) and confirm zero regressions in: `generate-happy-path.spec.ts`, `generate-fallback.spec.ts`, `setup-form.spec.ts`, `tabs-shell.spec.ts`, `e2e-flow.spec.ts`. These specs consume the updated `seedPhoto(page)` helper from T015 transparently; green confirms FR-317 / SC-306 — no downstream behaviour regression.
- [X] T028 [P] Run `cd frontend && npm run lint && npm run build`. ESLint flat config must be clean (no warnings). `tsc --noEmit` (the first step of `npm run build`) must pass under strict mode. If the `no-unused-vars` / `prefer-const` rules flag anything from the PhotoIntake rewrite, resolve in the same commit.
- [ ] T029 Manual quickstart smoke per `specs/004-camera-only-capture/quickstart.md`. Execute each section on a real browser with a real webcam: (a) Happy path — tap circle → grant → live → shutter → still → Retake 2× → Keep → photo committed → Generate → poster; (b) Permission denied — block at the prompt, observe polite message, unblock in site settings, retry without reload; (c) No camera hardware — disable webcam via OS settings, observe polite message; (d) Capability missing — paste the `navigator.mediaDevices = undefined` console snippet before tapping, observe the variant polite message. Record any regressions as new tickets; do NOT fix them in this feature's PR unless they are blocking.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: T001 has no dependencies — can start immediately.
- **Foundational (Phase 2)**: No task dependency on Setup (T001 is E2E-only), but logically lands right after for narrative coherence. T002–T004 (tests) must fail-first; T005–T007 (impl) depend on their paired tests.
- **US1 (Phase 3)**: Depends on Phase 2 complete (T005, T006, T007 all merged and green). T008 depends on T002–T007. T011 depends on T005, T006, T007. T012 depends on T011 (same stylesheet, but CSS lands atomically with the JSX consuming it — don't split across commits).
- **US2 (Phase 4)**: Depends on US1 complete (T011 rewrite is the JSX-level removal; US2's T018–T019 are the CSS/dead-code cleanup that US1 leaves behind).
- **US3 (Phase 5)**: Depends on Phase 2 (the hook's `unavailable` state machine is foundational) and Phase 3 (T011 is the component being extended by T023). Can start in parallel with US2 once Phase 3 is checkpoint-green, since US2 and US3 touch different concerns.
- **Polish (Phase 6)**: Depends on Phases 1–5 being all green.

### User Story Dependencies

- **US1 (P1)**: Depends on Phase 2. No dependency on other user stories.
- **US2 (P1)**: Depends on Phase 3 (US1's component rewrite defines what's being "removed"). In practice US1 and US2 ship in the same PR — the rewrite inherently removes the pill buttons and US2 adds the explicit test + cleans the dead CSS.
- **US3 (P2)**: Depends on Phase 3 (T011 is the file being extended). Independent of US2.

### Within Each User Story

- Tests MUST be written and FAIL before implementation (Principle III).
- Pure libs and hooks (Phase 2) before UI consumers (Phase 3+).
- JSX rewrite (T011) before CSS updates (T012 — but lands atomically in the same commit).
- Peer-test regressions (T014, T015) can land in parallel with T011–T013 because they touch different files, but all must land **in the same PR** as the JSX rewrite to keep `main` green.

### Parallel Opportunities

- **Phase 2 tests** (T002, T003, T004) in parallel — three separate files.
- **Phase 2 implementations** (T005, T006, T007) in parallel — three separate files, no cross-imports.
- **Phase 3 tests** (T009, T010) in parallel with each other. T008 is its own file; T009 extends an existing spec; T010 creates a new spec.
- **Phase 3 peer-test updates** (T014, T015) in parallel with T011–T013 once the new production code compiles.
- **US2 + US3** can proceed in parallel after Phase 3 checkpoint.
- **Phase 6 polish** — T025, T026, T027, T028 all in parallel.

---

## Parallel Example: Phase 2 Foundational

```bash
# Launch the three failing unit tests together:
Task: "Unit test squareCrop.test.ts in frontend/src/features/alterego/lib/squareCrop.test.ts"
Task: "Unit test cameraCapability.test.ts in frontend/src/features/alterego/lib/cameraCapability.test.ts"
Task: "Unit test useCameraStream.test.ts in frontend/src/features/alterego/hooks/useCameraStream.test.ts"

# After all three fail as expected, launch the three implementations together:
Task: "Implement squareCrop.ts in frontend/src/features/alterego/lib/squareCrop.ts"
Task: "Implement cameraCapability.ts in frontend/src/features/alterego/lib/cameraCapability.ts"
Task: "Implement useCameraStream.ts in frontend/src/features/alterego/hooks/useCameraStream.ts"
```

## Parallel Example: Phase 3 US1 — tests

```bash
# T008 (same-file rewrite) sequential; T009 / T010 parallel with each other:
Task: "Rewrite PhotoIntake.test.tsx for the happy-path state machine"
# then, in parallel:
Task: "Extend keyboard-walkthrough.spec.ts with the new Tab order"
Task: "Create camera-capture.spec.ts Playwright happy-path + Retake-reuse"
```

---

## Implementation Strategy

### MVP First (US1 only)

1. Complete Phase 1: Setup (T001).
2. Complete Phase 2: Foundational (T002–T007) — fail-first tests then implementations.
3. Complete Phase 3: US1 (T008–T015) — the rewrite + helpers/peer-test updates.
4. **STOP and VALIDATE**: Run `npm test` + `npm run test:e2e tests/e2e/camera-capture.spec.ts`. Manual smoke: open the Setup tab, take a selfie, press Generate, get a poster. MVP shipped.

At this checkpoint, US2 falls out almost for free (the rewrite has no pills), so bundling US2 into the same PR keeps the narrative clean.

### Incremental Delivery

1. Setup + Foundational → libs green, no UI change yet.
2. US1 → PhotoIntake rewritten; happy path works end-to-end. MVP.
3. US2 → tests + CSS cleanup confirm no dead surface lingers.
4. US3 → unavailable path polished.
5. Polish → axe, coverage, regression sweep, lint, quickstart smoke.
6. PR.

### Parallel Team Strategy

With multiple developers after Phase 2 checkpoint:

- Developer A: Phase 3 (US1 rewrite + peer-test updates).
- Developer B: Phase 5 (US3 unavailable-state tests + impl) — can start in parallel because it's additive on top of a stub `PhotoIntake` that Developer A has stood up through the live/still states.
- Either: Phase 4 (US2 explicit tests + dead-CSS cleanup).
- Converge for Polish.

---

## Notes

- **Zero backend tasks.** Do NOT edit anything under `backend/` for this feature. If a reviewer asks "where's the API change?", the answer is in `contracts/README.md`: there is none.
- **Zero new npm dependencies.** If a `package.json` diff appears in the PR, something went wrong — `research.md` R1 explicitly rejects `react-webcam`, `pica`, and `adapter.js`.
- [P] tasks = different files, no dependencies on incomplete tasks in the same phase.
- [Story] label maps task to specific user story for traceability.
- TDD gate: verify tests fail before implementing. `git diff --stat` on the test-only commit should be non-empty and CI red; implementation commit turns it green.
- Commit after each task or logical group (one commit per test/impl pair is fine).
- Stop at any checkpoint to validate the story independently.
- Avoid: vague tasks, same-file conflicts across [P] tasks, cross-story dependencies that break US1's MVP independence.

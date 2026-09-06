# Data Model — Camera-Only Photo Intake

**Feature**: 004-camera-only-capture
**Date**: 2026-04-23

This feature is UI-local and in-memory only. There is no persistent data, no database table, no API contract change, no new wire field. The "data model" here is the **client-side state** owned by the rewritten `PhotoIntake` component and its helper hook, plus the unchanged downstream entities it feeds.

---

## Unchanged entities (inherited, restated for clarity)

### Photo

- **Source of truth**: 001 FR-001 (content), 003 FR-215 (lifecycle).
- **Shape in frontend state**: `{ photoBlob: Blob | null; photoPreviewUrl: string | null }`, already carried by `AlterEgoSession` in `frontend/src/features/alterego/state/reducer.ts`. Semantics unchanged.
- **What changes in this feature**: The `photoBlob` now arrives via the new `useCameraStream` + `squareCrop` pipeline instead of a file-input `change` event. The downstream contract — the Blob is a JPEG or PNG, ≤ 5 MB after the 001 `downscalePhoto()` step, dimensions ≤ 1024×1024 post-downscale — is preserved exactly. Consumers (reducer, `alterEgoClient`, `PhotoReducer` on the backend) see no difference.

### AlterEgoSession (reducer state)

- **Source of truth**: defined in `frontend/src/features/alterego/state/reducer.ts`.
- **What changes**: no state fields are added or removed. The existing `PhotoSelected` and `PhotoCleared` actions continue to carry the Blob + object URL; `PhotoIntake` dispatches them from the new Keep path and the existing Retake/clear button respectively. All six phases (idle, picking, generating, succeeded, failed_with_fallback, idle after StartOver) and all eleven action types are unchanged.

### AlterEgoRequest / AlterEgoResponse (wire)

- **Source of truth**: `specs/001-initial-poc/contracts/alter-egos.openapi.yaml` (as updated by 003 to include `vibe` and `meta.reason`).
- **What changes**: **nothing.** No field added, no field renamed, no enum modified. The OpenAPI version does not bump.

---

## New transient entities (client-only, in-memory)

### CameraSession

Owned by the `useCameraStream` hook. One instance per capture session; born on circle activation, dies on Keep / Cancel / unmount / tab-away. Never persisted, never serialised.

**Fields**:

| Field | Type | Lifecycle |
|---|---|---|
| `stream` | `MediaStream \| null` | Assigned by successful `getUserMedia`. Its tracks MUST be `track.stop()`-ed and the ref cleared on every exit path. |
| `status` | `"idle" \| "requesting" \| "live" \| "capturing" \| "still-preview" \| "releasing" \| "unavailable"` | Drives UI rendering. Transitions enumerated below. |
| `unavailableReason` | `CameraUnavailableReason \| null` | Populated only when `status === "unavailable"`. Internal only; never rendered raw (FR-313). |
| `stillBlob` | `Blob \| null` | Populated on shutter, cleared on Retake / Keep / Cancel. |
| `stillPreviewUrl` | `string \| null` | `URL.createObjectURL(stillBlob)`. Revoked on the same transitions that clear `stillBlob`. |

**State machine**:

```text
        activate (circle click / Enter / Space)
 idle ───────────────────────────────────────► requesting
                                                    │
                                   getUserMedia resolves
                                                    ▼
                                                   live ◄────────────┐
                                                    │                 │
                                            shutter pressed           │ Retake
                                                    ▼                 │
                                                capturing             │
                                                    │                 │
                                          squareCrop resolves         │
                                                    ▼                 │
                                              still-preview ──────────┘
                                                    │
                                              Keep pressed
                                                    ▼
                                                releasing ────► idle
                                                    │
                                             stop tracks, revoke URL

 idle ─── Cancel / unmount / tab-away (from live or still) ───► releasing
 requesting ─── getUserMedia rejects ─────────────────────────► unavailable
 idle ─── cameraCapability.supported === false ────────────────► unavailable
 unavailable ─── user retries (circle activation) ───────────► requesting
```

**Invariants**:

- `status === "live"` ⇒ `stream !== null` and at least one video track is `readyState === "live"`.
- `status === "still-preview"` ⇒ `stream !== null` (kept open for Retake reuse) AND `stillBlob !== null` AND `stillPreviewUrl !== null`.
- `status === "idle"` ⇒ `stream === null` AND `stillBlob === null` AND `stillPreviewUrl === null` (object URL revoked).
- On any transition into `releasing`, the stream's tracks are stopped before `status` advances to `idle`. This is enforced in the hook's cleanup.

### CameraUnavailableReason

Internal discriminator, consumed only by test assertions and optional telemetry. Never surfaced as raw text in the UI (FR-313).

```ts
export type CameraUnavailableReason =
  | "permission_denied"       // NotAllowedError, SecurityError
  | "no_camera_hardware"      // NotFoundError, OverconstrainedError
  | "stream_unavailable"      // NotReadableError, AbortError, TypeError (malformed constraints)
  | "capability_missing"      // cameraCapability() returned { supported: false }
```

The mapping from `DOMException.name` to this type lives in `useCameraStream.classifyError()` (unit-tested). The mapping from this type to user-facing copy lives in `PhotoIntake.tsx` and collapses to the single FR-312 message plus the FR-313-approved nuance for the `capability_missing` case (R10 in `research.md`).

### CapturedFrame (transient, intra-function)

Owned by `squareCrop.ts` — lifetime is a single invocation of `squareCrop(source, { mirror, targetSize }) → Promise<Blob>`. Not stored in any React state.

| Aspect | Value |
|---|---|
| Shape | Square JPEG Blob |
| Default target size | 1024×1024 pixels |
| Actual size rule | `min(side, targetSize)` where `side = min(source.videoWidth, source.videoHeight)` — never up-scaled |
| Mirror | Always `false` on the commit path (CSS mirror handles the live preview per R3 / FR-302a) |
| Encoding | `image/jpeg` at quality 0.92 |
| Origin | Centre-cropped from the source frame — `sx = (w - side) / 2`, `sy = (h - side) / 2` |

The Blob produced here is handed directly to `downscalePhoto()` from 001 — which typically passes it through unchanged because 1024×1024 JPEG is already under the 5 MB + 1024×1024 ceiling that downscale enforces. The existing downscale + size-validation pipeline is the final gate before `onPhotoSelected` dispatches `PhotoSelected` into the reducer.

---

## Props-level contract changes (component API)

`PhotoIntake` retains its existing public props contract — 001 designed it to be wire-agnostic:

```ts
interface Props {
  photoPreviewUrl: string | null
  onPhotoSelected: (blob: Blob, url: string) => void
  onPhotoCleared: () => void
  externalError?: string | null
}
```

**No change.** The parent (`SetupLayout` / `AlterEgoPage`) keeps dispatching `PhotoSelected` / `PhotoCleared` exactly as before; only the internals that call `onPhotoSelected` change (from file-input `change` handler → Keep button handler). This keeps the integration-seam 1:1 with 001/002/003 so regression risk for `SetupLayout`, `GenerateButton` gating, and the reducer is zero.

`externalError` continues to surface backend rejection messages. The new in-component `unavailableReason`-driven copy is *separate* from `externalError` and both can coexist (rare case: a stale upload error from a prior session plus a new unavailable state — they render in distinct sub-regions, both `role="alert"`-announced).

---

## What is explicitly NOT modelled here

- **No persisted storage.** Nothing in IndexedDB, localStorage, sessionStorage, cookies, cache, or memory-after-unload (FR-315).
- **No multi-camera selection UI.** FR-302 accepts the browser default. If multiple cameras exist, `facingMode: "user"` steers getUserMedia to a front camera; otherwise the browser picks one. No `deviceId` enumeration, no "switch camera" button.
- **No capture-history queue.** Each successful Keep replaces the previous Photo. The reducer's single `photoBlob` slot is the source of truth; there is no "undo last capture" semantics beyond the existing Retake / clear.
- **No backend state.** The backend sees only the final POSTed multipart body, identical in shape to what the file-upload path produced. No backend code knows or cares that the Photo arrived via live capture.

---

## Relationships

```text
AlterEgoSession (reducer, shared)
    └── photoBlob, photoPreviewUrl  ◄──── onPhotoSelected(blob, url)
                                              │
                                              │ (dispatch PhotoSelected)
                                              │
PhotoIntake (component)                       │
    ├── CameraSession (via useCameraStream)   │
    │       ├── stream                        │
    │       ├── status ──state transitions──► │
    │       ├── unavailableReason             │
    │       ├── stillBlob                     │
    │       └── stillPreviewUrl               │
    │                                         │
    ├── squareCrop(video, { mirror: false,    │
    │              targetSize: 1024 })        │
    │       └── CapturedFrame (Blob) ─────────┘ (via downscalePhoto, then onPhotoSelected)
    │
    └── cameraCapability() ─► initial empty-vs-unavailable decision
```

No cross-feature state is introduced. No new reducer actions. No new selectors. The data-model delta from 003 to 004 is exclusively the addition of the transient `CameraSession` and `CapturedFrame` types — both confined to `frontend/src/features/alterego/hooks/useCameraStream.ts` and `.../lib/squareCrop.ts`.

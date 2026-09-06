# Contracts — 004-camera-only-capture

**This feature introduces no contract changes.**

The HTTP API between frontend and backend (`alter-egos.openapi.yaml`, defined in 001 and extended by 003 with `vibe` and `meta.reason`) is untouched. No field is added, renamed, or removed. The OpenAPI version does not bump.

The feature's scope — rewriting the client-side photo-intake surface — terminates at the existing `onPhotoSelected(blob, url)` callback inside `PhotoIntake.tsx`. Everything downstream (`alterEgoClient.generate`, the multipart POST, the backend's `AlterEgoController`, the `PhotoReducer`, the Gemini call, the response shape) sees the same Blob and the same wire shape it saw before.

See `../data-model.md` for a full accounting of what *is* new (all client-side, in-memory only): `CameraSession`, `CameraUnavailableReason`, `CapturedFrame`.

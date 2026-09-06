/**
 * 004 FR-312 static capability check: does the current user agent expose
 * `navigator.mediaDevices.getUserMedia`? Returns a discriminated union
 * that the caller (useCameraStream) uses to branch between the normal
 * acquisition path and the degraded "capability_missing" surface.
 *
 * <p>MUST NOT invoke the permission prompt. Callers that need runtime
 * classification (permission_denied, no_camera_hardware,
 * stream_unavailable) must try `getUserMedia` and catch.
 */

export type CameraUnavailableReason =
  | 'permission_denied'
  | 'no_camera_hardware'
  | 'stream_unavailable'
  | 'capability_missing'

export type CameraCapability =
  | { supported: true }
  | { supported: false; reason: 'capability_missing' }

export function cameraCapability(): CameraCapability {
  if (typeof navigator === 'undefined') {
    return { supported: false, reason: 'capability_missing' }
  }
  const md = navigator.mediaDevices as { getUserMedia?: unknown } | undefined
  if (!md || typeof md.getUserMedia !== 'function') {
    return { supported: false, reason: 'capability_missing' }
  }
  return { supported: true }
}

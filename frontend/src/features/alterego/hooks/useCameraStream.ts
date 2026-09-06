import { useCallback, useEffect, useRef, useState } from 'react'
import { cameraCapability, type CameraUnavailableReason } from '../lib/cameraCapability'
import { squareCrop } from '../lib/squareCrop'

/**
 * 004 FR-301 / FR-301a / FR-301b / FR-305 / FR-312–314 — owns the
 * MediaStream lifecycle for the inline circle-as-viewfinder capture UX.
 *
 * <p>A single stream is acquired on start(), reused across any number
 * of shutter → Retake cycles per FR-301b, and stopped + released on
 * commit(), cancel(), unmount, visibility change to hidden, or pagehide.
 * Object URLs for still-preview Blobs are revoked on the same exits so
 * no photo bytes linger in memory past the session (FR-315).
 */

export type CameraStatus =
  | 'idle'
  | 'requesting'
  | 'live'
  | 'capturing'
  | 'still-preview'
  | 'releasing'
  | 'unavailable'

export interface UseCameraStreamResult {
  status: CameraStatus
  unavailableReason: CameraUnavailableReason | null
  stream: MediaStream | null
  stillPreviewUrl: string | null
  start: () => Promise<void>
  captureStill: (video: HTMLVideoElement) => Promise<void>
  retake: () => void
  commit: () => Promise<Blob>
  cancel: () => void
}

function classifyError(err: unknown): CameraUnavailableReason {
  if (err instanceof DOMException) {
    switch (err.name) {
      case 'NotAllowedError':
      case 'SecurityError':
        return 'permission_denied'
      case 'NotFoundError':
      case 'OverconstrainedError':
        return 'no_camera_hardware'
      case 'NotReadableError':
      case 'AbortError':
        return 'stream_unavailable'
    }
  }
  // TypeError fires when constraints are malformed; treat as unavailable-stream.
  if (err instanceof TypeError) return 'stream_unavailable'
  return 'stream_unavailable'
}

function stopStream(stream: MediaStream | null): void {
  if (!stream) return
  for (const track of stream.getTracks()) {
    try {
      track.stop()
    } catch {
      // ignore — releasing a track that's already stopped is a no-op
    }
  }
}

/**
 * Resolve once the `<video>` has non-zero dimensions (readyState ≥ 2).
 * Bounded by a short timeout so tests that never supply a stream don't
 * hang — the caller re-surfaces the timeout as a capture failure.
 */
function waitForVideoReady(video: HTMLVideoElement): Promise<void> {
  if (video.readyState >= 2 && video.videoWidth > 0 && video.videoHeight > 0) {
    return Promise.resolve()
  }
  return new Promise((resolve, reject) => {
    const timer = window.setTimeout(() => {
      cleanup()
      reject(new Error('Camera stream did not produce a frame in time'))
    }, 3000)
    const cleanup = () => {
      window.clearTimeout(timer)
      video.removeEventListener('loadeddata', onReady)
      video.removeEventListener('canplay', onReady)
      video.removeEventListener('playing', onReady)
    }
    const onReady = () => {
      if (video.videoWidth > 0 && video.videoHeight > 0) {
        cleanup()
        resolve()
      }
    }
    video.addEventListener('loadeddata', onReady)
    video.addEventListener('canplay', onReady)
    video.addEventListener('playing', onReady)
  })
}

export function useCameraStream(): UseCameraStreamResult {
  const [status, setStatus] = useState<CameraStatus>('idle')
  const [unavailableReason, setUnavailableReason] = useState<CameraUnavailableReason | null>(null)
  const [stream, setStream] = useState<MediaStream | null>(null)
  const [stillBlob, setStillBlob] = useState<Blob | null>(null)
  const [stillPreviewUrl, setStillPreviewUrl] = useState<string | null>(null)

  // Refs shadow the pieces of state that teardown handlers need to read
  // synchronously on unmount / visibilitychange — without them the
  // listener closures capture stale nulls.
  const streamRef = useRef<MediaStream | null>(null)
  const stillUrlRef = useRef<string | null>(null)

  useEffect(() => {
    streamRef.current = stream
  }, [stream])
  useEffect(() => {
    stillUrlRef.current = stillPreviewUrl
  }, [stillPreviewUrl])

  const releaseAll = useCallback(() => {
    stopStream(streamRef.current)
    streamRef.current = null
    if (stillUrlRef.current) {
      URL.revokeObjectURL(stillUrlRef.current)
      stillUrlRef.current = null
    }
    setStream(null)
    setStillBlob(null)
    setStillPreviewUrl(null)
  }, [])

  // Teardown on unmount, tab hide, and page unload — FR-315 / plan R2.
  useEffect(() => {
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') {
        releaseAll()
        setStatus('idle')
        setUnavailableReason(null)
      }
    }
    const onPageHide = () => {
      releaseAll()
    }
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('pagehide', onPageHide)
    return () => {
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('pagehide', onPageHide)
      releaseAll()
    }
  }, [releaseAll])

  const start = useCallback(async () => {
    const cap = cameraCapability()
    if (!cap.supported) {
      setStatus('unavailable')
      setUnavailableReason(cap.reason)
      return
    }
    setStatus('requesting')
    setUnavailableReason(null)
    try {
      const next = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user' },
        audio: false,
      })
      streamRef.current = next
      setStream(next)
      setStatus('live')
    } catch (err) {
      setStatus('unavailable')
      setUnavailableReason(classifyError(err))
    }
  }, [])

  const captureStill = useCallback(async (video: HTMLVideoElement) => {
    setStatus('capturing')
    try {
      // The <video> element needs at least HAVE_CURRENT_DATA (readyState 2)
      // to expose non-zero videoWidth/videoHeight. Users can press the
      // shutter before the stream finishes buffering its first frame; wait
      // briefly for readiness before handing off to squareCrop.
      await waitForVideoReady(video)
      const blob = await squareCrop(video, { mirror: false, targetSize: 1024 })
      const url = URL.createObjectURL(blob)
      // Revoke any prior still URL before overwriting (defensive; retake/commit
      // should have cleared it, but this keeps the invariant under unusual
      // orderings).
      if (stillUrlRef.current) URL.revokeObjectURL(stillUrlRef.current)
      stillUrlRef.current = url
      setStillBlob(blob)
      setStillPreviewUrl(url)
      setStatus('still-preview')
    } catch (err) {
      // Roll back to 'live' so the shutter re-enables and the user can
      // try again, or Cancel out — otherwise the UI gets stuck in
      // 'capturing' with the shutter disabled forever.
      setStatus('live')
      throw err
    }
  }, [])

  const retake = useCallback(() => {
    if (stillUrlRef.current) {
      URL.revokeObjectURL(stillUrlRef.current)
      stillUrlRef.current = null
    }
    setStillBlob(null)
    setStillPreviewUrl(null)
    // Stream stays open — reuse per FR-301b.
    setStatus('live')
  }, [])

  const commit = useCallback(async (): Promise<Blob> => {
    const blob = stillBlob
    if (!blob) throw new Error('useCameraStream.commit: no still captured')
    setStatus('releasing')
    stopStream(streamRef.current)
    streamRef.current = null
    if (stillUrlRef.current) {
      URL.revokeObjectURL(stillUrlRef.current)
      stillUrlRef.current = null
    }
    setStream(null)
    setStillBlob(null)
    setStillPreviewUrl(null)
    setStatus('idle')
    setUnavailableReason(null)
    return blob
  }, [stillBlob])

  const cancel = useCallback(() => {
    setStatus('releasing')
    releaseAll()
    setStatus('idle')
    setUnavailableReason(null)
  }, [releaseAll])

  return {
    status,
    unavailableReason,
    stream,
    stillPreviewUrl,
    start,
    captureStill,
    retake,
    commit,
    cancel,
  }
}

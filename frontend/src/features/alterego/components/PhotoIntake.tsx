import { useCallback, useEffect, useId, useRef, useState } from 'react'
import { Aperture, Camera, Check, RotateCcw, X } from 'lucide-react'
import { useLiveAnnouncer } from '../../../components/useLiveAnnouncer'
import { useCameraStream } from '../hooks/useCameraStream'
import { downscalePhoto } from '../lib/downscalePhoto'

/**
 * 004 camera-only photo intake — FR-301 / FR-301a / FR-301b / FR-302 /
 * FR-302a / FR-303 / FR-304 / FR-305 / FR-309–314.
 *
 * <p>The circle is the single interactive control: activating it opens
 * the device camera via {@link useCameraStream}, the live stream renders
 * inline inside the circle (un-mirrored — FR-302a was revised after user
 * testing so the viewfinder, still-preview, and saved image all share one
 * orientation), and a shutter → still-preview → Keep/Retake row appears
 * directly below. Keep commits the centre-cropped 1:1 still into the
 * existing {@link downscalePhoto} pipeline so everything downstream
 * (Generate request shape, backend reduction, Gemini call, poster
 * rendering) is unchanged from 003.
 *
 * <p>When the camera cannot be activated — permission denied, no
 * hardware, MediaDevices missing, unreadable stream — a single polite,
 * non-blocking message renders within the photo column; Generate
 * remains disabled until a photo is committed.
 */

interface Props {
  photoPreviewUrl: string | null
  onPhotoSelected: (blob: Blob, url: string) => void
  onPhotoCleared: () => void
  /** External error (e.g. backend rejection) surfaced alongside internal messages. */
  externalError?: string | null
}

const MAX_SIZE_BYTES = 5 * 1024 * 1024

function unavailableCopy(
  reason: 'permission_denied' | 'no_camera_hardware' | 'stream_unavailable' | 'capability_missing',
): string {
  // FR-313: generic, non-blaming copy; no raw DOMException strings. The
  // capability_missing variant is nuanced because the remedy is
  // substantively different (use a supported browser vs. grant permission).
  if (reason === 'capability_missing') {
    return "Your browser doesn't support in-app camera capture. Please open this page in a supported browser."
  }
  return 'Camera access is needed to take your photo. Please allow camera access in your browser and try again, or connect a camera if none is available.'
}

export function PhotoIntake({
  photoPreviewUrl,
  onPhotoSelected,
  onPhotoCleared,
  externalError,
}: Props) {
  const errorId = useId()
  const unavailableId = useId()
  const videoRef = useRef<HTMLVideoElement | null>(null)
  const rootRef = useRef<HTMLDivElement | null>(null)
  const [processing, setProcessing] = useState(false)
  const [processingError, setProcessingError] = useState<string | null>(null)
  const { announce } = useLiveAnnouncer()

  const camera = useCameraStream()

  // Bind the MediaStream to the <video> element whenever the hook
  // exposes a new stream. On cleanup (retake → new stream; commit →
  // null) we detach so the element stops holding a reference.
  useEffect(() => {
    const video = videoRef.current
    if (!video) return
    if (camera.stream) {
      if (video.srcObject !== camera.stream) {
        video.srcObject = camera.stream
      }
    } else if (video.srcObject) {
      video.srcObject = null
    }
  }, [camera.stream, camera.status])

  // Announce state transitions to assistive tech (FR-311).
  useEffect(() => {
    switch (camera.status) {
      case 'live':
        announce('Camera ready', 'polite')
        break
      case 'still-preview':
        announce('Photo captured. Press Keep photo or Retake.', 'polite')
        break
      case 'unavailable':
        if (camera.unavailableReason) {
          announce(unavailableCopy(camera.unavailableReason), 'polite')
        }
        break
      default:
        break
    }
  }, [camera.status, camera.unavailableReason, announce])

  const handleActivate = useCallback(() => {
    if (processing) return
    // If a committed photo exists, re-activating the circle starts a
    // fresh capture (FR-304). Clear the existing photo first so the
    // reducer reflects "no photo" while the user frames a new one.
    if (photoPreviewUrl) {
      onPhotoCleared()
    }
    setProcessingError(null)
    void camera.start()
  }, [camera, onPhotoCleared, photoPreviewUrl, processing])

  const handleShutter = useCallback(async () => {
    const video = videoRef.current
    if (!video) return
    try {
      await camera.captureStill(video)
    } catch {
      setProcessingError('Could not capture this frame. Please try again.')
    }
  }, [camera])

  const handleKeep = useCallback(async () => {
    setProcessing(true)
    setProcessingError(null)
    try {
      const raw = await camera.commit()
      const downscaled = await downscalePhoto(raw)
      if (downscaled.size > MAX_SIZE_BYTES) {
        setProcessingError('Photo is too large even after resizing. Please try again.')
        return
      }
      const url = URL.createObjectURL(downscaled)
      onPhotoSelected(downscaled, url)
      announce('Photo saved.', 'polite')
    } catch {
      setProcessingError('Could not process this photo.')
    } finally {
      setProcessing(false)
    }
  }, [camera, onPhotoSelected, announce])

  const handleRetake = useCallback(() => {
    camera.retake()
  }, [camera])

  const handleCancel = useCallback(() => {
    camera.cancel()
    setProcessingError(null)
  }, [camera])

  const handleClear = useCallback(() => {
    onPhotoCleared()
    setProcessingError(null)
  }, [onPhotoCleared])

  // Escape cancels the in-flight capture while focus is inside this
  // component's subtree (so we don't hijack global Escape). Listener is
  // installed only while capture is open — when the component returns
  // to idle/unavailable, there's nothing for Escape to cancel.
  useEffect(() => {
    const active =
      camera.status === 'requesting' ||
      camera.status === 'live' ||
      camera.status === 'capturing' ||
      camera.status === 'still-preview'
    if (!active) return
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return
      const root = rootRef.current
      if (!root) return
      if (!root.contains(document.activeElement)) return
      e.preventDefault()
      handleCancel()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
    }
  }, [camera.status, handleCancel])

  const isLive =
    camera.status === 'live' || camera.status === 'capturing' || camera.status === 'requesting'
  const isStill = camera.status === 'still-preview'
  const isUnavailable = camera.status === 'unavailable'

  const circleLabel = photoPreviewUrl ? 'Retake your photo' : 'Take a photo of yourself'
  const activeError = processingError ?? externalError ?? null

  const describedBy: string[] = []
  if (activeError) describedBy.push(errorId)
  if (isUnavailable) describedBy.push(unavailableId)

  return (
    <div className="photo-intake" ref={rootRef}>
      <button
        type="button"
        className="photo-intake__circle"
        data-status={camera.status}
        data-filled={photoPreviewUrl ? 'true' : 'false'}
        aria-label={circleLabel}
        aria-describedby={describedBy.length ? describedBy.join(' ') : undefined}
        onClick={handleActivate}
        disabled={processing || camera.status === 'capturing' || camera.status === 'releasing'}
      >
        {isStill && camera.stillPreviewUrl ? (
          <img src={camera.stillPreviewUrl} alt="" className="photo-intake__still" />
        ) : isLive ? (
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            aria-hidden="true"
            className="photo-intake__viewfinder"
          />
        ) : photoPreviewUrl ? (
          <img src={photoPreviewUrl} alt="" className="photo-intake__image" />
        ) : (
          <span className="photo-intake__placeholder" aria-hidden="true">
            <span className="photo-intake__placeholder-icon">
              <Camera width={36} height={36} strokeWidth={1.4} />
            </span>
            <span className="photo-intake__placeholder-text">Take photo</span>
          </span>
        )}
      </button>

      {isLive ? (
        <div className="photo-intake__actions" data-state="live">
          <button type="button" className="photo-intake__cancel" onClick={handleCancel}>
            <X width={14} height={14} strokeWidth={2} aria-hidden="true" />
            <span>Cancel</span>
          </button>
          <button
            type="button"
            className="photo-intake__shutter"
            onClick={handleShutter}
            disabled={camera.status !== 'live'}
          >
            <Aperture width={16} height={16} strokeWidth={2} aria-hidden="true" />
            <span>Take photo</span>
          </button>
        </div>
      ) : null}

      {isStill ? (
        <div className="photo-intake__actions" data-state="still">
          <button
            type="button"
            className="photo-intake__keep"
            onClick={handleKeep}
            disabled={processing}
          >
            <Check width={16} height={16} strokeWidth={2.25} aria-hidden="true" />
            <span>Keep photo</span>
          </button>
          <button
            type="button"
            className="photo-intake__retake"
            onClick={handleRetake}
            disabled={processing}
          >
            <RotateCcw width={14} height={14} strokeWidth={2} aria-hidden="true" />
            <span>Retake</span>
          </button>
        </div>
      ) : null}

      {photoPreviewUrl && !isLive && !isStill ? (
        <button type="button" className="photo-intake__clear" onClick={handleClear}>
          <RotateCcw width={12} height={12} strokeWidth={2} aria-hidden="true" />
          <span>Retake / clear</span>
        </button>
      ) : null}

      {processing ? (
        <p role="status" className="photo-intake__status">
          Processing photo…
        </p>
      ) : null}

      {isUnavailable && camera.unavailableReason ? (
        <p id={unavailableId} role="alert" className="photo-intake__unavailable">
          {unavailableCopy(camera.unavailableReason)}
        </p>
      ) : null}

      {activeError ? (
        <p id={errorId} role="alert" className="photo-intake__error">
          {activeError}
        </p>
      ) : null}
    </div>
  )
}

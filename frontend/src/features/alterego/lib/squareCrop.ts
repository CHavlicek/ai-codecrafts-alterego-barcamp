/**
 * 004 FR-303a — square-crop the largest centred 1:1 region of a video
 * frame (or canvas) and return it as a JPEG Blob.
 *
 * <p>The returned bytes are un-mirrored; mirroring for the live
 * viewfinder is applied at the CSS layer only (FR-302a), so the source
 * pixels this function reads are already the canonical orientation that
 * downstream consumers (downscalePhoto, Gemini) expect.
 *
 * <p>Never up-scales: `targetPx = min(side, targetSize)`, preserving
 * 003 FR-207's "do not re-encode unnecessarily" posture for
 * already-small camera inputs.
 */

export interface SquareCropOptions {
  /**
   * Visual mirror is a CSS concern (FR-302a); this flag is retained for
   * future callers that may want to invert the saved image. In this
   * feature it is always passed as `false` on the commit path.
   */
  mirror: boolean
  /** Default 1024 per FR-303a — matches Gemini reference-image conventions. */
  targetSize: number
}

type Source = HTMLVideoElement | HTMLCanvasElement

function sourceWidth(source: Source): number {
  if ('videoWidth' in source && source.videoWidth > 0) return source.videoWidth
  return source.width
}

function sourceHeight(source: Source): number {
  if ('videoHeight' in source && source.videoHeight > 0) return source.videoHeight
  return source.height
}

export async function squareCrop(source: Source, options: SquareCropOptions): Promise<Blob> {
  const w = sourceWidth(source)
  const h = sourceHeight(source)
  if (w <= 0 || h <= 0) {
    throw new Error('squareCrop: source has zero dimensions')
  }
  const side = Math.min(w, h)
  const sx = Math.floor((w - side) / 2)
  const sy = Math.floor((h - side) / 2)
  const targetPx = Math.min(side, options.targetSize)

  if (typeof OffscreenCanvas !== 'undefined') {
    const off = new OffscreenCanvas(targetPx, targetPx)
    const ctx = off.getContext('2d')
    if (!ctx) throw new Error('squareCrop: 2D context unavailable on OffscreenCanvas')
    if (options.mirror) {
      ctx.translate(targetPx, 0)
      ctx.scale(-1, 1)
    }
    ctx.drawImage(source, sx, sy, side, side, 0, 0, targetPx, targetPx)
    return off.convertToBlob({ type: 'image/jpeg', quality: 0.92 })
  }

  const canvas = document.createElement('canvas')
  canvas.width = targetPx
  canvas.height = targetPx
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('squareCrop: 2D context unavailable on HTMLCanvasElement')
  if (options.mirror) {
    ctx.translate(targetPx, 0)
    ctx.scale(-1, 1)
  }
  ctx.drawImage(source, sx, sy, side, side, 0, 0, targetPx, targetPx)
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error('squareCrop: canvas.toBlob returned null'))),
      'image/jpeg',
      0.92,
    )
  })
}

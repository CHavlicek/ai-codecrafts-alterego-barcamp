/**
 * Client-side photo downscale (research.md R3): resize a user-supplied
 * image down to fit within {@code maxWidth × maxHeight} preserving the
 * aspect ratio, then re-encode as JPEG (or PNG) at the configured quality.
 *
 * Keeps typical payloads ≤ 500 KB and removes incidental EXIF data —
 * smaller wire size + a privacy nice-to-have. Used by PhotoIntake (T076)
 * before handing the bytes to {@link ../services/alterEgoClient}.
 */

export interface DownscaleOptions {
  maxWidth?: number
  maxHeight?: number
  /** JPEG quality 0..1. Ignored for PNG output. */
  quality?: number
  outputType?: 'image/jpeg' | 'image/png'
}

const DEFAULTS = {
  maxWidth: 1024,
  maxHeight: 1024,
  quality: 0.85,
  outputType: 'image/jpeg' as const,
}

/**
 * Pure helper — given source dimensions and a max bounding box, returns
 * the largest fitting (width, height) preserving aspect ratio. Returns
 * the source dimensions unchanged when they're already within bounds.
 */
export function computeTargetSize(
  srcWidth: number,
  srcHeight: number,
  maxWidth: number,
  maxHeight: number,
): { width: number; height: number } {
  if (srcWidth <= maxWidth && srcHeight <= maxHeight) {
    return { width: srcWidth, height: srcHeight }
  }
  const ratio = Math.min(maxWidth / srcWidth, maxHeight / srcHeight)
  return {
    width: Math.max(1, Math.round(srcWidth * ratio)),
    height: Math.max(1, Math.round(srcHeight * ratio)),
  }
}

export async function downscalePhoto(blob: Blob, options: DownscaleOptions = {}): Promise<Blob> {
  const maxWidth = options.maxWidth ?? DEFAULTS.maxWidth
  const maxHeight = options.maxHeight ?? DEFAULTS.maxHeight
  const quality = options.quality ?? DEFAULTS.quality
  const outputType = options.outputType ?? DEFAULTS.outputType

  const bitmap = await createImageBitmap(blob)
  try {
    const { width, height } = computeTargetSize(bitmap.width, bitmap.height, maxWidth, maxHeight)
    return await renderBitmapToBlob(bitmap, width, height, outputType, quality)
  } finally {
    bitmap.close()
  }
}

async function renderBitmapToBlob(
  bitmap: ImageBitmap,
  width: number,
  height: number,
  outputType: 'image/jpeg' | 'image/png',
  quality: number,
): Promise<Blob> {
  if (typeof OffscreenCanvas !== 'undefined') {
    const canvas = new OffscreenCanvas(width, height)
    const ctx = canvas.getContext('2d')
    if (!ctx) throw new Error('Failed to acquire 2D context on OffscreenCanvas')
    ctx.drawImage(bitmap, 0, 0, width, height)
    return canvas.convertToBlob({ type: outputType, quality })
  }
  // Fallback path for environments without OffscreenCanvas (rare in modern browsers).
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) throw new Error('Failed to acquire 2D context on HTMLCanvasElement')
  ctx.drawImage(bitmap, 0, 0, width, height)
  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (b) => (b ? resolve(b) : reject(new Error('Canvas toBlob produced null'))),
      outputType,
      quality,
    )
  })
}

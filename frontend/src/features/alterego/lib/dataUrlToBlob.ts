/**
 * 023 (issue #57) — decode a `data:image/(png|jpeg);base64,…` URL into
 * a `Blob` (+ canonical media type string) suitable for appending to a
 * `multipart/form-data` body.
 *
 * Defensive: the FE only ever passes `session.result.poster.dataUrl`,
 * which the backend guarantees is `image/png` or `image/jpeg`. We
 * still validate the prefix so a regression in the response shape
 * surfaces here, not as an opaque server-side 415.
 */

type SupportedMediaType = 'image/png' | 'image/jpeg'

export interface DecodedDataUrl {
  blob: Blob
  mediaType: SupportedMediaType
}

const DATA_URL_PATTERN = /^data:image\/(png|jpeg);base64,([A-Za-z0-9+/=]*)$/

export function dataUrlToBlob(dataUrl: string): DecodedDataUrl {
  const match = DATA_URL_PATTERN.exec(dataUrl)
  if (!match || !match[1] || match[2] == null) {
    throw new Error('Expected a data:image/(png|jpeg);base64,... URL — got something else.')
  }
  const mediaType: SupportedMediaType = match[1] === 'png' ? 'image/png' : 'image/jpeg'
  const binary = atob(match[2])
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return { blob: new Blob([bytes], { type: mediaType }), mediaType }
}

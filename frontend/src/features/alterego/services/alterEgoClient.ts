/**
 * Wire layer for the alter-ego generation request. Builds the multipart
 * body, threads the correlation ID, and delegates retry + fallback to
 * {@link ../../../lib/resilientFetch} per Principle IV.
 *
 * Keeps no global state. Caller (the React hook) owns scheduling.
 */
import { resilientFetch } from '../../../lib/resilientFetch'
import { fallbackPoster } from '../fallback/fallbackPoster'
import type { AlterEgoResponse, Selections } from '../types'

export interface GenerateOptions {
  photoBlob: Blob
  selections: Selections
  correlationId: string
  signal?: AbortSignal
}

const ENDPOINT = '/api/v1/alter-egos'

export async function generateAlterEgo({
  photoBlob,
  selections,
  correlationId,
  signal,
}: GenerateOptions): Promise<AlterEgoResponse> {
  const form = buildMultipartBody(photoBlob, selections)

  const response = await resilientFetch(
    ENDPOINT,
    {
      method: 'POST',
      // Browser sets `Content-Type: multipart/form-data; boundary=…` automatically
      // for FormData bodies; explicit Content-Type would clobber the boundary.
      headers: { 'X-Request-Id': correlationId },
      body: form,
      ...(signal ? { signal } : {}),
    },
    {
      ...(signal ? { signal } : {}),
      fallback: () => synthesiseFallbackResponse(selections.firstName, correlationId),
    },
  )

  if (!response.ok) {
    // Non-retriable status (4xx) — surface a typed error so the caller can
    // dispatch GenerateFailedWithFallback with a readable message.
    let detail = `HTTP ${response.status}`
    try {
      const body = await response.text()
      if (body) detail = body
    } catch {
      // Body unreadable — fall back to the status string.
    }
    throw new Error(detail)
  }

  return (await response.json()) as AlterEgoResponse
}

function buildMultipartBody(photoBlob: Blob, selections: Selections): FormData {
  const form = new FormData()
  const filename = photoBlob.type === 'image/png' ? 'photo.png' : 'photo.jpg'
  form.append('photo', photoBlob, filename)
  form.append(
    'selections',
    new Blob([JSON.stringify(serialiseSelections(selections))], { type: 'application/json' }),
  )
  return form
}

/**
 * 022 (issue #50): wire-shape serialiser. Omits `archetype` when null
 * (the user supplied a custom role) and `customRole` when blank / absent
 * (the prefab is the role of record). The trimmed `customRole` is what
 * lands on the wire — the backend trusts the input is already trimmed.
 */
function serialiseSelections(selections: Selections): Record<string, unknown> {
  const trimmedCustom = selections.customRole?.trim() ?? ''
  const trimmedCustomUniverse = selections.customUniverse?.trim() ?? ''
  const wire: Record<string, unknown> = {
    artStyle: selections.artStyle,
    photoMode: selections.photoMode,
    firstName: selections.firstName,
  }
  if (selections.archetype != null) wire.archetype = selections.archetype
  if (trimmedCustom.length > 0) wire.customRole = trimmedCustom
  // 029: mirror the custom-Role wire shape for Universe. Omit `universe`
  // when null (custom supplied); include `customUniverse` only when non-blank.
  if (selections.universe != null) wire.universe = selections.universe
  if (trimmedCustomUniverse.length > 0) wire.customUniverse = trimmedCustomUniverse
  return wire
}

function synthesiseFallbackResponse(firstName: string, correlationId: string): Response {
  const body = fallbackPoster(firstName, correlationId)
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json', 'X-Request-Id': correlationId },
  })
}

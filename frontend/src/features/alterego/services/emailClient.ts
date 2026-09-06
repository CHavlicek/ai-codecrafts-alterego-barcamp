/**
 * 023 (issue #57) — wire layer for `POST /api/v1/alter-egos/email`.
 *
 * Builds the multipart body (`to`, `firstName`, `image`), threads the
 * correlation ID, and classifies the response into one of three
 * terminal outcomes: `sent` / `not_configured` / `failed`.
 *
 * <p>Retry posture (Principle IV with the smarts to recognise that the
 * typed 503 "not-configured" outcome is NOT a transient failure):
 *
 *   - 200 → `sent` (no retry)
 *   - 503 + problem-detail type ending in `/email/not-configured` →
 *     `not_configured` (terminal — retrying won't change the operator's
 *     config mid-flight)
 *   - any other non-2xx (including 502 / 5xx / 4xx / network throw) →
 *     retry with exponential backoff up to 5 attempts; final failure
 *     classifies as `failed`.
 *
 * <p>The single user-facing feedback channel is the alert primitive
 * fired by {@link useSendAlterEgoEmail} (clarification 2026-05-11 Q2).
 */
import { dataUrlToBlob } from '../lib/dataUrlToBlob'

export type SendOutcome = 'sent' | 'not_configured' | 'failed'

export interface SendOptions {
  to: string
  firstName: string
  posterDataUrl: string
  correlationId: string
  signal?: AbortSignal
  /**
   * Maximum number of fetch attempts; default 5 (Principle IV). Tests
   * override to 1 to keep retry-budget exhaustion sub-second.
   */
  attempts?: number
}

const ENDPOINT = '/api/v1/alter-egos/email'
const NOT_CONFIGURED_TYPE_SUFFIX = '/email/not-configured'

interface ProblemDetail {
  type?: string
  status?: number
  title?: string
  detail?: string
}

const MAX_ATTEMPTS = 5
const INITIAL_DELAY_MS = 200
const MAX_DELAY_MS = 5_000

export async function sendAlterEgoEmail({
  to,
  firstName,
  posterDataUrl,
  correlationId,
  signal,
  attempts: maxAttempts = MAX_ATTEMPTS,
}: SendOptions): Promise<{ outcome: SendOutcome }> {
  // Decode FIRST so an unsupported MIME surfaces as a clear local error
  // (test pin) — not as a backend 415 after a network round-trip.
  const { blob } = dataUrlToBlob(posterDataUrl)

  const buildForm = (): FormData => {
    const form = new FormData()
    form.append('to', to)
    form.append('firstName', firstName)
    form.append('image', blob, blob.type === 'image/jpeg' ? 'poster.jpg' : 'poster.png')
    return form
  }

  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    let response: Response
    try {
      response = await fetch(ENDPOINT, {
        method: 'POST',
        headers: { 'X-Request-Id': correlationId },
        body: buildForm(),
        ...(signal ? { signal } : {}),
      })
    } catch {
      // Network-level throw — eligible for retry until the budget is gone.
      if (await advanceOrGiveUp(attempt, maxAttempts, signal)) continue
      return { outcome: 'failed' }
    }

    if (response.status === 200) {
      return { outcome: 'sent' }
    }

    if (response.status === 503) {
      const problem = await safeReadProblemDetail(response)
      if (problem?.type?.endsWith(NOT_CONFIGURED_TYPE_SUFFIX)) {
        // Terminal — operator config won't change mid-flight.
        return { outcome: 'not_configured' }
      }
      // Untyped 503 (load-balancer maintenance, etc.) — treat as transient.
      if (await advanceOrGiveUp(attempt, maxAttempts, signal)) continue
      return { outcome: 'failed' }
    }

    // 4xx or 5xx other than 503: treat 4xx as terminal, 5xx as transient.
    if (response.status >= 400 && response.status < 500) {
      return { outcome: 'failed' }
    }
    if (await advanceOrGiveUp(attempt, maxAttempts, signal)) continue
    return { outcome: 'failed' }
  }

  return { outcome: 'failed' }
}

async function advanceOrGiveUp(
  attempt: number,
  maxAttempts: number,
  signal?: AbortSignal,
): Promise<boolean> {
  if (attempt >= maxAttempts - 1) return false
  const exp = INITIAL_DELAY_MS * Math.pow(2, attempt)
  const capped = Math.min(exp, MAX_DELAY_MS)
  const jitter = (Math.random() - 0.5) * capped // ±50% jitter
  const delay = Math.max(0, capped + jitter)
  await sleep(delay, signal)
  return true
}

function sleep(ms: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Aborted', 'AbortError'))
      return
    }
    const timer = setTimeout(resolve, ms)
    signal?.addEventListener(
      'abort',
      () => {
        clearTimeout(timer)
        reject(new DOMException('Aborted', 'AbortError'))
      },
      { once: true },
    )
  })
}

async function safeReadProblemDetail(response: Response): Promise<ProblemDetail | null> {
  try {
    const text = await response.text()
    if (!text) return null
    return JSON.parse(text) as ProblemDetail
  } catch {
    return null
  }
}

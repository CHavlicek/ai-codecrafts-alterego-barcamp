/**
 * Resilient `fetch` wrapper implementing constitutional Principle IV:
 * 5 attempts, exponential back-off, randomised jitter, optional fallback
 * on final failure.
 *
 * Owns retry policy. TanStack Query mutations should NOT also retry —
 * configure them with `retry: false` and let this client own the loop.
 */

export interface ResilientFetchOptions {
  /** Maximum number of attempts (default 5). 1 means no retries. */
  attempts?: number
  /** Initial back-off in ms (default 200). */
  initialDelayMs?: number
  /** Cap for any single back-off in ms (default 5000). */
  maxDelayMs?: number
  /** Exponential growth factor (default 2). */
  multiplier?: number
  /** Symmetric jitter as a fraction of the computed delay (default 0.5). */
  jitterFactor?: number
  /**
   * Custom retry predicate. Return false to stop retrying immediately.
   * Defaults to retrying on network errors and 408/429/5xx responses.
   */
  shouldRetry?: (error: unknown, attempt: number) => boolean
  /**
   * Final-failure fallback. Invoked after all attempts are exhausted; its
   * `Response` becomes the resolved value of `resilientFetch`. Use this to
   * return a canned `200` body so the UI always has something to render.
   */
  fallback?: () => Response | Promise<Response>
  /** Abort signal forwarded to `fetch` and to internal back-off sleeps. */
  signal?: AbortSignal
}

interface ResolvedOptions {
  attempts: number
  initialDelayMs: number
  maxDelayMs: number
  multiplier: number
  jitterFactor: number
}

const DEFAULTS: ResolvedOptions = {
  attempts: 5,
  initialDelayMs: 200,
  maxDelayMs: 5_000,
  multiplier: 2,
  jitterFactor: 0.5,
}

const isRetriableStatus = (status: number): boolean =>
  status >= 500 || status === 408 || status === 429

const isAbortError = (err: unknown): err is DOMException =>
  err instanceof DOMException && err.name === 'AbortError'

const sleep = (ms: number, signal?: AbortSignal): Promise<void> =>
  new Promise((resolve, reject) => {
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

const computeBackoff = (attempt: number, opts: ResolvedOptions): number => {
  const exp = opts.initialDelayMs * Math.pow(opts.multiplier, attempt)
  const capped = Math.min(exp, opts.maxDelayMs)
  // Symmetric jitter in the range ±(jitterFactor * capped).
  const jitterRange = capped * opts.jitterFactor
  const jitter = (Math.random() - 0.5) * 2 * jitterRange
  return Math.max(0, capped + jitter)
}

export async function resilientFetch(
  input: RequestInfo | URL,
  init: RequestInit = {},
  options: ResilientFetchOptions = {},
): Promise<Response> {
  const opts: ResolvedOptions = {
    attempts: options.attempts ?? DEFAULTS.attempts,
    initialDelayMs: options.initialDelayMs ?? DEFAULTS.initialDelayMs,
    maxDelayMs: options.maxDelayMs ?? DEFAULTS.maxDelayMs,
    multiplier: options.multiplier ?? DEFAULTS.multiplier,
    jitterFactor: options.jitterFactor ?? DEFAULTS.jitterFactor,
  }
  const signal = options.signal ?? init.signal ?? undefined
  let lastError: unknown

  for (let attempt = 0; attempt < opts.attempts; attempt++) {
    try {
      const response = await fetch(input, signal ? { ...init, signal } : init)

      if (response.ok) return response

      if (!isRetriableStatus(response.status)) {
        // Non-retriable client / server failure — return as-is so the caller
        // can surface a typed error / problem-detail body to the user.
        return response
      }

      // Retriable; throw so the catch path runs.
      lastError = new Error(`HTTP ${response.status}`)
      throw lastError
    } catch (err) {
      lastError = err
      if (isAbortError(err)) throw err

      const isLastAttempt = attempt === opts.attempts - 1
      if (isLastAttempt) break

      const customRetry = options.shouldRetry?.(err, attempt)
      if (customRetry === false) break

      await sleep(computeBackoff(attempt, opts), signal)
    }
  }

  if (options.fallback) {
    return options.fallback()
  }

  throw lastError instanceof Error ? lastError : new Error(String(lastError))
}

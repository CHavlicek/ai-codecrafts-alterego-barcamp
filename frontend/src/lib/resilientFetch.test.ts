import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { resilientFetch } from './resilientFetch'

/**
 * T041 — resilientFetch contract (Principle IV):
 *  - 5 attempts with exponential back-off + ±50% jitter.
 *  - Resolves on first 2xx.
 *  - Non-retriable status (4xx other than 408/429) returns immediately.
 *  - Retriable status (5xx, 408, 429) → retries until exhausted, then
 *    invokes the {@code fallback} hook (or throws if no fallback).
 *  - AbortSignal aborts mid-flight.
 *  - shouldRetry predicate can short-circuit the loop.
 */

describe('resilientFetch', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  function ok(body = '{}'): Response {
    return new Response(body, { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  function status(code: number, body = ''): Response {
    return new Response(body, { status: code })
  }

  test('returns the response on first successful attempt', async () => {
    fetchMock.mockResolvedValueOnce(ok('{"a":1}'))
    const r = await resilientFetch('/x', {}, { initialDelayMs: 1, maxDelayMs: 1 })
    expect(r.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  test('passes non-retriable 4xx through to the caller without retry', async () => {
    fetchMock.mockResolvedValue(status(400, '{"err":"bad"}'))
    const r = await resilientFetch('/x', {}, { initialDelayMs: 1, maxDelayMs: 1 })
    expect(r.status).toBe(400)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  test('retries on 500 then resolves on a later 200', async () => {
    fetchMock
      .mockResolvedValueOnce(status(500))
      .mockResolvedValueOnce(status(503))
      .mockResolvedValueOnce(ok())
    const r = await resilientFetch(
      '/x',
      {},
      {
        attempts: 5,
        initialDelayMs: 1,
        maxDelayMs: 1,
        jitterFactor: 0,
      },
    )
    expect(r.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  test('retries on 429 (rate limit)', async () => {
    fetchMock.mockResolvedValueOnce(status(429)).mockResolvedValueOnce(ok())
    const r = await resilientFetch('/x', {}, { initialDelayMs: 1, maxDelayMs: 1, jitterFactor: 0 })
    expect(r.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  test('exhausts attempts and invokes fallback when all fail', async () => {
    fetchMock.mockResolvedValue(status(500))
    const fallbackResponse = new Response('"fallback"', { status: 200 })
    const fallback = vi.fn(() => fallbackResponse)

    const r = await resilientFetch(
      '/x',
      {},
      { attempts: 3, initialDelayMs: 1, maxDelayMs: 1, jitterFactor: 0, fallback },
    )

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(fallback).toHaveBeenCalledTimes(1)
    expect(await r.text()).toBe('"fallback"')
  })

  test('throws when attempts exhausted and no fallback configured', async () => {
    fetchMock.mockResolvedValue(status(500))
    await expect(
      resilientFetch(
        '/x',
        {},
        {
          attempts: 2,
          initialDelayMs: 1,
          maxDelayMs: 1,
          jitterFactor: 0,
        },
      ),
    ).rejects.toThrow(/HTTP 500/)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  test('shouldRetry returning false stops the loop early', async () => {
    fetchMock.mockResolvedValue(status(500))
    const fallback = vi.fn(() => new Response('"fb"', { status: 200 }))
    const shouldRetry = vi.fn(() => false)

    await resilientFetch(
      '/x',
      {},
      {
        attempts: 5,
        initialDelayMs: 1,
        maxDelayMs: 1,
        jitterFactor: 0,
        shouldRetry,
        fallback,
      },
    )

    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(shouldRetry).toHaveBeenCalledTimes(1)
    expect(fallback).toHaveBeenCalledTimes(1)
  })

  test('AbortError propagates immediately and bypasses fallback', async () => {
    const ctrl = new AbortController()
    fetchMock.mockImplementation(() => Promise.reject(new DOMException('Aborted', 'AbortError')))
    const fallback = vi.fn(() => new Response())

    await expect(
      resilientFetch('/x', { signal: ctrl.signal }, { initialDelayMs: 1, maxDelayMs: 1, fallback }),
    ).rejects.toMatchObject({ name: 'AbortError' })
    expect(fallback).not.toHaveBeenCalled()
  })

  test('aborting during a back-off sleep rejects with AbortError', async () => {
    // First attempt returns a retriable 500 — resilientFetch enters sleep.
    // We abort the signal ~20 ms into that sleep; the listener wired up inside
    // sleep() clears the timer and rejects with AbortError.
    fetchMock.mockResolvedValueOnce(status(500))
    const ctrl = new AbortController()
    const promise = resilientFetch(
      '/x',
      { signal: ctrl.signal },
      {
        attempts: 3,
        initialDelayMs: 200,
        maxDelayMs: 200,
        jitterFactor: 0,
      },
    )
    setTimeout(() => ctrl.abort(), 20)
    await expect(promise).rejects.toMatchObject({ name: 'AbortError' })
  })

  test('retries on a thrown network error (not a Response failure)', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Network down')).mockResolvedValueOnce(ok())
    const r = await resilientFetch(
      '/x',
      {},
      {
        attempts: 3,
        initialDelayMs: 1,
        maxDelayMs: 1,
        jitterFactor: 0,
      },
    )
    expect(r.status).toBe(200)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})

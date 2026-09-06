import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { sendAlterEgoEmail } from './emailClient'

/**
 * 023 (issue #57) — emailClient.sendAlterEgoEmail contract.
 *
 * Exercises: multipart body shape (`to`, `firstName`, `image` parts);
 * `X-Request-Id` correlation header; outcome classification against
 * the three terminal HTTP responses defined in
 * contracts/alter-egos-email.openapi.yaml.
 */

function pngDataUrl(): string {
  // tiny valid base64 (4 bytes "fake")
  return 'data:image/png;base64,ZmFrZQ=='
}

function jpegDataUrl(): string {
  return 'data:image/jpeg;base64,ZmFrZQ=='
}

function okSentResponse(): Response {
  return new Response(JSON.stringify({ status: 'sent' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function notConfiguredResponse(): Response {
  const body = {
    type: 'https://aiavatar.local/problems/email/not-configured',
    title: 'Email service is not configured',
    status: 503,
    detail: 'The mail server is not configured. Contact the operator to enable email delivery.',
  }
  return new Response(JSON.stringify(body), {
    status: 503,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function sendFailedResponse(): Response {
  const body = {
    type: 'https://aiavatar.local/problems/email/send-failed',
    title: 'Email send failed',
    status: 502,
  }
  return new Response(JSON.stringify(body), {
    status: 502,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

describe('emailClient.sendAlterEgoEmail (023)', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  test('posts multipart with `to`, `firstName`, and `image` parts and forwards X-Request-Id', async () => {
    fetchMock.mockResolvedValueOnce(okSentResponse())

    const result = await sendAlterEgoEmail({
      to: 'someone@example.com',
      firstName: 'Dmytro',
      posterDataUrl: pngDataUrl(),
      correlationId: 'corr-123',
    })

    expect(result).toEqual({ outcome: 'sent' })
    expect(fetchMock).toHaveBeenCalledTimes(1)

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/v1/alter-egos/email')
    expect(init.method).toBe('POST')

    // The headers should include X-Request-Id and MUST NOT explicitly set
    // Content-Type (the browser fills the multipart boundary).
    const headers = init.headers as Record<string, string>
    expect(headers['X-Request-Id']).toBe('corr-123')
    expect(Object.keys(headers).map((k) => k.toLowerCase())).not.toContain('content-type')

    // Inspect the FormData body for the three parts.
    const form = init.body as FormData
    expect(form).toBeInstanceOf(FormData)
    expect(form.get('to')).toBe('someone@example.com')
    expect(form.get('firstName')).toBe('Dmytro')
    const image = form.get('image') as Blob
    expect(image).toBeInstanceOf(Blob)
    expect(image.type).toBe('image/png')
  })

  test('uses image/jpeg for jpeg data URLs', async () => {
    fetchMock.mockResolvedValueOnce(okSentResponse())

    await sendAlterEgoEmail({
      to: 'someone@example.com',
      firstName: 'Dmytro',
      posterDataUrl: jpegDataUrl(),
      correlationId: 'corr-456',
    })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const form = init.body as FormData
    const image = form.get('image') as Blob
    expect(image.type).toBe('image/jpeg')
  })

  test('classifies 200 + {status:"sent"} as outcome "sent"', async () => {
    fetchMock.mockResolvedValueOnce(okSentResponse())
    const out = await sendAlterEgoEmail({
      to: 'a@b.c',
      firstName: 'D',
      posterDataUrl: pngDataUrl(),
      correlationId: 'c',
    })
    expect(out).toEqual({ outcome: 'sent' })
  })

  test('classifies 503 + email/not-configured problem-detail type as outcome "not_configured"', async () => {
    fetchMock.mockResolvedValueOnce(notConfiguredResponse())
    const out = await sendAlterEgoEmail({
      to: 'a@b.c',
      firstName: 'D',
      posterDataUrl: pngDataUrl(),
      correlationId: 'c',
    })
    expect(out).toEqual({ outcome: 'not_configured' })
  })

  test('classifies 502 problem-detail as outcome "failed"', async () => {
    fetchMock.mockResolvedValue(sendFailedResponse())
    const out = await sendAlterEgoEmail({
      to: 'a@b.c',
      firstName: 'D',
      posterDataUrl: pngDataUrl(),
      correlationId: 'c',
      attempts: 1, // skip retry budget so the test runs sub-second
    })
    expect(out).toEqual({ outcome: 'failed' })
  })

  test('classifies generic 4xx as outcome "failed"', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 400 }))
    const out = await sendAlterEgoEmail({
      to: 'a@b.c',
      firstName: 'D',
      posterDataUrl: pngDataUrl(),
      correlationId: 'c',
    })
    expect(out).toEqual({ outcome: 'failed' })
  })

  test('classifies a 503 with a NON-matching problem-detail type as outcome "failed" (not "not_configured")', async () => {
    const body = {
      type: 'https://aiavatar.local/problems/email/other',
      title: 'Other',
      status: 503,
    }
    fetchMock.mockResolvedValue(
      new Response(JSON.stringify(body), {
        status: 503,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )
    const out = await sendAlterEgoEmail({
      to: 'a@b.c',
      firstName: 'D',
      posterDataUrl: pngDataUrl(),
      correlationId: 'c',
      attempts: 1, // untyped 503 is "transient" — skip retry budget here
    })
    expect(out).toEqual({ outcome: 'failed' })
  })

  test('thrown fetch (network error) is classified as "failed"', async () => {
    fetchMock.mockRejectedValue(new TypeError('network down'))
    const out = await sendAlterEgoEmail({
      to: 'a@b.c',
      firstName: 'D',
      posterDataUrl: pngDataUrl(),
      correlationId: 'c',
      attempts: 1, // skip retry budget for fast feedback
    })
    expect(out).toEqual({ outcome: 'failed' })
  })

  test('rejects an unsupported data URL with a clear error', async () => {
    await expect(
      sendAlterEgoEmail({
        to: 'a@b.c',
        firstName: 'D',
        posterDataUrl: 'data:image/gif;base64,ZmFrZQ==',
        correlationId: 'c',
      }),
    ).rejects.toThrow(/png|jpeg/i)
  })
})

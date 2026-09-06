import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { renderHook, waitFor, act } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useSendAlterEgoEmail } from './useSendAlterEgoEmail'
import type { SendOutcome } from '../services/emailClient'

/**
 * 023 (issue #57) — useSendAlterEgoEmail wires
 * {@code sendAlterEgoEmail} into a TanStack Query mutation. On every
 * terminal outcome (sent / not_configured / failed) it fires a single
 * `window.alert(...)` with the spec-pinned message.
 */

const sendAlterEgoEmailMock =
  vi.fn<
    (args: {
      to: string
      firstName: string
      posterDataUrl: string
      correlationId: string
    }) => Promise<{ outcome: SendOutcome }>
  >()

vi.mock('../services/emailClient', () => ({
  sendAlterEgoEmail: (args: {
    to: string
    firstName: string
    posterDataUrl: string
    correlationId: string
  }) => sendAlterEgoEmailMock(args),
}))

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: {
      mutations: { retry: false },
      queries: { retry: false },
    },
  })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

const baseArgs = {
  to: 'someone@example.com',
  firstName: 'Dmytro',
  posterDataUrl: 'data:image/png;base64,ZmFrZQ==',
  correlationId: 'corr-1',
}

describe('useSendAlterEgoEmail (023)', () => {
  let alertSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    sendAlterEgoEmailMock.mockReset()
    alertSpy = vi.spyOn(window, 'alert').mockImplementation(() => {})
  })

  afterEach(() => {
    alertSpy.mockRestore()
  })

  test('outcome "sent" → alert reports the recipient address', async () => {
    sendAlterEgoEmailMock.mockResolvedValue({ outcome: 'sent' })
    const { result } = renderHook(() => useSendAlterEgoEmail(), { wrapper })

    await act(async () => {
      result.current.send(baseArgs)
    })
    await waitFor(() => expect(alertSpy).toHaveBeenCalledTimes(1))

    expect(alertSpy).toHaveBeenCalledWith('Email sent to someone@example.com.')
  })

  test('outcome "not_configured" → "not yet configured" alert', async () => {
    sendAlterEgoEmailMock.mockResolvedValue({ outcome: 'not_configured' })
    const { result } = renderHook(() => useSendAlterEgoEmail(), { wrapper })

    await act(async () => {
      result.current.send(baseArgs)
    })
    await waitFor(() => expect(alertSpy).toHaveBeenCalledTimes(1))

    expect(alertSpy).toHaveBeenCalledWith('The email server is not yet configured.')
  })

  test('outcome "failed" → "please try again" alert', async () => {
    sendAlterEgoEmailMock.mockResolvedValue({ outcome: 'failed' })
    const { result } = renderHook(() => useSendAlterEgoEmail(), { wrapper })

    await act(async () => {
      result.current.send(baseArgs)
    })
    await waitFor(() => expect(alertSpy).toHaveBeenCalledTimes(1))

    expect(alertSpy).toHaveBeenCalledWith('Sending failed. Please try again.')
  })

  test('thrown mutation (network throw passes through) → "failed" alert', async () => {
    sendAlterEgoEmailMock.mockRejectedValue(new Error('network down'))
    const { result } = renderHook(() => useSendAlterEgoEmail(), { wrapper })

    await act(async () => {
      result.current.send(baseArgs)
    })
    await waitFor(() => expect(alertSpy).toHaveBeenCalledTimes(1))

    expect(alertSpy).toHaveBeenCalledWith('Sending failed. Please try again.')
  })

  test('isPending flips around the mutation lifecycle', async () => {
    let resolver: (value: { outcome: SendOutcome }) => void = () => {}
    sendAlterEgoEmailMock.mockReturnValue(
      new Promise((res) => {
        resolver = res
      }),
    )
    const { result } = renderHook(() => useSendAlterEgoEmail(), { wrapper })

    expect(result.current.isPending).toBe(false)

    act(() => {
      result.current.send(baseArgs)
    })
    await waitFor(() => expect(result.current.isPending).toBe(true))

    act(() => {
      resolver({ outcome: 'sent' })
    })
    await waitFor(() => expect(result.current.isPending).toBe(false))
  })

  test('the to value is passed through to the client unchanged', async () => {
    sendAlterEgoEmailMock.mockResolvedValue({ outcome: 'sent' })
    const { result } = renderHook(() => useSendAlterEgoEmail(), { wrapper })

    await act(async () => {
      result.current.send({ ...baseArgs, to: 'paula@example.org' })
    })
    await waitFor(() => expect(sendAlterEgoEmailMock).toHaveBeenCalledTimes(1))

    expect(sendAlterEgoEmailMock).toHaveBeenCalledWith(
      expect.objectContaining({ to: 'paula@example.org' }),
    )
  })

  test('repeat invocations fire repeat alerts (button is not single-use — FR-2317)', async () => {
    sendAlterEgoEmailMock.mockResolvedValue({ outcome: 'sent' })
    const { result } = renderHook(() => useSendAlterEgoEmail(), { wrapper })

    await act(async () => {
      result.current.send(baseArgs)
    })
    await waitFor(() => expect(alertSpy).toHaveBeenCalledTimes(1))

    await act(async () => {
      result.current.send(baseArgs)
    })
    await waitFor(() => expect(alertSpy).toHaveBeenCalledTimes(2))
  })
})

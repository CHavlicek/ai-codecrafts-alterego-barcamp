import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { SendAsEmailButton } from './SendAsEmailButton'

const sendMock = vi.fn()

vi.mock('../hooks/useSendAlterEgoEmail', () => ({
  useSendAlterEgoEmail: () => ({ send: sendMock, isPending: false }),
}))

function wrap(node: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
  })
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>)
}

/**
 * 023 (issue #57) — SendAsEmailButton contract.
 *
 * Disabled (aria-disabled, click no-op) when the captured email is
 * blank or invalid (clarification Q1). Enabled when the email passes
 * the validator AND a posterDataUrl is supplied. Click fires the
 * mutation with `to` (trimmed), `firstName`, `posterDataUrl`,
 * `correlationId` (a UUID).
 */
describe('SendAsEmailButton (023)', () => {
  beforeEach(() => {
    sendMock.mockReset()
  })
  afterEach(() => {
    vi.useRealTimers()
  })

  test('disabled (aria-disabled + disabled) when email is blank', async () => {
    wrap(
      <SendAsEmailButton
        email=""
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const button = screen.getByRole('button', { name: /send/i })
    expect(button).toHaveAttribute('aria-disabled', 'true')
    expect(button).toBeDisabled()
    const user = userEvent.setup()
    await user.click(button)
    expect(sendMock).not.toHaveBeenCalled()
  })

  test('disabled when email is whitespace-only', async () => {
    wrap(
      <SendAsEmailButton
        email={'   \t  '}
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const button = screen.getByRole('button', { name: /send/i })
    expect(button).toBeDisabled()
  })

  test('disabled when email is non-blank but malformed', async () => {
    wrap(
      <SendAsEmailButton
        email="not-an-email"
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const button = screen.getByRole('button', { name: /send/i })
    expect(button).toBeDisabled()
  })

  test('disabled state exposes an accessible explanation via aria-label / title', () => {
    wrap(
      <SendAsEmailButton
        email=""
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const button = screen.getByRole('button', { name: /send/i })
    // The aria-label should name the missing prerequisite OR the title
    // attribute should — either is acceptable per FR-2315.
    const aria = button.getAttribute('aria-label') ?? ''
    const title = button.getAttribute('title') ?? ''
    expect(aria + title).toMatch(/email/i)
  })

  test('enabled when email is valid AND posterDataUrl is present', () => {
    wrap(
      <SendAsEmailButton
        email="someone@example.com"
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const button = screen.getByRole('button', { name: /send/i })
    expect(button).not.toBeDisabled()
    expect(button).not.toHaveAttribute('aria-disabled', 'true')
  })

  // ---------- 025 (issue #60): additive optional props for prop-lifting
  // `useSendAlterEgoEmail` up to AlterEgoPanel so the inline email
  // fallback can share the same `isPending` value (research R4). The
  // existing 023 callsite passes neither prop and continues to read
  // from the mocked hook above (which the previous tests assert).

  test('025 — when isPending prop is true, the button is disabled regardless of email validity', () => {
    wrap(
      <SendAsEmailButton
        email="someone@example.com"
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
        isPending={true}
      />,
    )
    const button = screen.getByRole('button', { name: /send/i })
    expect(button).toBeDisabled()
  })

  test('025 — when send prop is provided, click invokes the injected send, not the hook', async () => {
    const injectedSend = vi.fn()
    wrap(
      <SendAsEmailButton
        email="someone@example.com"
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
        send={injectedSend}
      />,
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /send/i }))
    expect(injectedSend).toHaveBeenCalledTimes(1)
    expect(sendMock).not.toHaveBeenCalled()
  })

  test('025 — default-props behaviour unchanged: no props → hook is the source of send + isPending', async () => {
    // No `isPending` / `send` props supplied — the previous 023 tests
    // already verify this path. This explicit case pins the
    // backwards-compatibility contract: AlterEgoPanel + 023 callsites
    // share the same component without behavioural drift.
    wrap(
      <SendAsEmailButton
        email="someone@example.com"
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /send/i }))
    expect(sendMock).toHaveBeenCalledTimes(1)
  })

  test('025 — disabled-state hint when blank email and the inline field is right above ("Enter a valid email above…")', () => {
    wrap(
      <SendAsEmailButton
        email=""
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const button = screen.getByRole('button', { name: /send/i })
    const aria = button.getAttribute('aria-label') ?? ''
    const title = button.getAttribute('title') ?? ''
    // UI contract C2: the hint must point to the inline field above —
    // the 023 wording "on the Setup tab" is no longer correct here
    // since the Alter Ego tab now offers an inline recovery field.
    expect(aria + title).toMatch(/above/i)
  })

  test('click fires the mutation with trimmed email + firstName + posterDataUrl + a UUID correlationId', async () => {
    // Stub crypto.randomUUID for determinism.
    const origRandomUUID = (globalThis.crypto as Crypto | undefined)?.randomUUID
    Object.defineProperty(globalThis.crypto, 'randomUUID', {
      configurable: true,
      value: () =>
        '00000000-0000-0000-0000-000000000023' as `${string}-${string}-${string}-${string}-${string}`,
    })

    wrap(
      <SendAsEmailButton
        email="  someone@example.com  "
        firstName="Dmytro"
        posterDataUrl="data:image/png;base64,ZmFrZQ=="
      />,
    )
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: /send/i }))

    expect(sendMock).toHaveBeenCalledTimes(1)
    expect(sendMock).toHaveBeenCalledWith({
      to: 'someone@example.com',
      firstName: 'Dmytro',
      posterDataUrl: 'data:image/png;base64,ZmFrZQ==',
      correlationId: '00000000-0000-0000-0000-000000000023',
    })

    if (origRandomUUID) {
      Object.defineProperty(globalThis.crypto, 'randomUUID', {
        configurable: true,
        value: origRandomUUID,
      })
    }
  })
})

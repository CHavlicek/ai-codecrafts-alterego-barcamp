import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { InlineEmailFallback } from './InlineEmailFallback'

/**
 * 025 (issue #60) — InlineEmailFallback component contract.
 *
 * Thin wrapper around {@code EmailInput} rendered on the Alter Ego tab
 * above the actions row, only when {@code !isSendableEmail(session)}.
 * Re-uses the canonical {@code EmailInput} contract (label, type, X
 * button, aria-invalid, role="alert"); this test verifies that the
 * wrapper does not break the contract and forwards the props as-is.
 */
describe('InlineEmailFallback (025)', () => {
  test('renders an empty type="email" input with accessible name "Email"', () => {
    render(<InlineEmailFallback value="" onChange={() => {}} />)
    const input = screen.getByLabelText('Email') as HTMLInputElement
    expect(input.type).toBe('email')
    expect(input.value).toBe('')
  })

  test('renders pre-filled with the provided value (US3 — correct in place)', () => {
    render(<InlineEmailFallback value="not-an-email" onChange={() => {}} />)
    const input = screen.getByLabelText('Email') as HTMLInputElement
    expect(input.value).toBe('not-an-email')
  })

  test('typing a character fires onChange with the raw event-target value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<InlineEmailFallback value="" onChange={onChange} />)
    await user.type(screen.getByLabelText('Email'), 'a')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  test('clear-X button is absent when value is empty', () => {
    render(<InlineEmailFallback value="" onChange={() => {}} />)
    expect(screen.queryByRole('button', { name: /clear email/i })).not.toBeInTheDocument()
  })

  test('clear-X button is present when value is non-empty and fires onChange("")', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<InlineEmailFallback value="someone@example.com" onChange={onChange} />)
    const clear = screen.getByRole('button', { name: /clear email/i })
    expect(clear).toBeInTheDocument()
    await user.click(clear)
    expect(onChange).toHaveBeenCalledWith('')
  })

  test('disabled propagates to both the input and the clear-X button', () => {
    render(<InlineEmailFallback value="someone@example.com" onChange={() => {}} disabled />)
    const input = screen.getByLabelText('Email') as HTMLInputElement
    const clear = screen.getByRole('button', { name: /clear email/i })
    expect(input).toBeDisabled()
    expect(clear).toBeDisabled()
  })

  test('non-blank invalid value renders role="alert" with the canonical error text (FR-2507)', () => {
    render(<InlineEmailFallback value="not-an-email" onChange={() => {}} />)
    const alert = screen.getByRole('alert')
    expect(alert).toBeInTheDocument()
    expect(alert.textContent).toMatch(/valid email/i)
  })

  test('blank value never renders the inline error', () => {
    render(<InlineEmailFallback value="" onChange={() => {}} />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('the wrapper exposes the .alter-ego-panel__email-fallback class for spacing (R5)', () => {
    const { container } = render(<InlineEmailFallback value="" onChange={() => {}} />)
    expect(container.querySelector('.alter-ego-panel__email-fallback')).not.toBeNull()
  })
})

import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { EmailInput } from './EmailInput'

/**
 * 023 (issue #57) — EmailInput component contract.
 *
 * Mirrors the CustomRoleInput contract (022): controlled value,
 * trailing clear-X visible only when value is non-empty, inline
 * error rendered when the non-blank value fails validation.
 * `disabled` propagates to both the input and the clear-X button.
 */
describe('EmailInput (023)', () => {
  test('renders an empty type="email" input + label and HIDES the X when value is empty', () => {
    render(<EmailInput value="" onChange={() => {}} />)
    const input = screen.getByLabelText('Email') as HTMLInputElement
    expect(input.value).toBe('')
    expect(input.type).toBe('email')
    expect(screen.queryByRole('button', { name: /clear email/i })).not.toBeInTheDocument()
  })

  test('typing a character fires onChange with the raw value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<EmailInput value="" onChange={onChange} />)
    await user.type(screen.getByLabelText('Email'), 'a')
    expect(onChange).toHaveBeenCalledWith('a')
  })

  test('shows the X button when value is non-empty and clicking it fires onChange("")', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<EmailInput value="someone@example.com" onChange={onChange} />)
    const clear = screen.getByRole('button', { name: /clear email/i })
    expect(clear).toBeInTheDocument()
    await user.click(clear)
    expect(onChange).toHaveBeenCalledWith('')
  })

  test('X button is keyboard-operable: Tab focusable and Space activates', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<EmailInput value="someone@example.com" onChange={onChange} />)
    const clear = screen.getByRole('button', { name: /clear email/i })
    clear.focus()
    expect(clear).toHaveFocus()
    await user.keyboard(' ')
    expect(onChange).toHaveBeenCalledWith('')
  })

  test('maxLength={254} is applied (FR-2301 + research R7)', () => {
    render(<EmailInput value="" onChange={() => {}} />)
    const input = screen.getByLabelText('Email') as HTMLInputElement
    expect(input.maxLength).toBe(254)
  })

  test('autocomplete + inputMode are set to email-friendly values', () => {
    render(<EmailInput value="" onChange={() => {}} />)
    const input = screen.getByLabelText('Email') as HTMLInputElement
    expect(input.getAttribute('autocomplete')).toBe('email')
    expect(input.getAttribute('inputmode')).toBe('email')
  })

  test('disabled propagates to both the input and the X button', () => {
    render(<EmailInput value="someone@example.com" onChange={() => {}} disabled />)
    const input = screen.getByLabelText('Email') as HTMLInputElement
    const clear = screen.getByRole('button', { name: /clear email/i })
    expect(input).toBeDisabled()
    expect(clear).toBeDisabled()
  })

  test('blank value never shows an inline error (FR-2303 — blank is valid)', () => {
    render(<EmailInput value="" onChange={() => {}} />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('whitespace-only value never shows an inline error (blank-equivalent)', () => {
    // Pass the string as a JS expression so escape sequences (\t) are
    // interpreted as whitespace — JSX attribute literals would treat
    // `"   \t  "` as a literal backslash + t.
    render(<EmailInput value={'   \t  '} onChange={() => {}} />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('malformed non-blank value shows an inline error with role="alert" (FR-2305)', () => {
    render(<EmailInput value="not-an-email" onChange={() => {}} />)
    const alert = screen.getByRole('alert')
    expect(alert).toBeInTheDocument()
    expect(alert.textContent).toMatch(/valid email/i)
  })

  test('correcting the malformed value removes the inline error', async () => {
    const { rerender } = render(<EmailInput value="not-an-email" onChange={() => {}} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    rerender(<EmailInput value="someone@example.com" onChange={() => {}} />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  test('clearing the field removes the inline error', () => {
    const { rerender } = render(<EmailInput value="not-an-email" onChange={() => {}} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    rerender(<EmailInput value="" onChange={() => {}} />)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

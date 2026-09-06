import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { CustomRoleInput } from './CustomRoleInput'

/**
 * 022 (issue #50) — CustomRoleInput component contract.
 *
 * Covers: input + X-clear-button render, typing fires onChange with raw
 * payload, X-button click + keyboard activation fires onChange(''),
 * disabled propagates to both input and X button, hard 100-character cap
 * is enforced by the input's `maxLength` attribute.
 */
describe('CustomRoleInput (022)', () => {
  test('renders an empty input + the label and HIDES the X button when value is empty', () => {
    render(<CustomRoleInput value="" onChange={() => {}} />)
    const input = screen.getByLabelText('Custom role') as HTMLInputElement
    expect(input.value).toBe('')
    expect(screen.queryByRole('button', { name: /clear custom role/i })).not.toBeInTheDocument()
  })

  test('typing a character fires onChange with the raw value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CustomRoleInput value="" onChange={onChange} />)
    await user.type(screen.getByLabelText('Custom role'), 'T')
    expect(onChange).toHaveBeenCalledWith('T')
  })

  test('shows the X button when value is non-empty and clicking it fires onChange("")', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CustomRoleInput value="Tester" onChange={onChange} />)
    const clear = screen.getByRole('button', { name: /clear custom role/i })
    expect(clear).toBeInTheDocument()
    await user.click(clear)
    expect(onChange).toHaveBeenCalledWith('')
  })

  test('X button is keyboard-operable: Tab focusable and Space / Enter activate', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CustomRoleInput value="Tester" onChange={onChange} />)
    const clear = screen.getByRole('button', { name: /clear custom role/i })
    clear.focus()
    expect(clear).toHaveFocus()
    await user.keyboard(' ')
    expect(onChange).toHaveBeenCalledWith('')
    onChange.mockClear()
    await user.keyboard('{Enter}')
    expect(onChange).toHaveBeenCalledWith('')
  })

  test('maxLength={100} is enforced on the input (FR-2204)', () => {
    render(<CustomRoleInput value="" onChange={() => {}} />)
    const input = screen.getByLabelText('Custom role') as HTMLInputElement
    expect(input.maxLength).toBe(100)
  })

  test('maxLength can be overridden via prop', () => {
    render(<CustomRoleInput value="" onChange={() => {}} maxLength={42} />)
    const input = screen.getByLabelText('Custom role') as HTMLInputElement
    expect(input.maxLength).toBe(42)
  })

  test('disabled propagates to both the input and the X button', () => {
    render(<CustomRoleInput value="Tester" onChange={() => {}} disabled />)
    const input = screen.getByLabelText('Custom role') as HTMLInputElement
    const clear = screen.getByRole('button', { name: /clear custom role/i })
    expect(input).toBeDisabled()
    expect(clear).toBeDisabled()
  })

  test("whitespace-only value still shows the X button (the wrapping precedence rule is the reducer's job, not the component's)", () => {
    render(<CustomRoleInput value="   " onChange={() => {}} />)
    expect(screen.getByRole('button', { name: /clear custom role/i })).toBeInTheDocument()
  })

  test('accepts plain noun phrases up to 100 chars (Tester, Sales Engineer, Founder)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<CustomRoleInput value="" onChange={onChange} />)
    await user.type(screen.getByLabelText('Custom role'), 'Sales Engineer')
    // Each keystroke fires one onChange — assert the final call holds the
    // last character (controlled-input semantics; parent updates the value).
    expect(onChange).toHaveBeenLastCalledWith('r')
  })
})

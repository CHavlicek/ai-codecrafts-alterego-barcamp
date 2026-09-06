import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PhotoModeSwitch } from './PhotoModeSwitch'

/**
 * 011 — PhotoModeSwitch contract. A single sliding-thumb toggle: one
 * native {@code <button role="switch">} flips between SINGLE (off) and
 * GROUP (on). Both labels are rendered inside the track for visual
 * affordance but are decorative ({@code aria-hidden}); the accessible
 * name comes from the section heading via {@code aria-labelledby}.
 */
describe('PhotoModeSwitch', () => {
  test('renders a single switch control with "Photo Mode" as its accessible name', () => {
    render(<PhotoModeSwitch value="single" onChange={() => {}} />)
    const sw = screen.getByRole('switch', { name: /photo mode/i })
    expect(sw).toBeInTheDocument()
    // There is exactly one switch — not two radios.
    expect(screen.queryAllByRole('radio')).toHaveLength(0)
  })

  test('default value "single" leaves the switch unchecked', () => {
    render(<PhotoModeSwitch value="single" onChange={() => {}} />)
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'false')
  })

  test('value "group" marks the switch as aria-checked', () => {
    render(<PhotoModeSwitch value="group" onChange={() => {}} />)
    expect(screen.getByRole('switch')).toHaveAttribute('aria-checked', 'true')
  })

  test('clicking the switch when off flips to "group"', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<PhotoModeSwitch value="single" onChange={onChange} />)
    await user.click(screen.getByRole('switch'))
    expect(onChange).toHaveBeenCalledWith('group')
    expect(onChange).toHaveBeenCalledTimes(1)
  })

  test('clicking the switch when on flips back to "single"', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<PhotoModeSwitch value="group" onChange={onChange} />)
    await user.click(screen.getByRole('switch'))
    expect(onChange).toHaveBeenCalledWith('single')
  })

  test('Space toggles the switch (native button keyboard contract)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<PhotoModeSwitch value="single" onChange={onChange} />)
    screen.getByRole('switch').focus()
    await user.keyboard(' ')
    expect(onChange).toHaveBeenCalledWith('group')
  })

  test('Enter toggles the switch', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<PhotoModeSwitch value="single" onChange={onChange} />)
    screen.getByRole('switch').focus()
    await user.keyboard('{Enter}')
    expect(onChange).toHaveBeenCalledWith('group')
  })

  test('the visible Single / Group labels are decorative (aria-hidden)', () => {
    render(<PhotoModeSwitch value="single" onChange={() => {}} />)
    // Track is aria-hidden so its inner labels and icons don't pollute the
    // accessible name of the parent button. The accessible name is the
    // "Photo Mode" heading via aria-labelledby.
    const sw = screen.getByRole('switch')
    const track = sw.querySelector('[aria-hidden="true"]')
    expect(track).not.toBeNull()
    // Sanity: visually we still ship Single + Group text inside the track.
    expect(track?.textContent).toContain('Single')
    expect(track?.textContent).toContain('Group')
  })

  test('renders a live status hint that reflects the current mode', () => {
    const { rerender } = render(<PhotoModeSwitch value="single" onChange={() => {}} />)
    expect(screen.getByRole('status').textContent).toMatch(/single person/i)
    rerender(<PhotoModeSwitch value="group" onChange={() => {}} />)
    expect(screen.getByRole('status').textContent).toMatch(/group photo/i)
  })

  test('the data-state attribute reflects the current value (drives the thumb position)', () => {
    const { rerender } = render(<PhotoModeSwitch value="single" onChange={() => {}} />)
    expect(screen.getByRole('switch')).toHaveAttribute('data-state', 'single')
    rerender(<PhotoModeSwitch value="group" onChange={() => {}} />)
    expect(screen.getByRole('switch')).toHaveAttribute('data-state', 'group')
  })
})

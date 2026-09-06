import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { PrintButton } from './PrintButton'
import * as printPosterImageModule from '../lib/printPosterImage'

/**
 * PrintButton — 010 FR-902 / FR-913 / FR-914.
 *
 * <p>One responsibility: on user activation, delegate to
 * {@link printPosterImage}. The detailed iframe-print behaviour lives
 * in {@code printPosterImage.ts} and is exercised in its own test +
 * the e2e suite; here we only assert the button surface and the
 * delegation contract.
 */

const SAMPLE_DATA_URL = 'data:image/png;base64,AAAA'

describe('PrintButton', () => {
  let printSpy: ReturnType<typeof vi.spyOn>

  beforeEach(() => {
    printSpy = vi.spyOn(printPosterImageModule, 'printPosterImage').mockImplementation(() => {})
  })

  afterEach(() => {
    printSpy.mockRestore()
  })

  test('renders a button with visible text "Print" and accessible name "Print my alter ego" (FR-913)', () => {
    render(<PrintButton posterDataUrl={SAMPLE_DATA_URL} />)
    const btn = screen.getByRole('button', { name: /print my alter ego/i })
    expect(btn).toBeInTheDocument()
    expect(btn).toHaveTextContent(/^\s*Print\s*$/)
    expect(btn).toHaveAttribute('type', 'button')
  })

  test('click invokes printPosterImage exactly once with the supplied dataUrl (FR-902)', async () => {
    const user = userEvent.setup()
    render(<PrintButton posterDataUrl={SAMPLE_DATA_URL} />)
    await user.click(screen.getByRole('button', { name: /print my alter ego/i }))
    expect(printSpy).toHaveBeenCalledTimes(1)
    expect(printSpy).toHaveBeenCalledWith(SAMPLE_DATA_URL)
  })

  test('Enter activation invokes printPosterImage exactly once (FR-913 / SC-906)', async () => {
    const user = userEvent.setup()
    render(<PrintButton posterDataUrl={SAMPLE_DATA_URL} />)
    const btn = screen.getByRole('button', { name: /print my alter ego/i })
    btn.focus()
    await user.keyboard('{Enter}')
    expect(printSpy).toHaveBeenCalledTimes(1)
  })

  test('Space activation invokes printPosterImage exactly once (FR-913 / SC-906)', async () => {
    const user = userEvent.setup()
    render(<PrintButton posterDataUrl={SAMPLE_DATA_URL} />)
    const btn = screen.getByRole('button', { name: /print my alter ego/i })
    btn.focus()
    await user.keyboard(' ')
    expect(printSpy).toHaveBeenCalledTimes(1)
  })

  test('repeated presses delegate once per press and render output stays byte-identical (FR-914 / SC-903)', async () => {
    const user = userEvent.setup()
    const { container } = render(<PrintButton posterDataUrl={SAMPLE_DATA_URL} />)
    const before = container.innerHTML

    const btn = screen.getByRole('button', { name: /print my alter ego/i })
    await user.click(btn)
    await user.click(btn)
    await user.click(btn)

    expect(printSpy).toHaveBeenCalledTimes(3)
    expect(container.innerHTML).toBe(before)
  })
})

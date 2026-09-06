import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { StartOverButton } from './StartOverButton'

/** T040 — StartOverButton revokes the preview URL before dispatching reset. */
describe('StartOverButton', () => {
  let revokeSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    revokeSpy = vi.fn()
    vi.stubGlobal('URL', {
      createObjectURL: vi.fn(),
      revokeObjectURL: revokeSpy,
    } as unknown as typeof URL)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  test('click revokes the blob URL and fires onStartOver in that order', async () => {
    const onStartOver = vi.fn()
    const user = userEvent.setup()
    render(<StartOverButton photoPreviewUrl="blob:fake-url" onStartOver={onStartOver} />)

    await user.click(screen.getByRole('button', { name: /start over/i }))

    expect(revokeSpy).toHaveBeenCalledWith('blob:fake-url')
    expect(onStartOver).toHaveBeenCalledTimes(1)
    expect(revokeSpy.mock.invocationCallOrder[0]).toBeLessThan(
      onStartOver.mock.invocationCallOrder[0]!,
    )
  })

  test('does not call revokeObjectURL when there is no preview URL', async () => {
    const onStartOver = vi.fn()
    const user = userEvent.setup()
    render(<StartOverButton photoPreviewUrl={null} onStartOver={onStartOver} />)

    await user.click(screen.getByRole('button', { name: /start over/i }))
    expect(revokeSpy).not.toHaveBeenCalled()
    expect(onStartOver).toHaveBeenCalledTimes(1)
  })
})

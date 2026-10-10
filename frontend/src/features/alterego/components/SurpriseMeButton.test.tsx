import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { SurpriseMeButton } from './SurpriseMeButton'
import { initialAlterEgoSession, type AlterEgoSession } from '../state/reducer'

/**
 * 009 T015 — SurpriseMeButton. Covers FR-901..FR-903 + FR-912.
 * Mirrors GenerateButton's test surface; photo + name are the only two
 * required inputs, so every category combination leaves the button
 * enabled.
 */

function session(overrides: Partial<AlterEgoSession> = {}): AlterEgoSession {
  return { ...initialAlterEgoSession(), ...overrides }
}

function samplePhoto(): Blob {
  return new Blob([new Uint8Array([1])], { type: 'image/jpeg' })
}

function readySession(): AlterEgoSession {
  return session({ photoBlob: samplePhoto(), firstName: 'Paula' })
}

describe('SurpriseMeButton', () => {
  test('renders a button with the accessible name "Surprise Me"', () => {
    render(
      <SurpriseMeButton
        session={initialAlterEgoSession()}
        isSubmitting={false}
        onSurprise={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: /surprise me/i })).toBeInTheDocument()
  })

  test('is disabled and names BOTH photo and first name as missing on a fresh session', () => {
    render(
      <SurpriseMeButton
        session={initialAlterEgoSession()}
        isSubmitting={false}
        onSurprise={() => {}}
      />,
    )
    const button = screen.getByRole('button', { name: /surprise me/i })
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-disabled', 'true')

    const hint = screen.getByRole('status')
    expect(hint).toHaveTextContent(/photo/i)
    expect(hint).toHaveTextContent(/first name/i)
  })

  test('hint only flags the ACTUALLY missing inputs — not category grids (FR-903)', () => {
    // Session has photo + name but no category picks. Surprise Me is
    // supposed to fill those in itself, so the hint MUST NOT mention
    // pose / role / universe / art style / vibe.
    render(<SurpriseMeButton session={readySession()} isSubmitting={false} onSurprise={() => {}} />)
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  test('is enabled when photo + name are present and phase is not generating', () => {
    render(<SurpriseMeButton session={readySession()} isSubmitting={false} onSurprise={() => {}} />)
    expect(screen.getByRole('button', { name: /surprise me/i })).not.toBeDisabled()
  })

  test('category-only selections do NOT block the button (enabled as long as photo + name)', () => {
    // Partial category picks should still leave Surprise Me enabled —
    // clicking will simply overwrite them.
    render(
      <SurpriseMeButton
        session={session({
          photoBlob: samplePhoto(),
          firstName: 'Paula',
          archetype: 'software-developer',
          artStyle: 'oil-painting',
        })}
        isSubmitting={false}
        onSurprise={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: /surprise me/i })).not.toBeDisabled()
  })

  test('lists photo only when name is present but photo is missing', () => {
    render(
      <SurpriseMeButton
        session={session({ firstName: 'Paula' })}
        isSubmitting={false}
        onSurprise={() => {}}
      />,
    )
    const hint = screen.getByRole('status')
    expect(hint).toHaveTextContent(/photo/i)
    expect(hint).not.toHaveTextContent(/first name/i)
  })

  test('lists first name only when photo is present but name is blank', () => {
    render(
      <SurpriseMeButton
        session={session({ photoBlob: samplePhoto() })}
        isSubmitting={false}
        onSurprise={() => {}}
      />,
    )
    const hint = screen.getByRole('status')
    expect(hint).toHaveTextContent(/first name/i)
    expect(hint).not.toHaveTextContent(/^still needed: photo\./i)
  })

  test('disabled + "Generating…" status shown while isSubmitting', () => {
    render(<SurpriseMeButton session={readySession()} isSubmitting onSurprise={() => {}} />)
    expect(screen.getByRole('button', { name: /surprise me/i })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent(/generating/i)
  })

  test('click fires onSurprise once when enabled', async () => {
    const onSurprise = vi.fn()
    const user = userEvent.setup()
    render(
      <SurpriseMeButton session={readySession()} isSubmitting={false} onSurprise={onSurprise} />,
    )
    await user.click(screen.getByRole('button', { name: /surprise me/i }))
    expect(onSurprise).toHaveBeenCalledTimes(1)
  })

  test('click is a no-op when disabled (FR-903)', async () => {
    const onSurprise = vi.fn()
    const user = userEvent.setup()
    render(
      <SurpriseMeButton
        session={initialAlterEgoSession()}
        isSubmitting={false}
        onSurprise={onSurprise}
      />,
    )
    await user.click(screen.getByRole('button', { name: /surprise me/i }))
    expect(onSurprise).not.toHaveBeenCalled()
  })

  test('keyboard: Enter activates when enabled (FR-912)', async () => {
    const onSurprise = vi.fn()
    const user = userEvent.setup()
    render(
      <SurpriseMeButton session={readySession()} isSubmitting={false} onSurprise={onSurprise} />,
    )
    const button = screen.getByRole('button', { name: /surprise me/i })
    button.focus()
    await user.keyboard('{Enter}')
    expect(onSurprise).toHaveBeenCalledTimes(1)
  })

  test('keyboard: Space activates when enabled (FR-912)', async () => {
    const onSurprise = vi.fn()
    const user = userEvent.setup()
    render(
      <SurpriseMeButton session={readySession()} isSubmitting={false} onSurprise={onSurprise} />,
    )
    const button = screen.getByRole('button', { name: /surprise me/i })
    button.focus()
    await user.keyboard(' ')
    expect(onSurprise).toHaveBeenCalledTimes(1)
  })

  test('aria-describedby points at the hint node when the button is disabled', () => {
    render(
      <SurpriseMeButton
        session={initialAlterEgoSession()}
        isSubmitting={false}
        onSurprise={() => {}}
      />,
    )
    const button = screen.getByRole('button', { name: /surprise me/i })
    const hint = screen.getByRole('status')
    expect(button).toHaveAttribute('aria-describedby', hint.id)
  })

  test('011 — in group mode the missing-input hint reads "group name" instead of "first name"', () => {
    render(
      <SurpriseMeButton
        session={session({ photoMode: 'group' })}
        isSubmitting={false}
        onSurprise={() => {}}
      />,
    )
    const hint = screen.getByRole('status')
    expect(hint).toHaveTextContent(/group name/i)
    expect(hint).not.toHaveTextContent(/first name/i)
  })
})

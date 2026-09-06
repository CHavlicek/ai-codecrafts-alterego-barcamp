import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { GenerateButton } from './GenerateButton'
import { initialAlterEgoSession, type AlterEgoSession } from '../state/reducer'

/** T037 — GenerateButton gating (FR-009) + missing-input hint (FR-010). */

function session(overrides: Partial<AlterEgoSession> = {}): AlterEgoSession {
  return { ...initialAlterEgoSession(), ...overrides }
}

function fullSession(): AlterEgoSession {
  // 020 — pose and vibe were dropped from session state; the server rolls
  // them per request. Closes issue #51.
  return session({
    photoBlob: new Blob([new Uint8Array([1])], { type: 'image/jpeg' }),
    archetype: 'software-developer',
    universe: 'star-wars',
    artStyle: 'oil-painting',
    firstName: 'Paula',
  })
}

describe('GenerateButton', () => {
  test('button is disabled and lists all missing inputs on a fresh session', () => {
    render(
      <GenerateButton
        session={initialAlterEgoSession()}
        isSubmitting={false}
        onSubmit={() => {}}
      />,
    )
    expect(screen.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
    const hint = screen.getByRole('status')
    // 020 — pose no longer appears in the missing-input hint.
    expect(hint).toHaveTextContent(/photo/i)
    expect(hint).toHaveTextContent(/role/i)
    expect(hint).toHaveTextContent(/universe/i)
    expect(hint).toHaveTextContent(/first name/i)
    expect(hint).not.toHaveTextContent(/pose/i)
    expect(hint).not.toHaveTextContent(/vibe/i)
  })

  test('button is enabled when every required input is present', () => {
    render(<GenerateButton session={fullSession()} isSubmitting={false} onSubmit={() => {}} />)
    expect(screen.getByRole('button', { name: /generate my alter ego/i })).not.toBeDisabled()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  test('button disabled + "Generating…" status shown while isSubmitting', () => {
    render(<GenerateButton session={fullSession()} isSubmitting onSubmit={() => {}} />)
    expect(screen.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent(/generating/i)
  })

  test('click fires onSubmit when enabled', async () => {
    const onSubmit = vi.fn()
    const user = userEvent.setup()
    render(<GenerateButton session={fullSession()} isSubmitting={false} onSubmit={onSubmit} />)
    await user.click(screen.getByRole('button', { name: /generate my alter ego/i }))
    expect(onSubmit).toHaveBeenCalledTimes(1)
  })

  test('button is programmatically described by the hint via aria-describedby', () => {
    render(
      <GenerateButton
        session={initialAlterEgoSession()}
        isSubmitting={false}
        onSubmit={() => {}}
      />,
    )
    const button = screen.getByRole('button', { name: /generate my alter ego/i })
    const hint = screen.getByRole('status')
    expect(button).toHaveAttribute('aria-describedby', hint.id)
  })

  test('011 — in group mode the missing-input hint reads "group name" instead of "first name"', () => {
    render(
      <GenerateButton
        session={session({ photoMode: 'group' })}
        isSubmitting={false}
        onSubmit={() => {}}
      />,
    )
    const hint = screen.getByRole('status')
    expect(hint).toHaveTextContent(/group name/i)
    expect(hint).not.toHaveTextContent(/first name/i)
  })
})

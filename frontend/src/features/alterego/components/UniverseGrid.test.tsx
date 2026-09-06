import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UniverseGrid } from './UniverseGrid'

/**
 * UniverseGrid. 002 expands from 4 to 6 options and replaces the 001 set
 * with the mockup values (spec FR-117).
 */
describe('UniverseGrid', () => {
  test('renders all 6 universes in mockup order', () => {
    render(<UniverseGrid value={null} onChange={() => {}} />)
    for (const label of [
      'Marvel',
      'Star Wars',
      '80s Retro / Synthwave',
      '90s Sitcom',
      'Spy Thriller',
      'Ghostbusters',
    ]) {
      expect(screen.getByRole('radio', { name: label })).toBeInTheDocument()
    }
  })

  test('click fires onChange with the wire value', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<UniverseGrid value={null} onChange={onChange} />)
    await user.click(screen.getByRole('radio', { name: 'Star Wars' }))
    expect(onChange).toHaveBeenCalledWith('star-wars')
  })
})

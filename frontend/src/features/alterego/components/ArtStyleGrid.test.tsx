import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ArtStyleGrid } from './ArtStyleGrid'

/**
 * ArtStyleGrid (006 FR-301 / FR-302 / FR-303). Six mutually-exclusive art
 * styles (019 delta: `pixel-art`, `low-poly-3d`, `line-art` retired —
 * specs/019-remove-art-styles, closes #49), single-select with replace
 * semantics (no deselect toggle — art style is required for Generate per
 * FR-304). Same keyboard and ARIA behaviour as the other four category
 * grids via the {@code SelectionGrid} primitive.
 */
describe('ArtStyleGrid', () => {
  test('renders all six surviving art style labels as radio options (019 FR-1901)', () => {
    render(<ArtStyleGrid value={null} onChange={() => {}} />)
    for (const label of [
      'Oil Painting',
      'Watercolor',
      'Pop Art',
      'Renaissance Portrait',
      'Japanese Woodblock',
      'Cel-Shaded',
    ]) {
      expect(screen.getByRole('radio', { name: label })).toBeInTheDocument()
    }
    // Exactly six tiles — no extras.
    expect(screen.getAllByRole('radio')).toHaveLength(6)
  })

  test('019: retired tiles (Pixel Art, Low-Poly 3D, Line Art) are not in the grid', () => {
    render(<ArtStyleGrid value={null} onChange={() => {}} />)
    expect(screen.queryByRole('radio', { name: 'Pixel Art' })).toBeNull()
    expect(screen.queryByRole('radio', { name: 'Low-Poly 3D' })).toBeNull()
    expect(screen.queryByRole('radio', { name: 'Line Art' })).toBeNull()
  })

  test('click fires onChange with the kebab-case wire value (FR-305)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ArtStyleGrid value={null} onChange={onChange} />)
    await user.click(screen.getByRole('radio', { name: 'Pop Art' }))
    expect(onChange).toHaveBeenCalledWith('pop-art')
  })

  test('Lucide icon next to each option is aria-hidden so the accessible name is the label alone (FR-302)', () => {
    render(<ArtStyleGrid value={null} onChange={() => {}} />)
    const radio = screen.getByRole('radio', { name: 'Japanese Woodblock' })
    const icon = radio.querySelector('.selection-grid__icon')
    expect(icon).not.toBeNull()
    expect(icon?.getAttribute('aria-hidden')).toBe('true')
    // Accessible name equals the label only — the icon is not part of it.
    expect(radio.getAttribute('aria-label')).toBe('Japanese Woodblock')
  })

  test('selected option exposes aria-checked=true (WAI-ARIA radio-group)', () => {
    render(<ArtStyleGrid value="pop-art" onChange={() => {}} />)
    expect(screen.getByRole('radio', { name: 'Pop Art' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radio', { name: 'Oil Painting' })).toHaveAttribute(
      'aria-checked',
      'false',
    )
  })

  test('ArrowDown / ArrowRight advance the selection (keyboard parity, 006 US3)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ArtStyleGrid value="oil-painting" onChange={onChange} />)
    screen.getByRole('radio', { name: 'Oil Painting' }).focus()
    await user.keyboard('{ArrowDown}')
    expect(onChange).toHaveBeenCalledWith('watercolor')
    await user.keyboard('{ArrowRight}')
    // After ArrowDown, focus moved to "Watercolor"; ArrowRight advances once
    // more — 019 trimmed three tiles, so the next survivor is `pop-art`.
    expect(onChange).toHaveBeenCalledWith('pop-art')
  })

  test('ArrowLeft / ArrowUp retreat and wrap at the start', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ArtStyleGrid value="oil-painting" onChange={onChange} />)
    screen.getByRole('radio', { name: 'Oil Painting' }).focus()
    await user.keyboard('{ArrowLeft}')
    // Wraps around to the last option ("Cel-Shaded").
    expect(onChange).toHaveBeenCalledWith('cel-shaded')
  })

  test('Enter on the active option does not deselect when the group is required (no toggle — FR-303)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ArtStyleGrid value="pop-art" onChange={onChange} />)
    screen.getByRole('radio', { name: 'Pop Art' }).focus()
    await user.keyboard('{Enter}')
    // SelectionGrid's click handler short-circuits when the already-selected
    // option is clicked and allowDeselect is false — so onChange MUST NOT fire.
    expect(onChange).not.toHaveBeenCalled()
  })

  test('legend label reads "Art style"', () => {
    render(<ArtStyleGrid value={null} onChange={() => {}} />)
    expect(screen.getByText('Art style')).toBeInTheDocument()
  })
})

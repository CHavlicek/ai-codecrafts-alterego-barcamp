import { describe, expect, test, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ArchetypeGrid } from './ArchetypeGrid'

/**
 * ArchetypeGrid. Nine prefab Role options after 022 (issue #50) — the
 * original six engineering roles plus HR / Administration / Customer
 * Relations. The UI label was renamed from "Engineer role" to "Role" with
 * 022 because the three new options are not engineering roles.
 */
describe('ArchetypeGrid', () => {
  test('renders all 9 prefab options in stable order (022)', () => {
    render(<ArchetypeGrid value={null} onChange={() => {}} />)
    const expectedOrder = [
      'Software Developer',
      'Project Manager',
      'Data Analyst',
      'Marketing Specialist',
      'Sales & Customer Relations',
      'People & Culture',
      'Operations Manager',
      'Finance Controller',
      'Sustainability Lead',
    ]
    for (const label of expectedOrder) {
      expect(screen.getByRole('radio', { name: label })).toBeInTheDocument()
    }
    // Assert document order — Surprise Me and accessibility tests rely on
    // a stable enumeration.
    const radios = screen.getAllByRole('radio')
    expect(radios.map((r) => r.getAttribute('aria-label'))).toEqual(expectedOrder)
  })

  test('022 — visible legend reads "Role" (not "Engineer role")', () => {
    render(<ArchetypeGrid value={null} onChange={() => {}} />)
    expect(screen.getByText('Role')).toBeInTheDocument()
    expect(screen.queryByText('Engineer role')).not.toBeInTheDocument()
  })

  test('click fires onChange with the wire value (kebab-case)', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ArchetypeGrid value={null} onChange={onChange} />)
    await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
    expect(onChange).toHaveBeenCalledWith('software-developer')
  })

  test('022 — new options fire onChange with their kebab-case wire values', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ArchetypeGrid value={null} onChange={onChange} />)
    await user.click(screen.getByRole('radio', { name: 'People & Culture' }))
    expect(onChange).toHaveBeenCalledWith('people-culture')
    await user.click(screen.getByRole('radio', { name: 'Operations Manager' }))
    expect(onChange).toHaveBeenCalledWith('operations-manager')
    await user.click(screen.getByRole('radio', { name: 'Sales & Customer Relations' }))
    expect(onChange).toHaveBeenCalledWith('sales-customer-relations')
  })

  test('ArrowDown advances selection', async () => {
    const onChange = vi.fn()
    const user = userEvent.setup()
    render(<ArchetypeGrid value="software-developer" onChange={onChange} />)
    screen.getByRole('radio', { name: 'Software Developer' }).focus()
    await user.keyboard('{ArrowDown}')
    expect(onChange).toHaveBeenCalledWith('project-manager')
  })

  // 022 (issue #50) — disabled state: when the custom-role input has a
  // non-blank trimmed value, the prefab grid is rendered blurred and
  // non-interactive (FR-2205). The wrapping fieldset gains the
  // `is-disabled` class, every option button is `aria-disabled="true"` and
  // has `tabIndex={-1}`, and clicks + arrow keys are no-ops.

  describe('022 disabled state', () => {
    test('adds .is-disabled to the fieldset and aria-disabled="true" to every option', () => {
      render(<ArchetypeGrid value={null} onChange={() => {}} disabled />)
      const fieldset = document.querySelector('.selection-grid') as HTMLFieldSetElement
      expect(fieldset.classList.contains('is-disabled')).toBe(true)
      expect(fieldset.getAttribute('aria-disabled')).toBe('true')
      for (const radio of screen.getAllByRole('radio')) {
        expect(radio.getAttribute('aria-disabled')).toBe('true')
        expect(radio.getAttribute('tabindex')).toBe('-1')
      }
    })

    test('clicks on prefab options are no-ops while disabled', async () => {
      const onChange = vi.fn()
      const user = userEvent.setup()
      render(<ArchetypeGrid value={null} onChange={onChange} disabled />)
      await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
      await user.click(screen.getByRole('radio', { name: 'People & Culture' }))
      expect(onChange).not.toHaveBeenCalled()
    })

    test('ArrowDown is a no-op while disabled', async () => {
      const onChange = vi.fn()
      const user = userEvent.setup()
      render(<ArchetypeGrid value="software-developer" onChange={onChange} disabled />)
      screen.getByRole('radio', { name: 'Software Developer' }).focus()
      await user.keyboard('{ArrowDown}')
      expect(onChange).not.toHaveBeenCalled()
    })

    test('absence of disabled prop defaults to interactive (regression for the default value)', async () => {
      const onChange = vi.fn()
      const user = userEvent.setup()
      render(<ArchetypeGrid value={null} onChange={onChange} />)
      const fieldset = document.querySelector('.selection-grid') as HTMLFieldSetElement
      expect(fieldset.classList.contains('is-disabled')).toBe(false)
      await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
      expect(onChange).toHaveBeenCalledWith('software-developer')
    })
  })
})

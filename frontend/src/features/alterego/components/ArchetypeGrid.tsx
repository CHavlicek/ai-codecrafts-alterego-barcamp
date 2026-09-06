import { ACCENT_VARS, ARCHETYPE_OPTIONS } from '../options'
import type { Archetype } from '../types'
import { SelectionGrid } from './SelectionGrid'

interface Props {
  value: Archetype | null
  onChange: (value: Archetype) => void
  /**
   * 022 (issue #50): when {@code true}, the grid renders blurred and
   * non-interactive — set by {@code SetupLayout} whenever the custom-role
   * input has a non-blank trimmed value (precedence rule, FR-2205).
   */
  disabled?: boolean
}

/**
 * Displayed label: "Role" (022 widens this from the pre-022 "Engineer role"
 * — the three new prefab options HR / Administration / Customer Relations
 * are not engineering roles). The wire field name on the request is still
 * {@code archetype}; the visible rename is UI-only.
 */
export function ArchetypeGrid({ value, onChange, disabled = false }: Props) {
  return (
    <SelectionGrid<Archetype>
      label="Role"
      options={ARCHETYPE_OPTIONS}
      value={value}
      onChange={onChange}
      accentVar={ACCENT_VARS.archetype}
      disabled={disabled}
    />
  )
}

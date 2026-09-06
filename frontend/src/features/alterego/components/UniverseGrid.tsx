import { ACCENT_VARS, UNIVERSE_OPTIONS } from '../options'
import type { Universe } from '../types'
import { SelectionGrid } from './SelectionGrid'

interface Props {
  value: Universe | null
  onChange: (value: Universe) => void
  /**
   * 029 (verbund-rebrand): when {@code true}, the grid renders blurred and
   * non-interactive — set by {@code SetupLayout} whenever the custom-universe
   * input has a non-blank trimmed value (precedence rule, mirrors the custom
   * Role behaviour).
   */
  disabled?: boolean
}

export function UniverseGrid({ value, onChange, disabled = false }: Props) {
  return (
    <SelectionGrid<Universe>
      label="Universe / Style"
      options={UNIVERSE_OPTIONS}
      value={value}
      onChange={onChange}
      accentVar={ACCENT_VARS.universe}
      disabled={disabled}
    />
  )
}

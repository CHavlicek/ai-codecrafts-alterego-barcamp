import { ACCENT_VARS, UNIVERSE_OPTIONS } from '../options'
import type { Universe } from '../types'
import { SelectionGrid } from './SelectionGrid'

interface Props {
  value: Universe | null
  onChange: (value: Universe) => void
}

export function UniverseGrid({ value, onChange }: Props) {
  return (
    <SelectionGrid<Universe>
      label="Universe / Style"
      options={UNIVERSE_OPTIONS}
      value={value}
      onChange={onChange}
      accentVar={ACCENT_VARS.universe}
    />
  )
}

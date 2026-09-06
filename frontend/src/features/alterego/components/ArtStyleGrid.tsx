import { ACCENT_VARS, ART_STYLE_OPTIONS } from '../options'
import type { ArtStyle } from '../types'
import { SelectionGrid } from './SelectionGrid'

interface Props {
  value: ArtStyle | null
  onChange: (value: ArtStyle) => void
}

/**
 * Art Style picker (006 FR-301 / FR-303 / FR-304). Nine mutually-exclusive
 * rendering styles, single-select with replace semantics (no deselect
 * toggle — art style is required for Generate to enable). Uses
 * {@code SelectionGrid<ArtStyle>} with the fifth accent-colour token.
 */
export function ArtStyleGrid({ value, onChange }: Props) {
  return (
    <SelectionGrid<ArtStyle>
      label="Art style"
      options={ART_STYLE_OPTIONS}
      value={value}
      onChange={onChange}
      accentVar={ACCENT_VARS.artstyle}
    />
  )
}

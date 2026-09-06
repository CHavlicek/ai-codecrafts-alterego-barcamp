import type { CSSProperties, ReactNode } from 'react'

/**
 * CSS-only visually-hidden helper. Content stays in the accessibility tree
 * (and is announced by screen readers) but is not visible. Used by
 * `<LiveRegion>` and anywhere an accessible name needs to be present
 * without showing on screen.
 */
const visuallyHiddenStyle: CSSProperties = {
  position: 'absolute',
  width: '1px',
  height: '1px',
  padding: 0,
  margin: '-1px',
  overflow: 'hidden',
  clip: 'rect(0, 0, 0, 0)',
  whiteSpace: 'nowrap',
  border: 0,
}

interface Props {
  children: ReactNode
}

export function VisuallyHidden({ children }: Props) {
  return <span style={visuallyHiddenStyle}>{children}</span>
}

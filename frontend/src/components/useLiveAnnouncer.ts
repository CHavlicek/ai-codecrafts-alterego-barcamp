import { createContext, useContext } from 'react'

/**
 * Context + consumer hook for the singleton ARIA live region (FR-022).
 * Lives in its own file so the component file (`LiveRegion.tsx`) can keep
 * a clean component-only export surface — required by the
 * `react-refresh/only-export-components` lint rule for Fast Refresh.
 *
 * `LiveRegionProvider` (the component half) lives in `./LiveRegion`.
 */

export type Politeness = 'polite' | 'assertive'

export interface AnnouncerContextValue {
  announce: (message: string, politeness?: Politeness) => void
}

export const AnnouncerContext = createContext<AnnouncerContextValue | null>(null)

export function useLiveAnnouncer(): AnnouncerContextValue {
  const ctx = useContext(AnnouncerContext)
  if (!ctx) {
    throw new Error('useLiveAnnouncer must be used inside <LiveRegionProvider>')
  }
  return ctx
}

import { useCallback, useState, type ReactNode } from 'react'
import { VisuallyHidden } from './VisuallyHidden'
import { AnnouncerContext, type Politeness } from './useLiveAnnouncer'

/**
 * Singleton ARIA live region implementing FR-022.
 *
 * Mounted once at the app root. Components push announcements via the
 * `useLiveAnnouncer()` hook (see `./useLiveAnnouncer`).
 *
 * Two channels:
 *  - `polite`    — non-interrupting; loading / success / fallback.
 *  - `assertive` — interrupting; reserved for blocking errors.
 *
 * Repeated identical messages are still announced thanks to the
 * blank-then-set pattern via `requestAnimationFrame`, which forces ATs
 * to re-fire the announcement.
 */

interface ProviderProps {
  children: ReactNode
}

export function LiveRegionProvider({ children }: ProviderProps) {
  const [politeMessage, setPoliteMessage] = useState('')
  const [assertiveMessage, setAssertiveMessage] = useState('')

  const announce = useCallback((message: string, politeness: Politeness = 'polite') => {
    if (politeness === 'polite') {
      setPoliteMessage('')
      requestAnimationFrame(() => setPoliteMessage(message))
    } else {
      setAssertiveMessage('')
      requestAnimationFrame(() => setAssertiveMessage(message))
    }
  }, [])

  return (
    <AnnouncerContext.Provider value={{ announce }}>
      {children}
      <VisuallyHidden>
        <div role="status" aria-live="polite" aria-atomic="true">
          {politeMessage}
        </div>
        <div role="alert" aria-live="assertive" aria-atomic="true">
          {assertiveMessage}
        </div>
      </VisuallyHidden>
    </AnnouncerContext.Provider>
  )
}

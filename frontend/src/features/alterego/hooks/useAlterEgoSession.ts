import { useContext } from 'react'
import { AlterEgoContext, type AlterEgoContextValue } from '../state/context'

/**
 * Consumer hook for {@link AlterEgoContext}. Throws if invoked outside
 * an {@code <AlterEgoProvider>} so misuse is surfaced eagerly.
 */
export function useAlterEgoSession(): AlterEgoContextValue {
  const ctx = useContext(AlterEgoContext)
  if (!ctx) {
    throw new Error('useAlterEgoSession must be used inside <AlterEgoProvider>')
  }
  return ctx
}

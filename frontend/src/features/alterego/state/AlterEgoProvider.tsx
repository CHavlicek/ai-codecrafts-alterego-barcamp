import { useReducer, type ReactNode } from 'react'
import { AlterEgoContext } from './context'
import { alterEgoReducer, initialAlterEgoSession } from './reducer'

interface ProviderProps {
  children: ReactNode
}

/**
 * Provides the AlterEgoSession reducer state to its descendants. Mount
 * once near the top of the feature page (T083 — AlterEgoPage.tsx).
 */
export function AlterEgoProvider({ children }: ProviderProps) {
  const [state, dispatch] = useReducer(alterEgoReducer, undefined, initialAlterEgoSession)
  return <AlterEgoContext.Provider value={{ state, dispatch }}>{children}</AlterEgoContext.Provider>
}

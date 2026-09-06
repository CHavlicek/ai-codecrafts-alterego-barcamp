import { createContext, type Dispatch } from 'react'
import type { AlterEgoAction, AlterEgoSession } from './reducer'

/**
 * Context object for {@link AlterEgoSession}. Lives in its own non-tsx
 * file so the Provider component file ({@code AlterEgoProvider.tsx}) and
 * the consumer hook ({@code ../hooks/useAlterEgoSession.ts}) can each
 * keep clean export surfaces — the {@code react-refresh/only-export-components}
 * rule otherwise complains about mixed exports.
 */
export interface AlterEgoContextValue {
  state: AlterEgoSession
  dispatch: Dispatch<AlterEgoAction>
}

export const AlterEgoContext = createContext<AlterEgoContextValue | null>(null)

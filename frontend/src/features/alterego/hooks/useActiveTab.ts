/**
 * Thin selector hook over {@link useAlterEgoSession} that exposes only the
 * two-tab-related slice of the session: {@code activeTab} and a
 * {@code setActiveTab} dispatcher.
 *
 * <p>Intended for {@code TabsShell} and any other component that wants to
 * respond to (or change) the active tab without pulling in the whole
 * session reducer.
 */
import { useCallback } from 'react'
import type { ActiveTab } from '../state/reducer'
import { useAlterEgoSession } from './useAlterEgoSession'

export interface UseActiveTab {
  activeTab: ActiveTab
  setActiveTab: (tab: ActiveTab) => void
}

export function useActiveTab(): UseActiveTab {
  const { state, dispatch } = useAlterEgoSession()
  const setActiveTab = useCallback(
    (tab: ActiveTab) => dispatch({ type: 'ActiveTabChanged', tab }),
    [dispatch],
  )
  return { activeTab: state.activeTab, setActiveTab }
}

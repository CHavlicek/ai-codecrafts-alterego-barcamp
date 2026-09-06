/**
 * Thin selector hook over {@link useAlterEgoSession} that exposes only
 * the monotonic {@code generateAutoSwitchNonce} counter written by the
 * reducer on every {@code ActiveTabChanged} dispatched with
 * {@code reason: 'generate'}.
 *
 * <p>005 FR-401 / FR-403: {@code TabsShell} watches this counter to
 * trigger the one-shot entrance animation on the incoming alter-ego
 * panel. Because the value increases monotonically, consumers can key a
 * {@code useEffect} on it and reliably observe each Generate-triggered
 * auto-switch — including re-clicks while the alter-ego tab is already
 * active (spec Acceptance 4).
 */
import { useAlterEgoSession } from './useAlterEgoSession'

export interface UseTabAnimationSignal {
  generateAutoSwitchNonce: number
}

export function useTabAnimationSignal(): UseTabAnimationSignal {
  const { state } = useAlterEgoSession()
  return { generateAutoSwitchNonce: state.generateAutoSwitchNonce }
}

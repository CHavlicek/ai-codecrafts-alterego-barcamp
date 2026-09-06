import type { ReactElement, ReactNode } from 'react'
import { render, type RenderOptions, type RenderResult } from '@testing-library/react'
import { LiveRegionProvider } from '../components/LiveRegion'

/**
 * Shared Testing Library helpers.
 *
 * {@link renderWithLiveRegion} wraps a render in LiveRegionProvider so
 * components that call {@code useLiveAnnouncer()} don't need to set up
 * the provider per-test.
 */

export function renderWithLiveRegion(
  ui: ReactElement,
  options?: Omit<RenderOptions, 'wrapper'>,
): RenderResult {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <LiveRegionProvider>{children}</LiveRegionProvider>
  )
  return render(ui, { wrapper: Wrapper, ...options })
}

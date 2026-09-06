import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react'
import { useActiveTab } from '../hooks/useActiveTab'
import { useTabAnimationSignal } from '../hooks/useTabAnimationSignal'
import type { ActiveTab } from '../state/reducer'

export interface TabDescriptor {
  id: ActiveTab
  /** Visible tab label, e.g. "1 Setup". Numbered prefix is part of the label (spec FR-101 / 007 FR-501). */
  label: string
  /** Rendered inside the matching tabpanel. */
  panel: ReactNode
  /**
   * 007 FR-502: when true, the tab header is rendered non-interactive —
   * `aria-disabled="true"`, `tabindex="-1"`, `data-disabled="true"`,
   * reduced opacity, `cursor: not-allowed`. Click / Enter / Space are
   * no-ops; Arrow/Home/End focus navigation skips the entry. Defaults to
   * false so pre-007 call sites remain unchanged.
   */
  disabled?: boolean
}

interface Props {
  tabs: readonly [TabDescriptor, TabDescriptor]
}

/** 005 FR-407 / FR-409: keep slightly above the CSS animation duration
 * (320 ms) so onAnimationEnd wins in the happy path; the timeout is the
 * reduced-motion + browser-backgrounded safety net. */
const ANIMATION_SAFETY_TIMEOUT_MS = 400

/**
 * Two-tab top-level shell implementing the WAI-ARIA "Tabs with manual
 * activation" pattern (research.md §R1). Both tabpanels are always
 * mounted in the DOM; the inactive one is hidden with the HTML
 * {@code hidden} attribute (research.md §R8) so user-entered state
 * (scroll, focus, form values) survives tab switches (FR-106, SC-105).
 *
 * <p>Keyboard model:
 *   <ul>
 *     <li>Tab / Shift+Tab to enter and leave the tablist.</li>
 *     <li>Left / Right (and Up / Down) arrows move the DOM focus between
 *         tabs via roving tabindex, but DO NOT change the selected tab —
 *         users must press Enter or Space to activate (manual activation).</li>
 *     <li>Home / End jump focus to the first / last tab.</li>
 *   </ul>
 *
 * <p>Selection and active-tab state are owned by the session reducer
 * (see {@link useActiveTab}); this component is a controlled view.
 *
 * <p>005: the alter-ego tabpanel plays a one-shot entrance animation
 * on every Generate-triggered auto-switch (spec 005 FR-401). The trigger
 * is the {@code generateAutoSwitchNonce} counter from the session
 * reducer — a monotonic value makes the {@code useEffect} dep-array
 * stable even when {@code GenerateSubmitted} dispatches in the same
 * commit. Only the incoming (visible) panel animates; the outgoing one
 * is hidden via the HTML {@code hidden} attribute and {@code display:
 * none} means CSS animations on it are ignored anyway.
 */
export function TabsShell({ tabs }: Props) {
  const { activeTab, setActiveTab } = useActiveTab()
  const { generateAutoSwitchNonce } = useTabAnimationSignal()
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([])
  const tablistId = useId()

  // Adjust derived state during render (React "Storing information from
  // previous renders" pattern) — arms the animation whenever the
  // monotonic nonce advances. Doing this in render instead of in an
  // effect avoids cascading-render warnings from react-hooks/set-state-in-effect
  // and makes the transition observable in the same React commit as the
  // ActiveTabChanged dispatch (required for the Playwright
  // `data-animating` assertion to latch before the animation ends).
  const [prevNonce, setPrevNonce] = useState(generateAutoSwitchNonce)
  const [isAnimatingIn, setIsAnimatingIn] = useState(false)
  if (prevNonce !== generateAutoSwitchNonce) {
    setPrevNonce(generateAutoSwitchNonce)
    if (generateAutoSwitchNonce > 0) setIsAnimatingIn(true)
  }

  // Safety fallback — under prefers-reduced-motion the keyframe is
  // suppressed so `animationend` never fires; also covers tab-backgrounded
  // and suspended-animation edge cases (005 FR-409).
  useEffect(() => {
    if (!isAnimatingIn) return
    const t = window.setTimeout(() => setIsAnimatingIn(false), ANIMATION_SAFETY_TIMEOUT_MS)
    return () => window.clearTimeout(t)
  }, [isAnimatingIn])

  // 007 FR-502/FR-508: arrow/Home/End navigation skips disabled tabs.
  // Walks in the given direction (+1/-1) from `startIndex` and returns the
  // first enabled index, wrapping around once. If every tab is disabled
  // (never possible by FR-507), returns `startIndex` so focus doesn't move.
  const findNextEnabledIndex = (startIndex: number, step: 1 | -1): number => {
    const len = tabs.length
    for (let i = 1; i <= len; i += 1) {
      const candidate = (((startIndex + step * i) % len) + len) % len
      if (!tabs[candidate]!.disabled) return candidate
    }
    return startIndex
  }

  const firstEnabledIndex = (): number => {
    for (let i = 0; i < tabs.length; i += 1) {
      if (!tabs[i]!.disabled) return i
    }
    return 0
  }

  const lastEnabledIndex = (): number => {
    for (let i = tabs.length - 1; i >= 0; i -= 1) {
      if (!tabs[i]!.disabled) return i
    }
    return tabs.length - 1
  }

  const focusTab = (index: number) => {
    tabRefs.current[index]?.focus()
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLButtonElement>, index: number) => {
    switch (e.key) {
      case 'ArrowLeft':
      case 'ArrowUp':
        e.preventDefault()
        focusTab(findNextEnabledIndex(index, -1))
        return
      case 'ArrowRight':
      case 'ArrowDown':
        e.preventDefault()
        focusTab(findNextEnabledIndex(index, 1))
        return
      case 'Home':
        e.preventDefault()
        focusTab(firstEnabledIndex())
        return
      case 'End':
        e.preventDefault()
        focusTab(lastEnabledIndex())
        return
      case 'Enter':
      case ' ':
      case 'Spacebar':
        e.preventDefault()
        // 007 FR-502: Enter / Space on a disabled tab is a no-op.
        if (tabs[index]!.disabled) return
        setActiveTab(tabs[index]!.id)
        return
      default:
        return
    }
  }

  return (
    <div className="tabs-shell">
      <div
        role="tablist"
        aria-label="Workflow steps"
        className="tabs-shell__tablist"
        id={tablistId}
      >
        {tabs.map((tab, index) => {
          const selected = activeTab === tab.id
          const disabled = !!tab.disabled
          // 007 FR-502: a disabled tab is NEVER the roving-tabindex entry
          // point. By FR-507 the active tab is always enabled, so
          // `selected && disabled` is unreachable; this guard is defence-
          // in-depth.
          const rovingTabIndex = selected && !disabled ? 0 : -1
          const classNames = ['tabs-shell__tab', selected && !disabled ? 'is-active' : '']
            .filter(Boolean)
            .join(' ')
          return (
            <button
              key={tab.id}
              ref={(el) => {
                tabRefs.current[index] = el
              }}
              type="button"
              role="tab"
              id={`${tablistId}-tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`${tablistId}-panel-${tab.id}`}
              aria-disabled={disabled || undefined}
              tabIndex={rovingTabIndex}
              data-selected={selected ? 'true' : 'false'}
              data-disabled={disabled ? 'true' : undefined}
              onClick={() => {
                // 007 FR-502: click on a disabled tab is a no-op.
                if (disabled) return
                setActiveTab(tab.id)
              }}
              onKeyDown={(e) => handleKeyDown(e, index)}
              className={classNames}
            >
              {tab.label}
            </button>
          )
        })}
      </div>
      {tabs.map((tab) => {
        const selected = activeTab === tab.id
        const isAlterEgo = tab.id === 'alter-ego'
        // 005 FR-401/403: only the alter-ego panel animates, and only
        // when a Generate click latched the nonce. Manual clicks leave
        // isAnimatingIn=false so `data-animating` stays 'false' and the
        // animate-in class is absent — matching 002 instant-switch.
        const animatingThisPanel = isAlterEgo && isAnimatingIn
        const panelClass = [
          'tabs-shell__panel',
          animatingThisPanel && 'tabs-shell__panel--animate-in',
        ]
          .filter(Boolean)
          .join(' ')
        return (
          <div
            key={tab.id}
            role="tabpanel"
            id={`${tablistId}-panel-${tab.id}`}
            aria-labelledby={`${tablistId}-tab-${tab.id}`}
            hidden={!selected}
            aria-hidden={!selected}
            className={panelClass}
            tabIndex={0}
            {...(isAlterEgo && {
              'data-animating': animatingThisPanel ? 'true' : 'false',
              onAnimationEnd: () => setIsAnimatingIn(false),
            })}
          >
            {tab.panel}
          </div>
        )
      })}
    </div>
  )
}

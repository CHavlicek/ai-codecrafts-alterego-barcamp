import { act } from 'react'
import { afterEach, describe, expect, test, vi } from 'vitest'
import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AlterEgoProvider } from '../state/AlterEgoProvider'
import { useAlterEgoSession } from '../hooks/useAlterEgoSession'
import { TabsShell } from './TabsShell'

/**
 * T003 — TabsShell. Verifies the WAI-ARIA manual-activation tabs pattern
 * (research.md §R1), roving tabindex, persistent-panel state preservation
 * per research.md §R8, and visible/aria-selected active-state signalling.
 */

function renderShell() {
  return render(
    <AlterEgoProvider>
      <TabsShell
        tabs={[
          {
            id: 'setup',
            label: '1 Setup',
            panel: <div data-testid="panel-setup">Setup content</div>,
          },
          {
            id: 'alter-ego',
            label: '2 Your Alter Ego',
            panel: <div data-testid="panel-alter-ego">Alter ego content</div>,
          },
        ]}
      />
    </AlterEgoProvider>,
  )
}

describe('TabsShell', () => {
  test('renders both tabs with the mockup labels', () => {
    renderShell()
    expect(screen.getByRole('tab', { name: '1 Setup' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toBeInTheDocument()
    expect(screen.getAllByRole('tab')).toHaveLength(2)
  })

  test('setup tab is selected on first paint', () => {
    renderShell()
    expect(screen.getByRole('tab', { name: '1 Setup' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
      'aria-selected',
      'false',
    )
  })

  test('both panels are in the DOM; inactive one is hidden (state preservation)', () => {
    renderShell()
    // The hidden attribute removes the element from the accessibility tree
    // but keeps it mounted, so React state inside it survives a tab switch.
    const setupPanel = document.querySelector('[id$="-panel-setup"]') as HTMLElement
    const alterEgoPanel = document.querySelector('[id$="-panel-alter-ego"]') as HTMLElement
    expect(setupPanel.hidden).toBe(false)
    expect(alterEgoPanel.hidden).toBe(true)
    expect(within(setupPanel).getByTestId('panel-setup')).toBeInTheDocument()
    expect(within(alterEgoPanel).getByTestId('panel-alter-ego')).toBeInTheDocument()
  })

  test('click activates the clicked tab', async () => {
    const user = userEvent.setup()
    renderShell()
    await user.click(screen.getByRole('tab', { name: '2 Your Alter Ego' }))
    expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    expect(screen.getByRole('tab', { name: '1 Setup' })).toHaveAttribute('aria-selected', 'false')
  })

  test('roving tabindex: only the active tab has tabindex=0', () => {
    renderShell()
    expect(screen.getByRole('tab', { name: '1 Setup' })).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute('tabindex', '-1')
  })

  test('arrow keys move focus WITHOUT changing selection (manual activation)', async () => {
    const user = userEvent.setup()
    renderShell()
    const setupTab = screen.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = screen.getByRole('tab', { name: '2 Your Alter Ego' })
    setupTab.focus()
    await user.keyboard('{ArrowRight}')
    // Focus should have moved but selection should NOT change yet.
    expect(alterEgoTab).toHaveFocus()
    expect(setupTab).toHaveAttribute('aria-selected', 'true')
    expect(alterEgoTab).toHaveAttribute('aria-selected', 'false')
  })

  test('Enter on a focused tab activates it', async () => {
    const user = userEvent.setup()
    renderShell()
    const setupTab = screen.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = screen.getByRole('tab', { name: '2 Your Alter Ego' })
    setupTab.focus()
    await user.keyboard('{ArrowRight}{Enter}')
    expect(alterEgoTab).toHaveAttribute('aria-selected', 'true')
  })

  test('Space also activates', async () => {
    const user = userEvent.setup()
    renderShell()
    const setupTab = screen.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = screen.getByRole('tab', { name: '2 Your Alter Ego' })
    setupTab.focus()
    await user.keyboard('{ArrowRight} ')
    expect(alterEgoTab).toHaveAttribute('aria-selected', 'true')
  })

  test('End jumps focus to the last tab; Home to the first', async () => {
    const user = userEvent.setup()
    renderShell()
    const setupTab = screen.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = screen.getByRole('tab', { name: '2 Your Alter Ego' })
    setupTab.focus()
    await user.keyboard('{End}')
    expect(alterEgoTab).toHaveFocus()
    await user.keyboard('{Home}')
    expect(setupTab).toHaveFocus()
  })

  test('non-colour-only active signal: aria-selected is load-bearing', () => {
    // Spec FR-103 requires the active tab to be indicated by more than
    // colour alone. aria-selected + a data-selected attribute both qualify.
    renderShell()
    const setupTab = screen.getByRole('tab', { name: '1 Setup' })
    expect(setupTab).toHaveAttribute('aria-selected', 'true')
    expect(setupTab).toHaveAttribute('data-selected', 'true')
  })

  test('tabpanel is labelled by its tab', () => {
    renderShell()
    const setupTab = screen.getByRole('tab', { name: '1 Setup' })
    const setupPanel = document.querySelector('[id$="-panel-setup"]') as HTMLElement
    expect(setupTab.getAttribute('aria-controls')).toBe(setupPanel.id)
    expect(setupPanel.getAttribute('aria-labelledby')).toBe(setupTab.id)
  })
})

/**
 * 005 — entrance animation on the incoming alter-ego tabpanel when the
 * auto-switch was triggered by a Generate click. Drives FR-401 (animate
 * only on generate), FR-403 (manual clicks stay instant), FR-408
 * (observable data-animating attribute), and FR-409 (safety timeout).
 */
describe('TabsShell — Generate-triggered animation (005)', () => {
  function renderShellWithProbe() {
    // DispatchProbe writes the reducer dispatcher into a closure-scoped
    // variable so tests can exercise the animation signal without
    // dragging in useGenerateAlterEgo. The `!` definite-assignment
    // assertion is safe: React renders DispatchProbe synchronously
    // during render(), so `dispatch` is assigned before the return.
    let dispatch!: ReturnType<typeof useAlterEgoSession>['dispatch']
    function DispatchProbe() {
      dispatch = useAlterEgoSession().dispatch
      return null
    }
    const view = render(
      <AlterEgoProvider>
        <DispatchProbe />
        <TabsShell
          tabs={[
            {
              id: 'setup',
              label: '1 Setup',
              panel: <div data-testid="panel-setup">Setup</div>,
            },
            {
              id: 'alter-ego',
              label: '2 Your Alter Ego',
              panel: <div data-testid="panel-alter-ego">Alter ego</div>,
            },
          ]}
        />
      </AlterEgoProvider>,
    )
    return { ...view, dispatch }
  }

  function alterEgoPanel(): HTMLElement {
    return document.querySelector('[id$="-panel-alter-ego"]') as HTMLElement
  }

  afterEach(() => {
    vi.useRealTimers()
  })

  test('before any dispatch, the alter-ego panel has data-animating="false" and no animate-in class', () => {
    renderShellWithProbe()
    const panel = alterEgoPanel()
    expect(panel.getAttribute('data-animating')).toBe('false')
    expect(panel.className).not.toMatch(/tabs-shell__panel--animate-in/)
  })

  test('Generate-triggered ActiveTabChanged latches data-animating="true" and the animate-in class', () => {
    const { dispatch } = renderShellWithProbe()
    act(() => {
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    })
    const panel = alterEgoPanel()
    expect(panel.getAttribute('data-animating')).toBe('true')
    expect(panel.className).toMatch(/tabs-shell__panel--animate-in/)
  })

  test('onAnimationEnd clears data-animating back to "false" and removes the class (005 FR-409 happy path)', () => {
    const { dispatch } = renderShellWithProbe()
    act(() => {
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    })
    const panel = alterEgoPanel()
    expect(panel.getAttribute('data-animating')).toBe('true')

    fireEvent.animationEnd(panel)

    expect(panel.getAttribute('data-animating')).toBe('false')
    expect(panel.className).not.toMatch(/tabs-shell__panel--animate-in/)
  })

  test('manual tab click to alter-ego does NOT animate (005 FR-403)', async () => {
    const user = userEvent.setup()
    renderShellWithProbe()
    await user.click(screen.getByRole('tab', { name: '2 Your Alter Ego' }))
    const panel = alterEgoPanel()
    expect(panel.getAttribute('data-animating')).toBe('false')
    expect(panel.className).not.toMatch(/tabs-shell__panel--animate-in/)
  })

  test('safety timeout clears data-animating even if animationend never fires (005 FR-409)', () => {
    vi.useFakeTimers()
    const { dispatch } = renderShellWithProbe()
    act(() => {
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    })
    const panel = alterEgoPanel()
    expect(panel.getAttribute('data-animating')).toBe('true')

    act(() => {
      vi.advanceTimersByTime(450) // > 400 ms safety window
    })

    expect(panel.getAttribute('data-animating')).toBe('false')
    expect(panel.className).not.toMatch(/tabs-shell__panel--animate-in/)
  })

  test('re-dispatching a Generate ActiveTabChanged while alter-ego is active re-triggers animation (spec Acceptance 4)', () => {
    const { dispatch } = renderShellWithProbe()
    act(() => {
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    })
    const panel = alterEgoPanel()
    fireEvent.animationEnd(panel)
    expect(panel.getAttribute('data-animating')).toBe('false')

    act(() => {
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    })

    expect(panel.getAttribute('data-animating')).toBe('true')
  })

  test('setup panel never carries the animate-in class (only the incoming alter-ego panel animates)', () => {
    const { dispatch } = renderShellWithProbe()
    act(() => {
      dispatch({ type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    })
    const setupPanel = document.querySelector('[id$="-panel-setup"]') as HTMLElement
    expect(setupPanel.className).not.toMatch(/tabs-shell__panel--animate-in/)
    expect(setupPanel.hasAttribute('data-animating')).toBe(false)
  })
})

/**
 * 007 — disabled-tab contract. Drives FR-502 (aria-disabled + tabindex=-1
 * + ignore click/Enter/Space + skip in arrow nav), FR-503..FR-505 (phase-
 * dependent gating, exercised via the `disabled` prop directly so this
 * block stays decoupled from session-phase plumbing), and FR-508
 * (manual-activation pattern preserved on the still-enabled tab).
 */
describe('TabsShell — disabled tab (007)', () => {
  function renderShellWith(options: { setupDisabled?: boolean; alterEgoDisabled?: boolean }): void {
    render(
      <AlterEgoProvider>
        <TabsShell
          tabs={[
            {
              id: 'setup',
              label: '1 Setup',
              panel: <div data-testid="panel-setup">Setup</div>,
              disabled: options.setupDisabled ?? false,
            },
            {
              id: 'alter-ego',
              label: '2 Your Alter Ego',
              panel: <div data-testid="panel-alter-ego">Alter ego</div>,
              disabled: options.alterEgoDisabled ?? false,
            },
          ]}
        />
      </AlterEgoProvider>,
    )
  }

  describe('alter-ego tab disabled (pre-generation state)', () => {
    test('exposes aria-disabled, tabindex=-1, data-disabled', () => {
      renderShellWith({ alterEgoDisabled: true })
      const disabled = screen.getByRole('tab', { name: '2 Your Alter Ego' })
      expect(disabled).toHaveAttribute('aria-disabled', 'true')
      expect(disabled).toHaveAttribute('tabindex', '-1')
      expect(disabled).toHaveAttribute('data-disabled', 'true')
    })

    test('setup tab is the roving-tabindex entry point', () => {
      renderShellWith({ alterEgoDisabled: true })
      expect(screen.getByRole('tab', { name: '1 Setup' })).toHaveAttribute('tabindex', '0')
    })

    test('click on disabled alter-ego tab does NOT change aria-selected', async () => {
      const user = userEvent.setup()
      renderShellWith({ alterEgoDisabled: true })
      await user.click(screen.getByRole('tab', { name: '2 Your Alter Ego' }))
      expect(screen.getByRole('tab', { name: '1 Setup' })).toHaveAttribute('aria-selected', 'true')
      expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
        'aria-selected',
        'false',
      )
    })

    test('Enter on a programmatically focused disabled tab does NOT activate it', async () => {
      const user = userEvent.setup()
      renderShellWith({ alterEgoDisabled: true })
      const disabled = screen.getByRole('tab', { name: '2 Your Alter Ego' })
      disabled.focus()
      await user.keyboard('{Enter}')
      expect(screen.getByRole('tab', { name: '1 Setup' })).toHaveAttribute('aria-selected', 'true')
    })

    test('Space on a programmatically focused disabled tab does NOT activate it', async () => {
      const user = userEvent.setup()
      renderShellWith({ alterEgoDisabled: true })
      const disabled = screen.getByRole('tab', { name: '2 Your Alter Ego' })
      disabled.focus()
      await user.keyboard(' ')
      expect(screen.getByRole('tab', { name: '1 Setup' })).toHaveAttribute('aria-selected', 'true')
    })

    test('ArrowRight from the enabled setup tab skips over the disabled sibling (stays on setup)', async () => {
      const user = userEvent.setup()
      renderShellWith({ alterEgoDisabled: true })
      const setup = screen.getByRole('tab', { name: '1 Setup' })
      setup.focus()
      await user.keyboard('{ArrowRight}')
      // Only one enabled tab → focus stays put rather than landing on a disabled element.
      expect(setup).toHaveFocus()
    })

    test('Home and End both land focus on the enabled setup tab', async () => {
      const user = userEvent.setup()
      renderShellWith({ alterEgoDisabled: true })
      const setup = screen.getByRole('tab', { name: '1 Setup' })
      setup.focus()
      await user.keyboard('{End}')
      expect(setup).toHaveFocus()
      await user.keyboard('{Home}')
      expect(setup).toHaveFocus()
    })

    test('non-colour-only disabled signal: aria-disabled + data-disabled BOTH present (FR-502)', () => {
      renderShellWith({ alterEgoDisabled: true })
      const disabled = screen.getByRole('tab', { name: '2 Your Alter Ego' })
      expect(disabled).toHaveAttribute('aria-disabled', 'true')
      expect(disabled).toHaveAttribute('data-disabled', 'true')
    })
  })

  describe('setup tab disabled (mid-request state)', () => {
    test('exposes aria-disabled + tabindex=-1; alter-ego becomes the entry point', () => {
      renderShellWith({ setupDisabled: true })
      const disabled = screen.getByRole('tab', { name: '1 Setup' })
      expect(disabled).toHaveAttribute('aria-disabled', 'true')
      expect(disabled).toHaveAttribute('tabindex', '-1')
    })

    test('ArrowLeft / click / Enter on disabled setup do NOT activate it (alter-ego must stay active)', async () => {
      const user = userEvent.setup()
      render(
        <AlterEgoProvider>
          <TabsShell
            tabs={[
              {
                id: 'setup',
                label: '1 Setup',
                panel: <div>s</div>,
                disabled: true,
              },
              {
                id: 'alter-ego',
                label: '2 Your Alter Ego',
                panel: <div>a</div>,
                disabled: false,
              },
            ]}
          />
        </AlterEgoProvider>,
      )

      // Activate alter-ego first so it is the selected tab for the duration of this test.
      await user.click(screen.getByRole('tab', { name: '2 Your Alter Ego' }))
      expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
        'aria-selected',
        'true',
      )

      // Click setup — must be ignored.
      await user.click(screen.getByRole('tab', { name: '1 Setup' }))
      expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
        'aria-selected',
        'true',
      )

      // ArrowLeft from focused alter-ego — must stay on alter-ego (only enabled).
      screen.getByRole('tab', { name: '2 Your Alter Ego' }).focus()
      await user.keyboard('{ArrowLeft}')
      expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveFocus()

      // Enter on programmatically focused disabled Setup — ignored.
      screen.getByRole('tab', { name: '1 Setup' }).focus()
      await user.keyboard('{Enter}')
      expect(screen.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
        'aria-selected',
        'true',
      )
    })
  })
})

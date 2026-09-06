import { expect, test } from '@playwright/test'
import { fillAllSelections, mockHappyApi } from './helpers'

/**
 * T007 — Playwright coverage for the two-tab workflow shell.
 *
 * Drives spec FR-101..FR-109 at the rendered-browser level:
 *  - Two tabs with the mockup labels, side-by-side, full page width.
 *  - Active tab communicated by more than colour alone (aria-selected +
 *    underline + data-selected).
 *  - Keyboard-only navigation (manual activation pattern, research.md §R1).
 *  - Pre-generation empty-state on tab 2 — not a broken / blank state.
 *  - Auto-switch to tab 2 on Generate click (FR-108).
 *  - Start-over returns the active tab to tab 1 (FR-102).
 *  - Structural match for SC-106 at 1440 px + archived screenshot.
 */

test.describe('Tabs shell', () => {
  test('both tabs render with the mockup labels', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('tab', { name: '1 Setup' })).toBeVisible()
    await expect(page.getByRole('tab', { name: '2 Your Alter Ego' })).toBeVisible()
    await expect(page.getByRole('tab')).toHaveCount(2)
  })

  test('"1 setup" is active on first paint; "2 Your Alter Ego" is inactive', async ({ page }) => {
    await page.goto('/')
    await expect(page.getByRole('tab', { name: '1 Setup' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(page.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
      'aria-selected',
      'false',
    )
  })

  test('non-colour-only active indicator: data-selected + aria-selected agree', async ({
    page,
  }) => {
    await page.goto('/')
    const activeTab = page.getByRole('tab', { name: '1 Setup' })
    await expect(activeTab).toHaveAttribute('data-selected', 'true')
    await expect(activeTab).toHaveAttribute('aria-selected', 'true')
  })

  test('pre-generation, tab 2 is gated (007 supersedes the pre-007 "empty-state placeholder" contract)', async ({
    page,
  }) => {
    await page.goto('/')
    const alterEgoTab = page.getByRole('tab', { name: '2 Your Alter Ego' })
    // 007 FR-503: before the first generation resolves, the alter-ego tab
    // is disabled — replacing the pre-007 "empty-state placeholder"
    // contract. Clicking it must not flip aria-selected.
    await expect(alterEgoTab).toHaveAttribute('aria-disabled', 'true')
    await alterEgoTab.click({ force: true })
    await expect(page.getByRole('tab', { name: '1 Setup' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  test('keyboard-only: Tab → arrow keys → Enter activates (post-generation, both tabs enabled)', async ({
    page,
  }) => {
    // 007 US1: pre-generation the alter-ego tab is disabled and arrow
    // navigation skips it — drive a successful generation so both tabs
    // are enabled, then verify the manual-activation pattern still holds.
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    const setupTab = page.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = page.getByRole('tab', { name: '2 Your Alter Ego' })
    await setupTab.focus()
    await page.keyboard.press('ArrowRight')
    await expect(alterEgoTab).toBeFocused()
    // Arrow moved focus — selection stays on alter-ego (currently active
    // post-auto-switch); pressing Enter is a no-op on the focused tab.
    await page.keyboard.press('ArrowLeft')
    await expect(setupTab).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(setupTab).toHaveAttribute('aria-selected', 'true')
  })

  test('auto-switch on Generate: tab 2 becomes active the instant Generate is clicked (FR-108)', async ({
    page,
  }) => {
    // Delay the API so the tab state advances before the response lands.
    await page.route('**/api/v1/alter-egos', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 500))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          character: {
            heroTitleLine1: 'PAULA',
            heroTitleLine2: 'The Cloud Guardrail',
            tagline: 'STILL SHIPS ON FRIDAYS.',
            superpowers: ['a', 'b', 'c'],
            quote: 'q',
          },
          poster: {
            dataUrl:
              'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkAAIAAAoAAv/lPAAAAABJRU5ErkJggg==',
            mediaType: 'image/png',
            widthPx: 900,
            heightPx: 1200,
          },
          meta: { outcome: 'real', correlationId: 'x' },
        }),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    // 005 FR-401 — the Generate-triggered auto-switch latches the
    // animation signal in the same React commit as the tab change.
    // Assert data-animating FIRST so a slow CI doesn't miss the ~320 ms
    // window.
    const alterEgoPanel = page.locator('[id$="-panel-alter-ego"]')
    await expect(alterEgoPanel).toHaveAttribute('data-animating', 'true')
    // The auto-switch happens synchronously on click (dispatched from onMutate).
    // 005 FR-402 — the ARIA state must still flip immediately; the
    // animation does not delay this 002 FR-108 contract.
    await expect(page.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  test('manual tab click does NOT animate (005 FR-403)', async ({ page }) => {
    // 007 US1: the alter-ego tab is gated until a generation resolves, so
    // drive a successful generation first, then exercise the
    // "manual-click-doesn't-animate" contract by clicking back and forth.
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    const alterEgoPanel = page.locator('[id$="-panel-alter-ego"]')
    // After the auto-switch animation completes (onAnimationEnd clears it),
    // data-animating settles back to 'false'. Wait for that quiescence.
    await expect(alterEgoPanel).toHaveAttribute('data-animating', 'false')

    // Click Setup then back to alter-ego — neither manual click animates.
    await page.getByRole('tab', { name: '1 Setup' }).click()
    await expect(alterEgoPanel).toHaveAttribute('data-animating', 'false')
    await expect(alterEgoPanel).not.toHaveClass(/tabs-shell__panel--animate-in/)

    await page.getByRole('tab', { name: '2 Your Alter Ego' }).click()
    await expect(page.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(alterEgoPanel).toHaveAttribute('data-animating', 'false')
    await expect(alterEgoPanel).not.toHaveClass(/tabs-shell__panel--animate-in/)
  })

  test('start-over returns the active tab to "1 Setup"', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    await page.getByRole('button', { name: /start over/i }).click()
    await expect(page.getByRole('tab', { name: '1 Setup' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })

  test('tab switch preserves Setup inputs (SC-105; post-generation)', async ({ page }) => {
    // 007 US1: pre-generation the alter-ego tab is gated, so the SC-105
    // "always-mounted panels" property is now verified against the
    // post-generation free-switching window.
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    // Alter-ego is active + poster visible; now switch back and forth.
    await page.getByRole('tab', { name: '1 Setup' }).click()
    await page.getByRole('tab', { name: '2 Your Alter Ego' }).click()
    await page.getByRole('tab', { name: '1 Setup' }).click()

    // Selections survive every switch.
    await expect(page.getByRole('radio', { name: 'Heroic' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    await expect(page.getByRole('radio', { name: 'Cloud Architect' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    await expect(page.getByLabel('First name')).toHaveValue('Paula')
  })

  test('structural match at 1440 px + archived screenshot (SC-106 / T070)', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')

    // Required mockup elements, per spec FR-110..FR-121.
    await expect(page.getByRole('heading', { name: /YOUR PHOTO/i })).toBeVisible()
    await expect(page.getByText(/real face as a direct reference/i)).toBeVisible()
    await expect(page.getByRole('heading', { name: /ROLE, UNIVERSE & KEYS/i })).toBeVisible()

    // Sub-group counts per FR-116/117/118 + the 002 Pose addition + 006 Art style.
    // Pose (4) + Engineer role (6) + Universe (6) + Art style (9) = 25 radios.
    // Vibe (4) uses the toggle-button pattern — aria-pressed is set on each pill.
    await expect(page.getByRole('radio')).toHaveCount(25)
    await expect(page.locator('[role="button"][aria-pressed]')).toHaveCount(4)

    // Archive the 1440 px screenshot into test-results/ — this is the
    // visual-diff artifact referenced by SC-106 / T070. Manual reviewer
    // compares it side-by-side with specs/002-sleek-tabbed-ui/mockup.png.
    await page.screenshot({
      path: 'test-results/setup-tab-1440.png',
      fullPage: true,
    })
  })
})

/**
 * 005 FR-405 — users who opted out of motion get the pre-005 instant
 * switch. The React-state attribute `data-animating` still latches (it
 * is state-driven, not media-query-driven) so this same assertion works
 * in both environments; the visual difference is in CSS computed values.
 */
test.describe('Tabs shell — prefers-reduced-motion', () => {
  test('auto-switch on Generate does not run the CSS animation under reduce-motion', async ({
    page,
  }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.route('**/api/v1/alter-egos', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 500))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          character: {
            heroTitleLine1: 'PAULA',
            heroTitleLine2: 'The Cloud Guardrail',
            tagline: 'STILL SHIPS ON FRIDAYS.',
            superpowers: ['a', 'b', 'c'],
            quote: 'q',
          },
          poster: {
            dataUrl:
              'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkAAIAAAoAAv/lPAAAAABJRU5ErkJggg==',
            mediaType: 'image/png',
            widthPx: 900,
            heightPx: 1200,
          },
          meta: { outcome: 'real', correlationId: 'x' },
        }),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()

    const alterEgoPanel = page.locator('[id$="-panel-alter-ego"]')
    // The ARIA state still flips instantly — the reduced-motion override
    // does not compromise the 002 FR-108 contract.
    await expect(page.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    // The CSS `animation-name` on the incoming panel resolves to `none`
    // under the media-query override, even though the React-state
    // `data-animating` attribute briefly latches `true`.
    await expect(alterEgoPanel).toHaveCSS('animation-name', 'none')
  })
})

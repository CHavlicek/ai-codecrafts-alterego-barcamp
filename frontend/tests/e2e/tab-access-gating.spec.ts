import { expect, test } from '@playwright/test'
import { buildSampleResponse, fillAllSelections, mockHappyApi } from './helpers'

/**
 * 007 — end-to-end tab access gating. Drives spec 007/spec.md
 * user stories US1..US4 through the rendered browser.
 *
 * US1: The "2 Your Alter Ego" tab is disabled before any generation resolves.
 * US2: The "1 Setup" tab is disabled while a generation is in flight.
 * US3: Once a generation resolves (succeeded OR failed-with-fallback) both
 *      tabs are freely interchangeable.
 * US4: Start-over restores the first-load gating (Setup active, alter-ego
 *      disabled).
 */

test.describe('007 — tab access gating', () => {
  test('US1: alter-ego tab is disabled on first load (aria-disabled + tabindex=-1 + click no-op)', async ({
    page,
  }) => {
    await page.goto('/')
    const alterEgoTab = page.getByRole('tab', { name: '2 Your Alter Ego' })
    await expect(alterEgoTab).toHaveAttribute('aria-disabled', 'true')
    await expect(alterEgoTab).toHaveAttribute('tabindex', '-1')
    await expect(alterEgoTab).toHaveAttribute('data-disabled', 'true')

    // Clicking must not flip the active tab. Use { force: true } so
    // Playwright delivers the click past `pointer-events: none`-style
    // CSS if any; the DOM is still correct — aria-selected does not flip.
    await alterEgoTab.click({ force: true })
    await expect(page.getByRole('tab', { name: '1 Setup' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(alterEgoTab).toHaveAttribute('aria-selected', 'false')

    // Setup panel is still the visible one; alter-ego panel still hidden.
    await expect(page.locator('[id$="-panel-alter-ego"]')).toBeHidden()
  })

  test('US1: keyboard focus cannot land on the disabled alter-ego tab via ArrowRight', async ({
    page,
  }) => {
    await page.goto('/')
    const setupTab = page.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = page.getByRole('tab', { name: '2 Your Alter Ego' })
    await setupTab.focus()
    await page.keyboard.press('ArrowRight')
    // Only one enabled tab → focus stays on setup.
    await expect(setupTab).toBeFocused()
    await expect(alterEgoTab).not.toBeFocused()
    // Enter on the still-focused setup tab is a no-op (already selected).
    await page.keyboard.press('Enter')
    await expect(setupTab).toHaveAttribute('aria-selected', 'true')
  })

  test('US2: setup tab is disabled while a generation is in flight', async ({ page }) => {
    // Defer the response so we have a window to inspect mid-flight state.
    await page.route('**/api/v1/alter-egos', async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 900))
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()

    // Mid-flight: alter-ego is now active, setup is disabled, loading state
    // is visible.
    const setupTab = page.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = page.getByRole('tab', { name: '2 Your Alter Ego' })
    await expect(alterEgoTab).toHaveAttribute('aria-selected', 'true')
    await expect(setupTab).toHaveAttribute('aria-disabled', 'true')
    await expect(setupTab).toHaveAttribute('tabindex', '-1')

    // Attempt to click Setup — the click must be ignored.
    await setupTab.click({ force: true })
    await expect(alterEgoTab).toHaveAttribute('aria-selected', 'true')

    // Wait for resolution and confirm the gates lift.
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    await expect(setupTab).not.toHaveAttribute('aria-disabled', 'true')
    await expect(alterEgoTab).not.toHaveAttribute('aria-disabled', 'true')
  })

  test('US3: after a successful generation, both tabs are fully interchangeable', async ({
    page,
  }) => {
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    const setupTab = page.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = page.getByRole('tab', { name: '2 Your Alter Ego' })

    // Free switching — both directions.
    await setupTab.click()
    await expect(setupTab).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByLabel('First name')).toHaveValue('Paula')

    await alterEgoTab.click()
    await expect(alterEgoTab).toHaveAttribute('aria-selected', 'true')
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    // Keyboard round-trip also works — neither tab is disabled anymore.
    await alterEgoTab.focus()
    await page.keyboard.press('ArrowLeft')
    await expect(setupTab).toBeFocused()
    await page.keyboard.press('Enter')
    await expect(setupTab).toHaveAttribute('aria-selected', 'true')
  })

  test('US4: Start-over restores the first-load gating', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    await page.getByRole('button', { name: /start over/i }).click()

    const setupTab = page.getByRole('tab', { name: '1 Setup' })
    const alterEgoTab = page.getByRole('tab', { name: '2 Your Alter Ego' })
    await expect(setupTab).toHaveAttribute('aria-selected', 'true')
    await expect(alterEgoTab).toHaveAttribute('aria-disabled', 'true')
    await expect(alterEgoTab).toHaveAttribute('tabindex', '-1')
    // Setup inputs are reset.
    await expect(page.getByLabel('First name')).toHaveValue('')
    // Clicking the disabled tab is still a no-op (parity with fresh load).
    await alterEgoTab.click({ force: true })
    await expect(setupTab).toHaveAttribute('aria-selected', 'true')
  })
})

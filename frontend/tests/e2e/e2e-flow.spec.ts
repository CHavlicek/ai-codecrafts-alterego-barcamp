import { expect, test } from '@playwright/test'
import { buildSampleResponse, fillAllSelections, mockHappyApi } from './helpers'

/**
 * T035 — Full end-to-end flow on the 002 UI, including the re-generate
 * path (FR-124 — pressing Generate again with a previous poster replaces
 * it without requiring Start-over).
 */

test.describe('002 end-to-end flow', () => {
  test('full happy path: Setup → auto-switch → poster → Start-over', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')

    // User lands on the Setup tab.
    await expect(page.getByRole('tab', { name: '1 Setup' })).toHaveAttribute(
      'aria-selected',
      'true',
    )

    // Fill Setup.
    await fillAllSelections(page, 'Paula')

    // Submit.
    await page.getByRole('button', { name: /generate my alter ego/i }).click()

    // Auto-switch to the Alter Ego tab (FR-108).
    await expect(page.getByRole('tab', { name: '2 Your Alter Ego' })).toHaveAttribute(
      'aria-selected',
      'true',
    )

    // Poster arrives.
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    await expect(page.getByText('The Cloud Guardrail')).toBeVisible()

    // Start-over resets + returns to tab 1.
    await page.getByRole('button', { name: /start over/i }).click()
    await expect(page.getByRole('tab', { name: '1 Setup' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
  })

  test('re-generate without Start-over (FR-124): old poster replaced by new one', async ({
    page,
  }) => {
    let callCount = 0
    await page.route('**/api/v1/alter-egos', async (route) => {
      callCount += 1
      const body =
        callCount === 1
          ? buildSampleResponse({ heroTitleLine2: 'The Cloud Guardrail' })
          : buildSampleResponse({ heroTitleLine2: 'The Backend Dev' })
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(body),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByText('The Cloud Guardrail')).toBeVisible()

    // Switch back to Setup, change the role, re-Generate. No Start-over.
    await page.getByRole('tab', { name: '1 Setup' }).click()
    await page.getByRole('radio', { name: 'Backend Dev' }).click()
    await page.getByRole('button', { name: /generate my alter ego/i }).click()

    // Auto-switch fires again; old poster is replaced by the new one.
    await expect(page.getByText('The Backend Dev')).toBeVisible()
    await expect(page.getByText('The Cloud Guardrail')).toHaveCount(0)
  })

  test('Setup inputs survive a post-generation tab switch (SC-105)', async ({ page }) => {
    // 007 US1: the alter-ego tab is gated until a generation resolves, so
    // SC-105's "panel state preserved across tab switches" invariant is
    // now verified after the user has a poster.
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    // Cross back to Setup then over to tab 2 again.
    await page.getByRole('tab', { name: '1 Setup' }).click()
    await expect(page.getByRole('radio', { name: 'Heroic' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    await page.getByRole('tab', { name: '2 Your Alter Ego' }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    await page.getByRole('tab', { name: '1 Setup' }).click()

    // Every selection still there.
    await expect(page.getByRole('radio', { name: 'Heroic' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    await expect(page.getByRole('radio', { name: 'Cloud Architect' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    await expect(page.getByRole('radio', { name: 'Star Wars' })).toHaveAttribute(
      'aria-checked',
      'true',
    )
    await expect(page.getByLabel('First name')).toHaveValue('Paula')
  })

  test('Vibe is optional: full flow with vibe omitted succeeds', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    // fillAllSelections intentionally leaves vibe unselected.
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
  })
})

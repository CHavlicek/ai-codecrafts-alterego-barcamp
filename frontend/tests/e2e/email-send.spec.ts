import { expect, test } from '@playwright/test'
import { fillAllSelections, mockHappyApi } from './helpers'

/**
 * 023 (issue #57) — E2E happy path + not-configured branch for the
 * Send As Email action.
 *
 * Drives the full user journey end-to-end against the real Vite-built
 * React app, with the Generate backend call mocked (helpers.mockHappyApi)
 * and the new POST /api/v1/alter-egos/email route mocked per-test for
 * the three terminal outcomes the FE classifies.
 */

test.describe('Email send', () => {
  test('valid email + happy backend → "Email sent" alert', async ({ page }) => {
    await mockHappyApi(page)
    await page.route('**/api/v1/alter-egos/email', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'sent' }),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByLabel('Email').fill('someone@example.com')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    // The native alert is captured via page.on('dialog', ...).
    const dialogPromise = page.waitForEvent('dialog')
    await page.getByRole('button', { name: /send as email/i }).click()
    const dialog = await dialogPromise
    expect(dialog.message()).toBe('Email sent to someone@example.com.')
    await dialog.accept()
  })

  test('not-configured backend → "email server is not yet configured" alert', async ({ page }) => {
    await mockHappyApi(page)
    await page.route('**/api/v1/alter-egos/email', async (route) => {
      await route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify({
          type: 'https://aiavatar.local/problems/email/not-configured',
          title: 'Email service is not configured',
          status: 503,
          detail: 'The mail server is not configured.',
        }),
      })
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByLabel('Email').fill('someone@example.com')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    const dialogPromise = page.waitForEvent('dialog')
    await page.getByRole('button', { name: /send as email/i }).click()
    const dialog = await dialogPromise
    expect(dialog.message()).toBe('The email server is not yet configured.')
    await dialog.accept()
  })

  test('Send As Email is disabled when the email is blank', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')
    // Skip the email field entirely.
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    await expect(page.getByRole('button', { name: /send as email/i })).toBeDisabled()
  })

  test('Send As Email is disabled when the email is malformed', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')

    // Generate should also be blocked by the malformed email.
    await page.getByLabel('Email').fill('not-an-email')
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()

    // Correcting the email re-enables Generate.
    await page.getByLabel('Email').fill('someone@example.com')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    await expect(page.getByRole('button', { name: /send as email/i })).toBeEnabled()
  })
})

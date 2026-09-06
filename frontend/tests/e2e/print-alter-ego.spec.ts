import { expect, test } from '@playwright/test'
import { fillAllSelections, mockHappyApi } from './helpers'

/**
 * Print alter ego — end-to-end (010 + iframe-print rewrite).
 *
 * Drives the full happy path (Setup → Generate → poster), then exercises
 * the Print button. We cannot observe the native print dialog through
 * Playwright (it's owned by the browser/OS), but we can assert that
 * pressing Print creates the hidden print iframe with the right content
 * and that no network / storage side effects occurred.
 */

test.describe('print-alter-ego', () => {
  test.beforeEach(async ({ page }) => {
    // Install a window.print seam in EVERY frame (covers the print iframe
    // we inject on click). We can't observe the OS print dialog, but we
    // can count print() invocations across the page tree.
    await page.addInitScript(() => {
      ;(window as unknown as { __printCalls: number }).__printCalls = 0
      window.print = () => {
        // Propagate the count to the top frame so the test can read it.
        const top = window.top as unknown as { __printCalls?: number }
        if (top) top.__printCalls = (top.__printCalls ?? 0) + 1
      }
    })
  })

  test('Print button is absent before generation', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')

    // Pre-generation: the Alter Ego tab is locked (007). The Print button
    // MUST NOT be in the DOM, and no print iframe should exist either.
    await expect(page.getByRole('button', { name: /print my alter ego/i })).toHaveCount(0)
    await expect(page.locator('iframe[title="print preview"]')).toHaveCount(0)
  })

  test('Generate → Print mounts the print iframe and invokes window.print()', async ({ page }) => {
    await mockHappyApi(page)

    // Zero-network assertion surface: count every request made AFTER the
    // initial generate call has settled. We arm the counter when we press
    // Print and disarm it after the second click.
    let postPrintRequestCount = 0
    let countingRequests = false
    page.on('request', () => {
      if (countingRequests) postPrintRequestCount += 1
    })

    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    const printBtn = page.getByRole('button', { name: /print my alter ego/i })
    await expect(printBtn).toBeVisible()
    // No hidden print artefact exists in the main document — the print
    // path is now an on-demand iframe injected by the button.
    await expect(page.locator('iframe[title="print preview"]')).toHaveCount(0)

    countingRequests = true
    await printBtn.click()

    // After click, the print iframe is appended and contains an <img>.
    const iframe = page.locator('iframe[title="print preview"]')
    await expect(iframe).toHaveCount(1)
    const inner = iframe.contentFrame()
    await expect(inner.locator('img')).toHaveCount(1)

    // print() inside the iframe fires. Our addInitScript propagates the
    // count to the top frame; wait briefly for the load+print pipeline.
    await expect
      .poll(
        async () =>
          await page.evaluate(() => (window as unknown as { __printCalls: number }).__printCalls),
      )
      .toBe(1)

    // The iframe self-removes after a short delay; advance the clock by
    // waiting it out, then verify it's gone.
    await expect(iframe).toHaveCount(0, { timeout: 3000 })

    // Zero new network requests fired as a result of the Print click.
    countingRequests = false
    expect(postPrintRequestCount).toBe(0)

    // Zero bytes written to cookies / localStorage / sessionStorage.
    const storageAfter = await page.evaluate(() => ({
      local: Object.keys(localStorage).length,
      session: Object.keys(sessionStorage).length,
      cookie: document.cookie,
    }))
    expect(storageAfter.local).toBe(0)
    expect(storageAfter.session).toBe(0)
    expect(storageAfter.cookie).toBe('')

    // Start Over unmounts Print together with the poster.
    await page.getByRole('button', { name: /start over/i }).click()
    await expect(page.getByRole('button', { name: /print my alter ego/i })).toHaveCount(0)
  })

  test('Print is keyboard-operable: Enter / Space invoke print', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await page.getByRole('button', { name: /generate my alter ego/i }).click()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    await page.getByRole('button', { name: /print my alter ego/i }).focus()
    await page.keyboard.press('Enter')
    await expect
      .poll(
        async () =>
          await page.evaluate(() => (window as unknown as { __printCalls: number }).__printCalls),
      )
      .toBe(1)

    // Wait for the first iframe to be torn down before the next press,
    // otherwise the locator finds two iframes momentarily.
    await expect(page.locator('iframe[title="print preview"]')).toHaveCount(0, { timeout: 3000 })

    await page.getByRole('button', { name: /print my alter ego/i }).focus()
    await page.keyboard.press('Space')
    await expect
      .poll(
        async () =>
          await page.evaluate(() => (window as unknown as { __printCalls: number }).__printCalls),
      )
      .toBe(2)
  })
})

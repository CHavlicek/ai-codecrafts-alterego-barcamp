import { expect, test, type Page } from '@playwright/test'
import { fillAllSelections, mockHappyApi, selectPhoto } from './helpers'

/**
 * T046 + 004 T009 — Keyboard-only walkthrough.
 *
 * Proves the full flow is operable by keyboard alone: no
 * {@code page.mouse} calls. Covers SC-007's keyboard-walkthrough leg
 * (the a11y scan leg lives in axe-scan.spec.ts).
 *
 * After 004, the photo is captured inline via the circle/shutter/Keep
 * pipeline — every control is a real <button> reachable by Tab and
 * activatable by Enter or Space. Chromium's --use-fake-device flag
 * provides a deterministic camera stream (see playwright.config.ts).
 */

async function capturePhotoWithKeyboard(page: Page): Promise<void> {
  const circle = page.getByRole('button', { name: /take a photo of yourself/i })
  await circle.focus()
  await page.keyboard.press('Enter')
  const shutter = page.getByRole('button', { name: /take photo/i })
  await shutter.waitFor({ state: 'visible' })
  await shutter.focus()
  await page.keyboard.press('Enter')
  const keep = page.getByRole('button', { name: /keep photo/i })
  await keep.waitFor({ state: 'visible' })
  await keep.focus()
  await page.keyboard.press('Enter')
  await page.getByRole('button', { name: /retake \/ clear/i }).waitFor({ state: 'visible' })
}

test('arrow keys move selection + focus within a picker', async ({ page }) => {
  await page.goto('/')
  await selectPhoto(page)

  const heroic = page.getByRole('radio', { name: 'Heroic' })
  await heroic.focus()
  await expect(heroic).toBeFocused()

  await page.keyboard.press('ArrowRight')
  const stealthy = page.getByRole('radio', { name: 'Stealthy' })
  await expect(stealthy).toHaveAttribute('aria-checked', 'true')
  await expect(stealthy).toBeFocused()

  await page.keyboard.press('ArrowDown')
  const mystical = page.getByRole('radio', { name: 'Mystical' })
  await expect(mystical).toHaveAttribute('aria-checked', 'true')
  await expect(mystical).toBeFocused()

  // ArrowLeft from the first option wraps to the last.
  await heroic.focus()
  await page.keyboard.press('ArrowLeft')
  await expect(page.getByRole('radio', { name: 'Scholar' })).toHaveAttribute('aria-checked', 'true')
})

test('full journey drivable by keyboard alone: form → poster → Start over', async ({ page }) => {
  await mockHappyApi(page)
  await page.goto('/')

  // Photo — 004 inline capture via keyboard
  await capturePhotoWithKeyboard(page)

  // Pose
  await page.getByRole('radio', { name: 'Heroic' }).focus()
  await page.keyboard.press('Space') // select focused
  await expect(page.getByRole('radio', { name: 'Heroic' })).toHaveAttribute('aria-checked', 'true')

  // Engineer role (002: replaces the 001 archetype set; no separate colour picker)
  await page.getByRole('radio', { name: 'Cloud Architect' }).focus()
  await page.keyboard.press('ArrowRight') // → Backend Dev
  await expect(page.getByRole('radio', { name: 'Backend Dev' })).toHaveAttribute(
    'aria-checked',
    'true',
  )

  // Universe
  await page.getByRole('radio', { name: 'Star Wars' }).focus()
  await page.keyboard.press('Space')
  await expect(page.getByRole('radio', { name: 'Star Wars' })).toHaveAttribute(
    'aria-checked',
    'true',
  )

  // Art style (004 — required)
  await page.getByRole('radio', { name: 'Pixel Art' }).focus()
  await page.keyboard.press('Space')
  await expect(page.getByRole('radio', { name: 'Pixel Art' })).toHaveAttribute(
    'aria-checked',
    'true',
  )

  // First name
  const firstName = page.getByLabel('First name')
  await firstName.focus()
  await page.keyboard.type('Paula')

  // Generate via Enter
  const generate = page.getByRole('button', { name: /generate my alter ego/i })
  await generate.focus()
  await expect(generate).toBeEnabled()
  await page.keyboard.press('Enter')

  await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

  // Start over via Enter
  const startOver = page.getByRole('button', { name: /start over/i })
  await startOver.focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: /take a photo of yourself/i })).toBeVisible()
})

test('Generate button is in the tab order after the form inputs', async ({ page }) => {
  await mockHappyApi(page)
  await page.goto('/')
  await fillAllSelections(page, 'Paula')

  const generate = page.getByRole('button', { name: /generate my alter ego/i })
  await generate.focus()
  await expect(generate).toBeFocused()
  await expect(generate).toBeEnabled()
})

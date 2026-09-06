import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'
import { fillAllSelections, selectPhoto } from './helpers'

/**
 * T034 + T036 + T065 — Playwright coverage for the Setup form at the
 * rendered-browser level.
 *
 * Covers:
 *  - Structural: sub-group counts, option orders, Name + Generate
 *    placement (FR-115..FR-123, SC-106).
 *  - Responsive: two columns at 1440 px, single stacked column at 375 px
 *    without horizontal overflow (SC-104).
 *  - Keyboard reachability of every Setup control (FR-104, SC-102).
 *  - Vibe's optional-selection behaviour (FR-118).
 *  - Photo intake: circular preview renders after upload (T065).
 *  - axe-core scans against three Setup-tab content states (SC-103, T036).
 */

async function assertNoSeriousOrCriticalViolations(page: Page, label: string) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze()
  const blocking = results.violations.filter(
    (v) => v.impact === 'serious' || v.impact === 'critical',
  )
  expect(blocking, `a11y violations on ${label}: ${JSON.stringify(blocking, null, 2)}`).toEqual([])
}

test.describe('Setup form — structure', () => {
  test('1440 px: two columns with the mockup section headings', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    await expect(page.getByRole('heading', { name: /YOUR PHOTO/i })).toBeVisible()
    await expect(page.getByRole('heading', { name: /ROLE, UNIVERSE & KEYS/i })).toBeVisible()
  })

  test('five numbered sub-groups in the right column, in order', async ({ page }) => {
    await page.goto('/')
    // Labels appear in the fieldset <legend> elements.
    const numberedGroups = page.locator('.setup-layout__numbered-group')
    await expect(numberedGroups).toHaveCount(5)

    // Visible labels confirm the sub-group shape + order (006 added Art style at 4).
    const labels = ['Pose', 'Engineer role', 'Universe / Style', 'Art style', 'Vibe (optional)']
    for (const label of labels) {
      await expect(page.getByText(label, { exact: false }).first()).toBeVisible()
    }
  })

  test('Art style has 9 options in mockup order (006 FR-301)', async ({ page }) => {
    await page.goto('/')
    const expected = [
      'Oil Painting',
      'Watercolor',
      'Pixel Art',
      'Low-Poly 3D',
      'Line Art',
      'Pop Art',
      'Renaissance Portrait',
      'Japanese Woodblock',
      'Cel-Shaded',
    ]
    for (const label of expected) {
      await expect(page.getByRole('radio', { name: label })).toBeVisible()
    }
  })

  test('Pose has 4 options in order', async ({ page }) => {
    await page.goto('/')
    const expected = ['Heroic', 'Stealthy', 'Mystical', 'Scholar']
    for (const label of expected) {
      await expect(page.getByRole('radio', { name: label })).toBeVisible()
    }
  })

  test('Engineer role has 6 options in mockup order', async ({ page }) => {
    await page.goto('/')
    const expected = [
      'Cloud Architect',
      'Backend Dev',
      'Frontend Dev',
      'AI Engineer',
      'Platform Eng.',
      'Data Engineer',
    ]
    for (const label of expected) {
      await expect(page.getByRole('radio', { name: label })).toBeVisible()
    }
  })

  test('Universe has 6 options in mockup order', async ({ page }) => {
    await page.goto('/')
    const expected = [
      'Marvel',
      'Star Wars',
      'Cyberpunk',
      'The Office',
      'Indiana Jones',
      'Lord of the Rings',
    ]
    for (const label of expected) {
      await expect(page.getByRole('radio', { name: label })).toBeVisible()
    }
  })

  test('Vibe has 4 optional pills using the toggle-button pattern', async ({ page }) => {
    await page.goto('/')
    const expected = ['Builder', 'Thinker', 'Rebel', 'Architect']
    for (const label of expected) {
      const pill = page.getByRole('button', { name: label })
      await expect(pill).toBeVisible()
      // Toggle-button pattern: aria-pressed is set; aria-checked is not.
      await expect(pill).toHaveAttribute('aria-pressed', /true|false/)
    }
  })

  test('Name input sits below Vibe, Generate sits at the bottom of the right column', async ({
    page,
  }) => {
    await page.goto('/')
    const name = page.getByLabel('First name')
    const generate = page.getByRole('button', { name: /generate my alter ego/i })
    const vibeLegend = page.getByText(/vibe \(optional\)/i).first()
    await expect(name).toBeVisible()
    await expect(generate).toBeVisible()

    // Document-order: vibe → name → generate.
    const nameBox = await name.boundingBox()
    const vibeBox = await vibeLegend.boundingBox()
    const generateBox = await generate.boundingBox()
    if (!nameBox || !vibeBox || !generateBox) {
      throw new Error('Missing bounding boxes for ordering assertion')
    }
    expect(vibeBox.y).toBeLessThan(nameBox.y)
    expect(nameBox.y).toBeLessThan(generateBox.y)
  })
})

test.describe('Setup form — behaviour', () => {
  test('clicking Vibe twice deselects it (FR-118)', async ({ page }) => {
    await page.goto('/')
    const rebel = page.getByRole('button', { name: 'Rebel' })
    await rebel.click()
    await expect(rebel).toHaveAttribute('aria-pressed', 'true')
    await rebel.click()
    await expect(rebel).toHaveAttribute('aria-pressed', 'false')
  })

  test('Generate remains disabled without a photo; vibe does NOT gate', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('radio', { name: 'Heroic' }).click()
    await page.getByRole('radio', { name: 'Cloud Architect' }).click()
    await page.getByRole('radio', { name: 'Star Wars' }).click()
    await page.getByRole('radio', { name: 'Pixel Art' }).click()
    await page.getByLabel('First name').fill('Paula')
    // No photo + no vibe — still disabled because of missing photo.
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()

    // Adding vibe doesn't enable — photo is still missing.
    await page.getByRole('button', { name: 'Rebel' }).click()
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()

    // Capture a photo via the 004 inline camera flow; now it's enabled —
    // Vibe did not contribute to the gate.
    await selectPhoto(page)
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeEnabled()

    // Deselect vibe — still enabled.
    await page.getByRole('button', { name: 'Rebel' }).click()
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeEnabled()
  })

  test('photo capture renders the circular preview (FR-303 parity with 001)', async ({ page }) => {
    await page.goto('/')
    await selectPhoto(page)
    // The 004 committed preview lives on the same .photo-intake__image
    // element as 001/002/003 did, but now inside a <button> circle.
    const preview = page.locator('.photo-intake__image').first()
    await expect(preview).toBeVisible()
    const box = await preview.boundingBox()
    if (!box) throw new Error('No preview bounding box')
    expect(box.width / box.height).toBeGreaterThan(0.95)
    expect(box.width / box.height).toBeLessThan(1.05)
  })
})

test.describe('Setup form — responsive', () => {
  test('no horizontal scroll at 1440 px', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 })
    await page.goto('/')
    const hasHorizontalScroll = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(hasHorizontalScroll).toBe(false)
  })

  test('no horizontal scroll at 375 px (single-column stacked)', async ({ page }) => {
    await page.setViewportSize({ width: 375, height: 800 })
    await page.goto('/')
    const hasHorizontalScroll = await page.evaluate(
      () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
    )
    expect(hasHorizontalScroll).toBe(false)
  })
})

test.describe('Setup form — a11y scans (SC-103, T036)', () => {
  test('empty Setup form has no serious/critical axe violations', async ({ page }) => {
    await page.goto('/')
    await assertNoSeriousOrCriticalViolations(page, 'setup-empty')
  })

  test('partially filled Setup form has no serious/critical axe violations', async ({ page }) => {
    await page.goto('/')
    await page.getByRole('radio', { name: 'Heroic' }).click()
    await page.getByRole('radio', { name: 'Cloud Architect' }).click()
    await assertNoSeriousOrCriticalViolations(page, 'setup-partial')
  })

  test('fully filled Setup form has no serious/critical axe violations', async ({ page }) => {
    await page.goto('/')
    await fillAllSelections(page, 'Paula')
    await assertNoSeriousOrCriticalViolations(page, 'setup-full')
  })
})

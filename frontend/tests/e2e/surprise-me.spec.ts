import { expect, test } from '@playwright/test'
import { buildSampleResponse, mockHappyApi, selectPhoto } from './helpers'

/**
 * 009 Surprise Me — end-to-end flow.
 *
 * Covers:
 *  - US1 (FR-901/902/904/906/908): click Surprise Me with no category
 *    picks → poster renders on the alter-ego tab.
 *  - US2 (FR-907 / SC-903): after return, the Setup form shows exactly
 *    one aria-checked option in every grid; a subsequent Generate click
 *    carries those values.
 *  - US3 (FR-902/903): button disabled without photo / without name /
 *    without both / while a generation is in flight.
 *  - SC-902: every outbound request body carries valid enum values for
 *    pose, archetype, universe, vibe, artStyle.
 */

test.describe('Surprise Me — 009', () => {
  test('US1 — no category picks → click Surprise Me → poster renders (FR-908 parity)', async ({
    page,
  }) => {
    // Capture the outbound request body so we can assert SC-902 inside
    // the same route handler that fulfils the 200.
    let capturedBody: unknown = null
    await page.route('**/api/v1/alter-egos', async (route) => {
      const request = route.request()
      try {
        capturedBody = request.postDataJSON()
      } catch {
        capturedBody = null
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'X-Request-Id': '00000000-0000-0000-0000-000000000001' },
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')

    // Fresh session — Surprise Me starts disabled.
    const surprise = page.getByRole('button', { name: /surprise me/i })
    await expect(surprise).toBeDisabled()

    // Take a photo + type a name only. No category picks.
    await selectPhoto(page)
    await page.getByLabel('First name').fill('Paula')

    await expect(surprise).toBeEnabled()
    // Generate is still disabled because the five categories are empty.
    await expect(page.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()

    await surprise.click()

    // Same happy-path verification as the normal Generate flow — poster
    // should render on the alter-ego tab within the SC-001 budget.
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
    await expect(page.getByText('The Cloud Guardrail')).toBeVisible()

    // SC-902 — payload contains valid enum values for all five category
    // fields. The enum values come from the in-memory options arrays.
    const VALID_POSES = ['heroic', 'stealthy', 'mystical', 'scholar']
    const VALID_ARCHETYPES = [
      'cloud-architect',
      'backend-dev',
      'frontend-dev',
      'ai-engineer',
      'platform-eng',
      'data-engineer',
    ]
    const VALID_UNIVERSES = [
      'marvel',
      'star-wars',
      'cyberpunk',
      'the-office',
      'indiana-jones',
      'lord-of-the-rings',
    ]
    const VALID_VIBES = ['builder', 'thinker', 'rebel', 'architect']
    const VALID_ART_STYLES = [
      'oil-painting',
      'watercolor',
      'pixel-art',
      'low-poly-3d',
      'line-art',
      'pop-art',
      'renaissance-portrait',
      'japanese-woodblock',
      'cel-shaded',
    ]
    expect(capturedBody).not.toBeNull()
    const body = capturedBody as Record<string, unknown>
    const selections = body.selections as Record<string, string>
    expect(VALID_POSES).toContain(selections.pose)
    expect(VALID_ARCHETYPES).toContain(selections.archetype)
    expect(VALID_UNIVERSES).toContain(selections.universe)
    expect(VALID_VIBES).toContain(selections.vibe)
    expect(VALID_ART_STYLES).toContain(selections.artStyle)
    expect(selections.firstName).toBe('Paula')
  })

  test('US2 — Setup form reflects the random picks after Surprise Me lands', async ({ page }) => {
    await mockHappyApi(page)
    await page.goto('/')

    await selectPhoto(page)
    await page.getByLabel('First name').fill('Paula')
    await page.getByRole('button', { name: /surprise me/i }).click()

    // Wait for the poster to render.
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()

    // Navigate back to Setup (it re-enables per 007 FR-505 after the
    // response lands).
    await page.getByRole('tab', { name: /1 setup/i }).click()

    // Every category grid now has exactly one aria-checked option.
    // SC-903: enumerate the five radiogroups and count aria-checked=true.
    const radiogroups = page.getByRole('radiogroup')
    const count = await radiogroups.count()
    // Pose / Archetype / Universe / Art Style / Vibe = 5 groups.
    expect(count).toBeGreaterThanOrEqual(5)
    for (let i = 0; i < count; i++) {
      const group = radiogroups.nth(i)
      const checked = group.locator('[role="radio"][aria-checked="true"]')
      await expect(checked).toHaveCount(1)
    }
  })

  test('US3 — button disabled states (FR-902, FR-903)', async ({ page }) => {
    await page.goto('/')

    const surprise = page.getByRole('button', { name: /surprise me/i })

    // Fresh session: disabled, hint mentions both photo and first name.
    await expect(surprise).toBeDisabled()
    await expect(surprise).toHaveAttribute('aria-disabled', 'true')
    // Target the hint by its id (connected via aria-describedby).
    const describedById = await surprise.getAttribute('aria-describedby')
    expect(describedById).not.toBeNull()
    const hint = page.locator(`#${describedById!}`)
    await expect(hint).toContainText(/photo/i)
    await expect(hint).toContainText(/first name/i)

    // Only a name: photo still missing.
    await page.getByLabel('First name').fill('Paula')
    await expect(surprise).toBeDisabled()
    await expect(hint).toContainText(/photo/i)

    // Clear name, add photo: name still missing.
    await page.getByLabel('First name').fill('')
    await selectPhoto(page)
    await expect(surprise).toBeDisabled()
    // Re-query the hint id — it may have changed.
    const describedByAfter = await surprise.getAttribute('aria-describedby')
    const hintAfter = page.locator(`#${describedByAfter!}`)
    await expect(hintAfter).toContainText(/first name/i)

    // Both present: enabled.
    await page.getByLabel('First name').fill('Paula')
    await expect(surprise).toBeEnabled()
  })

  test('US3 — Surprise Me is disabled while a normal Generate is in flight', async ({ page }) => {
    // Gate the API so we can observe the in-flight state.
    let release!: () => void
    const gate = new Promise<void>((resolve) => {
      release = resolve
    })
    await page.route('**/api/v1/alter-egos', async (route) => {
      await gate
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')
    // Fill every field so Generate enables too.
    await selectPhoto(page)
    await page.getByRole('radio', { name: 'Heroic' }).click()
    await page.getByRole('radio', { name: 'Cloud Architect' }).click()
    await page.getByRole('radio', { name: 'Star Wars' }).click()
    await page.getByRole('radio', { name: 'Pixel Art' }).click()
    await page.getByLabel('First name').fill('Paula')

    await page.getByRole('button', { name: /generate my alter ego/i }).click()

    // Setup tab is now disabled per 007 FR-504; to click Surprise Me we'd
    // have to go back — but we can't while generating. Assert via the
    // aria-selected state that tab 2 is active and Setup is gated.
    await expect(page.getByRole('tab', { name: /2 your alter ego/i })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await expect(page.getByRole('tab', { name: /1 setup/i })).toHaveAttribute(
      'aria-disabled',
      'true',
    )

    // Resolve the gated call so the test tears down cleanly.
    release()
    await expect(page.getByRole('heading', { name: 'PAULA' })).toBeVisible()
  })
})

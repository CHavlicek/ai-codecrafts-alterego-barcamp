import { expect, test } from '@playwright/test'
import { buildSampleResponse, selectPhoto } from './helpers'

/**
 * 028 partial-surprise-me — end-to-end coverage for "preserve explicit picks".
 *
 * US1 — user picks Universe explicitly, leaves Role and Art Style empty,
 *       clicks Surprise Me → outbound payload's `universe` matches the
 *       pick; `archetype` + `artStyle` are randomly drawn. Setup tab on
 *       return still highlights the user's Universe.
 *
 * US2 — user types a non-empty Custom Role, leaves Universe + Art Style
 *       empty, clicks Surprise Me → outbound carries `customRole` (trimmed)
 *       and `archetype` is omitted. Setup tab on return still displays
 *       the typed string verbatim.
 *
 * The wire body is multipart/form-data — we read `request.postData()` and
 * regex-match the JSON blob, following the pattern used by
 * `group-photos.spec.ts`.
 */

const VALID_ARCHETYPES = [
  'cloud-architect',
  'backend-dev',
  'frontend-dev',
  'ai-engineer',
  'platform-eng',
  'data-engineer',
  'hr',
  'administration',
  'customer-relations',
]
const VALID_UNIVERSES = [
  'marvel',
  'star-wars',
  'cyberpunk',
  'the-office',
  'indiana-jones',
  'lord-of-the-rings',
]
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

interface CapturedSelections {
  archetype?: string
  universe?: string
  artStyle?: string
  customRole?: string
}

function parseSelectionsFromMultipart(multipart: string): CapturedSelections {
  const out: CapturedSelections = {}
  const archetype = multipart.match(/"archetype":"([^"]+)"/)
  const universe = multipart.match(/"universe":"([^"]+)"/)
  const artStyle = multipart.match(/"artStyle":"([^"]+)"/)
  const customRole = multipart.match(/"customRole":"([^"]+)"/)
  if (archetype) out.archetype = archetype[1]!
  if (universe) out.universe = universe[1]!
  if (artStyle) out.artStyle = artStyle[1]!
  if (customRole) out.customRole = customRole[1]!
  return out
}

test.describe('Partial Surprise Me — 028', () => {
  test('US1 — explicit Universe is preserved; Role + Art Style are rolled (FR-2805/FR-2808)', async ({
    page,
  }) => {
    let captured: CapturedSelections | null = null
    await page.route('**/api/v1/alter-egos', async (route) => {
      const multipart = route.request().postData() ?? ''
      captured = parseSelectionsFromMultipart(multipart)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'X-Request-Id': '00000000-0000-0000-0000-000000000028' },
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')

    await selectPhoto(page)
    await page.getByRole('radio', { name: 'Star Wars' }).click()
    await page.getByLabel('First name').fill('Paula')

    const surprise = page.getByRole('button', { name: /surprise me/i })
    await expect(surprise).toBeEnabled()
    await surprise.click()

    // Wait for the poster to land so the fetch has definitely fired.
    // 017 FR-1713: PAULA / The Cloud Guardrail are baked into the
    // poster image — the alt text is the user-observable signal.
    await expect(page.getByRole('img', { name: /alter ego poster/i })).toBeVisible()

    // Wire-side assertion: the explicit Universe survives the merge.
    expect(captured).not.toBeNull()
    const sel = captured as unknown as CapturedSelections
    expect(sel.universe).toBe('star-wars')
    expect(VALID_ARCHETYPES).toContain(sel.archetype)
    expect(VALID_ART_STYLES).toContain(sel.artStyle)
    expect(sel.customRole).toBeUndefined()

    // UI-side assertion: Setup tab on return still shows Star Wars
    // highlighted (FR-2807 + FR-2808).
    await page.getByRole('tab', { name: /1 setup/i }).click()
    const starWars = page.getByRole('radio', { name: 'Star Wars' })
    await expect(starWars).toHaveAttribute('aria-checked', 'true')
  })

  test('US2 — non-empty Custom Role is preserved on session AND wire (FR-2803/FR-2808/FR-2814)', async ({
    page,
  }) => {
    let captured: CapturedSelections | null = null
    await page.route('**/api/v1/alter-egos', async (route) => {
      const multipart = route.request().postData() ?? ''
      captured = parseSelectionsFromMultipart(multipart)
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        headers: { 'X-Request-Id': '00000000-0000-0000-0000-000000000029' },
        body: JSON.stringify(buildSampleResponse()),
      })
    })

    await page.goto('/')

    await selectPhoto(page)
    // 022: the Custom Role input lives below the Archetype grid and
    // claims precedence over the prefab when its trimmed value is
    // non-empty. The label may evolve — match a forgiving regex.
    await page
      .getByRole('textbox', { name: 'Custom role' })
      .fill('Distinguished Spreadsheet Wrangler')
    await page.getByLabel('First name').fill('Paula')

    const surprise = page.getByRole('button', { name: /surprise me/i })
    await expect(surprise).toBeEnabled()
    await surprise.click()

    await expect(page.getByRole('img', { name: /alter ego poster/i })).toBeVisible()

    // Wire-side: customRole carries the typed string (trimmed match — we
    // typed without leading/trailing whitespace, so the trim is a no-op).
    expect(captured).not.toBeNull()
    const sel = captured as unknown as CapturedSelections
    expect(sel.customRole).toBe('Distinguished Spreadsheet Wrangler')
    // 022 serialiser drops `archetype` when customRole is present.
    expect(sel.archetype).toBeUndefined()
    // Universe + Art Style still get rolled.
    expect(VALID_UNIVERSES).toContain(sel.universe)
    expect(VALID_ART_STYLES).toContain(sel.artStyle)

    // UI-side: the typed string survives Surprise Me (FR-2814 — this is
    // the deliberate regression of 022's FR-2209 clear behavior).
    await page.getByRole('tab', { name: /1 setup/i }).click()
    const customRoleInput = page.getByRole('textbox', { name: 'Custom role' })
    await expect(customRoleInput).toHaveValue('Distinguished Spreadsheet Wrangler')
  })
})

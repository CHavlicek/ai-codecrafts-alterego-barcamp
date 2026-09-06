import { describe, expect, test } from 'vitest'
import { initialAlterEgoSession, type AlterEgoSession } from '../state/reducer'
import { mergeSurpriseWithExplicit } from './mergeSurpriseWithExplicit'
import type { SurpriseMePicks } from './randomSelections'

/**
 * 028 partial-surprise-me — pure merge contract for "preserve explicit
 * picks, roll the rest" semantics (FR-2801..FR-2806). Eight cases per
 * research.md R-7.
 *
 * The function takes the current session AND a precomputed full random
 * roll, and returns the final picks payload to dispatch via
 * `SurpriseMePicked`. Non-empty session values win; empty slots inherit
 * the roll. No RNG inside this helper — callers compose it with
 * `randomSelections()`.
 */

const FULL_ROLL: SurpriseMePicks = {
  archetype: 'cloud-architect',
  universe: 'star-wars',
  artStyle: 'oil-painting',
}

describe('mergeSurpriseWithExplicit', () => {
  test('(a) all-empty session → returns fullRoll verbatim (FR-2806)', () => {
    const session = initialAlterEgoSession()
    const merged = mergeSurpriseWithExplicit(session, FULL_ROLL)
    expect(merged).toEqual(FULL_ROLL)
  })

  test('(b) only archetype explicit → archetype passes through, universe + artStyle rolled', () => {
    const session: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: 'backend-dev',
    }
    const merged = mergeSurpriseWithExplicit(session, FULL_ROLL)
    expect(merged.archetype).toBe('backend-dev')
    expect(merged.universe).toBe(FULL_ROLL.universe)
    expect(merged.artStyle).toBe(FULL_ROLL.artStyle)
  })

  test('(c) only universe explicit → universe passes through, archetype + artStyle rolled', () => {
    const session: AlterEgoSession = {
      ...initialAlterEgoSession(),
      universe: 'cyberpunk',
    }
    const merged = mergeSurpriseWithExplicit(session, FULL_ROLL)
    expect(merged.archetype).toBe(FULL_ROLL.archetype)
    expect(merged.universe).toBe('cyberpunk')
    expect(merged.artStyle).toBe(FULL_ROLL.artStyle)
  })

  test('(d) only artStyle explicit → artStyle passes through, archetype + universe rolled', () => {
    const session: AlterEgoSession = {
      ...initialAlterEgoSession(),
      artStyle: 'pop-art',
    }
    const merged = mergeSurpriseWithExplicit(session, FULL_ROLL)
    expect(merged.archetype).toBe(FULL_ROLL.archetype)
    expect(merged.universe).toBe(FULL_ROLL.universe)
    expect(merged.artStyle).toBe('pop-art')
  })

  test('(e) non-empty customRole (archetype null per 022 invariant) → Role is explicit, archetype slot holds the roll (caller will override with customRole in Selections)', () => {
    const session: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: null,
      customRole: 'Distinguished Spreadsheet Wrangler',
    }
    const merged = mergeSurpriseWithExplicit(session, FULL_ROLL)
    // The helper's return value carries the rolled archetype as a stand-in
    // (type-safety constraint — SurpriseMePicks.archetype is non-nullable).
    // The hook's Selections builder reads customRole directly from session
    // and the serialiser omits archetype when customRole is present, so this
    // value is conceptually don't-care here. We pin it to fullRoll.archetype
    // so the contract is unambiguous for downstream consumers.
    expect(merged.archetype).toBe(FULL_ROLL.archetype)
    expect(merged.universe).toBe(FULL_ROLL.universe)
    expect(merged.artStyle).toBe(FULL_ROLL.artStyle)
  })

  test('(f) all explicit → fullRoll is entirely overridden, no random substitution (US4)', () => {
    const session: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: 'ai-engineer',
      universe: 'marvel',
      artStyle: 'pop-art',
    }
    const merged = mergeSurpriseWithExplicit(session, FULL_ROLL)
    expect(merged).toEqual({
      archetype: 'ai-engineer',
      universe: 'marvel',
      artStyle: 'pop-art',
    })
  })

  test('(g) whitespace-only customRole is treated as empty per 022 trim rule (Role rolls normally)', () => {
    const session: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: null,
      customRole: '   \t  ',
    }
    const merged = mergeSurpriseWithExplicit(session, FULL_ROLL)
    // Role is empty → archetype slot inherits the roll, as in case (a).
    expect(merged.archetype).toBe(FULL_ROLL.archetype)
    expect(merged.universe).toBe(FULL_ROLL.universe)
    expect(merged.artStyle).toBe(FULL_ROLL.artStyle)
  })

  test('(h) purity — inputs are not mutated', () => {
    const session: AlterEgoSession = {
      ...initialAlterEgoSession(),
      universe: 'lord-of-the-rings',
    }
    const sessionSnapshot = JSON.parse(JSON.stringify(session))
    const rollSnapshot = JSON.parse(JSON.stringify(FULL_ROLL))
    mergeSurpriseWithExplicit(session, FULL_ROLL)
    expect(JSON.parse(JSON.stringify(session))).toEqual(sessionSnapshot)
    expect(FULL_ROLL).toEqual(rollSnapshot)
  })
})

import { describe, expect, test } from 'vitest'
import { ARCHETYPE_OPTIONS, ART_STYLE_OPTIONS, UNIVERSE_OPTIONS } from '../options'
import { pickOne, randomSelections } from './randomSelections'

/**
 * T001 / T002 — Surprise Me randomizer contract (020 update).
 *
 * The utility is pure. Production code passes no RNG (defaults to
 * Math.random); tests pass a seeded RNG so the picks are deterministic.
 * See specs/009-surprise-me/data-model.md § "New Utility" for the
 * original contract and specs/020-hide-vibe-pose/spec.md (Story 3) for
 * the 020 delta: Pose and Vibe are no longer drawn here.
 */

describe('pickOne', () => {
  test('rng() === 0 picks index 0', () => {
    expect(pickOne(['a', 'b', 'c'], () => 0)).toBe('a')
  })

  test('rng() === 0.999 picks the last index', () => {
    expect(pickOne(['a', 'b', 'c'], () => 0.999)).toBe('c')
  })

  test('rng() === 0.5 on a 4-element list picks index 2', () => {
    expect(pickOne(['a', 'b', 'c', 'd'], () => 0.5)).toBe('c')
  })

  test('throws on an empty list', () => {
    expect(() => pickOne([], () => 0)).toThrow(/empty list/i)
  })

  test('clamps on the out-of-contract rng() === 1 rather than returning undefined', () => {
    // Math.random is specified to be strictly < 1, but a buggy custom RNG
    // could return 1 exactly. Math.floor(1 * N) = N → out of bounds.
    // The utility defends against this by clamping to the last index.
    expect(pickOne(['a', 'b', 'c'], () => 1)).toBe('c')
  })

  test('defaults to Math.random when no rng is provided', () => {
    const seen = new Set<string>()
    for (let i = 0; i < 50; i++) {
      seen.add(pickOne(['a', 'b', 'c']))
    }
    expect(seen.size).toBeGreaterThan(0)
    for (const v of seen) {
      expect(['a', 'b', 'c']).toContain(v)
    }
  })
})

describe('randomSelections', () => {
  test('with rng=() => 0, picks the first option in every visible category', () => {
    const picks = randomSelections(() => 0)
    expect(picks.archetype).toBe(ARCHETYPE_OPTIONS[0]!.value)
    expect(picks.universe).toBe(UNIVERSE_OPTIONS[0]!.value)
    expect(picks.artStyle).toBe(ART_STYLE_OPTIONS[0]!.value)
  })

  test('with rng=() => 0.999, picks the last option in every visible category', () => {
    const picks = randomSelections(() => 0.999)
    expect(picks.archetype).toBe(ARCHETYPE_OPTIONS[ARCHETYPE_OPTIONS.length - 1]!.value)
    expect(picks.universe).toBe(UNIVERSE_OPTIONS[UNIVERSE_OPTIONS.length - 1]!.value)
    expect(picks.artStyle).toBe(ART_STYLE_OPTIONS[ART_STYLE_OPTIONS.length - 1]!.value)
  })

  test('020 — picks no longer include pose or vibe (closes #51)', () => {
    const picks = randomSelections(() => 0)
    expect(Object.keys(picks)).not.toContain('pose')
    expect(Object.keys(picks)).not.toContain('vibe')
  })

  test('every category pick is drawn from its canonical options list (no cross-category bleed)', () => {
    for (let i = 0; i < 50; i++) {
      const picks = randomSelections()
      expect(ARCHETYPE_OPTIONS.map((o) => o.value)).toContain(picks.archetype)
      expect(UNIVERSE_OPTIONS.map((o) => o.value)).toContain(picks.universe)
      expect(ART_STYLE_OPTIONS.map((o) => o.value)).toContain(picks.artStyle)
    }
  })

  test('each call is independent — 30 back-to-back calls produce at least 2 distinct archetypes', () => {
    // Memoryless randomness across 30 draws on a 6-option list — probability
    // of a single value winning every draw is (1/6)^29 ≈ 0.
    const archetypes = new Set<string>()
    for (let i = 0; i < 30; i++) {
      archetypes.add(randomSelections().archetype)
    }
    expect(archetypes.size).toBeGreaterThan(1)
  })

  test('019 SC-1902: never picks Pixel Art, Low-Poly 3D, or Line Art across 1000 uniform-RNG draws', () => {
    const retired = new Set(['pixel-art', 'low-poly-3d', 'line-art'])
    const survivors = new Set(ART_STYLE_OPTIONS.map((o) => o.value))
    const draws = 1000
    for (let i = 0; i < draws; i++) {
      const picks = randomSelections()
      expect(retired.has(picks.artStyle as string)).toBe(false)
      expect(survivors.has(picks.artStyle)).toBe(true)
    }
  })

  test('sequential rng returning 0, 0.5, 0.999 picks index 0, mid, last across visible categories in call order', () => {
    // 020 — Confirms pickOne is called in the documented order (archetype,
    // universe, artStyle) and consumes one rng() value per category. Three
    // samples are enough now that pose and vibe are no longer in the mix.
    let i = 0
    const samples = [0, 0.5, 0.999]
    const rng = () => samples[i++] ?? 0

    const picks = randomSelections(rng)
    expect(picks.archetype).toBe(ARCHETYPE_OPTIONS[0]!.value)
    expect(picks.universe).toBe(UNIVERSE_OPTIONS[Math.floor(0.5 * UNIVERSE_OPTIONS.length)]!.value)
    expect(picks.artStyle).toBe(ART_STYLE_OPTIONS[ART_STYLE_OPTIONS.length - 1]!.value)
  })

  // 022 (issue #50): Role pool widens from six to nine. Surprise Me MUST
  // draw uniformly from the full nine; the three new options are eligible
  // alongside the six engineering ones. The randomiser MUST NOT produce a
  // custom-role string — it stays grounded in the prefab enum.

  test('022 — ARCHETYPE_OPTIONS exposes all 9 prefab values in stable order', () => {
    expect(ARCHETYPE_OPTIONS.map((o) => o.value)).toEqual([
      'software-developer',
      'project-manager',
      'data-analyst',
      'marketing-specialist',
      'sales-customer-relations',
      'people-culture',
      'operations-manager',
      'finance-controller',
      'sustainability-lead',
    ])
  })

  test('022 — seeded RNG can pick every one of the nine archetypes (covers the three non-engineering roles)', () => {
    const seen = new Set<string>()
    for (let i = 0; i < 9; i++) {
      const seeded = () => (i + 0.5) / 9
      seen.add(randomSelections(seeded).archetype)
    }
    expect(seen.size).toBe(9)
    expect(seen.has('people-culture')).toBe(true)
    expect(seen.has('operations-manager')).toBe(true)
    expect(seen.has('sales-customer-relations')).toBe(true)
  })

  test('022 — across 200 Math.random draws the three new options each appear at least once', () => {
    const seen = new Set<string>()
    for (let i = 0; i < 200; i++) {
      seen.add(randomSelections().archetype)
    }
    expect(seen.has('people-culture')).toBe(true)
    expect(seen.has('operations-manager')).toBe(true)
    expect(seen.has('sales-customer-relations')).toBe(true)
  })

  test('022 — randomSelections never returns a customRole field (Surprise Me stays grounded in the prefab enum)', () => {
    const picks = randomSelections(() => 0.5) as unknown as Record<string, unknown>
    expect(Object.keys(picks)).not.toContain('customRole')
  })
})

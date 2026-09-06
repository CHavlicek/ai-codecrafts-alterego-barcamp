import { describe, expect, test } from 'vitest'
import { ARCHETYPE_OPTIONS } from '../options'
import type { Archetype } from '../types'
import { humanizeArchetype } from './humanizeSelection'

/**
 * humanizeArchetype maps every kebab-case Archetype wire value to the
 * human-readable label already declared in `options.ts`. Used by the
 * on-screen role label in AlterEgoPanel and by the printable image's
 * alt text in PrintArtefact (017 FR-1714).
 *
 * <p>The round-trip assertion is the canonical way to verify the map
 * stays in sync when a future feature adds a 7th archetype: extending
 * `options.ts` alone is enough.
 */

describe('humanizeArchetype — round-trip every declared value', () => {
  test('returns ARCHETYPE_OPTIONS[*].label for every declared Archetype value', () => {
    for (const opt of ARCHETYPE_OPTIONS) {
      expect(humanizeArchetype(opt.value)).toBe(opt.label)
    }
    // Explicit spot-check of the canonical "must not leak kebab" case.
    expect(humanizeArchetype('software-developer')).toBe('Software Developer')
  })
})

describe('humanizeArchetype — forward-compatibility fallback', () => {
  test('returns a non-empty Title-Cased string for an unknown wire value rather than throwing', () => {
    // Callers may receive a value the backend added before the FE was rebuilt.
    // The helper MUST NOT throw; it MUST produce readable output.
    const unknownArchetype = 'vr-engineer' as Archetype
    expect(() => humanizeArchetype(unknownArchetype)).not.toThrow()
    expect(humanizeArchetype(unknownArchetype)).toBe('Vr Engineer')
    expect(humanizeArchetype(unknownArchetype)).not.toMatch(/-/) // no raw kebab leaks
  })
})

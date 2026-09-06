/**
 * 009 Surprise Me — pure randomizer utility.
 *
 * <p>Picks one value from each canonical visible-category option list
 * ({@link ARCHETYPE_OPTIONS} / {@link UNIVERSE_OPTIONS} /
 * {@link ART_STYLE_OPTIONS}). The RNG is injected so tests can pass a
 * seeded function and assert exact picks; production call sites omit it
 * and inherit {@link Math.random}.
 *
 * <p>See specs/009-surprise-me/data-model.md § "New Utility" for the
 * original contract and specs/020-hide-vibe-pose/spec.md (Story 3) for
 * the 020 delta: Pose and Vibe are no longer drawn here — the server
 * rolls those server-side per request.
 */
import { ARCHETYPE_OPTIONS, ART_STYLE_OPTIONS, UNIVERSE_OPTIONS } from '../options'
import type { Archetype, ArtStyle, Universe } from '../types'

/** RNG contract: returns a number in [0, 1). Matches {@link Math.random}. */
export type Rng = () => number

export interface SurpriseMePicks {
  archetype: Archetype
  universe: Universe
  artStyle: ArtStyle
}

/**
 * Draw one element uniformly at random from {@code list}.
 *
 * <p>An empty list is a caller bug — the function throws rather than
 * returning {@code undefined}. A buggy RNG returning 1 (violating the
 * {@link Math.random} contract) is clamped to the last index so the
 * return type stays {@code T} and not {@code T | undefined}.
 */
export function pickOne<T>(list: ReadonlyArray<T>, rng: Rng = Math.random): T {
  if (list.length === 0) {
    throw new Error('pickOne: cannot pick from an empty list')
  }
  const index = Math.floor(rng() * list.length)
  const clamped = Math.min(index, list.length - 1)
  return list[clamped]!
}

/**
 * Build one {@link SurpriseMePicks} by drawing exactly one value from
 * each of the three visible category option lists. Call order
 * (archetype → universe → artStyle) is part of the contract — tests
 * assert it so seeded RNGs can produce predictable picks.
 */
export function randomSelections(rng: Rng = Math.random): SurpriseMePicks {
  return {
    archetype: pickOne(ARCHETYPE_OPTIONS, rng).value,
    universe: pickOne(UNIVERSE_OPTIONS, rng).value,
    artStyle: pickOne(ART_STYLE_OPTIONS, rng).value,
  }
}

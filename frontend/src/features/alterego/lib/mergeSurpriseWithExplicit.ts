/**
 * 028 partial-surprise-me — pure merge helper.
 *
 * <p>Composes the current {@link AlterEgoSession} with a precomputed
 * full random roll (the {@link SurpriseMePicks} value `randomSelections()`
 * produces) and returns the final picks payload that
 * {@code SurpriseMePicked} commits to the session and that the hook
 * uses to build the outbound `Selections`.
 *
 * <p>"Explicit vs empty" classification is the spec FR-2801 invariant —
 * a category's value is *explicit* iff its current session slot is
 * non-empty, regardless of how it got there (user click, previous
 * Surprise Me roll, or anything else short of `Start Over` /
 * page refresh per FR-2814). No provenance is tracked.
 *
 * <p>Role is a composite category — explicit iff `archetype !== null`
 * OR `customRole.trim().length > 0` (FR-2802 + FR-2803 + FR-2804). The
 * 022 precedence rule guarantees the two channels can't both be
 * non-empty at the same time, so the OR is safe.
 *
 * <p>When `customRole` is the explicit Role channel, the returned
 * `archetype` slot carries `fullRoll.archetype` as a type-safe stand-in
 * (the `SurpriseMePicks.archetype` field is non-nullable). The hook's
 * `Selections` builder reads `customRole` directly from session and the
 * `alterEgoClient` serialiser drops the `archetype` wire field when
 * `customRole` is present — so this stand-in is effectively don't-care
 * downstream. See spec 028 data-model.md → "Custom-role passthrough".
 *
 * <p>Pure: does not mutate `session` or `fullRoll`. Re-runnable.
 */
import type { AlterEgoSession } from '../state/reducer'
import type { SurpriseMePicks } from './randomSelections'

export function mergeSurpriseWithExplicit(
  session: AlterEgoSession,
  fullRoll: SurpriseMePicks,
): SurpriseMePicks {
  const roleIsExplicitViaPrefab = session.archetype !== null
  const roleIsExplicitViaCustom = session.customRole.trim().length > 0
  const archetype = roleIsExplicitViaPrefab ? session.archetype! : fullRoll.archetype
  // 029: Universe is a composite category (prefab OR custom), mirroring Role.
  // When explicit via the custom string the prefab slot is don't-care for the
  // downstream serialiser (which drops `universe` when `customUniverse` is
  // present); we return fullRoll's value as a type-safe stand-in.
  const universeIsExplicitViaCustom = session.customUniverse.trim().length > 0
  const universe = session.universe !== null ? session.universe : fullRoll.universe
  const artStyle = session.artStyle !== null ? session.artStyle : fullRoll.artStyle
  // The …ViaCustom variables are part of the spec contract but do not
  // influence the return shape — their observable effect is in the
  // Selections builder in `useGenerateAlterEgo`.
  void roleIsExplicitViaCustom
  void universeIsExplicitViaCustom
  return { archetype, universe, artStyle }
}

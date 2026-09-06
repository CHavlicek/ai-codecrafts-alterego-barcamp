/**
 * Wire-value → display-label helper for Archetype.
 *
 * <p>Single source of truth is `../options.ts` — the same
 * `ARCHETYPE_OPTIONS` array the Setup grid renders. Extending that
 * array automatically flows into the lookup below at module load, so
 * the on-screen role label and the printable image's alt text never
 * drift from the Setup grid.
 *
 * <p>Unknown wire values (e.g. a backend-added enum the FE hasn't
 * redeployed for) fall through to a Title-Cased kebab so nothing
 * throws and nothing surfaces raw `cloud-architect`.
 */
import { ARCHETYPE_OPTIONS, type EnumOption } from '../options'
import type { Archetype } from '../types'

function buildLookup<T extends string>(
  options: ReadonlyArray<EnumOption<T>>,
): Record<string, string> {
  const map: Record<string, string> = {}
  for (const opt of options) map[opt.value] = opt.label
  return map
}

const ARCHETYPE_LABELS = buildLookup(ARCHETYPE_OPTIONS)

function titleCaseKebab(value: string): string {
  return value
    .split('-')
    .filter((part) => part.length > 0)
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1).toLowerCase())
    .join(' ')
}

function humanize(map: Record<string, string>, value: string): string {
  return map[value] ?? titleCaseKebab(value)
}

export function humanizeArchetype(value: Archetype): string {
  return humanize(ARCHETYPE_LABELS, value)
}

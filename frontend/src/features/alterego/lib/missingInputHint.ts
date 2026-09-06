/**
 * 009 T003 — Shared missing-input hint formatting.
 *
 * <p>Extracted from {@code GenerateButton} so the new
 * {@code SurpriseMeButton} can share the same label map and the same
 * Oxford-comma-style list formatter. Single source of truth for the
 * disabled-state hint copy across the Setup tab's two action buttons.
 *
 * <p>011 delta: {@link formatMissingList} accepts an optional
 * {@code labels} override map so the {@code firstName} entry can be
 * relabelled to "group name" when the session is in group mode. The
 * default {@link FIELD_LABELS} preserves today's single-mode copy.
 */
import type { RequiredInput } from '../state/selectors'
import type { PhotoMode } from '../types'

export const FIELD_LABELS: Record<RequiredInput, string> = {
  photo: 'photo',
  archetype: 'role',
  universe: 'universe',
  artStyle: 'art style',
  // 023 (issue #57): malformed email gates Generate / Surprise Me — the
  // missing-input hint surfaces it as "valid email" (the field itself
  // is optional, so "missing" really means "present but malformed").
  email: 'valid email',
  firstName: 'first name',
}

/**
 * 011 — derive the missing-input label map for a given photo mode.
 * In {@code group} mode {@code firstName} reads "group name"; everything
 * else is unchanged. Returned as a fresh object so callers can pass it
 * to {@link formatMissingList} without mutating the canonical
 * {@link FIELD_LABELS}.
 */
export function fieldLabelsForMode(mode: PhotoMode): Record<RequiredInput, string> {
  if (mode === 'group') {
    return { ...FIELD_LABELS, firstName: 'group name' }
  }
  return FIELD_LABELS
}

/**
 * Turn a list of missing-input keys into a human-readable phrase.
 * Empty list → empty string; two items → "A and B"; three-or-more →
 * Oxford-comma prose ("A, B, and C").
 *
 * <p>The {@code labels} override defaults to {@link FIELD_LABELS} —
 * pass {@link fieldLabelsForMode}'s output to swap in the group-mode
 * copy.
 */
export function formatMissingList(
  missing: ReadonlyArray<RequiredInput>,
  labels: Record<RequiredInput, string> = FIELD_LABELS,
): string {
  const mapped = missing.map((m) => labels[m])
  if (mapped.length === 0) return ''
  if (mapped.length === 1) return mapped[0] ?? ''
  if (mapped.length === 2) return `${mapped[0]} and ${mapped[1]}`
  return `${mapped.slice(0, -1).join(', ')}, and ${mapped[mapped.length - 1]}`
}

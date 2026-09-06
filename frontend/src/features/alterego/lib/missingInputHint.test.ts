import { describe, expect, test } from 'vitest'
import { FIELD_LABELS, fieldLabelsForMode, formatMissingList } from './missingInputHint'
import type { RequiredInput } from '../state/selectors'

/**
 * 009 T003 — Shared missing-input hint helper. Extracted from the
 * original location in GenerateButton so SurpriseMeButton can reuse
 * the same label map + list formatter without duplication.
 */

describe('FIELD_LABELS', () => {
  test('maps every RequiredInput key to a user-facing label', () => {
    // 020 — pose dropped from RequiredInput; closes issue #51.
    const keys: RequiredInput[] = ['photo', 'archetype', 'universe', 'artStyle', 'firstName']
    for (const key of keys) {
      expect(FIELD_LABELS[key]).toBeTypeOf('string')
      expect(FIELD_LABELS[key].length).toBeGreaterThan(0)
    }
  })

  test('uses human-friendly labels (not wire keys) — spot-check', () => {
    expect(FIELD_LABELS.archetype).toBe('role')
    expect(FIELD_LABELS.artStyle).toBe('art style')
    expect(FIELD_LABELS.firstName).toBe('first name')
  })
})

describe('formatMissingList', () => {
  test('returns empty string for an empty list', () => {
    expect(formatMissingList([])).toBe('')
  })

  test('returns a single label for a one-item list', () => {
    expect(formatMissingList(['photo'])).toBe('photo')
  })

  test('joins two items with " and "', () => {
    expect(formatMissingList(['photo', 'firstName'])).toBe('photo and first name')
  })

  test('joins three-or-more items with Oxford-comma-style prose', () => {
    expect(formatMissingList(['photo', 'archetype', 'firstName'])).toBe(
      'photo, role, and first name',
    )
  })

  test('011 — accepts a labels override and uses it in place of FIELD_LABELS', () => {
    const groupLabels = fieldLabelsForMode('group')
    expect(formatMissingList(['photo', 'firstName'], groupLabels)).toBe('photo and group name')
    expect(formatMissingList(['firstName'], groupLabels)).toBe('group name')
  })
})

describe('fieldLabelsForMode (011)', () => {
  test('single mode returns the canonical FIELD_LABELS', () => {
    expect(fieldLabelsForMode('single')).toBe(FIELD_LABELS)
  })

  test('group mode swaps firstName to "group name" and leaves the rest unchanged', () => {
    const labels = fieldLabelsForMode('group')
    expect(labels.firstName).toBe('group name')
    expect(labels.photo).toBe(FIELD_LABELS.photo)
    expect(labels.archetype).toBe(FIELD_LABELS.archetype)
    expect(labels.universe).toBe(FIELD_LABELS.universe)
    expect(labels.artStyle).toBe(FIELD_LABELS.artStyle)
  })

  test('group mode does NOT mutate the canonical FIELD_LABELS', () => {
    fieldLabelsForMode('group')
    expect(FIELD_LABELS.firstName).toBe('first name')
  })
})

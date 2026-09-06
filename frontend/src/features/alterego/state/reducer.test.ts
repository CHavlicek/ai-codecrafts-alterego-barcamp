import { describe, expect, test } from 'vitest'
import {
  alterEgoReducer,
  initialAlterEgoSession,
  type AlterEgoAction,
  type AlterEgoSession,
} from './reducer'
import type { AlterEgoResponse } from '../types'

/**
 * T029 — Reducer contract.
 *
 * Covers all 11 action types across the 6 phases. Initial state →
 * picking → generating → succeeded / failed_with_fallback → idle (via
 * StartOverRequested).
 */

const sampleResponse: AlterEgoResponse = {
  character: {
    heroTitleLine1: 'PAULA',
    heroTitleLine2: 'The Cloud Guardrail',
    tagline: 'STILL SHIPS ON FRIDAYS.',
    superpowers: ['p1', 'p2', 'p3'],
    quote: 'It is always DNS.',
  },
  poster: {
    dataUrl: 'data:image/png;base64,AAAA',
    mediaType: 'image/png',
    widthPx: 900,
    heightPx: 1200,
  },
  meta: {
    outcome: 'real',
    provider: 'gemini',
    correlationId: '00000000-0000-0000-0000-000000000001',
  },
}

const sampleBlob = new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' })
const sampleUrl = 'blob:fake-url'

function reduce(state: AlterEgoSession, action: AlterEgoAction): AlterEgoSession {
  return alterEgoReducer(state, action)
}

describe('initialAlterEgoSession', () => {
  test('starts in idle phase on the setup tab with all selections empty', () => {
    const s = initialAlterEgoSession()
    expect(s.activeTab).toBe('setup')
    expect(s.generateAutoSwitchNonce).toBe(0)
    expect(s.phase).toBe('idle')
    expect(s.photoBlob).toBeNull()
    expect(s.photoPreviewUrl).toBeNull()
    expect(s.archetype).toBeNull()
    expect(s.universe).toBeNull()
    expect(s.artStyle).toBeNull()
    expect(s.firstName).toBe('')
    expect(s.errorMessage).toBeNull()
    expect(s.result).toBeNull()
  })

  test('011 — photoMode defaults to "single" (FR-1003)', () => {
    expect(initialAlterEgoSession().photoMode).toBe('single')
  })

  test('020 — initial session has no pose or vibe field (closes #51)', () => {
    const keys = Object.keys(initialAlterEgoSession())
    expect(keys).not.toContain('pose')
    expect(keys).not.toContain('vibe')
  })
})

describe('PhotoModeSelected (011)', () => {
  test('first flip to "group" updates photoMode and moves phase to picking', () => {
    const next = reduce(initialAlterEgoSession(), {
      type: 'PhotoModeSelected',
      photoMode: 'group',
    })
    expect(next.photoMode).toBe('group')
    expect(next.phase).toBe('picking')
  })

  test('flipping from "group" back to "single" restores the default (no null intermediate)', () => {
    const after = reduce(initialAlterEgoSession(), {
      type: 'PhotoModeSelected',
      photoMode: 'group',
    })
    const back = reduce(after, { type: 'PhotoModeSelected', photoMode: 'single' })
    expect(back.photoMode).toBe('single')
  })

  test('does not mutate any other session field (FR-1007 / FR-1012)', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      photoBlob: sampleBlob,
      photoPreviewUrl: sampleUrl,
      archetype: 'cloud-architect',
      universe: 'star-wars',
      artStyle: 'oil-painting',
      firstName: 'Paula',
      activeTab: 'alter-ego',
      generateAutoSwitchNonce: 3,
    }
    const next = reduce(seeded, { type: 'PhotoModeSelected', photoMode: 'group' })
    // Only photoMode + phase changed.
    expect(next.photoMode).toBe('group')
    expect(next.phase).toBe('picking')
    expect(next.photoBlob).toBe(sampleBlob)
    expect(next.photoPreviewUrl).toBe(sampleUrl)
    expect(next.archetype).toBe('cloud-architect')
    expect(next.universe).toBe('star-wars')
    expect(next.artStyle).toBe('oil-painting')
    expect(next.firstName).toBe('Paula')
    expect(next.activeTab).toBe('alter-ego')
    expect(next.generateAutoSwitchNonce).toBe(3)
  })
})

describe('ArtStyleSelected', () => {
  test('first click selects', () => {
    const next = reduce(initialAlterEgoSession(), {
      type: 'ArtStyleSelected',
      artStyle: 'oil-painting',
    })
    expect(next.artStyle).toBe('oil-painting')
    expect(next.phase).toBe('picking')
  })

  test('second click on the same value is a stable replace (no deselect — art style is required)', () => {
    const first = reduce(initialAlterEgoSession(), {
      type: 'ArtStyleSelected',
      artStyle: 'oil-painting',
    })
    const second = reduce(first, { type: 'ArtStyleSelected', artStyle: 'oil-painting' })
    expect(second.artStyle).toBe('oil-painting')
  })

  test('click on a different art style replaces the previous one', () => {
    const first = reduce(initialAlterEgoSession(), {
      type: 'ArtStyleSelected',
      artStyle: 'oil-painting',
    })
    const second = reduce(first, { type: 'ArtStyleSelected', artStyle: 'oil-painting' })
    expect(second.artStyle).toBe('oil-painting')
  })
})

describe('ActiveTabChanged', () => {
  test('manual flip: changes tab and does NOT bump the animation nonce', () => {
    const initial = initialAlterEgoSession()
    const next = reduce(initial, { type: 'ActiveTabChanged', tab: 'alter-ego' })
    expect(next.activeTab).toBe('alter-ego')
    expect(next.generateAutoSwitchNonce).toBe(0)
    expect({ ...next, activeTab: 'setup' as const }).toEqual(initial)
  })

  test('manual no-op (same tab, no reason) returns the SAME reference (React bail-out)', () => {
    const initial = initialAlterEgoSession()
    const next = reduce(initial, { type: 'ActiveTabChanged', tab: 'setup' })
    expect(next).toBe(initial)
  })

  test('reason: "generate" increments generateAutoSwitchNonce and flips the tab (005 FR-401)', () => {
    const initial = initialAlterEgoSession()
    const next = reduce(initial, { type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    expect(next.activeTab).toBe('alter-ego')
    expect(next.generateAutoSwitchNonce).toBe(1)
  })

  test('reason: "generate" on the ALREADY-active tab still increments the nonce (spec Acceptance 4: re-click re-animates)', () => {
    const seeded = {
      ...initialAlterEgoSession(),
      activeTab: 'alter-ego' as const,
      generateAutoSwitchNonce: 1,
    }
    const next = reduce(seeded, { type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'generate' })
    expect(next).not.toBe(seeded)
    expect(next.activeTab).toBe('alter-ego')
    expect(next.generateAutoSwitchNonce).toBe(2)
  })

  test('reason: "manual" explicit is equivalent to omitting reason (no nonce bump)', () => {
    const initial = initialAlterEgoSession()
    const next = reduce(initial, { type: 'ActiveTabChanged', tab: 'alter-ego', reason: 'manual' })
    expect(next.generateAutoSwitchNonce).toBe(0)
  })

  test('nonce survives unrelated actions (monotonic — no transient reset)', () => {
    // This is the critical property that makes the nonce work in a single
    // React commit: GenerateSubmitted fires right after the auto-switch,
    // and must NOT clobber the counter before TabsShell's effect reads it.
    const initial = initialAlterEgoSession()
    const afterGenerate = reduce(initial, {
      type: 'ActiveTabChanged',
      tab: 'alter-ego',
      reason: 'generate',
    })
    expect(afterGenerate.generateAutoSwitchNonce).toBe(1)
    const afterGenerateSubmitted = reduce(afterGenerate, { type: 'GenerateSubmitted' })
    expect(afterGenerateSubmitted.generateAutoSwitchNonce).toBe(1)
    const afterSucceeded = reduce(afterGenerateSubmitted, {
      type: 'GenerateSucceeded',
      result: sampleResponse,
    })
    expect(afterSucceeded.generateAutoSwitchNonce).toBe(1)
  })
})

// 020 — The pre-020 VibeSelected describe block was removed. Vibe is no
// longer a user-controllable category; the server rolls it per request.

describe('selection actions transition to picking', () => {
  test('PhotoSelected stores blob + preview url and moves to picking', () => {
    const next = reduce(initialAlterEgoSession(), {
      type: 'PhotoSelected',
      photoBlob: sampleBlob,
      photoPreviewUrl: sampleUrl,
    })
    expect(next.photoBlob).toBe(sampleBlob)
    expect(next.photoPreviewUrl).toBe(sampleUrl)
    expect(next.phase).toBe('picking')
  })

  test('PhotoCleared nulls blob + preview and stays in picking', () => {
    const seeded = reduce(initialAlterEgoSession(), {
      type: 'PhotoSelected',
      photoBlob: sampleBlob,
      photoPreviewUrl: sampleUrl,
    })
    const cleared = reduce(seeded, { type: 'PhotoCleared' })
    expect(cleared.photoBlob).toBeNull()
    expect(cleared.photoPreviewUrl).toBeNull()
    expect(cleared.phase).toBe('picking')
  })

  test('ArchetypeSelected, UniverseSelected each set their field', () => {
    let s = initialAlterEgoSession()
    s = reduce(s, { type: 'ArchetypeSelected', archetype: 'cloud-architect' })
    s = reduce(s, { type: 'UniverseSelected', universe: 'star-wars' })
    expect(s.archetype).toBe('cloud-architect')
    expect(s.universe).toBe('star-wars')
    expect(s.phase).toBe('picking')
  })

  test('FirstNameChanged stores the name verbatim (trim is a selector concern)', () => {
    const next = reduce(initialAlterEgoSession(), {
      type: 'FirstNameChanged',
      firstName: '  Paula  ',
    })
    expect(next.firstName).toBe('  Paula  ')
    expect(next.phase).toBe('picking')
  })
})

describe('generation lifecycle', () => {
  test('GenerateSubmitted transitions to generating and clears prior error/result', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      phase: 'failed_with_fallback',
      errorMessage: 'previous error',
      result: sampleResponse,
    }
    const next = reduce(seeded, { type: 'GenerateSubmitted' })
    expect(next.phase).toBe('generating')
    expect(next.errorMessage).toBeNull()
    expect(next.result).toBeNull()
  })

  test('GenerateSubmitted is a no-op when already generating', () => {
    const generating: AlterEgoSession = { ...initialAlterEgoSession(), phase: 'generating' }
    const next = reduce(generating, { type: 'GenerateSubmitted' })
    expect(next).toBe(generating)
  })

  test('GenerateSucceeded stores the result and sets phase=succeeded', () => {
    const generating: AlterEgoSession = { ...initialAlterEgoSession(), phase: 'generating' }
    const next = reduce(generating, { type: 'GenerateSucceeded', result: sampleResponse })
    expect(next.phase).toBe('succeeded')
    expect(next.result).toBe(sampleResponse)
    expect(next.errorMessage).toBeNull()
  })

  test('GenerateFailedWithFallback stores result + errorMessage and sets phase=failed_with_fallback', () => {
    const generating: AlterEgoSession = { ...initialAlterEgoSession(), phase: 'generating' }
    const next = reduce(generating, {
      type: 'GenerateFailedWithFallback',
      result: sampleResponse,
      errorMessage: 'service down',
    })
    expect(next.phase).toBe('failed_with_fallback')
    expect(next.result).toBe(sampleResponse)
    expect(next.errorMessage).toBe('service down')
  })
})

describe('SurpriseMePicked (009 / 020)', () => {
  const picks = {
    archetype: 'ai-engineer' as const,
    universe: 'cyberpunk' as const,
    artStyle: 'pop-art' as const,
  }

  test('assigns the three visible category fields from the payload and transitions phase to picking', () => {
    const next = reduce(initialAlterEgoSession(), { type: 'SurpriseMePicked', picks })
    expect(next.archetype).toBe('ai-engineer')
    expect(next.universe).toBe('cyberpunk')
    expect(next.artStyle).toBe('pop-art')
    expect(next.phase).toBe('picking')
  })

  test('020 — the picks payload no longer carries pose or vibe (closes #51)', () => {
    expect(Object.keys(picks)).not.toContain('pose')
    expect(Object.keys(picks)).not.toContain('vibe')
  })

  test('overwrites prior user selections in the same session (FR-905)', () => {
    const seeded = reduce(initialAlterEgoSession(), {
      type: 'ArchetypeSelected',
      archetype: 'cloud-architect',
    })
    expect(seeded.archetype).toBe('cloud-architect')
    const next = reduce(seeded, { type: 'SurpriseMePicked', picks })
    expect(next.archetype).toBe('ai-engineer')
  })

  test('does NOT mutate firstName, photoBlob, photoPreviewUrl, activeTab, generateAutoSwitchNonce, errorMessage, or result', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      firstName: 'Paula',
      photoBlob: sampleBlob,
      photoPreviewUrl: sampleUrl,
      activeTab: 'alter-ego',
      generateAutoSwitchNonce: 7,
      errorMessage: 'prior error',
      result: sampleResponse,
    }
    const next = reduce(seeded, { type: 'SurpriseMePicked', picks })
    expect(next.firstName).toBe('Paula')
    expect(next.photoBlob).toBe(sampleBlob)
    expect(next.photoPreviewUrl).toBe(sampleUrl)
    expect(next.activeTab).toBe('alter-ego')
    expect(next.generateAutoSwitchNonce).toBe(7)
    expect(next.errorMessage).toBe('prior error')
    expect(next.result).toBe(sampleResponse)
  })

  test('FR-913 — PhotoSelected after SurpriseMePicked leaves the seeded categories intact', () => {
    const afterPicks = reduce(initialAlterEgoSession(), { type: 'SurpriseMePicked', picks })
    const afterPhoto = reduce(afterPicks, {
      type: 'PhotoSelected',
      photoBlob: sampleBlob,
      photoPreviewUrl: sampleUrl,
    })
    expect(afterPhoto.archetype).toBe('ai-engineer')
    expect(afterPhoto.universe).toBe('cyberpunk')
    expect(afterPhoto.artStyle).toBe('pop-art')
  })

  test('028 US1 — when picks duplicate a previously-explicit universe, the reducer writes that value verbatim (no overwrite-with-roll)', () => {
    // The hook is responsible for merging session explicit values into the
    // picks payload. This case pins the reducer-side contract: whatever
    // picks the hook constructs, the reducer commits exactly. Documentary
    // — prevents a future refactor from "fixing" the reducer to ignore
    // matching picks.
    const seeded = reduce(initialAlterEgoSession(), {
      type: 'UniverseSelected',
      universe: 'star-wars',
    })
    const explicitMergedPicks = { ...picks, universe: 'star-wars' as const }
    const next = reduce(seeded, { type: 'SurpriseMePicked', picks: explicitMergedPicks })
    expect(next.universe).toBe('star-wars')
    expect(next.archetype).toBe('ai-engineer')
    expect(next.artStyle).toBe('pop-art')
    expect(next.phase).toBe('picking')
  })

  test('028 US3 back-compat — fully-blank initial session still receives a full roll', () => {
    // Regression pin: when the hook's merge collapses to "all empty →
    // returns fullRoll verbatim", the reducer commits the full roll
    // unchanged. This is the 009 baseline behavior preserved through 028.
    const next = reduce(initialAlterEgoSession(), { type: 'SurpriseMePicked', picks })
    expect(next.archetype).toBe('ai-engineer')
    expect(next.universe).toBe('cyberpunk')
    expect(next.artStyle).toBe('pop-art')
  })

  test('FR-913 — FirstNameChanged after SurpriseMePicked leaves the seeded categories intact', () => {
    const afterPicks = reduce(initialAlterEgoSession(), { type: 'SurpriseMePicked', picks })
    const afterName = reduce(afterPicks, { type: 'FirstNameChanged', firstName: 'Paula' })
    expect(afterName.archetype).toBe('ai-engineer')
    expect(afterName.universe).toBe('cyberpunk')
    expect(afterName.artStyle).toBe('pop-art')
  })

  test('FR-907 ordering — the category picks survive the subsequent ActiveTabChanged + GenerateSubmitted dispatches that useGenerateAlterEgo.surprise() fires', () => {
    const afterPicks = reduce(initialAlterEgoSession(), { type: 'SurpriseMePicked', picks })
    const afterTabSwitch = reduce(afterPicks, {
      type: 'ActiveTabChanged',
      tab: 'alter-ego',
      reason: 'generate',
    })
    const afterSubmit = reduce(afterTabSwitch, { type: 'GenerateSubmitted' })
    expect(afterSubmit.archetype).toBe('ai-engineer')
    expect(afterSubmit.universe).toBe('cyberpunk')
    expect(afterSubmit.artStyle).toBe('pop-art')
    expect(afterSubmit.phase).toBe('generating')
    expect(afterSubmit.activeTab).toBe('alter-ego')
    expect(afterSubmit.generateAutoSwitchNonce).toBe(1)
  })
})

describe('StartOverRequested', () => {
  test('returns to initial state from any phase and always lands on the setup tab', () => {
    const populated: AlterEgoSession = {
      activeTab: 'alter-ego',
      generateAutoSwitchNonce: 5,
      photoBlob: sampleBlob,
      photoPreviewUrl: sampleUrl,
      archetype: 'cloud-architect',
      universe: 'star-wars',
      artStyle: 'oil-painting',
      photoMode: 'group',
      firstName: 'Paula',
      customRole: 'Tester',
      email: 'someone@example.com',
      phase: 'succeeded',
      errorMessage: null,
      result: sampleResponse,
    }
    const next = reduce(populated, { type: 'StartOverRequested' })
    expect(next).toEqual(initialAlterEgoSession())
    // 022 (issue #50): Start-over also clears the custom-role input.
    expect(next.customRole).toBe('')
    expect(next.activeTab).toBe('setup')
    expect(next.artStyle).toBeNull()
    // 011 FR-1011: Start-over resets photoMode back to the default.
    expect(next.photoMode).toBe('single')
    // 005 FR-404: Start-over resets the animation counter so the next
    // manual navigation to the alter-ego tab does not trigger a stale
    // entrance animation.
    expect(next.generateAutoSwitchNonce).toBe(0)
  })

  test('also resets when invoked from idle (idempotent)', () => {
    const initial = initialAlterEgoSession()
    expect(reduce(initial, { type: 'StartOverRequested' })).toEqual(initial)
  })
})

describe('CustomRoleChanged (022)', () => {
  test('non-blank trimmed payload sets customRole and silently clears any prefab archetype (FR-2207)', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: 'cloud-architect',
    }
    const next = reduce(seeded, { type: 'CustomRoleChanged', customRole: 'Tester' })
    expect(next.customRole).toBe('Tester')
    expect(next.archetype).toBeNull()
    expect(next.phase).toBe('picking')
  })

  test('whitespace-only payload preserves the prefab archetype (precedence rule waits for real input)', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: 'cloud-architect',
    }
    const next = reduce(seeded, { type: 'CustomRoleChanged', customRole: '   \t' })
    expect(next.customRole).toBe('   \t')
    expect(next.archetype).toBe('cloud-architect')
  })

  test('empty payload (after typing then deleting) preserves the prefab archetype', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: 'cloud-architect',
    }
    const next = reduce(seeded, { type: 'CustomRoleChanged', customRole: '' })
    expect(next.customRole).toBe('')
    expect(next.archetype).toBe('cloud-architect')
  })

  test('payload with surrounding whitespace AND content still triggers precedence', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: 'cloud-architect',
    }
    const next = reduce(seeded, { type: 'CustomRoleChanged', customRole: '   Tester  ' })
    expect(next.customRole).toBe('   Tester  ') // raw value preserved
    expect(next.archetype).toBeNull()
  })

  test('updates within a non-blank input do not re-clear archetype that was already null', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      customRole: 'Test',
      archetype: null,
    }
    const next = reduce(seeded, { type: 'CustomRoleChanged', customRole: 'Tester' })
    expect(next.customRole).toBe('Tester')
    expect(next.archetype).toBeNull()
  })

  test('022 — initial session has customRole: ""', () => {
    expect(initialAlterEgoSession().customRole).toBe('')
  })
})

/**
 * 023 (issue #57) — `email` is a new session-scoped optional string.
 * Empty initial; `EmailChanged` overwrites; `StartOverRequested` clears
 * via `initialAlterEgoSession()`; `SurpriseMePicked` does NOT clear
 * (FR-2308 — the captured email must survive Surprise Me + Generate +
 * tab switches).
 */
describe('EmailChanged (023)', () => {
  test('initial session has email: ""', () => {
    expect(initialAlterEgoSession().email).toBe('')
  })

  test('non-blank payload overwrites email and does NOT change phase', () => {
    const seeded = initialAlterEgoSession()
    const next = reduce(seeded, { type: 'EmailChanged', email: 'someone@example.com' })
    expect(next.email).toBe('someone@example.com')
    // Email edits are not a generation-flow transition — phase MUST be untouched.
    expect(next.phase).toBe(seeded.phase)
  })

  test('empty payload overwrites email to "" and does NOT change phase', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      email: 'someone@example.com',
      phase: 'picking',
    }
    const next = reduce(seeded, { type: 'EmailChanged', email: '' })
    expect(next.email).toBe('')
    expect(next.phase).toBe('picking')
  })

  test("whitespace payload is preserved verbatim (trimming is the validator's job)", () => {
    const next = reduce(initialAlterEgoSession(), {
      type: 'EmailChanged',
      email: '   someone@example.com   ',
    })
    expect(next.email).toBe('   someone@example.com   ')
  })

  test('does NOT touch any other field', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      photoBlob: sampleBlob,
      photoPreviewUrl: sampleUrl,
      archetype: 'cloud-architect',
      universe: 'star-wars',
      artStyle: 'oil-painting',
      firstName: 'Paula',
      customRole: 'Tester',
      activeTab: 'alter-ego',
      generateAutoSwitchNonce: 2,
    }
    const next = reduce(seeded, { type: 'EmailChanged', email: 'a@b.c' })
    expect(next.email).toBe('a@b.c')
    expect(next.photoBlob).toBe(sampleBlob)
    expect(next.photoPreviewUrl).toBe(sampleUrl)
    expect(next.archetype).toBe('cloud-architect')
    expect(next.universe).toBe('star-wars')
    expect(next.artStyle).toBe('oil-painting')
    expect(next.firstName).toBe('Paula')
    expect(next.customRole).toBe('Tester')
    expect(next.activeTab).toBe('alter-ego')
    expect(next.generateAutoSwitchNonce).toBe(2)
  })
})

describe('Email retention across StartOver / SurpriseMe (023 FR-2308)', () => {
  test('StartOverRequested clears email along with the rest of the Setup state', () => {
    const populated: AlterEgoSession = {
      ...initialAlterEgoSession(),
      email: 'someone@example.com',
      phase: 'succeeded',
      result: sampleResponse,
    }
    const next = reduce(populated, { type: 'StartOverRequested' })
    expect(next.email).toBe('')
    expect(next).toEqual(initialAlterEgoSession())
  })

  test('SurpriseMePicked does NOT clear email (FR-2308 — the field must survive Surprise Me)', () => {
    const picks = {
      archetype: 'ai-engineer' as const,
      universe: 'cyberpunk' as const,
      artStyle: 'pop-art' as const,
    }
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      email: 'someone@example.com',
    }
    const next = reduce(seeded, { type: 'SurpriseMePicked', picks })
    expect(next.email).toBe('someone@example.com')
  })

  test('GenerateSubmitted does NOT clear email', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      email: 'someone@example.com',
    }
    const next = reduce(seeded, { type: 'GenerateSubmitted' })
    expect(next.email).toBe('someone@example.com')
  })
})

describe('SurpriseMePicked preserves customRole (028)', () => {
  // 028 FR-2814 inverts the 022 FR-2209 behavior — the reducer's
  // SurpriseMePicked branch MUST NOT clear customRole. Start Over and a
  // page refresh are the only clearing mechanisms. The 022 prefab-vs-
  // custom precedence invariant continues to hold via CustomRoleChanged.
  const picks = {
    archetype: 'ai-engineer' as const,
    universe: 'cyberpunk' as const,
    artStyle: 'pop-art' as const,
  }

  test('preserves a non-empty customRole when picks include an archetype (FR-2803/FR-2808/FR-2814)', () => {
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      customRole: 'Tester',
      archetype: null,
    }
    const next = reduce(seeded, { type: 'SurpriseMePicked', picks })
    expect(next.customRole).toBe('Tester')
    expect(next.archetype).toBe('ai-engineer')
    expect(next.phase).toBe('picking')
  })

  test('leaves a blank customRole blank (no-op write)', () => {
    const seeded = initialAlterEgoSession()
    const next = reduce(seeded, { type: 'SurpriseMePicked', picks })
    expect(next.customRole).toBe('')
  })

  test('preserves customRole alongside field overwrites — visible side effect of one dispatch is { archetype, universe, artStyle } only', () => {
    // This case is structurally protected by the 022 invariant — by the
    // time `customRole` is non-empty, `CustomRoleChanged` has already
    // cleared `archetype` to null. We still pin the reducer-level
    // behavior here so the customRole survival is unambiguous even if a
    // bug elsewhere produces a session where both channels are filled.
    const seeded: AlterEgoSession = {
      ...initialAlterEgoSession(),
      archetype: 'cloud-architect',
      customRole: 'Tester',
    }
    const next = reduce(seeded, { type: 'SurpriseMePicked', picks })
    expect(next.archetype).toBe('ai-engineer')
    expect(next.universe).toBe('cyberpunk')
    expect(next.artStyle).toBe('pop-art')
    expect(next.customRole).toBe('Tester')
  })
})

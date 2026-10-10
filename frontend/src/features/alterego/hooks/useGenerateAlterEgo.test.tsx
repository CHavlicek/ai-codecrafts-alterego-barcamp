import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { AlterEgoProvider } from '../state/AlterEgoProvider'
import { FALLBACK_NOTICE_COPY } from '../constants'
import { ARCHETYPE_OPTIONS, ART_STYLE_OPTIONS, UNIVERSE_OPTIONS } from '../options'
import { useAlterEgoSession } from './useAlterEgoSession'
import { useGenerateAlterEgo } from './useGenerateAlterEgo'
import type { AlterEgoResponse, Selections } from '../types'

/**
 * T043 — useGenerateAlterEgo wires {@link generateAlterEgo} into a
 * TanStack Query mutation and dispatches reducer actions on each
 * lifecycle step. Tests mock the alterEgoClient module so we control
 * the mutation result without touching fetch.
 */

const generateAlterEgoMock =
  vi.fn<
    (args: {
      photoBlob: Blob
      selections: Selections
      correlationId: string
    }) => Promise<AlterEgoResponse>
  >()

vi.mock('../services/alterEgoClient', () => ({
  generateAlterEgo: (args: { photoBlob: Blob; selections: Selections; correlationId: string }) =>
    generateAlterEgoMock(args),
}))

const sampleSelections: Selections = {
  archetype: 'software-developer',
  universe: 'star-wars',
  artStyle: 'oil-painting',
  photoMode: 'single',
  firstName: 'Paula',
  // 020 — pose and vibe are no longer in Selections.
}

const sampleResponseSuccess: AlterEgoResponse = {
  character: {
    heroTitleLine1: 'PAULA',
    heroTitleLine2: 'The Cloud Guardrail',
    tagline: 'STILL SHIPS ON FRIDAYS.',
    superpowers: ['p1', 'p2', 'p3'],
    quote: 'Quote.',
  },
  poster: {
    dataUrl: 'data:image/png;base64,XX',
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

const sampleResponseFallback: AlterEgoResponse = {
  ...sampleResponseSuccess,
  meta: {
    outcome: 'fallback',
    provider: 'stub',
    reason: 'network_error',
    correlationId: '00000000-0000-0000-0000-000000000002',
  },
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return (
    <QueryClientProvider client={client}>
      <AlterEgoProvider>{children}</AlterEgoProvider>
    </QueryClientProvider>
  )
}

function useTestHook() {
  const session = useAlterEgoSession()
  const mutation = useGenerateAlterEgo()
  return { session, mutation }
}

describe('useGenerateAlterEgo', () => {
  beforeEach(() => {
    generateAlterEgoMock.mockReset()
    if (typeof crypto.randomUUID !== 'function') {
      vi.stubGlobal('crypto', { randomUUID: () => '11111111-1111-1111-1111-111111111111' })
    }
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  test('on success → dispatches GenerateSubmitted then GenerateSucceeded', async () => {
    generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
    const { result } = renderHook(useTestHook, { wrapper })
    const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

    result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })

    await waitFor(() => expect(result.current.session.state.phase).toBe('succeeded'))
    expect(result.current.session.state.result).toEqual(sampleResponseSuccess)
    expect(result.current.session.state.errorMessage).toBeNull()
    expect(generateAlterEgoMock).toHaveBeenCalledTimes(1)
  })

  test('005 FR-401 — onMutate latches the animation nonce BEFORE the mutation resolves', async () => {
    // Gate the mock so we can sample state mid-flight: submit, wait for
    // the auto-switch to settle, then resolve the promise and assert the
    // nonce is already 1. A monotonic counter is the contract the
    // TabsShell effect relies on.
    let resolveMock!: (r: AlterEgoResponse) => void
    generateAlterEgoMock.mockImplementationOnce(
      () =>
        new Promise<AlterEgoResponse>((resolve) => {
          resolveMock = resolve
        }),
    )
    const { result } = renderHook(useTestHook, { wrapper })
    const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

    result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })

    await waitFor(() => expect(result.current.session.state.activeTab).toBe('alter-ego'))
    // The auto-switch (ActiveTabChanged with reason='generate') and
    // GenerateSubmitted both fired in onMutate; after the commit the
    // nonce MUST be 1 and the phase MUST be 'generating'.
    expect(result.current.session.state.generateAutoSwitchNonce).toBe(1)
    expect(result.current.session.state.phase).toBe('generating')

    resolveMock(sampleResponseSuccess)
    await waitFor(() => expect(result.current.session.state.phase).toBe('succeeded'))
    // And the nonce survives the entire mutation lifecycle unchanged.
    expect(result.current.session.state.generateAutoSwitchNonce).toBe(1)
  })

  test('005 Acceptance 4 — re-clicking Generate bumps the nonce even when the alter-ego tab is already active', async () => {
    generateAlterEgoMock.mockResolvedValue(sampleResponseSuccess)
    const { result } = renderHook(useTestHook, { wrapper })
    const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

    result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })
    await waitFor(() => expect(result.current.session.state.generateAutoSwitchNonce).toBe(1))
    await waitFor(() => expect(result.current.session.state.phase).toBe('succeeded'))

    result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })
    await waitFor(() => expect(result.current.session.state.generateAutoSwitchNonce).toBe(2))
  })

  test('on server-side fallback outcome → dispatches GenerateFailedWithFallback with FR-214 copy', async () => {
    generateAlterEgoMock.mockResolvedValueOnce(sampleResponseFallback)
    const { result } = renderHook(useTestHook, { wrapper })
    const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

    result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })

    await waitFor(() => expect(result.current.session.state.phase).toBe('failed_with_fallback'))
    expect(result.current.session.state.result).toEqual(sampleResponseFallback)
    // T041 / FR-214: errorMessage MUST be the generic constant, not something
    // derived from the server's reason code or the outcome value.
    expect(result.current.session.state.errorMessage).toBe(FALLBACK_NOTICE_COPY)
  })

  test('fallback errorMessage does not leak reason or provider name regardless of reason code', async () => {
    // Run once per reason code; every variant MUST resolve to the same
    // FALLBACK_NOTICE_COPY string (FR-214 single-variant).
    const reasons = [
      'not_configured',
      'network_error',
      'rate_limited',
      'timeout',
      'malformed_response',
      'safety_refused',
    ] as const
    for (const reason of reasons) {
      generateAlterEgoMock.mockResolvedValueOnce({
        ...sampleResponseFallback,
        meta: { outcome: 'fallback', provider: 'stub', reason, correlationId: 'corr-' + reason },
      })
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })
      result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })
      await waitFor(() => expect(result.current.session.state.phase).toBe('failed_with_fallback'))
      expect(result.current.session.state.errorMessage).toBe(FALLBACK_NOTICE_COPY)
      expect(result.current.session.state.errorMessage).not.toContain(reason)
    }
  })

  test('unknown outcome wire value is treated as fallback for graceful degradation', async () => {
    // Forward-compatible: a server that adds a new outcome like 'real-degraded'
    // must not accidentally render as a success. We cast to bypass the type
    // system for this specific degradation test.
    const forwardCompatResponse = {
      ...sampleResponseSuccess,
      meta: {
        outcome: 'future-unknown' as 'real',
        provider: 'gemini' as const,
        correlationId: 'x',
      },
    }
    generateAlterEgoMock.mockResolvedValueOnce(forwardCompatResponse)
    const { result } = renderHook(useTestHook, { wrapper })
    const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

    result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })

    await waitFor(() => expect(result.current.session.state.phase).toBe('failed_with_fallback'))
    expect(result.current.session.state.errorMessage).toBe(FALLBACK_NOTICE_COPY)
  })

  /**
   * 009 Surprise Me — hook-level surprise() entry point. The mutation
   * must go through the identical onMutate → onSuccess path a normal
   * submit() uses (FR-906, FR-908); the only difference is that the
   * selections are randomized and the Setup form reflects those picks
   * via SurpriseMePicked (FR-907) before the request fires.
   */
  describe('surprise() — 009 Surprise Me entry point', () => {
    test('dispatches SurpriseMePicked BEFORE the mutation fires, so the Setup form reflects the picks', async () => {
      // Gate the mock so we can sample state right after the onMutate
      // handler runs (tab auto-switch + GenerateSubmitted) but before the
      // promise resolves. At that point all five category fields MUST be
      // non-null on the session.
      let resolveMock!: (r: AlterEgoResponse) => void
      generateAlterEgoMock.mockImplementationOnce(
        () =>
          new Promise<AlterEgoResponse>((resolve) => {
            resolveMock = resolve
          }),
      )
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(result.current.session.state.phase).toBe('generating'))
      // FR-907 — by the time the phase has transitioned, the reducer's
      // SurpriseMePicked case has already committed the visible picks.
      // 020 delta: Pose and Vibe are no longer on session state.
      const state = result.current.session.state
      expect(ARCHETYPE_OPTIONS.map((o) => o.value)).toContain(state.archetype)
      expect(UNIVERSE_OPTIONS.map((o) => o.value)).toContain(state.universe)
      expect(ART_STYLE_OPTIONS.map((o) => o.value)).toContain(state.artStyle)

      resolveMock(sampleResponseSuccess)
      await waitFor(() => expect(result.current.session.state.phase).toBe('succeeded'))
    })

    test('outbound request body matches the picks the reducer committed (FR-906)', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: '  Paula  ',
        photoMode: 'single',
      })

      await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
      const call = generateAlterEgoMock.mock.calls[0]!
      const arg = call[0]
      const stateAfter = result.current.session.state
      expect(arg.photoBlob).toBe(photo)
      // The Selections submitted to the service MUST match the picks on
      // the session — i.e. Setup would show the same values the backend
      // sees. 020 delta: pose and vibe are no longer here; the server
      // rolls them per request.
      expect(arg.selections.archetype).toBe(stateAfter.archetype)
      expect(arg.selections.universe).toBe(stateAfter.universe)
      expect(arg.selections.artStyle).toBe(stateAfter.artStyle)
      expect(arg.selections).not.toHaveProperty('pose')
      expect(arg.selections).not.toHaveProperty('vibe')
      // firstName is trimmed (matches the submit() path contract).
      expect(arg.selections.firstName).toBe('Paula')
      expect(arg.correlationId).toMatch(/[0-9a-f-]{8,}/i)
    })

    test('011 FR-1012 — surprise() preserves the caller-supplied photoMode and does NOT randomise it', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'The Architects',
        photoMode: 'group',
      })

      await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
      const arg = generateAlterEgoMock.mock.calls[0]![0]
      // Mode is preserved verbatim — the randomiser only touches the five
      // category fields.
      expect(arg.selections.photoMode).toBe('group')
    })

    test('surprise() triggers the same 005 tab-switch animation as submit() — nonce increments to 1', async () => {
      // Gate the mutation so we can observe the intermediate "generating"
      // state before the resolve lands; otherwise the phase would race
      // through to "succeeded" by the time waitFor settles.
      let resolveMock!: (r: AlterEgoResponse) => void
      generateAlterEgoMock.mockImplementationOnce(
        () =>
          new Promise<AlterEgoResponse>((resolve) => {
            resolveMock = resolve
          }),
      )
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(result.current.session.state.activeTab).toBe('alter-ego'))
      expect(result.current.session.state.generateAutoSwitchNonce).toBe(1)
      expect(result.current.session.state.phase).toBe('generating')

      resolveMock(sampleResponseSuccess)
      await waitFor(() => expect(result.current.session.state.phase).toBe('succeeded'))
    })

    // ------------------------------------------------------------------
    // 028 partial-surprise-me — surprise() must read the current session
    // before rolling and override the random pick for any category that
    // is already explicit (FR-2801..FR-2806 + FR-2808 + FR-2812).
    // ------------------------------------------------------------------

    test('028 US1 — explicit Universe is preserved through surprise() (FR-2805/FR-2808)', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      // Seed Universe explicitly before clicking Surprise Me.
      await waitFor(() => expect(result.current.session.state.phase).toBe('idle'))
      result.current.session.dispatch({ type: 'UniverseSelected', universe: 'star-wars' })
      await waitFor(() => expect(result.current.session.state.universe).toBe('star-wars'))

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
      const arg = generateAlterEgoMock.mock.calls[0]![0]
      expect(arg.selections.universe).toBe('star-wars')
      expect(ARCHETYPE_OPTIONS.map((o) => o.value)).toContain(arg.selections.archetype)
      expect(ART_STYLE_OPTIONS.map((o) => o.value)).toContain(arg.selections.artStyle)
      // Committed state matches the wire — the Setup form will reflect
      // the user's explicit pick + the rolled values on return.
      expect(result.current.session.state.universe).toBe('star-wars')
    })

    test('028 US1 — explicit Archetype is preserved through surprise() (FR-2802)', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.session.dispatch({
        type: 'ArchetypeSelected',
        archetype: 'software-developer',
      })
      await waitFor(() => expect(result.current.session.state.archetype).toBe('software-developer'))

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
      const arg = generateAlterEgoMock.mock.calls[0]![0]
      expect(arg.selections.archetype).toBe('software-developer')
      expect(UNIVERSE_OPTIONS.map((o) => o.value)).toContain(arg.selections.universe)
      expect(ART_STYLE_OPTIONS.map((o) => o.value)).toContain(arg.selections.artStyle)
      expect(result.current.session.state.archetype).toBe('software-developer')
    })

    test('028 US1 — multiple explicit categories all pass through unchanged (FR-2805)', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.session.dispatch({ type: 'UniverseSelected', universe: 'retro-synthwave' })
      result.current.session.dispatch({ type: 'ArtStyleSelected', artStyle: 'pop-art' })
      await waitFor(() => expect(result.current.session.state.universe).toBe('retro-synthwave'))
      await waitFor(() => expect(result.current.session.state.artStyle).toBe('pop-art'))

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
      const arg = generateAlterEgoMock.mock.calls[0]![0]
      expect(arg.selections.universe).toBe('retro-synthwave')
      expect(arg.selections.artStyle).toBe('pop-art')
      // Only the still-empty archetype slot gets a rolled value.
      expect(ARCHETYPE_OPTIONS.map((o) => o.value)).toContain(arg.selections.archetype)
    })

    test('028 US2 — non-empty customRole survives surprise() and flows onto the wire (FR-2803/FR-2808)', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.session.dispatch({
        type: 'CustomRoleChanged',
        customRole: 'Distinguished Spreadsheet Wrangler',
      })
      await waitFor(() =>
        expect(result.current.session.state.customRole).toBe('Distinguished Spreadsheet Wrangler'),
      )
      // 022 invariant: CustomRoleChanged with a non-blank value clears any
      // prefab archetype. Re-assert here so the test is self-explanatory.
      expect(result.current.session.state.archetype).toBeNull()

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
      const arg = generateAlterEgoMock.mock.calls[0]![0]
      expect(arg.selections.customRole).toBe('Distinguished Spreadsheet Wrangler')
      // Universe + Art Style are empty → rolled normally.
      expect(UNIVERSE_OPTIONS.map((o) => o.value)).toContain(arg.selections.universe)
      expect(ART_STYLE_OPTIONS.map((o) => o.value)).toContain(arg.selections.artStyle)
      // Session retains customRole — the reducer's 022 FR-2209 clear is
      // removed in this feature (FR-2814).
      expect(result.current.session.state.customRole).toBe('Distinguished Spreadsheet Wrangler')
    })

    test('028 US4 — all explicit → zero random substitutions, payload mirrors session', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseSuccess)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.session.dispatch({
        type: 'ArchetypeSelected',
        archetype: 'software-developer',
      })
      result.current.session.dispatch({ type: 'UniverseSelected', universe: 'star-wars' })
      result.current.session.dispatch({ type: 'ArtStyleSelected', artStyle: 'oil-painting' })
      await waitFor(() => expect(result.current.session.state.artStyle).toBe('oil-painting'))

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
      const arg = generateAlterEgoMock.mock.calls[0]![0]
      expect(arg.selections.archetype).toBe('software-developer')
      expect(arg.selections.universe).toBe('star-wars')
      expect(arg.selections.artStyle).toBe('oil-painting')
    })

    test('on server-side fallback outcome via surprise() → GenerateFailedWithFallback with FR-214 copy', async () => {
      generateAlterEgoMock.mockResolvedValueOnce(sampleResponseFallback)
      const { result } = renderHook(useTestHook, { wrapper })
      const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

      result.current.mutation.surprise({
        photoBlob: photo,
        firstName: 'Paula',
        photoMode: 'single',
      })

      await waitFor(() => expect(result.current.session.state.phase).toBe('failed_with_fallback'))
      expect(result.current.session.state.errorMessage).toBe(FALLBACK_NOTICE_COPY)
      // Setup still shows the seeded picks even after a fallback outcome
      // (the reducer never clears them outside StartOverRequested).
      // 020: assert on the remaining visible categories.
      expect(result.current.session.state.archetype).not.toBeNull()
      expect(result.current.session.state.artStyle).not.toBeNull()
    })
  })

  test('on thrown error → dispatches GenerateFailedWithFallback with FE-side fallback + generic copy', async () => {
    generateAlterEgoMock.mockRejectedValueOnce(new Error('network down'))
    const { result } = renderHook(useTestHook, { wrapper })
    const photo = new Blob([new Uint8Array([1])], { type: 'image/jpeg' })

    result.current.mutation.submit({ photoBlob: photo, selections: sampleSelections })

    await waitFor(() => expect(result.current.session.state.phase).toBe('failed_with_fallback'))
    // FR-214: even on a thrown error, the user-visible message is the
    // generic constant — NOT the raw error.message.
    expect(result.current.session.state.errorMessage).toBe(FALLBACK_NOTICE_COPY)
    expect(result.current.session.state.errorMessage).not.toContain('network down')
    expect(result.current.session.state.result?.character.heroTitleLine1).toBe('PAULA')
    expect(result.current.session.state.result?.character.heroTitleLine2).toBe('The Resilient')
    expect(result.current.session.state.result?.meta.outcome).toBe('fallback')
  })
})

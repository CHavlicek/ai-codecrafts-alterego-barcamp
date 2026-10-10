import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { AlterEgoPage } from './AlterEgoPage'
import { LiveRegionProvider } from '../../components/LiveRegion'
import type { AlterEgoResponse, Selections } from './types'

/**
 * Seed a committed photo via the PhotoIntake camera capture flow without
 * going through the real useCameraStream hook. The hook is mocked below
 * with a trivial state machine: start() → live, captureStill() → still,
 * commit() → returns a stub JPEG Blob.
 */
interface MockCamState {
  status: 'idle' | 'live' | 'still-preview'
  stillPreviewUrl: string | null
}
const mockCam: MockCamState = { status: 'idle', stillPreviewUrl: null }
const mockListeners = new Set<() => void>()
const mockStartSpy = vi.fn(async () => {
  mockCam.status = 'live'
  mockListeners.forEach((l) => l())
})
const mockCaptureSpy = vi.fn(async () => {
  mockCam.status = 'still-preview'
  mockCam.stillPreviewUrl = 'blob:fake-still'
  mockListeners.forEach((l) => l())
})
const mockCommitSpy = vi.fn(async () => {
  mockCam.status = 'idle'
  mockCam.stillPreviewUrl = null
  mockListeners.forEach((l) => l())
  return new Blob([new Uint8Array(1024)], { type: 'image/jpeg' })
})
const mockRetakeSpy = vi.fn()
const mockCancelSpy = vi.fn()

vi.mock('./hooks/useCameraStream', async () => {
  const react = await import('react')
  return {
    useCameraStream: () => {
      const [, forceUpdate] = react.useReducer((x: number) => x + 1, 0)
      react.useEffect(() => {
        mockListeners.add(forceUpdate)
        return () => {
          mockListeners.delete(forceUpdate)
        }
      }, [])
      return {
        status: mockCam.status,
        unavailableReason: null,
        stream:
          mockCam.status === 'live'
            ? ({
                getTracks: () => [],
                getVideoTracks: () => [],
              } as unknown as MediaStream)
            : null,
        stillPreviewUrl: mockCam.stillPreviewUrl,
        start: mockStartSpy,
        captureStill: mockCaptureSpy,
        retake: mockRetakeSpy,
        commit: mockCommitSpy,
        cancel: mockCancelSpy,
      }
    },
  }
})

async function seedPhoto(): Promise<void> {
  const user = userEvent.setup()
  await user.click(screen.getByRole('button', { name: /take a photo of yourself/i }))
  await waitFor(() =>
    expect(screen.getByRole('button', { name: /take photo/i })).toBeInTheDocument(),
  )
  await user.click(screen.getByRole('button', { name: /take photo/i }))
  await waitFor(() =>
    expect(screen.getByRole('button', { name: /keep photo/i })).toBeInTheDocument(),
  )
  await user.click(screen.getByRole('button', { name: /keep photo/i }))
}

/**
 * Integration test for the page composition — exercises the happy path
 * (form → generating → success poster → start over) against mocked
 * alterEgoClient.
 *
 * Coverage lift: without this test AlterEgoPage.tsx is 0% covered.
 */

const generateAlterEgoMock =
  vi.fn<
    (args: {
      photoBlob: Blob
      selections: Selections
      correlationId: string
    }) => Promise<AlterEgoResponse>
  >()

vi.mock('./services/alterEgoClient', () => ({
  generateAlterEgo: (args: { photoBlob: Blob; selections: Selections; correlationId: string }) =>
    generateAlterEgoMock(args),
}))

vi.mock('./lib/downscalePhoto', () => ({
  downscalePhoto: vi.fn(async (blob: Blob) => blob),
}))

const sampleResponse: AlterEgoResponse = {
  character: {
    heroTitleLine1: 'PAULA',
    heroTitleLine2: 'The Cloud Guardrail',
    tagline: 'STILL SHIPS ON FRIDAYS.',
    superpowers: ['p1', 'p2', 'p3'],
    quote: 'quote',
  },
  poster: {
    dataUrl: 'data:image/png;base64,XX',
    mediaType: 'image/png',
    widthPx: 900,
    heightPx: 1200,
  },
  meta: { outcome: 'real', provider: 'gemini', correlationId: 'abc' },
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return (
    <QueryClientProvider client={client}>
      <LiveRegionProvider>{children}</LiveRegionProvider>
    </QueryClientProvider>
  )
}

describe('AlterEgoPage', () => {
  beforeEach(() => {
    generateAlterEgoMock.mockReset()
    ;(URL as { createObjectURL: (b: Blob) => string }).createObjectURL = () => 'blob:fake-url'
    ;(URL as { revokeObjectURL: (u: string) => void }).revokeObjectURL = () => {}
    if (typeof crypto.randomUUID !== 'function') {
      vi.stubGlobal('crypto', { randomUUID: () => '11111111-1111-1111-1111-111111111111' })
    }
    mockCam.status = 'idle'
    mockCam.stillPreviewUrl = null
    mockStartSpy.mockClear()
    mockCaptureSpy.mockClear()
    mockCommitSpy.mockClear()
    mockRetakeSpy.mockClear()
    mockCancelSpy.mockClear()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  test('renders the form on mount with Generate disabled', () => {
    render(<AlterEgoPage />, { wrapper })
    expect(
      screen.getByRole('heading', { name: /AI @ VERBUND 2026/i, level: 1 }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /take a photo of yourself/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
  })

  test('happy path: form → generating → poster → start over', async () => {
    generateAlterEgoMock.mockResolvedValueOnce(sampleResponse)
    const user = userEvent.setup()
    render(<AlterEgoPage />, { wrapper })

    await seedPhoto()

    await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
    await user.click(screen.getByRole('radio', { name: 'Star Wars' }))
    await user.click(screen.getByRole('radio', { name: 'Pop Art' }))
    await user.type(screen.getByLabelText('First name'), 'Paula')

    const generate = screen.getByRole('button', { name: /generate my alter ego/i })
    await waitFor(() => expect(generate).toBeEnabled())
    await user.click(generate)

    // 017 FR-1713: heroTitleLine1 + tagline are baked into the poster
    // image now and no longer rendered as on-screen HTML. Use the
    // surviving heroTitleLine2 element as the "poster mounted" sentinel.
    await waitFor(() => {
      const posterView = document.querySelector('.poster-view') as HTMLElement | null
      expect(posterView).not.toBeNull()
      expect(posterView!.querySelector('.poster-view__title-line-2')).toHaveTextContent(
        'The Cloud Guardrail',
      )
    })
    const posterView = document.querySelector('.poster-view') as HTMLElement
    expect(posterView).not.toBeNull()
    // The previously-shimmering H2 + the tagline paragraph are gone.
    expect(posterView.querySelector('.poster-view__title-line-1')).toBeNull()
    expect(posterView.querySelector('.poster-view__tagline')).toBeNull()
    // The widened <img alt> carries the three in-image strings (FR-1714).
    const img = posterView.querySelector('img.poster-view__image') as HTMLImageElement
    // 017 (refined 2026-05-08): alt now carries firstName + humanised
    // archetype only — hero title / tagline live only on the image.
    expect(img.alt).toBe('Alter ego poster for Paula, Software Developer. quote.')
    expect(generateAlterEgoMock).toHaveBeenCalledTimes(1)

    const startOver = screen.getByRole('button', { name: /start over/i })
    await user.click(startOver)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /take a photo of yourself/i })).toBeInTheDocument(),
    )
    expect(screen.getByRole('button', { name: /generate my alter ego/i })).toBeDisabled()
  })

  test('011 — default Generate payload carries photoMode: "single"', async () => {
    generateAlterEgoMock.mockResolvedValueOnce(sampleResponse)
    const user = userEvent.setup()
    render(<AlterEgoPage />, { wrapper })

    await seedPhoto()
    await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
    await user.click(screen.getByRole('radio', { name: 'Star Wars' }))
    await user.click(screen.getByRole('radio', { name: 'Pop Art' }))
    await user.type(screen.getByLabelText('First name'), 'Paula')

    const generate = screen.getByRole('button', { name: /generate my alter ego/i })
    await waitFor(() => expect(generate).toBeEnabled())
    await user.click(generate)

    await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
    const call = generateAlterEgoMock.mock.calls[0]!
    expect(call[0].selections.photoMode).toBe('single')
  })

  test('011 — flipping the switch to Group sends photoMode: "group" on Generate', async () => {
    generateAlterEgoMock.mockResolvedValueOnce(sampleResponse)
    const user = userEvent.setup()
    render(<AlterEgoPage />, { wrapper })

    await seedPhoto()
    await user.click(screen.getByRole('switch', { name: /photo mode/i }))
    await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
    await user.click(screen.getByRole('radio', { name: 'Star Wars' }))
    await user.click(screen.getByRole('radio', { name: 'Pop Art' }))
    // 011: in group mode the input label switches to "Group name".
    await user.type(screen.getByLabelText('Group name'), 'The Architects')

    const generate = screen.getByRole('button', { name: /generate my alter ego/i })
    await waitFor(() => expect(generate).toBeEnabled())
    await user.click(generate)

    await waitFor(() => expect(generateAlterEgoMock).toHaveBeenCalledTimes(1))
    const call = generateAlterEgoMock.mock.calls[0]!
    expect(call[0].selections.photoMode).toBe('group')
  })

  test('fallback path: server returns outcome=fallback → banner rendered', async () => {
    generateAlterEgoMock.mockResolvedValueOnce({
      ...sampleResponse,
      meta: {
        outcome: 'fallback',
        provider: 'stub',
        reason: 'network_error',
        correlationId: 'xyz',
      },
    })
    const user = userEvent.setup()
    render(<AlterEgoPage />, { wrapper })

    await seedPhoto()

    await user.click(screen.getByRole('radio', { name: 'Software Developer' }))
    await user.click(screen.getByRole('radio', { name: 'Star Wars' }))
    await user.click(screen.getByRole('radio', { name: 'Pop Art' }))
    await user.type(screen.getByLabelText('First name'), 'Paula')
    await user.click(screen.getByRole('button', { name: /generate my alter ego/i }))

    await waitFor(() => {
      const banner = screen
        .getAllByRole('alert')
        .find((el) => /preview image/i.test(el.textContent ?? ''))
      expect(banner).toBeDefined()
    })
  })
})

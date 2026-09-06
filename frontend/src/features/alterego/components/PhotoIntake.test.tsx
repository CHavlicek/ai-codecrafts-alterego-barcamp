import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test, vi } from 'vitest'
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useEffect, useReducer, type ReactNode } from 'react'
import { PhotoIntake } from './PhotoIntake'
import { LiveRegionProvider } from '../../../components/LiveRegion'
import type { CameraUnavailableReason } from '../lib/cameraCapability'
import type { CameraStatus, UseCameraStreamResult } from '../hooks/useCameraStream'

/**
 * T008 + T016 + T020 — PhotoIntake component contract for 004.
 *
 * Covers US1 happy path, US2 absence-of-pills, US3 unavailable branches
 * by mocking useCameraStream so we can assert the component's own
 * branching, ARIA, focus, and pipeline-integration invariants without a
 * real MediaStream.
 */

// Module-level mock state + broadcast channel so async mock functions and
// synchronous `setStatus` helpers can both force a harness re-render
// without losing vi.fn() spy identity.
interface MockState {
  status: CameraStatus
  unavailableReason: CameraUnavailableReason | null
  stream: MediaStream | null
  stillPreviewUrl: string | null
}

const state: MockState = {
  status: 'idle',
  unavailableReason: null,
  stream: null,
  stillPreviewUrl: null,
}

const listeners = new Set<() => void>()
function notify() {
  listeners.forEach((l) => l())
}

const startSpy = vi.fn(async () => {
  state.status = 'live'
  state.stream = {
    getTracks: () => [],
    getVideoTracks: () => [],
  } as unknown as MediaStream
  notify()
})
const captureStillSpy = vi.fn(async () => {
  state.status = 'still-preview'
  state.stillPreviewUrl = 'blob:fake-still'
  notify()
})
const retakeSpy = vi.fn(() => {
  state.status = 'live'
  state.stillPreviewUrl = null
  notify()
})
let commitBlob: Blob = new Blob([new Uint8Array(1024)], { type: 'image/jpeg' })
const commitSpy = vi.fn(async (): Promise<Blob> => {
  state.status = 'idle'
  state.stream = null
  state.stillPreviewUrl = null
  notify()
  return commitBlob
})
const cancelSpy = vi.fn(() => {
  state.status = 'idle'
  state.stream = null
  state.stillPreviewUrl = null
  notify()
})

function currentMock(): UseCameraStreamResult {
  return {
    status: state.status,
    unavailableReason: state.unavailableReason,
    stream: state.stream,
    stillPreviewUrl: state.stillPreviewUrl,
    start: startSpy,
    captureStill: captureStillSpy,
    retake: retakeSpy,
    commit: commitSpy,
    cancel: cancelSpy,
  }
}

vi.mock('../hooks/useCameraStream', () => ({
  useCameraStream: () => {
    // Force-update subscription is installed by Harness; this hook call
    // just reads the current snapshot.
    return currentMock()
  },
}))

vi.mock('../lib/downscalePhoto', () => ({
  downscalePhoto: vi.fn(async (blob: Blob) => blob),
}))

import { downscalePhoto } from '../lib/downscalePhoto'

// Test helpers — advance the mock state, triggering a harness re-render.
function setStatus(status: CameraStatus, reason: CameraUnavailableReason | null = null) {
  state.status = status
  state.unavailableReason = reason
  if (status === 'still-preview') {
    state.stillPreviewUrl = state.stillPreviewUrl ?? 'blob:fake-still'
  } else if (status === 'idle' || status === 'unavailable') {
    state.stillPreviewUrl = null
    state.stream = null
  } else if (status === 'live') {
    state.stream = {
      getTracks: () => [],
      getVideoTracks: () => [],
    } as unknown as MediaStream
  }
  notify()
}

function resetMockState() {
  state.status = 'idle'
  state.unavailableReason = null
  state.stream = null
  state.stillPreviewUrl = null
  startSpy.mockClear()
  captureStillSpy.mockClear()
  retakeSpy.mockClear()
  commitSpy.mockClear()
  cancelSpy.mockClear()
}

function Harness(props: {
  photoPreviewUrl: string | null
  onPhotoSelected?: (blob: Blob, url: string) => void
  onPhotoCleared?: () => void
  externalError?: string | null
}): ReactNode {
  const [, forceUpdate] = useReducer((x: number) => x + 1, 0)
  useEffect(() => {
    listeners.add(forceUpdate)
    return () => {
      listeners.delete(forceUpdate)
    }
  }, [])
  const externalError = props.externalError ?? null
  return (
    <LiveRegionProvider>
      <PhotoIntake
        photoPreviewUrl={props.photoPreviewUrl}
        onPhotoSelected={props.onPhotoSelected ?? (() => {})}
        onPhotoCleared={props.onPhotoCleared ?? (() => {})}
        externalError={externalError}
      />
    </LiveRegionProvider>
  )
}

// ---------------------------------------------------------------------------

const originalCreateObjectURL = URL.createObjectURL as ((b: Blob) => string) | undefined
const originalRevokeObjectURL = URL.revokeObjectURL as ((u: string) => void) | undefined

beforeAll(() => {
  ;(URL as { createObjectURL: (b: Blob) => string }).createObjectURL = () => 'blob:committed'
  ;(URL as { revokeObjectURL: (u: string) => void }).revokeObjectURL = () => {}
})

afterAll(() => {
  if (originalCreateObjectURL) URL.createObjectURL = originalCreateObjectURL
  else delete (URL as { createObjectURL?: unknown }).createObjectURL
  if (originalRevokeObjectURL) URL.revokeObjectURL = originalRevokeObjectURL
  else delete (URL as { revokeObjectURL?: unknown }).revokeObjectURL
})

beforeEach(() => {
  commitBlob = new Blob([new Uint8Array(1024)], { type: 'image/jpeg' })
  resetMockState()
  ;(downscalePhoto as unknown as ReturnType<typeof vi.fn>).mockImplementation(
    async (blob: Blob) => blob,
  )
})

afterEach(() => {
  vi.clearAllMocks()
})

// ---------------------------------------------------------------------------
// US1 — Happy path: tap circle → live → shutter → still → Keep → committed
// ---------------------------------------------------------------------------

describe('US1 — happy path', () => {
  test('renders an accessible button labelled "Take a photo of yourself" when empty', () => {
    render(<Harness photoPreviewUrl={null} />)
    expect(screen.getByRole('button', { name: /take a photo of yourself/i })).toBeInTheDocument()
  })

  test('clicking the circle calls start() on the camera hook', async () => {
    const user = userEvent.setup()
    render(<Harness photoPreviewUrl={null} />)
    await user.click(screen.getByRole('button', { name: /take a photo of yourself/i }))
    expect(startSpy).toHaveBeenCalledTimes(1)
  })

  test('pressing Enter or Space on the focused circle activates it', async () => {
    const user = userEvent.setup()
    render(<Harness photoPreviewUrl={null} />)
    const circle = screen.getByRole('button', { name: /take a photo of yourself/i })
    circle.focus()
    await user.keyboard('{Enter}')
    expect(startSpy).toHaveBeenCalledTimes(1)
    await user.keyboard(' ')
    expect(startSpy).toHaveBeenCalledTimes(2)
  })

  test('once live, a Take photo shutter appears directly below the circle', async () => {
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('live')
    })
    await waitFor(() =>
      expect(screen.getByRole('button', { name: /take photo/i })).toBeInTheDocument(),
    )
  })

  test('pressing the shutter calls captureStill on the hook', async () => {
    const user = userEvent.setup()
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('live')
    })
    await user.click(await screen.findByRole('button', { name: /take photo/i }))
    expect(captureStillSpy).toHaveBeenCalledTimes(1)
  })

  test('in still-preview state, Keep and Retake controls appear (Keep first in Tab order)', async () => {
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('still-preview')
    })
    const keep = await screen.findByRole('button', { name: /keep photo/i })
    const retake = await screen.findByRole('button', { name: /^retake$/i })
    expect(keep.compareDocumentPosition(retake) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  test('pressing Keep commits the still, runs downscalePhoto, calls onPhotoSelected', async () => {
    const user = userEvent.setup()
    const onPhotoSelected = vi.fn()
    render(<Harness photoPreviewUrl={null} onPhotoSelected={onPhotoSelected} />)
    act(() => {
      setStatus('still-preview')
    })
    await user.click(await screen.findByRole('button', { name: /keep photo/i }))
    await waitFor(() => expect(commitSpy).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(downscalePhoto).toHaveBeenCalledTimes(1))
    await waitFor(() => expect(onPhotoSelected).toHaveBeenCalledTimes(1))
    expect(onPhotoSelected).toHaveBeenCalledWith(expect.any(Blob), 'blob:committed')
  })

  test('pressing Retake calls retake() on the hook (stream reuse, no start() re-call)', async () => {
    const user = userEvent.setup()
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('still-preview')
    })
    await user.click(await screen.findByRole('button', { name: /^retake$/i }))
    expect(retakeSpy).toHaveBeenCalledTimes(1)
    expect(startSpy).not.toHaveBeenCalled()
  })

  test('in live state, a Cancel button is available', async () => {
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('live')
    })
    expect(await screen.findByRole('button', { name: /^cancel$/i })).toBeInTheDocument()
  })

  test('clicking Cancel calls cancel() on the hook', async () => {
    const user = userEvent.setup()
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('live')
    })
    await user.click(await screen.findByRole('button', { name: /^cancel$/i }))
    expect(cancelSpy).toHaveBeenCalledTimes(1)
  })

  test('Escape key cancels the in-flight capture', async () => {
    const user = userEvent.setup()
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('live')
    })
    const shutter = await screen.findByRole('button', { name: /take photo/i })
    shutter.focus()
    await user.keyboard('{Escape}')
    expect(cancelSpy).toHaveBeenCalledTimes(1)
  })

  test('committed-state preview uses .photo-intake__image (parity with 001/002/003)', () => {
    const { container } = render(<Harness photoPreviewUrl="blob:committed-photo" />)
    const img = container.querySelector('.photo-intake__image') as HTMLImageElement | null
    expect(img).not.toBeNull()
    expect(img!.src).toContain('blob:committed-photo')
  })

  test('re-activating the circle after a committed photo clears it first (FR-304)', async () => {
    const user = userEvent.setup()
    const onPhotoCleared = vi.fn()
    render(<Harness photoPreviewUrl="blob:committed-photo" onPhotoCleared={onPhotoCleared} />)
    await user.click(screen.getByRole('button', { name: /retake your photo/i }))
    expect(onPhotoCleared).toHaveBeenCalledTimes(1)
    expect(startSpy).toHaveBeenCalledTimes(1)
  })

  test('circle aria-label flips to "Retake your photo" when a photo exists', () => {
    render(<Harness photoPreviewUrl="blob:committed-photo" />)
    expect(screen.getByRole('button', { name: /retake your photo/i })).toBeInTheDocument()
  })
})

// ---------------------------------------------------------------------------
// US2 — No Camera / Upload photo controls in any state
// ---------------------------------------------------------------------------

describe('US2 — removed pill buttons and file-picker surfaces', () => {
  test.each<[string, string | null, CameraStatus, CameraUnavailableReason | null]>([
    ['empty', null, 'idle', null],
    ['live', null, 'live', null],
    ['still-preview', null, 'still-preview', null],
    ['committed', 'blob:committed-photo', 'idle', null],
    ['unavailable', null, 'unavailable', 'permission_denied'],
  ])('no Camera/Upload control in the %s state', (_label, previewUrl, status, reason) => {
    const { container } = render(<Harness photoPreviewUrl={previewUrl} />)
    act(() => {
      setStatus(status, reason)
    })
    expect(screen.queryByRole('button', { name: /^camera$/i })).toBeNull()
    expect(screen.queryByLabelText(/upload photo/i)).toBeNull()
    expect(container.querySelector('input[type="file"]')).toBeNull()
    expect(container.querySelector('.photo-intake__pill')).toBeNull()
    expect(container.querySelector('.photo-intake__file-input')).toBeNull()
  })
})

// ---------------------------------------------------------------------------
// US3 — Graceful handling when the camera is unavailable
// ---------------------------------------------------------------------------

describe('US3 — unavailable state', () => {
  test.each<[CameraUnavailableReason, RegExp]>([
    ['permission_denied', /camera access is needed/i],
    ['no_camera_hardware', /camera access is needed/i],
    ['stream_unavailable', /camera access is needed/i],
  ])('%s → generic polite message via role=alert', (reason, expected) => {
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('unavailable', reason)
    })
    const alerts = screen.getAllByRole('alert')
    expect(alerts.some((el) => expected.test(el.textContent ?? ''))).toBe(true)
    for (const alert of alerts) {
      expect(alert.textContent).not.toMatch(/NotAllowedError|NotFoundError|NotReadableError/)
    }
  })

  test('capability_missing uses the FR-313 "supported browser" variant', () => {
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('unavailable', 'capability_missing')
    })
    const alerts = screen.getAllByRole('alert')
    const match = alerts.find(
      (el) =>
        /doesn't support/i.test(el.textContent ?? '') &&
        /supported browser/i.test(el.textContent ?? ''),
    )
    expect(match).toBeDefined()
  })

  test('circle remains activable in unavailable state (retry path per FR-314)', async () => {
    const user = userEvent.setup()
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('unavailable', 'permission_denied')
    })
    const circle = screen.getByRole('button', { name: /take a photo of yourself/i })
    expect(circle).not.toBeDisabled()
    await user.click(circle)
    expect(startSpy).toHaveBeenCalledTimes(1)
  })

  test('unavailable → live transition after re-activation (recovery per FR-314)', async () => {
    render(<Harness photoPreviewUrl={null} />)
    act(() => {
      setStatus('unavailable', 'permission_denied')
    })
    expect(screen.getAllByRole('alert').length).toBeGreaterThan(0)

    act(() => {
      setStatus('live')
    })
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /take photo/i })).toBeInTheDocument()
    })
  })
})

// ---------------------------------------------------------------------------
// External error — backend-surfaced rejection still renders alongside
// ---------------------------------------------------------------------------

describe('external error', () => {
  test('surfaces externalError alongside the circle', () => {
    render(<Harness photoPreviewUrl={null} externalError="Server said no" />)
    const alerts = screen.getAllByRole('alert')
    expect(alerts.some((el) => /server said no/i.test(el.textContent ?? ''))).toBe(true)
  })
})

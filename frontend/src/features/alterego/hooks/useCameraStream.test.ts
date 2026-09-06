import { act, renderHook, waitFor } from '@testing-library/react'
import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, test, vi } from 'vitest'
import { useCameraStream } from './useCameraStream'

/**
 * T004 — useCameraStream hook contract per plan.md R2 / R5 /
 * data-model.md CameraSession state machine.
 */

interface FakeTrack {
  kind: string
  readyState: string
  stop: ReturnType<typeof vi.fn>
}

function makeFakeStream(): { stream: MediaStream; track: FakeTrack } {
  const track: FakeTrack = { kind: 'video', readyState: 'live', stop: vi.fn() }
  const stream = {
    getTracks: () => [track],
    getVideoTracks: () => [track],
  } as unknown as MediaStream
  return { stream, track }
}

function mockGetUserMedia(result: Promise<MediaStream>) {
  const gum = vi.fn(() => result)
  vi.stubGlobal('navigator', {
    mediaDevices: { getUserMedia: gum },
  })
  return gum
}

/**
 * jsdom's <video> exposes videoWidth=0 and readyState=0 — the hook's
 * waitForVideoReady guard then times out. Make the element look ready
 * synchronously so captureStill proceeds straight to squareCrop.
 */
function makeReadyVideoElement(): HTMLVideoElement {
  const video = document.createElement('video')
  Object.defineProperty(video, 'readyState', { value: 4, configurable: true })
  Object.defineProperty(video, 'videoWidth', { value: 1280, configurable: true })
  Object.defineProperty(video, 'videoHeight', { value: 720, configurable: true })
  return video
}

// squareCrop is mocked at module level so we don't need a real canvas.
vi.mock('../lib/squareCrop', () => ({
  squareCrop: vi.fn(async () => new Blob([new Uint8Array([1, 2, 3])], { type: 'image/jpeg' })),
}))

// React's passive-effect cleanup runs after afterEach's vi.unstubAllGlobals,
// so installing URL methods on a fresh stub per test leaves a window where
// the hook's teardown sees no-op globals. Install the methods once at
// beforeAll so they outlive the per-test teardown; reset the spy state
// between tests.
const createObjectURLMock = vi.fn<(blob: Blob) => string>(() => 'blob:fake-still')
const revokeObjectURLMock = vi.fn<(url: string) => void>()

beforeAll(() => {
  ;(URL as unknown as { createObjectURL: typeof createObjectURLMock }).createObjectURL =
    createObjectURLMock
  ;(URL as unknown as { revokeObjectURL: typeof revokeObjectURLMock }).revokeObjectURL =
    revokeObjectURLMock
})

afterAll(() => {
  delete (URL as unknown as { createObjectURL?: unknown }).createObjectURL
  delete (URL as unknown as { revokeObjectURL?: unknown }).revokeObjectURL
})

beforeEach(() => {
  createObjectURLMock.mockClear()
  createObjectURLMock.mockReturnValue('blob:fake-still')
  revokeObjectURLMock.mockClear()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe('useCameraStream — initial state', () => {
  test('starts in idle with no stream, no reason, no still URL', () => {
    mockGetUserMedia(Promise.resolve(makeFakeStream().stream))
    const { result } = renderHook(() => useCameraStream())
    expect(result.current.status).toBe('idle')
    expect(result.current.unavailableReason).toBeNull()
    expect(result.current.stream).toBeNull()
    expect(result.current.stillPreviewUrl).toBeNull()
  })
})

describe('useCameraStream — start() happy path', () => {
  test('transitions idle → requesting → live and stores the stream', async () => {
    const { stream } = makeFakeStream()
    const gum = mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    expect(result.current.status).toBe('live')
    expect(result.current.stream).toBe(stream)
    expect(gum).toHaveBeenCalledTimes(1)
  })

  test('start() calls getUserMedia with facingMode: "user" and audio: false', async () => {
    const { stream } = makeFakeStream()
    const gum = mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    expect(gum).toHaveBeenCalledWith({
      video: { facingMode: 'user' },
      audio: false,
    })
  })
})

describe('useCameraStream — unavailable classification', () => {
  test.each([
    ['NotAllowedError', 'permission_denied'],
    ['SecurityError', 'permission_denied'],
    ['NotFoundError', 'no_camera_hardware'],
    ['OverconstrainedError', 'no_camera_hardware'],
    ['NotReadableError', 'stream_unavailable'],
    ['AbortError', 'stream_unavailable'],
    ['TypeError', 'stream_unavailable'],
  ] as const)('%s → unavailable(%s)', async (name, expectedReason) => {
    mockGetUserMedia(Promise.reject(new DOMException('', name)))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    expect(result.current.status).toBe('unavailable')
    expect(result.current.unavailableReason).toBe(expectedReason)
    expect(result.current.stream).toBeNull()
  })

  test('plain TypeError (not a DOMException) maps to stream_unavailable', async () => {
    mockGetUserMedia(Promise.reject(new TypeError('malformed constraint')))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    expect(result.current.status).toBe('unavailable')
    expect(result.current.unavailableReason).toBe('stream_unavailable')
  })

  test('unknown error type defaults to stream_unavailable', async () => {
    mockGetUserMedia(Promise.reject(new Error('mysterious')))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    expect(result.current.status).toBe('unavailable')
    expect(result.current.unavailableReason).toBe('stream_unavailable')
  })

  test('when cameraCapability returns unsupported, transitions to unavailable(capability_missing) WITHOUT calling getUserMedia', async () => {
    const gum = vi.fn()
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: 'not-a-function' } })
    // The hook's start() should consult cameraCapability first.
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    expect(result.current.status).toBe('unavailable')
    expect(result.current.unavailableReason).toBe('capability_missing')
    expect(gum).not.toHaveBeenCalled()
  })

  test('retry after unavailable does NOT skip the capability check', async () => {
    // First attempt: unsupported capability.
    vi.stubGlobal('navigator', { mediaDevices: undefined })
    const { result } = renderHook(() => useCameraStream())
    await act(async () => {
      await result.current.start()
    })
    expect(result.current.status).toBe('unavailable')

    // Second attempt: capability now present, getUserMedia resolves.
    const { stream } = makeFakeStream()
    const gum = vi.fn(() => Promise.resolve(stream))
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: gum } })
    await act(async () => {
      await result.current.start()
    })
    expect(result.current.status).toBe('live')
    expect(gum).toHaveBeenCalledTimes(1)
  })
})

describe('useCameraStream — captureStill / retake / commit (stream reuse)', () => {
  test('captureStill from live transitions to still-preview with a URL, stream still open', async () => {
    const { stream } = makeFakeStream()
    mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    const video = makeReadyVideoElement()
    await act(async () => {
      await result.current.captureStill(video)
    })

    expect(result.current.status).toBe('still-preview')
    expect(result.current.stillPreviewUrl).toBe('blob:fake-still')
    expect(result.current.stream).toBe(stream) // stream still open
  })

  test('retake from still-preview returns to live and does NOT call getUserMedia again', async () => {
    const { stream } = makeFakeStream()
    const gum = mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })
    const video = makeReadyVideoElement()
    await act(async () => {
      await result.current.captureStill(video)
    })
    await act(() => {
      result.current.retake()
    })

    expect(result.current.status).toBe('live')
    expect(result.current.stillPreviewUrl).toBeNull()
    expect(gum).toHaveBeenCalledTimes(1) // only the initial start()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-still')
  })

  test('commit from still-preview stops every track, clears stream, returns the Blob', async () => {
    const { stream, track } = makeFakeStream()
    mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })
    const video = makeReadyVideoElement()
    await act(async () => {
      await result.current.captureStill(video)
    })

    let committed: Blob | null = null
    await act(async () => {
      committed = await result.current.commit()
    })

    expect(committed).toBeInstanceOf(Blob)
    expect(committed!.type).toBe('image/jpeg')
    expect(track.stop).toHaveBeenCalledTimes(1)
    expect(result.current.status).toBe('idle')
    expect(result.current.stream).toBeNull()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-still')
  })
})

describe('useCameraStream — cancel paths', () => {
  test('cancel from live stops the track and returns to idle', async () => {
    const { stream, track } = makeFakeStream()
    mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })
    await act(() => {
      result.current.cancel()
    })

    expect(result.current.status).toBe('idle')
    expect(track.stop).toHaveBeenCalledTimes(1)
    expect(result.current.stream).toBeNull()
  })

  test('cancel from still-preview revokes the still URL AND stops the track', async () => {
    const { stream, track } = makeFakeStream()
    mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })
    const video = makeReadyVideoElement()
    await act(async () => {
      await result.current.captureStill(video)
    })
    await act(() => {
      result.current.cancel()
    })

    expect(result.current.status).toBe('idle')
    expect(result.current.stillPreviewUrl).toBeNull()
    expect(track.stop).toHaveBeenCalledTimes(1)
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:fake-still')
  })
})

describe('useCameraStream — cleanup on unmount', () => {
  test('stopStream tolerates a track.stop() that throws', async () => {
    const throwingTrack: FakeTrack = {
      kind: 'video',
      readyState: 'live',
      stop: vi.fn(() => {
        throw new Error('already stopped')
      }),
    }
    const stream = {
      getTracks: () => [throwingTrack],
      getVideoTracks: () => [throwingTrack],
    } as unknown as MediaStream
    mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())
    await act(async () => {
      await result.current.start()
    })
    expect(() =>
      act(() => {
        result.current.cancel()
      }),
    ).not.toThrow()
    expect(throwingTrack.stop).toHaveBeenCalled()
  })

  test('unmount stops every track (C3)', async () => {
    const { stream, track } = makeFakeStream()
    mockGetUserMedia(Promise.resolve(stream))
    const { result, unmount } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })
    expect(result.current.status).toBe('live')

    unmount()
    expect(track.stop).toHaveBeenCalledTimes(1)
  })

  test('document.visibilitychange → hidden stops the track (C3)', async () => {
    const { stream, track } = makeFakeStream()
    mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    await act(() => {
      Object.defineProperty(document, 'visibilityState', {
        value: 'hidden',
        configurable: true,
      })
      document.dispatchEvent(new Event('visibilitychange'))
    })

    await waitFor(() => {
      expect(track.stop).toHaveBeenCalled()
    })
  })

  test('window pagehide stops the track (C3)', async () => {
    const { stream, track } = makeFakeStream()
    mockGetUserMedia(Promise.resolve(stream))
    const { result } = renderHook(() => useCameraStream())

    await act(async () => {
      await result.current.start()
    })

    await act(() => {
      window.dispatchEvent(new Event('pagehide'))
    })

    await waitFor(() => {
      expect(track.stop).toHaveBeenCalled()
    })
  })
})

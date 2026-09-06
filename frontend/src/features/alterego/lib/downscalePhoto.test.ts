import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { computeTargetSize, downscalePhoto } from './downscalePhoto'

/**
 * T042 — downscalePhoto contract.
 *
 *  - {@link computeTargetSize} pure-function tests cover the
 *    aspect-ratio + bounding-box maths.
 *  - The async {@link downscalePhoto} function is covered by stubbing
 *    {@code createImageBitmap} + {@code OffscreenCanvas} so the test
 *    runs in Node/jsdom without a real Canvas implementation. The full
 *    rendering path is exercised by the Playwright E2E in T044.
 */

describe('computeTargetSize', () => {
  test('returns the source dimensions when both fit', () => {
    expect(computeTargetSize(800, 600, 1024, 1024)).toEqual({ width: 800, height: 600 })
  })

  test('scales down preserving aspect ratio when wider than max', () => {
    const r = computeTargetSize(2048, 1024, 1024, 1024)
    expect(r).toEqual({ width: 1024, height: 512 })
  })

  test('scales down preserving aspect ratio when taller than max', () => {
    const r = computeTargetSize(1024, 2048, 1024, 1024)
    expect(r).toEqual({ width: 512, height: 1024 })
  })

  test('uses the tighter of the two ratios when both axes overflow', () => {
    // src 4000×2000 → wider needs ÷4 (→ 1000), taller needs ÷2 (→ 1000); takes ÷4
    const r = computeTargetSize(4000, 2000, 1000, 1000)
    expect(r).toEqual({ width: 1000, height: 500 })
  })

  test('rounds and clamps to at least 1 pixel', () => {
    const r = computeTargetSize(1, 1, 1024, 1024)
    expect(r.width).toBeGreaterThanOrEqual(1)
    expect(r.height).toBeGreaterThanOrEqual(1)
  })
})

describe('downscalePhoto', () => {
  let drawSpy: ReturnType<typeof vi.fn>
  let convertSpy: ReturnType<typeof vi.fn>
  let constructedDimensions: { width: number; height: number }[]

  beforeEach(() => {
    constructedDimensions = []
    drawSpy = vi.fn()
    convertSpy = vi.fn(
      (opts: { type: string; quality?: number }) =>
        Promise.resolve(new Blob(['fake-' + opts.type], { type: opts.type })) as Promise<Blob>,
    )

    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(
        async (_blob: Blob) =>
          ({ width: 2000, height: 1500, close: vi.fn() }) as unknown as ImageBitmap,
      ),
    )

    class FakeOffscreenCanvas {
      constructor(
        public width: number,
        public height: number,
      ) {
        constructedDimensions.push({ width, height })
      }
      getContext() {
        return { drawImage: drawSpy }
      }
      convertToBlob(opts: { type: string; quality?: number }) {
        return convertSpy(opts)
      }
    }
    vi.stubGlobal('OffscreenCanvas', FakeOffscreenCanvas as unknown as typeof OffscreenCanvas)
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  test('downscales when source exceeds max bounds', async () => {
    const blob = new Blob([new Uint8Array(10)], { type: 'image/jpeg' })
    const out = await downscalePhoto(blob)
    // 2000×1500 fitted into 1024×1024 preserves aspect → 1024×768.
    expect(constructedDimensions).toEqual([{ width: 1024, height: 768 }])
    expect(drawSpy).toHaveBeenCalledTimes(1)
    expect(out.type).toBe('image/jpeg')
  })

  test('passes through dimensions when source already fits', async () => {
    vi.stubGlobal(
      'createImageBitmap',
      vi.fn(
        async (_blob: Blob) =>
          ({ width: 800, height: 600, close: vi.fn() }) as unknown as ImageBitmap,
      ),
    )
    const blob = new Blob([new Uint8Array(10)], { type: 'image/jpeg' })
    await downscalePhoto(blob)
    expect(constructedDimensions).toEqual([{ width: 800, height: 600 }])
  })

  test('honours custom outputType + quality', async () => {
    const blob = new Blob([new Uint8Array(10)], { type: 'image/png' })
    const out = await downscalePhoto(blob, { outputType: 'image/png', quality: 0.5 })
    expect(out.type).toBe('image/png')
    expect(convertSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'image/png',
        quality: 0.5,
      }),
    )
  })

  test('default quality is 0.85 and default output is image/jpeg', async () => {
    const blob = new Blob([new Uint8Array(10)], { type: 'image/jpeg' })
    await downscalePhoto(blob)
    expect(convertSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        type: 'image/jpeg',
        quality: 0.85,
      }),
    )
  })
})

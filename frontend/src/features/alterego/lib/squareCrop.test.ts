import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { squareCrop } from './squareCrop'

/**
 * T002 — squareCrop.ts pure-function contract per plan.md R4 / FR-303a.
 *
 * jsdom does not implement the HTML canvas 2D API, nor does its Blob
 * copy byte-array parts reliably. We stub `getContext('2d')` to produce
 * a spy-able context, and `toBlob` to record the destination canvas's
 * chosen width/height on a side-channel we can assert against.
 */

interface DrawImageCall {
  args: unknown[]
}

interface ToBlobCall {
  width: number
  height: number
  type: string
}

// Module-level side channel populated by the stubbed toBlob.
let toBlobCalls: ToBlobCall[] = []
let drawImageCalls: DrawImageCall[] = []
let translateCalls: unknown[][] = []
let scaleCalls: unknown[][] = []

function installCanvas2dStub(): { restore: () => void } {
  const originalGetContext = HTMLCanvasElement.prototype.getContext
  const originalToBlob = HTMLCanvasElement.prototype.toBlob

  HTMLCanvasElement.prototype.getContext = function (this: HTMLCanvasElement, type: string) {
    if (type !== '2d') {
      return originalGetContext.call(this, type)
    }
    return {
      translate: (...a: unknown[]) => {
        translateCalls.push(a)
      },
      scale: (...a: unknown[]) => {
        scaleCalls.push(a)
      },
      drawImage: (...args: unknown[]) => {
        drawImageCalls.push({ args })
      },
    } as unknown as CanvasRenderingContext2D
  } as typeof HTMLCanvasElement.prototype.getContext

  HTMLCanvasElement.prototype.toBlob = function (
    this: HTMLCanvasElement,
    cb: BlobCallback,
    type?: string,
  ) {
    toBlobCalls.push({
      width: this.width,
      height: this.height,
      type: type ?? 'image/jpeg',
    })
    cb(new Blob([], { type: type ?? 'image/jpeg' }))
  }

  return {
    restore: () => {
      HTMLCanvasElement.prototype.getContext = originalGetContext
      HTMLCanvasElement.prototype.toBlob = originalToBlob
    },
  }
}

function makeSourceCanvas(width: number, height: number): HTMLCanvasElement {
  const c = document.createElement('canvas')
  c.width = width
  c.height = height
  return c
}

let teardown: () => void = () => {}

beforeEach(() => {
  toBlobCalls = []
  drawImageCalls = []
  translateCalls = []
  scaleCalls = []
  vi.stubGlobal('OffscreenCanvas', undefined)
  const stub = installCanvas2dStub()
  teardown = stub.restore
})

afterEach(() => {
  teardown()
  vi.unstubAllGlobals()
})

describe('squareCrop', () => {
  test('16:9 source → centred 1:1 output of min(side, targetSize)', async () => {
    const src = makeSourceCanvas(1920, 1080)
    await squareCrop(src, { mirror: false, targetSize: 1024 })
    // min(1080, 1024) = 1024 → 1024×1024
    expect(toBlobCalls.length).toBe(1)
    expect(toBlobCalls[0]!.width).toBe(1024)
    expect(toBlobCalls[0]!.height).toBe(1024)
  })

  test('4:3 source → centred 1:1 output of min(side, targetSize)', async () => {
    const src = makeSourceCanvas(800, 600)
    await squareCrop(src, { mirror: false, targetSize: 1024 })
    // min(600, 1024) = 600 → 600×600 (never up-scales)
    expect(toBlobCalls[0]!.width).toBe(600)
    expect(toBlobCalls[0]!.height).toBe(600)
  })

  test('already-square source is preserved', async () => {
    const src = makeSourceCanvas(512, 512)
    await squareCrop(src, { mirror: false, targetSize: 1024 })
    expect(toBlobCalls[0]!.width).toBe(512)
    expect(toBlobCalls[0]!.height).toBe(512)
  })

  test('smaller-than-target source is NOT up-scaled', async () => {
    const src = makeSourceCanvas(480, 480)
    await squareCrop(src, { mirror: false, targetSize: 1024 })
    expect(toBlobCalls[0]!.width).toBe(480)
    expect(toBlobCalls[0]!.height).toBe(480)
  })

  test('output MIME is image/jpeg', async () => {
    const src = makeSourceCanvas(800, 800)
    const blob = await squareCrop(src, { mirror: false, targetSize: 1024 })
    expect(blob.type).toBe('image/jpeg')
    expect(toBlobCalls[0]!.type).toBe('image/jpeg')
  })

  test('centre-crop picks sx = (w - side) / 2 and sy = (h - side) / 2', async () => {
    const src = makeSourceCanvas(1200, 800)
    await squareCrop(src, { mirror: false, targetSize: 1024 })
    // Centre crop of 1200×800: side = 800, sx = 200, sy = 0, targetPx = 800
    const call = drawImageCalls.find((c) => c.args.length === 9)
    expect(call).toBeDefined()
    const args = call!.args
    expect(args[1]).toBe(200) // sx
    expect(args[2]).toBe(0) // sy
    expect(args[3]).toBe(800) // sw
    expect(args[4]).toBe(800) // sh
    expect(args[5]).toBe(0) // dx
    expect(args[6]).toBe(0) // dy
    expect(args[7]).toBe(800) // dw
    expect(args[8]).toBe(800) // dh
  })

  test('accepts a duck-typed video-like source using videoWidth/videoHeight', async () => {
    const src = makeSourceCanvas(0, 0)
    Object.defineProperty(src, 'videoWidth', { value: 2000, configurable: true })
    Object.defineProperty(src, 'videoHeight', { value: 1000, configurable: true })
    await squareCrop(src as unknown as HTMLVideoElement, {
      mirror: false,
      targetSize: 1024,
    })
    // min(1000, 1024) = 1000 — picked up via videoHeight, not width.
    expect(toBlobCalls[0]!.width).toBe(1000)
    expect(toBlobCalls[0]!.height).toBe(1000)
  })

  test('mirror: false does NOT apply translate/scale transforms', async () => {
    const src = makeSourceCanvas(800, 800)
    await squareCrop(src, { mirror: false, targetSize: 1024 })
    expect(translateCalls).toEqual([])
    expect(scaleCalls).toEqual([])
  })

  test('mirror: true DOES apply translate + scale(-1, 1)', async () => {
    const src = makeSourceCanvas(800, 800)
    await squareCrop(src, { mirror: true, targetSize: 1024 })
    expect(translateCalls.length).toBeGreaterThan(0)
    expect(scaleCalls[0]).toEqual([-1, 1])
  })
})

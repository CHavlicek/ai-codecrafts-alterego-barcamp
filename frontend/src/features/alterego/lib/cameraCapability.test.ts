import { afterEach, describe, expect, test, vi } from 'vitest'
import { cameraCapability } from './cameraCapability'

/**
 * T003 — cameraCapability.ts static capability detection per plan.md R5.
 *
 * The detector MUST NOT invoke getUserMedia. It only inspects the shape
 * of navigator.mediaDevices.
 */

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('cameraCapability', () => {
  test('returns { supported: true } when navigator.mediaDevices.getUserMedia is a function', () => {
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: () => Promise.resolve() },
    })
    expect(cameraCapability()).toEqual({ supported: true })
  })

  test('returns { supported: false, reason: "capability_missing" } when mediaDevices is undefined', () => {
    vi.stubGlobal('navigator', {})
    expect(cameraCapability()).toEqual({
      supported: false,
      reason: 'capability_missing',
    })
  })

  test('returns { supported: false, reason: "capability_missing" } when getUserMedia is not a function', () => {
    vi.stubGlobal('navigator', {
      mediaDevices: { getUserMedia: 'not-a-function' },
    })
    expect(cameraCapability()).toEqual({
      supported: false,
      reason: 'capability_missing',
    })
  })

  test('returns { supported: false, reason: "capability_missing" } when navigator itself is undefined', () => {
    // Can't `vi.stubGlobal('navigator', undefined)` because jsdom's
    // global navigator is non-configurable; simulate by temporarily
    // deleting the property with a Proxy. The simplest reliable shim:
    // set navigator to an object that fails the in-check for mediaDevices
    // AND respect the `typeof navigator === "undefined"` guard by
    // replacing globalThis.navigator with undefined via Object.defineProperty.
    const original = globalThis.navigator
    Object.defineProperty(globalThis, 'navigator', {
      value: undefined,
      configurable: true,
      writable: true,
    })
    try {
      expect(cameraCapability()).toEqual({
        supported: false,
        reason: 'capability_missing',
      })
    } finally {
      Object.defineProperty(globalThis, 'navigator', {
        value: original,
        configurable: true,
        writable: true,
      })
    }
  })

  test('does NOT invoke getUserMedia during capability detection', () => {
    const getUserMediaSpy = vi.fn()
    vi.stubGlobal('navigator', { mediaDevices: { getUserMedia: getUserMediaSpy } })
    cameraCapability()
    expect(getUserMediaSpy).not.toHaveBeenCalled()
  })
})

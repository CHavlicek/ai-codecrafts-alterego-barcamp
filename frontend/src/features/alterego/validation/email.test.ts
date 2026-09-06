import { describe, expect, test } from 'vitest'
import { validateEmail } from './email'

/**
 * 023 (issue #57) — Email validator contract.
 *
 * Blank / whitespace-only → ok with `trimmed === ''` (the field is
 * optional; only non-blank values are format-validated). Non-blank
 * is checked against a pragmatic single-line grammar
 * (`local@domain.tld`-shaped) and capped at 254 chars on the trimmed
 * value (RFC 5321 practical envelope cap; research.md R7).
 */
describe('validateEmail (023)', () => {
  test('empty string → ok with trimmed === ""', () => {
    const v = validateEmail('')
    expect(v.ok).toBe(true)
    if (v.ok) expect(v.trimmed).toBe('')
  })

  test('whitespace-only → ok with trimmed === "" (blank == optional)', () => {
    const v = validateEmail('   \t  ')
    expect(v.ok).toBe(true)
    if (v.ok) expect(v.trimmed).toBe('')
  })

  test('canonical "someone@example.com" → ok', () => {
    const v = validateEmail('someone@example.com')
    expect(v.ok).toBe(true)
    if (v.ok) expect(v.trimmed).toBe('someone@example.com')
  })

  test('surrounding whitespace is trimmed before validation and reported in result', () => {
    const v = validateEmail('  someone@example.com  ')
    expect(v.ok).toBe(true)
    if (v.ok) expect(v.trimmed).toBe('someone@example.com')
  })

  test('short but valid "a@b.c" → ok', () => {
    const v = validateEmail('a@b.c')
    expect(v.ok).toBe(true)
  })

  test.each([
    'not-an-email',
    'a@', // no domain
    '@b.c', // no localpart
    'a@b', // no dot in domain
    'a@.c', // empty subdomain
    'a@b.', // trailing dot — domain not terminated
    'a b@c.d', // whitespace in localpart
    'a@b c.d', // whitespace in domain
    'a@@b.c', // double @ (matches "@" in disallowed range)
  ])('malformed "%s" → { ok: false, code: "invalid_format" }', (input) => {
    const v = validateEmail(input)
    expect(v.ok).toBe(false)
    if (!v.ok) expect(v.code).toBe('invalid_format')
  })

  test('254-char address (RFC 5321 envelope cap) → ok', () => {
    // local of 64 chars (RFC 5321 local max) + "@" + 189-char domain
    // ending in ".io" → 64 + 1 + 189 = 254 chars total
    const local = 'a'.repeat(64)
    const domainHead = 'b'.repeat(186) // 186 + ".io" = 189 chars
    const addr = `${local}@${domainHead}.io`
    expect(addr.length).toBe(254)
    expect(validateEmail(addr).ok).toBe(true)
  })

  test('255-char address → { ok: false, code: "too_long" }', () => {
    const local = 'a'.repeat(65) // one over
    const domainHead = 'b'.repeat(186)
    const addr = `${local}@${domainHead}.io`
    expect(addr.length).toBe(255)
    const v = validateEmail(addr)
    expect(v.ok).toBe(false)
    if (!v.ok) expect(v.code).toBe('too_long')
  })

  test('NFC normalisation: a composed and decomposed "é" both validate identically', () => {
    const composed = 'café@example.com' // U+00E9 (é precomposed)
    const decomposed = 'café@example.com' // e + COMBINING ACUTE ACCENT
    expect(validateEmail(composed).ok).toBe(true)
    expect(validateEmail(decomposed).ok).toBe(true)
  })

  test('length cap is measured on the TRIMMED value, not the raw input', () => {
    // 254 valid chars + lots of trailing whitespace should still be ok
    const local = 'a'.repeat(64)
    const domainHead = 'b'.repeat(186)
    const addr = `${local}@${domainHead}.io   \t   `
    expect(validateEmail(addr).ok).toBe(true)
  })
})

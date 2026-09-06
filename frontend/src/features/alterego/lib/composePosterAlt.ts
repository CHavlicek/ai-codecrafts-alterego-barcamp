/**
 * Compose the {@code <img alt>} text used by both {@code PosterView}
 * and {@code PrintArtefact} for the generated poster image
 * (017 FR-1714 — refined 2026-05-08 to include the quote since it is
 * now baked into the bottom region as a second line).
 *
 * <p>Format: {@code "Alter ego poster for {firstName}, {role}. {quote}"}
 * — joined with comma + period so screen readers (Voice Over / NVDA /
 * Narrator) introduce natural pauses between the name, the role, and
 * the quote. Trailing punctuation on any input is normalised so the
 * output never contains a double period.
 *
 * <p>Pure / deterministic — exhaustively unit-testable.
 */
export function composePosterAlt(firstName: string, role: string, quote: string): string {
  const name = firstName.trim()
  const r = trimTrailingTerminator(role.trim())
  const q = trimTrailingTerminator(quote.trim())
  const head = composeHead(name, r)
  if (!q) return `${head}.`
  return `${head}. ${q}.`
}

function composeHead(name: string, role: string): string {
  if (!name && !role) return 'Alter ego poster'
  if (!role) return `Alter ego poster for ${name}`
  if (!name) return `Alter ego poster — ${role}`
  return `Alter ego poster for ${name}, ${role}`
}

function trimTrailingTerminator(s: string): string {
  return s.replace(/[.!?]+$/u, '')
}

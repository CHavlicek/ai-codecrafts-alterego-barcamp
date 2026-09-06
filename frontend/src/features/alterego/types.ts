/**
 * AI Alter Ego API types — hand-authored mirror of
 * `specs/002-sleek-tabbed-ui/contracts/alter-egos.openapi.yaml`.
 *
 * 002 delta: {@code Colour} removed; {@code Archetype} and {@code Universe}
 * value sets replaced with the engineering-role / mockup-universe sets
 * from the 002 mockup; {@code Vibe} introduced as an optional enum on
 * {@link Selections}.
 *
 * 020 delta: {@code Pose} and {@code Vibe} types removed from the frontend
 * (closes issue #51 / specs/020-hide-vibe-pose). The categories remain on
 * the backend's prompt-building logic; the server now rolls one value of
 * each per request. The {@link Selections} interface drops the `pose` and
 * `vibe` fields accordingly.
 *
 * Run `npm run generate:api-types` to produce a `types.generated.ts`
 * counterpart from the OpenAPI source for CI-time drift detection.
 * Production code imports from this file (cleaner names, hand-tuned
 * shapes); the generated file is for verification only.
 */

export type Archetype =
  | 'cloud-architect'
  | 'backend-dev'
  | 'frontend-dev'
  | 'ai-engineer'
  | 'platform-eng'
  | 'data-engineer'
  // 022 (issue #50): three non-engineering prefab options. Wire values
  // follow the existing kebab-case public-contract convention; UI labels
  // live in `options.ts → ARCHETYPE_OPTIONS`.
  | 'hr'
  | 'administration'
  | 'customer-relations'

export type Universe =
  | 'marvel'
  | 'star-wars'
  | 'cyberpunk'
  | 'the-office'
  | 'indiana-jones'
  | 'lord-of-the-rings'

/**
 * 006 addition: rendering style for the generated poster. Required selection
 * — see specs/006-art-style-category/spec.md FR-304. Wire values are stable
 * public-contract strings from first merge (FR-310); renaming any value is a
 * breaking change.
 *
 * 019 delta: `pixel-art`, `low-poly-3d`, `line-art` retired
 * (specs/019-remove-art-styles, closes #49). Six surviving members.
 */
export type ArtStyle =
  | 'oil-painting'
  | 'watercolor'
  | 'pop-art'
  | 'renaissance-portrait'
  | 'japanese-woodblock'
  | 'cel-shaded'

/**
 * 011 addition: composition mode — whether the reference photo contains a
 * single subject (default) or a group of subjects. The mode is a required
 * session field (never null) and branches the backend's Gemini prompt:
 * {@code 'single'} → singular "portrait of the person" wording (today's
 * baseline); {@code 'group'} → plural "group portrait of the people"
 * wording with explicit "EVERY person visible" + "Do NOT invent" clauses.
 *
 * <p>Wire values are stable public-contract strings (FR-1005 / FR-1008).
 */
export type PhotoMode = 'single' | 'group'

/**
 * 003 delta: `success` → `real`. A `real` outcome means a real-provider
 * (Gemini or fal.ai per 016) path produced the image; `fallback` means the 001
 * stub image was returned after a failure at any layer. Unknown values SHOULD
 * be treated as `fallback` for graceful degradation with forward-compatible
 * servers.
 *
 * 016 invariant (FR-1612): `outcome === 'real'` iff
 * `provider ∈ {'gemini', 'falai'}`; `outcome === 'fallback'` iff
 * `provider === 'stub'`.
 */
export type Outcome = 'real' | 'fallback'

/**
 * 016 addition: discriminator naming the path that produced the central image
 * of this specific response. Mandatory on every response (FR-1612).
 *
 * For operator / automated-test visibility only — the frontend MUST NOT
 * surface this value to the end user (FR-1622). The user-facing fallback
 * notice remains the generic single-variant message from 003 FR-214.
 *
 * The response body deliberately does NOT carry an `attemptedProvider`
 * field telling you which real provider was attempted-and-failed; that
 * information lives only in the backend's per-run structured log line
 * (FR-1613 / 016 clarification Q2).
 */
export type Provider = 'gemini' | 'falai' | 'stub'

/**
 * Classification of WHY a fallback was served. Present (non-null) iff
 * `outcome === 'fallback'`. Fixed closed enumeration per 003 FR-218.
 *
 * For operator / automated-test visibility only — the frontend MUST NOT
 * surface this value to the end user (FR-214). The user-facing copy is the
 * single generic notice in {@link ./constants}.
 */
export type FallbackReason =
  | 'not_configured'
  | 'network_error'
  | 'rate_limited'
  | 'timeout'
  | 'malformed_response'
  | 'safety_refused'

export interface Selections {
  /**
   * 022 (issue #50): nullable when the user supplied a custom role
   * (`customRole` non-blank takes precedence). The OR-invariant is
   * enforced server-side by the class-level `@RoleOfRecordPresent`
   * Bean Validation constraint.
   */
  archetype: Archetype | null
  universe: Universe
  /** 006: required rendering style for the generated poster. */
  artStyle: ArtStyle
  /**
   * 011: composition mode. Required on outbound requests from clients
   * shipping this feature. Backend treats the field as optional at the
   * Bean Validation layer (defaults to {@code SINGLE}) so old clients
   * continue to work during a partial rollout.
   */
  photoMode: PhotoMode
  firstName: string
  /**
   * 022 (issue #50): optional free-form role string (≤ 100 chars after
   * trim). When present and trimmed-non-empty, takes precedence over
   * `archetype` as the role-of-record for the image prompt, the
   * character bio prompt, and the poster text overlay. The frontend
   * MUST omit this field entirely when blank (do not send `""`).
   */
  customRole?: string
}

export interface GeneratedCharacter {
  heroTitleLine1: string
  heroTitleLine2: string
  tagline: string
  /** Exactly three superpower descriptions. */
  superpowers: [string, string, string]
  quote: string
}

export interface Poster {
  /** `data:image/png;base64,…` or `data:image/jpeg;base64,…` */
  dataUrl: string
  mediaType: 'image/png' | 'image/jpeg'
  widthPx: number
  heightPx: number
}

export interface ResponseMeta {
  outcome: Outcome
  correlationId: string
  /**
   * Present iff `outcome === 'fallback'`. Omitted from JSON on the real path
   * (backend uses `@JsonInclude(NON_NULL)`). Not surfaced to the end user.
   */
  reason?: FallbackReason
  /**
   * 016 (FR-1612): mandatory on every response. Discriminates which path
   * produced the central image. Operator-visible only — not rendered in UI.
   */
  provider: Provider
}

export interface AlterEgoResponse {
  character: GeneratedCharacter
  poster: Poster
  meta: ResponseMeta
}

/** RFC 7807 problem details (Spring Boot's default error shape). */
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail?: string
  instance?: string
}

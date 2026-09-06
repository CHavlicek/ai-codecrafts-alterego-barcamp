/**
 * Shared constants for the alter-ego feature.
 *
 * 003 FR-214: the fallback notice rendered in {@link components/PosterView}
 * MUST be a single generic message, without naming the provider and without
 * disclosing the fallback reason to the end user. Reason codes live only in
 * backend logs and in the response metadata for operator / devtools
 * inspection (FR-218 / FR-219). Centralising the copy here keeps all three
 * sites (hook, component, tests) in lockstep.
 */
export const FALLBACK_NOTICE_COPY =
  "Showing a preview image — live AI generation isn't available right now."

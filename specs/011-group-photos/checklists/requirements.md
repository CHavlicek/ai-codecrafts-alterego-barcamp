# Checklist: Requirements Completeness (011 Group Photos)

> Gate: every item must be `[x]` before `/speckit.implement` starts.

## Functional scope

- [x] All 14 functional requirements (FR-1001..FR-1014) are numbered and distinct.
- [x] Each FR has a single, testable subject (no compound "MUST X and Y and Z" without clear seams).
- [x] The three user stories cover the three viable flow variants (default single, opt-in group, a11y / keyboard).
- [x] Out-of-scope items are enumerated (server-side face detection, per-person attribute customisation, persistence, etc.).

## Domain model

- [x] New enum `PhotoMode` has wire values pinned (`"single"` / `"group"`).
- [x] New session field `photoMode` has a defined default (`'single'`) and is never `null`.
- [x] New action `PhotoModeSelected` has a typed payload and a defined reducer branch.
- [x] `StartOverRequested` reset behaviour is specified (reverts to `'single'`).
- [x] Invariants I-1..I-6 are enumerated in data-model.md.

## Interaction model

- [x] Keyboard contract pinned: Arrow keys to move, Space / Enter to activate, wrap-around.
- [x] ARIA contract pinned: `role="radiogroup"` with two `role="radio"` children; one always checked; deselection disallowed.
- [x] Focus / tab-order expectations stated relative to Step 1 (Pose).
- [x] Screen-reader announcement expected for activation.

## Wire contract

- [x] Outbound payload field name and wire values are specified.
- [x] Backend field is specified as nullable with a domain default.
- [x] Backwards-compatibility direction (old client → new server) is explicitly covered (FR-1009).
- [x] Forward-compatibility direction (new client → old server) is acknowledged — the old server would reject `photoMode` unless its Jackson config ignores unknown properties. Flagged in research.md R3.

## Edge cases

- [x] Mode picked but photo contains opposite count (Single/Group mismatch) — defined as "we trust the user".
- [x] Surprise Me interaction — mode NOT randomised (FR-1012).
- [x] Start-over interaction — resets to default (FR-1011).
- [x] Mid-generation mode change — blocked by 007 tab gating (FR-1013 note).
- [x] Fallback path — mode-agnostic (Edge Cases / Fallback).
- [x] Malformed wire value — 400 (Edge Cases / Malformed).

## Integration with existing features

- [x] D-1001..D-1005 explicitly enumerate the five feature dependencies (002 / 003 / 004 / 007 / 009).
- [x] No regression against the singular-portrait baseline (SC-1001 + prompt-builder regression lock).
- [x] Generate readiness selector unchanged (FR-1007).
- [x] Fallback / retry / resilient-fetch layers unchanged (NFRs + "No new HTTP" in FR-1014).

## Terminology

- [x] "photoMode" / "PhotoMode" / "Single mode" / "Group mode" / "composition choice" are defined in data-model.md Glossary.
- [x] Wire values are lowercase kebab-case and pinned; the enum constants `SINGLE` / `GROUP` are the Java counterpart.

## Completion signals

- [x] SC-1001..SC-1005 each cite a concrete measurement (diff, test, axe scan).
- [x] NFR-1001 pins "no layout shift on first paint".
- [x] NFR-1002 pins prompt size headroom (existing 768-byte StringBuilder initial capacity).
- [x] NFR-1003 reaffirms the constitutional ≥ 90% line coverage gate.

## Clarifications addressed

- [x] Single photo / multi-face reading confirmed (A-1001).
- [x] Single name field interpretation (group name) clarified (A-1001, Group-name label in prompt).
- [x] `'switch'` wording vs. ARIA role pinned (A-1004).
- [x] Default mode pinned to `'single'` (A-1002 implicit + FR-1003).
- [x] Surprise Me does not randomise mode (FR-1012).

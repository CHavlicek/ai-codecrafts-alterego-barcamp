# Public HTTP Contract — 026

**Date**: 2026-05-18
**Status**: Final

## Verdict

**No change.** This feature ships zero contract delta. The OpenAPI YAML pinned by the backend `contract/` test tier is byte-identical before and after the change, and the contract tier MUST remain green throughout.

## What guests / clients send and receive

The two public endpoints — `POST /api/v1/alter-egos` and `POST /api/v1/alter-egos/email` — accept and return the same shapes as today. The only Role-adjacent field on the wire is `archetype`:

| Direction | Field | Type | Allowed values |
|---|---|---|---|
| Request `POST /api/v1/alter-egos` → `AlterEgoRequest.archetype` | `string` (nullable) | one of `cloud-architect`, `backend-dev`, `frontend-dev`, `ai-engineer`, `platform-eng`, `data-engineer`, `hr`, `administration`, `customer-relations` — or `null` when `customRole` is non-blank (validation rule from 022) |

These nine kebab-case strings are **the wire identity of each Role**. They are unchanged by this feature (SC-2604) and must continue to be unchanged.

The **display label** ("People Operations", "Backend Developer", …) is a frontend / poster-side rendering concern. It is never sent on the wire from client to server, and the server never sends the rendered label back to the client over the public HTTP API for the Role field. (The poster image bytes returned by the generation endpoint do contain the rendered label baked into the pixels — but that is image content, not a JSON contract.)

## Contract-tier test

The existing `backend/src/test/java/com/aiavatar/alterego/contract/*Test.java` files (`@WebMvcTest`) pin the wire enum via JSON round-trip assertions. Those tests:

- **MUST continue to pass unchanged** before, during, and after this feature.
- Are the canary that catches any accidental wire mutation introduced by this feature (e.g. if a sloppy edit changed `Archetype.HR("hr", ...)` → `Archetype.HR("people-ops", ...)`).

Per Constitution Principle IX: "Contract tests MUST pin the public HTTP contract (OpenAPI YAML) and MUST be the first tier to fail when a request or response shape changes incompatibly." This feature deliberately changes nothing in that contract.

## OpenAPI delta

```diff
(none — file untouched)
```

## Backwards compatibility

- **Old clients** (older bundles still resident in a guest's browser tab when the new backend deploys) — fully compatible. They send `archetype: "backend-dev"`, the new backend accepts it identically, and the poster comes back with "Backend Developer" baked in. The Setup grid in the old tab continues to display "Backend Dev" until the page reloads.
- **Old servers** (very brief window during a backend rolling deploy where the new frontend talks to the old backend) — fully compatible. The frontend sends `archetype: "backend-dev"`, the old backend accepts it identically, and the poster comes back with the *old* "Backend Dev" baked in. The Setup grid in the new bundle still shows "Backend Developer" — meaning a guest who happens to generate during that handful of seconds may see a poster whose label lags the grid. Acceptable: window is short, the inconsistency is cosmetic, and a re-generate after the deploy completes restores consistency.

No API version bump required. No client SDK change required (none exists).

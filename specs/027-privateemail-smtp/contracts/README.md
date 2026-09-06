# Public Contracts: 027-privateemail-smtp

**Result**: **No public HTTP contract change.**

This feature is operational. The two existing public endpoints from 023 / 024 — `POST /api/v1/alter-egos` and `POST /api/v1/alter-egos/email` — are untouched. Request bodies, response bodies, status codes, header echoes (`X-Request-Id`), and the two RFC 7807 `type` URIs (`/email/not-configured`, `/email/send-failed`) are byte-identical before and after.

## Observable change in outcome distribution (not contract)

The probability of each outcome shifts because the `EmailConfigured` gate tightens (FR-2706):

| Deployment shape | Before 027 (host-only gate) | After 027 (host + username + password + From gate) |
|---|---|---|
| Fresh deploy, no env vars set | `503 not-configured` | `503 not-configured` (unchanged — host now defaults non-blank but the other three are still blank) |
| Host present, no creds | `502 send-failed` (after retries) | `503 not-configured` (**user-visible improvement** — the helpful "not configured" alert fires instead of "send failed, retry") |
| Host + creds + From all present, PrivateEmail accepts | `200 sent` | `200 sent` |
| Host + creds + From all present, PrivateEmail rejects creds | `502 send-failed` (after retries) | `502 send-failed` (after retries) |

Same status codes, same `type` URIs, same response bodies. Only the deployment configuration determines which branch the user lands on, and the new gate makes the "not configured" branch easier for operators to hit by accident — exactly the intended behaviour (Story 2).

## Contract files

No contract files are added or modified by this feature. The 023 OpenAPI-shaped contract (implicit in the `POST /api/v1/alter-egos/email` controller + its `ProblemDetail` advice) continues to be exercised by the existing `AlterEgoEmailControllerIT` and `AlterEgoEmailControllerNotConfiguredIT` integration tests.

## Frontend implications

None. The frontend's existing `type`-URI classifier (used in `email-send` client code) already handles both `/email/not-configured` and `/email/send-failed`; the only change is that more deployments will hit the not-configured branch.

The Playwright E2E flow (`frontend/tests/e2e/email-send.spec.ts`) continues to pass without modification — it exercises the not-configured and configured paths via Spring profile selection, both of which still work post-027.

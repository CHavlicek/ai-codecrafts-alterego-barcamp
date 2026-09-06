# Contracts — Poster Text Overlay (017)

## Public contract delta: NONE

This feature deliberately does not modify the public API.

- The OpenAPI spec at
  `backend/src/main/resources/openapi/alter-egos.yaml` (and the
  hand-mirrored TypeScript types at
  `frontend/src/features/alterego/types.ts`) are **untouched** by 017.
- No new field is added to the request body
  (`Selections` / `AlterEgoRequest`) or the response body
  (`AlterEgoResponse` / `GeneratedCharacter` / `Poster`).
- No new HTTP header, query parameter, or status code is introduced.
- No new HTTP endpoint is introduced.

## What does change (informationally — not contract-bearing)

The pixel content of `response.poster.dataUrl` now contains three
rendered text lines (the user's first name, `heroTitleLine1`, and
`tagline`) inside the dark long-bottom region of the frame. The
response field types, `mediaType`, `widthPx`, and `heightPx` are
identical to 015's contract.

## Why an explicit "no-delta" file

The 015 + 016 PRs both touched contracts; readers landing on this
folder by reflex would expect a contract file here. Leaving an empty
folder would invite a future reader to wonder whether something was
forgotten. This README is the answer: the absence is intentional, and
this is the audit trail for that decision.

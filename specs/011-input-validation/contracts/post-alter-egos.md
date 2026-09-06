# Contract Delta — `POST /api/v1/alter-egos` (rejection paths only)

**Feature**: 011-input-validation
**Date**: 2026-04-27

This feature does not change the success contract for `POST /api/v1/alter-egos`. The endpoint, method, multipart shape, and 200-response payload remain exactly as defined in feature 003. Only the **rejection** behaviour for the `firstName` form-part is tightened, and only within the existing RFC 7807 `ProblemDetail` framing.

## Request

Unchanged. Multipart `multipart/form-data`:

- `selections` (JSON form-part, `application/json`) — body conforms to `AlterEgoRequest`. The `firstName` field is the focus of this contract delta.
- `photo` (file form-part, `image/*`) — unchanged.

## `firstName` constraints

| Rule | Status | Behaviour |
|---|---|---|
| `@NotBlank` | UNCHANGED | Empty / blank → 400 |
| `@Size(max=…)` | **CHANGED** | Was `min=1, max=40`. Now `min=1, max=50`. Length is measured on the value after `trim()` then NFC normalisation, in Unicode code points (see data-model). |
| `@ValidFirstName` | **NEW** | Rejects values containing ASCII controls, Unicode invisibles / bidi controls, structural injection markers (`<`, `>`, `` ` ``, `{`, `}`, `[`, `]`, `\`, `|`, `${`, `\`\`\``), or instruction-shaped phrases (case-insensitive, whole-word match — see data-model Family E). |

## Rejection response

HTTP **400 Bad Request** with `Content-Type: application/problem+json`, body conforming to RFC 7807 with the existing `errors` array shape produced by `ProblemDetailAdvice`:

```json
{
  "type": "about:blank",
  "title": "Validation failed",
  "status": 400,
  "detail": "Request body validation failed",
  "instance": "/api/v1/alter-egos",
  "errors": [
    { "field": "firstName", "code": "firstName.tooLong" }
  ]
}
```

### Possible `errors[].code` values for `firstName`

| Code | Cause |
|---|---|
| `NotBlank` | Existing — empty / whitespace-only |
| `Size` | Existing — length out of range (now > 50 or < 1) |
| `firstName.tooLong` | New, alias for the above to give a stable client-readable code |
| `firstName.invalidChars` | New — Family B (ASCII controls), C (Unicode invisibles), or D (structural markers) |
| `firstName.looksLikeInstructions` | New — Family E (instruction-shaped phrase) |

### Constraints on the rejection body

- **MUST NOT** include the rejected `firstName` value in any field (`detail`, `errors[].rejectedValue`, etc.). FR-1107.
- **MUST NOT** include the photo bytes (already enforced by `ProblemDetailAdvice`).
- The error array MAY contain entries for other fields (e.g., a missing photo) — multi-field rejection is unchanged from existing behaviour.
- The provider (Gemini) MUST NOT be called for any request that fails validation. The existing `@Valid` pipeline runs before the controller method body, so this is enforced structurally — but the integration test pins it via a mock provider that fails the test if invoked on a rejected request.

## Success response

Unchanged.

## Caller-side migration notes

Callers already handling `400 Problem+JSON` for `firstName` will continue to work. New `errors[].code` values (`firstName.tooLong`, `firstName.invalidChars`, `firstName.looksLikeInstructions`) are additive — clients that ignore unknown codes and surface the response `detail` text are unaffected.

The frontend in this repo (`alterEgoClient`) already displays the backend's `detail` field on rejection; no frontend wire-protocol change is required.

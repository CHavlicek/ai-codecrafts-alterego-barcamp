# Contracts — 024 Architecture Refactor

This refactor preserves the public HTTP contract byte-compatibly (FR-2413, FR-2425, SC-005). It does **not** redefine the wire format. The canonical OpenAPI files remain where they were first written:

| Public contract | Canonical file | Status under `024` |
|---|---|---|
| `POST /api/alter-ego` | `specs/001-initial-poc/contracts/alter-egos.openapi.yaml` | **Frozen by `024`** — contract tests under `backend/src/test/java/com/aiavatar/alterego/contract/` use `swagger-request-validator-mockmvc` against this file. If a refactor commit causes the contract tests to fail, the refactor — not the contract — is rolled back. |
| `POST /api/alter-ego/email` | `specs/023-email-send-image/contracts/alter-egos-email.openapi.yaml` | **Frozen by `024`** — same enforcement. |

`024` adds **one new internal contract**, which is the post-refactor backend Service Provider Interface (SPI) that all generation providers and the fallback path satisfy:

| Internal contract | File |
|---|---|
| `ImageGeneratorPort` SPI | [`image-generator.spi.md`](./image-generator.spi.md) |

This is not an HTTP contract and not consumed by the frontend; it's the post-refactor seam definition required by US1 and FR-2402. It is enforced by ArchUnit in the `arch/` test tier and by JUnit 5 tests under `backend/src/test/java/com/aiavatar/alterego/service/`.

## What is NOT redefined here

- No new public HTTP endpoint.
- No new request or response shape.
- No new validation rule.
- No new RFC 7807 problem-type.

If the planning phase had introduced any of those, this directory would carry a full OpenAPI YAML. Because the refactor's invariant is byte-for-byte wire compatibility, the existing YAMLs are authoritative.

# Contract — `POST /api/v1/alter-egos` request body change

## Scope

Only the **request body shape** changes. The HTTP method, path, response shape, status codes, headers, and multipart layout are all unchanged.

## Multipart parts (unchanged)

| Part name | Content type | Description |
|---|---|---|
| `photo` | `image/jpeg` or `image/png` | The captured / uploaded photo bytes. |
| `selections` | `application/json` | The JSON document described below. |

## `selections` JSON body — before

```json
{
  "pose": "heroic",
  "archetype": "engineer",
  "universe": "star-wars",
  "vibe": "builder",
  "artStyle": "oil-painting",
  "firstName": "Mia",
  "photoMode": "shoulders-up"
}
```

Fields `pose` and `vibe` were carried from the UI (Pose was required; Vibe was optional).

## `selections` JSON body — after (this feature)

```json
{
  "archetype": "engineer",
  "universe": "star-wars",
  "artStyle": "oil-painting",
  "firstName": "Mia",
  "photoMode": "shoulders-up"
}
```

The client MUST stop sending `pose` and `vibe`.

## Server behaviour for extraneous fields

If a client still sends `pose` and/or `vibe`, the server MUST silently ignore them (Jackson's default `FAIL_ON_UNKNOWN_PROPERTIES=false` posture, which the project already relies on). The server then independently rolls one Pose and one Vibe value uniformly at random from the full backend enum, and uses those rolled values in the prompt pipeline.

The contract test `AlterEgoControllerContractTest.clientSuppliedPoseAndVibeAreIgnored` will explicitly verify this:

1. Issue `selections` with `pose: "heroic"`, `vibe: "rebel"` set.
2. Re-issue the same request 50 times.
3. Capture the resolved `AlterEgoRequest` that the service hands to the prompt builder (using a `Mockito.ArgumentCaptor` on a partially-mocked or test-doubled prompt builder seam).
4. Assert that across 50 requests, at least two distinct Pose values and at least two distinct Vibe values are observed — i.e., the client-supplied value is not pinning the outcome.

## Validation surface (after)

The server now validates the public DTO `AlterEgoUserSelections`. Constraint violations result in **400 Bad Request** exactly as before.

| Field | Constraint |
|---|---|
| `archetype` | `@NotNull` |
| `universe` | `@NotNull` |
| `artStyle` | `@NotNull` |
| `firstName` | `@NotBlank` + `@Size(max = 50)` + `@ValidFirstName` (existing 011 rules) |
| `photoMode` | optional at Bean-Validation layer (preserves existing 011 behaviour) |

The previous `@NotNull Pose pose` and the optional `Vibe vibe` are **no longer validated** — the fields are not on the DTO at all.

## Response (unchanged)

`200 OK` with the existing `AlterEgoResponse` JSON shape, headers, and request-ID echo on `X-Request-Id`. No client-visible response field changes.

## Idempotency / safety

No state is persisted; calling the endpoint twice with identical inputs is allowed to produce different posters (because the server re-rolls Pose and Vibe each time). This is documented in the spec FR-2023 and was already true for the Gemini model's intrinsic non-determinism.

## Failing-test order (TDD checkpoint)

Before any production code is edited, the following two failing tests must exist and run red:

1. `AlterEgoControllerContractTest.requestBodyWithoutPoseOrVibeProduces200`: sends the **new-shape** body, expects 200. *(Fails red because the old `AlterEgoRequest` has `@NotNull Pose pose` and will 400.)*
2. `AlterEgoControllerContractTest.clientSuppliedPoseAndVibeAreIgnored`: as described above. *(Fails red because the old service uses the client's value.)*

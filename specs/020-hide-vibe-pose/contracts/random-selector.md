# Contract — `RandomCategorySelector` (server-internal)

## Location

`backend/src/main/java/com/aiavatar/alterego/service/random/RandomCategorySelector.java`

## Bean type

`@Component` — Spring singleton, autowired into `AlterEgoService`.

## Public API

```java
public <E extends Enum<E>> E pickUniform(Class<E> clazz);
```

### Inputs

- `clazz`: the `Class` object of a Java enum (e.g. `Pose.class`, `Vibe.class`).

### Outputs

- Exactly one enum constant from the input enum's full constant set.

### Behaviour

- **Uniformity**: each constant is returned with probability `1 / clazz.getEnumConstants().length`. Over `N` calls the empirical distribution converges to uniform.
- **Independence**: consecutive calls are independent; no internal "deck" or "cooldown" — successive calls may return the same value.
- **No state per call**: the method does not write to any field, does not block, and does not allocate beyond the array returned by `getEnumConstants()`.

### Errors

- If `clazz.getEnumConstants()` returns `null` (the caller passed a non-enum `Class`) or an empty array, the method throws `IllegalStateException` with a message identifying the offending class. The exception bubbles up and is mapped by the existing controller error-handling onto the resilient frontend's fallback state (Principle IV).
- A `null` `clazz` argument throws `NullPointerException` (the implicit one from `clazz.getEnumConstants()` is acceptable; the test asserts behaviour, not the specific cause class).

## Construction

Two constructors:

| Visibility | Signature | Purpose |
|---|---|---|
| `public` | `RandomCategorySelector()` | Production default — uses `RandomGenerator.getDefault()`. |
| package-private | `RandomCategorySelector(RandomGenerator rng)` | Test seam — allows a deterministic / seeded generator. |

## Failing-test order (TDD checkpoint)

`RandomCategorySelectorTest` (file `backend/src/test/java/com/aiavatar/alterego/unit/random/RandomCategorySelectorTest.java`) — written before the production class exists; each test fails red on the missing class, then passes green once the class is added:

1. `pickUniform_returnsEnumConstant`: assert the returned value is one of `Pose.values()`.
2. `pickUniform_isUniformAcrossConstants_pose`: with a `RandomGenerator` seeded for reproducibility, draw 10,000 picks and assert each Pose constant appears at least `10000 / 4 × 0.85` times (chi-squared light check; covers FR-2022).
3. `pickUniform_isUniformAcrossConstants_vibe`: same as #2 for Vibe.
4. `pickUniform_emptyEnum_throws`: a custom `enum EmptyTestEnum {}` defined in the test scope — assert `IllegalStateException`.
5. `pickUniform_isIndependent`: two consecutive calls with a seeded generator both return the deterministically-expected constant (sanity check that no cooldown / dedup was accidentally introduced).
6. `injectedRng_isUsed`: pass a `RandomGenerator` whose `nextInt(bound)` returns a fixed value; assert the returned enum constant matches the expected index.

## What this contract does **not** specify

- Thread-safety of concurrent `pickUniform` calls. The kiosk POC issues at most one Generate request at a time per JVM; concurrent contention is not exercised by any test. If a future feature parallelises generation it must add a thread-safety test and (if needed) switch the implementation to `ThreadLocalRandom.current()` or wrap the call.
- Cryptographic randomness. `pickUniform` is **not** secure; do not use it for tokens, secrets, or anti-replay nonces.

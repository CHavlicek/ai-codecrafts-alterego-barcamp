package com.aiavatar.alterego.domain.policy;

import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * 020 — Uniformly-random enum picker used to fill in the {@code Pose} and
 * {@code Vibe} categories on every Generate request after they were removed
 * from the UI (spec FR-2020 / FR-2021 / FR-2022 / FR-2025).
 *
 * <p>Single responsibility: given an enum {@code Class}, return one constant
 * drawn uniformly at random from its full constant set. Not secure — do not
 * use for tokens or nonces (cryptographic randomness is not required by the
 * spec).
 *
 * <p>Production uses {@code new Random()} via the no-arg constructor.
 * {@code Random} implements {@link RandomGenerator} (since JDK 17) and is
 * always available — unlike {@code RandomGenerator.getDefault()} which on
 * some slimmed JREs resolves to an algorithm (e.g. {@code L32X64MixRandom})
 * whose provider is not shipped, causing startup to fail.
 *
 * <p>The second constructor is a test seam — pass a deterministic / seeded
 * {@link RandomGenerator} (e.g. {@code new Random(42L)}) to make the picks
 * reproducible.
 */
public class RandomCategorySelector {

    private final RandomGenerator rng;

    public RandomCategorySelector() {
        this(new Random());
    }

    /**
     * Test seam — pass in a deterministic / seeded {@link RandomGenerator}
     * (e.g. {@code new Random(42L)}) to make the picks reproducible. The
     * production wiring always uses the no-arg constructor.
     */
    public RandomCategorySelector(RandomGenerator rng) {
        this.rng = Objects.requireNonNull(rng, "rng");
    }

    /**
     * Returns one constant of {@code clazz} chosen uniformly at random.
     *
     * @throws IllegalStateException if {@code clazz} has no constants
     *                               (defensive guard — Java enums are
     *                               non-empty by construction, so this only
     *                               fires on programmer error).
     */
    public <E extends Enum<E>> E pickUniform(Class<E> clazz) {
        E[] values = clazz.getEnumConstants();
        if (values == null || values.length == 0) {
            throw new IllegalStateException("No enum constants for " + clazz.getName());
        }
        return values[rng.nextInt(values.length)];
    }
}

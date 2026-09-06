package com.aiavatar.alterego.unit.random;

import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.domain.policy.RandomCategorySelector;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 020 — unit tests for {@link RandomCategorySelector}.
 *
 * <p>Per {@code contracts/random-selector.md}: covers the enum-constant
 * return invariant, the uniformity property over many draws, the empty-enum
 * defensive guard, and the injected-RNG seam.
 */
class RandomCategorySelectorTest {

    @Test
    void pickUniformReturnsOneOfPoseValues() {
        RandomCategorySelector selector = new RandomCategorySelector();
        Pose pick = selector.pickUniform(Pose.class);
        assertNotNull(pick);
        assertTrue(java.util.Arrays.asList(Pose.values()).contains(pick),
                () -> "Returned " + pick + " is not a Pose constant");
    }

    @Test
    void pickUniformIsUniformAcrossPoseConstants() {
        // Seeded RNG → deterministic distribution. 10 000 draws across 4
        // constants; the floor is 10000 / 4 × 0.85 = 2125 — generous slack
        // for the seeded sequence but tight enough to catch a stuck index.
        RandomCategorySelector selector = new RandomCategorySelector(new Random(0xABCDEFL));
        Map<Pose, Integer> counts = new EnumMap<>(Pose.class);
        for (Pose pose : Pose.values()) counts.put(pose, 0);
        for (int i = 0; i < 10_000; i++) {
            counts.merge(selector.pickUniform(Pose.class), 1, Integer::sum);
        }
        for (Pose pose : Pose.values()) {
            assertTrue(counts.get(pose) >= 2125,
                    () -> "Pose " + pose + " observed only " + counts.get(pose) + " / 10000 times");
        }
    }

    @Test
    void pickUniformIsUniformAcrossVibeConstants() {
        RandomCategorySelector selector = new RandomCategorySelector(new Random(0xFEEDBEEFL));
        Map<Vibe, Integer> counts = new EnumMap<>(Vibe.class);
        for (Vibe vibe : Vibe.values()) counts.put(vibe, 0);
        for (int i = 0; i < 10_000; i++) {
            counts.merge(selector.pickUniform(Vibe.class), 1, Integer::sum);
        }
        for (Vibe vibe : Vibe.values()) {
            assertTrue(counts.get(vibe) >= 2125,
                    () -> "Vibe " + vibe + " observed only " + counts.get(vibe) + " / 10000 times");
        }
    }

    @Test
    void pickUniformWithEmptyEnumThrows() {
        RandomCategorySelector selector = new RandomCategorySelector();
        assertThrows(IllegalStateException.class,
                () -> selector.pickUniform(EmptyTestEnum.class));
    }

    @Test
    void pickUniformWithInjectedRngHonoursIt() {
        // FixedIndexRandom returns the same nextInt(bound) — every draw must
        // land on the same enum index.
        RandomGenerator fixedZero = new FixedIndexGenerator(0);
        RandomCategorySelector selectorZero = new RandomCategorySelector(fixedZero);
        assertSame(Pose.values()[0], selectorZero.pickUniform(Pose.class));

        RandomGenerator fixedTwo = new FixedIndexGenerator(2);
        RandomCategorySelector selectorTwo = new RandomCategorySelector(fixedTwo);
        assertSame(Pose.values()[2], selectorTwo.pickUniform(Pose.class));
    }

    @Test
    void consecutivePicksAreIndependent() {
        // Two consecutive nextInt(4) calls from a freshly-seeded Random(42L)
        // produce a deterministic pair — we don't care what the values are,
        // only that the selector does not introduce a cooldown / dedup.
        Random reference = new Random(42L);
        int firstExpected = reference.nextInt(Pose.values().length);
        int secondExpected = reference.nextInt(Pose.values().length);

        RandomCategorySelector selector = new RandomCategorySelector(new Random(42L));
        assertSame(Pose.values()[firstExpected], selector.pickUniform(Pose.class));
        assertSame(Pose.values()[secondExpected], selector.pickUniform(Pose.class));
    }

    @Test
    void defaultConstructorYieldsWorkingPicker() {
        // Smoke-check that the no-arg constructor (production wiring) doesn't
        // throw and produces valid picks. 100 draws is enough to fail loud if
        // the underlying RandomGenerator is missing.
        RandomCategorySelector selector = new RandomCategorySelector();
        for (int i = 0; i < 100; i++) {
            assertNotNull(selector.pickUniform(Vibe.class));
        }
        assertEquals(Pose.values().length, 4); // sanity: still 4 poses
    }

    /** Empty enum fixture — only exists to exercise the defensive guard. */
    private enum EmptyTestEnum { }

    /** Deterministic {@link RandomGenerator} that always reports the same index. */
    private record FixedIndexGenerator(int index) implements RandomGenerator {
        @Override public long nextLong() { return index; }
        @Override public int nextInt(int bound) { return index % bound; }
    }
}

package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.infrastructure.photo.PhotoReductionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 016 T010 — compact-constructor invariants for {@link PhotoReductionConfig}.
 *
 * <p>The record drives 016's provider-neutral {@code PhotoReducer} (R4).
 * Misconfigured thresholds MUST fail fast at construction so a Spring Boot
 * startup carrying a bad {@code aiavatar.{gemini|falai}.*} config block
 * surfaces the mistake immediately.
 */
class PhotoReductionConfigTest {

    @Test
    void happyPathCarriesAllFour() {
        PhotoReductionConfig cfg = new PhotoReductionConfig(
                4 * 1024 * 1024, 1536, 1024, 0.85);
        assertEquals(4 * 1024 * 1024, cfg.maxBytes());
        assertEquals(1536, cfg.maxLongestEdge());
        assertEquals(1024, cfg.reducedTargetLongestEdge());
        assertEquals(0.85, cfg.reducedJpegQuality(), 1e-9);
    }

    @Test
    void rejectsZeroOrNegativeMaxBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(0, 1536, 1024, 0.85));
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(-1, 1536, 1024, 0.85));
    }

    @Test
    void rejectsZeroOrNegativeMaxLongestEdge() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(4 * 1024 * 1024, 0, 1024, 0.85));
    }

    @Test
    void rejectsZeroOrNegativeReducedTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(4 * 1024 * 1024, 1536, 0, 0.85));
    }

    @Test
    void rejectsJpegQualityOutOfRange() {
        // q must be in (0, 1].
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(4 * 1024 * 1024, 1536, 1024, 0.0));
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(4 * 1024 * 1024, 1536, 1024, -0.1));
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(4 * 1024 * 1024, 1536, 1024, 1.01));
    }

    @Test
    void acceptsJpegQualityAtUpperBound() {
        // q == 1.0 is the lossless-ish ceiling; allowed.
        PhotoReductionConfig cfg = new PhotoReductionConfig(
                4 * 1024 * 1024, 1536, 1024, 1.0);
        assertEquals(1.0, cfg.reducedJpegQuality(), 1e-9);
    }

    @Test
    void rejectsTargetLargerThanCeiling() {
        // Up-scaling is forbidden — reducedTargetLongestEdge MUST be <=
        // maxLongestEdge (otherwise an over-threshold input would be
        // up-sampled, contradicting 003 FR-207).
        assertThrows(IllegalArgumentException.class,
                () -> new PhotoReductionConfig(4 * 1024 * 1024, 1024, 2048, 0.85));
    }

    @Test
    void allowsTargetEqualToCeiling() {
        PhotoReductionConfig cfg = new PhotoReductionConfig(
                4 * 1024 * 1024, 1024, 1024, 0.85);
        assertEquals(cfg.maxLongestEdge(), cfg.reducedTargetLongestEdge());
    }
}

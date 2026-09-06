package com.aiavatar.alterego.infrastructure.photo;

/**
 * Provider-neutral parameters for {@link PhotoReducer}. Replaces the implicit
 * {@code GeminiProperties} dependency that 003's reducer carried, so 016's
 * fal.ai path can drive the same reducer with its own (possibly different)
 * thresholds — see 016 research.md R4 / FR-1616.
 *
 * @param maxBytes                  pass-through ceiling — encoded byte size
 * @param maxLongestEdge            pass-through ceiling — pixels (longest edge)
 * @param reducedTargetLongestEdge  resize target on over-threshold input
 * @param reducedJpegQuality        JPEG quality factor in {@code (0, 1]}
 */
public record PhotoReductionConfig(
        int maxBytes,
        int maxLongestEdge,
        int reducedTargetLongestEdge,
        double reducedJpegQuality
) {
    public PhotoReductionConfig {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes < 1");
        }
        if (maxLongestEdge < 1) {
            throw new IllegalArgumentException("maxLongestEdge < 1");
        }
        if (reducedTargetLongestEdge < 1) {
            throw new IllegalArgumentException("reducedTargetLongestEdge < 1");
        }
        if (reducedJpegQuality <= 0 || reducedJpegQuality > 1) {
            throw new IllegalArgumentException("reducedJpegQuality out of (0, 1]");
        }
        if (reducedTargetLongestEdge > maxLongestEdge) {
            throw new IllegalArgumentException(
                    "reducedTargetLongestEdge MUST be <= maxLongestEdge");
        }
    }
}

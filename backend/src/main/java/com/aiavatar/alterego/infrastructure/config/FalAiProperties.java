package com.aiavatar.alterego.infrastructure.config;

import com.aiavatar.alterego.infrastructure.photo.PhotoReductionConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the fal.ai provider (016 FR-1610 / FR-1611 /
 * FR-1614a / FR-1616). Bound from the {@code aiavatar.falai.*} block in
 * {@code application.yml}; every key is env-var-overridable per
 * 016 research.md R6.
 *
 * <p>A blank {@link #apiKey()} leaves the provider "not configured" —
 * {@code FalAiImageGenerator} (016) throws
 * {@code GenerationFailure(NOT_CONFIGURED)} pre-HTTP so the orchestrator
 * falls back without attempting an outbound call (FR-1604, mirroring 003's
 * Gemini behaviour).
 *
 * <p>Timeout knobs:
 * <ul>
 *   <li>{@link #submitTimeoutMs()} — per-attempt timeout for the queue submit
 *       POST.</li>
 *   <li>{@link #pollTimeoutMs()} — per-attempt timeout for each subscribe /
 *       status GET.</li>
 *   <li>{@link #fetchTimeoutMs()} — per-attempt timeout for the result GET
 *       and the image-bytes CDN GET.</li>
 *   <li>{@link #endToEndTimeoutMs()} — wall-clock cap across the full
 *       exchange (FR-1614a). Default 30 000 ms (30 s). NOT a per-attempt
 *       timeout that resets on retry.</li>
 * </ul>
 *
 * <p>Photo-reduction parameters mirror {@code GeminiProperties} so both
 * providers can drive the shared {@code PhotoReducer} via
 * {@link #photoReductionConfig()} (016 R4).
 */
@ConfigurationProperties(prefix = "aiavatar.falai")
public record FalAiProperties(
        String apiKey,
        String modelId,
        String endpointUrl,
        int submitTimeoutMs,
        int pollTimeoutMs,
        int fetchTimeoutMs,
        int endToEndTimeoutMs,
        int pollInitialIntervalMs,
        int pollMaxIntervalMs,
        int maxInputBytes,
        int maxInputLongestEdge,
        int reducedTargetLongestEdge,
        double reducedJpegQuality
) {

    /**
     * True if the API key is present (non-null, non-blank). Reads from
     * {@code FAL_AI_API_KEY} via the env-var binding in {@code application.yml}.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Adapter to the provider-neutral {@link PhotoReductionConfig} consumed
     * by {@code PhotoReducer} (016 R4). Lets the fal.ai path drive the same
     * reducer the Gemini path uses, with fal.ai-tuned thresholds.
     */
    public PhotoReductionConfig photoReductionConfig() {
        return new PhotoReductionConfig(maxInputBytes, maxInputLongestEdge,
                reducedTargetLongestEdge, reducedJpegQuality);
    }
}

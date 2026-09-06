package com.aiavatar.alterego.infrastructure.config;

import com.aiavatar.alterego.infrastructure.photo.PhotoReductionConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the Gemini provider. Bound from the
 * {@code aiavatar.gemini.*} block in {@code application.yml}; every key
 * is env-var overridable per 003 research.md R6.
 *
 * <p>A blank {@link #apiKey()} leaves the provider "not configured" —
 * {@code GeminiImageGenerator} (003) and {@code GeminiCharacterGenerator}
 * (014) both throw {@code GenerationFailure(NOT_CONFIGURED)} pre-HTTP so
 * the orchestrator falls back without attempting an outbound call
 * (003 FR-212 / 014 FR-1403).
 *
 * <p>014 delta: two text-side fields appended at the trailing position —
 * {@link #textModelId()} (env: {@code GEMINI_TEXT_MODEL_ID}; default
 * {@code gemini-2.5-flash}) and {@link #textRequestTimeoutMs()} (env:
 * {@code GEMINI_TEXT_REQUEST_TIMEOUT_MS}; default {@code 15000}, per
 * 014 FR-1417 / spec clarification Q3). The shared {@link #apiKey()}
 * configures both image and character paths (014 FR-1414); image-side
 * {@link #modelId()} and {@link #requestTimeoutMs()} remain unchanged.
 */
@ConfigurationProperties(prefix = "aiavatar.gemini")
public record GeminiProperties(
        String apiKey,
        String modelId,
        String endpointUrl,
        int requestTimeoutMs,
        int maxInputBytes,
        int maxInputLongestEdge,
        int reducedTargetLongestEdge,
        double reducedJpegQuality,
        String textModelId,
        int textRequestTimeoutMs
) {

    /**
     * True if the API key is present (non-null, non-blank). Reads from
     * {@code GEMINI_API_KEY} via the env-var binding in {@code application.yml}.
     * Shared by image (003) and character (014) paths.
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Adapter to the provider-neutral {@link PhotoReductionConfig} consumed
     * by {@code PhotoReducer} (016 R4). Lets the Gemini path drive the same
     * reducer the fal.ai path uses, with Gemini-tuned thresholds.
     */
    public PhotoReductionConfig photoReductionConfig() {
        return new PhotoReductionConfig(maxInputBytes, maxInputLongestEdge,
                reducedTargetLongestEdge, reducedJpegQuality);
    }
}

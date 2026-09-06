package com.aiavatar.alterego.unit.falai;

import com.aiavatar.alterego.infrastructure.config.FalAiProperties;
import com.aiavatar.alterego.infrastructure.photo.PhotoReductionConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 016 T024 — typed-config behaviour for {@link FalAiProperties} (R6).
 *
 * <ul>
 *   <li>{@code isConfigured()} mirrors the blank/null/non-blank api-key.</li>
 *   <li>{@code photoReductionConfig()} produces the expected
 *       {@link PhotoReductionConfig}.</li>
 * </ul>
 *
 * <p>Env-var binding + default values are exercised by Spring's
 * {@code @SpringBootTest} elsewhere (loading {@code application.yml}); this
 * test keeps the constructor-level invariants pinned without booting Spring.
 */
class FalAiPropertiesTest {

    private FalAiProperties propsWith(String apiKey) {
        return new FalAiProperties(
                apiKey,
                "fal-ai/nano-banana-pro/edit",
                "https://queue.fal.run",
                /* submitTimeoutMs */ 8_000,
                /* pollTimeoutMs */ 5_000,
                /* fetchTimeoutMs */ 10_000,
                /* endToEndTimeoutMs */ 30_000,
                /* pollInitialIntervalMs */ 1_000,
                /* pollMaxIntervalMs */ 5_000,
                /* maxInputBytes */ 4 * 1024 * 1024,
                /* maxInputLongestEdge */ 1536,
                /* reducedTargetLongestEdge */ 1024,
                /* reducedJpegQuality */ 0.85);
    }

    @Test
    void isConfiguredFalseWhenApiKeyIsNull() {
        assertFalse(propsWith(null).isConfigured());
    }

    @Test
    void isConfiguredFalseWhenApiKeyIsBlank() {
        assertFalse(propsWith("").isConfigured());
        assertFalse(propsWith("   ").isConfigured());
    }

    @Test
    void isConfiguredTrueWhenApiKeyIsNonBlank() {
        assertTrue(propsWith("falai-test-key").isConfigured());
    }

    @Test
    void photoReductionConfigMirrorsTheFourFalaiFields() {
        FalAiProperties props = propsWith("k");
        PhotoReductionConfig cfg = props.photoReductionConfig();
        assertEquals(props.maxInputBytes(), cfg.maxBytes());
        assertEquals(props.maxInputLongestEdge(), cfg.maxLongestEdge());
        assertEquals(props.reducedTargetLongestEdge(), cfg.reducedTargetLongestEdge());
        assertEquals(props.reducedJpegQuality(), cfg.reducedJpegQuality(), 1e-9);
    }

    @Test
    void carriesAllEnvVarBackedFields() {
        // Pin the shape — if any field is removed or renamed without updating
        // the application.yml binding, Spring Boot startup binds blank, and
        // these assertions catch the drift.
        FalAiProperties props = propsWith("k");
        assertEquals("k", props.apiKey());
        assertEquals("fal-ai/nano-banana-pro/edit", props.modelId());
        assertEquals("https://queue.fal.run", props.endpointUrl());
        assertEquals(8_000, props.submitTimeoutMs());
        assertEquals(5_000, props.pollTimeoutMs());
        assertEquals(10_000, props.fetchTimeoutMs());
        // 016 FR-1614a — 30s default end-to-end cap.
        assertEquals(30_000, props.endToEndTimeoutMs());
        assertEquals(1_000, props.pollInitialIntervalMs());
        assertEquals(5_000, props.pollMaxIntervalMs());
    }
}

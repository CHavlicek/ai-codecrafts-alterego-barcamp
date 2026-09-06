package com.aiavatar.alterego.service.config;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T019 — Spring binding for the {@link GeminiProperties} record. Asserts
 * that {@link org.springframework.boot.context.properties.ConfigurationProperties}
 * wiring picks up the overrides in {@link TestPropertySource} and that
 * {@link GeminiProperties#isConfigured()} toggles correctly on a non-blank
 * key.
 *
 * <p>014 delta: text-side fields {@code textModelId} (env: {@code GEMINI_TEXT_MODEL_ID})
 * and {@code textRequestTimeoutMs} (env: {@code GEMINI_TEXT_REQUEST_TIMEOUT_MS})
 * are appended to the trailing position of the record. Their bindings are
 * exercised here alongside the eight 003 fields.
 */
@SpringBootTest(classes = GeminiPropertiesTest.TestConfig.class)
@TestPropertySource(properties = {
        "aiavatar.gemini.api-key=test-key-xyz",
        "aiavatar.gemini.model-id=gemini-test-model",
        "aiavatar.gemini.endpoint-url=http://localhost:9999/v1beta",
        "aiavatar.gemini.request-timeout-ms=5000",
        "aiavatar.gemini.max-input-bytes=1048576",
        "aiavatar.gemini.max-input-longest-edge=512",
        "aiavatar.gemini.reduced-target-longest-edge=256",
        "aiavatar.gemini.reduced-jpeg-quality=0.75",
        "aiavatar.gemini.text-model-id=gemini-test-text-model",
        "aiavatar.gemini.text-request-timeout-ms=7777",
})
class GeminiPropertiesTest {

    @Autowired private GeminiProperties props;

    @Test
    void bindsAllPropertiesFromSpringContext() {
        assertEquals("test-key-xyz", props.apiKey());
        assertEquals("gemini-test-model", props.modelId());
        assertEquals("http://localhost:9999/v1beta", props.endpointUrl());
        assertEquals(5000, props.requestTimeoutMs());
        assertEquals(1_048_576, props.maxInputBytes());
        assertEquals(512, props.maxInputLongestEdge());
        assertEquals(256, props.reducedTargetLongestEdge());
        assertEquals(0.75, props.reducedJpegQuality(), 0.0001);
        assertEquals("gemini-test-text-model", props.textModelId());
        assertEquals(7777, props.textRequestTimeoutMs());
    }

    @Test
    void isConfiguredTrueWhenKeyIsNonBlank() {
        assertTrue(props.isConfigured());
    }

    @Test
    void isConfiguredFalseForNullKey() {
        GeminiProperties blank = new GeminiProperties(null, "m", "http://x", 1, 1, 1, 1, 0.5, "t", 1);
        assertFalse(blank.isConfigured());
    }

    @Test
    void isConfiguredFalseForBlankKey() {
        GeminiProperties blank = new GeminiProperties("   ", "m", "http://x", 1, 1, 1, 1, 0.5, "t", 1);
        assertFalse(blank.isConfigured());
    }

    @Test
    void isConfiguredFalseForEmptyKey() {
        GeminiProperties blank = new GeminiProperties("", "m", "http://x", 1, 1, 1, 1, 0.5, "t", 1);
        assertFalse(blank.isConfigured());
    }

    @org.springframework.boot.context.properties.EnableConfigurationProperties(GeminiProperties.class)
    @org.springframework.boot.SpringBootConfiguration
    static class TestConfig {
    }
}

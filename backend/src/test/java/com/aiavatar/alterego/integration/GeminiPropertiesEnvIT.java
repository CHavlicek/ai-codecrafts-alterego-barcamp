package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * T031 — pins 014 FR-1414 / spec US-5 acceptance scenario 2: independent
 * env-var overrides for the text-side and image-side knobs do NOT cross-
 * contaminate.
 *
 * <p>Spring Boot's environment composition makes a true env-var test
 * awkward (env vars are process-wide), so this IT exercises the same
 * binding mechanism via {@code @TestPropertySource} — which feeds the
 * {@code aiavatar.gemini.*} keys exactly as the env-var bindings in
 * {@code application.yml} would (research §R6).
 *
 * <p>Three nested classes assert the four cells of the (image-override,
 * text-override) truth table.
 */
class GeminiPropertiesEnvIT {

    /** No overrides → both fields take their {@code application.yml} defaults. */
    @SpringBootTest
    @ActiveProfiles("default")
    @Nested
    static class NoOverrides {
        @Autowired private GeminiProperties props;

        @Test
        void imageModelIdDefault() {
            assertEquals("gemini-3.1-flash-image-preview", props.modelId());
        }

        @Test
        void textModelIdDefault() {
            assertEquals("gemini-2.5-flash", props.textModelId());
        }

        @Test
        void textTimeoutDefault() {
            assertEquals(15_000, props.textRequestTimeoutMs());
        }

        @Test
        void imageTimeoutDefault() {
            assertEquals(25_000, props.requestTimeoutMs());
        }
    }

    /** Override only the text-side model id → image-side default holds. */
    @SpringBootTest
    @ActiveProfiles("default")
    @TestPropertySource(properties = {
            "aiavatar.gemini.text-model-id=custom-text-model",
    })
    @Nested
    static class TextModelOverrideOnly {
        @Autowired private GeminiProperties props;

        @Test
        void textModelChanged() {
            assertEquals("custom-text-model", props.textModelId());
        }

        @Test
        void imageModelUnchanged() {
            assertEquals("gemini-3.1-flash-image-preview", props.modelId(),
                    "FR-1414: overriding GEMINI_TEXT_MODEL_ID MUST NOT affect GEMINI_MODEL_ID");
        }

        @Test
        void overrideKeepsThemDistinct() {
            assertNotEquals(props.textModelId(), props.modelId(),
                    "after text-only override, the two model ids MUST be distinct");
        }
    }

    /** Override only the image-side model id → text-side default holds. */
    @SpringBootTest
    @ActiveProfiles("default")
    @TestPropertySource(properties = {
            "aiavatar.gemini.model-id=custom-image-model",
    })
    @Nested
    static class ImageModelOverrideOnly {
        @Autowired private GeminiProperties props;

        @Test
        void imageModelChanged() {
            assertEquals("custom-image-model", props.modelId());
        }

        @Test
        void textModelUnchanged() {
            assertEquals("gemini-2.5-flash", props.textModelId(),
                    "FR-1414: overriding GEMINI_MODEL_ID MUST NOT affect GEMINI_TEXT_MODEL_ID");
        }
    }

    /** Override BOTH — they take effect independently and don't bleed into each other. */
    @SpringBootTest
    @ActiveProfiles("default")
    @TestPropertySource(properties = {
            "aiavatar.gemini.model-id=image-A",
            "aiavatar.gemini.text-model-id=text-B",
            "aiavatar.gemini.request-timeout-ms=20000",
            "aiavatar.gemini.text-request-timeout-ms=10000",
    })
    @Nested
    static class BothOverridden {
        @Autowired private GeminiProperties props;

        @Test
        void overridesAreApplyIndependently() {
            assertEquals("image-A", props.modelId());
            assertEquals("text-B", props.textModelId());
            assertEquals(20_000, props.requestTimeoutMs());
            assertEquals(10_000, props.textRequestTimeoutMs());
        }
    }
}

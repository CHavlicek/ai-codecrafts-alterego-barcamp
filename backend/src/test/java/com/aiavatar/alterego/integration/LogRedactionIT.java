package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T021 — Log-redaction integration test (FR-016). Boots the real Spring
 * context, sends a successful generation request, and asserts that the
 * captured log output contains <strong>none</strong> of the markers that
 * would indicate a photo payload leaked into a log line:
 *
 * <ul>
 *   <li>{@code data:image/} (data-URL prefix; would mean the response body
 *       was logged verbatim),</li>
 *   <li>{@code photoBytes}, {@code imageData} (the field-name markers
 *       {@link com.aiavatar.alterego.boundary.logging.PhotoRedactionFilter} drops on),</li>
 *   <li>The {@code "photo": } JSON-field marker (defensive: in case
 *       request body logging is ever enabled).</li>
 * </ul>
 *
 * Note: {@link com.aiavatar.alterego.boundary.logging.PhotoRedactionFilter} is the
 * primary enforcement; this test is the integration-level safety net that
 * proves the application's actual log output stays clean for a real
 * request flow under the default profile.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
@ExtendWith(OutputCaptureExtension.class)
class LogRedactionIT {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void successfulRequestEmitsNoPhotoBytesIntoLogs(CapturedOutput output) {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);
        assertEquals(200, response.getStatusCode().value());

        String all = output.getAll();
        assertFalse(all.contains("data:image/"),
                "Logs MUST NOT contain a data: URL prefix (FR-016): " + firstSnippet(all, "data:image/"));
        assertFalse(all.contains("photoBytes"),
                "Logs MUST NOT contain the photoBytes field marker");
        assertFalse(all.contains("imageData"),
                "Logs MUST NOT contain the imageData field marker");
        assertFalse(all.contains("\"photo\":"),
                "Logs MUST NOT contain a serialized photo field");
    }

    /**
     * T039 — 003 FR-219. Every Generate run emits exactly one structured
     * {@code event=generation.completed} log line carrying {@code outcome}
     * (and — on fallback — {@code reason}). The structured fields MUST
     * appear; the photo bytes / API key MUST NOT.
     */
    @Test
    void defaultProfileEmitsStructuredGenerationCompletedLineWithFallbackOutcomeAndProvider(CapturedOutput output) {
        // 016 FR-1612: default profile reports outcome=fallback,
        // provider=stub, reason=not_configured. The structured log line
        // also carries provider and attemptedProvider keys (FR-1613).
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, null, ArtStyle.CEL_SHADED, "Maria", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);
        assertEquals(200, response.getStatusCode().value());

        String all = output.getAll();
        assertTrue(all.contains("\"event\":\"generation.completed\""),
                "FR-219: generation.completed event field MUST appear in logs");
        // 016: default profile is the stub path → outcome=fallback.
        assertTrue(all.contains("\"outcome\":\"fallback\""),
                "016 FR-1612: default-profile run MUST log outcome=fallback");
        assertTrue(all.contains("\"reason\":\"not_configured\""),
                "016 FR-1612: default-profile run MUST log reason=not_configured");
        // 016 FR-1613: log line MUST carry provider AND attemptedProvider keys.
        assertTrue(all.contains("\"provider\":\"stub\""),
                "016 FR-1613: log line MUST carry provider=stub on default profile");
        assertTrue(all.contains("\"attemptedProvider\":\"none\""),
                "016 FR-1613: log line MUST carry attemptedProvider=none when stub-only");

        // Exactly one structured generation.completed event per request
        // (014 FR-1411 / 016 FR-1613).
        long completedCount = countOccurrences(all, "\"event\":\"generation.completed\"");
        assertEquals(1L, completedCount,
                "FR-1411: exactly one structured generation.completed event per request; got " + completedCount);
    }

    /**
     * 016 T041 / FR-1613 / FR-1620 — when the orchestrator routes through
     * the fal.ai provider, the structured log line MUST carry both
     * {@code provider} and {@code attemptedProvider} keys, AND MUST NOT
     * leak the API key, photo bytes, or fal.ai's image URL.
     *
     * <p>Driven against the {@code falai} profile with WireMock simulating
     * a happy-path fal.ai exchange — co-located here rather than in a
     * dedicated {@code FalaiLogRedactionIT} to keep the redaction-related
     * assertions in one place.
     */
    @org.junit.jupiter.api.Nested
    @org.springframework.test.context.NestedTestConfiguration(
            org.springframework.test.context.NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @ActiveProfiles("falai")
    @ExtendWith(OutputCaptureExtension.class)
    class FalaiLogRedaction {

        private static final String MODEL_ID = "fal-ai/test-model/edit";
        private static final String REQUEST_ID = "rid-log-it";
        private static final String SECRET_KEY = "the-falai-key-MUST-NOT-leak";

        private static com.github.tomakehurst.wiremock.WireMockServer wireMock;

        @Autowired private TestRestTemplate falaiRestTemplate;

        @org.junit.jupiter.api.BeforeAll
        static void startWireMock() {
            wireMock = new com.github.tomakehurst.wiremock.WireMockServer(0);
            wireMock.start();
        }

        @org.junit.jupiter.api.AfterAll
        static void stopWireMock() {
            if (wireMock != null) wireMock.stop();
        }

        @org.junit.jupiter.api.BeforeEach
        void resetStubs() { wireMock.resetAll(); }

        @org.springframework.test.context.DynamicPropertySource
        static void falAiProperties(org.springframework.test.context.DynamicPropertyRegistry registry) {
            registry.add("aiavatar.falai.api-key", () -> SECRET_KEY);
            registry.add("aiavatar.falai.endpoint-url", () -> wireMock.baseUrl());
            registry.add("aiavatar.falai.model-id", () -> MODEL_ID);
            registry.add("aiavatar.falai.submit-timeout-ms", () -> "5000");
            registry.add("aiavatar.falai.poll-timeout-ms", () -> "5000");
            registry.add("aiavatar.falai.fetch-timeout-ms", () -> "5000");
            registry.add("aiavatar.falai.end-to-end-timeout-ms", () -> "10000");
            registry.add("aiavatar.falai.poll-initial-interval-ms", () -> "1");
            registry.add("aiavatar.falai.poll-max-interval-ms", () -> "1");
        }

        @Test
        void falaiHappyPathLogLineCarriesProviderAndDoesNotLeakSecrets(CapturedOutput output) throws Exception {
            String statusUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status";
            String responseUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID;
            String cdnUrl = wireMock.baseUrl() + "/cdn/img.jpg";

            wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.post(
                    com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/" + MODEL_ID))
                    .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"request_id\":\"" + REQUEST_ID + "\","
                                    + "\"status_url\":\"" + statusUrl + "\","
                                    + "\"response_url\":\"" + responseUrl + "\","
                                    + "\"status\":\"IN_QUEUE\"}")));
            wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                    com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(
                            "/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status"))
                    .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"status\":\"COMPLETED\"}")));
            wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                    com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo(
                            "/" + MODEL_ID + "/requests/" + REQUEST_ID))
                    .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"images\":[{\"url\":\"" + cdnUrl
                                    + "\",\"width\":64,\"height\":64,\"content_type\":\"image/jpeg\"}]}")));
            byte[] jpeg = renderTinyJpeg();
            wireMock.stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(
                    com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo("/cdn/img.jpg"))
                    .willReturn(com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "image/jpeg")
                            .withBody(jpeg)));

            AlterEgoRequest selections = new AlterEgoRequest(
                    Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                    null, ArtStyle.OIL_PAINTING, "Paula", null);

            org.springframework.http.ResponseEntity<AlterEgoResponse> response = falaiRestTemplate.exchange(
                    "/api/v1/alter-egos", org.springframework.http.HttpMethod.POST,
                    MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                    AlterEgoResponse.class);
            assertEquals(200, response.getStatusCode().value());

            String all = output.getAll();
            // FR-1613: log line carries provider AND attemptedProvider keys.
            assertTrue(all.contains("\"provider\":\"falai\""),
                    "FR-1613: log line MUST carry provider=falai on a fal.ai real-success");
            assertTrue(all.contains("\"attemptedProvider\":\"falai\""),
                    "FR-1613: log line MUST carry attemptedProvider=falai");
            assertTrue(all.contains("\"event\":\"generation.completed\""),
                    "FR-219: generation.completed event MUST appear");

            // FR-1611 / FR-1620: API key MUST NOT leak.
            assertFalse(all.contains(SECRET_KEY),
                    "FR-1611: fal.ai API key MUST NOT appear in logs");
            // FR-1618 / FR-1620: fal.ai's CDN URL MUST NOT appear in logs
            // surfaced at INFO+ level.
            assertFalse(all.contains(cdnUrl),
                    "FR-1620: fal.ai CDN URL MUST NOT leak into application logs");
            // Photo bytes / data URL MUST NOT leak.
            assertFalse(all.contains("data:image/"),
                    "FR-1618: photo data URL MUST NOT leak into logs");
        }

        private static byte[] renderTinyJpeg() throws java.io.IOException {
            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(
                    64, 64, java.awt.image.BufferedImage.TYPE_INT_RGB);
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "JPEG", baos);
            return baos.toByteArray();
        }
    }

    private static long countOccurrences(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String firstSnippet(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        if (idx < 0) return "<not present>";
        int from = Math.max(0, idx - 50);
        int to = Math.min(haystack.length(), idx + needle.length() + 50);
        return haystack.substring(from, to);
    }
}

package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.testsupport.GeminiTextWireMockStubs;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.http.Fault;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T036 — SC-205 fault-injection matrix under the {@code gemini} profile.
 * Each test row injects a distinct provider-boundary failure via WireMock
 * and asserts that:
 * <ul>
 *   <li>HTTP 200 is returned with a complete fallback poster (001 FR-018),</li>
 *   <li>{@code meta.outcome == "fallback"} with the expected
 *       {@code meta.reason} code (003 FR-218),</li>
 *   <li>a subsequent healthy run succeeds (no sticky error state).</li>
 * </ul>
 *
 * <p>The {@code NOT_CONFIGURED} row is covered separately (no WireMock
 * stubbing needed): {@link GenerateAlterEgoGeminiNotConfiguredIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gemini")
class GenerateAlterEgoGeminiFailureIT {

    private static WireMockServer wireMock;

    @Autowired private TestRestTemplate restTemplate;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(0);
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @BeforeEach
    void resetStubsAndRegisterTextBaseline() {
        wireMock.resetAll();
        // 014 — happy-path text stub so each row of this matrix isolates
        // an IMAGE-side fault. urlPathEqualTo on the text-model URL beats
        // the image stubs' broad urlPathMatching(".*") pattern.
        GeminiTextWireMockStubs.happyPath(wireMock, "gemini-test-text-model");
    }

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.gemini.api-key", () -> "test-integration-key");
        registry.add("aiavatar.gemini.endpoint-url", () -> wireMock.baseUrl() + "/v1beta");
        registry.add("aiavatar.gemini.model-id", () -> "gemini-test-model");
        registry.add("aiavatar.gemini.text-model-id", () -> "gemini-test-text-model");
        // Short per-attempt timeout so the timeout matrix row fires quickly.
        registry.add("aiavatar.gemini.request-timeout-ms", () -> "500");
        // Generous text timeout so the image-side faults remain isolated.
        registry.add("aiavatar.gemini.text-request-timeout-ms", () -> "5000");
    }

    @Test
    @DisplayName("CONNECTION_RESET_BY_PEER → outcome=fallback, reason=network_error")
    void connectionResetMapsToNetworkError() {
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        AlterEgoResponse body = postGenerate();
        assertFallbackOutcome(body, FallbackReason.NETWORK_ERROR);
    }

    @Test
    @DisplayName("HTTP 500 → outcome=fallback, reason=network_error")
    void http500MapsToNetworkError() {
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(500).withBody("server went home")));

        AlterEgoResponse body = postGenerate();
        assertFallbackOutcome(body, FallbackReason.NETWORK_ERROR);
    }

    @Test
    @DisplayName("HTTP 429 → outcome=fallback, reason=rate_limited")
    void http429MapsToRateLimited() {
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "30")));

        AlterEgoResponse body = postGenerate();
        assertFallbackOutcome(body, FallbackReason.RATE_LIMITED);
    }

    @Test
    @DisplayName("fixed delay exceeds per-attempt timeout → outcome=fallback, reason=timeout")
    void fixedDelayMapsToTimeout() {
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(200).withFixedDelay(3_000)));

        AlterEgoResponse body = postGenerate();
        assertFallbackOutcome(body, FallbackReason.TIMEOUT);
    }

    @Test
    @DisplayName("HTTP 200 with garbage JSON → outcome=fallback, reason=malformed_response")
    void garbageBodyMapsToMalformedResponse() {
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"garbage\": true}")));

        AlterEgoResponse body = postGenerate();
        assertFallbackOutcome(body, FallbackReason.MALFORMED_RESPONSE);
    }

    @Test
    @DisplayName("HTTP 200 with safety blockReason → outcome=fallback, reason=safety_refused")
    void promptFeedbackBlockReasonMapsToSafetyRefused() {
        String body = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},"
                + "\"candidates\":[{\"content\":{\"parts\":[]}}]}";
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(body)));

        AlterEgoResponse response = postGenerate();
        assertFallbackOutcome(response, FallbackReason.SAFETY_REFUSED);
    }

    @Test
    @DisplayName("no sticky error: fault then healthy → second run is outcome=real")
    void aSecondHealthyRunAfterFaultReturnsReal() throws Exception {
        // First: force a 500 to exercise the fallback path.
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(500)));
        AlterEgoResponse faulty = postGenerate();
        assertFallbackOutcome(faulty, FallbackReason.NETWORK_ERROR);

        // Now switch to a healthy stub and re-run. Outcome should flip to REAL.
        wireMock.resetAll();
        // 014 — re-register the text baseline that @BeforeEach set up;
        // resetAll() cleared it.
        GeminiTextWireMockStubs.happyPath(wireMock, "gemini-test-text-model");
        String base64 = Base64.getEncoder().encodeToString(renderPng(8, 8));
        String healthyBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + base64 + "\"}}]}}]}";
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(healthyBody)));

        AlterEgoResponse healthy = postGenerate();
        assertEquals(AlterEgoResponse.Outcome.REAL, healthy.meta().outcome(),
                "after resetting to healthy stub, a new Generate MUST return outcome=real");
    }

    // --- helpers --------------------------------------------------------

    private AlterEgoResponse postGenerate() {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);
        assertEquals(200, response.getStatusCode().value(),
                "001 FR-018: every run MUST return HTTP 200 with a complete poster");
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private static void assertFallbackOutcome(AlterEgoResponse body, FallbackReason expected) {
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome(),
                "provider fault MUST yield outcome=fallback");
        assertEquals(expected, body.meta().reason(),
                () -> "expected reason=" + expected + ", got " + body.meta().reason());
        assertEquals(Provider.STUB, body.meta().provider(),
                "016 FR-1612: every fallback MUST report provider=STUB");
        // Fallback path still produces a complete poster + character.
        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"));
        assertNotNull(body.character().heroTitleLine1());
    }

    private static byte[] renderPng(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, new Color(x % 256, y % 256, (x + y) % 256).getRGB());
            }
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }
    }
}

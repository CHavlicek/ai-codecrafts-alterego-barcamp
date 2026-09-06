package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.Pose;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T017 — fault-injection matrix for the text endpoint under the
 * {@code gemini} profile. Each test row stubs the text endpoint with a
 * distinct failure mode while leaving the image endpoint on a happy-path
 * stub, then asserts that:
 * <ul>
 *   <li>HTTP 200 is returned with a complete fallback character + fallback poster
 *       (FR-1404, FR-1407, 001 FR-018),</li>
 *   <li>{@code meta.outcome == "fallback"} with the expected {@code meta.reason}
 *       (research §R5 / §R9, mirrors 003's image-side matrix),</li>
 *   <li>{@code character.heroTitleLine1} is the user's first name uppercased
 *       (FR-1408 — preserved on fallback by {@code FallbackPosterProvider}).</li>
 * </ul>
 *
 * <p>The {@code NOT_CONFIGURED} row is covered separately:
 * {@link GenerateAlterEgoGeminiNotConfiguredIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gemini")
class GenerateAlterEgoGeminiCharacterFailureIT {

    private static final String IMAGE_MODEL_ID = "gemini-test-image-model";
    private static final String TEXT_MODEL_ID = "gemini-test-text-model";
    private static final String TEXT_PATH = "/v1beta/models/" + TEXT_MODEL_ID + ":generateContent";
    private static final String IMAGE_PATH = "/v1beta/models/" + IMAGE_MODEL_ID + ":generateContent";

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
    void registerImageHappyPathBaseline() throws Exception {
        wireMock.resetAll();
        // Every row of this matrix isolates a TEXT-side fault. The image
        // path is held to a happy-path baseline so failures can only be
        // attributed to the text endpoint.
        String posterBase64 = Base64.getEncoder().encodeToString(renderPng(8, 8));
        String imageBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(IMAGE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(imageBody)));
    }

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.gemini.api-key", () -> "test-integration-key");
        registry.add("aiavatar.gemini.endpoint-url", () -> wireMock.baseUrl() + "/v1beta");
        registry.add("aiavatar.gemini.model-id", () -> IMAGE_MODEL_ID);
        registry.add("aiavatar.gemini.text-model-id", () -> TEXT_MODEL_ID);
        registry.add("aiavatar.gemini.request-timeout-ms", () -> "5000");
        // Short text timeout so the TIMEOUT row resolves quickly under the
        // RetryTemplate's 5-attempt fan-out.
        registry.add("aiavatar.gemini.text-request-timeout-ms", () -> "500");
    }

    // ── Network-level faults ───────────────────────────────────────────────

    @Test
    void connectionResetMapsToNetworkError() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
        assertFallbackWithReason(FallbackReason.NETWORK_ERROR);
    }

    @Test
    void http503MapsToNetworkError() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(503).withBody("")));
        assertFallbackWithReason(FallbackReason.NETWORK_ERROR);
    }

    @Test
    void http500MapsToNetworkError() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(500).withBody("")));
        assertFallbackWithReason(FallbackReason.NETWORK_ERROR);
    }

    @Test
    void http429MapsToRateLimited() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Retry-After", "30").withBody("")));
        assertFallbackWithReason(FallbackReason.RATE_LIMITED);
    }

    @Test
    void fixedDelayExceedingTimeoutMapsToTimeout() {
        // The text-request-timeout-ms is 500; a 2 s WireMock delay forces
        // every retry attempt to time out.
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200).withFixedDelay(2_000)
                        .withBody(GeminiTextWireMockStubs.envelopeWrappingText(
                                GeminiTextWireMockStubs.HAPPY_PATH_TRAIT_JSON))));
        assertFallbackWithReason(FallbackReason.TIMEOUT);
    }

    @Test
    void http504MapsToTimeout() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(504).withBody("")));
        assertFallbackWithReason(FallbackReason.TIMEOUT);
    }

    @Test
    void http400MapsToMalformedResponse() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(400).withBody("")));
        assertFallbackWithReason(FallbackReason.MALFORMED_RESPONSE);
    }

    // ── Body-level faults — wrong outer envelope ───────────────────────────

    @Test
    void nonJsonOuterBodyMapsToMalformedResponse() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200).withBody("not-even-json")));
        assertFallbackWithReason(FallbackReason.MALFORMED_RESPONSE);
    }

    @Test
    void textPartIsNotJsonMapsToMalformedResponse() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWrappingText("plain prose, no JSON"))));
        assertFallbackWithReason(FallbackReason.MALFORMED_RESPONSE);
    }

    // ── Body-level faults — parser-level shape violations (also exercised by US3) ─

    @Test
    void missingHeroTitleLine2MapsToMalformedResponse() {
        String json = "{\"tagline\":\"x\",\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWrappingText(json))));
        assertFallbackWithReason(FallbackReason.MALFORMED_RESPONSE);
    }

    @Test
    void wrongSuperpowerCountMapsToMalformedResponse() {
        String json = "{\"heroTitleLine2\":\"x\",\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\",\"d\"],\"quote\":\"q\"}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWrappingText(json))));
        assertFallbackWithReason(FallbackReason.MALFORMED_RESPONSE);
    }

    @Test
    void overlongTraitMapsToMalformedResponse() {
        // A 105-codepoint tagline — exceeds the 100-codepoint cap.
        String oversized = "x".repeat(105);
        String json = "{\"heroTitleLine2\":\"y\",\"tagline\":\"" + oversized + "\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWrappingText(json))));
        assertFallbackWithReason(FallbackReason.MALFORMED_RESPONSE);
    }

    @Test
    void blankTraitAfterTrimMapsToMalformedResponse() {
        String json = "{\"heroTitleLine2\":\"y\",\"tagline\":\"x\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"   \"}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWrappingText(json))));
        assertFallbackWithReason(FallbackReason.MALFORMED_RESPONSE);
    }

    // ── Safety refusals ────────────────────────────────────────────────────

    @Test
    void promptFeedbackBlockReasonMapsToSafetyRefused() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWithBlockReason("SAFETY"))));
        assertFallbackWithReason(FallbackReason.SAFETY_REFUSED);
    }

    @Test
    void candidateFinishReasonSafetyMapsToSafetyRefused() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWithFinishReason("SAFETY"))));
        assertFallbackWithReason(FallbackReason.SAFETY_REFUSED);
    }

    @Test
    void candidateFinishReasonRecitationMapsToSafetyRefused() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo(TEXT_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWithFinishReason("RECITATION"))));
        assertFallbackWithReason(FallbackReason.SAFETY_REFUSED);
    }

    private void assertFallbackWithReason(FallbackReason expected) {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        assertEquals(expected, body.meta().reason(),
                "expected meta.reason=" + expected.wire() + " for the injected text-side fault");
        // FR-1404 / FR-1408 — the fallback character preserves the user's first name.
        assertEquals("PAULA", body.character().heroTitleLine1());
        // FR-1402 — the fallback poster is still served (data URL non-null, valid).
        assertNotNull(body.poster().dataUrl());
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

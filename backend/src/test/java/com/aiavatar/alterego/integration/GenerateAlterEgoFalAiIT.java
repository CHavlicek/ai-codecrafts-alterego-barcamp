package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 016 T028 — Integration test for the happy fal.ai path. Boots the real
 * Spring context under {@code falai} profile, points
 * {@code aiavatar.falai.*} at a WireMock stand-in, and verifies:
 *
 * <ul>
 *   <li>200 OK with {@code meta.outcome == "real"}, {@code meta.provider == "falai"},
 *       no {@code reason}.</li>
 *   <li>The submit POST body carries the expected fal.ai shape (data URL
 *       in {@code image_urls[0]}, prompt, num_images=1, aspect_ratio=3:4).</li>
 *   <li>WireMock receives the queue-protocol sequence: 1 POST submit,
 *       at least 1 GET status, 1 GET result, 1 GET image-bytes (CDN URL).</li>
 *   <li>Poster {@code dataUrl} starts {@code data:image/...} (the framed
 *       PNG canvas re-encodes whatever fal.ai returned).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("falai")
class GenerateAlterEgoFalAiIT {

    private static final String MODEL_ID = "fal-ai/test-model/edit";
    private static final String REQUEST_ID = "rid-falai-it";

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
    void resetStubs() {
        wireMock.resetAll();
    }

    @DynamicPropertySource
    static void falAiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.falai.api-key", () -> "test-integration-key");
        registry.add("aiavatar.falai.endpoint-url", () -> wireMock.baseUrl());
        registry.add("aiavatar.falai.model-id", () -> MODEL_ID);
        // Keep timeouts tight so misconfigured stubs don't drag the suite.
        registry.add("aiavatar.falai.submit-timeout-ms", () -> "5000");
        registry.add("aiavatar.falai.poll-timeout-ms", () -> "5000");
        registry.add("aiavatar.falai.fetch-timeout-ms", () -> "5000");
        registry.add("aiavatar.falai.end-to-end-timeout-ms", () -> "20000");
        // Sub-millisecond poll back-off so the test doesn't sleep waiting
        // for the IN_PROGRESS → COMPLETED transition.
        registry.add("aiavatar.falai.poll-initial-interval-ms", () -> "1");
        registry.add("aiavatar.falai.poll-max-interval-ms", () -> "1");
    }

    @Test
    void happyPathReturns200WithRealOutcomeAndProviderFalai() throws Exception {
        String statusUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status";
        String responseUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID;
        String cdnImageUrl = wireMock.baseUrl() + "/cdn/result.jpg";

        // 1. Submit POST → IN_QUEUE
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"request_id\":\"" + REQUEST_ID + "\","
                                + "\"status_url\":\"" + statusUrl + "\","
                                + "\"response_url\":\"" + responseUrl + "\","
                                + "\"status\":\"IN_QUEUE\"}")));

        // 2. Status GET — first IN_PROGRESS, then COMPLETED. WireMock
        //    ordered scenarios cover this.
        String scenario = "falai-status-progression";
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status"))
                .inScenario(scenario)
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willSetStateTo("seen-progress")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"IN_PROGRESS\",\"queue_position\":0}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status"))
                .inScenario(scenario)
                .whenScenarioStateIs("seen-progress")
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"COMPLETED\"}")));

        // 3. Result GET — points at the fake CDN URL on the same WireMock host.
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/" + MODEL_ID + "/requests/" + REQUEST_ID))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"images\":[{\"url\":\"" + cdnImageUrl
                                + "\",\"width\":64,\"height\":64,\"content_type\":\"image/jpeg\"}]}")));

        // 4. CDN GET → tiny JPEG bytes
        byte[] jpeg = renderJpeg(64, 64);
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/cdn/result.jpg"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "image/jpeg")
                        .withBody(jpeg)));

        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.REAL, body.meta().outcome());
        assertEquals(Provider.FALAI, body.meta().provider());
        assertNull(body.meta().reason(), "real outcome MUST carry null reason");
        assertNotNull(body.meta().correlationId());

        // 015: every poster — fal.ai or otherwise — is composited into the
        // 768×1152 framed canvas. The poster is therefore a PNG regardless
        // of the underlying provider's content type (FR-1509).
        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"));
        assertEquals(768, body.poster().widthPx());
        assertEquals(1152, body.poster().heightPx());

        // Verify exactly the expected outbound calls happened.
        wireMock.verify(1, postRequestedFor(urlPathEqualTo("/" + MODEL_ID)));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/" + MODEL_ID + "/requests/" + REQUEST_ID)));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/cdn/result.jpg")));

        // Submit body MUST carry the fal.ai shape — image_urls as data URL,
        // num_images=1, aspect_ratio=3:4. (FR-1606 / R3.)
        List<LoggedRequest> submits = wireMock.findAll(
                postRequestedFor(urlPathEqualTo("/" + MODEL_ID)));
        assertEquals(1, submits.size());
        String submitBody = submits.get(0).getBodyAsString();
        assertTrue(submitBody.contains("\"image_urls\":[\"data:image/jpeg;base64,"),
                "submit body MUST inline the photo as a data URL; got: " + submitBody);
        assertTrue(submitBody.contains("\"num_images\":1"));
        assertTrue(submitBody.contains("\"aspect_ratio\":\"3:4\""));
        // Authorization header MUST carry the fal.ai 'Key <api-key>' form.
        String authHeader = submits.get(0).getHeader("Authorization");
        assertEquals("Key test-integration-key", authHeader);
        // FR-1611: API key MUST NOT leak to the response body that goes
        // back to the browser.
        assertFalse(body.toString().contains("test-integration-key"),
                "FR-1611: API key must not leak into response body");
    }

    private static byte[] renderJpeg(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "JPEG", baos);
            return baos.toByteArray();
        }
    }

    @SuppressWarnings("unused")
    private static RequestPatternBuilder postRequestedForPattern(String url) {
        return postRequestedFor(urlPathEqualTo(url));
    }
}

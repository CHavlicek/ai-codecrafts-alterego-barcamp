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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 016 T039 — fault-injection matrix for the fal.ai path (FR-1614 / FR-1615 /
 * SC-1606). Each row injects a distinct provider-boundary failure via
 * WireMock and asserts:
 *
 * <ul>
 *   <li>HTTP 200 (FR-018: user always gets a poster).</li>
 *   <li>{@code meta.outcome == "fallback"}, {@code meta.provider == "stub"}.</li>
 *   <li>{@code meta.reason} matches the injected fault per FR-1615 mapping.</li>
 *   <li>The poster + character are fully populated from
 *       {@code FallbackPosterProvider}.</li>
 * </ul>
 *
 * <p>The "no fal.ai key" branch lives in a sibling test class with its own
 * Spring context (no key configured), to avoid context-cache pollution
 * across tests that assume a configured key.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("falai")
class GenerateAlterEgoFalAiFailureIT {

    private static final String MODEL_ID = "fal-ai/test-model/edit";
    private static final String REQUEST_ID = "rid-fail-it";

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
        registry.add("aiavatar.falai.submit-timeout-ms", () -> "1500");
        registry.add("aiavatar.falai.poll-timeout-ms", () -> "1500");
        registry.add("aiavatar.falai.fetch-timeout-ms", () -> "1500");
        // Keep end-to-end short so the CI-friendly variant of the timeout
        // case completes in a few seconds.
        registry.add("aiavatar.falai.end-to-end-timeout-ms", () -> "3000");
        registry.add("aiavatar.falai.poll-initial-interval-ms", () -> "1");
        registry.add("aiavatar.falai.poll-max-interval-ms", () -> "1");
    }

    // ----- a) network error on submit -----

    @Test
    @DisplayName("submit CONNECTION_RESET → outcome=fallback reason=network_error")
    void submitConnectionResetMapsToNetworkError() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

        assertFallback(FallbackReason.NETWORK_ERROR);
    }

    // ----- b) HTTP 5xx on submit -----

    @Test
    @DisplayName("submit HTTP 503 (after retries) → outcome=fallback reason=network_error")
    void submitHttp503MapsToNetworkError() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse().withStatus(503)));

        assertFallback(FallbackReason.NETWORK_ERROR);
    }

    // ----- c) HTTP 429 on submit -----

    @Test
    @DisplayName("submit HTTP 429 → outcome=fallback reason=rate_limited")
    void submit429MapsToRateLimited() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse().withStatus(429)
                        .withHeader("Retry-After", "30")
                        .withBody("{\"error\":\"too many requests\"}")));

        assertFallback(FallbackReason.RATE_LIMITED);
    }

    // ----- d) submit per-attempt timeout -----

    @Test
    @DisplayName("submit fixed-delay > timeout → outcome=fallback reason=timeout")
    void submitFixedDelayExceedsTimeoutMapsToTimeout() {
        // Delay (3500 ms) longer than per-attempt timeout (1500 ms) AND
        // shorter than end-to-end (3000 ms) — but multiple in-budget retries
        // each hit the same delay so the deadline trips on the second try.
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse().withFixedDelay(3500).withStatus(200)
                        .withBody("won't matter")));

        assertFallback(FallbackReason.TIMEOUT);
    }

    // ----- e) queue stall (poll always returns IN_PROGRESS) -----

    @Test
    @DisplayName("queue stall (always IN_PROGRESS) → outcome=fallback reason=timeout")
    void queueStallMapsToTimeout() {
        // Submit succeeds; poll never reaches a terminal state so the
        // end-to-end deadline (3000 ms) trips inside the poll loop.
        String statusUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status";
        String responseUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID;
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"request_id\":\"" + REQUEST_ID + "\","
                                + "\"status_url\":\"" + statusUrl + "\","
                                + "\"response_url\":\"" + responseUrl + "\","
                                + "\"status\":\"IN_QUEUE\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"IN_PROGRESS\"}")));

        assertFallback(FallbackReason.TIMEOUT);
    }

    // ----- f) malformed submit body -----

    @Test
    @DisplayName("submit 200 with garbage body → outcome=fallback reason=malformed_response")
    void submit200WithGarbageMapsToMalformedResponse() {
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"garbage\":true}")));

        assertFallback(FallbackReason.MALFORMED_RESPONSE);
    }

    // ----- g) poll returns FAILED with safety hint -----

    @Test
    @DisplayName("poll FAILED with safety detail → outcome=fallback reason=safety_refused")
    void pollFailedWithSafetyDetailMapsToSafetyRefused() {
        String statusUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status";
        String responseUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID;
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/" + MODEL_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"request_id\":\"" + REQUEST_ID + "\","
                                + "\"status_url\":\"" + statusUrl + "\","
                                + "\"response_url\":\"" + responseUrl + "\","
                                + "\"status\":\"IN_QUEUE\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"FAILED\",\"detail\":\"request blocked: nsfw\"}")));

        assertFallback(FallbackReason.SAFETY_REFUSED);
    }

    private void assertFallback(FallbackReason expectedReason) {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value(),
                "FR-018: fallback path MUST still return 200");
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        assertEquals(Provider.STUB, body.meta().provider(),
                "016 FR-1612: every fallback response carries provider=STUB");
        assertEquals(expectedReason, body.meta().reason(),
                "FR-1615 mapping: expected " + expectedReason + " on this fault");
        // Complete poster from FallbackPosterProvider.
        assertEquals("PAULA", body.character().heroTitleLine1());
        assertEquals("The Resilient", body.character().heroTitleLine2());
        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"));
        // 015 frame: 768×1152, 2:3 portrait.
        assertEquals(768, body.poster().widthPx());
        assertEquals(1152, body.poster().heightPx());
    }
}

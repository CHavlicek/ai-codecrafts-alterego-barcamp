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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 016 T040 / SC-1611 — proves the 30 s end-to-end wall-clock cap fires
 * deterministically (FR-1614a). Uses a {@link Clock} stub the test
 * advances past {@code endToEndTimeoutMs} on its second call so the
 * deadline check trips inside the poll loop on the first poll attempt.
 *
 * <p>This is the CI-friendly variant of SC-1611 — no real 30-second sleep.
 * The {@code @Tag("slow")} variant (a real-clock smoke test that actually
 * waits 30 s) is deferred to a nightly job.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {com.aiavatar.alterego.AlterEgoApplication.class,
                FalAiTimeoutBudgetIT.AdvancingClockConfig.class}
)
@ActiveProfiles("falai")
class FalAiTimeoutBudgetIT {

    private static final String MODEL_ID = "fal-ai/test-model/edit";
    private static final String REQUEST_ID = "rid-timeout-it";

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
        AdvancingClockConfig.reset();
    }

    @DynamicPropertySource
    static void falAiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.falai.api-key", () -> "test-integration-key");
        registry.add("aiavatar.falai.endpoint-url", () -> wireMock.baseUrl());
        registry.add("aiavatar.falai.model-id", () -> MODEL_ID);
        registry.add("aiavatar.falai.submit-timeout-ms", () -> "5000");
        registry.add("aiavatar.falai.poll-timeout-ms", () -> "5000");
        registry.add("aiavatar.falai.fetch-timeout-ms", () -> "5000");
        registry.add("aiavatar.falai.end-to-end-timeout-ms", () -> "30000");
        registry.add("aiavatar.falai.poll-initial-interval-ms", () -> "1");
        registry.add("aiavatar.falai.poll-max-interval-ms", () -> "1");
    }

    @Test
    void deadlineOverrunInPollLoopFiresTimeoutFallback() {
        // Submit succeeds; poll returns IN_PROGRESS forever. The advancing
        // clock jumps past the 30 s budget on its second tick so the
        // FalAiClient deadline check fires deterministically without a real
        // wall-clock wait.
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

        long wallClockStart = System.currentTimeMillis();
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);
        long elapsedMs = System.currentTimeMillis() - wallClockStart;

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        // FR-1614a: deadline overrun → outcome=fallback, reason=timeout, provider=stub.
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        assertEquals(FallbackReason.TIMEOUT, body.meta().reason(),
                "FR-1614a: end-to-end overrun MUST yield reason=timeout");
        assertEquals(Provider.STUB, body.meta().provider());
        // SC-1611 (CI-friendly variant): the actual wall-clock time is far
        // shorter than 30 s because the clock stub jumps the budget. We
        // assert "much less than 30 s" rather than "≤ 31 s" — the slow
        // variant covers the literal wall-clock guarantee.
        assertTrue(elapsedMs < 10_000,
                "deadline-stub variant should complete well under 10s; took " + elapsedMs + "ms");
    }

    /**
     * Replaces the production {@link Clock} bean with one that advances by
     * 60 seconds on its second {@code instant()} call — far past the 30 s
     * end-to-end budget. The first call (FalAiImageGenerator's
     * {@code clock.instant().plusMillis(...)}) sees the base time so the
     * deadline is constructed correctly; subsequent calls inside the poll
     * loop see the jumped time, tripping the budget check.
     */
    @TestConfiguration
    static class AdvancingClockConfig {
        private static final Instant BASE = Instant.parse("2026-05-06T10:00:00Z");
        private static final AtomicReference<Integer> CALL_COUNT = new AtomicReference<>(0);

        static void reset() { CALL_COUNT.set(0); }

        @Bean
        @Primary
        Clock advancingClock() {
            return new Clock() {
                @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() {
                    int call = CALL_COUNT.updateAndGet(i -> i + 1);
                    // First call: base time (deadline = BASE + 30s).
                    // Subsequent calls: BASE + 60s, well past the deadline.
                    return call == 1 ? BASE : BASE.plusSeconds(60);
                }
            };
        }
    }
}

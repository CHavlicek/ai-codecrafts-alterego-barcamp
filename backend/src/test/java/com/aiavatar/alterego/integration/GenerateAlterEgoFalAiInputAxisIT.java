package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.domain.policy.RandomCategorySelector;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.random.RandomGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 016 T035 / US2 / FR-1607 — proves that every Setup-form axis (archetype,
 * universe, pose, art-style, vibe-present-vs-absent) reaches fal.ai as a
 * distinct prompt token. The model-side perceptual A/B is left to the
 * SC-1602 manual walkthrough; this IT pins the wire-level invariant that
 * all axes are actually serialised onto the outbound submit body.
 *
 * <p>The fixture fully stubs the queue: submit → COMPLETED → result → CDN.
 * After each Generate, it captures the submit-request body via WireMock's
 * {@link com.github.tomakehurst.wiremock.verification.LoggedRequest} log
 * and asserts pair-wise prompt differences along each axis.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {com.aiavatar.alterego.AlterEgoApplication.class,
                   GenerateAlterEgoFalAiInputAxisIT.DeterministicRandomConfig.class})
@ActiveProfiles("falai")
class GenerateAlterEgoFalAiInputAxisIT {

    /**
     * 020 — Pin {@link RandomCategorySelector} to a fixed index so the rolled
     * Pose / Vibe values are identical across every request inside this IT.
     * Without this, axis-only delta assertions race the random pick.
     */
    @TestConfiguration
    static class DeterministicRandomConfig {
        @Bean
        @Primary
        RandomCategorySelector deterministicSelector() {
            return new RandomCategorySelector(new RandomGenerator() {
                @Override public long nextLong() { return 0L; }
                @Override public int nextInt(int bound) { return 0; }
            });
        }
    }

    private static final String MODEL_ID = "fal-ai/test-model/edit";
    private static final String REQUEST_ID = "rid-axis-it";

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
    void resetAndStubHappyPath() throws IOException {
        wireMock.resetAll();

        String statusUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID + "/status";
        String responseUrl = wireMock.baseUrl() + "/" + MODEL_ID + "/requests/" + REQUEST_ID;
        String cdnUrl = wireMock.baseUrl() + "/cdn/img.jpg";

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
                        .withBody("{\"status\":\"COMPLETED\"}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/" + MODEL_ID + "/requests/" + REQUEST_ID))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"images\":[{\"url\":\"" + cdnUrl
                                + "\",\"width\":64,\"height\":64,\"content_type\":\"image/jpeg\"}]}")));
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/cdn/img.jpg"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "image/jpeg")
                        .withBody(renderTinyJpeg())));
    }

    @DynamicPropertySource
    static void falAiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.falai.api-key", () -> "test-integration-key");
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
    void archetypeAxisProducesDistinctPrompts() {
        // 021 (issue #54): inverted from 017 (refined). The role IS back
        // in the fal.ai image prompt as a visual scene direction. A
        // role-only delta MUST therefore alter the outbound prompt, and
        // each prompt MUST contain its own Prompt label (per
        // specs/021-engineer-role-prompt/data-model.md).
        AlterEgoRequest a = req(Archetype.DATA_ANALYST, Universe.STAR_WARS,
                Pose.HEROIC, ArtStyle.OIL_PAINTING, null);
        AlterEgoRequest b = req(Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Pose.HEROIC, ArtStyle.OIL_PAINTING, null);

        String promptA = capturePrompt(a);
        wireMock.resetRequests();
        String promptB = capturePrompt(b);

        assertThat(promptA).contains("Data Analyst");
        assertThat(promptB).contains("Software Developer");
        assertThat(promptA).isNotEqualTo(promptB);
    }

    @Test
    void universeAxisProducesDistinctPrompts() {
        AlterEgoRequest a = req(Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Pose.HEROIC, ArtStyle.OIL_PAINTING, null);
        AlterEgoRequest b = req(Archetype.SOFTWARE_DEVELOPER, Universe.RETRO_SYNTHWAVE,
                Pose.HEROIC, ArtStyle.OIL_PAINTING, null);

        String promptA = capturePrompt(a);
        wireMock.resetRequests();
        String promptB = capturePrompt(b);

        assertThat(promptA).contains("Star Wars");
        assertThat(promptB).contains("synthwave");
        assertThat(promptA).isNotEqualTo(promptB);
    }

    // 020 — Pose is no longer a user-controllable axis (issue #51); server
    // rolls it per request. The pre-020 "poseAxisProducesDistinctPrompts"
    // test asserted client-controlled Pose deltas and is therefore no longer
    // meaningful — deleted on this branch.

    @Test
    void artStyleAxisProducesDistinctPrompts() {
        // 019 (closes #49): retired PIXEL_ART; use JAPANESE_WOODBLOCK as the
        // contrasting axis value — it stays distinct from oil-painting on the
        // prompt-substring axis and is a surviving member of the trimmed enum.
        AlterEgoRequest a = req(Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Pose.HEROIC, ArtStyle.JAPANESE_WOODBLOCK, null);
        AlterEgoRequest b = req(Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Pose.HEROIC, ArtStyle.OIL_PAINTING, null);

        String promptA = capturePrompt(a);
        wireMock.resetRequests();
        String promptB = capturePrompt(b);

        assertThat(promptA).contains("ukiyo-e");
        assertThat(promptB).contains("oil painting");
        assertThat(promptA).isNotEqualTo(promptB);
    }

    // 020 — Vibe is no longer a user-controllable axis (issue #51). The
    // pre-020 "vibePresentVsAbsentProducesDistinctPrompts" test compared a
    // client-supplied Vibe value against null; the server now always rolls a
    // non-null Vibe regardless of what the client sends, so the absent branch
    // is no longer reachable. Deleted on this branch.

    private String capturePrompt(AlterEgoRequest selections) {
        var response = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);
        assertEquals(200, response.getStatusCode().value());

        List<LoggedRequest> submits = wireMock.findAll(
                postRequestedFor(urlPathEqualTo("/" + MODEL_ID)));
        assertEquals(1, submits.size(),
                "expected exactly one submit per Generate call; got " + submits.size());
        String body = submits.get(0).getBodyAsString();
        // The prompt is the first JSON field in the submit body. Pull it
        // out via a simple substring match — robust to whitespace because
        // we control the serialiser (Jackson with default settings).
        int promptStart = body.indexOf("\"prompt\":\"");
        int promptEnd = body.indexOf("\",\"image_urls\"");
        assertThat(promptStart).withFailMessage("submit body missing prompt field: %s", body)
                .isGreaterThanOrEqualTo(0);
        assertThat(promptEnd).withFailMessage("submit body missing image_urls field: %s", body)
                .isGreaterThan(promptStart);
        return body.substring(promptStart + "\"prompt\":\"".length(), promptEnd);
    }

    private static AlterEgoRequest req(Archetype archetype, Universe universe,
                                       Pose pose, ArtStyle style, Vibe vibe) {
        return new AlterEgoRequest(pose, archetype, universe, vibe, style, "Paula", null);
    }

    private static byte[] renderTinyJpeg() throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "JPEG", baos);
            return baos.toByteArray();
        }
    }
}

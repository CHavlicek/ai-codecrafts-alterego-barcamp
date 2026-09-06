package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.domain.policy.RandomCategorySelector;
import com.aiavatar.alterego.testsupport.GeminiTextWireMockStubs;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
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
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.random.RandomGenerator;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * T031 — US2 input-coverage matrix. Varies one Setup axis at a time,
 * captures the outbound Gemini request body in both runs, and asserts the
 * axis-varied token differs while all other tokens match. Six axes:
 * role, universe, pose, vibe-present-vs-absent, firstName, photo. The
 * photo axis compares base64-encoded {@code inline_data.data}, closing the
 * request-side half of US2 acceptance scenario #4 (visual face-anchoring
 * remains manual, T054).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = {com.aiavatar.alterego.AlterEgoApplication.class,
                   GenerateAlterEgoGeminiInputCoverageIT.DeterministicRandomConfig.class})
@ActiveProfiles("gemini")
class GenerateAlterEgoGeminiInputCoverageIT {

    /**
     * 020 — Replaces the production {@link RandomCategorySelector} with a
     * fixed-index one so server-rolled {@code Pose} / {@code Vibe} values are
     * identical across every request inside this IT. Without this, the
     * "axis-only delta" assertions below would race the random pick and pass
     * for the wrong reason (or fail spuriously).
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

    private static final String IMAGE_MODEL_ID = "gemini-test-model";
    private static final String TEXT_MODEL_ID = "gemini-test-text-model";
    private static final String IMAGE_PATH = "/v1beta/models/" + IMAGE_MODEL_ID + ":generateContent";

    private static WireMockServer wireMock;

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private ObjectMapper mapper;

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
    void resetStubs() throws Exception {
        wireMock.resetAll();
        // Image-side: one uniform happy stub on the EXACT image-model URL —
        // differences are captured in the outbound request body, not in
        // Gemini's response.
        String posterBase64 = java.util.Base64.getEncoder().encodeToString(renderPng(8, 8));
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(IMAGE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(body)));
        // 014 — Text-side baseline so the new GeminiCharacterGenerator's
        // outbound call resolves successfully and doesn't drag the test
        // into a fallback path. The text URL is distinct from IMAGE_PATH,
        // so this stub doesn't compete with the image stub above.
        GeminiTextWireMockStubs.happyPath(wireMock, TEXT_MODEL_ID);
    }

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.gemini.api-key", () -> "test-integration-key");
        registry.add("aiavatar.gemini.endpoint-url", () -> wireMock.baseUrl() + "/v1beta");
        registry.add("aiavatar.gemini.model-id", () -> IMAGE_MODEL_ID);
        registry.add("aiavatar.gemini.text-model-id", () -> TEXT_MODEL_ID);
        registry.add("aiavatar.gemini.request-timeout-ms", () -> "5000");
        registry.add("aiavatar.gemini.text-request-timeout-ms", () -> "5000");
    }

    @Test
    void changingRoleChangesImagePromptSinceRoleIsBackInTheLoop() {
        // 021 (issue #54): inverted from 017 (refined). The engineering
        // role IS back in the IMAGE prompt — emitted as a visual scene
        // direction with an inline "NOT as text" clarifier and a
        // strengthened composition-note rule. A role-only delta MUST
        // therefore alter the outbound image prompt; pair-wise role
        // distinctness is SC-2103's automatable half.
        AlterEgoRequest a = new AlterEgoRequest(
                Pose.HEROIC, Archetype.BACKEND_DEV, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        AlterEgoRequest b = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        assertSingleAxisChangesPrompt(a, b, SamplePhotos.tinyJpeg(), SamplePhotos.tinyJpeg(),
                "archetype");
    }

    @Test
    void changingUniversePreservesAllOtherTokens() {
        AlterEgoRequest a = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.CYBERPUNK, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        AlterEgoRequest b = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.THE_OFFICE, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        assertSingleAxisChangesPrompt(a, b, SamplePhotos.tinyJpeg(), SamplePhotos.tinyJpeg(),
                "universe");
    }

    // 020 — Pose is no longer a user-controllable axis; the server rolls it
    // server-side per request. The pre-020 "changingPosePreservesAllOtherTokens"
    // test asserted Pose-only deltas in the *client* payload and is therefore
    // no longer meaningful — deleted on this branch (issue #51).

    @Test
    void changingArtStylePreservesAllOtherTokens() {
        // 006 T008 — varying only artStyle MUST alter the outbound Gemini
        // prompt text. Satisfies Principle III's "at least one integration
        // test per feature" gate for 006 and pins the end-to-end payload
        // round-trip for FR-305 / FR-307.
        AlterEgoRequest a = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        AlterEgoRequest b = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.JAPANESE_WOODBLOCK, "Paula", null);
        assertSingleAxisChangesPrompt(a, b, SamplePhotos.tinyJpeg(), SamplePhotos.tinyJpeg(),
                "artStyle");
    }

    // 020 — Vibe is no longer a user-controllable axis. The pre-020
    // "vibePresentVsAbsentChangesPrompt" test asserted client-controlled
    // Vibe presence/absence and is no longer meaningful — deleted on this
    // branch (issue #51). The server now always rolls a non-null Vibe.

    @Test
    void changingFirstNameNoLongerChangesImagePromptSinceFirstNameIsExcluded() {
        // 017 (refined 2026-05-08): firstName is no longer in the
        // IMAGE prompt — it was being rendered as decorative banner
        // text by the AI. (firstName still drives the character text
        // generator and the in-image overlay; just not the image AI.)
        AlterEgoRequest a = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        AlterEgoRequest b = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Maria", null);
        String bodyA = sendAndCaptureOutboundBody(a, SamplePhotos.tinyJpeg(), "image/jpeg");
        String bodyB = sendAndCaptureOutboundBody(b, SamplePhotos.tinyJpeg(), "image/jpeg");
        assertEquals(readPrompt(bodyA), readPrompt(bodyB),
                "017 (refined): firstName-only delta must NOT alter the image prompt");
    }

    @Test
    void differentPhotosProduceDifferentInlineDataBase64() {
        // US2 acceptance #4 (request-side): same selections, different
        // photos → different outbound base64 bytes. Visual face-anchoring
        // check remains manual per T054.
        AlterEgoRequest same = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        String bodyA = sendAndCaptureOutboundBody(same, SamplePhotos.tinyJpeg(), "image/jpeg");
        String bodyB = sendAndCaptureOutboundBody(same, SamplePhotos.tinyPng(),  "image/png");

        String dataA = readInlineData(bodyA);
        String dataB = readInlineData(bodyB);
        assertNotEquals(dataA, dataB,
                "different photos MUST produce different inline_data.data base64 values");

        // Prompts should be identical since all other axes match.
        assertEquals(readPrompt(bodyA), readPrompt(bodyB),
                "photo-only change must not alter the prompt text");
    }

    // --- helpers --------------------------------------------------------

    private void assertSingleAxisChangesPrompt(AlterEgoRequest a,
                                               AlterEgoRequest b,
                                               byte[] photoA,
                                               byte[] photoB,
                                               String axisLabel) {
        String bodyA = sendAndCaptureOutboundBody(a, photoA, "image/jpeg");
        String bodyB = sendAndCaptureOutboundBody(b, photoB, "image/jpeg");
        String promptA = readPrompt(bodyA);
        String promptB = readPrompt(bodyB);
        assertNotEquals(promptA, promptB,
                () -> "prompts MUST differ when axis " + axisLabel + " changes;\nA=\n"
                        + promptA + "\nB=\n" + promptB);
    }

    private String sendAndCaptureOutboundBody(AlterEgoRequest selections,
                                              byte[] photoBytes,
                                              String mime) {
        wireMock.resetRequests();  // only this call's outbound requests are captured
        restTemplate.exchange("/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(photoBytes, mime, selections),
                AlterEgoResponse.class);

        // 014 — Generate now triggers TWO outbound calls (text + image).
        // This IT inspects the IMAGE-side prompt body, so filter accordingly.
        List<ServeEvent> imageEvents = wireMock.getAllServeEvents().stream()
                .filter(ev -> IMAGE_PATH.equals(ev.getRequest().getUrl()))
                .toList();
        assertEquals(1, imageEvents.size(),
                () -> "expected exactly one image-side Gemini call per Generate, got " + imageEvents.size());
        return new String(imageEvents.get(0).getRequest().getBody(), java.nio.charset.StandardCharsets.UTF_8);
    }

    private String readInlineData(String outboundJson) {
        return readFromParts(outboundJson, p ->
                p.path("inline_data").path("data").asText(null));
    }

    private String readPrompt(String outboundJson) {
        return readFromParts(outboundJson, p -> p.path("text").asText(null));
    }

    private String readFromParts(String outboundJson, Function<JsonNode, String> pick) {
        try {
            JsonNode root = mapper.readTree(outboundJson);
            JsonNode parts = root.path("contents").get(0).path("parts");
            for (JsonNode part : parts) {
                String val = pick.apply(part);
                if (val != null && !val.isBlank()) return val;
            }
            return "";
        } catch (IOException e) {
            throw new RuntimeException("failed to parse outbound body: " + e.getMessage(), e);
        }
    }

    private static byte[] renderPng(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, new Color(x * 11 % 256, y * 7 % 256, (x + y) * 3 % 256).getRGB());
            }
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }
    }
}

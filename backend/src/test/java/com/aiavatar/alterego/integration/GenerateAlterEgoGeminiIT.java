package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.testsupport.GeminiTextWireMockStubs;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
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
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T024 — Integration test for the happy Gemini path. Boots the real Spring
 * context under {@code gemini} profile, points {@code aiavatar.gemini.*}
 * at a WireMock stand-in, and verifies:
 * <ul>
 *   <li>200 OK with {@code meta.outcome == "real"} and no {@code reason}.</li>
 *   <li>Poster is a valid data URL.</li>
 *   <li>Exactly one outbound request was made to Gemini with the expected
 *       URL shape + auth header.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gemini")
class GenerateAlterEgoGeminiIT {

    private static WireMockServer wireMock;

    @Autowired private TestRestTemplate restTemplate;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(0);  // random port
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @BeforeEach
    void resetStubsAndRegisterTextBaseline() {
        wireMock.resetAll();
        // 014 — under @ActiveProfiles("gemini") the new GeminiCharacterGenerator
        // is also active and will issue an outbound text call before the image
        // call. Register a happy-path text stub so this image-focused IT
        // continues to assert what it claims to assert (image-side outcomes).
        // The text stub uses urlPathEqualTo on the text-model URL — strictly
        // more specific than the image stubs' urlPathMatching(".*") pattern,
        // so WireMock picks the right stub for each endpoint.
        GeminiTextWireMockStubs.happyPath(wireMock, "gemini-test-text-model");
    }

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.gemini.api-key", () -> "test-integration-key");
        registry.add("aiavatar.gemini.endpoint-url", () -> wireMock.baseUrl() + "/v1beta");
        registry.add("aiavatar.gemini.model-id", () -> "gemini-test-model");
        registry.add("aiavatar.gemini.text-model-id", () -> "gemini-test-text-model");
        // Keep timeout short so tests don't block on accidental 30s waits.
        registry.add("aiavatar.gemini.request-timeout-ms", () -> "5000");
        registry.add("aiavatar.gemini.text-request-timeout-ms", () -> "5000");
    }

    @Test
    void happyPathReturns200WithRealOutcomeAndForwardsOneRequestToGemini() throws Exception {
        String posterBase64 = Base64.getEncoder().encodeToString(renderPng(16, 16));
        String geminiBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";

        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(geminiBody)));

        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.REAL, body.meta().outcome());
        assertEquals(Provider.GEMINI, body.meta().provider(),
                "016 FR-1612: real Gemini success MUST report provider=GEMINI");
        assertNull(body.meta().reason(), "real outcome MUST carry null reason");
        assertNotNull(body.meta().correlationId());

        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"));
        // 015: every poster is composited into the frame asset's canvas
        // (768×1152, 2:3 portrait, 10×15 print-ready) — the test fixture's
        // upstream Gemini stub returns a tiny 16×16 PNG; the framing step
        // upsizes it into the frame's inner rectangle and re-emits at the
        // canvas dimensions.
        assertEquals(768, body.poster().widthPx());
        assertEquals(1152, body.poster().heightPx());

        // Gemini hit exactly once.
        wireMock.verify(1, postRequestedFor(
                urlPathMatching("/v1beta/models/gemini-test-model:generateContent")));
    }

    @Test
    void happyPathSendsAuthHeaderAndExpectedModelInUrl() throws Exception {
        String posterBase64 = Base64.getEncoder().encodeToString(renderPng(8, 8));
        String geminiBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";

        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(geminiBody)));

        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.STEALTHY, Archetype.AI_ENGINEER, Universe.CYBERPUNK, null, ArtStyle.CEL_SHADED, "Maria", null);

        restTemplate.exchange("/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        wireMock.verify(postRequestedFor(
                urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .withHeader("x-goog-api-key", WireMock.equalTo("test-integration-key")));
    }

    /**
     * 021 (issue #54): the engineering role MUST influence the image-
     * generation prompt for every Archetype value. This parametrised
     * test posts a Generate request per archetype and verifies the
     * outbound request body to Gemini contains the archetype's Prompt
     * label (per specs/021-engineer-role-prompt/data-model.md). The
     * label is JSON-encoded directly into the {@code contents[0].parts[0].text}
     * field by {@code GeminiClient.renderRequestBody}, so a plain
     * {@code containing(...)} match on the request body is sufficient.
     */
    @ParameterizedTest
    @EnumSource(Archetype.class)
    void generatedPromptIncludesEachArchetypeLabel(Archetype archetype) throws Exception {
        String posterBase64 = Base64.getEncoder().encodeToString(renderPng(8, 8));
        String geminiBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";

        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(geminiBody)));

        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, archetype, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);

        restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        String expectedLabel = expectedPromptLabel(archetype);
        wireMock.verify(postRequestedFor(
                urlPathMatching("/v1beta/models/gemini-test-model:generateContent"))
                .withRequestBody(WireMock.containing(expectedLabel)));
    }

    private static String expectedPromptLabel(Archetype a) {
        return switch (a) {
            case CLOUD_ARCHITECT -> "Cloud Architect";
            case BACKEND_DEV -> "Backend Developer";
            case FRONTEND_DEV -> "Frontend Developer";
            case AI_ENGINEER -> "AI Engineer";
            case PLATFORM_ENG -> "Platform Engineer";
            case DATA_ENGINEER -> "Data Engineer";
            // 022 (issue #50) — three non-engineering prefab options. Same
            // expansion convention as 021 (UI label ≠ prompt label).
            case HR -> "Human Resources";
            case ADMINISTRATION -> "Administration / Operations";
            case CUSTOMER_RELATIONS -> "Customer Relations / Support";
        };
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

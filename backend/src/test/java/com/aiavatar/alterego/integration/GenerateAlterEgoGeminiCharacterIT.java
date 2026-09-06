package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.Pose;
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
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T010 — Integration test for the happy text-path under the {@code gemini}
 * profile. Boots the real Spring context, points {@code aiavatar.gemini.*}
 * at WireMock, registers stubs for both the image endpoint AND the new
 * text endpoint (014), and verifies:
 * <ul>
 *   <li>{@code meta.outcome == "real"} with no {@code reason}.</li>
 *   <li>The character traits in the response come from the stubbed
 *       {@code GeminiTextWireMockStubs.HAPPY_PATH_TRAIT_JSON} — NOT from
 *       {@code stubs/characters.json} (the deterministic fixture file).</li>
 *   <li>Exactly one outbound POST is made to the text endpoint with the
 *       expected URL shape and {@code x-goog-api-key} header.</li>
 * </ul>
 *
 * <p>The text and image endpoints are distinguished by URL path: the text
 * stub uses {@link com.github.tomakehurst.wiremock.client.WireMock#urlPathEqualTo}
 * for {@code /v1beta/models/gemini-test-text-model:generateContent}; the image
 * stub uses the broader {@code urlPathMatching} pattern (which still matches
 * the image's {@code gemini-test-image-model:generateContent} URL).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gemini")
class GenerateAlterEgoGeminiCharacterIT {

    private static final String IMAGE_MODEL_ID = "gemini-test-image-model";
    private static final String TEXT_MODEL_ID = "gemini-test-text-model";

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
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.gemini.api-key", () -> "test-integration-key");
        registry.add("aiavatar.gemini.endpoint-url", () -> wireMock.baseUrl() + "/v1beta");
        registry.add("aiavatar.gemini.model-id", () -> IMAGE_MODEL_ID);
        registry.add("aiavatar.gemini.text-model-id", () -> TEXT_MODEL_ID);
        registry.add("aiavatar.gemini.request-timeout-ms", () -> "5000");
        registry.add("aiavatar.gemini.text-request-timeout-ms", () -> "5000");
    }

    @Test
    void happyPathReturnsRealOutcomeWithLlmAuthoredCharacterText() throws Exception {
        // Image endpoint — happy path with a tiny PNG.
        String posterBase64 = Base64.getEncoder().encodeToString(renderPng(16, 16));
        String geminiImageBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/v1beta/models/" + IMAGE_MODEL_ID + ":generateContent"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(geminiImageBody)));

        // Text endpoint — canonical happy-path stub.
        GeminiTextWireMockStubs.happyPath(wireMock, TEXT_MODEL_ID);

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
        assertEquals(AlterEgoResponse.Outcome.REAL, body.meta().outcome());
        assertNull(body.meta().reason(), "real outcome MUST carry null reason");
        assertNotNull(body.meta().correlationId());

        GeneratedCharacter character = body.character();
        assertEquals("PAULA", character.heroTitleLine1(),
                "FR-1408: heroTitleLine1 MUST be the trimmed firstName uppercased");

        // The four LLM-authored traits MUST match the WireMock fixture and
        // MUST NOT match any line in the deterministic stubs/characters.json
        // file that StubCharacterGenerator would have produced.
        assertEquals("The Test-Forged Sentinel", character.heroTitleLine2());
        assertEquals("GUARDS THE GREEN BUILD.", character.tagline());
        assertEquals(3, character.superpowers().size());
        assertEquals("Pinpoints flakes from one stack frame", character.superpowers().get(0));
        assertEquals("The test that fails twice is the spec.", character.quote());

        // Defence-in-depth: line 2 MUST not be one of the cloud-architect
        // fixture variants ("The Cloud Guardrail", "The Multi-Region Mediator",
        // "The Terraform Tactician", "The Cost-Optimised Pragmatist").
        assertFalse(character.heroTitleLine2().equals("The Cloud Guardrail")
                || character.heroTitleLine2().equals("The Multi-Region Mediator")
                || character.heroTitleLine2().equals("The Terraform Tactician")
                || character.heroTitleLine2().equals("The Cost-Optimised Pragmatist"),
                "heroTitleLine2 MUST come from the LLM, not stubs/characters.json; got: "
                        + character.heroTitleLine2());

        // Poster contract preserved.
        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"));

        // Exactly one POST to each endpoint.
        wireMock.verify(1, postRequestedFor(
                urlPathEqualTo("/v1beta/models/" + TEXT_MODEL_ID + ":generateContent")));
        wireMock.verify(1, postRequestedFor(
                urlPathEqualTo("/v1beta/models/" + IMAGE_MODEL_ID + ":generateContent")));
    }

    @Test
    void happyPathTextCallSendsExpectedAuthHeaderAndModelInUrl() throws Exception {
        String posterBase64 = Base64.getEncoder().encodeToString(renderPng(8, 8));
        String geminiImageBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";
        wireMock.stubFor(WireMock.post(urlPathMatching("/v1beta/models/.*:generateContent"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(geminiImageBody)));
        GeminiTextWireMockStubs.happyPath(wireMock, TEXT_MODEL_ID);

        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.STEALTHY, Archetype.AI_ENGINEER, Universe.CYBERPUNK,
                null, ArtStyle.CEL_SHADED, "Maria", null);

        restTemplate.exchange("/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        wireMock.verify(postRequestedFor(
                urlPathEqualTo("/v1beta/models/" + TEXT_MODEL_ID + ":generateContent"))
                .withHeader("x-goog-api-key", WireMock.equalTo("test-integration-key")));
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

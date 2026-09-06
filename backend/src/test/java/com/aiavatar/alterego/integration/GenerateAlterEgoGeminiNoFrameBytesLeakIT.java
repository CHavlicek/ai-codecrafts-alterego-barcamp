package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.testsupport.GeminiTextWireMockStubs;
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
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 015 FR-1502 / FR-1505: the bundled poster frame asset MUST NOT be sent
 * to Gemini on any outbound request. The frame is composited locally
 * after the provider returns, never uploaded.
 *
 * <p>Replaces the deleted 008 {@code GenerateAlterEgoGeminiNoLogoLeakIT}
 * — the structural equivalent of "no branding bytes cross the wire" but
 * for the new single frame asset rather than the two old logos.
 *
 * <p>Verifies two channels: (a) the request body does NOT contain the
 * frame asset's base64 payload (which is what an image upload would look
 * like as a Gemini {@code inline_data.data} field), and (b) the request
 * body does NOT contain any of the frame asset's first 256 raw bytes as
 * a literal subsequence (defence in depth — covers any future code path
 * that might serialise the frame raw rather than base64).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gemini")
class GenerateAlterEgoGeminiNoFrameBytesLeakIT {

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
        GeminiTextWireMockStubs.happyPath(wireMock, "gemini-test-text-model");
    }

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("aiavatar.gemini.api-key", () -> "test-integration-key");
        registry.add("aiavatar.gemini.endpoint-url", () -> wireMock.baseUrl() + "/v1beta");
        registry.add("aiavatar.gemini.model-id", () -> "gemini-test-model");
        registry.add("aiavatar.gemini.text-model-id", () -> "gemini-test-text-model");
        registry.add("aiavatar.gemini.request-timeout-ms", () -> "5000");
        registry.add("aiavatar.gemini.text-request-timeout-ms", () -> "5000");
    }

    @Test
    void frameAssetBytesNeverAppearInOutboundGeminiRequest() throws Exception {
        // Stub Gemini to return a minimal 16×16 PNG so the run reaches the
        // overlay step. We don't care about the response shape here — only
        // about what we sent.
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

        // Load the frame asset and compute the two leak signatures.
        byte[] frameBytes;
        try (InputStream in = new ClassPathResource("branding/poster-frame.png").getInputStream()) {
            frameBytes = in.readAllBytes();
        }
        String frameBase64 = Base64.getEncoder().encodeToString(frameBytes);

        // Inspect every outbound request to the Gemini image endpoint.
        List<LoggedRequest> requests = wireMock.findAll(postRequestedFor(
                urlPathMatching("/v1beta/models/gemini-test-model:generateContent")));
        assertEquals(1, requests.size(),
                "expected exactly one outbound Gemini image-generation request");

        for (LoggedRequest req : requests) {
            String bodyAsText = req.getBodyAsString();
            assertFalse(bodyAsText.contains(frameBase64),
                    "FR-1502/1505: outbound request body MUST NOT contain the frame asset's base64 payload");

            // Defence in depth: check the first 256 raw bytes don't appear
            // as a literal sub-sequence in the request body bytes.
            byte[] needle = new byte[Math.min(256, frameBytes.length)];
            System.arraycopy(frameBytes, 0, needle, 0, needle.length);
            assertFalse(containsSubsequence(req.getBody(), needle),
                    "FR-1502/1505: outbound request body bytes MUST NOT contain the frame asset's raw byte prefix");

            // Also verify that the JSON body asks for 3:4.
            assertTrue(bodyAsText.contains("\"aspectRatio\":\"3:4\""),
                    "outbound request body MUST set generationConfig.imageConfig.aspectRatio = \"3:4\"");
        }
    }

    private static boolean containsSubsequence(byte[] haystack, byte[] needle) {
        if (needle.length == 0 || haystack.length < needle.length) return false;
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }

    private static byte[] renderPng(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        try {
            g.setColor(Color.MAGENTA);
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }
}

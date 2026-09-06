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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T033 — Log-redaction integration test for the new 014 text path
 * (FR-1413). Boots the gemini profile against WireMock, drives both a
 * happy-path text call AND a malformed-response failure, and asserts:
 *
 * <ul>
 *   <li>The user-supplied {@code firstName} appears in NEITHER outbound
 *       nor inbound log lines (the structured WARN events use only fixed
 *       vocabulary).</li>
 *   <li>The composed text prompt (no sentinel substring from the prompt
 *       template surfaces in any log line).</li>
 *   <li>The raw LLM response body — the canonical
 *       {@code GeminiTextWireMockStubs.HAPPY_PATH_TRAIT_JSON} fixture
 *       contents — does not appear anywhere in logs.</li>
 *   <li>The {@code phase=gemini-text-call} structured field IS present in
 *       the failure run (proves the WARN line landed without leaking the
 *       prompt or response).</li>
 *   <li>FR-1411 — exactly one {@code event=generation.completed} event per
 *       Generate run.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gemini")
@ExtendWith(OutputCaptureExtension.class)
class LogRedactionGeminiTextIT {

    private static final String IMAGE_MODEL_ID = "gemini-test-image-model";
    private static final String TEXT_MODEL_ID = "gemini-test-text-model";
    private static final String IMAGE_PATH = "/v1beta/models/" + IMAGE_MODEL_ID + ":generateContent";

    // A first name with a recognisable prefix so we can grep the captured
    // log output unambiguously.
    private static final String SENTINEL_FIRST_NAME = "Zorblax-Nirpa";

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
    void resetStubs() throws Exception {
        wireMock.resetAll();
        // Image-side: happy-path stub.
        String posterBase64 = Base64.getEncoder().encodeToString(renderPng(8, 8));
        String imageBody = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + posterBase64 + "\"}}]}}]}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo(IMAGE_PATH))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json").withBody(imageBody)));
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
    void happyPathDoesNotLeakFirstNameOrPromptOrResponseBody(CapturedOutput output) {
        GeminiTextWireMockStubs.happyPath(wireMock, TEXT_MODEL_ID);

        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, SENTINEL_FIRST_NAME, null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);
        assertEquals(200, response.getStatusCode().value());

        String all = output.getAll();

        // FR-1413 — the user's first name MUST NOT appear in any log line.
        // The fallback character path uppercases firstName into heroTitleLine1,
        // but that's a response field, not a log field — so this sentinel
        // (rare unicase tokens) should literally never appear in log output.
        assertFalse(all.contains(SENTINEL_FIRST_NAME),
                "FR-1413: firstName MUST NOT appear in logs; found at: "
                        + firstSnippet(all, SENTINEL_FIRST_NAME));
        assertFalse(all.contains(SENTINEL_FIRST_NAME.toUpperCase()),
                "FR-1413: uppercased firstName MUST NOT appear in logs either");

        // FR-1413 — the prompt body MUST NOT appear. The prompt template
        // contains a distinctive sentinel string we can grep for.
        assertFalse(all.contains("alter ego\" trading-card poster"),
                "FR-1413: composed text prompt MUST NOT be logged");

        // FR-1413 — the raw LLM response body MUST NOT appear. The canonical
        // happy-path fixture from GeminiTextWireMockStubs contains a
        // distinctive line we grep for.
        assertFalse(all.contains("The Test-Forged Sentinel"),
                "FR-1413: raw LLM response trait values MUST NOT be logged");
        assertFalse(all.contains("GUARDS THE GREEN BUILD."),
                "FR-1413: raw LLM response tagline MUST NOT be logged");

        // FR-1411 — exactly one structured generation.completed event per request.
        long completedCount = countOccurrences(all, "\"event\":\"generation.completed\"");
        assertEquals(1L, completedCount,
                "FR-1411: exactly one structured generation.completed event per request; got " + completedCount);
        assertTrue(all.contains("\"outcome\":\"real\""),
                "happy text+image run MUST log outcome=real");
    }

    @Test
    void textCallFailureLogsViolationLabelButNotResponseBody(CapturedOutput output) {
        // Stub the text endpoint with a body that violates FR-1406 — a 105-codepoint tagline.
        String oversized = "x".repeat(105);
        String malformedJson = "{\"heroTitleLine2\":\"y\",\"tagline\":\"" + oversized + "\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/v1beta/models/" + TEXT_MODEL_ID + ":generateContent"))
                .willReturn(aResponse().withStatus(200)
                        .withBody(GeminiTextWireMockStubs.envelopeWrappingText(malformedJson))));

        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, SENTINEL_FIRST_NAME, null);

        restTemplate.exchange("/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        String all = output.getAll();

        // The fixed-vocabulary violation label IS present.
        assertTrue(all.contains("\"phase\":\"gemini-text-parse\""),
                "Failure run MUST emit the gemini-text-parse phase WARN line");
        assertTrue(all.contains("\"violation\":\"trait_too_long:tagline\""),
                "Failure run MUST emit the fixed-vocabulary violation label");

        // The offending response body MUST NOT be logged — the parser logs
        // the violation label, never the value.
        assertFalse(all.contains(oversized),
                "FR-1413: the raw oversized trait value MUST NOT be logged");

        // FR-1411 — still exactly one generation.completed event (now with outcome=fallback).
        long completedCount = countOccurrences(all, "\"event\":\"generation.completed\"");
        assertEquals(1L, completedCount,
                "FR-1411: exactly one structured generation.completed event even on failure");
        assertTrue(all.contains("\"outcome\":\"fallback\"")
                && all.contains("\"reason\":\"malformed_response\""),
                "fallback log MUST carry outcome=fallback + reason=malformed_response");

        // FR-1413 — firstName still not in the log surface.
        assertFalse(all.contains(SENTINEL_FIRST_NAME),
                "FR-1413: firstName MUST NOT leak into logs even on failure");
    }

    private static long countOccurrences(String haystack, String needle) {
        long count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    private static String firstSnippet(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        if (idx < 0) return "<not present>";
        int from = Math.max(0, idx - 50);
        int to = Math.min(haystack.length(), idx + needle.length() + 50);
        return haystack.substring(from, to);
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

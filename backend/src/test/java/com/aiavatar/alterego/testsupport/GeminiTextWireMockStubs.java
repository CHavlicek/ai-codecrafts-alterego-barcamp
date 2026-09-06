package com.aiavatar.alterego.testsupport;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Shared WireMock stubs for the Gemini text endpoint. Used by every
 * {@code @ActiveProfiles("gemini")} integration test so the new
 * outbound character-text call (014) does not hit the real provider
 * when the test merely wants to exercise the image path or assert
 * non-text behaviour. Centralised so the canonical happy-path JSON
 * body lives in exactly one place across the test suite.
 *
 * <p>The text endpoint URL is exactly {@code {endpointUrl}/models/{textModelId}:generateContent}
 * — a more specific match than the image-side {@code urlPathMatching(".*:generateContent")}
 * pattern, so registering a text stub does not capture image calls and
 * vice versa.
 *
 * <p>Stateless. All methods are static.
 */
public final class GeminiTextWireMockStubs {

    /**
     * Canonical four-trait JSON the LLM is expected to emit. Each trait
     * is well-formed under FR-1406: ASCII, NFC-normalised, ≤ 100
     * Unicode code points, non-blank. Exactly three superpowers.
     * The values intentionally do NOT match any line in
     * {@code stubs/characters.json} so an integration test can assert
     * "the response came from the LLM, not the fixture file".
     */
    public static final String HAPPY_PATH_TRAIT_JSON =
            "{"
            + "\"heroTitleLine2\":\"The Test-Forged Sentinel\","
            + "\"tagline\":\"GUARDS THE GREEN BUILD.\","
            + "\"superpowers\":["
            +   "\"Pinpoints flakes from one stack frame\","
            +   "\"Reads CI logs in dimmed-terminal half-light\","
            +   "\"Rewrites assertions other people understand\""
            + "],"
            + "\"quote\":\"The test that fails twice is the spec.\""
            + "}";

    private GeminiTextWireMockStubs() {
        // utility
    }

    /**
     * Register a happy-path stub for the text endpoint at exactly
     * {@code /v1beta/models/{textModelId}:generateContent}. The response
     * is a Gemini envelope wrapping {@link #HAPPY_PATH_TRAIT_JSON} as
     * the {@code candidates[0].content.parts[0].text} field.
     *
     * <p>Call this from a test's {@code @BeforeEach} (or the start of
     * an individual test method) when the test wants the text path to
     * succeed silently. Pair with whatever image stub the test already
     * registers.
     */
    public static void happyPath(WireMockServer wm, String textModelId) {
        wm.stubFor(WireMock.post(urlPathEqualTo("/v1beta/models/" + textModelId + ":generateContent"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(envelopeWrappingText(HAPPY_PATH_TRAIT_JSON))));
    }

    /**
     * Wrap an arbitrary text payload as the {@code candidates[0].content.parts[0].text}
     * of a minimal Gemini envelope. Useful for failure-matrix tests that
     * want to drop in a malformed payload (e.g. non-JSON, missing key,
     * overlong trait) without rebuilding the envelope each time.
     */
    public static String envelopeWrappingText(String textPayload) {
        return "{\"candidates\":[{\"content\":{\"parts\":[{"
                + "\"text\":" + jsonString(textPayload)
                + "}]}}]}";
    }

    /**
     * Wrap a Gemini envelope that signals safety refusal at the
     * {@code candidates[0].finishReason} level. Used by the failure
     * matrix to drive {@code SAFETY_REFUSED}.
     */
    public static String envelopeWithFinishReason(String finishReason) {
        return "{\"candidates\":[{\"finishReason\":" + jsonString(finishReason) + ","
                + "\"content\":{\"parts\":[]}}]}";
    }

    /**
     * Wrap a Gemini envelope that signals safety refusal at the top-level
     * {@code promptFeedback.blockReason}. Drives {@code SAFETY_REFUSED}.
     */
    public static String envelopeWithBlockReason(String blockReason) {
        return "{\"promptFeedback\":{\"blockReason\":" + jsonString(blockReason) + "},"
                + "\"candidates\":[]}";
    }

    /** Minimal JSON-string escaper for the small set of payloads used here. */
    private static String jsonString(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}

package com.aiavatar.alterego.unit.gemini;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T007 — happy-path coverage for {@link GeminiCharacterClient} (014).
 *
 * <p>Asserts that the client:
 * <ul>
 *   <li>POSTs to {@code {endpointUrl}/models/{textModelId}:generateContent}.</li>
 *   <li>Sends the {@code x-goog-api-key} header from {@code GeminiProperties.apiKey()}.</li>
 *   <li>Sends {@code Content-Type: application/json}.</li>
 *   <li>Builds a request body with the single text part, {@code responseMimeType=application/json},
 *       a non-empty {@code responseSchema}, and {@code candidateCount=1} (research.md §R3).</li>
 *   <li>On 200 with a Gemini envelope wrapping a valid JSON text part, returns the
 *       embedded {@link JsonNode} (NOT the outer envelope).</li>
 * </ul>
 *
 * <p>The full failure-classification matrix lands with {@code GeminiCharacterClientTest}
 * extension in T016 / US2.
 */
class GeminiCharacterClientTest {

    private static final GeminiProperties DEFAULTS = new GeminiProperties(
            "test-key", "gemini-test-image-model", "http://gemini.test/v1beta",
            25_000, 4 * 1024 * 1024, 1536, 1024, 0.85,
            "gemini-2.5-flash", 15_000);

    private HttpClient httpClient;
    private ObjectMapper mapper;
    private GeminiCharacterClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        mapper = new ObjectMapper();
        client = new GeminiCharacterClient(httpClient, DEFAULTS, mapper);
    }

    @Test
    void happyPathPostsToExpectedUrlAndReturnsEmbeddedJson() throws Exception {
        String embedded = "{\"heroTitleLine2\":\"The Test-Forged Sentinel\","
                + "\"tagline\":\"GUARDS THE GREEN BUILD.\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],"
                + "\"quote\":\"q\"}";
        stubResponse(200, envelopeWrappingText(embedded));

        JsonNode result = client.generateText("gemini-2.5-flash", "build me a character");

        assertNotNull(result);
        assertEquals("The Test-Forged Sentinel", result.path("heroTitleLine2").asText());
        assertEquals("GUARDS THE GREEN BUILD.", result.path("tagline").asText());
        assertEquals(3, result.path("superpowers").size());
        assertEquals("q", result.path("quote").asText());

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient).send(reqCap.capture(), any());
        HttpRequest sent = reqCap.getValue();
        assertEquals("http://gemini.test/v1beta/models/gemini-2.5-flash:generateContent",
                sent.uri().toString());
        assertEquals("POST", sent.method());
        assertEquals("test-key", sent.headers().firstValue("x-goog-api-key").orElseThrow());
        assertEquals("application/json", sent.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void requestBodyContainsPromptTextAsSinglePart() throws Exception {
        stubResponse(200, envelopeWrappingText("{\"heroTitleLine2\":\"x\",\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}"));

        client.generateText("gemini-2.5-flash", "Compose a character please");

        HttpRequest sent = captureSentRequest();
        JsonNode body = mapper.readTree(bodyAsString(sent));
        JsonNode parts = body.path("contents").get(0).path("parts");
        assertEquals(1, parts.size(),
                "text-side request body MUST send exactly one part (no inline_data); got " + parts);
        assertEquals("Compose a character please", parts.get(0).path("text").asText());
    }

    @Test
    void requestBodyDeclaresJsonResponseMimeAndSchema() throws Exception {
        stubResponse(200, envelopeWrappingText("{\"heroTitleLine2\":\"x\",\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}"));

        client.generateText("gemini-2.5-flash", "p");

        JsonNode body = mapper.readTree(bodyAsString(captureSentRequest()));
        JsonNode genCfg = body.path("generationConfig");
        assertEquals("application/json", genCfg.path("responseMimeType").asText(),
                "responseMimeType MUST be application/json (research §R3)");
        assertTrue(genCfg.has("responseSchema"),
                "request body MUST declare a responseSchema");
        assertEquals(1, genCfg.path("candidateCount").asInt());
    }

    @Test
    void requestBodyResponseSchemaPinsTheFourTraitShape() throws Exception {
        stubResponse(200, envelopeWrappingText("{\"heroTitleLine2\":\"x\",\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}"));

        client.generateText("gemini-2.5-flash", "p");

        JsonNode schema = mapper.readTree(bodyAsString(captureSentRequest()))
                .path("generationConfig").path("responseSchema");
        // The schema asks for an OBJECT with the four traits; superpowers is a 3-element array.
        assertEquals("object", schema.path("type").asText().toLowerCase());
        JsonNode props = schema.path("properties");
        assertTrue(props.has("heroTitleLine2"));
        assertTrue(props.has("tagline"));
        assertTrue(props.has("superpowers"));
        assertTrue(props.has("quote"));

        JsonNode supers = props.path("superpowers");
        assertEquals(3, supers.path("minItems").asInt());
        assertEquals(3, supers.path("maxItems").asInt());
    }

    // ─── 014 / T016: full R5 failure-classification matrix ─────────────────

    @Test
    void timeoutExceptionMapsToTimeoutReason() throws Exception {
        org.mockito.Mockito.doThrow(new HttpTimeoutException("slow provider"))
                .when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.TIMEOUT, ex.reason());
    }

    @Test
    void connectExceptionMapsToNetworkError() throws Exception {
        org.mockito.Mockito.doThrow(new ConnectException("refused"))
                .when(httpClient).send(any(HttpRequest.class), any());
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void unknownHostExceptionMapsToNetworkError() throws Exception {
        org.mockito.Mockito.doThrow(new UnknownHostException("no such host"))
                .when(httpClient).send(any(HttpRequest.class), any());
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void sslExceptionMapsToNetworkError() throws Exception {
        org.mockito.Mockito.doThrow(new SSLException("handshake"))
                .when(httpClient).send(any(HttpRequest.class), any());
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void genericIOExceptionMapsToNetworkError() throws Exception {
        org.mockito.Mockito.doThrow(new IOException("connection refused"))
                .when(httpClient).send(any(HttpRequest.class), any());
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void http500MapsToNetworkError() throws Exception {
        stubResponse(500, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void http502MapsToNetworkError() throws Exception {
        stubResponse(502, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void http503MapsToNetworkError() throws Exception {
        stubResponse(503, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void http504MapsToTimeout() throws Exception {
        stubResponse(504, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.TIMEOUT, ex.reason(),
                "504 Gateway Timeout → TIMEOUT");
    }

    @Test
    void http429MapsToRateLimited() throws Exception {
        stubResponse(429, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.RATE_LIMITED, ex.reason());
    }

    @Test
    void http400MapsToMalformedResponse() throws Exception {
        stubResponse(400, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void http401MapsToMalformedResponse() throws Exception {
        stubResponse(401, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void http403MapsToMalformedResponse() throws Exception {
        stubResponse(403, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void http404MapsToMalformedResponse() throws Exception {
        stubResponse(404, "");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void nonJsonOuterBodyMapsToMalformedResponse() throws Exception {
        stubResponse(200, "not-even-json");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void envelopeWithoutCandidatesMapsToMalformedResponse() throws Exception {
        stubResponse(200, "{\"unrelated\":\"field\"}");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void envelopeWithEmptyCandidatesArrayMapsToMalformedResponse() throws Exception {
        stubResponse(200, "{\"candidates\":[]}");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void envelopeWithoutTextPartMapsToMalformedResponse() throws Exception {
        // candidates[0].content.parts has an inline_data (image-shaped) part
        // instead of a text part — wrong shape for the text endpoint.
        stubResponse(200, "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"inline_data\":{\"mime_type\":\"image/png\",\"data\":\"AAAA\"}}]}}]}");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void envelopeWhereTextPartIsNotJsonMapsToMalformedResponse() throws Exception {
        // The wrapping envelope is fine, but the embedded "text" field is
        // plain prose, not JSON — the parser tries to deserialise and fails.
        stubResponse(200, "{\"candidates\":[{\"content\":{\"parts\":[{"
                + "\"text\":\"plain prose, not JSON\"}]}}]}");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void promptFeedbackBlockReasonMapsToSafetyRefused() throws Exception {
        stubResponse(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},\"candidates\":[]}");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.SAFETY_REFUSED, ex.reason());
    }

    @Test
    void candidateFinishReasonSafetyMapsToSafetyRefused() throws Exception {
        stubResponse(200, "{\"candidates\":[{\"finishReason\":\"SAFETY\","
                + "\"content\":{\"parts\":[]}}]}");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.SAFETY_REFUSED, ex.reason());
    }

    @Test
    void candidateFinishReasonRecitationMapsToSafetyRefused() throws Exception {
        stubResponse(200, "{\"candidates\":[{\"finishReason\":\"RECITATION\","
                + "\"content\":{\"parts\":[]}}]}");
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateText("m", "p"));
        assertEquals(FallbackReason.SAFETY_REFUSED, ex.reason(),
                "Gemini's RECITATION finishReason maps to SAFETY_REFUSED (the closest "
                        + "existing FallbackReason; FR-1410 forbids enum extension).");
    }

    private void stubResponse(int status, String body) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> fake = mock(HttpResponse.class);
        when(fake.statusCode()).thenReturn(status);
        when(fake.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fake).when(httpClient).send(any(HttpRequest.class), any());
    }

    private HttpRequest captureSentRequest() throws Exception {
        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(cap.capture(), any());
        return cap.getValue();
    }

    private String bodyAsString(HttpRequest req) {
        java.util.concurrent.atomic.AtomicReference<String> capture = new java.util.concurrent.atomic.AtomicReference<>("");
        req.bodyPublisher().ifPresent(pub -> {
            StringBuilder sb = new StringBuilder();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            pub.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                @Override
                public void onSubscribe(java.util.concurrent.Flow.Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(java.nio.ByteBuffer item) {
                    byte[] b = new byte[item.remaining()];
                    item.get(b);
                    sb.append(new String(b, java.nio.charset.StandardCharsets.UTF_8));
                }

                @Override
                public void onError(Throwable throwable) {
                    latch.countDown();
                }

                @Override
                public void onComplete() {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            capture.set(sb.toString());
        });
        return capture.get();
    }

    private static String envelopeWrappingText(String text) {
        // Minimal Gemini envelope mirror of GeminiTextWireMockStubs.
        StringBuilder sb = new StringBuilder("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":");
        sb.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
        sb.append("}]}}]}");
        return sb.toString();
    }
}

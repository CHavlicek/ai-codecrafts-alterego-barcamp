package com.aiavatar.alterego.unit.gemini;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * T022 — happy-path coverage for {@link GeminiClient}: request-body shape,
 * URL, headers, response parsing, and the two exception-mapping cases
 * already wired in Phase 3 (timeout → TIMEOUT, parse failure →
 * MALFORMED_RESPONSE). The full mapping table lands with T035 / US3.
 */
class GeminiClientTest {

    private static final GeminiProperties DEFAULTS = new GeminiProperties(
            "test-key", "gemini-test-model", "http://gemini.test/v1beta",
            25_000, 4 * 1024 * 1024, 1536, 1024, 0.85,
            "gemini-2.5-flash", 15_000);

    private HttpClient httpClient;
    private ObjectMapper mapper;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        mapper = new ObjectMapper();
        client = new GeminiClient(httpClient, DEFAULTS, mapper);
    }

    @Test
    void happyPathPostsToExpectedUrlWithAuthHeaderAndParsesImage() throws Exception {
        byte[] tinyPng = renderPng(8, 8);
        String base64 = Base64.getEncoder().encodeToString(tinyPng);
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + base64 + "\"}}]}}]}";

        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());

        PhotoPayload photo = new PhotoPayload(new byte[]{1, 2, 3, 4}, "image/jpeg");
        PosterImage poster = client.generateImage("gemini-test-model", "test prompt", photo);

        assertNotNull(poster);
        assertEquals("image/png", poster.mediaType());
        assertEquals(8, poster.widthPx());
        assertEquals(8, poster.heightPx());
        assertEquals(tinyPng.length, poster.bytes().length);

        ArgumentCaptor<HttpRequest> reqCap = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(reqCap.capture(), any());
        HttpRequest sent = reqCap.getValue();
        assertEquals("http://gemini.test/v1beta/models/gemini-test-model:generateContent",
                sent.uri().toString());
        assertEquals("POST", sent.method());
        assertEquals("test-key", sent.headers().firstValue("x-goog-api-key").orElseThrow());
        assertEquals("application/json", sent.headers().firstValue("Content-Type").orElseThrow());
    }

    @Test
    void requestBodyContainsPromptTextAndBase64Photo() throws Exception {
        stubHappyResponseForAnyRequest();
        byte[] photoBytes = new byte[]{9, 8, 7, 6};
        PhotoPayload photo = new PhotoPayload(photoBytes, "image/jpeg");
        client.generateImage("m", "hello gemini", photo);

        HttpRequest sent = captureSentRequest();
        String body = bodyAsString(sent);
        JsonNode root = mapper.readTree(body);

        JsonNode parts = root.path("contents").get(0).path("parts");
        assertEquals("hello gemini", parts.get(0).path("text").asText());
        assertEquals("image/jpeg", parts.get(1).path("inline_data").path("mime_type").asText());
        assertEquals(Base64.getEncoder().encodeToString(photoBytes),
                parts.get(1).path("inline_data").path("data").asText());
    }

    @Test
    void requestBodyDeclaresImageResponseModalityAndSingleCandidate() throws Exception {
        stubHappyResponseForAnyRequest();
        client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png"));

        String body = bodyAsString(captureSentRequest());
        JsonNode root = mapper.readTree(body);
        assertEquals("IMAGE",
                root.path("generationConfig").path("responseModalities").get(0).asText());
        assertEquals(1, root.path("generationConfig").path("candidateCount").asInt());
    }

    @Test
    void timeoutExceptionMapsToTimeoutReason() throws Exception {
        org.mockito.Mockito.doThrow(new HttpTimeoutException("slow provider"))
                .when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.TIMEOUT, ex.reason());
    }

    @Test
    void genericIOExceptionMapsToNetworkErrorInPhase3() throws Exception {
        org.mockito.Mockito.doThrow(new IOException("connection refused"))
                .when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    // T035 — full HTTP-status → FallbackReason mapping table (research.md R5).
    // Phase-3 MVP lumped every 4xx/5xx into MALFORMED_RESPONSE; T042 refined
    // the mapping into five discrete buckets, asserted below.

    @Test
    void http500MapsToNetworkError() throws Exception {
        stubHttpStatus(500);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason(),
                "5xx (other than 504) → NETWORK_ERROR");
    }

    @Test
    void http502MapsToNetworkError() throws Exception {
        stubHttpStatus(502);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void http503MapsToNetworkError() throws Exception {
        stubHttpStatus(503);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void http504GatewayTimeoutMapsToTimeout() throws Exception {
        stubHttpStatus(504);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.TIMEOUT, ex.reason(),
                "504 Gateway Timeout → TIMEOUT");
    }

    @Test
    void http429MapsToRateLimited() throws Exception {
        stubHttpStatus(429);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.RATE_LIMITED, ex.reason());
    }

    @Test
    void http400MapsToMalformedResponse() throws Exception {
        stubHttpStatus(400);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason(),
                "4xx (other than 429) → MALFORMED_RESPONSE");
    }

    @Test
    void http401UnauthorisedMapsToMalformedResponse() throws Exception {
        stubHttpStatus(401);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void http403ForbiddenMapsToMalformedResponse() throws Exception {
        stubHttpStatus(403);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void http404NotFoundMapsToMalformedResponse() throws Exception {
        stubHttpStatus(404);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void connectExceptionMapsToNetworkError() throws Exception {
        org.mockito.Mockito.doThrow(new ConnectException("refused"))
                .when(httpClient).send(any(HttpRequest.class), any());
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void unknownHostExceptionMapsToNetworkError() throws Exception {
        org.mockito.Mockito.doThrow(new UnknownHostException("no such host"))
                .when(httpClient).send(any(HttpRequest.class), any());
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void sslExceptionMapsToNetworkError() throws Exception {
        org.mockito.Mockito.doThrow(new SSLException("handshake"))
                .when(httpClient).send(any(HttpRequest.class), any());
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
    }

    @Test
    void promptFeedbackBlockReasonMapsToSafetyRefused() throws Exception {
        String body = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"},"
                + "\"candidates\":[{\"content\":{\"parts\":[]}}]}";
        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.SAFETY_REFUSED, ex.reason());
    }

    @Test
    void candidateFinishReasonImageSafetyMapsToSafetyRefused() throws Exception {
        String body = "{\"candidates\":[{\"finishReason\":\"IMAGE_SAFETY\",\"content\":{\"parts\":[]}}]}";
        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.SAFETY_REFUSED, ex.reason());
    }

    @Test
    void candidateFinishReasonSafetyMapsToSafetyRefused() throws Exception {
        String body = "{\"candidates\":[{\"finishReason\":\"SAFETY\",\"content\":{\"parts\":[]}}]}";
        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.SAFETY_REFUSED, ex.reason());
    }

    private void stubHttpStatus(int status) throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(status);
        when(fakeResponse.body()).thenReturn("");
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());
    }

    @Test
    void malformedJsonResponseMapsToMalformedResponse() throws Exception {
        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn("not valid json {");
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void responseMissingInlineDataMapsToMalformedResponse() throws Exception {
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"no image here\"}]}}]}";
        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage("m", "p", new PhotoPayload(new byte[]{1}, "image/png")));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void responseWithCamelCaseInlineDataFieldStillParses() throws Exception {
        // Some Gemini API previews use `inlineData` (camelCase) instead of
        // `inline_data` — the parser handles both per R3.
        byte[] tinyPng = renderPng(4, 4);
        String base64 = Base64.getEncoder().encodeToString(tinyPng);
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{"
                + "\"mimeType\":\"image/png\",\"data\":\"" + base64 + "\"}}]}}]}";

        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());

        PosterImage poster = client.generateImage("m", "p",
                new PhotoPayload(new byte[]{1}, "image/png"));
        assertTrue(poster.bytes().length > 0);
    }

    private void stubHappyResponseForAnyRequest() throws Exception {
        byte[] tinyPng = renderPng(4, 4);
        String base64 = Base64.getEncoder().encodeToString(tinyPng);
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{"
                + "\"mime_type\":\"image/png\",\"data\":\"" + base64 + "\"}}]}}]}";
        @SuppressWarnings("unchecked")
        HttpResponse<String> fakeResponse = mock(HttpResponse.class);
        when(fakeResponse.statusCode()).thenReturn(200);
        when(fakeResponse.body()).thenReturn(body);
        org.mockito.Mockito.doReturn(fakeResponse).when(httpClient).send(any(HttpRequest.class), any());
    }

    private HttpRequest captureSentRequest() throws Exception {
        ArgumentCaptor<HttpRequest> cap = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(cap.capture(), any());
        return cap.getValue();
    }

    private String bodyAsString(HttpRequest req) throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> capture = new java.util.concurrent.atomic.AtomicReference<>();
        req.bodyPublisher().ifPresent(pub -> {
            StringBuilder sb = new StringBuilder();
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            pub.subscribe(new java.util.concurrent.Flow.Subscriber<>() {
                @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
                @Override public void onNext(java.nio.ByteBuffer item) {
                    byte[] b = new byte[item.remaining()];
                    item.get(b);
                    sb.append(new String(b, java.nio.charset.StandardCharsets.UTF_8));
                }
                @Override public void onError(Throwable throwable) { latch.countDown(); }
                @Override public void onComplete() { latch.countDown(); }
            });
            try { latch.await(); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            capture.set(sb.toString());
        });
        return capture.get();
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

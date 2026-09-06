package com.aiavatar.alterego.unit.falai;

import com.aiavatar.alterego.infrastructure.config.FalAiProperties;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.infrastructure.provider.falai.FalAiClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 016 T026 — exception-mapping + queue-protocol coverage for
 * {@link FalAiClient} (R3 / R5 / R8). Mocks the JDK {@link HttpClient}
 * directly so tests run without WireMock.
 *
 * <p>Time advances via a constructor-injected {@link Clock}; the deadline-
 * overrun branch is forced by passing a deadline that's already in the past.
 *
 * <p>Heads-up: the real client uses {@code Thread.sleep} for poll-loop
 * back-off. To keep tests fast we configure {@code pollInitialIntervalMs}
 * and {@code pollMaxIntervalMs} to {@code 1} ms in the fixture.
 */
class FalAiClientTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-06T10:00:00Z");

    private HttpClient httpClient;
    private FalAiClient client;
    private FalAiProperties props;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        client = new FalAiClient(httpClient, new ObjectMapper(), clock);
        props = propsWith("falai-test-key");
    }

    @Test
    void deadlineAlreadyPastThrowsTimeoutWithoutContactingFalAi() throws Exception {
        Instant pastDeadline = FIXED_NOW.minusSeconds(1);
        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(props, "prompt", samplePhoto(), pastDeadline));
        assertEquals(FallbackReason.TIMEOUT, ex.reason());
        verify(httpClient, times(0)).send(any(), any());
    }

    @Test
    void happyPathSubmitPollCompletedFetchReturnsPosterImage() throws Exception {
        Stub stubs = new Stub();
        stubs.queue(submitOk("rid-1", "https://queue.fal.run/status/rid-1",
                "https://queue.fal.run/result/rid-1"));
        stubs.queue(statusOk("IN_PROGRESS", null));
        stubs.queue(statusOk("COMPLETED", null));
        stubs.queue(resultOk("https://fal.media/files/abc.jpg"));
        stubs.queueBytes(jpegBytesOk(tinyJpeg()));

        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofByteArray())))
                .thenAnswer(stubs::nextBytesResponse);

        PosterImage poster = client.generateImage(props, "PROMPT", samplePhoto(), futureDeadline());

        assertEquals("image/jpeg", poster.mediaType());
        assertEquals(64, poster.widthPx());
        assertEquals(64, poster.heightPx());
        // 4 string calls (submit, poll-1, poll-2, fetch-result) + 1 bytes call.
        verify(httpClient, times(4)).send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.ofString()));
        verify(httpClient, times(1)).send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.ofByteArray()));
    }

    @Test
    void submitReturns429MapsToRateLimited() throws Exception {
        Stub stubs = new Stub();
        stubs.queue(plain(429, "{\"error\":\"too many requests\"}"));
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(props, "P", samplePhoto(), futureDeadline()));
        assertEquals(FallbackReason.RATE_LIMITED, ex.reason());
    }

    @Test
    void submitReturns500RepeatedlyRetriesThenMapsToNetworkError() throws Exception {
        Stub stubs = new Stub();
        // 4 calls = initial attempt + 3 retries (MAX_IN_BUDGET_RETRIES_PER_STEP).
        for (int i = 0; i < 4; i++) {
            stubs.queue(plain(500, "internal error"));
        }
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(propsFastBackoff("k"), "P", samplePhoto(), futureDeadline()));
        assertEquals(FallbackReason.NETWORK_ERROR, ex.reason());
        // The retry path SHOULD have invoked send more than once.
        verify(httpClient, atLeast(2)).send(any(HttpRequest.class),
                eq(HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    void submitTimeoutMapsToTimeout() throws Exception {
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenThrow(new HttpTimeoutException("submit timed out"));

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(propsFastBackoff("k"), "P", samplePhoto(), futureDeadline()));
        assertEquals(FallbackReason.TIMEOUT, ex.reason());
    }

    @Test
    void pollReportsFailedWithNsfwDetailMapsToSafetyRefused() throws Exception {
        Stub stubs = new Stub();
        stubs.queue(submitOk("rid-2", "https://queue.fal.run/status/rid-2",
                "https://queue.fal.run/result/rid-2"));
        stubs.queue(statusOk("FAILED", "request blocked: nsfw"));
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(propsFastBackoff("k"), "P", samplePhoto(), futureDeadline()));
        assertEquals(FallbackReason.SAFETY_REFUSED, ex.reason());
    }

    @Test
    void pollReportsFailedWithOpaqueDetailMapsToMalformedResponse() throws Exception {
        Stub stubs = new Stub();
        stubs.queue(submitOk("rid-3", "https://queue.fal.run/status/rid-3",
                "https://queue.fal.run/result/rid-3"));
        stubs.queue(statusOk("FAILED", "model error: please retry"));
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(propsFastBackoff("k"), "P", samplePhoto(), futureDeadline()));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void resultBodyMissingImagesFieldMapsToMalformedResponse() throws Exception {
        Stub stubs = new Stub();
        stubs.queue(submitOk("rid-4", "https://queue.fal.run/status/rid-4",
                "https://queue.fal.run/result/rid-4"));
        stubs.queue(statusOk("COMPLETED", null));
        // result body without `images[]`
        stubs.queue(plain(200, "{\"seed\":42}"));
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(propsFastBackoff("k"), "P", samplePhoto(), futureDeadline()));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void submitBodyMissingRequestIdMapsToMalformedResponse() throws Exception {
        Stub stubs = new Stub();
        stubs.queue(plain(200, "{\"garbage\":true}"));
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                client.generateImage(props, "P", samplePhoto(), futureDeadline()));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void submitBodyCarriesImageUrlsAsDataUrlAndExpectedShape() throws Exception {
        Stub stubs = new Stub();
        stubs.queue(submitOk("rid-5", "https://queue.fal.run/status/rid-5",
                "https://queue.fal.run/result/rid-5"));
        stubs.queue(statusOk("COMPLETED", null));
        stubs.queue(resultOk("https://fal.media/files/x.jpg"));
        stubs.queueBytes(jpegBytesOk(tinyJpeg()));
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofString())))
                .thenAnswer(stubs::nextStringResponse);
        when(httpClient.send(any(HttpRequest.class), eq(HttpResponse.BodyHandlers.ofByteArray())))
                .thenAnswer(stubs::nextBytesResponse);

        client.generateImage(props, "MY-PROMPT", samplePhoto(), futureDeadline());

        // Verify the submit body shape: data URL in image_urls[0], num_images=1.
        verify(httpClient, atLeast(1)).send(argThat((HttpRequest req) -> {
            if (!"POST".equals(req.method())) return false;
            HttpRequest.BodyPublisher pub = req.bodyPublisher().orElse(null);
            if (pub == null) return false;
            BodyCapture capture = new BodyCapture();
            pub.subscribe(capture);
            String body = capture.body();
            return body.contains("\"prompt\":\"MY-PROMPT\"")
                    && body.contains("\"image_urls\":[\"data:image/jpeg;base64,")
                    && body.contains("\"num_images\":1")
                    && body.contains("\"aspect_ratio\":\"3:4\"");
        }), eq(HttpResponse.BodyHandlers.ofString()));
    }

    // -------- helpers --------

    private static Instant futureDeadline() {
        return FIXED_NOW.plusSeconds(60);
    }

    private static FalAiProperties propsWith(String apiKey) {
        return new FalAiProperties(apiKey,
                "fal-ai/nano-banana-pro/edit",
                "https://queue.fal.run",
                8_000, 5_000, 10_000, 30_000,
                /* pollInitialIntervalMs */ 1,
                /* pollMaxIntervalMs */ 1,
                4 * 1024 * 1024, 1536, 1024, 0.85);
    }

    /** Sub-millisecond back-off so retry loops don't slow the test suite. */
    private static FalAiProperties propsFastBackoff(String apiKey) {
        return new FalAiProperties(apiKey,
                "fal-ai/nano-banana-pro/edit",
                "https://queue.fal.run",
                8_000, 5_000, 10_000, 30_000,
                1, 1,
                4 * 1024 * 1024, 1536, 1024, 0.85);
    }

    private static PhotoPayload samplePhoto() {
        return new PhotoPayload(new byte[]{1, 2, 3, 4}, "image/jpeg");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> plain(int status, String body) {
        HttpResponse<String> r = (HttpResponse<String>) mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }

    private static HttpResponse<String> submitOk(String requestId, String statusUrl, String responseUrl) {
        return plain(200, "{\"request_id\":\"" + requestId
                + "\",\"status_url\":\"" + statusUrl
                + "\",\"response_url\":\"" + responseUrl
                + "\",\"status\":\"IN_QUEUE\"}");
    }

    private static HttpResponse<String> statusOk(String status, String detail) {
        StringBuilder body = new StringBuilder("{\"status\":\"").append(status).append("\"");
        if (detail != null) {
            body.append(",\"detail\":\"").append(detail).append("\"");
        }
        body.append('}');
        return plain(200, body.toString());
    }

    private static HttpResponse<String> resultOk(String imageUrl) {
        return plain(200, "{\"images\":[{\"url\":\"" + imageUrl
                + "\",\"width\":64,\"height\":64,\"content_type\":\"image/jpeg\"}],\"seed\":1}");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<byte[]> jpegBytesOk(byte[] bytes) {
        HttpResponse<byte[]> r = (HttpResponse<byte[]>) mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(200);
        when(r.body()).thenReturn(bytes);
        return r;
    }

    private static byte[] tinyJpeg() throws IOException {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "JPEG", baos);
        return baos.toByteArray();
    }

    /** Pull-style queue of canned responses, drained per HttpClient.send call. */
    private static final class Stub {
        private final List<HttpResponse<String>> stringQueue = new ArrayList<>();
        private final List<HttpResponse<byte[]>> bytesQueue = new ArrayList<>();

        void queue(HttpResponse<String> r) { stringQueue.add(r); }
        void queueBytes(HttpResponse<byte[]> r) { bytesQueue.add(r); }

        HttpResponse<String> nextStringResponse(InvocationOnMock inv) {
            if (stringQueue.isEmpty()) {
                throw new AssertionError("FalAiClient called HttpClient.send more times than stubbed");
            }
            return stringQueue.remove(0);
        }

        HttpResponse<byte[]> nextBytesResponse(InvocationOnMock inv) {
            if (bytesQueue.isEmpty()) {
                throw new AssertionError("FalAiClient called bytes-send more times than stubbed");
            }
            return bytesQueue.remove(0);
        }
    }

    /**
     * Minimal {@link java.util.concurrent.Flow.Subscriber} that buffers the
     * full publisher output into a String. Used to peek at HttpRequest body
     * shape inside the {@code argThat(...)} matcher above.
     */
    private static final class BodyCapture implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        @Override public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
        @Override public void onNext(java.nio.ByteBuffer item) {
            byte[] arr = new byte[item.remaining()];
            item.get(arr);
            buffer.writeBytes(arr);
        }
        @Override public void onError(Throwable t) {}
        @Override public void onComplete() {}

        String body() { return buffer.toString(); }
    }
}

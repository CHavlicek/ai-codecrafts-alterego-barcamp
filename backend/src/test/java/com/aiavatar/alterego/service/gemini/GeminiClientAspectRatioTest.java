package com.aiavatar.alterego.service.gemini;

import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiClient;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiPromptBuilder;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression-lock: the outbound Gemini request body carries
 * {@code generationConfig.imageConfig.aspectRatio = "3:4"} so the
 * provider is asked for portrait 3:4 in the typed channel as well as in
 * the prompt text. Pairs with {@link GeminiPromptBuilderTest} which
 * locks the prompt-text channel.
 */
class GeminiClientAspectRatioTest {

    @Test
    void requestBodyIncludesImageAspectRatioThreeToFour() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        HttpClient httpClient = mock(HttpClient.class);

        // Stub out send() with a captured-request handler that returns a
        // minimal valid Gemini response carrying a tiny PNG inline_data.
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(buildSuccessBody());
        doReturn(response).when(httpClient).send(any(HttpRequest.class), any());

        GeminiProperties props = new GeminiProperties(
                "fake-api-key",
                "gemini-test-model",
                "https://example.invalid/v1beta",
                15_000,
                10_000_000,
                4096,
                1024,
                0.85,
                "gemini-test-text-model",
                15_000
        );

        GeminiClient client = new GeminiClient(httpClient, props, mapper);
        PosterImage poster = client.generateImage("gemini-test-model", "any prompt",
                new PhotoPayload(new byte[]{1, 2, 3}, "image/jpeg"));
        assertNotNull(poster);

        // Capture the actual outbound HttpRequest and decode its body.
        ArgumentCaptor<HttpRequest> captor = ArgumentCaptor.forClass(HttpRequest.class);
        org.mockito.Mockito.verify(httpClient).send(captor.capture(), any());
        HttpRequest sent = captor.getValue();

        String bodyJson = extractBodyAsString(sent);
        JsonNode body = mapper.readTree(bodyJson);
        JsonNode aspectRatio = body
                .path("generationConfig")
                .path("imageConfig")
                .path("aspectRatio");
        assertEquals("3:4", aspectRatio.asText(""),
                "outbound Gemini body MUST set "
                        + "generationConfig.imageConfig.aspectRatio = \"3:4\". Body was:\n"
                        + bodyJson);
    }

    /** Build a minimal Gemini success response whose inline_data is a 16×16 PNG. */
    private static String buildSuccessBody() throws IOException {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(Color.BLUE);
            g.fillRect(0, 0, 16, 16);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        return "{\"candidates\":[{\"content\":{\"parts\":[{\"inline_data\":{\"mime_type\":\"image/png\",\"data\":\""
                + base64 + "\"}}]}}]}";
    }

    /** HttpRequest body publishers don't expose the raw body. Drain it. */
    private static String extractBodyAsString(HttpRequest req) {
        var publisherOpt = req.bodyPublisher();
        if (publisherOpt.isEmpty()) {
            return "";
        }
        var publisher = publisherOpt.get();
        java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> sub;
        StringBuilder out = new StringBuilder();
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        sub = new java.util.concurrent.Flow.Subscriber<>() {
            @Override
            public void onSubscribe(java.util.concurrent.Flow.Subscription s) { s.request(Long.MAX_VALUE); }
            @Override
            public void onNext(java.nio.ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                out.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
            }
            @Override
            public void onError(Throwable t) { done.countDown(); }
            @Override
            public void onComplete() { done.countDown(); }
        };
        publisher.subscribe(sub);
        try {
            done.await(5, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return out.toString();
    }
}

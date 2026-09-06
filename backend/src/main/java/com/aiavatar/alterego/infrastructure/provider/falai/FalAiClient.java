package com.aiavatar.alterego.infrastructure.provider.falai;

import com.aiavatar.alterego.infrastructure.config.FalAiProperties;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import javax.net.ssl.SSLException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Thin HTTP wrapper around fal.ai's queue-based REST API for the
 * {@code nano-banana-pro/edit} image-edit model (016 research.md R2 / R3 /
 * R8). Implements the three-step submit → poll → fetch protocol entirely
 * within an end-to-end wall-clock deadline (FR-1614a).
 *
 * <p>All HTTP calls go through Java 21's {@link HttpClient}; bodies are
 * built and parsed with Jackson. No fal.ai SDK is added (Constitution
 * Principles I + VI; mirrors the rationale 003 R2 used for Gemini).
 *
 * <p>Constructor-injected {@link Clock} keeps deadline assertions
 * deterministic in unit tests — pass {@code Clock.systemUTC()} from
 * production wiring; pass a fixed clock from tests that exercise the
 * timeout branch.
 */
@Component
public class FalAiClient {

    private static final int MAX_IN_BUDGET_RETRIES_PER_STEP = 3;
    private static final int FETCH_RETRIES = 2;
    private static final long INITIAL_RETRY_BACKOFF_MS = 200;
    private static final long MAX_RETRY_BACKOFF_MS = 2_000;
    /** Bound on poll-loop iterations so a misbehaving fixture can't spin
     *  forever even if the deadline check itself drifts. */
    private static final int MAX_POLL_ITERATIONS = 200;

    /**
     * User-Agent sent on every fal.ai request. The JDK's default
     * ({@code Java-http-client/21}) is treated as a bot signature by fal.ai's
     * edge (Cloudflare) and gets a 403 before reaching their auth layer. A
     * self-identifying UA keeps requests on the legitimate-traffic path.
     */
    private static final String USER_AGENT = "aiavatar-backend/0.1";

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final Clock clock;

    public FalAiClient(HttpClient httpClient, ObjectMapper mapper, Clock clock) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * Submit the request, poll until terminal status, fetch the result, and
     * resolve the image-bytes URL — all bounded by the supplied
     * {@code deadline}. Throws {@link GenerationFailure} carrying a
     * {@link FallbackReason} on any provider-boundary failure
     * (016 research.md R5).
     */
    public PosterImage generateImage(FalAiProperties props,
                                     String prompt,
                                     PhotoPayload photo,
                                     Instant deadline) {
        ensureBudgetRemaining(deadline);
        FalAiSubmitResponse submit = submit(props, prompt, photo, deadline);
        FalAiResultRefs refs = pollUntilTerminal(props, submit, deadline);
        FalAiImageRef image = fetchResult(props, refs, deadline);
        return downloadImage(props, image, deadline);
    }

    // -------------- step 1: submit --------------

    private FalAiSubmitResponse submit(FalAiProperties props,
                                       String prompt,
                                       PhotoPayload photo,
                                       Instant deadline) {
        String body = renderSubmitBody(prompt, photo);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(props.endpointUrl() + "/" + props.modelId()))
                .timeout(Duration.ofMillis(props.submitTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", "Key " + props.apiKey())
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = sendWithRetry(request, deadline,
                MAX_IN_BUDGET_RETRIES_PER_STEP, "submit");
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return parseSubmit(response.body());
        }
        throw new GenerationFailure(classifyErrorStatus(status, response.body()),
                "fal.ai submit returned HTTP " + status);
    }

    // -------------- step 2: poll --------------

    /** Truncate a provider response body for inclusion in error messages —
     *  long enough to be diagnostic, short enough not to flood logs. */
    private static String truncateBody(String body) {
        if (body == null) return "<no body>";
        return body.length() <= 500 ? body : body.substring(0, 500) + "…(truncated)";
    }

    private FalAiResultRefs pollUntilTerminal(FalAiProperties props,
                                              FalAiSubmitResponse submit,
                                              Instant deadline) {
        long interval = props.pollInitialIntervalMs();
        for (int i = 0; i < MAX_POLL_ITERATIONS; i++) {
            ensureBudgetRemaining(deadline);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(submit.statusUrl()))
                    .timeout(Duration.ofMillis(props.pollTimeoutMs()))
                    .header("Accept", "application/json")
                    .header("Authorization", "Key " + props.apiKey())
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            HttpResponse<String> response = sendWithRetry(request, deadline,
                    MAX_IN_BUDGET_RETRIES_PER_STEP, "poll");
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new GenerationFailure(classifyErrorStatus(status, response.body()),
                        "fal.ai poll returned HTTP " + status);
            }
            FalAiStatusResponse parsed = parseStatus(response.body());
            String s = parsed.status() == null ? "" : parsed.status().toUpperCase(Locale.ROOT);
            switch (s) {
                case "COMPLETED" -> {
                    return new FalAiResultRefs(submit.responseUrl());
                }
                case "FAILED" -> {
                    throw new GenerationFailure(classifyFailureDetail(parsed.detail()),
                            "fal.ai poll reported FAILED" + (parsed.detail() == null ? "" : ": " + parsed.detail()));
                }
                case "IN_QUEUE", "IN_PROGRESS" -> {
                    // continue
                }
                default -> {
                    // Unknown status → treat as malformed; the contract is a
                    // closed enumeration on the documented surface.
                    throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                            "fal.ai poll returned unknown status: " + parsed.status());
                }
            }
            sleepWithJitter(interval, deadline);
            interval = Math.min(interval * 2, props.pollMaxIntervalMs());
        }
        throw new GenerationFailure(FallbackReason.TIMEOUT,
                "fal.ai poll loop exceeded " + MAX_POLL_ITERATIONS + " iterations");
    }

    // -------------- step 3: fetch result + image bytes --------------

    private FalAiImageRef fetchResult(FalAiProperties props,
                                      FalAiResultRefs refs,
                                      Instant deadline) {
        ensureBudgetRemaining(deadline);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(refs.responseUrl()))
                .timeout(Duration.ofMillis(props.fetchTimeoutMs()))
                .header("Accept", "application/json")
                .header("Authorization", "Key " + props.apiKey())
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<String> response = sendWithRetry(request, deadline,
                FETCH_RETRIES, "fetch-result");
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new GenerationFailure(classifyErrorStatus(status, response.body()),
                    "fal.ai fetch-result returned HTTP " + status);
        }
        return parseResult(response.body());
    }

    private PosterImage downloadImage(FalAiProperties props,
                                      FalAiImageRef image,
                                      Instant deadline) {
        ensureBudgetRemaining(deadline);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(image.url()))
                .timeout(Duration.ofMillis(props.fetchTimeoutMs()))
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        HttpResponse<byte[]> response = sendBytesWithRetry(request, deadline,
                FETCH_RETRIES, "fetch-bytes");
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new GenerationFailure(classifyErrorStatus(status, ""),
                    "fal.ai image-bytes GET returned HTTP " + status);
        }
        byte[] bytes = response.body();
        if (bytes == null || bytes.length == 0) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "fal.ai image-bytes response was empty");
        }
        return decodeImageBytes(bytes, image.contentType());
    }

    // -------------- HTTP send helpers --------------

    private HttpResponse<String> sendWithRetry(HttpRequest request, Instant deadline,
                                               int maxRetries, String stepName) {
        GenerationFailure lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            ensureBudgetRemaining(deadline);
            try {
                HttpResponse<String> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (isTransientForRetry(status) && attempt < maxRetries) {
                    // Retry within budget. Includes 5xx (provider hiccup)
                    // and 403 (Cloudflare bot-management on cold-start: the
                    // first request gets challenged, the response carries
                    // a `__cf_bm` cookie our CookieManager captures, the
                    // retry echoes it back and slips through). 403 is
                    // bounded by the same maxRetries / deadline as 5xx so
                    // a genuinely-Forbidden key still surfaces quickly.
                    lastFailure = new GenerationFailure(FallbackReason.NETWORK_ERROR,
                            "fal.ai " + stepName + " returned HTTP " + status + " (transient)");
                    sleepWithJitter(retryBackoff(attempt), deadline);
                    continue;
                }
                return response;
            } catch (HttpTimeoutException e) {
                lastFailure = new GenerationFailure(FallbackReason.TIMEOUT,
                        "fal.ai " + stepName + " timed out", e);
            } catch (ConnectException | UnknownHostException | SSLException e) {
                lastFailure = new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " failed (" + e.getClass().getSimpleName() + ")", e);
            } catch (IOException e) {
                lastFailure = new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " failed (IOException): "
                                + e.getClass().getSimpleName(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " interrupted", e);
            }
            if (attempt < maxRetries) {
                sleepWithJitter(retryBackoff(attempt), deadline);
            }
        }
        throw (lastFailure != null) ? lastFailure
                : new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " exhausted retries with no recorded failure");
    }

    /** 5xx are provider hiccups; 403 is Cloudflare's bot-management
     *  cold-start dance (it issues a {@code __cf_bm} cookie that the
     *  CookieManager picks up; the retry slips through). All other 4xx
     *  are real classification failures and surface immediately. */
    private static boolean isTransientForRetry(int status) {
        return (status >= 500 && status < 600) || status == 403;
    }

    private HttpResponse<byte[]> sendBytesWithRetry(HttpRequest request, Instant deadline,
                                                    int maxRetries, String stepName) {
        GenerationFailure lastFailure = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            ensureBudgetRemaining(deadline);
            try {
                HttpResponse<byte[]> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofByteArray());
                int status = response.statusCode();
                if (isTransientForRetry(status) && attempt < maxRetries) {
                    lastFailure = new GenerationFailure(FallbackReason.NETWORK_ERROR,
                            "fal.ai " + stepName + " returned HTTP " + status + " (transient)");
                    sleepWithJitter(retryBackoff(attempt), deadline);
                    continue;
                }
                return response;
            } catch (HttpTimeoutException e) {
                lastFailure = new GenerationFailure(FallbackReason.TIMEOUT,
                        "fal.ai " + stepName + " timed out", e);
            } catch (ConnectException | UnknownHostException | SSLException e) {
                lastFailure = new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " failed (" + e.getClass().getSimpleName() + ")", e);
            } catch (IOException e) {
                lastFailure = new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " failed (IOException): "
                                + e.getClass().getSimpleName(), e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " interrupted", e);
            }
            if (attempt < maxRetries) {
                sleepWithJitter(retryBackoff(attempt), deadline);
            }
        }
        throw (lastFailure != null) ? lastFailure
                : new GenerationFailure(FallbackReason.NETWORK_ERROR,
                        "fal.ai " + stepName + " exhausted retries with no recorded failure");
    }

    // -------------- deadline + back-off helpers --------------

    private void ensureBudgetRemaining(Instant deadline) {
        if (!clock.instant().isBefore(deadline)) {
            throw new GenerationFailure(FallbackReason.TIMEOUT,
                    "fal.ai end-to-end deadline reached");
        }
    }

    /**
     * Sleep for {@code intendedMs} (with ±20% jitter), capped by the
     * remaining budget. If the cap clips to zero or negative, we just
     * return — the next {@link #ensureBudgetRemaining(Instant)} will fire
     * the {@link FallbackReason#TIMEOUT} path.
     */
    private void sleepWithJitter(long intendedMs, Instant deadline) {
        long jitterMs = jitter(intendedMs);
        long remainingMs = Duration.between(clock.instant(), deadline).toMillis();
        if (remainingMs <= 0) return;
        long sleepMs = Math.min(jitterMs, remainingMs);
        if (sleepMs <= 0) return;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationFailure(FallbackReason.NETWORK_ERROR,
                    "fal.ai backoff sleep interrupted", e);
        }
    }

    private static long jitter(long ms) {
        // ±20% jitter (Principle IV "randomised jitter"). ThreadLocalRandom
        // is fine here — no determinism requirement at this seam.
        double factor = 0.8 + (ThreadLocalRandom.current().nextDouble() * 0.4);
        return Math.max(1, (long) (ms * factor));
    }

    private static long retryBackoff(int attempt) {
        long base = INITIAL_RETRY_BACKOFF_MS << Math.min(attempt, 6);
        return Math.min(base, MAX_RETRY_BACKOFF_MS);
    }

    // -------------- request body + parsing --------------

    private String renderSubmitBody(String prompt, PhotoPayload photo) {
        ObjectNode root = mapper.createObjectNode();
        root.put("prompt", prompt);
        ArrayNode imageUrls = root.putArray("image_urls");
        imageUrls.add("data:" + photo.mediaType() + ";base64,"
                + Base64.getEncoder().encodeToString(photo.bytes()));
        root.put("num_images", 1);
        // Frame asset's transparent inner cutout is ~3:4 (the rest of the
        // 1024×1536 canvas is opaque chrome). PosterFrameOverlayService
        // logs `event=frame.apply.ratio_mismatch` and falls into the fail-soft
        // path if the input deviates from the target aspect — matching 003's
        // GeminiClient (`generationConfig.imageConfig.aspectRatio = "3:4"`).
        root.put("aspect_ratio", "3:4");
        root.put("output_format", "jpeg");
        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Failed to serialise fal.ai submit body", e);
        }
    }

    private FalAiSubmitResponse parseSubmit(String body) {
        JsonNode root = parseJson(body, "submit");
        String requestId = root.path("request_id").asText(null);
        String statusUrl = root.path("status_url").asText(null);
        String responseUrl = root.path("response_url").asText(null);
        String status = root.path("status").asText(null);
        if (requestId == null || statusUrl == null || responseUrl == null) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "fal.ai submit response missing request_id / status_url / response_url");
        }
        return new FalAiSubmitResponse(requestId, statusUrl, responseUrl, status);
    }

    private FalAiStatusResponse parseStatus(String body) {
        JsonNode root = parseJson(body, "poll");
        String status = root.path("status").asText(null);
        Integer queuePosition = root.has("queue_position")
                ? root.path("queue_position").asInt() : null;
        String detail = root.path("detail").asText(null);
        if (detail == null) {
            // Some fal.ai endpoints embed the failure message in `error` or
            // `error.message` rather than `detail`.
            JsonNode err = root.path("error");
            if (err.isTextual()) {
                detail = err.asText();
            } else if (err.isObject() && err.has("message")) {
                detail = err.path("message").asText(null);
            }
        }
        return new FalAiStatusResponse(status, queuePosition, detail);
    }

    private FalAiImageRef parseResult(String body) {
        JsonNode root = parseJson(body, "fetch-result");
        JsonNode images = root.path("images");
        if (!images.isArray() || images.isEmpty()) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "fal.ai fetch-result missing images[0]");
        }
        JsonNode first = images.get(0);
        String url = first.path("url").asText(null);
        if (url == null || url.isBlank()) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "fal.ai fetch-result images[0].url missing");
        }
        Integer width = first.has("width") ? first.path("width").asInt() : null;
        Integer height = first.has("height") ? first.path("height").asInt() : null;
        String contentType = first.path("content_type").asText("image/jpeg");
        return new FalAiImageRef(url, width, height, contentType);
    }

    private JsonNode parseJson(String body, String stepName) {
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "fal.ai " + stepName + " response was not valid JSON", e);
        }
    }

    // -------------- error classification --------------

    private static FallbackReason classifyErrorStatus(int status, String body) {
        if (status == 504) return FallbackReason.TIMEOUT;
        if (status == 429) return FallbackReason.RATE_LIMITED;
        if (status >= 500 && status < 600) return FallbackReason.NETWORK_ERROR;
        // 4xx other than 429: check for safety-flagged refusals embedded in
        // the response body before defaulting to MALFORMED_RESPONSE.
        if (body != null && containsSafetyMarker(body)) return FallbackReason.SAFETY_REFUSED;
        return FallbackReason.MALFORMED_RESPONSE;
    }

    private static FallbackReason classifyFailureDetail(String detail) {
        if (detail != null && containsSafetyMarker(detail)) {
            return FallbackReason.SAFETY_REFUSED;
        }
        // FAILED with non-safety detail — treat as malformed; the model
        // refused for an opaque reason.
        return FallbackReason.MALFORMED_RESPONSE;
    }

    private static boolean containsSafetyMarker(String haystack) {
        String lower = haystack.toLowerCase(Locale.ROOT);
        return lower.contains("safety")
                || lower.contains("content_policy")
                || lower.contains("nsfw")
                || lower.contains("unsafe");
    }

    // -------------- image decode --------------

    private static PosterImage decodeImageBytes(byte[] bytes, String contentType) {
        int width;
        int height;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                        "fal.ai image bytes were not a decodable image");
            }
            width = img.getWidth();
            height = img.getHeight();
        } catch (IOException e) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "fal.ai image bytes could not be read as an image", e);
        }
        String normalised = "image/jpg".equalsIgnoreCase(contentType)
                ? "image/jpeg" : contentType;
        if (!"image/png".equals(normalised) && !"image/jpeg".equals(normalised)) {
            // PosterImage only accepts png/jpeg; coerce to jpeg as the model's
            // documented default. Reaching here means content_type was an
            // unexpected variant — log via the GenerationFailure cause.
            normalised = "image/jpeg";
        }
        return new PosterImage(bytes, normalised, width, height);
    }

    // -------------- internal records --------------

    /** Fal.ai submit-response shape (R3). Package-private; not on the wire. */
    record FalAiSubmitResponse(String requestId, String statusUrl,
                               String responseUrl, String status) {}

    /** Fal.ai status-response shape (R3). */
    record FalAiStatusResponse(String status, Integer queuePosition, String detail) {}

    /** Convenience pair of refs returned by the submit step. */
    record FalAiResultRefs(String responseUrl) {}

    /** Fal.ai result-response's first image entry (R3). */
    record FalAiImageRef(String url, Integer width, Integer height, String contentType) {}
}

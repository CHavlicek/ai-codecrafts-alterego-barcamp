package com.aiavatar.alterego.infrastructure.provider.gemini;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
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
import java.time.Duration;
import java.util.Base64;
import javax.net.ssl.SSLException;

/**
 * Thin HTTP wrapper around Google's Gemini {@code generateContent} endpoint
 * (003 research.md R2 / R3). Builds the JSON body with Jackson, sends via
 * Java 21's {@link HttpClient}, and parses the inline_data base64 image out
 * of the response — returning a {@link PosterImage} with decoded bytes and
 * dimensions read via ImageIO.
 *
 * <p>Happy-path + timeout + malformed-response mapping lives here in
 * Phase 3 (T028); the full exception-mapping table (network / 4xx / 429 /
 * 5xx / safety refusal) lands with 003 US3 / T042.
 *
 * <p>Constructor-injectable {@link HttpClient} keeps this unit-testable via
 * Mockito without WireMock.
 */
@Component
public class GeminiClient {

    private static final String RESPONSE_MODALITY_IMAGE = "IMAGE";

    private final HttpClient httpClient;
    private final GeminiProperties props;
    private final ObjectMapper mapper;

    public GeminiClient(HttpClient httpClient, GeminiProperties props, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * Call {@code POST {endpoint}/models/{modelId}:generateContent} with the
     * composed prompt and the (reduced) photo. Returns the decoded image as
     * a {@link PosterImage}.
     *
     * @throws GenerationFailure on any provider-boundary failure; subclasses
     *                           of the {@link FallbackReason} enum carry the
     *                           specific classification (T042 expands this).
     */
    public PosterImage generateImage(String modelId, String prompt, PhotoPayload photo) {
        String body = renderRequestBody(prompt, photo);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildEndpointUri(modelId))
                .timeout(Duration.ofMillis(props.requestTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new GenerationFailure(FallbackReason.TIMEOUT,
                    "Gemini call timed out after " + props.requestTimeoutMs() + "ms", e);
        } catch (ConnectException | UnknownHostException | SSLException e) {
            throw new GenerationFailure(FallbackReason.NETWORK_ERROR,
                    "Gemini call failed (" + e.getClass().getSimpleName() + ")", e);
        } catch (IOException e) {
            // Other IOExceptions — broken pipe, reset, etc. — all map to
            // NETWORK_ERROR per research.md R5.
            throw new GenerationFailure(FallbackReason.NETWORK_ERROR,
                    "Gemini call failed (IOException): " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GenerationFailure(FallbackReason.NETWORK_ERROR,
                    "Gemini call interrupted", e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return parseSuccessBody(response.body());
        }
        // Full R5 status → reason mapping:
        //   504              → TIMEOUT         (gateway timeout)
        //   429              → RATE_LIMITED
        //   other 5xx        → NETWORK_ERROR   ("provider is having a bad day")
        //   other 4xx        → MALFORMED_RESPONSE (our request was wrong)
        throw new GenerationFailure(classifyErrorStatus(status),
                "Gemini returned HTTP " + status);
    }

    private static FallbackReason classifyErrorStatus(int status) {
        if (status == 504) return FallbackReason.TIMEOUT;
        if (status == 429) return FallbackReason.RATE_LIMITED;
        if (status >= 500 && status < 600) return FallbackReason.NETWORK_ERROR;
        return FallbackReason.MALFORMED_RESPONSE;
    }

    private URI buildEndpointUri(String modelId) {
        // endpointUrl = https://generativelanguage.googleapis.com/v1beta
        // final URI    = {endpointUrl}/models/{modelId}:generateContent
        return URI.create(props.endpointUrl() + "/models/" + modelId + ":generateContent");
    }

    private String renderRequestBody(String prompt, PhotoPayload photo) {
        ObjectNode root = mapper.createObjectNode();

        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);
        ObjectNode inlineDataPart = parts.addObject();
        ObjectNode inlineData = inlineDataPart.putObject("inline_data");
        inlineData.put("mime_type", photo.mediaType());
        inlineData.put("data", Base64.getEncoder().encodeToString(photo.bytes()));

        ObjectNode generationConfig = root.putObject("generationConfig");
        generationConfig.putArray("responseModalities").add(RESPONSE_MODALITY_IMAGE);
        generationConfig.put("candidateCount", 1);
        // Ask the provider for portrait 3:4 on the typed channel — the
        // poster-frame asset's transparent inner cutout is ~3:4 (the rest of
        // the 2:3 canvas is opaque chrome). The prompt text states the same
        // ratio in GeminiPromptBuilder; we set both so the request is
        // unambiguous on whichever channel the model honours. Models that
        // don't recognise the field ignore it; FR-1511 letterboxes
        // mismatched responses.
        generationConfig.putObject("imageConfig").put("aspectRatio", "3:4");

        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            // Jackson on an ObjectNode we just built shouldn't fail;
            // treat as malformed for safety.
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Failed to serialise Gemini request body", e);
        }
    }

    private PosterImage parseSuccessBody(String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException e) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini response body wasn't valid JSON", e);
        }

        // Safety refusal detection per R5:
        //   promptFeedback.blockReason present  OR
        //   candidates[0].finishReason == "IMAGE_SAFETY" or "SAFETY"
        JsonNode blockReason = root.path("promptFeedback").path("blockReason");
        if (blockReason.isTextual() && !blockReason.asText().isBlank()) {
            throw new GenerationFailure(FallbackReason.SAFETY_REFUSED,
                    "Gemini blocked prompt: " + blockReason.asText());
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini response missing candidates[0]");
        }
        JsonNode finishReason = candidates.get(0).path("finishReason");
        if (finishReason.isTextual()) {
            String fr = finishReason.asText();
            if ("SAFETY".equals(fr) || "IMAGE_SAFETY".equals(fr)) {
                throw new GenerationFailure(FallbackReason.SAFETY_REFUSED,
                        "Gemini candidate refused with finishReason=" + fr);
            }
        }

        JsonNode partsNode = candidates.get(0).path("content").path("parts");
        if (!partsNode.isArray() || partsNode.isEmpty()) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini response candidates[0].content.parts missing or empty");
        }

        for (JsonNode part : partsNode) {
            JsonNode inline = part.path("inline_data");
            if (inline.isMissingNode() || inline.isNull()) {
                // camelCase alternative some Gemini previews use
                inline = part.path("inlineData");
            }
            if (!inline.isMissingNode() && !inline.isNull()) {
                String data = inline.path("data").asText(null);
                String mime = inline.path("mime_type").asText(
                        inline.path("mimeType").asText("image/png"));
                if (data == null || data.isBlank()) continue;
                return decodeImagePart(data, mime);
            }
        }
        throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                "Gemini response contained no inline_data image part");
    }

    private PosterImage decodeImagePart(String base64, String mime) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini inline_data.data wasn't valid base64", e);
        }
        if (bytes.length == 0) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini inline_data.data decoded to zero bytes");
        }

        int width;
        int height;
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
            if (img == null) {
                throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                        "Gemini inline_data bytes were not a decodable image");
            }
            width = img.getWidth();
            height = img.getHeight();
        } catch (IOException e) {
            throw new GenerationFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini inline_data bytes could not be read as an image", e);
        }

        // Normalise the mime to match PosterImage's validator — jpeg vs jpg.
        String normalisedMime = "image/jpg".equalsIgnoreCase(mime) ? "image/jpeg" : mime;
        return new PosterImage(bytes, normalisedMime, width, height);
    }
}

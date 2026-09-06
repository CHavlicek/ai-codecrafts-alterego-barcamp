package com.aiavatar.alterego.infrastructure.provider.gemini;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Thin HTTP wrapper around Google's Gemini text-generation
 * {@code generateContent} endpoint (014 research.md §R3). Sibling to
 * {@link GeminiClient} (image side); same Java 21 {@link HttpClient} +
 * Jackson plumbing, retargeted at structured-JSON text output.
 *
 * <p>The client builds a request body with one text part (no inline_data),
 * sets {@code generationConfig.responseMimeType="application/json"} +
 * {@code responseSchema} pinning the four-trait shape (research.md §R3),
 * POSTs, and on a 200 unwraps {@code candidates[0].content.parts[*].text}
 * which is itself a JSON string the Gemini API has validated against the
 * supplied schema. The embedded JSON is parsed and returned to the caller.
 *
 * <p>Failure-classification table — research §R5, mirrors 003:
 * <ul>
 *   <li>HTTP 504 / {@link HttpTimeoutException} → {@link FallbackReason#TIMEOUT}</li>
 *   <li>HTTP 429 → {@link FallbackReason#RATE_LIMITED}</li>
 *   <li>{@link ConnectException} / {@link UnknownHostException} / {@link SSLException} /
 *       other {@link IOException} / HTTP 5xx (other than 504) → {@link FallbackReason#NETWORK_ERROR}</li>
 *   <li>HTTP 4xx (other than 429) → {@link FallbackReason#MALFORMED_RESPONSE}</li>
 *   <li>{@code promptFeedback.blockReason} OR {@code candidates[0].finishReason ∈ {SAFETY, RECITATION}}
 *       → {@link FallbackReason#SAFETY_REFUSED}</li>
 *   <li>2xx body that doesn't parse as JSON, lacks {@code candidates[0].content.parts[*].text},
 *       or whose embedded text isn't itself valid JSON → {@link FallbackReason#MALFORMED_RESPONSE}</li>
 * </ul>
 */
@Component
public class GeminiCharacterClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiCharacterClient.class);

    private static final String PHASE = "gemini-text-call";

    private final HttpClient httpClient;
    private final GeminiProperties props;
    private final ObjectMapper mapper;

    public GeminiCharacterClient(HttpClient httpClient, GeminiProperties props, ObjectMapper mapper) {
        this.httpClient = httpClient;
        this.props = props;
        this.mapper = mapper;
    }

    /**
     * Call {@code POST {endpoint}/models/{modelId}:generateContent} with the
     * composed prompt and a structured-JSON {@code generationConfig}.
     * Returns the embedded JSON object (the parsed value of
     * {@code candidates[0].content.parts[0].text}) — NOT the outer Gemini
     * envelope.
     *
     * @throws GenerationFailure on any provider-boundary failure; the
     *         {@link FallbackReason} on the exception encodes which row of
     *         the R5 table fired.
     */
    public JsonNode generateText(String modelId, String prompt) {
        String body = renderRequestBody(prompt);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(buildEndpointUri(modelId))
                .timeout(Duration.ofMillis(props.textRequestTimeoutMs()))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", props.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw classifiedFailure(FallbackReason.TIMEOUT,
                    "Gemini text call timed out after " + props.textRequestTimeoutMs() + "ms", null, e);
        } catch (ConnectException | UnknownHostException | SSLException e) {
            throw classifiedFailure(FallbackReason.NETWORK_ERROR,
                    "Gemini text call failed (" + e.getClass().getSimpleName() + ")", null, e);
        } catch (IOException e) {
            // Other IOExceptions — broken pipe, reset, etc. — map to NETWORK_ERROR per R5.
            throw classifiedFailure(FallbackReason.NETWORK_ERROR,
                    "Gemini text call failed (IOException): " + e.getClass().getSimpleName(), null, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw classifiedFailure(FallbackReason.NETWORK_ERROR,
                    "Gemini text call interrupted", null, e);
        }

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return parseSuccessBody(response.body());
        }
        throw classifiedFailure(classifyErrorStatus(status),
                "Gemini text returned HTTP " + status, status, null);
    }

    private static FallbackReason classifyErrorStatus(int status) {
        if (status == 504) return FallbackReason.TIMEOUT;
        if (status == 429) return FallbackReason.RATE_LIMITED;
        if (status >= 500 && status < 600) return FallbackReason.NETWORK_ERROR;
        return FallbackReason.MALFORMED_RESPONSE;
    }

    private URI buildEndpointUri(String modelId) {
        return URI.create(props.endpointUrl() + "/models/" + modelId + ":generateContent");
    }

    private String renderRequestBody(String prompt) {
        ObjectNode root = mapper.createObjectNode();

        ArrayNode contents = root.putArray("contents");
        ObjectNode content = contents.addObject();
        content.put("role", "user");
        ArrayNode parts = content.putArray("parts");
        parts.addObject().put("text", prompt);

        ObjectNode genCfg = root.putObject("generationConfig");
        genCfg.put("responseMimeType", "application/json");
        genCfg.set("responseSchema", traitResponseSchema());
        genCfg.put("candidateCount", 1);
        genCfg.put("temperature", 0.9);

        try {
            return mapper.writeValueAsString(root);
        } catch (IOException e) {
            // Building a Jackson tree we just constructed shouldn't fail;
            // treat as malformed for safety.
            throw classifiedFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Failed to serialise Gemini text request body", null, e);
        }
    }

    /**
     * Build the JSON-Schema sent as {@code generationConfig.responseSchema}.
     * This is the wire-level contract that pins the four-trait shape — paired
     * with {@link GeminiCharacterResponseParser}'s defensive re-check, both
     * Gemini and the parser enforce FR-1406's rules.
     */
    private ObjectNode traitResponseSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ArrayNode required = schema.putArray("required");
        required.add("heroTitleLine2");
        required.add("tagline");
        required.add("superpowers");
        required.add("quote");

        ObjectNode props = schema.putObject("properties");
        props.set("heroTitleLine2", traitStringSchema());
        props.set("tagline", traitStringSchema());

        ObjectNode supers = props.putObject("superpowers");
        supers.put("type", "array");
        supers.put("minItems", 3);
        supers.put("maxItems", 3);
        supers.set("items", traitStringSchema());

        props.set("quote", traitStringSchema());

        return schema;
    }

    private ObjectNode traitStringSchema() {
        ObjectNode s = mapper.createObjectNode();
        s.put("type", "string");
        s.put("minLength", 1);
        s.put("maxLength", 100);
        return s;
    }

    private JsonNode parseSuccessBody(String body) {
        JsonNode root;
        try {
            root = mapper.readTree(body);
        } catch (IOException e) {
            throw classifiedFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini text response wasn't valid JSON", 200, e);
        }

        // Safety refusal detection per R5:
        //   promptFeedback.blockReason present  OR
        //   candidates[0].finishReason ∈ {SAFETY, RECITATION}
        JsonNode blockReason = root.path("promptFeedback").path("blockReason");
        if (blockReason.isTextual() && !blockReason.asText().isBlank()) {
            throw classifiedFailure(FallbackReason.SAFETY_REFUSED,
                    "Gemini blocked text prompt: " + blockReason.asText(), 200, null);
        }

        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            throw classifiedFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini text response missing candidates[0]", 200, null);
        }
        JsonNode finishReason = candidates.get(0).path("finishReason");
        if (finishReason.isTextual()) {
            String fr = finishReason.asText();
            if ("SAFETY".equals(fr) || "RECITATION".equals(fr)) {
                throw classifiedFailure(FallbackReason.SAFETY_REFUSED,
                        "Gemini text candidate refused with finishReason=" + fr, 200, null);
            }
        }

        JsonNode partsNode = candidates.get(0).path("content").path("parts");
        if (!partsNode.isArray() || partsNode.isEmpty()) {
            throw classifiedFailure(FallbackReason.MALFORMED_RESPONSE,
                    "Gemini text response candidates[0].content.parts missing or empty", 200, null);
        }

        for (JsonNode part : partsNode) {
            JsonNode textNode = part.path("text");
            if (textNode.isTextual()) {
                String text = textNode.asText();
                if (text.isBlank()) continue;
                try {
                    return mapper.readTree(text);
                } catch (IOException e) {
                    throw classifiedFailure(FallbackReason.MALFORMED_RESPONSE,
                            "Gemini text candidate's text part was not valid JSON", 200, e);
                }
            }
        }
        throw classifiedFailure(FallbackReason.MALFORMED_RESPONSE,
                "Gemini text response contained no text part", 200, null);
    }

    /**
     * Build a {@link GenerationFailure} and emit the fixed-schema WARN line
     * per research §R11 — never logs the prompt, never logs the response body.
     */
    private static GenerationFailure classifiedFailure(FallbackReason reason,
                                                       String message,
                                                       Integer status,
                                                       Throwable cause) {
        log.warn("Gemini text call failed",
                kv("phase", PHASE),
                kv("status", status == null ? "n/a" : status.toString()),
                kv("reason", reason.wire()));
        return cause == null
                ? new GenerationFailure(reason, message)
                : new GenerationFailure(reason, message, cause);
    }
}

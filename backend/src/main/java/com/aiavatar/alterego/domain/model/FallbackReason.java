package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Why a {@link AlterEgoResponse.Outcome#FALLBACK} run was served. Present
 * (non-null) iff {@code outcome == FALLBACK}. For operator / automated-test
 * visibility only — the frontend MUST NOT surface this to the end user
 * (003 FR-214 / FR-218).
 *
 * <p>Closed enumeration. Values are spec-pinned by 003 FR-218 — no extension
 * without spec amendment.
 */
public enum FallbackReason {

    /**
     * {@code aiavatar.gemini.api-key} is blank at call time. Checked up-front
     * inside {@code GeminiImageGenerator}; no outbound HTTP attempted.
     */
    NOT_CONFIGURED("not_configured"),

    /**
     * {@link java.net.ConnectException}, {@code UnknownHostException},
     * {@code SSLException}, other {@link java.io.IOException} (excluding
     * {@link java.net.http.HttpTimeoutException}), or HTTP 5xx other than 504
     * after retries exhausted.
     */
    NETWORK_ERROR("network_error"),

    /** HTTP 429 or {@code RESOURCE_EXHAUSTED} in the response body. */
    RATE_LIMITED("rate_limited"),

    /** {@link java.net.http.HttpTimeoutException} or HTTP 504. */
    TIMEOUT("timeout"),

    /**
     * 200 body that doesn't parse, lacks
     * {@code candidates[0]…inline_data.data}, or decodes to an invalid image.
     * Also the catch-all for unexpected 4xx (non-429) and any non-Gemini
     * {@link RuntimeException} that escapes the generator seam.
     */
    MALFORMED_RESPONSE("malformed_response"),

    /**
     * 200 with {@code promptFeedback.blockReason} present, or
     * {@code candidates[0].finishReason == "IMAGE_SAFETY"}.
     */
    SAFETY_REFUSED("safety_refused");

    private final String wire;

    FallbackReason(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static FallbackReason fromWire(@JsonProperty String value) {
        for (FallbackReason r : values()) {
            if (r.wire.equals(value)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Unknown FallbackReason: " + value);
    }
}

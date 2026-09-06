package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Discriminator naming the path that produced the central image of this
 * specific response. Mandatory on every {@link AlterEgoResponse.ResponseMeta}
 * (016 FR-1612). Operator-visible only — the frontend MUST NOT render this
 * value to the end user (016 FR-1622). The user-facing fallback notice
 * remains the generic single-variant message from 003 FR-214.
 *
 * <p>Invariant (enforced by the {@link AlterEgoResponse.ResponseMeta}
 * compact constructor): {@code outcome == REAL} iff
 * {@code provider ∈ {GEMINI, FALAI}}; {@code outcome == FALLBACK} iff
 * {@code provider == STUB}.
 *
 * <p>Wire values are stable from 016's first merge.
 */
public enum Provider {

    /** Real Google Gemini image-generation success (003). */
    GEMINI("gemini"),

    /** Real fal.ai {@code nano-banana-pro/edit} success (016). */
    FALAI("falai"),

    /**
     * Fallback path served the image, for any reason
     * (not_configured / network_error / rate_limited / timeout /
     * malformed_response / safety_refused). Which real provider was attempted
     * on a fallback is recorded only in the FR-1613 backend log line, never
     * in the response body (016 clarification Q2).
     */
    STUB("stub");

    private final String wire;

    Provider(String wire) {
        this.wire = wire;
    }

    @JsonValue
    public String wire() {
        return wire;
    }

    @JsonCreator
    public static Provider fromWire(@JsonProperty String value) {
        for (Provider p : values()) {
            if (p.wire.equals(value)) {
                return p;
            }
        }
        throw new IllegalArgumentException("Unknown Provider: " + value);
    }
}

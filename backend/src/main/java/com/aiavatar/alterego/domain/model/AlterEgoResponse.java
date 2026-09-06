package com.aiavatar.alterego.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.UUID;

/**
 * Response body shape for {@code POST /api/v1/alter-egos}. Mirrors the
 * {@code AlterEgoResponse} schema in the OpenAPI contract.
 *
 * <p>The {@code poster} sub-object carries the image as a data URL (not raw
 * bytes) so the frontend renders it with a single {@code <img>} element
 * and no second fetch.
 *
 * <p>003 delta: {@link ResponseMeta} gained an optional {@link FallbackReason}
 * {@code reason} field (present iff {@code outcome == FALLBACK}); the
 * {@link Outcome} wire value {@code "success"} was renamed to {@code "real"}.
 *
 * <p>016 delta: {@link ResponseMeta} additionally carries a mandatory
 * {@link Provider} discriminator (FR-1612) so operators can verify which path
 * produced the image. The compact constructor enforces the matrix:
 * {@code outcome == REAL} requires {@code provider ∈ {GEMINI, FALAI}};
 * {@code outcome == FALLBACK} requires {@code provider == STUB}.
 */
public record AlterEgoResponse(
        GeneratedCharacter character,
        Poster poster,
        ResponseMeta meta
) {

    /**
     * Wire shape for the {@code poster} field — derived from {@link PosterImage}
     * but exposes the data URL instead of the raw bytes.
     */
    public record Poster(String dataUrl, String mediaType, int widthPx, int heightPx) {
        public static Poster fromImage(PosterImage image) {
            return new Poster(image.toDataUrl(), image.mediaType(), image.widthPx(), image.heightPx());
        }
    }

    /**
     * Response metadata. {@code reason} is {@code null} on the happy path
     * ({@code outcome == REAL}) and non-null on any fallback outcome.
     * {@code provider} is mandatory and co-determined with {@code outcome}
     * by the FR-1612 invariant.
     *
     * <p>{@code @JsonInclude(NON_NULL)} ensures the {@code reason} field is
     * omitted from the JSON on the happy path (matching the contract in
     * {@code specs/016-falai-image-provider/contracts/alter-egos.openapi.yaml}).
     * The {@code provider} field is always serialised because it is mandatory.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ResponseMeta(
            Outcome outcome,
            UUID correlationId,
            FallbackReason reason,
            Provider provider
    ) {

        public ResponseMeta {
            Objects.requireNonNull(outcome, "outcome is required");
            Objects.requireNonNull(correlationId, "correlationId is required");
            Objects.requireNonNull(provider, "provider is required");
            if (outcome == Outcome.REAL && reason != null) {
                throw new IllegalArgumentException(
                        "reason must be null when outcome is REAL; got " + reason);
            }
            if (outcome == Outcome.FALLBACK && reason == null) {
                throw new IllegalArgumentException(
                        "reason is required when outcome is FALLBACK");
            }
            if (outcome == Outcome.REAL && provider == Provider.STUB) {
                throw new IllegalArgumentException(
                        "outcome REAL requires a real provider (gemini|falai); got STUB");
            }
            if (outcome == Outcome.FALLBACK && provider != Provider.STUB) {
                throw new IllegalArgumentException(
                        "outcome FALLBACK requires provider STUB; got " + provider);
            }
        }

        /** Convenience factory for the real-provider success path. */
        public static ResponseMeta real(UUID correlationId, Provider provider) {
            return new ResponseMeta(Outcome.REAL, correlationId, null, provider);
        }

        /**
         * Convenience factory for the fallback path. Always sets
         * {@code provider = STUB} per FR-1612 — the response body never
         * names the real provider that was attempted-and-failed (that lives
         * only in the FR-1613 log line).
         */
        public static ResponseMeta fallback(UUID correlationId, FallbackReason reason) {
            return new ResponseMeta(Outcome.FALLBACK, correlationId, reason, Provider.STUB);
        }
    }

    /**
     * Generation outcome flag. Both values return HTTP 200 — the user-facing
     * contract is "you always get a poster" (001 FR-018).
     *
     * <p>003 delta: wire value {@code "success"} renamed to {@code "real"}.
     * Forward-compatible clients SHOULD treat any unknown outcome value as
     * {@code FALLBACK} for graceful degradation.
     */
    public enum Outcome {
        REAL("real"),
        FALLBACK("fallback");

        private final String wire;

        Outcome(String wire) {
            this.wire = wire;
        }

        @JsonValue
        public String wire() {
            return wire;
        }

        @JsonCreator
        public static Outcome fromWire(@JsonProperty String value) {
            for (Outcome o : values()) {
                if (o.wire.equals(value)) return o;
            }
            throw new IllegalArgumentException("Unknown outcome: " + value);
        }
    }
}

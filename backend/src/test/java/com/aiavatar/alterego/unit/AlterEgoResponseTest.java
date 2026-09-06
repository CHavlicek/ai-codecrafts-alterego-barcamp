package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.AlterEgoResponse.Outcome;
import com.aiavatar.alterego.domain.model.AlterEgoResponse.ResponseMeta;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.Provider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link AlterEgoResponse} wire contract.
 *
 * <p>003 invariants (carried over):
 * <ul>
 *   <li>{@link Outcome#REAL} serialises to {@code "real"}.</li>
 *   <li>{@code @JsonInclude(NON_NULL)} omits {@code reason} on REAL outcome.</li>
 *   <li>Compact constructor rejects REAL+reason and FALLBACK+null-reason.</li>
 * </ul>
 *
 * <p>016 additions:
 * <ul>
 *   <li>{@code provider} is mandatory and serialises on every response.</li>
 *   <li>Compact constructor rejects (REAL, STUB), (FALLBACK, GEMINI),
 *       (FALLBACK, FALAI), and {@code provider == null}.</li>
 *   <li>{@link ResponseMeta#real(UUID, Provider)} requires a real provider.</li>
 *   <li>{@link ResponseMeta#fallback(UUID, FallbackReason)} always sets
 *       {@code provider = STUB}.</li>
 * </ul>
 */
class AlterEgoResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void outcomeRealSerialisesToRealWire() throws Exception {
        String json = mapper.writeValueAsString(Outcome.REAL);
        assertEquals("\"real\"", json);
    }

    @Test
    void outcomeFallbackSerialisesToFallbackWire() throws Exception {
        String json = mapper.writeValueAsString(Outcome.FALLBACK);
        assertEquals("\"fallback\"", json);
    }

    @Test
    void realFactoryProducesNullReasonAndCarriesProvider() {
        UUID id = UUID.randomUUID();
        ResponseMeta meta = ResponseMeta.real(id, Provider.GEMINI);
        assertEquals(Outcome.REAL, meta.outcome());
        assertEquals(id, meta.correlationId());
        assertNull(meta.reason());
        assertEquals(Provider.GEMINI, meta.provider());
    }

    @Test
    void realFactoryAcceptsFalAiProvider() {
        ResponseMeta meta = ResponseMeta.real(UUID.randomUUID(), Provider.FALAI);
        assertEquals(Provider.FALAI, meta.provider());
    }

    @Test
    void fallbackFactoryCarriesReasonAndAlwaysSetsStubProvider() {
        UUID id = UUID.randomUUID();
        ResponseMeta meta = ResponseMeta.fallback(id, FallbackReason.RATE_LIMITED);
        assertEquals(Outcome.FALLBACK, meta.outcome());
        assertEquals(id, meta.correlationId());
        assertEquals(FallbackReason.RATE_LIMITED, meta.reason());
        assertEquals(Provider.STUB, meta.provider());
    }

    @Test
    void compactConstructorRejectsRealWithReason() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseMeta(Outcome.REAL, UUID.randomUUID(),
                        FallbackReason.TIMEOUT, Provider.GEMINI));
    }

    @Test
    void compactConstructorRejectsFallbackWithoutReason() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseMeta(Outcome.FALLBACK, UUID.randomUUID(),
                        null, Provider.STUB));
    }

    @Test
    void compactConstructorRejectsRealWithStubProvider() {
        // FR-1612 invariant: outcome=REAL requires provider ∈ {GEMINI, FALAI}.
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseMeta(Outcome.REAL, UUID.randomUUID(),
                        null, Provider.STUB));
    }

    @Test
    void compactConstructorRejectsFallbackWithGeminiProvider() {
        // FR-1612 invariant: outcome=FALLBACK requires provider == STUB.
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseMeta(Outcome.FALLBACK, UUID.randomUUID(),
                        FallbackReason.NETWORK_ERROR, Provider.GEMINI));
    }

    @Test
    void compactConstructorRejectsFallbackWithFalaiProvider() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResponseMeta(Outcome.FALLBACK, UUID.randomUUID(),
                        FallbackReason.TIMEOUT, Provider.FALAI));
    }

    @Test
    void compactConstructorRejectsNullProvider() {
        assertThrows(NullPointerException.class,
                () -> new ResponseMeta(Outcome.REAL, UUID.randomUUID(), null, null));
    }

    @Test
    void reasonOmittedFromJsonWhenOutcomeIsReal() throws Exception {
        ResponseMeta meta = ResponseMeta.real(UUID.randomUUID(), Provider.GEMINI);
        String json = mapper.writeValueAsString(meta);
        ObjectNode parsed = (ObjectNode) mapper.readTree(json);
        assertFalse(parsed.has("reason"),
                () -> "reason must be omitted on REAL outcome; got " + json);
        assertEquals("real", parsed.get("outcome").asText());
        assertEquals("gemini", parsed.get("provider").asText());
    }

    @Test
    void providerAlwaysPresentInJson() throws Exception {
        // FR-1612: provider is mandatory on every response. Both happy path
        // (real, gemini) and fallback (stub) MUST carry it on the wire.
        ResponseMeta real = ResponseMeta.real(UUID.randomUUID(), Provider.FALAI);
        ResponseMeta fallback = ResponseMeta.fallback(UUID.randomUUID(),
                FallbackReason.NOT_CONFIGURED);

        ObjectNode realJson = (ObjectNode) mapper.readTree(mapper.writeValueAsString(real));
        ObjectNode fallbackJson = (ObjectNode) mapper.readTree(mapper.writeValueAsString(fallback));

        assertEquals("falai", realJson.get("provider").asText());
        assertEquals("stub", fallbackJson.get("provider").asText());
    }

    @Test
    void reasonPresentInJsonWhenOutcomeIsFallback() throws Exception {
        ResponseMeta meta = ResponseMeta.fallback(UUID.randomUUID(), FallbackReason.NOT_CONFIGURED);
        String json = mapper.writeValueAsString(meta);
        ObjectNode parsed = (ObjectNode) mapper.readTree(json);
        assertEquals("fallback", parsed.get("outcome").asText());
        assertEquals("not_configured", parsed.get("reason").asText());
        assertEquals("stub", parsed.get("provider").asText());
    }
}

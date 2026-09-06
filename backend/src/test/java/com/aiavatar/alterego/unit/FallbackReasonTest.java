package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.domain.model.FallbackReason;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T008 — {@link FallbackReason} wire contract. Round-trips every value and
 * asserts that unknown wire values fail loudly. Values are spec-pinned by
 * 003 FR-218 and MUST NOT drift without a spec amendment.
 */
class FallbackReasonTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void allValuesRoundTripThroughWireValue() throws Exception {
        for (FallbackReason r : FallbackReason.values()) {
            String json = mapper.writeValueAsString(r);
            FallbackReason back = mapper.readValue(json, FallbackReason.class);
            assertEquals(r, back, () -> "round-trip mismatch for " + r);
        }
    }

    @Test
    void wireValuesMatchSpecFr218() {
        assertEquals("not_configured", FallbackReason.NOT_CONFIGURED.wire());
        assertEquals("network_error", FallbackReason.NETWORK_ERROR.wire());
        assertEquals("rate_limited", FallbackReason.RATE_LIMITED.wire());
        assertEquals("timeout", FallbackReason.TIMEOUT.wire());
        assertEquals("malformed_response", FallbackReason.MALFORMED_RESPONSE.wire());
        assertEquals("safety_refused", FallbackReason.SAFETY_REFUSED.wire());
    }

    @Test
    void unknownWireValueRejectedByJsonCreator() {
        assertThrows(IllegalArgumentException.class,
                () -> FallbackReason.fromWire("totally_made_up"));
    }

    @Test
    void unknownWireValueRejectedByJackson() {
        // Quoted JSON string → invoked via @JsonCreator; Jackson wraps the
        // IllegalArgumentException in a ValueInstantiationException, whose
        // cause carries the original IAE.
        assertThrows(Exception.class,
                () -> mapper.readValue("\"mystery_code\"", FallbackReason.class));
    }

    @Test
    void enumHasExactlySixValuesPerSpec() {
        // Guard against silent enum growth. If this assertion fires, the spec
        // (FR-218) must be amended first and this test updated in lockstep.
        assertEquals(6, FallbackReason.values().length,
                "FR-218 pins the enum to exactly 6 values");
    }
}

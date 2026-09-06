package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.domain.model.Provider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 016 T006 — wire contract for {@link Provider} (FR-1612).
 *
 * <ul>
 *   <li>Three values: {@code GEMINI}, {@code FALAI}, {@code STUB}.</li>
 *   <li>Wire round-trip via {@code @JsonValue} / {@code @JsonCreator}.</li>
 *   <li>Unknown wire values are rejected.</li>
 * </ul>
 */
class ProviderTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void hasExactlyThreeValues() {
        assertEquals(3, Provider.values().length);
    }

    @Test
    void geminiSerialisesToGeminiWire() throws Exception {
        assertEquals("\"gemini\"", mapper.writeValueAsString(Provider.GEMINI));
    }

    @Test
    void falaiSerialisesToFalaiWire() throws Exception {
        assertEquals("\"falai\"", mapper.writeValueAsString(Provider.FALAI));
    }

    @Test
    void stubSerialisesToStubWire() throws Exception {
        assertEquals("\"stub\"", mapper.writeValueAsString(Provider.STUB));
    }

    @Test
    void wireValuesRoundTrip() throws Exception {
        for (Provider p : Provider.values()) {
            String json = mapper.writeValueAsString(p);
            Provider parsed = mapper.readValue(json, Provider.class);
            assertEquals(p, parsed);
        }
    }

    @Test
    void fromWireRejectsUnknownValue() {
        assertThrows(IllegalArgumentException.class,
                () -> Provider.fromWire("openai"));
    }

    @Test
    void fromWireMatchesEnumValues() {
        assertEquals(Provider.GEMINI, Provider.fromWire("gemini"));
        assertEquals(Provider.FALAI, Provider.fromWire("falai"));
        assertEquals(Provider.STUB, Provider.fromWire("stub"));
    }
}

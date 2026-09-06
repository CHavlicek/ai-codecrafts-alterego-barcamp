package com.aiavatar.alterego.unit.gemini;

import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterResponseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * T008 — happy-path coverage for {@link GeminiCharacterResponseParser} (014).
 *
 * <p>Asserts that on a well-formed JSON body:
 * <ul>
 *   <li>The four traits round-trip into a {@link GeneratedCharacter} after NFC + trim.</li>
 *   <li>{@code heroTitleLine1} is the user's first name uppercased — regardless of any
 *       value the JSON may have carried for that field (FR-1408).</li>
 *   <li>{@code superpowers.size() == 3}.</li>
 * </ul>
 *
 * <p>The full malformed-fixture matrix (missing key, four superpowers, blank trait,
 * overlong trait, NFC + emoji boundary cases) lands with {@code GeminiCharacterResponseParserTest}
 * extension in T021 / US3.
 */
class GeminiCharacterResponseParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiCharacterResponseParser parser = new GeminiCharacterResponseParser();

    @Test
    void happyPathReturnsGeneratedCharacterWithUppercasedFirstName() throws Exception {
        JsonNode body = mapper.readTree("""
                {
                  "heroTitleLine2": "The Test-Forged Sentinel",
                  "tagline": "GUARDS THE GREEN BUILD.",
                  "superpowers": [
                    "Pinpoints flakes from one stack frame",
                    "Reads CI logs in dimmed-terminal half-light",
                    "Rewrites assertions other people understand"
                  ],
                  "quote": "The test that fails twice is the spec."
                }
                """);

        GeneratedCharacter character = parser.parse(body, "paula");

        assertNotNull(character);
        assertEquals("PAULA", character.heroTitleLine1(),
                "FR-1408: heroTitleLine1 MUST be firstName.toUpperCase regardless of LLM payload");
        assertEquals("The Test-Forged Sentinel", character.heroTitleLine2());
        assertEquals("GUARDS THE GREEN BUILD.", character.tagline());
        assertEquals(3, character.superpowers().size());
        assertEquals("Pinpoints flakes from one stack frame", character.superpowers().get(0));
        assertEquals("Reads CI logs in dimmed-terminal half-light", character.superpowers().get(1));
        assertEquals("Rewrites assertions other people understand", character.superpowers().get(2));
        assertEquals("The test that fails twice is the spec.", character.quote());
    }

    @Test
    void firstNameInJsonBodyIsIgnoredAndOverwrittenWithUserSuppliedFirstName() throws Exception {
        // Even if the LLM helpfully includes a heroTitleLine1, the parser MUST
        // discard it and substitute firstName.toUpperCase. The schema doesn't
        // ask for line 1, but defence-in-depth: don't trust whatever lands.
        JsonNode body = mapper.readTree("""
                {
                  "heroTitleLine1": "DIFFERENT-NAME-FROM-LLM",
                  "heroTitleLine2": "The Watcher",
                  "tagline": "WATCHES THE WATCHERS.",
                  "superpowers": ["s1", "s2", "s3"],
                  "quote": "q"
                }
                """);

        GeneratedCharacter character = parser.parse(body, "Paula");

        assertEquals("PAULA", character.heroTitleLine1(),
                "FR-1408: parser owns the firstName substitution; LLM's value MUST be discarded");
    }

    @Test
    void firstNameIsTrimmedThenUppercased() throws Exception {
        JsonNode body = mapper.readTree("""
                {
                  "heroTitleLine2": "x",
                  "tagline": "y",
                  "superpowers": ["a", "b", "c"],
                  "quote": "q"
                }
                """);

        // Spec: the first name lands trimmed before uppercasing — matches the
        // existing StubCharacterGenerator convention so swapping providers
        // doesn't change the heroTitleLine1 output for any given input.
        GeneratedCharacter character = parser.parse(body, "  paula  ");
        assertEquals("PAULA", character.heroTitleLine1());
    }

    // ─── 014 / T021: malformed-fixture matrix (FR-1406 / FR-1407) ──────────

    @ParameterizedTest(name = "non-object body → MALFORMED_RESPONSE: {0}")
    @ValueSource(strings = {
            "[1,2,3]",       // JSON array, not object
            "\"a string\"",  // JSON primitive (string)
            "42",            // JSON primitive (number)
            "true",          // JSON primitive (boolean)
            "null",          // JSON null
    })
    void nonObjectBodyMapsToMalformedResponse(String json) throws Exception {
        JsonNode body = mapper.readTree(json);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void nullBodyMapsToMalformedResponse() {
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(null, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @ParameterizedTest(name = "missing key {0} → MALFORMED_RESPONSE")
    @ValueSource(strings = {"heroTitleLine2", "tagline", "superpowers", "quote"})
    void missingRequiredKeyMapsToMalformedResponse(String missingKey) throws Exception {
        String body = """
                {
                  "heroTitleLine2": "x",
                  "tagline": "y",
                  "superpowers": ["a", "b", "c"],
                  "quote": "q"
                }
                """;
        // Strip the named key by reading then mutating.
        com.fasterxml.jackson.databind.node.ObjectNode obj =
                (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(body);
        obj.remove(missingKey);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(obj, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @ParameterizedTest(name = "{0} superpowers → MALFORMED_RESPONSE")
    @ValueSource(ints = {0, 1, 2, 4, 5, 10})
    void wrongSuperpowerCountMapsToMalformedResponse(int count) throws Exception {
        StringBuilder arr = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) arr.append(',');
            arr.append("\"s").append(i).append('"');
        }
        arr.append(']');
        String json = "{\"heroTitleLine2\":\"x\",\"tagline\":\"y\","
                + "\"superpowers\":" + arr + ",\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void superpowersAsStringMapsToMalformedResponse() throws Exception {
        String json = "{\"heroTitleLine2\":\"x\",\"tagline\":\"y\","
                + "\"superpowers\":\"three powers as one string\",\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void traitAsNumberMapsToMalformedResponse() throws Exception {
        String json = "{\"heroTitleLine2\":42,\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void traitAsNullMapsToMalformedResponse() throws Exception {
        String json = "{\"heroTitleLine2\":null,\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @ParameterizedTest(name = "blank trait variant {0} → MALFORMED_RESPONSE")
    @ValueSource(strings = {"", " ", "   ", "\t", "\n", "  \t  \n  "})
    void blankTraitAfterTrimMapsToMalformedResponse(String blank) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
        body.put("heroTitleLine2", "x");
        body.put("tagline", blank);
        body.putArray("superpowers").add("a").add("b").add("c");
        body.put("quote", "q");
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void blankSuperpowerEntryMapsToMalformedResponse() throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
        body.put("heroTitleLine2", "x");
        body.put("tagline", "y");
        body.putArray("superpowers").add("ok").add("   ").add("ok2");
        body.put("quote", "q");
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void exactly100CodepointTraitPasses() throws Exception {
        // Boundary case — 100 ASCII code points after trim. MUST pass.
        String exactly100 = "x".repeat(100);
        String json = "{\"heroTitleLine2\":\"" + exactly100 + "\",\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GeneratedCharacter character = parser.parse(body, "Paula");
        assertEquals(100, character.heroTitleLine2().codePointCount(0, character.heroTitleLine2().length()));
    }

    @Test
    void over100CodepointTraitMapsToMalformedResponse() throws Exception {
        String tooLong = "x".repeat(101);
        String json = "{\"heroTitleLine2\":\"" + tooLong + "\",\"tagline\":\"y\","
                + "\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void over100CodepointSuperpowerEntryMapsToMalformedResponse() throws Exception {
        String tooLong = "x".repeat(101);
        com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
        body.put("heroTitleLine2", "x");
        body.put("tagline", "y");
        body.putArray("superpowers").add("ok").add(tooLong).add("ok2");
        body.put("quote", "q");
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void traitWithLeadingWhitespacePushingItOver100CodepointsAfterTrimStillPasses() throws Exception {
        // Spec FR-1406: "after trimming". A trait with 100 codepoints of
        // content padded by whitespace MUST pass (whitespace doesn't count).
        String paddedHundred = "  " + "y".repeat(100) + "   ";
        String json = "{\"heroTitleLine2\":\"x\",\"tagline\":" + mapper.writeValueAsString(paddedHundred)
                + ",\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GeneratedCharacter character = parser.parse(body, "Paula");
        // The stored value is trimmed to exactly 100 'y's.
        assertEquals("y".repeat(100), character.tagline());
    }

    @Test
    void emojiFamilyCountsAsMultipleCodepoints() throws Exception {
        // The "family: man, woman, girl, boy" emoji (👨‍👩‍👧‍👦) is a ZWJ sequence
        // of 7 code points (4 emojis + 3 ZWJ joiners). This is ONE visible
        // grapheme cluster but SEVEN Unicode code points. Per clarification
        // Q1 we count code points, not graphemes — so a 14-emoji-family trait
        // would be 7×14 = 98 code points (passes), and 15 would be 105 (fails).
        String fourteenFamilies = "👨‍👩‍👧‍👦"
                .repeat(14);
        String json = "{\"heroTitleLine2\":" + mapper.writeValueAsString(fourteenFamilies)
                + ",\"tagline\":\"y\",\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GeneratedCharacter character = parser.parse(body, "Paula");
        assertEquals(98, character.heroTitleLine2().codePointCount(0, character.heroTitleLine2().length()),
                "14 family-emoji ZWJ sequences = 98 code points, under the 100 cap");
    }

    @Test
    void fifteenEmojiFamiliesIsOver100CodepointsAndMapsToMalformedResponse() throws Exception {
        String fifteenFamilies = "👨‍👩‍👧‍👦"
                .repeat(15);
        String json = "{\"heroTitleLine2\":" + mapper.writeValueAsString(fifteenFamilies)
                + ",\"tagline\":\"y\",\"superpowers\":[\"a\",\"b\",\"c\"],\"quote\":\"q\"}";
        JsonNode body = mapper.readTree(json);
        GenerationFailure ex = assertThrows(GenerationFailure.class,
                () -> parser.parse(body, "Paula"));
        assertEquals(FallbackReason.MALFORMED_RESPONSE, ex.reason());
    }

    @Test
    void combiningAcuteIsNfcNormalisedToOneCodepointBeforeCounting() throws Exception {
        // "Café" written with combining acute (U+0301) is "Café" = 5 code
        // points before NFC; after NFC normalisation it becomes "Café" = 4
        // code points. The parser counts the NFC-normalised form, so a trait
        // built up to be exactly 100 NFC code points (regardless of pre-NFC
        // form) must pass.
        String composed = "Café — RIDES INTO PROD";
        String decomposedAcute = "Café — RIDES INTO PROD";
        // Both should produce the same stored value after NFC.
        com.fasterxml.jackson.databind.node.ObjectNode body = mapper.createObjectNode();
        body.put("heroTitleLine2", "y");
        body.put("tagline", decomposedAcute);
        body.putArray("superpowers").add("a").add("b").add("c");
        body.put("quote", "q");

        GeneratedCharacter character = parser.parse(body, "Paula");
        assertEquals(composed, character.tagline(),
                "decomposed combining-acute MUST be NFC-normalised to composed form");
    }

    @Test
    void extraKeysAreToleratedNotRejected() throws Exception {
        // FR-1406 says required keys must be present; extra keys are
        // tolerated and ignored — defence against future Gemini diagnostic
        // fields (e.g. _metadata, _safetyAttributes).
        String json = """
                {
                  "heroTitleLine2": "x",
                  "tagline": "y",
                  "superpowers": ["a", "b", "c"],
                  "quote": "q",
                  "_metadata": {"future": "field"},
                  "extraField": "ignored"
                }
                """;
        JsonNode body = mapper.readTree(json);
        GeneratedCharacter character = parser.parse(body, "Paula");
        assertNotNull(character);
        assertEquals("x", character.heroTitleLine2());
    }

    @Test
    void traitsAreNfcNormalisedAndTrimmedOnHappyPath() throws Exception {
        // Trim: a trailing space in a trait MUST not survive into the
        // GeneratedCharacter (spec FR-1406: "after trimming"). NFC: a
        // combining-acute "e + U+0301" MUST be folded to "é" so the field
        // survives the ≤100-codepoint cap unambiguously.
        String composedE = "Café";              // "Café" — 4 NFC code points
        String decomposedE = "Café";           // "Café" written as 'C','a','f','e','◌́' — 5 code points pre-NFC
        JsonNode body = mapper.readTree("""
                {
                  "heroTitleLine2": "  The Composed Sentinel  ",
                  "tagline": "%s — RIDES INTO PROD",
                  "superpowers": ["a", "b", "c"],
                  "quote": "q"
                }
                """.formatted(decomposedE));

        GeneratedCharacter character = parser.parse(body, "paula");
        assertEquals("The Composed Sentinel", character.heroTitleLine2(),
                "trait MUST be trimmed before being stored");
        assertEquals(composedE + " — RIDES INTO PROD", character.tagline(),
                "trait MUST be NFC-normalised before being stored");
    }
}

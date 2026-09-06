package com.aiavatar.alterego.unit.gemini;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterClient;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterGenerator;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterPromptBuilder;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterResponseParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T009 — orchestration coverage for {@link GeminiCharacterGenerator} (014).
 *
 * <p>Asserts:
 * <ul>
 *   <li>FR-1403 — when {@code GeminiProperties.isConfigured()} is false,
 *       throws {@code GenerationFailure(NOT_CONFIGURED)} pre-HTTP. The
 *       prompt builder, the HTTP client, and the parser are NEVER invoked.</li>
 *   <li>FR-1401 — happy path: prompt builder is called once with the trimmed
 *       request, client is called with {@code props.textModelId()} + the
 *       prompt, parser is called with the client's JSON node + the trimmed
 *       firstName; the parser's {@link GeneratedCharacter} is returned
 *       verbatim.</li>
 *   <li>FR-1410 — every {@link FallbackReason} that the client throws is
 *       propagated unchanged so the orchestrator can populate
 *       {@code meta.reason}.</li>
 * </ul>
 */
class GeminiCharacterGeneratorTest {

    private GeminiCharacterClient client;
    private GeminiCharacterPromptBuilder promptBuilder;
    private GeminiCharacterResponseParser parser;

    @BeforeEach
    void setUp() {
        client = mock(GeminiCharacterClient.class);
        promptBuilder = mock(GeminiCharacterPromptBuilder.class);
        parser = mock(GeminiCharacterResponseParser.class);
    }

    @Test
    void blankApiKeyShortCircuitsToNotConfiguredWithoutContactingGemini() {
        GeminiProperties noKey = propsWith("");
        GeminiCharacterGenerator gen = new GeminiCharacterGenerator(client, promptBuilder, parser, noKey);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleRequest()));
        assertEquals(FallbackReason.NOT_CONFIGURED, ex.reason());

        verify(client, never()).generateText(any(), any());
        verify(promptBuilder, never()).build(any());
        verify(parser, never()).parse(any(), any());
    }

    @Test
    void nullApiKeyShortCircuitsToNotConfigured() {
        GeminiProperties noKey = propsWith(null);
        GeminiCharacterGenerator gen = new GeminiCharacterGenerator(client, promptBuilder, parser, noKey);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleRequest()));
        assertEquals(FallbackReason.NOT_CONFIGURED, ex.reason());
    }

    @Test
    void whitespaceApiKeyShortCircuitsToNotConfigured() {
        GeminiProperties noKey = propsWith("   ");
        GeminiCharacterGenerator gen = new GeminiCharacterGenerator(client, promptBuilder, parser, noKey);
        assertEquals(FallbackReason.NOT_CONFIGURED,
                assertThrows(GenerationFailure.class, () -> gen.generate(sampleRequest())).reason());
    }

    @Test
    void happyPathDelegatesPromptBuilderClientAndParserInOrder() throws Exception {
        GeminiProperties configured = propsWith("sk-test");
        GeminiCharacterGenerator gen = new GeminiCharacterGenerator(client, promptBuilder, parser, configured);

        AlterEgoRequest request = sampleRequest();
        when(promptBuilder.build(any(AlterEgoRequest.class))).thenReturn("BUILT-PROMPT");

        ObjectMapper mapper = new ObjectMapper();
        JsonNode geminiBody = mapper.readTree("""
                {"heroTitleLine2":"x","tagline":"y","superpowers":["a","b","c"],"quote":"q"}
                """);
        when(client.generateText(eq(configured.textModelId()), eq("BUILT-PROMPT")))
                .thenReturn(geminiBody);

        GeneratedCharacter expected = new GeneratedCharacter(
                "PAULA", "x", "y", List.of("a", "b", "c"), "q");
        when(parser.parse(eq(geminiBody), eq(request.firstName()))).thenReturn(expected);

        GeneratedCharacter actual = gen.generate(request);

        assertSame(expected, actual,
                "generator MUST return the parser's value verbatim");

        InOrder order = inOrder(promptBuilder, client, parser);
        order.verify(promptBuilder).build(any(AlterEgoRequest.class));
        order.verify(client).generateText(eq(configured.textModelId()), eq("BUILT-PROMPT"));
        order.verify(parser).parse(eq(geminiBody), eq(request.firstName()));
    }

    @Test
    void firstNameIsTrimmedBeforeBeingPassedToPromptBuilderAndParser() {
        GeminiProperties configured = propsWith("sk-test");
        GeminiCharacterGenerator gen = new GeminiCharacterGenerator(client, promptBuilder, parser, configured);

        AlterEgoRequest withSpaces = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "  Paula  ", null);

        when(promptBuilder.build(any())).thenReturn("P");
        when(client.generateText(any(), any())).thenReturn(emptyJson());
        when(parser.parse(any(), any())).thenReturn(new GeneratedCharacter(
                "PAULA", "x", "y", List.of("a", "b", "c"), "q"));

        gen.generate(withSpaces);

        // Both the prompt builder and the parser see the trimmed firstName,
        // never the spaces-padded original.
        verify(promptBuilder).build(org.mockito.ArgumentMatchers.argThat(
                req -> "Paula".equals(req.firstName())));
        verify(parser).parse(any(), eq("Paula"));
    }

    @ParameterizedTest(name = "client throws {0} → generator propagates it unchanged")
    @EnumSource(value = FallbackReason.class, names = {"NOT_CONFIGURED"},
            mode = EnumSource.Mode.EXCLUDE)
    void allClientFailureReasonsPropagateUnchanged(FallbackReason reason) {
        GeminiProperties configured = propsWith("sk-test");
        GeminiCharacterGenerator gen = new GeminiCharacterGenerator(client, promptBuilder, parser, configured);
        when(promptBuilder.build(any())).thenReturn("P");
        when(client.generateText(any(), any()))
                .thenThrow(new GenerationFailure(reason, "simulated " + reason.wire()));

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleRequest()));
        assertEquals(reason, ex.reason(),
                () -> "generator MUST propagate client GenerationFailure reason unchanged; got "
                        + ex.reason() + " for injected " + reason);

        // Parser is never called once the client throws.
        verify(parser, never()).parse(any(), any());
    }

    private static GeminiProperties propsWith(String apiKey) {
        return new GeminiProperties(apiKey,
                "gemini-test-image-model", "http://x",
                25_000, 4 * 1024 * 1024, 1536, 1024, 0.85,
                "gemini-2.5-flash", 15_000);
    }

    private static AlterEgoRequest sampleRequest() {
        return new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
    }

    private static JsonNode emptyJson() {
        try {
            return new ObjectMapper().readTree("{}");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

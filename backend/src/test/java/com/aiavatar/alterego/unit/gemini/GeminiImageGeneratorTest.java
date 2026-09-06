package com.aiavatar.alterego.unit.gemini;

import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiClient;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiImageGenerator;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiPromptBuilder;
import com.aiavatar.alterego.infrastructure.photo.PhotoReducer;
import com.aiavatar.alterego.infrastructure.photo.PhotoReductionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T023 — happy-path orchestration coverage for {@link GeminiImageGenerator}.
 *
 * <ul>
 *   <li>When {@code GeminiProperties.isConfigured()} is false, throws
 *       {@code GenerationFailure(NOT_CONFIGURED)} PRE-HTTP — no call to
 *       {@link GeminiClient} (FR-212).</li>
 *   <li>When configured, delegates photo reduction → prompt build → client
 *       call in order, and returns the client's {@link PosterImage}.</li>
 * </ul>
 *
 * <p>Exception-mapping parametrised rows land with T034 (US3).
 */
class GeminiImageGeneratorTest {

    private GeminiClient client;
    private PhotoReducer reducer;
    private GeminiPromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        client = mock(GeminiClient.class);
        reducer = mock(PhotoReducer.class);
        promptBuilder = mock(GeminiPromptBuilder.class);
    }

    @Test
    void blankApiKeyShortCircuitsToNotConfiguredWithoutContactingGemini() {
        GeminiProperties noKey = propsWith("");
        GeminiImageGenerator gen = new GeminiImageGenerator(client, promptBuilder, reducer, noKey);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleCharacter(), sampleRequest(), samplePhoto()));
        assertEquals(FallbackReason.NOT_CONFIGURED, ex.reason());

        verify(client, never()).generateImage(any(), any(), any());
        verify(reducer, never()).reduce(any(), any());
        verify(promptBuilder, never()).build(any());
    }

    @Test
    void providerNameIsGemini() {
        // 016 FR-1612 — generator advertises its provider so AlterEgoService
        // can thread it into ResponseMeta.provider on the real-success path.
        GeminiImageGenerator gen = new GeminiImageGenerator(
                client, promptBuilder, reducer, propsWith("sk-test"));
        assertEquals("gemini", gen.provider().wire());
    }

    @Test
    void wantsExternalRetryDefaultsToTrue() {
        // 016 FR-1614 / Constitution Principle IV — the orchestrator's
        // 5-attempt RetryTemplate STILL wraps Gemini calls. fal.ai opts out;
        // Gemini does not.
        GeminiImageGenerator gen = new GeminiImageGenerator(
                client, promptBuilder, reducer, propsWith("sk-test"));
        org.junit.jupiter.api.Assertions.assertTrue(gen.externalRetry());
    }

    @Test
    void nullApiKeyShortCircuitsToNotConfigured() {
        GeminiProperties noKey = propsWith(null);
        GeminiImageGenerator gen = new GeminiImageGenerator(client, promptBuilder, reducer, noKey);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleCharacter(), sampleRequest(), samplePhoto()));
        assertEquals(FallbackReason.NOT_CONFIGURED, ex.reason());
    }

    @Test
    void happyPathDelegatesReducerPromptAndClientInOrder() {
        GeminiProperties configured = propsWith("sk-test");
        GeminiImageGenerator gen = new GeminiImageGenerator(client, promptBuilder, reducer, configured);

        PhotoPayload original = samplePhoto();
        PhotoPayload reduced = new PhotoPayload(new byte[]{9, 9}, "image/jpeg");
        when(reducer.reduce(eq(original), any(PhotoReductionConfig.class))).thenReturn(reduced);
        when(promptBuilder.build(any(AlterEgoRequest.class))).thenReturn("BUILT PROMPT");

        PosterImage expected = new PosterImage(new byte[]{1, 2, 3, 4}, "image/png", 100, 200);
        when(client.generateImage(eq(configured.modelId()), eq("BUILT PROMPT"), eq(reduced)))
                .thenReturn(expected);

        PosterImage actual = gen.generate(sampleCharacter(), sampleRequest(), original);

        assertNotNull(actual);
        assertEquals(expected, actual);
        verify(reducer).reduce(eq(original), any(PhotoReductionConfig.class));
        verify(promptBuilder).build(any(AlterEgoRequest.class));

        ArgumentCaptor<PhotoPayload> photoCap = ArgumentCaptor.forClass(PhotoPayload.class);
        verify(client).generateImage(eq(configured.modelId()), eq("BUILT PROMPT"),
                photoCap.capture());
        assertEquals(reduced, photoCap.getValue(),
                "client MUST receive the reducer's output, not the original photo");
    }

    // T034 — each FallbackReason emitted by GeminiClient MUST propagate
    // through GeminiImageGenerator unchanged so AlterEgoService can thread
    // it into ResponseMeta.reason (FR-218). Parametrised over every
    // non-NOT_CONFIGURED value (NOT_CONFIGURED is tested via the pre-HTTP
    // short-circuit above).

    @org.junit.jupiter.params.ParameterizedTest(name = "client throws {0} → generator propagates it unchanged")
    @org.junit.jupiter.params.provider.EnumSource(
            value = FallbackReason.class,
            names = {"NOT_CONFIGURED"},
            mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    void allClientFailureReasonsPropagateUnchanged(FallbackReason reason) {
        GeminiProperties configured = propsWith("sk-test");
        GeminiImageGenerator gen = new GeminiImageGenerator(client, promptBuilder, reducer, configured);
        when(reducer.reduce(any(), any(PhotoReductionConfig.class))).thenReturn(samplePhoto());
        when(promptBuilder.build(any())).thenReturn("P");
        when(client.generateImage(any(), any(), any()))
                .thenThrow(new GenerationFailure(reason, "simulated " + reason.wire()));

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleCharacter(), sampleRequest(), samplePhoto()));
        assertEquals(reason, ex.reason(),
                () -> "generator MUST propagate client's GenerationFailure reason unchanged; got "
                        + ex.reason() + " for injected " + reason);
    }

    private static GeminiProperties propsWith(String apiKey) {
        return new GeminiProperties(apiKey,
                "gemini-test-model", "http://x",
                25_000, 4 * 1024 * 1024, 1536, 1024, 0.85,
                "gemini-2.5-flash", 15_000);
    }

    private static AlterEgoRequest sampleRequest() {
        return new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
    }

    private static PhotoPayload samplePhoto() {
        return new PhotoPayload(new byte[]{1, 2, 3, 4}, "image/jpeg");
    }

    private static GeneratedCharacter sampleCharacter() {
        return new GeneratedCharacter("PAULA", "The Cloud Guardrail",
                "STILL SHIPS ON FRIDAYS.",
                List.of("P1", "P2", "P3"), "Some quote.");
    }
}

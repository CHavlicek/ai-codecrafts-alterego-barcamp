package com.aiavatar.alterego.unit.falai;

import com.aiavatar.alterego.infrastructure.config.FalAiProperties;
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
import com.aiavatar.alterego.infrastructure.provider.falai.FalAiClient;
import com.aiavatar.alterego.infrastructure.provider.falai.FalAiImageGenerator;
import com.aiavatar.alterego.infrastructure.provider.falai.FalAiPromptBuilder;
import com.aiavatar.alterego.infrastructure.photo.PhotoReducer;
import com.aiavatar.alterego.infrastructure.photo.PhotoReductionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 016 T027 — orchestration coverage for {@link FalAiImageGenerator}.
 *
 * <ul>
 *   <li>Blank api-key short-circuits to {@code GenerationFailure(NOT_CONFIGURED)}
 *       PRE-HTTP — neither client, nor reducer, nor prompt-builder run.</li>
 *   <li>{@code providerName()} returns {@code "falai"}.</li>
 *   <li>{@code wantsExternalRetry()} returns {@code false} so the orchestrator
 *       skips its 5-attempt retry wrapper for this provider.</li>
 *   <li>Happy path delegates reduce → prompt → client in order with the
 *       deadline computed from the injected {@link Clock}.</li>
 * </ul>
 */
class FalAiImageGeneratorTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-06T10:00:00Z");

    private FalAiClient client;
    private FalAiPromptBuilder promptBuilder;
    private PhotoReducer reducer;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        client = mock(FalAiClient.class);
        promptBuilder = mock(FalAiPromptBuilder.class);
        reducer = mock(PhotoReducer.class);
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    }

    @Test
    void blankApiKeyShortCircuitsToNotConfiguredWithoutContactingFalAi() {
        FalAiProperties noKey = propsWith("");
        FalAiImageGenerator gen = new FalAiImageGenerator(client, promptBuilder, reducer,
                noKey, fixedClock);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleCharacter(), sampleRequest(), samplePhoto()));
        assertEquals(FallbackReason.NOT_CONFIGURED, ex.reason());

        verify(client, never()).generateImage(any(), any(), any(), any());
        verify(reducer, never()).reduce(any(), any());
        verify(promptBuilder, never()).build(any());
    }

    @Test
    void nullApiKeyShortCircuitsToNotConfigured() {
        FalAiProperties noKey = propsWith(null);
        FalAiImageGenerator gen = new FalAiImageGenerator(client, promptBuilder, reducer,
                noKey, fixedClock);

        GenerationFailure ex = assertThrows(GenerationFailure.class, () ->
                gen.generate(sampleCharacter(), sampleRequest(), samplePhoto()));
        assertEquals(FallbackReason.NOT_CONFIGURED, ex.reason());
    }

    @Test
    void providerNameIsFalai() {
        FalAiImageGenerator gen = new FalAiImageGenerator(client, promptBuilder, reducer,
                propsWith("k"), fixedClock);
        assertEquals("falai", gen.provider().wire());
    }

    @Test
    void wantsExternalRetryReturnsFalse() {
        // 016 R8 — fal.ai opts out of the orchestrator's RetryTemplate
        // because the deadline-aware client manages its own per-step retries.
        FalAiImageGenerator gen = new FalAiImageGenerator(client, promptBuilder, reducer,
                propsWith("k"), fixedClock);
        assertFalse(gen.externalRetry());
    }

    @Test
    void happyPathDelegatesReducerPromptAndClientWithDeadline() {
        FalAiProperties props = propsWith("falai-test-key");
        FalAiImageGenerator gen = new FalAiImageGenerator(client, promptBuilder, reducer,
                props, fixedClock);

        PhotoPayload original = samplePhoto();
        PhotoPayload reduced = new PhotoPayload(new byte[]{9, 9, 9}, "image/jpeg");
        when(reducer.reduce(eq(original), any(PhotoReductionConfig.class))).thenReturn(reduced);
        when(promptBuilder.build(any(AlterEgoRequest.class))).thenReturn("BUILT FAL.AI PROMPT");

        PosterImage expected = new PosterImage(
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0},
                "image/jpeg", 100, 200);
        when(client.generateImage(eq(props), eq("BUILT FAL.AI PROMPT"), eq(reduced), any(Instant.class)))
                .thenReturn(expected);

        PosterImage actual = gen.generate(sampleCharacter(), sampleRequest(), original);

        assertNotNull(actual);
        assertEquals(expected, actual);
        verify(reducer).reduce(eq(original), any(PhotoReductionConfig.class));
        verify(promptBuilder, times(1)).build(any(AlterEgoRequest.class));

        // Deadline = now + endToEndTimeoutMs. Verify by capturing the arg.
        org.mockito.ArgumentCaptor<Instant> deadlineCap =
                org.mockito.ArgumentCaptor.forClass(Instant.class);
        verify(client).generateImage(eq(props), eq("BUILT FAL.AI PROMPT"),
                eq(reduced), deadlineCap.capture());
        Instant captured = deadlineCap.getValue();
        Instant expectedDeadline = FIXED_NOW.plusMillis(props.endToEndTimeoutMs());
        assertTrue(captured.equals(expectedDeadline) || captured.isAfter(expectedDeadline.minusMillis(10)),
                "deadline must be (clock.now + endToEndTimeoutMs); expected ~"
                        + expectedDeadline + ", got " + captured);
    }

    private static FalAiProperties propsWith(String apiKey) {
        return new FalAiProperties(
                apiKey,
                "fal-ai/nano-banana-pro/edit",
                "https://queue.fal.run",
                8_000, 5_000, 10_000,
                /* endToEndTimeoutMs */ 30_000,
                1_000, 5_000,
                4 * 1024 * 1024, 1536, 1024, 0.85);
    }

    private static AlterEgoRequest sampleRequest() {
        return new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
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

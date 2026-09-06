package com.aiavatar.alterego.unit.application;

import com.aiavatar.alterego.application.AlterEgoUseCase;
import com.aiavatar.alterego.application.port.CharacterGeneratorPort;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.application.port.ImageGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.application.pipeline.PosterPipeline;
import com.aiavatar.alterego.domain.policy.RandomCategorySelector;
import org.junit.jupiter.api.Test;
import org.springframework.retry.support.RetryTemplate;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit-level smoke test for {@link AlterEgoUseCase}. Asserts behaviour
 * parity with the legacy {@code AlterEgoService} on the three branches
 * the spec calls out (FR-2425):
 *
 *   1. happy path — primary returns → outcome=real, provider=primary.provider()
 *   2. GenerationFailure → outcome=fallback, reason carried through
 *   3. unexpected RuntimeException → outcome=fallback, reason=MALFORMED_RESPONSE
 *
 * 024 T012. Deeper coverage (every {@link FallbackReason}, retry-bypass
 * paths, structured-log assertions) lands as expansion of this file in a
 * follow-up commit.
 */
class AlterEgoUseCaseTest {

    private static final UUID CORRELATION = UUID.fromString("00000000-0000-0000-0000-000000000024");

    private final AlterEgoRequest request = new AlterEgoRequest(
            Pose.HEROIC, Archetype.BACKEND_DEV, Universe.STAR_WARS, Vibe.BUILDER,
            ArtStyle.OIL_PAINTING, "Sam", PhotoMode.SINGLE, null);
    private final PhotoPayload photo = new PhotoPayload(new byte[]{1, 2, 3}, "image/jpeg");
    private final GeneratedCharacter realChar = new GeneratedCharacter(
            "SAM", "the bold", "tagline", List.of("debug", "ship", "scale"), "quote");
    private final GeneratedCharacter fallbackChar = new GeneratedCharacter(
            "SAM", "fallback", "fb-tagline", List.of("fb1", "fb2", "fb3"), "fb-quote");
    private final PosterImage poster = new PosterImage(new byte[]{9, 9}, "image/png", 900, 1200);

    @Test
    void happyPath_realOutcomeWithPrimaryProvider() {
        AlterEgoUseCase useCase = newUseCase(Provider.GEMINI, ports -> {
            when(ports.primaryChar().generate(any())).thenReturn(realChar);
            when(ports.primaryImg().generate(any(), any(), any())).thenReturn(poster);
        });
        AlterEgoResponse resp = useCase.generate(request, photo, CORRELATION);
        assertEquals(AlterEgoResponse.Outcome.REAL, resp.meta().outcome());
        assertEquals(Provider.GEMINI, resp.meta().provider());
        assertEquals("SAM", resp.character().heroTitleLine1());
    }

    @Test
    void generationFailure_fallbackWithCarriedReason() {
        AlterEgoUseCase useCase = newUseCase(Provider.GEMINI, ports -> {
            when(ports.primaryChar().generate(any())).thenReturn(realChar);
            when(ports.primaryImg().generate(any(), any(), any()))
                    .thenThrow(new GenerationFailure(FallbackReason.RATE_LIMITED, "throttled"));
            when(ports.fallbackChar().generate(any())).thenReturn(fallbackChar);
            when(ports.fallbackImg().generate(any(), any(), any())).thenReturn(poster);
        });
        AlterEgoResponse resp = useCase.generate(request, photo, CORRELATION);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, resp.meta().outcome());
        assertEquals(FallbackReason.RATE_LIMITED, resp.meta().reason());
        assertEquals(Provider.STUB, resp.meta().provider());
    }

    @Test
    void unexpectedRuntimeException_fallbackWithMalformedResponse() {
        AlterEgoUseCase useCase = newUseCase(Provider.GEMINI, ports -> {
            when(ports.primaryChar().generate(any())).thenReturn(realChar);
            when(ports.primaryImg().generate(any(), any(), any()))
                    .thenThrow(new IllegalStateException("unexpected"));
            when(ports.fallbackChar().generate(any())).thenReturn(fallbackChar);
            when(ports.fallbackImg().generate(any(), any(), any())).thenReturn(poster);
        });
        AlterEgoResponse resp = useCase.generate(request, photo, CORRELATION);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, resp.meta().outcome());
        assertEquals(FallbackReason.MALFORMED_RESPONSE, resp.meta().reason());
    }

    // ── plumbing ─────────────────────────────────────────────────────────

    private record Ports(CharacterGeneratorPort primaryChar, ImageGeneratorPort primaryImg,
                         CharacterGeneratorPort fallbackChar, ImageGeneratorPort fallbackImg) {}

    private AlterEgoUseCase newUseCase(Provider primaryProvider,
                                       java.util.function.Consumer<Ports> stub) {
        CharacterGeneratorPort primaryChar = mock(CharacterGeneratorPort.class);
        ImageGeneratorPort primaryImg = mock(ImageGeneratorPort.class);
        CharacterGeneratorPort fallbackChar = mock(CharacterGeneratorPort.class);
        ImageGeneratorPort fallbackImg = mock(ImageGeneratorPort.class);
        lenient().when(primaryChar.externalRetry()).thenReturn(false);
        lenient().when(primaryImg.externalRetry()).thenReturn(false);
        lenient().when(primaryImg.provider()).thenReturn(primaryProvider);
        Ports ports = new Ports(primaryChar, primaryImg, fallbackChar, fallbackImg);
        stub.accept(ports);
        PosterPipeline pipeline = new PosterPipeline(java.util.List.of());  // identity
        return new AlterEgoUseCase(
                primaryChar, primaryImg, fallbackChar, fallbackImg,
                pipeline, RetryTemplate.builder().maxAttempts(1).build(),
                mock(RandomCategorySelector.class));
    }
}

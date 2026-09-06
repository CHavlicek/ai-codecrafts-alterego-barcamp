package com.aiavatar.alterego.application;

import com.aiavatar.alterego.application.port.CharacterGeneratorPort;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.application.port.ImageGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.prompt.RoleOfRecord;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.AlterEgoUserSelections;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.domain.policy.RandomCategorySelector;
import com.aiavatar.alterego.application.pipeline.PosterPipeline;
import com.aiavatar.alterego.application.pipeline.StageContext;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.DependsOn;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application-layer orchestrator for one alter-ego generate request
 * (Constitution Principles VII/VIII). Depends only on application-facing
 * ports — never on concrete infrastructure provider classes — and contains
 * no provider-identity branching. The fallback path is reached through the
 * same {@link ImageGeneratorPort} / {@link CharacterGeneratorPort} seam as
 * a normal generation; only a {@code @Qualifier("fallback")} discriminates
 * the bean.
 *
 * <p>024 rename of the legacy {@code AlterEgoService}. Behaviour, log
 * shape, and response shape are byte-identical (FR-2425).
 */
@Service
@DependsOn("providerProfileGuard")
public class AlterEgoUseCase {

    private static final Logger log = LoggerFactory.getLogger(AlterEgoUseCase.class);

    private static final String NO_PROVIDER_ATTEMPTED = "none";

    private final CharacterGeneratorPort primaryCharacter;
    private final ImageGeneratorPort primaryImage;
    private final CharacterGeneratorPort fallbackCharacter;
    private final ImageGeneratorPort fallbackImage;
    private final PosterPipeline posterPipeline;
    private final RetryTemplate retryTemplate;
    private final RandomCategorySelector randomCategorySelector;

    public AlterEgoUseCase(CharacterGeneratorPort primaryCharacter,
                           ImageGeneratorPort primaryImage,
                           @Qualifier("fallbackCharacterGenerator") CharacterGeneratorPort fallbackCharacter,
                           @Qualifier("fallbackImageGenerator") ImageGeneratorPort fallbackImage,
                           PosterPipeline posterPipeline,
                           RetryTemplate retryTemplate,
                           RandomCategorySelector randomCategorySelector) {
        this.primaryCharacter = primaryCharacter;
        this.primaryImage = primaryImage;
        this.fallbackCharacter = fallbackCharacter;
        this.fallbackImage = fallbackImage;
        this.posterPipeline = posterPipeline;
        this.retryTemplate = retryTemplate;
        this.randomCategorySelector = randomCategorySelector;
    }

    /**
     * 020 entry point: rolls Pose + Vibe uniformly at random, then delegates
     * to the canonical generate(...) method.
     */
    public AlterEgoResponse generate(AlterEgoUserSelections userSelections,
                                     PhotoPayload photo,
                                     UUID correlationId) {
        Pose rolledPose = randomCategorySelector.pickUniform(Pose.class);
        Vibe rolledVibe = randomCategorySelector.pickUniform(Vibe.class);
        AlterEgoRequest resolved = new AlterEgoRequest(
                rolledPose,
                userSelections.archetype(),
                userSelections.universe(),
                rolledVibe,
                userSelections.artStyle(),
                userSelections.firstName(),
                userSelections.photoMode(),
                userSelections.customRole(),
                userSelections.customUniverse());
        return generate(resolved, photo, correlationId);
    }

    public AlterEgoResponse generate(AlterEgoRequest request, PhotoPayload photo, UUID correlationId) {
        String attemptedProvider = primaryImage.provider() == Provider.STUB
                ? NO_PROVIDER_ATTEMPTED
                : primaryImage.provider().wire();

        try {
            GeneratedCharacter character = primaryCharacter.externalRetry()
                    ? retryTemplate.execute(ctx -> primaryCharacter.generate(request))
                    : primaryCharacter.generate(request);
            PosterImage rawPoster = primaryImage.externalRetry()
                    ? retryTemplate.execute(ctx -> primaryImage.generate(character, request, photo))
                    : primaryImage.generate(character, request, photo);
            PosterImage poster = posterPipeline.apply(rawPoster,
                    new StageContext(request.firstName(), RoleOfRecord.from(request).value(), character.quote()));
            Provider responseProvider = primaryImage.provider();
            log.info("event=generation.completed outcome=real provider={} correlationId={}",
                    responseProvider.wire(), correlationId,
                    StructuredArguments.kv("event", "generation.completed"),
                    StructuredArguments.kv("outcome", "real"),
                    StructuredArguments.kv("provider", responseProvider.wire()),
                    StructuredArguments.kv("attemptedProvider", attemptedProvider),
                    StructuredArguments.kv("correlationId", correlationId));
            return new AlterEgoResponse(
                    character,
                    AlterEgoResponse.Poster.fromImage(poster),
                    AlterEgoResponse.ResponseMeta.real(correlationId, responseProvider));
        } catch (GenerationFailure gf) {
            return handleFallback(request, photo, correlationId, gf.reason(), attemptedProvider, gf);
        } catch (RuntimeException e) {
            return handleFallback(request, photo, correlationId, FallbackReason.MALFORMED_RESPONSE,
                    attemptedProvider, e);
        }
    }

    private AlterEgoResponse handleFallback(AlterEgoRequest request, PhotoPayload photo,
                                            UUID correlationId, FallbackReason reason,
                                            String attemptedProvider, Throwable cause) {
        log.warn("event=generation.completed outcome=fallback provider=stub "
                        + "attemptedProvider={} reason={} correlationId={}",
                attemptedProvider, reason.wire(), correlationId,
                StructuredArguments.kv("event", "generation.completed"),
                StructuredArguments.kv("outcome", "fallback"),
                StructuredArguments.kv("provider", Provider.STUB.wire()),
                StructuredArguments.kv("attemptedProvider", attemptedProvider),
                StructuredArguments.kv("reason", reason.wire()),
                StructuredArguments.kv("seam", reason == FallbackReason.NOT_CONFIGURED ? "config" : "provider"),
                StructuredArguments.kv("correlationId", correlationId),
                cause);
        GeneratedCharacter fallbackChar = fallbackCharacter.generate(request);
        PosterImage rawFallback = fallbackImage.generate(fallbackChar, request, photo);
        PosterImage fallbackPoster = posterPipeline.apply(rawFallback,
                new StageContext(request.firstName(), RoleOfRecord.from(request).value(), fallbackChar.quote()));
        return new AlterEgoResponse(
                fallbackChar,
                AlterEgoResponse.Poster.fromImage(fallbackPoster),
                AlterEgoResponse.ResponseMeta.fallback(correlationId, reason));
    }
}

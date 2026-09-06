package com.aiavatar.alterego.infrastructure.provider.falai;

import org.springframework.context.annotation.Primary;


import com.aiavatar.alterego.infrastructure.config.FalAiProperties;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.application.port.ImageGeneratorPort;
import com.aiavatar.alterego.domain.model.Provider;
import com.aiavatar.alterego.infrastructure.photo.PhotoReducer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * {@link ImageGenerator} that calls fal.ai's
 * {@code nano-banana-pro/edit} image-edit model (016 FR-1606). Composes the
 * prompt via {@link FalAiPromptBuilder}, reduces the photo via the shared
 * {@link PhotoReducer} (016 R4), and delegates the queue-based exchange to
 * {@link FalAiClient}.
 *
 * <p>Wired under the {@code falai} Spring profile so the 001 stub
 * ({@code StubImageGenerator @Profile("default")}) and the 003 Gemini path
 * ({@code GeminiImageGenerator @Profile("gemini")}) stay independently
 * activatable. Activate via {@code SPRING_PROFILES_ACTIVE=falai} together
 * with {@code FAL_AI_API_KEY} (016 quickstart §2c). Activating both
 * {@code gemini} AND {@code falai} simultaneously refuses startup
 * (016 FR-1605 / {@code ProviderProfileGuard}).
 *
 * <p>{@link Primary} keeps the bean resolution deterministic if a
 * {@code @SpringBootTest} ever activates multiple profiles inadvertently
 * — same defensive-belt approach as 003.
 *
 * <p>016 FR-1614a: the orchestrator's {@code RetryTemplate} is bypassed for
 * fal.ai (see {@link #wantsExternalRetry()}). The 30 s end-to-end deadline
 * is enforced via the {@code Instant} captured here and passed into
 * {@link FalAiClient#generateImage}.
 *
 * <p>Pre-HTTP short-circuit: when {@link FalAiProperties#isConfigured()}
 * is false (blank API key), throws
 * {@code GenerationFailure(NOT_CONFIGURED)} immediately — the orchestrator
 * serves the fallback without a wasted outbound attempt (FR-1604).
 */
@Component
@Profile("falai")
@Primary
public class FalAiImageGenerator implements ImageGeneratorPort {

    private final FalAiClient client;
    private final FalAiPromptBuilder promptBuilder;
    private final PhotoReducer reducer;
    private final FalAiProperties props;
    private final Clock clock;

    public FalAiImageGenerator(FalAiClient client,
                               FalAiPromptBuilder promptBuilder,
                               PhotoReducer reducer,
                               FalAiProperties props,
                               Clock clock) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.reducer = reducer;
        this.props = props;
        this.clock = clock;
    }

    @Override
    public Provider provider() {
        return Provider.FALAI;
    }

    @Override
    public boolean externalRetry() {
        // 016 R8 — fal.ai's deadline-aware client manages its own per-step
        // retries inside the 30 s end-to-end budget. The orchestrator's
        // RetryTemplate (5 attempts, exponential back-off + jitter) MUST NOT
        // wrap fal.ai calls, or the budget would be exceeded by retries.
        return false;
    }

    @Override
    public PosterImage generate(GeneratedCharacter character,
                                AlterEgoRequest request,
                                PhotoPayload photo) {
        if (!props.isConfigured()) {
            throw new GenerationFailure(FallbackReason.NOT_CONFIGURED,
                    "FAL_AI_API_KEY not configured");
        }
        Instant deadline = clock.instant().plusMillis(props.endToEndTimeoutMs());
        PhotoPayload reduced = reducer.reduce(photo, props.photoReductionConfig());
        String prompt = promptBuilder.build(request);
        return client.generateImage(props, prompt, reduced, deadline);
    }
}

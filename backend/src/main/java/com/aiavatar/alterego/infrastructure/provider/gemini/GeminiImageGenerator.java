package com.aiavatar.alterego.infrastructure.provider.gemini;

import org.springframework.context.annotation.Primary;


import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
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

/**
 * {@link ImageGenerator} that calls Google's Gemini image-generation model
 * (003 FR-201). Composes the prompt via {@link GeminiPromptBuilder},
 * reduces the photo via {@link PhotoReducer}, and delegates the HTTP call
 * to {@link GeminiClient}.
 *
 * <p>Wired under the {@code gemini} Spring profile so the 001 stub
 * ({@code StubImageGenerator @Profile("default")}) stays the active bean
 * on the {@code default} profile. Activate via
 * {@code SPRING_PROFILES_ACTIVE=gemini} together with {@code GEMINI_API_KEY}
 * (research.md R6).
 *
 * <p>{@link Primary} so a {@code @SpringBootTest} that activates both
 * profiles (e.g. {@code @ActiveProfiles({"gemini","force-stub-failure"})})
 * still resolves deterministically — not a common scenario, but avoids any
 * ambiguous-bean surprises.
 *
 * <p>Pre-HTTP short-circuit: when {@link GeminiProperties#isConfigured()}
 * is false (blank API key), throws
 * {@code GenerationFailure(NOT_CONFIGURED)} immediately — the orchestrator
 * serves the fallback without a wasted outbound attempt (FR-212).
 */
@Component
@Profile("gemini")
@Primary
public class GeminiImageGenerator implements ImageGeneratorPort {

    private final GeminiClient client;
    private final GeminiPromptBuilder promptBuilder;
    private final PhotoReducer reducer;
    private final GeminiProperties props;

    public GeminiImageGenerator(GeminiClient client,
                                GeminiPromptBuilder promptBuilder,
                                PhotoReducer reducer,
                                GeminiProperties props) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.reducer = reducer;
        this.props = props;
    }

    @Override
    public Provider provider() {
        return Provider.GEMINI;
    }

    @Override
    public PosterImage generate(GeneratedCharacter character,
                                AlterEgoRequest request,
                                PhotoPayload photo) {
        if (!props.isConfigured()) {
            throw new GenerationFailure(FallbackReason.NOT_CONFIGURED,
                    "GEMINI_API_KEY not configured");
        }
        PhotoPayload reduced = reducer.reduce(photo, props.photoReductionConfig());
        String prompt = promptBuilder.build(request);
        return client.generateImage(props.modelId(), prompt, reduced);
    }
}

package com.aiavatar.alterego.infrastructure.provider.gemini;

import org.springframework.context.annotation.Primary;


import com.aiavatar.alterego.infrastructure.config.GeminiProperties;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.application.port.CharacterGeneratorPort;
import com.aiavatar.alterego.application.port.GenerationFailure;
import com.aiavatar.alterego.domain.model.Provider;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * {@link CharacterGenerator} backed by Google's Gemini text-generation API
 * (014 FR-1401). Composes a structured-output prompt via
 * {@link GeminiCharacterPromptBuilder}, calls the provider via
 * {@link GeminiCharacterClient}, and parses the response into a
 * {@link GeneratedCharacter} via {@link GeminiCharacterResponseParser}.
 *
 * <p>Wired under the {@code gemini} Spring profile so the 001
 * {@code StubCharacterGenerator} stays the active bean on the
 * {@code default} profile. Activate via
 * {@code SPRING_PROFILES_ACTIVE=gemini} together with {@code GEMINI_API_KEY}
 * (research.md §R6 / §R7).
 *
 * <p>{@link Primary} so a {@code @SpringBootTest} that activates both
 * profiles (currently no such test) resolves deterministically — this is
 * a defensive tiebreak, not a runtime gate. Once the StubCharacterGenerator's
 * profile narrows to {@code "default"} only (014 T029), {@code @Primary}
 * stops being load-bearing.
 *
 * <p>Pre-HTTP short-circuit (FR-1403): when {@link GeminiProperties#isConfigured()}
 * is false (blank API key), throws {@code GenerationFailure(NOT_CONFIGURED)}
 * immediately — the orchestrator serves the fallback character without a
 * wasted outbound attempt. Mirrors {@link GeminiImageGenerator}'s posture.
 */
@Component
@Profile("gemini")
@Primary
public class GeminiCharacterGenerator implements CharacterGeneratorPort {

    private final GeminiCharacterClient client;
    private final GeminiCharacterPromptBuilder promptBuilder;
    private final GeminiCharacterResponseParser parser;
    private final GeminiProperties props;

    public GeminiCharacterGenerator(GeminiCharacterClient client,
                                    GeminiCharacterPromptBuilder promptBuilder,
                                    GeminiCharacterResponseParser parser,
                                    GeminiProperties props) {
        this.client = client;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.props = props;
    }

    @Override
    public Provider provider() {
        return Provider.GEMINI;
    }

    @Override
    public GeneratedCharacter generate(AlterEgoRequest request) {
        AlterEgoRequest req = request.withTrimmedFirstName();
        if (!props.isConfigured()) {
            throw new GenerationFailure(FallbackReason.NOT_CONFIGURED,
                    "GEMINI_API_KEY not configured");
        }
        String prompt = promptBuilder.build(req);
        JsonNode body = client.generateText(props.textModelId(), prompt);
        return parser.parse(body, req.firstName());
    }
}

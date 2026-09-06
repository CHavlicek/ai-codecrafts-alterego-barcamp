package com.aiavatar.alterego.unit.infrastructure.provider.gemini;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiPromptBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * T024a — FR-2407 part (b): provider-specific prompt builders MUST be
 * unit-testable without an {@code HttpClient} collaborator and MUST
 * produce deterministic output for a fixed input.
 */
class GeminiPromptBuilderPurityTest {

    @Test
    void buildIsDeterministicAndDoesNotRequireHttpClient() {
        GeminiPromptBuilder builder = new GeminiPromptBuilder();
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.BUILDER,
                ArtStyle.OIL_PAINTING, "Sam", PhotoMode.SINGLE, null);
        String a = builder.build(req);
        String b = builder.build(req);
        assertEquals(a, b, "Prompt build MUST be a pure function of the request (FR-2407a)");
        assertFalse(a.isBlank(), "Prompt MUST NOT be empty");
    }
}

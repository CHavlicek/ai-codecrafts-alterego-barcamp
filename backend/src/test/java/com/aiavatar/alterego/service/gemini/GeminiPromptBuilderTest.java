package com.aiavatar.alterego.service.gemini;

import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiClient;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiPromptBuilder;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression-locks for {@link GeminiPromptBuilder}: the prompt requests
 * portrait 3:4 — matching the frame asset's transparent inner cutout
 * (PosterFrameOverlayService.TARGET_ASPECT). The prompt body is the
 * load-bearing channel for asking Gemini to emit at portrait 3:4; the
 * typed {@code generationConfig.imageConfig.aspectRatio} parameter on
 * {@link GeminiClient} is the belt-and-braces channel covered separately
 * by {@link GeminiClientAspectRatioTest}.
 */
class GeminiPromptBuilderTest {

    private final GeminiPromptBuilder builder = new GeminiPromptBuilder();

    @Test
    void singlePromptStatesThreeToFourAspectRatioAndDropsTwoToThree() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", PhotoMode.SINGLE);

        String prompt = builder.build(req);

        assertTrue(prompt.contains("3:4 aspect ratio"),
                "SINGLE prompt MUST request the 3:4 portrait aspect ratio; got:\n" + prompt);
        assertFalse(prompt.contains("2:3 aspect ratio"),
                "SINGLE prompt MUST NOT mention the legacy 2:3 ratio; got:\n" + prompt);
    }

    @Test
    void groupPromptStatesThreeToFourAspectRatioAndDropsTwoToThree() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP);

        String prompt = builder.build(req);

        assertTrue(prompt.contains("3:4 aspect ratio"),
                "GROUP prompt MUST request the 3:4 portrait aspect ratio; got:\n" + prompt);
        assertFalse(prompt.contains("2:3 aspect ratio"),
                "GROUP prompt MUST NOT mention the legacy 2:3 ratio; got:\n" + prompt);
    }
}

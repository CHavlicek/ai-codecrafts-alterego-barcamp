package com.aiavatar.alterego.unit.infrastructure.provider.falai;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.infrastructure.provider.falai.FalAiPromptBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** T024b — same assertion as Gemini's, applied to fal.ai. */
class FalAiPromptBuilderPurityTest {

    @Test
    void buildIsDeterministicAndDoesNotRequireHttpClient() {
        FalAiPromptBuilder builder = new FalAiPromptBuilder();
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.BACKEND_DEV, Universe.STAR_WARS, Vibe.BUILDER,
                ArtStyle.OIL_PAINTING, "Sam", PhotoMode.SINGLE, null);
        String a = builder.build(req);
        String b = builder.build(req);
        assertEquals(a, b);
        assertFalse(a.isBlank());
    }
}

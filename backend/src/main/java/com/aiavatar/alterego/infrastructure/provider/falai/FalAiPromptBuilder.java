package com.aiavatar.alterego.infrastructure.provider.falai;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.prompt.RoleOfRecord;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure function that composes the text prompt sent to fal.ai's
 * {@code nano-banana-pro/edit} image-edit model (016 FR-1606 / FR-1607 /
 * research.md R3 + R11).
 *
 * <p>Mirrors the structure of 003's {@code GeminiPromptBuilder} but opens
 * with the verb {@code "Edit"} (rather than {@code "Generate"}) — fal.ai's
 * model is image-edit-conditioned and reads the input photo as the canvas
 * to modify rather than as a reference to copy from. Display-label maps for
 * pose / archetype / universe / vibe / art-style are duplicated from the
 * Gemini builder per R11 ("forks rather than shares") so the two builders
 * can diverge independently if a fal.ai-specific prompt-engineering tweak
 * surfaces during testing.
 *
 * <p>Stateless — safe to share as a singleton component.
 */
@Component
public class FalAiPromptBuilder {

    private static final Map<Pose, String> POSE_LABELS = new EnumMap<>(Pose.class);
    private static final Map<Archetype, String> ROLE_LABELS = new EnumMap<>(Archetype.class);
    private static final Map<Universe, String> UNIVERSE_LABELS = new EnumMap<>(Universe.class);
    private static final Map<Vibe, String> VIBE_LABELS = new EnumMap<>(Vibe.class);
    private static final Map<ArtStyle, String> ART_STYLE_LABELS = new EnumMap<>(ArtStyle.class);

    static {
        POSE_LABELS.put(Pose.HEROIC, "heroic, chest forward");
        POSE_LABELS.put(Pose.STEALTHY, "stealthy, low profile");
        POSE_LABELS.put(Pose.MYSTICAL, "mystical, ethereal");
        POSE_LABELS.put(Pose.SCHOLAR, "scholarly, thoughtful");

        // 021 (issue #54): role labels forked from GeminiCharacterPromptBuilder.ROLE_LABELS
        // (016 R11 "fork rather than share") so this builder can diverge
        // independently from the Gemini image builder and the bio prompt.
        // Today the three label sets are identical — see
        // specs/021-engineer-role-prompt/data-model.md.
        ROLE_LABELS.put(Archetype.CLOUD_ARCHITECT, "Cloud Architect");
        ROLE_LABELS.put(Archetype.BACKEND_DEV, "Backend Developer");
        ROLE_LABELS.put(Archetype.FRONTEND_DEV, "Frontend Developer");
        ROLE_LABELS.put(Archetype.AI_ENGINEER, "AI Engineer");
        ROLE_LABELS.put(Archetype.PLATFORM_ENG, "Platform Engineer");
        ROLE_LABELS.put(Archetype.DATA_ENGINEER, "Data Engineer");
        // 022 (issue #50) — non-engineering prefab options forked from
        // GeminiPromptBuilder.ROLE_LABELS per 016 R11 ("fork rather than share").
        ROLE_LABELS.put(Archetype.HR, "Human Resources");
        ROLE_LABELS.put(Archetype.ADMINISTRATION, "Administration / Operations");
        ROLE_LABELS.put(Archetype.CUSTOMER_RELATIONS, "Customer Relations / Support");

        UNIVERSE_LABELS.put(Universe.MARVEL, "Marvel superhero universe");
        UNIVERSE_LABELS.put(Universe.STAR_WARS, "Star Wars");
        UNIVERSE_LABELS.put(Universe.CYBERPUNK, "Cyberpunk neo-noir");
        UNIVERSE_LABELS.put(Universe.THE_OFFICE, "The Office sitcom");
        UNIVERSE_LABELS.put(Universe.INDIANA_JONES, "Indiana Jones adventure");
        UNIVERSE_LABELS.put(Universe.LORD_OF_THE_RINGS, "Lord of the Rings");

        VIBE_LABELS.put(Vibe.BUILDER, "builder / tinkerer");
        VIBE_LABELS.put(Vibe.THINKER, "thinker / strategist");
        VIBE_LABELS.put(Vibe.REBEL, "rebellious");
        VIBE_LABELS.put(Vibe.ARCHITECT, "architectural, measured");

        ART_STYLE_LABELS.put(ArtStyle.OIL_PAINTING,
                "oil painting, visible brushstrokes and impasto texture");
        ART_STYLE_LABELS.put(ArtStyle.WATERCOLOR,
                "watercolor painting, soft washes and bleeding edges");
        ART_STYLE_LABELS.put(ArtStyle.POP_ART,
                "pop art, bold flat colours and halftone dots");
        ART_STYLE_LABELS.put(ArtStyle.RENAISSANCE_PORTRAIT,
                "Renaissance oil portrait, chiaroscuro lighting");
        ART_STYLE_LABELS.put(ArtStyle.JAPANESE_WOODBLOCK,
                "Japanese ukiyo-e woodblock print, hand-carved line work");
        ART_STYLE_LABELS.put(ArtStyle.CEL_SHADED,
                "cel-shaded anime, crisp outlines and flat colour fills");
    }

    /**
     * Compose the prompt for the given request. The photo itself is
     * attached to fal.ai as an inline data URL by {@link FalAiClient};
     * this builder only produces the text payload.
     */
    public String build(AlterEgoRequest request) {
        return request.effectivePhotoMode() == PhotoMode.GROUP
                ? buildGroup(request)
                : buildSingle(request);
    }

    private String buildSingle(AlterEgoRequest request) {
        StringBuilder sb = new StringBuilder(768);
        sb.append("Edit the reference photo to render the person as their alter ego.\n\n");
        sb.append("The subject's face, hair, skin tone, approximate age, and general build ")
                .append("MUST closely match the reference photo. ")
                .append("Render the subject as an alter ego with the following attributes:\n\n");
        appendCategoryLines(sb, request);
        sb.append("\nComposition notes:\n");
        sb.append("- Portrait orientation, 3:4 aspect ratio, bright rim lighting on the subject.\n");
        sb.append("- Background: bright, clean and airy — a light, luminous setting with an overall high-key palette that leans toward whites, soft blues and cool daylight tones. Avoid dark, murky, black or heavily shadowed backgrounds; the poster frame around this image is bright white and blue, so the scene must feel light and open, not gloomy.\n");
        sb.append("- Clear focus on the subject; the universe aesthetic is the setting, not the subject.\n");
        sb.append("- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the engineering-role label. The poster's text overlay is composited downstream — your job is the visual scene only.\n");
        return sb.toString();
    }

    private String buildGroup(AlterEgoRequest request) {
        StringBuilder sb = new StringBuilder(896);
        sb.append("Edit the reference photo to render every person as the same alter ego.\n\n");
        sb.append("Render EVERY person visible in the reference photo as the same alter ego. ")
                .append("Each person's appearance (face, hair, skin tone, approximate age, general build) ")
                .append("MUST closely match their own face in the reference photo. ")
                .append("Do NOT invent additional people who are not in the reference photo. ")
                .append("Apply the same attributes uniformly to every person:\n\n");
        appendCategoryLines(sb, request);
        sb.append("\nComposition notes:\n");
        sb.append("- Portrait orientation, 3:4 aspect ratio, bright rim lighting on the subjects.\n");
        sb.append("- Background: bright, clean and airy — a light, luminous setting with an overall high-key palette that leans toward whites, soft blues and cool daylight tones. Avoid dark, murky, black or heavily shadowed backgrounds; the poster frame around this image is bright white and blue, so the scene must feel light and open, not gloomy.\n");
        sb.append("- Clear focus on all subjects as a group; the universe aesthetic is the setting, not the subjects.\n");
        sb.append("- Arrange the group so every face is clearly visible.\n");
        sb.append("- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the engineering-role label. The poster's text overlay is composited downstream — your job is the visual scene only.\n");
        return sb.toString();
    }

    /**
     * Shared category-label block. The engineering role is emitted as a
     * <em>visual scene direction</em> — props, environment, attire, activity —
     * rather than as a verbatim label, with an inline {@code "NOT as text"}
     * clarifier and a reinforced composition-note rule (021, issue #54).
     *
     * <p>The user's first name remains deliberately excluded: unbounded
     * user input is harder for the model to resist transcribing than a
     * closed-set role label. Name + role text are composited downstream
     * by {@code PosterTextOverlayService}.
     *
     * <p>See {@code specs/021-engineer-role-prompt/} for the spec, plan,
     * and the per-Archetype Prompt-label vocabulary (forked from
     * {@code GeminiCharacterPromptBuilder.ROLE_LABELS} per 016 R11).
     */
    private static void appendCategoryLines(StringBuilder sb, AlterEgoRequest request) {
        sb.append("- Fictional universe / aesthetic: ").append(label(UNIVERSE_LABELS, request.universe())).append('\n');
        sb.append("- Engineering role (render as visual cues — props, environment, attire, activity — NOT as text): ")
                .append(resolveRoleLabel(request)).append('\n');
        sb.append("- Art style: ").append(label(ART_STYLE_LABELS, request.artStyle())).append('\n');
        sb.append("- Pose / stance: ").append(label(POSE_LABELS, request.pose())).append('\n');
        if (request.vibe() != null) {
            sb.append("- Vibe / tone: ").append(label(VIBE_LABELS, request.vibe())).append('\n');
        }
    }

    private static <E extends Enum<E>> String label(Map<E, String> labels, E value) {
        String mapped = labels.get(value);
        return mapped != null ? mapped : value.name().toLowerCase().replace('_', '-');
    }

    /**
     * 022 — resolves the role string for the prompt. Custom role (trimmed,
     * when non-blank) takes precedence over the prefab archetype's label.
     */
    private static String resolveRoleLabel(AlterEgoRequest request) {
        if (request.customRole() != null && !request.customRole().isBlank()) {
            return request.customRole().trim();
        }
        return request.archetype() != null
                ? label(ROLE_LABELS, request.archetype())
                : RoleOfRecord.from(request).value();
    }
}

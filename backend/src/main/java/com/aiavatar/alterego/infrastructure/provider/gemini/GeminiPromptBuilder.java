package com.aiavatar.alterego.infrastructure.provider.gemini;

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
 * Pure function that composes the text prompt sent to Gemini's
 * {@code generateContent} endpoint (003 FR-201 / FR-203 / research.md R3).
 *
 * <p>Each setup enum maps to a human-readable display label (not the wire
 * value) — the natural-language grounding is what makes role, universe,
 * pose, vibe, and art style contribute measurably distinct composition cues
 * in the generated image.
 *
 * <p>Stateless — safe to share as a singleton component.
 */
@Component
public class GeminiPromptBuilder {

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

        // 021 (issue #54): role labels mirror GeminiCharacterPromptBuilder.ROLE_LABELS
        // byte-for-byte so the image and the bio share the same role
        // vocabulary (spec FR-2102). Kept duplicated rather than shared
        // per 016 R11 — see specs/021-engineer-role-prompt/data-model.md.
        ROLE_LABELS.put(Archetype.CLOUD_ARCHITECT, "Cloud Architect");
        ROLE_LABELS.put(Archetype.BACKEND_DEV, "Backend Developer");
        ROLE_LABELS.put(Archetype.FRONTEND_DEV, "Frontend Developer");
        ROLE_LABELS.put(Archetype.AI_ENGINEER, "AI Engineer");
        ROLE_LABELS.put(Archetype.PLATFORM_ENG, "Platform Engineer");
        ROLE_LABELS.put(Archetype.DATA_ENGINEER, "Data Engineer");
        // 022 (issue #50) — non-engineering prefab options. Expanded label
        // forms ground the model in the role's broader vocabulary the same
        // way "Backend Developer" expands the UI "Backend Dev" label.
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
     * attached as an inline_data part by {@link GeminiClient} and isn't
     * referenced here textually — the "reference photo" instruction in
     * the template tells Gemini to ground on it.
     *
     * <p>011 delta: branches on {@link AlterEgoRequest#effectivePhotoMode()}.
     * {@link PhotoMode#SINGLE} — baseline singular-subject wording, byte-
     * identical to the pre-011 prompt (regression-locked by unit test).
     * {@link PhotoMode#GROUP} — plural wording, explicit "EVERY person visible"
     * instruction, negative constraint against inventing additional people,
     * and a reformatted {@code Group name:} label so the model reads
     * {@code firstName} as the group's collective name.
     */
    public String build(AlterEgoRequest request) {
        return request.effectivePhotoMode() == PhotoMode.GROUP
                ? buildGroup(request)
                : buildSingle(request);
    }

    private String buildSingle(AlterEgoRequest request) {
        StringBuilder sb = new StringBuilder(768);
        sb.append("Generate a cinematic portrait poster of the person in the reference photo.\n\n");
        sb.append("The subject's appearance (face, hair, skin tone, approximate age, general build) ")
                .append("MUST closely match the reference photo. Render the subject as an \"alter ego\" ")
                .append("with the following attributes:\n\n");
        appendCategoryLines(sb, request);
        sb.append("\nComposition notes:\n");
        sb.append("- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.\n");
        sb.append("- Clear focus on the subject; the universe aesthetic is the setting, not the subject.\n");
        sb.append("- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the engineering-role label. The poster's text overlay is composited downstream — your job is the visual scene only.\n");
        return sb.toString();
    }

    private String buildGroup(AlterEgoRequest request) {
        StringBuilder sb = new StringBuilder(896);
        sb.append("Generate a cinematic group portrait poster of the people in the reference photo.\n\n");
        sb.append("Render EVERY person visible in the reference photo as the same alter ego. ")
                .append("Each person's appearance (face, hair, skin tone, approximate age, general build) ")
                .append("MUST closely match their own face in the reference photo. ")
                .append("Do NOT invent additional people who are not in the reference photo. ")
                .append("Apply the same attributes uniformly to every person:\n\n");
        appendCategoryLines(sb, request);
        sb.append("\nComposition notes:\n");
        sb.append("- Portrait orientation, 3:4 aspect ratio, dramatic rim lighting.\n");
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
     * <p>The user's first name remains deliberately excluded: it was the
     * other half of the 017 (refined) workaround against banner-text bleed,
     * and the rationale (unbounded user input is harder for the model to
     * resist transcribing than a closed-set role label) still holds.
     * Name + role text are composited downstream by
     * {@code PosterTextOverlayService}.
     *
     * <p>See {@code specs/021-engineer-role-prompt/} for the spec, plan,
     * and the byte-for-byte SINGLE-variant fixture pinned by tests.
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
        // Defensive default — if an enum value is ever added without a label,
        // fall back to the wire form so Gemini still gets *something* and the
        // prompt doesn't break. Unit tests pin every value to a distinct label.
        return mapped != null ? mapped : value.name().toLowerCase().replace('_', '-');
    }

    /**
     * 022 — resolves the role string for the prompt. When the user supplied
     * a custom role on the request, {@link AlterEgoRequest#roleLabel()} returns
     * the trimmed custom string (taking precedence over any prefab archetype).
     * For the prefab path we keep using {@link #ROLE_LABELS} so the model
     * sees the longer / more-grounded "Backend Developer" form instead of
     * the UI's compact "Backend Dev" label (see 021 data-model.md).
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

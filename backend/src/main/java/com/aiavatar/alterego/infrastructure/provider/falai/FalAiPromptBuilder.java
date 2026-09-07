package com.aiavatar.alterego.infrastructure.provider.falai;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.prompt.RoleOfRecord;
import com.aiavatar.alterego.domain.prompt.UniverseOfRecord;
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

        // 029 (verbund-rebrand): corporate role vocabulary, forked from
        // GeminiPromptBuilder.ROLE_LABELS per 016 R11 ("fork rather than share").
        ROLE_LABELS.put(Archetype.SOFTWARE_DEVELOPER, "Software Developer");
        ROLE_LABELS.put(Archetype.PROJECT_MANAGER, "Project Manager");
        ROLE_LABELS.put(Archetype.DATA_ANALYST, "Data Analyst");
        ROLE_LABELS.put(Archetype.MARKETING_SPECIALIST, "Marketing & Communications Specialist");
        ROLE_LABELS.put(Archetype.SALES_CUSTOMER_RELATIONS, "Sales & Customer Relations");
        ROLE_LABELS.put(Archetype.PEOPLE_CULTURE, "People & Culture (HR)");
        ROLE_LABELS.put(Archetype.OPERATIONS_MANAGER, "Operations Manager");
        ROLE_LABELS.put(Archetype.FINANCE_CONTROLLER, "Finance & Controlling");
        ROLE_LABELS.put(Archetype.SUSTAINABILITY_LEAD, "Sustainability & Energy-Transition Lead");

        // 029 — broadly recognisable 80s/90s/2000s pop-culture aesthetics.
        UNIVERSE_LABELS.put(Universe.MARVEL, "Marvel superhero universe");
        UNIVERSE_LABELS.put(Universe.STAR_WARS, "Star Wars");
        UNIVERSE_LABELS.put(Universe.RETRO_SYNTHWAVE, "1980s Miami Vice, pastel neon, palm trees and chrome");
        UNIVERSE_LABELS.put(Universe.NINETIES_SITCOM, "The Office, a warm, bright mockumentary workplace sitcom set");
        UNIVERSE_LABELS.put(Universe.SPY_THRILLER, "a sleek, glamorous James Bond spy thriller, adventurous and suave");
        UNIVERSE_LABELS.put(Universe.GHOSTBUSTERS, "playful 1980s Ghostbusters adventure");

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
        sb.append("Edit the reference photo to render the person as an uplifting alter ego ")
                .append("for a hopeful, innovation-driven future.\n\n");
        sb.append("The subject's face, hair, skin tone, approximate age, and general build ")
                .append("MUST closely match the reference photo. ")
                .append("Render the subject as an optimistic, forward-looking alter ego with the following attributes:\n\n");
        appendCategoryLines(sb, request);
        sb.append("\nComposition notes:\n");
        sb.append("- Portrait orientation, 3:4 aspect ratio, bright rim lighting on the subject.\n");
        sb.append("- Overall mood: hopeful, positive and forward-looking, but it need not be earnest — the image can be playful, tongue-in-cheek and humorous, having fun with the theme and not taking itself too seriously. The subject looks confident and inspired, like someone helping build a brighter, more sustainable tomorrow.\n");
        sb.append("- Adapt the mood to the chosen universe / aesthetic: fully embrace its signature look and energy even when it is moodier, edgier or more conflict-driven (e.g. neon-noir Miami Vice, a tense spy thriller). Lean into that atmosphere with confidence and wit while keeping the underlying spirit optimistic and fun — never bleak, grim or hopeless.\n");
        sb.append("- Background: lean bright, clean and airy where the universe allows — a light, luminous setting leaning toward whites, soft blues and cool daylight tones — but let the chosen aesthetic drive the palette when it calls for something richer or moodier. Weave in subtle hints of renewable-energy optimism where they fit the scene naturally: open sky, sunlight, greenery, clean-energy motifs and especially hydropower (flowing water, rivers, dams, reservoirs and turbines — Verbund produces most of its energy from hydropower). Prefer light, open scenes over gloomy, murky or heavily shadowed ones so the poster stays inviting.\n");
        sb.append("- Clear focus on the subject; the universe aesthetic is the setting, not the subject.\n");
        sb.append("- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the role label. The poster's text overlay is composited downstream — your job is the visual scene only.\n");
        return sb.toString();
    }

    private String buildGroup(AlterEgoRequest request) {
        StringBuilder sb = new StringBuilder(896);
        sb.append("Edit the reference photo to render every person as an uplifting alter ego ")
                .append("for a hopeful, innovation-driven future.\n\n");
        sb.append("Render EVERY person visible in the reference photo as the same alter ego. ")
                .append("Each person's appearance (face, hair, skin tone, approximate age, general build) ")
                .append("MUST closely match their own face in the reference photo. ")
                .append("Do NOT invent additional people who are not in the reference photo. ")
                .append("Apply the same optimistic, forward-looking attributes uniformly to every person:\n\n");
        appendCategoryLines(sb, request);
        sb.append("\nComposition notes:\n");
        sb.append("- Portrait orientation, 3:4 aspect ratio, bright rim lighting on the subjects.\n");
        sb.append("- Overall mood: hopeful, positive and forward-looking, but it need not be earnest — the image can be playful, tongue-in-cheek and humorous, having fun with the theme and not taking itself too seriously. The subjects look confident and inspired, like a team helping build a brighter, more sustainable tomorrow.\n");
        sb.append("- Adapt the mood to the chosen universe / aesthetic: fully embrace its signature look and energy even when it is moodier, edgier or more conflict-driven (e.g. neon-noir Miami Vice, a tense spy thriller). Lean into that atmosphere with confidence and wit while keeping the underlying spirit optimistic and fun — never bleak, grim or hopeless.\n");
        sb.append("- Background: lean bright, clean and airy where the universe allows — a light, luminous setting leaning toward whites, soft blues and cool daylight tones — but let the chosen aesthetic drive the palette when it calls for something richer or moodier. Weave in subtle hints of renewable-energy optimism where they fit the scene naturally: open sky, sunlight, greenery, clean-energy motifs and especially hydropower (flowing water, rivers, dams, reservoirs and turbines — Verbund produces most of its energy from hydropower). Prefer light, open scenes over gloomy, murky or heavily shadowed ones so the poster stays inviting.\n");
        sb.append("- Clear focus on all subjects as a group; the universe aesthetic is the setting, not the subjects.\n");
        sb.append("- Arrange the group so every face is clearly visible.\n");
        sb.append("- ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the role label. The poster's text overlay is composited downstream — your job is the visual scene only.\n");
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
        sb.append("- Fictional universe / aesthetic: ").append(resolveUniverseLabel(request)).append('\n');
        sb.append("- Professional role (render as uplifting visual cues — props, environment, attire, activity — NOT as text): ")
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

    /**
     * 029 — resolves the universe string for the prompt. A non-blank
     * {@code customUniverse} takes precedence over the prefab universe's label.
     */
    private static String resolveUniverseLabel(AlterEgoRequest request) {
        if (request.customUniverse() != null && !request.customUniverse().isBlank()) {
            return request.customUniverse().trim();
        }
        return request.universe() != null
                ? label(UNIVERSE_LABELS, request.universe())
                : UniverseOfRecord.from(request).value();
    }
}

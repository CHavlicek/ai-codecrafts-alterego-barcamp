package com.aiavatar.alterego.infrastructure.provider.gemini;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.prompt.RoleOfRecord;
import com.aiavatar.alterego.domain.prompt.UniverseOfRecord;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * Pure function that composes the text prompt sent to Gemini's text-generation
 * endpoint (014 FR-1405 / research.md §R3). Mirrors the role
 * {@link GeminiPromptBuilder} (003) plays for image prompts.
 *
 * <p>Each Setup enum maps to a human-readable display label (not the wire
 * value). The label maps are deliberately separate from the image-side
 * builder's so the two can diverge in tone if needed (the image wants
 * visual cues; the text wants narrative cues — today they happen to share
 * the same vocabulary, but coupling them through one map would invite
 * accidental drift).
 *
 * <p>The prompt is structured-output friendly: it tells the LLM to respond
 * with one JSON object matching a schema the client also pins via
 * {@code generationConfig.responseSchema}. The schema is the wire-level
 * contract; the prompt repeats the per-trait constraints in natural
 * language to give the model a second cue.
 *
 * <p>Stateless — safe to share as a singleton component.
 *
 * <p>FR-1418 / clarification Q2: instructs the LLM to respond in English only.
 * FR-1419 / clarification Q4: {@code firstName} is interpolated as-is — the
 * 011 {@code ValidFirstName} validator at the controller boundary is the
 * sanitization layer; this builder MUST NOT add a second pass.
 */
@Component
public class GeminiCharacterPromptBuilder {

    private static final Map<Pose,      String> POSE_LABELS = new EnumMap<>(Pose.class);
    private static final Map<Archetype, String> ROLE_LABELS = new EnumMap<>(Archetype.class);
    private static final Map<Universe,  String> UNIVERSE_LABELS = new EnumMap<>(Universe.class);
    private static final Map<Vibe,      String> VIBE_LABELS = new EnumMap<>(Vibe.class);
    private static final Map<ArtStyle,  String> ART_STYLE_LABELS = new EnumMap<>(ArtStyle.class);

    static {
        POSE_LABELS.put(Pose.HEROIC, "heroic, chest forward");
        POSE_LABELS.put(Pose.STEALTHY, "stealthy, low profile");
        POSE_LABELS.put(Pose.MYSTICAL, "mystical, ethereal");
        POSE_LABELS.put(Pose.SCHOLAR, "scholarly, thoughtful");

        // 029 (verbund-rebrand): corporate role vocabulary. Expanded labels
        // keep the bio prompt and image prompt in sync (021 spec FR-2102).
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
        UNIVERSE_LABELS.put(Universe.RETRO_SYNTHWAVE, "1980s retro synthwave");
        UNIVERSE_LABELS.put(Universe.NINETIES_SITCOM, "1990s sitcom");
        UNIVERSE_LABELS.put(Universe.SPY_THRILLER, "classic spy thriller");
        UNIVERSE_LABELS.put(Universe.GHOSTBUSTERS, "Ghostbusters adventure");

        VIBE_LABELS.put(Vibe.BUILDER, "builder / tinkerer");
        VIBE_LABELS.put(Vibe.THINKER, "thinker / strategist");
        VIBE_LABELS.put(Vibe.REBEL, "rebellious");
        VIBE_LABELS.put(Vibe.ARCHITECT, "architectural, measured");

        ART_STYLE_LABELS.put(ArtStyle.OIL_PAINTING, "oil painting, visible brushstrokes");
        ART_STYLE_LABELS.put(ArtStyle.WATERCOLOR, "watercolor, soft washes");
        ART_STYLE_LABELS.put(ArtStyle.POP_ART, "pop art, halftone dots");
        ART_STYLE_LABELS.put(ArtStyle.RENAISSANCE_PORTRAIT, "Renaissance oil portrait");
        ART_STYLE_LABELS.put(ArtStyle.JAPANESE_WOODBLOCK, "Japanese ukiyo-e woodblock");
        ART_STYLE_LABELS.put(ArtStyle.CEL_SHADED, "cel-shaded anime");
    }

    /**
     * Compose the prompt for the given request. The companion
     * {@link GeminiCharacterClient} pairs this prompt with a structured-output
     * {@code responseSchema} that pins the JSON shape; the per-trait length
     * and count rules are also repeated below in natural language so the
     * LLM is told the contract twice.
     */
    public String build(AlterEgoRequest request) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append("You are writing the captions for a fictional \"alter ego\" trading-card poster.\n");
        sb.append("Respond with ONE JSON object that exactly matches the response schema. ");
        sb.append("Do not include any prose outside the JSON object. ");
        sb.append("Write every string in ENGLISH only.\n\n");

        sb.append("The poster is for one person. Their attributes:\n\n");
        sb.append("- First name (used for tone, NOT to repeat verbatim in line 2): ")
                .append(request.firstName()).append('\n');
        sb.append("- Professional role: ")
                .append(resolveRoleLabel(request)).append('\n');
        sb.append("- Fictional universe / aesthetic: ")
                .append(resolveUniverseLabel(request)).append('\n');
        sb.append("- Pose / stance: ")
                .append(label(POSE_LABELS, request.pose())).append('\n');
        sb.append("- Art style of the poster: ")
                .append(label(ART_STYLE_LABELS, request.artStyle())).append('\n');
        if (request.vibe() != null) {
            sb.append("- Vibe / tone: ")
                    .append(label(VIBE_LABELS, request.vibe())).append('\n');
        }

        sb.append('\n');
        sb.append("Trait constraints (each MUST be satisfied):\n");
        sb.append("- heroTitleLine2: a single short hero-style title line (e.g. \"The Future Builder\", ")
                .append("\"The Bright-Spark Visionary\"). MUST start with \"The \". MUST NOT contain the first name. ")
                .append("Up to 100 characters, at least 1 character.\n");
        sb.append("- tagline: a single short punchy line, typically 4-8 words, often UPPERCASE. ")
                .append("Up to 100 characters, at least 1 character.\n");
        sb.append("- superpowers: EXACTLY THREE short superpower descriptions, each one short ")
                .append("sentence or noun phrase. Each entry up to 100 characters, at least 1 character.\n");
        sb.append("- quote: a short uplifting, forward-looking quote, ideally up to 12 words. ")
                .append("Up to 100 characters, at least 1 character.\n");
        sb.append('\n');

        sb.append("Voice: warm, hopeful, inspiring and lightly witty — the energy of someone helping ");
        sb.append("build a brighter, more sustainable future through innovation and clean energy. ");
        sb.append("Upbeat and empowering, never cynical or overly technical. ");
        sb.append("Reference the chosen universe and role naturally — do not just name them.\n\n");
        sb.append("Output: only the JSON object. No code fences, no commentary, no Markdown.\n");

        return sb.toString();
    }

    private static <E extends Enum<E>> String label(Map<E, String> labels, E value) {
        // Defensive default — if an enum value is added without a label,
        // fall back to the wire form so the prompt still grounds on
        // *something* and the build doesn't fail. Unit tests pin every
        // value to a distinct label.
        String mapped = labels.get(value);
        return mapped != null ? mapped : value.name().toLowerCase().replace('_', '-');
    }

    /**
     * 022 — resolves the role string for the bio prompt. Custom role
     * (trimmed, when non-blank) takes precedence over the prefab archetype's
     * label; otherwise the longer prompt-side label is used so bio + image
     * prompts share the same role vocabulary.
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
     * 029 — resolves the universe string for the bio prompt. Custom universe
     * (trimmed, when non-blank) takes precedence over the prefab universe's
     * label; otherwise the prompt-side label is used so bio + image prompts
     * share the same universe vocabulary.
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

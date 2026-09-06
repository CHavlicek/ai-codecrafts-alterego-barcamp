package com.aiavatar.alterego.unit.gemini;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T020 — happy-path {@link GeminiPromptBuilder} coverage. Verifies the
 * prompt template fills with each setup field and that the first name
 * lands verbatim (FR-201). Field-coverage / per-axis distinctness tests
 * land with 003 US2 / T030.
 */
class GeminiPromptBuilderTest {

    private final GeminiPromptBuilder builder = new GeminiPromptBuilder();

    @Test
    void promptIsNonEmptyAndDoesNotContainFirstName() {
        // 017 (refined 2026-05-08): firstName is no longer in the image
        // prompt. The supersedes-this test name kept for git-blame
        // continuity with the pre-017 contract.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null));
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        assertFalse(prompt.contains("Paula"),
                () -> "017 (refined): firstName MUST NOT appear in image prompt; got: " + prompt);
    }

    @Test
    void promptMentionsTheReferencePhotoForGrounding() {
        String prompt = builder.build(sampleRequestWithVibe(Vibe.REBEL));
        assertTrue(prompt.toLowerCase().contains("reference photo"),
                () -> "prompt must instruct Gemini to ground on the reference photo");
    }

    @Test
    void vibeClausePresentWhenVibeIsSet() {
        String prompt = builder.build(sampleRequestWithVibe(Vibe.REBEL));
        assertTrue(prompt.contains("Vibe"),
                () -> "prompt must include a Vibe line when vibe is non-null");
    }

    @Test
    void vibeClauseOmittedWhenVibeIsNull() {
        String prompt = builder.build(sampleRequestWithVibe(null));
        assertFalse(prompt.contains("Vibe"),
                () -> "prompt must NOT include a Vibe line when vibe is null");
    }

    @Test
    void purelyVisualAxesAndRoleAppearInTheTemplate() {
        // 021 (issue #54): the engineering role is back in the image prompt,
        // emitted as a visual scene direction (props/environment/attire/
        // activity) rather than as a verbatim label. The role line MUST be
        // present; the first-name exclusion from 017 (refined) is preserved.
        String prompt = builder.build(sampleRequestWithVibe(Vibe.THINKER));
        assertTrue(prompt.contains("Fictional universe"),
                "universe axis header expected in prompt");
        assertTrue(prompt.contains("Pose / stance:"),
                "pose axis header expected in prompt");
        assertTrue(prompt.contains("Art style:"),
                "art-style axis header expected in prompt");
        assertTrue(prompt.contains("Professional role ("),
                "021: role axis header expected in prompt as a visual scene direction");
        assertFalse(prompt.contains("Name:"),
                "017 (refined): firstName must NOT appear in image prompt");
    }

    @Test
    void promptForbidsAllRenderedTextInTheImage() {
        // Strengthened 2026-05-08 (017 refined): the AI was rendering name
        // plates and parchment scrolls; the wording enumerates offenders.
        // Strengthened again 2026-05-11 (021 / issue #54): with the role
        // back in the prompt, the composition-notes line additionally
        // forbids transcribing the engineering-role label.
        String prompt = builder.build(sampleRequestWithVibe(null));
        String lower = prompt.toLowerCase();
        assertTrue(lower.contains("absolutely no rendered text"),
                "prompt MUST contain the strengthened negative-text instruction");
        assertTrue(lower.contains("banner") && lower.contains("scroll") && lower.contains("name plate"),
                "prompt MUST enumerate banner / scroll / name plate as forbidden");
        assertTrue(lower.contains("no transcribing the role label"),
                "021: composition note MUST forbid transcribing the role label");
    }

    @Test
    void compositionNoteForbidsTranscribingTheRoleLabel() {
        // 021 explicit regression-lock: the role-label-transcribe forbid
        // clause MUST appear in BOTH the SINGLE and GROUP composition
        // notes (the latter is exercised in groupPhotoModeUsesPluralWording).
        String prompt = builder.build(sampleRequestWithVibe(null));
        assertTrue(prompt.contains("NO transcribing the role label"),
                "021: composition note MUST contain the role-label-transcribe forbid clause verbatim");
    }

    @Test
    void firstNameIsNotPassedToImageGenerationAi() {
        // 017 (refined 2026-05-08): explicit regression-lock — the
        // user's first name MUST NOT appear anywhere in the image
        // prompt. Even with strong negative-text instructions, the AI
        // tends to render the name on banners if it sees it.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Lalo", null));
        assertFalse(prompt.contains("Lalo"),
                "image prompt MUST NOT contain the user's first name");
    }

    @Test
    void archetypeRoleAppearsInTheImagePrompt() {
        // 021 (issue #54): inverted from 017 (refined). The role IS back
        // in the image prompt — communicated as a visual scene direction
        // (props/environment/attire/activity), guarded by an inline
        // "NOT as text" clarifier on the role line itself plus a
        // strengthened composition-note no-text rule.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Paula", null));
        assertTrue(prompt.contains("Professional role ("),
                "021: image prompt MUST contain the Professional role line header");
        assertTrue(prompt.contains("Software Developer"),
                "021: image prompt MUST contain the role's Prompt label (Software Developer)");
    }

    @Test
    void promptIsDeterministicForIdenticalInputs() {
        String a = builder.build(sampleRequestWithVibe(Vibe.REBEL));
        String b = builder.build(sampleRequestWithVibe(Vibe.REBEL));
        assertEquals(a, b, "prompt builder must be deterministic");
    }

    // T030 — field-coverage parametrised rows. Each axis-varied pair asserts
    // the prompt changes measurably on that one axis, so SC-202's automatable
    // half is green at the unit layer (the visual observer-check remains
    // manual, covered by T054). Also pins display-label maps to the
    // human-readable variants — wire values MUST NOT appear in the prompt.

    @Test
    void changingRoleChangesPromptSubstring() {
        // 021 (issue #54): re-inverted from 017 (refined). The role IS
        // back in the prompt as a visual scene direction; a role-only
        // delta MUST therefore alter the prompt and each prompt MUST
        // contain its own Prompt label per data-model.md.
        String a = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
        String b = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.PROJECT_MANAGER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
        assertNotEquals(a, b,
                "021: role-only delta MUST change the prompt");
        assertTrue(a.contains("Software Developer"),
                "021: SOFTWARE_DEVELOPER prompt MUST contain 'Software Developer' (Prompt label, not UI label)");
        assertTrue(b.contains("Project Manager"),
                "021: PROJECT_MANAGER prompt MUST contain 'Project Manager'");
        assertFalse(a.contains("software-developer"),
                "wire values must not appear in the prompt (only Prompt labels)");
    }

    @Test
    void everyArchetypeProducesAUniquePromptLine() {
        // 021 SC-2103 automatable half — pair-wise distinctness across
        // every Archetype value, plus per-archetype Prompt-label pinning.
        java.util.Map<Archetype, String> expectedLabels = java.util.Map.ofEntries(
                java.util.Map.entry(Archetype.SOFTWARE_DEVELOPER,        "Software Developer"),
                java.util.Map.entry(Archetype.PROJECT_MANAGER,          "Project Manager"),
                java.util.Map.entry(Archetype.DATA_ANALYST,             "Data Analyst"),
                java.util.Map.entry(Archetype.MARKETING_SPECIALIST,     "Marketing & Communications Specialist"),
                java.util.Map.entry(Archetype.SALES_CUSTOMER_RELATIONS, "Sales & Customer Relations"),
                java.util.Map.entry(Archetype.PEOPLE_CULTURE,           "People & Culture (HR)"),
                java.util.Map.entry(Archetype.OPERATIONS_MANAGER,       "Operations Manager"),
                java.util.Map.entry(Archetype.FINANCE_CONTROLLER,       "Finance & Controlling"),
                java.util.Map.entry(Archetype.SUSTAINABILITY_LEAD,      "Sustainability & Energy-Transition Lead"));
        java.util.Set<String> seen = new HashSet<>();
        for (Archetype role : Archetype.values()) {
            String prompt = builder.build(new AlterEgoRequest(
                    Pose.HEROIC, role, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
            String expectedLabel = expectedLabels.get(role);
            assertNotNull(expectedLabel, "test fixture missing Prompt label for " + role);
            assertTrue(prompt.contains(expectedLabel),
                    () -> "021: prompt for " + role + " MUST contain Prompt label '" + expectedLabel + "'");
            assertTrue(seen.add(prompt),
                    () -> "021: prompt for " + role + " is not distinct from a previous archetype's prompt");
        }
        assertEquals(Archetype.values().length, seen.size(),
                "021: each Archetype MUST produce a unique prompt");
    }

    @Test
    void engineeringRoleLineFollowsUniverseAndPrecedesArtStyle() {
        // 021 contract: the role line sits between the Universe line and
        // the Art-style line on the attribute block — pins the new
        // category-line ordering documented in contracts/image-prompt-contract.md.
        String prompt = builder.build(sampleRequestWithVibe(null));
        int universeIdx = prompt.indexOf("- Fictional universe");
        int roleIdx     = prompt.indexOf("- Professional role (");
        int artStyleIdx = prompt.indexOf("- Art style:");
        assertTrue(universeIdx >= 0, "universe line present");
        assertTrue(roleIdx >= 0,     "021: role line present");
        assertTrue(artStyleIdx >= 0, "art-style line present");
        assertTrue(universeIdx < roleIdx,
                () -> "021: Universe line MUST precede Professional role line; got universeIdx="
                        + universeIdx + ", roleIdx=" + roleIdx);
        assertTrue(roleIdx < artStyleIdx,
                () -> "021: Professional role line MUST precede Art style line; got roleIdx="
                        + roleIdx + ", artStyleIdx=" + artStyleIdx);
    }

    @Test
    void changingUniverseChangesPromptSubstring() {
        String a = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.RETRO_SYNTHWAVE, null, ArtStyle.OIL_PAINTING, "Paula", null));
        String b = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.NINETIES_SITCOM, null, ArtStyle.OIL_PAINTING, "Paula", null));
        assertFalse(a.equals(b), "universe change must alter the prompt");
        assertTrue(a.contains("synthwave"));
        assertTrue(b.contains("1990s sitcom"));
        assertFalse(a.contains("retro-synthwave"), "lowercase wire value must not appear");
        assertFalse(b.contains("nineties-sitcom"));
    }

    @Test
    void changingPoseChangesPromptSubstring() {
        String a = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
        String b = builder.build(new AlterEgoRequest(
                Pose.SCHOLAR, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
        assertFalse(a.equals(b), "pose change must alter the prompt");
        assertTrue(a.contains("heroic"));
        assertTrue(b.contains("scholarly"));
    }

    @Test
    void vibePresentVsAbsentProducesDifferentPrompts() {
        String withVibe = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null));
        String noVibe = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
        assertFalse(withVibe.equals(noVibe), "adding a vibe must alter the prompt");
        assertTrue(withVibe.contains("rebellious"), "vibe display label present when set");
        assertFalse(noVibe.contains("rebellious"), "vibe absent → its label must not leak in");
    }

    @Test
    void changingFirstNameNoLongerChangesPromptSinceFirstNameIsExcluded() {
        // 017 (refined 2026-05-08): firstName is no longer part of the
        // image prompt. Two requests that differ only in firstName now
        // produce identical prompts — the AI no longer sees the name.
        String paula = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
        String maria = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Maria", null));
        assertEquals(paula, maria,
                "017 (refined): firstName-only delta must NOT change the prompt");
        assertFalse(paula.contains("Paula"));
        assertFalse(maria.contains("Maria"));
    }

    @Test
    void displayLabelsAreHumanReadableNotWireValues() {
        // Pin every enum value to its display label so a future enum addition
        // without a label doesn't silently regress by falling back to the
        // wire form (defensive default in the builder).
        for (Archetype role : Archetype.values()) {
            String p = builder.build(new AlterEgoRequest(
                    Pose.HEROIC, role, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
            assertFalse(p.contains(role.wire() + "\n"),
                    () -> "wire value " + role.wire() + " must not appear as the role token");
        }
        for (Universe u : Universe.values()) {
            String p = builder.build(new AlterEgoRequest(
                    Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, u, null, ArtStyle.OIL_PAINTING, "Paula", null));
            assertFalse(p.contains(u.wire() + "\n"),
                    () -> "wire value " + u.wire() + " must not appear as the universe token");
        }
        for (Pose pose : Pose.values()) {
            String p = builder.build(new AlterEgoRequest(
                    pose, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
            // Pose labels share some substring with wire values (e.g. "heroic"
            // for HEROIC) — so just assert the prompt compiles and is distinct.
            assertFalse(p.isBlank());
        }
        for (Vibe v : Vibe.values()) {
            String p = builder.build(new AlterEgoRequest(
                    Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, v, ArtStyle.OIL_PAINTING, "Paula", null));
            assertFalse(p.isBlank());
        }
    }

    // 004 — art style distinctness + pinning. Per spec SC-305, every enum
    // value must emit a unique, non-empty prompt substring on the
    // "- Art style:" line. This locks the nine label strings against
    // accidental collapse or deletion.

    @Test
    void artStylePromptLineAppearsForEveryValue() {
        for (ArtStyle style : ArtStyle.values()) {
            String prompt = builder.build(new AlterEgoRequest(
                    Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                    null, style, "Paula", null));
            assertTrue(prompt.contains("- Art style: "),
                    () -> "prompt must contain an '- Art style:' line for " + style);
        }
    }

    @Test
    void artStyleLabelsAreMutuallyDistinct() {
        Set<String> lines = new HashSet<>();
        for (ArtStyle style : ArtStyle.values()) {
            String prompt = builder.build(new AlterEgoRequest(
                    Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                    null, style, "Paula", null));
            String line = extractLineStartingWith(prompt, "- Art style: ");
            assertNotNull(line, () -> "missing art-style line for " + style);
            assertFalse(line.endsWith("- Art style: "),
                    () -> "art-style line must not be empty for " + style);
            assertTrue(lines.add(line),
                    () -> "art-style line for " + style + " is a duplicate of a previous value: " + line);
        }
        assertEquals(ArtStyle.values().length, lines.size(),
                "each ArtStyle must produce a unique prompt line");
    }

    @Test
    void changingArtStyleChangesPromptSubstring() {
        String a = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, ArtStyle.OIL_PAINTING, "Paula", null));
        String b = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, ArtStyle.JAPANESE_WOODBLOCK, "Paula", null));
        assertFalse(a.equals(b), "art style change must alter the prompt");
        assertTrue(a.contains("oil painting"));
        assertTrue(b.contains("ukiyo-e"));
    }

    @Test
    void artStyleLinePrecedesPoseLineForComposition() {
        // Documented ordering: art style is an up-front rendering cue, so it
        // sits immediately after universe and before pose. Locks the prompt
        // layout so downstream prompt-engineering work has a stable reference.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, ArtStyle.POP_ART, "Paula", null));
        int artIdx = prompt.indexOf("- Art style:");
        int poseIdx = prompt.indexOf("- Pose / stance:");
        assertTrue(artIdx > 0, "art style line present");
        assertTrue(poseIdx > artIdx,
                () -> "pose line must appear after art style line; artIdx=" + artIdx + ", poseIdx=" + poseIdx);
    }

    // 011 — PhotoMode branching. Single vs. group prompts must differ on the
    // subject-count instruction and ONLY on that dimension (spec SC-1003);
    // Single is regression-locked to today's output byte-for-byte.

    @Test
    void defaultPhotoModeIsSingleForNullField() {
        // 011 FR-1009 / SC-1001 — a null photoMode defaults to SINGLE at the
        // domain boundary, so an old-client request without the field
        // continues to produce today's baseline prompt.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null));
        assertTrue(prompt.startsWith("Generate an uplifting, cinematic portrait poster of the person in the reference photo as an inspiring alter ego for a hopeful, innovation-driven future."),
                () -> "null photoMode must produce the SINGLE baseline opening, got: " + prompt.substring(0, Math.min(200, prompt.length())));
    }

    @Test
    void explicitSinglePhotoModeProducesTodaysBaselinePrompt() {
        // 011 SC-1001 / SC-1003 — SINGLE variant is byte-identical to today's
        // prompt. Any accidental divergence (e.g. reworded composition note)
        // would break backwards-compatibility with downstream consumers and
        // trigger this assertion.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", PhotoMode.SINGLE));
        String expected = """
                Generate an uplifting, cinematic portrait poster of the person in the reference photo as an inspiring alter ego for a hopeful, innovation-driven future.

                The subject's appearance (face, hair, skin tone, approximate age, general build) MUST closely match the reference photo. Render the subject as an optimistic, forward-looking "alter ego" with the following attributes:

                - Fictional universe / aesthetic: Star Wars
                - Professional role (render as uplifting visual cues — props, environment, attire, activity — NOT as text): Software Developer
                - Art style: oil painting, visible brushstrokes and impasto texture
                - Pose / stance: heroic, chest forward
                - Vibe / tone: rebellious

                Composition notes:
                - Portrait orientation, 3:4 aspect ratio, bright rim lighting on the subject.
                - Overall mood: hopeful, positive, energetic and forward-looking — the subject looks confident and inspired, like someone helping build a brighter, more sustainable tomorrow.
                - Background: bright, clean and airy — a light, luminous setting with an overall high-key palette that leans toward whites, soft blues and cool daylight tones, with subtle hints of renewable-energy optimism (open sky, sunlight, greenery or clean-energy motifs) where they fit the scene naturally. Avoid dark, murky, black or heavily shadowed backgrounds; the poster frame around this image is bright white and blue, so the scene must feel light and open, not gloomy.
                - Clear focus on the subject; the universe aesthetic is the setting, not the subject.
                - ABSOLUTELY NO rendered text anywhere in the image: no name plates, no banners, no parchment scrolls, no signs, no captions, no watermarks, no logos, and NO transcribing the role label. The poster's text overlay is composited downstream — your job is the visual scene only.
                """;
        // 017 (refined 2026-05-08): Name + Engineering role lines were
        // removed from the image prompt because the AI was rendering them
        // as decorative banner text inside the character cutout (e.g.
        // "CLOUD ARCHITECT LALO." on a parchment scroll). The negative-text
        // composition note was strengthened to enumerate the offenders.
        // 021 (2026-05-11, issue #54): Engineering role line is back —
        // now phrased as a visual scene direction with an inline "NOT as
        // text" clarifier, and the composition note adds an explicit
        // "NO transcribing the engineering-role label" clause. First
        // name remains excluded.
        assertEquals(expected, prompt, "SINGLE variant must match the current prompt");
    }

    @Test
    void groupPhotoModeUsesPluralWording() {
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "The Architects", PhotoMode.GROUP));
        // 011 FR-1010 — GROUP variant instructs the model to render every
        // person visible, forbids inventing additional people, and uses the
        // "Group name:" label for the collective identifier.
        assertTrue(prompt.startsWith("Generate an uplifting, cinematic group portrait poster of the people in the reference photo as inspiring alter egos for a hopeful, innovation-driven future."),
                () -> "GROUP opening line wrong, got: " + prompt.substring(0, Math.min(200, prompt.length())));
        assertTrue(prompt.contains("EVERY person visible"),
                "GROUP variant must instruct the model to render every person");
        assertTrue(prompt.contains("Do NOT invent additional people"),
                "GROUP variant must forbid inventing teammates");
        // 017 (refined 2026-05-08): name/role no longer in the prompt —
        // not even in GROUP mode. The collective identifier (firstName
        // for groups) was previously emitted as "Group name: …"; that
        // line is now omitted along with the SINGLE-mode "Name:" line.
        assertFalse(prompt.contains("Group name"),
                "017 (refined): GROUP prompt MUST NOT include a 'Group name:' line");
        assertFalse(prompt.contains("The Architects"),
                "017 (refined): GROUP prompt MUST NOT echo the collective name");
        assertTrue(prompt.contains("all subjects as a group"),
                "GROUP variant composition note must reference all subjects");
    }

    @Test
    void groupPhotoModeIncludesEngineeringRoleLine() {
        // 021 (issue #54) — GROUP variant must include the same role line
        // as SINGLE: the role IS back in the image prompt and applies
        // uniformly to every person rendered (US2 / FR-2105).
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP));
        assertTrue(prompt.contains("- Professional role ("),
                "021: GROUP prompt MUST contain the Professional role line header");
        assertTrue(prompt.contains("Software Developer"),
                "021: GROUP prompt MUST contain the role's Prompt label");
    }

    @Test
    void groupPhotoModeRoleLineFollowsUniverseAndPrecedesArtStyle() {
        // 021: ordering pin mirrors the SINGLE variant — Universe < role < Art-style.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP));
        int universeIdx = prompt.indexOf("- Fictional universe");
        int roleIdx     = prompt.indexOf("- Professional role (");
        int artStyleIdx = prompt.indexOf("- Art style:");
        assertTrue(universeIdx >= 0 && roleIdx >= 0 && artStyleIdx >= 0,
                "GROUP prompt MUST contain Universe / role / Art-style lines");
        assertTrue(universeIdx < roleIdx && roleIdx < artStyleIdx,
                () -> "021: GROUP ordering MUST be Universe < role < Art-style; got universeIdx="
                        + universeIdx + ", roleIdx=" + roleIdx + ", artStyleIdx=" + artStyleIdx);
    }

    @Test
    void groupPhotoModeCompositionNoteForbidsTranscribingTheRoleLabel() {
        // 021: GROUP composition note must carry the same strengthened
        // no-rendered-text rule as SINGLE (FR-2105 + Edge Cases).
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP));
        assertTrue(prompt.contains("NO transcribing the role label"),
                "021: GROUP composition note MUST contain the role-label-transcribe forbid clause");
    }

    @Test
    void groupPhotoModeOmitsVibeLineWhenVibeIsNull() {
        // Same optional-vibe behaviour as the SINGLE variant.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "The Architects", PhotoMode.GROUP));
        assertFalse(prompt.contains("Vibe"),
                "GROUP variant must omit the Vibe line when vibe is null");
    }

    @Test
    void groupPhotoModeIncludesVibeLineWhenVibeIsSet() {
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.THINKER, ArtStyle.OIL_PAINTING, "The Architects", PhotoMode.GROUP));
        assertTrue(prompt.contains("- Vibe / tone: thinker / strategist"),
                "GROUP variant must include the Vibe line when vibe is non-null");
    }

    @Test
    void singleAndGroupPromptsDifferForOtherwiseIdenticalInputs() {
        AlterEgoRequest single = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", PhotoMode.SINGLE);
        AlterEgoRequest group = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", PhotoMode.GROUP);
        String singlePrompt = builder.build(single);
        String groupPrompt = builder.build(group);
        assertFalse(singlePrompt.equals(groupPrompt),
                "mode change must alter the prompt");
    }

    private static String extractLineStartingWith(String haystack, String prefix) {
        int start = haystack.indexOf(prefix);
        if (start < 0) return null;
        int end = haystack.indexOf('\n', start);
        return end < 0 ? haystack.substring(start) : haystack.substring(start, end);
    }

    // 022 (issue #50) — custom role substitution at the prompt-builder
    // boundary. The image prompt MUST emit the trimmed custom string in
    // the role-line slot (overriding any prefab archetype label) and the
    // surrounding "NOT as text" framing MUST remain unchanged.

    @Test
    void customRoleAppearsVerbatimInTheRoleLine() {
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, null, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Paula", null, "Tester"));
        assertTrue(prompt.contains("Professional role (render as uplifting visual cues"),
                "022: role line header must remain unchanged");
        assertTrue(prompt.contains(": Tester"),
                "022: custom role 'Tester' MUST appear on the role line");
        assertFalse(prompt.contains("Software Developer"),
                "022: archetype label MUST NOT leak when null + custom present");
    }

    @Test
    void customRoleTakesPrecedenceOverPrefabArchetype() {
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Paula", null, "Tester"));
        assertTrue(prompt.contains(": Tester"),
                "022: customRole MUST win precedence over the prefab label");
        assertFalse(prompt.contains("Software Developer"),
                "022: the prefab label MUST NOT appear when customRole supplies the role-of-record");
    }

    @Test
    void blankCustomRoleFallsBackToPrefabLabel() {
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Paula", null, "   "));
        assertTrue(prompt.contains("Software Developer"),
                "022: whitespace-only customRole must NOT override the prefab label");
    }

    @Test
    void customRoleIsTrimmedBeforeInjection() {
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, null, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Paula", null, "  Tester  "));
        assertTrue(prompt.contains(": Tester\n"),
                "022: customRole MUST be trimmed before injection");
    }

    @Test
    void customRoleAppliesToGroupVariantToo() {
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, null, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP, "Tester"));
        assertTrue(prompt.contains("EVERY person visible"),
                "022: customRole must not regress GROUP wording");
        assertTrue(prompt.contains(": Tester"),
                "022: customRole MUST appear on the role line in GROUP variant");
    }

    @Test
    void noTextCompositionNoteIsUnchangedWhenCustomRoleSubstitutes() {
        // 022: the surrounding no-rendered-text composition note is byte-
        // identical regardless of whether the role line carries a prefab
        // label or a custom string.
        String prompt = builder.build(new AlterEgoRequest(
                Pose.HEROIC, null, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Paula", null, "Tester"));
        assertTrue(prompt.toLowerCase().contains("absolutely no rendered text"));
        assertTrue(prompt.contains("NO transcribing the role label"),
                "022: composition-note role-label-transcribe forbid clause stays unchanged");
    }

    private static AlterEgoRequest sampleRequestWithVibe(Vibe vibe) {
        return new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, vibe, ArtStyle.OIL_PAINTING, "Paula", null);
    }
}

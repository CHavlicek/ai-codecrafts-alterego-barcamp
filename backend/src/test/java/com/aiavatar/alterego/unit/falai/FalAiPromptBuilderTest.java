package com.aiavatar.alterego.unit.falai;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.PhotoMode;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.infrastructure.provider.falai.FalAiPromptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 016 T025 — {@link FalAiPromptBuilder} composition tests (FR-1606 / FR-1607).
 *
 * <ul>
 *   <li>Opens with the verb {@code "Edit"} (R3 / R11) — locks in the
 *       fal.ai-specific phrasing decision.</li>
 *   <li>Every Setup field (firstName, archetype, universe, art-style, pose,
 *       vibe-when-present) contributes a distinct token to the prompt.</li>
 *   <li>{@code vibe == null} omits the vibe line cleanly.</li>
 *   <li>{@code PhotoMode.GROUP} branches to plural wording with explicit
 *       "EVERY person visible" + "Do NOT invent" clauses.</li>
 * </ul>
 */
class FalAiPromptBuilderTest {

    private final FalAiPromptBuilder builder = new FalAiPromptBuilder();

    @Test
    void opensWithEditVerb() {
        // R3 / R11 — fal.ai's nano-banana-pro/edit is image-edit-conditioned;
        // the opener telegraphs that to the model.
        String prompt = builder.build(sampleRequest());
        assertTrue(prompt.startsWith("Edit the reference photo"),
                "fal.ai prompt MUST open with 'Edit the reference photo'; got: "
                        + prompt.substring(0, Math.min(80, prompt.length())));
    }

    @Test
    void firstNameIsNotPassedToImageGenerationAi() {
        // 017 (refined 2026-05-08): firstName MUST NOT appear in the
        // image prompt — the AI was rendering it as decorative banner
        // text inside the character cutout.
        String prompt = builder.build(sampleRequest("Lalo", null));
        assertFalse(prompt.contains("Lalo"),
                "image prompt MUST NOT contain the user's first name");
        assertFalse(prompt.contains("Name:"),
                "image prompt MUST NOT contain a 'Name:' line");
    }

    @Test
    void roleArchetypeAppearsInTheImagePrompt() {
        // 021 (issue #54): inverted from 017 (refined). The role IS back
        // in the image prompt as a visual scene direction. Each
        // Archetype's Prompt label (per data-model.md) MUST appear, and
        // the role-line header MUST be present.
        java.util.Map<Archetype, String> expectedLabels = java.util.Map.ofEntries(
                java.util.Map.entry(Archetype.CLOUD_ARCHITECT,    "Cloud Architect"),
                java.util.Map.entry(Archetype.BACKEND_DEV,        "Backend Developer"),
                java.util.Map.entry(Archetype.FRONTEND_DEV,       "Frontend Developer"),
                java.util.Map.entry(Archetype.AI_ENGINEER,        "AI Engineer"),
                java.util.Map.entry(Archetype.PLATFORM_ENG,       "Platform Engineer"),
                java.util.Map.entry(Archetype.DATA_ENGINEER,      "Data Engineer"),
                // 022 (issue #50) — three non-engineering prefab options.
                java.util.Map.entry(Archetype.HR,                 "Human Resources"),
                java.util.Map.entry(Archetype.ADMINISTRATION,     "Administration / Operations"),
                java.util.Map.entry(Archetype.CUSTOMER_RELATIONS, "Customer Relations / Support"));
        for (Archetype a : Archetype.values()) {
            AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, a, Universe.STAR_WARS,
                    null, ArtStyle.OIL_PAINTING, "Paula", null);
            String prompt = builder.build(req);
            String expected = expectedLabels.get(a);
            assertTrue(prompt.contains("Engineering role ("),
                    "021: image prompt MUST contain the Engineering role line header for " + a);
            assertTrue(prompt.contains(expected),
                    () -> "021: image prompt MUST contain Prompt label '" + expected + "' for " + a);
        }
    }

    @Test
    void changingRoleChangesPromptSubstring() {
        // 021 SC-2103 automatable half — a role-only delta MUST alter
        // the prompt now that the role is back in the image prompt.
        AlterEgoRequest a = new AlterEgoRequest(Pose.HEROIC, Archetype.BACKEND_DEV,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
        AlterEgoRequest b = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
        assertNotEquals(builder.build(a), builder.build(b),
                "021: role-only delta MUST change the prompt");
    }

    @Test
    void engineeringRoleLineFollowsUniverseAndPrecedesArtStyle() {
        // 021 contract: the role line sits between the Universe line and
        // the Art-style line — mirrors the Gemini-side ordering pin.
        String prompt = builder.build(sampleRequest("Paula", null));
        int universeIdx = prompt.indexOf("- Fictional universe");
        int roleIdx     = prompt.indexOf("- Engineering role (");
        int artStyleIdx = prompt.indexOf("- Art style:");
        assertTrue(universeIdx >= 0, "universe line present");
        assertTrue(roleIdx >= 0,     "021: role line present");
        assertTrue(artStyleIdx >= 0, "art-style line present");
        assertTrue(universeIdx < roleIdx,
                () -> "021: Universe line MUST precede Engineering role line; got universeIdx="
                        + universeIdx + ", roleIdx=" + roleIdx);
        assertTrue(roleIdx < artStyleIdx,
                () -> "021: Engineering role line MUST precede Art style line; got roleIdx="
                        + roleIdx + ", artStyleIdx=" + artStyleIdx);
    }

    @Test
    void compositionNoteForbidsTranscribingTheRoleLabel() {
        // 021: composition-notes no-text rule reinforced — explicitly
        // forbids transcribing the engineering-role label as decorative
        // banner text inside the image.
        String prompt = builder.build(sampleRequest());
        assertTrue(prompt.toLowerCase().contains("no transcribing the engineering-role label"),
                "021: composition note MUST contain the role-label-transcribe forbid clause");
    }

    @Test
    void includesEveryArtStyleDistinctly() {
        // FR-1607 SC-1602 axis: art style MUST contribute a distinct token.
        // Build prompts for every art style and confirm pair-wise differences.
        ArtStyle[] styles = ArtStyle.values();
        for (int i = 0; i < styles.length; i++) {
            for (int j = i + 1; j < styles.length; j++) {
                AlterEgoRequest a = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                        Universe.STAR_WARS, null, styles[i], "Paula", null);
                AlterEgoRequest b = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                        Universe.STAR_WARS, null, styles[j], "Paula", null);
                assertNotEquals(builder.build(a), builder.build(b),
                        "Art-style pair " + styles[i] + " vs " + styles[j]
                                + " MUST produce different prompts");
            }
        }
    }

    @Test
    void omitsVibeLineWhenVibeIsNull() {
        String prompt = builder.build(sampleRequest("Paula", null));
        assertFalse(prompt.contains("Vibe / tone:"),
                "vibe-absent prompt MUST NOT contain a 'Vibe / tone:' line");
    }

    @Test
    void includesVibeLineWhenVibeIsPresent() {
        String prompt = builder.build(sampleRequest("Paula", Vibe.REBEL));
        assertTrue(prompt.contains("Vibe / tone:"),
                "vibe-present prompt MUST include a 'Vibe / tone:' line");
        assertTrue(prompt.contains("rebellious"),
                "vibe REBEL maps to label 'rebellious'");
    }

    @ParameterizedTest
    @EnumSource(Pose.class)
    void everyPoseContributesDistinctToken(Pose pose) {
        AlterEgoRequest req = new AlterEgoRequest(pose, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Pose / stance:"),
                "prompt MUST include Pose / stance: line for " + pose);
    }

    @ParameterizedTest
    @EnumSource(Universe.class)
    void everyUniverseContributesDistinctToken(Universe universe) {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                universe, null, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Fictional universe / aesthetic:"),
                "prompt MUST include the universe line for " + universe);
    }

    @Test
    void groupModeIncludesEngineeringRoleLineForEveryArchetype() {
        // 021 (issue #54) US2 / FR-2105 — GROUP variant must carry the
        // role line for every Archetype value, with each archetype's
        // Prompt label appearing in the outbound prompt.
        java.util.Map<Archetype, String> expectedLabels = java.util.Map.ofEntries(
                java.util.Map.entry(Archetype.CLOUD_ARCHITECT,    "Cloud Architect"),
                java.util.Map.entry(Archetype.BACKEND_DEV,        "Backend Developer"),
                java.util.Map.entry(Archetype.FRONTEND_DEV,       "Frontend Developer"),
                java.util.Map.entry(Archetype.AI_ENGINEER,        "AI Engineer"),
                java.util.Map.entry(Archetype.PLATFORM_ENG,       "Platform Engineer"),
                java.util.Map.entry(Archetype.DATA_ENGINEER,      "Data Engineer"),
                // 022 (issue #50) — three non-engineering prefab options.
                java.util.Map.entry(Archetype.HR,                 "Human Resources"),
                java.util.Map.entry(Archetype.ADMINISTRATION,     "Administration / Operations"),
                java.util.Map.entry(Archetype.CUSTOMER_RELATIONS, "Customer Relations / Support"));
        for (Archetype a : Archetype.values()) {
            AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, a, Universe.STAR_WARS,
                    null, ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP);
            String prompt = builder.build(req);
            String expected = expectedLabels.get(a);
            assertTrue(prompt.contains("- Engineering role ("),
                    "021: GROUP prompt MUST contain the Engineering role line header for " + a);
            assertTrue(prompt.contains(expected),
                    () -> "021: GROUP prompt MUST contain Prompt label '" + expected + "' for " + a);
        }
    }

    @Test
    void groupModeRoleLineFollowsUniverseAndPrecedesArtStyle() {
        // 021: GROUP-variant ordering mirrors SINGLE — Universe < role < Art-style.
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP);
        String prompt = builder.build(req);
        int universeIdx = prompt.indexOf("- Fictional universe");
        int roleIdx     = prompt.indexOf("- Engineering role (");
        int artStyleIdx = prompt.indexOf("- Art style:");
        assertTrue(universeIdx >= 0 && roleIdx >= 0 && artStyleIdx >= 0,
                "GROUP prompt MUST contain Universe / role / Art-style lines");
        assertTrue(universeIdx < roleIdx && roleIdx < artStyleIdx,
                () -> "021: GROUP ordering MUST be Universe < role < Art-style; got universeIdx="
                        + universeIdx + ", roleIdx=" + roleIdx + ", artStyleIdx=" + artStyleIdx);
    }

    @Test
    void groupModeCompositionNoteForbidsTranscribingTheRoleLabel() {
        // 021: GROUP composition note must carry the same strengthened
        // no-rendered-text rule as SINGLE (FR-2105 + Edge Cases).
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Crew Six", PhotoMode.GROUP);
        String prompt = builder.build(req);
        assertTrue(prompt.toLowerCase().contains("no transcribing the engineering-role label"),
                "021: GROUP composition note MUST contain the role-label-transcribe forbid clause");
    }

    @Test
    void groupModeUsesPluralWordingAndForbidsInvention() {
        AlterEgoRequest groupReq = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "The Architects", PhotoMode.GROUP);
        String prompt = builder.build(groupReq);
        assertTrue(prompt.contains("EVERY person visible"),
                "GROUP prompt MUST instruct the model to render EVERY person visible");
        assertTrue(prompt.contains("Do NOT invent"),
                "GROUP prompt MUST forbid inventing additional people");
        // 017 (refined 2026-05-08): group name omitted along with SINGLE-mode
        // Name line — the collective identifier is no longer in the image
        // prompt.
        assertFalse(prompt.contains("Group name"),
                "017 (refined): GROUP prompt MUST NOT include a 'Group name:' line");
        assertFalse(prompt.contains("The Architects"),
                "017 (refined): GROUP prompt MUST NOT echo the collective name");
    }

    @Test
    void promptForbidsAllRenderedTextInTheImage() {
        // 017 (refined 2026-05-08): strengthened negative-text instruction
        // enumerates banner / scroll / name plate as forbidden.
        // 021 (2026-05-11, issue #54): additionally forbids transcribing
        // the engineering-role label — guards against the model painting
        // the role string as banner text now that the role is back in
        // the prompt.
        String prompt = builder.build(sampleRequest());
        String lower = prompt.toLowerCase();
        assertTrue(lower.contains("absolutely no rendered text"));
        assertTrue(lower.contains("banner") && lower.contains("scroll") && lower.contains("name plate"));
        assertTrue(lower.contains("no transcribing the engineering-role label"),
                "021: composition note MUST forbid transcribing the engineering-role label");
    }

    // 022 (issue #50) — custom role substitution. When the request carries
    // a non-blank customRole, the role line MUST render the trimmed custom
    // string verbatim instead of the prefab archetype's label. The
    // surrounding "render as visual cues — NOT as text" framing stays
    // unchanged.

    @Test
    void customRoleAppearsVerbatimInTheRoleLine() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, null,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null, "Tester");
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Engineering role (render as visual cues"),
                "022: role line header must remain unchanged");
        assertTrue(prompt.contains(": Tester"),
                () -> "022: custom role 'Tester' MUST appear on the role line; got: " + prompt);
        assertFalse(prompt.contains("Cloud Architect"),
                "022: when archetype is null + custom is present, no prefab label leaks");
    }

    @Test
    void customRoleTakesPrecedenceOverPrefabArchetype() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null, "Tester");
        String prompt = builder.build(req);
        assertTrue(prompt.contains(": Tester"),
                "022: customRole MUST win the precedence rule when both fields are set");
        assertFalse(prompt.contains("Cloud Architect"),
                "022: the prefab label MUST NOT appear when customRole supplies the role-of-record");
    }

    @Test
    void blankCustomRoleFallsBackToArchetypeLabel() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null, "   ");
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Cloud Architect"),
                "022: whitespace-only customRole must NOT override the prefab label");
    }

    @Test
    void customRoleIsTrimmedBeforeBeingEmittedToTheModel() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, null,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null, "   Tester   ");
        String prompt = builder.build(req);
        assertTrue(prompt.contains(": Tester\n"),
                "022: customRole MUST be trimmed before injection");
    }

    @Test
    void customRoleAppliesToGroupVariantToo() {
        AlterEgoRequest req = new AlterEgoRequest(Pose.HEROIC, null,
                Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Crew", PhotoMode.GROUP, "Tester");
        String prompt = builder.build(req);
        assertTrue(prompt.contains("EVERY person visible"),
                "022: customRole must not regress the GROUP wording");
        assertTrue(prompt.contains(": Tester"),
                "022: customRole MUST appear on the role line in the GROUP variant");
    }

    private static AlterEgoRequest sampleRequest() {
        return sampleRequest("Paula", Vibe.REBEL);
    }

    private static AlterEgoRequest sampleRequest(String firstName, Vibe vibe) {
        return new AlterEgoRequest(Pose.HEROIC, Archetype.CLOUD_ARCHITECT,
                Universe.STAR_WARS, vibe, ArtStyle.OIL_PAINTING, firstName, null);
    }
}

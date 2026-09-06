package com.aiavatar.alterego.unit.gemini;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterPromptBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T006 — Unit coverage for {@link GeminiCharacterPromptBuilder} (014).
 *
 * <p>Asserts that the rendered prompt:
 * <ul>
 *   <li>Mentions every Setup field (pose, archetype, universe, vibe, art style, firstName)
 *       so the LLM has full grounding (FR-1405).</li>
 *   <li>Carries the English-only instruction (FR-1418, clarification Q2).</li>
 *   <li>Carries the trait-shape rules — exactly three superpowers, ≤ 100 characters
 *       per trait — in the prompt body so the LLM is told the contract before it tries
 *       to satisfy it (FR-1406, defence-in-depth alongside the parser guard).</li>
 *   <li>Omits the vibe line entirely when {@code request.vibe() == null}.</li>
 *   <li>Interpolates {@code firstName} verbatim (no escaping or sanitization beyond
 *       what the 011 {@code ValidFirstName} validator already enforces) — FR-1419,
 *       clarification Q4.</li>
 * </ul>
 */
class GeminiCharacterPromptBuilderTest {

    private final GeminiCharacterPromptBuilder builder = new GeminiCharacterPromptBuilder();

    @Test
    void promptMentionsEnglishOnlyInstruction() {
        String prompt = builder.build(sample());
        assertTrue(prompt.toLowerCase().contains("english"),
                "prompt MUST instruct the LLM to respond in English (FR-1418); got:\n" + prompt);
    }

    @Test
    void promptDeclaresExactlyThreeSuperpowersRule() {
        String prompt = builder.build(sample());
        // Either "EXACTLY THREE" or "exactly 3" — be liberal in what we accept here.
        String lower = prompt.toLowerCase();
        assertTrue(lower.contains("exactly three") || lower.contains("exactly 3"),
                "prompt MUST tell the LLM there are exactly 3 superpowers (FR-1406); got:\n" + prompt);
    }

    @Test
    void promptDeclaresHundredCodepointMaximumPerTrait() {
        String prompt = builder.build(sample());
        // The prompt is a human-readable instruction; it can phrase the cap as
        // "100 characters" or similar — what matters is that the cap is mentioned.
        assertTrue(prompt.contains("100"),
                "prompt MUST tell the LLM the per-trait length cap is 100 (FR-1406); got:\n" + prompt);
    }

    @Test
    void promptIncludesFirstNameVerbatim() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Paula"),
                "prompt MUST contain the user's first name verbatim (FR-1419 — no extra escaping); got:\n"
                        + prompt);
    }

    @Test
    void promptDoesNotEscapeOrFenceFirstNameWithSpecialCharacters() {
        // FR-1419 says NO additional sanitization — the value goes through as-is.
        // 011 ValidFirstName allows letters / spaces / hyphens / apostrophes; verify
        // an apostrophe is interpolated verbatim, not escaped to \' or wrapped.
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, ArtStyle.OIL_PAINTING, "O'Brien", null);
        String prompt = builder.build(req);
        assertTrue(prompt.contains("O'Brien"),
                "first name with apostrophe MUST appear verbatim; got:\n" + prompt);
        assertFalse(prompt.contains("\\'") || prompt.contains("\\\""),
                "first name MUST NOT be escaped");
    }

    @Test
    void absentVibeOmitsVibeFieldLine() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        // The Setup-fields block uses "- " bullet lines. A null vibe MUST
        // omit the bullet entirely. The closing voice instruction may still
        // mention "vibe" lowercase as ordinary prose — that does not count
        // as a vibe field line.
        boolean hasVibeFieldLine = prompt.lines()
                .anyMatch(l -> l.startsWith("- Vibe"));
        assertFalse(hasVibeFieldLine,
                "prompt MUST omit the '- Vibe' bullet when request.vibe() is null; got:\n" + prompt);
    }

    @Test
    void presentVibeIncludesVibeFieldLine() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        boolean hasVibeFieldLine = prompt.lines()
                .anyMatch(l -> l.startsWith("- Vibe"));
        assertTrue(hasVibeFieldLine,
                "prompt MUST include the '- Vibe' bullet when request.vibe() is present; got:\n" + prompt);
    }

    @ParameterizedTest
    @EnumSource(Pose.class)
    void everyPoseLabelAppearsInPrompt(Pose pose) {
        AlterEgoRequest req = new AlterEgoRequest(
                pose, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        // The display label for each pose contains a distinct substring;
        // we just check the wire form's leading token survives.
        String marker = pose.wire().split("-")[0];
        assertTrue(prompt.toLowerCase().contains(marker),
                "prompt MUST mention pose " + pose + " (marker: " + marker + "); got:\n" + prompt);
    }

    @ParameterizedTest
    @EnumSource(Archetype.class)
    void everyArchetypeContributesADistinctLabel(Archetype archetype) {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, archetype, Universe.STAR_WARS,
                null, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        assertNotNull(prompt);
        assertFalse(prompt.isBlank());
        // Each archetype's label has at least one distinguishing word.
        // Verify the prompt's "Engineering role:" line is non-empty and
        // contains at least one alphabetical word matching the wire form.
        String firstWord = archetype.wire().split("-")[0];
        assertTrue(prompt.toLowerCase().contains(firstWord),
                "prompt MUST mention archetype " + archetype + "; got:\n" + prompt);
    }

    @ParameterizedTest
    @EnumSource(Universe.class)
    void everyUniverseContributesADistinctLabel(Universe universe) {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, universe,
                null, ArtStyle.OIL_PAINTING, "Paula", null);
        String prompt = builder.build(req);
        // 029 (verbund-rebrand): the character builder's universe labels no
        // longer derive from the wire value (e.g. NINETIES_SITCOM →
        // "1990s sitcom", not "nineties"). Pin each to a distinctive token
        // from its GeminiCharacterPromptBuilder.UNIVERSE_LABELS entry.
        String expectedToken = switch (universe) {
            case MARVEL -> "marvel";
            case STAR_WARS -> "star wars";
            case RETRO_SYNTHWAVE -> "synthwave";
            case NINETIES_SITCOM -> "1990s sitcom";
            case SPY_THRILLER -> "spy thriller";
            case GHOSTBUSTERS -> "ghostbusters";
        };
        assertTrue(prompt.toLowerCase().contains(expectedToken),
                "prompt MUST mention universe " + universe + "; got:\n" + prompt);
    }

    @ParameterizedTest
    @EnumSource(ArtStyle.class)
    void everyArtStyleContributesADistinctLabel(ArtStyle artStyle) {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, artStyle, "Paula", null);
        String prompt = builder.build(req);
        String firstWord = artStyle.wire().split("-")[0];
        assertTrue(prompt.toLowerCase().contains(firstWord),
                "prompt MUST mention art style " + artStyle + "; got:\n" + prompt);
    }

    @Test
    void promptInstructsRespondingWithJsonOnly() {
        // Defensive — research §R3 calls for structured-JSON output and the
        // prompt must explicitly tell the LLM to respond with the JSON object,
        // no prose, no code fences. We verify "JSON" appears.
        String prompt = builder.build(sample());
        assertTrue(prompt.toUpperCase().contains("JSON"),
                "prompt MUST tell the LLM to respond with JSON; got:\n" + prompt);
    }

    // 022 (issue #50) — bio prompt MUST also consume the role-of-record so
    // the bio's role mentions match the poster's role label. Custom role
    // takes precedence over the prefab archetype's label; whitespace-only
    // custom falls back to the prefab.

    @Test
    void customRoleAppearsInTheBioPromptVerbatim() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, null, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null, "Tester");
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Tester"),
                "022: bio prompt MUST contain the trimmed customRole verbatim");
        assertFalse(prompt.contains("Software Developer"),
                "022: archetype label MUST NOT leak when null + custom present");
    }

    @Test
    void customRoleTakesPrecedenceOverPrefabInBioPrompt() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null, "Tester");
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Tester"),
                "022: customRole takes precedence in the bio prompt");
        assertFalse(prompt.contains("Software Developer"),
                "022: prefab label is replaced when customRole is present");
    }

    @Test
    void blankCustomRoleFallsBackToPrefabBioLabel() {
        AlterEgoRequest req = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null, "   ");
        String prompt = builder.build(req);
        assertTrue(prompt.contains("Software Developer"),
                "022: whitespace-only customRole does NOT override prefab in bio prompt");
    }

    private static AlterEgoRequest sample() {
        return new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
    }
}

package com.aiavatar.alterego.infrastructure.provider.stub;

import org.springframework.context.annotation.Primary;


import com.aiavatar.alterego.application.port.CharacterGeneratorPort;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.Provider;
// Same package as StubGenerationException — no import needed but kept for clarity.
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic, fixture-driven character generator. Loads
 * {@code stubs/characters.json} once at startup and indexes its variants
 * by {@link Archetype}. At generation time, hashes the request tuple to
 * pick a variant in a stable, repeatable way (research.md §R6).
 *
 * <p>002 delta: the hash input drops {@code colour} (no longer a user input)
 * and picks up the optional {@code vibe} when present. The fixture file's
 * keys are the new archetype wire values.
 *
 * <p>Active under the {@code default} profile only. The {@code gemini}
 * profile activates {@link com.aiavatar.alterego.infrastructure.provider.gemini.GeminiCharacterGenerator}
 * (014) which is the {@code @Primary} bean for that profile. The
 * {@code force-stub-failure} profile registers
 * {@link ForceFailureCharacterGenerator} instead so SC-004 / SC-1404 is
 * testable.
 *
 * <p>014 delta: profile narrowed from {@code {"default", "gemini"}} to
 * {@code "default"} only (research §R7) — symmetric with how
 * {@link com.aiavatar.alterego.infrastructure.provider.stub.StubImageGenerator} relates to
 * {@link com.aiavatar.alterego.infrastructure.provider.gemini.GeminiImageGenerator}, and
 * removes the dormant Stub bean from the gemini-profile context for
 * clarity.
 */
@Component
@Profile({"default", "falai"})
@Primary
public class StubCharacterGenerator implements CharacterGeneratorPort {

    private static final String FIXTURE_PATH = "/stubs/characters.json";

    private final Map<Archetype, List<CharacterVariant>> variants;

    public StubCharacterGenerator(ObjectMapper objectMapper) {
        this.variants = loadFixtures(objectMapper);
    }

    @Override
    public Provider provider() {
        return Provider.STUB;
    }

    @Override
    public boolean externalRetry() {
        return false;
    }

    private static Map<Archetype, List<CharacterVariant>> loadFixtures(ObjectMapper objectMapper) {
        try (InputStream in = StubCharacterGenerator.class.getResourceAsStream(FIXTURE_PATH)) {
            if (in == null) {
                throw new StubGenerationException("Character fixtures not found at " + FIXTURE_PATH);
            }
            Map<String, List<CharacterVariant>> raw = objectMapper.readValue(
                    in, new TypeReference<Map<String, List<CharacterVariant>>>() {});
            EnumMap<Archetype, List<CharacterVariant>> indexed = new EnumMap<>(Archetype.class);
            for (Map.Entry<String, List<CharacterVariant>> entry : raw.entrySet()) {
                Archetype archetype = Archetype.fromWire(entry.getKey());
                indexed.put(archetype, List.copyOf(entry.getValue()));
            }
            // Fail fast at startup if any archetype lacks variants.
            for (Archetype archetype : Archetype.values()) {
                List<CharacterVariant> v = indexed.get(archetype);
                if (v == null || v.isEmpty()) {
                    throw new StubGenerationException(
                            "Character fixtures missing variants for archetype: " + archetype.wire());
                }
            }
            return Map.copyOf(indexed);
        } catch (IOException e) {
            throw new StubGenerationException("Failed to load character fixtures", e);
        }
    }

    @Override
    public GeneratedCharacter generate(AlterEgoRequest request) {
        AlterEgoRequest req = request.withTrimmedFirstName();
        List<CharacterVariant> archetypeVariants = variants.get(req.archetype());
        // Loaded eagerly; a missing archetype here means a configuration bug, not a runtime miss.
        int idx = pickIndex(req, archetypeVariants.size());
        CharacterVariant pick = archetypeVariants.get(idx);
        String line2 = pick.heroTitleLine2();
        if (req.vibe() != null) {
            // Light tonal tilt: prepend the vibe label to line 2 so the poster
            // reflects the user's optional selection without replacing the
            // archetype-driven variant. FR-131 differentiation is preserved.
            line2 = "The " + req.vibe().label() + " " + stripLeadingThe(line2);
        }
        return new GeneratedCharacter(
                req.firstName().toUpperCase(Locale.ROOT),
                line2,
                pick.tagline(),
                pick.superpowers(),
                pick.quote()
        );
    }

    private static String stripLeadingThe(String s) {
        if (s.regionMatches(true, 0, "The ", 0, 4)) {
            return s.substring(4);
        }
        return s;
    }

    private static int pickIndex(AlterEgoRequest req, int variantCount) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            String key = req.pose().wire() + "|"
                    + req.archetype().wire() + "|"
                    + req.universe().wire() + "|"
                    + (req.vibe() == null ? "" : req.vibe().wire()) + "|"
                    + req.firstName();
            byte[] hash = sha.digest(key.getBytes(StandardCharsets.UTF_8));
            int bucket = ((hash[0] & 0x7F) << 24)
                    | ((hash[1] & 0xFF) << 16)
                    | ((hash[2] & 0xFF) << 8)
                    | (hash[3] & 0xFF);
            return bucket % variantCount;
        } catch (NoSuchAlgorithmException e) {
            throw new StubGenerationException("SHA-256 not available", e);
        }
    }

    record CharacterVariant(String heroTitleLine2, String tagline, List<String> superpowers, String quote) {
    }
}

package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T037 (003) + extended in T018 (014) — {@code gemini} profile active but
 * no API key configured → every Generate request takes the fallback path
 * with {@code reason=not_configured} (003 FR-212, SC-204).
 *
 * <p>Both the image short-circuit (003) and the new character short-circuit
 * (014 FR-1403) live inside their respective {@code Generator}s via
 * {@code GeminiProperties.isConfigured()}; **no outbound HTTP attempt is
 * made on either path**. WireMock is not needed because nothing is sent.
 *
 * <p>014 delta: assertions strengthened to confirm that BOTH paths fell
 * back (not just the image side) — verified by asserting that the
 * character text matches {@link com.aiavatar.alterego.infrastructure.provider.fallback.FallbackPosterProvider}'s
 * canned "The Resilient" output, which only the fallback path produces.
 * Under 003 the stub character generator's pool of variants would have
 * supplied a different {@code heroTitleLine2}; under 014 (with the stub
 * also profile-narrowed by T029) only the fallback path can produce these
 * exact lines.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("gemini")
@TestPropertySource(properties = {
        "aiavatar.gemini.api-key=",  // explicitly blank
})
class GenerateAlterEgoGeminiNotConfiguredIT {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void geminiProfileWithBlankKeyReturnsFallbackWithNotConfigured() {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value(),
                "FR-212 + FR-1403: backend MUST stay up and serve a fallback when the key is missing");
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        assertEquals(FallbackReason.NOT_CONFIGURED, body.meta().reason());
        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"));

        // FR-1408 / FR-1404 — the fallback character preserves the user's first name.
        assertEquals("PAULA", body.character().heroTitleLine1());

        // 014 — both the image AND character paths short-circuited pre-HTTP.
        // FallbackPosterProvider.character is the only producer of the canned
        // "The Resilient" line; if the stub character generator had answered
        // (which it would have under the legacy {default,gemini} stub profile),
        // heroTitleLine2 would be one of the cloud-architect variants instead.
        assertEquals("The Resilient", body.character().heroTitleLine2(),
                "BOTH paths MUST short-circuit to the fallback provider when the key is blank "
                        + "(014 FR-1403 + 003 FR-212).");
        assertEquals("DEGRADED, NOT DEFEATED.", body.character().tagline());
        assertEquals(3, body.character().superpowers().size());
    }
}

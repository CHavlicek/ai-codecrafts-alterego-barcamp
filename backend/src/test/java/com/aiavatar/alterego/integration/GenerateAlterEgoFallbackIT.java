package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T020 — Force-failure integration test. Activates the
 * {@code force-stub-failure} profile so the {@code ForceFailure*}
 * generators replace the real stubs and throw on every call. Asserts that:
 *
 * <ul>
 *   <li>the response is still HTTP 200 (FR-018 — the user always gets a poster),</li>
 *   <li>{@code meta.outcome} is {@code "fallback"} so the frontend can render the FR-023 banner,</li>
 *   <li>the poster + character are fully populated from
 *       {@link com.aiavatar.alterego.infrastructure.provider.fallback.FallbackPosterProvider}.</li>
 * </ul>
 *
 * Drives SC-004 ("≤ 5 s to fallback poster") at the integration layer; the
 * Playwright spec for the same SC lands in sub-checkpoint E.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("force-stub-failure")
class GenerateAlterEgoFallbackIT {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void forceFailureProfileStillReturnsCompletePosterWithFallbackOutcome() {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value(),
                "FR-018: fallback path MUST still return 200 with a complete poster");
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        // force-stub-failure throws StubGenerationException (generic
        // RuntimeException), which AlterEgoService maps to the catch-all
        // MALFORMED_RESPONSE reason per 003 FR-218 / research.md R5.
        assertEquals(FallbackReason.MALFORMED_RESPONSE, body.meta().reason());

        assertEquals("PAULA", body.character().heroTitleLine1());
        assertEquals("The Resilient", body.character().heroTitleLine2());
        assertEquals(3, body.character().superpowers().size());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"));
        // 015: fallback poster is composited into the frame asset's canvas
        // (768×1152, 2:3 portrait) — FR-1507/FR-1513/SC-1503.
        assertEquals(768, body.poster().widthPx());
        assertEquals(1152, body.poster().heightPx());
    }
}

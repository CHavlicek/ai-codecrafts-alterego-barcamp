package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.FallbackReason;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Provider;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T019 — Full-stack integration test for the happy path. Boots the real
 * Spring context (default profile → real stub generators), POSTs a real
 * multipart body over an HTTP socket, and asserts the end-to-end response
 * shape. Honors Principle III's mandatory integration-test gate per
 * feature.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
class GenerateAlterEgoIT {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void defaultProfileServesStubFallbackWithNotConfiguredReason() {
        // 016 FR-1612 / FR-1603 — under the default profile (no real-provider
        // profile active) the orchestrator short-circuits to the fallback
        // branch with reason=NOT_CONFIGURED, provider=STUB. This is a
        // wire-shape change from 003, where default-profile runs reported
        // outcome=REAL. The user-visible behaviour (poster appears, generic
        // fallback notice when applicable) is unchanged.
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        assertEquals(FallbackReason.NOT_CONFIGURED, body.meta().reason());
        assertEquals(Provider.STUB, body.meta().provider());
        assertNotNull(body.meta().correlationId());
        assertEquals("PAULA", body.character().heroTitleLine1());
        assertEquals(3, body.character().superpowers().size());
        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"),
                "poster.dataUrl should be a PNG data URL");
        // 015: every poster is composited into the frame asset's canvas
        // (768×1152, 2:3 portrait, 10×15 print-ready) — FR-1507/SC-1503.
        assertEquals(768, body.poster().widthPx());
        assertEquals(1152, body.poster().heightPx());
    }

    @Test
    void differentFirstNamesProduceDifferentPosters() {
        // 016 FR-1612: default profile routes through FallbackPosterProvider,
        // whose character is fixed ("The Resilient") regardless of archetype.
        // The first-name uppercase still flows through to heroTitleLine1, so
        // that's what we assert differs across requests. The archetype-varied
        // character text under a real provider is covered by the gemini-profile
        // ITs (GenerateAlterEgoGeminiCharacterIT).
        AlterEgoRequest paula = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);
        AlterEgoRequest maria = new AlterEgoRequest(
                Pose.STEALTHY, Archetype.DATA_ANALYST, Universe.RETRO_SYNTHWAVE, null, ArtStyle.CEL_SHADED, "Maria", null);

        ResponseEntity<AlterEgoResponse> r1 = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", paula),
                AlterEgoResponse.class);
        ResponseEntity<AlterEgoResponse> r2 = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", maria),
                AlterEgoResponse.class);

        AlterEgoResponse paulaBody = r1.getBody();
        AlterEgoResponse mariaBody = r2.getBody();
        assertNotNull(paulaBody);
        assertNotNull(mariaBody);
        assertEquals("PAULA", paulaBody.character().heroTitleLine1());
        assertEquals("MARIA", mariaBody.character().heroTitleLine1());
        assertTrue(!paulaBody.character().heroTitleLine1()
                .equals(mariaBody.character().heroTitleLine1()),
                "Different first names MUST produce different heroTitleLine1");
    }
}

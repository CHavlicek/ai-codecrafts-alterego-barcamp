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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 015 US3 (P2) — end-to-end {@code @SpringBootTest} that proves the new
 * frame chrome wraps every poster on the fallback path too.
 *
 * <p>Replaces deleted 008 {@code BrandingOverlayFallbackIT}. Activates
 * the {@code force-stub-failure} profile so the {@code ForceFailure*}
 * generators short-circuit to {@link com.aiavatar.alterego.infrastructure.provider.fallback.FallbackPosterProvider}
 * — the structural mirror of {@link GenerateAlterEgoFallbackIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("force-stub-failure")
class PosterFrameOverlayFallbackIT {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void fallbackPosterCarriesFrameChromeAndIsTwoToThree() throws Exception {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                null, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        assertEquals(FallbackReason.MALFORMED_RESPONSE, body.meta().reason());

        String dataUrl = body.poster().dataUrl();
        assertTrue(dataUrl.startsWith("data:image/png;base64,"),
                "FR-1509: data URL must announce image/png");
        byte[] pngBytes = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
        assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(pngBytes, 8));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertEquals(768, decoded.getWidth());
        assertEquals(1152, decoded.getHeight());
        double ratio = decoded.getHeight() / (double) decoded.getWidth();
        assertTrue(Math.abs(ratio - 1.5) <= 0.015,
                "FR-1507/FR-1513: fallback poster must be 2:3 portrait within ±1%; got " + ratio);

        // Frame chrome at SQUER-mark coord must be opaque AND not the
        // FallbackPosterProvider's solid-black background.
        int chromePixel = decoded.getRGB(68, 38);
        int chromeAlpha = (chromePixel >>> 24) & 0xFF;
        assertEquals(255, chromeAlpha,
                "FR-1513: fallback poster must carry frame chrome opaque at SQUER-mark coord");
        assertNotEquals(0, chromePixel & 0x00FFFFFF,
                "FR-1513: fallback poster's chrome pixel must NOT be the solid-black background");
    }
}

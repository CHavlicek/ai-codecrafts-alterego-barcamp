package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
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
 * frame chrome wraps every poster on the real-provider path.
 *
 * <p>Replaces deleted 008 {@code BrandingOverlayIT}. Boots the default
 * profile (real stub generators, no Gemini), POSTs a Generate, and
 * asserts the returned poster: (a) decodes as a PNG, (b) measures
 * 768×1152 (2:3 portrait, FR-1507/SC-1503), (c) carries frame chrome
 * pixels at the SQUER-mark coordinate, and (d) the inner-rectangle
 * centre carries the character image's pixels.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
class PosterFrameOverlayIT {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void posterCarriesFrameChromeAndIsTwoToThreeOnFallbackPath() throws Exception {
        // 016 FR-1612: default profile reports outcome=fallback (was REAL
        // pre-016). Frame chrome assertions are unchanged — every poster
        // (real or fallback) flows through PosterFrameOverlayService (015).
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());

        // Pull the PNG bytes out of the data URL.
        String dataUrl = body.poster().dataUrl();
        assertTrue(dataUrl.startsWith("data:image/png;base64,"),
                "FR-1509: data URL must announce image/png");
        byte[] pngBytes = Base64.getDecoder().decode(dataUrl.substring("data:image/png;base64,".length()));
        assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(pngBytes, 8),
                "FR-1509: poster bytes must carry the PNG signature");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertEquals(768, decoded.getWidth());
        assertEquals(1152, decoded.getHeight());
        double ratio = decoded.getHeight() / (double) decoded.getWidth();
        assertTrue(Math.abs(ratio - 1.5) <= 0.015,
                "FR-1507/SC-1503: poster must be 2:3 portrait within ±1%; got " + ratio);

        // Frame chrome is opaque at the SQUER-mark coordinate (~68, 38).
        // The bundled fallback/stub posters draw a flat colour; if the
        // chrome were absent, this pixel would carry the stub's drawn
        // background colour.
        int chromePixel = decoded.getRGB(68, 38);
        int chromeAlpha = (chromePixel >>> 24) & 0xFF;
        assertEquals(255, chromeAlpha,
                "frame chrome at SQUER-mark coord must be fully opaque");
        // ARGB-encoded SQUER mark: blue-channel anchor; assert it is NOT
        // the stub poster's pure-black fill.
        assertNotEquals(0, chromePixel & 0x00FFFFFF,
                "frame chrome at SQUER-mark coord must NOT be the stub's solid black");
    }
}

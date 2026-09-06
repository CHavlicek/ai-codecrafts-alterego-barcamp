package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAsset;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAssetLoader;
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
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 017 T033 — End-to-end integration test for the text-overlay step.
 *
 * <p>POSTs {@code /api/v1/alter-egos}, decodes the response's
 * {@code poster.dataUrl}, and asserts the bottom-region of the PNG
 * contains a non-trivial number of near-white pixels (proxy for "text
 * was drawn"). Runs under the {@code force-stub-failure} profile so the
 * full pipeline is deterministic — no real provider, no network.
 *
 * <p>FR-1709 invariant: the fallback path MUST also receive the text
 * overlay. The threshold is set generously (the canonical
 * "Alter ego poster for {firstName}: {heroTitleLine1}. {tagline}" string
 * at the default sizes typically lights up ~3–5% of the bottom region
 * in light pixels) but not so high that font-metric drift across builds
 * breaks the test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("force-stub-failure")
class GenerateAlterEgoTextOverlayIT {

    @Autowired private TestRestTemplate restTemplate;
    @Autowired private PosterFrameAssetLoader frameAssetLoader;

    @Test
    void fallbackPosterCarriesNameTitleAndTaglineInBottomRegion() throws Exception {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Ada", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());

        // Decode the response PNG and inspect the bottom region.
        String dataUrl = body.poster().dataUrl();
        assertTrue(dataUrl.startsWith("data:image/png;base64,"));
        byte[] pngBytes = Base64.getDecoder().decode(
                dataUrl.substring("data:image/png;base64,".length()));
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertNotNull(decoded, "decoded poster PNG must not be null");
        assertEquals(body.poster().widthPx(), decoded.getWidth());
        assertEquals(body.poster().heightPx(), decoded.getHeight());

        PosterFrameAsset asset = frameAssetLoader.get();
        assertTrue(asset.loaded(), "frame asset must be loaded for this assertion to be meaningful");
        long lightPixels = countLightPixels(decoded,
                asset.bottomRegionX(), asset.bottomRegionY(),
                asset.bottomRegionWidthPx(), asset.bottomRegionHeightPx());
        long regionPixels = (long) asset.bottomRegionWidthPx() * asset.bottomRegionHeightPx();

        // Threshold: > 1% of bottom-region pixels. The canonical fallback
        // text "Ada / PAULA / DEGRADED, NOT DEFEATED." at default 64/32/20
        // sizes lights up considerably more in practice; 1% is a safety
        // margin against font-metric drift between builds.
        assertTrue(lightPixels > regionPixels / 100,
                "FR-1709 / SC-1701: bottom region must contain >1% light pixels (text drawn). "
                        + "Got " + lightPixels + " light pixels of " + regionPixels
                        + " (" + (100.0 * lightPixels / regionPixels) + "%)");
    }

    private static long countLightPixels(BufferedImage img, int x0, int y0, int w, int h) {
        long count = 0;
        int[] row = new int[w];
        for (int y = y0; y < y0 + h; y++) {
            img.getRGB(x0, y, w, 1, row, 0, w);
            for (int rgb : row) {
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r > 200 && g > 200 && b > 200) {
                    count++;
                }
            }
        }
        return count;
    }
}

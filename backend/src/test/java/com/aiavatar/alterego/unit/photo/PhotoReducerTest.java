package com.aiavatar.alterego.unit.photo;

import com.aiavatar.alterego.domain.model.PhotoPayload;
import com.aiavatar.alterego.infrastructure.photo.PhotoReducer;
import com.aiavatar.alterego.infrastructure.photo.PhotoReductionConfig;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link PhotoReducer}:
 * <ul>
 *   <li>Under-threshold photos pass through bytes-identical (003 FR-207).</li>
 *   <li>Over-threshold photos are reduced to JPEG at the configured target
 *       longest-edge.</li>
 *   <li>Never up-scales a small photo (003 FR-207).</li>
 *   <li>Output mime is {@code image/jpeg} on reduction.</li>
 * </ul>
 *
 * <p>016: reducer is now a stateless component driven by a
 * {@link PhotoReductionConfig} parameter on each call (R4) so both the
 * Gemini path (003) and the fal.ai path (016) can drive it with their own
 * provider-tuned thresholds.
 */
class PhotoReducerTest {

    // Defaults mirror application.yml (003 R4/R6): 4 MB / 1536 px ceilings;
    // reduce to 1024 px JPEG q=0.85.
    private static final PhotoReductionConfig DEFAULTS = new PhotoReductionConfig(
            4 * 1024 * 1024, 1536, 1024, 0.85);

    private final PhotoReducer reducer = new PhotoReducer();

    @Test
    void underThresholdPhotoPassesThroughBytesIdentical() throws IOException {
        byte[] smallPng = renderPng(256, 256);
        PhotoPayload input = new PhotoPayload(smallPng, "image/png");

        PhotoPayload output = reducer.reduce(input, DEFAULTS);

        assertArrayEquals(smallPng, output.bytes(),
                "under-threshold photo must pass through bytes-identical");
        assertEquals("image/png", output.mediaType(),
                "under-threshold pass-through preserves the original mime");
    }

    @Test
    void overPixelThresholdPhotoIsReducedAndReencodedAsJpeg() throws IOException {
        // 2048 px square PNG (exceeds 1536 longest-edge ceiling)
        byte[] largePng = renderPng(2048, 2048);
        PhotoPayload input = new PhotoPayload(largePng, "image/png");

        PhotoPayload output = reducer.reduce(input, DEFAULTS);

        assertNotNull(output.bytes());
        assertEquals("image/jpeg", output.mediaType(),
                "over-threshold reduction MUST re-encode as JPEG");

        BufferedImage reducedImg = ImageIO.read(new ByteArrayInputStream(output.bytes()));
        assertNotNull(reducedImg, "reduced bytes must decode as a valid image");
        int longestEdge = Math.max(reducedImg.getWidth(), reducedImg.getHeight());
        assertEquals(1024, longestEdge,
                "longest edge MUST equal reducedTargetLongestEdge (1024)");
    }

    @Test
    void overByteThresholdPhotoIsReduced() throws IOException {
        // Tight maxBytes so any PNG we build will exceed it.
        PhotoReductionConfig strict = new PhotoReductionConfig(
                /* maxBytes */ 100,
                1536, 256, 0.85);

        byte[] pngBytes = renderPng(64, 64);
        assertTrue(pngBytes.length > 100,
                "fixture must be bigger than the tight byte ceiling");
        PhotoPayload input = new PhotoPayload(pngBytes, "image/png");

        PhotoPayload output = reducer.reduce(input, strict);

        assertEquals("image/jpeg", output.mediaType());
        BufferedImage reduced = ImageIO.read(new ByteArrayInputStream(output.bytes()));
        assertNotNull(reduced);
    }

    @Test
    void neverUpscalesASmallPhoto() throws IOException {
        // 64×64 is well under both thresholds → pass-through. It MUST NOT be
        // upscaled to the reducedTargetLongestEdge.
        byte[] tinyPng = renderPng(64, 64);
        PhotoPayload input = new PhotoPayload(tinyPng, "image/png");

        PhotoPayload output = reducer.reduce(input, DEFAULTS);

        assertArrayEquals(tinyPng, output.bytes(),
                "small photos MUST pass through unchanged, never upscaled");
    }

    @Test
    void portraitOrientationPreservedAcrossReduction() throws IOException {
        // Portrait aspect — width 1024, height 2048. Longest edge is 2048 →
        // over-threshold → reduce. Expect: longest edge == 1024,
        // aspect ratio (1:2) preserved within rounding.
        byte[] portraitPng = renderPng(1024, 2048);
        PhotoPayload input = new PhotoPayload(portraitPng, "image/png");

        PhotoPayload output = reducer.reduce(input, DEFAULTS);

        BufferedImage reduced = ImageIO.read(new ByteArrayInputStream(output.bytes()));
        assertTrue(reduced.getHeight() >= reduced.getWidth(),
                "portrait orientation must be preserved after reduction");
        assertEquals(1024, Math.max(reduced.getWidth(), reduced.getHeight()),
                "longest edge MUST equal reducedTargetLongestEdge");
    }

    @Test
    void perProviderConfigsDriveDifferentReductionTargets() throws IOException {
        // 016 R4: same input, two different per-provider configs → different
        // resized output dimensions. Locks in the parameterisation contract.
        byte[] largePng = renderPng(2048, 2048);
        PhotoPayload input = new PhotoPayload(largePng, "image/png");

        PhotoReductionConfig geminiCfg = new PhotoReductionConfig(
                4 * 1024 * 1024, 1536, 1024, 0.85);
        PhotoReductionConfig falaiCfg = new PhotoReductionConfig(
                4 * 1024 * 1024, 1536, 768, 0.85);

        BufferedImage geminiReduced = ImageIO.read(new ByteArrayInputStream(
                reducer.reduce(input, geminiCfg).bytes()));
        BufferedImage falaiReduced = ImageIO.read(new ByteArrayInputStream(
                reducer.reduce(input, falaiCfg).bytes()));

        assertEquals(1024, Math.max(geminiReduced.getWidth(), geminiReduced.getHeight()));
        assertEquals(768, Math.max(falaiReduced.getWidth(), falaiReduced.getHeight()));
    }

    private static byte[] renderPng(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        // Fill with a non-uniform colour so JPEG compression has something
        // to chew on and doesn't produce a zero-byte output.
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int rgb = new Color(x % 256, y % 256, (x + y) % 256).getRGB();
                img.setRGB(x, y, rgb);
            }
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }
    }
}

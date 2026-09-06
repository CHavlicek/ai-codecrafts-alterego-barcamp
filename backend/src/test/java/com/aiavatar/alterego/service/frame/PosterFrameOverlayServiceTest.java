package com.aiavatar.alterego.service.frame;

import com.aiavatar.alterego.infrastructure.overlay.frame.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiavatar.alterego.domain.model.PosterImage;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PosterFrameOverlayService} (T008/T009/T018/T019).
 *
 * <p>Covers:
 * <ul>
 *   <li>FR-1501 (frame chrome composited on every poster) — T008
 *   <li>FR-1512 (fail-soft when asset missing) — T009
 *   <li>FR-1511 (wrong-ratio corrective letterbox) — T018
 *   <li>FR-1509 (always PNG output regardless of input MIME) — T019
 * </ul>
 */
class PosterFrameOverlayServiceTest {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final int FLAT_COLOUR_RGB = 0x224488;

    private final PosterFrameAssetLoader loader = bundledLoader();
    private final PosterFrameOverlayService service = new PosterFrameOverlayService(loader);

    // ============================================================== T008
    @Test
    void appliesFrameChromeToRealProviderImage() throws IOException {
        PosterImage input = solidPoster(768, 1152, "image/png", FLAT_COLOUR_RGB);

        PosterImage output = service.apply(input);

        assertEquals("image/png", output.mediaType(), "FR-1509: always PNG");
        assertEquals(768, output.widthPx());
        assertEquals(1152, output.heightPx());
        assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(output.bytes(), 8),
                "framed output bytes must start with the PNG signature");

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.bytes()));
        // The SQUER mark sits at roughly (68, 38) on the asset's chrome region.
        // Compositing draws the asset on top of the character image, so this
        // pixel must NOT be the input's flat colour.
        int chromePixel = decoded.getRGB(68, 38) & 0x00FFFFFF;
        assertNotEquals(FLAT_COLOUR_RGB, chromePixel,
                "frame chrome must be drawn on top of the character image at (68, 38)");

        // The inner-rectangle centre at (384, 576) IS inside the transparent
        // window — the character image shows through exactly.
        int innerCentre = decoded.getRGB(384, 576) & 0x00FFFFFF;
        assertEquals(FLAT_COLOUR_RGB, innerCentre,
                "inner-rectangle centre must show the character image's flat colour");
    }

    // ============================================================== T009
    @Test
    void failSoftReturnsInputWhenAssetMissing() throws IOException {
        PosterFrameAssetLoader missingLoader = new MissingLoader();
        PosterFrameOverlayService failSoftService = new PosterFrameOverlayService(missingLoader);
        PosterImage input = solidPoster(900, 1200, "image/png", FLAT_COLOUR_RGB);
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterImage output = failSoftService.apply(input);

        assertSame(input, output, "fail-soft must return the input PosterImage instance unchanged");
        assertWarnContains(appender,
                "event=frame.apply.skipped",
                "reason=asset_missing");
    }

    // ============================================================== T018
    @Test
    void letterboxesWrongRatioInputInsideThreeToFourFitRect() throws IOException {
        // Square 1:1 input — character aspect 1.0 vs target aspect 3:4 = 0.75.
        // The character is wider than 3:4, so it's width-limited inside the
        // 3:4 target rect (which extends slightly beyond the inner bbox into
        // the chrome region). Letterbox bands sit above and below the
        // centred 1:1 sub-rect.
        PosterImage input = solidPoster(768, 768, "image/png", FLAT_COLOUR_RGB);
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterImage output = service.apply(input);

        assertEquals("image/png", output.mediaType());
        assertEquals(768, output.widthPx());
        assertEquals(1152, output.heightPx());

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.bytes()));
        PosterFrameAsset asset = loader.get();

        // Replicate fitCharacterRect's geometry: smallest 3:4 rect that
        // CONTAINS the inner bbox, centred on the bbox.
        int targetH = asset.innerHeightPx();
        int targetW = (int) Math.round(targetH * (3.0 / 4.0));
        int targetX = asset.innerX() + (asset.innerWidthPx() - targetW) / 2;
        int targetY = asset.innerY();

        // 1:1 input width-limited inside the 3:4 target → square fit rect.
        int fitW = targetW;
        int fitH = fitW;  // 1:1
        int fitY = targetY + (targetH - fitH) / 2;
        int fitCentreX = targetX + targetW / 2;
        int fitCentreY = fitY + fitH / 2;

        // Centre of the fitted sub-rect MUST show the input's flat colour.
        assertEquals(FLAT_COLOUR_RGB, decoded.getRGB(fitCentreX, fitCentreY) & 0x00FFFFFF,
                "centre of fitted sub-rect must show the character's flat colour");

        // A point above the fitted sub-rect but inside the inner-bbox must
        // be transparent (alpha=0) — the letterbox band.
        int bandY = asset.innerY() + Math.max(2, (fitY - asset.innerY()) / 2);
        int bandPixel = decoded.getRGB(fitCentreX, bandY);
        int bandAlpha = (bandPixel >>> 24) & 0xFF;
        assertEquals(0, bandAlpha,
                "letterbox band above fitted sub-rect must be transparent, got alpha=" + bandAlpha);

        // No-stretch: a point near the top of the inner bbox MUST NOT carry
        // the input's flat colour. Either transparent (letterbox) or chrome.
        int aboveSubRect = decoded.getRGB(fitCentreX, asset.innerY() + 5) & 0x00FFFFFF;
        assertNotEquals(FLAT_COLOUR_RGB, aboveSubRect,
                "no-stretch invariant: top of inner-rect must not carry the character's flat colour");

        // WARN line for ratio mismatch.
        assertWarnContains(appender,
                "event=frame.apply.ratio_mismatch",
                "inputWidth=768",
                "inputHeight=768");
    }

    // ============================================================== additional coverage
    @Test
    void failSoftReturnsInputWhenInputBytesAreNotADecodableImage() throws IOException {
        // Truncated PNG header — ImageIO.read returns null (or throws IIOException).
        byte[] truncated = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D};
        PosterImage input = new PosterImage(truncated, "image/png", 100, 100);
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterImage output = service.apply(input);

        assertSame(input, output, "fail-soft on undecodable input must return input unchanged");
        assertWarnContains(appender, "event=frame.apply.failed");
    }

    @Test
    void threeToFourInputDoesNotEmitRatioMismatchWarn() throws IOException {
        // 3:4 input matches TARGET_ASPECT — must hit the "no warn" branch.
        PosterImage input = solidPoster(768, 1024, "image/png", FLAT_COLOUR_RGB);
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterImage output = service.apply(input);

        long mismatchWarns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("event=frame.apply.ratio_mismatch"))
                .count();
        assertEquals(0L, mismatchWarns,
                "3:4 input must NOT trigger ratio_mismatch WARN");
        assertEquals("image/png", output.mediaType());
    }

    @Test
    void widthLimitedFitForLandscapeInput() throws IOException {
        // 16:9-ish input is wider than the inner rectangle's 0.5813 aspect.
        // Exercises the charAspect > innerAspect branch in fitCharacterRect.
        PosterImage input = solidPoster(1600, 900, "image/png", FLAT_COLOUR_RGB);

        PosterImage output = service.apply(input);

        assertEquals(768, output.widthPx());
        assertEquals(1152, output.heightPx());
        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(output.bytes()));
        // Centre of fitted region must show the input's flat colour.
        PosterFrameAsset asset = loader.get();
        int cx = asset.innerX() + asset.innerWidthPx() / 2;
        int cy = asset.innerY() + asset.innerHeightPx() / 2;
        assertEquals(FLAT_COLOUR_RGB, decoded.getRGB(cx, cy) & 0x00FFFFFF);
    }

    // ============================================================== T019
    @Test
    void alwaysEncodesOutputAsPngEvenWhenInputIsJpeg() throws IOException {
        PosterImage jpegInput = solidPoster(768, 1152, "image/jpeg", FLAT_COLOUR_RGB);

        PosterImage output = service.apply(jpegInput);

        assertEquals("image/png", output.mediaType(),
                "FR-1509: composited output is always PNG, regardless of input MIME");
        assertArrayEquals(PNG_SIGNATURE, Arrays.copyOf(output.bytes(), 8),
                "FR-1509: output bytes must carry the PNG signature");
    }

    // ----------------------------------------------------------------
    private static PosterFrameAssetLoader bundledLoader() {
        PosterFrameAssetLoader l = new PosterFrameAssetLoader();
        l.init();
        return l;
    }

    private static PosterImage solidPoster(int w, int h, String mime, int rgb) throws IOException {
        BufferedImage img = new BufferedImage(w, h, "image/jpeg".equals(mime)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(rgb));
            g.fillRect(0, 0, w, h);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String fmt = "image/jpeg".equals(mime) ? "jpeg" : "png";
        if (!ImageIO.write(img, fmt, baos)) {
            throw new IOException("ImageIO.write returned false for format " + fmt);
        }
        return new PosterImage(baos.toByteArray(), mime, w, h);
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger root = (Logger) LoggerFactory.getLogger(PosterFrameOverlayService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        return appender;
    }

    private static void assertWarnContains(ListAppender<ILoggingEvent> appender,
                                           String... substrings) {
        List<ILoggingEvent> warns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .toList();
        assertTrue(!warns.isEmpty(),
                "expected at least one WARN line, got: " + appender.list);
        boolean matched = warns.stream().anyMatch(e -> {
            String msg = e.getFormattedMessage();
            for (String s : substrings) {
                if (!msg.contains(s)) return false;
            }
            return true;
        });
        assertTrue(matched,
                "no WARN line contained all of " + List.of(substrings)
                        + "; saw: " + warns.stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    /** Loader stub whose {@code get()} always returns the missing sentinel. */
    private static final class MissingLoader extends PosterFrameAssetLoader {
        @Override
        public PosterFrameAsset get() {
            return PosterFrameAsset.missing();
        }
    }
}

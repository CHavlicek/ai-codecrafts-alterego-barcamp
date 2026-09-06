package com.aiavatar.alterego.service.text;

import com.aiavatar.alterego.infrastructure.overlay.text.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAsset;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAssetLoader;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PosterTextOverlayService} (017 T009 + T023 + T034).
 */
class PosterTextOverlayServiceTest {

    private static PosterFrameAsset bundledLikeAsset() {
        BufferedImage image = new BufferedImage(1024, 1536, BufferedImage.TYPE_INT_ARGB);
        return PosterFrameAsset.loaded(image,
                116, 124, 783, 1057,
                157, 1273, 701, 171);
    }

    private static PosterFrameAssetLoader assetLoaderReturning(PosterFrameAsset asset) {
        return new PosterFrameAssetLoader() {
            @Override
            public PosterFrameAsset get() { return asset; }
        };
    }

    private static PosterTextFonts bundledFonts() {
        PosterTextFonts fonts = new PosterTextFonts();
        fonts.init();
        return fonts;
    }

    private static PosterImage darkFramedPoster(PosterFrameAsset asset) {
        BufferedImage canvas = new BufferedImage(asset.canvasWidthPx(), asset.canvasHeightPx(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setColor(new Color(8, 8, 12));
            g.fillRect(0, 0, asset.canvasWidthPx(), asset.canvasHeightPx());
        } finally {
            g.dispose();
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(canvas, "png", out);
            return new PosterImage(out.toByteArray(), "image/png",
                    asset.canvasWidthPx(), asset.canvasHeightPx());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void happyPathDrawsTextIntoBottomRegion() throws IOException {
        PosterFrameAsset asset = bundledLikeAsset();
        PosterTextOverlayService service = new PosterTextOverlayService(
                assetLoaderReturning(asset), bundledFonts());

        PosterImage framed = darkFramedPoster(asset);
        PosterImage out = service.apply(framed,
                new PosterTextLines("Ada", "Cloud Architect", "It is always DNS."));

        assertEquals(asset.canvasWidthPx(), out.widthPx());
        assertEquals(asset.canvasHeightPx(), out.heightPx());
        assertEquals("image/png", out.mediaType());

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(out.bytes()));
        assertNotNull(decoded);
        long lightPixels = countLightPixelsInRegion(decoded, asset);
        long regionPixels = (long) asset.bottomRegionWidthPx() * asset.bottomRegionHeightPx();
        assertTrue(lightPixels > regionPixels / 100,
                "bottom region must contain >1% light pixels (text drawn), got "
                        + lightPixels + " of " + regionPixels);
    }

    @Test
    void allEmptyLinesShortCircuitReturnsInputUnchanged() {
        PosterFrameAsset asset = bundledLikeAsset();
        PosterTextOverlayService service = new PosterTextOverlayService(
                assetLoaderReturning(asset), bundledFonts());

        PosterImage framed = darkFramedPoster(asset);
        PosterImage out = service.apply(framed, new PosterTextLines("", "", ""));

        assertSame(framed, out, "all-empty lines must return input unchanged (no encode)");
    }

    @Test
    void haloFillAndOutlineBothPresentInBottomRegion() throws IOException {
        PosterFrameAsset asset = bundledLikeAsset();
        BufferedImage canvas = new BufferedImage(asset.canvasWidthPx(), asset.canvasHeightPx(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            for (int y = 0; y < asset.canvasHeightPx(); y += 32) {
                for (int x = 0; x < asset.canvasWidthPx(); x += 32) {
                    g.setColor(((x / 32) + (y / 32)) % 2 == 0
                            ? new Color(8, 8, 12) : new Color(245, 245, 240));
                    g.fillRect(x, y, 32, 32);
                }
            }
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", baos);

        PosterTextOverlayService service = new PosterTextOverlayService(
                assetLoaderReturning(asset), bundledFonts());
        PosterImage out = service.apply(
                new PosterImage(baos.toByteArray(), "image/png",
                        asset.canvasWidthPx(), asset.canvasHeightPx()),
                new PosterTextLines("Ada", "Cloud Architect", "It is always DNS."));

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(out.bytes()));
        assertNotNull(decoded);

        long darkPixelsAfter = countDarkPixelsInRegion(decoded, asset);
        long lightPixelsAfter = countLightPixelsInRegion(decoded, asset);
        long regionPixels = (long) asset.bottomRegionWidthPx() * asset.bottomRegionHeightPx();
        assertTrue(darkPixelsAfter > regionPixels / 4,
                "halo (dark) pixels must occupy >25% of bottom region (got " + darkPixelsAfter
                        + " of " + regionPixels + ")");
        assertTrue(lightPixelsAfter > regionPixels / 100,
                "fill (light) pixels must remain >1% of bottom region (got " + lightPixelsAfter
                        + " of " + regionPixels + ")");
    }

    @Test
    void assetMissingShortCircuitsAndLogsWarn() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        PosterTextOverlayService service = new PosterTextOverlayService(
                assetLoaderReturning(PosterFrameAsset.missing()),
                bundledFonts());

        PosterImage input = new PosterImage(new byte[]{1, 2, 3}, "image/png", 10, 10);
        PosterImage out = service.apply(input,
                new PosterTextLines("Ada", "Cloud Architect", "It is always DNS."));

        assertSame(input, out);
        assertWarnContains(appender, "event=text.apply.skipped", "reason=asset_missing");
    }

    @Test
    void decodeReturnsNullShortCircuitsAndLogsWarn() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        PosterFrameAsset asset = bundledLikeAsset();
        PosterTextOverlayService service = new PosterTextOverlayService(
                assetLoaderReturning(asset), bundledFonts());

        PosterImage garbage = new PosterImage(new byte[]{0, 0, 0, 0, 0, 0, 0, 0},
                "image/png", asset.canvasWidthPx(), asset.canvasHeightPx());
        PosterImage out = service.apply(garbage,
                new PosterTextLines("Ada", "Cloud Architect", "It is always DNS."));

        assertSame(garbage, out);
        assertWarnContains(appender, "event=text.apply.failed", "reason=decode_returned_null");
    }

    @Test
    void anyMissingFontShortCircuitsAndLogsWarn() {
        PosterFrameAsset asset = bundledLikeAsset();
        PosterTextFonts incompleteFonts = new PosterTextFonts() {
            @Override public Font display() { return null; }
            @Override public Font body() { return new Font(Font.SANS_SERIF, Font.PLAIN, 12); }
            @Override public Font italic() { return new Font(Font.SANS_SERIF, Font.PLAIN, 12); }
        };
        ListAppender<ILoggingEvent> appender = attachAppender();
        PosterTextOverlayService service = new PosterTextOverlayService(
                assetLoaderReturning(asset), incompleteFonts);

        PosterImage framed = darkFramedPoster(asset);
        PosterImage out = service.apply(framed,
                new PosterTextLines("Ada", "Cloud Architect", "It is always DNS."));

        assertSame(framed, out);
        assertWarnContains(appender, "event=text.apply.skipped", "reason=font_missing");
    }

    @Test
    void inputBytesUnchangedOnPathologicalDecodePath() {
        byte[] truncated = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D
        };
        PosterImage input = new PosterImage(truncated, "image/png", 1024, 1536);
        PosterFrameAsset asset = bundledLikeAsset();
        PosterTextOverlayService service = new PosterTextOverlayService(
                assetLoaderReturning(asset), bundledFonts());

        PosterImage out = service.apply(input,
                new PosterTextLines("Ada", "Cloud Architect", "It is always DNS."));
        assertSame(input, out);
        assertArrayEquals(truncated, out.bytes());
    }

    private static long countLightPixelsInRegion(BufferedImage img, PosterFrameAsset asset) {
        long count = 0;
        int x0 = asset.bottomRegionX();
        int y0 = asset.bottomRegionY();
        int w = asset.bottomRegionWidthPx();
        int h = asset.bottomRegionHeightPx();
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

    private static long countDarkPixelsInRegion(BufferedImage img, PosterFrameAsset asset) {
        long count = 0;
        int x0 = asset.bottomRegionX();
        int y0 = asset.bottomRegionY();
        int w = asset.bottomRegionWidthPx();
        int h = asset.bottomRegionHeightPx();
        int[] row = new int[w];
        for (int y = y0; y < y0 + h; y++) {
            img.getRGB(x0, y, w, 1, row, 0, w);
            for (int rgb : row) {
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                if (r < 30 && g < 30 && b < 30) {
                    count++;
                }
            }
        }
        return count;
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger root = (Logger) LoggerFactory.getLogger(PosterTextOverlayService.class);
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
}

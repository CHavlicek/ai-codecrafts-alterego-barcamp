package com.aiavatar.alterego.service.frame;

import com.aiavatar.alterego.infrastructure.overlay.frame.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PosterFrameAssetLoader} (T005 / 015-frame-overlay).
 *
 * <p>Covers FR-1502 (bundled), FR-1503 (loaded once / cached),
 * FR-1504 (transparent inner area discovered via alpha=0 bounding box),
 * FR-1512 (fail-soft on missing/decode-failed asset),
 * SC-1507 (asset read exactly once across multiple {@code get()} calls —
 * verified via reference-identity assertion in case (a)).
 */
class PosterFrameAssetLoaderTest {

    private static final String BUNDLED = "branding/poster-frame.png";

    // ---------------------------------------------------------------- (a)
    // Loaded happy path: real classpath asset decodes RGBA, inner-rect
    // bbox matches the asset's transparent area, two get() calls return
    // the same instance (SC-1507), and one INFO log line is emitted.
    @Test
    void loadedHappyPathDecodesRgbaAndCachesAsset() {
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterFrameAssetLoader loader = new PosterFrameAssetLoader(BUNDLED);
        loader.init();

        PosterFrameAsset first = loader.get();
        PosterFrameAsset second = loader.get();

        assertTrue(first.loaded(), "asset must be loaded=true on happy path");
        assertNotNull(first.image());
        assertEquals(BufferedImage.TYPE_INT_ARGB, first.image().getType(),
                "decoded image must be RGBA so the alpha channel survives");
        assertEquals(768, first.canvasWidthPx());
        assertEquals(1152, first.canvasHeightPx());
        // The bundled asset's transparent inner rectangle was prepared by
        // /speckit.specify (flood-fill from centre). It sits inside the
        // gradient border, so the bounding box is well within the canvas
        // and substantially smaller than full-canvas.
        assertTrue(first.innerX() >= 30 && first.innerX() <= 160,
                "inner-x within plausible band, got " + first.innerX());
        assertTrue(first.innerY() >= 30 && first.innerY() <= 160,
                "inner-y within plausible band, got " + first.innerY());
        assertTrue(first.innerWidthPx() >= 440 && first.innerWidthPx() <= 720,
                "inner-w within plausible band, got " + first.innerWidthPx());
        assertTrue(first.innerHeightPx() >= 740 && first.innerHeightPx() <= 1100,
                "inner-h within plausible band, got " + first.innerHeightPx());

        // SC-1507: cached singleton — identical instance AND identical image
        // reference across consecutive calls.
        assertSame(first, second, "two get() calls must return the same instance");
        assertSame(first.image(), second.image(),
                "two get() calls must return the same BufferedImage reference");

        // Exactly one INFO line on success.
        long infoCount = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .filter(e -> e.getFormattedMessage().contains("event=frame.asset.loaded"))
                .count();
        assertEquals(1L, infoCount,
                "expected one INFO event=frame.asset.loaded line, got: "
                        + appender.list);
    }

    // ---------------------------------------------------------------- (b)
    @Test
    void missingResourceReturnsMissingAndLogsWarn() {
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterFrameAssetLoader loader =
                new PosterFrameAssetLoader("branding/does-not-exist.png");
        loader.init();

        assertFalse(loader.get().loaded(), "missing resource → loaded=false");
        assertWarnContains(appender,
                "event=frame.asset.missing",
                "reason=not_found");
    }

    // ---------------------------------------------------------------- (c)
    @Test
    void decodeReturnsNullForNonImageFileAndLogsWarn(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException {
        // Non-image resource on the classpath. Use a custom loader subclass
        // that bypasses ClassPathResource for this synthetic case.
        Path notAnImage = tmp.resolve("not-an-image.txt");
        Files.writeString(notAnImage, "hello world");
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterFrameAssetLoader loader =
                new PosterFrameAssetLoader.FileSystemTestLoader(notAnImage);
        loader.init();

        assertFalse(loader.get().loaded(), "non-image file → loaded=false");
        assertWarnContains(appender,
                "event=frame.asset.missing",
                "reason=decode_returned_null");
    }

    // ---------------------------------------------------------------- (d)
    @Test
    void decodeFailureReturnsMissingAndLogsWarn(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException {
        // Pretend-PNG (PNG magic header but truncated body). ImageIO will
        // throw an IIOException reading it, which the loader maps to
        // reason=decode_failed.
        byte[] truncatedPng = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, // truncated IHDR chunk
        };
        Path corrupt = tmp.resolve("corrupt.png");
        Files.write(corrupt, truncatedPng);
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterFrameAssetLoader loader =
                new PosterFrameAssetLoader.FileSystemTestLoader(corrupt);
        loader.init();

        assertFalse(loader.get().loaded(), "corrupt PNG → loaded=false");
        assertWarnContains(appender,
                "event=frame.asset.missing",
                "reason=decode_");  // either decode_failed or decode_returned_null
    }

    // ---------------------------------------------------------------- (e)
    @Test
    void opaqueEverywhereAssetReturnsMissingAndLogsWarn(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException {
        // Synthetic PNG with no transparent pixels at all → no inner
        // rectangle to discover → fail-soft.
        BufferedImage opaque = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = opaque.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 64, 64);
        } finally {
            g.dispose();
        }
        Path opaquePath = tmp.resolve("opaque.png");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(opaque, "png", baos);
            Files.write(opaquePath, baos.toByteArray());
        }
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterFrameAssetLoader loader =
                new PosterFrameAssetLoader.FileSystemTestLoader(opaquePath);
        loader.init();

        assertFalse(loader.get().loaded(), "opaque-everywhere asset → loaded=false");
        assertWarnContains(appender,
                "event=frame.asset.missing",
                "reason=no_inner_rectangle");
    }

    // ---------------------------------------------------------------- (f)
    // RGB-only (non-RGBA) inputs must be promoted to RGBA before alpha-zero
    // bbox discovery — exercises the ensureRgba branch.
    @Test
    void rgbOnlyAssetIsPromotedAndStillFails(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException {
        // RGB-only PNG with no alpha channel at all → ensureRgba converts it
        // to ARGB, but every pixel ends up alpha=255 → no_inner_rectangle.
        BufferedImage rgb = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 64, 64);
        } finally {
            g.dispose();
        }
        Path rgbPath = tmp.resolve("rgb-only.png");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(rgb, "png", baos);
            Files.write(rgbPath, baos.toByteArray());
        }
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterFrameAssetLoader loader =
                new PosterFrameAssetLoader.FileSystemTestLoader(rgbPath);
        loader.init();

        assertFalse(loader.get().loaded(), "RGB-only with no transparency → loaded=false");
        assertWarnContains(appender, "event=frame.asset.missing", "reason=no_inner_rectangle");
    }

    // ---------------------------------------------------------------- (g)
    // PosterFrameAsset record-constructor validation branches: invalid
    // configurations must throw.
    @Test
    void posterFrameAssetRejectsInvalidLoadedConfigurations() {
        BufferedImage real = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
        // image=null with loaded=true → IllegalArgumentException via Objects.requireNonNull
        org.junit.jupiter.api.Assertions.assertThrows(NullPointerException.class,
                () -> new PosterFrameAsset(null, 10, 10, 0, 0, 5, 5, 0, 6, 5, 4, true));
        // canvas dims must be positive
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PosterFrameAsset(real, 0, 10, 0, 0, 5, 5, 0, 6, 5, 4, true));
        // inner rectangle must be non-empty
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PosterFrameAsset(real, 10, 10, 0, 0, 0, 5, 0, 6, 5, 4, true));
        // inner rectangle must lie inside canvas
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PosterFrameAsset(real, 10, 10, 9, 0, 5, 5, 0, 6, 5, 4, true));
        // bottom region must be non-empty (017)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PosterFrameAsset(real, 10, 10, 0, 0, 5, 5, 0, 6, 0, 4, true));
        // bottom region must sit below the inner cutout (017)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PosterFrameAsset(real, 10, 10, 0, 0, 5, 5, 0, 4, 5, 4, true));
        // bottom region must lie inside canvas (017)
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new PosterFrameAsset(real, 10, 10, 0, 0, 5, 5, 0, 6, 5, 5, true));
    }

    // ---------------------------------------------------------------- (h)
    // 017 T003: happy path also derives the bottom-region safe area for
    // text overlay. Bands check: x inset by ~4% of canvas-width on each
    // side of the inner cutout, y starts ~6% of canvas-height below the
    // cutout, the rect lives entirely inside the canvas.
    @Test
    void loadedHappyPathAlsoComputesBottomRegion() {
        PosterFrameAssetLoader loader = new PosterFrameAssetLoader(BUNDLED);
        loader.init();
        PosterFrameAsset asset = loader.get();
        assertTrue(asset.loaded(), "precondition: asset loaded");

        // Bottom-region X starts ~4% of canvas-width to the right of innerX.
        int expectedHorizontalInsetPx = (int) Math.round(asset.canvasWidthPx() * 0.04);
        assertEquals(asset.innerX() + expectedHorizontalInsetPx,
                asset.bottomRegionX(),
                "bottomRegionX must be innerX + 4% of canvas width");
        // Width is innerWidthPx minus the inset on each side.
        assertEquals(asset.innerWidthPx() - 2 * expectedHorizontalInsetPx,
                asset.bottomRegionWidthPx(),
                "bottomRegionWidthPx must be innerWidthPx − 2×4% inset");

        // Y starts ~6% of canvas-height below the cutout's bottom edge.
        int expectedVerticalGapPx = (int) Math.round(asset.canvasHeightPx() * 0.06);
        assertEquals(asset.innerY() + asset.innerHeightPx() + expectedVerticalGapPx,
                asset.bottomRegionY(),
                "bottomRegionY must sit 6% of canvas height below the inner cutout");
        // Height fills the rest of the canvas with another 6% bottom margin.
        assertEquals(asset.canvasHeightPx() - asset.bottomRegionY() - expectedVerticalGapPx,
                asset.bottomRegionHeightPx(),
                "bottomRegionHeightPx must fill the rest of canvas minus a 6% bottom margin");

        // Sanity: must lie strictly inside the canvas and below the cutout.
        assertTrue(asset.bottomRegionX() >= 0
                        && asset.bottomRegionY() >= asset.innerY() + asset.innerHeightPx()
                        && asset.bottomRegionX() + asset.bottomRegionWidthPx() <= asset.canvasWidthPx()
                        && asset.bottomRegionY() + asset.bottomRegionHeightPx() <= asset.canvasHeightPx(),
                "bottom region must lie inside canvas, below the inner cutout");

        // Plausible-band sanity for the bundled asset (768×1152 → ~525 wide, ~130 tall).
        assertTrue(asset.bottomRegionWidthPx() >= 370 && asset.bottomRegionWidthPx() <= 720,
                "bottomRegionWidthPx within plausible band, got " + asset.bottomRegionWidthPx());
        assertTrue(asset.bottomRegionHeightPx() >= 50 && asset.bottomRegionHeightPx() <= 275,
                "bottomRegionHeightPx within plausible band, got " + asset.bottomRegionHeightPx());
    }

    // ---------------------------------------------------------------- (i)
    // 017 T003: synthetic asset whose canvas leaves no usable space below
    // the inner cutout downgrades to PosterFrameAsset.missing() so the
    // overlay services no-op gracefully (FR-1716 + 015 carryover).
    @Test
    void bottomRegionTooSmallDowngradesToMissing(@org.junit.jupiter.api.io.TempDir Path tmp)
            throws IOException {
        // 64×64 canvas with a transparent inner rectangle that extends
        // almost to the bottom edge. After the 6% (≈4px) bottom margin
        // and 6% gap the remaining height is below PosterTextStyle's
        // minimum readable size (16 px) → the loader downgrades.
        BufferedImage shallow = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = shallow.createGraphics();
        try {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, 64, 64);
            // Erase a transparent inner rectangle extending to row 60,
            // leaving only 4 px below the cutout.
            g.setComposite(java.awt.AlphaComposite.Clear);
            g.fillRect(4, 4, 56, 56); // y from 4 to 60
        } finally {
            g.dispose();
        }
        Path shallowPath = tmp.resolve("shallow-bottom.png");
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(shallow, "png", baos);
            Files.write(shallowPath, baos.toByteArray());
        }
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterFrameAssetLoader loader =
                new PosterFrameAssetLoader.FileSystemTestLoader(shallowPath);
        loader.init();

        assertFalse(loader.get().loaded(),
                "asset with no usable bottom region → loaded=false");
        assertWarnContains(appender,
                "event=frame.asset.bottom_region_too_small");
    }

    // ----------------------------------------------------------------
    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger root = (Logger) LoggerFactory.getLogger(PosterFrameAssetLoader.class);
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

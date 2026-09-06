package com.aiavatar.alterego.infrastructure.overlay.frame;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Decodes the bundled poster frame PNG from the classpath exactly once
 * per JVM (015 FR-1503 / R-1503 / SC-1507) and caches it as an immutable
 * {@link PosterFrameAsset}. Computes both asset-derived geometric regions
 * at load time so neither overlay service has to scan the asset on every
 * Generate:
 *
 * <ol>
 *   <li>The transparent inner cutout bounding box (alpha=0 region),
 *       consumed by {@code PosterFrameOverlayService} (015).</li>
 *   <li>The bottom-region text safe area derived from the cutout +
 *       canvas, consumed by {@code PosterTextOverlayService} (017):
 *       a 4% horizontal inset on each side of the inner cutout, a 6%
 *       vertical gap below the cutout, and another 6% bottom margin
 *       above the canvas edge.</li>
 * </ol>
 *
 * <p>Fail-soft (FR-1512 / 017 FR-1716): missing resource, decode
 * failure, an "opaque-everywhere" asset, or a derived bottom region
 * shallower than {@link #BOTTOM_REGION_MIN_HEIGHT_PX} all collapse to
 * {@link PosterFrameAsset#missing()} plus a {@code WARN} log line. The
 * backend boots normally; both overlay services short-circuit to "return
 * input unchanged" when the loader reports {@code !loaded()}.
 */
@Component
public class PosterFrameAssetLoader {

    private static final Logger log = LoggerFactory.getLogger(PosterFrameAssetLoader.class);

    static final String DEFAULT_RESOURCE = "branding/poster-frame.png";

    /** 4% of canvas width as a horizontal inset on each side of the inner cutout. */
    private static final double BOTTOM_REGION_HORIZONTAL_INSET_RATIO = 0.04;
    /** 6% of canvas height as a vertical gap below the cutout AND as a bottom margin. */
    private static final double BOTTOM_REGION_VERTICAL_INSET_RATIO = 0.06;
    /**
     * Minimum usable bottom-region height (px). Below this the loader
     * downgrades to {@link PosterFrameAsset#missing()} so the text
     * overlay step skips silently and the frame still renders.
     * Matches the {@code PosterTextStyle.minSizePx} hard floor (017).
     */
    static final int BOTTOM_REGION_MIN_HEIGHT_PX = 16;

    private final String classpathResource;
    private PosterFrameAsset asset = PosterFrameAsset.missing();

    public PosterFrameAssetLoader() {
        this(DEFAULT_RESOURCE);
    }

    public PosterFrameAssetLoader(String classpathResource) {
        this.classpathResource = classpathResource;
    }

    @PostConstruct
    public void init() {
        this.asset = load();
    }

    public PosterFrameAsset get() {
        return asset;
    }

    PosterFrameAsset load() {
        try (InputStream in = openStream()) {
            if (in == null) {
                log.warn("event=frame.asset.missing resource={} reason=not_found",
                        classpathResource);
                return PosterFrameAsset.missing();
            }
            BufferedImage decoded;
            try {
                decoded = ImageIO.read(in);
            } catch (IOException e) {
                log.warn("event=frame.asset.missing resource={} reason=decode_failed",
                        classpathResource, e);
                return PosterFrameAsset.missing();
            }
            if (decoded == null) {
                log.warn("event=frame.asset.missing resource={} reason=decode_returned_null",
                        classpathResource);
                return PosterFrameAsset.missing();
            }
            BufferedImage rgba = ensureRgba(decoded);
            int[] bounds = computeAlphaZeroBoundingBox(rgba);
            if (bounds == null) {
                log.warn("event=frame.asset.missing resource={} reason=no_inner_rectangle",
                        classpathResource);
                return PosterFrameAsset.missing();
            }
            int innerX = bounds[0];
            int innerY = bounds[1];
            int innerW = bounds[2];
            int innerH = bounds[3];

            int canvasW = rgba.getWidth();
            int canvasH = rgba.getHeight();
            int horizontalInsetPx = (int) Math.round(canvasW * BOTTOM_REGION_HORIZONTAL_INSET_RATIO);
            int verticalInsetPx = (int) Math.round(canvasH * BOTTOM_REGION_VERTICAL_INSET_RATIO);
            int bottomRegionX = innerX + horizontalInsetPx;
            int bottomRegionWidthPx = innerW - 2 * horizontalInsetPx;
            int bottomRegionY = innerY + innerH + verticalInsetPx;
            int bottomRegionHeightPx = canvasH - bottomRegionY - verticalInsetPx;

            if (bottomRegionWidthPx <= 0 || bottomRegionHeightPx < BOTTOM_REGION_MIN_HEIGHT_PX) {
                log.warn("event=frame.asset.bottom_region_too_small resource={} "
                                + "canvasWidthPx={} canvasHeightPx={} "
                                + "bottomRegionWidthPx={} bottomRegionHeightPx={} minHeightPx={}",
                        classpathResource, canvasW, canvasH,
                        bottomRegionWidthPx, bottomRegionHeightPx, BOTTOM_REGION_MIN_HEIGHT_PX);
                return PosterFrameAsset.missing();
            }

            log.info("event=frame.asset.loaded resource={} canvasWidthPx={} canvasHeightPx={} "
                            + "innerX={} innerY={} innerWidthPx={} innerHeightPx={} "
                            + "bottomRegionX={} bottomRegionY={} bottomRegionWidthPx={} bottomRegionHeightPx={}",
                    classpathResource, canvasW, canvasH,
                    innerX, innerY, innerW, innerH,
                    bottomRegionX, bottomRegionY, bottomRegionWidthPx, bottomRegionHeightPx);
            return PosterFrameAsset.loaded(rgba, innerX, innerY, innerW, innerH,
                    bottomRegionX, bottomRegionY, bottomRegionWidthPx, bottomRegionHeightPx);
        } catch (IOException e) {
            log.warn("event=frame.asset.missing resource={} reason=decode_failed",
                    classpathResource, e);
            return PosterFrameAsset.missing();
        }
    }

    InputStream openStream() throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        if (!resource.exists()) {
            return null;
        }
        return resource.getInputStream();
    }

    private static BufferedImage ensureRgba(BufferedImage src) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB) {
            return src;
        }
        BufferedImage out = new BufferedImage(
                src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Compute the axis-aligned bounding box of fully-transparent pixels
     * (alpha == 0) in the given RGBA image.
     *
     * @return [x, y, width, height] or {@code null} if no transparent pixel exists.
     */
    static int[] computeAlphaZeroBoundingBox(BufferedImage rgba) {
        int w = rgba.getWidth();
        int h = rgba.getHeight();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            rgba.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int alpha = (row[x] >>> 24) & 0xFF;
                if (alpha == 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    /**
     * Test-only loader subclass that reads the asset from an absolute
     * file-system {@link Path} instead of the classpath. Used by the unit
     * tests for the missing/decode-failure/opaque-everywhere fault cases
     * where the classpath cannot host the synthetic fixture.
     */
    public static final class FileSystemTestLoader extends PosterFrameAssetLoader {
        private final Path path;

        public FileSystemTestLoader(Path path) {
            super(path.toString());
            this.path = path;
        }

        @Override
        InputStream openStream() throws IOException {
            if (!Files.exists(path)) {
                return null;
            }
            return Files.newInputStream(path);
        }
    }
}

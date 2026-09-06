package com.aiavatar.alterego.infrastructure.overlay.frame;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * The single bundled poster frame PNG decoded once at backend startup
 * (015 FR-1503 / R-1503) and reused by every Generate request. Carries
 * two asset-derived geometric regions so the overlay services do not have
 * to re-discover them per request:
 *
 * <ol>
 *   <li>The frame's <strong>transparent inner cutout</strong> bounding box
 *       (alpha=0 region) where the character image is composited
 *       (015).</li>
 *   <li>The <strong>bottom-region text safe area</strong> below the
 *       cutout where {@code PosterTextOverlayService} composites the
 *       three text lines (017). Its bounds are derived from the cutout
 *       and the canvas: a 4% horizontal inset on each side of the inner
 *       cutout, a 6% vertical gap below the cutout, and another 6%
 *       bottom margin above the canvas edge.</li>
 * </ol>
 *
 * <p>Mirrors 008's {@code LogoAsset} pattern: a {@link #missing()}
 * sentinel marks fail-soft state when the asset cannot be loaded
 * (FR-1512 / FR-1716). The overlay services check {@link #loaded()}
 * before compositing.
 */
public record PosterFrameAsset(
        BufferedImage image,
        int canvasWidthPx,
        int canvasHeightPx,
        int innerX,
        int innerY,
        int innerWidthPx,
        int innerHeightPx,
        int bottomRegionX,
        int bottomRegionY,
        int bottomRegionWidthPx,
        int bottomRegionHeightPx,
        boolean loaded
) {

    public PosterFrameAsset {
        if (loaded) {
            Objects.requireNonNull(image, "image must be non-null when loaded=true");
            if (canvasWidthPx <= 0 || canvasHeightPx <= 0) {
                throw new IllegalArgumentException(
                        "canvas dimensions must be positive when loaded=true");
            }
            if (innerWidthPx <= 0 || innerHeightPx <= 0) {
                throw new IllegalArgumentException(
                        "inner rectangle must be non-empty when loaded=true");
            }
            if (innerX < 0 || innerY < 0
                    || innerX + innerWidthPx > canvasWidthPx
                    || innerY + innerHeightPx > canvasHeightPx) {
                throw new IllegalArgumentException(
                        "inner rectangle must lie inside the canvas");
            }
            if (bottomRegionWidthPx <= 0 || bottomRegionHeightPx <= 0) {
                throw new IllegalArgumentException(
                        "bottom region must be non-empty when loaded=true");
            }
            if (bottomRegionY < innerY + innerHeightPx) {
                throw new IllegalArgumentException(
                        "bottom region must sit below the inner cutout");
            }
            if (bottomRegionX < 0
                    || bottomRegionX + bottomRegionWidthPx > canvasWidthPx
                    || bottomRegionY + bottomRegionHeightPx > canvasHeightPx) {
                throw new IllegalArgumentException(
                        "bottom region must lie inside the canvas");
            }
        }
    }

    public static PosterFrameAsset loaded(BufferedImage image,
                                          int innerX, int innerY,
                                          int innerWidthPx, int innerHeightPx,
                                          int bottomRegionX, int bottomRegionY,
                                          int bottomRegionWidthPx, int bottomRegionHeightPx) {
        return new PosterFrameAsset(image,
                image.getWidth(), image.getHeight(),
                innerX, innerY, innerWidthPx, innerHeightPx,
                bottomRegionX, bottomRegionY, bottomRegionWidthPx, bottomRegionHeightPx,
                true);
    }

    public static PosterFrameAsset missing() {
        return new PosterFrameAsset(null, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
}

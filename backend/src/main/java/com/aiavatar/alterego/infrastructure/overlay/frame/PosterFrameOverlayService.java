package com.aiavatar.alterego.infrastructure.overlay.frame;

import com.aiavatar.alterego.domain.model.PosterImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Composites every generated poster (real-provider OR fallback) inside the
 * bundled conference frame asset (015 FR-1501..FR-1513).
 *
 * <p>Pipeline (data-model.md §"Composition pipeline"):
 * <ol>
 *   <li>If the {@link PosterFrameAssetLoader} reports {@code !loaded()},
 *       short-circuit to fail-soft: return the input unchanged + WARN
 *       (FR-1512).</li>
 *   <li>Decode the input bytes; allocate an ARGB canvas at the frame's
 *       full dimensions (FR-1510 alpha preservation).</li>
 *   <li>Fit the character image into the frame's transparent inner
 *       rectangle preserving the character's own aspect — letterboxing
 *       inside the inner rectangle if the input is not 3:4 (FR-1511 with
 *       transparent bands per R-1504); a {@code WARN
 *       event=frame.apply.ratio_mismatch} line is emitted on mismatch.</li>
 *   <li>Draw the frame asset on top so its alpha-anti-aliased chrome
 *       composites cleanly over the character image (FR-1501).</li>
 *   <li>Encode the canvas as PNG (FR-1509: always PNG, regardless of input
 *       MIME) and return a new {@link PosterImage}.</li>
 * </ol>
 *
 * <p>Any {@link RuntimeException} or {@link IOException} during decode /
 * composite / encode is caught, logged at {@code WARN}, and the input is
 * returned unchanged (FR-1512 — never break the user-facing artefact for
 * a framing bug).
 */
@Component
public class PosterFrameOverlayService {

    private static final Logger log = LoggerFactory.getLogger(PosterFrameOverlayService.class);

    /** Tolerance for "is this aspect ratio a match?" — ±0.5% of the target. */
    private static final double ASPECT_TOLERANCE = 0.005;

    private final PosterFrameAssetLoader loader;

    public PosterFrameOverlayService(PosterFrameAssetLoader loader) {
        this.loader = loader;
    }

    public PosterImage apply(PosterImage input) {
        PosterFrameAsset asset = loader.get();
        if (!asset.loaded()) {
            log.warn("event=frame.apply.skipped reason=asset_missing widthPx={} heightPx={}",
                    input.widthPx(), input.heightPx());
            return input;
        }

        try {
            BufferedImage character = ImageIO.read(new ByteArrayInputStream(input.bytes()));
            if (character == null) {
                log.warn("event=frame.apply.failed reason=decode_returned_null widthPx={} heightPx={}",
                        input.widthPx(), input.heightPx());
                return input;
            }

            BufferedImage canvas = new BufferedImage(
                    asset.canvasWidthPx(), asset.canvasHeightPx(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = canvas.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);

                int[] fit = fitCharacterRect(character.getWidth(), character.getHeight(), asset);
                int fitX = fit[0];
                int fitY = fit[1];
                int fitW = fit[2];
                int fitH = fit[3];
                g.drawImage(character, fitX, fitY, fitW, fitH, null);

                // Frame chrome on top — its own alpha channel acts as the mask.
                g.drawImage(asset.image(), 0, 0, null);
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(canvas, "png", out)) {
                log.warn("event=frame.apply.failed reason=png_encoder_missing");
                return input;
            }
            return new PosterImage(out.toByteArray(), "image/png",
                    asset.canvasWidthPx(), asset.canvasHeightPx());
        } catch (RuntimeException | IOException e) {
            log.warn("event=frame.apply.failed reason=exception widthPx={} heightPx={}",
                    input.widthPx(), input.heightPx(), e);
            return input;
        }
    }

    /** Target aspect ratio: portrait 3:4, matching the frame asset's transparent inner cutout. */
    private static final double TARGET_ASPECT = 3.0 / 4.0;

    /**
     * Compute the rectangle the character image is drawn into.
     *
     * <p>The frame asset's transparent inner rectangle ("inner bbox")
     * may not be exactly 3:4 — its rounded corners and decorative
     * chrome shape it to a slightly different aspect. To keep the
     * character image edge-to-edge inside the visible inner area
     * (no top/bottom or left/right gaps), we compute the smallest
     * <em>3:4-shaped</em> rectangle that <strong>contains</strong>
     * the inner bbox and centre it on the bbox. The character image
     * fills that rectangle.
     *
     * <p>The character pixels in the slim band that extends past the
     * inner bbox into the chrome region are obscured when the asset is
     * drawn on top — the chrome's gradient border and decorative
     * patterns cover them. Net visible result: the character image
     * extends right up to the visible edge of the rounded inner area.
     *
     * <p>If the input aspect deviates from 3:4, the character is
     * letterboxed inside the 3:4 target rect, preserving its own
     * aspect (FR-1511 — never crop the subject) and a {@code WARN
     * event=frame.apply.ratio_mismatch} line is emitted.
     *
     * @return [x, y, width, height] in canvas coordinates.
     */
    private static int[] fitCharacterRect(int charW, int charH, PosterFrameAsset asset) {
        double charAspect = charW / (double) charH;

        // Step 1 — smallest 3:4 rect that CONTAINS the inner bbox, centred.
        int targetX;
        int targetY;
        int targetW;
        int targetH;
        double bboxAspect = asset.innerWidthPx() / (double) asset.innerHeightPx();
        if (bboxAspect < TARGET_ASPECT) {
            // bbox is taller-than-3:4 → extend horizontally past the bbox.
            targetH = asset.innerHeightPx();
            targetW = (int) Math.round(targetH * TARGET_ASPECT);
            targetY = asset.innerY();
            targetX = asset.innerX() + (asset.innerWidthPx() - targetW) / 2;
        } else if (bboxAspect > TARGET_ASPECT) {
            // bbox is wider-than-3:4 → extend vertically past the bbox.
            targetW = asset.innerWidthPx();
            targetH = (int) Math.round(targetW / TARGET_ASPECT);
            targetX = asset.innerX();
            targetY = asset.innerY() + (asset.innerHeightPx() - targetH) / 2;
        } else {
            targetX = asset.innerX();
            targetY = asset.innerY();
            targetW = asset.innerWidthPx();
            targetH = asset.innerHeightPx();
        }

        // Step 2 — fit the character into the 3:4 target rect.
        double deviation = Math.abs(charAspect - TARGET_ASPECT) / TARGET_ASPECT;
        if (deviation <= ASPECT_TOLERANCE) {
            // Character is 3:4 — fills the target rect exactly. The slim
            // chrome-overflow band on either side of the inner bbox is
            // covered when the frame asset is composited on top.
            return new int[]{targetX, targetY, targetW, targetH};
        }

        log.warn("event=frame.apply.ratio_mismatch inputWidth={} inputHeight={} inputAspect={} "
                        + "targetAspect={} deviation={}",
                charW, charH,
                String.format("%.4f", charAspect),
                String.format("%.4f", TARGET_ASPECT),
                String.format("%.4f", deviation));

        int fitW;
        int fitH;
        if (charAspect > TARGET_ASPECT) {
            fitW = targetW;
            fitH = (int) Math.round(fitW / charAspect);
        } else {
            fitH = targetH;
            fitW = (int) Math.round(fitH * charAspect);
        }
        int fitX = targetX + (targetW - fitW) / 2;
        int fitY = targetY + (targetH - fitH) / 2;
        return new int[]{fitX, fitY, fitW, fitH};
    }
}

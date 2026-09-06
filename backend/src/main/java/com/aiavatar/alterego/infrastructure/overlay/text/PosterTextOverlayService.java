package com.aiavatar.alterego.infrastructure.overlay.text;

import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAsset;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAssetLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Composites the user's first name + role + quote into the dark
 * long-bottom region of every framed poster (017 FR-1701..FR-1716,
 * refined 2026-05-08 for the three-slot brand-typographic layout).
 *
 * <p>Pipeline:
 * <ol>
 *   <li>If the {@link PosterFrameAssetLoader} reports {@code !loaded()}
 *       (frame asset missing or its bottom region too small), return the
 *       input unchanged + a {@code WARN event=text.apply.skipped reason=asset_missing}
 *       line — the framing step has already failed-soft, this layer
 *       follows suit.</li>
 *   <li>If the lines are all empty (defensive coercion in
 *       {@link PosterTextLines}), return the input unchanged silently.</li>
 *   <li>Decode the input bytes; on null/error return input unchanged + WARN.</li>
 *   <li>Run {@link PosterTextFitter#fit} to compute per-line font +
 *       baseline positions.</li>
 *   <li>For each fitted line, render the glyph outline with a stroke-then-fill
 *       halo (research.md §R1, FR-1716): {@link TextLayout#getOutline} →
 *       {@code g.draw(outline)} (dark halo) → {@code g.fill(outline)}
 *       (light glyph fill). The halo width scales with the line's size
 *       per {@code style.outlineRatio()}, clamped to ≥ 2 px.</li>
 *   <li>Encode the canvas as PNG and return a new {@link PosterImage} at
 *       the same canvas dimensions.</li>
 * </ol>
 *
 * <p>Any {@link RuntimeException} or {@link IOException} during decode /
 * render / encode is caught, logged at {@code WARN}, and the input is
 * returned unchanged (FR-1716 — never break the user-facing artefact for
 * a text-overlay bug).
 */
@Component
public class PosterTextOverlayService {

    private static final Logger log = LoggerFactory.getLogger(PosterTextOverlayService.class);

    private final PosterFrameAssetLoader assetLoader;
    private final PosterTextFonts fonts;
    private final PosterTextStyle style;

    @Autowired
    public PosterTextOverlayService(PosterFrameAssetLoader assetLoader,
                                    PosterTextFonts fonts) {
        this(assetLoader, fonts, PosterTextStyle.defaults());
    }

    PosterTextOverlayService(PosterFrameAssetLoader assetLoader,
                             PosterTextFonts fonts,
                             PosterTextStyle style) {
        this.assetLoader = assetLoader;
        this.fonts = fonts;
        this.style = style;
    }

    public PosterImage apply(PosterImage framed, PosterTextLines lines) {
        PosterFrameAsset asset = assetLoader.get();
        if (!asset.loaded()) {
            log.warn("event=text.apply.skipped reason=asset_missing widthPx={} heightPx={}",
                    framed.widthPx(), framed.heightPx());
            return framed;
        }
        if (lines.isEmpty()) {
            return framed;
        }
        if (fonts == null
                || fonts.display() == null
                || fonts.body() == null
                || fonts.italic() == null) {
            log.warn("event=text.apply.skipped reason=font_missing widthPx={} heightPx={}",
                    framed.widthPx(), framed.heightPx());
            return framed;
        }

        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(framed.bytes()));
            if (decoded == null) {
                log.warn("event=text.apply.failed reason=decode_returned_null widthPx={} heightPx={}",
                        framed.widthPx(), framed.heightPx());
                return framed;
            }
            BufferedImage canvas = ensureRgba(decoded, asset.canvasWidthPx(), asset.canvasHeightPx());

            Graphics2D g = canvas.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                        RenderingHints.VALUE_FRACTIONALMETRICS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,
                        RenderingHints.VALUE_STROKE_PURE);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);

                FontRenderContext frc = g.getFontRenderContext();
                List<PosterTextFitter.Fitted> fitted =
                        PosterTextFitter.fit(lines, style, asset, fonts, frc);
                for (PosterTextFitter.Fitted f : fitted) {
                    TextLayout layout = new TextLayout(f.text(), f.font(), frc);
                    Shape outline = layout.getOutline(
                            AffineTransform.getTranslateInstance(f.baselineX(), f.baselineY()));

                    float strokeWidth = Math.max(2f, f.sizePx() * style.outlineRatio());
                    g.setStroke(new BasicStroke(strokeWidth,
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.setColor(style.outlineColor());
                    g.draw(outline);

                    g.setColor(style.fillColor());
                    g.fill(outline);
                }
            } finally {
                g.dispose();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(canvas, "png", out)) {
                log.warn("event=text.apply.failed reason=png_encoder_missing");
                return framed;
            }
            return new PosterImage(out.toByteArray(), "image/png",
                    asset.canvasWidthPx(), asset.canvasHeightPx());
        } catch (RuntimeException | IOException e) {
            log.warn("event=text.apply.failed reason=exception widthPx={} heightPx={}",
                    framed.widthPx(), framed.heightPx(), e);
            return framed;
        }
    }

    private static BufferedImage ensureRgba(BufferedImage src, int canvasW, int canvasH) {
        if (src.getType() == BufferedImage.TYPE_INT_ARGB
                && src.getWidth() == canvasW
                && src.getHeight() == canvasH) {
            return src;
        }
        BufferedImage out = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(src, 0, 0, canvasW, canvasH, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}

package com.aiavatar.alterego.infrastructure.provider.fallback;

import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.GeneratedCharacter;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.policy.AccentResolver;
import com.aiavatar.alterego.domain.policy.AccentTone;
import com.aiavatar.alterego.infrastructure.provider.stub.StubGenerationException;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Canned content for the FR-018 "always return a poster" guarantee.
 * Invoked by {@link com.aiavatar.alterego.service.AlterEgoService} after
 * the retry loop exhausts attempts on either generator.
 *
 * <p>002 delta: the fallback poster's accent colour is derived from
 * {@code (archetype, universe)} via {@link AccentResolver} so that identical
 * inputs produce a visually consistent poster whether the primary stub
 * succeeded or the fallback fired (FR-132).
 *
 * <p>Profile-neutral — present in every profile so SC-004 holds even when
 * the primary stubs are forced to fail.
 */
@Component
public class FallbackPosterProvider {

    // Fallback emits at 3:4 portrait — matching PosterFrameOverlayService's
    // TARGET_ASPECT (the transparent inner cutout of the frame asset) so the
    // framing step never needs to letterbox on the fallback path
    // (no per-fallback WARN noise). 1024×1365 → 0.7502, within the ±0.5%
    // tolerance of the exact 3:4 = 0.75 target.
    private static final int POSTER_WIDTH = 1024;
    private static final int POSTER_HEIGHT = 1365;

    public GeneratedCharacter character(String firstName) {
        String name = (firstName == null || firstName.isBlank())
                ? "HERO"
                : firstName.trim().toUpperCase(Locale.ROOT);
        return new GeneratedCharacter(
                name,
                "The Resilient",
                "DEGRADED, NOT DEFEATED.",
                List.of(
                        "Outlasts every timeout",
                        "Turns errors into signage",
                        "Always returns a poster"
                ),
                "When the stack falls, the spec stands."
        );
    }

    public PosterImage poster(Archetype archetype, Universe universe) {
        AccentTone accent = AccentResolver.deriveAccent(archetype, universe);

        BufferedImage img = new BufferedImage(POSTER_WIDTH, POSTER_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            g.setColor(Color.BLACK);
            g.fillRect(0, 0, POSTER_WIDTH, POSTER_HEIGHT);

            // Accent-coloured border so the fallback visually matches the
            // primary poster's colour language for identical inputs.
            g.setColor(accent.awt());
            g.setStroke(new BasicStroke(4f));
            g.drawRect(40, 40, POSTER_WIDTH - 80, POSTER_HEIGHT - 80);

            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 80));
            FontMetrics fm = g.getFontMetrics();
            String title = "FALLBACK";
            // Vertically centred at 50% of canvas height; the sub-line sits 60 px below.
            int titleY = POSTER_HEIGHT / 2;
            g.drawString(title, (POSTER_WIDTH - fm.stringWidth(title)) / 2, titleY);

            g.setFont(new Font(Font.SERIF, Font.PLAIN, 30));
            fm = g.getFontMetrics();
            String sub = "Even when the robots sleep.";
            g.drawString(sub, (POSTER_WIDTH - fm.stringWidth(sub)) / 2, titleY + 60);
        } finally {
            g.dispose();
        }

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, "PNG", out)) {
                throw new StubGenerationException("No PNG ImageIO writer registered");
            }
            return new PosterImage(out.toByteArray(), "image/png", POSTER_WIDTH, POSTER_HEIGHT);
        } catch (IOException e) {
            throw new StubGenerationException("Failed to encode fallback poster", e);
        }
    }
}

package com.aiavatar.alterego.service.text;

import com.aiavatar.alterego.infrastructure.overlay.text.*;

import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAsset;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAssetLoader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * On-demand visual snapshot of a fully-rendered framed poster with the
 * {Unbounded / Geist / Geist Italic} typography, written to
 * {@code /tmp/poster-text-overlay-demo.png}. Skipped by default — run
 * with {@code -Dposter.demo=true} after tweaking poster typography to
 * confirm the rendered result before committing:
 *
 * <pre>./gradlew :backend:test \
 *     --tests com.aiavatar.alterego.infrastructure.overlay.text.PosterTextOverlayDemo \
 *     -Dposter.demo=true</pre>
 */
class PosterTextOverlayDemo {

    @Test
    @EnabledIfSystemProperty(named = "poster.demo", matches = "true")
    void writeSamplePosterToTmp() throws Exception {
        PosterFrameAssetLoader assetLoader = invokeInit(new PosterFrameAssetLoader());
        PosterFrameAsset asset = assetLoader.get();
        if (!asset.loaded()) {
            throw new IllegalStateException("frame asset failed to load");
        }

        PosterTextFonts fonts = new PosterTextFonts();
        fonts.init();

        BufferedImage canvas = new BufferedImage(asset.canvasWidthPx(),
                asset.canvasHeightPx(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            for (int y = 0; y < canvas.getHeight(); y++) {
                float t = (float) y / canvas.getHeight();
                int r, gg, b;
                if (t < 0.5f) {
                    float u = t * 2f;
                    r = (int) (232 + (168 - 232) * u);
                    gg = (int) (66 + (85 - 66) * u);
                    b = (int) (122 + (247 - 122) * u);
                } else {
                    float u = (t - 0.5f) * 2f;
                    r = (int) (168 + (0 - 168) * u);
                    gg = (int) (85 + (191 - 85) * u);
                    b = (int) (247 + (255 - 247) * u);
                }
                g.setColor(new Color(clamp(r), clamp(gg), clamp(b)));
                g.fillRect(0, y, canvas.getWidth(), 1);
            }
            g.drawImage(asset.image(), 0, 0, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(canvas, "png", baos);
        PosterImage framed = new PosterImage(baos.toByteArray(), "image/png",
                asset.canvasWidthPx(), asset.canvasHeightPx());

        PosterFrameAssetLoader exposed = new PosterFrameAssetLoader() {
            @Override public PosterFrameAsset get() { return asset; }
        };
        PosterTextOverlayService service = new PosterTextOverlayService(exposed, fonts);

        PosterImage out = service.apply(framed,
                new PosterTextLines("Ada", "Cloud Architect", "It is always DNS."));

        Path tmp = Path.of("/tmp/poster-text-overlay-demo.png");
        Files.write(tmp, out.bytes());
        System.out.println("wrote " + tmp + " (" + out.bytes().length + " bytes, "
                + out.widthPx() + "x" + out.heightPx() + ")");
    }

    private static PosterFrameAssetLoader invokeInit(PosterFrameAssetLoader loader)
            throws ReflectiveOperationException {
        Method m = PosterFrameAssetLoader.class.getDeclaredMethod("init");
        m.setAccessible(true);
        m.invoke(loader);
        return loader;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }
}

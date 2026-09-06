package com.aiavatar.alterego.infrastructure.overlay.text;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Decodes the bundled poster typography (display + body + italic) from
 * the classpath exactly once per JVM and caches it for reuse by
 * {@link PosterTextOverlayService}.
 *
 * <p>The poster text mirrors the frontend's typographic system:
 * <ul>
 *   <li><b>display</b> → Unbounded Bold — the geometric arcade-style
 *       face the frontend uses for headings (see
 *       {@code frontend/src/styles/tokens.css} {@code --font-display}).
 *       Renders the user's first name as an all-caps headline.</li>
 *   <li><b>body</b> → Geist Regular — the refined sans the frontend
 *       uses for body text ({@code --font-body}). Renders the role
 *       label as a small all-caps subtitle.</li>
 *   <li><b>italic</b> → Geist Italic — body italic for the quote so it
 *       reads as a voice rather than a label.</li>
 * </ul>
 *
 * <p>Mirrors {@code PosterFrameAssetLoader}'s shape: {@code @PostConstruct}
 * load, accessor, fail-soft. On any decode/registration failure (or a
 * missing resource) the failing slot falls through to JDK logical
 * {@link Font#SANS_SERIF} plus a {@code WARN event=text.font.fallback}
 * line — production deploys on Alpine-based JREs (no {@code fontconfig},
 * no system fonts) MUST keep the bundled TTFs to avoid the unreadable
 * square-glyph fallback that logical SansSerif resolves to in that
 * runtime.
 */
@Component
public class PosterTextFonts {

    private static final Logger log = LoggerFactory.getLogger(PosterTextFonts.class);

    static final String DISPLAY_RESOURCE = "branding/fonts/Unbounded-Bold.ttf";
    static final String BODY_RESOURCE = "branding/fonts/Geist-Regular.ttf";
    static final String ITALIC_RESOURCE = "branding/fonts/Geist-Italic.ttf";

    private final Map<String, String> resourcesBySlot;
    private Font display = logicalFallback();
    private Font body = logicalFallback();
    private Font italic = logicalFallback();

    public PosterTextFonts() {
        this(Map.of(
                "display", DISPLAY_RESOURCE,
                "body", BODY_RESOURCE,
                "italic", ITALIC_RESOURCE));
    }

    public PosterTextFonts(Map<String, String> resourcesBySlot) {
        this.resourcesBySlot = new HashMap<>(resourcesBySlot);
    }

    @PostConstruct
    public void init() {
        this.display = load(resourcesBySlot.get("display"));
        this.body = load(resourcesBySlot.get("body"));
        this.italic = load(resourcesBySlot.get("italic"));
    }

    public Font display() { return display; }
    public Font body() { return body; }
    public Font italic() { return italic; }

    Font load(String classpathResource) {
        try (InputStream in = openStream(classpathResource)) {
            if (in == null) {
                log.warn("event=text.font.fallback resource={} reason=not_found",
                        classpathResource);
                return logicalFallback();
            }
            Font loaded;
            try {
                loaded = Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (FontFormatException | IOException e) {
                log.warn("event=text.font.fallback resource={} reason=decode_failed",
                        classpathResource, e);
                return logicalFallback();
            }
            try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(loaded);
            } catch (RuntimeException e) {
                log.warn("event=text.font.register_failed resource={} family={}",
                        classpathResource, loaded.getFamily(), e);
            }
            log.info("event=text.font.loaded resource={} family={}",
                    classpathResource, loaded.getFamily());
            return loaded;
        } catch (IOException e) {
            log.warn("event=text.font.fallback resource={} reason=decode_failed",
                    classpathResource, e);
            return logicalFallback();
        }
    }

    protected InputStream openStream(String classpathResource) throws IOException {
        ClassPathResource resource = new ClassPathResource(classpathResource);
        if (!resource.exists()) {
            return null;
        }
        return resource.getInputStream();
    }

    private static Font logicalFallback() {
        Font fallback = Font.decode(Font.SANS_SERIF);
        return fallback != null ? fallback : new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    /**
     * Test-only loader that reads any slot's asset from an absolute
     * file-system {@link Path} instead of the classpath. Used by the unit
     * tests for the missing/decode-failure cases where the classpath
     * cannot host the synthetic fixture.
     */
    public static final class FileSystemTestFonts extends PosterTextFonts {
        private final Map<String, Path> pathsBySlot;

        public FileSystemTestFonts(Map<String, Path> pathsBySlot) {
            super(pathsToResources(pathsBySlot));
            this.pathsBySlot = new HashMap<>(pathsBySlot);
        }

        private static Map<String, String> pathsToResources(Map<String, Path> pathsBySlot) {
            Map<String, String> resources = new HashMap<>();
            for (Map.Entry<String, Path> e : pathsBySlot.entrySet()) {
                resources.put(e.getKey(), e.getValue().toString());
            }
            return resources;
        }

        @Override
        protected InputStream openStream(String resource) throws IOException {
            for (Map.Entry<String, Path> e : pathsBySlot.entrySet()) {
                if (resource.equals(e.getValue().toString())) {
                    if (!Files.exists(e.getValue())) return null;
                    return Files.newInputStream(e.getValue());
                }
            }
            return null;
        }
    }
}

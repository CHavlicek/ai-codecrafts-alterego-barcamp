package com.aiavatar.alterego.service.text;

import com.aiavatar.alterego.infrastructure.overlay.text.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PosterTextFonts}. Covers the bundled-TTF happy
 * path for all three slots, classpath-missing fallback, decode-failure
 * fallback, and the cache invariant ({@code display()/body()/italic()}
 * return the same instances across calls — single decode per JVM).
 */
class PosterTextFontsTest {

    @Test
    void defaultConstructorLoadsAllThreeBundledFontsAndCachesThem() {
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterTextFonts fonts = new PosterTextFonts();
        fonts.init();

        Font display1 = fonts.display();
        Font display2 = fonts.display();
        assertNotNull(display1);
        assertSame(display1, display2, "display() must return cached instance");
        assertNotNull(fonts.body());
        assertNotNull(fonts.italic());
        assertSame(fonts.body(), fonts.body());
        assertSame(fonts.italic(), fonts.italic());

        long infoCount = appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .filter(e -> e.getFormattedMessage().contains("event=text.font.loaded"))
                .count();
        assertEquals(3L, infoCount,
                "expected exactly three INFO event=text.font.loaded lines, got: "
                        + appender.list);

        long warnCount = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .count();
        assertEquals(0L, warnCount,
                "happy path emits no WARN; got: " + appender.list);
    }

    @Test
    void missingResourceFallsThroughToLogicalSansSerifAndLogsWarn() {
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterTextFonts fonts = new PosterTextFonts(Map.of(
                "display", "branding/fonts/does-not-exist.ttf",
                "body", "branding/fonts/Geist-Regular.ttf",
                "italic", "branding/fonts/Geist-Italic.ttf"));
        fonts.init();

        assertNotNull(fonts.display(), "fallback must be non-null even when resource missing");
        assertNotNull(fonts.body());
        assertNotNull(fonts.italic());
        assertWarnContains(appender, "event=text.font.fallback", "reason=not_found");
    }

    @Test
    void decodeFailureFallsThroughToLogicalSansSerifAndLogsWarn(@TempDir Path tmp)
            throws IOException {
        Path notAFont = tmp.resolve("not-a-font.ttf");
        Files.writeString(notAFont, "this is not a font file");
        ListAppender<ILoggingEvent> appender = attachAppender();

        PosterTextFonts fonts = new PosterTextFonts.FileSystemTestFonts(Map.of(
                "display", notAFont,
                "body", notAFont,
                "italic", notAFont));
        fonts.init();

        assertNotNull(fonts.display());
        assertNotNull(fonts.body());
        assertNotNull(fonts.italic());
        assertWarnContains(appender, "event=text.font.fallback", "reason=decode_failed");
    }

    @Test
    void openStreamIoExceptionFallsThroughToLogicalSansSerifAndLogsWarn() {
        ListAppender<ILoggingEvent> appender = attachAppender();
        PosterTextFonts fonts = new PosterTextFonts(Map.of(
                "display", "branding/fonts/exploding.ttf",
                "body", "branding/fonts/exploding.ttf",
                "italic", "branding/fonts/exploding.ttf")) {
            @Override
            protected java.io.InputStream openStream(String resource) throws IOException {
                throw new IOException("synthetic open failure");
            }
        };
        fonts.init();

        assertNotNull(fonts.display());
        assertNotNull(fonts.body());
        assertNotNull(fonts.italic());
        assertWarnContains(appender, "event=text.font.fallback", "reason=decode_failed");
    }

    private static ListAppender<ILoggingEvent> attachAppender() {
        Logger root = (Logger) LoggerFactory.getLogger(PosterTextFonts.class);
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

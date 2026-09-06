package com.aiavatar.alterego.unit;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import com.aiavatar.alterego.boundary.logging.PhotoRedactionFilter;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for {@link PhotoRedactionFilter} (FR-016) — covers the
 * branches that {@link com.aiavatar.alterego.integration.LogRedactionIT}
 * only exercises indirectly.
 */
class PhotoRedactionFilterTest {

    private final Filter<ch.qos.logback.classic.spi.ILoggingEvent> filter = new PhotoRedactionFilter();

    /**
     * Synthesise a LoggingEvent with an empty MDC by default. Logback's
     * {@code LoggingEvent.getMDCPropertyMap} NPEs when the event has neither
     * an explicit MDC nor a loggerContext, so initialising to an empty map
     * is the simplest way to keep the test framework-free.
     */
    private static LoggingEvent event(String message) {
        return event(message, Map.of());
    }

    private static LoggingEvent event(String message, Map<String, String> mdc) {
        LoggingEvent e = new LoggingEvent();
        e.setLevel(Level.INFO);
        e.setMessage(message);
        e.setMDCPropertyMap(mdc);
        return e;
    }

    private static LoggingEvent eventWithMdc(Map<String, String> mdc) {
        return event("plain", mdc);
    }

    @Test
    void returnsNeutralForPlainEvent() {
        assertEquals(FilterReply.NEUTRAL, filter.decide(event("just a regular log line")));
    }

    @Test
    void returnsNeutralForNullEvent() {
        assertEquals(FilterReply.NEUTRAL, filter.decide(null));
    }

    @Test
    void returnsNeutralForEmptyMessage() {
        assertEquals(FilterReply.NEUTRAL, filter.decide(event("")));
        assertEquals(FilterReply.NEUTRAL, filter.decide(event(null)));
    }

    @Test
    void deniesEventContainingDataImagePrefix() {
        assertEquals(FilterReply.DENY,
                filter.decide(event("Response body: data:image/png;base64,AAAA…")));
        assertEquals(FilterReply.DENY,
                filter.decide(event("leaked data:image/jpeg;base64,/9j/4AAQSkZJR…")));
    }

    @Test
    void deniesEventWithPhotoFieldMarker() {
        assertEquals(FilterReply.DENY, filter.decide(event("photo=AAAA")));
        assertEquals(FilterReply.DENY, filter.decide(event("{\"photo\":\"AAAA\"}")));
        assertEquals(FilterReply.DENY, filter.decide(event("photoBytes=AAAA")));
        assertEquals(FilterReply.DENY, filter.decide(event("\"imageData\":\"...\"")));
    }

    @Test
    void deniesEventWithSensitiveKeyInMdc() {
        assertEquals(FilterReply.DENY,
                filter.decide(eventWithMdc(Map.of("photo", "oops"))));
        assertEquals(FilterReply.DENY,
                filter.decide(eventWithMdc(Map.of("photoBytes", "oops"))));
        assertEquals(FilterReply.DENY,
                filter.decide(eventWithMdc(Map.of("imageData", "oops"))));
    }

    @Test
    void neutralWhenMdcHasOnlyBenignKeys() {
        assertEquals(FilterReply.NEUTRAL,
                filter.decide(eventWithMdc(Map.of("requestId", "abc-123"))));
    }
}

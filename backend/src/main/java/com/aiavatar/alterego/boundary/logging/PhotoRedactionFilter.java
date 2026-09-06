package com.aiavatar.alterego.boundary.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Logback filter that DROPS log events containing photo bytes, known
 * sensitive field markers, or credential-bearing field-name patterns.
 * Mounted on the JSON appender in {@code logback-spring.xml}.
 *
 * <p>FR-016: redact photo payloads.
 * <p>024 / FR-2415 / SC-008: also drop any event whose key matches the
 * credential field-name patterns ({@code api[_-]?key}, {@code authorization}).
 * Drop the whole line — false negatives on a scrubber are worse than losing
 * a debug line.
 */
public class PhotoRedactionFilter extends Filter<ILoggingEvent> {

    private static final Set<String> SENSITIVE_FIELDS = Set.of("photo", "photoBytes", "imageData");

    /** Field-name patterns that carry credentials we must never log (FR-2415). */
    private static final Pattern CREDENTIAL_FIELD_NAME = Pattern.compile(
            "(?i)(?:api[_-]?key|authorization)");

    /** Match {@code "apiKey": "..."} or {@code apiKey=...} in formatted message text. */
    private static final Pattern CREDENTIAL_IN_MESSAGE = Pattern.compile(
            "(?i)(?:\"\\s*(?:api[_-]?key|authorization)\\s*\"\\s*:|\\b(?:api[_-]?key|authorization)\\s*=)");

    @Override
    public FilterReply decide(ILoggingEvent event) {
        if (event == null) {
            return FilterReply.NEUTRAL;
        }
        if (containsSensitivePayload(event.getFormattedMessage())) {
            return FilterReply.DENY;
        }
        Map<String, String> mdc = event.getMDCPropertyMap();
        if (mdc != null) {
            for (String key : SENSITIVE_FIELDS) {
                if (mdc.containsKey(key)) {
                    return FilterReply.DENY;
                }
            }
            for (String key : mdc.keySet()) {
                if (CREDENTIAL_FIELD_NAME.matcher(key).find()) {
                    return FilterReply.DENY;
                }
            }
        }
        return FilterReply.NEUTRAL;
    }

    private boolean containsSensitivePayload(String message) {
        if (message == null || message.isEmpty()) {
            return false;
        }
        if (message.contains("data:image/")) {
            return true;
        }
        for (String field : SENSITIVE_FIELDS) {
            if (message.contains(field + "=") || message.contains("\"" + field + "\":")) {
                return true;
            }
        }
        return CREDENTIAL_IN_MESSAGE.matcher(message).find();
    }
}

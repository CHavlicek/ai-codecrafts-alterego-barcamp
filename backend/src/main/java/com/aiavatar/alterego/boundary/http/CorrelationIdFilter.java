package com.aiavatar.alterego.boundary.http;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.SecureRandom;

/**
 * Generates or propagates a {@code correlationId} per HTTP request, places
 * it on the SLF4J MDC for the duration of the request thread, and echoes
 * it as the {@code X-Correlation-Id} response header (FR-2412, US5).
 *
 * <p>Format: 12-character base32 (alphanumeric, upper-cased). Inbound
 * {@code X-Correlation-Id} values are accepted only if they match
 * {@code ^[A-Z0-9]{1,64}$}; malformed or oversized inbound values are
 * silently replaced with a freshly generated id (never trust
 * client-supplied identifiers for log correlation without bounding).
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} + 10 so it sees every
 * inbound request before any logging filter and clears the MDC in its
 * {@code finally} block (no thread-local leakage across the pool).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER = "X-Correlation-Id";

    // RFC 4648 base32 alphabet, upper-case only, no padding.
    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int ID_LENGTH = 12;

    private final SecureRandom random = new SecureRandom();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String id = resolve(req.getHeader(HEADER));
        MDC.put(MDC_KEY, id);
        res.setHeader(HEADER, id);
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolve(String inbound) {
        if (inbound == null || inbound.isBlank() || inbound.length() > 64 || !isAlphaNumUpper(inbound)) {
            return generate();
        }
        return inbound;
    }

    private static boolean isAlphaNumUpper(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'))) return false;
        }
        return true;
    }

    private String generate() {
        StringBuilder sb = new StringBuilder(ID_LENGTH);
        for (int i = 0; i < ID_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}

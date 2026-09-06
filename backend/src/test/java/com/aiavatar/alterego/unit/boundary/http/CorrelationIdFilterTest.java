package com.aiavatar.alterego.unit.boundary.http;

import com.aiavatar.alterego.boundary.http.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T059 — {@link CorrelationIdFilter} contract:
 *   - generates id when {@code X-Correlation-Id} header is absent;
 *   - propagates a valid inbound id (alphanumeric, ≤ 64 chars);
 *   - rejects malformed inbound and generates a fresh id;
 *   - clears MDC in {@code finally} (no thread-local leak).
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesFreshIdWhenHeaderIsAbsent() throws Exception {
        AtomicReference<String> midRequestMdc = new AtomicReference<>();
        HttpServletRequest req = mock(HttpServletRequest.class);
        HttpServletResponse res = mock(HttpServletResponse.class);
        FilterChain chain = (r, s) -> midRequestMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(req, res, chain);

        assertNotNull(midRequestMdc.get());
        assertEquals(12, midRequestMdc.get().length());
        assertTrue(midRequestMdc.get().chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')));
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "MDC must be cleared after request");
        verify(res).setHeader(CorrelationIdFilter.HEADER, midRequestMdc.get());
    }

    @Test
    void propagatesValidInboundId() throws Exception {
        when(mockReq("ABC123XYZ456").getHeader(CorrelationIdFilter.HEADER)).thenReturn("ABC123XYZ456");
        HttpServletRequest req = mockReq("ABC123XYZ456");
        HttpServletResponse res = mock(HttpServletResponse.class);
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(req, res, (r, s) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertEquals("ABC123XYZ456", seen.get());
        verify(res).setHeader(CorrelationIdFilter.HEADER, "ABC123XYZ456");
    }

    @Test
    void rejectsMalformedInboundAndGenerates() throws Exception {
        HttpServletRequest req = mockReq("bad-with-hyphens");
        HttpServletResponse res = mock(HttpServletResponse.class);
        AtomicReference<String> seen = new AtomicReference<>();
        filter.doFilter(req, res, (r, s) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertNotEquals("bad-with-hyphens", seen.get());
        assertEquals(12, seen.get().length());
    }

    private static HttpServletRequest mockReq(String headerValue) {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getHeader(CorrelationIdFilter.HEADER)).thenReturn(headerValue);
        return req;
    }
}

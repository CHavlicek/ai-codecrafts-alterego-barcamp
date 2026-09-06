package com.aiavatar.alterego.infrastructure.email;

/**
 * 023 (issue #57) — thrown by {@link AlterEgoEmailService} when
 * {@code spring.mail.host} was blank at startup so no SMTP send is
 * possible. Translated to RFC 7807 {@code 503 Service Unavailable} with
 * {@code type=https://aiavatar.local/problems/email/not-configured}
 * by {@code ProblemDetailAdvice} (FR-2311 / FR-2318; research R11).
 *
 * <p>This is the ONLY exception class that maps to the typed
 * "not-configured" 503. Transient SMTP failures bubble up as
 * {@code MailException} and map to a generic 502 — the FE
 * classifies on the {@code type} URI to disambiguate.
 */
public class EmailNotConfiguredException extends RuntimeException {

    private static final String DEFAULT_DETAIL =
            "The mail server is not configured. Contact the operator to enable email delivery.";

    private final String detail;

    public EmailNotConfiguredException() {
        super(DEFAULT_DETAIL);
        this.detail = DEFAULT_DETAIL;
    }

    public EmailNotConfiguredException(String detail) {
        super(detail);
        this.detail = detail;
    }

    public String getDetail() {
        return detail;
    }
}

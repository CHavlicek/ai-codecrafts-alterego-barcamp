package com.aiavatar.alterego.infrastructure.config;

import jakarta.annotation.PostConstruct;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 023 → 027 — startup-presence check (research R2 / 027 R2).
 *
 * <p>027 widens the cached boolean from a single-input check on
 * {@code spring.mail.host} (023 host-only rule) to a four-input AND
 * (FR-2706): host AND username AND password AND
 * {@code aiavatar.email.from} ALL non-blank. Rationale: with the
 * 027 default host {@code mail.privateemail.com} shipped in
 * {@code application.yml}, the 023 host-only rule would misreport
 * deployments with no credentials as "configured" and surface
 * confusing 502s on click; the four-input gate restores the helpful
 * "not yet configured" alert for the credentials-missing case.
 *
 * <p>No live SMTP probe is performed at startup (FR-2311 / FR-2318 /
 * FR-2708). Re-evaluated never. A configured-but-credentials-rejected
 * outcome (e.g. PrivateEmail says NO at AUTH or MAIL FROM time)
 * surfaces at click time as a retryable-failure 502 via
 * {@link ProblemDetailAdvice}, NOT as the "not configured" 503.
 */
@Component
public class EmailConfigured {

    private static final Logger log = LoggerFactory.getLogger(EmailConfigured.class);

    private final String host;
    private final String username;
    private final String password;
    private final String from;
    private boolean configured;

    public EmailConfigured(
            @Value("${spring.mail.host:}") String host,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${aiavatar.email.from:}") String from) {
        this.host = host;
        this.username = username;
        this.password = password;
        this.from = from;
    }

    @PostConstruct
    void init() {
        this.configured =
                isNonBlank(host)
                        && isNonBlank(username)
                        && isNonBlank(password)
                        && isNonBlank(from);
        // No PII — the boolean tells operators whether email is wired,
        // not the address/credentials the operator wired it to (R5).
        // No per-input mask: emitting "username=present, password=absent"
        // would leak which env var was forgotten in multi-tenant logs.
        log.info(
                "event=email.config.evaluated configured={}",
                configured,
                StructuredArguments.kv("event", "email.config.evaluated"),
                StructuredArguments.kv("configured", configured));
    }

    public boolean isConfigured() {
        return configured;
    }

    private static boolean isNonBlank(String s) {
        return s != null && !s.isBlank();
    }
}

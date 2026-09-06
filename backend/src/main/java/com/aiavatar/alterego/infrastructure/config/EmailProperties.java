package com.aiavatar.alterego.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 023 (issue #57) — typed config for the outbound email seam. Bound
 * from the {@code aiavatar.email.*} block in {@code application.yml};
 * both keys are env-var-overridable.
 *
 * <ul>
 *   <li>{@link #from()} — sender address used as the {@code From} header
 *       on every outbound message. Operator-supplied.</li>
 *   <li>{@link #subject()} — subject line. Defaults to the FR-2313
 *       string; overrideable via {@code AIAVATAR_EMAIL_SUBJECT} for
 *       localisation / per-event customisation.</li>
 * </ul>
 *
 * <p>The "configured" state is NOT determined here — see
 * {@link EmailConfigured} (research R2). EmailProperties supplies the
 * fixed sender + subject; whether to send at all is decided upstream.
 */
@ConfigurationProperties(prefix = "aiavatar.email")
public record EmailProperties(String from, String subject) {}

package com.aiavatar.alterego.service.email;

import com.aiavatar.alterego.infrastructure.config.EmailConfigured;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 023 → 027 — startup-presence check. Originally a one-input check on
 * {@code spring.mail.host} (023 FR-2311 / FR-2318 / research R2). 027
 * widens the gate to a four-input AND (FR-2706): host + username +
 * password + {@code aiavatar.email.from} ALL non-blank. The cached
 * boolean is computed once at {@code @PostConstruct}; no live SMTP probe
 * is performed at startup.
 *
 * <p>The four "host-only present" / "blank" cases retained from 023
 * continue to assert {@code isConfigured()=false} — under the 027 gate
 * host alone is not enough.
 */
class EmailConfiguredTest {

    private static final String HOST = "spring.mail.host=mail.privateemail.com";
    private static final String USER = "spring.mail.username=tester";
    private static final String PASS = "spring.mail.password=pw";
    private static final String FROM = "aiavatar.email.from=tester@example.com";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
            .withUserConfiguration(Config.class);

    // ── 023 baseline cases — host alone is NEVER enough under the 027 gate ──

    @Test
    void hostBlankProducesUnconfiguredState() {
        runner.withPropertyValues("spring.mail.host=").run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    @Test
    void hostAbsentProducesUnconfiguredState() {
        // No property override → property is absent (binding default "")
        runner.run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    @Test
    void hostOnlyPresentProducesUnconfiguredState() {
        // 027: host alone is no longer sufficient — three more inputs are
        // required by FR-2706 (this case used to assert true under the
        // 023 host-only gate; it now asserts false).
        runner.withPropertyValues(HOST).run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    @Test
    void hostWhitespaceOnlyProducesUnconfiguredState() {
        // Defensive — a host of "   " is not a real host.
        runner.withPropertyValues("spring.mail.host=   ").run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    // ── 027 new cases — each missing input flips the gate to false ──

    @Test
    void hostPresentButUsernameBlankProducesUnconfigured() {
        // FR-2706 (b)
        runner.withPropertyValues(HOST, "spring.mail.username=", PASS, FROM).run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    @Test
    void hostAndUsernamePresentButPasswordBlankProducesUnconfigured() {
        // FR-2706 (c)
        runner.withPropertyValues(HOST, USER, "spring.mail.password=", FROM).run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    @Test
    void hostUsernamePasswordPresentButFromBlankProducesUnconfigured() {
        // FR-2706 (d)
        runner.withPropertyValues(HOST, USER, PASS, "aiavatar.email.from=").run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    @Test
    void allFourPresentProducesConfigured() {
        // FR-2706 all four (a)+(b)+(c)+(d)
        runner.withPropertyValues(HOST, USER, PASS, FROM).run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isTrue();
        });
    }

    @Test
    void whitespaceOnlyUsernameProducesUnconfigured() {
        // Defensive — a username of "   " is not a real value.
        runner.withPropertyValues(HOST, "spring.mail.username=   ", PASS, FROM).run(ctx -> {
            EmailConfigured ec = ctx.getBean(EmailConfigured.class);
            assertThat(ec.isConfigured()).isFalse();
        });
    }

    @Configuration
    @Import(EmailConfigured.class)
    static class Config {}
}

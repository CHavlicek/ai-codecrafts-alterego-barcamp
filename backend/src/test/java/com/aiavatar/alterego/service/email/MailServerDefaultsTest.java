package com.aiavatar.alterego.service.email;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 027 — Pins the production {@code application.yml} {@code spring.mail.*}
 * defaults to Namecheap PrivateEmail's documented submission recipe
 * (research.md R1): host {@code mail.privateemail.com}, port {@code 587},
 * {@code mail.smtp.auth=true}, {@code mail.smtp.starttls.enable=true},
 * {@code mail.smtp.starttls.required=true}.
 *
 * <p>Service tier — boots Spring Boot's {@link MailSenderAutoConfiguration}
 * against the real {@code application.yml} on the classpath (no
 * {@code @TestPropertySource} override) so the assertions read the literal
 * shipped values. If any default in {@code application.yml} drifts away
 * from the PrivateEmail recipe, this class is the first tier to fail.
 *
 * <p>The fixture relies on {@link MailSenderAutoConfiguration}'s rule that
 * a {@link JavaMailSender} bean is created only when {@code spring.mail.host}
 * is non-blank. With the 027 defaults present, that condition holds and
 * the bean is autowired below. With the pre-027 blank-host default, this
 * class fails fast at context-startup with "no JavaMailSender bean" —
 * exactly the RED signal TDD wants before {@code application.yml} is updated.
 */
@SpringBootTest(classes = MailServerDefaultsTest.TestConfig.class)
class MailServerDefaultsTest {

    @Autowired
    private JavaMailSender mailSender;

    @Test
    void hostDefaultsToPrivateEmail() {
        JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
        assertThat(impl.getHost()).isEqualTo("mail.privateemail.com");
    }

    @Test
    void portDefaultsTo587() {
        JavaMailSenderImpl impl = (JavaMailSenderImpl) mailSender;
        assertThat(impl.getPort()).isEqualTo(587);
    }

    @Test
    void smtpAuthIsEnabledByDefault() {
        Properties props = ((JavaMailSenderImpl) mailSender).getJavaMailProperties();
        assertThat(props.getProperty("mail.smtp.auth")).isEqualTo("true");
    }

    @Test
    void starttlsEnableIsTrueByDefault() {
        Properties props = ((JavaMailSenderImpl) mailSender).getJavaMailProperties();
        assertThat(props.getProperty("mail.smtp.starttls.enable")).isEqualTo("true");
    }

    @Test
    void starttlsRequiredIsTrueByDefault() {
        Properties props = ((JavaMailSenderImpl) mailSender).getJavaMailProperties();
        assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");
    }

    /**
     * Minimal Spring Boot configuration: imports only the mail auto-config so
     * the test boots fast and reads no production beans beyond
     * {@code JavaMailSenderImpl}. {@code application.yml} is loaded by the
     * standard Spring Boot ConfigData pipeline even when {@code classes=}
     * scopes the bean graph.
     */
    @SpringBootConfiguration
    @ImportAutoConfiguration(MailSenderAutoConfiguration.class)
    static class TestConfig {}
}

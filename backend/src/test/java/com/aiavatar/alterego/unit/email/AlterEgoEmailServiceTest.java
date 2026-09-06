package com.aiavatar.alterego.unit.email;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiavatar.alterego.infrastructure.config.EmailConfigured;
import com.aiavatar.alterego.infrastructure.config.EmailProperties;
import com.aiavatar.alterego.infrastructure.config.RetryConfig;
import com.aiavatar.alterego.infrastructure.email.AlterEgoEmailBodyBuilder;
import com.aiavatar.alterego.infrastructure.email.AlterEgoEmailService;
import com.aiavatar.alterego.infrastructure.email.EmailNotConfiguredException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 023 (issue #57) — service-level contract for the email-send pipeline.
 *
 * Covers:
 *   • configured-state short-circuit (NOT_CONFIGURED → exception, no SMTP call)
 *   • happy-path MIME composition (subject + body + recipient + attachment)
 *   • PII redaction in log output (R5: recipient, body, first-name MUST NOT appear)
 *   • RetryTemplate wrap on `mailSender.send()` (5 attempts on MailSendException)
 */
class AlterEgoEmailServiceTest {

    private static final byte[] FAKE_PNG = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);

    private JavaMailSender mailSender;
    private EmailConfigured emailConfigured;
    private EmailProperties emailProperties;
    private AlterEgoEmailBodyBuilder bodyBuilder;
    private AlterEgoEmailService service;

    private Logger emailServiceLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setup() {
        mailSender = mock(JavaMailSender.class);
        // JavaMailSender.createMimeMessage() must return a real (non-null)
        // MimeMessage so MimeMessageHelper can populate it.
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        emailConfigured = mock(EmailConfigured.class);
        emailProperties = new EmailProperties(
                "alter-ego@ai-verbund-2026.local",
                "Your AI Generated Alter Ego - AI @ Verbund 2026");
        bodyBuilder = new AlterEgoEmailBodyBuilder();

        service = new AlterEgoEmailService(
                emailConfigured,
                emailProperties,
                java.util.Optional.of(mailSender),
                bodyBuilder,
                new RetryConfig().retryTemplate());

        emailServiceLogger = (Logger) LoggerFactory.getLogger(AlterEgoEmailService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        emailServiceLogger.addAppender(logAppender);
    }

    @AfterEach
    void teardown() {
        emailServiceLogger.detachAppender(logAppender);
    }

    @Test
    void unconfiguredShortCircuitsWithNotConfiguredAndNeverInvokesMailSender() {
        when(emailConfigured.isConfigured()).thenReturn(false);

        assertThrows(
                EmailNotConfiguredException.class,
                () -> service.send(
                        "someone@example.com",
                        "Dmytro",
                        FAKE_PNG,
                        "image/png",
                        UUID.randomUUID()));

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(mailSender, never()).createMimeMessage();
    }

    @Test
    void configuredCallComposesMimeMessageWithSubjectBodyRecipientAndAttachment() throws Exception {
        when(emailConfigured.isConfigured()).thenReturn(true);

        service.send(
                "someone@example.com",
                "Dmytro",
                FAKE_PNG,
                "image/png",
                UUID.randomUUID());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());

        MimeMessage sent = captor.getValue();
        // Subject must match FR-2313 exactly.
        assertThat(sent.getSubject()).isEqualTo("Your AI Generated Alter Ego - AI @ Verbund 2026");
        // Recipient must be the trimmed-to address.
        assertThat(sent.getAllRecipients()).hasSize(1);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo("someone@example.com");
        // From must come from EmailProperties.
        assertThat(sent.getFrom()).hasSize(1);
        assertThat(sent.getFrom()[0].toString()).isEqualTo("alter-ego@ai-verbund-2026.local");
        // Body must contain the FR-2314 substituted text.
        String raw = extractContent(sent);
        assertThat(raw).contains("Hey, Dmytro!");
        assertThat(raw).contains("Thank you, for being a part of AI @ Verbund 2026!");
        assertThat(raw).contains("Find your AI Generated Alter Ego attached to this letter.");
        assertThat(raw).contains("Happy times!");
    }

    @Test
    void trimsRecipientBeforeAddressing() throws Exception {
        when(emailConfigured.isConfigured()).thenReturn(true);

        service.send("  someone@example.com  ", "Dmytro", FAKE_PNG, "image/png", UUID.randomUUID());

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(captor.capture());
        assertThat(captor.getValue().getAllRecipients()[0].toString()).isEqualTo("someone@example.com");
    }

    @Test
    void successLogEmitsCorrelationIdButRedactsRecipientBodyAndFirstName() {
        when(emailConfigured.isConfigured()).thenReturn(true);
        UUID correlationId = UUID.randomUUID();

        service.send("alice-pii@private-domain.test", "Dmytro", FAKE_PNG, "image/png", correlationId);

        boolean hasCompletion = logAppender.list.stream()
                .anyMatch(e -> e.getFormattedMessage().contains("event=email.send.completed")
                        && e.getFormattedMessage().contains("outcome=sent")
                        && e.getFormattedMessage().contains(correlationId.toString()));
        assertThat(hasCompletion).as("expected one event=email.send.completed log line").isTrue();

        // PII redaction (R5 / SC-2306):
        for (ILoggingEvent event : logAppender.list) {
            String msg = event.getFormattedMessage();
            assertThat(msg).doesNotContain("alice-pii");
            assertThat(msg).doesNotContain("private-domain.test");
            assertThat(msg).doesNotContain("Dmytro");
            assertThat(msg).doesNotContain("Hey,");
            assertThat(msg).doesNotContain("Happy times!");
        }
    }

    @Test
    void retryWrapsMailSenderSendOnTransientFailure() {
        when(emailConfigured.isConfigured()).thenReturn(true);
        // Throw on every attempt; the project-wide RetryTemplate retries 5x.
        org.mockito.Mockito.doThrow(new MailSendException("kaboom"))
                .when(mailSender)
                .send(any(MimeMessage.class));

        assertThrows(
                MailSendException.class,
                () -> service.send(
                        "someone@example.com",
                        "Dmytro",
                        FAKE_PNG,
                        "image/png",
                        UUID.randomUUID()));

        verify(mailSender, times(5)).send(any(MimeMessage.class));
    }

    @Test
    void retryEventuallySucceedsWhenSendFailsThenRecovers() {
        when(emailConfigured.isConfigured()).thenReturn(true);
        org.mockito.Mockito.doThrow(new MailSendException("transient"))
                .doThrow(new MailSendException("transient"))
                .doNothing()
                .when(mailSender)
                .send(any(MimeMessage.class));

        service.send("someone@example.com", "Dmytro", FAKE_PNG, "image/png", UUID.randomUUID());

        verify(mailSender, times(3)).send(any(MimeMessage.class));
    }

    private static String extractContent(MimeMessage message) throws Exception {
        // Multipart MIME: walk the parts and concatenate any text/* content.
        Object content = message.getContent();
        if (content instanceof String s) return s;
        if (content instanceof jakarta.mail.Multipart mp) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mp.getCount(); i++) {
                jakarta.mail.BodyPart part = mp.getBodyPart(i);
                if (part.getContentType().toLowerCase().startsWith("text/")) {
                    // Use the part's InputStream so we don't depend on JavaMail's
                    // getContent() return type (which varies across implementations).
                    byte[] bytes = part.getInputStream().readAllBytes();
                    sb.append(new String(bytes, StandardCharsets.UTF_8));
                }
            }
            return sb.toString();
        }
        return content.toString();
    }
}

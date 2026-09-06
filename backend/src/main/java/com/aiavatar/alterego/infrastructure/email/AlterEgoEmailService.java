package com.aiavatar.alterego.infrastructure.email;

import com.aiavatar.alterego.application.port.EmailSenderPort;
import com.aiavatar.alterego.infrastructure.config.EmailConfigured;
import com.aiavatar.alterego.infrastructure.config.EmailProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import net.logstash.logback.argument.StructuredArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * 023 (issue #57) — orchestrates the email-send pipeline.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>If {@link EmailConfigured#isConfigured()} is false → throw
 *       {@link EmailNotConfiguredException} (no SMTP attempted).</li>
 *   <li>Compose a {@link MimeMessage} with the FR-2313 subject, the
 *       FR-2314 body (substituted), and the supplied image as an
 *       attachment.</li>
 *   <li>Wrap {@code mailSender.send(message)} in the project-wide
 *       {@link RetryTemplate} (Principle IV — 5 attempts, exponential
 *       backoff with jitter; research R10).</li>
 *   <li>On success, emit a single {@code event=email.send.completed}
 *       log line carrying only the {@code correlationId} (PII redaction
 *       per R5 / FR-2320 / SC-2306).</li>
 * </ol>
 *
 * <p>Transient {@link MailException} bubbles up after retry exhaustion
 * — {@code ProblemDetailAdvice} translates it to 502.
 */
@Service
public class AlterEgoEmailService implements EmailSenderPort {

    private static final Logger log = LoggerFactory.getLogger(AlterEgoEmailService.class);

    private final EmailConfigured emailConfigured;
    private final EmailProperties emailProperties;
    private final Optional<JavaMailSender> mailSender;
    private final AlterEgoEmailBodyBuilder bodyBuilder;
    private final RetryTemplate retryTemplate;

    public AlterEgoEmailService(
            EmailConfigured emailConfigured,
            EmailProperties emailProperties,
            Optional<JavaMailSender> mailSender,
            AlterEgoEmailBodyBuilder bodyBuilder,
            RetryTemplate retryTemplate) {
        this.emailConfigured = emailConfigured;
        this.emailProperties = emailProperties;
        this.mailSender = mailSender;
        this.bodyBuilder = bodyBuilder;
        this.retryTemplate = retryTemplate;
    }

    /**
     * @throws EmailNotConfiguredException if the mail server is not
     *     configured at startup (FR-2311).
     * @throws MailException after RetryTemplate exhausts on transient
     *     SMTP failures (FR-2316 → 502).
     */
    @Override
    public void send(
            String to,
            String firstName,
            byte[] imageBytes,
            String imageContentType,
            UUID correlationId) {
        if (!emailConfigured.isConfigured()) {
            throw new EmailNotConfiguredException();
        }
        // If isConfigured() is true, MailSenderAutoConfiguration MUST
        // have created a JavaMailSender bean. Defend in depth — a missing
        // bean while "configured" is a wiring bug, not a transient failure.
        JavaMailSender sender = mailSender.orElseThrow(() -> new MailSendException(
                "JavaMailSender bean missing despite spring.mail.host being set"));

        String trimmedTo = to.trim();
        String trimmedFirstName = firstName.trim();
        String body = bodyBuilder.build(trimmedFirstName);
        String filename = "image/jpeg".equalsIgnoreCase(imageContentType)
                ? "ai-alter-ego.jpg"
                : "ai-alter-ego.png";

        retryTemplate.execute(
                ctx -> {
                    MimeMessage message = sender.createMimeMessage();
                    try {
                        MimeMessageHelper helper =
                                new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
                        helper.setFrom(emailProperties.from());
                        helper.setTo(trimmedTo);
                        helper.setSubject(emailProperties.subject());
                        helper.setText(body, false);
                        helper.addAttachment(
                                filename, new ByteArrayResource(imageBytes), imageContentType);
                    } catch (MessagingException ex) {
                        // Treat composition failures as send failures so the
                        // retry budget covers transient JAF / mail-library
                        // hiccups too. The wrapper makes the surface uniform.
                        throw new MailSendException("Failed to compose MIME message", ex);
                    }
                    sender.send(message);
                    return null;
                });

        log.info(
                "event=email.send.completed outcome=sent correlationId={}",
                correlationId,
                StructuredArguments.kv("event", "email.send.completed"),
                StructuredArguments.kv("outcome", "sent"),
                StructuredArguments.kv("correlationId", correlationId));
    }
}

package com.aiavatar.alterego.application.port;

import java.util.Objects;

/**
 * Typed checked exception thrown by {@link EmailSenderPort#send} when SMTP
 * delivery fails. Consolidates the email flow's failure modes; replaces the
 * legacy {@code EmailNotConfiguredException} which became one specific
 * {@link Reason}. See {@code data-model.md} Entity 6.
 */
public class EmailDeliveryFailure extends Exception {

    private static final long serialVersionUID = 1L;

    public enum Reason {
        /** {@code spring.mail.host} is blank / SMTP starter not active. */
        NOT_CONFIGURED,
        /** SMTP server rejects the message (5xx). */
        SMTP_REFUSED,
        /** Recipient address fails strict re-validation at the port. */
        MALFORMED_RECIPIENT,
        /** Composed {@code MimeMessage} exceeds provider's accepted size. */
        ATTACHMENT_TOO_LARGE,
        /** SMTP connect / send exceeds configured timeout. */
        TIMEOUT
    }

    private final Reason reason;

    public EmailDeliveryFailure(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public EmailDeliveryFailure(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}

package com.aiavatar.alterego.application.port;

import java.util.UUID;

/**
 * Application-facing seam for SMTP delivery of a generated alter-ego
 * (Constitution Principle VIII). The orchestrator (and any other
 * application-layer caller) depends on this port only; the JavaMail
 * implementation lives in {@code infrastructure.email}.
 *
 * <p>Signature mirrors today's email-send entry point (positional args
 * for compatibility with the existing controller multipart flow):
 * recipient, first name, image bytes + MIME, correlation id.
 *
 * @throws EmailDeliveryFailure on typed delivery failure (NOT_CONFIGURED,
 *         SMTP_REFUSED, MALFORMED_RECIPIENT, ATTACHMENT_TOO_LARGE, TIMEOUT)
 *         — see {@code data-model.md} Entity 6. May also propagate the
 *         legacy unchecked {@code EmailNotConfiguredException} until that
 *         path is consolidated in a future iteration.
 */
public interface EmailSenderPort {

    void send(String to, String firstName, byte[] imageBytes,
              String imageContentType, UUID correlationId);
}

package com.aiavatar.alterego.application;

import com.aiavatar.alterego.application.port.EmailSenderPort;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Application-layer entry point for the email-send flow (Constitution
 * Principle VII — layered symmetry with {@link AlterEgoUseCase}).
 *
 * <p>024: depends on the application-facing {@link EmailSenderPort} only
 * (no reverse-direction dependency on infrastructure). The
 * controller → use case → port → JavaMailSender chain keeps the
 * {@code boundary → application → infrastructure} arrow straight.
 */
@Service
public class SendAlterEgoEmailUseCase {

    private final EmailSenderPort delegate;

    public SendAlterEgoEmailUseCase(EmailSenderPort delegate) {
        this.delegate = delegate;
    }

    public void send(String to, String firstName, byte[] imageBytes,
                     String imageContentType, UUID correlationId) {
        delegate.send(to, firstName, imageBytes, imageContentType, correlationId);
    }
}

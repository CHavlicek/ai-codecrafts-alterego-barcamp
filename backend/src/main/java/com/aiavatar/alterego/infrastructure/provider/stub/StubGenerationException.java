package com.aiavatar.alterego.infrastructure.provider.stub;

/**
 * Thrown by force-failure stub doubles ({@code force-stub-failure} profile)
 * to drive the {@link AlterEgoService} fallback path during E2E and
 * integration tests of SC-004. Also thrown by stub generators on a genuine
 * runtime failure.
 */
public class StubGenerationException extends RuntimeException {
    public StubGenerationException(String message) {
        super(message);
    }

    public StubGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}

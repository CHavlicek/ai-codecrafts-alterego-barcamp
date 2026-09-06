package com.aiavatar.alterego.application.pipeline;

/**
 * Per-request inputs that any pipeline stage may need beyond the
 * {@link com.aiavatar.alterego.domain.model.PosterImage} itself. Captured
 * by {@code AlterEgoUseCase} from the validated request and the generated
 * character (FR-2409).
 *
 * @param firstName    user's trimmed first name (overlay)
 * @param roleOfRecord canonical role string (prefab archetype label OR
 *                     trimmed custom role) — same value the prompts use
 * @param quote        character.quote() — overlay text on poster
 */
public record StageContext(String firstName, String roleOfRecord, String quote) {
}

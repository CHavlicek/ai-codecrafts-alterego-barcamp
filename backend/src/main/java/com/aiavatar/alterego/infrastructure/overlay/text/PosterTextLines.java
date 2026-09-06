package com.aiavatar.alterego.infrastructure.overlay.text;

/**
 * Three-piece identity rendered into the poster's bottom region by
 * {@code PosterTextOverlayService} (017 FR-1701 / FR-1710 — refined
 * 2026-05-08 again to render name, role, and quote as three distinct
 * typographic slots, mirroring the frontend's
 * {@code --font-display}/{@code --font-body}/italic system).
 *
 * <p>Field mapping:
 * <ul>
 *   <li>{@link #name()} ← {@code AlterEgoRequest.firstName} — the
 *       user's first name from the Setup form. Rendered in the display
 *       face (Unbounded Bold) as the all-caps headline.</li>
 *   <li>{@link #role()} ← humanised {@code Archetype.label()} (e.g.
 *       "Cloud Architect"). Sourced from the request's selection, NOT
 *       the AI-generated character title. Rendered in the body face
 *       (Geist Regular) as a small all-caps subtitle.</li>
 *   <li>{@link #quote()} ← {@code GeneratedCharacter.quote} — the
 *       short statement the character generator emits. Rendered in
 *       Geist Italic between curly quotation marks.</li>
 * </ul>
 *
 * <p>Defensive normalisation: leading/trailing whitespace is stripped
 * at construction; null inputs are coerced to empty strings (overlay
 * must not throw on malformed inputs). Empty fields are skipped at
 * render time — if every field is empty, the overlay step is a silent
 * no-op.
 */
public record PosterTextLines(
        String name,
        String role,
        String quote
) {
    public PosterTextLines {
        name = name == null ? "" : name.strip();
        role = role == null ? "" : role.strip();
        quote = quote == null ? "" : quote.strip();
    }

    public boolean isEmpty() {
        return name.isEmpty() && role.isEmpty() && quote.isEmpty();
    }
}

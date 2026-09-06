package com.aiavatar.alterego.infrastructure.overlay.text;

import java.awt.Color;

/**
 * Per-feature constants for poster text rendering (017 FR-1701..FR-1716,
 * refined 2026-05-08 for the three-slot {name / role / quote} typographic
 * hierarchy that mirrors the frontend's {@code --font-display} +
 * {@code --font-body} + italic system).
 *
 * <p>The overlay renders three stacked lines centred in the bottom-region
 * safe area. Each slot carries its own font (display / body / italic),
 * target size, case transform and letter-spacing — together they produce
 * a brand-consistent poster credit block:
 *
 * <pre>
 *      ADA                 ← name      (display, big, all-caps, tight tracking)
 *   CLOUD ARCHITECT        ← role      (body, small, all-caps, expanded tracking)
 *   "It is always DNS."    ← quote     (italic, medium, mixed-case)
 * </pre>
 *
 * <p>Each line shrinks to fit its safe horizontal width independently,
 * down to {@link #minSizePx()}. If the combined stack height exceeds the
 * bottom region, all three lines scale proportionally.
 *
 * <p>Off-white fill on rich-black outline clears WCAG AA 4.5:1 — the
 * halo (Q5 → C, drop-shadow / outline) is the immediate effective
 * background of every glyph (FR-1707).
 */
public record PosterTextStyle(
        SlotStyle nameStyle,
        SlotStyle roleStyle,
        SlotStyle quoteStyle,
        float minSizePx,
        float lineGapPx,
        Color fillColor,
        Color outlineColor,
        float outlineRatio
) {
    public enum FontSlot { DISPLAY, BODY, ITALIC }
    public enum CaseTransform { NONE, UPPERCASE }

    /** Resolved render config for one slot — font + size + transforms. */
    public record SlotStyle(
            FontSlot fontSlot,
            float targetSizePx,
            CaseTransform caseTransform,
            float trackingEms
    ) {
        public SlotStyle {
            if (fontSlot == null || caseTransform == null) {
                throw new IllegalArgumentException("fontSlot and caseTransform must be non-null");
            }
            if (targetSizePx <= 0) {
                throw new IllegalArgumentException("targetSizePx must be positive");
            }
        }
    }

    public PosterTextStyle {
        if (fillColor == null || outlineColor == null) {
            throw new IllegalArgumentException("fillColor and outlineColor must be non-null");
        }
        if (nameStyle == null || roleStyle == null || quoteStyle == null) {
            throw new IllegalArgumentException("name/role/quote styles must be non-null");
        }
        if (nameStyle.targetSizePx() < quoteStyle.targetSizePx()
                || quoteStyle.targetSizePx() < roleStyle.targetSizePx()) {
            throw new IllegalArgumentException(
                    "target size hierarchy must be name >= quote >= role");
        }
        if (minSizePx <= 0 || minSizePx > roleStyle.targetSizePx()) {
            throw new IllegalArgumentException("minSizePx must be in (0, role target size]");
        }
        if (lineGapPx < 0) {
            throw new IllegalArgumentException("lineGapPx must be non-negative");
        }
        if (outlineRatio <= 0f || outlineRatio > 0.2f) {
            throw new IllegalArgumentException("outlineRatio must be in (0, 0.2]");
        }
    }

    /**
     * Default sizing + palette for the bundled 1024×1536 / ~700×171
     * bottom region. Targets are deliberately ambitious — 100 (name) +
     * 28 (role) + 34 (quote) with 0 px gap stacks naturally to ~191 px,
     * so the fitter's proportional vertical scale clamps the final
     * rendered sizes to ~89 / 25 / 30 — close to the safe-area ceiling
     * while preserving the name ≥ quote ≥ role hierarchy. Long inputs
     * still shrink-to-fit horizontally per slot.
     *
     * <p>Off-white (245, 245, 240) on rich-black (8, 8, 12) → contrast
     * ≈ 19:1, well above the 4.5:1 AA bar (FR-1707).
     */
    public static PosterTextStyle defaults() {
        return new PosterTextStyle(
                new SlotStyle(FontSlot.DISPLAY, 100f, CaseTransform.UPPERCASE, 0.02f),
                new SlotStyle(FontSlot.BODY, 28f, CaseTransform.UPPERCASE, 0.18f),
                new SlotStyle(FontSlot.ITALIC, 34f, CaseTransform.NONE, 0f),
                12f,
                0f,
                new Color(245, 245, 240),
                new Color(8, 8, 12),
                0.06f);
    }
}

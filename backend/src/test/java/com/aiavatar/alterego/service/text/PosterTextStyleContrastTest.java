package com.aiavatar.alterego.service.text;

import com.aiavatar.alterego.infrastructure.overlay.text.*;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 017 T022 — enforce that {@link PosterTextStyle#defaults()} chooses a
 * fill/outline pair whose WCAG contrast ratio clears the AA bar of 4.5:1
 * (FR-1707), plus exhaustive invariant coverage of the compact constructor.
 */
class PosterTextStyleContrastTest {

    private static final double WCAG_AA_CONTRAST_BAR = 4.5;
    private static final Color ANY = new Color(0, 0, 0);

    private static PosterTextStyle.SlotStyle name(float size) {
        return new PosterTextStyle.SlotStyle(
                PosterTextStyle.FontSlot.DISPLAY, size,
                PosterTextStyle.CaseTransform.UPPERCASE, 0.02f);
    }

    private static PosterTextStyle.SlotStyle role(float size) {
        return new PosterTextStyle.SlotStyle(
                PosterTextStyle.FontSlot.BODY, size,
                PosterTextStyle.CaseTransform.UPPERCASE, 0.18f);
    }

    private static PosterTextStyle.SlotStyle quote(float size) {
        return new PosterTextStyle.SlotStyle(
                PosterTextStyle.FontSlot.ITALIC, size,
                PosterTextStyle.CaseTransform.NONE, 0f);
    }

    @Test
    void defaultFillVsOutlineClearsAaContrastBar() {
        PosterTextStyle style = PosterTextStyle.defaults();
        double ratio = contrastRatio(style.fillColor(), style.outlineColor());
        assertTrue(ratio >= WCAG_AA_CONTRAST_BAR,
                "default fill/outline pair must clear WCAG AA 4.5:1, got " + ratio);
    }

    @Test
    void compactConstructorRejectsNullColors() {
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 12, 8, null, ANY, 0.06f));
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 12, 8, ANY, null, 0.06f));
    }

    @Test
    void compactConstructorRejectsNullSlotStyles() {
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                null, role(20), quote(28), 12, 8, ANY, ANY, 0.06f));
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), null, quote(28), 12, 8, ANY, ANY, 0.06f));
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), null, 12, 8, ANY, ANY, 0.06f));
    }

    @Test
    void slotStyleRejectsNonPositiveTargetSize() {
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle.SlotStyle(
                PosterTextStyle.FontSlot.DISPLAY, 0,
                PosterTextStyle.CaseTransform.NONE, 0f));
    }

    @Test
    void slotStyleRejectsNullFontSlotOrTransform() {
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle.SlotStyle(
                null, 20, PosterTextStyle.CaseTransform.NONE, 0f));
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle.SlotStyle(
                PosterTextStyle.FontSlot.BODY, 20, null, 0f));
    }

    @Test
    void compactConstructorRejectsHierarchyViolation() {
        // name target < quote target → reject (visual hierarchy violation).
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(20), role(15), quote(40), 12, 8, ANY, ANY, 0.06f));
        // quote target < role target → reject.
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(40), quote(20), 12, 8, ANY, ANY, 0.06f));
    }

    @Test
    void compactConstructorRejectsMinSizeOutOfRange() {
        // minSize ≤ 0
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 0, 8, ANY, ANY, 0.06f));
        // minSize > role target — role would clamp above floor.
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 25, 8, ANY, ANY, 0.06f));
    }

    @Test
    void compactConstructorRejectsNegativeLineGap() {
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 12, -1, ANY, ANY, 0.06f));
    }

    @Test
    void compactConstructorRejectsOutlineRatioOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 12, 8, ANY, ANY, 0f));
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 12, 8, ANY, ANY, -0.1f));
        assertThrows(IllegalArgumentException.class, () -> new PosterTextStyle(
                name(80), role(20), quote(28), 12, 8, ANY, ANY, 0.21f));
    }

    @Test
    void defaultsHasReadableShape() {
        PosterTextStyle style = PosterTextStyle.defaults();
        assertEquals(PosterTextStyle.FontSlot.DISPLAY, style.nameStyle().fontSlot());
        assertEquals(PosterTextStyle.FontSlot.BODY, style.roleStyle().fontSlot());
        assertEquals(PosterTextStyle.FontSlot.ITALIC, style.quoteStyle().fontSlot());
        assertEquals(PosterTextStyle.CaseTransform.UPPERCASE,
                style.nameStyle().caseTransform());
        assertEquals(PosterTextStyle.CaseTransform.UPPERCASE,
                style.roleStyle().caseTransform());
        assertEquals(PosterTextStyle.CaseTransform.NONE,
                style.quoteStyle().caseTransform());
        assertTrue(style.nameStyle().targetSizePx() >= style.quoteStyle().targetSizePx());
        assertTrue(style.quoteStyle().targetSizePx() >= style.roleStyle().targetSizePx());
        assertTrue(style.minSizePx() > 0
                && style.minSizePx() <= style.roleStyle().targetSizePx());
        assertTrue(style.lineGapPx() >= 0);
        assertTrue(style.outlineRatio() > 0 && style.outlineRatio() <= 0.2f);
    }

    private static double relativeLuminance(Color c) {
        double[] rgb = new double[]{
                c.getRed() / 255.0,
                c.getGreen() / 255.0,
                c.getBlue() / 255.0
        };
        for (int i = 0; i < 3; i++) {
            rgb[i] = rgb[i] <= 0.03928
                    ? rgb[i] / 12.92
                    : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        }
        return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2];
    }

    private static double contrastRatio(Color a, Color b) {
        double la = relativeLuminance(a);
        double lb = relativeLuminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }
}

package com.aiavatar.alterego.infrastructure.overlay.text;

import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAsset;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure helper that decides where each non-empty line of a
 * {@link PosterTextLines} is drawn inside a {@link PosterFrameAsset}'s
 * bottom-region safe area (017 FR-1701..FR-1715, refined 2026-05-08
 * for the three-slot {name / role / quote} layout).
 *
 * <p>For each slot the fitter applies the configured case transform,
 * derives the right per-slot font ({@link PosterTextFonts}) at the
 * target size with the configured letter-spacing, and shrinks the
 * size 1 px at a time until the line fits the safe horizontal width.
 * If the resulting stack height exceeds the bottom region, all slots
 * scale proportionally (clamped to {@link PosterTextStyle#minSizePx()}).
 *
 * <p>Stateless / deterministic — no I/O, no clock, no mutation — so
 * exhaustively unit-testable.
 */
public final class PosterTextFitter {

    private PosterTextFitter() {}

    /** Resolved render plan for one line — text + sized font + size + baseline pixel coords. */
    public record Fitted(String text, Font font, float sizePx, int baselineX, int baselineY) {}

    private record Slot(String text, PosterTextStyle.SlotStyle style, Font baseFont) {}

    public static List<Fitted> fit(
            PosterTextLines lines,
            PosterTextStyle style,
            PosterFrameAsset asset,
            PosterTextFonts fonts,
            FontRenderContext frc) {

        if (lines.isEmpty()) {
            return List.of();
        }

        List<Slot> slots = new ArrayList<>(3);
        if (!lines.name().isEmpty()) {
            slots.add(new Slot(
                    transform(lines.name(), style.nameStyle().caseTransform()),
                    style.nameStyle(),
                    fontFor(style.nameStyle().fontSlot(), fonts)));
        }
        if (!lines.role().isEmpty()) {
            slots.add(new Slot(
                    transform(lines.role(), style.roleStyle().caseTransform()),
                    style.roleStyle(),
                    fontFor(style.roleStyle().fontSlot(), fonts)));
        }
        if (!lines.quote().isEmpty()) {
            slots.add(new Slot(
                    transform(quoteWithCurlyMarks(lines.quote()),
                            style.quoteStyle().caseTransform()),
                    style.quoteStyle(),
                    fontFor(style.quoteStyle().fontSlot(), fonts)));
        }

        if (slots.isEmpty()) {
            return List.of();
        }

        float[] sizes = new float[slots.size()];
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            sizes[i] = shrinkToFitWidth(s.text(), s.style(), s.baseFont(),
                    style.minSizePx(), asset.bottomRegionWidthPx(), frc);
        }

        float stackHeight = stackHeight(slots, sizes, style.lineGapPx(), frc);
        if (stackHeight > asset.bottomRegionHeightPx()) {
            float scale = asset.bottomRegionHeightPx() / stackHeight;
            for (int i = 0; i < sizes.length; i++) {
                sizes[i] = Math.max(style.minSizePx(), sizes[i] * scale);
            }
            stackHeight = stackHeight(slots, sizes, style.lineGapPx(), frc);
        }

        int regionLeftX = asset.bottomRegionX();
        int regionWidth = asset.bottomRegionWidthPx();
        int centreX = regionLeftX + regionWidth / 2;
        float stackTopY = asset.bottomRegionY()
                + Math.max(0f, (asset.bottomRegionHeightPx() - stackHeight) / 2f);

        List<Fitted> out = new ArrayList<>(slots.size());
        float cursorY = stackTopY;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            float size = sizes[i];
            Font sized = derive(s.baseFont(), size, s.style().trackingEms());
            LineMetrics lm = sized.getLineMetrics(s.text(), frc);
            float lineHeight = lm.getAscent() + lm.getDescent();
            float baselineY = cursorY + lm.getAscent();

            TextLayout layout = new TextLayout(s.text(), sized, frc);
            int advance = (int) Math.ceil(layout.getAdvance());
            int baselineX = centreX - advance / 2;

            out.add(new Fitted(s.text(), sized, size, baselineX, Math.round(baselineY)));
            cursorY += lineHeight + (i == slots.size() - 1 ? 0f : style.lineGapPx());
        }
        return out;
    }

    private static Font fontFor(PosterTextStyle.FontSlot slot, PosterTextFonts fonts) {
        return switch (slot) {
            case DISPLAY -> fonts.display();
            case BODY -> fonts.body();
            case ITALIC -> fonts.italic();
        };
    }

    private static String transform(String text, PosterTextStyle.CaseTransform t) {
        return switch (t) {
            case NONE -> text;
            case UPPERCASE -> text.toUpperCase(Locale.ROOT);
        };
    }

    /** Wrap the raw quote in curly typographic quotation marks. */
    private static String quoteWithCurlyMarks(String quote) {
        if (quote.isEmpty()) return quote;
        char first = quote.charAt(0);
        char last = quote.charAt(quote.length() - 1);
        boolean alreadyOpen = first == '“' || first == '«';
        boolean alreadyClose = last == '”' || last == '»';
        if (alreadyOpen && alreadyClose) return quote;
        return '“' + quote + '”';
    }

    private static Font derive(Font baseFont, float sizePx, float trackingEms) {
        Font sized = baseFont.deriveFont(sizePx);
        if (trackingEms == 0f) return sized;
        return sized.deriveFont(Map.of(TextAttribute.TRACKING, trackingEms));
    }

    private static float shrinkToFitWidth(String text,
                                          PosterTextStyle.SlotStyle slotStyle,
                                          Font baseFont,
                                          float minSize,
                                          int safeWidthPx,
                                          FontRenderContext frc) {
        float size = slotStyle.targetSizePx();
        while (size > minSize) {
            Font sized = derive(baseFont, size, slotStyle.trackingEms());
            float advance = (float) new TextLayout(text, sized, frc).getAdvance();
            if (advance <= safeWidthPx) {
                return size;
            }
            size -= 1f;
        }
        return Math.max(minSize, 1f);
    }

    private static float stackHeight(List<Slot> slots, float[] sizes, float lineGapPx,
                                     FontRenderContext frc) {
        float total = 0f;
        for (int i = 0; i < slots.size(); i++) {
            Font sized = slots.get(i).baseFont().deriveFont(sizes[i]);
            LineMetrics lm = sized.getLineMetrics("M", frc);
            total += lm.getAscent() + lm.getDescent();
            if (i < slots.size() - 1) total += lineGapPx;
        }
        return total;
    }
}

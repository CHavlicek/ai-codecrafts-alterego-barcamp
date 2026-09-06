package com.aiavatar.alterego.service.text;

import com.aiavatar.alterego.infrastructure.overlay.text.*;

import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAsset;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure {@link PosterTextFitter} helper (017 T010 +
 * T017–T020, refined 2026-05-08 again for the three-slot
 * {name / role / quote} layout).
 *
 * <p>Uses a single SansSerif font for all three slots so size + position
 * assertions don't have to account for per-font metric variation.
 */
class PosterTextFitterTest {

    private static final FontRenderContext FRC =
            new FontRenderContext(new AffineTransform(), true, true);

    /** Test stub: returns the same SansSerif font for every slot. */
    private static final PosterTextFonts UNIFORM_FONTS = new PosterTextFonts() {
        private final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 96);
        @Override public Font display() { return font; }
        @Override public Font body() { return font; }
        @Override public Font italic() { return font; }
    };

    /**
     * A modest style whose natural three-slot stack fits comfortably
     * inside the bundled-asset bottom region (171 px) — used by the
     * tests that assert "short input renders at the configured target
     * sizes with no scaling". Production defaults are intentionally
     * larger and exercise the fitter's vertical scale-down path.
     */
    private static PosterTextStyle naturalFitStyle() {
        return new PosterTextStyle(
                new PosterTextStyle.SlotStyle(
                        PosterTextStyle.FontSlot.DISPLAY, 60f,
                        PosterTextStyle.CaseTransform.UPPERCASE, 0.02f),
                new PosterTextStyle.SlotStyle(
                        PosterTextStyle.FontSlot.BODY, 18f,
                        PosterTextStyle.CaseTransform.UPPERCASE, 0.18f),
                new PosterTextStyle.SlotStyle(
                        PosterTextStyle.FontSlot.ITALIC, 22f,
                        PosterTextStyle.CaseTransform.NONE, 0f),
                10f, 4f,
                new Color(245, 245, 240),
                new Color(8, 8, 12),
                0.06f);
    }

    private static PosterFrameAsset bundledLikeAsset() {
        BufferedImage image = new BufferedImage(1024, 1536, BufferedImage.TYPE_INT_ARGB);
        return PosterFrameAsset.loaded(image,
                116, 124, 783, 1057,
                157, 1273, 701, 171);
    }

    private static PosterFrameAsset shallowAsset(int bottomHeight) {
        BufferedImage image = new BufferedImage(1024, 1536, BufferedImage.TYPE_INT_ARGB);
        return PosterFrameAsset.loaded(image,
                116, 124, 783, 1057,
                157, 1273, 701, bottomHeight);
    }

    @Test
    void shortInputsAndNaturalFitStyleRenderAtTargetSizes() {
        PosterTextLines lines = new PosterTextLines("Ada", "Cloud Architect", "It is always DNS.");
        PosterTextStyle style = naturalFitStyle();
        PosterFrameAsset asset = bundledLikeAsset();

        List<PosterTextFitter.Fitted> fitted =
                PosterTextFitter.fit(lines, style, asset, UNIFORM_FONTS, FRC);

        assertEquals(3, fitted.size(), "name + role + quote all render");
        assertEquals("ADA", fitted.get(0).text(), "name uppercased");
        assertEquals("CLOUD ARCHITECT", fitted.get(1).text(), "role uppercased");
        assertEquals("“It is always DNS.”", fitted.get(2).text(),
                "quote wrapped in curly typographic marks");

        // Hierarchy preserved at the configured target sizes (short input — no shrinking).
        assertEquals(style.nameStyle().targetSizePx(), fitted.get(0).sizePx(), 0.01f);
        assertEquals(style.roleStyle().targetSizePx(), fitted.get(1).sizePx(), 0.01f);
        assertEquals(style.quoteStyle().targetSizePx(), fitted.get(2).sizePx(), 0.01f);

        for (PosterTextFitter.Fitted f : fitted) {
            assertTrue(f.baselineY() > asset.bottomRegionY()
                            && f.baselineY() < asset.bottomRegionY() + asset.bottomRegionHeightPx(),
                    "baselineY for '" + f.text() + "' must be inside the bottom region: "
                            + f.baselineY());
            assertTrue(f.baselineX() >= asset.bottomRegionX(),
                    "baselineX must be >= bottomRegionX");
        }
        // Stack order: name above role above quote.
        assertTrue(fitted.get(0).baselineY() < fitted.get(1).baselineY(),
                "name baseline must be above role baseline");
        assertTrue(fitted.get(1).baselineY() < fitted.get(2).baselineY(),
                "role baseline must be above quote baseline");
    }

    @Test
    void productionDefaultsAlwaysTriggerVerticalScaleToFillRegion() {
        // The shipping defaults intentionally overflow the bottom region's
        // natural stack height so the fitter scales every slot down to
        // exactly fill the region — i.e. the rendered text is as large
        // as it can be while preserving hierarchy. This test locks that
        // intent: short inputs MUST render below the configured target
        // sizes (because the vertical scale-down kicked in), but the
        // hierarchy MUST still hold and every line MUST stay at or
        // above minSizePx.
        PosterTextLines lines = new PosterTextLines("Ada", "Cloud Architect", "It is always DNS.");
        PosterTextStyle style = PosterTextStyle.defaults();
        PosterFrameAsset asset = bundledLikeAsset();

        List<PosterTextFitter.Fitted> fitted =
                PosterTextFitter.fit(lines, style, asset, UNIFORM_FONTS, FRC);

        assertEquals(3, fitted.size());
        assertTrue(fitted.get(0).sizePx() < style.nameStyle().targetSizePx(),
                "name must scale down from its target — defaults are oversized by design");
        assertTrue(fitted.get(0).sizePx() >= fitted.get(2).sizePx(),
                "name >= quote in rendered size (hierarchy)");
        assertTrue(fitted.get(2).sizePx() >= fitted.get(1).sizePx(),
                "quote >= role in rendered size (hierarchy)");
        for (PosterTextFitter.Fitted f : fitted) {
            assertTrue(f.sizePx() >= style.minSizePx(),
                    "every slot stays at or above minSizePx, got " + f.sizePx());
        }
    }

    @Test
    void emptyQuoteRendersOnlyNameAndRole() {
        PosterTextLines lines = new PosterTextLines("Ada", "Cloud Architect", "");
        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, PosterTextStyle.defaults(), bundledLikeAsset(), UNIFORM_FONTS, FRC);
        assertEquals(2, fitted.size());
        assertEquals("ADA", fitted.get(0).text());
        assertEquals("CLOUD ARCHITECT", fitted.get(1).text());
    }

    @Test
    void onlyQuotePresentRendersJustTheQuote() {
        PosterTextLines lines = new PosterTextLines("", "", "It is always DNS.");
        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, PosterTextStyle.defaults(), bundledLikeAsset(), UNIFORM_FONTS, FRC);
        assertEquals(1, fitted.size());
        assertEquals("“It is always DNS.”", fitted.get(0).text());
    }

    @Test
    void allEmptyLinesReturnsEmptyList() {
        PosterTextLines lines = new PosterTextLines("", "", "");
        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, PosterTextStyle.defaults(), bundledLikeAsset(), UNIFORM_FONTS, FRC);
        assertTrue(fitted.isEmpty());
    }

    @Test
    void onlyNamePresentRendersJustTheName() {
        PosterTextLines lines = new PosterTextLines("Ada", "", "");
        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, PosterTextStyle.defaults(), bundledLikeAsset(), UNIFORM_FONTS, FRC);
        assertEquals(1, fitted.size());
        assertEquals("ADA", fitted.get(0).text());
    }

    @Test
    void quoteAlreadyWrappedInCurlyMarksIsNotDoubleWrapped() {
        PosterTextLines lines = new PosterTextLines("", "", "“Already quoted.”");
        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, PosterTextStyle.defaults(), bundledLikeAsset(), UNIFORM_FONTS, FRC);
        assertEquals(1, fitted.size());
        assertEquals("“Already quoted.”", fitted.get(0).text());
    }

    @Test
    void longNameShrinksAndStaysAboveMinSize() {
        // Single very long name overflows the 701px safe width at 84px.
        PosterTextLines lines = new PosterTextLines(
                "Aleksandryyaaaaa Wonderfulton", "", "");
        PosterTextStyle style = PosterTextStyle.defaults();

        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, style, bundledLikeAsset(), UNIFORM_FONTS, FRC);

        assertEquals(1, fitted.size());
        assertTrue(fitted.get(0).sizePx() < style.nameStyle().targetSizePx(),
                "long name must have shrunk below target, got " + fitted.get(0).sizePx());
        assertTrue(fitted.get(0).sizePx() >= style.minSizePx());
    }

    @Test
    void longQuoteShrinksAloneOtherSlotsUntouched() {
        // Use a natural-fit style so the test isolates the long-quote
        // width-shrink behaviour from the production defaults' vertical
        // scale-down (covered separately).
        String longQuote =
                "When the cloud trembles and the gateways fail, only the YAML faithful prevail through deeper levels of indirection";
        PosterTextLines lines = new PosterTextLines("Ada", "Cloud Architect", longQuote);
        PosterTextStyle style = naturalFitStyle();

        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, style, bundledLikeAsset(), UNIFORM_FONTS, FRC);

        assertEquals(3, fitted.size());
        assertEquals(style.nameStyle().targetSizePx(), fitted.get(0).sizePx(), 0.01f,
                "name at target — long quote must not affect it");
        assertEquals(style.roleStyle().targetSizePx(), fitted.get(1).sizePx(), 0.01f,
                "role at target — long quote must not affect it");
        assertTrue(fitted.get(2).sizePx() < style.quoteStyle().targetSizePx(),
                "long quote must shrink below target");
        assertTrue(fitted.get(2).sizePx() >= style.minSizePx());
    }

    @Test
    void pathologicallyLongInputClampsAtMinSizeNotBelow() {
        String pathological = "X".repeat(500);
        PosterTextLines lines = new PosterTextLines(pathological, "", "");
        PosterTextStyle style = PosterTextStyle.defaults();

        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, style, bundledLikeAsset(), UNIFORM_FONTS, FRC);

        assertEquals(1, fitted.size());
        assertEquals(style.minSizePx(), fitted.get(0).sizePx(), 0.01f,
                "pathological input must clamp exactly at minSizePx (FR-1704)");
    }

    @Test
    void shallowBottomRegionTriggersVerticalScale() {
        // 60 px bottom region — short enough that the natural three-line stack
        // won't fit, so the fitter scales all slots down.
        PosterFrameAsset asset = shallowAsset(60);
        PosterTextLines lines = new PosterTextLines("Ada", "Cloud Architect", "It is always DNS.");
        PosterTextStyle style = PosterTextStyle.defaults();

        List<PosterTextFitter.Fitted> fitted = PosterTextFitter.fit(
                lines, style, asset, UNIFORM_FONTS, FRC);

        assertNotNull(fitted);
        assertEquals(3, fitted.size());
        assertTrue(fitted.get(0).sizePx() < style.nameStyle().targetSizePx(),
                "shallow region must shrink the name below target");
        for (PosterTextFitter.Fitted f : fitted) {
            assertTrue(f.sizePx() >= style.minSizePx(),
                    "every slot must clamp at minSizePx, got " + f.sizePx());
        }
    }
}

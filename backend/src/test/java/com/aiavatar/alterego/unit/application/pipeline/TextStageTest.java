package com.aiavatar.alterego.unit.application.pipeline;

import com.aiavatar.alterego.application.pipeline.StageContext;
import com.aiavatar.alterego.infrastructure.overlay.text.TextStage;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.infrastructure.overlay.text.PosterTextLines;
import com.aiavatar.alterego.infrastructure.overlay.text.PosterTextOverlayService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T028 — TextStage delegates with PosterTextLines composed from StageContext. */
class TextStageTest {

    @Test
    void delegatesWithPosterTextLinesFromStageContext() {
        PosterTextOverlayService overlay = mock(PosterTextOverlayService.class);
        PosterImage in = new PosterImage(new byte[]{1}, "image/png", 1, 1);
        PosterImage out = new PosterImage(new byte[]{2}, "image/png", 1, 1);
        when(overlay.apply(eq(in), any(PosterTextLines.class))).thenReturn(out);

        TextStage stage = new TextStage(overlay);
        StageContext ctx = new StageContext("Sam", "Backend Dev", "the quote");

        assertSame(out, stage.apply(in, ctx));

        ArgumentCaptor<PosterTextLines> linesCap = ArgumentCaptor.forClass(PosterTextLines.class);
        verify(overlay).apply(eq(in), linesCap.capture());
        PosterTextLines lines = linesCap.getValue();
        assertEquals("Sam", lines.name());
        assertEquals("Backend Dev", lines.role());
        assertEquals("the quote", lines.quote());
    }
}

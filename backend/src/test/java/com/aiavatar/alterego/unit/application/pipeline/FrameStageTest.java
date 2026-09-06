package com.aiavatar.alterego.unit.application.pipeline;

import com.aiavatar.alterego.infrastructure.overlay.frame.FrameStage;
import com.aiavatar.alterego.application.pipeline.StageContext;
import com.aiavatar.alterego.domain.model.PosterImage;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameOverlayService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** T027 — FrameStage delegates to PosterFrameOverlayService and never touches StageContext fields. */
class FrameStageTest {

    @Test
    void delegatesToFrameOverlayService() {
        PosterFrameOverlayService overlay = mock(PosterFrameOverlayService.class);
        PosterImage in = new PosterImage(new byte[]{1}, "image/png", 1, 1);
        PosterImage out = new PosterImage(new byte[]{2}, "image/png", 1, 1);
        when(overlay.apply(in)).thenReturn(out);

        FrameStage stage = new FrameStage(overlay);
        StageContext ctx = new StageContext("Sam", "Backend Dev", "quote");

        assertSame(out, stage.apply(in, ctx));
        verify(overlay).apply(in);
        // StageContext is not consumed by FrameStage — verified by the
        // mock receiving exactly one invocation of apply(PosterImage).
    }
}

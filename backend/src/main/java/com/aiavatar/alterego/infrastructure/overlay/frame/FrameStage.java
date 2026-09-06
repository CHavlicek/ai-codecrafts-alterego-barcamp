package com.aiavatar.alterego.infrastructure.overlay.frame;

import com.aiavatar.alterego.application.pipeline.PosterStage;
import com.aiavatar.alterego.application.pipeline.StageContext;
import com.aiavatar.alterego.domain.model.PosterImage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stage 1 of the post-image pipeline: composites the bundled poster frame
 * onto the raw image. Thin adapter over the existing
 * {@link PosterFrameOverlayService} so the underlying Graphics2D code is
 * preserved byte-for-byte (FR-2425).
 */
@Component
@Order(0)
public final class FrameStage implements PosterStage {

    private final PosterFrameOverlayService delegate;

    public FrameStage(PosterFrameOverlayService delegate) {
        this.delegate = delegate;
    }

    @Override
    public PosterImage apply(PosterImage input, StageContext ctx) {
        return delegate.apply(input);
    }
}

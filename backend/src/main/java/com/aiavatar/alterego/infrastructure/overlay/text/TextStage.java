package com.aiavatar.alterego.infrastructure.overlay.text;

import com.aiavatar.alterego.application.pipeline.PosterStage;
import com.aiavatar.alterego.application.pipeline.StageContext;
import com.aiavatar.alterego.domain.model.PosterImage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Stage 2 of the post-image pipeline: composites the three text lines
 * (first name, role-of-record, quote) onto the framed image. Adapter
 * over the existing {@link PosterTextOverlayService}.
 */
@Component
@Order(1)
public final class TextStage implements PosterStage {

    private final PosterTextOverlayService delegate;

    public TextStage(PosterTextOverlayService delegate) {
        this.delegate = delegate;
    }

    @Override
    public PosterImage apply(PosterImage input, StageContext ctx) {
        return delegate.apply(input,
                new PosterTextLines(ctx.firstName(), ctx.roleOfRecord(), ctx.quote()));
    }
}

package com.aiavatar.alterego.application.pipeline;

import com.aiavatar.alterego.domain.model.PosterImage;

/**
 * One named, ordered step in the post-image pipeline (FR-2409,
 * Constitution Principle VII). Implementations are pure transforms:
 * input image bytes + stage context → new image bytes. Each stage is
 * independently testable on a fixed input (FR-2423).
 *
 * <p>Production implementations live in {@code infrastructure.overlay.*}
 * (the {@code FrameStage} + {@code TextStage} classes). Tests may freely
 * implement the interface for stub stages.
 */
public interface PosterStage {

    PosterImage apply(PosterImage input, StageContext ctx);
}

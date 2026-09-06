package com.aiavatar.alterego.application.pipeline;

import com.aiavatar.alterego.domain.model.PosterImage;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ordered, named composition of {@link PosterStage} steps applied to a
 * generated poster image (FR-2409). Stages are injected via Spring's
 * {@code List<PosterStage>} mechanism, ordered by each stage's
 * {@code @Order} annotation. The orchestrator depends only on this class;
 * adding a new stage means adding a new {@code @Component} that implements
 * {@link PosterStage} and giving it an {@code @Order} value.
 */
@Component
public final class PosterPipeline {

    private final List<PosterStage> stages;

    public PosterPipeline(List<PosterStage> stages) {
        this.stages = List.copyOf(stages);
    }

    public PosterImage apply(PosterImage input, StageContext ctx) {
        PosterImage current = input;
        for (PosterStage stage : stages) {
            current = stage.apply(current, ctx);
        }
        return current;
    }

    /** Test-visible accessor for the ordered stage list. */
    public List<PosterStage> stages() {
        return stages;
    }
}

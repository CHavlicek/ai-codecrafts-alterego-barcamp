package com.aiavatar.alterego.unit.application.pipeline;

import com.aiavatar.alterego.application.pipeline.PosterPipeline;
import com.aiavatar.alterego.application.pipeline.PosterStage;
import com.aiavatar.alterego.application.pipeline.StageContext;
import com.aiavatar.alterego.domain.model.PosterImage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** T026 — PosterPipeline composes stages in order and is identity for an empty list. */
class PosterPipelineTest {

    private static final StageContext CTX = new StageContext("Sam", "Backend Dev", "quote");
    private static final PosterImage SEED = new PosterImage(new byte[]{1}, "image/png", 1, 1);

    @Test
    void emptyPipelineReturnsInputUnchanged() {
        PosterPipeline p = new PosterPipeline(List.of());
        assertSame(SEED, p.apply(SEED, CTX));
    }

    @Test
    void stagesApplyInDeclaredOrder() {
        List<String> trace = new ArrayList<>();
        PosterStage a = stage("A", trace, in -> in);
        PosterStage b = stage("B", trace, in -> in);
        new PosterPipeline(List.of(a, b)).apply(SEED, CTX);
        assertEquals(List.of("A", "B"), trace);
    }

    @Test
    void stagesChainOutputToNextInput() {
        PosterImage p1 = new PosterImage(new byte[]{2}, "image/png", 1, 1);
        PosterImage p2 = new PosterImage(new byte[]{3}, "image/png", 1, 1);
        PosterStage replaceWithP1 = stage("a", new ArrayList<>(), in -> p1);
        PosterStage replaceWithP2 = stage("b", new ArrayList<>(), in -> {
            assertSame(p1, in);
            return p2;
        });
        assertSame(p2, new PosterPipeline(List.of(replaceWithP1, replaceWithP2)).apply(SEED, CTX));
    }

    private static PosterStage stage(String tag, List<String> trace, UnaryOperator<PosterImage> body) {
        // Anonymous concrete implementations of a sealed interface are
        // disallowed; use FrameStage / TextStage flavour via a non-sealed
        // helper. For the pipeline-composition tests we only care about
        // ordering, so we route through a tiny inline stub.
        return new TraceStage(tag, trace, body);
    }

    /** Test double for the sealed PosterStage interface — package-private. */
    static final class TraceStage implements PosterStage {
        private final String tag;
        private final List<String> trace;
        private final UnaryOperator<PosterImage> body;

        TraceStage(String tag, List<String> trace, UnaryOperator<PosterImage> body) {
            this.tag = tag;
            this.trace = trace;
            this.body = body;
        }

        @Override
        public PosterImage apply(PosterImage input, StageContext ctx) {
            trace.add(tag);
            return body.apply(input);
        }
    }
}

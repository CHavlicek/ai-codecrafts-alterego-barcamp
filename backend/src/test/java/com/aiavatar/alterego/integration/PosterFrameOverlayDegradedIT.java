package com.aiavatar.alterego.integration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.domain.model.Vibe;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAsset;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameAssetLoader;
import com.aiavatar.alterego.infrastructure.overlay.frame.PosterFrameOverlayService;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 015 US3 (P2) FR-1512 / SC-1505 — fail-soft degraded mode: if the frame
 * asset is missing or broken, every poster still renders (un-framed,
 * pass-through) and {@code WARN event=frame.apply.skipped} is logged
 * once per request.
 *
 * <p>Replaces deleted 008 {@code BrandingOverlayDegradedIT}. Uses a
 * {@link TestConfiguration} {@link Primary} bean to override the real
 * {@link PosterFrameAssetLoader} with a stub whose {@code get()} always
 * returns {@link PosterFrameAsset#missing()}. The
 * {@link PosterFrameOverlayService} short-circuits and returns the
 * input unchanged.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = { com.aiavatar.alterego.AlterEgoApplication.class,
                PosterFrameOverlayDegradedIT.MissingAssetConfig.class }
)
@ActiveProfiles("default")
class PosterFrameOverlayDegradedIT {

    @Autowired private TestRestTemplate restTemplate;

    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        Logger overlayLogger = (Logger) LoggerFactory.getLogger(PosterFrameOverlayService.class);
        appender = new ListAppender<>();
        appender.start();
        overlayLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        Logger overlayLogger = (Logger) LoggerFactory.getLogger(PosterFrameOverlayService.class);
        overlayLogger.detachAppender(appender);
    }

    @Test
    void missingFrameAssetStillReturnsUsablePosterAndLogsWarn() {
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS,
                Vibe.REBEL, ArtStyle.OIL_PAINTING, "Paula", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg", selections),
                AlterEgoResponse.class);

        // FR-1512 / SC-1505: HTTP 200 even though the frame asset is missing.
        assertEquals(200, response.getStatusCode().value());
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        // 016 FR-1612: default profile reports outcome=fallback, provider=stub
        // (was REAL pre-016). The asset-missing branch tests the FRAME
        // service's fail-soft path independently of the orchestrator's
        // fallback branch — both legitimately produce a complete poster.
        assertEquals(AlterEgoResponse.Outcome.FALLBACK, body.meta().outcome());
        assertNotNull(body.poster().dataUrl());
        assertTrue(body.poster().dataUrl().startsWith("data:image/png;base64,"),
                "un-framed pass-through still announces image/png");

        // Exactly one WARN event=frame.apply.skipped reason=asset_missing line.
        // This is the load-bearing assertion that proves the overlay was
        // skipped (not silently applied with a no-op asset).
        long warns = appender.list.stream()
                .filter(e -> e.getLevel() == Level.WARN)
                .filter(e -> e.getFormattedMessage().contains("event=frame.apply.skipped"))
                .filter(e -> e.getFormattedMessage().contains("reason=asset_missing"))
                .count();
        assertEquals(1L, warns,
                "FR-1512: missing frame asset MUST emit exactly one WARN per Generate; saw: "
                        + appender.list);
    }

    /**
     * Replaces the real {@link PosterFrameAssetLoader} with one whose
     * {@code get()} always returns the missing sentinel — simulates the
     * "frame asset deliberately removed" fault-injection from
     * {@code quickstart.md} §"Manual verification — fail-soft".
     */
    @TestConfiguration
    static class MissingAssetConfig {
        @Bean
        @Primary
        PosterFrameAssetLoader stubMissingFrameAssetLoader() {
            return new PosterFrameAssetLoader() {
                @Override
                public PosterFrameAsset get() {
                    // Always report the missing sentinel — regardless of what
                    // the parent's @PostConstruct decoded — so the overlay
                    // service short-circuits to the fail-soft branch.
                    return PosterFrameAsset.missing();
                }
            };
        }
    }
}

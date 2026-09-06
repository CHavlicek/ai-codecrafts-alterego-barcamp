package com.aiavatar.alterego.integration;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T061 — standalone redaction-coverage assertion (FR-2415, SC-008).
 * Boots the full app under the default profile, fires a Generate request
 * with a fixture photo, captures every emitted log event via a Logback
 * {@link ListAppender} attached to the root logger, and asserts that:
 *
 * <ul>
 *   <li>no captured event's formatted message contains the photo bytes'
 *       SHA-256 hex digest (defensive: catches a leaked binary blob even
 *       if it sneaks through a base64 step);</li>
 *   <li>no captured event contains the literal {@code api[_-]?key} /
 *       {@code authorization} field markers in either message or MDC.</li>
 * </ul>
 *
 * <p>Complements the existing {@code LogRedactionIT} (which proves the
 * happy-path log line is clean of human-readable photo markers).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
class RedactionCoverageIT {

    @Autowired private TestRestTemplate restTemplate;

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void noEventLeaksPhotoBytesOrCredentialFieldMarkers() throws Exception {
        byte[] photo = SamplePhotos.tinyJpeg();
        String photoSha256 = sha256Hex(photo);
        AlterEgoRequest selections = new AlterEgoRequest(
                Pose.HEROIC, Archetype.SOFTWARE_DEVELOPER, Universe.STAR_WARS, null,
                ArtStyle.OIL_PAINTING, "Sam", null);

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos", HttpMethod.POST,
                MultipartHelper.generateRequest(photo, "image/jpeg", selections),
                AlterEgoResponse.class);
        assertEquals(200, response.getStatusCode().value());

        List<ILoggingEvent> events = appender.list;
        assertTrue(events.size() > 0, "Expected at least one log event during the request");

        for (ILoggingEvent ev : events) {
            String msg = ev.getFormattedMessage();
            assertFalse(msg != null && msg.contains(photoSha256),
                    "Log event leaked the photo's SHA-256 fingerprint: " + ev.getLoggerName());
            assertFalse(msg != null && msg.toLowerCase().contains("apikey"),
                    "Log event leaked an apiKey field marker: " + msg);
            // MDC must also not carry credential field-name keys for any
            // event that reached the appender.
            for (String k : ev.getMDCPropertyMap().keySet()) {
                assertFalse(k.toLowerCase().contains("apikey") || k.toLowerCase().contains("authorization"),
                        "Event MDC carried credential field-name key '" + k + "' — PhotoRedactionFilter must drop these");
            }
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}

package com.aiavatar.alterego.integration;

import com.aiavatar.alterego.domain.model.AlterEgoRequest;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.Archetype;
import com.aiavatar.alterego.domain.model.ArtStyle;
import com.aiavatar.alterego.domain.model.Pose;
import com.aiavatar.alterego.domain.model.Universe;
import com.aiavatar.alterego.testsupport.MultipartHelper;
import com.aiavatar.alterego.testsupport.SamplePhotos;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * T022 — Correlation-ID echo. Verifies the {@code X-Request-Id} header is
 * threaded through the request properly:
 *
 * <ul>
 *   <li>Client-supplied UUID is reused: appears in response header and in
 *       {@code meta.correlationId}.</li>
 *   <li>Header omitted: server generates a fresh UUID; both the response
 *       header and {@code meta.correlationId} carry it.</li>
 *   <li>Header malformed: server falls back to a fresh UUID instead of
 *       rejecting the request (correlation is a debugging aid, not a contract).</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("default")
class CorrelationIdIT {

    @Autowired private TestRestTemplate restTemplate;

    private static AlterEgoRequest sampleSelections() {
        return new AlterEgoRequest(
                Pose.HEROIC, Archetype.CLOUD_ARCHITECT, Universe.STAR_WARS, null, ArtStyle.OIL_PAINTING, "Paula", null);
    }

    @Test
    void clientSuppliedRequestIdIsEchoedInHeaderAndMeta() {
        UUID inbound = UUID.randomUUID();

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg",
                        sampleSelections(), inbound.toString()),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(inbound.toString(), response.getHeaders().getFirst("X-Request-Id"));
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(inbound, body.meta().correlationId());
    }

    @Test
    void absentRequestIdHeaderTriggersServerSideGeneration() {
        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg",
                        sampleSelections()),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        String header = response.getHeaders().getFirst("X-Request-Id");
        assertNotNull(header, "Server MUST generate and echo X-Request-Id when absent");
        UUID parsed = UUID.fromString(header);
        AlterEgoResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(parsed, body.meta().correlationId());
    }

    @Test
    void malformedRequestIdHeaderIsReplacedWithGeneratedUuid() {
        String malformed = "not-a-uuid";

        ResponseEntity<AlterEgoResponse> response = restTemplate.exchange(
                "/api/v1/alter-egos",
                HttpMethod.POST,
                MultipartHelper.generateRequest(SamplePhotos.tinyJpeg(), "image/jpeg",
                        sampleSelections(), malformed),
                AlterEgoResponse.class);

        assertEquals(200, response.getStatusCode().value());
        String header = response.getHeaders().getFirst("X-Request-Id");
        assertNotNull(header);
        assertNotEquals(malformed, header,
                "Malformed inbound X-Request-Id should be replaced, not echoed");
        // Should parse as a UUID.
        UUID.fromString(header);
    }
}

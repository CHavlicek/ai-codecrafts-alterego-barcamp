package com.aiavatar.alterego.boundary.http;

import com.aiavatar.alterego.application.AlterEgoUseCase;
import com.aiavatar.alterego.domain.model.AlterEgoResponse;
import com.aiavatar.alterego.domain.model.AlterEgoUserSelections;
import com.aiavatar.alterego.domain.model.PhotoPayload;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Single endpoint for the alter-ego generation flow. Accepts a multipart
 * body with a {@code photo} part (JPEG/PNG) and a {@code selections} part
 * (JSON, validated). Returns an {@link AlterEgoResponse} carrying the
 * generated character + a data-URL poster.
 *
 * <p>Threads the {@code X-Request-Id} correlation header through the
 * request: the inbound value is reused if present and parseable as a UUID,
 * otherwise the server generates one. The chosen ID is set into the SLF4J
 * MDC for the duration of the request so structured log lines carry it,
 * and is echoed back in the response header + body's
 * {@link AlterEgoResponse.ResponseMeta#correlationId}.
 */
@RestController
@RequestMapping("/api/v1/alter-egos")
public class AlterEgoController {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String MDC_REQUEST_ID = "requestId";
    static final Set<String> ACCEPTED_PHOTO_MIMES = Set.of("image/jpeg", "image/png");

    private final AlterEgoUseCase service;

    public AlterEgoController(AlterEgoUseCase service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AlterEgoResponse> generate(
            @RequestPart("photo") MultipartFile photo,
            @Valid @RequestPart("selections") AlterEgoUserSelections selections,
            @RequestHeader(name = REQUEST_ID_HEADER, required = false) String inboundRequestId
    ) throws HttpMediaTypeNotSupportedException, IOException {

        String mediaType = photo.getContentType();
        if (mediaType == null || !ACCEPTED_PHOTO_MIMES.contains(mediaType)) {
            throw new HttpMediaTypeNotSupportedException(
                    "Photo MIME must be one of: " + ACCEPTED_PHOTO_MIMES);
        }

        UUID correlationId = parseOrGenerate(inboundRequestId);
        MDC.put(MDC_REQUEST_ID, correlationId.toString());
        try {
            PhotoPayload payload = new PhotoPayload(photo.getBytes(), mediaType);
            AlterEgoResponse response = service.generate(selections, payload, correlationId);
            return ResponseEntity.ok()
                    .header(REQUEST_ID_HEADER, correlationId.toString())
                    .body(response);
        } finally {
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private static UUID parseOrGenerate(String header) {
        if (header == null || header.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(header);
        } catch (IllegalArgumentException ex) {
            // Caller sent something non-UUID — replace with a fresh ID rather than 400.
            // Correlation is a debugging aid, not a contract; broken IDs shouldn't fail requests.
            return UUID.randomUUID();
        }
    }
}

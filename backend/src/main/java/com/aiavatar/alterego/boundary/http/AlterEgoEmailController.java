package com.aiavatar.alterego.boundary.http;

import com.aiavatar.alterego.domain.model.SendAlterEgoEmailRequest;
import com.aiavatar.alterego.domain.model.SendAlterEgoEmailResponse;
import com.aiavatar.alterego.application.SendAlterEgoEmailUseCase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 023 (issue #57) — single endpoint for the email-send action on the
 * Alter Ego tab. Multipart body parts: {@code to} (text), {@code firstName}
 * (text), {@code image} (binary, image/png or image/jpeg).
 *
 * <p>Validation flow: the {@code to} + {@code firstName} parts are
 * wrapped in a {@link SendAlterEgoEmailRequest} and validated against
 * its Bean-Validation constraints. Violations surface as RFC 7807 400
 * Bad Request via {@link com.aiavatar.alterego.boundary.http.ProblemDetailAdvice}.
 * Image MIME outside the allow-list → 415.
 *
 * <p>Correlation: honours {@code X-Request-Id} for parity with
 * {@link AlterEgoController}; generates a fresh UUID when absent or
 * non-parseable. Echoed back in the response header.
 */
@RestController
@RequestMapping("/api/v1/alter-egos/email")
public class AlterEgoEmailController {

    static final String REQUEST_ID_HEADER = "X-Request-Id";
    static final String MDC_REQUEST_ID = "requestId";
    static final Set<String> ACCEPTED_IMAGE_MIMES = Set.of("image/jpeg", "image/png");

    private final SendAlterEgoEmailUseCase service;
    private final Validator validator;

    public AlterEgoEmailController(SendAlterEgoEmailUseCase service, Validator validator) {
        this.service = service;
        this.validator = validator;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
                 produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<SendAlterEgoEmailResponse> send(
            @RequestPart(value = "to", required = false) String to,
            @RequestPart(value = "firstName", required = false) String firstName,
            @RequestPart("image") MultipartFile image,
            @RequestHeader(name = REQUEST_ID_HEADER, required = false) String inboundRequestId)
            throws HttpMediaTypeNotSupportedException, IOException,
                    MethodArgumentNotValidException, MissingServletRequestParameterException {

        // Bean-Validation pass: build the wrapper record (which carries
        // the @NotBlank / @Email / @ValidFirstName constraints) and run
        // it through the validator. Translate any violations into the
        // same shape ProblemDetailAdvice already understands.
        SendAlterEgoEmailRequest req = new SendAlterEgoEmailRequest(to, firstName);
        Set<ConstraintViolation<SendAlterEgoEmailRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationsException(violations);
        }

        String mediaType = image.getContentType();
        if (mediaType == null || !ACCEPTED_IMAGE_MIMES.contains(mediaType)) {
            throw new HttpMediaTypeNotSupportedException(
                    "Image MIME must be one of: " + ACCEPTED_IMAGE_MIMES);
        }

        UUID correlationId = parseOrGenerate(inboundRequestId);
        MDC.put(MDC_REQUEST_ID, correlationId.toString());
        try {
            service.send(
                    to,
                    firstName,
                    image.getBytes(),
                    mediaType,
                    correlationId);
            return ResponseEntity.ok()
                    .header(REQUEST_ID_HEADER, correlationId.toString())
                    .body(SendAlterEgoEmailResponse.sent());
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
            return UUID.randomUUID();
        }
    }

    /**
     * Carries constraint-violation details into the {@link ProblemDetailAdvice}
     * 400-handler. Translated to an RFC 7807 problem detail with an
     * {@code errors[]} array of {field, code} pairs — the same shape the
     * existing {@code handleMethodArgumentNotValid} produces.
     */
    public static class ConstraintViolationsException extends RuntimeException {
        private final transient List<Map<String, String>> errors;

        ConstraintViolationsException(Set<ConstraintViolation<SendAlterEgoEmailRequest>> violations) {
            super("Request body validation failed.");
            this.errors = new ArrayList<>();
            for (ConstraintViolation<SendAlterEgoEmailRequest> v : violations) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("field", v.getPropertyPath().toString());
                entry.put("code", v.getMessage());
                errors.add(entry);
            }
        }

        public List<Map<String, String>> getErrors() {
            return errors;
        }
    }
}

package com.aiavatar.alterego.boundary.http;

import com.aiavatar.alterego.boundary.http.AlterEgoEmailController.ConstraintViolationsException;
import com.aiavatar.alterego.infrastructure.email.EmailNotConfiguredException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.mail.MailException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates the request-level exceptions our endpoint can produce into
 * RFC 7807 {@link ProblemDetail} responses with consistent shapes.
 * <p>
 * Extends {@link ResponseEntityExceptionHandler} so the overrides hook into
 * Spring 6's framework-exception pipeline. A bare
 * {@code @ExceptionHandler(HttpMediaTypeNotSupportedException.class)} in a
 * standalone advice class does NOT take precedence over the default
 * {@code ErrorResponse#getBody()} that ships with the framework exception
 * itself; you have to override the base class hooks.
 * <p>
 * FR-016: error responses MUST NOT echo any photo bytes back to the
 * caller. We construct fresh detail strings here rather than embedding
 * exception messages, so a stray byte sequence in {@code ex.getMessage()}
 * cannot leak into the response body.
 */
@ControllerAdvice
public class ProblemDetailAdvice extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);

    /** Stable URI the FE classifies on (research R11). */
    static final URI EMAIL_NOT_CONFIGURED_TYPE =
            URI.create("https://aiavatar.local/problems/email/not-configured");
    static final URI EMAIL_SEND_FAILED_TYPE =
            URI.create("https://aiavatar.local/problems/email/send-failed");

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                "Unsupported media type. Photo MUST be image/jpeg or image/png.");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "Photo exceeds the 5 MB size limit. Resize and try again.");
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * {@link MultipartException} (excluding {@link MaxUploadSizeExceededException}, which
     * has its own override) is not in the base class's hook list. Handle it
     * via a plain {@code @ExceptionHandler} on this same advice — that
     * suffices because no framework default ProblemDetail competes for it.
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(MultipartException.class)
    public ResponseEntity<Object> handleMultipart(MultipartException ex, WebRequest request) {
        if (ex instanceof MaxUploadSizeExceededException msee) {
            return handleMaxUploadSizeExceededException(
                    msee, new HttpHeaders(), HttpStatus.PAYLOAD_TOO_LARGE, request);
        }
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Malformed multipart request. Expected parts: photo, selections.");
        return handleExceptionInternal(ex, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 011: Translate Bean-Validation failures into a structured
     * {@code errors[]} array on the ProblemDetail body. Each entry carries
     * the offending field name and the constraint code, but never the
     * rejected value (FR-1107) — this is essential for the {@code firstName}
     * field, which can carry user-controlled content that we MUST NOT echo
     * back into a response body that some caller might log.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("field", fe.getField());
                    String message = fe.getDefaultMessage();
                    entry.put("code", message != null ? message : fe.getCode());
                    return entry;
                })
                .toList();
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Request body validation failed.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    /**
     * 023 — translate the constraint violations the email controller
     * surfaces (against the {@link com.aiavatar.alterego.domain.model.SendAlterEgoEmailRequest}
     * record) into the same {field, code}[] errors[] shape the existing
     * {@code handleMethodArgumentNotValid} produces. Different exception
     * type (we hand-validate against {@code Validator} instead of using
     * {@code @Valid} on the controller method) but identical wire shape.
     */
    @ExceptionHandler(ConstraintViolationsException.class)
    public ResponseEntity<Object> handleConstraintViolations(
            ConstraintViolationsException ex, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Request body validation failed.");
        body.setProperty("errors", ex.getErrors());
        return handleExceptionInternal(
                ex, body, new HttpHeaders(), HttpStatus.BAD_REQUEST, request);
    }

    /**
     * 023 (FR-2311 / FR-2318) — typed 503 with a stable {@code type} URI
     * the FE classifies on to distinguish the "not configured" branch
     * from a generic transient failure.
     */
    @ExceptionHandler(EmailNotConfiguredException.class)
    public ResponseEntity<Object> handleEmailNotConfigured(
            EmailNotConfiguredException ex, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getDetail());
        body.setType(EMAIL_NOT_CONFIGURED_TYPE);
        body.setTitle("Email service is not configured");
        // No PII to log — just the exception class.
        log.warn("event=email.send.failed reason=not-configured");
        return handleExceptionInternal(
                ex, body, new HttpHeaders(), HttpStatus.SERVICE_UNAVAILABLE, request);
    }

    /**
     * 023 (FR-2316) — Spring {@link MailException} bubbles up after the
     * project-wide RetryTemplate exhausts its attempts. The FE classifies
     * this as a retryable failure and shows the "Sending failed. Please
     * try again." alert.
     */
    @ExceptionHandler(MailException.class)
    public ResponseEntity<Object> handleMailException(MailException ex, WebRequest request) {
        ProblemDetail body = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_GATEWAY,
                "The mail server could not be reached after multiple attempts. Please try again.");
        body.setType(EMAIL_SEND_FAILED_TYPE);
        body.setTitle("Email send failed");
        // Log exception class name + correlationId (if in MDC) only —
        // no recipient, no body, no first-name (R5).
        log.warn(
                "event=email.send.failed reason=transient exceptionClass={}",
                ex.getClass().getSimpleName());
        return handleExceptionInternal(
                ex, body, new HttpHeaders(), HttpStatus.BAD_GATEWAY, request);
    }

    @Nullable
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        // 024 FR-2412: every Problem-Detail body carries the request's
        // correlationId pulled from the {@link CorrelationIdFilter}-managed MDC.
        if (body instanceof ProblemDetail pd) {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null) {
                pd.setProperty("correlationId", correlationId);
            }
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }
}

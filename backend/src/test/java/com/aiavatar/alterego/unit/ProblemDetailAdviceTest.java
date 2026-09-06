package com.aiavatar.alterego.unit;

import com.aiavatar.alterego.boundary.http.ProblemDetailAdvice;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Direct unit tests for {@link ProblemDetailAdvice}'s three handlers.
 * ContractTest exercises HttpMediaTypeNotSupported end-to-end via the
 * controller; the other two only execute when the corresponding
 * exception is thrown by the multipart pipeline, which is hard to
 * reproduce in ContractTest without clumsy fixtures. Unit-testing the
 * advice directly covers those branches cheaply.
 */
class ProblemDetailAdviceTest {

    /**
     * ResponseEntityExceptionHandler's override hooks are {@code protected};
     * this subclass widens them to public so the tests can invoke them
     * directly without going through the MVC dispatcher.
     */
    static final class TestableAdvice extends ProblemDetailAdvice {
        @Override
        public ResponseEntity<Object> handleHttpMediaTypeNotSupported(
                HttpMediaTypeNotSupportedException ex,
                HttpHeaders headers,
                HttpStatusCode status,
                WebRequest request) {
            return super.handleHttpMediaTypeNotSupported(ex, headers, status, request);
        }

        @Override
        public ResponseEntity<Object> handleMaxUploadSizeExceededException(
                MaxUploadSizeExceededException ex,
                HttpHeaders headers,
                HttpStatusCode status,
                WebRequest request) {
            return super.handleMaxUploadSizeExceededException(ex, headers, status, request);
        }
    }

    private final TestableAdvice advice = new TestableAdvice();

    private static ServletWebRequest request() {
        HttpServletRequest http = new MockHttpServletRequest("POST", "/api/v1/alter-egos");
        return new ServletWebRequest(http);
    }

    @Test
    void handleHttpMediaTypeNotSupportedReturns415WithSanitizedDetail() {
        ResponseEntity<Object> response = advice.handleHttpMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException("ignored raw message"),
                new HttpHeaders(),
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                request());
        assertNotNull(response);
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
        Object body = response.getBody();
        assertTrue(body instanceof ProblemDetail, "Body MUST be a ProblemDetail, was: " + body);
        ProblemDetail problem = (ProblemDetail) body;
        assertEquals(415, problem.getStatus());
        assertTrue(problem.getDetail().contains("image/jpeg"),
                () -> "Detail should mention accepted MIMEs: " + problem.getDetail());
        assertTrue(problem.getDetail().contains("image/png"),
                () -> "Detail should mention accepted MIMEs: " + problem.getDetail());
    }

    @Test
    void handleMaxUploadSizeExceededReturns413WithFixedDetail() {
        ResponseEntity<Object> response = advice.handleMaxUploadSizeExceededException(
                new MaxUploadSizeExceededException(5L * 1024 * 1024),
                new HttpHeaders(),
                HttpStatus.PAYLOAD_TOO_LARGE,
                request());
        assertNotNull(response);
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertNotNull(problem);
        assertEquals(413, problem.getStatus());
        assertTrue(problem.getDetail().contains("5 MB"),
                () -> "Detail should mention the 5 MB limit: " + problem.getDetail());
    }

    @Test
    void handleMultipartReturns400WithGuidance() {
        ResponseEntity<Object> response = advice.handleMultipart(
                new MultipartException("malformed"),
                request());
        assertNotNull(response);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ProblemDetail problem = (ProblemDetail) response.getBody();
        assertNotNull(problem);
        assertEquals(400, problem.getStatus());
        assertTrue(problem.getDetail().toLowerCase().contains("multipart"),
                () -> "Detail should mention multipart: " + problem.getDetail());
        assertTrue(problem.getDetail().contains("photo")
                        && problem.getDetail().contains("selections"),
                () -> "Detail should name expected parts: " + problem.getDetail());
    }

    @Test
    void handleMultipartDelegatesMaxUploadSizeSubclass() {
        // MaxUploadSizeExceededException IS-A MultipartException. Verify the
        // advice's handleMultipart branches on instanceof and routes it to
        // the 413 handler rather than the generic 400.
        ResponseEntity<Object> response = advice.handleMultipart(
                new MaxUploadSizeExceededException(5L * 1024 * 1024),
                request());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
    }
}

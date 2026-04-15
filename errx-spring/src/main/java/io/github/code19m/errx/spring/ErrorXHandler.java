package io.github.code19m.errx.spring;

import io.github.code19m.errx.ErrorX;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler that converts {@link ErrorX} into structured HTTP responses.
 *
 * <p><b>HTTP status mapping:</b> Each {@link io.github.code19m.errx.ErrorType} carries
 * its own HTTP status. Notably, {@code NOT_FOUND} maps to 400 (not 404),
 * because 404 is reserved for routing-level "no such endpoint" responses.
 *
 * <p><b>Logging:</b> Server errors (5xx) are logged at ERROR level with full trace.
 * Client errors (4xx) are logged at WARN level. Details and trace are never
 * exposed to the client — they stay in server logs only.
 *
 * <p>To activate, either annotate this class with {@code @Component} in your app,
 * or import it via {@code @Import(ErrorXHandler.class)}.
 */
@RestControllerAdvice
public class ErrorXHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrorXHandler.class);

    @ExceptionHandler(ErrorX.class)
    public ResponseEntity<ErrorResponse> handleErrorX(ErrorX ex) {
        int httpStatus = ex.type().httpStatus();

        // GOAL: Log enough context for debugging, but never leak internals to client
        if (httpStatus >= 500) {
            log.error("Server error [{}:{}] trace={} details={}",
                    ex.type(), ex.code(), ex.trace(), ex.details(), ex);
        } else {
            log.warn("Client error [{}:{}] trace={} details={}",
                    ex.type(), ex.code(), ex.trace(), ex.details());
        }

        ErrorResponse body = ErrorResponse.of(
                ex.code(),
                ex.type().name(),
                ex.getMessage(),
                ex.fields()
        );

        return ResponseEntity.status(httpStatus).body(body);
    }
}

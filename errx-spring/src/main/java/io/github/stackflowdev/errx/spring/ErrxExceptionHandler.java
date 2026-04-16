package io.github.stackflowdev.errx.spring;

import io.github.stackflowdev.errx.ErrorType;
import io.github.stackflowdev.errx.ErrxException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler that converts {@link ErrxException} into structured HTTP responses.
 *
 * <h2>i18n resolution</h2>
 * The exception's {@link ErrxException#code() code} is also used as the
 * {@link MessageSource} key. The locale is resolved from the current request
 * via {@link LocaleContextHolder} — Spring's default
 * {@code AcceptHeaderLocaleResolver} reads it from the {@code Accept-Language} header.
 *
 * <p>Resolution order:
 * <ol>
 *   <li>If {@link ErrxException#hasExplicitMessage()} is true → use that message verbatim</li>
 *   <li>Else if a {@link MessageSource} is configured → look up {@code code} in the bundle</li>
 *   <li>Else fall back to the code itself</li>
 * </ol>
 *
 * <h2>HTTP status mapping</h2>
 * Each {@link ErrorType} carries its own HTTP status. Notably,
 * {@code NOT_FOUND} maps to 400 (not 404), because 404 is reserved for
 * routing-level "no such endpoint" responses.
 *
 * <h2>Logging</h2>
 * Server errors (5xx) are logged at ERROR level with the full cause chain.
 * Client errors (4xx) are logged at WARN level. Details are included in logs
 * but never in responses.
 */
@RestControllerAdvice
public class ErrxExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ErrxExceptionHandler.class);

    private static final String VALIDATION_FAILED_CODE = "validation.failed";
    private static final String VALIDATION_FALLBACK_MESSAGE = "Validation failed";

    private final MessageSource messageSource;

    /** Constructor used when no MessageSource is available — i18n disabled. */
    public ErrxExceptionHandler() {
        this(null);
    }

    /** Constructor used by auto-configuration to inject Spring's MessageSource. */
    public ErrxExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @ExceptionHandler(ErrxException.class)
    public ResponseEntity<ErrorResponse> handleErrxException(ErrxException ex) {
        int httpStatus = ex.type().httpStatus();

        if (httpStatus >= 500) {
            log.error("Server error [{}:{}] details={}", ex.type(), ex.code(), ex.details(), ex);
        } else {
            log.warn("Client error [{}:{}] details={}", ex.type(), ex.code(), ex.details());
        }

        String message = resolveMessage(ex);

        ErrorResponse body = ErrorResponse.of(ex.code(), message, ex.fields());
        return ResponseEntity.status(httpStatus).body(body);
    }

    // ── Bean Validation: @Valid on @RequestBody ──────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage(),
                        (first, second) -> first + "; " + second
                ));

        log.warn("Validation error: {}", fields);

        ErrorResponse body = ErrorResponse.of(
                VALIDATION_FAILED_CODE,
                resolveValidationMessage(),
                fields
        );
        return ResponseEntity.status(ErrorType.VALIDATION.httpStatus()).body(body);
    }

    // ── Bean Validation: @Validated on path/query params ─────────────

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fields = ex.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        cv -> cv.getPropertyPath().toString(),
                        cv -> cv.getMessage(),
                        (first, second) -> first + "; " + second
                ));

        log.warn("Constraint violation: {}", fields);

        ErrorResponse body = ErrorResponse.of(
                VALIDATION_FAILED_CODE,
                resolveValidationMessage(),
                fields
        );
        return ResponseEntity.status(ErrorType.VALIDATION.httpStatus()).body(body);
    }

    // ── Message resolution ───────────────────────────────────────────

    private String resolveMessage(ErrxException ex) {
        if (ex.hasExplicitMessage()) {
            return ex.getMessage();
        }
        return lookup(ex.code(), ex.args(), ex.code());
    }

    private String resolveValidationMessage() {
        return lookup(VALIDATION_FAILED_CODE, new Object[0], VALIDATION_FALLBACK_MESSAGE);
    }

    /**
     * Look up a message by key. If no MessageSource is configured or the key
     * isn't in any bundle, return the fallback.
     */
    private String lookup(String key, Object[] args, String fallback) {
        if (messageSource == null) {
            return fallback;
        }
        Locale locale = LocaleContextHolder.getLocale();
        return messageSource.getMessage(key, args, fallback, locale);
    }
}

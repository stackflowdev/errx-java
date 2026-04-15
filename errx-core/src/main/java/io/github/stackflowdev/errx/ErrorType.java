package io.github.stackflowdev.errx;

/**
 * Categorizes errors into well-known application-level types.
 *
 * <p>Each type carries a default machine-readable code string and
 * an HTTP status code used by the Spring integration module.
 *
 * <p><b>Design note:</b> {@code NOT_FOUND} maps to HTTP 400, not 404.
 * 404 is reserved exclusively for routing-level "no such endpoint" responses.
 * A missing business resource (e.g. user not found) is a client input error (400).
 */
public enum ErrorType {

    INTERNAL    ("INTERNAL",       500),
    VALIDATION  ("VALIDATION",     400),
    NOT_FOUND   ("NOT_FOUND",      400),
    CONFLICT    ("CONFLICT",       409),
    AUTH        ("AUTHENTICATION", 401),
    FORBIDDEN   ("FORBIDDEN",      403),
    THROTTLING  ("THROTTLING",     429);

    private final String defaultCode;
    private final int httpStatus;

    ErrorType(String defaultCode, int httpStatus) {
        this.defaultCode = defaultCode;
        this.httpStatus = httpStatus;
    }

    /** Default machine-readable code (e.g. "NOT_FOUND", "VALIDATION"). */
    public String defaultCode() {
        return defaultCode;
    }

    /** HTTP status code for REST response mapping. */
    public int httpStatus() {
        return httpStatus;
    }
}

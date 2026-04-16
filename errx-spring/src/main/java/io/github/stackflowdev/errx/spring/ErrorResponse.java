package io.github.stackflowdev.errx.spring;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized error response body returned to API clients.
 *
 * <p>Fields with null values are excluded from JSON output, so
 * validation-free errors won't have an empty {@code "fields"} key.
 *
 * <p>The {@code type} carried internally by the exception is intentionally
 * NOT part of this response — clients rely on the HTTP status code and
 * the machine-readable {@code code} for programmatic handling.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        Map<String, String> fields,
        Instant timestamp
) {

    public static ErrorResponse of(String code, String message, Map<String, String> fields) {
        return new ErrorResponse(
                code,
                message,
                fields == null || fields.isEmpty() ? null : fields,
                Instant.now()
        );
    }
}
